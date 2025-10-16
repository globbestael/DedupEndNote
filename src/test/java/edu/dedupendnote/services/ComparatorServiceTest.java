package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;

@TestConfiguration
class ComparatorServiceTest {

	ComparatorService comparatorService = new ComparatorService();

	@ParameterizedTest(name = "{index}: compareTitles({0}, {1})={2}")
	@MethodSource("titleArgumentProvider")
	void compareTitlesTest(String title1, String title2, boolean expected) {
		Publication p1 = new Publication();
		p1.addTitles(title1);
		Publication p2 = new Publication();
		p2.addTitles(title2);

		boolean result = ComparatorService.compareTitles(p1, p2);
		assertThat(result).as("Not equal: '%s' and '%s'", title1, title2).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{index}: compareJournals({0}, {1})={2}")
	@MethodSource("journalArgumentProvider")
	void compareJournalsTest(String journal1, String journal2, boolean expected) {
		Publication p1 = new Publication();
		p1.addJournals(journal1);
		Publication p2 = new Publication();
		p2.addJournals(journal2);

		boolean result = ComparatorService.compareJournals(p1, p2);
		assertThat(result).isEqualTo(expected);
	}

	static Stream<Arguments> journalArgumentProvider() {
		// @formatter:off
			return Stream.of(
					arguments("Journal of Medicine", "Journal of Medicine", true),
					arguments("J. Med.", "Journal of Medicine", true),
					arguments("Different Journal", "Another Journal", false)
			);
			// @formatter:on
	}

	@ParameterizedTest(name = "{index}: compareIssns({0}, {1})={2}")
	@MethodSource("issnArgumentProvider")
	void compareIssnsTest(String issn1, String issn2, boolean expected) {
		Publication p1 = new Publication();
		p1.addIssns(issn1);
		Publication p2 = new Publication();
		p2.addIssns(issn2);

		boolean result = ComparatorService.compareIssns(p1, p2);
		assertThat(result).isEqualTo(expected);
	}

	static Stream<Arguments> issnArgumentProvider() {
		// @formatter:off
			return Stream.of(
					arguments("1234-5678", "1234-5678", true),
					arguments("1234-5678", "8765-4321", false),
					arguments("1234-567X", "1234-567x", true)
			);
			// @formatter:on
	}

	static Stream<Arguments> titleArgumentProvider() {
		// @formatter:off
		return Stream.of(
				arguments("This is a test title", "This is a test title", true),
				arguments("Another test title", "A different test title", false),
				arguments("Title with sufficient pages", "Title with sufficient pages", true),
				arguments("Reply to a title", "Some other title", false) // isReply should make it true
		);
		// @formatter:on
	}
}