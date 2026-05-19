package edu.dedupendnote.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class AppVersionAdvice {

	/*
		See https://blog.jetbrains.com/idea/2025/11/one-could-simply-add-nullability-check-support-without-even-noticing-it/
		TODO: How to prevent an empty Maven property app.version or empty application.properties:app.version OR how can an
		error be thrown if one pr both are empty?
	*/
	@SuppressWarnings("NullAway.Init")
	@Value("${app.version}")
	private String appVersion;

	@ModelAttribute("appVersion")
	public String appVersion() {
		return appVersion;
	}
}
