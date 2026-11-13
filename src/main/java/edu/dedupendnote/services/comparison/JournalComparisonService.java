package edu.dedupendnote.services.comparison;

import edu.dedupendnote.domain.BibliographicItem;

public interface JournalComparisonService {
    /** Compares the journal names only (no ISBN/ISSN comparison). */
    boolean compare(BibliographicItem r1, BibliographicItem r2);

    /** Compares the ISBNs (if both have them) or else the ISSNs. */
    boolean compareIssns(BibliographicItem r1, BibliographicItem r2);
}
