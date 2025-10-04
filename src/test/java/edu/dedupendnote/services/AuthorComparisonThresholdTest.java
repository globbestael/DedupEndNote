package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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
class AuthorComparisonThresholdTest extends AuthorsBaseTest {

	List<Triple> triples = new ArrayList<>();

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

		for (Triple triple : triples) {
			Publication r1 = fillRecord(triple.getAuthors1());
			Publication r2 = fillRecord(triple.getAuthors2());
			triple.setJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
		}
		triples.sort(Comparator.comparing(Triple::getJws).reversed());

		// The VS Code debug console does not show UTF-8 characters correctly with System.err::println
		// triples.stream().limit(10).forEach(System.err::println);
		triples.stream().limit(10).forEach(t -> log.error(t.toString()));

		for (int i = 100; i > 90; i--) {
			System.err.println("At " + i + ": " + percentile(triples, i));
		}
		assertThat(percentile(triples, 98))
				.as("98% of validated authors pairs are above the threshold AUTHOR_SIMILARITY_NO_REPLY")
				.isGreaterThan(DeduplicationService.DefaultAuthorsComparator.AUTHOR_SIMILARITY_NO_REPLY);
		assertThat(percentile(triples, 99)).as(
				"AUTHOR_SIMILARITY_NO_REPLY could have a higher value because 99% of validated authors pairs are above this threshold")
				.isLessThan(DeduplicationService.DefaultAuthorsComparator.AUTHOR_SIMILARITY_NO_REPLY);
	}

	private Publication fillRecord(String authors) {
		Publication r = new Publication();
		List<String> authorList1 = Arrays.asList(authors.split("; "));
		authorList1.stream().forEach(a -> r.addAuthors(a));
		r.fillAllAuthors();
		return r;
	}

}
