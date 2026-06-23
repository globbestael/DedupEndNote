package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import edu.dedupendnote.services.BibliographicItemReader;
import edu.dedupendnote.unit.BaseTest;

/*
 * Tests for the title-derived patterns of BibliographicItemReader:
 * REPLY_PATTERN, ERRATUM_PATTERN, SOURCE_PATTERN, COMMENT_PATTERN (-> isReply) and PHASE_PATTERN (-> isPhase).
 *
 * These tests were moved out of JWSimilarityTitleTest (which is only concerned with Jaro-Winkler title
 * similarity). File-based tests read curated example sets from ~/dedupendnote_input_files/unit (not in git).
 *
 * Note on case handling (mirrors how BibliographicItemReader applies the patterns):
 * - REPLY_PATTERN and PHASE_PATTERN are matched against the lower-cased title.
 * - ERRATUM_PATTERN, SOURCE_PATTERN and COMMENT_PATTERN are matched against the raw title.
 */
class BibliographicItemReaderTest extends BaseTest {

	@BeforeEach
	void initTestDir() {
		testDir = baseDir.resolve("unit");
	}

	// ----------------------------------------------------------------------------------------------------
	// REPLY_PATTERN
	// ----------------------------------------------------------------------------------------------------

	@ParameterizedTest(name = "[{index}] reply: \"{0}\"")
	@ValueSource(strings = {
			"Could TIPS be Applied in All Kinds of Portal Vein Thrombosis: We are not Sure! Reply",
			"Reply",
			"Letter: portal vein obstruction--which subset of patients could benefit the most? Authors' reply",
			"Authors' response to the comments on our article",
			"response" })
	void replyPattern_positives(String title) {
		assertThat(BibliographicItemReader.REPLY_PATTERN.matcher(title.toLowerCase(Locale.ROOT)).matches())
				.as("should be detected as reply: %s", title)
				.isTrue();
	}

	@ParameterizedTest(name = "[{index}] not a reply: \"{0}\"")
	@ValueSource(strings = {
			"Endothelial cell injury in cardiovascular surgery: the procoagulant response",
			"A randomized study of patient outcomes after surgery" })
	void replyPattern_negatives(String title) {
		assertThat(BibliographicItemReader.REPLY_PATTERN.matcher(title.toLowerCase(Locale.ROOT)).matches())
				.as("should NOT be detected as reply: %s", title)
				.isFalse();
	}

	// ----------------------------------------------------------------------------------------------------
	// ERRATUM_PATTERN
	// ----------------------------------------------------------------------------------------------------

	@ParameterizedTest(name = "[{index}] erratum: \"{0}\"")
	@ValueSource(strings = {
			"Correction to Smith et al.: A study of portal vein thrombosis",
			"Corrigendum to 'The original article title'",
			"Corrigendum to: The original article title",
			"Erratum: A study of hepatocellular carcinoma",
			"A study of hepatocellular carcinoma Erratum",
			"Long-term outcomes after radioembolization Corrigendum" })
	void erratumPattern_positives(String title) {
		assertThat(BibliographicItemReader.ERRATUM_PATTERN.matcher(title).matches())
				.as("should be detected as erratum: %s", title)
				.isTrue();
	}

	// Titles that mention a correction/erratum but are NOT errata themselves
	// (see the documented examples above ERRATUM_PATTERN in BibliographicItemReader).
	@ParameterizedTest(name = "[{index}] not an erratum: \"{0}\"")
	@ValueSource(strings = {
			"Diminished GABA(A) receptor-binding capacity in a patient with treatment-resistant depression and anxiety.[Erratum appears in Neuropsychopharmacology. 2004 Sep;29(9):1762]",
			"Extensive thrombosis in a patient with familial Mediterranean fever, despite hyperimmunoglobulin D state in serum. [corrected]",
			"A normal title about corrections in clinical genetics" })
	void erratumPattern_negatives(String title) {
		assertThat(BibliographicItemReader.ERRATUM_PATTERN.matcher(title).matches())
				.as("should NOT be detected as erratum: %s", title)
				.isFalse();
	}

	// ----------------------------------------------------------------------------------------------------
	// PHASE_PATTERN
	// ----------------------------------------------------------------------------------------------------

	@ParameterizedTest(name = "[{index}] phase: \"{0}\"")
	@ValueSource(strings = {
			"A phase 3 study of durvalumab as adjuvant therapy in hepatocellular carcinoma",
			"Phase II trial of ixabepilone and dasatinib for treatment of metastatic breast cancer",
			"Phase 2 open-label study of single-agent sorafenib in hepatocellular carcinoma" })
	void phasePattern_positives(String title) {
		assertThat(BibliographicItemReader.PHASE_PATTERN.matcher(title.toLowerCase(Locale.ROOT)).matches())
				.as("should be detected as a clinical-trial phase: %s", title)
				.isTrue();
	}

	@ParameterizedTest(name = "[{index}] not a phase: \"{0}\"")
	@ValueSource(strings = {
			"Imatinib-resistant or -intolerant chronic-phase chronic myeloid leukemia receiving dasatinib",
			"A study of disease progression in hepatocellular carcinoma" })
	void phasePattern_negatives(String title) {
		assertThat(BibliographicItemReader.PHASE_PATTERN.matcher(title.toLowerCase(Locale.ROOT)).matches())
				.as("should NOT be detected as a clinical-trial phase: %s", title)
				.isFalse();
	}

	// ----------------------------------------------------------------------------------------------------
	// SOURCE_PATTERN (errata that carry a "(vol N, pg N, YYYY)" source) -- file-based
	// ----------------------------------------------------------------------------------------------------

	@Test
	void testErrataFromFile() throws IOException {
		Path path = testDir.resolve("All__ST_TI_ending_with_round_bracket.txt");
		List<String> lines = Files.readAllLines(path);

		List<String> results = new ArrayList<>();

		for (String line : lines) {
			Matcher matcher = BibliographicItemReader.SOURCE_PATTERN.matcher(line);
			if (matcher.matches()) {
				results.add(matcher.group(1));
			}
		}

		assertThat(results).as("There are more than 10 results").hasSizeGreaterThan(10);
		assertThat(lines).as("There are more than 100 lines").hasSizeGreaterThan(100);
	}

	// ----------------------------------------------------------------------------------------------------
	// COMMENT_PATTERN -- file-based
	// ----------------------------------------------------------------------------------------------------

	@Test
	void testPositiveCommentsFromFile() throws IOException {
		Path path = testDir.resolve("All__comment__positive_examples.txt");
		List<String> lines = Files.readAllLines(path);

		List<String> nonMatchedCases = new ArrayList<>();
		List<String> matchedCases = new ArrayList<>();

		for (String line : lines) {
			Matcher matcher = BibliographicItemReader.COMMENT_PATTERN.matcher(line);
			if (matcher.matches()) {
				matchedCases.add(line);
			} else {
				System.err.println("- ERROR: Real comment NOT matched by regex: " + line);
				nonMatchedCases.add(line);
			}
		}

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(nonMatchedCases)
				.as("There are positive examples which are NOT caught as normal comments").hasSize(0);
		softAssertions.assertThat((100 * matchedCases.size()) / lines.size())
				.as("Only " + (100 * matchedCases.size()) / lines.size() + "% of positive cases caught").isEqualTo(100);
		softAssertions.assertAll();
	}

	@Test
	void testNegativeCommentsFromFile() throws IOException {
		Path path = testDir.resolve("All__comment__negative_examples.txt");
		List<String> lines = Files.readAllLines(path);

		List<String> negativeResults = new ArrayList<>();
		int EXPECTED_NUMBER_OF_ERRORS = 8;

		for (String line : lines) {
			Matcher matcher = BibliographicItemReader.COMMENT_PATTERN.matcher(line);
			if (matcher.matches()) {
				System.err.println("- ERROR: Non-comment matched by regex: " + line);
				negativeResults.add(line);
			}
		}

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(negativeResults).as("There are non-comments which are not matched as normal comments")
				.hasSize(EXPECTED_NUMBER_OF_ERRORS);
		softAssertions.assertAll();
	}

	@Test
	void testPositiveCommentsAndRepliesFromFile() throws IOException {
		Path path = testDir.resolve("All__comment_AND_reply__positive_examples.txt");
		List<String> lines = Files.readAllLines(path);

		List<String> nonMatchedCases = new ArrayList<>();
		List<String> matchedCases = new ArrayList<>();
		int EXPECTED_NUMBER_OF_ERRORS = 1;

		for (String line : lines) {
			Matcher matcher = BibliographicItemReader.COMMENT_PATTERN.matcher(line);
			if (matcher.matches()) {
				matchedCases.add(line);
			} else {
				System.err.println("- ERROR: Real comment and reply NOT matched by regex: " + line);
				nonMatchedCases.add(line);
			}
		}

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(nonMatchedCases).as("There are examples which are NOT caught as comments and replies")
				.hasSize(EXPECTED_NUMBER_OF_ERRORS);
		softAssertions.assertAll();
	}

	// ----------------------------------------------------------------------------------------------------
	// RIS_LINE_PATTERN
	// ----------------------------------------------------------------------------------------------------

	@Test
	void lineSeparator() {
		String lineSep = "\u2028"; // Unicode LINE SEPARATOR
		String line = "ST  - Total Pancreatectomy With Islet Cell Transplantation" + lineSep
				+ "for the Treatment of Pancreatic Cancer";
		Stream<String> lines = line.lines();
		List<String> linesList = lines.toList();

		assertThat(linesList.size()).as("LINE SEPARATOR is not an end of line character").isEqualTo(1);

		// LINE SEPARATOR messes with '.*$'
		Matcher matcher = BibliographicItemReader.RIS_LINE_PATTERN.matcher(line);
		assertThat(matcher.matches()).as("LINE SEPARATOR messes with '.*$'").isFalse();

		// Replacing the LINE SEPARATOR is necessary
		line = line.replace(lineSep, " ");
		matcher = BibliographicItemReader.RIS_LINE_PATTERN.matcher(line);

		assertThat(matcher.matches()).isTrue();
		assertThat(matcher.group(1)).isEqualTo("ST");
		assertThat(matcher.group(3)).endsWith("for the Treatment of Pancreatic Cancer");
	}

}
