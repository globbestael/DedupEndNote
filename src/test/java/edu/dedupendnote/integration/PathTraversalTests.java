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

// Does NOT extend AbstractIntegrationTest: that base class uses the default MOCK
// webEnvironment; this test needs RANDOM_PORT for real HTTP calls.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PathTraversalTests {

	@MockitoBean
	SimpMessagingTemplate simpMessagingTemplate;

	@LocalServerPort
	int port;

	// Plain RestTemplate with a no-op error handler so 4xx responses are returned as
	// ResponseEntity instead of throwing HttpClientErrorException.
	// spring-boot-resttestclient's TestRestTemplate needs additional modules
	// (spring-boot-http-client, spring-boot-restclient) not yet on the test classpath.
	private RestTemplate restTemplate;

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

	@Test
	void getResultFile_traversalFilename_returns400() {
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("fileNameResultFile", "../../pom.xml");
		params.add("markModeResultFile", "false");

		ResponseEntity<String> response = restTemplate.postForEntity(url("/getResultFile"), params, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadFile_traversalFilename_returns400() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		ByteArrayResource fileResource = new ByteArrayResource("TY  - JOUR\nER  -\n".getBytes()) {
			@Override
			public String getFilename() {
				return "../../evil.ris";
			}
		};
		body.add("file", fileResource);

		ResponseEntity<String> response = restTemplate.postForEntity(url("/uploadFile"),
				new HttpEntity<>(body, headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}
}
