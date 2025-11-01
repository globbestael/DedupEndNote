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
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.StompMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DeduplicationService {

	protected AuthorsComparisonService authorsComparisonService;

	// the DOIs have been lowercased
	public static Pattern COCHRANE_DOI_PATTERN = Pattern.compile("^.*10.1002/14651858.([a-z][a-z]\\d+).*",
			Pattern.CASE_INSENSITIVE);

	private IOService ioService;

	// @formatter:off
	/*
	 * Procedure for 1 file:
	 *
	 * 1. Do preliminary checks on the input file (EndNote IDs are present and unique) and exit when the checks do not pass.
	 * 2. Read all EndNote publications from inputfile and make Publication objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    (All publications start with kepPublication = true (default))
	 * 3. Deduplicate the publications.
	 *    When duplicates are found:
	 *    - put the id (EndNote ID field) of the first duplicate into the label (EndNote LB field) of all the duplicates
	 *    - set kepPublication = false for all but the first duplicate
	 *  4. Enrich the first duplicate with data from the other duplicates (DOI, starting page)
	 *  5. Read the input file again, extract the ID and get the corresponding publication.
	 *     If the publication is keptPublication = true,
	 *     copy the original content of the fields of this publication from the input file to the output file
	 *     except for the fields where content is standardized (DOI) or where content is enriched from the duplicates.
	 *
	 *   If markMode is set, publications are not enriched and ALL publications are written back.
	 *   If a publication is a duplicate, the Label field (LB) contains the ID of the first duplicate found.
	 *
	 *
	 * Procedure for 2 files:
	 *
	 * 1. Do preliminary checks on input file for old publications (EndNote IDs are present and unique) and exit when the checks do not pass.
	 * 2. Read all EndNote publications from this inputfile and make Publication objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    Alter the ID by prefixing them with "-" (to distinguish them from the IDs of the second file and making them unique over both files).
	 *    All OLD publications start with kepPublication = false and presentInOldFile = true.
	 * 3. Do preliminary checks on input file for new publications (EndNote IDs are present and unique) and exit when the checks do not pass.
	 * 4. Read all EndNote publications from this inputfile and make Publication objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    All NEW publications start with keptPublication = true (default)  and presentInOldFile = false (default).
	 * 5. Deduplicate the publications.
	 *    When duplicates are found:
	 *    - put the id (EndNote ID field) of the first duplicate into the label (EndNote LB field) of all the duplicates
	 *    - set keptPublication = false for all but the first duplicate
	 * 6. Enrich the first duplicate with data from the other duplicates (DOI, starting page)
	 *    only if the label exists and does not start with "-", i.e. is NOT a duplicate from an OLD publication.
	 * 7. Read the NEW input file again, extract the ID and get the corresponding publication.
	 *    If the publication is keptPublication = true,
	 *    copy the original content of the fields of this publication from the input file to the output file
	 *    except for the fields where content is standardized (DOI) or where content is enriched from the duplicates.
	 *
	 *  If markMode is set, publications are not enriched and ALL publications of the NEW inputfile are written back.
	 *  If a publication is a duplicate, the Label field (LB) contains the ID of the first duplicate found.
	 *  If the label starts with "-", it is a duplicate from a publication from the OLD input file.
	 */
	// @formatter:on
	// @Autowired
	private SimpMessagingTemplate simpMessagingTemplate;

	public DeduplicationService() {
		this.authorsComparisonService = new DefaultAuthorsComparisonService();
		this.ioService = new IOService();
	}

	public DeduplicationService(SimpMessagingTemplate simpMessagingTemplate) {
		this.authorsComparisonService = new DefaultAuthorsComparisonService();
		this.ioService = new IOService();
		this.simpMessagingTemplate = simpMessagingTemplate;
	}

	public DeduplicationService(AuthorsComparisonService authorsComparisonService) {
		this.authorsComparisonService = authorsComparisonService;
		this.ioService = new IOService();
	}

	public void compareSet(List<Publication> publications, Integer year, boolean descending, String wssessionId) {
		int noOfPublications = publications.size();
		int noOfDuplicates = 0;
		Map<String, Boolean> map = new HashMap<>(Map.of("sameDois", false));

		while (publications.size() > 1) {
			// In log messages this publication is called the pivot
			Publication publication = publications.remove(0);
			/*
			 * If descending / OneFile mode: only publications of year1 should be compared to publications of year1 and year2.
			 * The publications of year2 will be compared in the next pair of years.
			 * If ascending / TwoFile mode: publicationYear 0 publications are at the head of the publicationList!
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
				if (ComparisonService.compareStartPagesOrDois(r, publication, map)
						&& authorsComparisonService.compare(r, publication)
						&& ComparisonService.compareTitles(r, publication)
						&& (ComparisonService.compareSameDois(r, publication, map.get("sameDois"))
								|| ComparisonService.compareIssns(r, publication)
								|| ComparisonService.compareJournals(r, publication))) {

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
						// log.debug("=== Both pivot {} and pub {} get label {} from the publicationId of the pivot {}",
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
			wsMessage(wssessionId, "Working on %d for %d publications (marked %d duplicates)".formatted(year,
					noOfPublications, noOfDuplicates));
		}
	}

	private boolean containsDuplicateIds(List<Publication> publications) {
		return !publications.stream().map(Publication::getId).allMatch(new HashSet<>()::add);
	}

	private boolean containsOnlyPublicationsWithoutPublicationYear(List<Publication> publications) {
		return publications.stream().filter(r -> r.getPublicationYear() == 0).count() == publications.size();
	}

	private boolean containsPublicationsWithoutId(List<Publication> publications) {
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
			int numberWritten = ioService.writeMarkedPublications(publications, inputFileName, outputFileName);
			long labeledPublications = publications.stream().filter(r -> r.getLabel() != null).count();
			s = "DONE: DedupEndNote has written " + numberWritten + " publications with " + labeledPublications
					+ " duplicates marked in the Label field.";
			wsMessage(wssessionId, s);
			return s;
		}

		wsMessage(wssessionId, "Enriching the " + publications.size() + " deduplicated results");
		enrich(publications);
		wsMessage(wssessionId, "Saving the " + publications.size() + " deduplicated results");
		int numberWritten = ioService.writeDeduplicatedPublications(publications, inputFileName, outputFileName);
		s = formatResultString(publications.size(), numberWritten);
		wsMessage(wssessionId, s);

		return s;
	}

	public String deduplicateTwoFiles(String newInputFileName, String oldInputFileName, String outputFileName,
			boolean markMode, String wssessionId) {
		// read the old publications and mark them as present, then add the new publications
		log.info("oldInputFileName: {}", oldInputFileName);
		log.info("newInputFileName: {}", newInputFileName);
		List<Publication> publications = ioService.readPublications(oldInputFileName);

		String s = doSanityChecks(publications, oldInputFileName);
		if (s != null) {
			wsMessage(wssessionId, s);
			return s;
		}

		// Put "-" before the IDs of the old publications. In this way the labels of the publications (used for
		// identifying duplicate publications) will be unique over both lists.
		// When writing the deduplicated publications for the second list, publications with label "-..." can
		// be skipped because they are duplicates of publications from the first list.
		// When markMode is set, these publications are written.
		// Because of this "-", the publications which have duplicates in the first file (label = "-...")
		// can be distinguished from publications which have duplicates in the second file.
		publications.forEach(r -> {
			r.setId("-" + r.getId());
			r.setPresentInOldFile(true);
		});

		List<Publication> newPublications = ioService.readPublications(newInputFileName);
		s = doSanityChecks(newPublications, newInputFileName);
		if (s != null) {
			wsMessage(wssessionId, s);
			return s;
		}
		publications.addAll(newPublications);
		log.info("Publications read from 2 files: {}", publications.size());

		searchYearTwoFiles(publications, wssessionId);

		if (markMode) { // no enrich(), and add / overwrite LB (label) field
			int numberWritten = ioService.writeMarkedPublications(publications, newInputFileName, outputFileName);
			long numberLabeledPublications = publications.stream()
					.filter(r -> r.getLabel() != null && !r.isPresentInOldFile()).count();
			s = "DONE: DedupEndNote has written %s publications with %d duplicates marked in the Label field."
					.formatted(numberWritten, numberLabeledPublications);
			wsMessage(wssessionId, s);
			return s;
		}

		enrich(publications);
		// Get the publications from the new file that are not duplicates or not duplicates of publications of the old
		// file
		List<Publication> filteredPublications = publications.stream()
				.filter(r -> !r.isPresentInOldFile() && (r.getLabel() == null || !r.getLabel().startsWith("-")))
				.toList();
		log.error("Publications to write: {}", filteredPublications.size());
		int numberWritten = ioService.writeDeduplicatedPublications(filteredPublications, newInputFileName,
				outputFileName);
		s = "DONE: DedupEndNote removed %d publications from the new set, and has written %d publications."
				.formatted((newPublications.size() - numberWritten), numberWritten);
		wsMessage(wssessionId, s);
		return s;
	}

	public String doSanityChecks(List<Publication> publications, String fileName) {
		if (containsPublicationsWithoutId(publications)) {
			return "ERROR: The input file " + fileName
					+ " contains publications without IDs. The input file is not an Export as RIS-file from an EndNote library!";
		}
		if (containsOnlyPublicationsWithoutPublicationYear(publications)) {
			return "ERROR: All publications of the input file " + fileName
					+ " have no Publication Year. The input file is not an Export as RIS-file from an EndNote library!";
		}
		if (containsDuplicateIds(publications)) {
			return "ERROR: The IDs of the publications of input file " + fileName
					+ " are not unique. The input file is not an Export as RIS-file from 1 EndNote library!";
		}
		return null;
	}

	private void enrich(List<Publication> publications) {
		log.debug("Start enrich");
		// First the publications with duplicates
		Map<String, List<Publication>> labelMap = publications.stream()
				// when comparing 2 files, duplicates from the old file start with "-"
				.filter(r -> r.getLabel() != null && !r.getLabel().startsWith("-"))
				.collect(Collectors.groupingBy(Publication::getLabel));
		log.debug("Number of duplicate lists {}, and IDs of kept publications: {}", labelMap.size(), labelMap.keySet());
		List<Publication> publicationList;
		if (!labelMap.isEmpty()) {
			for (Map.Entry<String, List<Publication>> entry : labelMap.entrySet()) {
				publicationList = entry.getValue();
				Publication publicationToKeep = publicationList.remove(0);
				log.debug("Kept: {}: {}", publicationToKeep.getId(),
						(publicationToKeep.getTitles().isEmpty() ? "(no titles found)"
								: publicationToKeep.getTitles().get(0)));
				// Don't set keptPublication in compareSet(): trouble when multiple duplicates and no publication year
				publicationList.stream().forEach(r -> r.setKeptPublication(false));

				// Reply and Retraction: replace the title with the longest title from the duplicates
				if (publicationToKeep.isReply()
						|| (!publicationToKeep.isClinicalTrialGov() && publicationToKeep.getTitle() != null)) {
					log.debug("Publication {} is a reply: ", publicationToKeep.getId());
					String longestTitle = publicationList.stream()
							// .filter(Publication::isReply)
							.map(r -> {
								log.debug("Reply {} has title: {}.", r.getId(), r.getTitle());
								return r.getTitle() != null ? r.getTitle() : r.getTitles().get(0);
							}).max(Comparator.comparingInt(String::length)).orElse("");
					// There are cases where not all titles are recognized as replies -> publication.title can be null
					if (publicationToKeep.getTitle() == null
							|| publicationToKeep.getTitle().length() < longestTitle.length()) {
						log.debug("REPLY: changing title {}\nto {}", publicationToKeep.getTitle(), longestTitle);
						publicationToKeep.setTitle(longestTitle);
					}
				}
				// Clinical trials from ClinicalTrials.gov: replace the title with the shortest title from the
				// duplicates
				if (publicationToKeep.isClinicalTrialGov()) {
					log.debug("Publication {} is a trial: ", publicationToKeep.getId());
					String shortestTitle = publicationList.stream().map(r -> {
						log.debug("Trial {} has title: {}.", r.getId(), r.getTitle());
						return r.getTitle() != null ? r.getTitle() : r.getTitles().get(0);
					}).min(Comparator.comparingInt(String::length)).orElse("");
					// There are cases where publication.title can be null (??)
					if (publicationToKeep.getTitle() == null
							|| publicationToKeep.getTitle().length() > shortestTitle.length()) {
						log.debug("Trial: changing title {}\nto {}", publicationToKeep.getTitle(), shortestTitle);
						publicationToKeep.setTitle(shortestTitle);
					}
				}

				// Gather all the DOIs
				final Set<String> dois = publicationToKeep.getDois();
				for (Publication p : publicationList) {
					if (!p.getDois().isEmpty()) {
						dois.addAll(p.getDois());
					}
				}
				if (!dois.isEmpty()) {
					publicationToKeep.setDois(dois);
				}

				// Add missing publication year
				if (publicationToKeep.getPublicationYear() == 0) {
					log.debug("Reached publication without publicationYear");
					publicationList.stream().filter(r -> r.getPublicationYear() != 0).findFirst()
							.ifPresent(r -> publicationToKeep.setPublicationYear(r.getPublicationYear()));
				}

				if (publicationToKeep.isCochrane() && publicationToKeep.getPagesOutput() != null) {
					// replaceCochranePageStart(publicationToKeep, publicationList);
					publicationToKeep.setPagesOutput(publicationToKeep.getPagesOutput().toUpperCase());
				}

				// Add missing pagesOutput
				if (publicationToKeep.getPagesOutput() == null || publicationToKeep.getPagesOutput().isEmpty()) {
					log.debug("Reached publication without pagesOutput: {}", publicationToKeep.getId());
					publicationList.stream().filter(r -> r.getPagesOutput() != null).findFirst().ifPresent(r -> {
						// publicationToKeep.setPageStart(r.getPageStart());
						// publicationToKeep.setPageEnd(r.getPageEnd());
						publicationToKeep.setPagesOutput(r.getPagesOutput());
					});
				}

				/*
				 * FIXME: Should empty authors be filled in from the duplicate set? See DOI
				 * 10.2298/sarh0902077c in test database, but the 2 duplicates have not the same
				 * author forms: "Culafic, D." (WoS) and "Dorde, Ć" (Scopus, error)
				 * Better example: 4605 in BIG_TEST without authors, 21391 with authors.
				 * But publications can have different authors: in BIG_SET 4226 (none), 21471 (Banks ...), 36519 (Cabot ...)
				 */
			}
		}

		// Then the Cochrane publications without duplicates
		for (Publication r : publications) {
			if (r.isCochrane() && r.getLabel() == null && r.getPagesOutput() != null) {
				// replaceCochranePageStart(r, Collections.emptyList());
				r.setPagesOutput(r.getPagesOutput().toUpperCase());
			}
		}

		log.debug("Finished enrich");
	}

	public String formatResultString(int total, int totalWritten) {
		return "DONE: DedupEndNote has deduplicated " + total + " publications, has removed " + (total - totalWritten)
				+ " duplicates, and has written " + totalWritten + " publications.";
	}

	public AuthorsComparisonService getAuthorsComparisonService() {
		return authorsComparisonService;
	}

	/*
	 * For 1 file:
	 * - order year descending
	 * - add empty years (year == 0 and not identified as duplicate yet) AFTER each year1
	 *
	 * Reason: we prefer the data (duplicate kept) which is most recent (e.g. complete publication BEFORE ahead
	 * of print which is possibly from earlier year or without a year).
	 */
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
			wsMessage(wssessionId, "Working on " + year + " for " + yearSet.size() + " publications");
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
			wsMessage(wssessionId, "Working on " + year + " for " + yearSet.size() + " publications");
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