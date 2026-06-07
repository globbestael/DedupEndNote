package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "dedup.max-concurrent-runs=0")
@ActiveProfiles("test")
class ConcurrentRunsTests {

	@MockitoBean
	SimpMessagingTemplate simpMessagingTemplate;

	@LocalServerPort
	int port;

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
	void startOneFile_whenCapExhausted_returns429() {
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("fileName_1", "anyfile.txt");
		params.add("wssessionId", "00000000-0000-4000-8000-000000000001");

		ResponseEntity<String> response = restTemplate.postForEntity(url("/startOneFile"), params, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}

	@Test
	void startTwoFiles_whenCapExhausted_returns429() {
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("oldFile", "old.txt");
		params.add("newFile", "new.txt");
		params.add("wssessionId", "00000000-0000-4000-8000-000000000001");

		ResponseEntity<String> response = restTemplate.postForEntity(url("/startTwoFiles"), params, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
	}
}
