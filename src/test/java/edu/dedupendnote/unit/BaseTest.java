package edu.dedupendnote.unit;

import java.nio.file.Path;
import java.util.List;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.TestData;
import edu.dedupendnote.TestDataExtension;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ExtendWith(TestDataExtension.class)
public class BaseTest {
	/*
		The location of the test data comes from TestData (plain Java, because unit tests don't start a Spring
		context). Same initialization in AbstractIntegrationTest. TestDataExtension fails fast when the
		directory is missing or older than the version pinned in testdata.ref.
	 */
	protected Path baseDir = TestData.baseDir();
	protected Path testDir = baseDir;

	protected JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	protected Double getHighestSimilarityForAuthors(List<String> listAuthors1, List<String> listAuthors2) {
		Double highestSimilarity = 0.0;

		for (String authors1 : listAuthors1) {
			for (String authors2 : listAuthors2) {
				Double similarity = jws.apply(authors1, authors2);
				if (similarity > highestSimilarity) {
					highestSimilarity = similarity;
				}
			}
		}
		return highestSimilarity;
	}

	protected Logger setLoggerToDebug(String loggerName) {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger logger = loggerContext.getLogger(loggerName);
		logger.setLevel(Level.DEBUG);
		// log.debug("Logging level set to DEBUG for {}", loggerName);
		// logger.debug("Logging level set to DEBUG for logger {}", loggerName);
		return logger;
		/*
		 * Programmatically change logback configuration:
		 * - https://stackoverflow.com/questions/16910955/programmatically-configure-logback-appender
		 * - https://akhikhl.wordpress.com/2013/07/11/programmatic-configuration-of-slf4jlogback/
		 * - https://www.baeldung.com/junit-asserting-logs
		 */
	}

}
