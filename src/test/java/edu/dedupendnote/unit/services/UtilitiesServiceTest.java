package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.services.UtilitiesService;

// HTTP-level coverage is in PathTraversalTests (integration).
class UtilitiesServiceTest {

	private static final String UPLOAD_DIR = Path.of(System.getProperty("java.io.tmpdir")).resolve("dedup-test-uploads")
			.toString();
	private static final String UPLOAD_DIR_PREFIX = Path.of(UPLOAD_DIR).toAbsolutePath().normalize().toString();

	@Test
	void getSessionDir_resolvesUuidAsSubdirectory() {
		UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
		Path result = UtilitiesService.getSessionDir(UPLOAD_DIR, id);
		assertThat(result.toString()).startsWith(UPLOAD_DIR_PREFIX);
		assertThat(result.getFileName().toString()).isEqualTo(id.toString());
	}

	@Test
	void resolveInSessionDir_validInputs_resolvesInsideSessionDir() {
		UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
		Path result = UtilitiesService.resolveInSessionDir(UPLOAD_DIR, id, "myfile.ris");
		assertThat(result.toString()).startsWith(UPLOAD_DIR_PREFIX);
		assertThat(result.toString()).contains(id.toString());
		assertThat(result.getFileName().toString()).isEqualTo("myfile.ris");
	}

	@ParameterizedTest(name = "{index}: session dir filename traversal ''{0}''")
	@ValueSource(strings = { "../../etc/passwd", "../secret.txt", "subdir/file.ris", "/etc/passwd" })
	void resolveInSessionDir_traversalFilename_throws(String malicious) {
		UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
		assertThatThrownBy(() -> UtilitiesService.resolveInSessionDir(UPLOAD_DIR, id, malicious))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createPath_stripsExtensionAndAppendsSuffix() {
		Path result = UtilitiesService.createPath(Path.of("data/Stroke.txt"), "_TRUTH", "txt");
		assertThat(result.getFileName().toString()).isEqualTo("Stroke_TRUTH.txt");
	}

	@Test
	void createPath_risInputAlwaysProducesTxtOutput() {
		Path result = UtilitiesService.createPath(Path.of("data/TIL_Zotero.ris"),
				DeduplicationMode.MARK.filenameSuffix(), "txt");
		assertThat(result.getFileName().toString()).isEqualTo("TIL_Zotero_mark.txt");
	}

	@Test
	void createPath_nullAdditionProducesNoSuffix() {
		Path result = UtilitiesService.createPath(Path.of("data/Stroke.txt"), null, "txt");
		assertThat(result.getFileName().toString()).isEqualTo("Stroke.txt");
	}

	@Test
	void createPath_blankExtension_throws() {
		assertThatThrownBy(() -> UtilitiesService.createPath(Path.of("data/Stroke.txt"),
				DeduplicationMode.MARK.filenameSuffix(), "  ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createPath_nullExtension_throws() {
		assertThatThrownBy(() -> UtilitiesService.createPath(Path.of("data/Stroke.txt"),
				DeduplicationMode.MARK.filenameSuffix(), null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void filenameSuffix_markMode() {
		assertThat(DeduplicationMode.MARK.filenameSuffix()).isEqualTo("_mark");
	}

	@Test
	void filenameSuffix_removeMode() {
		assertThat(DeduplicationMode.REMOVE.filenameSuffix()).isEqualTo("_deduplicated");
	}
}
