package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import edu.dedupendnote.domain.Publication;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;

/*
 * Testing the threshold of author comparison with the authors of validated duplicates
 */
@Slf4j
@TestConfiguration
class AuthorsComparisonThresholdTest extends AuthorsBaseTest {

	// private final DeduplicationService deduplicationService;
	// private final DedupEndNoteController dedupEndNoteController;
	// private final DedupEndNoteApplication dedupEndNoteApplication;
	// private final ComparisonService comparisonService;
	// private final ComparisonService comparisonService;
	List<Triple> triples = new ArrayList<>();

	// AuthorComparisonThresholdTest(ComparisonService comparisonService, DedupEndNoteApplication
	// dedupEndNoteApplication,
	// DedupEndNoteController dedupEndNoteController, DeduplicationService deduplicationService) {
	// this.comparisonService = comparisonService;
	// this.dedupEndNoteApplication = dedupEndNoteApplication;
	// this.dedupEndNoteController = dedupEndNoteController;
	// this.deduplicationService = deduplicationService;
	// }

	// AuthorComparisonThresholdTest(ComparisonService comparisonService) {
	// this.comparisonService = comparisonService;
	// }

	@BeforeAll
	static void beforeAll() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote");
		rootLogger.setLevel(Level.INFO);
		log.debug("Logging level set to INFO");
	}

	@BeforeEach
	void before() throws IOException {
		triples = getValidatedAuthorsPairs();
	}

	@Test
	void test() {
		// triples.stream().limit(5).forEach(System.err::println);
		List<Triple> filledTriples = new ArrayList<>();

		/*
		 * The cases where one of both allAuthors is empty should be skipped
		 */
		for (Triple triple : triples) {
			Publication r1 = fillPublication(triple.getAuthors1());
			Publication r2 = fillPublication(triple.getAuthors2());
			triple.setJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));
			if (!r1.getAllAuthors().isEmpty() && !r2.getAllAuthors().isEmpty()) {
				filledTriples.add(triple);
			}
		}
		// Show the 10 lowest JWS values
		filledTriples.sort(Comparator.comparing(Triple::getJws));

		// The VS Code debug console does not show UTF-8 characters correctly with System.err::println
		// triples.stream().limit(10).forEach(System.err::println);
		filledTriples.stream().limit(10).forEach(t -> log.error(t.toString()));

		// Print the triples which are below the AUTHOR_SIMILARITY_NO_REPLY
		log.error("Current threshold does not accept following pairs: ");
		for (Triple triple : filledTriples) {
			if (triple.jws < DefaultAuthorsComparisonService.AUTHOR_SIMILARITY_NO_REPLY) {
				log.error("\n- {}\n- {}\n", triple.authors1, triple.authors2);
			}
		}

		/*
		 * Show the top 10 percentiles with their JWS threshold.comparisonService
		 * At present the threshold of the 98% percentile is used.
		 * 
		 * Not sure if the current selection of triples is right:
		 * - (in SQL) only the cases where authors strings are different triples 
		 * - distinct pairs? In getValidatedAuthorsPairs() the pairs were originally deduplicated
		 * 
		 * If all pairs (even if the same, and if not unique) are used, the 98% percentile would be higher.
		 * But are these added cases the ones which make the choice of the compairson by JWS worthwhile?
		 * 
		 * TODO: Look at the pairs which are just above the current threshold. 
		 * We know the current threshold is very forgiving, maybe too forgiving.
		 * Does the authorComparison have any influence on the results?
		 */
		filledTriples.sort(Comparator.comparing(Triple::getJws).reversed());

		/*
		 * These percentiles are for the DIFFERENT author strings (SQL used DISTINCT)
		 */
		for (int i = 100; i > 90; i--) {
			System.err.println("At " + i + ": " + percentile(filledTriples, i));
		}
		assertThat(percentile(filledTriples, 98))
				.as("98% of validated authors pairs are above the threshold AUTHOR_SIMILARITY_NO_REPLY")
				.isGreaterThan(DefaultAuthorsComparisonService.AUTHOR_SIMILARITY_NO_REPLY);
		assertThat(percentile(filledTriples, 99)).as(
				"AUTHOR_SIMILARITY_NO_REPLY could have a higher value because 99% of validated authors pairs are above this threshold")
				.isLessThan(DefaultAuthorsComparisonService.AUTHOR_SIMILARITY_NO_REPLY);

	}
}
