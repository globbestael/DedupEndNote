package edu.dedupendnote.validation.experiments;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.integration.AbstractIntegrationTest;
import edu.dedupendnote.services.AuthorThresholds;
import edu.dedupendnote.services.ComparisonService;
import edu.dedupendnote.services.DefaultAuthorsComparisonService;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.IOService;
import edu.dedupendnote.validation.domain.ValidationResult;
import edu.dedupendnote.validation.services.ValidationService;
import lombok.extern.slf4j.Slf4j;

/*
 * Experiments comparing alternative AuthorsComparisonService implementations against the production baseline.
 *
 * The production baseline is read from a ValidationResult constant (not recomputed) to avoid
 * running deduplication twice. Sensitivity/specificity are compared rather than raw counts.
 */
@Slf4j
class AuthorExperimentsTests extends AbstractIntegrationTest {

	@Autowired
	ValidationService validationService;

	@Autowired
	IOService ioService;

	@Test
	void higherAuthorThresholdsReduceSensitivityAndIncreaseSpecificity() throws IOException {
		// SRA2_Haematology: 222 TP, 6 FN, 1186 TN, 1 FP — has both TN and FP, making
		// specificity meaningful and demonstrating the experimental engine's trade-off.
		ValidationResult baseline = new ValidationResult("SRA2_Haematology", 222, 6, 1186, 1, 300L, 106);

		String subdir = testDir + "/SRA2/";
		String inputFile = subdir + "Haematology.txt";
		String markFile  = inputFile + "_mark.txt";
		String outputFile = subdir + "Haematology_experimental_to_validate.txt";
		String truthFile  = subdir + "Haematology_TRUTH.txt";

		// Threshold == 1.0 (the max JWS score) — similarity > 1.0 is never true, so no author
		// match ever succeeds; sensitivity drops to 0%, specificity reaches 100%.
		DeduplicationService expService = new DeduplicationService(new ComparisonService());
		AuthorThresholds noMatchThresholds = new AuthorThresholds(1.0, 1.0, 1.0);
		expService.setAuthorsComparisonService(new DefaultAuthorsComparisonService(noMatchThresholds));

		long start = System.currentTimeMillis();
		expService.deduplicateOneFile(inputFile, markFile, /* markMode= */ true, message -> {});
		List<Publication> publications = ioService.readPublications(markFile, message -> {}, /* includeLabelField= */ true);
		long duration = System.currentTimeMillis() - start;

		ValidationResult expResult = validationService.checkResults(
				"SRA2_Haematology_experimental", inputFile, outputFile, truthFile,
				publications, duration, /* withTracing= */ false, expService);

		// Higher author thresholds miss more duplicates (lower sensitivity) …
		assertThat(expResult.getSensitivity()).isLessThan(baseline.getSensitivity());
		// … but produce fewer false positives (higher or equal specificity).
		assertThat(expResult.getSpecificity()).isGreaterThanOrEqualTo(baseline.getSpecificity());

		System.err.println("Baseline:   " + baseline);
		System.err.println("Experiment: " + expResult);
	}

}
