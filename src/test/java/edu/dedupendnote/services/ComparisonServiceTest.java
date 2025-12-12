package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;

@TestConfiguration
class ComparisonServiceTest {

	@ParameterizedTest(name = "{index}: compareIssns({0}, {1})={2}")
	@MethodSource("issnArgumentProvider")
	void compareIssnsTest(String issn1, String issn2, boolean expected) {
		Publication p1 = new Publication();
		Publication p2 = new Publication();
		p1.getIssns().addAll(NormalizationService.normalizeInputIssns(issn1).issns());
		p2.getIssns().addAll(NormalizationService.normalizeInputIssns(issn2).issns());

		boolean result = ComparisonService.compareIssns(p1, p2);
		assertThat(result).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{index}: compareJournals({0}, {1})={2}")
	@MethodSource("journalArgumentProvider")
	void compareJournalsTest(String journal1, String journal2, boolean expected) {
		Publication p1 = new Publication();
		Publication p2 = new Publication();
		IOService.addNormalizedJournal(journal1, p1, "T2");
		IOService.addNormalizedJournal(journal2, p2, "T2");

		boolean result = ComparisonService.compareJournals(p1, p2);
		assertThat(result).isEqualTo(expected);
	}

	void compareStartPagesOrDoisTest() {
		fail("Not implemented yet");
	}

	@ParameterizedTest(name = "{index}: compareTitles({0}, {1})={2}")
	@MethodSource("titleArgumentProvider")
	void compareTitlesTest(String title1, String title2, boolean expected) {
		Publication p1 = new Publication();
		Publication p2 = new Publication();
		IOService.addNormalizedTitle(title1, p1);
		IOService.addNormalizedTitle(title2, p2);

		boolean result = ComparisonService.compareTitles(p1, p2);
		assertThat(result).as("Not equal: '%s' and '%s'", title1, title2).isEqualTo(expected);
	}

	static Stream<Arguments> journalArgumentProvider() {
		// @formatter:off
			return Stream.of(
				// made by Roo Code
				arguments("Journal of Medicine", "Journal of Medicine", true),
				arguments("J. Med.", "Journal of Medicine", true),
				arguments("Different Journal", "Another Journal", false),
				// own tests
				arguments("Ann Intern Med", "ANNALS OF INTERNAL MEDICINE", true),
				arguments("ARTHROSCOPY-THE JOURNAL OF ARTHROSCOPIC AND RELATED SURGERY", "Arthroscopy : the journal of arthroscopic & related surgery : official publication of the Arthroscopy Association of North America and the International Arthroscopy Association", true),
				// abbreviations which can't be matched
				arguments("Un med canada", "Union Med Can", false)
			);
			// @formatter:on
	}

	static Stream<Arguments> issnArgumentProvider() {
		// @formatter:off
			return Stream.of(
				// made by Roo Code
				arguments("1234-5678", "1234-5678", true),
				arguments("1234-5678", "8765-4321", false),
				arguments("1234-567X", "1234-567x", true),
				// own tests
				arguments("0000-0000 1111-1111", "2222-2222 1111-1111", true),
				arguments("0000-0000", "1111-1111 2222-2222", false),
				arguments("1234-568x (Print)", "1234568X (ISSN)", true)
			);
			// @formatter:on
	}

	static Stream<Arguments> titleArgumentProvider() {
		// @formatter:off
		return Stream.of(
			// made by Roo Code
			arguments("This is a test title", "This is a test title", true),
			arguments("Another test title", "A different test title", false),
			arguments("Title with sufficient pages", "Title with sufficient pages", true),
			arguments("Reply to a title", "Some other title", false) // isReply should make it true
			// own tests
		);
		// @formatter:on
	}
}