package edu.dedupendnote.domain;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/*
 * This class only conatins the pattern used in the normalization service / process
 */
public class NormPatterns {

	/**
	 * "&": will be replaced by SPACE because an ampersand is problem in some of the following patterns
	 */
	public static final Pattern AMPERSAND_PATTERN = Pattern.compile("&");

	/**
	 * Several forms of "author" values which are removed for comparisons (anonymous, et al, group names, ...)
	 */
	public static final Pattern ANONYMOUS_OR_GROUPNAME_PATTERN = Pattern.compile(
			"\\b(anonymous|No authorship, indicated|consortium|et al|grp|group|nct|study)\\b",
			Pattern.CASE_INSENSITIVE);

	/*
	 * Finds the longest group (i.e. the outer group) of balanced braces. Use group(0) to get this content.
	 * https://stackoverflow.com/questions/47162098/is-it-possible-to-match-nested-brackets-with-a-regex-without-using-recursion-or/47162099#47162099
	 */
	public static final Pattern BALANCED_BRACES_PATTERN = Pattern.compile(
			"(?=\\()(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^(]*(?=\\2$)");

	/**
	 * String starts with "Case report(s): ", "Case series: " or "Case [number]: " (titles). Will be removed
	 * 
	 * These substrings are too short for the TITLE_AND_SUBTITLE_PATTERN to be split as a (sub)title.
	 */
	public static final Pattern CASE_REPORT_PATTERN = Pattern.compile("^case (reports?|series|[-\\d]+)[.:] ",
			Pattern.CASE_INSENSITIVE);

	// see: http://blog.crossref.org/2015/08/doi-regular-expressions.html
	// see https://github.com/globbestael/DedupEndNote/issues/16 for shortDOIs
	public static final Pattern DOI_PATTERN = Pattern.compile("\\b(10.\\d{4,9}/[-._;()<>/:a-z0-9]+)\\b");

	/**
	 * Double quote character: will be removed
	 */
	public static final Pattern DOUBLE_QUOTES_PATTERN = Pattern.compile("\"");

	public static final Pattern EXCEPT_CAPITALS_PATTERN = Pattern.compile("[^A-Z]");

	/**
	 * Some subtitles of journals ("Technical report", "Electronic resource", ...): will be removed
	 */
	public static final List<String> EXCLUDED_JOURNALS_PARTS = Arrays.asList("electronic resource", "et al.",
			"technical report");

	/**
	 * "'s" at the end of a word: the apostrophe will be removed. E.g. Langenbeck's / Bailliere's / Crohn's. Must be
	 * called BEFORE the nonGenitiveApostrophePattern
	 */
	public static final Pattern GENITIVE_APOSTROPHE_PATTERN = Pattern.compile("'s\\b");

	/**
	 * "-"
	 */
	public static final Pattern HYPHEN_PATTERN = Pattern.compile("\\-");

	/**
	 * Split main title and subtitle on " -" except for cases as "...virus-positive and -negative patients".
	 * 
	 * There are also older records where Greek letters are skipped in database. Example from Embase 2003 article: Real:
	 * Role of κ-opioid receptor activation in pharmacological preconditioning of swine Embase: Role of -opioid receptor
	 * activation in pharmacological preconditioning of swine
	 */
	public static final Pattern HYPHEN_AS_SUBTITLE_DIVIDER_PATTERN = Pattern
			.compile("(.*(?<!( and| of| or|,|\\d)))( -)([ \\p{Alpha}]+)$");

	/**
	 * "-" or ".": will be replaced by a SPACE
	 */
	public static final Pattern HYPHEN_OR_DOT_PATTERN = Pattern.compile("[\\.-]");

	/**
	 * ISSN/ISBN pattern: very crude pattern for ISSN, ISBN-10 and ISBN-13. Will accept invalid input: 1234-x567,
	 * "-1-2-30x4", ISBN-13 with a "X" check digit
	 */
	public static final Pattern ISSN_ISBN_PATTERN = Pattern.compile("\\b([-\\dxX]{8,17})\\b");

	/**
	 * ":" or "/" and SPACE and all following characters: will be removed. E.g. "BJOG: An International Journal of
	 * Obstetrics and Gynaecology" --> "BJOG"
	 * 
	 * The space is wanted to prevent splitting in types as "Hematology/oncology".
	 */
	public static final Pattern JOURNAL_ADDITION_PATTERN = Pattern.compile("[:/]\\s.*$");

	/**
	 * "Dtsch": will be replaced by "Deutsch"
	 */
	public static final Pattern JOURNAL_ABBREVIATION_DTSCH_PATTERN = Pattern.compile("Dtsch");

	/**
	 * "[(...)G]eneeskd": will be replaced by "[(...)G]eneeskunde"
	 */
	public static final Pattern JOURNAL_ABBREVIATION_GENEESKD_PATTERN = Pattern.compile("eneeskd");

	/**
	 * "heilkd": will be replaced by "heilkunde"
	 */
	public static final Pattern JOURNAL_ABBREVIATION_HEILKD_PATTERN = Pattern.compile("heilkd");

	/**
	 * "Jbr-btr" (case insensitive): will be replaced by "JBR BTR". Cheater!
	 */
	public static final Pattern JOURNAL_ABBREVIATION_JBR_PATTERN = Pattern.compile("Jbr-btr", Pattern.CASE_INSENSITIVE);

	/**
	 * "Jpn": will be replaced by "Japanese"
	 */
	public static final Pattern JOURNAL_ABBREVIATION_JPN_PATTERN = Pattern.compile("Jpn");

	/**
	 * "Kongressbd" will be replaced by "Kongressband"
	 */
	public static final Pattern JOURNAL_ABBREVIATION_KONGRESSBD_PATTERN = Pattern.compile("Kongressbd");

	/**
	 * "Monbl" (case insensitive): will be replaced by "Monatsbl"
	 */
	public static final Pattern JOURNAL_ABBREVIATION_MONATSBLATT_PATTERN = Pattern.compile("Monbl\\b",
			Pattern.CASE_INSENSITIVE);

	/**
	 * "Natl": will be replaced by "National"
	 */
	public static final Pattern JOURNAL_ABBREVIATION_NATL_PATTERN = Pattern.compile("Natl");

	/**
	 * "^(Rofo|Fortschritte .* Gebiet.* R.ntgenstrahlen)" (case insensitive): will be replaced by "Rofo". Cheater!
	 *
	 * Must be used BEFORE the latinOPattern
	 *
	 */
	public static final Pattern JOURNAL_ABBREVIATION_ROFO_PATTERN = Pattern
			.compile("^(Rofo|Fortschritte .* Gebiet.* R.ntgenstrahlen)", Pattern.CASE_INSENSITIVE);

	/**
	 * Initial "Zbl(.?) " (case insensitive): will be replaced by "Zentralblatt"
	 */
	public static final Pattern JOURNAL_ABBREVIATION_ZENTRALBLATT_PATTERN = Pattern.compile("^Zbl[\\. ]",
			Pattern.CASE_INSENSITIVE);

	/**
	 * All characters between "(" and ")" at the end of a string, including the round brackets: will be removed
	 */
	public static final Pattern JOURNAL_ENDING_ROUND_BRACKETS_PATTERN = Pattern.compile("\\([^\\)]+\\)$");

	/*
	 * A number or ". Conference" and all following characters: The number and all following characters will be removed.
	 * E.g.: 
	 * - "Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date of Publication: 1989." --> "Clinical neuropharmacology" 
	 * - "European Respiratory Journal. Conference: European Respiratory Society Annual Congress" (Cochrane publications) --> "European Respiratory Journal"
	 */
	public static final Pattern JOURNAL_EXTRA_PATTERN = Pattern.compile("^(.+?)((\\d+|\\. Conference.*))$");

	/**
	 * "(" and ")": will be replaced by SPACE. See also journalEndingRoundBracketsPattern. Some journals only have "("
	 * without a ")", which causes regex problems
	 */
	public static final Pattern JOURNAL_OTHER_ROUND_BRACKETS_PATTERN = Pattern.compile("[\\)\\(]");

	/**
	 * Initial "(The |Le |La |Les |L'|Der |Die |Das |Il |Het )": will be removed
	 */
	public static final Pattern JOURNAL_STARTING_ARTICLE_PATTERN = Pattern
			.compile("^(The |Le |La |Les |L'|Der |Die |Das |Il |Het )");

	/**
	 * (Suppl|Supplement|Supplementum) and following characters: will be me removed
	 */
	public static final Pattern JOURNAL_SUPPLEMENT_PATTERN = Pattern
			.compile("(\\b(Suppl|Supplement|Supplementum)\\b.*)$", Pattern.CASE_INSENSITIVE);

	/**
	 * Esp. Scopus uses additions as "(Japanese)" (or "(Japanese text)") at the end of the title.
	 *
	 * The pattern is used on the lowercased title. The languages are not complete: based on the 200 most frequent
	 * (sub)titles in the testfiles.
	 */
	public static final Pattern LANGUAGE_PATTERN = Pattern
			.compile("(\\(?(chinese|dutch|french|german|italian|japanese|polish|russian|spanish)( text)?\\)?)$");

	public static final Pattern LAST_NAME_ADDITIONS_PATTERN = Pattern
			.compile("^(.+)\\b(jr|sr|1st|2nd|3rd|ii|iii)\\b(.*)$", Pattern.CASE_INSENSITIVE);

	/**
	 * "o-[NON-WHITE-SPACE]": the hyphen will be removed. E.g. "gastro-enterology" --> "gastroenterology". Must be used
	 * BEFORE the minusOrDotPattern
	 */
	public static final Pattern LATIN_O_PATTERN = Pattern.compile("o\\-(\\S)");

	/**
	 * Two or more white space characters: will be reduced to 1 SPACE
	 */
	public static final Pattern MULTIPLE_WHITE_SPACE_PATTERN = Pattern.compile("\\s{2,}");

	/**
	 * Titles which oindicate that there is no title. Will be removed
	 */
	public static final List<String> NO_TITLES = List.of("not available", "[not available]", "untitled");

	/**
	 * All characters except [a-z] (lowercase) and [0-9]
	 */
	public static final Pattern NON_ASCII_LOWERCASE_PATTERN = Pattern.compile("[^a-z0-9]");

	/**
	 * All characters except [a-z] (case insensitive) and [0-9] will be replaced by SPACE
	 */
	public static final Pattern NON_ASCII_PATTERN = Pattern.compile("[^a-z0-9]", Pattern.CASE_INSENSITIVE);

	/**
	 * All characters outside the BasicLatin Unicode block (\u0000 – \u007F). After Normalization with canonical
	 * decomposition (Normalizer.Form.NFD) all combining accents and diacritics, supplemental characters (e.g. "£") and
	 * all characters in other scripts will be removed
	 */
	public static final Pattern NON_BASIC_LATIN_PATTERN = Pattern.compile("[^\\p{InBasic_Latin}]");

	/**
	 * All other apostrophes (compare genitiveApostrophePattern): will be replaced by SPACE. E.g. "Annales d'Urologie",
	 * "Journal of Xi'an Jiaotong University (Medical Sciences)". Must be called AFTER genitiveApostrophePattern
	 */
	public static final Pattern NON_GENITIVE_APOSTROPHE_PATTERN = Pattern.compile("'");

	/**
	 * All characters between a non-initial "[" and "]", including the square brackets and the preceding character
	 */
	public static final Pattern NON_INITIAL_SQUARE_BRACKETS_PATTERN = Pattern.compile(".\\[[^\\\\]+\\]$");

	/**
	 * - "UNSP ..." (and variants) should be cleaned from the C7 field (WoS). Import may have changed UNSP" Into "Unsp". 
	 *   This replacement is now applied to ALL pages fields
	 * 
	 * A previous version had as pattern: "(^(UNSP|Article)\\s*|; author.+$)" and comment
	 * - "author..." (reply etc): delete rest of string
	 * Is this not necessary any more because the nromailzation of pages uses all ranges, and picks the first Arabic/Roman one?
	 */
	public static final Pattern PAGES_ADDITIONS_PATTERN = Pattern.compile("^(UNSP|Article|ARTN)\\s*",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Pattern to replace pages "S6-97-s6-99" by "S697-s699"
	 */
	public static final Pattern PAGES_HYPHEN_MERGE_1_PATTERN = Pattern
			.compile("(?<!\\d+)([a-zA-Z0-9]+)-(\\d+)-([a-zA-Z0-9]+)-(\\d+)");

	/**
	 * Pattern to replace pages "ii-218-ii-228" by "ii218-ii228", and "S-12" by "S12"
	 */
	public static final Pattern PAGES_HYPHEN_MERGE_2_PATTERN = Pattern.compile("(?<!\\d+)([a-zA-Z]+)-(\\d+)");

	/**
	 * English month names.
	 * 
	 * If Pages contains a month name string (e.g. "01 June"), omit the whole pages
	 */
	public static final Pattern PAGES_MONTH_PATTERN = Pattern
			.compile("\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)");

	/**
	 * Punctuation characters except for closing ')' and ']' (because they may be used as part of a following Pattern)
	 * 
	 * See https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html for substraction of
	 * Unicode character classes.
	 */
	public static final Pattern PARTIAL_ENDING_PUNCTUATION_PATTERN = Pattern.compile("([\\p{P}&&[^)\\]]]+)$");

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
	public static final Pattern POINTY_BRACKETS_PATTERN = Pattern.compile("<[^>]+>+");

	public static final Pattern PUBLICATION_YEAR_PATTERN = Pattern.compile("(^|\\D)(\\d{4})(\\D|$)");

	/**
	 * Both "(R)" and "(TM)", to be removed
	 */
	public static final Pattern REGISTERED_TRADEMARK_PATTERN = Pattern.compile("^(.+)(\\((R|TM)\\))(.+)$");

	/**
	 * The addition "(Reprinted ...)" in titles, to be removed
	 */
	public static final Pattern REPRINTED_ADDITION_PATTERN = Pattern.compile("^(.+)\\(Reprinted .*$");

	/**
	 * The starting "Reprint( of)?:" in titles, to be removed
	 */
	public static final Pattern REPRINTED_START_PATTERN = Pattern.compile("^Reprint( of)?:(.+)$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Ending "(Retracted [Aa]rticle ...", to be removed
	 */
	public static final Pattern RETRACTION_END_PATTERN = Pattern.compile("(.+)\\(Retracted [Aa]rticle.*\\)");

	/**
	 * Starting "(retracted|removed|review|withdrawn)( article)", to be removed
	 */
	public static final Pattern RETRACTION_START_PATTERN = Pattern
			.compile("((retracted|removed|review|withdrawn)( article)?[.:] )(.+)", Pattern.CASE_INSENSITIVE);

	/**
	 * "(" and ")"
	 */
	public static final Pattern ROUND_BRACKETS_PATTERN = Pattern.compile("[\\(\\)]");

	/**
	 * Initial "the|a|an" + SPACE: will be removed
	 * 
	 * See also: JOURNAL_STARTING_ARTICLE_PATTERN: contains more articles, except for "a(n)".
	 */
	public static final Pattern STARTING_ARTICLE_PATTERN = Pattern.compile("^(the|a|an) ");

	public static final Pattern STARTING_NUMBERS_PATTERN = Pattern.compile("^(\\d+)(.+)$");

	public static final Pattern TITLE_AND_SUBTITLE_PATTERN = Pattern.compile("^(.{20,}?)[:.?;] (.{40,})$");

	public static final Pattern TRANSLATION_PATTERN = Pattern.compile("(\\(author's transl\\))$");

	/**
	 * All whitespace characters within input fields. Will be replaced with a normal SPACE.
	 * 
	 * The pattern uses a maximum view:
	 *  - all "Separator, space" characters (class Zs) except for SPACE: https://www.fileformat.info/info/unicode/category/Zs/list.htm
	 *  - all "Separator, Line" (Zl) characters: https://www.fileformat.info/info/unicode/category/Zl/list.htm
	 *  - all "Separator, paragraph" (Zp) characters: https://www.fileformat.info/info/unicode/category/Zp/list.htm
	 *  - some "Other, Control" characters (class Cc), but not all: https://www.fileformat.info/info/unicode/category/Cc/list.htm
	 * 
	 * SPACE is excluded from class Zs for performance reason by making an intersection (&&) with the negation ([^ ]).
	 * See https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html for substraction of
	 * Unicode character classes.
	 * 
	 * LINE SEPARATOR and NO-BREAK SPACE have been observed in the test files.
	 * 
	 * Tested in TextNormalizerTest
	 */
	public static final Pattern UNUSUAL_WHITESPACE_PATTERN = Pattern
			.compile("[\\p{Zs}\\p{Zl}\\p{Zp}\\u0009\\u000A\\u000B\\u000C\\u000D&&[^ ]]");

}
