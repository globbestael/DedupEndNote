package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.controllers.DedupEndNoteController;
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.IOService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
class DedupEndNoteApplicationTests {

	public DeduplicationService deduplicationService = new DeduplicationService();

	String homeDir = System.getProperty("user.home");

	String testdir = homeDir + "/dedupendnote_files/experiments/";

	String wssessionId = "";

	@BeforeAll
	static void beforeAll() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote");
		rootLogger.setLevel(Level.INFO);
		log.debug("Logging level set to INFO");
	}

	@Test
	void contextLoads() {
		assertThat(deduplicationService).isNotNull();
	}

	// Input file can contain LINE SEPARATOR (\u2028)
	@Test
	void lineSeparator() {
		String line = "ST  - Total Pancreatectomy With Islet Cell Transplantation\u2028for the Treatment of Pancreatic Cancer";

		// LINE SEPARATOR is not an end of line character for a Reader
		try (StringReader stringReader = new StringReader(line);
				BufferedReader bufferedReader = new BufferedReader(stringReader)) {
			Stream<String> lines = bufferedReader.lines();
			assertThat(lines.count()).as("LINE SEPARATOR is not an end of line character").isEqualTo(1);
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		// LINE SEPARATOR messes with '.*$'
		Matcher matcher = IOService.risLinePattern.matcher(line);
		assertThat(matcher.matches()).as("LINE SEPARATOR messes with '.*$'").isFalse();

		// Replacing the LINE SEPARATOR is necessary
		line = line.replaceAll("\\u2028", " ");
		matcher = IOService.risLinePattern.matcher(line);

		assertThat(matcher.matches()).isTrue();
		assertThat(matcher.group(1)).isEqualTo("ST");
		assertThat(matcher.group(3)).endsWith("for the Treatment of Pancreatic Cancer");
	}

	@Test
	void deduplicate_OK() {
		String inputFileName = testdir + "t1.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteController.createOutputFileName(inputFileName, markMode);

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode,
				wssessionId);

		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(4, 1));
		// assertThat(resultString).startsWith("DONE: DedupEndNote removed 3 records, and
		// has written 1 records.");
	}

	
	  @ParameterizedTest
	    @CsvSource({
	        "'test805.txt', 805, 644",
	        // "'DedupEndNote_portal_vein_thrombosis_37741.txt', 37741, 24382",	// Very slow test
	        "'Non_Latin_input.txt', 2, 2",
	        "'Dedup_PATIJ2_Possibly_missed.txt', 18, 12"
	    })
	  void deduplicateSmallFiles(String fileName, int total, int totalWritten) {
			String inputFileName = testdir + fileName;
			boolean markMode = false;
			String outputFileName = DedupEndNoteController.createOutputFileName(inputFileName, markMode);
			assertThat(new File(inputFileName)).exists();

			String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, wssessionId);

			assertThat(resultString).isEqualTo(deduplicationService.formatResultString(total, totalWritten));
	  }

	@Test
	void deduplicate_withDuplicateIDs() {
		String inputFileName = testdir + "Bestand_met_duplicate_IDs.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteController.createOutputFileName(inputFileName, markMode);

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode,
				wssessionId);

		assertThat(resultString)
			.startsWith("ERROR: The IDs of the records of input file " + inputFileName + " are not unique");
	}

	@Test
	void addDois() {
		String doiString = "10.1371/journal.pone.11 onzin http://dx.doi.org/10.1371/journal%2EPONE.22. 10.1371/journal.pone";
		Publication publication = new Publication();
		Set<String> dois = publication.addDois(doiString);

		assertThat(dois).containsOnly("10.1371/journal.pone.11", "10.1371/journal.pone.22", "10.1371/journal.pone");
	}

	@Test
	void addDoisEscaped() {
		String doiString = "10.1016/S0016-5085(18)34101-5 http://dx.doi.org/10.1016/S0016-5085%2818%2934101-5";
		Publication publication = new Publication();
		Set<String> dois = publication.addDois(doiString);

		assertThat(dois).containsOnly("10.1016/s0016-5085(18)34101-5"); // is lowercased!
	}

	@Test
	void addIssns_valid() {
		String issn = "0002-9343 (Print) 00029342 (Electronic) 0-9752298-0-X (ISBN) xxxxXXXX (all X-es)";
		Publication publication = new Publication();
		List<String> issns = publication.addIssns(issn);

		assertThat(issns).hasSize(4).containsAll(Arrays.asList("00029343", "00029342", "097522980", "XXXXXXXX"));
	}

	@Test
	void addIssns_nonvalid() {
		String issn = "a002-9343 (with letter) 00029342X (11 characters) 0-12-34567890x (12 characters)";

		Publication publication = new Publication();
		List<String> issns = publication.addIssns(issn);

		assertThat(issns).isEmpty();
	}

	@Test
	void compareIssns() {
		Publication r1 = new Publication();
		Publication r2 = new Publication();
		r1.addIssns("0000-0000 1111-1111");
		r2.addIssns("2222-2222 1111-1111");

		assertThat(deduplicationService.compareIssns(r1, r2)).isTrue();

		Publication r3 = new Publication();
		Publication r4 = new Publication();
		r3.addIssns("0000-0000");
		r4.addIssns("1111-1111 2222-2222");

		assertThat(deduplicationService.compareIssns(r3, r4)).isFalse();

		Publication r5 = new Publication();
		Publication r6 = new Publication();
		r5.addIssns("1234-568x (Print)");
		r6.addIssns("1234568X (ISSN)");

		assertThat(deduplicationService.compareIssns(r5, r6)).isTrue();
	}

	@Test
	void checkReply() {
		Pattern replyPattern = Pattern.compile("(.*\\breply\\b.*|.*author(.+)respon.*|^response$)");
		Stream<String> titles = Stream
			.of("Could TIPS be Applied in All Kinds of Portal Vein Thrombosis: We are not Sure! Reply", "Reply");

		titles.forEach(t -> assertThat(replyPattern.matcher(t.toLowerCase()).matches()).isTrue());
	}

	// FIXME: tests for markMode = true;

	@SafeVarargs
	public static <T> T coalesce(T... values) {
		return Arrays.stream(values).filter(Objects::nonNull).findFirst().orElse(null);
	}
	
	@Test
	void checkCoalesce() {
		// FIXME: make assertions comparing with Apache Commons lang3: ObjectUtils::firstNonNull 
		assertThat(DedupEndNoteApplicationTests.coalesce((Object) null)).isNull();
		assertThat(DedupEndNoteApplicationTests.coalesce((String) null, "One", (String) null)).isEqualTo("One");
		
		String[] arrayNulls = { null,  null };
		assertThat(DedupEndNoteApplicationTests.coalesce(arrayNulls, "One")).isEqualTo(arrayNulls);

		String[] arrayNullNonNull = { null,  "A" };
		assertThat(DedupEndNoteApplicationTests.coalesce(arrayNullNonNull, "One")).isEqualTo(arrayNullNonNull);

		String[] arrayNonNull = { null,  "A" };
		assertThat(DedupEndNoteApplicationTests.coalesce(arrayNonNull, "One")).isEqualTo(arrayNonNull);
		
		fail("laatste 3 resultaten voor coalesce zijn NIET ok");
	}
}
