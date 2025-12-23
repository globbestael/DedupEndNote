package edu.dedupendnote.services;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

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

}