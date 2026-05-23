package edu.dedupendnote.services;

import edu.dedupendnote.domain.BibliographicItem;

public interface AuthorsComparisonService {

	boolean compare(BibliographicItem r1, BibliographicItem r2);

	Double getSimilarity();

}
