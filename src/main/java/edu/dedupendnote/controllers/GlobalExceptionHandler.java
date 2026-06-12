package edu.dedupendnote.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
class GlobalExceptionHandler {

	// Return 404 silently; NoResourceFoundException is a normal browser probe (favicon, etc.)
	// and must not be caught by the generic handler below, which would log ERROR and return 500.
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<Void> handleNoResource(NoResourceFoundException e) {
		return ResponseEntity.notFound().build();
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse> handleException(Exception e) {
		log.error("Unhandled exception", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ApiResponse("An internal error occurred"));
	}
}
