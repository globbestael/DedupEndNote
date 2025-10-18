package edu.dedupendnote.services;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.Publication;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ComparisonService {

	public static final Double JOURNAL_SIMILARITY_NO_REPLY = 0.90;
	public static final Double JOURNAL_SIMILARITY_REPLY = 0.93;
	public static final Double TITLE_SIMILARITY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.94;
	public static final Double TITLE_SIMILARITY_PHASE = 0.96;
	public static final Double TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.89;

	public static boolean compareSameDois(Publication r1, Publication r2, boolean sameDois) {
		if (sameDois) {
			if (log.isTraceEnabled()) {
				log.trace("- 4. DOIs are the same (ISSNs and Journals are NOT compared)");
			}
			return true;
		}
		if (log.isTraceEnabled()) {
			log.trace("- 4. DOIs are NOT the same: {} and {}", r1.getDois(), r2.getDois());
		}
		return false;
	}

	/*
	 * Compares the ISBNs or the ISSNs of 2 publications 
	 */
	public static boolean compareIssns(Publication r1, Publication r2) {
		if (!r1.getIsbns().isEmpty() && !r2.getIsbns().isEmpty()) {
			if (setsContainSameString(r1.getIsbns(), r2.getIsbns())) {
				log.trace("- 4. ISBNs are the same");
				return true;
			} else {
				if (log.isTraceEnabled()) {
					log.trace("- 4. ISBNs are NOT the same: {} and {}", r1.getIsbns(), r2.getIsbns());
				}
				return false;
			}
		}
		if (setsContainSameString(r1.getIssns(), r2.getIssns())) {
			log.trace("- 4. ISSNs are the same");
			return true;
		} else {
			if (log.isTraceEnabled()) {
				log.trace("- 4. ISSNs are NOT the same: {} and {}", r1.getIssns(), r2.getIssns());
			}
			return false;
		}
	}

	public static boolean compareJournals(Publication r1, Publication r2) {
		if (!r1.getIsbns().isEmpty() && !r2.getIsbns().isEmpty()) {
			return false;
		}
		Set<String> set1 = r1.getJournals();
		Set<String> set2 = r2.getJournals();
		boolean isReply = r1.isReply() || r2.isReply();

		if (set1.isEmpty() || set2.isEmpty()) {
			log.trace("- 4. At least 1 of the publications has no journal");
			return false;
		}

		Set<String> commonJournals = new HashSet<>(set1);
		commonJournals.retainAll(set2);
		if (!commonJournals.isEmpty()) {
			log.trace("- 4. Some journals are the same");
			return true;
		}

		JaroWinklerSimilarity jws = new JaroWinklerSimilarity();
		for (String s1 : set1) {
			for (String s2 : set2) {
				if (s1.startsWith("http") && s2.startsWith("http") && !s1.equals(s2)) {
					continue;
				}
				Double similarity = jws.apply(s1.toLowerCase(), s2.toLowerCase());
				if (isReply && similarity > JOURNAL_SIMILARITY_REPLY) {
					log.trace("- 4. Journal similarity above treshold (reply)");
					return true;
				}
				if (!isReply && similarity > JOURNAL_SIMILARITY_NO_REPLY) {
					log.trace("- 4. Journal similarity ({}) above treshold (not reply)", similarity);
					return true;
				}

				if (s1.toLowerCase().charAt(0) != s2.toLowerCase().charAt(0)) {
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
				if (s1.length() < 10 && s1.toUpperCase().equals(s1) && compareJournals_FirstAsInitialism(s1, s2)) {
					log.trace("- 4. compareJournals_FirstAsInitialism(1,2) is true");
					return true;
				}
				if (s2.length() < 10 && s2.toUpperCase().equals(s2) && compareJournals_FirstAsInitialism(s2, s1)) {
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

	private static boolean compareJournals_FirstAsAbbreviation(String j1, String j2) {
		Pattern pattern = Pattern.compile("\\b" + j1.replaceAll("\\s", ".*\\\\b") + ".*", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(j2);
		if (matcher.find()) {
			return true;
		}
		return false;
	}

	private static boolean compareJournals_FirstAsInitialism(String s1, String s2) {
		String patternString = s1.chars().mapToObj(c -> String.valueOf((char) c))
				.collect(Collectors.joining(".*\\b", "\\b", ".*"));
		Pattern patternShort2 = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
		Matcher matcher = patternShort2.matcher(s2);
		if (matcher.find()) {
			return true;
		}
		return false;
	}

	private static boolean compareJournals_FirstWithStartingInitialism(String s1, String s2) {
		String[] words = s1.split("\\s");
		if ("Samj".equals(words[0])) {
			words[0] = "SAMJ";
		}
		if (words[0].length() > 2 && words[0].equals(words[0].toUpperCase())
				|| words.length == 1 && words[0].length() < 6) {
			if ("AJNR".equals(words[0])) {
				words[0] = "AJN";
			}
			String patternString = words[0].chars().mapToObj(c -> String.valueOf((char) c))
					.collect(Collectors.joining(".*\\b", "\\b", ".*"));
			Pattern patternShort3 = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
			Matcher matcher = patternShort3.matcher(s2);
			if (matcher.find()) {
				return true;
			}
		}
		return false;
	}

	public static boolean compareStartPageOrDoi(Publication r1, Publication r2, Map<String, Boolean> map) {
		Set<String> dois1 = r1.getDois();
		Set<String> dois2 = r2.getDois();
		boolean bothCochrane = r1.isCochrane() && r2.isCochrane();
		boolean sufficientStartPages = r1.getPageForComparison() != null && r2.getPageForComparison() != null;
		boolean sufficientDois = !dois1.isEmpty() && !dois2.isEmpty();
		boolean atLeastOneSeveralPages = r1.isSeveralPages() || r2.isSeveralPages();

		if (sufficientDois && setsContainSameString(dois1, dois2)) {
			map.put("sameDois", true);
		}

		if (bothCochrane) {
			if (r1.getPublicationYear().equals(r2.getPublicationYear())) {
				if (sufficientDois) {
					if (setsContainSameString(dois1, dois2)) {
						log.trace("- 1. DOIs are the same for Cochrane");
						return true;
					} else {
						log.trace("- 1. DOIs are NOT the same for Cochrane");
						return false;
					}
				} else if (sufficientStartPages && r1.getPageForComparison().equals(r2.getPageForComparison())) {
					log.trace("- 1. Starting pages are the same for Cochrane");
					return true;
				}
			}
			log.trace("- 1. NOT the same startPage or DOI for Cochrane");
			return false;
		}

		if (!sufficientStartPages && !sufficientDois) {
			log.trace("- 1. At least one starting page AND at least one DOI are missing, therefore Same");
			return true;
		}
		if (atLeastOneSeveralPages) {
			if (sufficientDois) {
				if (setsContainSameString(dois1, dois2)) {
					log.trace("- 1. DOIs are the same for severalPages");
					return true;
				}
			}
			if (sufficientStartPages && r1.getPageForComparison().equals(r2.getPageForComparison())) {
				log.trace("- 1. Starting pages are the same for severalPages");
				return true;
			}
			log.trace("- 1. NOT the same startPage or DOI for severalPages");
			return false;
		}

		if (sufficientStartPages) {
			if (r1.getPageForComparison().equals(r2.getPageForComparison())) {
				log.trace("- 1. Starting pages are the same");
				return true;
			} else {
				log.trace("- 1. Starting pages are NOT the same");
				return false;
			}
		}

		if (setsContainSameString(dois1, dois2)) {
			log.trace("- 1. DOIs are the same");
			return true;
		}
		log.trace("- 1. DOIs and starting pages are NOT the same");
		return false;
	}

	public static boolean compareTitles(Publication r1, Publication r2) {
		if (r1.isReply() || r2.isReply()) {
			return true;
		}
		if (r1.isClinicalTrialGov() && r2.isClinicalTrialGov()) {
			log.trace("- 3. Both publications are from ClinicalTrials.gov");
			return true;
		}

		Double similarity = 0.0;
		List<String> titles1 = r1.getTitles();
		List<String> titles2 = r2.getTitles();
		boolean sufficientStartPages = r1.getPageForComparison() != null && r2.getPageForComparison() != null;
		boolean sufficientDois = !r1.getDois().isEmpty() && !r2.getDois().isEmpty();
		boolean isPhase = r1.isPhase() || r2.isPhase();

		if (titles1.isEmpty() || titles2.isEmpty()) {
			log.trace("- 3. No comparison of titles because no titles for at least one publication");
			return true;
		}
		Double highestSimilarity = 0.0;
		String highestTitle1 = "";
		String highestTitle2 = "";
		JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

		for (String title1 : titles1) {
			for (String title2 : titles2) {
				similarity = jws.apply(title1, title2);
				if (log.isTraceEnabled() && similarity > highestSimilarity) {
					highestSimilarity = similarity;
					highestTitle1 = title1;
					highestTitle2 = title2;
				}

				if (isPhase) {
					if (similarity > TITLE_SIMILARITY_PHASE) {
						log.trace("- 3. Title similarity (for Phase) is above threshold");
						return true;
					}
				} else {
					if (sufficientStartPages || sufficientDois) {
						if (similarity > TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS) {
							if (log.isTraceEnabled()) {
								log.trace(
										"- 3. Title similarity (with pages or DOIs) is above threshold: '{}' and '{}'",
										title1, title2);
							}
							return true;
						}
					}
					if (!(sufficientStartPages || sufficientDois)) {
						if (similarity > TITLE_SIMILARITY_INSUFFICIENT_STARTPAGES_AND_DOIS) {
							if (log.isTraceEnabled()) {
								log.trace(
										"- 3. Title similarity (without sufficient pages or DOIs) is above threshold: '{}' and '{}'",
										title1, title2);
							}
							return true;
						}
					}
				}
			}
		}
		if (log.isTraceEnabled()) {
			log.trace("- 3. Title similarity {} is below threshold: '{}' and '{}'], subtype {}", highestSimilarity,
					highestTitle1, highestTitle2,
					(isPhase ? "Phase"
							: (sufficientStartPages || sufficientDois) ? "sufficient startPages or DOIs"
									: "not sufficient startPages or DOIs"));
		}
		return false;
	}

	private static boolean setsContainSameString(Set<String> set1, Set<String> set2) {
		if (set1.isEmpty() || set2.isEmpty()) {
			return false;
		}
		Set<String> common = new HashSet<>(set1);
		common.retainAll(set2);
		return !common.isEmpty();
	}
}