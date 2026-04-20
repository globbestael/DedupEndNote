package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import edu.dedupendnote.services.NormalizationService;
import edu.dedupendnote.domain.AuthorRecord;

class NormalizationServiceAuthorTest {

	@ParameterizedTest(name = "{index}: normalizeAuthor({0})={1}, {2}")
	@MethodSource("authorArgumentProvider")
	void normalizeAuthorTest(String input, AuthorRecord expected) {
		AuthorRecord result = NormalizationService.normalizeInputAuthors(input);
		assertThat(result).isEqualTo(expected);
	}

	static Stream<Arguments> authorArgumentProvider() {
		// @formatter:off
		return Stream.of(
			arguments("Smith, Arthur", new AuthorRecord("Smith A", "Smith A", false)),
			arguments("Smith, Arthur J. C.", new AuthorRecord("Smith AJC", "Smith AJC", false)),
			arguments("Smith Jones, Arthur", new AuthorRecord("Smith Jones A", "Jones AS", true))
		);
		// @formatter:on
	}

}
