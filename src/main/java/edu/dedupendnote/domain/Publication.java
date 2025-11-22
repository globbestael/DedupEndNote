package edu.dedupendnote.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/*
 * This class has been made a POJO. 
 * Methods related to normalization of data when reading in external data have been put in / extracted to NormalizationService.
 * There are a small set of functions in IOService (e.g. fillAllAuthors) which originally belonged here.
 */
@Slf4j
@Data
public class Publication {

	private List<String> allAuthors = new ArrayList<>();

	protected List<String> authors = new ArrayList<>();

	private List<String> authorsTransposed = new ArrayList<>();

	private boolean authorsAreTransposed = false;

	private Set<String> dois = new HashSet<>();

	private String id;

	private Set<String> isbns = new HashSet<>();
	private Set<String> issns = new HashSet<>();

	private Set<String> journals = new HashSet<>();

	/*
	 * The label field is used internally to mark the duplicate lists: the label of all duplicate publications in a set receive the ID
	 * of the first publication of this list. If a publication has no duplicates, the label is not set. 
	 * It is NOT the content of the Label (EndNote field LB) of the EndNote input file. 
	 * If markMode is set, this field is exported. The original content of the Label field in the EndNote export file is overwritten in this case!
	 */
	private String label;

	/*
	 * Used for replacing the input pages field in the output file (except for markMode).
	 * - if null: use the input pages
	 * - if empty string: do not output any pages field
	 * - else: use this field instead (typically the long form "102-118" instead of "102-18")
	 */
	private String pagesOutput;
	private String pageStart;

	private Integer publicationYear = 0;

	private String referenceType;

	private String title; // only set for Reply-titles

	private List<String> titles = new ArrayList<>();

	private boolean isClinicalTrialGov = false;

	/**
	 * Cochrane publications need a slightly different comparison. The starting page is the Cochrane number of the
	 * review which doesn't change for different versions of the review. Each version of the review has a unique DOI
	 * (e.g. "10.1002/14651858.cd008759.pub2"), but the first version has no ".pub" part, AND bibliographic databases
	 * sometimes use the common DOI / DOI of first version for all versions. Therefore: - with other publications
	 * starting pages are compared BEFORE the DOIs. For Cochrane publications if both have a DOI, then only the DOIs are
	 * compared - publication year must be the same
	 */
	private boolean isCochrane = false;
	private boolean isKeptPublication = true;
	private boolean isPhase = false;
	private boolean isPresentInOldFile = false; // used when comparing 2 files

	/*
	 * Publications which are replies need special treatment. See the Pattern in the {@link IOService.replyPattern} 
	 * - publication pairs where one of them is isReply == true, aren't compared for title (always true)
	 * - journals are compared stricter (see DeduplicationService.JOURNAL_SIMILARITY_NO_REPLY < DeduplicationService.JOURNAL_SIMILARITY_NO_REPLY)
	 * - in enrich() the longest title of a duplicate set is used
	 */
	private boolean isReply = false;
	public boolean isSeveralPages;
	private String pagesInput = null;
}
