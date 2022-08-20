package edu.dedupendnote.services;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.domain.Record;
import edu.dedupendnote.domain.RecordDB;
import edu.dedupendnote.domain.ValidationResult;
import edu.dedupendnote.domain.ValidationResultASySD;
import lombok.extern.slf4j.Slf4j;

/**
 * The validation tests compare the current results to validated results (TRUTH files): see checkResults(...)
 * 
 * TRUTH files are the tab delimited output of a (Microsoft Access) database where validated records are marked as TP or TN, and TP records
 * have a non empty dedupid.
 *   
 * An unvalidated TRUTH file can be created with createInitialTruthFile().
 * Import the file into the database, validate some or all of the records, export the validated records as a tab delimited file (the TRUTH file).
 * 
 * See http://localhost:9777/developers for a description of the database. 
 */
@Slf4j
public class ValidationTests {

	private DeduplicationService deduplicationService = new DeduplicationService();
	private IOService ioService = new IOService();
	private RecordDBService recordDBService = new RecordDBService();
	String homeDir = System.getProperty("user.home");
	String testdir = homeDir + "/dedupendnote_files";
	String wssessionId = "";

	@BeforeAll
	static void beforeAll() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote");
		rootLogger.setLevel(Level.INFO);
	}
	
	/*
	 * Executes the deduplication in Mark mode, compares the results with the truth files and with the previous results.
	 * Prints the scores in the traditional way (TP = all records marked as duplicates, ...).
	 * See printValdationResultsASySD() for scores where TP = all records marked as duplicates except for the duplicate kept (i.e. all duplicate rightly removed)  
	 */
	@Test 
	void checkAllTruthFiles() throws IOException {
		// previous results
		Map<String, ValidationResult> validationResultsMap = List.of(
			new ValidationResult("ASySD_Cardiac_human", 6752, 21, 2175, 0),
			new ValidationResult("ASySD_Depression", 17399, 571, 61895, 15),
			new ValidationResult("ASySD_Diabetes", 1818, 16, 11, 0), 
			new ValidationResult("ASySD_Neuroimaging", 2170, 31, 1234, 3),
			new ValidationResult("ASySD_SRSR_Human", 27896, 101, 24986, 18),
			new ValidationResult("BIG_SET", 3697, 257, 964, 5),
			new ValidationResult("McKeown_2021", 2013, 59, 1058, 0),
			new ValidationResult("SRA2_Cytology_screening", 1359, 61, 436, 0),
			new ValidationResult("SRA2_Haematology", 222, 14, 1179, 0),
			new ValidationResult("SRA2_Respiratory", 769, 31, 1188, 0),
			new ValidationResult("SRA2_Stroke", 503, 7, 782, 0)
		).stream()
		.collect(Collectors.toMap(ValidationResult::getFileName, Function.identity(), (o1, o2) -> o1, TreeMap::new));

		Map<String, ValidationResult> resultsMap = List.of(
			checkResults_ASySD_Cardiac_human(),
			checkResults_ASySD_Depression(),
			checkResults_ASySD_Diabetes(),
			checkResults_ASySD_Neuroimaging(),
			checkResults_ASySD_SRSR_Human(),
			checkResults_BIG_SET(),
			checkResults_McKeown_2021(),
			checkResults_SRA2_Cytology_screening(),
			checkResults_SRA2_Haematology(),
			checkResults_SRA2_Respiratory(),
			checkResults_SRA2_Stroke()
		).stream()
		.collect(Collectors.toMap(ValidationResult::getFileName, Function.identity(), (o1, o2) -> o1, TreeMap::new));
		
		boolean changed = false;
		for (String setName : resultsMap.keySet()) {
			ValidationResult v = validationResultsMap.get(setName);
			ValidationResult c = resultsMap.get(setName);
			if (c == null) continue;	// easy when some checkResults_...() are commented out

			int tp = c.getTp(), fn = c.getFn(), tn = c.getTn(), fp = c.getFp();
			double precision = tp  * 100.0 / (tp + fp);
			double sensitivity = tp * 100.0 / (tp + fn); // == recall
			double specificity = tn * 100.0 / (tn + fp);
			double f1_score = 2 * precision * sensitivity / (precision + sensitivity);
			if (v.equals(c)) {
				System.out.println("\nResults: " + setName);
				System.out.println("------------------------------------------------------------------------------------------------------------------------------");
				System.out.println(String.format("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s | %12s | %12s |",
						"TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity", "Precision", "F1-score"));
				System.out.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% |",
						tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, sensitivity, tn, fp, specificity, precision, f1_score));
				System.out.println("------------------------------------------------------------------------------------------------------------------------------");
				System.out.flush();
//			errors.stream().forEach(System.err::println);
			} else {
				changed = true;
				System.err.println("\nResults: " + setName + ": HAS DIFFERENT RESULTS (first new, second old");
				System.err.println("------------------------------------------------------------------------------------------------------------------------------");
				System.err.println(String.format("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s | %12s | %12s |",
						"TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity", "Precision", "F1-score"));
//				System.err.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.2f%% |",
//									tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, (tp * 100.0/(tp + fn)), tn, fp, (tn * 100.0/(tn + fp))));
//				System.err.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.2f%% |",
//						tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, (tp * 100.0/(tp + fn)), tn, fp, (tn * 100.0/(tn + fp))));
				System.err.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% |",
						tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, sensitivity, tn, fp, specificity, precision, f1_score));
				tp = v.getTp(); fn = v.getFn(); tn = v.getTn(); fp = v.getFp();
				precision = tp  * 100.0 / (tp + fp);
				sensitivity = tp * 100.0 / (tp + fn); // == recall
				specificity = tn * 100.0 / (tn + fp);
				f1_score = 2 * precision * sensitivity / (precision + sensitivity);
				System.err.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% |",
						tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, sensitivity, tn, fp, specificity, precision, f1_score));
				System.err.println("------------------------------------------------------------------------------------------------------------------------------");
				System.err.flush();
			}
		}
		System.out.println("FP can be found with regex: \\ttrue\\tfalse\\tfalse\\ttrue\\tfalse\\t");
		System.out.println("FN can be found with regex: \\d\\ttrue\\tfalse\\tfalse\\tfalse\\ttrue\\t");
		System.out.println("TP which will be kept can be found with regex: ^(\\d+)\\t\\1\\t\\ttrue\\ttrue\\t");
		
		assertThat(changed).isFalse();
	}
	
	/*
	 * TODO: extend the function with real checkResults as in checkAllTruthFiles()?
	 * TODO: extract validationResultsMap to avoid duplication.
	 */
	@Test
	void printValidationResultsASySD() {
		
		/*
		 * FIXME: the number of unique duplicates has to be from the ..._to_validate.txt files.   
		 */

		Map<String, ValidationResultASySD> validationResultsMap = List.of(
//				new ValidationResult("Cytology_screening", 1360, 60, 436, 0),
//				new ValidationResult("Haematology", 222, 14, 1179, 0),
//				new ValidationResult("Respiratory", 765, 35, 1188, 0),
//				new ValidationResult("Stroke", 504, 6, 782, 0),
//				new ValidationResult("BIG_SET", 3697, 257, 964, 5), // 1347 unique
//				new ValidationResult("McKeown_2021", 2014, 58, 1058, 0),
				
				new ValidationResultASySD("ASySD_Cardiac_human", 6752, 21, 2175, 0, 3236),
				new ValidationResultASySD("ASySD_Depression", 17399, 571, 61895, 15, 7668),
				new ValidationResultASySD("ASySD_Diabetes", 1818, 16, 11, 0, 566), 
				new ValidationResultASySD("ASySD_Neuroimaging", 2170, 31, 1234, 3, 890),
				new ValidationResultASySD("ASySD_SRSR_Human", 27896, 101, 24986, 18, 11112)
			)
			.stream()
			.collect(Collectors.toMap(ValidationResultASySD::getFileName, Function.identity(), (o1, o2) -> o1, TreeMap::new));
		
		for (String setName : validationResultsMap.keySet()) {
			ValidationResultASySD v = validationResultsMap.get(setName);
			int tp = v.getTp() - v.getUniqueDuplicates(), fn = v.getFn(), tn = v.getTn() + v.getUniqueDuplicates(), fp = v.getFp();
			double precision = tp  * 100.0 / (tp + fp);
			double sensitivity = tp * 100.0 / (tp + fn);
			System.out.println("\nResults: " + setName);
			System.out.println("---------------------------------------------------------------------------------------------------------------------------------------------");
			System.out.println(String.format("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s | %12s | %12s | %12s |", "TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity", "Precision", "Accuracy", "F1"));
			System.out.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% | %11.3f%% |",
								tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, sensitivity, tn, fp, tn * 100.0 / (tn + fp),
								precision, (tp + tn) * 100.0 / (tp + fn + tn + fp), 2 * precision * sensitivity / (precision + sensitivity)));
			System.out.println("---------------------------------------------------------------------------------------------------------------------------------------------");

			// Printing out the traditional way
//			tp = v.getTp(); fn = v.getFn(); tn = v.getTn(); fp = v.getFp();
//			precision = tp  * 100.0 / (tp + fp);
//			sensitivity = tp * 100.0 / (tp + fn);
//			System.out.println("---------------------------------------------------------------------------------------------------------------------------------------------");
//			System.out.println(String.format("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s | %12s | %12s | %12s |", "TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity", "Precision", "Accuracy", "F1"));
//			System.out.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% | %11.3f%% |",
//								tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, sensitivity, tn, fp, tn * 100.0 / (tn + fp),
//								precision, (tp + tn) * 100.0 / (tp + fn + tn + fp), 2 * precision * sensitivity / (precision + sensitivity)));
//			System.out.println("---------------------------------------------------------------------------------------------------------------------------------------------");
			System.out.flush();
		}
	}
	
	@Test
	void readTruthFileTest() throws IOException {
		String fileName = testdir + "/SRA2/Cytology_screening_TRUTH.txt";
		List<RecordDB> truthRecords = readTruthFile(fileName);

		assertThat(truthRecords).hasSizeGreaterThan(10);
		
		Map<Integer, Set<Integer>> trueDuplicateSets = truthRecords.stream()
				.filter(r -> r.getDedupid() != null)
				// .map(RecordDB::getDedupid)
				.collect(Collectors.groupingBy(RecordDB::getDedupid,
												Collectors.mapping(RecordDB::getId, Collectors.toSet())));
		assertThat(trueDuplicateSets).hasSizeGreaterThan(10);
		trueDuplicateSets.entrySet().stream().limit(10).forEach(l -> System.err.println(l));
	}
	
	// @formatter:off
	ValidationResult checkResults(String setName, String inputFileName, String outputFileName, String truthFileName) throws IOException {
		log.error("- Validating {}", setName);
		List<RecordDB> truthRecords = readTruthFile(truthFileName);
		List<RecordDB> recordDBs = getRecordDBs(inputFileName);
		Map<Integer, RecordDB> validationMap = recordDBs.stream()
				.collect(Collectors.toMap(RecordDB::getId, Function.identity(), (o1, o2) -> o1, TreeMap::new));
		Map<Integer, Set<Integer>> trueDuplicateSets = truthRecords.stream()
				.filter(r -> r.getDedupid() != null)
				// .map(RecordDB::getDedupid)
				.collect(Collectors.groupingBy(RecordDB::getDedupid,
												Collectors.mapping(RecordDB::getId, Collectors.toSet())));
		int tns = 0, tps = 0, fps = 0, fns = 0;
		List<String> errors = new ArrayList<>();
		Map<Integer, Integer> fpErrors = new HashMap<>();
		
		for (RecordDB t : truthRecords) {
			RecordDB v = validationMap.get(t.getId());
			v.setValidated(t.isValidated());
			v.setUnsolvable(t.isUnsolvable());
			Integer tDedupId = t.getDedupid();
			Integer vDedupId = v.getDedupid();
			log.debug("Comparing {} with truth {} and validation {}", t.getId(), t.getDedupid(), v.getDedupid());
			if (vDedupId == null) {
				if (tDedupId == null) {
					v.setTrueNegative(true);
					tns++;
				} else {
					v.setFalseNegative(true);
					fns++;
					v.setCorrection(tDedupId);
				}
			} else if (trueDuplicateSets.containsKey(tDedupId) && trueDuplicateSets.get(tDedupId).contains(vDedupId)) {
				v.setTruePositive(true);
				tps++;
			} else {
				v.setFalsePositive(true);
				fps++;
				v.setCorrection(tDedupId);
				errors.add("FALSE POSITIVES: \n- TRUTH " + t + "\n- CURRENT " + v + "\n");
				fpErrors.put(v.getId(), vDedupId);
			}
		}
		recordDBService.saveRecordDBs(recordDBs, outputFileName);
		ValidationResult validationResult = new ValidationResult(setName, tps, fns, tns, fps);
		if (! errors.isEmpty()) {
			System.err.println("File " + setName +  " has FALSE POSITIVES!");
			errors.stream().forEach(System.err::println);
			System.err.println("These are the FP recordIDs for " + setName);
			fpErrors.entrySet().forEach(System.err::println);
		}
		long uniqueDuplicates = recordDBs.stream()
				.filter(r -> r.isTruePositive() == true && r.getDedupid().equals(r.getId()))
				.count();

		System.err.println("File " + setName +  " has unique duplicates: " + uniqueDuplicates);
		return validationResult;
	}
	
	ValidationResult checkResults_ASySD_Cardiac_human() throws IOException {
		String truthFileName = testdir + "/ASySD/dedupendnote_files/Cardiac_human_TRUTH.txt";
		String inputFileName = testdir + "/ASySD/dedupendnote_files/Cardiac_human.txt";
		String outputFileName = testdir + "/ASySD/dedupendnote_files/Cardiac_human_to_validate.txt";

		return checkResults("ASySD_Cardiac_human", inputFileName, outputFileName, truthFileName);
	}
	
	ValidationResult checkResults_ASySD_Depression() throws IOException {
		String truthFileName = testdir + "/ASySD/dedupendnote_files/Depression_TRUTH.txt";
		String inputFileName = testdir + "/ASySD/dedupendnote_files/Depression.txt";
		String outputFileName = testdir + "/ASySD/dedupendnote_files/Depression_to_validate.txt";

		return checkResults("ASySD_Depression", inputFileName, outputFileName, truthFileName);
	}
	
	ValidationResult checkResults_ASySD_Diabetes() throws IOException {
		String truthFileName = testdir + "/ASySD/dedupendnote_files/Diabetes_TRUTH.txt";
		String inputFileName = testdir + "/ASySD/dedupendnote_files/Diabetes.txt";
		String outputFileName = testdir + "/ASySD/dedupendnote_files/Diabetes_to_validate.txt";

		return checkResults("ASySD_Diabetes", inputFileName, outputFileName, truthFileName);
	}
	
	ValidationResult checkResults_ASySD_Neuroimaging() throws IOException {
		String truthFileName = testdir + "/ASySD/dedupendnote_files/Neuroimaging_sorted_TRUTH.txt";
		String inputFileName = testdir + "/ASySD/dedupendnote_files/Neuroimaging_sorted.txt";
		String outputFileName = testdir + "/ASySD/dedupendnote_files/Neuroimaging_sorted_to_validate.txt";

		return checkResults("ASySD_Neuroimaging", inputFileName, outputFileName, truthFileName);
	}
	
	ValidationResult checkResults_ASySD_SRSR_Human() throws IOException {
		String truthFileName = testdir + "/ASySD/dedupendnote_files/SRSR_Human_TRUTH.txt";
		String inputFileName = testdir + "/ASySD/dedupendnote_files/SRSR_Human.txt";
		String outputFileName = testdir + "/ASySD/dedupendnote_files/SRSR_Human_to_validate.txt";

		return checkResults("ASySD_SRSR_Human", inputFileName, outputFileName, truthFileName);
	}
	
	/*
	 * Deduplicates the whole file, but checks only the results of the validated subset 
	 */
	ValidationResult checkResults_BIG_SET() throws IOException {
		String truthFileName = testdir + "/own/BIG_SET_TRUTH.txt";
		String inputFileName = testdir + "/own/BIG_SET.txt";
		String outputFileName = testdir + "/own/BIG_SET_to_validate.txt";

		return checkResults("BIG_SET", inputFileName, outputFileName, truthFileName);
	}

	ValidationResult checkResults_McKeown_2021() throws IOException {
		String truthFileName = testdir + "/McKeown_S_2021/dedupendnote_files/McKeown_2021_TRUTH.txt";
		String inputFileName = testdir + "/McKeown_S_2021/dedupendnote_files/McKeown_2021.txt";
		String outputFileName = testdir + "/McKeown_S_2021/dedupendnote_files/McKeown_2021_to_validate.txt";

		return checkResults("McKeown_2021", inputFileName, outputFileName, truthFileName);
	}
	
	ValidationResult checkResults_SRA2_Cytology_screening() throws IOException {
		String truthFileName = testdir + "/SRA2/Cytology_screening_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Cytology_screening.txt";
		String outputFileName = testdir + "/SRA2/Cytology_screening_to_validate.txt";

		return checkResults("SRA2_Cytology_screening", inputFileName, outputFileName, truthFileName);
	}

	ValidationResult checkResults_SRA2_Haematology() throws IOException {
		String truthFileName = testdir + "/SRA2/Haematology_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Haematology.txt";
		String outputFileName = testdir + "/SRA2/Haematology_to_validate.txt";

		return checkResults("SRA2_Haematology", inputFileName, outputFileName, truthFileName);
	}

	ValidationResult checkResults_SRA2_Respiratory() throws IOException {
		String truthFileName = testdir + "/SRA2/Respiratory_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Respiratory.txt";
		String outputFileName = testdir + "/SRA2/Respiratory_to_validate.txt";

		return checkResults("SRA2_Respiratory", inputFileName, outputFileName, truthFileName);
	}
	
	ValidationResult checkResults_SRA2_Stroke() throws IOException {
		String truthFileName = testdir + "/SRA2/Stroke_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Stroke.txt";
		String outputFileName = testdir + "/SRA2/Stroke_to_validate.txt";

		return checkResults("SRA2_Stroke", inputFileName, outputFileName, truthFileName);
	}
	
	/*
	 * Test files only needed to create an initial TRUTH file (unvalidated).
	 * Result should be imported into a database and marked for validation there. 
	 */

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_Cardiac_human() {
		String dir = testdir + "/ASySD/dedupendnote_files";
		String inputFileName = dir + "/Cardiac_human.txt";
		String asysdInputfileName = dir + "/Cardiac_human_asysd_gold.txt";
		String outputFileName = dir + "/Cardiac_human_for_truth.txt";
		createInitialTruthFile(inputFileName, asysdInputfileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_Depression() {
		String dir = testdir + "/ASySD/dedupendnote_files";
		String inputFileName = dir + "/Depression.txt";
		String asysdInputfileName = dir + "/Depression_asysd_gold.txt";
		String outputFileName = dir + "/Depression_for_truth.txt";
		createInitialTruthFile(inputFileName, asysdInputfileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_Diabetes() {
		String dir = testdir + "/ASySD/dedupendnote_files";
		String inputFileName = dir + "/Diabetes.txt";
		String asysdInputfileName = dir + "/Diabetes_asysd_gold.txt";
		String outputFileName = dir + "/Diabetes_for_truth.txt";
		createInitialTruthFile(inputFileName, asysdInputfileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_Neuroimaging() {
		// Endnote DB is Neuroimaging_sorted
		String dir = testdir + "/ASySD/dedupendnote_files";
		String inputFileName = dir + "/Neuroimaging_sorted.txt";
		String asysdInputfileName = dir + "/Neuroimaging_sorted_asysd_gold.txt";
		String outputFileName = dir + "/Neuroimaging_for_truth.txt";
		createInitialTruthFile(inputFileName, asysdInputfileName, outputFileName);
	}
	
	/*
	 * There is a gap in the ASySD record numbers between 38669 and 43002!
	 */
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_SRSR_Human() {
		String dir = testdir + "/ASySD/dedupendnote_files";
		String inputFileName = dir + "/SRSR_Human.txt";
		String asysdInputfileName = dir + "/SRSR_Human_asysd_gold.txt"; // Columns L and U because of renumbering 
		String outputFileName = dir + "/SRSR_Human_for_truth.txt";
		createInitialTruthFile(inputFileName, asysdInputfileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_McKeown_2021() {
		String dir = testdir + "/McKeown_S_2021/dedupendnote_files";
		String inputFileName = dir + "/McKeown_2021.txt";
		String outputFileName = dir + "/McKeown_2021_for_truth.txt";
		createInitialTruthFile(inputFileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_SRA2_Haematology() {
		String inputFileName = testdir + "/SRA2/Haematology.txt";
		String outputFileName = testdir + "/SRA2/Haematology_for_truth.txt";
		createInitialTruthFile(inputFileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_SRA2_Respiratory() {
		String inputFileName = testdir + "/SRA2/Respiratory.txt";
		String outputFileName = testdir + "/SRA2/Respiratory_for_truth.txt";
		createInitialTruthFile(inputFileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_SRA2_Stroke() {
		String inputFileName = testdir + "/SRA2/Stroke.txt";
		String outputFileName = testdir + "/SRA2/Stroke_for_truth.txt";
		createInitialTruthFile(inputFileName, outputFileName);
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
//	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createRisWithTRUTH_BIG_SET_DS() throws IOException {
		String truthFileName = testdir + "/own/BIG_SET_TRUTH.txt";
		String inputFileName = testdir + "/Dedupe-sweep/dedupendnote_files/BIG_SET_mark_DS.txt";
		String outputFileName = testdir + "/Dedupe-sweep/dedupendnote_files/BIG_SET_mark_DS_with_TRUTH.txt";

		List<RecordDB> truthRecords = readTruthFile(truthFileName);
		ioService.writeRisWithTRUTH_forDS(truthRecords, inputFileName, outputFileName);
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
//	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createRisWithTRUTH_BIG_SET() throws IOException {
		String truthFileName = testdir + "/own/BIG_SET_TRUTH.txt";
		String inputFileName = testdir + "/own/BIG_SET.txt";
		String outputFileName = testdir + "/own/BIG_SET_with_TRUTH.txt";

		createRisWithTRUTH(inputFileName, truthFileName, outputFileName);
	}

	void createRisWithTRUTH_SRA2_Cytology_screening() throws IOException {
		String truthFileName = testdir + "/SRA2/Cytology_screening_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Cytology_screening.txt";
		String outputFileName = testdir + "/SRA2/Cytology_screening_with_TRUTH.txt";

		createRisWithTRUTH(inputFileName, truthFileName, outputFileName);
	}


	private void createRisWithTRUTH(String inputFileName, String truthFileName, String outputFileName) throws IOException {
		List<RecordDB> truthRecords = readTruthFile(truthFileName);
		ioService.writeRisWithTRUTH(truthRecords, inputFileName, outputFileName);
		
	}

	/*
	 * Utility functions
	 */
	/**
	 * createInitialTruthFile: deduplicate an EndNote export file and saves a tab delimited file with the results which can be imported into
	 * a validation database as still unvalidated records.
	 *  
	 * @param inputFileName: an EndNote export file
	 * @param outputFileName: a tab delimited file. Duplicate records have a non empty dedupid field.
	 */
	void createInitialTruthFile(String inputFileName, String outputFileName) {
		List<RecordDB> recordDBs = getRecordDBs(inputFileName);
		recordDBService.saveRecordDBs(recordDBs, outputFileName);
	}
	
	/**
	 * createInitialTruthFile: deduplicate an EndNote export file and save a tab delimited file with the results which can be imported into
	 * a validation database as still unvalidated records. The results of the ASySD export file is used to prefill the TP, TN, FP and FN fields.
	 *  
	 * @param inputFileName: an EndNote export file
	 * @param asysdInputfileName: an ASySD export file
	 * @param outputFileName: a tab delimited file. Duplicate records have a non empty dedupid field.
	 * 
	 * The ASySDInputFile is an export of the columns RecordID and DuplicateIDPlus columns of an ASySD file from https://osf.io/c9evs/ (final_data/..._checked.csv). 
	 * The column DuplicateIDPlus is a copy of the original DuplicateID with possible corrections / additions / ....
	 * To produce the file: select both columns, copy to a text editor, remove the first line with the column headers, save as a text file.
	 */
	void createInitialTruthFile(String inputFileName, String asysdInputfileName, String outputFileName) {
		Map<Integer, Set<Integer>> goldMap = readASySDGoldFile(asysdInputfileName);
		List<RecordDB> recordDBs = getRecordDBs(inputFileName);
//		Map<Integer,RecordDB> recordDBMap = recordDBs.stream().collect(Collectors.toMap(RecordDB::getId, Function.identity()));
		
//		goldMap.entrySet().stream().filter(e -> e.getValue().size() == 1).forEach(e -> {
//			if (recordDBMap.get(key))
//		});
		
		recordDBs.forEach(r -> {
			Integer id = r.getId();
			if (goldMap.get(id).size() == 1) {
				if (r.getDedupid() == null) {
					r.setTrueNegative(true);
				} else {
					r.setFalsePositive(true);
				}
			} else {
				if (r.getDedupid() == null) {
					r.setFalseNegative(true);
				} else {
					if (goldMap.get(id).contains(id)) {
						r.setTruePositive(true);
					}
				}
			}
		});
		recordDBService.saveRecordDBs(recordDBs, outputFileName);
	}
	
	private Map<Integer, Set<Integer>> readASySDGoldFile(String asysdInputfileName) {
		System.err.println("Start");
		List<String> lines = Collections.emptyList();
		try {
			lines = Files.readAllLines(Paths.get(asysdInputfileName));
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
		
		Map<Integer,Set<Integer>> goldMap = new HashMap<>();
		duplicateMapAdjusted.keySet().forEach(k -> goldMap.put(k, duplicateSetMap.get(duplicateMapAdjusted.get(k))));
		
		System.err.println("END with " + goldMap.keySet().size() + " keys");
		return goldMap;
	}

	/**
	 * getRecordDBs: deduplicates an EndNote export file and returns the results for the validation database.
	 * 
	 * @param inputFileName: an EndNote export file
	 * @return	List<RecordDB>
	 */
	List<RecordDB> getRecordDBs(String inputFileName) {
		List<Record> records = ioService.readRecords(inputFileName);

		String s = deduplicationService.doSanityChecks(records, inputFileName);
		if (s != null) {
			fail(s);
		}

		deduplicationService.searchYearOneFile(records, wssessionId);
		List<RecordDB> recordDBs = recordDBService.convertToRecordDB(records, inputFileName);
		
		return recordDBs;
	}

	List<RecordDB> readTruthFile(String fileName) throws IOException {
	    Path path = Paths.get(fileName);

		CsvMapper mapper = new CsvMapper();
		CsvSchema schema = mapper
				.schemaFor(RecordDB.class)
				.withHeader()
				.withColumnSeparator('\t')
				.withLineSeparator("\n");
		MappingIterator<RecordDB> it = mapper
				.readerFor(RecordDB.class)
				.with(schema)
				.with(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
				.readValues(path.toFile());
		List<RecordDB> records = it.readAll();
		
		return records;
	}
}
