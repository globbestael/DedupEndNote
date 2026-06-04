package edu.dedupendnote.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.services.UtilitiesService;

class TwoFilesTests extends AbstractIntegrationTest {
	@Autowired
	DeduplicationService deduplicationService;

	@Override
	@BeforeEach
	void initTestDir() {
		testDir = baseDir.resolve("experiments");
	}

	@Test
	void deduplicate_OK() {
		Path oldInputPath = testDir.resolve("TwoFiles_1.txt");
		Path newInputPath = testDir.resolve("TwoFiles_2.txt");
		DeduplicationMode mode = DeduplicationMode.REMOVE;
		Path outputPath = UtilitiesService.createOutputPath(newInputPath, mode);

		String resultString = deduplicationService.deduplicateTwoFiles(newInputPath, oldInputPath, outputPath,
				mode, message -> {});
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
		Path outputPath = UtilitiesService.createOutputPath(newInputPath, mode);

		String resultString = deduplicationService.deduplicateTwoFiles(newInputPath, oldInputPath, outputPath,
				mode, message -> {});
		System.err.println(resultString);
		assertThat(resultString).startsWith("ERROR: The second input file contains records without IDs");
	}

	// FIXME: write tests for mode = true

}
