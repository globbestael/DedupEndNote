package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SecurityHeadersTests extends AbstractRandomPortIntegrationTest {

	private static void assertSecurityHeaders(HttpHeaders headers) {
		assertThat(headers.getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("SAMEORIGIN");
		assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
	}

	@Test
	void page_carriesSecurityHeaders() {
		ResponseEntity<String> response = restTemplate.getForEntity(url("/"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertSecurityHeaders(response.getHeaders());
	}

	@Test
	void errorResponse_carriesSecurityHeaders() {
		ResponseEntity<String> response = restTemplate.getForEntity(url("/no-such-resource"), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertSecurityHeaders(response.getHeaders());
	}
}
