package edu.dedupendnote.domain;

public enum DeduplicationMode {
	REMOVE,
	MARK;

	public static DeduplicationMode from(boolean markMode) {
		return markMode ? MARK : REMOVE;
	}
}
