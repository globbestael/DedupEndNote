package edu.dedupendnote.services;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.http.HttpSession;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.context.annotation.SessionScope;

import edu.dedupendnote.domain.Record;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@SessionScope
public class DeduplicationService {
	
	// @formatter:off
	/*
	 * Procedure for 1 file:
	 * 
	 * 1. Do preliminary checks on the input file (EndNote IDs are present and unique) and exit when the checks do not pass. 
	 * 2. Read all EndNote records from inputfile and make Record objects only with the fields relevant for deduplication.
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
	 * 2. Read all EndNote records from this inputfile and make Record objects only with the fields relevant for deduplication.
	 *    Normalize fields as much as possible while calling the setters.
	 *    Normalize the rest as the last field (EndNote ER field) is read.
	 *    Alter the ID by prefixing them with "-" (to distinguish them from the IDs of the second file and making them unique over both files).
	 *    All OLD records start with kepRecord = false and presentInOldFile = true.
	 * 3. Do preliminary checks on input file for new records (EndNote IDs are present and unique) and exit when the checks do not pass. 
	 * 4. Read all EndNote records from this inputfile and make Record objects only with the fields relevant for deduplication.
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

	protected AuthorsComparator authorsComparator;
	private JaroWinklerSimilarity jws = new JaroWinklerSimilarity();
	private HttpSession session;
	private IOService ioService;

	/*
	 * Thresholds for Jaro-Winkler Similarity
	 * The thresholds for AUTHOR are part of the AuthorComparator implementations
	 */
	public static final Double JOURNAL_SIMILARITY_NO_REPLY = 0.90;
	public static final Double JOURNAL_SIMILARITY_REPLY = 0.93;
	
	public static final Double TITLE_SIMILARITY_PHASE = 0.96;
	public static final Double TITLE_SIMILARITY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.94;
	public static final Double TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.90;

	public DeduplicationService() {
		this.authorsComparator = new DefaultAuthorsComparator();
		this.ioService = new IOService();
	}
	
	public DeduplicationService(AuthorsComparator authorsComparator) {
		this.authorsComparator = authorsComparator;
		this.ioService = new IOService();
	}
	
	public AuthorsComparator getAuthorsComparator() {
		return authorsComparator;
	}

	// split into deduplicateOneFileAsync(...) and duplicateOneFile(...) for testability
	@Async
	public ListenableFuture<String> deduplicateOneFileAsync(String inputFileName, String outputFileName, boolean markMode, HttpSession session) {
		String s = deduplicateOneFile(inputFileName, outputFileName, markMode, session);
		return new AsyncResult<>(s);
	}

	// split into deduplicateTwoFilesAsync(...) and duplicateTwoFiles(...) for testability
	@Async
	public ListenableFuture<String> deduplicateTwoFilesAsync(String newInputFileName, String oldInputFileName, String outputFileName, boolean markMode, HttpSession session) {
		String s = deduplicateTwoFiles(newInputFileName, oldInputFileName, outputFileName, markMode, session);
		return new AsyncResult<>(s);
	}

	public String deduplicateOneFile(String inputFileName, String outputFileName, boolean markMode, HttpSession session) {
		this.session = session;
		List<Record> records = ioService.readRecords(inputFileName);

		String s = doSanityChecks(records, inputFileName);
		if (s != null) {
			return updateSession(session, s);
		}

		searchYearOneFile(records, session);
		
		if (markMode) { // no enrich(), and add / overwrite LB (label) field
			int numberWritten = ioService.writeMarkedRecords(records, inputFileName, outputFileName);
			long labeledRecords = records.stream().filter(r -> r.getLabel() != null).count();
			return updateSession(session, "DONE: DedupEndNote has written " + numberWritten + " records with " + labeledRecords + " duplicates marked in the Label field.");
		}

		enrich(records);
		int numberWritten = ioService.writeDeduplicatedRecords(records, inputFileName, outputFileName);
		s = formatResultString(records.size(), numberWritten);
		updateSession(session, s);	// not return yet
		
		return s;
	}

	public String deduplicateTwoFiles(String newInputFileName, String oldInputFileName, String outputFileName, boolean markMode, HttpSession session) {
		this.session = session;
		// read the old records and mark them as present, then add the new records
		log.info("oldInputFileName: {}", oldInputFileName);
		log.info("newInputFileName: {}", newInputFileName);
		List<Record> records = ioService.readRecords(oldInputFileName);
		
		String s = doSanityChecks(records, oldInputFileName);
		if (s != null) {
			return updateSession(session, s);
		}

		records.forEach(r -> {
			// Put "-" before the IDs of the old records. In this way the labels of the records (used for identifying duplicate records) will be unique over both lists.
			// When writing the deduplicated records for the second list, records with label "-..." can be skipped
			// because they are duplicates of records from the first list.
			// When markMode is set, these records are written. Because of this "-", de records which have duplicates in the first file (label = "-...")
			// can be distinguised from records which have duplicates in the second file.
			r.setId("-" + r.getId());
			r.setPresentInOldFile(true);
		});
		
		List<Record> newRecords = ioService.readRecords(newInputFileName);
		s = doSanityChecks(newRecords, newInputFileName);
		if (s != null) {
			return updateSession(session, s);
		}
		records.addAll(newRecords);
		log.info("Records read from 2 files: {}", records.size());
		
		searchYearTwoFiles(records, session);
		
		if (markMode) { // no enrich(), and add / overwrite LB (label) field
			int numberWritten = ioService.writeMarkedRecords(records, newInputFileName, outputFileName);
			long labeledRecords = records.stream()
										 .filter(r -> r.getLabel() != null && r.isPresentInOldFile() == false)
										 .count();
			return updateSession(session, "DONE: DedupEndNote has written " + numberWritten + " records with " + labeledRecords + " duplicates marked in the Label field.");
		}

		enrich(records);
		// Get the records from the new file that are not duplicates or not duplicates of records of the old file 
		List<Record> filteredRecords = records.stream()
				.filter(r -> r.isPresentInOldFile() == false && (r.getLabel() == null || ! r.getLabel().startsWith("-")))
				.collect(Collectors.toList());
		log.error("Records to write: {}", filteredRecords.size());
		int numberWritten = ioService.writeDeduplicatedRecords(filteredRecords, newInputFileName, outputFileName);
		return updateSession(session, "DONE: DedupEndNote removed " + (newRecords.size() - numberWritten) + " records from the new set, and has written " + numberWritten + " records.");
	}

	private void enrich(List<Record> records) {
		log.error("Start enrich");
		Map<String, List<Record>> labelMap = records.stream()
				.filter(r -> r.getLabel() != null && ! r.getLabel().startsWith("-")) // when comparing 2 files, duplicates from the old file start with "-"
				.collect(Collectors.groupingBy(Record::getLabel));
		log.debug("Number of duplicate lists {}, en IDs of kept records: {}", labelMap.keySet().size(), labelMap.keySet());
		List<Record> recordList;
		if (labelMap.keySet().size() > 0) {
			for (String l : labelMap.keySet()) {
				recordList = labelMap.get(l);
				Record recordToKeep = recordList.remove(0);
				log.debug("Kept: {}: {}", recordToKeep.getId(), recordToKeep.getTitles().get(0));
				// Don't set keptRecord in compareSet(): trouble when multiple duplicates and no publication year 
				recordList.stream().forEach(r -> r.setKeptRecord(false));

				// Reply: replace the title with the longest title from the duplicates 
				if (recordToKeep.isReply()) {
					log.debug("Record {} is a reply: ", recordToKeep.getId());
					String longestTitle = recordList.stream()
//							.filter(Record::isReply)
							.map(r -> { 
								log.debug("Reply {} has title: {}.", r.getId(), r.getTitle());
								return r.getTitle() != null ? r.getTitle() :  r.getTitles().get(0);
							})
							.max(Comparator.comparingInt(String::length))
							.orElse("");
					// There are cases where not all titles have are recognized as replies -> record.title can be null  
					if (recordToKeep.getTitle() == null || recordToKeep.getTitle().length() < longestTitle.length()) {
						log.debug("REPLY: changing title {}\nto {}", recordToKeep.getTitle(), longestTitle);
						recordToKeep.setTitle(longestTitle);
					}
				}
				
				// Gather all the DOIs
				final Map<String, Integer> dois = recordToKeep.getDois();
				recordList.stream().forEach(r -> {
					if (! r.getDois().isEmpty()) {
						r.getDois().forEach((k,v) -> dois.putIfAbsent(k, v)); 
					}
				});
				// FIXME: unnecessary?
				if (!dois.isEmpty()) {
					recordToKeep.setDois(dois);
				}
				
				// Add missing publication year
				if (recordToKeep.getPublicationYear() == 0) {
					log.debug("Reached record without publicationYear");
					recordList.stream()
						.filter(r -> r.getPublicationYear() != 0)
						.findFirst()
						.ifPresent(r -> recordToKeep.setPublicationYear(r.getPublicationYear()));
				}
				
				// Add missing startpages (and endpages)
				if (recordToKeep.getPageStart() == null) {
					log.debug("Reached record without pageStart: {}", recordToKeep.getAuthors());
					recordList.stream()
						.filter(r -> r.getPageStart() != null)
						.findFirst()
						.ifPresent(r -> { recordToKeep.setPageStart(r.getPageStart()); recordToKeep.setPageEnd(r.getPageEnd()); });
				}
				
				/*
				 *  FIXME: Should empty authors be filled in from the duplicate set?
				 *  See DOI 10.2298/sarh0902077c in test database, but the 2 duplicates have not the same author forms: "Culafic, D." (WoS) and "Dorde, Ć" (Scopus, error)  
				 */
			}
		}
		log.info("Finished enrich");
	}

	/*
	 * For 1 file:
	 * - order year descending
	 * - add empty years (year == 0 and not identified as duplicate yet) after each year1
	 * Reason: we prefer the data (duplicate kept) which is most recent
	 * (e.g. complete publication BEFORE ahead of print which is possibly from earlier year or without a year).  
	 */
	public void searchYearOneFile(List<Record> records, HttpSession session) {
		Map<Integer, List<Record>> yearSets = records.stream()
				.collect(Collectors.groupingBy(Record::getPublicationYear));
		
		List<Record> emptyYearlist = yearSets.remove(0);
		log.debug("YearSets: {}", yearSets.keySet().stream().sorted().collect(Collectors.toList()));
		yearSets.keySet().stream().sorted(Comparator.reverseOrder()).forEach(year -> {
			List<Record> yearSet = yearSets.get(year);
			if (emptyYearlist != null) {
				yearSet.addAll(emptyYearlist.stream().filter(r -> r.getLabel() == null).collect(Collectors.toList()));
			}
			List<Record> previousYearSet = yearSets.get(year - 1);
			if (previousYearSet != null) {
				yearSet.addAll(previousYearSet);
			}
			updateSession(session, "Working on " + year + " for " + yearSet.size() + " records");
			compareSet(yearSet, year, true, session);
		});
		return;
	}

	/*
	 * For 2 files:
	 * - order year ascending
	 * - add empty years (year == 0 and not identified as duplicate yet) before each year1
	 * Reason: we oldest data should set the label for a diplicate set  
	 * (e.g. ahead of print (which is possibly from earlier year or without a year and more probably in the old file than the new file) BEFORE the complate data  
	 */
	public void searchYearTwoFiles(List<Record> records, HttpSession session) {
		Map<Integer, List<Record>> yearSets = records.stream()
				.collect(Collectors.groupingBy(Record::getPublicationYear));
		
		List<Record> emptyYearlist = yearSets.remove(0);
		log.debug("YearSets: {}", yearSets.keySet().stream().sorted().collect(Collectors.toList()));
		yearSets.keySet().stream().sorted().forEach(year -> {
			List<Record> yearSet = new ArrayList<>();
			if (emptyYearlist != null) {
				yearSet.addAll(emptyYearlist.stream().filter(r -> r.getLabel() == null).collect(Collectors.toList()));
			}
			yearSet.addAll(yearSets.get(year));
			List<Record> nextYearSet = yearSets.get(year + 1);
			if (nextYearSet != null) {
				yearSet.addAll(nextYearSet);
			}
			updateSession(session, "Working on " + year + " for " + yearSet.size() + " records");
			compareSet(yearSet, year, false, session);
		});
		return;
	}

	public void compareSet(List<Record> records, Integer year, boolean descending, HttpSession session) {
		int listSize = records.size();
		int doubleSize = 0;
		while (records.size() > 1) {
			Record record = records.remove(0);
			if (descending && record.getPublicationYear() < year) {
				break; // only records of year1 should be compared to records of year1 and year2, The records of year2 will be compared in the next pair of years.
			} else if (! descending && record.getPublicationYear() != 0 && record.getPublicationYear() > year) {
				break;
			}
			log.debug("Comparing " + records.size() + " records to: " + record.getId() + " : " + record.getTitles().get(0));
			// FIXME: which is the most discriminating comparison? Short circuiting (and performance)
			List<Record> doubles = records.stream()
					.filter(r -> compareStartPageOrDoi(r, record) == true
								// && compareAuthors(r, record) == true
								&& authorsComparator.compare(r, record) == true
								&& compareTitles(r, record) == true
								&& (compareIssns(r, record) == true || compareJournals(r, record) == true))
					.map(r -> {	// Label is used later in enrich() to recreate the duplicate lists, and is used / exported if markMode is set
						// If the record was already a duplicate, use its label the new duplicates found, otherwise its ID
						if (r.getLabel() == null) {
							if (record.getLabel() != null) {
								log.debug("==> SETTING LABEL ALREADY PRESENT: {} has label {} and now sets label of duplicate to {}", record.getId(), record.getLabel(), r.getId());
								r.setLabel(record.getLabel());
							} else {
								r.setLabel(record.getId());
							}
						} else {
							// FIXME: does this ever occur?
							log.debug("==> LABEL ALREADY PRESENT: {} has label {} and should also get label {}", r.getId(), r.getLabel(), record.getId());
						}
						if (r.isReply()) {
							record.setReply(true);
						}
						log.debug("1. setting label {} to record {}", r.getLabel(), r.getId());
						return r;
					})
					.collect(Collectors.toList());
			if (!doubles.isEmpty()) {
				// @formatter:off
				/*
				 * The duplicates (doubles) are removed from the current set. Some duplicates may be missed by this action!!!
				 * e.g.:
				 * - record 1: page 63  + doi 10.4081/hr.2015.5927
				 * - record 2: page 519 + doi 10.4081/hr.2015.5927	==> duplicate because of doi
				 * - record 3: page 519 + doi (empty)				==> not a duplicate because 63 != 519
				 * Not removing the duplicates would have serious performance problems, AND would make adding the labels more complex.
				 * 
				 * TODO: Wouldn't this be solved if, whenever a duplicate is found,
				 * - DOIs from the duplicate found were added to the first record
				 * - the same for journal titles?
				 * - the same for titles??? 
				 */
				// @formatter:on
				// FIXME: removing the doubles makes the program faster (and less complicated)
				// but misses duplicates when the not first duplicate is sufficiently close to the following records, but the first one is not?
				// See test set: Author Bail, JP ...
				// records.removeAll(doubles); // no need to compare the doubles later on in this list
				// This could be set in the map (higher), but then would be set for each duplicate found
				if (record.getLabel() == null) {
					record.setLabel(record.getId());
					log.debug("2. setting label {} to record {}", record.getLabel(), record.getId());
				}
				doubleSize += doubles.size();
				updateSession(session, "Working on " + year + " for " + listSize + " records (marked " + doubleSize + " duplicates)");
			}
		}
	}

	// All unique titles and their reverse are compared
	public boolean compareTitles(Record r1, Record r2) {
		log.debug("Comparing " + r1.getId() + ": " + r1.getTitles().get(0) + "\nto " + r2.getId() + ": " + r2.getTitles().get(0));
		if (r1.isReply() || r2.isReply()) {
			return true;
		}
		Double similarity = 0.0;
		List<String> titles1 = r1.getTitles();
		List<String> titles2 = r2.getTitles();
		boolean sufficientStartPages = (r1.getPageStartForComparison() != null && r2.getPageStartForComparison() != null);
		boolean sufficientDois = (! r1.getDois().isEmpty() && ! r2.getDois().isEmpty());
		boolean isPhase = r1.isPhase() || r2.isPhase();
		
		for (String title1 : titles1) {
			for (String title2 : titles2) {
				similarity = jws.apply(title1, title2);
				if (isPhase) { // return result of comparison of only the first title variant
					log.debug("{} and {}:\n- {}\n- {}", r1.getId(), r2.getId(), title1, title2);
					return similarity > TITLE_SIMILARITY_PHASE;
				}
				if ((sufficientStartPages || sufficientDois) && similarity > TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS) {
					log.debug("Found comparable title (with pages or DOI) " + r1.getTitles().get(0) + " == " + r2.getTitles().get(0));
					return true;
				} else if (!(sufficientStartPages || sufficientDois)) {
					return similarity > TITLE_SIMILARITY_INSUFFICIENT_STARTPAGES_AND_DOIS;
				}
			}
		}
		return false;
	}

	/*
	 *  Comparing starting page before DOI is maybe faster than the other way around. 
	 *  But wouldn't more duplicates be found if DOIs are compared before starting pages?
	 *  Mind the case where a complete set of conference abstracts has the same DOI.
	 */
	public boolean compareStartPageOrDoi(Record r1, Record r2) {
		log.debug("Comparing " + r1.getId() + ": " + r1.getPageStartForComparison() + " to " + r2.getId() + ": " + r2.getPageStartForComparison());
		Map<String, Integer> dois1 = r1.getDois();
		Map<String, Integer> dois2 = r2.getDois();
		boolean sufficientStartPages = (r1.getPageStartForComparison() != null && r2.getPageStartForComparison() != null);
		boolean sufficientDois = (! dois1.isEmpty() && ! dois2.isEmpty());
		
		if (! sufficientStartPages && ! sufficientDois) {
			log.debug("At least one starting page AND at least one DOI are missing");
			return true; // no useful comparison possible
		}
		if (sufficientStartPages && r1.getPageStartForComparison().equals(r2.getPageStartForComparison())) {
			log.debug("Same starting pages");
			return true;
		}
		if (sufficientDois) {
			for (String d : dois1.keySet()) {
				if (dois2.containsKey(d)) {
					log.debug("One or more DOIs are the same: '{}' and '{}'", dois1, dois2);
					return true;
				}
			}
		}
		log.debug("Starting pages and DOIs are different");
		return false;
	}

	public static class DefaultAuthorsComparator implements AuthorsComparator {
		private JaroWinklerSimilarity jws = new JaroWinklerSimilarity();
		public Double similarity = 0.0;
		public static final Double AUTHOR_SIMILARITY_NO_REPLY = 0.67;
		public static final Double AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS = 0.75;
		public static final Double AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS = 0.80;
		
		/*
		 * See AuthorVariantsExperimentsTest for possible enhancements.
		 */
		@Override
		public boolean compare(Record r1, Record r2) {
			// log.error("Using the default AuthorComparator");
			similarity = 0.0;
			boolean isReply = (r1.isReply() || r2.isReply());
			boolean sufficientStartPages = (r1.getPageStartForComparison() != null && r2.getPageStartForComparison() != null);
			boolean sufficientDois = (! r1.getDois().isEmpty() && ! r2.getDois().isEmpty());

			if (r1.getAllAuthors().isEmpty() || r2.getAllAuthors().isEmpty()) {
				// Because Anonymous AND Reply would only compare on journals (and maybe SP/DOIs) (see "MedGenMed Medscape General Medicine" articles in Cannabis test set)
				// Because Anonymous AND no SP or DOI would only compare on title and journals (see "Abstracts of 16th National Congress of SIGENP" articles in Joost problem set)
				if (isReply || (! sufficientStartPages && ! sufficientDois)) {
					return false;
				}
				return true;
			}
			
			for (String authors1 : r1.getAllAuthors()) {
				for (String authors2 : r2.getAllAuthors()) {
					similarity = jws.apply(authors1, authors2);
					if (isReply) { 
						// TODO: do we have examples of this case?
						if (!(sufficientStartPages || sufficientDois) && similarity > AUTHOR_SIMILARITY_REPLY_INSUFFICIENT_STARTPAGES_AND_DOIS) { 
							return true;
						} else if ((sufficientStartPages || sufficientDois) && similarity > AUTHOR_SIMILARITY_REPLY_SUFFICIENT_STARTPAGES_OR_DOIS) {
							return true;
						}
					// FIXME: Why isReply?
					} else if (! isReply && similarity > AUTHOR_SIMILARITY_NO_REPLY) {
						return true;
					};
				}
			}
			return false;
		}

		@Override
		public Double getSimilarity() {
			return similarity;
		}
	}
	
	public boolean compareIssns(Record r1, Record r2) {
		// log.debug("Comparing for ISSN " + r1.getId() + ": " + r1.getTitles().get(0) + "\nto " + r2.getId() + ": " + r2.getTitles().get(0));
		if (listsContainSameString(r1.getIssns(), r2.getIssns())) {
			return true;
		};
		return false;
	}

	// FIXME: is Apache Commons CollectionUtils better? 
	private boolean listsContainSameString(List<String> list1, List<String> list2) {
		if (list1.isEmpty() || list2.isEmpty()) {
			return false;
		}
		List<String> common = new ArrayList<>(list1);
		common.retainAll(list2);
		return !common.isEmpty();
	}

	/*
	 * compareJournals
	 * 
	 * Paths not chosen: 
	 * - Creation of sets of journalPatterns for the whole set of records.
	 *   This might be overkill, since the comparison by journal is the last of all comparisons between any two records 
	 * - "AJR American Journal of Radiology": split into "AJR" and "American Journal of Radiology"
	 *   compareJournals_FirstWithStartingInitialism(...) will create a pattern on A, J en R which will find the second title.
	 */
	public boolean compareJournals(Record r1, Record r2) {
		Set<String> set1 = r1.getJournals();
		Set<String> set2 = r2.getJournals();
		boolean isReply = (r1.isReply() || r2.isReply());
		
		if (set1.isEmpty() || set2.isEmpty()) {
			log.debug("One of the records had no journal for '{}' and '{}'", set1, set2);
			return false;
		}
		
		Set<String> commonJournals = new HashSet<>(set1);
		commonJournals.retainAll(set2);
		if (! commonJournals.isEmpty()) {
			log.debug("Some journals are common: '{}' and '{}'", set1, set2);
			return true;
		}
		
		for (String s1 : set1) {
			for (String s2 : set2) {
				if (s1.startsWith("http") && s2.startsWith("http")) {
					if (! s1.equals(s2)) {	// JaroWinkler 0.9670930232558139 for 'Https://clinicaltrials.gov/show/nct00830466' and 'Https://clinicaltrials.gov/show/nct00667472'
						continue;
					}
				}
				Double similarity = jws.apply(s1.toLowerCase(), s2.toLowerCase());
				log.debug("Similarity: {} for '{}' and '{}'", similarity, s1, s2);
				 // Make comparison stricter for Reply-titles because titles haven't been compared ("Annals of Hepatology" - "Annals of Oncology": 0.916)
				if (isReply && similarity > JOURNAL_SIMILARITY_REPLY) { 
					return true;
				} else if (! isReply && similarity > JOURNAL_SIMILARITY_NO_REPLY) {
					return true;
				}

				if (s1.toLowerCase().charAt(0) != s2.toLowerCase().charAt(0)) {
					continue;
				}
				/*
				 * "Hepatology" and "Hepatology International" are considered the same
				 */
				if (compareJournals_FirstAsAbbreviation(s1, s2)) {
					return true;
				}
				if (compareJournals_FirstAsAbbreviation(s2, s1)) {
					return true;
				}
				
				if (s1.toUpperCase().equals(s1) && compareJournals_FirstAsInitialism(s1, s2)) {
					return true;
				}
				if (s2.toUpperCase().equals(s2) && compareJournals_FirstAsInitialism(s2, s1)) {
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
		log.debug("No Success 5 for '{}' and '{}'", set1, set2);
		return false;
	}

	// Searching 'BMJ' as "^B.*(\b|-|)M.*(\b|-|)J.*"
	private boolean compareJournals_FirstAsInitialism(String s1, String s2) {
//		String abbreviation = s1.chars()
//				.mapToObj(c -> String.valueOf((char) c))
//				.collect(Collectors.joining(" "));
//		Pattern patternShort2 = Pattern.compile("^" + abbreviation.replaceAll(" ", ".*(\\\\b|-|)") + ".*", Pattern.CASE_INSENSITIVE);
		String patternString = s1.chars()
				.mapToObj(c -> String.valueOf((char) c))
				.collect(Collectors.joining(".*(\\b|-|)", "^", ".*"));
		Pattern patternShort2 = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
		log.debug("Pattern INITIALISM for '{}': {} for '{}'", s1, patternShort2.toString(), s2);
		Matcher matcher = patternShort2.matcher(s2);
		if (matcher.matches()) {
			// log.debug("Pattern INITIALISM for '{}': {} for '{}'", s1, patternShort2.toString(), s2);
			return true;
		}
		return false;
	}
	
	// Search 'Ann Fr Anesth Reanim' as "^Ann.*(\b|-|)Fr.*(\b|-|)Anesth.*(\b|-|)Reanim.*"
	private boolean compareJournals_FirstAsAbbreviation(String j1, String j2) {
		Pattern pattern = Pattern.compile("^" + j1.replaceAll(" ", ".*(\\\\b|-|)") + ".*", Pattern.CASE_INSENSITIVE);
		log.debug("Pattern short 1 for '{}': {} for '{}'", j1, pattern.toString(), j2);
		Matcher matcher = pattern.matcher(j2);
		if (matcher.matches()) {
			log.debug("Pattern ABBREVIATION for '{}': {} for '{}'", j1, pattern.toString(), j2);
			return true;
		}
		return false;
	}
	
	// Search 'AJR Am J Roentgenol' as "^A.*(\b|-|)J.*(\b|-|)R.*"
	// Search 'JNCCN Journal of the National Comprehensive Cancer Network' as "..." comparing with 'Journal of the National Comprehensive Cancer Network'
	// Search 'Rofo' as "^R.*(\b|-|)o.*(\b|-|)f.*(\b|-|)o.*"
	private boolean compareJournals_FirstWithStartingInitialism(String s1, String s2) {
		String[] words = s1.split("\\s");
		if ((words[0].length() > 2 && words[0].equals(words[0].toUpperCase()))
				|| (words.length == 1 && words[0].length() < 6)) {
			String patternString = words[0].chars()
					.mapToObj(c -> String.valueOf((char) c))
					.collect(Collectors.joining(".*(\\b|-|)", "^", ".*"));
			Pattern patternShort3 = Pattern.compile(patternString, Pattern.CASE_INSENSITIVE);
			log.debug("Pattern STARTING_INITIALISM for '{}': {} for '{}'", s1, patternShort3.toString(), s2);
			Matcher matcher = patternShort3.matcher(s2);
			if (matcher.matches()) {
				// log.debug("Pattern STARTING_INITIALISM for '{}': {} for '{}'", s1, patternShort3.toString(), s2);
				return true;
			}
		}
		return false;
	}
	
	private boolean containsDuplicateIds(List<Record> records) {
		return ! records.stream()
				.map(Record::getId)
				.allMatch(new HashSet<>()::add);
	}

	private boolean containsOnlyRecordsWithoutPublicationYear(List<Record> records) {
		return records.stream().filter(r -> r.getPublicationYear() == 0).count() == records.size();
	}

	private boolean containsRecordsWithoutId(List<Record> records) {
		return records.stream().filter(r -> r.getId() == null).count() > 0L;
	}

	public String doSanityChecks(List<Record> records, String fileName) {
		if (containsRecordsWithoutId(records)) {
			return "ERROR: The input file " + fileName + " contains records without IDs. The input file is not a dump from an EndNote library!";
		}
		if (containsOnlyRecordsWithoutPublicationYear(records)) {
			return "ERROR: All records of the input file " + fileName + " have no Publication Year. The input file is not a dump from an EndNote library!";
		}
		if (containsDuplicateIds(records)) {
			return "ERROR: The IDs of the records of input file " + fileName + " are not unique. The input file is not a dump from 1 EndNote library!";
		}
		return null;
	}
	
	public String formatResultString(int total, int totalWritten) {
		return "DONE: DedupEndNote has deduplicated " + total + " records, has removed " + (total - totalWritten) + " duplicates, and has written " + totalWritten + " records.";
	}
	
	private String updateSession(HttpSession session, String message) {
		log.debug(message);
		session.setAttribute("result", message);
		return message;
	}
	
	@Async
	public ListenableFuture<String> generateReport(HttpSession session) {
		try {
			for (int i = 0; i < 10; i++) {
				Thread.sleep(2000);
				log.debug("in generateReport: {}", i);
				session.setAttribute("result", "Working on " + i);
			}
			session.setAttribute("result", "COMPLETE");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		String s = "COMPLETE";
		return new AsyncResult<>(s);
	}
}