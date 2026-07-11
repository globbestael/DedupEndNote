package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import edu.dedupendnote.services.SessionDirectoryReaper;

/**
 * Verifies the opt-in wiring: with {@code dedup.session-reaper.enabled=true} the conditional
 * configuration activates, the reaper bean is present and the context (with scheduling
 * enabled) still starts. The disabled-by-default case is covered implicitly by every other
 * integration test, whose contexts load without the reaper.
 */
@SpringBootTest(properties = "dedup.session-reaper.enabled=true")
@ActiveProfiles("test")
class SessionReaperWiringTests {

	@Autowired(required = false)
	SessionDirectoryReaper reaper;

	@Test
	void reaperBean_isPresent_whenEnabled() {
		assertThat(reaper).isNotNull();
	}
}
