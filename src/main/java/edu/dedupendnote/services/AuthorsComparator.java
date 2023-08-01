package edu.dedupendnote.services;

import edu.dedupendnote.domain.Publication;

public interface AuthorsComparator {

	boolean compare(Publication r1, Publication r2);

	Double getSimilarity();

}
