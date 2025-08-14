package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
public class BaseTest {

	protected JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	protected Double getHighestSimilarity(List<String> listAuthors1, List<String> listAuthors2) {
		Double highestSimilarity = 0.0;

		for (String authors1 : listAuthors1) {
			for (String authors2 : listAuthors2) {
				Double distance = jws.apply(authors1, authors2);
				if (distance > highestSimilarity) {
					highestSimilarity = distance;
				}
			}
		}
		return highestSimilarity;
	}

	protected void setLoggerToDebug(String loggerName) {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger logger = loggerContext.getLogger(loggerName);
		logger.setLevel(Level.DEBUG);
		// log.debug("Logging level set to DEBUG for {}", loggerName);
		// logger.debug("Logging level set to DEBUG for logger {}", loggerName);

		/*
		 * Programmatically change logback configuration:
		 * - https://stackoverflow.com/questions/16910955/programmatically-configure-logback-appender
		 * - https://akhikhl.wordpress.com/2013/07/11/programmatic-configuration-of-slf4jlogback/
		 */
	}

}
