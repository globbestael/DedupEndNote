package edu.dedupendnote.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.AuthorRecord;
import edu.dedupendnote.domain.IsbnIssnRecord;
import edu.dedupendnote.domain.PageRecord;
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.PublicationDB;
import edu.dedupendnote.domain.TitleRecord;
import lombok.extern.slf4j.Slf4j;

/*
 * The input method readPublications calls the NormalizationService for some of the field data. 
 * This IOService class has a couple of methods (addNormalized...) which are used in readPublications 
 * but also in the fixtures of the unit tests.
 */
@Slf4j
@Service
public class IOService {

	/*
	 * The titles of publications in these journals / books, are NOT normalized.
	 * The format of the titles of these journals / books is the output of addNormalizedJournal
	 */
	// @formatter:off
	static Set<String> skipNormalizationTitleFor = Set.of(
		"Molecular Imaging and Contrast Agent Database",	// Molecular Imaging and Contrast Agent Database (MICAD)
		"National Cancer Inst Carcinog Tech Rep Ser", 		// Natl Cancer Inst Carcinog Tech Rep Ser
		"National Toxicol Program Tech Rep Ser",			// Natl Toxicol Program Tech Rep Ser
		"Ont Health Technol Assess Ser"						// Ont Health Technol Assess Ser
	);
	// @formatter:on

	/**
	 * Pattern to identify conferences in the T3 field. \\d{4} select a.o. "Digestive Disease Week (DDW) 2020. United
	 * States."
	 */
	private static final Pattern CONFERENCE_PATTERN = Pattern
			.compile("((^\\d)|(.*(\\d{4}|Annual|Conference|Congress|Meeting|Society|Symposium))).*");

	/** Pattern to identify clinical trials phase (1 ..4, i .. iv) */
	private static final Pattern PHASE_PATTERN = Pattern.compile(".*phase\\s[\\di].*", Pattern.CASE_INSENSITIVE);

	// @formatter:on
	/*
	 * Replies and errata
	 * ==================
	 * 
	 * Replies:
	 * 	Don't use "Response" as last word, 
	 *  e.g: Endothelial cell injury in cardiovascular surgery: the procoagulant response
	 *
	 * Errata:
	 * 
	 * Starting patterns:
	 * "Correction to " + [A-Z]...
	 * "Correction to '" + ...'
	 * "Correction to: "
	 * "Correction: "
	 * "Corrections:  "
	 *
	 * "Corrigendum to "
	 * "Corrigendum to '"
	 * "Corrigendum to: "
	 * "Corrigendum: "
	
	 * "Erratum to "  [A-Z]...
	 * "Erratum: "
	 * 
	 * Ending patterns:
	 * - Correction
	 * - Corrigendum
	 * - Erratum
	 * 
	 * There are title with "... [Erratum appears ...]" (or "... [published erratum appears ...]") which are NOT errata
	 * - Diminished GABA(A) receptor-binding capacity and a DNA base substitution in a patient with treatment-resistant 
	 *   depression and anxiety.[Erratum appears in Neuropsychopharmacology. 2004 Sep;29(9):1762]
	 * - Variant of Rett syndrome and CDKL5 gene: clinical and autonomic description of 10 cases.[Erratum appears in 
	 *   Neuropediatrics. 2013 Aug;44(4):237]
	 * - White matter microstructure, cognition, and molecular markers in fragile X premutation females.[Erratum appears 
	 *   in Neurology. 2017 Sep 26;89(13):1430; PMID: 28947586]
	 * 
	 * There are titles with "[corrected]", sometimes with further additions, whch also are NOT errata
	 * - Extensive thrombosis in a patient with familial Mediterranean fever, despite hyperimmunoglobulin D state in 
	 *   serum. [corrected]
	 * - Ecologically-oriented neurorehabilitation of memory: robustness of outcome across diagnosis and 
	 *   severity... [corrected][published erratum appears in Brain Inj. 2013 Mar;27(3):377]
	 * - "Up-dating the monograph." [corrected] Cytolytic immune lymphocytes in the armamentarium of the human host
	 * In the last example the correction follows the "[corrected]"?
	 * 
	 * Errata are treated in the same was as replies: In IOService::readPublications titles which match 
	 * the erratumPattern also set the Publication::isReply field. In the comparisons of the DeduplicationService
	 * and in the enrich steps they are treated exactly like replies.
	 * 
	 * Because the errata titles are skipped in the DeduplicationService::compareTtitlesm, there is NO proprocessing
	 * of them in Publication::addTitles.
	 */
	// @formatter:on
	// FIXME: Can some of these 4 patterns be merged?
	private static final Pattern REPLY_PATTERN = Pattern
			.compile("(^C(omment|OMMENT)|^R[Ee]: .+|.*\\breply\\b.*|.*author(.+)respon.*|^response$)");
	private static final Pattern ERRATUM_PATTERN = Pattern.compile(
			"(^(Correction|Corrigendum|Erratum)( to (?=[A-Z])| to '|( to)?: ).*)|(.*(Correction|Corrigendum|Erratum)$)");
	public static final Pattern SOURCE_PATTERN = Pattern.compile(".+(\\(vol \\d+\\D+\\d+\\D+\\d+\\D*\\))");
	public static final Pattern COMMENT_PATTERN = Pattern.compile(
			"(e)?Comment(|s|ary)\\b.*|.+[cC]omment(|s|ary)( from| on| to)?:? [A-Z'\"].+|.+[Cc]omment(|s|ary)|.+COMMENT");

	/*
	 * If field content starts with a comma (",") EndNote exports "[Fieldname]  -,", NOT "[Fieldname]  - ," (EndNote X9.3.3).
	 * This pattern skips that initial comma, not the space which may come after that comma!
	 * 
	 * Whitespaces has already been normalized (input may have had '\u00A0' after '-').
	 */
	public static final Pattern RIS_LINE_PATTERN = Pattern.compile("(^[A-Z][A-Z0-9])( {2}-[ ,\\u00A0])(.*)$");

	/*
	 * readPublications: called in the first phase (before the comparison of publications), includes normalization of data.
	 * 
	 * There are several public write... methods in this class which read the publications:
	 * - no / harldy any normalization
	 * - result stored in a Map<String, String>
	 */
	public List<Publication> readPublications(String inputFileName) {
		List<Publication> publications = new ArrayList<>();
		String fieldContent = null;
		String fieldName = null;
		Map<String, String> pagesInputMap = new HashMap<>();
		String previousFieldName = "XYZ";
		/*
		 * With publications from clinicaltrials.gov the raw title must be recorded in publication.title. The publications
		 * can be identified by the content of the T2 or UR field. The first comes before the TI field, 
		 * the second after the TI field. We need this titleCache field for this second case, but use it after
		 * reading the ER field
		 */
		String titleCache = null;
		String journalCache = null;
		Publication publication = new Publication();

		boolean hasBom = UtilitiesService.detectBom(inputFileName);
		int missingId = 1;

		// Line starting with "TY - " triggers creation of record, line starting with
		// "ER - " signals end of record
		try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
			if (hasBom) {
				br.skip(1);
			}
			String line;
			while ((line = br.readLine()) != null) {
				line = NormalizationService.normalizeHyphensAndWhitespace(line);
				Matcher matcher = RIS_LINE_PATTERN.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3).strip();
					// "NA" added for the ASySD Depression set (R artifact)
					if ((fieldContent.isEmpty() && !"ER".equals(fieldName)) || "NA".equals(fieldContent)) {
						continue;
					}
					previousFieldName = "XYZ";
					switch (fieldName) {
					case "AU": // Authors
						if (fieldContent.contains("; ")) {
							List<String> authors = Arrays.asList(fieldContent.split("; "));
							for (String author : authors) {
								addNormalizedAuthor(author, publication);
							}
						} else {
							addNormalizedAuthor(fieldContent, publication);
						}
						break;
					case "C7": // article number (Scopus and WoS when imported as RIS format)
						pagesInputMap.put(fieldName, fieldContent);
						break;
					case "DO": // DOI
						if (fieldContent.startsWith("ARTN ")) {
							pagesInputMap.put("C7", fieldContent);
						} else {
							publication.getDois().addAll(NormalizationService.normalizeInputDois(fieldContent));
						}
						previousFieldName = fieldName;
						break;
					/*
					 * Zotero has split the pages between SP and EP (in that order in RIS file)
					 */
					case "EP":
						String sp = pagesInputMap.getOrDefault("SP", "");
						pagesInputMap.put("SP", sp + "-" + fieldContent);
						break;
					case "ER":
						if (publication.getId() == null) {
							publication.setId(Integer.toString(missingId++));
						}
						if (publication.isClinicalTrialGov()) {
							publication.getAuthors().clear();
							String journal = publication.getJournals().stream()
									.filter(j -> j.startsWith("https://clinicaltrials.gov")).findFirst().orElse(null);
							if (journal != null) {
								// should handle EndNoe records which have already been standardized
								String ctgId = journal.substring(journal.length() - 11);
								if (ctgId.startsWith("NCT")) {
									pagesInputMap.put("SP", ctgId);
									publication.getJournals().remove(journal);
									publication.getJournals().add("https://clinicaltrials.gov");
								}
							}
							publication.setTitle(titleCache);
						}
						fillAllAuthors(publication);
						addNormalizedPages(pagesInputMap, publication);
						if (publication.isSeveralPages) {
							addReversedTitles(publication);
						}
						publications.add(publication);

						journalCache = null;
						titleCache = null;
						publication = new Publication();
						pagesInputMap.clear();
						log.debug("Publication read with id {} and title: {}", publication.getId(),
								(publication.getTitles().isEmpty() ? "(none)" : publication.getTitles().getFirst()));
						break;
					case "ID": // EndNote Publication number
						publication.setId(fieldContent);
						// log.debug("Read ID {}", fieldContent);
						break;
					case "J2": // Alternate journal
						addNormalizedJournal(fieldContent, publication, fieldName);
						break;
					case "OP":
						// in PubMed: original title, in Web of Science (at least for conference papers): conference
						// title
						if ("CONF".equals(publication.getReferenceType())) {
							addNormalizedJournal(fieldContent, publication, fieldName);
						} else {
							addNormalizedTitle(fieldContent, publication);
						}
						break;
					case "PY": // Publication year
						publication
								.setPublicationYear(NormalizationService.normalizeInputPublicationYear(fieldContent));
						break;
					case "SN": // ISSN / ISBN
						IsbnIssnRecord normalized = NormalizationService.normalizeInputIssns(line);
						publication.getIsbns().addAll(normalized.isbns());
						publication.getIssns().addAll(normalized.issns());
						previousFieldName = fieldName;
						break;
					// Ovid Medline in RIS export has author address in repeatable M2 field,
					// EndNote 20 shows the content in field with label "Start Page",
					// but export file of such a record has this content in SE field!
					case "SE": // pages (Embase (which provider?), Ovid PsycINFO: examples in some SRA datasets)
					case "SP": // pages
						pagesInputMap.put(fieldName, fieldContent);
						break;
					/*
					 * original non-English titles:
					 * - PubMed: OP 
					 * - Embase: continuation line of title (ST and TI)
					 * - Scopus: ST and TT?
					 * 
					 * See below in continuation line of TI for PubMed chapters
					 */
					case "ST": // Original Title in Scopus
						addNormalizedTitle(fieldContent, publication);
						break;
					case "T2": // Journal title / Book title
						journalCache = fieldContent;
						if (fieldContent.startsWith("http") && fieldContent.contains("//clinicaltrials.gov")) {
							fieldContent = fieldContent.replace("http:", "https:");
							publication.setClinicalTrialGov(true);
						}
						addNormalizedJournal(fieldContent, publication, fieldName);
						break;
					// @formatter:off
					/*
					 * T3 (especially used in EMBASE (OVID)) has 3 types of content:
					 * - conference name (majority of cases)
					 * - original title 
					 * - alternative journal name
					 * T3 for PsycINFO (OVID) also puts alternative journal names in this field, sometimes more than one separated with a comma:
					 * 		T2  - Archives of Neurology
					 * 		T3  - A.M.A. Archives of Neurology, JAMA Neurology
					 *   ???: Many cases where T3 has the predecessor(s) of the journal of the publication.
					 *   BUT: PsycINFO (OVID) also has the original title in T2 and the journal of T3 (ovid URL has "D=psyc10":is this PsycINFO?)
					 * T3 for PubMed can have "Retraction of: ...", "Retraction in: ...", ... e.g.
					 * - Retraction of: Cancer Lett. 2022 Mar 31;529:19-36. doi: 10.1016/j.canlet.2021.12.032 PMID: 34979165 [https://pubmed.ncbi.nlm.nih.gov/34979165]
					 * - Retraction of: J Healthc Eng. 2022 Feb 18;2022:8507773. doi: 10.1155/2022/8507773 PMID: 35222894 [https://pubmed.ncbi.nlm.nih.gov/35222894]
					 * - Retraction in: Comput Intell Neurosci. 2024 Aug 5;2024:9896585. doi: 10.1155/2024/9896585 PMID: 39139200 [https://pubmed.ncbi.nlm.nih.gov/39139200]
					 * - [Erratum appears in Curr Top Behav Neurosci. 2017 Jul 15;:; PMID: 28710675]
					 * 
					 * T3 for WoS can contain the series title of the book (title of the book in T2)
					 * - Advances in Experimental Medicine and Biology [BIG_SET 40620, 40621, TY is CHAP]
					 * 
					 * Present solution:
					 * - skip it if it contains a number or "Annual|Conference|Congress|Meeting|Society"
					 *   ("Asian Pacific Digestive Week 2014. Bali Indonesia.",
					 *    "12th World Congress of the International Hepato-Pancreato-Biliary Association. Sao Paulo Brazil.",
					 *    "International Liver Transplantation Society 15th Annual International Congress. New York, NY United States.")
					 * - add it as Title 
					 * - add it as Journal
					 */
					// @formatter:on
					case "T3": // Book section
						if (!fieldContent.startsWith("Retract")
								&& !CONFERENCE_PATTERN.matcher(fieldContent).matches()) {
							addNormalizedJournal(fieldContent, publication, fieldName);
							addNormalizedTitle(fieldContent, publication);

							// This commented out code was an unsuccessful attempt to make better choices with the
							// McKeown test file.
							// See Github issue 53
							//
							// if (!publication.getIsbns().isEmpty()) {
							// addNormalizedJournal(fieldContent, publication, fieldName);
							// } else {
							// /*
							// * Compare its length with the length of the (cached)Journal (T2) and of the first title
							// (ST).
							// * Add it to the field with the most similar length
							// */
							// int jlength = Math.abs(journalCache.length() - fieldContent.length());
							// int tlength = Math
							// .abs(publication.getTitles().getFirst().length() - fieldContent.length());
							// log.error("\nTI: {}\nJO: {}\nT3: {}\n\n", publication.getTitles().getFirst(),
							// journalCache, fieldContent);
							// if (jlength < tlength - 15) {
							// addNormalizedJournal(fieldContent, publication, fieldName);
							// } else {
							// addNormalizedTitle(fieldContent, publication);
							// }
							// }
						}
						break;
					// ??? in Embase the original title is on the continuation line of ST and TI:
					// "Een 45-jarige patiente met chronische koliekachtige abdominale pijn". Not found in test set!
					case "TI": // Title
						addNormalizedTitle(fieldContent, publication);
						// Don't do this in IOService::readPublications because these 2 patterns are only applied to TI
						// field, not to the other fields which are added to List<String> titles
						if (REPLY_PATTERN.matcher(fieldContent.toLowerCase()).matches()
								|| ERRATUM_PATTERN.matcher(fieldContent).matches()
								|| (fieldContent.endsWith(")") && SOURCE_PATTERN.matcher(fieldContent).matches())
								|| COMMENT_PATTERN.matcher(fieldContent).matches()) {
							publication.setReply(true);
							publication.setTitle(fieldContent);
						}
						if (PHASE_PATTERN.matcher(fieldContent.toLowerCase()).matches()) {
							publication.setPhase(true);
						}
						titleCache = fieldContent;
						previousFieldName = fieldName;
						break;
					// TODO: When does TT occur? is translated (i.e. original?) title
					case "TY": // Type
						publication = new Publication();
						publication.setReferenceType(fieldContent);
						break;
					// do not use UR to extract more DOI's: see https://github.com/globbestael/DedupEndNote/issues/14
					case "UR":
						if (fieldContent.startsWith("http") && fieldContent.contains("//clinicaltrials.gov")) {
							fieldContent = fieldContent.replace("http:", "https:");
							publication.setClinicalTrialGov(true);
							addNormalizedJournal(fieldContent, publication, fieldName);
						}
						previousFieldName = fieldName;
						break;
					case "VL":
						if (fieldContent.length() > 10 && journalCache != null) {
							addNormalizedJournal(journalCache + ". " + fieldContent, publication, fieldName);
						}
						previousFieldName = fieldName;
						break;
					default:
						previousFieldName = fieldName;
						break;
					}
				} else { // continuation line
					switch (previousFieldName) {
					case "DO":
						publication.getDois().addAll(NormalizationService.normalizeInputDois(line));
						break;
					case "SN":
						IsbnIssnRecord normalized = NormalizationService.normalizeInputIssns(line);
						publication.getIsbns().addAll(normalized.isbns());
						publication.getIssns().addAll(normalized.issns());
						break;
					/*
					 * The ST field with continuation line has the same content as the TI field with its continuation line.
					 * Only the TI case is handled (makes the PubMed/Chapter handling easier)
					 */
					case "TI":
						/*
						 * PubMed book chapters (TY CHAP, PMID 21204472).
						 * The series of the book is the first line of ST and TI field,
						 * the title of the chapter is the continuation line.
						 * 
						 * False Positive deduplication is quite likely if some of the authors are shared):
						 * - The book chapters have NO page number of chapter number in EndNote
						 * - the series title is taken as one of the titles
						 * 
						 * Solution: if there is a continuation line of TI for a chapter (TY CHAP), 
						 * the existing titles from ST and TI should be removed,
						 * the continuation line of TI should be used as the only title.
						 * 
						 * The ST field has in these cases the same content as the TI field. 
						 * This switch statement does not need to have a case for ST. The solution (below) would be
						 * even be more complex if there was a case ST.
						 */
						if ("CHAP".equals(publication.getReferenceType())) {
							publication.getTitles().clear();
							addNormalizedTitle(line, publication);
						} else {
							/*
							* EMBASE original title (at least for articles).
							*/
							addNormalizedTitle(line, publication);
						}
						break;
					case "UR":
						if (line.startsWith("http") && line.contains("//clinicaltrials.gov")) {
							line = line.replace("http:", "https:");
							publication.setClinicalTrialGov(true);
							addNormalizedJournal(line, publication, fieldName);
						}
						break;
					default:
						break;
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			log.error("In field {} with content {}: Number could not be parsed", fieldName, fieldContent);
			e.printStackTrace();
		} catch (Exception e) {
			log.error("In field {} with content {}: other exception: {}", fieldName, fieldContent, e.getMessage());
			e.printStackTrace();
		}
		log.debug("Publications read: {}", publications.size());
		return publications;
	}

	public static void addNormalizedAuthor(String fieldContent, Publication publication) {
		AuthorRecord normalizedAuthor = NormalizationService.normalizeInputAuthors(fieldContent);
		if (normalizedAuthor.author() != null) {
			publication.getAuthors().add(normalizedAuthor.author());
			publication.getAuthorsTransposed().add(normalizedAuthor.authorTransposed());
			if (normalizedAuthor.isAuthorTransposed()) {
				publication.setAuthorsAreTransposed(true);
			}
		}
	}

	public static void addNormalizedJournal(String fieldContent, Publication publication, String fieldName) {
		if (fieldContent.toLowerCase().contains("cochrane")) {
			publication.setCochrane(true);
		}
		publication.getJournals().addAll(NormalizationService.normalizeInputJournals(fieldContent, fieldName));
	}

	public static void addNormalizedPages(Map<String, String> pagesInputMap, Publication publication) {
		publication.setPagesInput(pagesInputMap.toString());

		if (publication.isCochrane() && pagesInputMap.isEmpty()) {
			String c7 = getCochranePagesFromDoi(publication);
			if (c7 != null) {
				pagesInputMap.put("C7", c7);
			}
		}

		PageRecord normalizedPages = NormalizationService.normalizeInputPages(pagesInputMap, publication.getId());
		publication.setPageStart(normalizedPages.pageStart());
		publication.setPagesOutput(normalizedPages.pagesOutput());
		publication.setSeveralPages(normalizedPages.isSeveralPages());
	}

	public static void addNormalizedTitle(String fieldContent, Publication publication) {
		if (UtilitiesService.setsContainSameString(skipNormalizationTitleFor, publication.getJournals())) {
			publication.getTitles().clear();
			publication.getTitles().add(fieldContent);
		} else {
			TitleRecord normalizedTitle = NormalizationService.normalizeInputTitles(fieldContent);
			publication.getTitles().addAll(normalizedTitle.titles());
			if (normalizedTitle.originalTitle() != null) {
				publication.setTitle(normalizedTitle.originalTitle());
			}
		}
	}

	public static void addReversedTitles(Publication publication) {
		if (!UtilitiesService.setsContainSameString(skipNormalizationTitleFor, publication.getJournals())) {
			SequencedSet<String> titles = publication.getTitles();
			if (!titles.isEmpty()) {
				List<String> reversed = new ArrayList<>();
				for (String t : titles) {
					reversed.add(new StringBuilder(t).reverse().toString());
				}
				titles.addAll(reversed);
			}
		}
	}

	public static void fillAllAuthors(Publication publication) {
		List<String> authors = publication.getAuthors();
		if (authors.isEmpty()) {
			return;
		}

		String s = authors.stream().limit(40).collect(Collectors.joining("; "));
		publication.getAllAuthors().add(s);
		// DONT: lowercasing the names makes different authors closer to 1.0

		if (publication.isAuthorsAreTransposed()) {
			publication.getAllAuthors()
					.add(publication.getAuthorsTransposed().stream().limit(40).collect(Collectors.joining("; ")));
		}
	}

	// @formatter:off
	/**
	 * - PageStart (PG) and DOIs (DO) are replaced or inserted, but written at the same place as in the input file to
	 *   make comparisons between input file and output file easier.
	 * - Absent Publication year (PY) is replaced if there is one found in a duplicate record.
	 * - Author (AU) Anonymous is skipped.
	 * - Title (TI) is replaced with the longest duplicate title when it contains "Reply".
	 * - Article Number (C7) is skipped.
	 * - Absent Journal Name (T2) is copied from J2 (or filed in based on DOI foor SSRN): for embase.com records
	 *   (but no check on this origin!)
	 *
	 * Publications are read into a TreeMap, with continuation lines added. 
	 * writePublications(...) does the replacements, and writes to the output file.
	 */
	// @formatter:off
	public int writeDeduplicatedPublications(List<Publication> publications, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<Publication> recordsToKeep = publications.stream().filter(Publication::isKeptPublication).toList();
		log.debug("Publications to be kept: {}", recordsToKeep.size());

		Map<String, Publication> recordIdMap = publications.stream().filter(r -> !r.getId().startsWith("-"))
				.collect(Collectors.toMap(Publication::getId, Function.identity()));

		int numberWritten = 0;
		int lineNumber = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();

		boolean hasBom = UtilitiesService.detectBom(inputFileName);

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName));
				BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
			if (hasBom) {
				br.skip(1);
			}
			String line;
			Publication publication = null;
			Integer phantomId = 0;
			String realId = null;

			while ((line = br.readLine()) != null) {
				lineNumber++;
				line = NormalizationService.normalizeHyphensAndWhitespace(line);
				Matcher matcher = RIS_LINE_PATTERN.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3);
					previousFieldName = "XYZ";
					switch (fieldName) {
					case "ER":
						map.put(fieldName, fieldContent);
						phantomId++;
						if (realId == null) {
							publication = recordIdMap.get(phantomId.toString());
							if (publication != null) {
								publication.setId(phantomId.toString());
								map.put("ID", phantomId.toString());
							}
						}
						if (publication != null && publication.isKeptPublication()) {
							writePublication(map, publication, bw, true);
							numberWritten++;
						}
						map.clear();
						realId = null;
						break;
					case "ID": // EndNote Publication number
						map.put(fieldName, fieldContent);
						realId = line.substring(6);
						publication = recordIdMap.get(realId);
						break;
					default:
						if (map.containsKey(fieldName)) {
							if (line.startsWith(fieldName)) {
//								map.put(fieldName, map.get(fieldName) + "\n" + fieldContent);
								map.put(fieldName, map.get(fieldName) + "\n" + line);
							} else {
								map.put(fieldName, map.get(fieldName) + "\n" + line);
							}
						} else {
							map.put(fieldName, fieldContent);
						}
						previousFieldName = fieldName;
						break;
					}
				} else { // continuation line
					map.put(previousFieldName, map.get(previousFieldName) + "\n" + line);
				}
			}
		} catch (IOException e) {
            String message = String.format("IOException while writing deduplicated records to %s at line %d: %s", outputFileName, lineNumber, e.getMessage());
            log.error(message, e);
            // Consider re-throwing the exception or handling it in another appropriate way
            throw new RuntimeException(message, e);
		}
		log.debug("Finished writing to file. # records: {}", numberWritten);
		return numberWritten;
	}

	public int writeMarkedPublications(List<Publication> publications, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<Publication> recordsToKeep = publications.stream().filter(Publication::isKeptPublication).toList();
		log.debug("Publications to be kept: {}", recordsToKeep.size());

		Map<String, Publication> recordIdMap = publications.stream().filter(r -> !r.getId().startsWith("-"))
				.collect(Collectors.toMap(Publication::getId, Function.identity()));

		int numberWritten = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();

		boolean hasBom = UtilitiesService.detectBom(inputFileName);

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName));
				BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
			if (hasBom) {
				br.skip(1);
			}
			String line;
			Publication publication = null;
			Integer phantomId = 0;
			String realId = null;

			while ((line = br.readLine()) != null) {
				line = NormalizationService.normalizeHyphensAndWhitespace(line);
				Matcher matcher = RIS_LINE_PATTERN.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3);
					previousFieldName = "XYZ";
					switch (fieldName) {
					case "ER":
						phantomId++;
						if (realId == null) {
							publication = recordIdMap.get(phantomId.toString());
							publication.setId(phantomId.toString());
							map.put("ID", phantomId.toString());
						}
						if (publication != null && publication.isKeptPublication()) {
							map.put(fieldName, fieldContent);
							if (publication.getLabel() != null) {
								map.put("LB", publication.getLabel());
							}
							writePublication(map, publication, bw, false);
							numberWritten++;
						}
						map.clear();
						realId = null;
						break;
					case "ID": // EndNote Publication number
						map.put(fieldName, fieldContent);
						realId = fieldContent;
						publication = recordIdMap.get(realId);
						break;
					case "LB":
						break; // to ensure that the present Label is not used.
					default:
						if (map.containsKey(fieldName)) {
							map.put(fieldName, map.get(fieldName) + "\n" + line);
						} else {
							map.put(fieldName, fieldContent);
						}
						previousFieldName = fieldName;
						break;
					}
				} else { // continuation line
					map.put(previousFieldName, map.get(previousFieldName) + "\n" + line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.debug("Finished writing to file. # records: {}", numberWritten);
		return numberWritten;
	}

	/*
	 * Ordering of an EndNote export RIS file: the fields are ordered
	 * alphabetically, except for TY (first), and ID and ER (last fields)
	 */
	private void writePublication(Map<String, String> map, Publication publication, BufferedWriter bw, boolean enhance)
			throws IOException {
		if (enhance) {
			if (!publication.getDois().isEmpty()) {
				map.put("DO", "https://doi.org/"
						+ publication.getDois().stream().collect(Collectors.joining("\nhttps://doi.org/")));
			}
			if (publication.getPagesOutput() == null || publication.getPagesOutput().isEmpty()) {
				map.remove("SP");
			} else {
				map.put("SP", publication.getPagesOutput());
			}
			if (publication.isReply() || publication.getTitle() != null) {
				map.put("TI", publication.getTitle());
				map.put("ST", publication.getTitle());
			}
			if (publication.isClinicalTrialGov()) {
				map.put("TY", "JOUR");
				map.put("T2", "https://clinicaltrials.gov");
				String url = "https://clinicaltrials.gov/study/" + publication.getPageStart();
				List<String> urlList = new ArrayList<>();
				if (map.containsKey("UR")) {
					String urls = map.get("UR");
					urlList.addAll(Arrays.asList(urls.split("\n")));
					urlList.removeIf(u -> u.startsWith("https://clinicaltrials.gov"));
					if (urlList.isEmpty()) {
						map.put("UR", url);
					} else {
						map.put("UR", url + "\nUR  - " + 
							urlList.stream()
								.map(u -> u.replace("UR  - ", ""))
								.collect(Collectors.joining("\nUR  - ")));
					}
				} else {
					map.put("UR", url);
				}
			}

			// Some unusual authors should be kept, e.g. Group authors 
			if (publication.getAuthors().isEmpty() && ("Anonymous".equals(map.get("AU")) || "Nct".equals(map.get("AU")))) {
				map.remove("AU");
			}
			if (!map.containsKey("PY") && publication.getPublicationYear() != 0) {
				map.put("PY", publication.getPublicationYear().toString());
			}
			if (!map.containsKey("T2")) {
				if (map.containsKey("J2")) {
					map.put("T2", map.get("J2"));
				} else if (map.containsKey("DO") && map.get("DO").contains("https://doi.org/10.2139/ssrn")) {
					// alternative test could be ISSN 1556-5068
					map.put("T2", "Social Science Research Network");
				}
			}
		}
		// in enhanced mode C7 (Article number) is skipped, in Mark mode C7 is NOT skipped
		String skipFields = enhance ? "(C7|ER|ID|TY|XYZ)" : "(ER|ID|TY|XYZ)";
		StringBuilder sb = new StringBuilder();
		sb.append("TY  - ").append(map.get("TY")).append("\n");
		map.forEach((k, v) -> {
			if (!k.matches(skipFields)) {
				sb.append(k).append("  - ").append(v).append("\n");
			}
		});
		sb.append("ID  - ").append(map.get("ID")).append("\n");
		sb.append("ER  - ").append("\n\n");
		bw.write(sb.toString());
	}

	/**
	 * writeRisWithTRUTH(...): writes a RIS file with Caption field ['Duplicate', 'Unknown', empty] and, in case of true
	 * duplicates, with Label field the ID if the record which will be kept.
	 *
	 * <p>
	 * Caption field: - Duplicate: validated and True Positive - empty: validated and True Negative - Unknown: not
	 * validated
	 *
	 * <p>
	 * All records which are duplicates have the same ID in Label field, so this ID could be considered as the ID of a
	 * duplicate group. DedupEndNote in non-Mark mode would write only the record where the record ID is the same as
	 * Label.
	 *
	 * @param inputFileName  filename of a RIS export file
	 * @param truthRecords   List<PublicationDB> of validated records (TAB delimited export file from validation DB)
	 * @param outputFileName filename of a RIS file
	 */
	public void writeRisWithTRUTH(List<PublicationDB> truthRecords, String inputFileName, String outputFileName) {
		int numberWritten = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();

		Map<Integer, PublicationDB> truthMap = truthRecords.stream()
				.collect(Collectors.toMap(PublicationDB::getId, Function.identity()));

		boolean hasBom = UtilitiesService.detectBom(inputFileName);

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName));
				BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
			if (hasBom) {
				br.skip(1);
			}
			String line;
			Integer id = null;
			while ((line = br.readLine()) != null) {
				line = NormalizationService.normalizeHyphensAndWhitespace(line);
				Matcher matcher = RIS_LINE_PATTERN.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3);
					previousFieldName = "XYZ";
					switch (fieldName) {
					case "ER":
						if (id != null) {
							log.error("Writing {}", id);
							map.put(fieldName, fieldContent);
							if (truthMap.containsKey(id)) {
								if (truthMap.get(id).isTruePositive()) {
									map.put("CA", "Duplicate");
									map.put("LB", truthMap.get(id).getDedupid().toString());
								} else {
									map.remove("CA");
									map.remove("LB");
								}
							} else {
								map.put("CA", "Unknown");
							}
							writePublication(map, null, bw, false);
							numberWritten++;
						}
						map.clear();
						break;
					case "ID": // EndNote Publication number
						map.put(fieldName, fieldContent);
						id = Integer.valueOf(fieldContent);
						break;
					default:
						if (map.containsKey(fieldName)) {
							map.put(fieldName, map.get(fieldName) + "\n" + line);
						} else {
							map.put(fieldName, fieldContent);
						}
						previousFieldName = fieldName;
						break;
					}
				} else { // continuation line
					map.put(previousFieldName, map.get(previousFieldName) + "\n" + line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.debug("Finished writing to file. # records: {}", numberWritten);
	}

	public void writeRisWithTRUTH_forDS(List<PublicationDB> truthRecords, String inputFileName, String outputFileName) {
		int numberWritten = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();

		Map<Integer, PublicationDB> truthMap = truthRecords.stream()
				.collect(Collectors.toMap(PublicationDB::getId, Function.identity()));

		boolean hasBom = UtilitiesService.detectBom(inputFileName);

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName));
				BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
			if (hasBom) {
				br.skip(1);
			}
			String line;
			Integer id = null;
			while ((line = br.readLine()) != null) {
				line = NormalizationService.normalizeHyphensAndWhitespace(line);
				Matcher matcher = RIS_LINE_PATTERN.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3);
					previousFieldName = "XYZ";
					switch (fieldName) {
					case "ER":
						if (id != null) {
							log.error("Writing {}", id);
							map.put(fieldName, fieldContent);
							if (truthMap.containsKey(id)) {
								map.put("CA", map.get("CA").toUpperCase());
								if (truthMap.get(id).isTruePositive()) {
									map.put("LB", truthMap.get(id).getDedupid().toString());
								} else {
									map.remove("LB");
								}
							} else {
								map.put("CA", map.get("CA").toLowerCase());
							}
							writePublication(map, null, bw, false);
							numberWritten++;
						}
						map.clear();
						break;
					case "ID": // EndNote Publication number
						map.put(fieldName, fieldContent);
						id = Integer.valueOf(fieldContent);
						break;
					default:
						if (map.containsKey(fieldName)) {
							map.put(fieldName, map.get(fieldName) + "\n" + line);
						} else {
							map.put(fieldName, fieldContent);
						}
						previousFieldName = fieldName;
						break;
					}
				} else { // continuation line
					map.put(previousFieldName, map.get(previousFieldName) + "\n" + line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.debug("Finished writing to file. # records: {}", numberWritten);
	}

	/**
	 * Tries to extract ArticleNumber (C7) from the DOI of Cochrane publication.
	 * This function is only called if (1) isCochrane and (2) pagesInputMap is empty
	 */
	private static String getCochranePagesFromDoi(Publication publication) {
		String c7 = null;
		log.debug("Reached Cochrane publication without pageStart, getting it from the DOIs: {}", publication.getAuthors());
		for (String doi : publication.getDois()) {
			Matcher matcher = DeduplicationService.COCHRANE_DOI_PATTERN.matcher(doi);
			if (matcher.matches()) {
				c7 = matcher.group(1);
				break;
			}
		}
		return c7;
	}
}
