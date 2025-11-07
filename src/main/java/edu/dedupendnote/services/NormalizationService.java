package edu.dedupendnote.services;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/*
 * NormalizationService centralizes the normalization of data (read from external file, used in tests).
 - The normalizeInput... methods are called from IOService
 - the other normalize... methods * There is 
 */
@Slf4j
public class NormalizationService {
	// see: http://blog.crossref.org/2015/08/doi-regular-expressions.html
	// see https://github.com/globbestael/DedupEndNote/issues/16 for shortDOIs
	private static final Pattern DOI_PATTERN = Pattern.compile("\\b(10.\\d{4,9}/[-._;()<>/:a-z0-9]+)\\b");

	/**
	 * ISSN/ISBN pattern: very crude pattern for ISSN, ISBN-10 and ISBN-13. Will accept invalid input: 1234-x567,
	 * "-1-2-30x4", ISBN-13 with a "X" check digit
	 */
	private static final Pattern ISSN_ISBN_PATTERN = Pattern.compile("\\b([-\\dxX]{8,17})\\b");

	/*
	 * In Java 8 replaceAll via PATTERN.matcher(s).replaceAll(replacement) is faster than
	 * s.replaceAll(replacement) See below for Java9Plus versions.
	 */
	/**
	 * Double quote character: will be removed
	 */
	private static final Pattern DOUBLE_QUOTES_PATTERN = Pattern.compile("\"");

	/**
	 * All characters between a non-initial "[" and "]", including the square brackets and the preceding character
	 */
	private static final Pattern NON_INITIAL_SQUARE_BRACKETS_PATTERN = Pattern.compile(".\\[[^\\\\]+\\]$");

	/**
	 * "UNSP ..." (and variants) should be cleaned from the C7 field (WoS). Import may have changed UNSP" Into "Unsp".
	 * "author..." (reply etc): delete rest of string
	 */
	// private static final Pattern PAGES_ADDITIONS_PATTERN = Pattern.compile("(^(UNSP|Article)\\s*|; author.+$)",
	// Pattern.CASE_INSENSITIVE);
	private static final Pattern PAGES_ADDITIONS_PATTERN = Pattern.compile("^(UNSP|Article)\\s*",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Month names as part of pages field
	 */
	private static final Pattern PAGES_MONTH_PATTERN = Pattern
			.compile("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b");

	// FIXME: Not used any more in normalizeInputPages
	// private static final Pattern FIRST_PAGES_GROUP = Pattern.compile("^([^-,;]+)([,;])(.*)$");

	/**
	 * All characters between "<" and ">", including the pointy brackets
	 *
	 * ASYSD removes the following html tags from titles and abstracts see
	 * https://github.com/camaradesuk/ASySD/pull/67/commits/fe9c4d1b08eebe2fa7c369bf4ff80077576bb9df 2026-06-13 The list
	 * has been expanded based on tests in ValidationTest:
	 *
	 * List<String> htmlList = List.of( "<b>", "</b>", "<bold>", "</bold>", "<br />
	 * ", // not in ASYSD "<del>", "</del>", "<em>", "</em>", "<i>", "</i>", "<inf>", "</inf>", "<ins>", "</ins>",
	 * "<mark>", "</mark>", "<small>", "</small>", "<sub>", "</sub>", "<sup>", "</sup>", "<sup/>" // not in ASYSD );
	 *
	 * The ValidationTest files showed 3 more examples: <35 vs. =/> <35 vs. > <ORIGINAL>
	 *
	 * e.g. in title Comparison of liver transplant outcomes for recipients with MELD <35 Vs. >35 The "<ORIGINAL>" cases
	 * in the SRA2_Respiratory file with publications < 2000. Original database of publications unknown.
	 *
	 * Based on these results, the crude pointyBracketsPattern (regex "<[^>]+>") hasn't been changed. The method
	 * normalizeJava8(...) has code (commented out) for comparing the crude and explicit version
	 *
	 * Last ">+" because "<<...>>" also occurs
	 */
	private static final Pattern POINTY_BRACKETS_PATTERN = Pattern.compile("<[^>]+>+");

	/**
	 * "(" and ")"
	 */
	private static final Pattern ROUND_BRACKETS_PATTERN = Pattern.compile("[\\(\\)]");

	/**
	 * "-"
	 */
	private static final Pattern HYPHEN_PATTERN = Pattern.compile("\\-");

	/**
	 * All characters except [a-z] (lowercase) and [0-9]
	 */
	private static final Pattern NON_ASCII_LOWERCASE_PATTERN = Pattern.compile("[^a-z0-9]");

	/**
	 * All characters except [a-z] (case insensitive) and [0-9] will be replaced by SPACE
	 */
	private static final Pattern NON_ASCII_PATTERN = Pattern.compile("[^a-z0-9]", Pattern.CASE_INSENSITIVE);

	/**
	 * Two or more white space characters: will be reduced to 1 SPACE
	 */
	private static final Pattern MULTIPLE_WHITE_SPACE_PATTERN = Pattern.compile("\\s{2,}");

	/**
	 * Initial "the|a|an" + SPACE: will be removed
	 */
	private static final Pattern STARTING_ARTICLE_PATTERN = Pattern.compile("^(the|a|an) ");

	// FIXME: check last characters in pattern (space or punctuation?)
	/**
	 * Esp. Scopus uses "(Japanese)" (or "(Japanese text)") at the end of the title.
	 *
	 * The pattern is used on the lowercased title. The languages are not complete: based on the 200 most frequent
	 * (sub)titles in the testfiles
	 */
	private static final Pattern LANGUAGE_PATTERN = Pattern
			.compile("(\\(?(chinese|dutch|french|german|italian|japanese|polish|russian|spanish)( text)?\\)?)$");

	/**
	 * All characters outside the BasicLatin Unicode block (\u0000 – \u007F). After Normalization with canonical
	 * decomposition (Normalizer.Form.NFD) all combining accents and diacritics, supplemental characters (e.g. "£") and
	 * all characters in other scripts will be removed
	 */
	private static final Pattern NON_BASIC_LATIN_PATTERN = Pattern.compile("[^\\p{InBasic_Latin}]");

	/**
	 * "&": will be replaced by SPACE because an ampersand is problem in some of the following patterns
	 */
	private static final Pattern AMPERSAND_PATTERN = Pattern.compile("&");

	/**
	 * "Jpn": will be replaced by "Japanese"
	 */
	private static final Pattern JPN_PATTERN = Pattern.compile("Jpn");

	/**
	 * "Dtsch": will be replaced by "Deutsch"
	 */
	private static final Pattern DTSCH_PATTERN = Pattern.compile("Dtsch");

	/**
	 * "Natl": will be replaced by "National"
	 */
	private static final Pattern NATL_PATTERN = Pattern.compile("Natl");

	/**
	 * "[(...)G]eneeskd": will be replaced by "[(...)G]eneeskunde"
	 */
	private static final Pattern GENEESKD_PATTERN = Pattern.compile("eneeskd");

	/**
	 * "heilkd": will be replaced by "heilkunde"
	 */
	private static final Pattern HEILKD_PATTERN = Pattern.compile("heilkd");

	/**
	 * "Kongressbd" will be replaced by "Kongressband"
	 */
	private static final Pattern KONGRESSBD_PATTERN = Pattern.compile("Kongressbd");

	/**
	 * "Monbl" (case insensitive): will be replaced by "Monatsbl"
	 */
	private static final Pattern MONATSBLATT_PATTERN = Pattern.compile("Monbl\\b", Pattern.CASE_INSENSITIVE);

	/**
	 * Initial "Zbl(.?) " (case insensitive): will be replaced by "Zentralblatt"
	 */
	private static final Pattern ZENTRALBLATT_PATTERN = Pattern.compile("^Zbl[\\. ]", Pattern.CASE_INSENSITIVE);

	/**
	 * "Jbr-btr" (case insensitive): will be replaced by "JBR BTR". Cheater!
	 */
	private static final Pattern JBR_PATTERN = Pattern.compile("Jbr-btr", Pattern.CASE_INSENSITIVE);

	/**
	 * "^(Rofo|Fortschritte .* Gebiet.* R.ntgenstrahlen)" (case insensitive): will be replaced by "Rofo". Cheater!
	 *
	 * Must be used BEFORE the latinOPattern
	 *
	 */
	private static final Pattern ROFO_PATTERN = Pattern.compile("^(Rofo|Fortschritte .* Gebiet.* R.ntgenstrahlen)",
			Pattern.CASE_INSENSITIVE);

	/**
	 * "o-[NON-WHITE-SPACE]": the hyphen will be removed. E.g. "gastro-enterology" --> "gastroenterology". Must be used
	 * BEFORE the minusOrDotPattern
	 */
	private static final Pattern LATIN_O_PATTERN = Pattern.compile("o\\-(\\S)");

	/**
	 * "-" or ".": will be replaced by a SPACE
	 */
	private static final Pattern HYPHEN_OR_DOT_PATTERN = Pattern.compile("[\\.-]");

	/**
	 * Initial "(The |Le |La |Les |L'|Der |Die |Das |Il |Het )": will be removed
	 */
	private static final Pattern JOURNAL_STARTING_ARTICLE_PATTERN = Pattern
			.compile("^(The |Le |La |Les |L'|Der |Die |Das |Il |Het )");

	/**
	 * "'s" at the end of a word: the apostrophe will be removed. E.g. Langenbeck's / Bailliere's / Crohn's. Must be
	 * called BEFORE the nonGenitiveApostrophePattern
	 */
	private static final Pattern GENITIVE_APOSTROPHE_PATTERN = Pattern.compile("'s\\b");

	/**
	 * All other apostrophes (compare genitiveApostrophePattern): will be replaced by SPACE. E.g. "Annales d'Urologie",
	 * "Journal of Xi'an Jiaotong University (Medical Sciences)". Must be called AFTER genitiveApostrophePattern
	 */
	private static final Pattern NON_GENITIVE_APOSTROPHE_PATTERN = Pattern.compile("'");

	/**
	 * All characters between "(" and ")" at the end of a string, including the round brackets: will be removed
	 */
	private static final Pattern JOURNAL_ENDING_ROUND_BRACKETS_PATTERN = Pattern.compile("\\([^\\)]+\\)$");

	/**
	 * "(" and ")": will be replaced by SPACE. See also journalEndingRoundBracketsPattern. Some journals only have "("
	 * without a ")", which causes regex problems
	 */
	private static final Pattern JOURNAL_OTHER_ROUND_BRACKETS_PATTERN = Pattern.compile("[\\)\\(]");

	/**
	 * ":" or "/" and SPACE and all following characters: will be removed. E.g. "BJOG: An International Journal of
	 * Obstetrics and Gynaecology" --> "BJOG"
	 * 
	 * The space is wanted to prevent splitting in types as "Hematology/oncology".
	 */
	private static final Pattern JOURNAL_ADDITION_PATTERN = Pattern.compile("[:/]\\s.*$");

	/**
	 * (Suppl|Supplement|Supplementum) and following characters: will be me removed
	 */
	public static final Pattern JOURNAL_SUPPLEMENT_PATTERN = Pattern
			.compile("(\\b(Suppl|Supplement|Supplementum)\\b.*)$", Pattern.CASE_INSENSITIVE);

	private static final List<String> NO_TITLES = List.of("not available", "[not available]", "untitled");

	private static final Pattern RETRACTION_START_PATTERN = Pattern
			.compile("((retracted|removed|withdrawn)( article)?[.:] )(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern RETRACTION_END_PATTERN = Pattern.compile("(.+)\\(Retracted [Aa]rticle.*\\)");

	private static final Pattern TITLE_AND_SUBTITLE_PATTERN = Pattern.compile("^(.{50,}?)[:.?] (.{50,})$");

	/*
	* In Java 8 replaceAll via PATTERN.matcher(s).replaceAll(replacement) is faster than
	* s.replaceAll(replacement) See below for Java9Plus versions.
	*/
	private static final Pattern PUBLICATION_YEAR_PATTERN = Pattern.compile("(^|\\D)(\\d{4})(\\D|$)");

	/**
	 * A number or ". Conference" and all following characters: The number and all following characters will be removed.
	 * E.g.: - "Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date of Publication: 1989." -->
	 * "Clinical neuropharmacology" - "European Respiratory Journal. Conference: European Respiratory Society Annual
	 * Congress" (Cochrane publications)
	 */
	public static final Pattern JOURNAL_EXTRA_PATTERN = Pattern.compile("^(.+?)((\\d.*|\\. Conference.*))$");

	/**
	 * Some subtitles of journals ("Technical report", "Electronic resource", ...): will be removed
	 */
	static final List<String> EXCLUDED_JOURNALS_PARTS = Arrays.asList("electronic resource", "et al.",
			"technical report");

	// FIXME: add "No authorship, indicated"?
	public static final Pattern ANONYMOUS_OR_GROUPNAME_PATTERN = Pattern
			.compile("\\b(anonymous|consortium|et al|grp|group|nct|study)\\b", Pattern.CASE_INSENSITIVE);

	// FIXME: Not used any more in normalizeInputPages
	// private static final Pattern NUMBERS_WITHIN_PATTERN = Pattern.compile(".*\\d+.*");

	// @formatter:off
	/*
	 * normalizeAuthors() and author comparisons
	 *
	 * Possible enhancements:
	 * ----------------------
	 * - Compare only the first ... authors
	 *   See See AuthorVariantsExperimentsTest::compareOnlyFirst10AuthorsTest()
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
	 *   - same publication:
	 *     - Chuan-Xing, L.; Xu, H.; Bao-Shan, H.; Yong, L.; Pei-Jian, S.; Xian-Yi, Y.; Xiao-Ning, L.; Li-Gong, L.
	 *     - Li, C. X.; He, X.; Hu, B. S.; Li, Y.; Shao, P. J.; Yu, X. Y.; Luo, X. N.; Lu, L. G.
	 *   - same publication:
	 *     - Chen, Y.; Chen, J.; Luo, B.
	 *     - Yajin, C.; Jisheng, C.; Baoming, L.
	 *   - both orders in same publication:
	 *     - Jia-Wu, Li; Qiang, Lu; Yan, Luo; Li, Jia-Wu; Lu, Qiang; Luo, Yan [CINAHL: same 3 authors with transposed names]
	 * 	   - But this also occurs in CINAHL without transposed authors:
	 *        - Chung, R. T.; Iafrate, A. J.; Amrein, P. C.; Sahani, D. V.; Misdraji, J.; Chung, Raymond T.; Iafrate, A. John; Amrein, Philip C.; Sahani, Dushyant V.; Misdraji, Joseph
	 * - Parts run together:
	 *   - vanSpronsen, F. J.; deLangen, Z. J.; vanElburg, R. M.; Kimpen, J. L. L.
	 *   - 25 cases in BIG_SET, o.a. "DeAngelo, D. J."
	 *   - BUT: McGovern / MacGovern / FitzGerald
	 * - First author left out:
	 *   - Bureau, C.; Laurent, J.; Robic, M. A.; Christol, C.; Guillaume, M.; Ruidavets, J. B.; Ferrieres, J.; Péron, J. M.; Vinel, J. P.
	 *   - Laurent, J.; Robic, M. A.; Christol, C.; Guillaume, M.; Ruidavets, J. B.; Ferrieres, J.; Peron, J. M.; Vinel, J. P.
	 * - De, Marco L [should this be: De Marco, L?]
	 */
	// @formatter:on

	private static final Pattern LAST_NAME_ADDITIONS_PATTERN = Pattern
			.compile("^(.+)\\b(jr|sr|1st|2nd|3rd|ii|iii)\\b(.*)$", Pattern.CASE_INSENSITIVE);

	private static final Pattern EXCEPT_CAPITALS_PATTERN = Pattern.compile("[^A-Z]");

	public record AuthorRecord(String author, String authorTransposed, boolean isAuthorTransposed) {
	}

	public record IsbnIssnRecord(Set<String> isbns, Set<String> issns) {
	};

	public record PageRecord(String originalPages, String pageStart, String pagesOutput, boolean isSeveralPages) {
	};

	public record TitleRecord(String originalTitle, List<String> titles) {
	};

	public static AuthorRecord normalizeInputAuthors(String authorInput) {
		String authorResult = null;

		// skip "Anonymous", "et al" and group authors
		Matcher matcher = ANONYMOUS_OR_GROUPNAME_PATTERN.matcher(authorInput);
		if (matcher.find()) {
			return new AuthorRecord(null, null, false);
		}

		authorInput = NormalizationService.normalizeToBasicLatin(authorInput);

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
		matcher = LAST_NAME_ADDITIONS_PATTERN.matcher(lastName);
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
		String initials = EXCEPT_CAPITALS_PATTERN.matcher(firstNames).replaceAll("");
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
		// Scopus publications sometimes add Cited references in this field
		if (doi.length() > 200) {
			return dois;
		}
		// TODO: should illegal strings (java.lang.IllegalArgumentException) be treated differently?
		try {
			doi = URLDecoder.decode(doi, "UTF8");
			doi = StringEscapeUtils.unescapeHtml4(doi);
		} catch (UnsupportedEncodingException e) {
			log.info(e.getMessage());
			e.printStackTrace();
		}
		Matcher matcher = DOI_PATTERN.matcher(doi.toLowerCase());
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
	 * publications-on-multiple-media/
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

		Matcher matcher = ISSN_ISBN_PATTERN.matcher(issn.toUpperCase());
		while (matcher.find()) {
			String group = matcher.group(1).replace("-", "");
			String isbnToAdd = null;
			String issnToAdd = null;
			switch (group.length()) {
			case 8: // real ISSN
				issnToAdd = group;
				break;
			case 10: // ISBN-10
				isbnToAdd = group.substring(0, 9);
				break;
			case 13: // ISBN-13
				isbnToAdd = group.substring(3, 12);
				break;
			default:
				break;
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

	public static Set<String> normalizeInputJournals(String journal) {
		// @formatter:off
		/*
		 * General:
		 * - mark Cochrane publication
		 * - remove unwanted parts
		 * - split combined journal names into in separate journal names
		 * - create other variant journal names
		 * - for all journal names
		 * 		- capitalize
		 * 		- normalize
		 */
		// @formatter:on
		if (journal.startsWith("http")) {
			return Set.of(journal);
		}
		// Strip last part of "Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date
		// of Publication: 1989."
		Matcher matcher = NormalizationService.JOURNAL_EXTRA_PATTERN.matcher(journal);
		while (matcher.find()) {
			journal = matcher.group(1);
		}

		/*
		 * replace "\S/\S" with space: "Hematology/Oncology" --> "Hematology Oncology"
		 * BUT:
		 * "Chung-Kuo Hsiu Fu Chung Chien Wai Ko Tsa Chih/Chinese Journal of Reparative & Reconstructive Surgery"
		 * will NOT be split into 2 journals! Same for: Arzneimittel-Forschung/Drug Research
		 */
		// matcher = JOURNAL_SLASH_PATTERN.matcher(journal);
		// while (matcher.find()) {
		// journal = matcher.group(1) + " " + matcher.group(2);
		// }

		/*
		 * Split the following cases in separate journals: 
		 * - ... [...]: Zhonghua wai ke za zhi [Chinese journal of surgery] 
		 * - ... / ...: The Canadian Journal of Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques 
		 * - ... = ...: Zhen ci yan jiu = Acupuncture research
		 */
		// String[] parts = journal.split("[\\[\\]=|/]");
		String[] parts = journal.split("[\\[\\]]|([=|/] )");
		List<String> list = new ArrayList<>(Arrays.asList(parts));

		/*
		 * Journals with a ":" will get 2 variants. e.g
		 * "BJOG: An International Journal of Obstetrics and Gynaecology" or
		 * "Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine"
		 * - one with the colon and all following characters removed ("BJOG" and "Clinical Medicine Insights").
		 *   The removal of these characters does not happen here, but later within normalizeJournalJava8() (journalAdditionPattern)
		 * - one with the ":" replaced with a SPACE
		 *   ("BJOG: An International Journal of Obstetrics and Gynaecology" and
		 *    "Clinical Medicine Insights Circulatory, Respiratory and Pulmonary Medicine")
		 */
		Set<String> additional = new HashSet<>();
		for (String j : list) {
			if (j.contains(":")) {
				additional.add(j.replace(":", " "));
			}
			if (j.contains("-")) {
				additional.add(j.replace("-", " "));
				additional.add(j.replace("-", ""));
			}
			if (j.contains("ae")) {
				additional.add(j.replace("ae", "e"));
			}
			// if (JOURNAL_SECTION_MARKERS.matcher(journal).matches()) {
			// additional.add(JOURNAL_SECTION_MARKERS.matcher(journal).replaceAll("Matcher"));
			// }
			matcher = NormalizationService.JOURNAL_SUPPLEMENT_PATTERN.matcher(journal);
			if (matcher.find()) {
				log.debug("SupplementMatcher fired for: {}", journal);
				additional.add(matcher.replaceAll(""));
			}
		}

		if (!additional.isEmpty()) {
			list.addAll(additional);
		}
		/*
		 * The EXCLUDED_JOURNALS_PARTS are not a pattern. They are one of the journal(parts) and will be skipped
		 */
		Set<String> journals = new HashSet<>();
		for (String j : list) {
			j = j.strip();
			// FIXME: what happens when EXCLUDED_JOURNALS_PARTS.contains(j.toLowerCase())??
			if (!j.isEmpty() && !EXCLUDED_JOURNALS_PARTS.contains(j.toLowerCase())) {
				if (j.equals(j.toUpperCase()) && (j.contains(" ") || j.length() > 6)) {
					List<String> words = Arrays.asList(j.toLowerCase().split(" "));
					j = words.stream().map(StringUtils::capitalize).collect(Collectors.joining(" "));
				}
				String normalized = NormalizationService.normalizeJournal(j);
				if (!normalized.isEmpty()) {
					journals.add(normalized);
				}
			}
		}
		log.debug("Result for {}: {}", journal, journals);
		return journals;
	}

	/*
	 * normalizeInputPages: parses the different input strings with page numbers / article numbers to the fields
	 * pageStart, pageEnd and pageStartForComparison.
	 *
	 * C7 (Article Number) should sometimes overrule / overwrite SP (starting and ending page) because when C7 is
	 * present, SP often contains the number of pages, and in a few cases relative pages (1-10 for a 10 pages article).
	 * 
	 * SE (Starting Ending Page) should sometimes overrule / overwrite SP (SE: 1746-1746, SP: 19)
	 *
	 * C7 and SE occur before the SP field in an EndNote RIS file.
	 *
	 * Solution: 
	 * - treat C7 as pages 
	 * - if C7 has already been called AND SE is a range of pages (except "1-..."), then SE can overwrite the C7 data. 
	 * - if C7 or SE has already been called AND SP is a range of pages (except "1-...") (e.g. C7: Pmid 29451177, and SP: 3-4) 
	 *   then SP can overwrite the C7 data. 
	 * 
	 * The test if fieldName ... has already been called / pagesOutput is not null, is NOT part of this method, but is handled in
	 * IOService::readPublications (and addNormalizedPages).
	 *
	 * See also issues https://github.com/globbestael/DedupEndnote/issues/2 and https://github.com/globbestael/DedupEndnote/issues/3
	 */
	public static PageRecord normalizeInputPages(String pages, String fieldName) {
		pages = PAGES_ADDITIONS_PATTERN.matcher(pages).replaceAll("").strip();
		// replace "ii-218-ii-228" by "ii218-ii228", and "S-12" by "S12"
		pages = pages.replaceAll("(?<!\\d+)([a-zA-Z]+)-(\\d+)", "$1$2");

		String originalPages = pages;
		// if Pages contains a month name string (e.g. "01 June"), omit the whole pages
		Matcher matcher = PAGES_MONTH_PATTERN.matcher(pages);
		while (matcher.find()) {
			pages = null;
		}
		/*
		 * - Ovid Medline in RIS format puts author address in M2 field, which is exported as SP
		 * - WoS has in Article Number field (AR) cases as 'Pmid 29451177' and 'Pii s0016-5107(03)01975-8'. These article numbers
		 *   with a space can be skipped
		 */
		if (pages == null || pages.isEmpty() || pages.length() > 30
				|| (fieldName.equals("C7") && pages.contains(" "))) {
			return new PageRecord(originalPages, null, null, false);
		}

		boolean severalPages = false;
		String pagesOutput = null;
		String pageStart = null;
		String pageEnd = null;

		// Cochrane uses hyphen characters instead of minus
		pages = pages.replaceAll("[\\u2010\\u00ad]", "-");
		// Do not lowercase the pages yet
		// pages = pages.toLowerCase();

		if ((fieldName.equals("C7")) || ((pages.startsWith("e") || pages.startsWith("E")) && !pages.contains("-"))) {
			severalPages = true;
			pagesOutput = originalPages;
		}

		if (pages.endsWith("+")) {
			pages = pages.substring(0, pages.length() - 1);
		}
		List<String> pagesParts = Arrays.asList(pages.split("[+,;]\\s*"));
		// @formatter:off
		// Split into (1) group with only Roman numbers, and (2) others (could be Arabic numbers, combined Roman+Arabic ("ii208-212"),
		// combined Arabic+text ("S12-23", "CD123456". "67A-69A", text, ...)
		Map<Boolean, List<String>> resultMap = pagesParts.stream()
			.collect(Collectors.partitioningBy(p -> p.matches("[ivxlcmIVXLCM\\-]+")));
		// @formatter:on

		if (resultMap.get(false).isEmpty()) { // there are no Arabic numbers, possibly Roman numbers
			if (!resultMap.get(true).isEmpty()) {
				String[] parts = resultMap.get(true).removeFirst().split("-");
				try {
					pageStart = String.valueOf(UtilitiesService.romanToArabic(parts[0]));
					if (parts.length > 1) {
						pageEnd = String.valueOf(UtilitiesService.romanToArabic(parts[1]));
					}
				} catch (java.lang.IllegalArgumentException e) {
					pageStart = null;
					pageEnd = null;
					pagesOutput = originalPages;
				}
			}
		} else if (!resultMap.get(false).isEmpty()) { // there are Arabic numbers
			// Clean "1165A" to "1165", and "ii108" to "108"
			// String first = resultMap.get(false).getFirst().replaceAll("[^\\d\\-]", "");
			String first = resultMap.get(false).removeFirst().replaceAll("(?<!\\d+)([a-zA-Z]+)-(\\d+)", "$1$2");
			String[] parts = first.split("-");
			pageStart = parts[0];
			if (parts.length > 1) {
				pageEnd = parts[1];
				pageStart = pageStart.replaceAll("^(0+|N\\.PAG)", "");
				pageEnd = pageEnd.replaceAll("^(0+|N\\.PAG)", "");
			}
			if ("".equals(pageStart)) {
				pageStart = pageEnd;
				if ("".equals(pageStart)) {
					pageStart = null;
				}
				pageEnd = null;
				pagesOutput = composePagesOutput(pageStart, pageEnd, resultMap);
				// pagesOutput = "";
			} else if (pageStart.matches("[Vv]\\d+:\\d+")) {
				pageStart = pageStart.replaceAll("[Vv]\\d+:(\\d)", "$1");
				pageEnd = pageEnd.replaceAll("[Vv]\\d+:(\\d)", "$1");
				pagesOutput = originalPages;
			} else if ((pageEnd != null && pageStart.length() >= pageEnd.length())
					|| (resultMap.get(false).size() + resultMap.get(true).size() > 0)) {
				/*
				 * The test on pagesOutput == null because for C7 field pagesOutput has already been set.
				 */
				if (pagesOutput == null) {
					// if the whole pages string is the same pageStart - pageEnd, record the long form
					pagesOutput = composePagesOutput(pageStart, pageEnd, resultMap);
				}
				if (pageEnd != null && pageStart.length() >= pageEnd.length()) {
					pageEnd = pageStart.substring(0, pageStart.length() - pageEnd.length()) + pageEnd;
				}
			}
		} else {
			return new PageRecord(originalPages, null, null, false);
		}
		pageStart = cleanUpPage(pageStart);
		pageEnd = cleanUpPage(pageEnd);
		Integer pageStartInt = null;
		Integer pageEndInt = null;
		if (pageStart != null) {
			try {
				pageStartInt = Integer.valueOf(pageStart);
				pageStart = pageStartInt.toString();
			} catch (NumberFormatException e) {
				// log.error("- pageStart {} is NOT an integer", pageStart);
			}
		}
		if (pageEnd != null) {
			try {
				pageEndInt = Integer.valueOf(pageEnd);
				pageEnd = pageEndInt.toString();
				if (pageStart == null) {
					pageStart = pageEnd;
					pageEnd = null;
				}
			} catch (NumberFormatException e) {
				// log.error("- pageEnd {} is NOT an integer", pageEnd);
			}
		}
		if (pagesOutput == null && (pageStart == null && pageEnd == null)) {
			pagesOutput = "";
		}
		if (pageStartInt != null && pageEndInt != null) {
			severalPages = pageEndInt - pageStartInt > 1;
			if ((pageStartInt == 1 && pageEndInt >= 100)) {
				pageStart = pageEnd;
				pageEnd = null;
				pageStartInt = pageEndInt;
				pageEndInt = null;
			}
		}
		if (pageStartInt != null && pageStartInt == 0) {
			if (pageEndInt == null) {
				pageStart = null;
				pageStartInt = null;
			} else {
				pageStart = pageEnd;
				pageEnd = null;
				pageStartInt = pageEndInt;
				pageEndInt = null;
			}
		}

		if (severalPages == false) {
			if (resultMap.get(true).size() + resultMap.get(false).size() > 0) {
				severalPages = true;
			}
		}
		if (pagesOutput == null) {
			pagesOutput = originalPages;
		}
		return new PageRecord(originalPages, pageStart, pagesOutput, severalPages);
	}

	private static String composePagesOutput(String pageStart, String pageEnd, Map<Boolean, List<String>> resultMap) {
		List<String> pageRanges = new ArrayList<>();
		pageRanges.addAll(resultMap.get(true));
		if (pageEnd == null) {
			if (pageStart == null) {
				pageRanges.add("");
			} else {
				pageRanges.add(pageStart);
			}
		} else if (pageStart.length() >= pageEnd.length()) {
			pageRanges.add(pageStart + "-" + pageStart.substring(0, pageStart.length() - pageEnd.length()) + pageEnd);
		} else {
			pageRanges.add(pageStart + "-" + pageEnd);
		}
		pageRanges.addAll(resultMap.get(false));
		return pageRanges.stream().collect(Collectors.joining("; "));
	}

	private static String cleanUpPage(String page) {
		if (page != null) {
			page = page.replaceAll("[^\\d]", "");
			page = page.replaceAll("^(0+)", "");
		}
		if ("".equals(page)) {
			page = null;
		}
		return page;
	}

	public static Integer normalizeInputPublicationYear(String input) {
		Integer year = 0;
		Matcher matcher = PUBLICATION_YEAR_PATTERN.matcher(input);
		if (matcher.find()) {
			year = Integer.valueOf(matcher.group(2));
			if (year < 1800) {
				year = 0;
			}
		} else {
			return 0;
		}
		return year;
	}

	public static TitleRecord normalizeInputTitles(String title) {
		if (NO_TITLES.contains(title.toLowerCase())) {
			return new TitleRecord(null, new ArrayList<>());
		}
		String cachedTitle = title;
		String originalTitle = null;
		Matcher endMatcher = RETRACTION_END_PATTERN.matcher(title);
		if (endMatcher.matches()) {
			originalTitle = cachedTitle;
			title = endMatcher.group(1);
		}
		Matcher startMatcher = RETRACTION_START_PATTERN.matcher(title);
		if (startMatcher.matches()) {
			originalTitle = cachedTitle;
			title = startMatcher.group(4);
		}
		List<String> normalizedTitles = addTitleWithNormalization(title);

		boolean splittable = true;
		String secondPart = title;

		while (splittable) {
			Matcher matcher = TITLE_AND_SUBTITLE_PATTERN.matcher(secondPart);
			if (matcher.find()) {
				// titles.add(matcher.group(1)); // add only the first part (min 50 characters)
				String firstPart = matcher.group(1); // add only the first part (min 50 characters)
				secondPart = matcher.group(2);
				if (firstPart.toLowerCase().endsWith("vs")) {
					normalizedTitles.addAll(addTitleWithNormalization(firstPart + " " + secondPart));
					// we could set splittable to false, but then 2nd part wont be split
				} else {
					normalizedTitles.addAll(addTitleWithNormalization(firstPart));
					normalizedTitles.addAll(addTitleWithNormalization(secondPart));
				}
			} else {
				splittable = false;
			}
		}

		// Matcher matcher = titleAndSubtitlePattern.matcher(title);
		// while (matcher.find()) {
		// // titles.add(matcher.group(1)); // add only the first part (min 50 characters)
		// String firstPart = matcher.group(1); // add only the first part (min 50 characters)
		// addTitleWithNormalization(firstPart);
		// // do not add the subtitle: titles.add(matcher.group(2));
		// }
		return new TitleRecord(originalTitle, normalizedTitles);
	}

	private static List<String> addTitleWithNormalization(String title) {
		String normalized = NormalizationService.normalizeTitle(title);
		String[] parts = normalized.split("=");
		List<String> list = new ArrayList<>(Arrays.asList(parts));
		List<String> titles = new ArrayList<>();

		for (String t : list) {
			if (!titles.contains(t.strip())) {
				titles.add(normalized);
			}
		}
		return titles;
	}

	public static String normalizeJournal(String s) {
		String r = s;
		r = normalizeToBasicLatin(r);
		r = AMPERSAND_PATTERN.matcher(r).replaceAll(" "); // we don't want "&" in the patterns
		// irregular abbreviations
		r = JPN_PATTERN.matcher(r).replaceAll("Japanese");
		r = DTSCH_PATTERN.matcher(r).replaceAll("Deutsch");
		r = NATL_PATTERN.matcher(r).replaceAll("National");
		r = GENEESKD_PATTERN.matcher(r).replaceAll("eneeskunde");
		r = HEILKD_PATTERN.matcher(r).replaceAll("heilkunde");
		r = KONGRESSBD_PATTERN.matcher(r).replaceAll("Kongressband");
		r = MONATSBLATT_PATTERN.matcher(r).replaceAll("Monatsbl");
		r = ZENTRALBLATT_PATTERN.matcher(r).replaceAll("Zentralbl");
		// Cheating
		r = JBR_PATTERN.matcher(r).replaceAll("JBR BTR");
		r = ROFO_PATTERN.matcher(r).replaceAll("Rofo");
		// Java 8-version
		if (LATIN_O_PATTERN.matcher(r).find()) { // "Gastro-Enterology" -> "Gastroenterology"
			StringBuilder sb = new StringBuilder();
			int last = 0;
			Matcher latinOMatcher = LATIN_O_PATTERN.matcher(r);
			while (latinOMatcher.find()) {
				sb.append(r.substring(last, latinOMatcher.start()));
				sb.append("o").append(latinOMatcher.group(1).toLowerCase());
				last = latinOMatcher.end();
			}
			sb.append(r.substring(last));
			r = sb.toString();
		}
		// Java 9+-version: see
		// https://stackoverflow.com/questions/2770967/use-java-and-regex-to-convert-casing-in-a-string
		// r = LATIN_O_PATTERN.matcher(r).replaceAll(m -> "o" + m.group(1).toLowerCase());
		// // "Gastro-Enterology" -> "Gastroenterology"
		r = HYPHEN_OR_DOT_PATTERN.matcher(r).replaceAll(" ");
		r = JOURNAL_STARTING_ARTICLE_PATTERN.matcher(r).replaceAll(""); // article at start
		r = GENITIVE_APOSTROPHE_PATTERN.matcher(r).replaceAll("s"); // Langenbeck's / Bailliere's / Crohn's
		r = NON_GENITIVE_APOSTROPHE_PATTERN.matcher(r).replaceAll(" "); // Annales d'Urologie / Journal of Xi'an
																		// Jiaotong
																		// University (Medical Sciences)
		r = JOURNAL_ENDING_ROUND_BRACKETS_PATTERN.matcher(r).replaceAll(""); // "Ann Med Interne (Paris)" --> "Ann Med
																				// Interne",
																				// or "J Med Ultrason (2001)"
		r = JOURNAL_OTHER_ROUND_BRACKETS_PATTERN.matcher(r).replaceAll(" "); // Some journals only have "(" without a
																				// ")",
																				// which causes regex problems
		if (r.toLowerCase().startsWith("http")) {
			// Cochrane library CENTRAL has journal name of type: https://clinicaltrials.gov/show/nct00969397
			r = r.toLowerCase();
		} else {
			r = JOURNAL_ADDITION_PATTERN.matcher(r).replaceAll("");
			// "BJOG: An Journal of Obstetrics and Gynaecology" --> "BJOG"
			r = NON_ASCII_PATTERN.matcher(r).replaceAll(" ");
		}
		r = MULTIPLE_WHITE_SPACE_PATTERN.matcher(r).replaceAll(" ");
		return r.strip(); // DO NOT lowercase (http titles are the exception)
	}

	// static public String normalizeJournalJava9Plus(String s) {
	// return s.replaceAll("&", " ") // we don't want "&" in the patterns
	// .replaceAll("Jpn", "Japanese") // irregular abbreviations
	// .replaceAll("Dtsch", "Deutsch").replaceAll("Natl", "National").replaceAll("Geneeskd", "Geneeskunde")
	// .replaceAll("-", "").replaceAll("^(The|Le|La|Der|Die|Das|Il) ", "") // article as start
	// .replaceAll("\\(([^\\)]*)\\)$", "") // "Ann Med Interne (Paris)" --> "Ann Med Interne", or "J Med Ultrason
	// (2001)"
	// .replaceAll(".\\[[^\\\\]+\\]", "") // "Zhonghua wai ke za zhi [Chinese journal of surgery]" --> "Zhonghua wai ke
	// za zhi"
	// .replaceAll("(\\]|\\[)", "") // "[Technical report] SAM-TR" --> "Technical report SAM-TR"
	// .replaceAll("(:|/) .*$", "") // "BJOG: An International Journal of Obstetrics and Gynaecology" --> "BJOG"
	// .replaceAll("\\s{2,}", " ").trim();
	// }

	public static String normalizeTitle(String s) {
		// FIXME: Why starting with parameter s and later copying s to r?
		s = normalizeToBasicLatin(s);
		s = DOUBLE_QUOTES_PATTERN.matcher(s).replaceAll("");
		/*
		 * Assume "<<...>>" is not an addition, but a variant of double quotes. This replacement before the pointyBracketsPattern replacement.
		 * Skipped because later nonAsciiLowercasePattern will replace the pointy brackets with a space.
		 */
		// s = s.replaceAll("(<<|>>)", "");
		/**
		 * FIXME: Do a thorough check of retractions (including "WITHDRAWN: ..." Cochrane reviews). Cochrane: PubMed,
		 * Medline and EMBASE use format "WITHDRAWN: ...", Web of Science the format "... (Withdrawn Paper, 2011, Art.
		 * No. CD001727)". See also "Retraction note to: ..." (e.g. https://pubmed.ncbi.nlm.nih.gov/24577730/)
		 */
		/**
		 * FIXME: Do a thorough check in the validation files to make sure that erratum publications do not remove the
		 * original publications (erratum as first publication encountered). There are some tests in
		 * {@link edu.dedupendnote.JaroWinklerTitleTest} (and an incomplete method
		 * {@link edu.dedupendnote.JaroWinklerTitleTest#testErrata()})
		 */
		String r = s.toLowerCase();
		r = LANGUAGE_PATTERN.matcher(r).replaceAll("");
		r = NON_INITIAL_SQUARE_BRACKETS_PATTERN.matcher(r).replaceAll("");
		r = POINTY_BRACKETS_PATTERN.matcher(r).replaceAll("");
		// Checks for the pointyBracketsPattern (the path not chosen)
		// Matcher m = pointyBracketsPattern.matcher(r);
		// StringBuffer sb = new StringBuffer();
		// List<String> htmlList = List.of("<b>", "</b>", "<bold>", "</bold>", "<br />",
		// "<del>", "</del>",
		// "<em>", "</em>",
		// "<i>", "</i>",
		// "<inf>", "</inf>",
		// "<ins>", "</ins>",
		// "<mark>", "</mark>",
		// "<small>", "</small>",
		// "<sub>", "</sub>", "<sup>", "</sup>", "<sup/>");
		// while (m.find()) {
		// if (! htmlList.contains(m.group())) {
		// log.error("PointyBracketPattern fires for {}", m.group());
		// }
		// m.appendReplacement(sb, "");
		// }
		// m.appendTail(sb);
		// r = sb.toString();
		r = ROUND_BRACKETS_PATTERN.matcher(r).replaceAll("");
		r = HYPHEN_PATTERN.matcher(r).replaceAll("");
		r = NON_ASCII_LOWERCASE_PATTERN.matcher(r).replaceAll(" ");
		r = r.strip();
		r = MULTIPLE_WHITE_SPACE_PATTERN.matcher(r).replaceAll(" ");
		r = STARTING_ARTICLE_PATTERN.matcher(r).replaceAll("");
		return r.strip();
	}

	/*
	 * TODO: From Java 9 onwards performance of String::replaceAll is much better
	 *
	 * But please check first:
	 *  - if the performance is better than the Java 8 Pattern approach chosen
	 *  - if naming the patterns isn't useful (names, testability) 
	 *  - align the Java9Plus versions with the Java8 versions!!! the Java9Plus versions are old.
	 */
	// static public String normalizeTitleJava9Plus(String s) {
	// String r = s.replaceAll(".\\[[^\\\\]+\\]$", "") // remove non initial "[...]"
	// .replaceAll("<[^>]+>", "") // remove "<...>"
	// .replaceAll("[\\(\\)]", "") // remove "(" and ")"
	// .toLowerCase().replaceAll("[^a-z0-9]", " ").trim().replaceAll("\\s{2,}", " ")
	// .replaceAll("^(the|a|an) ", "").trim();
	// // System.err.println(r);
	// if (r.equals("")) {
	// System.err.println("Title is empty: " + s);
	// throw new RuntimeErrorException(new Error("Empty title"));
	// }
	// return r;
	// }

	/**
	 * normalizeToBasicLatin: removes accents and diacritics when the base character belongs to the BasicLatin Unicode
	 * block (U+0000–U+007F) and removes all other characters.
	 */
	public static String normalizeToBasicLatin(String r) {
		if (NON_BASIC_LATIN_PATTERN.matcher(r).find()) {
			r = Normalizer.normalize(r, Normalizer.Form.NFD);
			// you can't reuse the existing matcher because r might be changed
			r = NON_BASIC_LATIN_PATTERN.matcher(r).replaceAll("");
		}
		return r;
	}
}