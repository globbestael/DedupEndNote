package edu.dedupendnote.services;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import edu.dedupendnote.domain.Record;
import edu.dedupendnote.domain.RecordDB;
import edu.dedupendnote.domain.ValidationResult;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
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
@TestConfiguration
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
				new ValidationResult("BIG_SET", 2779, 148, 720, 0),
				new ValidationResult("McKeown_2021", 2002, 70, 1058, 0))
			.stream()
			.collect(Collectors.toMap(ValidationResult::getFileName, Function.identity(), (o1, o2) -> o1, TreeMap::new));

		Map<String, ValidationResult> resultsMap = List.of(
				checkResults_Cytology_screening(),
				checkResults_Haematology(),
				checkResults_Respiratory(),
				checkResults_Stroke(),
				checkResults_BIG_SET(),
				checkResults_McKeown_2021())
			.stream()
			.collect(Collectors.toMap(ValidationResult::getFileName, Function.identity()));
		
		boolean changed = false;
		for (String setName : validationResultsMap.keySet()) {
			ValidationResult v = validationResultsMap.get(setName);
			ValidationResult c = resultsMap.get(setName);

			int tp = c.getTp(), fn = c.getFn(), tn = c.getTn(), fp = c.getFp();
			if (v.equals(c)) {
				System.out.println("\nResults: " + setName);
				System.out.println("------------------------------------------------------------------------------------------------");
				System.out.println(String.format("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s |", "TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity"));
				System.out.println(String.format("| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.2f%% |",
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
		List<RecordDB> truthRecords = readTruthFile(truthFileName);
		List<RecordDB> recordDBs = getRecordDBs(inputFileName);
		Map<Integer, RecordDB> validationMap = recordDBs.stream()
				.collect(Collectors.toMap(RecordDB::getId, Function.identity(), (o1, o2) -> o1, TreeMap::new));
		int tns = 0, tps = 0, fps = 0, fns = 0;
		List<String> errors = new ArrayList<>();
		
		for (RecordDB t : truthRecords) {
			RecordDB v = validationMap.get(t.getId());
			v.setValidated(t.isValidated());
			Integer tDedupId = t.getDedupid();
			log.debug("Comparing {} with truth {} and validation {}", t.getId(), t.getDedupid(), v.getDedupid());
			if (tDedupId == null) {
				if (v.getDedupid() == null) {
					v.setTrueNegative(true);
					tns++;
				} else {
					v.setFalsePositive(true);
					fps++;
					v.setCorrection(v.getDedupid());
					errors.add("FALSE POSITIVES: \n- " + t + "\n- " + v + "\n");
				}
			} else {
				if (tDedupId.equals(v.getDedupid())) {
					v.setTruePositive(true);
					tps++;
				} else {
					v.setFalseNegative(true);
					fns++;
					v.setCorrection(t.getDedupid());
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
		String truthFileName = testdir + "/SRA2/BIG_SET_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/BIG_SET.txt";
		String outputFileName = testdir + "/SRA2/BIG_SET_to_validate.txt";

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
