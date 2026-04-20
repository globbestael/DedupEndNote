package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.SequencedSet;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import edu.dedupendnote.services.IOService;
import edu.dedupendnote.services.NormalizationService;
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.TitleRecord;

class NormalizationServiceTitleTest {

	@ParameterizedTest(name = "[{index}] Input: \"{0}\" -> No Title")
	@ValueSource(strings = { "not available", "[not available]", "untitled" })
	void normalizeInputTitles_whenNoTitle_shouldReturnEmpty(String input) {
		TitleRecord result = NormalizationService.normalizeInputTitles(input);

		assertThat(result.originalTitle()).isNull();
		assertThat(result.titles()).isEmpty();
	}

	@ParameterizedTest(name = "[{index}] Input: \"{0}\"")
	@MethodSource("titlesProviderRetraction")
	void normalizeInputTitles_withRetraction_shouldExtractOriginalTitle(String input, String expectedNormalized) {
		TitleRecord result = NormalizationService.normalizeInputTitles(input);

		assertThat(result.originalTitle()).isEqualTo(input);
		assertThat(result.titles()).containsExactly(expectedNormalized);
	}

	@ParameterizedTest(name = "[{index}] Input: \"{0}\"")
	@MethodSource("titlesProviderMultiple")
	void normalizeInputTitles_withMultipleResults_shouldCreateVariants(String input, List<String> expectedTitles) {
		TitleRecord result = NormalizationService.normalizeInputTitles(input);

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(result.originalTitle()).isNull();
		softAssertions.assertThat(result.titles()).containsExactlyInAnyOrderElementsOf(expectedTitles);
		softAssertions.assertAll();
	}

	static Stream<Arguments> titlesProviderMultiple() {
		// @formatter:off
		return Stream.of(
			// First with 1 result title 
			arguments(
				"This is the main title (Reprinted from somewhere)", 
				List.of("this is the main title")),
			arguments(
				"Reprint of: The original article title", 
				List.of("original article title")),
			arguments(
				"A study on Product(R) and its effects", 
				List.of("study on product and its effects")),
			arguments(
				"Editorial: A discussion on recent events", 
				List.of("discussion on recent events")),
			arguments(
				"A \"fancy\" title with <HTML> tags and (parentheses) and some Japanese text (Japanese).",
				List.of("fancy title with tags and parentheses and some japanese text")),
			// Then with multple result titles
			arguments(
				"10 things you should know about testing",
				List.of(
					"10 things you should know about testing", 
					"things you should know about testing")),
			arguments(
				"This is a very long title that is definitely more than fifty characters long: and this is a very long subtitle that is also more than fifty characters long.",
				List.of(
					"this is a very long title that is definitely more than fifty characters long and this is a very long subtitle that is also more than fifty characters long",
					"this is a very long title that is definitely more than fifty characters long",
					"and this is a very long subtitle that is also more than fifty characters long")),
			arguments(
				"Comparing A vs B is a very long title that is definitely more than fifty characters long: and this is a very long subtitle that is also more than fifty characters long.",
				List.of(
					"comparing a vs b is a very long title that is definitely more than fifty characters long and this is a very long subtitle that is also more than fifty characters long",
					"comparing a vs b is a very long title that is definitely more than fifty characters long",
					"and this is a very long subtitle that is also more than fifty characters long")),
			arguments( 
				"Restructuring consciousness: The psychedelic state in light of integrated information theory",
				List.of(
					"restructuring consciousness the psychedelic state in light of integrated information theory",
					"restructuring consciousness", 
					"psychedelic state in light of integrated information theory")),
			arguments( // hyphen between letters dioes NOT split the title
				"Restructuring consciousness-The psychedelic state in light of integrated information theory",
				List.of(
					"restructuring consciousnessthe psychedelic state in light of integrated information theory")),
			arguments( // double hyphen splits
				"Portal vein obstruction--epidemiology, pathogenesis, natural history, prognosis and treatment",
				List.of(
					"portal vein obstruction epidemiology pathogenesis natural history prognosis and treatment",
					"portal vein obstruction",
					"epidemiology pathogenesis natural history prognosis and treatment"
				)),
			arguments( // " -" does NOT split with preceding "of|...". Original title had a Greek kappa
				"Role of -opioid receptor activation in pharmacological preconditioning of swine",
				List.of(
					"role of opioid receptor activation in pharmacological preconditioning of swine"
				)),
			arguments( // " -" does split in normal cases
				"Restructuring consciousness -The psychedelic state in light of integrated information theory",
				List.of(
					"restructuring consciousness the psychedelic state in light of integrated information theory",
					"restructuring consciousness",
					"psychedelic state in light of integrated information theory"
				))
		);
		// @formatter:on
	}

	static Stream<Arguments> titlesProviderRetraction() {
		// @formatter:off
		return Stream.of(
				arguments("Some important research finding (Retracted Article)", "some important research finding"),
				arguments("Retracted article: The actual title of the paper", "actual title of the paper"));
		// @formatter:on
	}

	@ParameterizedTest(name = "{index}: normalizeTitle({0})={1}")
	@MethodSource("normalizeTitleArgumentProvider")
	void normalizeTitleTest(String input, String expected) {
		String result = NormalizationService.normalizeTitle(input);
		assertThat(result).isEqualTo(expected);
	}

	static Stream<Arguments> normalizeTitleArgumentProvider() {
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
				arguments("Title with (Japanese text)", "title with"),
				arguments("11 beta-Hydroxysteroid Dehydrogenases and Hypertension in the Metabolic Syndrome", "11 betahydroxysteroid dehydrogenases and hypertension in the metabolic syndrome")
		);
		// @formatter:on
	}

	@Test
	void testTitleSplitter() {
		Publication publication = new Publication();
		String t1 = "Severe deficiency of the specific von Willebrand factor-cleaving protease";
		String t2 = "ADAMTS 13 activity in a subgroup of children with atypical hemolytic uremic syndrome";
		IOService.addNormalizedTitle(t1 + ": " + t2, publication);
		SequencedSet<String> titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).hasSize(3);

		publication.getTitles().clear();
		IOService.addNormalizedTitle(t1.substring(0, 10) + ": " + t2, publication);
		titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).as("First part smaller than 50, no split").hasSize(1);

		publication.getTitles().clear();
		IOService.addNormalizedTitle(t1 + ": " + t2.substring(0, 10), publication);
		titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).as("Second part smaller than 50, no split").hasSize(1);

		publication.getTitles().clear();
		IOService.addNormalizedTitle(t1.substring(0, 10) + ": " + t2.substring(0, 10), publication);
		titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).as("Both parts smaller than 50, no split").hasSize(1);

		publication.getTitles().clear();
		IOService.addNormalizedTitle(t1 + ": " + t2.substring(0, 10) + ": " + t2.substring(11), publication);
		titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).as("Second part has embedded colon").hasSize(3);
	}

}
