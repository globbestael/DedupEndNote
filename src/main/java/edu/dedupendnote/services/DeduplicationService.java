package edu.dedupendnote.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.StompMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// @Service
// @SessionScope
public class DeduplicationService {

	public static class DefaultAuthorsComparator implements AuthorsComparator {

		public static final Double AUTHOR_SIMILARITY_NO_REPLY = 0.67;

		public static final Double AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.80;

		public static final Double AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.75;

		private static JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

		private Double similarity = 0.0;

		/*
		 * See AuthorVariantsExperimentsTest for possible enhancements.
		 */
		@Override
		public boolean compare(Publication r1, Publication r2) {
			similarity = 0.0;
			boolean isReply = r1.isReply() || r2.isReply();
			boolean sufficientStartPages = r1.getPageForComparison() != null && r2.getPageForComparison() != null;
			boolean sufficientDois = !r1.getDois().isEmpty() && !r2.getDois().isEmpty();

			if (r1.getAllAuthors().isEmpty() || r2.getAllAuthors().isEmpty()) {
				// Because Anonymous AND Reply would only compare on journals (and maybe
				// SP/DOIs) (see "MedGenMed Medscape General Medicine" articles in
				// Cannabis test set)
				// Because Anonymous AND no SP or DOI would only compare on title and
				// journals (see "Abstracts of 16th National Congress of SIGENP" articles
				// in Joost problem set)
				if (isReply || (!sufficientStartPages && !sufficientDois)) {
					return false;
				}
				return true;
			}

			for (String authors1 : r1.getAllAuthors()) {
				for (String authors2 : r2.getAllAuthors()) {
					similarity = jws.apply(authors1, authors2);
					if (isReply) {
						// TODO: do we have examples of this case?
						if (!(sufficientStartPages || sufficientDois)
								&& similarity > AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS) {
							return true;
						}
						if ((sufficientStartPages || sufficientDois)
								&& similarity > AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS) {
							return true;
						}
					} else if (similarity > AUTHOR_SIMILARITY_NO_REPLY) {
						return true;
					}
				}
			}
			return false;
		}

		@Override
		public Double getSimilarity() {
			return similarity;
		}

	}

	/*
	 * Thresholds for Jaro-Winkler Similarity The thresholds for AUTHOR are part of
	 * the AuthorComparator implementations
	 */
	public static final Double JOURNAL_SIMILARITY_NO_REPLY = 0.90;

	public static final Double JOURNAL_SIMILARITY_REPLY = 0.93;

	public static final Double TITLE_SIMILARITY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.94;

	public static final Double TITLE_SIMILARITY_PHASE = 0.96;

	public static final Double TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.90;

	protected AuthorsComparator authorsComparator;

	// the DOIs have been lowercased
	private static Pattern cochraneIdentifierPattern = Pattern.compile("^.*10.1002/14651858.([a-z][a-z]\\d+).*",
			Pattern.CASE_INSENSITIVE);

	private IOService ioService;

	private JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	// @formatter:off
	/*
	 * Procedure for 1 file:
	 *
	 * 1. Do preliminary checks on the input file (EndNote IDs are present and unique) and exit when the checks do not pass.
	 * 2. Read all EndNote records from inputfile and make Publication objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    (All records start with kepRecord = true (default))
	 * 3. Deduplicate the records.
	 *    When duplicates are found:
	 *    - put the id (EndNote ID field) of the first duplicate into the label (EndNote LB field) of all the duplicates
	 *    - set kepRecord = false for all but the first duplicate
	 *  4. Enrich the first duplicate with data from the other duplicates (DOI, starting page)
	 *  5. Read the input file again, extract the ID and get the corresponding record.
	 *     If the record is keptRecord = true,
	 *     copy the original content of the fields of this record from the input file to the output file
	 *     except for the fields where content is standardized (DOI) or where content is enriched from the duplicates.
	 *
	 *   If markMode is set, records are not enriched and ALL records are written back.
	 *   If a record is a duplicate, the Label field (LB) contains the ID of the first duplicate found.
	 *
	 *
	 * Procedure for 2 files:
	 *
	 * 1. Do preliminary checks on input file for old records (EndNote IDs are present and unique) and exit when the checks do not pass.
	 * 2. Read all EndNote records from this inputfile and make Publication objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    Alter the ID by prefixing them with "-" (to distinguish them from the IDs of the second file and making them unique over both files).
	 *    All OLD records start with kepRecord = false and presentInOldFile = true.
	 * 3. Do preliminary checks on input file for new records (EndNote IDs are present and unique) and exit when the checks do not pass.
	 * 4. Read all EndNote records from this inputfile and make Publication objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    All NEW records start with kepRecord = true (default)  and presentInOldFile = false (default).
	 * 5. Deduplicate the records.
	 *    When duplicates are found:
	 *    - put the id (EndNote ID field) of the first duplicate into the label (EndNote LB field) of all the duplicates
	 *    - set kepRecord = false for all but the first duplicate
	 * 6. Enrich the first duplicate with data from the other duplicates (DOI, starting page)
	 *    only if the label exists and does not start with "-", i.e. is NOT a duplicate from an OLD record.
	 * 7. Read the NEW input file again, extract the ID and get the corresponding record.
	 *    If the record is keptRecord = true,
	 *    copy the original content of the fields of this record from the input file to the output file
	 *    except for the fields where content is standardized (DOI) or where content is enriched from the duplicates.
	 *
	 *  If markMode is set, records are not enriched and ALL records of the NEW inputfile are written back.
	 *  If a record is a duplicate, the Label field (LB) contains the ID of the first duplicate found.
	 *  If the label starts with "-", it is a duplicate from a record from the OLD input file.
	 */
	// @formatter:on
	// @Autowired
	private SimpMessagingTemplate simpMessagingTemplate;

	public DeduplicationService() {
		this.authorsComparator = new DefaultAuthorsComparator();
		this.ioService = new IOService();
	}

	public DeduplicationService(SimpMessagingTemplate simpMessagingTemplate) {
		this.authorsComparator = new DefaultAuthorsComparator();
		this.ioService = new IOService();
		this.simpMessagingTemplate = simpMessagingTemplate;
	}

	public DeduplicationService(AuthorsComparator authorsComparator) {
		this.authorsComparator = authorsComparator;
		this.ioService = new IOService();
	}

	public boolean compareIssns(Publication r1, Publication r2) {
		return setsContainSameString(r1.getIssns(), r2.getIssns());
	}

	// @formatter:off
	/**
	 * compareJournals
	 *
	 * Paths not chosen:
	 * - Creation of sets of journalPatterns for the whole set of records. This might be overkill, since the comparison by journal is the last
	 *   of all comparisons between any two records
	 * - "AJR American Journal of Radiology": split into "AJR" and "American Journal of Radiology"
	 *   compareJournals_FirstWithStartingInitialism(...) will create a pattern on A, J en R which will find the second title.
	 */
	// @formatter:on
	public boolean compareJournals(Publication r1, Publication r2) {
		Set<String> set1 = r1.getJournals();
		Set<String> set2 = r2.getJournals();
		boolean isReply = r1.isReply() || r2.isReply();

		if (set1.isEmpty() || set2.isEmpty()) {
			// log.debug("One of the records had no journal for '{}' and '{}'", set1, set2);
			return false;
		}

		Set<String> commonJournals = new HashSet<>(set1);
		commonJournals.retainAll(set2);
		if (!commonJournals.isEmpty()) {
			// log.debug("Some journals are common: '{}' and '{}'", set1, set2);
			return true;
		}

		for (String s1 : set1) {
			for (String s2 : set2) {
				if (s1.startsWith("http") && s2.startsWith("http") && !s1.equals(s2)) {
					// JaroWinkler 0.9670930232558139 for
					// 'Https://clinicaltrials.gov/show/nct00830466'
					// and 'Https://clinicaltrials.gov/show/nct00667472'
					continue;
				}
				Double similarity = jws.apply(s1.toLowerCase(), s2.toLowerCase());
				// log.debug("Similarity: {} for '{}' and '{}'", similarity, s1, s2);
				// Make comparison stricter for Reply-titles because titles haven't been
				// compared ("Annals of Hepatology" - "Annals of Oncology": 0.916)
				if (isReply && similarity > JOURNAL_SIMILARITY_REPLY) {
					return true;
				}
				if (!isReply && similarity > JOURNAL_SIMILARITY_NO_REPLY) {
					return true;
				}

				if (s1.toLowerCase().charAt(0) != s2.toLowerCase().charAt(0)) {
					continue;
				}
				/*
				 * "Hepatology" and "Hepatology International" are considered the same
				 */
				// FIXME: the 3 following functions should first compare s1 - s2 and, if false, then s2 - s1
				if (compareJournals_FirstAsAbbreviation(s1, s2)) {
					return true;
				}
				if (compareJournals_FirstAsAbbreviation(s2, s1)) {
					return true;
				}
				/*
				 * Journals with very long titles produce very long Patterns: limit these cases
				 * to short journal names. ASySD SRS_Human has journal names in uppercase
				 */
				if (s1.length() < 10 && s1.toUpperCase().equals(s1) && compareJournals_FirstAsInitialism(s1, s2)) {
					return true;
				}
				if (s2.length() < 10 && s2.toUpperCase().equals(s2) && compareJournals_FirstAsInitialism(s2, s1)) {
					return true;
				}

				if (compareJournals_FirstWithStartingInitialism(s1, s2)) {
					return true;
				}
				if (compareJournals_FirstWithStartingInitialism(s2, s1)) {
					return true;
				}
			}
		}
		// log.debug("No Success 5 for '{}' and '{}'", set1, set2);
		return false;
	}

	// Searching 'Ann Fr Anesth Reanim' as "\bAnn.*\bFr.*\bAnesth.*\bReanim.*"
	private boolean compareJournals_FirstAsAbbreviation(String j1, String j2) {
		Pattern pattern = Pattern.compile("\\b" + j1.replaceAll("\\s", ".*\\\\b") + ".*", Pattern.CASE_INSENSITIVE);
		// log.debug("Pattern ABBREVIATION for '{}': {} for '{}'", j1, pattern.toString(), j2);
		Matcher matcher = pattern.matcher(j2);
		if (matcher.find()) {
			// log.debug("Pattern ABBREVIATION found for '{}': {} for '{}'", j1, pattern.toString(), j2);
			return true;
		}
		return false;
	}

	// Searching 'BMJ' as "\bB.*\bM.*\bJ.*"
	private boolean compareJournals_FirstAsInitialism(String s1, String s2) {
		String patternString = s1.chars().mapToObj(c -> String.valueOf((char) c))
				.collect(Collectors.joining(".*\\b", "\\b", ".*"));
		Pattern patternShort2 = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
		// log.debug("Pattern INITIALISM for '{}': {} for '{}'", s1, patternShort2.toString(), s2);
		Matcher matcher = patternShort2.matcher(s2);
		if (matcher.find()) {
			// log.debug("Pattern INITIALISM found for '{}': {} for '{}'", s1, patternShort2.toString(), s2);
			return true;
		}
		return false;
	}

	// Searching 'AJR Am J Roentgenol' as "\bA.*\bJ.*\bR.*"
	// Searching 'JNCCN Journal of the National Comprehensive Cancer Network' as
	// "\bJ.*\bN.*\bC.*\bC.*\bN.*"
	// Searching 'Bmj' as "\bB.*\bm.*\bj.*"
	private boolean compareJournals_FirstWithStartingInitialism(String s1, String s2) {
		String[] words = s1.split("\\s");
		if ("Samj".equals(words[0])) {
			words[0] = "SAMJ";
		}
		if (words[0].length() > 2 && words[0].equals(words[0].toUpperCase())
				|| words.length == 1 && words[0].length() < 6) {
			// 20220502: Because the pattern uses only word breaks "\b" and not
			// alternation "(\b|)", we have to adjust for at least (AJNR <=> American
			// journal of neuroradiology)
			if ("AJNR".equals(words[0])) { // AJNR = American Journal of Neuroradiology!
				words[0] = "AJN";
			}
			String patternString = words[0].chars().mapToObj(c -> String.valueOf((char) c))
					.collect(Collectors.joining(".*\\b", "\\b", ".*"));
			Pattern patternShort3 = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
			// log.debug("Pattern STARTING_INITIALISM for '{}': {} for '{}'", s1, patternShort3.toString(), s2);
			Matcher matcher = patternShort3.matcher(s2);
			if (matcher.find()) {
				// log.debug("Pattern STARTING_INITIALISM found for '{}': {} for '{}'", s1, patternShort3.toString(),
				// s2);
				return true;
			}
		}
		return false;
	}

	public void compareSet(List<Publication> publications, Integer year, boolean descending, String wssessionId) {
		int noOfPublications = publications.size();
		int noOfDuplicates = 0;
		Map<String, Boolean> map = new HashMap<>(Map.of("sameDois", false));

		while (publications.size() > 1) {
			// In log messages this publication is called the pivot
			Publication publication = publications.remove(0);
			/*
			 * If descending / OneFile mode: only records of year1 should be compared to records of year1 and year2.
			 * The records of year2 will be compared in the next pair of years.
			 * If ascending / TwoFile mode: publicationYear 0 records are at the head of the publicationList!
			 */
			if ((descending && publication.getPublicationYear() < year) || (!descending
					&& publication.getPublicationYear() != 0 && publication.getPublicationYear() > year)) {
				break;
			}
			// log.debug("Comparing {} publications to pivot {}: {}", publications.size(), publication.getId(),
			// publication.getTitles().get(0));

			for (Publication r : publications) {
				map.put("sameDois", false);
				if (compareStartPageOrDoi(r, publication, map) && authorsComparator.compare(r, publication)
						&& compareTitles(r, publication)
						&& (map.get("sameDois") || compareIssns(r, publication) || compareJournals(r, publication))) {

					noOfDuplicates++;
					// set the label
					if (publication.getLabel() != null) {
						// log.debug("=== pub {} gets label {} from pivot {}", r.getId(), publication.getLabel(),
						// publication.getId());
						r.setLabel(publication.getLabel());
					} else if (r.getLabel() != null) {
						/** 
						 * Labels can be promoted from a publication to the pivot without a label:
						 * - in loop N with pivot V 
						 *   - publication W is NOT seen as similar and gets no label
						 *   - publication X is seen as similar and gets V as label
						 * - in loop N + 1 with pivot W
						 *   - publication X is seen as similar and its label V is promoted to label of pivot W
						 * 
						 *   	V		W			X			
						 * SP	1-26	291-316		(None)		
						 * DOI	+		+			+
						 * 
						 * Loop N (V)
						 * - W.SP != V.SP	 -> W.label = NULL	
						 * - X.DOI = V.DOI	 -> X.label = V
						 * Loop N + 1 (W)
						 * - W.DOi = X.DOI	 -> W.lavel = X
						 * 
						 * Another reason can be that pivot W has more journal name variants than pivot V 
						 */
						// log.debug("=== pub {} SETs label {} in pivot {}", r.getId(), r.getLabel(),
						// publication.getId());
						publication.setLabel(r.getLabel());
					} else {
						// log.debug("=== Both pivot {} and pub {} get label {} from the recordId of the pivot {}",
						// publication.getId(), r.getId(), publication.getId(), publication.getId());
						publication.setLabel(publication.getId());
						r.setLabel(publication.getId());
					}

					if (r.isReply()) {
						publication.setReply(true);
					}
				}
			}
			wsMessage(wssessionId, "Working on %d for %d records (marked %d duplicates)".formatted(year,
					noOfPublications, noOfDuplicates));
		}
	}

	/*
	 * Comparing starting page before DOI may be faster than the other way around.
	 * But: a complete set of conference abstracts has the same DOI. So starting
	 * page MUST be compared before DOI, except for Cochrane Reviews.
	 * See the comment at {@link edu.dedupendnote.domain.Publication#isCochrane}
	 */
	public boolean compareStartPageOrDoi(Publication r1, Publication r2, Map<String, Boolean> map) {
		// log.debug("Comparing {}: {} to {}: {}", r1.getId(), r1.getPageForComparison(), r2.getId(),
		// r2.getPageForComparison());
		Set<String> dois1 = r1.getDois();
		Set<String> dois2 = r2.getDois();
		boolean bothCochrane = r1.isCochrane() && r2.isCochrane();
		boolean sufficientStartPages = r1.getPageForComparison() != null && r2.getPageForComparison() != null;
		boolean sufficientDois = !dois1.isEmpty() && !dois2.isEmpty();

		if (!bothCochrane && !sufficientStartPages && !sufficientDois) {
			// log.debug("At least one starting page AND at least one DOI are missing");
			return true; // no useful comparison possible
		}

		if (bothCochrane) {
			if (r1.getPublicationYear().equals(r2.getPublicationYear())) {
				if (sufficientDois) {
					return setsContainSameString(dois1, dois2);
				} else if (sufficientStartPages && r1.getPageForComparison().equals(r2.getPageForComparison())) {
					// log.debug("Same starting pages");
					return true;
				}
			}
			return false;
		}
		if (sufficientDois && setsContainSameString(dois1, dois2)) {
			map.put("sameDois", true);
		}
		if (sufficientStartPages && r1.getPageForComparison().equals(r2.getPageForComparison())) {
			// log.debug("Same starting pages");
			return true;
		} else if (!sufficientStartPages && sufficientDois && setsContainSameString(dois1, dois2)) {
			return true;
		}

		// log.debug("Starting pages and DOIs are different");
		return false;
	}

	/** 
	 * All unique titles and their reverse are compared
	 * 
	 * Paths not chosen:
	 * - start with setContainsSameString(titleSet1, titleSet2): @see <a href="https://github.com/globbestael/DedupEndNote/issues/20">GitHub issues</a>  
	 */
	public boolean compareTitles(Publication r1, Publication r2) {
		// log.debug("Comparing {}: {}\nto {}: {}", r1.getId(), r1.getTitles().get(0), r2.getId(),
		// r2.getTitles().get(0));
		if (r1.isReply() || r2.isReply()) {
			return true;
		}
		Double similarity;
		List<String> titles1 = r1.getTitles();
		List<String> titles2 = r2.getTitles();
		boolean sufficientStartPages = r1.getPageForComparison() != null && r2.getPageForComparison() != null;
		boolean sufficientDois = !r1.getDois().isEmpty() && !r2.getDois().isEmpty();
		boolean isPhase = r1.isPhase() || r2.isPhase();

		for (String title1 : titles1) {
			for (String title2 : titles2) {
				similarity = jws.apply(title1, title2);
				if (isPhase) { // return result of comparison of only the first title variant
					// log.debug("{} and {}:\n- {}\n- {}", r1.getId(), r2.getId(), title1, title2);
					return similarity > TITLE_SIMILARITY_PHASE;
				}
				if ((sufficientStartPages || sufficientDois)
						&& similarity > TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS) {
					// log.debug("Found comparable title (with pages or DOI) {} == {}", r1.getTitles().get(0),
					// r2.getTitles().get(0));
					return true;
				}
				if (!(sufficientStartPages || sufficientDois)) {
					return similarity > TITLE_SIMILARITY_INSUFFICIENT_STARTPAGES_AND_DOIS;
				}
			}
		}
		return false;
	}

	private boolean containsDuplicateIds(List<Publication> publications) {
		return !publications.stream().map(Publication::getId).allMatch(new HashSet<>()::add);
	}

	private boolean containsOnlyRecordsWithoutPublicationYear(List<Publication> publications) {
		return publications.stream().filter(r -> r.getPublicationYear() == 0).count() == publications.size();
	}

	private boolean containsRecordsWithoutId(List<Publication> publications) {
		return publications.stream().filter(r -> r.getId() == null).count() > 0L;
	}

	public String deduplicateOneFile(String inputFileName, String outputFileName, boolean markMode,
			String wssessionId) {
		wsMessage(wssessionId, "Reading file " + inputFileName);
		List<Publication> publications = ioService.readPublications(inputFileName);

		String s = doSanityChecks(publications, inputFileName);
		if (s != null) {
			wsMessage(wssessionId, s);
			return s;
		}

		searchYearOneFile(publications, wssessionId);

		if (markMode) { // no enrich(), and add / overwrite LB (label) field
			int numberWritten = ioService.writeMarkedRecords(publications, inputFileName, outputFileName);
			long labeledRecords = publications.stream().filter(r -> r.getLabel() != null).count();
			s = "DONE: DedupEndNote has written " + numberWritten + " records with " + labeledRecords
					+ " duplicates marked in the Label field.";
			wsMessage(wssessionId, s);
			return s;
		}

		wsMessage(wssessionId, "Enriching the " + publications.size() + " deduplicated results");
		enrich(publications);
		wsMessage(wssessionId, "Saving the " + publications.size() + " deduplicated results");
		int numberWritten = ioService.writeDeduplicatedRecords(publications, inputFileName, outputFileName);
		s = formatResultString(publications.size(), numberWritten);
		wsMessage(wssessionId, s);

		return s;
	}

	public String deduplicateTwoFiles(String newInputFileName, String oldInputFileName, String outputFileName,
			boolean markMode, String wssessionId) {
		// read the old records and mark them as present, then add the new records
		log.info("oldInputFileName: {}", oldInputFileName);
		log.info("newInputFileName: {}", newInputFileName);
		List<Publication> publications = ioService.readPublications(oldInputFileName);

		String s = doSanityChecks(publications, oldInputFileName);
		if (s != null) {
			wsMessage(wssessionId, s);
			return s;
		}

		// Put "-" before the IDs of the old records. In this way the labels of the records (used for
		// identifying duplicate records) will be unique over both lists.
		// When writing the deduplicated records for the second list, records with label "-..." can
		// be skipped because they are duplicates of records from the first list.
		// When markMode is set, these records are written.
		// Because of this "-", the records which have duplicates in the first file (label = "-...")
		// can be distinguished from records which have duplicates in the second file.
		publications.forEach(r -> {
			r.setId("-" + r.getId());
			r.setPresentInOldFile(true);
		});

		List<Publication> newRecords = ioService.readPublications(newInputFileName);
		s = doSanityChecks(newRecords, newInputFileName);
		if (s != null) {
			wsMessage(wssessionId, s);
			return s;
		}
		publications.addAll(newRecords);
		log.info("Records read from 2 files: {}", publications.size());

		searchYearTwoFiles(publications, wssessionId);

		if (markMode) { // no enrich(), and add / overwrite LB (label) field
			int numberWritten = ioService.writeMarkedRecords(publications, newInputFileName, outputFileName);
			long numberLabeledRecords = publications.stream()
					.filter(r -> r.getLabel() != null && !r.isPresentInOldFile()).count();
			s = "DONE: DedupEndNote has written %s records with %d duplicates marked in the Label field."
					.formatted(numberWritten, numberLabeledRecords);
			wsMessage(wssessionId, s);
			return s;
		}

		enrich(publications);
		// Get the records from the new file that are not duplicates or not duplicates of records of the old file
		List<Publication> filteredRecords = publications.stream()
				.filter(r -> !r.isPresentInOldFile() && (r.getLabel() == null || !r.getLabel().startsWith("-")))
				.toList();
		log.error("Records to write: {}", filteredRecords.size());
		int numberWritten = ioService.writeDeduplicatedRecords(filteredRecords, newInputFileName, outputFileName);
		s = "DONE: DedupEndNote removed %d records from the new set, and has written %d records."
				.formatted((newRecords.size() - numberWritten), numberWritten);
		wsMessage(wssessionId, s);
		return s;
	}

	public String doSanityChecks(List<Publication> publications, String fileName) {
		if (containsRecordsWithoutId(publications)) {
			return "ERROR: The input file " + fileName
					+ " contains records without IDs. The input file is not an Export as RIS-file from an EndNote library!";
		}
		if (containsOnlyRecordsWithoutPublicationYear(publications)) {
			return "ERROR: All records of the input file " + fileName
					+ " have no Publication Year. The input file is not an Export as RIS-file from an EndNote library!";
		}
		if (containsDuplicateIds(publications)) {
			return "ERROR: The IDs of the records of input file " + fileName
					+ " are not unique. The input file is not an Export as RIS-file from 1 EndNote library!";
		}
		return null;
	}

	private void enrich(List<Publication> publications) {
		log.debug("Start enrich");
		// First the records with duplicates
		Map<String, List<Publication>> labelMap = publications.stream()
				// when comparing 2 files, duplicates from the old file start with "-"
				.filter(r -> r.getLabel() != null && !r.getLabel().startsWith("-"))
				.collect(Collectors.groupingBy(Publication::getLabel));
		log.debug("Number of duplicate lists {}, and IDs of kept records: {}", labelMap.size(), labelMap.keySet());
		List<Publication> recordList;
		if (labelMap.size() > 0) {
			for (Map.Entry<String, List<Publication>> entry : labelMap.entrySet()) {
				recordList = entry.getValue();
				Publication recordToKeep = recordList.remove(0);
				log.debug("Kept: {}: {}", recordToKeep.getId(), recordToKeep.getTitles().get(0));
				// Don't set keptRecord in compareSet(): trouble when multiple duplicates and no publication year
				recordList.stream().forEach(r -> r.setKeptRecord(false));

				// Reply: replace the title with the longest title from the duplicates
				if (recordToKeep.isReply()) {
					log.debug("Publication {} is a reply: ", recordToKeep.getId());
					String longestTitle = recordList.stream()
							// .filter(Publication::isReply)
							.map(r -> {
								log.debug("Reply {} has title: {}.", r.getId(), r.getTitle());
								return r.getTitle() != null ? r.getTitle() : r.getTitles().get(0);
							}).max(Comparator.comparingInt(String::length)).orElse("");
					// There are cases where not all titles are recognized as replies ->
					// record.title can be null
					if (recordToKeep.getTitle() == null || recordToKeep.getTitle().length() < longestTitle.length()) {
						log.debug("REPLY: changing title {}\nto {}", recordToKeep.getTitle(), longestTitle);
						recordToKeep.setTitle(longestTitle);
					}
				}

				// Gather all the DOIs
				final Set<String> dois = recordToKeep.getDois();
				for (Publication p : recordList) {
					if (!p.getDois().isEmpty()) {
						dois.addAll(p.getDois());
					}
				}
				if (!dois.isEmpty()) {
					recordToKeep.setDois(dois);
				}

				// Add missing publication year
				if (recordToKeep.getPublicationYear() == 0) {
					log.debug("Reached record without publicationYear");
					recordList.stream().filter(r -> r.getPublicationYear() != 0).findFirst()
							.ifPresent(r -> recordToKeep.setPublicationYear(r.getPublicationYear()));
				}

				// Cochrane records with duplicates
				if (recordToKeep.isCochrane()) {
					replaceCochranePageStart(recordToKeep, recordList);
				}

				// Add missing startpages (and endpages)
				if (recordToKeep.getPageStart() == null) {
					log.debug("Reached record without pageStart: {}", recordToKeep.getAuthors());
					recordList.stream().filter(r -> r.getPageStart() != null).findFirst().ifPresent(r -> {
						recordToKeep.setPageStart(r.getPageStart());
						recordToKeep.setPageEnd(r.getPageEnd());
					});
				}

				/*
				 * FIXME: Should empty authors be filled in from the duplicate set? See DOI
				 * 10.2298/sarh0902077c in test database, but the 2 duplicates have not the same
				 * author forms: "Culafic, D." (WoS) and "Dorde, Ć" (Scopus, error)
				 */
			}
		}

		// Then the Cochrane records without duplicates
		for (Publication r : publications) {
			if (r.isCochrane() && r.getLabel() == null) {
				replaceCochranePageStart(r, Collections.emptyList());
			}
		}

		log.debug("Finished enrich");
	}

	private void replaceCochranePageStart(Publication recordToKeep, List<Publication> duplicates) {
		String pageStart = recordToKeep.getPageStart();
		if (pageStart != null) {
			pageStart = pageStart.toUpperCase();
			// C: cochrane reviews nd protocols, E: editorials, M: ???
			if (!(pageStart.startsWith("C") || pageStart.startsWith("E") || pageStart.startsWith("M"))) {
				pageStart = null;
			}
		}

		if (pageStart == null) {
			log.debug("Reached Cochrane record without pageStart, getting it from pageStart of the duplicates: {}",
					recordToKeep.getAuthors());
			for (Publication r : duplicates) {
				if (r.getPageStart() != null && r.getPageStart().toUpperCase().matches("^[CEM].+")) {
					recordToKeep.setPageStart(r.getPageStart().toUpperCase());
					return;
				}
			}
			log.debug("Reached Cochrane record without pageStart, getting it from the DOIs: {}",
					recordToKeep.getAuthors());
			for (String doi : recordToKeep.getDois()) {
				Matcher matcher = cochraneIdentifierPattern.matcher(doi);
				if (matcher.matches()) {
					pageStart = matcher.group(1);
					break;
				}
			}
		}
		if (pageStart != null) {
			recordToKeep.setPageStart(pageStart.toUpperCase());
		}
	}

	public String formatResultString(int total, int totalWritten) {
		return "DONE: DedupEndNote has deduplicated " + total + " records, has removed " + (total - totalWritten)
				+ " duplicates, and has written " + totalWritten + " records.";
	}

	public AuthorsComparator getAuthorsComparator() {
		return authorsComparator;
	}

	// FIXME: is Apache Commons CollectionUtils better?
	// private boolean listsContainSameString(List<String> list1, List<String> list2) {
	// if (list1.isEmpty() || list2.isEmpty()) {
	// return false;
	// }
	// List<String> common = new ArrayList<>(list1);
	// common.retainAll(list2);
	// return !common.isEmpty();
	// }

	// FIXME: is Apache Commons CollectionUtils or Spring CollectionUtils better?
	private boolean setsContainSameString(Set<String> set1, Set<String> set2) {
		if (set1.isEmpty() || set2.isEmpty()) {
			return false;
		}
		Set<String> common = new HashSet<>(set1);
		common.retainAll(set2);
		return !common.isEmpty();
	}

	// @formatter:off
	/*
	 * For 1 file:
	 * - order year descending
	 * - add empty years (year == 0 and not identified as duplicate yet) AFTER each year1
	 *
	 * Reason: we prefer the data (duplicate kept) which is most recent (e.g. complete publication BEFORE ahead
	 * of print which is possibly from earlier year or without a year).
	 */
	// @formatter:on
	public void searchYearOneFile(List<Publication> publications, String wssessionId) {
		Map<Integer, List<Publication>> yearSets = publications.stream()
				.collect(Collectors.groupingBy(Publication::getPublicationYear, TreeMap::new, Collectors.toList()))
				.descendingMap();

		Map<Integer, Integer> cumulativePercentages = getCumulativePercentages(publications, yearSets);

		List<Publication> emptyYearlist = yearSets.remove(0);
		log.debug("YearSets: {}", yearSets.keySet().stream().sorted().toList());
		yearSets.keySet().stream().forEach(year -> {
			List<Publication> yearSet = yearSets.get(year);
			if (emptyYearlist != null) {
				yearSet.addAll(emptyYearlist.stream().filter(r -> r.getLabel() == null).toList());
			}
			yearSet.addAll(yearSets.getOrDefault(year - 1, Collections.emptyList()));
			wsMessage(wssessionId, "Working on " + year + " for " + yearSet.size() + " records");
			compareSet(yearSet, year, true, wssessionId);
			wsMessage(wssessionId, "PROGRESS: " + cumulativePercentages.get(year));
		});
	}

	// @formatter:off
	/*
	 * For 2 files: 
	 * - order year ascending 
	 * - add empty years (year == 0 and not identified as duplicate yet) BEFORE each year1 
	 * 
	 * Reason: the oldest data should set the label for a duplicate set (e.g. ahead of print (which is possibly
	 * from earlier year or without a year and more probably in the old file than in the new file) BEFORE the complete data
	 */
	// @formatter:on
	public void searchYearTwoFiles(List<Publication> publications, String wssessionId) {
		Map<Integer, List<Publication>> yearSets = publications.stream()
				.collect(Collectors.groupingBy(Publication::getPublicationYear, TreeMap::new, Collectors.toList()));
		Map<Integer, Integer> cumulativePercentages = getCumulativePercentages(publications, yearSets);

		List<Publication> emptyYearlist = yearSets.remove(0);
		log.debug("YearSets: {}", yearSets.keySet().stream().toList());
		yearSets.keySet().stream().forEach(year -> {
			List<Publication> yearSet = new ArrayList<>();
			if (emptyYearlist != null) {
				yearSet.addAll(emptyYearlist.stream().filter(r -> r.getLabel() == null).toList());
			}
			yearSet.addAll(yearSets.get(year));
			yearSet.addAll(yearSets.getOrDefault(year + 1, Collections.emptyList()));
			wsMessage(wssessionId, "Working on " + year + " for " + yearSet.size() + " records");
			compareSet(yearSet, year, false, wssessionId);
			wsMessage(wssessionId, "PROGRESS: " + cumulativePercentages.get(year));
		});
	}

	private Map<Integer, Integer> getCumulativePercentages(List<Publication> publications,
			Map<Integer, List<Publication>> yearSets) {
		Map<Integer, Integer> cumulativePercentages = new LinkedHashMap<>();
		int current = 0;
		Integer total = publications.size();
		for (Map.Entry<Integer, List<Publication>> year : yearSets.entrySet()) {
			int simple = year.getValue().size();
			cumulativePercentages.put(year.getKey(), 100 * (simple + current) / total);
			current += simple;
		}
		log.debug("cumulativePercentages: " + cumulativePercentages);
		return cumulativePercentages;
	}

	private void wsMessage(String wssessionId, String message) {
		if (simpMessagingTemplate != null) {
			simpMessagingTemplate.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
		}
	}

}