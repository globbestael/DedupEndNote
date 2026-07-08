package edu.dedupendnote.services.comparison;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.jspecify.annotations.Nullable;

import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.services.UtilitiesService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultJournalComparisonService implements JournalComparisonService {

	private static final JaroWinklerSimilarity JWS = new JaroWinklerSimilarity();
	public static final Map<String, Pattern> ABBREVIATION_CACHE = new ConcurrentHashMap<>();
	public static final Map<String, Pattern> INITIALISM_CACHE = new ConcurrentHashMap<>();
	public static final Map<String, Pattern> STARTING_INITIALISM_CACHE = new ConcurrentHashMap<>();

	private final JournalThresholds thresholds;

	public DefaultJournalComparisonService() {
		this(JournalThresholds.DEFAULT);
	}

	public DefaultJournalComparisonService(JournalThresholds thresholds) {
		this.thresholds = thresholds;
	}

	@Override
	public boolean compare(BibliographicItem r1, BibliographicItem r2, @Nullable Boolean isSameDois) {
		if (!r1.getIsbns().isEmpty() && !r2.getIsbns().isEmpty()) {
			return false;
		}
		Set<String> set1 = r1.getJournals();
		Set<String> set2 = r2.getJournals();
		boolean isReply = r1.isReply() || r2.isReply();

		if (set1.isEmpty() || set2.isEmpty()) {
			log.trace("- 4. At least 1 of the bibliographicItems has no journal");
			return false;
		}

		Set<String> commonJournals = new HashSet<>(set1);
		commonJournals.retainAll(set2);
		if (!commonJournals.isEmpty()) {
			log.trace("- 4. Some journals are the same");
			return true;
		}

		for (String s1 : set1) {
			for (String s2 : set2) {
				if (s1.startsWith("http") && s2.startsWith("http") && !s1.equals(s2)) {
					continue;
				}
				Double similarity = JWS.apply(s1.toLowerCase(Locale.ROOT), s2.toLowerCase(Locale.ROOT));
				if (isReply && similarity > thresholds.reply()) {
					log.trace("- 4. Journal similarity above treshold (reply)");
					return true;
				}
				if (!isReply && similarity > thresholds.noReply()) {
					log.trace("- 4. Journal similarity ({}) above treshold (not reply)", similarity);
					return true;
				}
				/*
				    Claude analysis of Jspecify and NullAway has a minor issue with these charAt() calls. However the Set<String> journals
				    cannot contain null Strings.
				 */
				if (s1.toLowerCase(Locale.ROOT).charAt(0) != s2.toLowerCase(Locale.ROOT).charAt(0)) {
					continue;
				}

				if (compareJournals_FirstAsAbbreviation(s1, s2)) {
					log.trace("- 4. compareJournals_FirstAsAbbreviation(1,2) is true");
					return true;
				}
				if (compareJournals_FirstAsAbbreviation(s2, s1)) {
					log.trace("- 4. compareJournals_FirstAsAbbreviation(2,2) is true");
					return true;
				}
				if (s1.length() < 10 && s1.toUpperCase(Locale.ROOT).equals(s1)
						&& compareJournals_FirstAsInitialism(s1, s2)) {
					log.trace("- 4. compareJournals_FirstAsInitialism(1,2) is true");
					return true;
				}
				if (s2.length() < 10 && s2.toUpperCase(Locale.ROOT).equals(s2)
						&& compareJournals_FirstAsInitialism(s2, s1)) {
					log.trace("- 4. compareJournals_FirstAsInitialism(2,1) is true");
					return true;
				}
				if (compareJournals_FirstWithStartingInitialism(s1, s2)) {
					log.trace("- 4. compareJournals_FirstWithStartingInitialism(1,2) is true");
					return true;
				}
				if (compareJournals_FirstWithStartingInitialism(s2, s1)) {
					log.trace("- 4. compareJournals_FirstWithStartingInitialism(2,1) is true");
					return true;
				}
			}
		}
		if (log.isTraceEnabled()) {
			log.trace("- 4. Journals are NOT the same: {} and {}", r1.getJournals(), r2.getJournals());
		}
		return false;
	}

	/*
		Title: Br J Surg					pattern: \bBr.*\bJ.*\bSurg.*
		Title: JAMA							pattern: \bJAMA.*
		Title: Japanese J Clin Oncol		pattern: \bJapanese.*\bJ.*\bClin.*\bOncol.*	
	 */
	private static boolean compareJournals_FirstAsAbbreviation(String s1, String s2) {
		Pattern patternShort1 = ABBREVIATION_CACHE.computeIfAbsent(s1, k -> {
			String patternString = "\\b" + k.replaceAll("\\s", ".*\\\\b") + ".*";
			return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
		});
		Matcher matcher = patternShort1.matcher(s2);
		// log.error("The Cache ABBREVIATION_CACHE {}", ABBREVIATION_CACHE);
		return matcher.find();
	}

	/*
		Title: JAMA 		pattern: \bJ.*\bA.*\bM.*\bA.*
		Title: BMJ 			pattern: \bB.*\bM.*\bJ.*
	 */
	private static boolean compareJournals_FirstAsInitialism(String s1, String s2) {
		Pattern patternShort2 = INITIALISM_CACHE.computeIfAbsent(s1, k -> {
			String patternString = k.chars().mapToObj(c -> String.valueOf((char) c))
					.collect(Collectors.joining(".*\\b", "\\b", ".*"));
			return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
		});
		Matcher matcher = patternShort2.matcher(s2);
		// log.error("The Cache INITIALISM_CACHE {}", INITIALISM_CACHE);
		return matcher.find();
	}

	/*
		Title: AJR Am J Roentgenol					pattern: \bA.*\bJ.*\bR.*
		Title: AJNR Am J Neuroradiol				pattern: \bA.*\bJ.*\bN.*
		Title: BBA Clinical							pattern: \bB.*\bB.*\bA.*
		Title: Cmaj									pattern: \bC.*\bm.*\ba.*\bj.*	
		Title: QJM									pattern: \bQ.*\bJ.*\bM.*
	 */
	private static boolean compareJournals_FirstWithStartingInitialism(String s1, String s2) {
		String[] words = s1.split("\\s");
		if ("Samj".equals(words[0])) {
			words[0] = "SAMJ";
		}
		// words[0] may not be a number! "2016 Conference ..."
		if (words[0].length() > 2 && words[0].equals(words[0].toUpperCase(Locale.ROOT)) && words[0].matches("^\\D+$")
				|| words.length == 1 && words[0].length() < 6) {
			if ("AJNR".equals(words[0])) {
				words[0] = "AJN";
			}
			Pattern patternShort3 = STARTING_INITIALISM_CACHE.computeIfAbsent(words[0], k -> {
				String patternString = k.chars().mapToObj(c -> String.valueOf((char) c))
						.collect(Collectors.joining(".*\\b", "\\b", ".*"));
				return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
			});
			Matcher matcher = patternShort3.matcher(s2);
			// log.error("The Cache STARTING_INITIALISM_CACHE {}", STARTING_INITIALISM_CACHE);
			//			log.error("For s1 '{}' and s2 '{}' with pattern {}", s1, s2, patternShort3);
			return matcher.find();
		}
		return false;
	}

	public static boolean compareIssns(BibliographicItem r1, BibliographicItem r2, @Nullable Boolean isSameDois) {
		if (!r1.getIsbns().isEmpty() && !r2.getIsbns().isEmpty()) {
			if (UtilitiesService.setsContainSameString(r1.getIsbns(), r2.getIsbns())) {
				log.trace("- 4. ISBNs are the same");
				return true;
			} else {
				if (log.isTraceEnabled()) {
					log.trace("- 4. ISBNs are NOT the same: {} and {}", r1.getIsbns(), r2.getIsbns());
				}
				return false;
			}
		}
		if (UtilitiesService.setsContainSameString(r1.getIssns(), r2.getIssns())) {
			log.trace("- 4. ISSNs are the same");
			return true;
		} else {
			if (log.isTraceEnabled()) {
				log.trace("- 4. ISSNs are NOT the same: {} and {}", r1.getIssns(), r2.getIssns());
			}
			return false;
		}
	}
}
