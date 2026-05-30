package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.services.BibliographicItemReader;
import edu.dedupendnote.services.DefaultTitleComparisonService;

// See SimilarityJournalTest for a TODO on renaming Similarity*Test files.
class SimilarityTitleTest {

	/*
	 * full comparison for normalized titles: positive
	 */
	@ParameterizedTest(name = "{index}: compareTitles({0}, {1})")
	@MethodSource("fullPositiveArgumentProvider")
	void fullPositiveTest(String input1, String input2) {
		BibliographicItem p1 = new BibliographicItem();
		BibliographicItem p2 = new BibliographicItem();
		BibliographicItemReader.addNormalizedTitle(input1, p1);
		BibliographicItemReader.addNormalizedTitle(input2, p2);

		assertThat(new DefaultTitleComparisonService().compare(p1, p2))
				.as("Titles are NOT similar: '%s' and '%s'", input1, input2).isTrue();
	}

	/*
	 * full comparison for normalized titles: negative
	 */
	@ParameterizedTest(name = "{index}: compareTitles({0}, {1})")
	@MethodSource("fullNegativeArgumentProvider")
	void fullNegativeTest(String input1, String input2) {
		BibliographicItem p1 = new BibliographicItem();
		BibliographicItem p2 = new BibliographicItem();
		BibliographicItemReader.addNormalizedTitle(input1, p1);
		BibliographicItemReader.addNormalizedTitle(input2, p2);

		assertThat(new DefaultTitleComparisonService().compare(p1, p2))
				.as("Titles are similar: '%s' and '%s'", input1, input2).isFalse();
	}

	static Stream<Arguments> fullPositiveArgumentProvider() {
		// @formatter:off
		return Stream.of(
			arguments("This is a test title", "This is a test title"),
			arguments("Title with sufficient pages", "Title with sufficient pages")
		);
		// @formatter:on
	}

	static Stream<Arguments> fullNegativeArgumentProvider() {
		// @formatter:off
		return Stream.of(
			arguments("Another test title", "A different test title"),
			// isReply would make this true, but BibliographicItem.isReply is not set here
			arguments("Reply to a title", "Some other title")
		);
		// @formatter:on
	}
}
