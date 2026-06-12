package edu.dedupendnote.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

// Base for tests that need RANDOM_PORT (real HTTP calls) but cannot use
// AbstractIntegrationTest, which uses the default MOCK web environment.
// Plain RestTemplate with a no-op error handler so 4xx/5xx responses are
// returned as ResponseEntity instead of throwing HttpClientErrorException.
abstract class AbstractRandomPortIntegrationTest {

	@MockitoBean
	SimpMessagingTemplate simpMessagingTemplate;

	@LocalServerPort
	int port;

	protected RestTemplate restTemplate;

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

	protected String url(String path) {
		return "http://localhost:" + port + path;
	}
}
