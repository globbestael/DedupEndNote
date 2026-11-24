package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import edu.dedupendnote.services.DeduplicationService;

// Does NOT extend AbstractIntegrationTest: needs RANDOM_PORT and a property override.
// Happy path (normal completion within timeout) is covered by
// PathTraversalTests.startOneFile_withValidUpload_returns200WithDone.
//
// Deterministic by construction: the mocked DeduplicationService blocks until interrupted, so
// the run can never complete and the timeout always wins, however fast the machine is (with
// timeout 0 the runner may even cancel the worker before it starts). Timeout 0 is the smallest
// value the minutes-based property allows.
// Cancelling the worker and releasing the permit are covered in BoundedDedupRunnerTest.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "dedup.timeout-minutes=0")
@ActiveProfiles("test")
class DeduplicationTimeoutTests extends AbstractRandomPortIntegrationTest {

	private static final String ONE_RECORD_RIS = "TY  - JOUR\nID  - 1\nTI  - Test title\nER  - \n";
	private static final String WSSESSION_ID = "00000000-0000-4000-8000-000000000003";

	@MockitoBean
	DeduplicationService deduplicationService;

	@BeforeEach
	void blockWorkerUntilInterrupted() {
		when(deduplicationService.deduplicateOneFile(any(), any(), any())).thenAnswer(invocation -> {
			new CountDownLatch(1).await(); // never counted down: only an interrupt ends this
			return "unreachable";
		});
	}

	@Test
	void startOneFile_whenTimeoutExpires_returns503() {
		uploadFile("timeout_test.ris", ONE_RECORD_RIS.getBytes(), WSSESSION_ID);

		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("fileName_1", "timeout_test.ris");
		params.add("wssessionId", WSSESSION_ID);
		ResponseEntity<String> response = postForm("/startOneFile", params);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(response.getBody()).contains("ERROR");
		assertThat(response.getBody()).contains("timed out");
	}
}
