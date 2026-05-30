package edu.dedupendnote.services;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.util.StringUtils;

import edu.dedupendnote.domain.AuthorRecord;
import edu.dedupendnote.domain.IsbnIssnRecord;
import edu.dedupendnote.domain.NormPatterns;
import lombok.extern.slf4j.Slf4j;

/*
 * NormalizationService centralizes the normalization of data (read from external file, used in tests).
	public static String normalizeHyphensAndWhitespace(String s) {
 - The normalizeInput... methods and normalizeHyphensAndWhitespace are called from IOService
 - the other normalize... methods are used internally or in tests
 */
@Slf4j
public class NormalizationService {

	public static final int EARLIEST_PUBLICATION_YEAR = 1850;

	/*
	 * In Java 8 replaceAll via PATTERN.matcher(s).replaceAll(replacement) is faster than
	 * s.replaceAll(replacement) See below for Java9Plus versions.
	 */

	// @formatter:off
	/*
	 * normalizeAuthors() and author comparisons
	 *
	 * Possible enhancements:
	 * ----------------------
	 * - Compare only the first ... authors
	 *   See AuthorVariantsExperimentsTest::compareOnlyFirst10AuthorsTest()
	 *
	 * Paths not chosen:
	 * -----------------
	 * - treat all authors longer than ... characters as groups
	 * 	 This would skip "Treatment of Hepatocellular Carcinoma with Tumour, T" which doesn't contain a group-word
	 * 	 With length => 40 some person authors are still found:
	 * 		- Srivastava, K. Das N. Goel PBhatnagar V. [these are multiple authors]
	 * 		- Silveira, Renata Cristina de Campos Pereira	[PsycINFO]
	 * 		- Fantini, Francisca Goreth Malheiro Moraes	[PsycINFO]
	 * 		- Cardoso, Tania Aparecida Marchiori de Oliveira	[PsycINFO]
	 *
	 * Strange cases, maybe solveable:
	 * -------------------------------
	 * - Different numbers of author
	 *   - Yamashita, Y.
	 *   - Yamashita, Y.; Takahashi, M.; Koga, Y.; Saito, R.; Nanakawa, S.; Hatanaka, Y.; Sato, N.; Nakashima, K.; Urata, J.; Yoshizumi, K.; et al.
	 *   Could be solved if the same number of authors were compared. See AuthorVariantsExperimentsTest::compareSameNumberOfAuthorsTest
	 *   But implementing this could be difficult
	 *
	 * Strange cases, not handled yet:
	 * -------------------------------
	 * - CYK names: last and first names are often mixed up
	 *   - "Chung, J. W." and "Jin Wook, Chung"
	 *   - same bibliographicItem:
	 *     - Chuan-Xing, L.; Xu, H.; Bao-Shan, H.; Yong, L.; Pei-Jian, S.; Xian-Yi, Y.; Xiao-Ning, L.; Li-Gong, L.
	 *     - Li, C. X.; He, X.; Hu, B. S.; Li, Y.; Shao, P. J.; Yu, X. Y.; Luo, X. N.; Lu, L. G.
	 *   - same bibliographicItem:
	 *     - Chen, Y.; Chen, J.; Luo, B.
	 *     - Yajin, C.; Jisheng, C.; Baoming, L.
	 *   - both orders in same bibliographicItem:
	 *     - Jia-Wu, Li; Qiang, Lu; Yan, Luo; Li, Jia-Wu; Lu, Qiang; Luo, Yan [CINAHL: same 3 authors with transposed names]
	 * 	   - But this also occurs in CINAHL without transposed authors:
	 *        - Chung, R. T.; Iafrate, A. J.; Amrein, P. C.; Sahani, D. V.; Misdraji, J.; Chung, Raymond T.; Iafrate, A. John; Amrein, Philip C.; Sahani, Dushyant V.; Misdraji, Joseph
	 * - Parts run together:
	 *   - vanSpronsen, F. J.; deLangen, Z. J.; vanElburg, R. M.; Kimpen, J. L. L.
	 *   - 25 cases in BIG_SET, o.a. "DeAngelo, D. J."
	 *   - BUT: McGovern / MacGovern / FitzGerald
	 * - First author left out:
	 *   - Bureau, C.; Laurent, J.; Robic, M. A.; Christol, C.; Guillaume, M.; Ruidavets, J. B.; Ferrieres, J.; PÃ©ron, J. M.; Vinel, J. P.
	 *   - Laurent, J.; Robic, M. A.; Christol, C.; Guillaume, M.; Ruidavets, J. B.; Ferrieres, J.; Peron, J. M.; Vinel, J. P.
	 * - De, Marco L [should this be: De Marco, L?]
	 */
	// @formatter:on

	public static AuthorRecord normalizeInputAuthors(String authorInput) {
		String authorResult = null;

		// skip "Anonymous", "et al" and group authors
		Matcher matcher = NormPatterns.ANONYMOUS_OR_GROUPNAME_PATTERN.matcher(authorInput);
		if (matcher.find()) {
			return new AuthorRecord(null, null, false);
		}

		authorInput = normalizeToBasicLatin(authorInput);

		String[] parts = authorInput.split("\\s*,\\s+"); // see testfile Non_Latin_input.txt for " , "
		if (parts.length < 2) {
			log.debug("Author {} cannot be split", authorInput);
			return new AuthorRecord(authorInput, null, false);
		}
		String lastName = parts[0];
		String firstNames = parts[1];

		/*
		 * Normalize lastName: 
		 * - normal capitalization Some databases (a.o. Web of Science) can have complete author names in capitals. EndNote X9 shows them
		 *   capitalized ("Marchioli, R") but the export file has "MARCHIOLI, R"! 
		 * - additions (2nd, Jr, Sr, III) as part of LastName are removed. These additions usually are the 3rd part of the EndNote names 
		 *   (and not used, see above)
		 */
		if (lastName.equals(lastName.toUpperCase())) {
			String[] lastNameParts2 = lastName.toLowerCase().split(" ");
			List<String> lastNameParts = new ArrayList<>(Arrays.asList(lastNameParts2));
			lastName = lastNameParts.stream().map(StringUtils::capitalize).collect(Collectors.joining(" "));
		}
		matcher = NormPatterns.LAST_NAME_ADDITIONS_PATTERN.matcher(lastName);
		if (matcher.find()) {
			lastName = (matcher.group(1).strip() + " " + matcher.group(3).strip()).strip();
			log.debug("new lastName: {}", lastName);
		}

		// @formatter:off
		/*
		 * Possible enhancement: Switch firstNames and lastName for esp. Chinese author names IFF there is a full forName (Wei, Li ==> Li, Wei).
		 * - as for transposed author names: we need a Boolean authorsAreSwitched and a List<String> authorsSwitched for the temporary results.
		 *   fillAuthors() would add these authors IFF the Boolean is set
		 * - should the transposition (see below) also be applied to these switched authors?
		 * - this should be done before the reduction of firstNames to initials
		 *
		 * Both WoS and CINAHL use the "other" form: 10.1007/s11605-013-2150-4
		 * 10.5754/hge12986: PubMed: Yalin, K; WoS: Kong, Y.L. (is this for: Ya Lin?); Scopus: Kong, Y
		 *
		 * From which databases are the first names imported? CINAHL examples found
		 */
		// @formatter:on

		// Reducing the first names to their initials, stripping everything but the initials, and leaving out the comma
		// makes JWS higher for similar names and lower for different names.
		String initials = NormPatterns.EXCEPT_CAPITALS_PATTERN.matcher(firstNames).replaceAll("");
		authorResult = lastName + " " + initials;

		// @formatter:off
		/*
		 *  Transposed author names:
		 *  If the last name contains a space:
		 *  - create a transposed author with all parts of the last name except the last one left out and added as the last initial
		 *  	- Cobos Mateos, J. M. 		==> Mateos JMC
		 *  	- van Boxtel, M. P. J. 		==> Boxtel MPJV
		 *  	- De Brouwer de Boer, A.	==> Boer ADBD
		 *  - set authorsAreTransposed to true
		 *  If the last name does not contain a space:
		 *  - create a transposed author unchanged
		 *  - do NOT set authorsAreTransposed
		 *
		 *  In fillAuthors() there is a check on authorsAreTransposed. Only if set, are the authorsTransposed (joined as 1 String) and added to List<String> allAuthors.
		 *
		 *  Paths not chosen:
		 *  - create all transpositions (e.g. with "De Brouwer de Boer, A."). See AuthorPermutationsExperimentsTest
		 */
		// @formatter:on
		if (lastName.contains(" ")) {
			lastName = lastName.substring(0, 1).toUpperCase() + lastName.substring(1);
			String[] lastNameParts2 = lastName.split("\\s+");
			List<String> lastNameParts = new ArrayList<>(Arrays.asList(lastNameParts2));
			String lastPart = lastNameParts.remove(lastNameParts.size() - 1);
			if (!lastNameParts.isEmpty()) {
				initials += lastNameParts.stream().map(p -> p.substring(0, 1)).collect(Collectors.joining());
				log.debug("Author {} is transposed as {} {}", authorInput, lastPart, initials);
				return new AuthorRecord(authorResult, lastPart + " " + initials, true);
			} else {
				return new AuthorRecord(authorResult, lastName + " " + initials, false);
			}
		} else {
			return new AuthorRecord(authorResult, lastName + " " + initials, false);
		}
	}

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
