package edu.dedupendnote.services;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import edu.dedupendnote.domain.BibliographicItem;

public interface PagesComparisonService {
    boolean compare(BibliographicItem r1, BibliographicItem r2, Map<String, @Nullable Boolean> map);
}
