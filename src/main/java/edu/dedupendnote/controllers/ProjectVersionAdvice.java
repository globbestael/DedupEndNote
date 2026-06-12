package edu.dedupendnote.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ProjectVersionAdvice {

	/*
		See https://blog.jetbrains.com/idea/2025/11/one-could-simply-add-nullability-check-support-without-even-noticing-it/
		TODO: How to prevent an empty Maven property project.version or empty application.properties:spring.application.version OR how can an
		error be thrown if one or both are empty?
	*/
	@SuppressWarnings("NullAway.Init")
	@Value("${spring.application.version}")
	private String projectVersion;

	@ModelAttribute("projectVersion")
	public String projectVersion() {
		return projectVersion;
	}
}
