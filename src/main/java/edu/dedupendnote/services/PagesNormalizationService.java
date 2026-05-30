package edu.dedupendnote.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import edu.dedupendnote.domain.NormPatterns;
import edu.dedupendnote.domain.PageRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PagesNormalizationService {

	public static PageRecord normalizeInputPages(Map<String, String> pagesInputMap, int bibliographicItemId) {
		String rawC7 = pagesInputMap.get("C7");
		String rawSe = pagesInputMap.get("SE");
		String rawSp = pagesInputMap.get("SP");
		String c7Pages = null;
		String sePages = null;
		String spPages = null;
		String pages = null;
		String originalPages = null;
		boolean isSeveralPages = false;
		String pagesOutput = null;

		/*
		 * Step 1: normalize the 3 possible inputs (pagesInputMap).
		 * Each field is cleaned by its own private method; semantic decisions
		 * (early return, isSeveralPages, pagesOutput) are resolved here.
		 */

		if (rawC7 != null) {
			NormalizedField c7Norm = normalizeC7Field(rawC7);
			c7Pages = c7Norm.value();
			originalPages = c7Norm.originalPages();
			if (c7Pages == null) {
				if (rawSe == null && rawSp == null) {
					return new PageRecord(originalPages, null, null, false);
				}
			} else {
				isSeveralPages = true;
				pagesOutput = originalPages;
			}
		}

		if (rawSe != null) {
			NormalizedField seNorm = normalizeSeField(rawSe, c7Pages, bibliographicItemId);
			if (seNorm != null) {
				sePages = seNorm.value();
				originalPages = seNorm.originalPages();
			}
		}

		if (rawSp != null) {
			NormalizedField spNorm = normalizeSpField(rawSp);
			spPages = spNorm.value();
			originalPages = spNorm.originalPages();
			if (spNorm.isSeveralPages()) {
				isSeveralPages = true;
				pagesOutput = originalPages;
			}
		}

		/*
		 * Step 2: choose the pages and pagesOutput which will be used later for the comparison
		 */
		if (c7Pages != null) {
			isSeveralPages = true;
			if (spPages != null
					&& (spPages.startsWith(c7Pages) || (spPages.contains("-") && !spPages.startsWith("1-")))) {
				pages = spPages;
				pagesOutput = spPages;
			} else {
				pages = c7Pages;
			}
		} else if (spPages != null && !spPages.isBlank()) {
			pages = spPages;
		} else if (sePages != null) {
			pages = sePages;
		} else {
			return new PageRecord(originalPages, null, null, false);
		}

		return parsePageRange(pages, originalPages, pagesOutput, isSeveralPages);
	}

	private static PageRecord parsePageRange(String pages, @Nullable String originalPages,
			@Nullable String pagesOutput, boolean isSeveralPages) {
		List<String> pagesParts = Arrays.asList(pages.split("[+,;]\\s*"));
		// Split into (1) group with only Roman numbers, and (2) others (could be Arabic numbers, combined Roman+Arabic
		// ("ii208-212"), combined Arabic+text ("S12-23", "CD123456". "67A-69A", text, ...)
		Map<Boolean, List<String>> resultMap = pagesParts.stream()
				.collect(Collectors.partitioningBy(p -> p.matches("[ivxlcmIVXLCM\\-]+")));
		/*
		 * Working with "resultMap.get(false).[function]()" and NullAway caused a lot of "derefenced expression ...is @Nullable" errors.
		 * Splitting resultMap in 2 seperate Lists solved these errors.
		 * Is this the problem mentioned in the NullAway wiki at https://github.com/uber/NullAway/wiki/Maps?
		 *
		 * BTW: using these List romanPages and arabicPages makes the code a lot cleaner.
		 */
		List<String> romanPages = (List<String>) resultMap.getOrDefault(Boolean.TRUE, List.of());
		List<String> arabicPages = (List<String>) resultMap.getOrDefault(Boolean.FALSE, List.of());
		/*
		 * FIXME: The following example is a bad example: replace!
		 * Only the first of the pagesParts in the resultMap values will be used!
		 * In "A relational approach to rehabilitation: Thinking about relationships after brain injury. xvi, 376"
		 * the second part ("376") will be disregarded.
		 */
		String pageStart = null;
		String pageEnd = null;
		if (arabicPages.isEmpty()) { // there are no Arabic numbers, possibly Roman numbers
			if (!romanPages.isEmpty()) {
				String[] parts = romanPages.removeFirst().split("-");
				if (parts.length > 0) {
					try {
						pageStart = String.valueOf(UtilitiesService.romanToArabic(parts[0]));
						if (parts.length > 1) {
							pageEnd = String.valueOf(UtilitiesService.romanToArabic(parts[1]));
						}
					} catch (java.lang.IllegalArgumentException e) {
						pageStart = null;
						pageEnd = null;
						pagesOutput = originalPages;
					}
				}
			}
		} else { // there are Arabic numbers
			String first = arabicPages.removeFirst();
			String[] parts = first.split("-");
			if (parts.length > 0) {
				pageStart = parts[0];
				if (parts.length > 1) {
					pageEnd = parts[1];
					pageStart = pageStart.replaceAll("^(0+|N\\.PAG)", "");
					pageEnd = pageEnd.replaceAll("^(0+|N\\.PAG)", "");
					if (pageEnd.isBlank()) {
						pageEnd = null;
					}
				}
				if (pageStart.isBlank()) {
					pageStart = pageEnd;
					if (pageStart != null && pageStart.isBlank()) {
						pageStart = null;
					}
					pageEnd = null;
					pagesOutput = composePagesOutput(pageStart, pageEnd, romanPages, arabicPages);
					// pagesOutput = "";
				} else if (pageStart.matches("[Vv]\\d+:\\d+")) {
					pageStart = pageStart.replaceAll("[Vv]\\d+:(\\d)", "$1");
					if (pageEnd != null) {
						pageEnd = pageEnd.replaceAll("[Vv]\\d+:(\\d)", "$1");
					}
					pagesOutput = originalPages;
				} else if ((pageEnd != null && pageStart.length() >= pageEnd.length())
						|| (!arabicPages.isEmpty() || !romanPages.isEmpty())) {
					// The second OR above = there were other pageRanges than the current one
					/*
					* The test on pagesOutput == null because for C7 field pagesOutput has already been set.
					*/
					pageEnd = getLongPageEnd(pageStart, pageEnd);
					if (pagesOutput == null) {
						// if the whole pages string is the same pageStart - pageEnd, record the long form
						pagesOutput = composePagesOutput(pageStart, pageEnd, romanPages, arabicPages);
					}
				}
			}
		}
		pageStart = cleanUpPage(pageStart);
		pageEnd = cleanUpPage(pageEnd);
		Integer pageStartInt = null;
		Integer pageEndInt = null;
		if (pageStart != null) {
			try {
				pageStartInt = Integer.valueOf(pageStart);
				pageStart = pageStartInt.toString();
			} catch (NumberFormatException e) {
				// log.error("- pageStart {} is NOT an integer", pageStart);
			}
		}
		if (pageEnd != null) {
			try {
				pageEndInt = Integer.valueOf(pageEnd);
				pageEnd = pageEndInt.toString();
				if (pageStart == null) {
					pageStart = pageEnd;
					pageEnd = null;
				}
			} catch (NumberFormatException e) {
				// log.error("- pageEnd {} is NOT an integer", pageEnd);
			}
		}
		if (pagesOutput == null && (pageStart == null && pageEnd == null)) {
			pagesOutput = "";
		}
		if (pageStartInt != null && pageEndInt != null) {
			if (isSeveralPages == false) {
				isSeveralPages = pageEndInt - pageStartInt > 1;
			}
			if ((pageStartInt == 1 && pageEndInt >= 100)) {
				pageStart = pageEnd;
				pageEnd = null;
				pageStartInt = pageEndInt;
				pageEndInt = null;
			}
		}
		if (pageStartInt != null && pageStartInt == 0) {
			if (pageEndInt == null) {
				pageStart = null;
				pageStartInt = null;
			} else {
				pageStart = pageEnd;
				pageEnd = null;
				pageStartInt = pageEndInt;
				pageEndInt = null;
			}
		}
		if (isSeveralPages == false) {
			// The first part of one of them was removed. If there were more pages of the same kind or at least one of the other kind
			if (!arabicPages.isEmpty() || !romanPages.isEmpty()) {
				isSeveralPages = true;
			}
		}
		if (pagesOutput == null) {
			pagesOutput = originalPages;
		}
		// A last check
		if (isSeveralPages && (pageStart == null || pageStart.isEmpty())) {
			// log.error("isSeveralPages is set but pageStart is null or empty for bibliographicItemId {}", bibliographicItemId);
			isSeveralPages = false;
		}
		return new PageRecord(originalPages, pageStart, pagesOutput, isSeveralPages);
	}

	private record NormalizedField(@Nullable String value, @Nullable String originalPages, boolean isSeveralPages) {
	}

	private static NormalizedField normalizeC7Field(String rawC7) {
		String cleaned = initialPagesCleanup(rawC7);
		String original = cleaned;
		cleaned = clearPagesIfMonth(cleaned);
		// Cases like "Pii s1386-6346(02)00029-3"
		if (cleaned != null) {
			if (cleaned.contains(" ")) {
				cleaned = null;
			} else {
				// a value like "10007431(2019)02-0126-07" (part of DOI) is changed to a number, which later sets
				// isSeveralPages to true (which is most cases is true?).
				cleaned = cleaned.replaceAll("\\D", "");
			}
		}
		if (cleaned != null && cleaned.isBlank()) {
			cleaned = null;
		}
		return new NormalizedField(cleaned, original, false);
	}

	// Returns null when rawSe is too long (> 30 chars) — originalPages is not updated in that case.
	private static @Nullable NormalizedField normalizeSeField(String rawSe, @Nullable String c7Pages, int id) {
		if (c7Pages != null) {
			log.error("Found a case with both C7 and SE for bibliographicItem ID {}", id);
		}
		if (rawSe.length() > 30) {
			return null;
		}
		String cleaned = initialPagesCleanup(rawSe);
		String original = cleaned;
		cleaned = clearPagesIfMonth(cleaned);
		return new NormalizedField(cleaned, original, false);
	}

	private static NormalizedField normalizeSpField(String rawSp) {
		String cleaned = initialPagesCleanup(rawSp);
		String original = cleaned;
		cleaned = clearPagesIfMonth(cleaned);
		boolean isEPage = false;
		if (cleaned != null) {
			if ((cleaned.startsWith("e") || cleaned.startsWith("E")) && !cleaned.contains("-")) {
				isEPage = true;
			}
			if (cleaned.endsWith("+") || cleaned.endsWith("-")) {
				cleaned = cleaned.substring(0, cleaned.length() - 1);
			}
		}
		return new NormalizedField(cleaned, original, isEPage);
	}

	private static @Nullable String getLongPageEnd(String pageStart, @Nullable String pageEnd) {
		if (pageEnd != null && pageStart.length() >= pageEnd.length()) {
			pageEnd = pageStart.substring(0, pageStart.length() - pageEnd.length()) + pageEnd;
		}
		return pageEnd;
	}

	private static @Nullable String clearPagesIfMonth(@Nullable String pages) {
		if (pages != null) {
			Matcher matcher = NormPatterns.PAGES_MONTH_PATTERN.matcher(pages);
			while (matcher.find()) {
				pages = null;
			}
		}
		return pages;
	}

	private static @Nullable String initialPagesCleanup(String pages) {
		// Cochrane uses hyphen characters instead of minus
		pages = pages.replaceAll("[\\u2010\\u00ad]", "-");

		pages = NormPatterns.PAGES_ADDITIONS_PATTERN.matcher(pages).replaceAll("").strip();

		// replace "S6-97-s6-99" by "S697-s699"
		pages = NormPatterns.PAGES_HYPHEN_MERGE_1_PATTERN.matcher(pages).replaceAll("$1$2-$3$4");

		// replace "ii-218-ii-228" by "ii218-ii228", and "S-12" by "S12"
		pages = NormPatterns.PAGES_HYPHEN_MERGE_2_PATTERN.matcher(pages).replaceAll("$1$2");

		if (pages != null && pages.isBlank()) {
			pages = null;
		}
		return pages;
	}

	private static String composePagesOutput(@Nullable String pageStart, @Nullable String pageEnd,
			List<String> romanPages, List<String> arabicPages) {
		List<String> pageRanges = new ArrayList<>();
		// 1. add the Roman ranges first
		pageRanges.addAll(romanPages);
		// 2. add the first arabic pageStart (and pageEnd)
		if (pageEnd == null) {
			if (pageStart == null) {
				pageRanges.add("");
			} else {
				pageRanges.add(pageStart);
			}
		} else {
			pageRanges.add(pageStart + "-" + pageEnd);
		}
		// 3. add the other Arabic pages
		pageRanges.addAll(arabicPages);

		return pageRanges.stream().collect(Collectors.joining("; "));
	}

	private static @Nullable String cleanUpPage(@Nullable String page) {
		if (page != null) {
			page = page.replaceAll("[^\\d]", "");
			page = page.replaceAll("^(0+)", "");
			if ("".equals(page)) {
				page = null;
			}
		}
		return page;
	}
}
