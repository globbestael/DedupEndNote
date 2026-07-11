package edu.dedupendnote.unit.services.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import edu.dedupendnote.services.comparison.BoundedPatternCache;

/** Isolated tests for the LRU pattern cache — no Spring context. */
class BoundedPatternCacheTest {

	private static Pattern compile(String key) {
		return Pattern.compile(Pattern.quote(key));
	}

	@Test
	void get_miss_computes_hit_returnsSameInstanceWithoutRecomputing() {
		BoundedPatternCache cache = new BoundedPatternCache(10);
		AtomicInteger calls = new AtomicInteger();

		Pattern first = cache.get("A", k -> {
			calls.incrementAndGet();
			return compile(k);
		});
		Pattern second = cache.get("A", k -> {
			calls.incrementAndGet();
			return compile(k);
		});

		assertThat(calls.get()).isEqualTo(1); // computed once
		assertThat(second).isSameAs(first); // hit returns the cached instance
	}

	@Test
	void size_neverExceedsMax() {
		BoundedPatternCache cache = new BoundedPatternCache(100);

		for (int i = 0; i < 1000; i++) {
			cache.get("k" + i, BoundedPatternCacheTest::compile);
		}

		assertThat(cache.size()).isEqualTo(100);
	}

	@Test
	void evictsLeastRecentlyUsedEntry() {
		BoundedPatternCache cache = new BoundedPatternCache(2);
		cache.get("A", BoundedPatternCacheTest::compile);
		cache.get("B", BoundedPatternCacheTest::compile);

		// Touch A so B becomes the least-recently-used entry.
		cache.get("A", BoundedPatternCacheTest::compile);
		// Inserting C exceeds the cap → the LRU entry (B) is evicted.
		cache.get("C", BoundedPatternCacheTest::compile);

		assertThat(cache.keySet()).containsExactlyInAnyOrder("A", "C");
		assertThat(cache.get("B")).isNull();
	}
}
