package edu.dedupendnote.domain;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.util.StringUtils;

import edu.dedupendnote.services.NormalizationService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Publication {

	private NormalizationService normalizationService;

	private List<String> allAuthors = new ArrayList<>();

	protected List<String> authors = new ArrayList<>();

	private List<String> authorsTransposed = new ArrayList<>();

	private boolean authorsAreTransposed = false;

	private Set<String> dois = new HashSet<>();

	private String id;

	private Set<String> isbns = new HashSet<>();
	private Set<String> issns = new HashSet<>();

	private Set<String> journals = new HashSet<>();

	/*
	 * The label field is used internally to mark the duplicate lists: the label of all duplicate records in a set receive the ID
	 * of the first record of this list. If a record has no duplicates, the label is not set. 
	 * It is NOT the content of the Label (EndNote field LB) of the EndNote input file. 
	 * If markMode is set, this field is exported. The original content of the Label field in the EndNote export file is overwritten in this case!
	 */
	private String label;

	private String pageEnd;

	private String pageStart;

	private String pageForComparison;

	private boolean pagesWithComma = false;

	private boolean presentInOldFile = false; // used when comparing 2 files

	private Integer publicationYear = 0;

	private String referenceType;

	public boolean severalPages;

	private String title; // only set for Reply-titles

	private List<String> titles = new ArrayList<>();

	private boolean isClinicalTrialGov = false;

	// @formatter:off
	/**
	 * Cochrane publications need a slightly different comparison. 
	 * The starting page is the Cochrane number of the review which doesn't change for different versions of the review. 
	 * Each version of the review has a unique DOI (e.g. "10.1002/14651858.cd008759.pub2"), but the first version has no ".pub" part, AND
	 * bibliographic databases sometimes use the common DOI / DOI of first version for all versions. 
	 * Therefore:
	 * - with other publications starting pages are compared BEFORE the DOIs. For Cochrane publications if both have a DOI, then only the DOIs are compared 
	 * - publication year must be the same
	 */
	// @formatter:on
	private boolean isCochrane = false;

	// @formatter:off
	/*
	 * Publications which are replies need special treatment. See the Pattern in the {@link IOService.replyPattern} 
	 * - record pairs where one of them is isReply == true, aren't compared for title (always true)
	 * - journals are compared stricter (see DeduplicationService.JOURNAL_SIMILARITY_NO_REPLY < DeduplicationService.JOURNAL_SIMILARITY_NO_REPLY)
	 * - in enrich() the longest title of a duplicate set is used
	 */
	// @formatter:on
	private boolean isReply = false;

	private boolean isPhase = false;

	private Boolean keptRecord = true;

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
	private static final Pattern TITLE_AND_SUBTITLE_PATTERN = Pattern.compile("^(.{50,}?)[:.?] (.{50,})$");
	// private static Pattern titleAndSubtitlePattern = Pattern.compile("^(.+)[:.?] (.+)$");

	private static final Pattern NUMBERS_WITHIN_PATTERN = Pattern.compile(".*\\d+.*");

	/**
	 * Some subtitles of journals ("Technical report", "Electronic resource", ...): will be removed
	 */
	static List<String> excludedJournalsParts = Arrays.asList("electronic resource", "et al.", "technical report");

	/*
	 * TODO: From Java 9 onwards performance of String::replaceAll is much better
	 *
	 * But please check first:
	 *  - if the performance is better than the Java 8 Pattern approach chosen
	 *  - if naming the patterns isn't useful (names, testability) 
	 *  - align the Java9Plus versions with the Java8 versions!!! the Java9Plus versions are old.
	 */
	// static public String normalizeJava9Plus(String s) {
	// String r = s.replaceAll(".\\[[^\\\\]+\\]$", "") // remove non initial "[...]"
	// .replaceAll("<[^>]+>", "") // remove "<...>"
	// .replaceAll("[\\(\\)]", "") // remove "(" and ")"
	// .toLowerCase().replaceAll("[^a-z0-9]", " ")
	// .trim().replaceAll("\\s{2,}", " ")
	// .replaceAll("^(the|a|an) ", "")
	// .trim();
	// // System.err.println(r);
	// if (r.equals("")) {
	// System.err.println("Title is empty: " + s);
	// throw new RuntimeErrorException(new Error("Empty title"));
	// }
	// return r;
	// }
	//
	// static public String normalizeJournalJava9Plus(String s) {
	// return s.replaceAll("&", " ") // we don't want "&" in the patterns
	// .replaceAll("Jpn", "Japanese") // irregular abbreviations
	// .replaceAll("Dtsch", "Deutsch")
	// .replaceAll("Natl", "National")
	// .replaceAll("Geneeskd", "Geneeskunde")
	// .replaceAll("-", "")
	// .replaceAll("^(The|Le|La|Der|Die|Das|Il) ", "") // article as start
	// .replaceAll("\\(([^\\)]*)\\)$", "") // "Ann Med Interne (Paris)" --> "Ann Med
	// Interne", or "J Med Ultrason (2001)"
	// .replaceAll(".\\[[^\\\\]+\\]", "") // "Zhonghua wai ke za zhi [Chinese journal of
	// surgery]" --> "Zhonghua wai ke za zhi"
	// .replaceAll("(\\]|\\[)", "") // "[Technical report] SAM-TR" --> "Technical report
	// SAM-TR"
	// .replaceAll("(:|/) .*$", "") // "BJOG: An International Journal of Obstetrics and
	// Gynaecology" --> "BJOG"
	// .replaceAll("\\s{2,}", " ")
	// .trim();
	// }

	// @formatter:off
	/*
	 * addAuthors() and author comparisons
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

	// public because used in tests
	// FIXME: add "No authorship, indicated"?
	public static final Pattern ANONYMOUS_OR_GROUPNAME_PATTERN = Pattern
			.compile("\\b(anonymous|consortium|et al|grp|group|nct|study)\\b", Pattern.CASE_INSENSITIVE);

	public void addAuthors(String author, NormalizationService normalizationService) {
		// skip "Anonymous", "et al" and group authors
		Matcher matcher = ANONYMOUS_OR_GROUPNAME_PATTERN.matcher(author);
		if (matcher.find()) {
			return;
		}

		if (authors.size() == 40) {
			return;
		}

		author = normalizationService.normalizeToBasicLatin(author);

		String[] parts = author.split("\\s*,\\s+"); // see testfile Non_Latin_input.txt for " , "
		if (parts.length < 2) {
			log.debug("Author {} cannot be split", author);
			this.authors.add(author);
			return;
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
		this.authors.add(lastName + " " + initials);

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
		 *  - do not set authorsAreTransposed
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
				authorsAreTransposed = true;
				log.debug("Author {} is transposed as {} {}", author, lastPart, initials);
				this.authorsTransposed.add(lastPart + " " + initials);
			} else {
				this.authorsTransposed.add(lastName + " " + initials);
			}
		} else {
			this.authorsTransposed.add(lastName + " " + initials);
		}
	}

	public Set<String> addDois(String doi) {
		// Scopus records sometimes add Cited references in this field
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
	 * Crude validation: only lengths 8, 10 and 13 are accepted. Invalid results as
	 * "X2345678" ("X" on non last position) are accepted.
	 * 
	 * For ISBN-10 the check digit is removed, for ISBN-13 the new prefix and the check digit 
	 *
	 * ISSNs are unique.  
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
	public Set<String> addIssns(String issn) {
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
			if (issnToAdd != null && !issns.contains(issnToAdd)) {
				issns.add(issnToAdd);
			}
			if (isbnToAdd != null && !isbns.contains(isbnToAdd)) {
				isbns.add(isbnToAdd);
			}
		}
		return issns;
	}

	public Set<String> addJournals(String journal, NormalizationService normalizationService) {
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
			journals.add(journal);
			return journals;
		}
		if (journal.toLowerCase().contains("cochrane")) {
			this.isCochrane = true;
		}
		// Strip last part of "Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date
		// of Publication: 1989."
		// Matcher matcher = JOURNAL_EXTRA_PATTERN.matcher(journal);
		// while (matcher.find()) {
		// journal = matcher.group(1);
		// }

		/*
		 * replace "\S/\S" with space: "Hematology/Oncology" --> "Hematology Oncology"
		 * BUT:
		 * "Chung-Kuo Hsiu Fu Chung Chien Wai Ko Tsa Chih/Chinese Journal of Reparative & Reconstructive Surgery"
		 * will NOT be split into 2 journals! Same for: Arzneimittel-Forschung/Drug
		 * Research
		 */
		// matcher = JOURNAL_SLASH_PATTERN.matcher(journal);
		// while (matcher.find()) {
		// journal = matcher.group(1) + " " + matcher.group(2);
		// }

		/*
		 * Split the following cases in separate journals: - ... [...]: Zhonghua wai ke za
		 * zhi [Chinese journal of surgery] - ... / ...: The Canadian Journal of
		 * Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques - ... =
		 * ...: Zhen ci yan jiu = Acupuncture research
		 */
		String[] parts = journal.split("[\\[\\]=|/]");
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
			// additional.add(JOURNAL_SECTION_MARKERS.matcher(journal).replaceAll("$1"));
			// }
			// matcher = JOURNAL_SUPPLEMENT_PATTERN.matcher(journal);
			// if (matcher.find()) {
			// log.debug("SupplementMatcher fired for: {}", journal);
			// additional.add(matcher.replaceAll(""));
			// }
		}

		if (!additional.isEmpty()) {
			list.addAll(additional);
		}
		/*
		 * The excludedJournalsParts are not a pattern. They are one of the journal(parts)
		 * and will be skipped
		 */
		for (String j : list) {
			j = j.strip();
			// FIXME: what happens when excludedJournalsParts.contains(j.toLowerCase())??
			if (!j.isEmpty() && !excludedJournalsParts.contains(j.toLowerCase())) {
				if (j.equals(j.toUpperCase()) && (j.contains(" ") || j.length() > 6)) {
					List<String> words = Arrays.asList(j.toLowerCase().split(" "));
					j = words.stream().map(StringUtils::capitalize).collect(Collectors.joining(" "));
				}
				String normalized = normalizationService.normalizeJournal(j);
				if (!normalized.isEmpty()) {
					journals.add(normalized);
				}
			}
		}
		log.debug("Result for {}: {}", journal, journals);
		return journals;
	}

	private static final List<String> noTitles = List.of("not available", "[not available]", "untitled");

	private static final Pattern RETRACTION_START_PATTERN = Pattern
			.compile("((retracted|removed|withdrawn)( article)?[.:] )(.+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern RETRACTION_END_PATTERN = Pattern.compile("(.+)\\(Retracted [Aa]rticle.*\\)");

	public void addTitles(String title, NormalizationService normalizationService) {
		if (noTitles.contains(title.toLowerCase())) {
			return;
		}
		String origTitle = title;
		Matcher endMatcher = RETRACTION_END_PATTERN.matcher(title);
		if (endMatcher.matches()) {
			this.title = origTitle;
			title = endMatcher.group(1);
		}
		Matcher startMatcher = RETRACTION_START_PATTERN.matcher(title);
		if (startMatcher.matches()) {
			this.title = origTitle;
			title = startMatcher.group(4);
		}
		addTitleWithNormalization(title, normalizationService);

		boolean splittable = true;
		String secondPart = title;

		while (splittable) {
			Matcher matcher = TITLE_AND_SUBTITLE_PATTERN.matcher(secondPart);
			if (matcher.find()) {
				// titles.add(matcher.group(1)); // add only the first part (min 50 characters)
				String firstPart = matcher.group(1); // add only the first part (min 50 characters)
				secondPart = matcher.group(2);
				if (firstPart.toLowerCase().endsWith("vs")) {
					addTitleWithNormalization(firstPart + " " + secondPart, normalizationService);
					// we could set splittable to false, but then 2nd part wont be split
				} else {
					addTitleWithNormalization(firstPart, normalizationService);
					addTitleWithNormalization(secondPart, normalizationService);
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
	}

	private void addTitleWithNormalization(String title, NormalizationService normalizationService) {
		String normalized = normalizationService.normalizeTitle(title);
		String[] parts = normalized.split("=");
		List<String> list = new ArrayList<>(Arrays.asList(parts));

		for (String t : list) {
			if (!titles.contains(t.strip())) {
				titles.add(normalized);
			}
		}
	}

	/**
	 * "UNSP ..." (and variants) should be cleaned from the C7 field (WoS). Import may have changed UNSP" Into "Unsp".
	 * "author..." (reply etc): delete rest of string
	 */
	private static final Pattern PAGES_ADDITIONS_PATTERN = Pattern.compile("((UNSP|Unsp)\\s*|; author.+$)");

	private static final Pattern PAGES_DATE_PATTERN = Pattern
			.compile("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b");

	private static final Pattern FIRST_PAGES_GROUP = Pattern.compile("^([^-,;]+)([,;])(.*)$");

	/**
	 * parsePages: parses the different input strings with page numbers / article numbers to the fields pageStart,
	 * pageEnd and pageStartForComparison.
	 *
	 * See also issues https://github.com/globbestael/DedupEndnote/issues/2 and
	 * https://github.com/globbestael/DedupEndnote/issues/3
	 */
	public void parsePages(String pages, String fieldName) {
		pages = PAGES_ADDITIONS_PATTERN.matcher(pages).replaceAll("").strip();
		// if Pages contains a date string, omit the pages
		Matcher matcher = PAGES_DATE_PATTERN.matcher(pages);
		while (matcher.find()) {
			pages = null;
		}
		// @formatter:off
		/*
		 * - Ovid Medline in RIS format puts author address in M2 field, which is exported as SP
		 * - WoS has in Article Number field (AR) cases as 'Pmid 29451177' and 'Pii s0016-5107(03)01975-8'. These article numbers
		 *   with a space can be skipped
		 */
		// @formatter:on
		if (pages == null || pages.isEmpty() || pages.length() > 30
				|| (fieldName.equals("C7") && pages.contains(" "))) {
			return;
		}

		// @formatter:off
		/*
		 *  C7 (Article Number) should sometimes overrule / overwrite SP (starting and ending page)
		 *  because when C7 is present, SP often contains the number of pages, and in a few cases relative pages (1-10 for a 10 pages article).
		 *
		 *  SE (Starting Ending Page) should sometimes overrule / overwrite SP (SE: 1746-1746, SP: 19)
		 *
		 *  C7 and SE occur before the SP field in an EndNote RIS file.
		 *
		 *  Solution:
		 *  - treat C7 as pages
		 *  - if C7 has already been called AND SE is a range of pages (except "1-..."), then SE can overwrite the C7 data.
		 *  - if C7 or SE has already been called AND SP is a range of pages (except "1-...") 
		 * 	  (e.g. C7: Pmid 29451177, and SP: 3-4) then SP can overwrite the C7 data.
		 * 
		 * FIXME: Wouldn't it be simpler if parsePages had 2 parameters (fieldContent, fieldName)?
		 */
		// @formatter:on
		// Cochrane uses hyphen characters instead of minus
		pages = pages.replaceAll("[\\u2010\\u00ad]", "-");

		if (fieldName.equals("C7")) {
			severalPages = true;
		} else {
			if ((pages.startsWith("e") || pages.startsWith("E")) && !pages.contains("-")) {
				severalPages = true;
			}
		}

		if (pageForComparison != null && (!pages.contains("-") || pages.startsWith("1-"))) {
			return;
		}

		// normalize starting page: W-22 --> 22
		pages = pages.replaceAll("^([^\\d]+)\\-(\\d+)", "$1$2");

		// @formatter:off
		/*
		 * First check for "[,;] before any "-"
		 * - if "," then there is another (discontinuous?) page or an addition
		 * - if ";" the there is an addition 
		 * In both cases: consider it as NOT a meeting abstract, and severalPages = true
		 */ 
		// @formatter:on
		Matcher firstPagesGroupMatcher = FIRST_PAGES_GROUP.matcher(pages);
		if (firstPagesGroupMatcher.matches()) {
			severalPages = true;
			this.pageStart = firstPagesGroupMatcher.group(1);
			if (",".equals(firstPagesGroupMatcher.group(2))) {
				this.pagesWithComma = true;
			}
			this.pageEnd = firstPagesGroupMatcher.group(3).strip();
			if (this.pageEnd.equals("")) {
				this.pageEnd = null;
			}
		} else if (pages.contains("-")) {
			severalPages = true;
			int indexOf = pages.indexOf("-");
			this.pageStart = pages.substring(0, indexOf);
			pageStart = pageStart.replaceAll("^0+", "");
			this.pageEnd = pages.substring(indexOf + 1);
			pageEnd = pageEnd.replaceAll("^0+", "");
			if (this.pageStart.length() > this.pageEnd.length()) {
				this.pageEnd = this.pageStart.substring(0, this.pageStart.length() - this.pageEnd.length())
						+ this.pageEnd;
			}
		} else {
			this.pageStart = pages;
		}
		if (!NUMBERS_WITHIN_PATTERN.matcher(pageStart).matches() && pageEnd != null
				&& NUMBERS_WITHIN_PATTERN.matcher(pageEnd).matches()) {
			pageStart = pageEnd;
			pageEnd = null;
		}
		if (NUMBERS_WITHIN_PATTERN.matcher(pageStart).matches()) {
			/**
			 * Books, reports, ... all start with page 1, therefore the ending page is used if available. BUT: Because
			 * Publication type is not available, pages range >= 100 is used as a criterion.
			 */
			this.pageForComparison = pageStart;
			// @formatter:off
			/**
			 * normalize starting page: W22 --> 22, 22S --> 22 
			 * 
			 * - Cochrane "page numbers" (or really article number) in form "CD010546" can no longer be recognized 
			 *   as Cochrane identifiers: "10546"
			 * - FIXME: arXiv page numbers ("arXiv:2107.12817v1") will be reduced to publication year and month ("2107"), 
			 *   which may result in False Positives. 
			 * See https://github.com/globbestael/DedupEndnote/issues/4 for preprint publications.
			 */
			// @formatter:on
			pageForComparison = pageForComparison.replaceAll("^([^1-9]*)([\\d]+)(.*)$", "$2");
			// Use pageEnd instead of pageStart for books (criteria: start = 1, end >= 100)
			if ("1".equals(pageForComparison) && pageEnd != null && pageEnd.length() > 2) {
				log.debug("Long pageEnd used for pageForComparison {}", pageEnd);
				String pageEndForComparison = pageEnd.replaceAll("^([^1-9]*)([\\d]+)(.*)$", "$2");
				if (pageEndForComparison.length() > 2) {
					this.pageForComparison = pageEndForComparison;
				}
			}
			if (pageForComparison.length() < 11 && pageEnd != null) {
				try {
					int pageStartInt = Integer.valueOf(pageForComparison);
					String pageEndForComparison = pageEnd.replaceAll("^([^1-9]*)([\\d]+)(.*)$", "$2");
					int pageEndInt = Integer.valueOf(pageEndForComparison);
					int pageRange = pageEndInt - pageStartInt;
					severalPages = pageRange > 1;
				} catch (NumberFormatException e) {
					log.debug("Input {} could not be parsed as an Integer", pageForComparison);
				}
			}
			if ("0".equals(pageForComparison)) {
				pageForComparison = null;
			}
		}
	}

	private static final Pattern publicationYearPattern = Pattern.compile("(^|\\D)(\\d{4})(\\D|$)");

	public void parsePublicationYear(String input) {
		Matcher matcher = publicationYearPattern.matcher(input);
		if (matcher.find()) {
			setPublicationYear(Integer.valueOf(matcher.group(2)));
		}
	}

	public void addReversedTitles() {
		if (!titles.isEmpty()) {
			List<String> reversed = new ArrayList<>();
			for (String t : titles) {
				reversed.add(new StringBuilder(t).reverse().toString());
			}
			titles.addAll(reversed);
		}
	}

	public void fillAllAuthors() {
		if (authors.isEmpty()) {
			return;
		}

		String s = authors.stream().collect(Collectors.joining("; "));
		allAuthors.add(s);
		// DONT: lowercasing the names makes different authors closer to 1.0

		if (authorsAreTransposed) {
			allAuthors.add(authorsTransposed.stream().collect(Collectors.joining("; ")));
		}
	}

	/*
	 * Lombok overridden getters and setters
	 */

	public void setPublicationYear(Integer publicationYear) {
		if (publicationYear < 1800) {
			publicationYear = 0;
		}
		this.publicationYear = publicationYear;
	}

}
