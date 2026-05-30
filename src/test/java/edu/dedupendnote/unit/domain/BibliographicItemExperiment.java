package edu.dedupendnote.unit.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import edu.dedupendnote.domain.AuthorRecord;
import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.services.AuthorsNormalizationService;

/*
 * Used in tests for evaluating alternatives.
 *
 * This may be easier than using a strategy pattern for BibliographicItem.addAuthors(String author)
 * 
 * See https://github.com/globbestael/DedupEndNote/issues/51 for a big refactoring
 */
public class BibliographicItemExperiment extends BibliographicItem {

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
		AuthorRecord normalizedAuthor = AuthorsNormalizationService.normalizeInputAuthors(author);
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
