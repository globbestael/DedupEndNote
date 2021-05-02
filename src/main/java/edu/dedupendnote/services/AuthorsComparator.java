package edu.dedupendnote.services;

import edu.dedupendnote.domain.Record;

public interface AuthorsComparator {
	boolean compare(Record r1, Record r2);
	Double getSimilarity();
}
