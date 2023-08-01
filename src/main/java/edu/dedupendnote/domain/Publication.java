package edu.dedupendnote.domain;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.util.StringUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Publication {

	private List<String> allAuthors = new ArrayList<>();

	public List<String> authors = new ArrayList<>();

	public List<String> authorsTransposed = new ArrayList<>();

	public boolean authorsAreTransposed = false;

	// FIXME: change to Set<String>()
	public Map<String, Integer> dois = new HashMap<>();

	public String id;

	public List<String> issns = new ArrayList<>();

	public Set<String> journals = new HashSet<>();

	/*
	 * The label field is used internally to mark the duplicate lists: the label of all
	 * duplicate records in a set receive the ID of the first record of this list. If a
	 * record has no duplicates, the label is not set. It is NOT the content of the Label
	 * (EndNote field LB) of the EndNote input file. If markMode is set, this field is
	 * exported. The original content of the Label field in the EndNote export file is
	 * overwritten in this case!
	 */
	public String label;

	public String pageEnd;

	public String pageStart;

	private String pageForComparison;

	private boolean presentInOldFile = false; // used when comparing 2 files

	public Integer publicationYear = 0;

	public String title; // only set for Reply-titles

	public List<String> titles = new ArrayList<>();

	/*
	 * Cochrane publications need a slightly different comparison. The starting page is
	 * the Cochrane number of the review which doesn't change for different versions of
	 * the review. Each version of the review has a unique DOI (e.g.
	 * "10.1002/14651858.cd008759.pub2"), but the first version has no ".pub" part, AND
	 * bibliographic databases sometimes use the common DOI / DOI of first version for all
	 * versions. Therefore: - with other publications starting pages are compared BEFORE
	 * the DOIs. For Cochrane publications if both have a DOI, then only the DOIs are
	 * compared - publication year must be the same
	 */
	private boolean isCochrane = false;

	/*
	 * Publications which are replies need special treatment. See the Pattern in the
	 * IOService.replyPattern - record pairs where one of them is isReply == true, aren't
	 * compared for title (always true) - journals are compared stricter (see
	 * DeduplcationService.JOURNAL_SIMILARITY_NO_REPLY <
	 * DeduplcationService.JOURNAL_SIMILARITY_NO_REPLY) - in enrich() the longest title of
	 * a duplicate set is used
	 */
	private boolean isReply = false;

	private boolean isPhase = false;

	private Boolean keptRecord = true;

	// see: http://blog.crossref.org/2015/08/doi-regular-expressions.html
	private static Pattern doiPattern = Pattern.compile("\\b(10.\\d{4,9}/[-._;()<>/:a-z0-9]+)\\b");

	/**
	 * ISSN/ISBN pattern: very crude pattern for ISSN, ISBN-10 and ISBN-13. Will accept
	 * invalid input: 1234-x567, "-1-2-30x4", ISBN-13 with a "X" check digit
	 */
	private static Pattern issnIsbnPattern = Pattern.compile("\\b([-\\dxX]{8,17})\\b"); // ISSN
																						// or
																						// ISBN

	/*
	 * In Java 8 replaceAll via PATTERN.matcher(s).replaceAll(replacement) is faster than
	 * s.replaceAll(replacement) See below for Java9Plus versions.
	 */
	/**
	 * Double quote character: will be removed
	 */
	private static Pattern doubleQuotesPattern = Pattern.compile("\"");

	/**
	 * All characters between a non-initial "[" and "]", including the square brackets and
	 * the preceding character
	 */
	private static Pattern nonInitialSquareBracketsPattern = Pattern.compile(".\\[[^\\\\]+\\]$"); // non
																									// initial
																									// "[...]"

	/**
	 * All characters between "<" and ">", including the pointy brackets
	 */
	private static Pattern pointyBracketsPattern = Pattern.compile("<[^>]+>"); // "<...>"

	/**
	 * "(" and ")"
	 */
	private static Pattern roundBracketsPattern = Pattern.compile("[\\(\\)]"); // "(" or
																				// ")"

	/**
	 * "-"
	 */
	private static Pattern hyphenPattern = Pattern.compile("\\-");

	/**
	 * All characters except [a-z] (lowercase) and [0-9]
	 */
	private static Pattern nonAsciiLowercasePattern = Pattern.compile("[^a-z0-9]");

	/**
	 * All characters except [a-z] (case insensitive) and [0-: will be replaced by SPACE
	 */
	private static Pattern nonAsciiPattern = Pattern.compile("[^a-z0-9]", Pattern.CASE_INSENSITIVE);

	/**
	 * Two or more white space characters: will be reduced to 1 SPACE
	 */
	private static Pattern multipleWhiteSpacePattern = Pattern.compile("\\s{2,}");

	/**
	 * Initial "the|a|an" + SPACE: will be removed
	 */
	private static Pattern startingArticlePattern = Pattern.compile("^(the|a|an) ");

	// FIXME: check last characters in pattern (space or punctuation?)
	private static Pattern erratumPattern = Pattern.compile("^(Erratum: |Erratum to|Correction to )(.*)$");

	// https://stackoverflow.com/questions/47162098/is-it-possible-to-match-nested-brackets-with-a-regex-without-using-recursion-or/47162099#47162099
	private static Pattern balancedBracespattern = Pattern
		.compile("(?=\\()(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^(]*(?=\\2$)");

	/*
	 * FIXME: Why is normalizeToBasicLatin not used?
	 */
	static public String normalizeJava8(String s) {
		s = doubleQuotesPattern.matcher(s).replaceAll("");
		/*
		 * FIXME: Do a thorough check of retractions (including "WITHDRAWN: ..." Cochrane
		 * reviews). Cochrane: PubMed, Medline and EMBASE use format "WITHDRAWN: ...", Web
		 * of Science the format "... (Withdrawn Paper, 2011, Art. No. CD001727)" See also
		 * "Retraction note to: ..." (e.g. https://pubmed.ncbi.nlm.nih.gov/24577730/)
		 */
		/*
		 * FIXME: Do a thorough check in the validation files to make sure that erratum
		 * records do not remove the original records (erratum as first record
		 * encountered). There are some test in {@link
		 * edu.dedupendnote.JaroWinklerTitleTest} (and an incomplete method {@link
		 * edu.dedupendnote.JaroWinklerTitleTest#testErrata()})
		 */
		Matcher matcher = erratumPattern.matcher(s);
		if (matcher.find()) {
			log.debug("Title WAS: {}", s);
			s = matcher.group(2);
			log.debug("Title IS1: {}", s);
			matcher = balancedBracespattern.matcher(s);
			while (matcher.find()) {
				if (matcher.end(0) == s.length()) {
					if (matcher.start() == 0) {
						s = s.substring(1, s.length() - 1);
					}
					else {
						s = s.substring(0, matcher.start() - 1);
					}
				}
			}
			log.debug("Title IS2: {}", s);
		}
		String r = s.toLowerCase();
		r = nonInitialSquareBracketsPattern.matcher(r).replaceAll("");
		r = pointyBracketsPattern.matcher(r).replaceAll("");
		r = roundBracketsPattern.matcher(r).replaceAll("");
		r = hyphenPattern.matcher(r).replaceAll("");
		r = nonAsciiLowercasePattern.matcher(r).replaceAll(" ");
		r = r.trim();
		r = multipleWhiteSpacePattern.matcher(r).replaceAll(" ");
		r = startingArticlePattern.matcher(r).replaceAll("");
		return r.trim();
	}

	/**
	 * All characters outside the BasicLatin Unicode block (\u0000 – \u007F). After
	 * Normalization with canonical decomposition (Normalizer.Form.NFD) all combining
	 * accents and diacritics, supplemental characters (e.g. "£") and all characters in
	 * other scripts will be removed
	 */
	private static Pattern nonBasicLatinPattern = Pattern.compile("[^\\p{InBasic_Latin}]");

	/**
	 * "&": will be replaced by SPACE because an anmpersand is problem in some of the
	 * following patterns
	 */
	private static Pattern ampersandPattern = Pattern.compile("&");

	/**
	 * "Jpn": will be replaced by "Japanese"
	 */
	private static Pattern jpnPattern = Pattern.compile("Jpn");

	/**
	 * "Dtsch": will be replaced by "Deutsch"
	 */
	private static Pattern dtschPattern = Pattern.compile("Dtsch");

	/**
	 * "Natl": will be replaced by "National"
	 */
	private static Pattern natlPattern = Pattern.compile("Natl");

	/**
	 * "[(...)G]eneeskd": will be replaced by "[(...)G]eneeskunde"
	 */
	private static Pattern geneeskdPattern = Pattern.compile("eneeskd");

	/**
	 * "heilkd": will be replaced by "heilkunde"
	 */
	private static Pattern heilkdPattern = Pattern.compile("heilkd");

	/**
	 * "Kongressbd" will be replaced by "Kongressband"
	 */
	private static Pattern kongressbdPattern = Pattern.compile("Kongressbd");

	/**
	 * "Monbl" (case insensitive): will be replaced by "Monatsbl"
	 */
	private static Pattern monatsblattPattern = Pattern.compile("Monbl\\b", Pattern.CASE_INSENSITIVE);

	/**
	 * Initial "Zbl(.?) " (case insensitive): will be replaced by "Zentralblatt"
	 */
	private static Pattern zentralblattPattern = Pattern.compile("^Zbl(\\.| )", Pattern.CASE_INSENSITIVE);

	/**
	 * "Jbr-btr" (case insensitive): will be replaced by "JBR BTR". Cheater!
	 */
	private static Pattern jbrPattern = Pattern.compile("Jbr-btr", Pattern.CASE_INSENSITIVE);

	/**
	 * "^(Rofo|Fortschritte .* Gebiet.* R.ntgenstrahlen)" (case insensitive): will be
	 * replaced by "Rofo". Cheater!
	 *
	 * Must be used BEFORE the latin_o_Pattern
	 *
	 */
	private static Pattern rofoPattern = Pattern.compile("^(Rofo|Fortschritte .* Gebiet.* R.ntgenstrahlen)",
			Pattern.CASE_INSENSITIVE);

	/**
	 * "o-[NON-WHITE-SPACE]": the hyphen will be removed. E.g. "gastro-enterology" -->
	 * "gastroenterology". Must be used BEFORE the minusOrDotPattern
	 */
	private static Pattern latin_o_Pattern = Pattern.compile("o\\-(\\S)"); // before
																			// minusOrDotPattern!
																			// gastro-enterology
																			// -->
																			// gastroenterology

	/**
	 * "-" or ".": will be replaced by a SPACE
	 */
	private static Pattern hyphenOrDotPattern = Pattern.compile("(-|\\.)");

	/**
	 * Initial "(The |Le |La |Les |L'|Der |Die |Das |Il |Het )": will be removed
	 */
	private static Pattern journalStartingArticlePattern = Pattern
		.compile("^(The |Le |La |Les |L'|Der |Die |Das |Il |Het )");

	/**
	 * "'s" at the end of a word: the apostrophe will be removed. E.g. Langenbeck's /
	 * Bailliere's / Crohn's. Must be called BEFORE the nonGenitiveApostrophePattern
	 */
	private static Pattern genitiveApostrophePattern = Pattern.compile("'s\\b");

	/**
	 * All other apostrophes (compare genitiveApostrophePattern): will be replaced by
	 * SPACE. E.g. "Annales d'Urologie", "Journal of Xi'an Jiaotong University (Medical
	 * Sciences)". Must be called AFTER genitiveApostrophePattern
	 */
	private static Pattern nonGenitiveApostrophePattern = Pattern.compile("'");

	/**
	 * All characters between "(" and ")" at the end of a string, including the round
	 * brackets: will be removed
	 */
	private static Pattern journalEndingRoundBracketsPattern = Pattern.compile("\\([^\\)]+\\)$");

	/**
	 * "(" and ")": will be replaced by SPACE. See also journalEndingRoundBracketsPattern.
	 * Some journals only have "(" without a ")", which causes regex problems
	 */
	private static Pattern journalOtherRoundBracketsPattern = Pattern.compile("(\\)|\\()");

	/**
	 * ":" or "/" and all following characters: will be removed. E.g. "BJOG: An
	 * International Journal of Obstetrics and Gynaecology" --> "BJOG"
	 */
	private static Pattern journalAdditionPattern = Pattern.compile("(:|/).*$");

	/**
	 * "/" not preceded and followed by white space: will be replaced by a space. E.g.
	 * "Hematology/Oncology" --> "Hematology Oncology" This replacement before the
	 * treatment of " / ".
	 *
	 * Imcomplete: "Chung-Kuo Hsiu Fu Chung Chien Wai Ko Tsa Chih/Chinese Journal of
	 * Reparative & Reconstructive Surgery" will NOT be split into 2 journals!
	 */
	private static Pattern journalSlashPattern = Pattern.compile("^(.+\\S)/(\\S.+)");

	/**
	 * Section markers and possibly name of the sections: will be removed
	 */
	// TODO: Should "Part", "Section", ... at the end of $1 be left out? E.g. "Comp
	// Biochem Physiol Part D Genomics Proteomics"
	private static Pattern journalSectionMarkers = Pattern.compile("^(.+)(\\b(Part|Section))?\\b([A-I]\\b.*)$");

	/**
	 * a number or ". Conference" and all following characters: The number and all
	 * following characters will be removed. E.g. "Clinical neuropharmacology.12 Suppl 2
	 * ()(pp v-xii; S1-105) 1989.Date of Publication: 1989." --> "Clinical
	 * neuropharmacology" E.g.: "European Respiratory Journal. Conference: European
	 * Respiratory Society Annual Congress" (Cochrane records)
	 */
	private static Pattern journalExtraPattern = Pattern.compile("^(.+?)((\\d.*|\\. Conference.*))$");

	/**
	 * Some subtitles of journals ("Technical report", "Electronic resource", ...): will
	 * be removed
	 */
	static List<String> excludedJournalsParts = Arrays.asList("electronic resource", "et al.", "technical report");

	/**
	 * (Suppl|Supplement|Supplementum) and following characters: will be me removed
	 */
	private static Pattern journalSupplementPattern = Pattern.compile("(\\b(Suppl|Supplement|Supplementum)\\b.*)$",
			Pattern.CASE_INSENSITIVE);

	static public String normalizeJournalJava8(String s) {
		String r = s;
		r = normalizeToBasicLatin(r);
		r = ampersandPattern.matcher(r).replaceAll(" "); // we don't want "&" in the
															// patterns
		r = jpnPattern.matcher(r).replaceAll("Japanese"); // irregular abbreviations
		r = dtschPattern.matcher(r).replaceAll("Deutsch");
		r = natlPattern.matcher(r).replaceAll("National");
		r = geneeskdPattern.matcher(r).replaceAll("eneeskunde");
		r = heilkdPattern.matcher(r).replaceAll("heilkunde");
		r = kongressbdPattern.matcher(r).replaceAll("Kongressband");
		r = monatsblattPattern.matcher(r).replaceAll("Monatsbl");
		r = zentralblattPattern.matcher(r).replaceAll("Zentralbl");
		// Cheating
		r = jbrPattern.matcher(r).replaceAll("JBR BTR");
		r = rofoPattern.matcher(r).replaceAll("Rofo");
		// Java 8-version
		if (latin_o_Pattern.matcher(r).find()) { // "Gastro-Enterology" ->
													// "Gastroenterology"
			StringBuilder sb = new StringBuilder();
			int last = 0;
			Matcher latin_o_matcher = latin_o_Pattern.matcher(r);
			while (latin_o_matcher.find()) {
				sb.append(r.substring(last, latin_o_matcher.start()));
				sb.append("o").append(latin_o_matcher.group(1).toLowerCase());
				last = latin_o_matcher.end();
			}
			sb.append(r.substring(last));
			r = sb.toString();
		}
		// Java 9+-version: see
		// https://stackoverflow.com/questions/2770967/use-java-and-regex-to-convert-casing-in-a-string
		// r = latin_o_Pattern.matcher(r).replaceAll(m -> "o" + m.group(1).toLowerCase());
		// // "Gastro-Enterology" -> "Gastroenterology"
		r = hyphenOrDotPattern.matcher(r).replaceAll(" ");
		r = journalStartingArticlePattern.matcher(r).replaceAll(""); // article at start
		r = genitiveApostrophePattern.matcher(r).replaceAll("s"); // Langenbeck's /
																	// Bailliere's /
																	// Crohn's
		r = nonGenitiveApostrophePattern.matcher(r).replaceAll(" "); // Annales d'Urologie
																		// / Journal of
																		// Xi'an Jiaotong
																		// University
																		// (Medical
																		// Sciences)
		r = journalEndingRoundBracketsPattern.matcher(r).replaceAll(""); // "Ann Med
																			// Interne
																			// (Paris)"
																			// --> "Ann
																			// Med
																			// Interne",
																			// or "J Med
																			// Ultrason
																			// (2001)"
		r = journalOtherRoundBracketsPattern.matcher(r).replaceAll(" "); // Some journals
																			// only have
																			// "(" without
																			// a ")",
																			// which
																			// causes
																			// regex
																			// problems
		if (r.toLowerCase().startsWith("http")) { // Cochrane library CENTRAL has journal
													// name of type:
													// Https://clinicaltrials.gov/show/nct00969397
			r = r.toLowerCase();
		}
		else {
			r = journalAdditionPattern.matcher(r).replaceAll(""); // "BJOG: An
																	// International
																	// Journal of
																	// Obstetrics and
																	// Gynaecology" -->
																	// "BJOG"
			r = nonAsciiPattern.matcher(r).replaceAll(" ");
		}
		r = multipleWhiteSpacePattern.matcher(r).replaceAll(" ");
		return r.trim(); // DO NOT lowercase (http titles are the exception)
	}

	/**
	 * normalizeToBasicLatin: removes accents and diacritics when the base character
	 * belongs to the BasicLatin Unicode block (U+0000–U+007F) and removes all other
	 * characters.
	 */
	public static String normalizeToBasicLatin(String r) {
		Matcher matcher = nonBasicLatinPattern.matcher(r);
		if (matcher.find()) {
			r = Normalizer.normalize(r, Normalizer.Form.NFD).replaceAll("[^\\p{InBasic_Latin}]", "");
		}
		return r;
	}

	/*
	 * TODO: From Java 9 onwards performance of String::replaceAll is much better
	 *
	 * But please check first: - if the performance is better than the Java 8 Pattern
	 * approach chosen - if naming the patterns isn't useful (names, testability) - align
	 * the Java9Plus versions with the Java8 versions!!! the Java9Plus versions are old.
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

	static private Pattern lastNameAdditionsPattern = Pattern.compile("^(.+)\\b(jr|sr|1st|2nd|3rd|ii|iii)\\b(.*)$",
			Pattern.CASE_INSENSITIVE);

	// public because used in tests
	// FIXME: add "No authorship, indicated"?
	static public Pattern anonymousOrGroupNamePattern = Pattern
		.compile("\\b(anonymous|consortium|et al|grp|group|nct|study)\\b", Pattern.CASE_INSENSITIVE);

	public void addAuthors(String author) {
		// skip "Anonymous", "et al" and group authors
		Matcher matcher = anonymousOrGroupNamePattern.matcher(author);
		if (matcher.find()) {
			return;
		}

		author = normalizeToBasicLatin(author);
		author = author.replaceAll("-", " ");

		// FIXME: should this be a Pattern?
		String[] parts = author.split("\\s*,\\s+"); // see testfile Non_Latin_input.txt
													// for " , "
		if (parts.length < 2) {
			log.debug("Author {} cannot be split", author);
			this.authors.add(author);
			return;
		}
		String lastName = parts[0];
		String firstNames = parts[1];

		/*
		 * Normalize lastName: - normal capitalization Some databases (a.o. Web of
		 * Science) can have complete author names in capitals. EndNote X9 shows them
		 * capitalized ("Marchioli, R") but the export file has "MARCHIOLI, R"! -
		 * additions (2nd, Jr, Sr, III) as part of LastName are removed. These additions
		 * usually are the 3rd part of the EndNote names (and not used, see above)
		 */
		if (lastName.equals(lastName.toUpperCase())) {
			String[] lastNameParts2 = lastName.toLowerCase().split(" ");
			List<String> lastNameParts = new ArrayList<>(Arrays.asList(lastNameParts2));
			lastName = lastNameParts.stream().map(p -> StringUtils.capitalize(p)).collect(Collectors.joining(" "));
		}
		matcher = lastNameAdditionsPattern.matcher(lastName);
		if (matcher.find()) {
			// FIXME Java 8: NOT in Java 8, but in Java 11: String.strip()
			// See
			// https://stackoverflow.com/questions/51266582/difference-between-string-trim-and-strip-methods-in-java-11
			// lastName = (matcher.group(1) + matcher.group(3)).strip();
			lastName = (matcher.group(1).trim() + " " + matcher.group(3).trim()).trim();
			log.debug("new lastName: {}", lastName);
		}

		// @formatter:off
		/*
		 * Possible enhancement: Switch firstNames and lastName for esp. Chinese author names IFF there is a full forName (Wei, Li ==> Li, Wei).
		 * - as for transposed author names: we need a Boolean authorsAreSwitched and aList<String> authorsSwitched for the temporary results.
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

		// Reducing the first names to their initials, stripping everything but the
		// initials, and leaving out the comma
		// makes JWS higher for similar names and lower for different names.
		String initials = firstNames.replaceAll("[^A-Z]", "");
		this.authors.add(lastName + " " + initials);

		// @formatter:off
		/*
		 *  Transposed author names:
		 *  If the last name contains a space:
		 *  - create a transposed author with all parts of the last name except the last one left out and added as the last initials
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
				// initials += lastNameParts.stream().collect(Collectors.joining());
				// initials = initials.replaceAll("[^A-Z]", "");
				initials += lastNameParts.stream().map(p -> p.substring(0, 1)).collect(Collectors.joining());
				authorsAreTransposed = true;
				log.debug("Author {} is transposed as {} {}", author, lastPart, initials);
				this.authorsTransposed.add(lastPart + " " + initials);
			}
			else {
				this.authorsTransposed.add(lastName + " " + initials);
			}
		}
		else {
			this.authorsTransposed.add(lastName + " " + initials);
		}
	}

	public Map<String, Integer> addDois(String doi) {
		if (doi.length() > 100) { // Scopus records sometimes add Cited references in this
									// field
			return dois;
		}
		try {
			doi = URLDecoder.decode(doi, "UTF8");
			doi = StringEscapeUtils.unescapeHtml4(doi);
		}
		catch (UnsupportedEncodingException e) {
			log.info(e.getMessage());
			e.printStackTrace();
		}
		Matcher matcher = doiPattern.matcher(doi.toLowerCase());
		while (matcher.find()) {
			String group = matcher.group(1);
			dois.put(group.replaceAll("\\.$", ""), 1);
		}
		return dois;
	}

	/*
	 * ISSNs and ISBNs are treated in the same way: - uppercased - hyphens removed
	 *
	 * Crude validation: only lengths 8, 10 and 13 are accepted. Invalid results as
	 * "X2345678" ("X" on non last position) are acceoted.
	 *
	 * Paths not chosen: - full validation - (ISBN) conversion to ISBN-13 - (ISSN) use of
	 * ISSN-L and the linking table: see
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
	public List<String> addIssns(String issn) {
		Matcher matcher = issnIsbnPattern.matcher(issn.toUpperCase());
		while (matcher.find()) {
			String group = matcher.group(1).replaceAll("-", "");
			if (group.length() == 8 || group.length() == 10 || group.length() == 13) {
				issns.add(group);
			}
		}
		return issns;
	}

	public Set<String> addJournals(String journal) {
		/*
		 * General: - mark Cochrane publication - remove unwanted parts - split combined
		 * journal names into in separate journal names - create other variant journal
		 * names - for all journal names - capitalize - normalize
		 */
		if (journal.toLowerCase().contains("cochrane")) {
			this.isCochrane = true;
		}
		// journal = journalExtraPattern.matcher(journal).replaceAll(""); // Strip last
		// part of "Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date
		// of Publication: 1989."
		Matcher matcher = journalExtraPattern.matcher(journal); // Strip last part of
																// "Clinical
																// neuropharmacology.12
																// Suppl 2 ()(pp v-xii;
																// S1-105) 1989.Date of
																// Publication: 1989."
		while (matcher.find()) {
			journal = matcher.group(1);
		}

		/*
		 * replace "\S/\S" with space: "Hematology/Oncology" --> "Hematology Oncology"
		 * BUT:
		 * "Chung-Kuo Hsiu Fu Chung Chien Wai Ko Tsa Chih/Chinese Journal of Reparative & Reconstructive Surgery"
		 * will NOT be split into 2 journals! Same for: Arzneimittel-Forschung/Drug
		 * Research
		 */
		matcher = journalSlashPattern.matcher(journal);
		while (matcher.find()) {
			journal = matcher.group(1) + " " + matcher.group(2);
		}

		/*
		 * Split the following cases in separate journals: - ... [...]: Zhonghua wai ke za
		 * zhi [Chinese journal of surgery] - ... / ...: The Canadian Journal of
		 * Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques - ... =
		 * ...: Zhen ci yan jiu = Acupuncture research
		 */
		String[] parts = journal.split("(\\[|\\]|=|/)");
		List<String> list = new ArrayList<String>(Arrays.asList(parts));

		/*
		 * Journals with a ":" will get 2 variants. e.g
		 * "BJOG: An International Journal of Obstetrics and Gynaecology" or
		 * "Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine" -
		 * one with the colon and all following characters removed ("BJOG" and
		 * "Clinical Medicine Insights"). The removal of these characters does not happen
		 * here, but later within normalizeJournalJava8() (journalAdditionPattern) - one
		 * with the ":" replaced with a SPACE
		 * ("BJOG: An International Journal of Obstetrics and Gynaecology" and
		 * "Clinical Medicine Insights Circulatory, Respiratory and Pulmonary Medicine")
		 */
		Set<String> additional = new HashSet<>();
		for (String j : list) {
			if (j.contains(":")) {
				additional.add(j.replaceAll(":", " "));
			}
			if (j.contains("-")) {
				additional.add(j.replaceAll("-", " "));
				additional.add(j.replaceAll("-", ""));
			}
			if (j.contains("ae")) {
				additional.add(j.replaceAll("ae", "e"));
			}
			if (journalSectionMarkers.matcher(journal).matches()) {
				additional.add(journalSectionMarkers.matcher(journal).replaceAll("$1"));
			}
			matcher = journalSupplementPattern.matcher(journal);
			if (matcher.find()) {
				// log.debug("SupplementMatcher fired for: {}", journal);
				additional.add(matcher.replaceAll(""));
			}
		}

		if (!additional.isEmpty()) {
			list.addAll(additional);
		}
		/*
		 * The excludedJournalsParts are not a pattern. They are one of the journal(parts)
		 * and will be skipped
		 */
		for (String j : list) {
			j = j.trim();
			// FIXME: what happens when excludedJournalsParts.contains(j.toLowerCase())??
			if (!j.isEmpty() && !excludedJournalsParts.contains(j.toLowerCase())) {
				if (j.equals(j.toUpperCase()) && (j.contains(" ") || j.length() > 6)) {
					List<String> words = Arrays.asList(j.toLowerCase().split(" "));
					j = words.stream().map(p -> StringUtils.capitalize(p)).collect(Collectors.joining(" "));
				}
				String normalized = normalizeJournalJava8(j);
				if (!normalized.isEmpty()) {
					journals.add(normalized);
				}
			}
		}
		log.debug("Result for {}: {}", journal, journals);
		return journals;
	}

	public void addTitles(String title) {
		String normalized = normalizeJava8(title);
		if (!titles.contains(normalized)) {
			titles.add(normalized);
		}
	}

	private static Pattern pagesDatePattern = Pattern
		.compile("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\b");

	/**
	 * parsePages: parses the different input strings with page numbers / article numbers
	 * to the fields pageStart, pageEnd and pageStartForComparison.
	 *
	 * See also issues https://github.com/globbestael/DedupEndnote/issues/2 and
	 * https://github.com/globbestael/DedupEndnote/issues/3
	 */
	public void parsePages(String pages) {
		// "UNSP ..." should be cleaned from the C7 field (WoS). Import may have changed
		// "UNSP" Into "Unsp"
		pages = pages.replaceAll("(UNSP|Unsp)\\s*", "");
		Matcher matcher = pagesDatePattern.matcher(pages); // if Pages contains a date
															// string, omit the pages
		while (matcher.find()) {
			pages = null;
		}
		// Ovid Medline in RIS format puts author address in M2 field, which is exported
		// as SP
		if (pages == null || pages.isEmpty() || pages.length() > 20) {
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
		 *  - if C7 has already been called AND SE is a range of pages, then SE can overwrite the C7 data.
		 *  - if C7 or SE has already been called AND SP is a range of pages (e.g. C7: Pmid 29451177, and SP: 3-4)
		 *    then SP can overwrite the C7 data.
		 *
		 * TODO: Should range of pages starting with "1-" be excluded? But type "1-234" occurs with books.
		 */
		// @formatter:on
		if (pageForComparison != null && !pages.contains("-")) {
			return;
		}

		pages = pages.replaceAll("(\\u2010|\\u00ad)", "-"); // Cochrane uses hyphen
															// characters instead of minus
		// normalize starting page: W-22 --> 22
		pages = pages.replaceAll("^([^\\d]+)\\-(\\d+)", "$1$2");
		if (pages.contains("-")) {
			int indexOf = pages.indexOf("-");
			this.pageStart = pages.substring(0, indexOf);
			pageStart = pageStart.replaceAll("^0+", "");
			this.pageEnd = pages.substring(indexOf + 1);
			// pageEnd = pageEnd.replaceAll("^([^1-9]*)([\\d]+)(.*)$", "$2");
			pageEnd = pageEnd.replaceAll("^0+", "");
			if (this.pageStart.length() > this.pageEnd.length()) {
				this.pageEnd = this.pageStart.substring(0, this.pageStart.length() - this.pageEnd.length())
						+ this.pageEnd;
			}
		}
		else {
			this.pageStart = pages;
		}
		if (pageStart.matches(".*\\d+.*")) {
			/*
			 * Books, reports, ... all start with page 1, therefore the ending page is
			 * used if available. BUT: Because Publication type is not available, pages
			 * range >= 100 is used as a criterion.
			 */
			this.pageForComparison = pageStart;
			/*
			 * normalize starting page: W22 --> 22, 22S --> 22 - Cochrane "page numbers"
			 * (or really article number) in form "CD010546" can no longer be recognized
			 * as Cochrane identifiers: "10546" - FIXME: arXiv page numbers
			 * ("arXiv:2107.12817v1") will be reduced to publication year and month
			 * ("2107"), which may result in False Positives. See
			 * https://github.com/globbestael/DedupEndnote/issues/4 for preprint
			 * publications.
			 */
			pageForComparison = pageForComparison.replaceAll("^([^0-9]*)([\\d]+)(.*)$", "$2");
			if ("1".equals(pageForComparison) && pageEnd != null && pageEnd.length() > 2) {
				// log.error("Long pageEnd used for pageForComparison {}", pageEnd);
				String pageEndForComparison = pageEnd.replaceAll("^([^1-9]*)([\\d]+)(.*)$", "$2");
				if (pageEndForComparison.length() > 2) {
					this.pageForComparison = pageEndForComparison;
				}
			}
		}
	}

	public void addReversedTitles() {
		List<String> reversed = new ArrayList<>();
		if (titles.isEmpty()) {
			titles.add(this.id);
		}
		for (String t : titles) {
			reversed.add(new StringBuilder(t).reverse().toString());
		}
		titles.addAll(reversed);
	}

	public void fillAllAuthors() {
		if (authors.isEmpty()) {
			return;
		}

		String s = authors.stream().collect(Collectors.joining("; "));
		allAuthors.add(s);
		// DONT: lowercasing the names makes different authors closer to 1.0
		// allAuthors = allAuthors.toLowerCase();

		if (authorsAreTransposed) {
			allAuthors.add(authorsTransposed.stream().collect(Collectors.joining("; ")));
		}
	}

	/*
	 * Lombok overridden getters and setters
	 */

	public void setPublicationYear(Integer publicationYear) {
		if (publicationYear < 1900) {
			publicationYear = 0;
		}
		this.publicationYear = publicationYear;
	}

}
