package edu.dedupendnote.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/*
 * Used in tests for evaluating alternatives.
 *
 * This may be easier than using a strategy pattern for Publication.addAuthors(String author)
 */
public class PublicationExperiment extends Publication {

	// most primitive treatment of author
	public void addAuthorsWithoutPreprocessing(String author) {
		this.authors.add(author);
	}

	Pattern p = Pattern.compile("\\b[A-Z]");

	public void addAuthorsLimitedToFirstLetters(String author) {
		// String firstLetters = author.replaceAll("[a-z, \\.]", "");
		// String firstLetters = author.replaceAll("[^A-Z]", "");
		// List<String> letters = Arrays.asList(firstLetters.split(""));
		List<String> letters = new ArrayList<>();
		Matcher m = p.matcher(author);

		while (m.find()) {
			letters.add(m.group());
		}

		String sortedLetters = letters.stream().sorted().collect(Collectors.joining());
		this.authors.add(sortedLetters);
		// this.authors.add(firstLetters);
	}

}
