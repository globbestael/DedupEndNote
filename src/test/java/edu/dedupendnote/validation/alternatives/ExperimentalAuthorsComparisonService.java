package edu.dedupendnote.validation.alternatives;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.services.AuthorsComparisonService;
import lombok.extern.slf4j.Slf4j;

/**
 * An experimental author comparator that uses higher JWS thresholds than the production
 * DefaultAuthorsComparisonService. Because the thresholds exceed 1.0 (the maximum JWS score),
 * this implementation never finds an author match, making it useful as a worst-case experiment:
 * sensitivity drops to 0% (all duplicates are missed) while specificity reaches 100%.
 */
@Slf4j
public class ExperimentalAuthorsComparisonService implements AuthorsComparisonService {

	public static final Double AUTHOR_SIMILARITY_NO_REPLY = 0.67 + 0.5;
	public static final Double AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.75 + 0.2;
	public static final Double AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.80 + 0.2;

	private JaroWinklerSimilarity jws = new JaroWinklerSimilarity();
	Double similarity = 0.0;

	@Override
	public boolean compare(Publication r1, Publication r2) {
		log.error("Using the experimental AuthorComparator");
		boolean isReply = (r1.isReply() || r2.isReply());
		boolean sufficientStartPages = (r1.getPageStart() != null && r2.getPageStart() != null);
		boolean sufficientDois = (!r1.getDois().isEmpty() && !r2.getDois().isEmpty());

		if (r1.getAllAuthors().isEmpty() || r2.getAllAuthors().isEmpty()) {
			/*
			 * Because:
			 * - Anonymous AND Reply would only compare on journals (and maybe SP/DOIs)
			 *  (see "MedGenMed Medscape General Medicine" articles in Cannabis test set)
			 * - Anonymous AND no SP or DOI would only compare on title and journals
			 *   (see "Abstracts of 16th National Congress of SIGENP" articles in Joost problem set)
			 */
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
