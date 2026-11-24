package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CancellationTests extends AbstractRandomPortIntegrationTest {

	private static final String NO_FUTURE_SESSION = "00000000-0000-4000-8000-000000000010";
	private static final String CANCEL_SESSION    = "00000000-0000-4000-8000-000000000011";

	@Test
	void cancelDedup_withNoRunningDeduplication_returns404() {
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("wssessionId", NO_FUTURE_SESSION);

		ResponseEntity<String> response = postForm("/cancelDedup", params);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@Timeout(60)
	void startOneFile_whenCancelledMidRun_returnsErrorWithCancelledMessage() throws Exception {
		uploadFile("cancel_test.ris", generateSlowRis(3000).getBytes(), CANCEL_SESSION);

		// /startOneFile blocks until the task finishes; run it on a background thread
		CompletableFuture<ResponseEntity<String>> dedupFuture = CompletableFuture.supplyAsync(() -> {
			MultiValueMap<String, String> startParams = new LinkedMultiValueMap<>();
			startParams.add("fileName_1", "cancel_test.ris");
			startParams.add("wssessionId", CANCEL_SESSION);
			return postForm("/startOneFile", startParams);
		});

		// Poll /cancelDedup every 50 ms until the future is registered in runningFutures
		MultiValueMap<String, String> cancelParams = new LinkedMultiValueMap<>();
		cancelParams.add("wssessionId", CANCEL_SESSION);
		ResponseEntity<String> cancelResponse = postForm("/cancelDedup", cancelParams);
		while (cancelResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
			if (dedupFuture.isDone()) {
				fail("Deduplication completed before cancel could be sent — increase record count in generateSlowRis()");
			}
			Thread.sleep(50);
			cancelResponse = postForm("/cancelDedup", cancelParams);
		}

		assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cancelResponse.getBody()).contains("Cancellation requested");

		ResponseEntity<String> dedupResponse = dedupFuture.get(30, TimeUnit.SECONDS);
		assertThat(dedupResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(dedupResponse.getBody()).contains("ERROR");
		assertThat(dedupResponse.getBody()).contains("cancelled");
	}

}
