package edu.dedupendnote.domain;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/*
 * Used in tests for evaluating alternatives.
 * 
 * This may be easier than using a strategy pattern for Record.addAuthors(String author) 
 */
public class RecordExperiment extends Record {
	
	// most primitive treatment of author
	public void addAuthorsWithoutPreprocessing(String author) {
		this.authors.add(author);
	}

	public void addAuthorsLimitedToFirstLetters(String author) {
		String firstLetters = author.replaceAll("[a-z, \\.]", "");
		List<String> letters = Arrays.asList(firstLetters.split(""));
		String sortedLetters = letters.stream().sorted().collect(Collectors.joining());
		this.authors.add(sortedLetters);
	}
}
