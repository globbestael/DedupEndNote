package edu.dedupendnote.unit.services.comparison;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.services.comparison.AuthorsComparisonService;
import edu.dedupendnote.services.comparison.DuplicateMatcher;
import edu.dedupendnote.services.comparison.FieldComparators;
import edu.dedupendnote.services.comparison.JournalComparisonService;

/**
 * Tests the rule order of {@link DuplicateMatcher} with stub field comparators (no Spring context).
 */
class DuplicateMatcherTest {

	private final List<String> calls = new ArrayList<>();

	private boolean pages = true;
	private boolean authors = true;
	private boolean titles = true;
	private boolean issns = false;
	private boolean journals = false;

	private DuplicateMatcher matcher() {
		AuthorsComparisonService authorsStub = new AuthorsComparisonService() {
			@Override
			public boolean compare(BibliographicItem r1, BibliographicItem r2) {
				calls.add("authors");
				return authors;
			}

			@Override
			public Double getSimilarity() {
				return 0.0;
			}
		};
		JournalComparisonService journalsStub = new JournalComparisonService() {
			@Override
			public boolean compare(BibliographicItem r1, BibliographicItem r2) {
				calls.add("journals");
				return journals;
			}

			@Override
			public boolean compareIssns(BibliographicItem r1, BibliographicItem r2) {
				calls.add("issns");
				return issns;
			}
		};
		return new DuplicateMatcher(new FieldComparators(authorsStub, (r1, r2) -> {
			calls.add("titles");
			return titles;
		}, journalsStub, (r1, r2) -> {
			calls.add("pages");
			return pages;
		}));
	}

	private BibliographicItem item(String... dois) {
		BibliographicItem item = new BibliographicItem();
		item.setDois(Set.of(dois));
		return item;
	}

	@Test
	void stepsRunInOrder_andShortCircuitOnFirstFailure() {
		pages = false;
		assertThat(matcher().areDuplicates(item(), item())).isFalse();
		assertThat(calls).containsExactly("pages");
	}

	@Test
	void failingAuthorsOrTitles_meansNotDuplicates() {
		authors = false;
		assertThat(matcher().areDuplicates(item(), item())).isFalse();
		assertThat(calls).containsExactly("pages", "authors");

		calls.clear();
		authors = true;
		titles = false;
		assertThat(matcher().areDuplicates(item(), item())).isFalse();
		assertThat(calls).containsExactly("pages", "authors", "titles");
	}

	@Test
	void sharedDoi_skipsIssnAndJournalComparison() {
		assertThat(matcher().areDuplicates(item("10.1/a"), item("10.1/a"))).isTrue();
		assertThat(calls).containsExactly("pages", "authors", "titles");
	}

	@Test
	void differentDois_fallBackToIssnThenJournal() {
		assertThat(matcher().areDuplicates(item("10.1/a"), item("10.1/b"))).isFalse();
		assertThat(calls).containsExactly("pages", "authors", "titles", "issns", "journals");

		calls.clear();
		issns = true;
		assertThat(matcher().areDuplicates(item("10.1/a"), item("10.1/b"))).isTrue();
		assertThat(calls).containsExactly("pages", "authors", "titles", "issns");

		calls.clear();
		issns = false;
		journals = true;
		assertThat(matcher().areDuplicates(item("10.1/a"), item("10.1/b"))).isTrue();
		assertThat(calls).containsExactly("pages", "authors", "titles", "issns", "journals");
	}

	@Test
	void missingDoiOnOneSide_isNotASharedDoi() {
		assertThat(matcher().areDuplicates(item("10.1/a"), item())).isFalse();
		assertThat(calls).containsExactly("pages", "authors", "titles", "issns", "journals");
	}
}
