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

import edu.dedupendnote.domain.Record;
import edu.dedupendnote.domain.RecordDB;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class IOService {
	private UtilitiesService utilities = new UtilitiesService();
	/*
	 * Patterns
	 */
	/**
	 * Pattern to identify conferences in the T3 field
	 */
	// private static Pattern conferencePattern = Pattern.compile(".*(^[0-9]|\\d{4,4}|Annual|Conference|Congress|Meeting|Society|Symposium).*");
	private static Pattern conferencePattern = Pattern.compile("(^[0-9]|(.*(\\d{4,4}|Annual|Conference|Congress|Meeting|Society|Symposium))).*");
	/**
	 * Pattern to identify clinical trials phase (1 ..4, i .. iv)
	 */
	private static Pattern phasePattern = Pattern.compile(".*phase\\s(\\d|i).*", Pattern.CASE_INSENSITIVE);
	// Don't use "Response" as last word, e.g: Endothelial cell injury in cardiovascular surgery: the procoagulant response
	private static Pattern replyPattern = Pattern.compile("(.*\\breply\\b.*|.*author(.+)respon.*|^response$)");
	/*
	 * If field content starts with a comma (",") EndNote exports "[Fieldname]  -,", NOT "[Fieldname]  - ," (EndNote X9.3.3)
	 * This pattern skips that initial comma, not the space which may come after that comma!
	 */
	public static Pattern risLinePattern = Pattern.compile("(^[A-Z][A-Z0-9])(  -[ ,\\u00A0])(.*)$");
	/**
	 * Unusual white space characters within input fields (LINE SEPARATOR, NO-BREAK SPACE): will be replaced by SPACE 
	 */
	public static Pattern unusualWhiteSpacePattern = Pattern.compile("(\\u2028|\\u00A0)");

	public List<Record> readRecords(String inputFileName) {
		List<Record> records = new ArrayList<>();
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Record record = new Record();
		
		boolean hasBom = utilities.detectBom(inputFileName);
		
		// Line starting with "TY  - " triggers creation of record, line containing "ER  - " signals end of record
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
					fieldContent = matcher.group(3);
					// Added for the ASySD Depression set 
					if ("NA".equals(fieldContent)) {
						continue;
					}
					previousFieldName = "XYZ";
					switch (fieldName) {
//						case "AB": // Abstract
//							record.addAbstracttext(fieldContent);
//							break;
						case "AU": // Authors
							// XML files can put all authors on 1 line separated by "; "
							if (fieldContent.contains("; ")) {
								List<String> authors = Arrays.asList(fieldContent.split("; "));
								for (String author : authors) {
									record.addAuthors(author);
								}
							} else {
								record.addAuthors(fieldContent);
							}
							break;
						case "C7": // article number (Scopus and WoS when imported as RIS format)
							record.parsePages(fieldContent);
							break;
						case "DO": // DOI
							record.addDois(fieldContent);
							previousFieldName = fieldName;
							break;
						case "ER":
							record.addReversedTitles();
							record.fillAllAuthors();
							records.add(record);
							log.debug("Record read with id {} and title: {}", record.getId(), record.getTitles().get(0));
							break;
						case "ID": // EndNote Record number
							record.setId(fieldContent);
							log.debug("Read ID {}",  fieldContent);
							break;
						case "J2": // Alternate journal
							record.addJournals(fieldContent);
							break;
						case "OP": // original title (PubMed)
							record.addTitles(fieldContent);
							break;
						case "PY": // Publication year
							record.setPublicationYear(Integer.valueOf(fieldContent.trim()));
							break;
						case "SN": // ISSN / ISBN
							record.addIssns(fieldContent);
							previousFieldName = fieldName;
							break;
						// Ovid Medline in RIS export has author address in repeatable M2 field,
						// EndNote 20 shows the content in field with label "Start Page",
						// but export file of such a record has this content in SE field! 
						case "SE": // pages (Embase (which provider), Ovid PsycINFO: examples in some SRA datasets)
						case "SP": // pages
							record.parsePages(fieldContent);
							break;
						/*
						 * original non-English titles: - PubMed: OP - Embase: continuation line of title - Scopus: ST and TT?
						 */
						case "ST":	// Original Title in Scopus
							record.addTitles(fieldContent);
							break;
						case "T2": // Journal title / Book title
							record.addJournals(fieldContent);
							break;
						/*
						 * T3 (especially used in EMBASE (OVID)) has 3 types of content:
						 * - conference name (majority of cases)
						 * - original title
						 * - alternative journal name
						 * 
						 * Present solution:
						 * - skip it if it contains a number or "Annual|Conference|Congress|Meeting|Society"
						 *   ("Asian Pacific Digestive Week 2014. Bali Indonesia.",
						 *    "12th World Congress of the International Hepato-Pancreato-Biliary Association. Sao Paulo Brazil.",
						 *    "International Liver Transplantation Society 15th Annual International Congress. New York, NY United States.")
						 * - add it as Title
						 * - add it as Journal
						 */
						case "T3": // Book section
							if (! conferencePattern.matcher(fieldContent).matches()) {
								record.addJournals(fieldContent);
								record.addTitles(fieldContent);
							}
							break;
						// ??? in Embase the original title is on the continuation line: "Een 45-jarige patiente met chronische koliekachtige abdominale pijn". Not found in test set!
						case "TI": // Title
							record.addTitles(fieldContent);
							// Don't do this in IOService::readRecords because these 2 patterns are only applied to TI field,
							// not to the other fields which are added to List<String> titles
							if (replyPattern.matcher(fieldContent.toLowerCase()).matches()) {
								record.setReply(true);
								record.setTitle(fieldContent);
							}
							if (phasePattern.matcher(fieldContent.toLowerCase()).matches()) {
								record.setPhase(true);
							}
							previousFieldName = fieldName;
							break;
						// TODO: When does TT occur? is translated (i.e. original?) title
						case "TY": // Type
							record = new Record();
							break;
						default:
							previousFieldName = fieldName;
							break;
					}
				} else {	// continuation line 
					switch (previousFieldName) {
						case "DO":
							record.addDois(line);
							break;
						case "SN":
							record.addIssns(line);
							break;
						case "TI": // EMBASE original title
							record.addTitles(line);
							break;
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.debug("Records read: {}", records.size());
		return records;
	}

	/*
	 *  PageStart (PG) and DOIs (DO) are replaced or inserted, but written at the same place as in the input file
	 *  to make comparisons between input file and output file easier.
	 *  Absent Publication year (PY) is replaced if there is one found in a duplicate record.
	 *  Author (AU) Anonymous is skipped.
	 *  Title (TI) is replaced with the longest duplicate title when it contains "Reply".
	 *  Article Number (C7) is skipped.
	 *  
	 *  Records are read into a TreeMap, with continuation lines added.
	 *  writeRecords(...) does the replacements, and writes to the output file.
	 */
	public int writeDeduplicatedRecords(List<Record> records, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<Record> recordsToKeep = records.stream().filter(Record::getKeptRecord).collect(Collectors.toList());
		log.debug("Records to be kept: {}", recordsToKeep.size());

		Map<String, Record> recordIdMap = records.stream()
				.filter(r -> ! r.getId().startsWith("-"))
				.collect(Collectors.toMap(Record::getId, Function.identity()));

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
			Record record = null;
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
							if (record != null && record.getKeptRecord() == true) {
								writeRecord(map, record, bw, true);
								numberWritten++;
							}
							map.clear();
							break;
						case "ID": // EndNote Record number
							map.put(fieldName, fieldContent);
							String id = line.substring(6);
							record = recordIdMap.get(id);
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
				} else {	// continuation line
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
	 * Ordering of an EndNote export RIS file: the fields are ordered alphabetically, except for TY (first), and ID and ER (last fields)
	 */
	private void writeRecord(Map<String, String> map, Record record, BufferedWriter bw, Boolean enhance) throws IOException {
		if (enhance) {
			if (!record.getDois().isEmpty()) {
				map.put("DO", "https://doi.org/" + record.getDois().keySet().stream().collect(Collectors.joining("\nhttps://doi.org/")));
			}
			if (record.getPageStart() != null) {
				if (record.getPageEnd() != null && ! record.getPageEnd().equals(record.getPageStart())) {
					map.put("SP", record.getPageStart() + "-" + record.getPageEnd());
				} else {
					map.put("SP", record.getPageStart());
				}
			}
			if (record.isReply()) {
				map.put("TI", record.getTitle());
			}
			// An author as "Nct," should be kept
			if (record.getAuthors().isEmpty() && "Anonymous".equals(map.get("AU"))) {
				map.remove("AU");
			}
			if (! map.containsKey("PY") && record.getPublicationYear() != 0) {
				map.put("PY", record.getPublicationYear().toString());
			}
		}
		
		StringBuffer sb = new StringBuffer();
		sb.append("TY  - ").append(map.get("TY")).append("\n");
		map.forEach((k,v) -> {
			if (! k.matches("(C7|ER|ID|TY|XYZ)")) {
				sb.append(k).append("  - ").append(v).append("\n");
			}
		});
		sb.append("ID  - ").append(map.get("ID")).append("\n");
		sb.append("ER  - ").append("\n\n");
		bw.write(sb.toString());
	}

	public int writeMarkedRecords(List<Record> records, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<Record> recordsToKeep = records.stream().filter(Record::getKeptRecord).collect(Collectors.toList());
		log.debug("Records to be kept: {}", recordsToKeep.size());

		Map<String, Record> recordIdMap = records.stream()
				.filter(r -> ! r.getId().startsWith("-"))
				.collect(Collectors.toMap(Record::getId, Function.identity()));

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
			Record record = null;
			while ((line = br.readLine()) != null) {
				line = unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
				Matcher matcher = risLinePattern.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3);
					previousFieldName = "XYZ";
					switch (fieldName) {
						case "ER":
							if (record != null && record.getKeptRecord() == true) {
								map.put(fieldName, fieldContent);
								if (record.getLabel() != null) {
									map.put("LB", record.getLabel());
								}
								writeRecord(map, record, bw, false);
								numberWritten++;
							}
							map.clear();
							break;
						case "ID": // EndNote Record number
							map.put(fieldName, fieldContent);
							String id = fieldContent;
							record = recordIdMap.get(id);
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
				} else {	// continuation line
					map.put(previousFieldName, map.get(previousFieldName) + "\n" + line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.debug("Finished writing to file. # records: {}", numberWritten);
		return numberWritten;
	}

	/**
	 * writeRisWithTRUTH(...): writes a RIS file with Caption field ['Duplicate', 'Unknown', empty] and, in case of true duplicates, with Label field the ID if the record
	 * which will be kept.
	 * 
	 * Caption field:
	 * - Duplicate: validated and True Positive
	 * - empty: validated and True Negative
	 * - Unknown: not validated
	 *  
	 * All records which are duplicates have the same ID in Label field, so this ID could be considered as the ID of a duplicate group.
	 * DedupEndNote in non-Mark mode would write only the record where the record ID is the same as Label.
	 * 
	 * @param inputFileName	filename of a RIS export file
	 * @param truthRecords	List<RecordDB> of validated records (TAB delimited export file from validation DB)
	 * @param outputFileName	filename of a RIS file
	 */
	public void writeRisWithTRUTH(List<RecordDB> truthRecords, String inputFileName, String outputFileName) {
		int numberWritten = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();
		
		Map<Integer, RecordDB> truthMap = truthRecords.stream().collect(Collectors.toMap(RecordDB::getId, Function.identity()));

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
						case "ID": // EndNote Record number
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
				} else {	// continuation line
					map.put(previousFieldName, map.get(previousFieldName) + "\n" + line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.debug("Finished writing to file. # records: {}", numberWritten);
	}

	public void writeRisWithTRUTH_forDS(List<RecordDB> truthRecords, String inputFileName, String outputFileName) {
		int numberWritten = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();
		
		Map<Integer, RecordDB> truthMap = truthRecords.stream().collect(Collectors.toMap(RecordDB::getId, Function.identity()));

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
						case "ID": // EndNote Record number
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
				} else {	// continuation line
					map.put(previousFieldName, map.get(previousFieldName) + "\n" + line);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.debug("Finished writing to file. # records: {}", numberWritten);
	}

//	public int writeMarkedRecords_old(List<Record> records, String inputFileName, String outputFileName) {
//		log.debug("Start writing to file {}", outputFileName);
//
//		Map<String, Record> recordIdMap = records.stream()
//				.filter(r -> ! r.getId().startsWith("-"))
//				.collect(Collectors.toMap(Record::getId, Function.identity()));
//		StringBuffer sb = new StringBuffer();
//		int numberWritten = 0;
//		String fieldContent = null;
//		String fieldName = null;
//		boolean hasBom = utilities.detectBom(inputFileName);
//				
//		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName));
//			 BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
//			if (hasBom) {
//				br.skip(1);
//			}
//			String line;
//			Record record = null;
//			while ((line = br.readLine()) != null) {
//				line = unusualPunctuationPattern.matcher(line).replaceAll(" ");
//				Matcher matcher = risLinePattern.matcher(line);
//				if (matcher.matches()) {
//					fieldName = matcher.group(1);
//					fieldContent = matcher.group(3);
//					switch(fieldName) {
//					case "ER":
//						if (record != null) {
//							if (record.getLabel() != null) {
//								sb.append("LB  - ").append(record.getLabel()).append("\n");
//							}
//							sb.append("C8  - ").append(record.getId()).append("\n");
//							sb.append(line).append("\n\n");
//							bw.write(sb.toString());
//							numberWritten++;
//						}
//						sb.setLength(0);
//						break;
//					case "ID":
//						String id = fieldContent;
//						record = recordIdMap.get(id);
//						// log.debug("Writing record with ID {}: {}", id, record);
//						break;
//					case "LB":
//						; // these fields will use content from the Record (see above at ER-line)
//						break;
//					default:
//						sb.append(line).append("\n");
//					}
//				} else {
//					sb.append(line).append("\n");
//				}
//			}
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		log.debug("Finished writing to file. # records: {}", numberWritten);
//		return numberWritten;
//	}

}
