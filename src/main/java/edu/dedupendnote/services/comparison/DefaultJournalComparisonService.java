package edu.dedupendnote.services.comparison;

import java.util.Arrays;
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
	/*
		Terminology:
		- Initialism: The journal name variant with all capitals which are the initials of all important the words of the full journal name
		  e.g.: : "JAMA" for "Journal of the American Medical Assocation"
		- Abbreviation: other journal name variants with short forms of the important words of tyhe full journal name
		  e.g. "Br J Surg", "J Am J Assoc"
	 */
	private static final JaroWinklerSimilarity JWS = new JaroWinklerSimilarity();
	public static final Map<String, Pattern> ABBREVIATION_CACHE = new ConcurrentHashMap<>();
	public static final Map<String, Pattern> INITIALISM_CACHE = new ConcurrentHashMap<>();
	public static final Map<String, Pattern> STARTING_INITIALISM_CACHE = new ConcurrentHashMap<>();

	private static final int MINIMUM_LENGTH_INITIALISM = 2;
	private static final int MAXIMUM_LENGTH_INITIALISM = 6;
	private static final int MAXIMUM_NUMBER_WORDS_JOURNAL_PATTERN = 10;

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
				if (s1.length() >= MINIMUM_LENGTH_INITIALISM && s1.length() <= MAXIMUM_LENGTH_INITIALISM
						&& s1.toUpperCase(Locale.ROOT).equals(s1) && compareJournals_FirstAsInitialism(s1, s2)) {
					log.trace("- 4. compareJournals_FirstAsInitialism(1,2) is true");
					return true;
				}
				if (s2.length() >= MINIMUM_LENGTH_INITIALISM && s2.length() <= MAXIMUM_LENGTH_INITIALISM
						&& s2.toUpperCase(Locale.ROOT).equals(s2) && compareJournals_FirstAsInitialism(s2, s1)) {
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
	
		Limited to the first MAXIMUM_NUMBER_WORDS_JOURNAL_PATTERN words (=10). The limit is to prevent pathologic cases. From a OWASP 10 security review:
		"... the k code-inserted ".*\b" groups over an attacker-chosen multi-token target give
	 	 polynomial backtracking (quadratic-to-worse in target length, growing with
	 	 token count k, which the attacker controls via T3/journal content)"
	 */
	private static boolean compareJournals_FirstAsAbbreviation(String s1, String s2) {
		Pattern patternShort1 = ABBREVIATION_CACHE.computeIfAbsent(s1, k -> {
			String[] words = k.split("\\s");
			String patternString = Arrays.asList(words).stream().limit(MAXIMUM_NUMBER_WORDS_JOURNAL_PATTERN)
					.collect(Collectors.joining(".*\\b", "\\b", ".*"));
			// log.error("For '{}' pattern '{}'", k, patternString);
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
		String w1 = words[0];
		if ("Samj".equals(w1)) {
			w1 = "SAMJ";
		} else if ("AJNR".equals(w1)) {
			w1 = "AJN";
		}
		// words[0] may not be a number! "2016 Conference ..."
		// The key in the cache should be the journal name (input s1), but the computation if absent should be on the first word
		if (w1.length() >= MINIMUM_LENGTH_INITIALISM && w1.length() <= MAXIMUM_LENGTH_INITIALISM
				&& w1.equals(w1.toUpperCase(Locale.ROOT)) && w1.matches("^\\D+$")
				|| words.length == 1 && w1.length() <= MAXIMUM_LENGTH_INITIALISM) {
			Pattern patternShort3 = STARTING_INITIALISM_CACHE.get(s1);
			if (patternShort3 == null) {
				String patternString = w1.chars().mapToObj(c -> String.valueOf((char) c))
						.collect(Collectors.joining(".*\\b", "\\b", ".*"));
				patternShort3 = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
				STARTING_INITIALISM_CACHE.put(s1, patternShort3);
			}

			if (patternShort3 != null) {
				Matcher matcher = patternShort3.matcher(s2);
				// log.error("The Cache STARTING_INITIALISM_CACHE {}", STARTING_INITIALISM_CACHE);
				// log.error("For s1 '{}' and s2 '{}' with pattern {}", s1, s2, patternShort3);
				return matcher.find();
			}
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
