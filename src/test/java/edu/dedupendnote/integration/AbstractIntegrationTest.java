package edu.dedupendnote.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.services.UtilitiesService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

	@MockitoBean
	protected SimpMessagingTemplate simpMessagingTemplate;

	protected Path baseDir = Path.of(System.getProperty("user.home", "")).resolve("dedupendnote_input_files");

	protected Path testDir = baseDir;

	// Generated-output suffixes. Excludes input/truth suffixes (_TRUTH, _for_truth, _asysd_gold, _with_TRUTH).
	private static final List<String> GENERATED_OUTPUT_SUFFIXES = List.of(
			"_deduplicated", "_mark", "_markDB", "_to_validate",
			"_experimental_to_validate", "_FN_Analysis", "_FP_Analysis");

	/**
	 * Deletes any stale output files derived from {@code inputPath} before a test runs,
	 * so a failed test run cannot leave a previous run's output behind to mislead a developer.
	 * Call this as the first action in each test that writes output files, after the input Path is known.
	 */
	protected void deleteDerivedOutputs(Path inputPath) {
		for (String suffix : GENERATED_OUTPUT_SUFFIXES) {
			Path out = UtilitiesService.createPath(inputPath, suffix, "txt");
			try {
				Files.deleteIfExists(out);
			} catch (IOException e) {
				log.warn("Could not delete stale output file {}", out, e);
			}
		}
	}

	@BeforeAll
	static void setLogLevelToInfo() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote");
		rootLogger.setLevel(Level.INFO);
		log.debug("Logging level set to INFO");
	}

}
