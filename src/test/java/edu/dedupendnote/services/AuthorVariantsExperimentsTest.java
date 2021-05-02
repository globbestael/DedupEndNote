package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import edu.dedupendnote.domain.Record;
import edu.dedupendnote.domain.RecordExperiment;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;

/*
 * Test for comparing different implementations of author processing in Record.
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
	void addAuthorsWithoutPreprocessingTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		// triples.stream().limit(5).forEach(System.err::println);
		
		for (Triple triple : triples) {
			Record r1 = fillRecordDefault(triple.getAuthors1());
			Record r2 = fillRecordDefault(triple.getAuthors2());
			
			triple.setJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
			
			r1 = fillRecordAddAuthorsWithoutPreprocessing(triple.getAuthors1());
			r2 = fillRecordAddAuthorsWithoutPreprocessing(triple.getAuthors2());
			triple.setExpJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
		}
		
		// FIXME: why are there cases where the most simple treatment is better than the default complex one?
		showTripleComparisonDetails("AuthorsWithoutPreprocessing", triples, true);

		fail("Not yet implemented");
	}

	/*
	 * Very rude comparison which might be usefull for CYK names (because of mixup of first and last names):
	 * - take only the uppercase letters
	 * - sort them for each author
	 * 
	 * Results however are very poor compared to the default implementation (with its transposition of names)
	 */
	@Test
	void addAuthorsLimitedToFirstLettersTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		// triples.stream().limit(5).forEach(System.err::println);
		
		for (Triple triple : triples) {
			Record r1 = fillRecordDefault(triple.getAuthors1());
			Record r2 = fillRecordDefault(triple.getAuthors2());
			
			triple.setJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
			
			r1 = fillRecordAddAuthorsLimitedToFirstLetters(triple.getAuthors1());
			r2 = fillRecordAddAuthorsLimitedToFirstLetters(triple.getAuthors2());
			triple.setExpJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
		}
		
		// FIXME: why are there cases where the most simple treatment is better than the default complex one?
		showTripleComparisonDetails("AuthorsLimitedToFirstLetters", triples, true);

		fail("Not yet implemented");
	}
	
	@Test
	void explainPoorResults_AuthorsLimitedToFirstLetters() {
		String author1 = "de Ville de Goyet, J.";
		String author2 = "Degoyet, J. D.";
		Record r1 = fillRecordAddAuthorsLimitedToFirstLetters(author1);
		Record r2 = fillRecordAddAuthorsLimitedToFirstLetters(author2);
		String a1 = r1.getAllAuthors().get(0); 
		String a2 = r2.getAllAuthors().get(0);
		
		assertThat(a1).isEqualTo("GJV");
		assertThat(a2).isEqualTo("DDJ");
		assertThat(0.0)
			.as("Even with 1 letter out of 3 in common JWS is 0.0")
			.isEqualTo(jws.apply(a1, a2));
	}

	/*
	 * Compare the same number of authors for both records
	 * 
	 * Results: 
	 * - Default never wins, Experiment wins but only in 0,34% of cases
	 * - Threshold could be higher: 98th percentile 0,72 (instead of 0,68)
	 * 
	 * But implementing this will not be easy, and performance (speed, and memory?) might be impacted.
	 * Possible solution
	 * - Record.numberOfAuthors
	 * - Record has an array endOffsets[numberOfAuthors] of ending offsets for each author
	 * - record 1: numberOfAuthors = n1 
	 * - record 2: numberOfAuthors = n2
	 * - numberWanted = Math.min(n1, n2)
	 * - authors1 = if n1 > numberWanted then authors1.substring(0, endOffsets1[numberWanted] -1) else authors1
	 * - authors2 = if n2 > numberWanted then authors2.substring(0, endOffsets2[numberWanted] -1) else authors2
	 */
	@Test
	void compareSameNumberOfAuthorsTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		// triples.stream().limit(5).forEach(System.err::println);
		
		for (Triple triple : triples) {
			Record r1 = fillRecordDefault(triple.getAuthors1());
			Record r2 = fillRecordDefault(triple.getAuthors2());
			
			triple.setJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
			
			List<String> authorList1 = Arrays.asList(triple.getAuthors1().split("; "));
			List<String> authorList2 = Arrays.asList(triple.getAuthors2().split("; "));
			int min = Math.min(authorList1.size(), authorList2.size());
			
			r1 = fillRecordDefault(authorList1.subList(0, min).stream().collect(Collectors.joining("; ")));
			r2 = fillRecordDefault(authorList2.subList(0, min).stream().collect(Collectors.joining("; ")));
			triple.setExpJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
		}
		
		showTripleComparisonDetails("compareSameNumberOfAuthors", triples, true);

		fail("Not yet implemented");
	}
	
	/*
	 * Compare only the first 10 authors for both records
	 * 
	 * Results: 
	 * - Default never wins, Experiment wins but only in 0,17% of cases (2)
	 * - Threshold could be higher: 98th percentile 0,70 (instead of 0,68)
	 * 
	 * Implementing this is quite easy.
	 * - mwah: in AuthorsComparator
	 * - better: in Record: add only the first 10 authors
	 */
	@Test
	void compareOnlyFirst10AuthorsTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		int limit = 10;
		// triples.stream().limit(5).forEach(System.err::println);
		
		for (Triple triple : triples) {
			Record r1 = fillRecordDefault(triple.getAuthors1());
			Record r2 = fillRecordDefault(triple.getAuthors2());
			
			triple.setJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
			
			List<String> authorList1 = Arrays.asList(triple.getAuthors1().split("; "));
			if (authorList1.size() > limit) {
				authorList1 = authorList1.subList(0, limit);
			}
			List<String> authorList2 = Arrays.asList(triple.getAuthors2().split("; "));
			if (authorList2.size() > limit) {
				authorList2 = authorList2.subList(0, limit);
			}
			
			r1 = fillRecordDefault(authorList1.stream().collect(Collectors.joining("; ")));
			r2 = fillRecordDefault(authorList2.stream().collect(Collectors.joining("; ")));
			triple.setExpJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
		}
		
		showTripleComparisonDetails("compareOnlyFirst10Authors", triples, true);

		fail("Not yet implemented");
	}
	
	/*
	 * Compare only the first 5 authors for both records
	 * 
	 * Results: 
	 * - Default never wins, Experiment wins but only in 0,25% of cases (3)
	 * - Threshold could be higher: 98th percentile 0,70 (instead of 0,68)
	 * 
	 * Implementing this is quite easy
	 * - mwah: in AuthorsComparator
	 * - better: in Record: add only the first 10 authors
	 */
	@Test
	void compareOnlyFirst5AuthorsTest() throws IOException {
		List<Triple> triples = getValidatedAuthorsPairs();
		int limit = 5;
		// triples.stream().limit(5).forEach(System.err::println);
		
		for (Triple triple : triples) {
			Record r1 = fillRecordDefault(triple.getAuthors1());
			Record r2 = fillRecordDefault(triple.getAuthors2());
			
			triple.setJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
			
			List<String> authorList1 = Arrays.asList(triple.getAuthors1().split("; "));
			if (authorList1.size() > limit) {
				authorList1 = authorList1.subList(0, limit);
			}
			List<String> authorList2 = Arrays.asList(triple.getAuthors2().split("; "));
			if (authorList2.size() > limit) {
				authorList2 = authorList2.subList(0, limit);
			}
			
			r1 = fillRecordDefault(authorList1.stream().collect(Collectors.joining("; ")));
			r2 = fillRecordDefault(authorList2.stream().collect(Collectors.joining("; ")));
			triple.setExpJws(getHighestSimilarity(r1.getAllAuthors(), r2.getAllAuthors()));
		}
		
		showTripleComparisonDetails("compareOnlyFirst5Authors", triples, true);

		fail("Not yet implemented");
	}
	

	// Default implementation
	private Record fillRecordDefault(String authors) {
		Record r = new Record();
		List<String> authorList1 = Arrays.asList(authors.split("; "));
		authorList1.stream().forEach(a -> r.addAuthors(a));
		r.fillAllAuthors();
		return r;
	}

	private Record fillRecordAddAuthorsWithoutPreprocessing(String authors) {
		RecordExperiment r = new RecordExperiment();
		List<String> authorList1 = Arrays.asList(authors.split("; "));
		authorList1.stream().forEach(a -> r.addAuthorsWithoutPreprocessing(a));
		r.fillAllAuthors();
		return r;
	}

	private Record fillRecordAddAuthorsLimitedToFirstLetters(String authors) {
		RecordExperiment r = new RecordExperiment();
		List<String> authorList1 = Arrays.asList(authors.split("; "));
		authorList1.stream().forEach(a -> r.addAuthorsLimitedToFirstLetters(a));
		r.fillAllAuthors();
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
		return String.format("JWS %.2f vs %.2f (%.2f):\n- %s\n- %s",
							  t.getJws(), t.getExpJws(), t.getJws() - t.getExpJws(), t.getAuthors1(), t.getAuthors2());
	}
	
	private void showTripleComparisonDetails(String nameExperiment, List<Triple> triples, boolean onlySummary) {
	//		double threshold = 0.85;
			double threshold = DeduplicationService.DefaultAuthorsComparator.AUTHOR_SIMILARITY_NO_REPLY;

			List<Triple> better = triples.stream()
				.filter(t -> t.getJws() > threshold && t.getExpJws() <= threshold)
				.collect(Collectors.toList());
			better.sort(new TripleComparatorDefault());
			
			List<Triple> worse = triples.stream()
				.filter(t -> t.getJws() <= threshold && t.getExpJws() > threshold)
				.collect(Collectors.toList());
			worse.sort(new TripleComparatorExperiment());
			
			List<Triple> bothBelow = triples.stream()
				.filter(t -> t.getJws() <= threshold && t.getExpJws() <= threshold)
				.collect(Collectors.toList());
			bothBelow.sort(new TripleComparatorDefault());

			if (!onlySummary) {
				System.err.println("\nDefault algorithm better than algorithm " + nameExperiment + "\n=========================================");
				better.stream().forEach(t -> System.err.println(showTripleComparison(t)));
		
				System.err.println("\nDefault algorithm worse than algorithm " + nameExperiment + "\n=========================================");
				worse.stream().forEach(t -> System.err.println(showTripleComparison(t)));
		
				System.err.println("\nMissed by both default algorithm and " + nameExperiment + "\n=========================================");
				bothBelow.stream().forEach(t -> System.err.println(showTripleComparison(t)));
			}
	
			showTripleComparisonSummary(nameExperiment, triples, better, worse, bothBelow);
	}

	private void showTripleComparisonSummary(String nameExperiment, List<Triple> triples, List<Triple> better, List<Triple> worse, List<Triple> bothBelow) {
		System.err.println("\nExperiment: " + nameExperiment);
		System.err.println("--------------------------------------------------------------------");
		System.err.println(String.format("| %10s | %15s | %15s | %15s |", "Total", "Default wins", "Experiment wins", "Both below"));
		System.err.println("--------------------------------------------------------------------");
		System.err.println(String.format("| %10d | %7d (%2.2f%%) | %7d (%2.2f%%) | %7d (%2.2f%%) |",
				triples.size(), better.size(), better.size() * 100.0 / triples.size(),
				worse.size(), worse.size() * 100.0 / triples.size(),
				bothBelow.size(), bothBelow.size() * 100.0 / triples.size()));
		System.err.println("--------------------------------------------------------------------");
		triples.sort(Comparator.comparing(Triple::getJws).reversed());
		double default98 = percentile(triples, 98);
		double default99 = percentile(triples, 99);
		triples.sort(Comparator.comparing(Triple::getExpJws).reversed());
		double exp98 = expPercentile(triples, 98);
		double exp99 = expPercentile(triples, 99);
		System.err.println(String.format("98th percentile for default at %2.2f, for experiment at %2.2f: experiment wins? %b",
				default98, exp98, default98 < exp98));
		System.err.println(String.format("99th percentile for default at %2.2f, for experiment at %2.2f: experiment wins? %b",
				default99, exp99, default99 < exp99));
	}
}
