package edu.dedupendnote.services;

public class CancelledException extends DeduplicationException {
	public CancelledException(String message) {
		super(message);
	}
}
