package edu.dedupendnote.services;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.annotation.RequestScope;

import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.services.comparison.DefaultJournalComparisonService;
import edu.dedupendnote.services.comparison.DefaultPagesComparisonService;
import edu.dedupendnote.services.comparison.FieldComparators;
import lombok.extern.slf4j.Slf4j;

@Service
@RequestScope
@Slf4j
public class DeduplicationService {

	private final FieldComparators fieldComparators;
	private final BibliographicItemReader bibliographicItemReader;
	private final BibliographicItemWriter bibliographicItemWriter;

	/*
		AuthorExperimentsTests builds it's own DeduplicationService. 
		This int maxRecords is NOT initialized by @Value in that case, therefore  also initialized in Java ("= 100000").
	 */
	@Value("${dedup.max-records:100000}")
	private int maxRecords = 100000;

	// the DOIs have been lowercased
	public static final Pattern COCHRANE_DOI_PATTERN = Pattern.compile("^.*10.1002/14651858.([a-z][a-z]\\d+).*",
			Pattern.CASE_INSENSITIVE);

	// @formatter:off
	/*
	 * Procedure for 1 file: read → compare → write.
	 * Comparison assigns labels (groups items into duplicate sets). The output phase
	 * (writeBibliographicItems) synthesises a representation for each set (REMOVE mode)
	 * or annotates all items with their set membership (MARK mode).
	 *
	 * Procedure for 2 files:
	 * Old-file items have their id negated (id < 0). New-file items keep positive ids.
	 * After comparison, new-file items with label < 0 are duplicates of old-file items
	 * and are excluded from the REMOVE output by the writer's enrich() pass.
	 * MARK mode writes all new-file items (old-file items are excluded by the writer's
	 * recordIdMap which filters id > 0).
	 */
	// @formatter:on

	public DeduplicationService(FieldComparators fieldComparators, BibliographicItemReader bibliographicItemReader,
			BibliographicItemWriter bibliographicItemWriter) {
		this.bibliographicItemReader = bibliographicItemReader;
		this.bibliographicItemWriter = bibliographicItemWriter;
		this.fieldComparators = fieldComparators;
	}

	public void compareSet(List<BibliographicItem> bibliographicItems, Integer year, boolean descending,
			Consumer<String> progressReporter) {
		int noOfBibliographicItems = bibliographicItems.size();
		int noOfDuplicates = 0;
		// compareSet runs entirely on one worker thread; cache it so the cancellation
		// checks below are a single volatile read (allocation-free on the O(n²) hot path).
		Thread current = Thread.currentThread();
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
			if (current.isInterrupted()) {
				throw new CancelledException("ERROR: Deduplication was cancelled by the user.");
			}
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
				// Per-pair cancellation: observe the interrupt (user cancel or run timeout)
				// within one comparison rather than only between pivots.
				if (current.isInterrupted()) {
					throw new CancelledException("ERROR: Deduplication was cancelled by the user.");
				}
				map.put("isSameDois", null);
				// log.atDebug().setMessage("Clear results previous comparison {}")
				// .addArgument(() -> pivot.getLogLines().removeAll(bibliographicItem.getLogLines())).log();
				if (log.isTraceEnabled()) {
					log.trace("\nStarting comparison {} - {}", pivot.getId(), p.getId());
				}
				if (fieldComparators.pages().compare(p, pivot, map) && fieldComparators.authors().compare(p, pivot)
						&& fieldComparators.titles().compare(p, pivot)
						&& (DefaultPagesComparisonService.compareSameDois(p, pivot, map.get("isSameDois"))
								|| DefaultJournalComparisonService.compareIssns(p, pivot, map.get("isSameDois"))
								|| fieldComparators.journals().compare(p, pivot, map.get("isSameDois")))) {

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

	private void checkRecordCap(Path path) {
		try {
			long count = bibliographicItemReader.countRecords(path);
			if (count > maxRecords) {
				throw new RecordCapExceededException("ERROR: Input file " + path.getFileName() + " contains " + count
						+ " bibliographic items, which exceeds the maximum of " + maxRecords + ".");
			}
		} catch (IOException e) {
			// unreadable file — readBibliographicItems will handle it
		}
	}

	private boolean containsDuplicateIds(List<BibliographicItem> bibliographicItems) {
		return !bibliographicItems.stream().map(b -> b.getId()).allMatch(new HashSet<>()::add);
	}

	public String deduplicateOneFile(Path inputPath, DeduplicationMode mode, Consumer<String> progressReporter) {
		Path outputPath = UtilitiesService.createPath(inputPath, mode.filenameSuffix(), "txt");
		progressReporter.accept("Reading file " + inputPath.getFileName());
		checkRecordCap(inputPath);
		List<BibliographicItem> bibliographicItems = bibliographicItemReader.readBibliographicItems(inputPath,
				progressReporter);
		doSanityChecks(bibliographicItems, inputPath);
		progressReporter.accept("Read " + bibliographicItems.size() + " bibliographic items");

		searchYearOneFile(bibliographicItems, progressReporter);

		// @formatter:off
		return switch (mode) {
			case MARK -> {
				int numberWritten = bibliographicItemWriter.writeBibliographicItems(bibliographicItems, inputPath,
						outputPath, mode, progressReporter);
				long labeledBibliographicItems = bibliographicItems.stream().filter(r -> r.getLabel() != null).count();
				// TODO: use formatResultString which accepts a mode argument?
				String s = "DONE: DedupEndNote has written " + numberWritten + " bibliographic items with "
						+ labeledBibliographicItems + " duplicates marked in the Label field.";
				progressReporter.accept(s);
				yield s;
			}
			case REMOVE -> {
				int numberWritten = bibliographicItemWriter.writeBibliographicItems(bibliographicItems,
						inputPath, outputPath, mode, progressReporter);
				String s = formatResultString(bibliographicItems.size(), numberWritten);
				progressReporter.accept(s);
				yield s;
			}
		};
		// @formatter:on
	}

	public String deduplicateTwoFiles(Path newInputPath, Path oldInputPath, DeduplicationMode mode,
			Consumer<String> progressReporter) {
		Path outputPath = UtilitiesService.createPath(newInputPath, mode.filenameSuffix(), "txt");
		// read the old bibliographicItems and mark them as present, then add the new bibliographicItems
		log.info("oldInputPath: {}", oldInputPath);
		log.info("newInputPath: {}", newInputPath);
		try {
			long combinedCount = bibliographicItemReader.countRecords(oldInputPath)
					+ bibliographicItemReader.countRecords(newInputPath);
			if (combinedCount > maxRecords) {
				throw new RecordCapExceededException("ERROR: The two input files together contain " + combinedCount
						+ " bibliographic items, which exceeds the maximum of " + maxRecords + ".");
			}
		} catch (IOException e) {
			// unreadable file — readBibliographicItems will handle it
		}
		List<BibliographicItem> bibliographicItems = bibliographicItemReader.readBibliographicItems(oldInputPath,
				progressReporter);
		doSanityChecks(bibliographicItems, oldInputPath);

		// Negate IDs of old-file items so labels remain unique across both lists.
		// Old-file items: id < 0. New-file items: id > 0.
		// A new-file item whose duplicate-set representative is an old-file item gets label < 0;
		// enrich() in the writer marks such items as not-kept so they are excluded from REMOVE output.
		bibliographicItems.forEach(r -> r.setId(-r.getId()));

		List<BibliographicItem> newBibliographicItems = bibliographicItemReader.readBibliographicItems(newInputPath,
				progressReporter);
		doSanityChecks(newBibliographicItems, newInputPath);
		bibliographicItems.addAll(newBibliographicItems);
		log.info("Publications read from 2 files: {}", bibliographicItems.size());
		progressReporter.accept("Read " + bibliographicItems.size() + " bibliographic items");

		searchYearTwoFiles(bibliographicItems, progressReporter);

		// @formatter:off
		return switch (mode) {
			case MARK -> {
				int numberWritten = bibliographicItemWriter.writeBibliographicItems(bibliographicItems, newInputPath,
						outputPath, mode, progressReporter);
				long numberLabeledBibliographicItems = bibliographicItems.stream()
						.filter(r -> r.getLabel() != null && r.getId() > 0).count();
				String s = "DONE: DedupEndNote has written %s bibliographic items with %d duplicates marked in the Label field."
						.formatted(numberWritten, numberLabeledBibliographicItems);
				progressReporter.accept(s);
				yield s;
			}
			case REMOVE -> {
				int numberWritten = bibliographicItemWriter.writeBibliographicItems(bibliographicItems,
						newInputPath, outputPath, mode, progressReporter);
				String s = "DONE: DedupEndNote removed %d bibliographic items from the new set, and has written %d bibliographic items."
						.formatted((newBibliographicItems.size() - numberWritten), numberWritten);
				progressReporter.accept(s);
				yield s;
			}
		};
		// @formatter:on
	}

	public void doSanityChecks(List<BibliographicItem> bibliographicItems, Path inputPath) {
		/*
		 * "containsBibliographicItemsWithoutId" check removed: id is now a primitive int.
		 * BibliographicItemReader always assigns an id >= 1; InvalidRisFileException is thrown for non-numeric ID fields.
		 *
		 * "containsOnlyBibliographicItemsWithoutPublicationYear" check removed: a dataset where
		 * every record has publicationYear == 0 (no PY field, or year < 1850) is acceptable —
		 * year-bucketing simply groups everything in the year-0 bucket and comparisons still run.
		 */
		if (containsDuplicateIds(bibliographicItems)) {
			throw new DuplicateIdsException(
					"ERROR: The IDs of the bibliographic items of input file " + inputPath.getFileName()
							+ " are not unique. The input file is not an Export as RIS-file from 1 EndNote library!");
		}
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