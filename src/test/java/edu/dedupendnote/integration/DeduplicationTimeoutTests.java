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
// Happy path (normal completion within timeout) is covered by
// PathTraversalTests.startOneFile_withValidUpload_returns200WithDone.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "dedup.timeout-minutes=0")
@ActiveProfiles("test")
class DeduplicationTimeoutTests extends AbstractRandomPortIntegrationTest {

	private static final String ONE_RECORD_RIS = "TY  - JOUR\nID  - 1\nTI  - Test title\nER  - \n";
	private static final String WSSESSION_ID = "00000000-0000-4000-8000-000000000003";

	private void uploadFile(String filename) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(ONE_RECORD_RIS.getBytes()) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		body.add("wssessionId", WSSESSION_ID);
		restTemplate.postForEntity(url("/uploadFile"), new HttpEntity<>(body, headers), String.class);
	}

	@Test
	void startOneFile_whenTimeoutExpires_returns503() {
		uploadFile("timeout_test.ris");

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("fileName_1", "timeout_test.ris");
		params.add("wssessionId", WSSESSION_ID);
		ResponseEntity<String> response = restTemplate.postForEntity(url("/startOneFile"), params, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).contains("ERROR");
		assertThat(response.getBody()).contains("timed out");
	}
}
