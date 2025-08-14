package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.UtilitiesService;
import edu.dedupendnote.utils.MemoryAppender;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
class MissedDuplicatesTests extends BaseTest {

	public DeduplicationService deduplicationService = new DeduplicationService();

	String homeDir = System.getProperty("user.home");

	private final MemoryAppender memoryAppender = new MemoryAppender();

	String testdir = homeDir + "/dedupendnote_files/";

	String wssessionId = "";

	static Logger logger = null;

	@BeforeAll
	static void setup() {
		logger = (Logger) LoggerFactory.getLogger("edu.dedupendnote.services.DeduplicationService");
		logger.setLevel(Level.DEBUG);
	}

	/*
	 * For each source in the @ParameterizedTest a new memoryAppender is added.
	 * 
	 * Trying to reuse the memoryAppender (even by giving it a name) doesn't work.
	 */
	@BeforeEach
	void addMemoryAppender() {
		// memoryAppender.setName("The_memoryAppender");
		// String appenderName = memoryAppender.getName();
		// System.err.println("MemoryAppender with name " + appenderName);
		logger.addAppender(memoryAppender);
		memoryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
		memoryAppender.start();
	}

	@ParameterizedTest
	@CsvSource({ "'/own/missed_duplicates/9165.txt', 2, 2", "'/own/missed_duplicates/Rofo.txt', 3, 1",
			// Solved: authors in ALL are treated better
			"'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_52927.txt', 2, 1",
	// "'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_missed_1.txt', 6, 2", // Cochrane
	// "'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_missed_2.txt', 4, 2", // Cochrane
	// "'/problems/BIG_SET_missed_1.txt', 3, 1", "'/problems/BIG_SET_missed_2.txt', 3, 1",
	// "'/problems/BIG_SET_missed_3.txt', 3, 1", "'/problems/TIL_missed_duplicates.txt', 3, 1",
	// "'/problems/TIL_missed_duplicates_2.txt', 3, 1", // different pages, same DOI
	// "'/problems/TIL_missed_duplicates_3.txt', 4, 1", // SOLVED: same pages and DOI, different journal
	// "'/problems/TIL_missed_duplicates_3.txt', 4, 1", // SOLVED: same pages and DOI, different journal
	})
	void deduplicateMissedDuplicates(String fileName, int total, int totalWritten) {
		log.debug("Log level should be debug");
		String inputFileName = testdir + fileName;
		boolean markMode = false;
		String outputFileName = UtilitiesService.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode,
				wssessionId);

		// System.err.println("Number of events " + memoryAppender.getSize());
		// assertThat(memoryAppender.contains("1. Starting pages are the same", Level.DEBUG)).isTrue();
		// Pattern pattern = Pattern.compile("- (1|2|3|4). .+");
		// System.err.println("Messages: " + memoryAppender.filterByPattern(pattern, Level.DEBUG));
		// assertThat(memoryAppender.filterByPattern(Pattern.compile(".+ (1|2|3|4). .+"), Level.DEBUG).size())
		// .isGreaterThan(0);
		// assertThat(memoryAppender.containsPattern(Pattern.compile(".+ (1|2|3|4). .+"), Level.DEBUG)).isTrue();

		System.err.println("Messages: " + memoryAppender.filterByPatterns(patterns, Level.DEBUG));
		assertThat(memoryAppender.filterByPatterns(patterns, Level.DEBUG).size()).isGreaterThan(0);
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(total, totalWritten));
	}

	List<Pattern> patterns = List.of(Pattern.compile("- (1|2|3|4). .+"),
			Pattern.compile("\\d+ - \\d+ ARE (NOT )?DUPLICATES"));

	// FIXME: tests for markMode = true;

}
