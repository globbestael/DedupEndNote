package edu.dedupendnote.services;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

@Service
public class NormalizationService {

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
	 * ":" or "/"  and SPACE and all following characters: will be removed. 
	 * E.g. "BJOG: An International Journal of Obstetrics and Gynaecology" --> "BJOG"
	 * 
	 * The space is wanted to prevent splitting in types as "Hematology/oncology".
	 */
	private static final Pattern JOURNAL_ADDITION_PATTERN = Pattern.compile("[:/]\\s.*$");

	/**
	 * (Suppl|Supplement|Supplementum) and following characters: will be me removed
	 */
	public static final Pattern JOURNAL_SUPPLEMENT_PATTERN = Pattern
			.compile("(\\b(Suppl|Supplement|Supplementum)\\b.*)$", Pattern.CASE_INSENSITIVE);

	/**
	 * A number or ". Conference" and all following characters: The number and all following characters will be removed.
	 * E.g.:
	 * - "Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date of Publication: 1989." --> "Clinical neuropharmacology" 
	 * - "European Respiratory Journal. Conference: European Respiratory Society Annual Congress" (Cochrane records)
	 */
	public static final Pattern JOURNAL_EXTRA_PATTERN = Pattern.compile("^(.+?)((\\d.*|\\. Conference.*))$");

	/**
	 * Some subtitles of journals ("Technical report", "Electronic resource", ...): will be removed
	 */
	static List<String> excludedJournalsParts = Arrays.asList("electronic resource", "et al.", "technical report");

	public String normalizeTitle(String s) {
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
		 * FIXME: Do a thorough check in the validation files to make sure that erratum records do not remove the
		 * original records (erratum as first record encountered). There are some tests in
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

	public String normalizeJournal(String s) {
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

	/**
	 * normalizeToBasicLatin: removes accents and diacritics when the base character belongs to the BasicLatin Unicode
	 * block (U+0000–U+007F) and removes all other characters.
	 */
	public String normalizeToBasicLatin(String r) {
		if (NON_BASIC_LATIN_PATTERN.matcher(r).find()) {
			r = Normalizer.normalize(r, Normalizer.Form.NFD);
			// you can't reuse the existing matcher because r might be changed
			r = NON_BASIC_LATIN_PATTERN.matcher(r).replaceAll("");
		}
		return r;
	}
}