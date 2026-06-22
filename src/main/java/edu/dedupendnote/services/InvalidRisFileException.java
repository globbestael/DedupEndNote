package edu.dedupendnote.services;

public class InvalidRisFileException extends DeduplicationException {
	public InvalidRisFileException(String message) {
		super(message);
	}
}
