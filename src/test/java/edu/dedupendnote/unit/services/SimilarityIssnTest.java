package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.services.DefaultJournalComparisonService;
import edu.dedupendnote.services.NormalizationService;

// See SimilarityJournalTest for a TODO on renaming Similarity*Test files.
class SimilarityIssnTest {

	/*
	 * full comparison for normalised ISSNs/ISBNs: positive
	 */
	@ParameterizedTest(name = "{index}: compareIssns({0}, {1})")
	@MethodSource("fullPositiveArgumentProvider")
	void fullPositiveTest(String issn1, String issn2) {
		BibliographicItem p1 = new BibliographicItem();
		BibliographicItem p2 = new BibliographicItem();
		p1.getIssns().addAll(NormalizationService.normalizeInputIssns(issn1).issns());
		p2.getIssns().addAll(NormalizationService.normalizeInputIssns(issn2).issns());

		assertThat(DefaultJournalComparisonService.compareIssns(p1, p2, false))
				.as("ISSNs are NOT matching: '%s' and '%s'", issn1, issn2).isTrue();
	}

	/*
	 * full comparison for normalised ISSNs/ISBNs: negative
	 */
	@ParameterizedTest(name = "{index}: compareIssns({0}, {1})")
	@MethodSource("fullNegativeArgumentProvider")
	void fullNegativeTest(String issn1, String issn2) {
		BibliographicItem p1 = new BibliographicItem();
		BibliographicItem p2 = new BibliographicItem();
		p1.getIssns().addAll(NormalizationService.normalizeInputIssns(issn1).issns());
		p2.getIssns().addAll(NormalizationService.normalizeInputIssns(issn2).issns());

		assertThat(DefaultJournalComparisonService.compareIssns(p1, p2, false))
				.as("ISSNs are matching: '%s' and '%s'", issn1, issn2).isFalse();
	}

	static Stream<Arguments> fullPositiveArgumentProvider() {
		// @formatter:off
		return Stream.of(
			arguments("1234-5678", "1234-5678"),
			arguments("1234-567X", "1234-567x"),
			arguments("0000-0000 1111-1111", "2222-2222 1111-1111"),
			arguments("1234-568x (Print)", "1234568X (ISSN)")
		);
		// @formatter:on
	}

	static Stream<Arguments> fullNegativeArgumentProvider() {
		// @formatter:off
		return Stream.of(
			arguments("1234-5678", "8765-4321"),
			arguments("0000-0000", "1111-1111 2222-2222")
		);
		// @formatter:on
	}
}
