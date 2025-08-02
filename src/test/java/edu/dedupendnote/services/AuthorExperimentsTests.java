package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.Test;

import edu.dedupendnote.domain.Publication;
import lombok.extern.slf4j.Slf4j;

/*
 * Test for comparing different implementations of author comparisons; VERY INCOMPLETE
 *
 * The current ExperimentalAuthorsComparator just uses higher values for AUTHOR_SIMILARITY_... and compares deduplication results with the
 * production version.
 */
@Slf4j
class AuthorExperimentsTests {

	DeduplicationService service = new DeduplicationService();

	AuthorsComparator authorsComparator = new ExperimentalAuthorsComparator();

	DeduplicationService expService = new DeduplicationService(authorsComparator);

	String homeDir = System.getProperty("user.home");

	String testdir = homeDir + "/dedupendnote_files";

	String wssessionId = "";

	public static class ExperimentalAuthorsComparator implements AuthorsComparator {

		public static final Double AUTHOR_SIMILARITY_NO_REPLY = 0.67 + 0.5;

		public static final Double AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.75 + 0.2;

		public static final Double AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.80 + 0.2;

		private JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

		Double similarity = 0.0;

		@Override
		public boolean compare(Publication r1, Publication r2) {
			log.error("Using the experimental AuthorComparator");
			boolean isReply = (r1.isReply() || r2.isReply());
			boolean sufficientStartPages = (r1.getPageForComparison() != null && r2.getPageForComparison() != null);
			boolean sufficientDois = (!r1.getDois().isEmpty() && !r2.getDois().isEmpty());

			if (r1.getAllAuthors().isEmpty() || r2.getAllAuthors().isEmpty()) {
				// Because Anonymous AND Reply would only compare on journals (and maybe
				// SP/DOIs) (see "MedGenMed Medscape General Medicine" articles in
				// Cannabis test set)
				// Because Anonymous AND no SP or DOI would only compare on title and
				// journals (see "Abstracts of 16th National Congress of SIGENP" articles
				// in Joost problem set)
				if (isReply || (!sufficientStartPages && !sufficientDois)) {
					return false;
				}
				return true;
			}

			for (String authors1 : r1.getAllAuthors()) {
				for (String authors2 : r2.getAllAuthors()) {
					Double similarity = jws.apply(authors1, authors2);
					if (isReply) {
						// TODO: do we have examples of this case?
						if (!sufficientStartPages && similarity > AUTHOR_SIMILARITY_NO_REPLY) {
							return true;
						} else if (sufficientStartPages
								&& similarity > AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS) {
							return true;
						}
					} else if (similarity > AUTHOR_SIMILARITY_NO_REPLY) {
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public Double getSimilarity() {
			return similarity;
		}

	}

	@Test
	void higherAuthorSimilarityFindsLessDuplicates() {
		String subdir = testdir + "/experiments/";
		String inputFileName = subdir + "t1.txt";
		boolean markMode = false;
		String outputFileName = subdir + "t1_mark.txt";

		String resultString = service.deduplicateOneFile(inputFileName, outputFileName, markMode, wssessionId);

		assertThat(service.formatResultString(4, 1)).isEqualTo(resultString);

		String expResultString = expService.deduplicateOneFile(inputFileName, outputFileName, markMode, wssessionId);

		assertThat(resultString).isNotEqualTo(expResultString);
		assertThat(service.formatResultString(4, 4)).isEqualTo(expResultString);
	}

}
