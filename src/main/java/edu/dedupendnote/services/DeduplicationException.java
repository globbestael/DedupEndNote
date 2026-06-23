package edu.dedupendnote.services;

public class DeduplicationException extends RuntimeException {
	private final String errorMessage;

	public DeduplicationException(String message) {
		super(message);
		this.errorMessage = message;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
