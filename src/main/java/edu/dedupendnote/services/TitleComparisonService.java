package edu.dedupendnote.services;

import edu.dedupendnote.domain.Publication;

public interface TitleComparisonService {
    boolean compare(Publication r1, Publication r2);
}
