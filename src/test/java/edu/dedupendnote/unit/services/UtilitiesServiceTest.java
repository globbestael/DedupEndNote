package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.dedupendnote.services.UtilitiesService;

// HTTP-level coverage is in PathTraversalTests (integration).
class UtilitiesServiceTest {

	private static final String UPLOAD_DIR = System.getProperty("java.io.tmpdir") + "/dedup-test-uploads";

	private static final String UPLOAD_DIR_PREFIX =
			Path.of(UPLOAD_DIR).toAbsolutePath().normalize().toString();

	@Test
	void resolveInUploadDir_validFilename_resolvesInsideUploadDir() {
		Path result = UtilitiesService.resolveInUploadDir(UPLOAD_DIR, "myfile.ris");
		assertThat(result.toString()).endsWith("myfile.ris");
		assertThat(result.toString()).startsWith(UPLOAD_DIR_PREFIX);
	}

	// Any input that contains path separators or parent-directory references is rejected.
	@ParameterizedTest(name = "{index}: traversal attempt ''{0}''")
	@ValueSource(strings = { "../../etc/passwd", "../secret.txt", "..\\..\\windows\\win.ini",
			"subdir/../../../outside.txt", "/etc/passwd", "subdir/file.ris" })
	void resolveInUploadDir_pathTraversal_throws(String malicious) {
		assertThatThrownBy(() -> UtilitiesService.resolveInUploadDir(UPLOAD_DIR, malicious))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void resolveInUploadDir_dotDot_throws() {
		assertThatThrownBy(() -> UtilitiesService.resolveInUploadDir(UPLOAD_DIR, ".."))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void resolveInUploadDir_nullFilename_throws() {
		assertThatThrownBy(() -> UtilitiesService.resolveInUploadDir(UPLOAD_DIR, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void resolveInUploadDir_emptyFilename_throws() {
		assertThatThrownBy(() -> UtilitiesService.resolveInUploadDir(UPLOAD_DIR, ""))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
