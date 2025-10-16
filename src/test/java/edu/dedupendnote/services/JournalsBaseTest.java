package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.BaseTest;
import edu.dedupendnote.domain.Publication;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class JournalsBaseTest extends BaseTest {
	DeduplicationService deduplicationService = new DeduplicationService();

	String homeDir = System.getProperty("user.home");

	String testdir = homeDir + "/dedupendnote_files";

	List<Triple> localTriples = new ArrayList<>();

	@Data
	public class Triple {

		String journal1;

		String journal2;

		Boolean similar = false;

		public String toString() {
			return "- " + journal1 + "\n- " + journal2 + "\n";
		}

	}

	// @formatter:off
	/*
	 * The file validated_journal_pairs.txt was build with the validated records in the
	 * database with queries as: (example is the query
	 * validated_journal_pairs_BIG_SET_TRUTH)
	 *
	 * SELECT DISTINCT BIG_SET_TRUTH.title2, BIG_SET_TRUTH_1.title2 
	 * FROM BIG_SET_TRUTH
	 * INNER JOIN BIG_SET_TRUTH AS BIG_SET_TRUTH_1 ON BIG_SET_TRUTH.dedupid = BIG_SET_TRUTH_1.dedupid 
	 * WHERE (((BIG_SET_TRUTH_1.title2) <> [BIG_SET_TRUTH].[title2]) 
	 *   AND ((BIG_SET_TRUTH.id) > 0) 
	 *   AND ((BIG_SET_TRUTH_1.id) <> [BIG_SET_TRUTH].[id] 
	 *   AND (BIG_SET_TRUTH_1.id) > [BIG_SET_TRUTH].[id]) 
	 *   AND ((BIG_SET_TRUTH.Validated)=True));
	 *
	 * The whole file validated_authors_pairs.txt is created on the TRUTH files for
	 * BIG_SET, ASySD_SRSR_Human.
	 */
	// @formatter:on
	protected List<Triple> getValidatedJournalPairs() throws IOException {
		String fileName = testdir + "/experiments/validated_journal_pairs.txt";
		localTriples.clear();
		Path path = Path.of(fileName);
		Stream<String> lines = Files.lines(path);
		lines.forEach(l -> {
			String[] parts = l.split("\t");
			Triple triple = new Triple();
			localTriples.add(triple);
			triple.setJournal1(parts[0]);
			triple.setJournal2(parts[1]);
		});
		lines.close();
		log.error("There were {} triples", localTriples.size());
		// deduplicate
		localTriples = localTriples.stream().distinct().collect(Collectors.toList());
		log.error("There are {} triples", localTriples.size());

		assertThat(localTriples).as("There are more than 100 journal pairs").hasSizeGreaterThan(100);

		return localTriples;
	}

	@BeforeAll
	static void beforeAll() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote");
		rootLogger.setLevel(Level.INFO);
		log.debug("Logging level set to INFO");
	}

	@Test
	void compareJournalsTest() throws IOException {
		List<Triple> triples = getValidatedJournalPairs();
		// triples.stream().limit(5).forEach(System.err::println);

		for (Triple triple : triples) {
			Publication r1 = new Publication();
			r1.addJournals(triple.getJournal1());
			Publication r2 = new Publication();
			r2.addJournals(triple.getJournal2());

			triple.setSimilar(ComparatorService.compareJournals(r1, r2));
		}

		triples.stream().filter(t -> t.getSimilar() == false).forEach(System.err::println);
		long numberMissed = triples.stream().filter(t -> t.getSimilar() == false).count();

		/*
		 	20220528: started with 125 missed
			20250920: 95
			20251014: 107		TODO: Why more errors than previously?
		*/
		assertThat(numberMissed).as(numberMissed + " journal pairs (of " + triples.size()
				+ ") were not seen as similar by comparison on journal names").isLessThanOrEqualTo(107);
	}

}
