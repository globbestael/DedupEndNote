package edu.dedupendnote.services.comparison;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * A size-bounded, thread-safe cache of compiled {@link Pattern}s keyed by String, with
 * least-recently-used eviction. Replaces the previously unbounded static maps in
 * {@link DefaultJournalComparisonService} so that processing many distinct journal names
 * cannot grow the heap without limit (security hardening issue 04).
 *
 * <p>The cache is a pure memo: eviction never changes a comparison result, it only forces
 * the pattern to be recompiled on the next miss. All access is serialised on the backing
 * map so compute-if-absent, access-order update and eviction happen atomically.
 */
public class BoundedPatternCache {

	@SuppressWarnings("serial") // never serialised
	private static final class LruPatternMap extends LinkedHashMap<String, Pattern> {

		private final int maxSize;

		LruPatternMap(int maxSize) {
			super(16, 0.75f, true); // accessOrder = true → get()/put() move the entry to MRU
			this.maxSize = maxSize;
		}

		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
			return size() > maxSize;
		}
	}

	private final Map<String, Pattern> cache;

	public BoundedPatternCache(int maxSize) {
		this.cache = new LruPatternMap(maxSize);
	}

	/**
	 * Returns the cached pattern for {@code key}, computing and storing it with
	 * {@code mappingFunction} on a miss. Access, computation and eviction happen atomically.
	 */
	public Pattern get(String key, Function<String, Pattern> mappingFunction) {
		synchronized (cache) {
			Pattern existing = cache.get(key);
			if (existing != null) {
				return existing;
			}
			Pattern computed = mappingFunction.apply(key);
			cache.put(key, computed);
			return computed;
		}
	}

	/** Returns the cached pattern for {@code key}, or {@code null} if not present. */
	public @Nullable Pattern get(String key) {
		synchronized (cache) {
			return cache.get(key);
		}
	}

	/** Returns a snapshot copy of the current keys, safe to iterate without holding the lock. */
	public Set<String> keySet() {
		synchronized (cache) {
			return new LinkedHashSet<>(cache.keySet());
		}
	}

	public int size() {
		synchronized (cache) {
			return cache.size();
		}
	}
}
