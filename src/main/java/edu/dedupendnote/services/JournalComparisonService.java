package edu.dedupendnote.services;

import org.jspecify.annotations.Nullable;

import edu.dedupendnote.domain.Publication;

public interface JournalComparisonService {
    boolean compare(Publication r1, Publication r2, @Nullable Boolean isSameDois);
}
