package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.controllers.DedupEndNoteController;
import edu.dedupendnote.services.DeduplicationService;

@TestConfiguration
public class TwoFilesTest {

	public DeduplicationService deduplicationService = new DeduplicationService();

	String homeDir = System.getProperty("user.home");

	String testdir = homeDir + "/dedupendnote_files/experiments/";

	String wssessionId = "";

	@Test
	void deduplicate_OK() {
		String oldFileName = testdir + "TwoFiles_1.txt";
		String newFileName = testdir + "TwoFiles_2.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteController.createOutputFileName(newFileName, markMode);

		String resultString = deduplicationService.deduplicateTwoFiles(newFileName, oldFileName, outputFileName,
				markMode, wssessionId);
		System.err.println(resultString);
		assertThat(resultString)
			.startsWith("DONE: DedupEndNote removed 552 records from the new set, and has written 113 records.");
	}

	@Disabled
	@Test
	void files_without_IDs() {
		String oldFileName = testdir + "Recurrance_rate_EndNote_Library_original_deduplicated.txt";
		String newFileName = testdir + "Recurrence_rate_search_updated_sept_18_deduplicated.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteController.createOutputFileName(newFileName, markMode);

		String resultString = deduplicationService.deduplicateTwoFiles(newFileName, oldFileName, outputFileName,
				markMode, wssessionId);
		System.err.println(resultString);
		assertThat(resultString).startsWith("ERROR: The second input file contains records without IDs");
	}

	// FIXME: write tests for markMode = true

}
