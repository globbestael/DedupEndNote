package edu.dedupendnote.validation.experiments;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.integration.AbstractIntegrationTest;
import edu.dedupendnote.services.comparison.AuthorThresholds;
import edu.dedupendnote.services.comparison.DefaultAuthorsComparisonService;
import edu.dedupendnote.services.comparison.DefaultJournalComparisonService;
import edu.dedupendnote.services.comparison.DefaultPagesComparisonService;
import edu.dedupendnote.services.comparison.DefaultTitleComparisonService;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.BibliographicItemReader;
import edu.dedupendnote.services.BibliographicItemWriter;
import edu.dedupendnote.services.EnrichmentService;
import edu.dedupendnote.services.comparison.FieldComparators;
import edu.dedupendnote.services.UtilitiesService;
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
	BibliographicItemReader bibliographicItemReader;

	@BeforeEach
	void initTestDir() {
		testDir = baseDir.resolve("validation");
	}

	@Test
	void higherAuthorThresholdsReduceSensitivityAndIncreaseSpecificity() throws IOException {
		// SRA2_Haematology: 222 TP, 6 FN, 1186 TN, 1 FP — has both TN and FP, making
		// specificity meaningful and demonstrating the experimental engine's trade-off.
		ValidationResult baseline = new ValidationResult("SRA2_Haematology", 222, 6, 1186, 1, 300L, 106);

		Path inputPath = testDir.resolve("SRA2/Haematology.txt");
		Path outputPath = testDir.resolve("SRA2/Haematology_experimental_to_validate.txt");
		Path truthPath = testDir.resolve("SRA2/Haematology_TRUTH.txt");
		deleteDerivedOutputs(inputPath);

		// Threshold == 1.0 (the max JWS score) — similarity > 1.0 is never true, so no author
		// match ever succeeds; sensitivity drops to 0%, specificity reaches 100%.
		AuthorThresholds noMatchThresholds = new AuthorThresholds(1.0, 1.0, 1.0);
		FieldComparators cs = new FieldComparators(new DefaultAuthorsComparisonService(noMatchThresholds),
				new DefaultTitleComparisonService(), new DefaultJournalComparisonService(),
				new DefaultPagesComparisonService());
		EnrichmentService enrichmentService = new EnrichmentService();
		DeduplicationService expService = new DeduplicationService(cs, new BibliographicItemReader(),
				new BibliographicItemWriter(enrichmentService), enrichmentService);

		long start = System.currentTimeMillis();
		expService.deduplicateOneFile(inputPath, DeduplicationMode.MARK, message -> {
		});
		Path markPath = UtilitiesService.createPath(inputPath, DeduplicationMode.MARK.filenameSuffix(), "txt");
		List<BibliographicItem> bibliographicItems = bibliographicItemReader.readBibliographicItems(markPath,
				message -> {
				}, /* includeLabelField= */ true);
		long duration = System.currentTimeMillis() - start;

		ValidationResult expResult = validationService.checkResults("SRA2_Haematology_experimental", inputPath,
				outputPath, truthPath, bibliographicItems, duration, /* withTracing= */ false, expService);

		System.err.println("Baseline:   " + baseline);
		System.err.println("Experiment: " + expResult);

		// Higher author thresholds miss more duplicates (lower sensitivity) …
		assertThat(expResult.getSensitivity()).isLessThan(baseline.getSensitivity());
		// … but produce fewer false positives (higher or equal specificity).
		assertThat(expResult.getSpecificity()).isGreaterThanOrEqualTo(baseline.getSpecificity());
	}

}
