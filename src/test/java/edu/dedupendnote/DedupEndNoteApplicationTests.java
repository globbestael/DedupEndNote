package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.mock.web.MockHttpSession;

import edu.dedupendnote.domain.Record;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.IOService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestConfiguration
public class DedupEndNoteApplicationTests {
	public DeduplicationService deduplicationService = new DeduplicationService();

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
		} catch (IOException e) {
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
		String inputFileName = "src/test/resources/t1.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteApplication.createOutputFileName(inputFileName, markMode);

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, new MockHttpSession());

		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(4, 1));
//		assertThat(resultString).startsWith("DONE: DedupEndNote removed 3 records, and has written 1 records.");
	}

	@Test
	void deduplicate_OK_test805() {
		String inputFileName = "src/test/resources/test805.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteApplication.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, new MockHttpSession());
		
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(805, 644));
	}

	@Disabled("Very slow test")
	@Test
	void deduplicate_BIG_FILE() {
		String inputFileName = "src/test/resources/DedupEndNote_portal_vein_thrombosis_37741.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteApplication.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, new MockHttpSession());
		
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(37741, 24382));
	}

	@Test
	void deduplicate_NonLatinInput() {
		String inputFileName = "src/test/resources/Non_Latin_input.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteApplication.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, new MockHttpSession());
		
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(2, 2));
	}

	@Test
	void deduplicate_Possibly_missed() {
		String inputFileName = "src/test/resources/Dedup_PATIJ2_Possibly_missed.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteApplication.createOutputFileName(inputFileName, markMode);
		assertThat(new File(inputFileName)).exists();

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, new MockHttpSession());
				
		assertThat(resultString).isEqualTo(deduplicationService.formatResultString(18, 12));
	}

	@Disabled("File missing")
	@Test
	void file_without_IDs() {
		String fileName = "src/test/resources/Recurrance_rate_EndNote_Library_original_deduplicated.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteApplication.createOutputFileName(fileName, markMode);

		String resultString = deduplicationService.deduplicateOneFile(fileName, outputFileName, markMode, new MockHttpSession());
		assertThat(resultString).startsWith("ERROR: The input file contains records without IDs");
	}

	@Test
	void deduplicate_withDuplicateIDs() {
		String inputFileName = "src/test/resources/Bestand_met_duplicate_IDs.txt";
		boolean markMode = false;
		String outputFileName = DedupEndNoteApplication.createOutputFileName(inputFileName, markMode);

		String resultString = deduplicationService.deduplicateOneFile(inputFileName, outputFileName, markMode, new MockHttpSession());

		assertThat(resultString).startsWith("ERROR: The IDs of the records of input file " + inputFileName +  " are not unique");
	}

	@Test
	void addDois() {
		String doiString = "10.1371/journal.pone.11 onzin http://dx.doi.org/10.1371/journal%2EPONE.22. 10.1371/journal.pone";
		Record record = new Record();
		Map<String, Integer> dois = record.addDois(doiString);

		assertThat(dois).containsOnlyKeys("10.1371/journal.pone.11", "10.1371/journal.pone.22",	"10.1371/journal.pone");
	}

	@Test
	void addDoisEscaped() {
		String doiString = "10.1016/S0016-5085(18)34101-5 http://dx.doi.org/10.1016/S0016-5085%2818%2934101-5";
		Record record = new Record();
		Map<String, Integer> dois = record.addDois(doiString);

		assertThat(dois).containsOnlyKeys("10.1016/s0016-5085(18)34101-5");	// is lowercased!
	}

	@Test
	void addIssns() {
		String issn = "0002-9343 (Print) 00029342 (Electronic) 0-12-34567890x (ISBN)";
		Record record = new Record();
		List<String> issns = record.addIssns(issn);

		assertThat(issns).containsAll(Arrays.asList("0002-9343", "0002-9342", "01234567890X"));
	}

	@Test
	void compareIssns() {
		Record r1 = new Record();
		Record r2 = new Record();
		r1.addIssns("0000-0000 1111-1111");
		r2.addIssns("2222-2222 1111-1111");

		assertThat(deduplicationService.compareIssns(r1, r2)).isTrue();

		Record r3 = new Record();
		Record r4 = new Record();
		r3.addIssns("0000-0000");
		r4.addIssns("1111-1111 2222-2222");

		assertThat(deduplicationService.compareIssns(r3, r4)).isFalse();

		Record r5 = new Record();
		Record r6 = new Record();
		r5.addIssns("1234-568x (Print)");
		r6.addIssns("1234568X (ISSN)");

		assertThat(deduplicationService.compareIssns(r5, r6)).isTrue();
	}
	
	@Test
	void checkReply() {
		Pattern replyPattern = Pattern.compile("(.*\\breply\\b.*|.*author(.+)respon.*|^response$)");
		Stream<String> titles = Stream.of("Could TIPS be Applied in All Kinds of Portal Vein Thrombosis: We are not Sure! Reply",
				"Reply");
		
		titles.forEach(t -> assertThat(replyPattern.matcher(t.toLowerCase()).matches()).isTrue());
	}
	
	// FIXME: tests for markMode = true;
}
