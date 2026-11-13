package edu.dedupendnote.services.comparison;

import edu.dedupendnote.domain.BibliographicItem;

public interface PagesComparisonService {
    boolean compare(BibliographicItem r1, BibliographicItem r2);
}
