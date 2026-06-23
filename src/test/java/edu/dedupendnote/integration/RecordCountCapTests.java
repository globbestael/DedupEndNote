package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.RecordCapExceededException;

@SpringBootTest(properties = "dedup.max-records=2")
@ActiveProfiles("test")
class RecordCountCapTests extends AbstractIntegrationTest {

	@Autowired
	DeduplicationService deduplicationService;

	private static final String ONE_RECORD_RIS = """
			TY  - JOUR
			ID  - 1
			TI  - Title one
			ER  -\s
			""";

	private static final String THREE_RECORD_RIS = """
			TY  - JOUR
			ID  - 1
			TI  - Title one
			ER  -\s
			TY  - JOUR
			ID  - 2
			TI  - Title two
			ER  -\s
			TY  - JOUR
			ID  - 3
			TI  - Title three
			ER  -\s
			""";

	@Test
	void deduplicateOneFile_whenCapExceeded_throwsException(@TempDir Path tempDir) throws IOException {
		Path inputPath = tempDir.resolve("test.ris");
		Files.writeString(inputPath, THREE_RECORD_RIS);

		assertThatThrownBy(() -> deduplicationService.deduplicateOneFile(inputPath, DeduplicationMode.REMOVE,
				message -> {}))
				.isInstanceOf(RecordCapExceededException.class)
				.hasMessageContaining("exceeds the maximum");
	}

	@Test
	void deduplicateOneFile_withinCap_succeeds(@TempDir Path tempDir) throws IOException {
		Path inputPath = tempDir.resolve("test.ris");
		Files.writeString(inputPath, ONE_RECORD_RIS);

		String result = deduplicationService.deduplicateOneFile(inputPath, DeduplicationMode.REMOVE, message -> {});

		assertThat(result).startsWith("DONE:");
	}

	@Test
	void deduplicateTwoFiles_whenCombinedCapExceeded_throwsException(@TempDir Path tempDir) throws IOException {
		Path oldPath = tempDir.resolve("old.ris");
		Path newPath = tempDir.resolve("new.ris");
		// two records each = 4 combined, exceeds max-records=2
		String twoRecords = THREE_RECORD_RIS.lines().limit(8).collect(Collectors.joining("\n")) + "\n";
		Files.writeString(oldPath, twoRecords);
		Files.writeString(newPath, twoRecords.replace("ID  - 1", "ID  - 10").replace("ID  - 2", "ID  - 20"));

		assertThatThrownBy(() -> deduplicationService.deduplicateTwoFiles(newPath, oldPath, DeduplicationMode.REMOVE,
				message -> {}))
				.isInstanceOf(RecordCapExceededException.class)
				.hasMessageContaining("exceeds the maximum");
	}

	@Test
	void deduplicateTwoFiles_withinCap_succeeds(@TempDir Path tempDir) throws IOException {
		Path oldPath = tempDir.resolve("old.ris");
		Path newPath = tempDir.resolve("new.ris");
		// one record each = 2 combined, within max-records=2
		Files.writeString(oldPath, ONE_RECORD_RIS);
		Files.writeString(newPath, ONE_RECORD_RIS.replace("ID  - 1", "ID  - 2").replace("Title one", "Title two"));

		String result = deduplicationService.deduplicateTwoFiles(newPath, oldPath, DeduplicationMode.REMOVE,
				message -> {});

		assertThat(result).startsWith("DONE:");
	}
}
