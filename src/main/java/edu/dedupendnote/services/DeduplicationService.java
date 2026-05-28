package edu.dedupendnote.services;

import java.util.ArrayList;
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

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.domain.DeduplicationMode;
import lombok.extern.slf4j.Slf4j;

@Service
@RequestScope
@Slf4j
public class DeduplicationService {

	private final ComparisonService comparisonService;

	private final IOService ioService;

	// the DOIs have been lowercased
	public static Pattern COCHRANE_DOI_PATTERN = Pattern.compile("^.*10.1002/14651858.([a-z][a-z]\\d+).*",
			Pattern.CASE_INSENSITIVE);

	// @formatter:off
	/*
	 * Procedure for 1 file:
	 *
	 * 1. Do preliminary checks on the input file (EndNote IDs are present and unique) and exit when the checks do not pass.
	 * 2. Read all EndNote bibliographicItems from inputfile and make BibliographicItem objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    (All bibliographicItems start with kepBibliographicItem = true (default))
	 * 3. Deduplicate the bibliographicItems.
	 *    When duplicates are found:
	 *    - put the id (EndNote ID field) of the first duplicate into the label (EndNote LB field) of all the duplicates
	 *    - set kepBibliographicItem = false for all but the first duplicate
	 *  4. Enrich the first duplicate with data from the other duplicates (DOI, starting page)
	 *  5. Read the input file again, extract the ID and get the corresponding bibliographicItem.
	 *     If the bibliographicItem is kepBibliographicItem = true,
	 *     copy the original content of the fields of this bibliographicItem from the input file to the output file
	 *     except for the fields where content is standardized (DOI) or where content is enriched from the duplicates.
	 *
	 *   If markMode is set, bibliographicItems are not enriched and ALL bibliographicItems are written back.
	 *   If a bibliographicItem is a duplicate, the Label field (LB) contains the ID of the first duplicate found.
	 *
	 *
	 * Procedure for 2 files:
	 *
	 * 1. Do preliminary checks on input file for old bibliographicItems (EndNote IDs are present and unique) and exit when the checks do not pass.
	 * 2. Read all EndNote bibliographicItems from this inputfile and make BibliographicItem objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    Alter the ID by prefixing them with "-" (to distinguish them from the IDs of the second file and making them unique over both files).
	 *    All OLD bibliographicItems start with kepBibliographicItem = false and presentInOldFile = true.
	 * 3. Do preliminary checks on input file for new bibliographicItems (EndNote IDs are present and unique) and exit when the checks do not pass.
	 * 4. Read all EndNote bibliographicItems from this inputfile and make BibliographicItem objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    All NEW bibliographicItems start with kepBibliographicItem = true (default)  and presentInOldFile = false (default).
	 * 5. Deduplicate the bibliographicItems.
	 *    When duplicates are found:
	 *    - put the id (EndNote ID field) of the first duplicate into the label (EndNote LB field) of all the duplicates
	 *    - set kepBibliographicItem = false for all but the first duplicate
	 * 6. Enrich the first duplicate with data from the other duplicates (DOI, starting page)
	 *    only if the label exists and does not start with "-", i.e. is NOT a duplicate from an OLD bibliographicItem.
	 * 7. Read the NEW input file again, extract the ID and get the corresponding bibliographicItem.
	 *    If the bibliographicItem is kepBibliographicItem = true,
	 *    copy the original content of the fields of this bibliographicItem from the input file to the output file
	 *    except for the fields where content is standardized (DOI) or where content is enriched from the duplicates.
	 *
	 *  If markMode is set, bibliographicItems are not enriched and ALL bibliographicItems of the NEW inputfile are written back.
	 *  If a bibliographicItem is a duplicate, the Label field (LB) contains the ID of the first duplicate found.
	 *  If the label starts with "-", it is a duplicate from a bibliographicItem from the OLD input file.
	 */
	// @formatter:on

	public DeduplicationService(ComparisonService comparisonService) {
		this.ioService = new IOService();
		this.comparisonService = comparisonService;
	}

	public void compareSet(List<BibliographicItem> bibliographicItems, Integer year, boolean descending,
			Consumer<String> progressReporter) {
		int noOfBibliographicItems = bibliographicItems.size();
		int noOfDuplicates = 0;
		/*
		 * This Map holds temporary results of the comparison between 2 bibliographicItems.
		 * At present there is only 1 key (isSameDois). If we need more keys, a POJO would be better?
		 * 
		 * isSameDois is three-valued: null (i.e uninitialized), false, true
		 * Don't initialize here as "new HashMap<>(Map.of("isSameDois", null))" because null values are not allowed.
		 * 
		 * This three-valuedness was an attempt to lower the False Positives. ComparisonService.compareIssns and compareJournals
		 * would short circuit when isSameDois == false (both bibliographicItems have DOIs but they are different).
		 * FPs didn't go down, however there were more FNs (especially with errors in DOIs)
		 */
		Map<String, @Nullable Boolean> map = new HashMap<>();
		// Map<String, Boolean> map = new HashMap<>(Map.of("isSameDois", null));

		while (bibliographicItems.size() > 1) {
			BibliographicItem pivot = bibliographicItems.remove(0);
			/*
			 * If descending / OneFile mode: only bibliographicItems of year1 should be compared to bibliographicItems of year1 and year2.
			 * The bibliographicItems of year2 will be compared in the next pair of years.
			 * If ascending / TwoFile mode: publicationYear 0 bibliographicItems are at the head of the bibliographicItemList!
			 */
			if ((descending && pivot.getPublicationYear() < year)
					|| (!descending && pivot.getPublicationYear() != 0 && pivot.getPublicationYear() > year)) {
				break;
			}

			for (BibliographicItem p : bibliographicItems) {
				map.put("isSameDois", null);
				// log.atDebug().setMessage("Clear results previous comparison {}")
				// .addArgument(() -> pivot.getLogLines().removeAll(bibliographicItem.getLogLines())).log();
				if (log.isTraceEnabled()) {
					log.trace("\nStarting comparison {} - {}", pivot.getId(), p.getId());
				}
				if (comparisonService.compareStartPagesOrDois(p, pivot, map)
						&& comparisonService.compareAuthors(p, pivot) && comparisonService.compareTitles(p, pivot)
						&& (ComparisonService.compareSameDois(p, pivot, map.get("isSameDois"))
								|| ComparisonService.compareIssns(p, pivot, map.get("isSameDois"))
								|| comparisonService.compareJournals(p, pivot, map.get("isSameDois")))) {

					noOfDuplicates++;
					// set the label
					if (pivot.getLabel() != null) {
						// log.debug("=== pub {} gets label {} from pivot {}", r.getId(), pivot.getLabel(),
						// pivot.getId());
						p.setLabel(pivot.getLabel());
					} else if (p.getLabel() != null) {
						// @formatter:off
						/**
						 * THIS COPYING OF THE LABEL FROM THE BIBLIOGRAPHICITEM p TO THE PIVOT HAS BEEN DISABLED 
						 * because it reduces the FPs at a smell cost of more FNs.
						 * 
						 * Labels can be promoted from a bibliographicItem to the pivot without a label:
						 * - in loop N with pivot V 
						 *   - bibliographicItem W is NOT seen as similar and gets no label
						 *   - bibliographicItem X is seen as similar and gets V as label
						 * - in loop N + 1 with pivot W
						 *   - bibliographicItem X is seen as similar and its label V is promoted to label of pivot W
						 * 
						 * 		V 		W 		X 
						 * SP	1-26	291-316	(None) 
						 * DOI 	+ 		+ 		+
						 * 
						 * Loop N (V) 
						 * - W.SP != V.SP -> W.label = NULL 
						 * - X.DOI = V.DOI -> X.label = V 
						 * Loop N + 1 (W) 
						 * - W.DOi = X.DOI -> W.lavel = X (=V)
						 * 
						 * Another reason can be that pivot W has more journal name variants than pivot V
						 * 
						 * But this can cause False Positives
						 * Take 3 cochrane reviews, different versions (different DOIs) but same review number
l						 * 		V 		W 		X 
						 * DOI 	d1 		d2 		(None)
						 * SP	C26		C26		C26 
						 * 
						 * Loop N (V) 
						 * - W.DOI != V.dois 	-> W.label = NULL 
						 * - X.SP = V.SP 		-> X.label = V 
						 * Loop N + 1 (W) 
						 * - W.SP = X.SP 		-> W.label = X (=V)
						 * 
						 * See MissedDuplicatesTest: comment with test file
						 * /ASySD/dedupendnote_files/missed_duplicates/SRSR_Human_missed_2_4.txt
						 * 
						 * BIG_SET id set 10428, 10915, 22038, 38961 has some small changes in the order of the authors (last 3 with a
						 * large number of group authors).
						 * Without copy of label TO the pivot 10915 is NOT seen as a duplicate of 10428
						 */
						// @formatter:on
						// log.error("=== pub {} SETs label {} in pivot {}", p.getId(), p.getLabel(),
						// pivot.getId());
						// pivot.setLabel(p.getLabel());
					} else {
						// log.debug("=== Both pivot {} and pub {} get label {} from the publicationId of the pivot {}",
						// pivot.getId(), p.getId(), pivot.getId(), pivot.getId());
						pivot.setLabel(pivot.getId());
						p.setLabel(pivot.getId());
					}

					if (p.isReply()) {
						pivot.setReply(true);
					} else {
						if (p.getTitle() != null && pivot.getTitle() == null) {
							pivot.setTitle(p.getTitle());
						}
					}
					if (log.isTraceEnabled()) {
						log.trace("{} - {} ARE DUPLICATES", pivot.getId(), p.getId());
					}
				} else {
					if (log.isTraceEnabled()) {
						log.trace("{} - {} ARE NOT DUPLICATES", pivot.getId(), p.getId());
						// log.trace("Comparisons:\n"
						// + pivot.getLogLines().stream().collect(Collectors.joining("\n- ")));
					}
				}
			}
			progressReporter.accept("Working on %d for %d bibliographic items (marked %d duplicates)".formatted(year,
					noOfBibliographicItems, noOfDuplicates));
		}
	}

	private boolean containsDuplicateIds(List<BibliographicItem> bibliographicItems) {
		return !bibliographicItems.stream().map(BibliographicItem::getId).allMatch(new HashSet<>()::add);
	}

	private boolean containsOnlyBibliographicItemsWithoutPublicationYear(List<BibliographicItem> bibliographicItems) {
		return bibliographicItems.stream().filter(r -> r.getPublicationYear() == 0).count() == bibliographicItems
				.size();
	}

	private boolean containsBibliographicItemsWithoutId(List<BibliographicItem> bibliographicItems) {
		return bibliographicItems.stream().filter(r -> r.getId() == null).count() > 0L;
	}

	public String deduplicateOneFile(String inputFileName, String outputFileName, DeduplicationMode mode,
			Consumer<String> progressReporter) {
		progressReporter.accept("Reading file " + inputFileName);
		List<BibliographicItem> bibliographicItems = ioService.readBibliographicItems(inputFileName, progressReporter);

		String s = doSanityChecks(bibliographicItems, inputFileName);
		if (s != null) {
			progressReporter.accept(s);
			return s;
		}

		searchYearOneFile(bibliographicItems, progressReporter);

		if (mode == DeduplicationMode.MARK) {
			int numberWritten = ioService.writeMarkedBibliographicItems(bibliographicItems, inputFileName,
					outputFileName);
			long labeledBibliographicItems = bibliographicItems.stream().filter(r -> r.getLabel() != null).count();
			s = "DONE: DedupEndNote has written " + numberWritten + " bibliographic items with "
					+ labeledBibliographicItems + " duplicates marked in the Label field.";
			progressReporter.accept(s);
			return s;
		}

		progressReporter.accept("Enriching the " + bibliographicItems.size() + " deduplicated results");
		enrich(bibliographicItems);
		progressReporter.accept("Saving the " + bibliographicItems.size() + " deduplicated results");
		int numberWritten = ioService.writeDeduplicatedBibliographicItems(bibliographicItems, inputFileName,
				outputFileName);
		s = formatResultString(bibliographicItems.size(), numberWritten);
		progressReporter.accept(s);

		return s;
	}

	public String deduplicateTwoFiles(String newInputFileName, String oldInputFileName, String outputFileName,
			DeduplicationMode mode, Consumer<String> progressReporter) {
		// read the old bibliographicItems and mark them as present, then add the new bibliographicItems
		log.info("oldInputFileName: {}", oldInputFileName);
		log.info("newInputFileName: {}", newInputFileName);
		List<BibliographicItem> bibliographicItems = ioService.readBibliographicItems(oldInputFileName,
				progressReporter);

		String s = doSanityChecks(bibliographicItems, oldInputFileName);
		if (s != null) {
			progressReporter.accept(s);
			return s;
		}

		/*
		 * Put "-" before the IDs of the old bibliographicItems. In this way the labels of the bibliographicItems (used for
		 * identifying duplicate bibliographicItems) will be unique over both lists.
		 * When writing the deduplicated bibliographicItems for the second list, bibliographicItems with label "-..." can
		 * be skipped because they are duplicates of bibliographicItems from the first list.
		 * When MARK mode is set, these bibliographicItems are written.
		 * Because of this "-", the bibliographicItems which have duplicates in the first file (label = "-...")
		 * can be distinguished from bibliographicItems which have duplicates in the second file.
		 */
		bibliographicItems.forEach(r -> {
			r.setId("-" + r.getId());
			r.setPresentInOldFile(true);
		});

		List<BibliographicItem> newBibliographicItems = ioService.readBibliographicItems(newInputFileName,
				progressReporter);
		s = doSanityChecks(newBibliographicItems, newInputFileName);
		if (s != null) {
			progressReporter.accept(s);
			return s;
		}
		bibliographicItems.addAll(newBibliographicItems);
		log.info("Publications read from 2 files: {}", bibliographicItems.size());

		searchYearTwoFiles(bibliographicItems, progressReporter);

		if (mode == DeduplicationMode.MARK) {
			int numberWritten = ioService.writeMarkedBibliographicItems(bibliographicItems, newInputFileName,
					outputFileName);
			long numberLabeledBibliographicItems = bibliographicItems.stream()
					.filter(r -> r.getLabel() != null && !r.isPresentInOldFile()).count();
			s = "DONE: DedupEndNote has written %s bibliographic items with %d duplicates marked in the Label field."
					.formatted(numberWritten, numberLabeledBibliographicItems);
			progressReporter.accept(s);
			return s;
		}

		enrich(bibliographicItems);
		// Get the bibliographicItems from the new file that are not duplicates or not duplicates of bibliographicItems of the old
		// file
		List<BibliographicItem> filteredBibliographicItems = bibliographicItems.stream()
				.filter(r -> !r.isPresentInOldFile() && (r.getLabel() == null || !r.getLabel().startsWith("-")))
				.toList();
		log.error("Publications to write: {}", filteredBibliographicItems.size());
		int numberWritten = ioService.writeDeduplicatedBibliographicItems(filteredBibliographicItems, newInputFileName,
				outputFileName);
		s = "DONE: DedupEndNote removed %d bibliographic items from the new set, and has written %d bibliographic items."
				.formatted((newBibliographicItems.size() - numberWritten), numberWritten);
		progressReporter.accept(s);
		return s;
	}

	public @Nullable String doSanityChecks(List<BibliographicItem> bibliographicItems, String fileName) {
		/*
			FIXME: IOService::readBibliographicItems adds a Id for records without an input-ID in the switch case for "ER".
			This check containsBibliographicItemsWithoutId can never return the error-string?
		 */
		if (containsBibliographicItemsWithoutId(bibliographicItems)) {
			return "ERROR: The input file " + fileName
					+ " contains bibliographic items without IDs. The input file is not an Export as RIS-file from an EndNote library!";
		}
		/*
			FIXME: With an empty bibliographicItems List as input, the result TRUE returns a string that is not accurate
			See also issue #70
		*/
		if (containsOnlyBibliographicItemsWithoutPublicationYear(bibliographicItems)) {
			return "ERROR: All bibliographic items of the input file " + fileName
					+ " have no publication year. The input file is not an Export as RIS-file from an EndNote library!";
		}
		if (containsDuplicateIds(bibliographicItems)) {
			return "ERROR: The IDs of the bibliographic items of input file " + fileName
					+ " are not unique. The input file is not an Export as RIS-file from 1 EndNote library!";
		}
		return null;
	}

	private void enrich(List<BibliographicItem> bibliographicItems) {
		log.debug("Start enrich");
		// First the bibliographicItems with duplicates
		Map<String, List<BibliographicItem>> labelMap = bibliographicItems.stream()
				// when comparing 2 files, duplicates from the old file start with "-"
				.filter(r -> r.getLabel() != null && !r.getLabel().startsWith("-"))
				.collect(Collectors.groupingBy(BibliographicItem::getLabel));
		log.debug("Number of duplicate lists {}, and IDs of kept bibliographicItems: {}", labelMap.size(),
				labelMap.keySet());
		List<BibliographicItem> bibliographicItemList;
		if (!labelMap.isEmpty()) {
			for (Map.Entry<String, List<BibliographicItem>> entry : labelMap.entrySet()) {
				bibliographicItemList = entry.getValue();
				BibliographicItem bibliographicItemToKeep = bibliographicItemList.remove(0);
				log.debug("Kept: {}: {}", bibliographicItemToKeep.getId(),
						(bibliographicItemToKeep.getTitles().isEmpty() ? "(no titles found)"
								: bibliographicItemToKeep.getTitles().getFirst()));
				// Don't set keptPublication in compareSet(): trouble when multiple duplicates and no bibliographicItem year
				bibliographicItemList.stream().forEach(r -> r.setKeptBibliographicItem(false));

				// Reply and Retraction: replace the title with the longest title from the duplicates
				if (bibliographicItemToKeep.isReply() || (!bibliographicItemToKeep.isClinicalTrialGov()
						&& bibliographicItemToKeep.getTitle() != null)) {
					log.debug("BibliographicItem {} is a reply: ", bibliographicItemToKeep.getId());
					String longestTitle = bibliographicItemList.stream()
							// .filter(BibliographicItem::isReply)
							.map(r -> {
								log.debug("Reply {} has title: {}.", r.getId(), r.getTitle());
								return r.getTitle() != null ? r.getTitle() : r.getTitles().getFirst();
							}).max(Comparator.comparingInt(String::length)).orElse("");
					// There are cases where not all titles are recognized as replies -> bibliographicItem.title can be null
					if (bibliographicItemToKeep.getTitle() == null
							|| bibliographicItemToKeep.getTitle().length() < longestTitle.length()) {
						log.debug("REPLY: changing title {}\nto {}", bibliographicItemToKeep.getTitle(), longestTitle);
						bibliographicItemToKeep.setTitle(longestTitle);
					}
				}
				// Clinical trials from ClinicalTrials.gov: replace the title with the shortest title from the
				// duplicates
				if (bibliographicItemToKeep.isClinicalTrialGov()) {
					log.debug("BibliographicItem {} is a trial: ", bibliographicItemToKeep.getId());
					String shortestTitle = bibliographicItemList.stream().map(r -> {
						log.debug("Trial {} has title: {}.", r.getId(), r.getTitle());
						return r.getTitle() != null ? r.getTitle() : r.getTitles().getFirst();
					}).min(Comparator.comparingInt(String::length)).orElse("");
					// There are cases where bibliographicItem.title can be null (??)
					if (bibliographicItemToKeep.getTitle() == null
							|| bibliographicItemToKeep.getTitle().length() > shortestTitle.length()) {
						log.debug("Trial: changing title {}\nto {}", bibliographicItemToKeep.getTitle(), shortestTitle);
						bibliographicItemToKeep.setTitle(shortestTitle);
					}
				}

				// Gather all the DOIs
				final Set<String> dois = bibliographicItemToKeep.getDois();
				for (BibliographicItem p : bibliographicItemList) {
					if (!p.getDois().isEmpty()) {
						dois.addAll(p.getDois());
					}
				}
				if (!dois.isEmpty()) {
					bibliographicItemToKeep.setDois(dois);
				}

				// Add missing bibliographicItem year
				if (bibliographicItemToKeep.getPublicationYear() == 0) {
					log.debug("Reached bibliographicItem without publicationYear");
					bibliographicItemList.stream().filter(r -> r.getPublicationYear() != 0).findFirst()
							.ifPresent(r -> bibliographicItemToKeep.setPublicationYear(r.getPublicationYear()));
				}

				if (bibliographicItemToKeep.isCochrane() && bibliographicItemToKeep.getPagesOutput() != null) {
					// replaceCochranePageStart(bibliographicItemToKeep, bibliographicItemList);
					bibliographicItemToKeep.setPagesOutput(bibliographicItemToKeep.getPagesOutput().toUpperCase());
				}

				// Add missing pagesOutput
				if (bibliographicItemToKeep.getPagesOutput() == null
						|| bibliographicItemToKeep.getPagesOutput().isEmpty()) {
					log.debug("Reached bibliographicItem without pagesOutput: {}", bibliographicItemToKeep.getId());
					bibliographicItemList.stream().filter(r -> r.getPagesOutput() != null).findFirst().ifPresent(r -> {
						// publicationToKeep.setPageStart(r.getPageStart());
						// publicationToKeep.setPageEnd(r.getPageEnd());
						bibliographicItemToKeep.setPagesOutput(r.getPagesOutput());
					});
				}

				/*
				 * FIXME: Should empty authors be filled in from the duplicate set? See DOI
				 * 10.2298/sarh0902077c in test database, but the 2 duplicates have not the same
				 * author forms: "Culafic, D." (WoS) and "Dorde, Ć" (Scopus, error)
				 * Better example: 4605 in BIG_TEST without authors, 21391 with authors.
				 * But bibliographicItems can have different authors: in BIG_SET 4226 (none), 21471 (Banks ...), 36519 (Cabot ...)
				 */
			}
		}

		// Then the Cochrane bibliographicItems without duplicates
		for (BibliographicItem r : bibliographicItems) {
			if (r.isCochrane() && r.getLabel() == null && r.getPagesOutput() != null) {
				// replaceCochranePageStart(r, Collections.emptyList());
				r.setPagesOutput(r.getPagesOutput().toUpperCase());
			}
		}

		log.debug("Finished enrich");
	}

	public String formatResultString(int total, int totalWritten) {
		return "DONE: DedupEndNote has deduplicated " + total + " bibliographic items, has removed "
				+ (total - totalWritten) + " duplicates, and has written " + totalWritten + " bibliographic items.";
	}

	/*
	 * For 1 file:
	 * - order year descending
	 * - add empty years (year == 0 and not identified as duplicate yet) AFTER each year1
	 *
	 * Reason: we prefer the data (duplicate kept) which is most recent (e.g. complete bibliographicItem BEFORE ahead
	 * of print which is possibly from earlier year or without a year).
	 */
	public void searchYearOneFile(List<BibliographicItem> bibliographicItems, Consumer<String> progressReporter) {
		Map<Integer, List<BibliographicItem>> yearSets = bibliographicItems.stream()
				.collect(
						Collectors.groupingBy(BibliographicItem::getPublicationYear, TreeMap::new, Collectors.toList()))
				.descendingMap();

		Map<Integer, Integer> cumulativePercentages = getCumulativePercentages(bibliographicItems, yearSets);

		List<BibliographicItem> emptyYearlist = yearSets.remove(0);
		// log.debug("YearSets: {}", yearSets.keySet().stream().sorted().toList());
		yearSets.keySet().stream().forEach(year -> {
			List<BibliographicItem> yearSet = yearSets.get(year);
			if (yearSet != null) {
				if (emptyYearlist != null) {
					yearSet.addAll(emptyYearlist.stream().filter(r -> r.getLabel() == null).toList());
				}
				yearSet.addAll(yearSets.getOrDefault(year - 1, List.of()));
				progressReporter.accept("Working on " + year + " for " + yearSet.size() + " bibliographic items");
				compareSet(yearSet, year, true, progressReporter);
				progressReporter.accept("PROGRESS: " + cumulativePercentages.get(year));
			}
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
	public void searchYearTwoFiles(List<BibliographicItem> bibliographicItems, Consumer<String> progressReporter) {
		Map<Integer, List<BibliographicItem>> yearSets = bibliographicItems.stream().collect(
				Collectors.groupingBy(BibliographicItem::getPublicationYear, TreeMap::new, Collectors.toList()));
		Map<Integer, Integer> cumulativePercentages = getCumulativePercentages(bibliographicItems, yearSets);

		List<BibliographicItem> emptyYearlist = yearSets.remove(0);
		log.debug("YearSets: {}", yearSets.keySet().stream().toList());
		yearSets.keySet().stream().forEach(year -> {
			List<BibliographicItem> yearSet = new ArrayList<>();
			if (emptyYearlist != null) {
				yearSet.addAll(emptyYearlist.stream().filter(r -> r.getLabel() == null).toList());
			}
			yearSet.addAll(yearSets.get(year));
			yearSet.addAll(yearSets.getOrDefault(year + 1, List.of()));
			progressReporter.accept("Working on " + year + " for " + yearSet.size() + " bibliographic items");
			compareSet(yearSet, year, false, progressReporter);
			progressReporter.accept("PROGRESS: " + cumulativePercentages.get(year));
		});
	}

	private Map<Integer, Integer> getCumulativePercentages(List<BibliographicItem> bibliographicItems,
			Map<Integer, List<BibliographicItem>> yearSets) {
		Map<Integer, Integer> cumulativePercentages = new LinkedHashMap<>();
		int current = 0;
		Integer total = bibliographicItems.size();
		for (Map.Entry<Integer, List<BibliographicItem>> year : yearSets.entrySet()) {
			int simple = year.getValue().size();
			cumulativePercentages.put(year.getKey(), 100 * (simple + current) / total);
			current += simple;
		}
		// log.debug("cumulativePercentages: " + cumulativePercentages);
		return cumulativePercentages;
	}

}