package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
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
public class MissedDuplicatesTests {
	public DeduplicationService deduplicationService = new DeduplicationService();
	String homeDir = System.getProperty("user.home");
	String testdir = homeDir + "/dedupendnote_files/";
	String wssessionId = "";

	@BeforeAll
	static void beforeAll() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote");
		rootLogger.setLevel(Level.DEBUG);
		log.debug("Logging level set to DEBUG");
	}
	
	@Test
	void deduplicate_missed_BIG_SET() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote.services");
		rootLogger.setLevel(Level.DEBUG);
		log.debug("Logging level set to DEBUG");

		String inputFileName = testdir + "/own/missed_duplicates/9165.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteController.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, wssessionId);
		
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(2, 2));
	}

	@Test
	void deduplicate_missed_BIG_SET_Rofo() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote.services");
		rootLogger.setLevel(Level.DEBUG);
		log.debug("Logging level set to DEBUG");

		String inputFileName = testdir + "/own/missed_duplicates/Rofo.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteController.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, wssessionId);
		
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(3, 1));
	}

	
	@Disabled("Solved: authors in ALL CAPS are treated better")
	@Test
	void deduplicate_missed_SRSR_Human() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote.services");
		rootLogger.setLevel(Level.DEBUG);
		log.debug("Logging level set to DEBUG");

		String inputFileName = testdir + "/ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_52927.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteController.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, wssessionId);
		
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(2, 2));
	}


	// FIXME: tests for markMode = true;
}
