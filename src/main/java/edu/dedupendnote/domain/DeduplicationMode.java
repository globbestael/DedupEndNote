package edu.dedupendnote.domain;

public enum DeduplicationMode {
	REMOVE, MARK;

	public String filenameSuffix() {
		return this == MARK ? "_mark" : "_deduplicated";
	}

	public String logCode() {
		return this == MARK ? "M" : "D";
	}
}
