package edu.dedupendnote.services;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import edu.dedupendnote.domain.Publication;

public interface PagesComparisonService {
    boolean compare(Publication r1, Publication r2, Map<String, @Nullable Boolean> map);
}
