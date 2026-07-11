package edu.dedupendnote.services;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Owns the full lifecycle of a deduplication run: the concurrency permit (a bounded
 * {@link java.util.concurrent.Semaphore}), the worker execution, the per-run timeout,
 * and the registry of in-flight runs used to cancel a run by session id.
 *
 * <p>Hard contract: the concurrency permit is released exactly once on <em>every</em>
 * outcome — normal completion, busy rejection, timeout, user cancellation, domain error,
 * and the pathological case where the worker task does not stop after being interrupted.
 * Release is done in a {@code finally} that never awaits worker termination, so a stuck
 * task cannot hold the release. A timed-out or cancelled worker keeps running orphaned on
 * the shared executor but holds no permit; how promptly it actually stops is bounded by
 * the cooperative-cancellation checks in the comparison loop (a separate concern).
 *
 * <p>This module is deliberately free of HTTP, file, and WebSocket concerns so it can be
 * unit-tested in isolation. The caller maps the returned {@link RunOutcome} to a response,
 * performs any output-file cleanup, and emits progress messages.
 */
@Service
@Slf4j
public class BoundedDedupRunner {

	public enum RunStatus {
		COMPLETED, BUSY, TIMED_OUT, CANCELLED, FAILED
	}

	/**
	 * Outcome of a run. {@code result} is set only for {@link RunStatus#COMPLETED};
	 * {@code errorMessage} is set only for {@link RunStatus#CANCELLED} and
	 * {@link RunStatus#FAILED}.
	 */
	public record RunOutcome(RunStatus status, @Nullable String result, @Nullable String errorMessage) {

		static RunOutcome completed(String result) {
			return new RunOutcome(RunStatus.COMPLETED, result, null);
		}

		static RunOutcome busy() {
			return new RunOutcome(RunStatus.BUSY, null, null);
		}

		static RunOutcome timedOut() {
			return new RunOutcome(RunStatus.TIMED_OUT, null, null);
		}

		static RunOutcome cancelled(String message) {
			return new RunOutcome(RunStatus.CANCELLED, null, message);
		}

		static RunOutcome failed(String message) {
			return new RunOutcome(RunStatus.FAILED, null, message);
		}
	}

	private static final String CANCELLED_MESSAGE = "ERROR: Deduplication was cancelled by the user.";

	@Value("${dedup.max-concurrent-runs:4}")
	private int maxConcurrentRuns;

	@Value("${dedup.timeout-minutes:20}")
	private int timeoutMinutes;

	@SuppressWarnings("NullAway.Init") // set in init() / test constructor
	private Semaphore semaphore;

	@SuppressWarnings("NullAway.Init")
	private ExecutorService executor;

	@SuppressWarnings("NullAway.Init")
	private Duration timeout;

	private final ConcurrentHashMap<UUID, Future<?>> runningFutures = new ConcurrentHashMap<>();

	public BoundedDedupRunner() {
	}

	/** Test constructor: fixed permit count and an arbitrarily short timeout. */
	public BoundedDedupRunner(int maxConcurrentRuns, Duration timeout) {
		this.maxConcurrentRuns = maxConcurrentRuns;
		this.timeout = timeout;
		init();
	}

	@PostConstruct
	void init() {
		if (timeout == null) {
			timeout = Duration.ofMinutes(timeoutMinutes);
		}
		semaphore = new Semaphore(maxConcurrentRuns);
		executor = Executors.newVirtualThreadPerTaskExecutor();
	}

	@PreDestroy
	void shutdown() {
		executor.shutdownNow();
	}

	/**
	 * Runs {@code task} under the concurrency limit, blocking until it completes, times
	 * out, or is cancelled. Always releases the permit before returning.
	 */
	public RunOutcome runWithLimit(UUID sessionId, Callable<String> task) {
		if (!semaphore.tryAcquire()) {
			return RunOutcome.busy();
		}
		Future<String> future = executor.submit(task);
		runningFutures.put(sessionId, future);
		try {
			String result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
			return RunOutcome.completed(result);
		} catch (TimeoutException e) {
			// Do NOT await termination: interrupt the worker and return immediately.
			future.cancel(true);
			return RunOutcome.timedOut();
		} catch (CancellationException e) {
			return RunOutcome.cancelled(CANCELLED_MESSAGE);
		} catch (ExecutionException e) {
			Throwable cause = e.getCause();
			// CancelledException is a subtype of DeduplicationException — check it first.
			if (cause instanceof CancelledException ce) {
				return RunOutcome.cancelled(ce.getErrorMessage());
			}
			if (cause instanceof DeduplicationException de) {
				return RunOutcome.failed(de.getErrorMessage());
			}
			log.error("Unexpected error during deduplication run", cause == null ? e : cause);
			throw new IllegalStateException("Unexpected error during deduplication run", cause == null ? e : cause);
		} catch (InterruptedException e) {
			// The caller's thread was interrupted while waiting; abandon the run.
			Thread.currentThread().interrupt();
			future.cancel(true);
			return RunOutcome.cancelled(CANCELLED_MESSAGE);
		} finally {
			runningFutures.remove(sessionId);
			semaphore.release();
		}
	}

	/**
	 * Requests cancellation of the run for {@code sessionId} by interrupting its worker.
	 *
	 * @return {@code false} if no run is currently registered for that session
	 */
	public boolean cancel(UUID sessionId) {
		Future<?> future = runningFutures.get(sessionId);
		if (future == null) {
			return false;
		}
		future.cancel(true);
		return true;
	}
}
