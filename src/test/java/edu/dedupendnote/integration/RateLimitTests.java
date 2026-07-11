package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

// Does NOT extend AbstractIntegrationTest: needs RANDOM_PORT and a property override.
// Happy-path upload is covered by PathTraversalTests.startOneFile_withValidUpload_returns200WithDone.
//
// The rate limiter keys on the client IP. Tests identify the client via X-Forwarded-For
// (the first hop): this exercises the trusted-proxy path AND gives each test a distinct
// client so the shared localhost remoteAddr does not leak state across test methods.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "dedup.upload-cooldown-seconds=3600")
@ActiveProfiles("test")
class RateLimitTests extends AbstractRandomPortIntegrationTest {

	private static final String RIS_CONTENT = "TY  - JOUR\nID  - 1\nTI  - Test title\nER  - \n";

	private ResponseEntity<String> upload(String clientIp, String sessionId) {
		return upload(clientIp, sessionId, RIS_CONTENT.getBytes());
	}

	private ResponseEntity<String> upload(String clientIp, String sessionId, byte[] content) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.add("X-Forwarded-For", clientIp);
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(content) {
			@Override
			public String getFilename() {
				return "test.ris";
			}
		});
		body.add("wssessionId", sessionId);
		return restTemplate.postForEntity(url("/uploadFile"), new HttpEntity<>(body, headers), String.class);
	}

	@Test
	void firstUploadFromIp_isAllowed() {
		ResponseEntity<String> response = upload("10.0.0.1", "00000000-0000-4000-8000-000000000001");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void secondUploadWithinBurst_isAllowed() {
		String session = "00000000-0000-4000-8000-000000000002";
		upload("10.0.0.2", session);
		ResponseEntity<String> response = upload("10.0.0.2", session);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void thirdUploadWithinCooldown_isRejected() {
		String session = "00000000-0000-4000-8000-000000000003";
		upload("10.0.0.3", session);
		upload("10.0.0.3", session);
		ResponseEntity<String> response = upload("10.0.0.3", session);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getBody()).contains("ERROR");
	}

	// The fix: a fresh wssessionId no longer resets the quota — the same IP is throttled
	// regardless of the (client-chosen) session id.
	@Test
	void differentSessionIdsFromSameIp_shareQuota() {
		upload("10.0.0.4", "00000000-0000-4000-8000-0000000000a1");
		upload("10.0.0.4", "00000000-0000-4000-8000-0000000000a2");
		ResponseEntity<String> response = upload("10.0.0.4", "00000000-0000-4000-8000-0000000000a3");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void differentIps_areIsolated() {
		String session = "00000000-0000-4000-8000-0000000000b1";
		upload("10.0.0.5", session); // exhaust the burst for IP .5
		upload("10.0.0.5", session);

		ResponseEntity<String> response = upload("10.0.0.6", session); // different IP → own quota

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	// Rejected uploads (here: empty file → 400) must not consume the cooldown slot, so
	// legitimate retries are not blocked.
	@Test
	void failedUploadsDoNotConsumeQuota() {
		String session = "00000000-0000-4000-8000-0000000000c1";
		ResponseEntity<String> firstEmpty = upload("10.0.0.7", session, new byte[0]);
		ResponseEntity<String> secondEmpty = upload("10.0.0.7", session, new byte[0]);
		assertThat(firstEmpty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(secondEmpty.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

		ResponseEntity<String> valid = upload("10.0.0.7", session);

		assertThat(valid.getStatusCode()).isEqualTo(HttpStatus.OK);
	}
}
