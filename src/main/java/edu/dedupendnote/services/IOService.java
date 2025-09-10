package edu.dedupendnote.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.PublicationDB;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IOService {
	/*
	 * Patterns
	 */
	/**
	 * Pattern to identify conferences in the T3 field
	 */
	private static final Pattern conferencePattern = Pattern
			.compile("((^\\d)|(.*(\\d{4}|Annual|Conference|Congress|Meeting|Society|Symposium))).*");

	/** Pattern to identify clinical trials phase (1 ..4, i .. iv) */
	private static final Pattern phasePattern = Pattern.compile(".*phase\\s[\\di].*", Pattern.CASE_INSENSITIVE);

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
	private static final Pattern replyPattern = Pattern
			.compile("(^C(omment|OMMENT)|^R[Ee]: .+|.*\\breply\\b.*|.*author(.+)respon.*|^response$)");
	private static final Pattern erratumPattern = Pattern.compile(
			"(^(Correction|Corrigendum|Erratum)( to (?=[A-Z])| to '|( to)?: ).*)|(.*(Correction|Corrigendum|Erratum)$)");
	public static final Pattern sourcePattern = Pattern.compile(".+(\\(vol \\d+\\D+\\d+\\D+\\d+\\D*\\))");
	public static Pattern commentPattern = Pattern.compile(
			"(e)?Comment(|s|ary)\\b.*|.+[cC]omment(|s|ary)( from| on| to)?:? [A-Z'\"].+|.+[Cc]omment(|s|ary)|.+COMMENT");

	/*
	 * If field content starts with a comma (",") EndNote exports "[Fieldname]  -,",
	 * NOT "[Fieldname]  - ," (EndNote X9.3.3) This pattern skips that initial
	 * comma, not the space which may come after that comma!
	 */
	public static final Pattern risLinePattern = Pattern.compile("(^[A-Z][A-Z0-9])( {2}-[ ,\\u00A0])(.*)$");

	/**
	 * All white space characters within input fields will replaced with a normal SPACE. LINE SEPARATOR and NO-BREAK
	 * SPACE have been observed in the test files. The pattern uses a maximum view: - all white space / "Separator,
	 * space" characters (class Zs): https://www.fileformat.info/info/unicode/category/Zs/list.htm - all "Separator,
	 * Line" (Zl) and "Separator, Paragraph" (Zp) characters - some "Control, other" characters (class Cc), but not all:
	 * https://www.fileformat.info/info/unicode/category/Cc/list.htm
	 * 
	 * Pattern excludes SPACE from class Zs for performance reason by making an intersection (&&) with the negation ([^
	 * ]).
	 * 
	 * Tested in TextNormalizerTest
	 */
	public static final Pattern unusualWhiteSpacePattern = Pattern
			.compile("[\\p{Zs}\\p{Zl}\\p{Zp}\\u0009\\u000A\\u000B\\u000C\\u000D&&[^ ]]");

	public List<Publication> readPublications(String inputFileName) {
		List<Publication> publications = new ArrayList<>();
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		/*
		 * With records from clinicaltrials.gov the raw title must be recorded in publication.title. The records
		 * can be identified by the content of the T2 or UR field. The first comes before the TI field, 
		 * the second after the TI field. We need this titleCache field for this second case, but use it after
		 * reading the ER field
		 */
		String titleCache = null;
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
				line = unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
				Matcher matcher = risLinePattern.matcher(line);
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
						// XML files can put all authors on 1 line separated by "; "
						if (fieldContent.contains("; ")) {
							List<String> authors = Arrays.asList(fieldContent.split("; "));
							for (String author : authors) {
								publication.addAuthors(author);
							}
						} else {
							publication.addAuthors(fieldContent);
						}
						break;
					case "C7": // article number (Scopus and WoS when imported as RIS format)
						publication.parsePages(fieldContent, fieldName);
						break;
					case "DO": // DOI
						publication.addDois(fieldContent);
						previousFieldName = fieldName;
						break;
					case "ER":
						if (publication.getId() == null) {
							publication.setId(Integer.toString(missingId++));
						}
						publication.addReversedTitles();
						if (publication.isClinicalTrialGov()) {
							publication.getAuthors().clear();
							String journal = publication.getJournals().stream()
									.filter(j -> j.startsWith("https://clinicaltrials.gov")).findFirst().orElse(null);
							if (journal != null) {
								// should handle EndNoe records which have already been standardized
								String ctgId = journal.substring(journal.length() - 11);
								if (ctgId.startsWith("NCT")) {
									publication.parsePages(ctgId, "T2");
									publication.getJournals().remove(journal);
									publication.getJournals().add("https://clinicaltrials.gov");
								}
							}
							publication.setTitle(titleCache);
						}
						publication.fillAllAuthors();
						publications.add(publication);
						log.debug("Publication read with id {} and title: {}", publication.getId(),
								(publication.getTitles().isEmpty() ? "(none)" : publication.getTitles().get(0)));
						break;
					case "ID": // EndNote Publication number
						publication.setId(fieldContent);
						// log.debug("Read ID {}", fieldContent);
						break;
					case "J2": // Alternate journal
						publication.addJournals(fieldContent);
						break;
					case "OP": // in PubMed: original title, in Web of Science (at least for conference papaers):
								// conference title
						if ("CONF".equals(publication.getReferenceType())) {
							publication.addJournals(fieldContent);
						} else {
							publication.addTitles(fieldContent);
						}
						break;
					case "PY": // Publication year
						publication.parsePublicationYear(fieldContent);
						break;
					case "SN": // ISSN / ISBN
						publication.addIssns(fieldContent);
						previousFieldName = fieldName;
						break;
					// Ovid Medline in RIS export has author address in repeatable M2 field,
					// EndNote 20 shows the content in field with label "Start Page",
					// but export file of such a record has this content in SE field!
					case "SE": // pages (Embase (which provider?), Ovid PsycINFO: examples in some SRA datasets)
					case "SP": // pages
						publication.parsePages(fieldContent, fieldName);
						break;
					/*
					 * original non-English titles:
					 * - PubMed: OP 
					 * - Embase: continuation line of title
					 * - Scopus: ST and TT?
					 */
					case "ST": // Original Title in Scopus
						publication.addTitles(fieldContent);
						break;
					case "T2": // Journal title / Book title
						if (fieldContent.startsWith("https://clinicaltrials.gov")) {
							publication.setClinicalTrialGov(true);
						}
						publication.addJournals(fieldContent);
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
						if (!conferencePattern.matcher(fieldContent).matches()) {
							publication.addJournals(fieldContent);
							publication.addTitles(fieldContent);
						}
						break;
					// ??? in Embase the original title is on the continuation line:
					// "Een 45-jarige patiente met chronische koliekachtige abdominale
					// pijn". Not found in test set!
					case "TI": // Title
						publication.addTitles(fieldContent);
						// Don't do this in IOService::readRecords because these 2 patterns are only applied to TI
						// field, not to the other fields which are added to List<String> titles
						if (replyPattern.matcher(fieldContent.toLowerCase()).matches()
								|| erratumPattern.matcher(fieldContent).matches()
								|| (fieldContent.endsWith(")") && sourcePattern.matcher(fieldContent).matches())
								|| commentPattern.matcher(fieldContent).matches()) {
							publication.setReply(true);
							publication.setTitle(fieldContent);
						}
						if (phasePattern.matcher(fieldContent.toLowerCase()).matches()) {
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
						if (fieldContent.startsWith("https://clinicaltrials.gov")) {
							publication.setClinicalTrialGov(true);
							publication.addJournals(fieldContent);
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
						publication.addDois(line);
						break;
					case "SN":
						publication.addIssns(line);
						break;
					case "TI": // EMBASE original title
						publication.addTitles(line);
						break;
					case "UR":
						if (line.startsWith("https://clinicaltrials.gov")) {
							publication.setClinicalTrialGov(true);
							publication.addJournals(line);
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
	 * Records are read into a TreeMap, with continuation lines added. 
	 * writeRecords(...) does the replacements, and writes to the output file.
	 */
	// @formatter:off
	public int writeDeduplicatedRecords(List<Publication> publications, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<Publication> recordsToKeep = publications.stream().filter(Publication::getKeptRecord).toList();
		log.debug("Records to be kept: {}", recordsToKeep.size());

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
				line = unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
				Matcher matcher = risLinePattern.matcher(line);
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
						if (publication != null && publication.getKeptRecord()) {
							writeRecord(map, publication, bw, true);
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
								map.put(fieldName, map.get(fieldName) + "\n" + fieldContent);
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

	public int writeMarkedRecords(List<Publication> publications, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<Publication> recordsToKeep = publications.stream().filter(Publication::getKeptRecord).toList();
		log.debug("Records to be kept: {}", recordsToKeep.size());

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
				line = unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
				Matcher matcher = risLinePattern.matcher(line);
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
						if (publication != null && publication.getKeptRecord()) {
							map.put(fieldName, fieldContent);
							if (publication.getLabel() != null) {
								map.put("LB", publication.getLabel());
							}
							writeRecord(map, publication, bw, false);
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
	private void writeRecord(Map<String, String> map, Publication publication, BufferedWriter bw, boolean enhance)
			throws IOException {
		if (enhance) {
			if (!publication.getDois().isEmpty()) {
				map.put("DO", "https://doi.org/"
						+ publication.getDois().stream().collect(Collectors.joining("\nhttps://doi.org/")));
			}
			if (publication.getPageStart() != null) {
				if (publication.getPageEnd() != null && !publication.getPageEnd().equals(publication.getPageStart())) {
					map.put("SP", publication.getPageStart() + (publication.isPagesWithComma() ? ", " : "-") + publication.getPageEnd());
				} else {
					map.put("SP", publication.getPageStart());
				}
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
					urlList.addFirst(url);
					map.put("UR", urlList.stream().collect(Collectors.joining("\nUR  - ")));
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
				line = unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
				Matcher matcher = risLinePattern.matcher(line);
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
							writeRecord(map, null, bw, false);
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
				line = unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
				Matcher matcher = risLinePattern.matcher(line);
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
							writeRecord(map, null, bw, false);
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
}
