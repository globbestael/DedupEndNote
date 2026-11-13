package edu.dedupendnote.services.comparison;

import edu.dedupendnote.domain.BibliographicItem;

public interface JournalComparisonService {
    /**
     * Compares the journal names. Returns false when both items have ISBNs (books are compared with
     * {@link #compareIssns}, not by journal name); ISSNs are not compared here.
     */
    boolean compare(BibliographicItem r1, BibliographicItem r2);

    /** Compares the ISBNs (if both have them) or else the ISSNs. */
    boolean compareIssns(BibliographicItem r1, BibliographicItem r2);
}
