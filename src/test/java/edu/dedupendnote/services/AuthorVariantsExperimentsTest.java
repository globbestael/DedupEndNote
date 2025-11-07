package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.PublicationExperiment;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;

/*
 * Test for comparing different implementations of author processing in Publication.
 *
 * See AuthorExperimentsTests for tests of different implementations of author comparisons (i.e. in DeduplicationService).
 */
@Slf4j
class AuthorVariantsExperimentsTest extends AuthorsBaseTest {

	@BeforeAll
	static void beforeAll() {
		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		Logger rootLogger = loggerContext.getLogger("edu.dedupendnote");
		rootLogger.setLevel(Level.INFO);
		log.debug("Logging level set to INFO");
	}

	@Test
	void compareAuthorsWithoutPreprocessingTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		// triples.stream().limit(5).forEach(System.err::println);

		for (Triple triple : triples) {
			Publication r1 = fillPublication(triple.getAuthors1());
			Publication r2 = fillPublication(triple.getAuthors2());

			triple.setJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));

			r1 = fillPublicationAddAuthorsWithoutPreprocessing(triple.getAuthors1());
			r2 = fillPublicationAddAuthorsWithoutPreprocessing(triple.getAuthors2());
			triple.setExpJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));
		}
		// @formatter:off
		/*
		 * There are cases where the most simple treatment (0.73) is better than the default complex one (0,65).
		 * Input (= without preprocessing)								| Default
		 * - Rodriguez, C. E.; Heriberto, B.; Leon, O.; Guthrie, T. B.	| Rodriguez CE; Heriberto B; Leon O; Guthrie TB
		 * - Blanco, C. E. R.; Leon, H. O.; Guthrie, T. B.				| Blanco CER; Leon HO; Guthrie TB
		 * Caused by the comma, space and dot characters in the unprocessed string?
		 */
		// @formatter:on

		showTripleComparisonDetails("AuthorsWithoutPreprocessing", triples, true);

		assertThat(1).isEqualTo(1);
	}

	// @formatter:off
	/*
	 * Very rude comparison which might be usefull for CYK names (because of mixup of first and last names):
	 * - take only the uppercase letters 
	 * - sort them for each author
	 *
	 * Results however are very poor compared to the default implementation (with its transposition of names)
	 */
	// @formatter:on
	@Test
	void compareAuthorsLimitedToFirstLettersTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		// triples.stream().limit(5).forEach(System.err::println);

		for (Triple triple : triples) {
			Publication r1 = fillPublication(triple.getAuthors1());
			Publication r2 = fillPublication(triple.getAuthors2());

			triple.setJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));

			r1 = fillPublicationAddAuthorsLimitedToFirstLetters(triple.getAuthors1());
			r2 = fillPublicationAddAuthorsLimitedToFirstLetters(triple.getAuthors2());
			triple.setExpJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));
		}

		showTripleComparisonDetails("AuthorsLimitedToFirstLetters", triples, false);

		assertThat(1).isEqualTo(1);
	}

	@Test
	void explainPoorResults_AuthorsLimitedToFirstLetters() {
		String author1 = "de Ville de Goyet, J.";
		String author2 = "Degoyet, J. D.";
		Publication r1 = fillPublicationAddAuthorsLimitedToFirstLetters(author1);
		Publication r2 = fillPublicationAddAuthorsLimitedToFirstLetters(author2);
		String a1 = r1.getAllAuthors().get(0);
		String a2 = r2.getAllAuthors().get(0);

		assertThat(a1).isEqualTo("GJV");
		assertThat(a2).isEqualTo("DDJ");
		assertThat(jws.apply(a1, a2)).as("Even with 1 letter out of 3 in common JWS is 0.0").isEqualTo(0.0);
	}

	// @formatter:off
	/*
	 * Compare the same number of authors for both records
	 *
	 * Results: 
	 * - Default never wins, Experiment wins but only in 0,34% of cases 
	 * - Threshold could be higher: 98th percentile 0,72 (instead of 0,68)
	 *
	 * But implementing this will not be easy, and performance (speed, and memory?) might
	 * be impacted. 
	 * Possible solution 
	 * - Publication.numberOfAuthors 
	 * - Publication has an array endOffsets[numberOfAuthors] of ending offsets for each author 
	 * - record 1: numberOfAuthors = n1 
	 * - record 2: numberOfAuthors = n2 
	 * - numberWanted = Math.min(n1, n2) 
	 * - authors1 = if n1 > numberWanted then authors1.substring(0, endOffsets1[numberWanted] -1) else authors1
	 * - authors2 = if n2 > numberWanted then authors2.substring(0, endOffsets2[numberWanted] -1) else authors2
	 */
	// @formatter:on
	@Test
	void compareSameNumberOfAuthorsTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		// triples.stream().limit(5).forEach(System.err::println);

		for (Triple triple : triples) {
			Publication r1 = fillPublication(triple.getAuthors1());
			Publication r2 = fillPublication(triple.getAuthors2());

			triple.setJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));

			List<String> authorList1 = Arrays.asList(triple.getAuthors1().split("; "));
			List<String> authorList2 = Arrays.asList(triple.getAuthors2().split("; "));
			int min = Math.min(authorList1.size(), authorList2.size());

			// r1 = fillPublication(authorList1.subList(0, min).stream().collect(Collectors.joining("; ")));
			// r2 = fillPublication(authorList2.subList(0, min).stream().collect(Collectors.joining("; ")));
			r1 = fillPublication(authorList1.stream().limit(min).collect(Collectors.joining("; ")));
			r2 = fillPublication(authorList2.stream().limit(min).collect(Collectors.joining("; ")));
			triple.setExpJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));
		}

		showTripleComparisonDetails("compareSameNumberOfAuthors", triples, false);

		assertThat(1).isEqualTo(1);
	}

	// @formatter:off
	/*
	 * Compare only the first 10 authors for both records
	 *
	 * Results: 
	 * - Default never wins, Experiment wins but only in 0,17% of cases (2) 
	 * - Threshold could be higher: 98th percentile 0,70 (instead of 0,68)
	 *
	 * Implementing this is quite easy. 
	 * - mwah: in AuthorsComparator 
	 * - better: in Publication: add only the first 10 authors
	 */
	// @formatter:on
	@Test
	void compareOnlyFirst10AuthorsTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		int limit = 10;
		// triples.stream().limit(5).forEach(System.err::println);

		for (Triple triple : triples) {
			Publication r1 = fillPublication(triple.getAuthors1());
			Publication r2 = fillPublication(triple.getAuthors2());

			triple.setJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));

			List<String> authorList1 = Arrays.asList(triple.getAuthors1().split("; "));
			if (authorList1.size() > limit) {
				authorList1 = authorList1.subList(0, limit);
			}
			List<String> authorList2 = Arrays.asList(triple.getAuthors2().split("; "));
			if (authorList2.size() > limit) {
				authorList2 = authorList2.subList(0, limit);
			}

			r1 = fillPublication(authorList1.stream().collect(Collectors.joining("; ")));
			r2 = fillPublication(authorList2.stream().collect(Collectors.joining("; ")));
			triple.setExpJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));
		}

		showTripleComparisonDetails("compareOnlyFirst10Authors", triples, true);

		assertThat(1).isEqualTo(1);
	}

	// @formatter:off
	/*
	 * Compare only the first 5 authors for both records
	 *
	 * Results: 
	 * - Default never wins, Experiment wins but only in 0,25% of cases (3) 
	 * - Threshold could be higher: 98th percentile 0,70 (instead of 0,68)
	 *
	 * Implementing this is quite easy 
	 * - mwah: in AuthorsComparator 
	 * - better: in Publication: add only the first 10 authors
	 */
	// @formatter:on
	@Test
	void compareOnlyFirst5AuthorsTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		int limit = 5;
		// triples.stream().limit(5).forEach(System.err::println);

		for (Triple triple : triples) {
			Publication r1 = fillPublication(triple.getAuthors1());
			Publication r2 = fillPublication(triple.getAuthors2());

			triple.setJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));

			List<String> authorList1 = Arrays.asList(triple.getAuthors1().split("; "));
			if (authorList1.size() > limit) {
				authorList1 = authorList1.subList(0, limit);
			}
			List<String> authorList2 = Arrays.asList(triple.getAuthors2().split("; "));
			if (authorList2.size() > limit) {
				authorList2 = authorList2.subList(0, limit);
			}

			r1 = fillPublication(authorList1.stream().collect(Collectors.joining("; ")));
			r2 = fillPublication(authorList2.stream().collect(Collectors.joining("; ")));
			triple.setExpJws(getHighestSimilarityForAuthors(r1.getAllAuthors(), r2.getAllAuthors()));
		}

		showTripleComparisonDetails("compareOnlyFirst5Authors", triples, false);

		assertThat(1).isEqualTo(1);
	}

	/*
	 * Using only the first letters if uppercased
	 * 
	 * Sorting he first letters:
	 * - good results for when first and last names are switched
	 * 		Real errors: "Clifford, M.; Leah, M.; Charles, N." vs "Mwita, C.; Mwai, L.; Newton, C."
	 * 		Chinese names: "Zheng, H. P.; Hu, Z. P.; Lu, W." vs "Haiping, Zheng; Zhiping, Hu; Wei, Lu"
	 * - bad results with short author lists with differeent conventions, esp when sorting
	 * 		"Weller, S. C." vs "WELLER, SC"		(WSC vs WS, sorting: CSW vs SW)
	 */
	private Publication fillPublicationAddAuthorsWithoutPreprocessing(String authors) {
		PublicationExperiment r = new PublicationExperiment();
		List<String> authorList1 = Arrays.asList(authors.split("; "));
		authorList1.stream().forEach(a -> r.addAuthorsWithoutPreprocessing(a));
		IOService.fillAllAuthors(r);
		return r;
	}

	private Publication fillPublicationAddAuthorsLimitedToFirstLetters(String authors) {
		PublicationExperiment r = new PublicationExperiment();
		List<String> authorList1 = Arrays.asList(authors.split("; "));
		authorList1.stream().forEach(a -> r.addAuthorsLimitedToFirstLetters(a));
		IOService.fillAllAuthors(r);
		// System.err.println(r.getAllAuthors().get(0) + " => " + authors);
		return r;
	}

	/*
	 * Utility functions
	 */
	class TripleComparatorDefault implements Comparator<Triple> {

		public int compare(Triple a, Triple b) {
			Double diff1 = a.getJws() - a.getExpJws();
			Double diff2 = b.getJws() - b.getExpJws();
			return diff2.compareTo(diff1);
		}

	}

	class TripleComparatorExperiment implements Comparator<Triple> {

		public int compare(Triple a, Triple b) {
			Double diff1 = a.getJws() - a.getExpJws();
			Double diff2 = b.getJws() - b.getExpJws();
			return diff1.compareTo(diff2);
		}

	}

	private String showTripleComparison(Triple t) {
		return "JWS %.2f vs %.2f (%.2f):\n- %s\n- %s".formatted(t.getJws(), t.getExpJws(), t.getJws() - t.getExpJws(),
				t.getAuthors1(), t.getAuthors2());
	}

	private void showTripleComparisonDetails(String nameExperiment, List<Triple> triples, boolean onlySummary) {
		// double threshold = 0.85;
		double threshold = DefaultAuthorsComparisonService.AUTHOR_SIMILARITY_NO_REPLY;

		List<Triple> better = triples.stream().filter(t -> t.getJws() > threshold && t.getExpJws() <= threshold)
				.collect(Collectors.toList());
		better.sort(new TripleComparatorDefault());

		List<Triple> worse = triples.stream().filter(t -> t.getJws() <= threshold && t.getExpJws() > threshold)
				.collect(Collectors.toList());
		worse.sort(new TripleComparatorExperiment());

		List<Triple> bothBelow = triples.stream().filter(t -> t.getJws() <= threshold && t.getExpJws() <= threshold)
				.collect(Collectors.toList());
		bothBelow.sort(new TripleComparatorDefault());

		if (!onlySummary) {
			System.err.println("\nDefault algorithm better than algorithm " + nameExperiment
					+ "\n=========================================");
			better.stream().forEach(t -> System.err.println(showTripleComparison(t)));

			System.err.println("\nDefault algorithm worse than algorithm " + nameExperiment
					+ "\n=========================================");
			worse.stream().forEach(t -> System.err.println(showTripleComparison(t)));

			System.err.println("\nMissed by both default algorithm and " + nameExperiment
					+ "\n=========================================");
			bothBelow.stream().forEach(t -> System.err.println(showTripleComparison(t)));
		}

		showTripleComparisonSummary(nameExperiment, triples, better, worse, bothBelow);
	}

	private void showTripleComparisonSummary(String nameExperiment, List<Triple> triples, List<Triple> better,
			List<Triple> worse, List<Triple> bothBelow) {
		System.err.println("\nExperiment: " + nameExperiment);
		System.err.println("--------------------------------------------------------------------");
		System.err.println(
				"| %10s | %15s | %15s | %15s |".formatted("Total", "Default wins", "Experiment wins", "Both below"));
		System.err.println("--------------------------------------------------------------------");
		System.err.println("| %10d | %7d (%2.2f%%) | %7d (%2.2f%%) | %7d (%2.2f%%) |".formatted(triples.size(),
				better.size(), better.size() * 100.0 / triples.size(), worse.size(),
				worse.size() * 100.0 / triples.size(), bothBelow.size(), bothBelow.size() * 100.0 / triples.size()));
		System.err.println("--------------------------------------------------------------------");
		triples.sort(Comparator.comparing(Triple::getJws).reversed());
		double default98 = percentile(triples, 98);
		double default99 = percentile(triples, 99);
		triples.sort(Comparator.comparing(Triple::getExpJws).reversed());
		double exp98 = expPercentile(triples, 98);
		double exp99 = expPercentile(triples, 99);
		System.err.println("98th percentile for default at %2.2f, for experiment at %2.2f: experiment wins? %b"
				.formatted(default98, exp98, default98 < exp98));
		System.err.println("99th percentile for default at %2.2f, for experiment at %2.2f: experiment wins? %b"
				.formatted(default99, exp99, default99 < exp99));
	}

}
