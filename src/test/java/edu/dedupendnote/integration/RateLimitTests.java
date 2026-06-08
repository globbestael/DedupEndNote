package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

// Does NOT extend AbstractIntegrationTest: needs RANDOM_PORT and a property override.
// Happy-path upload is covered by PathTraversalTests.startOneFile_withValidUpload_returns200WithDone.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "dedup.upload-cooldown-seconds=3600")
@ActiveProfiles("test")
class RateLimitTests {

	@MockitoBean
	SimpMessagingTemplate simpMessagingTemplate;

	@LocalServerPort
	int port;

	private RestTemplate restTemplate;

	private static final String RIS_CONTENT = "TY  - JOUR\nID  - 1\nTI  - Test title\nER  - \n";
	private static final String WSSESSION_ID = "00000000-0000-4000-8000-000000000004";

	@BeforeEach
	void setupRestTemplate() {
		restTemplate = new RestTemplate();
		restTemplate.setErrorHandler(new ResponseErrorHandler() {
			@Override
			public boolean hasError(ClientHttpResponse response) {
				return false;
			}
		});
	}

	private String url(String path) {
		return "http://localhost:" + port + path;
	}

	private ResponseEntity<String> upload(String fakeIp) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		headers.set("X-Forwarded-For", fakeIp);
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(RIS_CONTENT.getBytes()) {
			@Override
			public String getFilename() {
				return "test.ris";
			}
		});
		body.add("wssessionId", WSSESSION_ID);
		return restTemplate.postForEntity(url("/uploadFile"), new HttpEntity<>(body, headers), String.class);
	}

	@Test
	void uploadFile_firstRequest_isAllowed() {
		ResponseEntity<String> response = upload("10.0.0.1");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void uploadFile_secondRequestWithinCooldown_isRejected() {
		upload("10.0.0.2");
		ResponseEntity<String> response = upload("10.0.0.2");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(response.getBody()).contains("ERROR");
	}
}
