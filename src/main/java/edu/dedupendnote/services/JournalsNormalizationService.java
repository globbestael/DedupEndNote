package edu.dedupendnote.services;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import edu.dedupendnote.domain.NormPatterns;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JournalsNormalizationService {

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
		Matcher matcher = NormPatterns.JOURNAL_EXTRA_PATTERN.matcher(journal);
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
			matcher = NormPatterns.JOURNAL_SUPPLEMENT_PATTERN.matcher(journal);
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
			if (!j.isEmpty() && !NormPatterns.EXCLUDED_JOURNALS_PARTS.contains(j.toLowerCase())) {
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
		r = NormPatterns.AMPERSAND_PATTERN.matcher(r).replaceAll(" "); // we don't want "&" in the patterns
		// irregular abbreviations
		r = NormPatterns.JOURNAL_ABBREVIATION_JPN_PATTERN.matcher(r).replaceAll("Japanese");
		r = NormPatterns.JOURNAL_ABBREVIATION_DTSCH_PATTERN.matcher(r).replaceAll("Deutsch");
		r = NormPatterns.JOURNAL_ABBREVIATION_GENEESKD_PATTERN.matcher(r).replaceAll("eneeskunde");
		r = NormPatterns.JOURNAL_ABBREVIATION_HEILKD_PATTERN.matcher(r).replaceAll("heilkunde");
		r = NormPatterns.JOURNAL_ABBREVIATION_KONGRESSBD_PATTERN.matcher(r).replaceAll("Kongressband");
		r = NormPatterns.JOURNAL_ABBREVIATION_MONATSBLATT_PATTERN.matcher(r).replaceAll("Monatsbl");
		r = NormPatterns.JOURNAL_ABBREVIATION_NATL_PATTERN.matcher(r).replaceAll("National");
		r = NormPatterns.JOURNAL_ABBREVIATION_ZENTRALBLATT_PATTERN.matcher(r).replaceAll("Zentralbl");
		// Cheating
		r = NormPatterns.JOURNAL_ABBREVIATION_JBR_PATTERN.matcher(r).replaceAll("JBR BTR");
		r = NormPatterns.JOURNAL_ABBREVIATION_ROFO_PATTERN.matcher(r).replaceAll("Rofo");
		// Java 8-version
		if (NormPatterns.LATIN_O_PATTERN.matcher(r).find()) { // "Gastro-Enterology" -> "Gastroenterology"
			StringBuilder sb = new StringBuilder();
			int last = 0;
			Matcher latinOMatcher = NormPatterns.LATIN_O_PATTERN.matcher(r);
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
		r = NormPatterns.HYPHEN_OR_DOT_PATTERN.matcher(r).replaceAll(" ");
		r = NormPatterns.JOURNAL_STARTING_ARTICLE_PATTERN.matcher(r).replaceAll(""); // article at start
		r = NormPatterns.GENITIVE_APOSTROPHE_PATTERN.matcher(r).replaceAll("s"); // Langenbeck's / Bailliere's / Crohn's
		// Annales d'Urologie / Journal of Xi'an Jiaotong University (Medical Sciences)
		r = NormPatterns.NON_GENITIVE_APOSTROPHE_PATTERN.matcher(r).replaceAll(" ");
		// "Ann Med Interne (Paris)" --> "Ann Med Interne",or "J Med Ultrason (2001)"
		r = NormPatterns.JOURNAL_ENDING_ROUND_BRACKETS_PATTERN.matcher(r).replaceAll("");
		// Some journals only have "(" without a ")", which causes regex problems
		r = NormPatterns.JOURNAL_OTHER_ROUND_BRACKETS_PATTERN.matcher(r).replaceAll(" ");
		if (r.toLowerCase().startsWith("http")) {
			// Cochrane library CENTRAL has journal name of type: https://clinicaltrials.gov/show/nct00969397
			r = r.toLowerCase();
		} else {
			r = NormPatterns.JOURNAL_ADDITION_PATTERN.matcher(r).replaceAll("");
			// "BJOG: An Journal of Obstetrics and Gynaecology" --> "BJOG"
			r = NormPatterns.NON_ASCII_PATTERN.matcher(r).replaceAll(" ");
		}
		r = NormPatterns.MULTIPLE_WHITE_SPACE_PATTERN.matcher(r).replaceAll(" ");
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
