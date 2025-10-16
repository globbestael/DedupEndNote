package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.services.NormalizationService;

@TestConfiguration
class NormalizationServiceTest {

	NormalizationService normalizationService = new NormalizationService();

	@ParameterizedTest(name = "{index}: normalizeTitle({0})={1}")
	@MethodSource("titleArgumentProvider")
	void normalizeTitleTest(String input, String expected) {
		String result = NormalizationService.normalizeTitle(input);
		assertThat(result).isEqualTo(expected);
	}

	@ParameterizedTest(name = "{index}: normalizeJournal({0})={1}")
	@MethodSource("journalArgumentProvider")
	void normalizeJournalTest(String input, String expected) {
		String result = NormalizationService.normalizeJournal(input);
		assertThat(result).isEqualTo(expected);
	}

	static Stream<Arguments> journalArgumentProvider() {
		// @formatter:off
		return Stream.of(
				arguments("The Journal of Medicine", "Journal of Medicine"),
				arguments("Jpn. J. Med.", "Japanese J Med"),
				arguments("My Journal & Co.", "My Journal Co"),
				arguments("Journal with (Parentheses)", "Journal with"),
				arguments("Journal with: A Subtitle", "Journal with"),
				arguments("Langenbeck's Archives of Surgery", "Langenbecks Archives of Surgery"),
				arguments("Annales d'Urologie", "Annales d Urologie"),
				arguments("Zbl. Chir.", "Zentralbl Chir"),
				arguments("Jbr-btr", "JBR BTR"),
				arguments("Rofo", "Rofo"),
				arguments("Gastro-Enterology", "Gastroenterology")
		);
		// @formatter:on
	}

	static Stream<Arguments> titleArgumentProvider() {
		// @formatter:off
		return Stream.of(
				arguments("This is a simple title.", "this is a simple title"),
				arguments("Title with [brackets] and (parentheses)", "title with brackets and parentheses"),
				arguments("  Title with   extra whitespace  ", "title with extra whitespace"),
				arguments("Title with \"quotes\" and <tags>", "title with quotes and"),
				arguments("A Title Starting With An Article", "title starting with an article"),
				arguments("The Title with Mixed Case", "title with mixed case"),
				arguments("Title with hyphenated-words", "title with hyphenatedwords"),
				arguments("Title with numbers 123 and symbols!@#", "title with numbers 123 and symbols"),
				arguments("Español y François y Deutsch", "espanol y francois y deutsch"),
				arguments("Title with (Japanese)", "title with"),
				arguments("Title with (Japanese text)", "title with")
		);
		// @formatter:on
	}
}