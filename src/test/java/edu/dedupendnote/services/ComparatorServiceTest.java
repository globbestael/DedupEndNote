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
	NormalizationService normalizationService = new NormalizationService();

	@ParameterizedTest(name = "{index}: compareTitles({0}, {1})={2}")
	@MethodSource("titleArgumentProvider")
	void compareTitlesTest(String title1, String title2, boolean expected) {
		Publication p1 = new Publication();
		p1.addTitles(title1, normalizationService);
		Publication p2 = new Publication();
		p2.addTitles(title2, normalizationService);

		boolean result = comparatorService.compareTitles(p1, p2);
		assertThat(result).isEqualTo(expected);
	}

	static Stream<Arguments> titleArgumentProvider() {
		// @formatter:off
		return Stream.of(
				arguments("This is a test title", "This is a test title", true),
				arguments("Another test title", "A different test title", false),
				arguments("Title with sufficient pages", "Title with sufficient pages", true),
				arguments("Reply to a title", "Some other title", true) // isReply should make it true
		);
		// @formatter:on
	}
}