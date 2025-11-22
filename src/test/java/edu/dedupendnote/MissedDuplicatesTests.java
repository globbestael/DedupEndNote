package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.UtilitiesService;
import edu.dedupendnote.utils.MemoryAppender;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
class MissedDuplicatesTests extends BaseTest {
	@Autowired
	DeduplicationService deduplicationService;

	@MockitoBean
	SimpMessagingTemplate simpMessagingTemplate;

	private final MemoryAppender memoryAppender = new MemoryAppender();

	String homeDir = System.getProperty("user.home");
	String testdir = homeDir + "/dedupendnote_files/";
	String wssessionId = "";

	static Logger logger = null;

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
	void addMemoryAppender() {
		List<Logger> loggers = new ArrayList<>();
		List<String> loggerNames = List.of("edu.dedupendnote.services.DeduplicationService",
				"edu.dedupendnote.services.ComparisonService",
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
	 * 		//  "'/own/missed_duplicates/9165.txt', 2, 1", // solved
		//  "'/own/missed_duplicates/Rofo.txt', 3, 1",
		// Solved: authors in ALL are treated better
		// "'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_52927.txt', 2, 1",
		// "'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_missed_2.txt', 4, 1", // Cochrane, solved
		// "'/problems/AI_Query_2022_missed_duplicates_1.txt', 2, 1",
		// "'/problems/AI_Query_2022_missed_duplicates_2.txt', 4, 1",
		// "'/problems/AI_Query_2022_missed_duplicates_3.txt', 2, 1",
		// "'/problems/BIG_SET_missed_1.txt', 4, 1", 
		// "'/problems/BIG_SET_missed_2.txt', 3, 1",
		// "'/problems/TIL_missed_duplicates.txt', 3, 1",
		// "'/problems/TIL_missed_duplicates_3.txt', 4, 1", // SOLVED: same pages and DOI, different journal
		// "'/problems/TIL_missed_duplicates_3.txt', 4, 1", // SOLVED: same pages and DOI, different journal
		// "'Wilson_Emma_2025/missed_duplicates/Birtele_M.txt', 2, 2" // different pages
	 */
	@ParameterizedTest
	@CsvSource({
		// "'/own/missed_duplicates/SP_C7_none.txt', 3, 1", // after refactoring pages: record with SP, C7 and none
		// "'/problems/test805_missed_duplicates_1.txt', 2, 1", 
		// "'/problems/AI_Query_2022_missed_duplicates_4.txt', 2, 2", // title too different
		// "'/problems/AI_Query_2022_missed_duplicates_5.txt', 2, 2", // ISSN same, ISBN different
		// "'/ASySD/dedupendnote_files/missed_duplicates/Cardiac_Human_missed_duplicates_1.txt', 2, 2",
		// "'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_missed_1.txt', 6, 2", // Cochrane
		// "'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_missed_3.txt', 2, 2", // book chapters
		// "'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_missed_4.txt', 2, 2", // book chapters
		"'/ASySD/dedupendnote_files/missed_duplicates/Neuroimaging_missed_1.txt', 2, 2",
		// "'/problems/Semaglutide_wrong_duplicates.txt', 4, 2",
		// "'/problems/BIG_SET_missed_3.txt', 3, 2", 
		// "'/problems/TIL_missed_duplicates_2.txt', 3, 1", // different pages, same DOI
	})
	// @formatter:on
	void deduplicateMissedDuplicates(String fileName, int total, int totalWritten) {
		log.debug("Log level should be debug");
		String inputFileName = testdir + fileName;
		boolean markMode = false;
		String outputFileName = UtilitiesService.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode,
				wssessionId);

		// System.err.println("Number of events " + memoryAppender.getSize());
		// log.error("Number of events {}", memoryAppender.getSize());
		// assertThat(memoryAppender.contains("1. Starting pages are the same", Level.DEBUG)).isTrue();
		// Pattern pattern = Pattern.compile("- (1|2|3|4). .+");
		// System.err.println("Messages: " + memoryAppender.filterByPattern(pattern, Level.DEBUG));
		// assertThat(memoryAppender.filterByPattern(Pattern.compile(".+ (1|2|3|4). .+"), Level.DEBUG).size())
		// .isGreaterThan(0);
		// assertThat(memoryAppender.containsPattern(Pattern.compile(".+ (1|2|3|4). .+"), Level.DEBUG)).isTrue();

		System.err.println("Messages: " + memoryAppender.filterByPatterns(tracePatterns, Level.TRACE));
		assertThat(memoryAppender.filterByPatterns(tracePatterns, Level.TRACE).size()).isGreaterThan(0);
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(total, totalWritten));
	}

	// FIXME: tests for markMode = true;

}
