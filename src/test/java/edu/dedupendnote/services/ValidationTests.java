package edu.dedupendnote.services;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.Charset;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.SetFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

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
@SpringBootTest
@ActiveProfiles("test")
public class ValidationTests {

	private DeduplicationService deduplicationService = new DeduplicationService();
	private IOService ioService = new IOService();
	private MockHttpSession session = new MockHttpSession();
	private RecordDBService recordDBService = new RecordDBService();
	@Value("${testdir}")
	private String testdir;

	@BeforeAll
	static void beforeAll() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote");
		rootLogger.setLevel(Level.INFO);
	}
	
	@Test 
	void checkAllTruthFiles() throws IOException {
		Map<String, ValidationResult> validationResultsMap = List.of(
				new ValidationResult("Cytology_screening", 1360, 60, 436, 0),
				new ValidationResult("Haematology", 222, 14, 1179, 0),
				new ValidationResult("Respiratory", 758, 42, 1188, 0),
				new ValidationResult("Stroke", 504, 6, 782, 0),
				new ValidationResult("BIG_SET", 3163, 125, 755, 3),
				new ValidationResult("McKeown_2021", 2002, 70, 1058, 0),
				new ValidationResult("ASySD_Cardiac_human", 6752, 21, 2175, 0),
				new ValidationResult("ASySD_Depression", 17282, 740, 61856, 2),
				new ValidationResult("ASySD_Diabetes", 1806, 24, 11, 4),
				new ValidationResult("ASySD_Neuroimaging", 2170, 31, 1236, 1))
			.stream()
			.collect(Collectors.toMap(ValidationResult::getFileName, Function.identity(), (o1, o2) -> o1, TreeMap::new));

		Map<String, ValidationResult> resultsMap = List.of(
				checkResults_Cytology_screening(),
				checkResults_Haematology(),
				checkResults_Respiratory(),
				checkResults_Stroke(),
				checkResults_BIG_SET(),
				checkResults_McKeown_2021(),
				checkResults_ASySD_Cardiac_human(),
				checkResults_ASySD_Depression(),
				checkResults_ASySD_Diabetes(),
				checkResults_ASySD_Neuroimaging())
			.stream()
			.collect(Collectors.toMap(ValidationResult::getFileName, Function.identity()));
		
		boolean changed = false;
		for (String setName : validationResultsMap.keySet()) {
			ValidationResult v = validationResultsMap.get(setName);
			ValidationResult c = resultsMap.get(setName);
			if (c == null) continue;	// easy when some checkResults_...() are commented out

			int tp = c.getTp(), fn = c.getFn(), tn = c.getTn(), fp = c.getFp();
			if (v.equals(c)) {
				System.out.println("\nResults: " + setName);
				System.out.println("------------------------------------------------------------------------------------------------");
				System.out.println(String.format("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s |", "TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity"));
				System.out.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% |",
									tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, (tp * 100.0/(tp + fn)), tn, fp, (tn * 100.0/(tn + fp))));
				System.out.println("------------------------------------------------------------------------------------------------");
				System.out.flush();
//			errors.stream().forEach(System.err::println);
			} else {
				changed = true;
				System.err.println("\nResults: " + setName + ": HAS DIFFERENT RESULTS (first new, second old");
				System.err.println("------------------------------------------------------------------------------------------------");
				System.err.println(String.format("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s |", "TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity"));
				System.err.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.2f%% |",
									tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, (tp * 100.0/(tp + fn)), tn, fp, (tn * 100.0/(tn + fp))));
				tp = v.getTp(); fn = v.getFn(); tn = v.getTn(); fp = v.getFp();
				System.err.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.2f%% |",
						tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, (tp * 100.0/(tp + fn)), tn, fp, (tn * 100.0/(tn + fp))));
				System.err.println("------------------------------------------------------------------------------------------------");
				System.err.flush();
			}
		}
		System.out.println("FP can be found with regex: \\ttrue\\tfalse\\tfalse\\ttrue\\tfalse\\t");
		System.out.println("FN can be found with regex: \\ttrue\\tfalse\\tfalse\\tfalse\\ttrue\\t");
		
		assertThat(changed).isFalse();
	}
	
	@Test
	void readTruthFileTest() throws IOException {
		String fileName = testdir + "/SRA2/Cytology_screening_TRUTH.txt";
		List<RecordDB> records = readTruthFile(fileName);

		assertThat(records).hasSizeGreaterThan(10);
		
		// records.stream().limit(10).forEach(l -> System.err.println(l));
	}
	
	// @formatter:off
	ValidationResult checkResults(String setName, String inputFileName, String outputFileName, String truthFileName) throws IOException {
		log.error("- Validating {}", setName);
		List<RecordDB> truthRecords = readTruthFile(truthFileName);
		List<RecordDB> recordDBs = getRecordDBs(inputFileName);
		Map<Integer, RecordDB> validationMap = recordDBs.stream()
				.collect(Collectors.toMap(RecordDB::getId, Function.identity(), (o1, o2) -> o1, TreeMap::new));
		Set<Integer> trueDuplicateSets = truthRecords.stream()
				.filter(r -> r.getDedupid() != null)
				.map(RecordDB::getDedupid)
				.collect(Collectors.toSet());
		int tns = 0, tps = 0, fps = 0, fns = 0;
		List<String> errors = new ArrayList<>();
		
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
			} else {
				if (vDedupId.equals(tDedupId)) {
					v.setTruePositive(true);
					tps++;
				} else {
					/*
					 *  If the truth file has a  dedupId for the current dedupId, then the current record was linked to the wrong set of duplicate records: False Positive
					 *  If the truth file had NO dedupId for the current dedupId, then the current record belongs to a set of duplicate records which shoul have been merged with
					 *  another set of duplicate records: False Negative 
					 */
					if (trueDuplicateSets.contains(vDedupId)) {
						v.setFalsePositive(true);
						fps++;
						v.setCorrection(tDedupId);
						errors.add("FALSE POSITIVES: \n- TRUTH " + t + "\n- CURRENT " + v + "\n");
					} else {
						v.setFalseNegative(true);
						fns++;
						v.setCorrection(tDedupId);
					}
				}
			}
		}
		recordDBService.saveRecordDBs(recordDBs, outputFileName);
		ValidationResult validationResult = new ValidationResult(setName, tps, fns, tns, fps);
		if (! errors.isEmpty()) {
			System.err.println("File " + setName +  " has FALSE POSITIVES!");
			errors.stream().forEach(System.err::println);
		}
		return validationResult;
	}
	
	ValidationResult checkResults_Cytology_screening() throws IOException {
		String truthFileName = testdir + "/SRA2/Cytology_screening_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Cytology_screening.txt";
		String outputFileName = testdir + "/SRA2/Cytology_screening_to_validate.txt";

		return checkResults("Cytology_screening", inputFileName, outputFileName, truthFileName);
	}

	ValidationResult checkResults_Haematology() throws IOException {
		String truthFileName = testdir + "/SRA2/Haematology_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Haematology.txt";
		String outputFileName = testdir + "/SRA2/Haematology_to_validate.txt";

		return checkResults("Haematology", inputFileName, outputFileName, truthFileName);
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

	ValidationResult checkResults_Respiratory() throws IOException {
		String truthFileName = testdir + "/SRA2/Respiratory_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Respiratory.txt";
		String outputFileName = testdir + "/SRA2/Respiratory_to_validate.txt";

		return checkResults("Respiratory", inputFileName, outputFileName, truthFileName);
	}
	
	ValidationResult checkResults_Stroke() throws IOException {
		String truthFileName = testdir + "/SRA2/Stroke_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Stroke.txt";
		String outputFileName = testdir + "/SRA2/Stroke_to_validate.txt";

		return checkResults("Stroke", inputFileName, outputFileName, truthFileName);
	}
	
	ValidationResult checkResults_McKeown_2021() throws IOException {
		String truthFileName = testdir + "/McKeown_S_2021/dedupendnote_files/McKeown_2021_TRUTH.txt";
		String inputFileName = testdir + "/McKeown_S_2021/dedupendnote_files/McKeown_2021.txt";
		String outputFileName = testdir + "/McKeown_S_2021/dedupendnote_files/McKeown_2021_to_validate.txt";

		return checkResults("McKeown_2021", inputFileName, outputFileName, truthFileName);
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
	
	/*
	 * Test files only needed to create an initial TRUTH file (unvalidated).
	 * Result should be imported into a database and marked for validation there. 
	 */

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_Haematology() {
		String inputFileName = testdir + "/SRA2/Haematology.txt";
		String outputFileName = testdir + "/SRA2/Haematology_for_truth.txt";
		createInitialTruthFile(inputFileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_Respiratory() {
		String inputFileName = testdir + "/SRA2/Respiratory.txt";
		String outputFileName = testdir + "/SRA2/Respiratory_for_truth.txt";
		createInitialTruthFile(inputFileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_Stroke() {
		String inputFileName = testdir + "/SRA2/Stroke.txt";
		String outputFileName = testdir + "/SRA2/Stroke_for_truth.txt";
		createInitialTruthFile(inputFileName, outputFileName);
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
	void createInitialTruthFile_ASySD_Cardiac_human() {
		String dir = testdir + "/ASySD/dedupendnote_files";
		String inputFileName = dir + "/Cardiac_human.txt";
		String asysdInputfileName = dir + "/Cardiac_human_asysd_gold.txt";
		String outputFileName = dir + "/Cardiac_human_for_truth.txt";
		createInitialTruthFile(inputFileName, asysdInputfileName, outputFileName);
	}
	
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_ASySD_Diabetes() {
		String dir = testdir + "/ASySD/dedupendnote_files";
		String inputFileName = dir + "/Diabetes.txt";
		String outputFileName = dir + "/Diabetes_for_truth.txt";
		createInitialTruthFile(inputFileName, outputFileName);
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
	void createInitialTruthFile_ASySD_Neuroimaging() {
		// Endnote DB is Neuroimaging_sorted
		String dir = testdir + "/ASySD/dedupendnote_files";
		String inputFileName = dir + "/Neuroimaging_sorted.txt";
		String asysdInputfileName = dir + "/Neuroimaging_sorted_asysd_gold.txt";
		String outputFileName = dir + "/Neuroimaging_for_truth.txt";
		createInitialTruthFile(inputFileName, asysdInputfileName, outputFileName);
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
	 * createInitialTruthFile: deduplicate an EndNote export file and saves a tab delimited file with the results which can be imported into
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
		 * Not all ASySD export files have recordID from 1 upward. The DedupEndNote files always strat with recordID 1 
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
		// TODO Auto-generated method stub
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

		deduplicationService.searchYearOneFile(records, session);
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
