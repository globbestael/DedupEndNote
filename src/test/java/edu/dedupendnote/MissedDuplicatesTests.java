package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.controllers.DedupEndNoteController;
import edu.dedupendnote.services.DeduplicationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
class MissedDuplicatesTests {

	public DeduplicationService deduplicationService = new DeduplicationService();

	String homeDir = System.getProperty("user.home");

	String testdir = homeDir + "/dedupendnote_files/";

	String wssessionId = "";

	@ParameterizedTest
	@CsvSource({ "'/own/missed_duplicates/9165.txt', 2, 2", "'/own/missed_duplicates/Rofo.txt', 3, 1",
			"'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_52927.txt', 2, 1", // Solved: authors in ALL are
																						// treated better
			"'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_missed_1.txt', 6, 2", // Cochrane
			"'/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_missed_2.txt', 4, 2", // Cochrane
			"'/problems/BIG_SET_missed_1.txt', 3, 1", "'/problems/BIG_SET_missed_2.txt', 3, 1",
			"'/problems/BIG_SET_missed_3.txt', 3, 1", "'/problems/TIL_missed_duplicates.txt', 3, 1",
			"'/problems/TIL_missed_duplicates_2.txt', 3, 1", // different pages, same DOI
			"'/problems/TIL_missed_duplicates_3.txt', 4, 1", // SOLVED: same pages and DOI, different journal
			"'/problems/TIL_missed_duplicates_3.txt', 4, 1", // SOLVED: same pages and DOI, different journal
	})
	void deduplicateMissedDuplicates(String fileName, int total, int totalWritten) {
		setLoggerToDebug();
		String inputFileName = testdir + fileName;
		boolean markMode = false;
		String outputFileName = DedupEndNoteController.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode,
				wssessionId);

		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(total, totalWritten));
	}

	private void setLoggerToDebug() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote.services");
		rootLogger.setLevel(Level.DEBUG);
		log.debug("Logging level set to DEBUG");
	}

	// FIXME: tests for markMode = true;

}
