package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PathTraversalTests extends AbstractRandomPortIntegrationTest {

	@Test
	void getResultFile_traversalFilename_returns400() {
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("fileNameResultFile", "../../pom.xml");
		params.add("deduplicationModeResultFile", "REMOVE");
		params.add("wssessionId", "00000000-0000-4000-8000-000000000001");

		ResponseEntity<String> response = postForm("/getResultFile", params);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void uploadFile_traversalFilename_returns400() {
		ResponseEntity<String> response = uploadFile("../../evil.ris", "TY  - JOUR\nER  -\n".getBytes(),
				"00000000-0000-4000-8000-000000000001");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
	}

	@Test
	void startOneFile_withValidUpload_returns200WithDone() {
		String wssessionId = "00000000-0000-4000-8000-000000000002";
		String risContent = "TY  - JOUR\nID  - 1\nTI  - Test title\nER  - \n";

		uploadFile("test.ris", risContent.getBytes(), wssessionId);

		MultiValueMap<String, String> startParams = new LinkedMultiValueMap<>();
		startParams.add("fileName_1", "test.ris");
		startParams.add("wssessionId", wssessionId);
		ResponseEntity<String> response = postForm("/startOneFile", startParams);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody()).contains("DONE");
	}
}
