package edu.dedupendnote.services.normalization;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringEscapeUtils;

import edu.dedupendnote.domain.TitleRecord;

public class TitlesNormalizationService {

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
	private static final Pattern CASE_REPORT_PATTERN = Pattern.compile("^case (reports?|series|[-\\d]+)[.:] ",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Double quote character: will be removed
	 */
	private static final Pattern DOUBLE_QUOTES_PATTERN = Pattern.compile("\"");

	/**
	 * Split main title and subtitle on " -" except for cases as "...virus-positive and -negative patients".
	 *
	 * There are also older records where Greek letters are skipped in database. Example from Embase 2003 article: Real:
	 * Role of κ-opioid receptor activation in pharmacological preconditioning of swine Embase: Role of -opioid receptor
	 * activation in pharmacological preconditioning of swine
	 */
	private static final Pattern HYPHEN_AS_SUBTITLE_DIVIDER_PATTERN = Pattern
			.compile("(.*(?<!( and| of| or|,|\\d)))( -)([ \\p{Alpha}]+)$");

	/**
	 * "-"
	 */
	private static final Pattern HYPHEN_PATTERN = Pattern.compile("\\-");

	/**
	 * Esp. Scopus uses additions as "(Japanese)" (or "(Japanese text)") at the end of the title.
	 *
	 * The pattern is used on the lowercased title. The languages are not complete: based on the 200 most frequent
	 * (sub)titles in the testfiles.
	 */
	private static final Pattern LANGUAGE_PATTERN = Pattern
			.compile("(\\(?(chinese|dutch|french|german|italian|japanese|polish|russian|spanish)( text)?\\)?)$");

	/**
	 * Two or more white space characters: will be reduced to 1 SPACE
	 */
	private static final Pattern MULTIPLE_WHITE_SPACE_PATTERN = Pattern.compile("\\s{2,}");

	/**
	 * Titles which indicate that there is no title. Will be removed
	 */
	private static final List<String> NO_TITLES = List.of("not available", "[not available]", "untitled");

	/**
	 * All characters except [a-z] (lowercase) and [0-9]
	 */
	private static final Pattern NON_ASCII_LOWERCASE_PATTERN = Pattern.compile("[^a-z0-9]");

	/**
	 * All characters between a non-initial "[" and "]", including the square brackets and the preceding character
	 */
	private static final Pattern NON_INITIAL_SQUARE_BRACKETS_PATTERN = Pattern.compile(".\\[[^\\\\]+\\]$");

	/**
	 * Only numbers and hyphens, probably an ID.
	 * Occurs in CINAHL in the ST field. To be skipped as title variant
	 */
	private static final Pattern ONLY_NUMBERS_AND_HYPHENS_PATTERN = Pattern.compile("^[-\\d]+$");

	/**
	 * Punctuation characters except for closing ')' and ']' (because they may be used as part of a following Pattern)
	 *
	 * See https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/regex/Pattern.html for substraction of
	 * Unicode character classes.
	 */
	private static final Pattern PARTIAL_ENDING_PUNCTUATION_PATTERN = Pattern.compile("([\\p{P}&&[^)\\]]]+)$");

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
	 * in the SRA2_Respiratory file with bibliographicItems < 2000. Original database of bibliographicItems unknown.
	 *
	 * Based on these results, the crude pointyBracketsPattern (regex "<[^>]+>") hasn't been changed. The method
	 * normalizeJava8(...) has code (commented out) for comparing the crude and explicit version
	 *
	 * Last ">+" because "<<...>>" also occurs
	 */
	private static final Pattern POINTY_BRACKETS_PATTERN = Pattern.compile("<[^>]+>+");

	/**
	 * Both "(R)" and "(TM)", to be removed
	 */
	private static final Pattern REGISTERED_TRADEMARK_PATTERN = Pattern.compile("^(.+)(\\((R|TM)\\))(.+)$");

	/**
	 * The addition "(Reprinted ...)" in titles, to be removed
	 */
	private static final Pattern REPRINTED_ADDITION_PATTERN = Pattern.compile("^(.+)\\(Reprinted .*$");

	/**
	 * The starting "Reprint( of)?:" in titles, to be removed
	 */
	private static final Pattern REPRINTED_START_PATTERN = Pattern.compile("^Reprint( of)?:(.+)$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Ending "(Retracted [Aa]rticle ...", to be removed
	 */
	private static final Pattern RETRACTION_END_PATTERN = Pattern.compile("(.+)\\(Retracted [Aa]rticle.*\\)");

	/**
	 * Starting "(retracted|removed|review|withdrawn)( article)", to be removed
	 */
	private static final Pattern RETRACTION_START_PATTERN = Pattern
			.compile("((retracted|removed|review|withdrawn)( article)?[.:] )(.+)", Pattern.CASE_INSENSITIVE);

	/**
	 * "(" and ")"
	 */
	private static final Pattern ROUND_BRACKETS_PATTERN = Pattern.compile("[\\(\\)]");

	/**
	 * Initial "the|a|an" + SPACE: will be removed
	 *
	 * See also: JOURNAL_STARTING_ARTICLE_PATTERN: contains more articles, except for "a(n)".
	 */
	private static final Pattern STARTING_ARTICLE_PATTERN = Pattern.compile("^(the|a|an) ");

	private static final Pattern STARTING_NUMBERS_PATTERN = Pattern.compile("^(\\d+)(.+)$");

	private static final Pattern TITLE_AND_SUBTITLE_PATTERN = Pattern.compile("^(.{20,}?)[:.?;] (.{40,})$");

	private static final Pattern TRANSLATION_PATTERN = Pattern.compile("(\\(author's transl\\))$");

	public static TitleRecord normalizeInputTitles(String title) {
		if (NO_TITLES.contains(title.toLowerCase())
				|| ONLY_NUMBERS_AND_HYPHENS_PATTERN.matcher(title).matches()) {
			return new TitleRecord(null, new ArrayList<>());
		}
		title = StringEscapeUtils.unescapeHtml4(title);
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

		if (title.startsWith("Retraction: ")) {
			Matcher balancedBracesMatcher = BALANCED_BRACES_PATTERN.matcher(title);
			if (balancedBracesMatcher.find()) {
				String addition = balancedBracesMatcher.group(0);
				title = title.substring(0, title.length() - addition.length());
				title = title.substring("Retraction: ".length());
			}
		}

		Matcher reprintAdditionMatcher = REPRINTED_ADDITION_PATTERN.matcher(title);
		if (reprintAdditionMatcher.matches()) {
			title = reprintAdditionMatcher.group(1);
		}

		Matcher reprintStartMatcher = REPRINTED_START_PATTERN.matcher(title);
		if (reprintStartMatcher.matches()) {
			title = reprintStartMatcher.group(2);
		}

		Matcher registeredtrademarkMatcher = REGISTERED_TRADEMARK_PATTERN.matcher(title);
		while (registeredtrademarkMatcher.find()) {
			title = registeredtrademarkMatcher.group(1) + " " + registeredtrademarkMatcher.group(4);
		}

		if (title.startsWith("Editorial: ")) {
			title = title.substring("Editorial: ".length());
		}
		if (title.startsWith("Editorial on ")) {
			title = title.substring("Editorial on ".length());
		}
		// Replace "--" and " -" with the normal splitter for main title - subtitle (": ")
		title = title.replaceAll("--", ": ");
		Matcher hyphenAsSubtitleDividerMatchermatcher = HYPHEN_AS_SUBTITLE_DIVIDER_PATTERN.matcher(title);
		if (hyphenAsSubtitleDividerMatchermatcher.matches()) {
			// log.error("\n- orig: {}\n- G1: {}\n- G2: {}\n- G3: {}\n- G4: {}", title,
			// hyphenAsSubtitleDividerMatchermatcher.group(1), hyphenAsSubtitleDividerMatchermatcher.group(2),
			// hyphenAsSubtitleDividerMatchermatcher.group(3), hyphenAsSubtitleDividerMatchermatcher.group(4));
			title = hyphenAsSubtitleDividerMatchermatcher.group(1) + ": "
					+ hyphenAsSubtitleDividerMatchermatcher.group(4);
			;
		}

		List<String> normalizedTitles = addTitleWithNormalization(title);

		Matcher startingNumbMatcher = STARTING_NUMBERS_PATTERN.matcher(title);
		if (startingNumbMatcher.matches()) {
			title = startingNumbMatcher.group(2);
			normalizedTitles.addAll(addTitleWithNormalization(title));
		}

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
		String normalized = normalizeTitle(title);
		String[] parts = normalized.split("=");
		List<String> list = new ArrayList<>(Arrays.asList(parts));
		List<String> titles = new ArrayList<>();

		for (String t : list) {
			if (!t.isBlank() && !titles.contains(t.strip())) {
				titles.add(normalized);
			}
		}
		return titles;
	}

	public static String normalizeTitle(String s) {
		String r = PARTIAL_ENDING_PUNCTUATION_PATTERN.matcher(s).replaceAll("");
		r = NormalizationService.normalizeToBasicLatin(r);
		r = DOUBLE_QUOTES_PATTERN.matcher(r).replaceAll("");
		/*
		 * Assume "<<...>>" is not an addition, but a variant of double quotes. This replacement before the pointyBracketsPattern replacement.
		 * Skipped because later nonAsciiLowercasePattern will replace the pointy brackets with a space.
		 */
		// r = r.replaceAll("(<<|>>)", "");
		/**
		 * FIXME: Do a thorough check of retractions (including "WITHDRAWN: ..." Cochrane reviews). Cochrane: PubMed,
		 * Medline and EMBASE use format "WITHDRAWN: ...", Web of Science the format "... (Withdrawn Paper, 2011, Art.
		 * No. CD001727)". See also "Retraction note to: ..." (e.g. https://pubmed.ncbi.nlm.nih.gov/24577730/)
		 */
		/**
		 * FIXME: Do a thorough check in the validation files to make sure that erratum bibliographicItems do not remove the
		 * original bibliographicItems (erratum as first bibliographicItem encountered). There are some tests in
		 * {@link edu.dedupendnote.JaroWinklerTitleTest} (and an incomplete method
		 * {@link edu.dedupendnote.JaroWinklerTitleTest#testErrata()})
		 */
		r = r.toLowerCase();
		r = LANGUAGE_PATTERN.matcher(r).replaceAll("");
		r = TRANSLATION_PATTERN.matcher(r).replaceAll("");
		r = CASE_REPORT_PATTERN.matcher(r).replaceAll("");
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
		// r = r.replaceAll(" ", "");
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
}
