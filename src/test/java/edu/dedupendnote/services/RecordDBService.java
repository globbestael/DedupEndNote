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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.Record;
import edu.dedupendnote.domain.RecordDB;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RecordDBService {
	private UtilitiesService utilities = new UtilitiesService();

	/*
	 *  FIXME: These are the same fieldnames as the @JSonPropertyOrder({...}) of RecordDB.
	 *  Can a Spring property be used in both cases?
	 *  See: https://stackoverflow.com/questions/35089257/conditional-jsonproperty-using-jackson-with-spring-boot
	 *  There is a findSerializationPropertyOrder(...) method in com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
	 *  
	 *  Alternative: can the value be set in @JSonPropertyOrder({...}), read from this @JSonPropertyOrder({...}) and be used here?
	 */
	private static List<String> dbFields = Arrays.asList("id", "dedupid", "correction", "validated", "true_pos", "true_neg", "false_pos", "false_neg", "unsolvable", "authors_truncated", "authors", "publ_year", "title_truncated", "title", "title2", "volume", "issue", "pages", "article_number", "dois", "publ_type", "database", "number_authors");

	public int writeMarkedRecordsForDB(List<Record> records, String inputFileName, String outputFileName) {
		List<RecordDB> recordDBs = convertToRecordDB(records, inputFileName);
		int numberWritten = saveRecordDBs(recordDBs, outputFileName);
		return numberWritten;
	}

	public int saveRecordDBs(List<RecordDB> recordDBs, String outputFileName) {
		// FIXME: alter to validation_results? Plus date?
		outputFileName = outputFileName.replace("mark.", "markDB."); 
		log.debug("Start writing {} records to file {}", recordDBs.size(), outputFileName);
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName))) {
			bw.write(dbFields.stream().collect(Collectors.joining("\t")) + "\n");
			for (RecordDB r : recordDBs) {
				writeRecordForDB(r, bw);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		log.debug("Finished writing {} records to file {}", recordDBs.size(), outputFileName);
		return recordDBs.size();
	}

	public List<RecordDB> convertToRecordDB(List<Record> records, String inputFileName) {
		boolean hasBom = utilities.detectBom(inputFileName);

		Map<String, Record> recordIdMap = records.stream()
				.filter(r -> ! r.getId().startsWith("-"))
				.collect(Collectors.toMap(Record::getId, Function.identity()));

		List<RecordDB> recordDBs = new ArrayList<>();
		String fieldContent = null;
		String fieldName = null;
		RecordDB recordDB = new RecordDB();

		try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
			if (hasBom) {
				br.skip(1);
			}
			String line;
			Record record = null;
			while ((line = br.readLine()) != null) {
				line = IOService.unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
				Matcher matcher = IOService.risLinePattern.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3);
					switch (fieldName) {
						case "AU":
							recordDB.getAuthorsList().add(fieldContent);
							break;
						case "C7":
							recordDB.setArticleNumber(fieldContent); break;
						case "DO":
							recordDB.getDoisList().add(fieldContent);
							break;
						case "DP":
							recordDB.setDatabase(fieldContent); break;
						case "ER":
							record = recordIdMap.get(String.valueOf(recordDB.getId()));
							if (record != null) {
								if (record.getLabel() != null) {
									recordDB.setDedupid(Integer.valueOf(record.getLabel()));
								}
								recordDBs.add(recordDB);
							}
							recordDB = new RecordDB();
							break;
						case "ID": // EndNote Record number
							recordDB.setId(Integer.valueOf(fieldContent));
							break;
						case "IS":
							recordDB.setIssue(fieldContent); break;
						case "PY":
							recordDB.setPublYear(Integer.valueOf(fieldContent.trim())); break;
						case "SP":
							recordDB.setPages(fieldContent); break;
						case "T2":	// truncated at 254 characters
							recordDB.setTitle2(fieldContent.substring(0, Math.min(fieldContent.length(), 254))); break;
						case "TI":
							recordDB.setTitleTruncated(fieldContent.substring(0, Math.min(fieldContent.length(), 254)));
							recordDB.setTitle(fieldContent);
							break;
						case "TY":
							recordDB.setPublType(fieldContent); break;
						case "VL":
							recordDB.setVolume(fieldContent); break;
						default:
							// previousFieldName = fieldName;
							break;
					}
				} else {	// continuation line
					;
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return recordDBs;
	}

	private void writeRecordForDB(RecordDB recordDB, BufferedWriter bw) throws IOException {
		if (!recordDB.getAuthorsList().isEmpty()) {
			String authors = recordDB.getAuthorsList().stream().collect(Collectors.joining("; "));
			recordDB.setAuthorsTruncated(authors.substring(0, Math.min(authors.length(), 254)));
			recordDB.setAuthors(authors);
		}
		if (!recordDB.getDoisList().isEmpty()) {
			recordDB.setDois(recordDB.getDoisList().stream().collect(Collectors.joining("; ")));
		}
		// log.error("Line: {}",  recordDB.toDBLine());
		bw.write(recordDB.toDBLine());
	}
}
