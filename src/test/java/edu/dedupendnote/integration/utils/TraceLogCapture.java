package edu.dedupendnote.integration.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * Captures TRACE-level deduplication trace from a fixed set of loggers into a
 * {@link MemoryAppender}, restoring every logger's original level and detaching
 * the appender on {@link #close()}. Single source of truth for the logger list
 * and the trace-line patterns shared by MissedDuplicatesTests and ValidationService.
 */
public final class TraceLogCapture implements AutoCloseable {

	// @formatter:off
	/** Loggers whose TRACE output makes up a deduplication comparison trace. */
	public static final List<String> TRACE_LOGGER_NAMES = List.of(
		"edu.dedupendnote.services.DeduplicationService",
		"edu.dedupendnote.services.comparison.DefaultAuthorsComparisonService",
		"edu.dedupendnote.services.comparison.DefaultJournalComparisonService",
		"edu.dedupendnote.services.comparison.DefaultPagesComparisonService",
		"edu.dedupendnote.services.comparison.DefaultTitleComparisonService",
		"edu.dedupendnote.validation.services.ValidationService" // emits the step-0 year pre-check trace
	);
	// @formatter:on

	/** Step 0 = year pre-check (years too far apart); steps 1-4 = the comparison algorithm. */
	public static final List<Pattern> TRACE_PATTERNS = List.of(Pattern.compile("- (0|1|2|3|4). .+"),
			Pattern.compile("\\d+ - \\d+ ARE (NOT )?DUPLICATES"));

	private final MemoryAppender appender;

	private final Map<Logger, Level> savedLevels;

	private TraceLogCapture(MemoryAppender appender, Map<Logger, Level> savedLevels) {
		this.appender = appender;
		this.savedLevels = savedLevels;
	}

	public static TraceLogCapture attach() {
		return attach(TRACE_LOGGER_NAMES);
	}

	public static TraceLogCapture attach(List<String> loggerNames) {
		MemoryAppender appender = new MemoryAppender();
		appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
		Map<Logger, Level> savedLevels = new LinkedHashMap<>();
		for (String name : loggerNames) {
			Logger logger = (Logger) LoggerFactory.getLogger(name);
			savedLevels.put(logger, logger.getLevel()); // may be null (= inherit)
			logger.setLevel(Level.TRACE);
			logger.addAppender(appender);
		}
		appender.start();
		return new TraceLogCapture(appender, savedLevels);
	}

	/** The underlying buffer, for callers that need other MemoryAppender queries. */
	public MemoryAppender appender() {
		return appender;
	}

	/** Trace lines matching {@link #TRACE_PATTERNS}, in log order. */
	public List<String> tracedMessages() {
		return appender.filterByPatterns(TRACE_PATTERNS, Level.TRACE);
	}

	/** Clear captured events without detaching (for reuse within one capture). */
	public void reset() {
		appender.reset();
	}

	@Override
	public void close() {
		appender.stop();
		savedLevels.forEach((logger, level) -> {
			logger.detachAppender(appender);
			logger.setLevel(level); // null legally restores "inherit from parent"
		});
	}

}
