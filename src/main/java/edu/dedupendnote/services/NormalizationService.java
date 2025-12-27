package edu.dedupendnote.services;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.springframework.util.StringUtils;

import edu.dedupendnote.domain.AuthorRecord;
import edu.dedupendnote.domain.IsbnIssnRecord;
import edu.dedupendnote.domain.NormPatterns;
import edu.dedupendnote.domain.PageRecord;
import edu.dedupendnote.domain.TitleRecord;
import lombok.extern.slf4j.Slf4j;

/*
 * NormalizationService centralizes the normalization of data (read from external file, used in tests).
	public static String normalizeHyphensAndWhitespace(String s) {
 - The normalizeInput... methods and normalizeHyphensAndWhitespace are called from IOService
 - the other normalize... methods are used internally or in tests
 */
@Slf4j
public class NormalizationService {

	/*
	 * In Java 8 replaceAll via PATTERN.matcher(s).replaceAll(replacement) is faster than
	 * s.replaceAll(replacement) See below for Java9Plus versions.
	 */

	// @formatter:off
	/*
	 * normalizeAuthors() and author comparisons
	 *
	 * Possible enhancements:
	 * ----------------------
	 * - Compare only the first ... authors
	 *   See AuthorVariantsExperimentsTest::compareOnlyFirst10AuthorsTest()
	 *
	 * Paths not chosen:
	 * -----------------
	 * - treat all authors longer than ... characters as groups
	 * 	 This would skip "Treatment of Hepatocellular Carcinoma with Tumour, T" which doesn't contain a group-word
	 * 	 With length => 40 some person authors are still found:
	 * 		- Srivastava, K. Das N. Goel PBhatnagar V. [these are multiple authors]
	 * 		- Silveira, Renata Cristina de Campos Pereira	[PsycINFO]
	 * 		- Fantini, Francisca Goreth Malheiro Moraes	[PsycINFO]
	 * 		- Cardoso, Tania Aparecida Marchiori de Oliveira	[PsycINFO]
	 *
	 * Strange cases, maybe solveable:
	 * -------------------------------
	 * - Different numbers of author
	 *   - Yamashita, Y.
	 *   - Yamashita, Y.; Takahashi, M.; Koga, Y.; Saito, R.; Nanakawa, S.; Hatanaka, Y.; Sato, N.; Nakashima, K.; Urata, J.; Yoshizumi, K.; et al.
	 *   Could be solved if the same number of authors were compared. See AuthorVariantsExperimentsTest::compareSameNumberOfAuthorsTest
	 *   But implementing this could be difficult
	 *
	 * Strange cases, not handled yet:
	 * -------------------------------
	 * - CYK names: last and first names are often mixed up
	 *   - "Chung, J. W." and "Jin Wook, Chung"
	 *   - same publication:
	 *     - Chuan-Xing, L.; Xu, H.; Bao-Shan, H.; Yong, L.; Pei-Jian, S.; Xian-Yi, Y.; Xiao-Ning, L.; Li-Gong, L.
	 *     - Li, C. X.; He, X.; Hu, B. S.; Li, Y.; Shao, P. J.; Yu, X. Y.; Luo, X. N.; Lu, L. G.
	 *   - same publication:
	 *     - Chen, Y.; Chen, J.; Luo, B.
	 *     - Yajin, C.; Jisheng, C.; Baoming, L.
	 *   - both orders in same publication:
	 *     - Jia-Wu, Li; Qiang, Lu; Yan, Luo; Li, Jia-Wu; Lu, Qiang; Luo, Yan [CINAHL: same 3 authors with transposed names]
	 * 	   - But this also occurs in CINAHL without transposed authors:
	 *        - Chung, R. T.; Iafrate, A. J.; Amrein, P. C.; Sahani, D. V.; Misdraji, J.; Chung, Raymond T.; Iafrate, A. John; Amrein, Philip C.; Sahani, Dushyant V.; Misdraji, Joseph
	 * - Parts run together:
	 *   - vanSpronsen, F. J.; deLangen, Z. J.; vanElburg, R. M.; Kimpen, J. L. L.
	 *   - 25 cases in BIG_SET, o.a. "DeAngelo, D. J."
	 *   - BUT: McGovern / MacGovern / FitzGerald
	 * - First author left out:
	 *   - Bureau, C.; Laurent, J.; Robic, M. A.; Christol, C.; Guillaume, M.; Ruidavets, J. B.; Ferrieres, J.; Péron, J. M.; Vinel, J. P.
	 *   - Laurent, J.; Robic, M. A.; Christol, C.; Guillaume, M.; Ruidavets, J. B.; Ferrieres, J.; Peron, J. M.; Vinel, J. P.
	 * - De, Marco L [should this be: De Marco, L?]
	 */
	// @formatter:on

	public static AuthorRecord normalizeInputAuthors(String authorInput) {
		String authorResult = null;

		// skip "Anonymous", "et al" and group authors
		Matcher matcher = NormPatterns.ANONYMOUS_OR_GROUPNAME_PATTERN.matcher(authorInput);
		if (matcher.find()) {
			return new AuthorRecord(null, null, false);
		}

		authorInput = normalizeToBasicLatin(authorInput);

		String[] parts = authorInput.split("\\s*,\\s+"); // see testfile Non_Latin_input.txt for " , "
		if (parts.length < 2) {
			log.debug("Author {} cannot be split", authorInput);
			return new AuthorRecord(authorInput, null, false);
		}
		String lastName = parts[0];
		String firstNames = parts[1];

		/*
		 * Normalize lastName: 
		 * - normal capitalization Some databases (a.o. Web of Science) can have complete author names in capitals. EndNote X9 shows them
		 *   capitalized ("Marchioli, R") but the export file has "MARCHIOLI, R"! 
		 * - additions (2nd, Jr, Sr, III) as part of LastName are removed. These additions usually are the 3rd part of the EndNote names 
		 *   (and not used, see above)
		 */
		if (lastName.equals(lastName.toUpperCase())) {
			String[] lastNameParts2 = lastName.toLowerCase().split(" ");
			List<String> lastNameParts = new ArrayList<>(Arrays.asList(lastNameParts2));
			lastName = lastNameParts.stream().map(StringUtils::capitalize).collect(Collectors.joining(" "));
		}
		matcher = NormPatterns.LAST_NAME_ADDITIONS_PATTERN.matcher(lastName);
		if (matcher.find()) {
			lastName = (matcher.group(1).strip() + " " + matcher.group(3).strip()).strip();
			log.debug("new lastName: {}", lastName);
		}

		// @formatter:off
		/*
		 * Possible enhancement: Switch firstNames and lastName for esp. Chinese author names IFF there is a full forName (Wei, Li ==> Li, Wei).
		 * - as for transposed author names: we need a Boolean authorsAreSwitched and a List<String> authorsSwitched for the temporary results.
		 *   fillAuthors() would add these authors IFF the Boolean is set
		 * - should the transposition (see below) also be applied to these switched authors?
		 * - this should be done before the reduction of firstNames to initials
		 *
		 * Both WoS and CINAHL use the "other" form: 10.1007/s11605-013-2150-4
		 * 10.5754/hge12986: PubMed: Yalin, K; WoS: Kong, Y.L. (is this for: Ya Lin?); Scopus: Kong, Y
		 *
		 * From which databases are the first names imported? CINAHL examples found
		 */
		// @formatter:on

		// Reducing the first names to their initials, stripping everything but the initials, and leaving out the comma
		// makes JWS higher for similar names and lower for different names.
		String initials = NormPatterns.EXCEPT_CAPITALS_PATTERN.matcher(firstNames).replaceAll("");
		authorResult = lastName + " " + initials;

		// @formatter:off
		/*
		 *  Transposed author names:
		 *  If the last name contains a space:
		 *  - create a transposed author with all parts of the last name except the last one left out and added as the last initial
		 *  	- Cobos Mateos, J. M. 		==> Mateos JMC
		 *  	- van Boxtel, M. P. J. 		==> Boxtel MPJV
		 *  	- De Brouwer de Boer, A.	==> Boer ADBD
		 *  - set authorsAreTransposed to true
		 *  If the last name does not contain a space:
		 *  - create a transposed author unchanged
		 *  - do NOT set authorsAreTransposed
		 *
		 *  In fillAuthors() there is a check on authorsAreTransposed. Only if set, are the authorsTransposed (joined as 1 String) and added to List<String> allAuthors.
		 *
		 *  Paths not chosen:
		 *  - create all transpositions (e.g. with "De Brouwer de Boer, A."). See AuthorPermutationsExperimentsTest
		 */
		// @formatter:on
		if (lastName.contains(" ")) {
			lastName = lastName.substring(0, 1).toUpperCase() + lastName.substring(1);
			String[] lastNameParts2 = lastName.split("\\s+");
			List<String> lastNameParts = new ArrayList<>(Arrays.asList(lastNameParts2));
			String lastPart = lastNameParts.remove(lastNameParts.size() - 1);
			if (!lastNameParts.isEmpty()) {
				initials += lastNameParts.stream().map(p -> p.substring(0, 1)).collect(Collectors.joining());
				log.debug("Author {} is transposed as {} {}", authorInput, lastPart, initials);
				return new AuthorRecord(authorResult, lastPart + " " + initials, true);
			} else {
				return new AuthorRecord(authorResult, lastName + " " + initials, false);
			}
		} else {
			return new AuthorRecord(authorResult, lastName + " " + initials, false);
		}
	}

	public static Set<String> normalizeInputDois(String doi) {
		Set<String> dois = new HashSet<>();
		// Scopus publications sometimes add Cited references in this field
		if (doi.length() > 200) {
			return dois;
		}
		try {
			doi = URLDecoder.decode(doi, "UTF8");
			doi = StringEscapeUtils.unescapeHtml4(doi);
		} catch (UnsupportedEncodingException e) {
			log.error("DOI {} threw an UnsupportedEncodingException", doi);
			// e.printStackTrace();
		}
		Matcher matcher = NormPatterns.DOI_PATTERN.matcher(doi.toLowerCase());
		while (matcher.find()) {
			String group = matcher.group(1);
			dois.add(group.replaceAll("\\.$", ""));
		}
		return dois;
	}

	// @formatter:off
	/*
	 * ISSNs and ISBNs are treated in the same way: uppercased and hyphens removed
	 *
	 * Crude validation: only lengths 8, 10 and 13 are accepted. 
	 * Invalid results as "X2345678" ("X" on non last position) are accepted.
	 * 
	 * For ISBN-10 the check digit is removed, for ISBN-13 the new prefix and the check digit 
	 *
	 * ISBNs and ISSNs are made unique.  
	 * 
	 * Paths not chosen:
	 * - full validation 
	 * - (ISBN) conversion to ISBN-13 
	 * - (ISSN) use of ISSN-L and the linking table: see
	 * https://www.issn.org/understanding-the-issn/assignment-rules/the-issn-l-for-
	 * publications-on-multiple-media/
	 *
	 * Validation of ISSN and ISBN and conversion to ISBN-13 are possible with the Apache
	 * Commons Validator:
	 * https://mvnrepository.com/artifact/commons-validator/commons-validator/1.7
	 * https://commons.apache.org/proper/commons-validator/apidocs/index.html?org/apache/
	 * commons/validator/routines/ISBNValidator.html
	 *
	 * ISSN-L and the linking table: in theory this should be useful. But a test with some
	 * large data files should prove its extra value.
	 */
	// @formatter:on
	public static IsbnIssnRecord normalizeInputIssns(String issn) {
		Set<String> normalizedIsbns = new HashSet<>();
		Set<String> normalizedIssns = new HashSet<>();
		IsbnIssnRecord result = new IsbnIssnRecord(normalizedIsbns, normalizedIssns);

		Matcher matcher = NormPatterns.ISSN_ISBN_PATTERN.matcher(issn.toUpperCase());
		while (matcher.find()) {
			String group = matcher.group(1).replace("-", "");
			String isbnToAdd = null;
			String issnToAdd = null;
			switch (group.length()) {
			case 8: // real ISSN
				issnToAdd = group;
				break;
			case 10: // ISBN-10
				isbnToAdd = group.substring(0, 9);
				break;
			case 13: // ISBN-13
				isbnToAdd = group.substring(3, 12);
				break;
			default:
				break;
			}
			if (issnToAdd != null && !normalizedIssns.contains(issnToAdd)) {
				normalizedIssns.add(issnToAdd);
			}
			if (isbnToAdd != null && !normalizedIsbns.contains(isbnToAdd)) {
				normalizedIsbns.add(isbnToAdd);
			}
		}
		return result;
	}

	public static Set<String> normalizeInputJournals(String journal, String fieldName) {
		// @formatter:off
		/*
		 * General:
		 * - mark Cochrane publication
		 * - remove unwanted parts
		 * - split combined journal names into in separate journal names
		 * - create other variant journal names
		 * - for all journal names
		 * 		- capitalize
		 * 		- normalize
		 */
		// @formatter:on
		if (journal.startsWith("http")) {
			return Set.of(journal);
		}
		// Strip last part of "Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date
		// of Publication: 1989."
		Matcher matcher = NormPatterns.JOURNAL_EXTRA_PATTERN.matcher(journal);
		if (matcher.matches()) {
			journal = matcher.group(1);
		}

		/*
		 * replace "\S/\S" with space: "Hematology/Oncology" --> "Hematology Oncology"
		 * BUT:
		 * "Chung-Kuo Hsiu Fu Chung Chien Wai Ko Tsa Chih/Chinese Journal of Reparative & Reconstructive Surgery"
		 * will NOT be split into 2 journals! Same for: Arzneimittel-Forschung/Drug Research
		 */
		// matcher = JOURNAL_SLASH_PATTERN.matcher(journal);
		// while (matcher.find()) {
		// journal = matcher.group(1) + " " + matcher.group(2);
		// }

		/*
		 * Split the following cases in separate journals: 
		 * - ... [...]: Zhonghua wai ke za zhi [Chinese journal of surgery] 
		 * - ... / ...: The Canadian Journal of Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques 
		 * - ... = ...: Zhen ci yan jiu = Acupuncture research
		 */
		Set<String> journalSet = new HashSet<>();
		String[] parts = null;
		/*
		 * Don't use "." as split character for J2 content because field oten has content as "Clin. Med.J. R. Coll. Phys. Lond."
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
			// if (JOURNAL_SECTION_MARKERS.matcher(journal).matches()) {
			// additional.add(JOURNAL_SECTION_MARKERS.matcher(journal).replaceAll("Matcher"));
			// }
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
		 * The EXCLUDED_JOURNALS_PARTS are not a pattern. They are one of the journal(parts) and will be skipped
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

	/*
	 * normalizeInputPages: parses the different input strings with page numbers / article numbers to the fields
	 * pageStart, pageEnd and pagesOutput.
	 * 
	 * - IOService::readPublications gathers the fieldContent for these fields in a map
	 * - IOService::readPublications calls this parsing function when the last field (ER) of a publication is encountered
	 *
	 * 3 steps:
	 * - normalize the content of the 3 fields
	 * - choose which of these 3 field values will be used
	 * - handle the chosen field value to get pageStart, pagesOutput and isSevtalPages
	 */
	public static PageRecord normalizeInputPages(Map<String, String> pagesInputMap, String publicationId) {
		String c7Pages = pagesInputMap.get("C7");
		String sePages = pagesInputMap.get("SE");
		String spPages = pagesInputMap.get("SP");
		String pages = null;
		String originalPages = null;
		boolean isSeveralPages = false;
		String pagesOutput = null;
		String pageStart = null;
		String pageEnd = null;

		/*
		 * Step 1: normalize the 3 possible inputs (pagesInputMap)
		 * 
		 * There is already a return in the c7pages branch!
		 */

		if (c7Pages != null) {
			c7Pages = initialPagesCleanup(c7Pages, publicationId);
			originalPages = c7Pages;
			c7Pages = clearPagesIfMonth(c7Pages);
			// Cases like "Pii s1386-6346(02)00029-3"
			if (c7Pages.contains(" ")) {
				c7Pages = null;
			} else {
				// a value like "10007431(2019)02-0126-07" (part of DOI) is changed to a number, which later sets
				// isSeveralPages to true (which is most cases is true?).
				c7Pages = c7Pages.replaceAll("\\D", "");
			}
			if (c7Pages != null && c7Pages.isBlank()) {
				c7Pages = null;
			}
			if (c7Pages == null) {
				if (sePages == null && spPages == null) {
					return new PageRecord(originalPages, null, null, false);
				}
			} else {
				isSeveralPages = true;
				pagesOutput = originalPages;
			}
		}

		if (sePages != null) {
			if (c7Pages != null) {
				log.error("Found a case with both C7 %s and SE %s(publication ID %s)".formatted(pagesInputMap.get("C7"),
						pagesInputMap.get("SE"), publicationId));
			}
			if (sePages.length() > 30) {
				sePages = null;
			} else {
				sePages = initialPagesCleanup(sePages, publicationId);
				originalPages = sePages;
				sePages = clearPagesIfMonth(sePages);
			}
		}

		if (spPages != null) {
			spPages = initialPagesCleanup(spPages, publicationId);
			originalPages = spPages;
			spPages = clearPagesIfMonth(spPages);
			if (spPages != null) {
				if ((spPages.startsWith("e") || spPages.startsWith("E")) && !spPages.contains("-")) {
					isSeveralPages = true;
					pagesOutput = originalPages;
				}
				if (spPages.endsWith("+") || spPages.endsWith("-")) {
					spPages = spPages.substring(0, spPages.length() - 1);
				}
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

		/*
		 * Step 3
		 */
		List<String> pagesParts = Arrays.asList(pages.split("[+,;]\\s*"));
		// Split into (1) group with only Roman numbers, and (2) others (could be Arabic numbers, combined Roman+Arabic
		// ("ii208-212"), combined Arabic+text ("S12-23", "CD123456". "67A-69A", text, ...)
		Map<Boolean, List<String>> resultMap = pagesParts.stream()
				.collect(Collectors.partitioningBy(p -> p.matches("[ivxlcmIVXLCM\\-]+")));

		/*
		 * Only the first of the pagesParts in the resultMap values will be used!
		 * In "A relational approach to rehabilitation: Thinking about relationships after brain injury. xvi, 376"
		 * the second part ("376") will be disregarded. 
		 */
		if (resultMap.get(false).isEmpty()) { // there are no Arabic numbers, possibly Roman numbers
			if (!resultMap.get(true).isEmpty()) {
				String[] parts = resultMap.get(true).removeFirst().split("-");
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
		} else if (!resultMap.get(false).isEmpty()) { // there are Arabic numbers
			String first = resultMap.get(false).removeFirst();
			String[] parts = first.split("-");
			pageStart = parts[0];
			if (parts.length > 1) {
				pageEnd = parts[1];
				pageStart = pageStart.replaceAll("^(0+|N\\.PAG)", "");
				pageEnd = pageEnd.replaceAll("^(0+|N\\.PAG)", "");
				if (pageEnd.isBlank()) {
					pageEnd = null;
				}
			}
			if ("".equals(pageStart)) {
				pageStart = pageEnd;
				if ("".equals(pageStart)) {
					pageStart = null;
				}
				pageEnd = null;
				pagesOutput = composePagesOutput(pageStart, pageEnd, resultMap);
				// pagesOutput = "";
			} else if (pageStart.matches("[Vv]\\d+:\\d+")) {
				pageStart = pageStart.replaceAll("[Vv]\\d+:(\\d)", "$1");
				pageEnd = pageEnd.replaceAll("[Vv]\\d+:(\\d)", "$1");
				pagesOutput = originalPages;
			} else if ((pageEnd != null && pageStart.length() >= pageEnd.length())
					|| (resultMap.get(false).size() + resultMap.get(true).size() > 0)) {
				// The second OR above = there were other pageRanges than the current one
				/*
				 * The test on pagesOutput == null because for C7 field pagesOutput has already been set.
				 */
				pageEnd = getLongPageEnd(pageStart, pageEnd);
				if (pagesOutput == null) {
					// if the whole pages string is the same pageStart - pageEnd, record the long form
					pagesOutput = composePagesOutput(pageStart, pageEnd, resultMap);
				}
			}
		} else {
			return new PageRecord(originalPages, null, null, false);
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
			if (resultMap.get(true).size() + resultMap.get(false).size() > 0) {
				isSeveralPages = true;
			}
		}
		if (pagesOutput == null) {
			pagesOutput = originalPages;
		}
		// A last check
		if (isSeveralPages && (pageStart == null || pageStart.isEmpty())) {
			// log.error("isSeveralPages is set but pageStart is null or empty for publicationId {}", publicationId);
			isSeveralPages = false;
		}
		return new PageRecord(originalPages, pageStart, pagesOutput, isSeveralPages);
	}

	private static String getLongPageEnd(String pageStart, String pageEnd) {
		if (pageEnd != null && pageStart.length() >= pageEnd.length()) {
			pageEnd = pageStart.substring(0, pageStart.length() - pageEnd.length()) + pageEnd;
		}
		return pageEnd;
	}

	private static String clearPagesIfMonth(String pages) {
		if (pages != null) {
			Matcher matcher = NormPatterns.PAGES_MONTH_PATTERN.matcher(pages);
			while (matcher.find()) {
				pages = null;
			}
		}
		return pages;
	}

	private static String initialPagesCleanup(String pages, String publicationId) {
		// Cochrane uses hyphen characters instead of minus
		pages = pages.replaceAll("[\\u2010\\u00ad]", "-");

		pages = NormPatterns.PAGES_ADDITIONS_PATTERN.matcher(pages).replaceAll("").strip();

		// replace "S6-97-s6-99" by "S697-s699"
		pages = NormPatterns.PAGES_HYPHEN_MERGE_1_PATTERN.matcher(pages).replaceAll("$1$2-$3$4");
		// pages = pages.replaceAll("(?<!\\d+)([a-zA-Z0-9]+)-(\\d+)-([a-zA-Z0-9]+)-(\\d+)", "$1$2-$3$4");

		// replace "ii-218-ii-228" by "ii218-ii228", and "S-12" by "S12"
		pages = NormPatterns.PAGES_HYPHEN_MERGE_2_PATTERN.matcher(pages).replaceAll("$1$2");
		// pages = pages.replaceAll("(?<!\\d+)([a-zA-Z]+)-(\\d+)", "$1$2");

		if (pages != null && pages.isBlank()) {
			pages = null;
		}
		return pages;
	}

	private static String composePagesOutput(String pageStart, String pageEnd, Map<Boolean, List<String>> resultMap) {
		List<String> pageRanges = new ArrayList<>();
		// 1. add the Roman ranges first
		pageRanges.addAll(resultMap.get(true));
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
		pageRanges.addAll(resultMap.get(false));

		return pageRanges.stream().collect(Collectors.joining("; "));
	}

	private static String cleanUpPage(String page) {
		if (page != null) {
			page = page.replaceAll("[^\\d]", "");
			page = page.replaceAll("^(0+)", "");
		}
		if ("".equals(page)) {
			page = null;
		}
		return page;
	}

	public static Integer normalizeInputPublicationYear(String input) {
		Integer year = 0;
		Matcher matcher = NormPatterns.PUBLICATION_YEAR_PATTERN.matcher(input);
		if (matcher.find()) {
			year = Integer.valueOf(matcher.group(2));
			if (year < 1850) {
				year = 0;
			}
		} else {
			return 0;
		}
		return year;
	}

	public static TitleRecord normalizeInputTitles(String title) {
		if (NormPatterns.NO_TITLES.contains(title.toLowerCase())) {
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

	public static String normalizeJournal(String s) {
		String r = s;
		r = normalizeToBasicLatin(r);
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

	public static String normalizeTitle(String s) {
		String r = NormPatterns.PARTIAL_ENDING_PUNCTUATION_PATTERN.matcher(s).replaceAll("");
		r = normalizeToBasicLatin(r);
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
		 * FIXME: Do a thorough check in the validation files to make sure that erratum publications do not remove the
		 * original publications (erratum as first publication encountered). There are some tests in
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

	/**
	 * normalizeToBasicLatin: removes accents and diacritics when the base character belongs to the BasicLatin Unicode
	 * block (U+0000–U+007F) and removes all other characters.
	 */
	public static String normalizeToBasicLatin(String s) {
		if (NormPatterns.NON_BASIC_LATIN_PATTERN.matcher(s).find()) {
			s = Normalizer.normalize(s, Normalizer.Form.NFD);
			// you can't reuse the existing matcher because r might be changed
			s = NormPatterns.NON_BASIC_LATIN_PATTERN.matcher(s).replaceAll("");
		}
		return s;
	}

	public static String normalizeHyphensAndWhitespace(String s) {
		// replace HYPHEN with HYPHEN-MINUS
		s = s.replaceAll("\u2010", "\u002D");
		// remove THIN SPACE. Some databases use THIN SPACE within "30 mg", others use no character
		s = s.replaceAll("\u2009", "");
		s = NormPatterns.UNUSUAL_WHITESPACE_PATTERN.matcher(s).replaceAll(" ");
		return s;
	}
}