package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.services.CancelledException;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.comparison.AuthorsComparisonService;
import edu.dedupendnote.services.comparison.FieldComparators;

/**
 * Proves the comparison loop observes cancellation at per-pair granularity, not only
 * between pivots (issue 02). No Spring context: a fake {@link FieldComparators} drives
 * the loop and interrupts the worker after the first pair.
 */
class DeduplicationServiceCancellationTest {

	@AfterEach
	void clearInterrupt() {
		// The per-pair check leaves the (test) worker thread interrupted; clear it so the
		// flag does not leak into other tests that reuse this thread.
		Thread.interrupted();
	}

	@Test
	void compareSet_observesInterrupt_perPair_notOnlyPerPivot() {
		AtomicInteger pagesCalls = new AtomicInteger();

		AuthorsComparisonService authors = new AuthorsComparisonService() {
			@Override
			public boolean compare(BibliographicItem r1, BibliographicItem r2) {
				return false;
			}

			@Override
			public Double getSimilarity() {
				return 0.0;
			}
		};

		// pages() is the first comparator in the chain: it interrupts the thread on its
		// first call and returns false (short-circuiting authors/titles/journals).
		FieldComparators fieldComparators = new FieldComparators(authors, (r1, r2) -> false,
				(r1, r2, isSameDois) -> false, (r1, r2, map) -> {
					pagesCalls.incrementAndGet();
					Thread.currentThread().interrupt();
					return false;
				});

		DeduplicationService service = new DeduplicationService(fieldComparators, null, null);

		// 1 pivot + 3 inner items: the inner loop would run 3× without per-pair cancellation.
		List<BibliographicItem> yearBucket = new ArrayList<>();
		for (int id = 1; id <= 4; id++) {
			BibliographicItem item = new BibliographicItem();
			item.setId(id);
			item.setPublicationYear(2020);
			yearBucket.add(item);
		}

		assertThatThrownBy(() -> service.compareSet(yearBucket, 2020, true, msg -> {
		})).isInstanceOf(CancelledException.class).hasMessageContaining("cancelled by the user");

		// Exactly one comparison ran before the interrupt was observed → per-pair.
		// A per-pivot-only check would have compared all three inner items first (count 3).
		assertThat(pagesCalls.get()).isEqualTo(1);
	}
}
