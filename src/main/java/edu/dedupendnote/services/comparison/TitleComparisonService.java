package edu.dedupendnote.services.comparison;

import edu.dedupendnote.domain.BibliographicItem;

public interface TitleComparisonService {
    boolean compare(BibliographicItem r1, BibliographicItem r2);
}
