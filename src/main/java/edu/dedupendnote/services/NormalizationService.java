package edu.dedupendnote.services;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.text.StringEscapeUtils;

import edu.dedupendnote.domain.IsbnIssnRecord;
import edu.dedupendnote.domain.NormPatterns;
import lombok.extern.slf4j.Slf4j;

/*
 * NormalizationService holds shared utilities used by the per-domain normalization services.
 * - normalizeToBasicLatin and normalizeHyphensAndWhitespace are called from BibliographicItemReader and the per-domain services
 * - normalizeInputDois, normalizeInputIssns, normalizeInputPublicationYear are called from BibliographicItemReader
 */
@Slf4j
public class NormalizationService {

	public static final int EARLIEST_PUBLICATION_YEAR = 1850;

	public static Set<String> normalizeInputDois(String doi) {
		Set<String> dois = new HashSet<>();
		// Scopus bibliographicItems sometimes add Cited references in this field
		if (doi.length() > 200) {
			return dois;
		}
		try {
			doi = URLDecoder.decode(doi, "UTF8");
			doi = StringEscapeUtils.unescapeHtml4(doi);
		} catch (UnsupportedEncodingException e) {
			log.error("DOI {} threw an UnsupportedEncodingException", doi);
			// e.printStackTrace();
		}
		Matcher matcher = NormPatterns.DOI_PATTERN.matcher(doi.toLowerCase());
		while (matcher.find()) {
			String group = matcher.group(1);
			dois.add(group.replaceAll("\\.$", ""));
		}
		return dois;
	}

	// @formatter:off
	/*
	 * ISSNs and ISBNs are treated in the same way: uppercased and hyphens removed
	 *
	 * Crude validation: only lengths 8, 10 and 13 are accepted. 
	 * Invalid results as "X2345678" ("X" on non last position) are accepted.
	 * 
	 * For ISBN-10 the check digit is removed, for ISBN-13 the new prefix and the check digit 
	 *
	 * ISBNs and ISSNs are made unique.  
	 * 
	 * Paths not chosen:
	 * - full validation 
	 * - (ISBN) conversion to ISBN-13 
	 * - (ISSN) use of ISSN-L and the linking table: see
	 * https://www.issn.org/understanding-the-issn/assignment-rules/the-issn-l-for-
	 * bibliographicItems-on-multiple-media/
	 *
	 * Validation of ISSN and ISBN and conversion to ISBN-13 are possible with the Apache
	 * Commons Validator:
	 * https://mvnrepository.com/artifact/commons-validator/commons-validator/1.7
	 * https://commons.apache.org/proper/commons-validator/apidocs/index.html?org/apache/
	 * commons/validator/routines/ISBNValidator.html
	 *
	 * ISSN-L and the linking table: in theory this should be useful. But a test with some
	 * large data files should prove its extra value.
	 */
	// @formatter:on
	public static IsbnIssnRecord normalizeInputIssns(String issn) {
		Set<String> normalizedIsbns = new HashSet<>();
		Set<String> normalizedIssns = new HashSet<>();
		IsbnIssnRecord result = new IsbnIssnRecord(normalizedIsbns, normalizedIssns);

		Matcher matcher = NormPatterns.ISSN_ISBN_PATTERN.matcher(issn.toUpperCase());
		while (matcher.find()) {
			String group = matcher.group(1).replace("-", "");
			String isbnToAdd = null;
			String issnToAdd = null;
			switch (group.length()) {
			case 8 -> issnToAdd = group; // real ISSN
			case 10 -> isbnToAdd = group.substring(0, 9); // ISBN-10
			case 13 -> isbnToAdd = group.substring(3, 12); // ISBN-13
			default -> {
			}
			}
			if (issnToAdd != null && !normalizedIssns.contains(issnToAdd)) {
				normalizedIssns.add(issnToAdd);
			}
			if (isbnToAdd != null && !normalizedIsbns.contains(isbnToAdd)) {
				normalizedIsbns.add(isbnToAdd);
			}
		}
		return result;
	}

	public static Integer normalizeInputPublicationYear(String input) {
		Integer year = 0;
		Matcher matcher = NormPatterns.PUBLICATION_YEAR_PATTERN.matcher(input);
		if (matcher.find()) {
			year = Integer.valueOf(matcher.group(2));
			if (year < EARLIEST_PUBLICATION_YEAR) {
				year = 0;
			}
		} else {
			return 0;
		}
		return year;
	}

	/**
	 * normalizeToBasicLatin: removes accents and diacritics when the base character belongs to the BasicLatin Unicode
	 * block (U+0000â€“U+007F) and removes all other characters.
	 */
	public static String normalizeToBasicLatin(String s) {
		if (NormPatterns.NON_BASIC_LATIN_PATTERN.matcher(s).find()) {
			s = Normalizer.normalize(s, Normalizer.Form.NFD);
			// you can't reuse the existing matcher because r might be changed
			s = NormPatterns.NON_BASIC_LATIN_PATTERN.matcher(s).replaceAll("");
		}
		return s;
	}

	public static String normalizeHyphensAndWhitespace(String s) {
		// standardize / replace HYPHENs (Unicode Dash Punctuation, not Hyphen!) to HYPHEN-MINUS
		// https://www.unicode.org/reports/tr44/#Dash
		// https://www.compart.com/en/unicode/category/Pd
		s = s.replaceAll("\\p{Pd}", "\u002D");
		// remove THIN SPACE. Some databases use THIN SPACE within "30 mg", others use no character
		s = s.replaceAll("\u2009", "");
		s = NormPatterns.UNUSUAL_WHITESPACE_PATTERN.matcher(s).replaceAll(" ");
		return s;
	}
}
