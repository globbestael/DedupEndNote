package edu.dedupendnote.services;

public class RecordCapExceededException extends DeduplicationException {
	public RecordCapExceededException(String message) {
		super(message);
	}
}
