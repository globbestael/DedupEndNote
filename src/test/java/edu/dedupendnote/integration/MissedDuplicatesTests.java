package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.integration.utils.MemoryAppender;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class MissedDuplicatesTests extends AbstractIntegrationTest {
	@Autowired
	DeduplicationService deduplicationService;

	private final MemoryAppender memoryAppender = new MemoryAppender();

	//	static Logger logger = null;

	List<Pattern> tracePatterns = List.of(Pattern.compile("- (1|2|3|4). .+"),
			Pattern.compile("\\d+ - \\d+ ARE (NOT )?DUPLICATES"));

	/*
	 * For each source in the @ParameterizedTest a new memoryAppender is added.
	 *
	 * Trying to reuse the memoryAppender (even by giving it a name) doesn't work.
	 *
	 * FIXME: There is a big overlap with ValidationTests::writeFNandFPresults in initialization of the memoryAppender
	 */
	@BeforeEach
	void initTestDir() {
		testDir = baseDir.resolve("integration/missed_duplicates");
	}

	@BeforeEach
	void addMemoryAppender() {
		List<Logger> loggers = new ArrayList<>();
		List<String> loggerNames = List.of("edu.dedupendnote.services.DeduplicationService",
				"edu.dedupendnote.services.DefaultAuthorsComparisonService");
		// Level oldLevel = null;

		for (String loggerName : loggerNames) {
			Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
			// oldLevel = logger.getLevel();
			logger.setLevel(Level.TRACE);
			logger.addAppender(memoryAppender);
			loggers.add(logger);
		}
		memoryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
		memoryAppender.start();
	}

	// @formatter:off

	/*
	 * Solved cases
	 *
		// "'AI_Query_2022_missed_duplicates_1.txt', 2, 1",
		// "'AI_Query_2022_missed_duplicates_2.txt', 4, 1",
		// "'AI_Query_2022_missed_duplicates_3.txt', 2, 1",
		// "'AI_Query_2022_missed_duplicates_4.txt', 2, 2", // title too different
		// "'AI_Query_2022_missed_duplicates_5.txt', 2, 2", // ISSN same, ISBN different
		// "'BIG_SET_9165.txt', 2, 1", // solved
		// "'BIG_SET_Rofo.txt', 3, 1",
		// "'BIG_SET_chinese_and_english_title.txt', 2, 1", // 1 ST in Chinese, 1 ST and 2 TI in English, 1 T3 = '-1'
		// "'BIG_SET_SP_C7_none.txt', 3, 1", // after refactoring pages: record with SP, C7 and none
		// "'BIG_SET_missed_3.txt', 3, 2", 
		// "'SRSR_Human_52927.txt', 2, 1",
		// "'BIG_SET_missed_1.txt', 4, 1", 
		// "'BIG_SET_missed_2.txt', 3, 1",
		// "'Cardiac_Human_missed_duplicates_1.txt', 2, 2",
		// "'Rayyan_missed_251_252.txt', 2, 2", // SOLVED: Rayyan: both isSeveralPages = false
		// "'Neuroimaging_missed_1.txt', 2, 2",
		// "'Semaglutide_wrong_duplicates.txt', 4, 2",
		// "'SRSR_Human_missed_1.txt', 6, 2", // Cochrane
		// "'SRSR_Human_missed_3.txt', 2, 2", // book chapters
		// "'SRSR_Human_missed_4.txt', 2, 2", // book chapters 	
		// "'test805_missed_duplicates_1.txt', 2, 1", 
		// "'TIL_missed_duplicates.txt', 3, 1",
		// "'TIL_missed_duplicates_2.txt', 3, 1", // different pages, same DOI
		// "'TIL_missed_duplicates_3.txt', 4, 1", // SOLVED: same pages and DOI, different journal
		// "'TIL_Zotero_missed_duplicates_1.txt', 5, 2",
		// "'Wilson_Emma_Birtele_M.txt', 2, 2" // different pages
	 */
	@ParameterizedTest
	@CsvSource({

		/*
		 * Originally
		 * False positives or negatives for these 2 versions of Cochrane review CD006069
		 * 20282, 20456, 36223, 36439, 51545, 51546
		 * The first 4 (in SRSR_Human_missed_2_4.txt) merges 20456 to 20282 
		 * All 6 (in SRSR_Human_missed_2_4.txt) merges 20456 to 20282, and keeps 51546 as a second set with 1 bibliographicItem
		 * 
		 * The false merge of 20456 with 20282 is triggered by the comparison of 20456 with 36459
		 * - 1. with doi / without doi
		 * - 1. same SP
		 * - 2. same AU
		 * - 3. same TI
		 * - 4. different ISSN
		 * - 4. same journal
		 * And then the label of THE COMPARED JOURNAL (36459, label 20282) is copied to THE PIVOT (20456)"
		 * 
		 * On 2025-12-18 copying the label from the bibliographicItem to the pivot has been disabled
		 */
		"'Rayyan_missed_10_11.txt', 2, 1",
		"'SRSR_Human_missed_2_4.txt', 4, 2", // Cochrane
		"'SRSR_Human_missed_2_6.txt', 6, 3", // Cochrane
	})
	// @formatter:on
	void deduplicateMissedDuplicates(String fileName, int total, int totalWritten) {
		log.debug("Log level should be debug");
		Path inputPath = testDir.resolve(fileName);
		DeduplicationMode mode = DeduplicationMode.REMOVE;
		assertThat(inputPath).exists();
		deleteDerivedOutputs(inputPath);

		String resultString = deduplicationService.deduplicateOneFile(inputPath, mode, message -> {
		});

		System.err.println("Messages: " + memoryAppender.filterByPatterns(tracePatterns, Level.TRACE));
		assertThat(memoryAppender.filterByPatterns(tracePatterns, Level.TRACE).size()).isGreaterThan(0);
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(total, totalWritten));
	}

	// FIXME: tests for mode = true;

}
