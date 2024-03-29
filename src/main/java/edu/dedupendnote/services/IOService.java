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
	// private static Pattern conferencePattern =
	// Pattern.compile(".*(^[0-9]|\\d{4,4}|Annual|Conference|Congress|Meeting|Society|Symposium).*");
	private static Pattern conferencePattern = Pattern
			.compile("(^[0-9]|(.*(\\d{4,4}|Annual|Conference|Congress|Meeting|Society|Symposium))).*");

	/** Pattern to identify clinical trials phase (1 ..4, i .. iv) */
	private static Pattern phasePattern = Pattern.compile(".*phase\\s(\\d|i).*", Pattern.CASE_INSENSITIVE);

	/*
	 * FIXME Java 11ff: Switch to NIO2. See
	 * https://horstmann.com/unblog/2023-04-09/index.html
	 */

	// Don't use "Response" as last word, e.g: Endothelial cell injury in
	// cardiovascular surgery: the procoagulant response
	private static Pattern replyPattern = Pattern.compile("(.*\\breply\\b.*|.*author(.+)respon.*|^response$)");

	/*
	 * If field content starts with a comma (",") EndNote exports "[Fieldname]  -,",
	 * NOT "[Fieldname]  - ," (EndNote X9.3.3) This pattern skips that initial
	 * comma, not the space which may come after that comma!
	 */
	public static Pattern risLinePattern = Pattern.compile("(^[A-Z][A-Z0-9])(  -[ ,\\u00A0])(.*)$");

	/**
	 * All white space characters within input fields will replaced with a normal SPACE.
	 * LINE SEPARATOR and NO-BREAK SPACE have been observed in the test files. 
	 * The pattern uses a maximum view:
	 * - all white space / "Separator, space" characters (class Zs): https://www.fileformat.info/info/unicode/category/Zs/list.htm
	 * - all "Separator, Line" (Zl)  and "Separator, Paragraph" (Zp) characters
	 * - some "Control, other" characters (class Cc), but not all: https://www.fileformat.info/info/unicode/category/Cc/list.htm
	 * 
	 * Pattern excludes SPACE from class Zs for performance reason by making an intersection (&&) with the negation ([^ ]).
	 *  
	 * Tested in TextNormalizerTest
	 */
//	public static Pattern unusualWhiteSpacePattern = Pattern.compile("[\\p{Zs}\\p{Cc}]");
	public static Pattern unusualWhiteSpacePattern = Pattern.compile("[\\p{Zs}\\p{Zl}\\p{Zp}\\u0009\\u000A\\u000B\\u000C\\u000D&&[^ ]]");
	
	private UtilitiesService utilities = new UtilitiesService();

	public List<Publication> readPublications(String inputFileName) {
		List<Publication> publications = new ArrayList<>();
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Publication publication = new Publication();

		boolean hasBom = utilities.detectBom(inputFileName);
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
					if ((fieldContent.isEmpty() && ! "ER".equals(fieldName)) || "NA".equals(fieldContent)) {
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
					case "C7": // article number (Scopus and WoS when imported as RIS
						// format)
						publication.parsePages(fieldContent);
						break;
					case "DO": // DOI
						publication.addDois(fieldContent);
						previousFieldName = fieldName;
						break;
					case "ER":
						if (publication.getId() == null) {
							publication.setId(Integer.valueOf(missingId++).toString());
						}
						publication.addReversedTitles();
						publication.fillAllAuthors();
						publications.add(publication);
						log.debug("Publication read with id {} and title: {}", publication.getId(),
								publication.getTitles().get(0));
						break;
					case "ID": // EndNote Publication number
						publication.setId(fieldContent);
						log.debug("Read ID {}", fieldContent);
						break;
					case "J2": // Alternate journal
						publication.addJournals(fieldContent);
						break;
					case "OP": // in PubMed: original title, in Web of Science (at least for conference papaers): conference title
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
					// Ovid Medline in RIS export has author address in repeatable M2
					// field,
					// EndNote 20 shows the content in field with label "Start Page",
					// but export file of such a record has this content in SE field!
					case "SE": // pages (Embase (which provider?), Ovid PsycINFO:
						// examples in some SRA datasets)
					case "SP": // pages
						publication.parsePages(fieldContent);
						break;
					/*
					 * original non-English titles: - PubMed: OP - Embase: continuation line of
					 * title - Scopus: ST and TT?
					 */
					case "ST": // Original Title in Scopus
						publication.addTitles(fieldContent);
						break;
					case "T2": // Journal title / Book title
						publication.addJournals(fieldContent);
						break;
					/*
					 * T3 (especially used in EMBASE (OVID)) has 3 types of content: - conference
					 * name (majority of cases) - original title - alternative journal name
					 *
					 * Present solution: - skip it if it contains a number or
					 * "Annual|Conference|Congress|Meeting|Society"
					 * ("Asian Pacific Digestive Week 2014. Bali Indonesia.",
					 * "12th World Congress of the International Hepato-Pancreato-Biliary Association. Sao Paulo Brazil."
					 * ,
					 * "International Liver Transplantation Society 15th Annual International Congress. New York, NY United States."
					 * ) - add it as Title - add it as Journal
					 */
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
						// Don't do this in IOService::readRecords because these 2
						// patterns are only applied to TI field,
						// not to the other fields which are added to List<String>
						// titles
						if (replyPattern.matcher(fieldContent.toLowerCase()).matches()) {
							publication.setReply(true);
							publication.setTitle(fieldContent);
						}
						if (phasePattern.matcher(fieldContent.toLowerCase()).matches()) {
							publication.setPhase(true);
						}
						previousFieldName = fieldName;
						break;
					// TODO: When does TT occur? is translated (i.e. original?) title
					case "TY": // Type
						publication = new Publication();
						publication.setReferenceType(fieldContent);
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
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			log.error("In field {} with content {}: Number could not be parsed", fieldName, fieldContent);
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
			log.error("In field {} with content {}: other exception: {}", fieldName, fieldContent, e.getMessage());
		}
		log.debug("Records read: {}", publications.size());
		return publications;
	}

	/*
	 * PageStart (PG) and DOIs (DO) are replaced or inserted, but written at the
	 * same place as in the input file to make comparisons between input file and
	 * output file easier. Absent Publication year (PY) is replaced if there is one
	 * found in a duplicate record. Author (AU) Anonymous is skipped. Title (TI) is
	 * replaced with the longest duplicate title when it contains "Reply". Article
	 * Number (C7) is skipped.
	 *
	 * Records are read into a TreeMap, with continuation lines added.
	 * writeRecords(...) does the replacements, and writes to the output file.
	 */
	public int writeDeduplicatedRecords(List<Publication> publications, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<Publication> recordsToKeep = publications.stream().filter(Publication::getKeptRecord)
				.collect(Collectors.toList());
		log.debug("Records to be kept: {}", recordsToKeep.size());

		Map<String, Publication> recordIdMap = publications.stream().filter(r -> !r.getId().startsWith("-"))
				.collect(Collectors.toMap(Publication::getId, Function.identity()));

		int numberWritten = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();

		boolean hasBom = utilities.detectBom(inputFileName);

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
						map.put(fieldName, fieldContent);
						phantomId++;
						if (realId == null) {
							publication = recordIdMap.get(phantomId.toString());
							if (publication != null) {
								publication.setId(phantomId.toString());
								map.put("ID", phantomId.toString());
							}
						}
						if (publication != null && publication.getKeptRecord() == true) {
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

	public int writeMarkedRecords(List<Publication> publications, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<Publication> recordsToKeep = publications.stream().filter(Publication::getKeptRecord)
				.collect(Collectors.toList());
		log.debug("Records to be kept: {}", recordsToKeep.size());

		Map<String, Publication> recordIdMap = publications.stream().filter(r -> !r.getId().startsWith("-"))
				.collect(Collectors.toMap(Publication::getId, Function.identity()));

		int numberWritten = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();

		boolean hasBom = utilities.detectBom(inputFileName);

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
						if (publication != null && publication.getKeptRecord() == true) {
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
						break;	// to ensure that the present Label is not used.
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
	private void writeRecord(Map<String, String> map, Publication publication, BufferedWriter bw, Boolean enhance)
			throws IOException {
		if (enhance) {
			if (!publication.getDois().isEmpty()) {
				map.put("DO", "https://doi.org/"
						+ publication.getDois().keySet().stream().collect(Collectors.joining("\nhttps://doi.org/")));
			}
			if (publication.getPageStart() != null) {
				if (publication.getPageEnd() != null && !publication.getPageEnd().equals(publication.getPageStart())) {
					map.put("SP", publication.getPageStart() + "-" + publication.getPageEnd());
				} else {
					map.put("SP", publication.getPageStart());
				}
			}
			if (publication.isReply()) {
				map.put("TI", publication.getTitle());
			}
			// An author as "Nct," should be kept
			if (publication.getAuthors().isEmpty() && "Anonymous".equals(map.get("AU"))) {
				map.remove("AU");
			}
			if (!map.containsKey("PY") && publication.getPublicationYear() != 0) {
				map.put("PY", publication.getPublicationYear().toString());
			}
		}
		// in enhanced mode C7 (Article number) is skipped, in Mark mode C7 is NOT
		// skipped
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
	 * writeRisWithTRUTH(...): writes a RIS file with Caption field ['Duplicate',
	 * 'Unknown', empty] and, in case of true duplicates, with Label field the ID if
	 * the record which will be kept.
	 *
	 * <p>
	 * Caption field: - Duplicate: validated and True Positive - empty: validated
	 * and True Negative - Unknown: not validated
	 *
	 * <p>
	 * All records which are duplicates have the same ID in Label field, so this ID
	 * could be considered as the ID of a duplicate group. DedupEndNote in non-Mark
	 * mode would write only the record where the record ID is the same as Label.
	 *
	 * @param inputFileName  filename of a RIS export file
	 * @param truthRecords   List<PublicationDB> of validated records (TAB delimited
	 *                       export file from validation DB)
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

		boolean hasBom = utilities.detectBom(inputFileName);

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

		boolean hasBom = utilities.detectBom(inputFileName);

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

	// public int writeMarkedRecords_old(List<Publication> records, String
	// inputFileName,
	// String outputFileName) {
	// log.debug("Start writing to file {}", outputFileName);
	//
	// Map<String, Publication> recordIdMap = records.stream()
	// .filter(r -> ! r.getId().startsWith("-"))
	// .collect(Collectors.toMap(Publication::getId, Function.identity()));
	// StringBuffer sb = new StringBuffer();
	// int numberWritten = 0;
	// String fieldContent = null;
	// String fieldName = null;
	// boolean hasBom = utilities.detectBom(inputFileName);
	//
	// try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName));
	// BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
	// if (hasBom) {
	// br.skip(1);
	// }
	// String line;
	// Publication record = null;
	// while ((line = br.readLine()) != null) {
	// line = unusualPunctuationPattern.matcher(line).replaceAll(" ");
	// Matcher matcher = risLinePattern.matcher(line);
	// if (matcher.matches()) {
	// fieldName = matcher.group(1);
	// fieldContent = matcher.group(3);
	// switch(fieldName) {
	// case "ER":
	// if (record != null) {
	// if (record.getLabel() != null) {
	// sb.append("LB - ").append(record.getLabel()).append("\n");
	// }
	// sb.append("C8 - ").append(record.getId()).append("\n");
	// sb.append(line).append("\n\n");
	// bw.write(sb.toString());
	// numberWritten++;
	// }
	// sb.setLength(0);
	// break;
	// case "ID":
	// String id = fieldContent;
	// record = recordIdMap.get(id);
	// // log.debug("Writing record with ID {}: {}", id, record);
	// break;
	// case "LB":
	// ; // these fields will use content from the Publication (see above at
	// ER-line)
	// break;
	// default:
	// sb.append(line).append("\n");
	// }
	// } else {
	// sb.append(line).append("\n");
	// }
	// }
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// log.debug("Finished writing to file. # records: {}", numberWritten);
	// return numberWritten;
	// }

}
