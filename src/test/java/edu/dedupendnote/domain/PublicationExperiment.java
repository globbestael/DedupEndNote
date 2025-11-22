package edu.dedupendnote.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.dedupendnote.services.NormalizationService;
import edu.dedupendnote.services.NormalizationService.AuthorRecord;

/*
 * Used in tests for evaluating alternatives.
 *
 * This may be easier than using a strategy pattern for Publication.addAuthors(String author)
 * 
 * See https://github.com/globbestael/DedupEndNote/issues/51 for a big refactoring
 */
public class PublicationExperiment extends Publication {

	Pattern firstLettersPattern = Pattern.compile("\\b[A-Z]");
	Pattern toFirstLettersPattern = Pattern.compile("^(.*) ([A-Z]+)$");

	// most primitive treatment of author
	public void addAuthorsWithoutPreprocessing(String author) {
		this.authors.add(author);
	}

	public void addAuthorsLimitedToFirstLetters(String author) {
		// String firstLetters = author.replaceAll("[a-z, \\.]", "");
		// String firstLetters = author.replaceAll("[^A-Z]", "");
		// List<String> letters = Arrays.asList(firstLetters.split(""));
		List<String> letters = new ArrayList<>();
		Matcher m = firstLettersPattern.matcher(author);

		while (m.find()) {
			letters.add(m.group());
		}

		String sortedLetters = letters.stream().sorted().collect(Collectors.joining());
		this.authors.add(sortedLetters);
		// this.authors.add(firstLetters);
	}

	public void addAuthorsLimitedToFirstLetters_2(String author) {
		AuthorRecord normalizedAuthor = NormalizationService.normalizeInputAuthors(author);
		String a = normalizedAuthor.author();
		if (a != null) {
			Matcher m = toFirstLettersPattern.matcher(a);

			while (m.find()) {
				String lastNames = m.group(1).replaceAll("[^A-Z]", "");
				String firstNames = m.group(2);
				if (lastNames.length() > 0 && firstNames.length() > 0) {
					String a1 = lastNames + " " + firstNames.substring(0, 1);
					String a2 = firstNames.substring(0, 1) + " " + lastNames.substring(0, 1);
					this.authors.add(a1);
					this.getAuthorsTransposed().add(a2);
					this.setAuthorsAreTransposed(true);
				}
			}
		}
	}

}
