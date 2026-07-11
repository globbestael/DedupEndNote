package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import edu.dedupendnote.services.BoundedDedupRunner;
import edu.dedupendnote.services.BoundedDedupRunner.RunOutcome;
import edu.dedupendnote.services.BoundedDedupRunner.RunStatus;
import edu.dedupendnote.services.CancelledException;
import edu.dedupendnote.services.DeduplicationException;

/**
 * Isolated tests for the permit-release contract — no Spring context, no HTTP.
 * The runner must free its single concurrency slot on every outcome, including a
 * worker that ignores interruption, so a subsequent run can always acquire a slot.
 */
@Timeout(value = 15, unit = TimeUnit.SECONDS)
class BoundedDedupRunnerTest {

	/** A short timeout keeps the timeout-path tests fast. */
	private static BoundedDedupRunner runnerWithOnePermit() {
		return new BoundedDedupRunner(1, Duration.ofMillis(300));
	}

	/** Proves a permit is free by running a trivial task and expecting COMPLETED (not BUSY). */
	private static void assertPermitAvailable(BoundedDedupRunner runner) {
		RunOutcome outcome = runner.runWithLimit(UUID.randomUUID(), () -> "ok");
		assertThat(outcome.status()).isEqualTo(RunStatus.COMPLETED);
		assertThat(outcome.result()).isEqualTo("ok");
	}

	@Test
	void completed_returnsResult_andReleasesPermit() {
		BoundedDedupRunner runner = runnerWithOnePermit();

		RunOutcome outcome = runner.runWithLimit(UUID.randomUUID(), () -> "DONE: 5 written");

		assertThat(outcome.status()).isEqualTo(RunStatus.COMPLETED);
		assertThat(outcome.result()).isEqualTo("DONE: 5 written");
		assertPermitAvailable(runner);
	}

	@Test
	void busy_whenAllPermitsHeld_thenSlotReusableAfterRelease() throws Exception {
		BoundedDedupRunner runner = runnerWithOnePermit();
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);

		Thread holder = new Thread(() -> runner.runWithLimit(UUID.randomUUID(), () -> {
			started.countDown();
			release.await();
			return "held";
		}));
		holder.start();
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

		// Permit is taken by the holder → a second run is rejected as BUSY.
		RunOutcome busy = runner.runWithLimit(UUID.randomUUID(), () -> "second");
		assertThat(busy.status()).isEqualTo(RunStatus.BUSY);
		assertThat(busy.result()).isNull();

		release.countDown();
		holder.join(5_000);
		assertPermitAvailable(runner);
	}

	@Test
	void timedOut_whenTaskExceedsTimeout_andReleasesPermit() {
		BoundedDedupRunner runner = runnerWithOnePermit();

		RunOutcome outcome = runner.runWithLimit(UUID.randomUUID(), () -> {
			Thread.sleep(30_000); // far beyond the 300ms test timeout; interrupted on cancel
			return "never";
		});

		assertThat(outcome.status()).isEqualTo(RunStatus.TIMED_OUT);
		assertPermitAvailable(runner);
	}

	@Test
	void cancelled_viaCancel_returnsCancelled_andReleasesPermit() throws Exception {
		BoundedDedupRunner runner = runnerWithOnePermit();
		UUID sessionId = UUID.randomUUID();
		CountDownLatch started = new CountDownLatch(1);

		Thread runThread = new Thread(() -> {
			RunOutcome outcome = runner.runWithLimit(sessionId, () -> {
				started.countDown();
				Thread.sleep(30_000);
				return "never";
			});
			// The worker throws CancelledException on interruption → CANCELLED outcome.
			assertThat(outcome.status()).isEqualTo(RunStatus.CANCELLED);
		});
		runThread.start();
		assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

		assertThat(runner.cancel(sessionId)).isTrue();
		runThread.join(5_000);
		assertPermitAvailable(runner);
	}

	@Test
	void failed_whenTaskThrowsDeduplicationException_carriesMessage_andReleasesPermit() {
		BoundedDedupRunner runner = runnerWithOnePermit();

		RunOutcome outcome = runner.runWithLimit(UUID.randomUUID(), () -> {
			throw new DeduplicationException("ERROR: The IDs are not unique.");
		});

		assertThat(outcome.status()).isEqualTo(RunStatus.FAILED);
		assertThat(outcome.errorMessage()).isEqualTo("ERROR: The IDs are not unique.");
		assertPermitAvailable(runner);
	}

	@Test
	void cancelledException_fromTask_mapsToCancelled_notFailed() {
		BoundedDedupRunner runner = runnerWithOnePermit();

		RunOutcome outcome = runner.runWithLimit(UUID.randomUUID(), () -> {
			throw new CancelledException("ERROR: Deduplication was cancelled by the user.");
		});

		assertThat(outcome.status()).isEqualTo(RunStatus.CANCELLED);
		assertThat(outcome.errorMessage()).isEqualTo("ERROR: Deduplication was cancelled by the user.");
		assertPermitAvailable(runner);
	}

	@Test
	void uncooperativeTask_thatIgnoresInterrupt_stillReleasesPermitOnTimeout() {
		BoundedDedupRunner runner = runnerWithOnePermit();
		AtomicBoolean release = new AtomicBoolean(false);
		try {
			RunOutcome outcome = runner.runWithLimit(UUID.randomUUID(), () -> {
				// Spin-wait ignores the interrupt flag entirely — the worst case.
				while (!release.get()) {
					Thread.onSpinWait();
				}
				return "eventually";
			});

			assertThat(outcome.status()).isEqualTo(RunStatus.TIMED_OUT);
			// The orphan is still spinning, yet a fresh run must acquire a slot.
			assertPermitAvailable(runner);
		} finally {
			release.set(true); // let the orphan exit so the test JVM is clean
		}
	}

	@Test
	void cancel_unknownSession_returnsFalse() {
		BoundedDedupRunner runner = runnerWithOnePermit();
		assertThat(runner.cancel(UUID.randomUUID())).isFalse();
	}
}
