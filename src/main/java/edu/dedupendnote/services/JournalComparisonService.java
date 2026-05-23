package edu.dedupendnote.services;

import org.jspecify.annotations.Nullable;

import edu.dedupendnote.domain.BibliographicItem;

public interface JournalComparisonService {
    boolean compare(BibliographicItem r1, BibliographicItem r2, @Nullable Boolean isSameDois);
}
