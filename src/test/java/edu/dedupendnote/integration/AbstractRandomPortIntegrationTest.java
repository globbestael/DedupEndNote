package edu.dedupendnote.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

// Base for tests that need RANDOM_PORT (real HTTP calls) but cannot use
// AbstractIntegrationTest, which uses the default MOCK web environment.
// RestClient with a status handler that accepts every status, so 4xx/5xx responses are
// returned as ResponseEntity instead of throwing RestClientResponseException.
abstract class AbstractRandomPortIntegrationTest {

	@MockitoBean
	SimpMessagingTemplate simpMessagingTemplate;

	@LocalServerPort
	int port;

	private RestClient restClient;

	@BeforeEach
	void setupRestClient() {
		restClient = RestClient.builder()
				.baseUrl("http://localhost:" + port)
				.defaultStatusHandler(status -> true, (request, response) -> {
					// no-op: leave the response for the caller to inspect
				})
				.build();
	}

	protected ResponseEntity<String> get(String path) {
		return restClient.get().uri(path).retrieve().toEntity(String.class);
	}

	protected ResponseEntity<String> postForm(String path, MultiValueMap<String, String> params) {
		return restClient.post()
				.uri(path)
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(params)
				.retrieve()
				.toEntity(String.class);
	}

	protected ResponseEntity<String> uploadFile(String filename, byte[] content, String wssessionId) {
		return uploadFile(filename, content, wssessionId, new HttpHeaders());
	}

	protected ResponseEntity<String> uploadFile(String filename, byte[] content, String wssessionId,
			HttpHeaders extraHeaders) {
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		HttpHeaders fileHeaders = new HttpHeaders();
		fileHeaders.setContentDispositionFormData("file", filename);
		body.add("file", new HttpEntity<>(content, fileHeaders));
		body.add("wssessionId", wssessionId);
		return postMultipart("/uploadFile", body, extraHeaders);
	}

	protected ResponseEntity<String> postMultipart(String path, MultiValueMap<String, Object> body,
			HttpHeaders extraHeaders) {
		return restClient.post()
				.uri(path)
				.headers(h -> h.addAll(extraHeaders))
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(body)
				.retrieve()
				.toEntity(String.class);
	}
}
