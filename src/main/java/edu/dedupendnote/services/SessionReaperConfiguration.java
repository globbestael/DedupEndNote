package edu.dedupendnote.services;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wires and schedules the {@link SessionDirectoryReaper} only when
 * {@code dedup.session-reaper.enabled=true}. When the property is absent or false the whole
 * configuration is skipped — no reaper bean and no scheduling — so the cron stop/restart
 * cleanup remains the sole mechanism. The toggle is evaluated once at startup (per jar /
 * per launch), not at runtime.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "dedup.session-reaper.enabled", havingValue = "true")
class SessionReaperConfiguration {

	@Bean
	SessionDirectoryReaper sessionDirectoryReaper(@Value("${upload-dir}") String uploadDir,
			@Value("${dedup.session-reaper.max-age-hours:24}") long maxAgeHours) {
		return new SessionDirectoryReaper(Path.of(uploadDir), Duration.ofHours(maxAgeHours), Clock.systemUTC());
	}
}
