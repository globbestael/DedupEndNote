package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.services.DeduplicationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public class MissedDuplicatesTests {
	public DeduplicationService deduplicationService = new DeduplicationService();
	@Value("${testdir}")
	private String testdir;

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
		String outputFileName = DedupEndNoteApplication.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, new MockHttpSession());
		
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(2, 2));
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
		String outputFileName = DedupEndNoteApplication.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, new MockHttpSession());
		
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(2, 2));
	}


	// FIXME: tests for markMode = true;
}
