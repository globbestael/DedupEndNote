package edu.dedupendnote.services.normalization;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JournalsNormalizationService {

	/**
	 * "&": will be replaced by SPACE because an ampersand is problem in some of the following patterns
	 */
	private static final Pattern AMPERSAND_PATTERN = Pattern.compile("&");

	/**
	 * Some subtitles of journals ("Technical report", "Electronic resource", ...): will be removed
	 */
	private static final List<String> EXCLUDED_JOURNALS_PARTS = Arrays.asList("electronic resource", "et al.",
			"technical report");

	/**
	 * "'s" at the end of a word: the apostrophe will be removed. E.g. Langenbeck's / Bailliere's / Crohn's. Must be
	 * called BEFORE the nonGenitiveApostrophePattern
	 */
	private static final Pattern GENITIVE_APOSTROPHE_PATTERN = Pattern.compile("'s\\b");

	/**
	 * "-" or ".": will be replaced by a SPACE
	 */
	private static final Pattern HYPHEN_OR_DOT_PATTERN = Pattern.compile("[\\.-]");

	/**
	 * "Dtsch": will be replaced by "Deutsch"
	 */
	private static final Pattern JOURNAL_ABBREVIATION_DTSCH_PATTERN = Pattern.compile("Dtsch");

	/**
	 * "[(...)G]eneeskd": will be replaced by "[(...)G]eneeskunde"
	 */
	private static final Pattern JOURNAL_ABBREVIATION_GENEESKD_PATTERN = Pattern.compile("eneeskd");

	/**
	 * "heilkd": will be replaced by "heilkunde"
	 */
	private static final Pattern JOURNAL_ABBREVIATION_HEILKD_PATTERN = Pattern.compile("heilkd");

	/**
	 * "Jbr-btr" (case insensitive): will be replaced by "JBR BTR". Cheater!
	 */
	private static final Pattern JOURNAL_ABBREVIATION_JBR_PATTERN = Pattern.compile("Jbr-btr", Pattern.CASE_INSENSITIVE);

	/**
	 * "Jpn": will be replaced by "Japanese"
	 */
	private static final Pattern JOURNAL_ABBREVIATION_JPN_PATTERN = Pattern.compile("Jpn");

	/**
	 * "Kongressbd" will be replaced by "Kongressband"
	 */
	private static final Pattern JOURNAL_ABBREVIATION_KONGRESSBD_PATTERN = Pattern.compile("Kongressbd");

	/**
	 * "Monbl" (case insensitive): will be replaced by "Monatsbl"
	 */
	private static final Pattern JOURNAL_ABBREVIATION_MONATSBLATT_PATTERN = Pattern.compile("Monbl\\b",
			Pattern.CASE_INSENSITIVE);

	/**
	 * "Natl": will be replaced by "National"
	 */
	private static final Pattern JOURNAL_ABBREVIATION_NATL_PATTERN = Pattern.compile("Natl");

	/**
	 * "^(Rofo|Fortschritte .* Gebiet.* R.ntgenstrahlen)" (case insensitive): will be replaced by "Rofo". Cheater!
	 *
	 * Must be used BEFORE the latinOPattern
	 *
	 */
	private static final Pattern JOURNAL_ABBREVIATION_ROFO_PATTERN = Pattern
			.compile("^(Rofo|Fortschritte .* Gebiet.* R.ntgenstrahlen)", Pattern.CASE_INSENSITIVE);

	/**
	 * Initial "Zbl(.?) " (case insensitive): will be replaced by "Zentralblatt"
	 */
	private static final Pattern JOURNAL_ABBREVIATION_ZENTRALBLATT_PATTERN = Pattern.compile("^Zbl[\\. ]",
			Pattern.CASE_INSENSITIVE);

	/**
	 * ":" or "/" and SPACE and all following characters: will be removed. E.g. "BJOG: An International Journal of
	 * Obstetrics and Gynaecology" --> "BJOG"
	 *
	 * The space is wanted to prevent splitting in types as "Hematology/oncology".
	 */
	private static final Pattern JOURNAL_ADDITION_PATTERN = Pattern.compile("[:/]\\s.*$");

	/**
	 * All characters between "(" and ")" at the end of a string, including the round brackets: will be removed
	 */
	private static final Pattern JOURNAL_ENDING_ROUND_BRACKETS_PATTERN = Pattern.compile("\\([^\\)]+\\)$");

	/**
	 * A number or ". Conference" and all following characters: The number and all following characters will be removed.
	 * E.g.:
	 * - "Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date of BibliographicItem: 1989." --> "Clinical neuropharmacology"
	 * - "European Respiratory Journal. Conference: European Respiratory Society Annual Congress" (Cochrane bibliographicItems) --> "European Respiratory Journal"
	 */
	private static final Pattern JOURNAL_EXTRA_PATTERN = Pattern.compile("^(.+?)((\\d+|\\. Conference.*))$");

	/**
	 * "(" and ")": will be replaced by SPACE. See also journalEndingRoundBracketsPattern. Some journals only have "("
	 * without a ")", which causes regex problems
	 */
	private static final Pattern JOURNAL_OTHER_ROUND_BRACKETS_PATTERN = Pattern.compile("[\\)\\(]");

	/**
	 * Initial "(The |Le |La |Les |L'|Der |Die |Das |Il |Het )": will be removed
	 */
	private static final Pattern JOURNAL_STARTING_ARTICLE_PATTERN = Pattern
			.compile("^(The |Le |La |Les |L'|Der |Die |Das |Il |Het )");

	/**
	 * (Suppl|Supplement|Supplementum) and following characters: will be removed
	 */
	private static final Pattern JOURNAL_SUPPLEMENT_PATTERN = Pattern
			.compile("(\\b(Suppl|Supplement|Supplementum)\\b.*)$", Pattern.CASE_INSENSITIVE);

	/**
	 * "o-[NON-WHITE-SPACE]": the hyphen will be removed. E.g. "gastro-enterology" --> "gastroenterology". Must be used
	 * BEFORE the minusOrDotPattern
	 */
	private static final Pattern LATIN_O_PATTERN = Pattern.compile("o\\-(\\S)");

	/**
	 * Two or more white space characters: will be reduced to 1 SPACE
	 */
	private static final Pattern MULTIPLE_WHITE_SPACE_PATTERN = Pattern.compile("\\s{2,}");

	/**
	 * All characters except [a-z] (case insensitive) and [0-9] will be replaced by SPACE
	 */
	private static final Pattern NON_ASCII_PATTERN = Pattern.compile("[^a-z0-9]", Pattern.CASE_INSENSITIVE);

	/**
	 * All other apostrophes (compare genitiveApostrophePattern): will be replaced by SPACE. E.g. "Annales d'Urologie",
	 * "Journal of Xi'an Jiaotong University (Medical Sciences)". Must be called AFTER genitiveApostrophePattern
	 */
	private static final Pattern NON_GENITIVE_APOSTROPHE_PATTERN = Pattern.compile("'");

	// @formatter:off
	/*
		* General:
		* - mark Cochrane bibliographicItem
		* - remove unwanted parts
		* - split combined journal names into in separate journal names
		* - create other variant journal names
		* - for all journal names
		* 		- capitalize
		* 		- normalize
		*/
	// @formatter:on
	public static Set<String> normalizeInputJournals(String journal, String fieldName) {
		if (journal.startsWith("http")) {
			return Set.of(journal);
		}
		// Strip last part of "Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date
		// of BibliographicItem: 1989."
		Matcher matcher = JOURNAL_EXTRA_PATTERN.matcher(journal);
		if (matcher.matches()) {
			journal = matcher.group(1);
		}

		/*
		 * Split the following cases in separate journals:
		 * - ... [...]: Zhonghua wai ke za zhi [Chinese journal of surgery]
		 * - ... / ...: The Canadian Journal of Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques
		 * - ... = ...: Zhen ci yan jiu = Acupuncture research
		 */
		Set<String> journalSet = new HashSet<>();
		String[] parts = null;
		/*
		 * Don't use "." as split character for J2 content because field often has content as "Clin. Med.J. R. Coll. Phys. Lond."
		 */
		if ("J2".equals(fieldName)) {
			parts = journal.split("[\\[\\]]|[=|/]");
		} else {
			parts = journal.split("[\\[\\]]|[=|/]|([.] )");
		}
		journalSet.addAll(Arrays.asList(parts));
		if (parts.length > 1) {
			journalSet.add(journal);
		}
		/*
		 * Journals with a ":" will get 2 variants. e.g
		 * "BJOG: An International Journal of Obstetrics and Gynaecology" or
		 * "Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine"
		 * - one with the colon and all following characters removed ("BJOG" and "Clinical Medicine Insights").
		 *   The removal of these characters does not happen here, but later within normalizeJournalJava8() (journalAdditionPattern)
		 * - one with the ":" replaced with a SPACE
		 *   ("BJOG An International Journal of Obstetrics and Gynaecology" and
		 *    "Clinical Medicine Insights Circulatory, Respiratory and Pulmonary Medicine")
		 */
		Set<String> additional = new HashSet<>();
		for (String j : journalSet) {
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
			matcher = JOURNAL_SUPPLEMENT_PATTERN.matcher(journal);
			if (matcher.find()) {
				log.debug("SupplementMatcher fired for: {}", journal);
				additional.add(matcher.replaceAll(""));
			}
		}

		if (!additional.isEmpty()) {
			journalSet.addAll(additional);
		}
		/*
		 * The EXCLUDED_JOURNALS_PARTS are one of the journal(parts) and will be skipped
		 */
		Set<String> journals = new HashSet<>();
		for (String j : journalSet) {
			j = j.strip();
			if (!j.isEmpty() && !EXCLUDED_JOURNALS_PARTS.contains(j.toLowerCase())) {
				if (j.equals(j.toUpperCase()) && (j.contains(" ") || j.length() > 6)) {
					List<String> words = Arrays.asList(j.toLowerCase().split(" "));
					j = words.stream().map(StringUtils::capitalize).collect(Collectors.joining(" "));
				}
				String normalized = normalizeJournal(j);
				if (!normalized.isEmpty()) {
					journals.add(normalized);
				}
			}
		}
		log.debug("Result for {}: {}", journal, journals);
		return journals;
	}

	public static String normalizeJournal(String s) {
		String r = s;
		r = NormalizationService.normalizeToBasicLatin(r);
		r = AMPERSAND_PATTERN.matcher(r).replaceAll(" "); // we don't want "&" in the patterns
		// irregular abbreviations
		r = JOURNAL_ABBREVIATION_JPN_PATTERN.matcher(r).replaceAll("Japanese");
		r = JOURNAL_ABBREVIATION_DTSCH_PATTERN.matcher(r).replaceAll("Deutsch");
		r = JOURNAL_ABBREVIATION_GENEESKD_PATTERN.matcher(r).replaceAll("eneeskunde");
		r = JOURNAL_ABBREVIATION_HEILKD_PATTERN.matcher(r).replaceAll("heilkunde");
		r = JOURNAL_ABBREVIATION_KONGRESSBD_PATTERN.matcher(r).replaceAll("Kongressband");
		r = JOURNAL_ABBREVIATION_MONATSBLATT_PATTERN.matcher(r).replaceAll("Monatsbl");
		r = JOURNAL_ABBREVIATION_NATL_PATTERN.matcher(r).replaceAll("National");
		r = JOURNAL_ABBREVIATION_ZENTRALBLATT_PATTERN.matcher(r).replaceAll("Zentralbl");
		// Cheating
		r = JOURNAL_ABBREVIATION_JBR_PATTERN.matcher(r).replaceAll("JBR BTR");
		r = JOURNAL_ABBREVIATION_ROFO_PATTERN.matcher(r).replaceAll("Rofo");
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
		// Annales d'Urologie / Journal of Xi'an Jiaotong University (Medical Sciences)
		r = NON_GENITIVE_APOSTROPHE_PATTERN.matcher(r).replaceAll(" ");
		// "Ann Med Interne (Paris)" --> "Ann Med Interne",or "J Med Ultrason (2001)"
		r = JOURNAL_ENDING_ROUND_BRACKETS_PATTERN.matcher(r).replaceAll("");
		// Some journals only have "(" without a ")", which causes regex problems
		r = JOURNAL_OTHER_ROUND_BRACKETS_PATTERN.matcher(r).replaceAll(" ");
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
}
