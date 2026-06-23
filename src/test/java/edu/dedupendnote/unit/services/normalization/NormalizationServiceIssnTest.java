package edu.dedupendnote.unit.services.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.dedupendnote.services.normalization.NormalizationService;

class NormalizationServiceIssnTest {

	@Test
	void normalizeInputIssns_valid() {
		String issn = "0002-9343 (Print) 00029342 (Electronic) 0-9752298-0-X (ISBN) xxxxXXXX (all X-es)";

		Set<String> issns = NormalizationService.normalizeInputIssns(issn).issns();

		assertThat(issns).hasSize(3).containsAll(Set.of("00029343", "00029342", "XXXXXXXX"));
	}

	@Test
	void normalizeInputIssns_valid2() {
		String issn = "0001-4079 (Print) 0001-4079";

		Set<String> issns = NormalizationService.normalizeInputIssns(issn).issns();

		assertThat(issns).hasSize(1).containsAll(Set.of("00014079"));
	}

	@Test
	void normalizeInputIssns_nonvalid() {
		String issn = "a002-9343 (with letter) 00029342X (11 characters) 0-12-34567890x (12 characters)";

		Set<String> issns = NormalizationService.normalizeInputIssns(issn).issns();

		assertThat(issns).isEmpty();
	}

}
