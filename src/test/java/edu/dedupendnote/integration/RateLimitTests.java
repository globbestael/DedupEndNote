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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "dedup.upload-cooldown-seconds=3600")
@ActiveProfiles("test")
class RateLimitTests extends AbstractRandomPortIntegrationTest {

	private static final String RIS_CONTENT = "TY  - JOUR\nID  - 1\nTI  - Test title\nER  - \n";

	private ResponseEntity<String> upload(String sessionId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(RIS_CONTENT.getBytes()) {
			@Override
			public String getFilename() {
				return "test.ris";
			}
		});
		body.add("wssessionId", sessionId);
		return restTemplate.postForEntity(url("/uploadFile"), new HttpEntity<>(body, headers), String.class);
	}

	@Test
	void uploadFile_firstRequest_isAllowed() {
		ResponseEntity<String> response = upload("00000000-0000-4000-8000-000000000001");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void uploadFile_secondRequestWithinCooldown_isAllowed() {
		String sessionId = "00000000-0000-4000-8000-000000000002";
		upload(sessionId);
		ResponseEntity<String> response = upload(sessionId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void uploadFile_thirdRequestWithinCooldown_isRejected() {
		String sessionId = "00000000-0000-4000-8000-000000000003";
		upload(sessionId);
		upload(sessionId);
		ResponseEntity<String> response = upload(sessionId);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getBody()).contains("ERROR");
	}
}
