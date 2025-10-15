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
import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.StompMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
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
				// Because Anonymous would only compare on journals (and maybe SP/DOIs)
				// (see "MedGenMed Medscape General Medicine" articles in Cannabis test set)
				// Because Anonymous AND no SP or DOI would only compare on title and journals
				// (see "Abstracts of 16th National Congress of SIGENP" articles in Joost problem set)
				if (!sufficientStartPages && !sufficientDois) {
					/*
					 * Exception within the exception:
					 * Conference proceedings (and books?) have no author (AU, maybe A2 which is not used).
					 * If they have an ISBN, the author comparison without authors returns true.
					 */
					if (!r1.getIsbns().isEmpty() && !r2.getIsbns().isEmpty()) {
						log.trace("- 2. No authors, startpages or DOIs, but ISBNs are present, considered the same");
						return true;
					}
					log.trace(
							"- 2. Not the same because not enough data: One or both authors are empty AND not enough starting pages AND not enough DOIs");
					return false;
				}
				log.trace("- 2. One or both authors are empty");
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
						log.trace("- 2. Author similarity is above threshold");
						return true;
					}
				}
			}
			if (log.isTraceEnabled()) {
				log.trace("- 2. Author similarity {} is below threshold: {} and {}", similarity, r1.getAllAuthors(),
						r2.getAllAuthors());
			}
			return false;
		}

		@Override
		public Double getSimilarity() {
			return similarity;
		}

	}

	protected AuthorsComparator authorsComparator;

	// the DOIs have been lowercased
	private static Pattern cochraneIdentifierPattern = Pattern.compile("^.*10.1002/14651858.([a-z][a-z]\\d+).*",
			Pattern.CASE_INSENSITIVE);

	private IOService ioService;

	private ComparatorService comparatorService;

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
	}

	public DeduplicationService(NormalizationService normalizationService, ComparatorService comparatorService) {
		this.authorsComparator = new DefaultAuthorsComparator();
		this.ioService = new IOService(normalizationService);
		this.comparatorService = comparatorService;
	}

	public DeduplicationService(SimpMessagingTemplate simpMessagingTemplate, NormalizationService normalizationService,
			ComparatorService comparatorService) {
		this.authorsComparator = new DefaultAuthorsComparator();
		this.ioService = new IOService(normalizationService);
		this.simpMessagingTemplate = simpMessagingTemplate;
		this.comparatorService = comparatorService;
	}

	public DeduplicationService(AuthorsComparator authorsComparator, NormalizationService normalizationService,
			ComparatorService comparatorService) {
		this.authorsComparator = authorsComparator;
		this.ioService = new IOService(normalizationService);
		this.comparatorService = comparatorService;
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

			for (Publication r : publications) {
				map.put("sameDois", false);
				// log.atDebug().setMessage("Clear results previous comparison {}")
				// .addArgument(() -> publication.getLogLines().removeAll(publication.getLogLines())).log();
				if (log.isTraceEnabled()) {
					log.trace("\nStarting comparison {} - {}", publication.getId(), r.getId());
				}
				if (comparatorService.compareStartPageOrDoi(r, publication, map)
						&& authorsComparator.compare(r, publication) && comparatorService.compareTitles(r, publication)
						&& (comparatorService.compareSameDois(r, publication, map.get("sameDois"))
								|| comparatorService.compareIssns(r, publication)
								|| comparatorService.compareJournals(r, publication))) {

					noOfDuplicates++;
					// set the label
					if (publication.getLabel() != null) {
						// log.debug("=== pub {} gets label {} from pivot {}", r.getId(), publication.getLabel(),
						// publication.getId());
						r.setLabel(publication.getLabel());
					} else if (r.getLabel() != null) {
						// @formatter:off
						/**
						 * Labels can be promoted from a publication to the pivot without a label:
						 * - in loop N with pivot V 
						 *   - publication W is NOT seen as similar and gets no label
						 *   - publication X is seen as similar and gets V as label
						 * - in loop N + 1 with pivot W
						 *   - publication X is seen as similar and its label V is promoted to label of pivot W
						 * 
						 * V W X SP 1-26 291-316 (None) DOI + + +
						 * 
						 * Loop N (V) - W.SP != V.SP -> W.label = NULL - X.DOI = V.DOI -> X.label = V Loop N + 1 (W) -
						 * W.DOi = X.DOI -> W.lavel = X
						 * 
						 * Another reason can be that pivot W has more journal name variants than pivot V
						 */
						// @formatter:on
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
					} else {
						if (r.getTitle() != null && publication.getTitle() == null) {
							publication.setTitle(r.getTitle());
						}
					}
					if (log.isTraceEnabled()) {
						log.trace("{} - {} ARE DUPLICATES", publication.getId(), r.getId());
					}
				} else {
					if (log.isTraceEnabled()) {
						log.trace("{} - {} ARE NOT DUPLICATES", publication.getId(), r.getId());
						// log.trace("Comparisons:\n"
						// + publication.getLogLines().stream().collect(Collectors.joining("\n- ")));
					}
				}
			}
			wsMessage(wssessionId, "Working on %d for %d records (marked %d duplicates)".formatted(year,
					noOfPublications, noOfDuplicates));
		}
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
		if (!labelMap.isEmpty()) {
			for (Map.Entry<String, List<Publication>> entry : labelMap.entrySet()) {
				recordList = entry.getValue();
				Publication recordToKeep = recordList.remove(0);
				log.debug("Kept: {}: {}", recordToKeep.getId(),
						(recordToKeep.getTitles().isEmpty() ? "(no titles found)" : recordToKeep.getTitles().get(0)));
				// Don't set keptRecord in compareSet(): trouble when multiple duplicates and no publication year
				recordList.stream().forEach(r -> r.setKeptRecord(false));

				// Reply and Retraction: replace the title with the longest title from the duplicates
				if (recordToKeep.isReply() || (!recordToKeep.isClinicalTrialGov() && recordToKeep.getTitle() != null)) {
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
				// Clinical trials from ClinicalTrials.gov: replace the title with the shortest title from the
				// duplicates
				if (recordToKeep.isClinicalTrialGov()) {
					log.debug("Publication {} is a trial: ", recordToKeep.getId());
					String shortestTitle = recordList.stream().map(r -> {
						log.debug("Trial {} has title: {}.", r.getId(), r.getTitle());
						return r.getTitle() != null ? r.getTitle() : r.getTitles().get(0);
					}).min(Comparator.comparingInt(String::length)).orElse("");
					// There are cases where not all titles are recognized as replies ->
					// record.title can be null
					if (recordToKeep.getTitle() == null || recordToKeep.getTitle().length() > shortestTitle.length()) {
						log.debug("Trial: changing title {}\nto {}", recordToKeep.getTitle(), shortestTitle);
						recordToKeep.setTitle(shortestTitle);
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
				 * Better example: 4605 in BIG_TEST without authors, 21391 with authors.
				 * But records can have different authors: in BIG_SET 4226 (none), 21471 (Banks ...), 36519 (Cabot ...)
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
		// log.debug("YearSets: {}", yearSets.keySet().stream().sorted().toList());
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
		// log.debug("cumulativePercentages: " + cumulativePercentages);
		return cumulativePercentages;
	}

	private void wsMessage(String wssessionId, String message) {
		if (simpMessagingTemplate != null) {
			simpMessagingTemplate.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
		}
	}

}