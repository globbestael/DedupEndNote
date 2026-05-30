package edu.dedupendnote.services;

public class InvalidRisFileException extends RuntimeException {
	private final String errorMessage;

	public InvalidRisFileException(String message) {
		super(message);
		this.errorMessage = message;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
