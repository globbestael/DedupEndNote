package edu.dedupendnote;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
public abstract class AbstractIntegrationTest {

	@MockitoBean
	protected SimpMessagingTemplate simpMessagingTemplate;

	protected String baseDir = System.getProperty("user.home", "") + "/dedupendnote_files";

	protected String testDir = "";

	protected String wssessionId = "";

	@BeforeAll
	static void setLogLevelToInfo() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote");
		rootLogger.setLevel(Level.INFO);
		log.debug("Logging level set to INFO");
	}

	@BeforeEach
	void initTestDir() {
		testDir = baseDir;
	}
}
