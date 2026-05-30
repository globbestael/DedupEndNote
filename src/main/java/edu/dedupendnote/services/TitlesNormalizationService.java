package edu.dedupendnote.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.commons.text.StringEscapeUtils;

import edu.dedupendnote.domain.NormPatterns;
import edu.dedupendnote.domain.TitleRecord;

public class TitlesNormalizationService {

	public static TitleRecord normalizeInputTitles(String title) {
		if (NormPatterns.NO_TITLES.contains(title.toLowerCase())
				|| NormPatterns.ONLY_NUMBERS_AND_HYPHENS_PATTERN.matcher(title).matches()) {
			return new TitleRecord(null, new ArrayList<>());
		}
		title = StringEscapeUtils.unescapeHtml4(title);
		String cachedTitle = title;
		String originalTitle = null;
		Matcher endMatcher = NormPatterns.RETRACTION_END_PATTERN.matcher(title);
		if (endMatcher.matches()) {
			originalTitle = cachedTitle;
			title = endMatcher.group(1);
		}
		Matcher startMatcher = NormPatterns.RETRACTION_START_PATTERN.matcher(title);
		if (startMatcher.matches()) {
			originalTitle = cachedTitle;
			title = startMatcher.group(4);
		}

		if (title.startsWith("Retraction: ")) {
			Matcher balancedBracesMatcher = NormPatterns.BALANCED_BRACES_PATTERN.matcher(title);
			if (balancedBracesMatcher.find()) {
				String addition = balancedBracesMatcher.group(0);
				title = title.substring(0, title.length() - addition.length());
				title = title.substring("Retraction: ".length());
			}
		}

		Matcher reprintAdditionMatcher = NormPatterns.REPRINTED_ADDITION_PATTERN.matcher(title);
		if (reprintAdditionMatcher.matches()) {
			title = reprintAdditionMatcher.group(1);
		}

		Matcher reprintStartMatcher = NormPatterns.REPRINTED_START_PATTERN.matcher(title);
		if (reprintStartMatcher.matches()) {
			title = reprintStartMatcher.group(2);
		}

		Matcher registeredtrademarkMatcher = NormPatterns.REGISTERED_TRADEMARK_PATTERN.matcher(title);
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
		Matcher hyphenAsSubtitleDividerMatchermatcher = NormPatterns.HYPHEN_AS_SUBTITLE_DIVIDER_PATTERN.matcher(title);
		if (hyphenAsSubtitleDividerMatchermatcher.matches()) {
			// log.error("\n- orig: {}\n- G1: {}\n- G2: {}\n- G3: {}\n- G4: {}", title,
			// hyphenAsSubtitleDividerMatchermatcher.group(1), hyphenAsSubtitleDividerMatchermatcher.group(2),
			// hyphenAsSubtitleDividerMatchermatcher.group(3), hyphenAsSubtitleDividerMatchermatcher.group(4));
			title = hyphenAsSubtitleDividerMatchermatcher.group(1) + ": "
					+ hyphenAsSubtitleDividerMatchermatcher.group(4);
			;
		}

		List<String> normalizedTitles = addTitleWithNormalization(title);

		Matcher startingNumbMatcher = NormPatterns.STARTING_NUMBERS_PATTERN.matcher(title);
		if (startingNumbMatcher.matches()) {
			title = startingNumbMatcher.group(2);
			normalizedTitles.addAll(addTitleWithNormalization(title));
		}

		boolean splittable = true;
		String secondPart = title;

		while (splittable) {
			Matcher matcher = NormPatterns.TITLE_AND_SUBTITLE_PATTERN.matcher(secondPart);
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
		String r = NormPatterns.PARTIAL_ENDING_PUNCTUATION_PATTERN.matcher(s).replaceAll("");
		r = NormalizationService.normalizeToBasicLatin(r);
		r = NormPatterns.DOUBLE_QUOTES_PATTERN.matcher(r).replaceAll("");
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
		r = NormPatterns.LANGUAGE_PATTERN.matcher(r).replaceAll("");
		r = NormPatterns.TRANSLATION_PATTERN.matcher(r).replaceAll("");
		r = NormPatterns.CASE_REPORT_PATTERN.matcher(r).replaceAll("");
		r = NormPatterns.NON_INITIAL_SQUARE_BRACKETS_PATTERN.matcher(r).replaceAll("");
		r = NormPatterns.POINTY_BRACKETS_PATTERN.matcher(r).replaceAll("");
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
		r = NormPatterns.ROUND_BRACKETS_PATTERN.matcher(r).replaceAll("");
		r = NormPatterns.HYPHEN_PATTERN.matcher(r).replaceAll("");
		r = NormPatterns.NON_ASCII_LOWERCASE_PATTERN.matcher(r).replaceAll(" ");
		r = r.strip();
		r = NormPatterns.MULTIPLE_WHITE_SPACE_PATTERN.matcher(r).replaceAll(" ");
		r = NormPatterns.STARTING_ARTICLE_PATTERN.matcher(r).replaceAll("");
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
