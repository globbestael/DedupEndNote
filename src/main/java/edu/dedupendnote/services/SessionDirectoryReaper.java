package edu.dedupendnote.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.apache.commons.io.file.PathUtils;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.extern.slf4j.Slf4j;

/**
 * Periodically deletes stale per-session upload directories so they cannot accumulate on
 * disk while the application runs without a restart (security hardening issue 06). It is an
 * opt-in, in-process alternative to the cron stop/restart cleanup: disabled unless
 * {@code dedup.session-reaper.enabled=true} (wired by {@link SessionReaperConfiguration}).
 *
 * <p>Staleness is judged by the directory's last-modified time — an upload or output write
 * bumps it — which keeps the reaper decoupled from the run registry: an in-flight or recent
 * run's directory is naturally spared because it was touched within the max-age window.
 *
 * <p>Plain POJO (constructor takes an injectable {@link Clock}) so the reaping logic is unit
 * tested without a Spring context.
 */
@Slf4j
public class SessionDirectoryReaper {

	private final Path uploadDir;
	private final Duration maxAge;
	private final Clock clock;

	public SessionDirectoryReaper(Path uploadDir, Duration maxAge, Clock clock) {
		this.uploadDir = uploadDir;
		this.maxAge = maxAge;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${dedup.session-reaper.interval-ms:3600000}")
	public void reap() {
		if (!Files.isDirectory(uploadDir)) {
			return;
		}
		Instant cutoff = clock.instant().minus(maxAge);
		try (Stream<Path> children = Files.list(uploadDir)) {
			children.filter(Files::isDirectory).filter(dir -> isOlderThan(dir, cutoff)).forEach(this::deleteQuietly);
		} catch (IOException e) {
			log.warn("Session-directory reaper could not list {}", uploadDir, e);
		}
	}

	private boolean isOlderThan(Path dir, Instant cutoff) {
		try {
			return Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff);
		} catch (IOException e) {
			log.warn("Session-directory reaper could not read the timestamp of {}", dir, e);
			return false;
		}
	}

	private void deleteQuietly(Path dir) {
		try {
			PathUtils.deleteDirectory(dir);
			log.info("Session-directory reaper deleted stale session directory {}", dir.getFileName());
		} catch (IOException e) {
			log.warn("Session-directory reaper could not delete {}", dir, e);
		}
	}
}
