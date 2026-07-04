package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CancellationTests extends AbstractRandomPortIntegrationTest {

	private static final String NO_FUTURE_SESSION = "00000000-0000-4000-8000-000000000010";
	private static final String CANCEL_SESSION     = "00000000-0000-4000-8000-000000000011";

	/*
	 * Every record has the same year, same starting page, and the same author so that
	 * comparisons pass steps 1–3 before failing at step 5 (unique journal per record).
	 * This keeps the comparison loop busy long enough for the cancel poll to fire.
	 */
	private static String generateRis(int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= count; i++) {
			sb.append("TY  - JOUR\n")
			  .append("ID  - ").append(i).append("\n")
			  .append("TI  - Effect of treatment on patient outcomes a multicentre randomised controlled trial ").append(i).append("\n")
			  .append("AU  - Smith, John\n")
			  .append("PY  - 2020\n")
			  .append("SP  - 100\n")
			  .append("JO  - Journal of Clinical Medicine ").append(i).append("\n")
			  .append("ER  - \n");
		}
		return sb.toString();
	}

	private void uploadRis(String filename, String content, String wssessionId) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);
		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", new ByteArrayResource(content.getBytes()) {
			@Override
			public String getFilename() {
				return filename;
			}
		});
		body.add("wssessionId", wssessionId);
		restTemplate.postForEntity(url("/uploadFile"), new HttpEntity<>(body, headers), String.class);
	}

	@Test
	void cancelDedup_withNoRunningDeduplication_returns404() {
		MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
		params.add("wssessionId", NO_FUTURE_SESSION);

		ResponseEntity<String> response = restTemplate.postForEntity(url("/cancelDedup"), params, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	@Timeout(60)
	void startOneFile_whenCancelledMidRun_returnsErrorWithCancelledMessage() throws Exception {
		uploadRis("cancel_test.ris", generateRis(3000), CANCEL_SESSION);

		// /startOneFile blocks until the task finishes; run it on a background thread
		CompletableFuture<ResponseEntity<String>> dedupFuture = CompletableFuture.supplyAsync(() -> {
			MultiValueMap<String, String> startParams = new LinkedMultiValueMap<>();
			startParams.add("fileName_1", "cancel_test.ris");
			startParams.add("wssessionId", CANCEL_SESSION);
			return restTemplate.postForEntity(url("/startOneFile"), startParams, String.class);
		});

		// Poll /cancelDedup every 50 ms until the future is registered in runningFutures
		MultiValueMap<String, String> cancelParams = new LinkedMultiValueMap<>();
		cancelParams.add("wssessionId", CANCEL_SESSION);
		ResponseEntity<String> cancelResponse = restTemplate.postForEntity(url("/cancelDedup"), cancelParams, String.class);
		while (cancelResponse.getStatusCode() == HttpStatus.NOT_FOUND) {
			if (dedupFuture.isDone()) {
				fail("Deduplication completed before cancel could be sent — increase record count in generateRis()");
			}
			Thread.sleep(50);
			cancelResponse = restTemplate.postForEntity(url("/cancelDedup"), cancelParams, String.class);
		}

		assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(cancelResponse.getBody()).contains("Cancellation requested");

		ResponseEntity<String> dedupResponse = dedupFuture.get(30, TimeUnit.SECONDS);
		assertThat(dedupResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(dedupResponse.getBody()).contains("ERROR");
		assertThat(dedupResponse.getBody()).contains("cancelled");
	}
}
