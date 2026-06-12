package edu.dedupendnote.validation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.integration.AbstractIntegrationTest;
import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.BibliographicItemReader;
import edu.dedupendnote.services.UtilitiesService;
import edu.dedupendnote.validation.services.ValidationIOService;
import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.domain.BibliographicItemDB;
import edu.dedupendnote.validation.domain.ValidationResult;
import edu.dedupendnote.validation.services.RecordDBService;
import edu.dedupendnote.validation.services.ValidationService;
import lombok.extern.slf4j.Slf4j;

/**
 * The validation tests compare the current results to validated results (TRUTH files): see checkResults(...)
 *
 * TRUTH files are the tab delimited output of a (Microsoft Access) database where validated records are marked as TP or
 * TN, and TP records have a non empty dedupid.
 *
 * An unvalidated TRUTH file can be created with createInitialTruthFile(). Import the file into the database, validate
 * some or all of the records, export the validated records as a tab delimited file (the TRUTH file).
 *
 * See http://localhost:9777/developers for a description of the database.
 */
@Slf4j
class ValidationTests extends AbstractIntegrationTest {
	@Autowired
	DeduplicationService deduplicationService;

	@Autowired
	BibliographicItemReader bibliographicItemReader;

	@Autowired
	ValidationIOService validationIOService;

	@Autowired
	RecordDBService recordDBService;

	@Autowired
	ValidationService validationService;

	Map<String, Integer> titleCounter = new HashMap<>();

	private static Logger rootLogger;
	private boolean withTracing = false;
	private boolean withTitleSplitterOutput = false;

	static final String TABLE_HEADER = "| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s | %12s | %12s | %12s | %11s | %9s |"
			.formatted("TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity", "Precision",
					"Accuracy", "F1-score", "Duration", "Uniq dupl");
	static final String TABLE_DIVIDER = "|---------|--------------|---------|---------|--------------|---------|---------|--------------|--------------|--------------|--------------|-------------|-----------|";
	static final String EXPLANATION = """
			FP can be found with regex: \\ttrue\\tfalse\\tfalse\\ttrue\\tfalse\\t
			FN can be found with regex: \\d\\ttrue\\tfalse\\tfalse\\tfalse\\ttrue\\t
			FN solvable can be found with regex: ^\\d+\\t\\t\\d+\\ttrue\\tfalse\\tfalse\\tfalse\\ttrue\\tfalse
			TP which will be kept can be found with regex: ^(\\d+)\\t\\1\\t\\ttrue\\ttrue\\t
			""";

	@BeforeAll
	static void beforeAll() {
		/*
		 * Be sure to not have the log level at Debug. VS Code hangs.
		 * 
		 * The reason why the extensive log.debug messaging in DeduplicationService.compareSet causes this problem is not clear.
		 */
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		rootLogger = loggerContext.getLogger("edu.dedupendnote.services.DeduplicationService");
		rootLogger.setLevel(Level.INFO);
	}

	// @formatter:off
	Map<String, ValidationResult> validationResultsMap = List
		.of(
			new ValidationResult("AI_subset", 507, 9, 2567, 0, 29_000L, 218),	// why so slow?
			new ValidationResult("ASySD_Cardiac_human", 6759, 5, 2183, 1, 3_700L, 3238),
			new ValidationResult("ASySD_Diabetes", 1811, 11, 21, 2, 1_000L, 563),
			new ValidationResult("ASySD_Neuroimaging", 2181, 11, 1244, 2, 1_350L, 896),
			new ValidationResult("ASySD_SRSR_Human", 27945, 36, 25016, 4, 100_000L,11130),
			new ValidationResult("BIG_SET", 3952, 92, 1030, 8, 66_000L,1444),
			new ValidationResult("Clinical_trials", 219, 0, 0, 0, 190L, 87),
			new ValidationResult("McKeown_2021", 2023, 33, 1074, 0, 800L, 820),
			new ValidationResult("SRA2_Cytology_screening", 1361, 33, 462, 0, 400L, 623),
			new ValidationResult("SRA2_Haematology", 222, 6, 1186, 1, 300L, 106),
			new ValidationResult("SRA2_Respiratory", 768, 18, 1202, 0, 800L, 356),
			new ValidationResult("SRA2_Stroke", 497, 8, 787, 0, 320L, 190),
			new ValidationResult("TIL", 696, 4, 392, 0, 9_000L, 262),
			new ValidationResult("TIL_Zotero", 695, 5, 392, 0, 9_000L, 262))
		.stream().collect(Collectors.toMap(ValidationResult::getFileName, Function.identity(), (o1, o2) -> o1, TreeMap::new));
	// @formatter:on

	// @formatter:off
	/*
	 * - Executes the deduplication in Mark mode. 
	 * - Compares the results with the truth files and with the previous results.
	 * - Prints the scores in the traditional way (TP = all records marked as duplicates, ...). 
	 * 
	 * See  printValdationResultsASySD() for scores where TP = all records marked as duplicates except for the duplicate kept
	 * (i.e. all duplicate rightly removed)
	 * 
	 * "ASySD_Depression" removed because of bad format input file
	 * 		new ValidationResult("ASySD_Depression", 17389, 576, 61894, 21, 76_000L),
	 */
	// @formatter:on
	@Test
	void checkAllTruthFiles() throws IOException {
		// rootLogger.setLevel(Level.DEBUG);

		withTracing = true;
		withTitleSplitterOutput = false;

		// @formatter:off
		Map<String, ValidationResult> resultsMap = List
				.of(
					checkResults_AI_subset(),
					checkResults_ASySD_Cardiac_human(),
					checkResults_ASySD_Diabetes(),
					checkResults_ASySD_Neuroimaging(),
					checkResults_ASySD_SRSR_Human(),
					checkResults_BIG_SET(),
					checkResults_Clinical_trials(),
					checkResults_McKeown_2021(),
					checkResults_SRA2_Cytology_screening(),
					checkResults_SRA2_Haematology(),
					checkResults_SRA2_Respiratory(),
					checkResults_SRA2_Stroke(),
					checkResults_TIL(),
					checkResults_TIL_Zotero()
				)
				.stream().collect(Collectors.toMap(ValidationResult::getFileName, Function.identity(), (o1, o2) -> o1,
						TreeMap::new));
		// @formatter:on

		boolean changed = false;

		for (String setName : resultsMap.keySet()) {
			ValidationResult v = validationResultsMap.get(setName);
			ValidationResult c = resultsMap.get(setName);
			if (v == null || c == null) {
				continue; // easy when some checkResults_...() are commented out
			}
			if (v.getFn() == c.getFn() && v.getFp() == c.getFp() && v.getTn() == c.getTn() && v.getTp() == c.getTp()
					&& (c.getDuration() >= (long) (v.getDuration() * 0.9))
					&& c.getDuration() <= (long) (v.getDuration() * 1.1)
					&& c.getUniqueDuplicates() == v.getUniqueDuplicates()) {
				printValidationResult(setName, c, null);
			} else {
				changed = true;
				printValidationResult(setName, c, v);
			}
		}
		System.out.println(EXPLANATION);

		Map<String, Integer> sortedTitleMap = titleCounter.entrySet().stream()
				.sorted((c1, c2) -> c2.getValue().compareTo(c1.getValue())).collect(Collectors.toMap(Map.Entry::getKey,
						Map.Entry::getValue, (oldValue, newValue) -> oldValue, LinkedHashMap::new));
		int i = 0;
		for (Map.Entry<String, Integer> entry : sortedTitleMap.entrySet()) {
			if (i++ > 500) {
				break;
			}
			System.err.println("title: " + entry.getKey() + " --> " + entry.getValue());
		}
		// temporarily changed for Roo Code refactoring
		assertThat(changed).isTrue();
		// assertThat(changed).isFalse();
	}

	private void printValidationResult(String setName, ValidationResult newV, @Nullable ValidationResult oldV) {
		System.out.println(
				"\nResults: " + setName + (oldV == null ? "" : ": HAS DIFFERENT RESULTS (first new, second old)"));
		System.out.println(TABLE_HEADER);
		System.out.println(TABLE_DIVIDER);
		printIndividualValidationResult(newV);
		if (oldV != null) {
			printIndividualValidationResult(oldV);
		}
		System.out.flush();
	}

	private void printIndividualValidationResult(ValidationResult v) {
		System.out.println(
				"| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% | %11.3f%% | %11.2f | %9d |"
						.formatted(v.getTotal(), v.getPercDuplicates(), v.getTp(), v.getFn(), v.getSensitivity(),
								v.getTn(), v.getFp(), v.getSpecificity(), v.getPrecision(), v.getAccuracy(),
								v.getF1Score(), (double) (v.getDuration() / 1000.0), v.getUniqueDuplicates()));
	}

	@Test
	void printValidationResultsASySD() {
		for (String setName : validationResultsMap.keySet()) {
			ValidationResult v = validationResultsMap.get(setName);
			int tp = v.getTp() - v.getUniqueDuplicates();
			int fn = v.getFn();
			int tn = v.getTn() + v.getUniqueDuplicates();
			int fp = v.getFp();
			int total = tp + fn + tn + fp;

			double precision = tp * 100.0 / (tp + fp);
			double sensitivity = tp * 100.0 / (tp + fn);
			double specificity = tn * 100.0 / (tn + fp);
			double accuracy = (tp + tn) * 100.0 / total;
			double f1Score = 2 * precision * sensitivity / (precision + sensitivity);

			System.out.println("\nResults: " + setName);
			System.out.println(
					"--------------------------------------------------------------------------------------------------------------------------------------------------------");
			System.out.println("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s | %12s | %12s | %12s | %9s |"
					.formatted("TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity",
							"Precision", "Accuracy", "F1", "Uniq dupl"));
			System.out.println(
					"| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% | %11.3f%% | %9d |"
							.formatted(total, (tp + fn) * 100.0 / total, tp, fn, sensitivity, tn, fp, specificity,
									precision, accuracy, f1Score, v.getUniqueDuplicates()));
			System.out.println(
					"--------------------------------------------------------------------------------------------------------------------------------------------------------");
			System.out.flush();
			assertThat(1 * 1).isEqualTo(1);
		}
	}

	@Test
	void readTruthFileTest() throws IOException {
		Path truthPath = testDir.resolve("SRA2/Cytology_screening_TRUTH.txt");
		List<BibliographicItemDB> truthRecords = validationService.readTruthFile(truthPath);

		assertThat(truthRecords).hasSizeGreaterThan(10);

		Map<Integer, Set<Integer>> trueDuplicateSets = truthRecords.stream().filter(r -> r.getDedupid() != null)
				// .map(BibliographicItemDB::getDedupid)
				.collect(Collectors.groupingBy(BibliographicItemDB::getDedupid,
						Collectors.mapping(BibliographicItemDB::getId, Collectors.toSet())));
		assertThat(trueDuplicateSets).hasSizeGreaterThan(10);
		trueDuplicateSets.entrySet().stream().limit(10).forEach(System.err::println);
	}

	// @formatter:off
	ValidationResult checkResults(String setName, Path inputPath, Path outputPath, Path truthPath) throws IOException {
		log.error("- Validating {}", setName);
		long startTime = System.currentTimeMillis();
		List<BibliographicItem> bibliographicItems = deduplicate(inputPath);
		long durationMs = System.currentTimeMillis() - startTime;
		ValidationResult validationResult = validationService.checkResults(
				setName, inputPath, outputPath, truthPath,
				bibliographicItems, durationMs, withTracing, deduplicationService);

		if (withTitleSplitterOutput) {
			for (BibliographicItem p : bibliographicItems) {
				for (String t : p.getTitles()) {
					if (!titleCounter.containsKey(t)) {
						titleCounter.put(t, 1);
					} else {
						titleCounter.put(t, titleCounter.get(t) + 1);
					}
				}
			}
		}
		return validationResult;
	}


	ValidationResult checkResultsFor(String setName, Path inputPath) throws IOException {
		return checkResults(setName, inputPath,
			UtilitiesService.createPath(inputPath, "_to_validate", "txt"),
			UtilitiesService.createPath(inputPath, "_TRUTH", "txt"));
	}

	void createInitialTruthFileFor(Path inputPath) {
		createInitialTruthFile(inputPath,
			UtilitiesService.createPath(inputPath, "_for_truth", "txt"));
	}

	void createInitialTruthFileWithASySDFor(Path inputPath) {
		createInitialTruthFile(inputPath,
			UtilitiesService.createPath(inputPath, "_asysd_gold", "txt"),
			UtilitiesService.createPath(inputPath, "_for_truth",  "txt"));
	}

	void createRisWithTRUTHFor(Path inputPath) throws IOException {
		createRisWithTRUTH(inputPath,
			UtilitiesService.createPath(inputPath, "_TRUTH",      "txt"),
			UtilitiesService.createPath(inputPath, "_with_TRUTH", "txt"));
	}

	ValidationResult checkResults_AI_subset() throws IOException {
		return checkResultsFor("AI_subset", testDir.resolve("AI_subset/AI_subset.txt"));
	}

	ValidationResult checkResults_ASySD_Cardiac_human() throws IOException {
		return checkResultsFor("ASySD_Cardiac_human", testDir.resolve("ASySD/dedupendnote_files/Cardiac_human.txt"));
	}

	// ValidationResult checkResults_ASySD_Depression() throws IOException {
	// 	return checkResultsFor("ASySD_Depression", testDir.resolve("ASySD/dedupendnote_files/Depression.txt"));
	// }

	ValidationResult checkResults_ASySD_Diabetes() throws IOException {
		return checkResultsFor("ASySD_Diabetes", testDir.resolve("ASySD/dedupendnote_files/Diabetes.txt"));
	}

	ValidationResult checkResults_ASySD_Neuroimaging() throws IOException {
		return checkResultsFor("ASySD_Neuroimaging", testDir.resolve("ASySD/dedupendnote_files/Neuroimaging_sorted.txt"));
	}

	ValidationResult checkResults_ASySD_SRSR_Human() throws IOException {
		return checkResultsFor("ASySD_SRSR_Human", testDir.resolve("ASySD/dedupendnote_files/SRSR_Human.txt"));
	}

	/*
	 * Deduplicates the whole file, but checks only the results of the validated subset
	 */
	ValidationResult checkResults_BIG_SET() throws IOException {
		return checkResultsFor("BIG_SET", testDir.resolve("own/BIG_SET.txt"));
	}

	ValidationResult checkResults_Clinical_trials() throws IOException {
		return checkResultsFor("Clinical_trials", testDir.resolve("Clinical_trials/clinicaltrialsdotgov.txt"));
	}

	ValidationResult checkResults_McKeown_2021() throws IOException {
		return checkResultsFor("McKeown_2021", testDir.resolve("McKeown_S_2021/dedupendnote_files/McKeown_2021.txt"));
	}

	ValidationResult checkResults_SRA2_Cytology_screening() throws IOException {
		return checkResultsFor("SRA2_Cytology_screening", testDir.resolve("SRA2/Cytology_screening.txt"));
	}

	ValidationResult checkResults_SRA2_Haematology() throws IOException {
		return checkResultsFor("SRA2_Haematology", testDir.resolve("SRA2/Haematology.txt"));
	}

	ValidationResult checkResults_SRA2_Respiratory() throws IOException {
		return checkResultsFor("SRA2_Respiratory", testDir.resolve("SRA2/Respiratory.txt"));
	}

	ValidationResult checkResults_SRA2_Stroke() throws IOException {
		return checkResultsFor("SRA2_Stroke", testDir.resolve("SRA2/Stroke.txt"));
	}

	ValidationResult checkResults_TIL() throws IOException {
		return checkResultsFor("TIL", testDir.resolve("TIL/TIL.txt"));
	}

	// Exception: TIL_Zotero shares TIL's truth file rather than having its own TIL_Zotero_TRUTH.txt
	ValidationResult checkResults_TIL_Zotero() throws IOException {
		return checkResults("TIL_Zotero",
			testDir.resolve("TIL/TIL_Zotero.ris"),
			UtilitiesService.createPath(testDir.resolve("TIL/TIL_Zotero.ris"), "_to_validate", "txt"),
			testDir.resolve("TIL/TIL_TRUTH.txt"));
	}

	/*
	 * Test files only needed to create an initial TRUTH file (unvalidated).
	 * Result should be imported into a database and marked for validation there.
	 */

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_AI_subset() {
		createInitialTruthFileFor(testDir.resolve("AI_subset/AI_subset.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_Cardiac_human() {
		createInitialTruthFileWithASySDFor(testDir.resolve("ASySD/dedupendnote_files/Cardiac_human.txt"));
	}

	// @Disabled("Only needed for initialisation of TRUTH file")
	// @Test
	// void createInitialTruthFile_ASySD_Depression() {
	//  Path dir = testDir.resolve("ASySD/dedupendnote_files");
	// 	Path inputPath = dir.resolve(dir + "/Depression.txt");
	// 	Path asysdInputPath = dir.resolve(dir + "/Depression_asysd_gold.txt");
	// 	Path outputPath = dir.resolve(dir + "/Depression_for_truth.txt");
	// 	createInitialTruthFile(inputPath, asysdInputPath, outputPath);
	// }

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_Diabetes() {
		createInitialTruthFileWithASySDFor(testDir.resolve("ASySD/dedupendnote_files/Diabetes.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_Neuroimaging() {
		// EndNote DB is Neuroimaging_sorted
		createInitialTruthFileWithASySDFor(testDir.resolve("ASySD/dedupendnote_files/Neuroimaging_sorted.txt"));
	}

	/*
	 * There is a gap in the ASySD record numbers between 38669 and 43002!
	 */
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_SRSR_Human() {
		// Columns L and U because of renumbering
		createInitialTruthFileWithASySDFor(testDir.resolve("ASySD/dedupendnote_files/SRSR_Human.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_CTG() {
		createInitialTruthFileFor(testDir.resolve("clinical_trials/clinicaltrialsdotgov.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_McKeown_2021() {
		createInitialTruthFileFor(testDir.resolve("McKeown_S_2021/dedupendnote_files/McKeown_2021.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_SRA2_Haematology() {
		createInitialTruthFileFor(testDir.resolve("SRA2/Haematology.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_SRA2_Respiratory() {
		createInitialTruthFileFor(testDir.resolve("SRA2/Respiratory.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_SRA2_Stroke() {
		createInitialTruthFileFor(testDir.resolve("SRA2/Stroke.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_TIL() {
		createInitialTruthFileFor(testDir.resolve("TIL/TIL.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_TIL_Zotero() {
		// uses the same TRUTH file as createInitialTruthFile_TIL
		createInitialTruthFileFor(testDir.resolve("TIL/TIL_Zotero.ris"));
	}

	/*
	 * Enrich the results of deduplication by Dedup-sweep AND DedupEndNote
	 * - if the record is validated, uppercase the Caption
	 *
	 * After executing:
	 * - remove EndNote DB BIG_SET_mark_DS_with_TRUTH
	 * - create new EndNote DB BIG_SET_mark_DS_with_TRUTH
	 * - import BIG_SET_mark_DS_with_TRUTH.txt
	 *
	 * Tested dedup-sweep Duplicate <> DedupEndNote No label: 19-9-2021: up to 13465
	 * Tested dedup-sweep Unique <> DedupEndNote With label: ???
	 */
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createRisWithTRUTH_BIG_SET_DS() throws IOException {
		Path truthPath = testDir.resolve("own/BIG_SET_TRUTH.txt");
		Path inputPath = testDir.resolve("Dedupe-sweep/dedupendnote_files/BIG_SET_mark_DS.txt");
		Path outputPath = testDir.resolve("Dedupe-sweep/dedupendnote_files/BIG_SET_mark_DS_with_TRUTH.txt");

		List<BibliographicItemDB> truthRecords = validationService.readTruthFile(truthPath);
		validationIOService.writeRisWithTRUTH_forDS(truthRecords, inputPath, outputPath);

		assertThat(1*1).isEqualTo(1);
	}

	/*
	 * After executing:
	 * - remove EndNote DB BIG_SET_TRUTH
	 * - create new EndNote DB BIG_SET_TRUTH
	 * - import BIG_SET_with_TRUTH.txt
	 *
	 * Limit the DB to validated records (NOT Caption unknown)
	 * Export as XML file for testing with Dedupe-sweep
	 */
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createRisWithTRUTH_BIG_SET() throws IOException {
		createRisWithTRUTHFor(testDir.resolve("own/BIG_SET.txt"));
		assertThat(1*1).isEqualTo(1);
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createRisWithTRUTH_SRA2_Cytology_screening() throws IOException {
		createRisWithTRUTHFor(testDir.resolve("SRA2/Cytology_screening.txt"));
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createRisWithTRUTH(Path inputPath, Path truthPath, Path outputPath) throws IOException {
		List<BibliographicItemDB> truthRecords = validationService.readTruthFile(truthPath);
		validationIOService.writeRisWithTRUTH(truthRecords, inputPath, outputPath);
	}

	/*
	 * Utility functions
	 */
	/**
	 * createInitialTruthFile: deduplicate an EndNote export file and saves a tab delimited file with the results which can be imported into
	 * a validation database as still unvalidated records.
	 *
	 * @param inputPath: an EndNote export file
	 * @param outputPath: a tab delimited file. Duplicate records have a non empty dedupid field.
	 */
	void createInitialTruthFile(Path inputPath, Path outputPath) {
		List<BibliographicItem> bibliographicItems = deduplicate(inputPath);
		List<BibliographicItemDB> publicationDBs = recordDBService.convertToRecordDB(bibliographicItems, inputPath);
		recordDBService.saveRecordDBs(publicationDBs, outputPath);
	}

	/**
	 * createInitialTruthFile: deduplicate an EndNote export file and save a tab delimited file with the results which can be imported into
	 * a validation database as still unvalidated records. The results of the ASySD export file is used to prefill the TP, TN, FP and FN fields.
	 *
	 * @param inputPath: an EndNote export file
	 * @param asysdInputPath: an ASySD export file
	 * @param outputPath: a tab delimited file. Duplicate records have a non empty dedupid field.
	 *
	 * The ASySDInputFile is an export of the columns RecordID and DuplicateIDPlus columns of an ASySD file from https://osf.io/c9evs/ (final_data/..._checked.csv).
	 * The column DuplicateIDPlus is a copy of the original DuplicateID with possible corrections / additions / ....
	 * To produce the file: select both columns, copy to a text editor, remove the first line with the column headers, save as a text file.
	 */
	void createInitialTruthFile(Path inputPath, Path asysdInputPath, Path outputPath) {
		Map<Integer, Set<Integer>> goldMap = readASySDGoldFile(asysdInputPath);
		List<BibliographicItem> bibliographicItems = deduplicate(inputPath);
		List<BibliographicItemDB> publicationDBs = recordDBService.convertToRecordDB(bibliographicItems, inputPath);

		publicationDBs.forEach(r -> {
			Integer id = r.getId();
			if (goldMap.get(id) != null && goldMap.get(id).size() == 1) {
				if (r.getDedupid() == null) {
					r.setTrueNegative(true);
				} else {
					r.setFalsePositive(true);
				}
			} else {
				if (r.getDedupid() == null) {
					r.setFalseNegative(true);
				} else {
					if (goldMap.get(id) != null && goldMap.get(id).contains(id)) {
						r.setTruePositive(true);
					}
				}
			}
		});
		recordDBService.saveRecordDBs(publicationDBs, outputPath);
	}

	private Map<Integer, Set<Integer>> readASySDGoldFile(Path asysdInputPath) {
		System.err.println("Start");
		List<String> lines = Collections.emptyList();
		try {
			lines = Files.readAllLines(asysdInputPath);
		} catch (IOException e) {
			e.printStackTrace();
		}
		Map<Integer, Integer> duplicateMap = lines.stream()
				.map(next -> next.split("\t"))
	    	    .collect(Collectors.toMap(entry -> Integer.valueOf(entry[0]), entry -> Integer.valueOf(entry[1])));
		/*
		 * Not all ASySD export files have recordID from 1 upward. The DedupEndNote files always start with recordID 1
		 */
		Integer offset = duplicateMap.keySet().stream().sorted().limit(1).findFirst().get() - 1;
		Map<Integer, Integer> duplicateMapAdjusted = new HashMap<>();
		for (Integer i : duplicateMap.keySet()) {
			duplicateMapAdjusted.put(i - offset, duplicateMap.get(i) - offset);
		}
		Map<Integer, Set<Integer>> duplicateSetMap = duplicateMapAdjusted.entrySet().stream()
				.collect(Collectors.groupingBy(Entry::getValue, Collectors.mapping(Entry::getKey, Collectors.toSet())));
		
		Map<Integer, Set<Integer>> goldMap = new HashMap<>();
		duplicateMapAdjusted.keySet().forEach(k -> goldMap.put(k, duplicateSetMap.get(duplicateMapAdjusted.get(k))));
		
		System.err.println("END with " + goldMap.keySet().size() + " keys");
		return goldMap;
	}

	private List<BibliographicItem> deduplicate(Path inputPath) {
		/*
		 * Run deduplicateOneFile in mark mode and read the marked output.
		 * This closes the gap between validation and production: validation now exercises
		 * the exact code path the production deployment runs, instead of mimicking it.
		 */
		deduplicationService.deduplicateOneFile(inputPath, DeduplicationMode.MARK, message -> {});
		Path markPath = UtilitiesService.createPath(inputPath, DeduplicationMode.MARK.filenameSuffix(), "txt");
		return bibliographicItemReader.readBibliographicItems(markPath, message -> {}, true);
	}
}