package edu.dedupendnote.services;

public class DuplicateIdsException extends DeduplicationException {
	public DuplicateIdsException(String message) {
		super(message);
	}
}
