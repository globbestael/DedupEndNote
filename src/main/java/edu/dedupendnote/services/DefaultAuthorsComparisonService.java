package edu.dedupendnote.services;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import edu.dedupendnote.domain.Publication;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultAuthorsComparisonService implements AuthorsComparisonService {

	public static final Double AUTHOR_SIMILARITY_NO_REPLY = 0.67;
	public static final Double AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.80;
	public static final Double AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.75;

	private static JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	private Double similarity = 0.0;

	/*
	 * See AuthorVariantsExperimentsTest for possible enhancements.
	 */
	@Override
	public boolean compare(Publication r1, Publication r2) {
		similarity = 0.0;
		boolean isReply = r1.isReply() || r2.isReply();
		boolean sufficientStartPages = r1.getPageForComparison() != null && r2.getPageForComparison() != null;
		boolean sufficientDois = !r1.getDois().isEmpty() && !r2.getDois().isEmpty();

		if (r1.getAllAuthors().isEmpty() || r2.getAllAuthors().isEmpty()) {
			// Because Anonymous would only compare on journals (and maybe SP/DOIs)
			// (see "MedGenMed Medscape General Medicine" articles in Cannabis test set)
			// Because Anonymous AND no SP or DOI would only compare on title and journals
			// (see "Abstracts of 16th National Congress of SIGENP" articles in Joost problem set)
			if (!sufficientStartPages && !sufficientDois) {
				/*
				 * Exception within the exception:
				 * Conference proceedings (and books?) have no author (AU, maybe A2 which is not used).
				 * If they have an ISBN, the author comparison without authors returns true.
				 */
				if (!r1.getIsbns().isEmpty() && !r2.getIsbns().isEmpty()) {
					log.trace("- 2. No authors, startpages or DOIs, but ISBNs are present, considered the same");
					return true;
				}
				log.trace(
						"- 2. Not the same because not enough data: One or both authors are empty AND not enough starting pages AND not enough DOIs");
				return false;
			}
			log.trace("- 2. One or both authors are empty");
			return true;
		}

		for (String authors1 : r1.getAllAuthors()) {
			for (String authors2 : r2.getAllAuthors()) {
				similarity = jws.apply(authors1, authors2);
				if (isReply) {
					// TODO: do we have examples of this case?
					if (!(sufficientStartPages || sufficientDois)
							&& similarity > AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS) {
						return true;
					}
					if ((sufficientStartPages || sufficientDois)
							&& similarity > AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS) {
						return true;
					}
				} else if (similarity > AUTHOR_SIMILARITY_NO_REPLY) {
					log.trace("- 2. Author similarity is above threshold");
					return true;
				}
			}
		}
		if (log.isTraceEnabled()) {
			log.trace("- 2. Author similarity {} is below threshold: {} and {}", similarity, r1.getAllAuthors(),
					r2.getAllAuthors());
		}
		return false;
	}

	@Override
	public Double getSimilarity() {
		return similarity;
	}

}