package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.services.DeduplicationService;

class DeduplicationServiceTests extends AbstractIntegrationTest {
	@Autowired
	DeduplicationService deduplicationService;

	@BeforeEach
	void initTestDir() {
		testDir = baseDir.resolve("integration").resolve("other");
	}

	@Test
	void deduplicate_OK() {
		Path inputPath = testDir.resolve("t1.txt");
		DeduplicationMode mode = DeduplicationMode.REMOVE;

		String resultString = deduplicationService.deduplicateOneFile(inputPath, mode, message -> {
		});

		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(4, 1));
		// assertThat(resultString).startsWith("DONE: DedupEndNote removed 3 records, and
		// has written 1 records.");
	}

	@ParameterizedTest
	@CsvSource({ "'test805.txt', 805, 629",
			// "'DedupEndNote_portal_vein_thrombosis_37741.txt', 37741, 24382", // Very slow test
			"'Non_Latin_input.txt', 2, 2", "'Dedup_PATIJ2_Possibly_missed.txt', 18, 11" })
	void deduplicateSmallFiles(String fileName, int total, int totalWritten) {
		Path inputPath = testDir.resolve(fileName);
		DeduplicationMode mode = DeduplicationMode.REMOVE;
		assertThat(inputPath).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputPath, mode, message -> {
		});

		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(total, totalWritten));
	}

	@Test
	void deduplicate_withDuplicateIDs() {
		Path inputPath = testDir.resolve("File_with_duplicate_IDs.txt");
		DeduplicationMode mode = DeduplicationMode.REMOVE;

		String resultString = deduplicationService.deduplicateOneFile(inputPath, mode, message -> {
		});

		assertThat(resultString).startsWith("ERROR: The IDs of the bibliographic items of input file "
				+ inputPath.getFileName() + " are not unique");
	}

	@Test
	void deduplicateTwoFiles_OK() {
		Path oldInputPath = testDir.resolve("TwoFiles_1.txt");
		Path newInputPath = testDir.resolve("TwoFiles_2.txt");
		DeduplicationMode mode = DeduplicationMode.REMOVE;

		String resultString = deduplicationService.deduplicateTwoFiles(newInputPath, oldInputPath, mode, message -> {
		});
		System.err.println(resultString);
		assertThat(resultString).startsWith(
				"DONE: DedupEndNote removed 551 bibliographic items from the new set, and has written 114 bibliographic items.");
	}

	@Disabled("TODO: Why was this disabled")
	@Test
	void files_without_IDs() {
		Path oldInputPath = testDir.resolve("Recurrance_rate_EndNote_Library_original_deduplicated.txt");
		Path newInputPath = testDir.resolve("Recurrence_rate_search_updated_sept_18_deduplicated.txt");
		DeduplicationMode mode = DeduplicationMode.REMOVE;

		String resultString = deduplicationService.deduplicateTwoFiles(newInputPath, oldInputPath, mode, message -> {
		});
		System.err.println(resultString);
		assertThat(resultString).startsWith("ERROR: The second input file contains records without IDs");
	}

	// FIXME: tests for DeduplicationMode.MARK;

}
