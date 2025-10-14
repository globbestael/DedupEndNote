package edu.dedupendnote.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
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
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.PublicationDB;
import edu.dedupendnote.domain.ValidationResult;
import edu.dedupendnote.domain.ValidationResultASySD;
import edu.dedupendnote.utils.MemoryAppender;
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
class ValidationTests {
	// temporary
	public Map<String, Integer> titleCounter = new HashMap<>();
	NormalizationService normalizationService = new NormalizationService();
	ComparatorService comparatorService = new ComparatorService();
	DeduplicationService deduplicationService = new DeduplicationService(normalizationService, comparatorService);

	IOService ioService = new IOService(normalizationService);

	RecordDBService recordDBService = new RecordDBService();

	String homeDir = System.getProperty("user.home");

	String testdir = homeDir + "/dedupendnote_files";

	String wssessionId = "";

	private static Logger rootLogger;

	private boolean withTracing = false;
	private boolean withTitleSplitterOutput = false;

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
		// previous results
		Map<String, ValidationResult> validationResultsMap = List
				.of(
					new ValidationResult("AI_subset", 491, 0, 2590, 2, 29_000L),	// why so slow?
					new ValidationResult("ASySD_Cardiac_human", 6756, 17, 2175, 0, 3_700L),
					new ValidationResult("ASySD_Diabetes", 1816, 18, 11, 0, 1_000L),
					new ValidationResult("ASySD_Neuroimaging", 2179, 22, 1234, 3, 1_350L),
					new ValidationResult("ASySD_SRSR_Human", 27918, 99, 24973, 11, 100_000L),
					new ValidationResult("BIG_SET", 3937, 176, 959, 10, 66_000L),
					new ValidationResult("Clinical_trials", 219, 0, 0, 0, 190L),
					new ValidationResult("McKeown_2021", 2018, 56, 1056, 0, 800L),
					new ValidationResult("SRA2_Cytology_screening", 1361, 59, 436, 0, 400L),
					new ValidationResult("SRA2_Haematology", 225, 11, 1177, 2, 300L),
					new ValidationResult("SRA2_Respiratory", 766, 34, 1184, 4, 800L),
					new ValidationResult("SRA2_Stroke", 497, 13, 782, 0, 320L),
					new ValidationResult("TIL", 691, 11, 390, 0, 9_000L),
					new ValidationResult("TIL_Zotero", 685, 17, 389, 1, 9_000L))
				.stream().collect(Collectors.toMap(ValidationResult::getFileName, Function.identity(), (o1, o2) -> o1,
						TreeMap::new));
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
		String divider = "|---------|--------------|---------|---------|--------------|---------|---------|--------------|--------------|--------------|--------------|-------------|";
		for (String setName : resultsMap.keySet()) {
			ValidationResult v = validationResultsMap.get(setName);
			ValidationResult c = resultsMap.get(setName);
			if (c == null) {
				continue; // easy when some checkResults_...() are commented out
			}
			int tp = c.getTp(), fn = c.getFn(), tn = c.getTn(), fp = c.getFp();
			double precision = tp * 100.0 / (tp + fp);
			double sensitivity = tp * 100.0 / (tp + fn); // == recall
			double specificity = tn * 100.0 / (tn + fp);
			double accuracy = (tp + tn) * 100.0 / (tp + fn + tn + fp);
			double f1Score = 2 * precision * sensitivity / (precision + sensitivity);
			if (v.getFn() == c.getFn() && v.getFp() == c.getFp() && v.getTn() == c.getTn() && v.getTp() == c.getTp()
					&& (c.getDurationMilliseconds() >= (long) (v.getDurationMilliseconds() * 0.9))
					&& c.getDurationMilliseconds() <= (long) (v.getDurationMilliseconds() * 1.1)) {
				System.out.println("\nResults: " + setName);
				System.out.println("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s | %12s | %12s | %12s | %11s |"
						.formatted("TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity",
								"Precision", "Accuracy", "F1-score", "Duration"));
				System.out.println(divider);
				System.out.println(
						"| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% | %11.3f%% | %11.2f |"
								.formatted(tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn,
										sensitivity, tn, fp, specificity, precision, accuracy, f1Score,
										(double) (c.getDurationMilliseconds() / 1000.0)));
				System.out.flush();
			} else {
				changed = true;
				System.err.println("\nResults: " + setName + ": HAS DIFFERENT RESULTS (first new, second old)");
				System.err.println("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s | %12s | %12s | %12s | %11s |"
						.formatted("TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity",
								"Precision", "Accuracy", "F1-score", "Duration"));
				System.out.println(divider);
				System.err.println(
						"| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% | %11.3f%% | %11.2f |"
								.formatted(tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn,
										sensitivity, tn, fp, specificity, precision, accuracy, f1Score,
										(double) (c.getDurationMilliseconds() / 1000.0)));
				tp = v.getTp();
				fn = v.getFn();
				tn = v.getTn();
				fp = v.getFp();
				precision = tp * 100.0 / (tp + fp);
				sensitivity = tp * 100.0 / (tp + fn); // == recall
				specificity = tn * 100.0 / (tn + fp);
				accuracy = (tp + tn) * 100.0 / (tp + fn + tn + fp);
				f1Score = 2 * precision * sensitivity / (precision + sensitivity);
				System.err.println(
						"| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% | %11.3f%% | %11.2f |"
								.formatted(tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn,
										sensitivity, tn, fp, specificity, precision, accuracy, f1Score,
										(double) (v.getDurationMilliseconds() / 1000.0)));
				System.err.flush();
			}
		}
		System.out.println("FP can be found with regex: \\ttrue\\tfalse\\tfalse\\ttrue\\tfalse\\t");
		System.out.println("FN can be found with regex: \\d\\ttrue\\tfalse\\tfalse\\tfalse\\ttrue\\t");
		System.out.println(
				"FN solvable can be found with regex: ^\\d+\\t\\t\\d+\\ttrue\\tfalse\\tfalse\\tfalse\\ttrue\\tfalse");
		System.out.println("TP which will be kept can be found with regex: ^(\\d+)\\t\\1\\t\\ttrue\\ttrue\\t");

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

		assertThat(changed).isFalse();
	}

	/*
	 * TODO: extend the function with real checkResults as in checkAllTruthFiles()? TODO:
	 * extract validationResultsMap to avoid duplication.
	 */
	@Test
	void printValidationResultsASySD() {

		/*
		 * FIXME: the number of unique duplicates has to be from the ..._to_validate.txt
		 * files.
		 */

		// @formatter:off
		Map<String, ValidationResultASySD> validationResultsMap = List
				.of(
					new ValidationResultASySD("AI_subset", 491, 0, 2590, 2, 210),	// why so slow?
					new ValidationResultASySD("Cytology_screening", 1361, 59, 436, 0, 623),
					new ValidationResultASySD("Cytology_screening", 1359, 61, 436, 0, 623),
					new ValidationResultASySD("Haematology", 225, 11, 1177, 2, 107),
					new ValidationResultASySD("Respiratory", 766, 34, 1184, 4, 354),
					new ValidationResultASySD("Stroke", 497, 13, 782, 0, 190),
					new ValidationResultASySD("BIG_SET", 3937, 176, 959, 10, 1433),
					new ValidationResultASySD("Clinical_trials", 219, 0, 0, 0, 87),
					new ValidationResultASySD("McKeown_2021", 2018, 56, 1056, 0, 817),
					new ValidationResultASySD("ASySD_Cardiac_human", 6753, 20, 2175, 0, 3235),
					// new ValidationResultASySD("ASySD_Depression", 17389, 576, 61894, 21, 7705),
					new ValidationResultASySD("ASySD_Diabetes", 1816, 18, 11, 0, 566),
					new ValidationResultASySD("ASySD_Neuroimaging", 2172, 29, 1235, 2, 891),
					new ValidationResultASySD("ASySD_SRSR_Human", 27897, 120, 24975, 9, 11111),
					new ValidationResultASySD("TIL", 687, 15, 390, 0, 258),
					new ValidationResultASySD("TIL_Zotero", 687, 15, 390, 0, 258)
				)
				.stream().collect(Collectors.toMap(ValidationResultASySD::getFileName, Function.identity(),
						(o1, o2) -> o1, TreeMap::new));
		// @formatter:on

		for (String setName : validationResultsMap.keySet()) {
			ValidationResultASySD v = validationResultsMap.get(setName);
			int tp = v.getTp() - v.getUniqueDuplicates(), fn = v.getFn(), tn = v.getTn() + v.getUniqueDuplicates(),
					fp = v.getFp();
			double precision = tp * 100.0 / (tp + fp);
			double sensitivity = tp * 100.0 / (tp + fn);
			System.out.println("\nResults: " + setName);
			System.out.println(
					"---------------------------------------------------------------------------------------------------------------------------------------------");
			System.out.println("| %7s | %12s | %7s | %7s | %12s | %7s | %7s | %12s | %12s | %12s | %12s |".formatted(
					"TOTAL", "% duplicates", "TP", "FN", "Sensitivity", "TN", "FP", "Specificity", "Precision",
					"Accuracy", "F1"));
			System.out.println(
					"| %7d | %11.2f%% | %7d | %7d | %11.2f%% | %7d | %7d | %11.3f%% | %11.3f%% | %11.3f%% | %11.3f%% |"
							.formatted(tp + tn + fp + fn, (tp + fn) * 100.0 / (tp + tn + fp + fn), tp, fn, sensitivity,
									tn, fp, tn * 100.0 / (tn + fp), precision, (tp + tn) * 100.0 / (tp + fn + tn + fp),
									2 * precision * sensitivity / (precision + sensitivity)));
			System.out.println(
					"---------------------------------------------------------------------------------------------------------------------------------------------");
			System.out.flush();
			assertThat(1 * 1).isEqualTo(1);
		}
	}

	@Test
	void readTruthFileTest() throws IOException {
		String fileName = testdir + "/SRA2/Cytology_screening_TRUTH.txt";
		List<PublicationDB> truthRecords = readTruthFile(fileName);

		assertThat(truthRecords).hasSizeGreaterThan(10);

		Map<Integer, Set<Integer>> trueDuplicateSets = truthRecords.stream().filter(r -> r.getDedupid() != null)
				// .map(PublicationDB::getDedupid)
				.collect(Collectors.groupingBy(PublicationDB::getDedupid,
						Collectors.mapping(PublicationDB::getId, Collectors.toSet())));
		assertThat(trueDuplicateSets).hasSizeGreaterThan(10);
		trueDuplicateSets.entrySet().stream().limit(10).forEach(System.err::println);
	}

	// @formatter:off
	ValidationResult checkResults(String setName, String inputFileName, String outputFileName, String truthFileName) throws IOException {
		log.error("- Validating {}", setName);
		List<PublicationDB> truthRecords = readTruthFile(truthFileName);
		long startTime = System.currentTimeMillis();
		List<Publication> publications = deduplicate(inputFileName);
		long endTime = System.currentTimeMillis();

		Map<String,Publication> publicationMap = publications.stream().collect(Collectors.toMap(Publication::getId,
                                              Function.identity()));
		List<PublicationDB> publicationDBs = recordDBService.convertToRecordDB(publications, inputFileName);
		Map<Integer, PublicationDB> validationMap = publicationDBs.stream()
				.collect(Collectors.toMap(PublicationDB::getId, Function.identity(), (o1, o2) -> o1, TreeMap::new));
		Map<Integer, Set<Integer>> trueDuplicateSets = truthRecords.stream()
				.filter(r -> r.getDedupid() != null)
				// .map(PublicationDB::getDedupid)
				.collect(Collectors.groupingBy(PublicationDB::getDedupid,
												Collectors.mapping(PublicationDB::getId, Collectors.toSet())));
		int tns = 0, tps = 0, fps = 0, fns = 0;
		List<String> errors = new ArrayList<>();
		Map<Integer, Integer> fpErrors = new HashMap<>();
		List<List<Publication>> fnPairs = new ArrayList<>();
		List<List<Publication>> fpPairs = new ArrayList<>();
		
		for (PublicationDB t : truthRecords) {
			PublicationDB v = validationMap.get(t.getId());
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
					if (! t.getId().equals(tDedupId)) {
						List<Publication> pair = new ArrayList<>();
						pair.add(publicationMap.get(t.getId().toString()));
						pair.add(publicationMap.get(tDedupId.toString()));
						fnPairs.add(pair);
					}
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
				if (! v.getId().equals(vDedupId)) {
					List<Publication> pair = new ArrayList<>();
					pair.add(publicationMap.get(v.getId().toString()));
					pair.add(publicationMap.get(vDedupId.toString()));
					fpPairs.add(pair);
				}
			}
		}
		recordDBService.saveRecordDBs(publicationDBs, outputFileName);
		ValidationResult validationResult = new ValidationResult(setName, tps, fns, tns, fps, endTime - startTime);
		if (withTracing) {
				/*
				 * There may be records in the output file with "... - ... ARE DUPLICATES" but with publication years
				 * which are more than 1 year apart, 
				 * because the test of the pair does not look at the publication years
				 */
			new File(inputFileName + "_FP_Analysis.txt").delete();	
			new File(inputFileName + "_FN_Analysis.txt").delete();	
			if (! fnPairs.isEmpty()) {
				validationResult.setFnPairs(fnPairs);
				writeFNandFPresults(fnPairs, inputFileName + "_FN_Analysis.txt");
			}
			if (! fpPairs.isEmpty()) {
				/*
				 * There may be records in the output file with the same DOI but an error in the journal and/or pages
				 */
				validationResult.setFpPairs(fpPairs);
				writeFNandFPresults(fpPairs, inputFileName + "_FP_Analysis.txt");
			}
		}
		if (! errors.isEmpty()) {
			System.err.println("File " + setName +  " has FALSE POSITIVES!");
			errors.stream().forEach(System.err::println);
			System.err.println("These are the FP recordIDs for " + setName);
			fpErrors.entrySet().forEach(System.err::println);
		}
		long uniqueDuplicates = publicationDBs.stream()
				.filter(r -> r.isTruePositive() == true && r.getDedupid().equals(r.getId()))
				.count();

		System.err.println("File " + setName +  " has unique duplicates: " + uniqueDuplicates);

		if (withTitleSplitterOutput) {
			for (Publication p : publications) {
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
	
	List<Pattern> tracePatterns = List.of(Pattern.compile("- (1|2|3|4). .+"),
			Pattern.compile("\\d+ - \\d+ ARE (NOT )?DUPLICATES"));

	private void writeFNandFPresults(List<List<Publication>> pairs, String outputFileName) {
		Logger logger = null;
		Level oldLevel = null;

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName))) {
			logger = (Logger) LoggerFactory.getLogger("edu.dedupendnote.services.DeduplicationService");
			oldLevel = logger.getLevel();
			logger.setLevel(Level.TRACE);
			MemoryAppender memoryAppender = new MemoryAppender();
			logger.addAppender(memoryAppender);
			memoryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
			memoryAppender.start();

			for (List<Publication> pair : pairs) {
				bw.write(pair.get(0).toString());
				bw.write("\n");
				if (pair.size() < 2) {
					bw.write("Pair contains only 1 record");
				} else {
					bw.write(pair.get(1).toString());
				}

				// deduplicate pair after writing because deduplication alter the pair
				deduplicationService.compareSet(pair, pair.get(0).getPublicationYear(), true, "dummy");

				bw.write("\nANALYSIS:\n");
				for (String s : memoryAppender.filterByPatterns(tracePatterns, Level.TRACE)) {
					bw.write(s + "\n");
				}
				bw.write("\n=======================\n");

				memoryAppender.reset();
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			logger.setLevel(oldLevel);
		}
	}

	ValidationResult checkResults_AI_subset() throws IOException {
		String truthFileName = testdir + "/AI_subset/AI_subset_TRUTH.txt";
		String inputFileName = testdir + "/AI_subset/AI_subset.txt";
		String outputFileName = testdir + "/AI_subset/AI_subset_to_validate.txt";

		return checkResults("AI_subset", inputFileName, outputFileName, truthFileName);
	}

	ValidationResult checkResults_ASySD_Cardiac_human() throws IOException {
		String truthFileName = testdir + "/ASySD/dedupendnote_files/Cardiac_human_TRUTH.txt";
		String inputFileName = testdir + "/ASySD/dedupendnote_files/Cardiac_human.txt";
		String outputFileName = testdir + "/ASySD/dedupendnote_files/Cardiac_human_to_validate.txt";

		return checkResults("ASySD_Cardiac_human", inputFileName, outputFileName, truthFileName);
	}

	// ValidationResult checkResults_ASySD_Depression() throws IOException {
	// 	String truthFileName = testdir + "/ASySD/dedupendnote_files/Depression_TRUTH.txt";
	// 	String inputFileName = testdir + "/ASySD/dedupendnote_files/Depression.txt";
	// 	String outputFileName = testdir + "/ASySD/dedupendnote_files/Depression_to_validate.txt";

	// 	return checkResults("ASySD_Depression", inputFileName, outputFileName, truthFileName);
	// }
	
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

	ValidationResult checkResults_Clinical_trials() throws IOException {
		String truthFileName = testdir + "/Clinical_trials/clinicaltrialsdotgov_TRUTH.txt";
		String inputFileName = testdir + "/Clinical_trials/clinicaltrialsdotgov.txt";
		String outputFileName = testdir + "/Clinical_trials/clinicaltrialsdotgov_to_validate.txt";

		return checkResults("Clinical_trials", inputFileName, outputFileName, truthFileName);
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
	
	ValidationResult checkResults_TIL() throws IOException {
		String truthFileName = testdir + "/TIL/TIL_TRUTH.txt";
		String inputFileName = testdir + "/TIL/TIL.txt";
		String outputFileName = testdir + "/TIL/TIL_to_validate.txt";

		return checkResults("TIL", inputFileName, outputFileName, truthFileName);
	}
	
	ValidationResult checkResults_TIL_Zotero() throws IOException {
		String truthFileName = testdir + "/TIL/TIL_TRUTH.txt";
		String inputFileName = testdir + "/TIL/TIL_Zotero.ris";
		String outputFileName = testdir + "/TIL/TIL_Zotero_to_validate.txt";

		return checkResults("TIL_Zotero", inputFileName, outputFileName, truthFileName);
	}

	/*
	 * Test files only needed to create an initial TRUTH file (unvalidated).
	 * Result should be imported into a database and marked for validation there.
	 */

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_AI_subset() {
		String inputFileName = testdir + "/AI_subset/AI_subset.txt";
		String outputFileName = testdir + "/AI_subset/AI_subset_for_truth.txt";
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
	
	// @Disabled("Only needed for initialisation of TRUTH file")
	// @Test
	// void createInitialTruthFile_ASySD_Depression() {
	// 	String dir = testdir + "/ASySD/dedupendnote_files";
	// 	String inputFileName = dir + "/Depression.txt";
	// 	String asysdInputfileName = dir + "/Depression_asysd_gold.txt";
	// 	String outputFileName = dir + "/Depression_for_truth.txt";
	// 	createInitialTruthFile(inputFileName, asysdInputfileName, outputFileName);
	// }
	
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
	void createInitialTruthFile_CTG() {
		String dir = testdir + "/clinical_trials";
		String inputFileName = dir + "/clinicaltrialsdotgov.txt";
		String outputFileName = dir + "/clinicaltrialsdotgov_for_truth.txt";
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

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_TIL() {
		String inputFileName = testdir + "/TIL/TIL.txt";
		String outputFileName = testdir + "/TIL/TIL_for_truth.txt";
		createInitialTruthFile(inputFileName, outputFileName);
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createInitialTruthFile_TIL_Zotero() {
		String inputFileName = testdir + "/TIL/TIL_Zotero.ris";
		// uses the same TRUTH file as createInitialTruthFile_TIL
		String outputFileName = testdir + "/TIL/TIL_Zotero_for_truth.txt";
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
	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createRisWithTRUTH_BIG_SET_DS() throws IOException {
		String truthFileName = testdir + "/own/BIG_SET_TRUTH.txt";
		String inputFileName = testdir + "/Dedupe-sweep/dedupendnote_files/BIG_SET_mark_DS.txt";
		String outputFileName = testdir + "/Dedupe-sweep/dedupendnote_files/BIG_SET_mark_DS_with_TRUTH.txt";

		List<PublicationDB> truthRecords = readTruthFile(truthFileName);
		ioService.writeRisWithTRUTH_forDS(truthRecords, inputFileName, outputFileName);

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
		String truthFileName = testdir + "/own/BIG_SET_TRUTH.txt";
		String inputFileName = testdir + "/own/BIG_SET.txt";
		String outputFileName = testdir + "/own/BIG_SET_with_TRUTH.txt";

		createRisWithTRUTH(inputFileName, truthFileName, outputFileName);

		assertThat(1*1).isEqualTo(1);
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	void createRisWithTRUTH_SRA2_Cytology_screening() throws IOException {
		String truthFileName = testdir + "/SRA2/Cytology_screening_TRUTH.txt";
		String inputFileName = testdir + "/SRA2/Cytology_screening.txt";
		String outputFileName = testdir + "/SRA2/Cytology_screening_with_TRUTH.txt";

		createRisWithTRUTH(inputFileName, truthFileName, outputFileName);
	}

	@Disabled("Only needed for initialisation of TRUTH file")
	@Test
	private void createRisWithTRUTH(String inputFileName, String truthFileName, String outputFileName) throws IOException {
		List<PublicationDB> truthRecords = readTruthFile(truthFileName);
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
		List<Publication> publications = deduplicate(inputFileName);
		List<PublicationDB> publicationDBs = recordDBService.convertToRecordDB(publications, inputFileName);
		recordDBService.saveRecordDBs(publicationDBs, outputFileName);
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
		List<Publication> publications = deduplicate(inputFileName);
		List<PublicationDB> publicationDBs = recordDBService.convertToRecordDB(publications, inputFileName);
		
		publicationDBs.forEach(r -> {
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
		recordDBService.saveRecordDBs(publicationDBs, outputFileName);
	}
	
	private Map<Integer, Set<Integer>> readASySDGoldFile(String asysdInputfileName) {
		System.err.println("Start");
		List<String> lines = Collections.emptyList();
		try {
			lines = Files.readAllLines(Path.of(asysdInputfileName));
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

	private List<Publication> deduplicate(String inputFileName) {
		List<Publication> publications = ioService.readPublications(inputFileName);

		String s = deduplicationService.doSanityChecks(publications, inputFileName);
		if (s != null) {
			fail(s);
		}

		deduplicationService.searchYearOneFile(publications, wssessionId);
		return publications;
	}

	List<PublicationDB> readTruthFile(String fileName) throws IOException {
	    Path path = Path.of(fileName);

		CsvMapper mapper = new CsvMapper();
		CsvSchema schema = mapper
				.schemaFor(PublicationDB.class)
				.withHeader()
				.withColumnSeparator('\t')
				.withLineSeparator("\n");
		MappingIterator<PublicationDB> it = mapper
				.readerFor(PublicationDB.class)
				.with(schema)
				.with(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
				.readValues(path.toFile());
		return it.readAll();
	}

}
