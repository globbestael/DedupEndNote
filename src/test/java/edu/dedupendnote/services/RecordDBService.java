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

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.PublicationDB;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RecordDBService {

	private UtilitiesService utilities = new UtilitiesService();

	/*
	 * FIXME: These are the same fieldnames as the @JSonPropertyOrder({...}) of
	 * PublicationDB. Can a Spring property be used in both cases? See:
	 * https://stackoverflow.com/questions/35089257/conditional-jsonproperty-using-jackson
	 * -with-spring-boot There is a findSerializationPropertyOrder(...) method in
	 * com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector
	 *
	 * Alternative: can the value be set in @JSonPropertyOrder({...}), read from
	 * this @JSonPropertyOrder({...}) and be used here?
	 */
	private static List<String> dbFields = Arrays.asList("id", "dedupid", "correction", "validated", "true_pos",
			"true_neg", "false_pos", "false_neg", "unsolvable", "authors_truncated", "authors", "publ_year",
			"title_truncated", "title", "title2", "volume", "issue", "pages", "article_number", "dois", "publ_type",
			"database", "number_authors");

	public int writeMarkedRecordsForDB(List<Publication> publications, String inputFileName, String outputFileName) {
		List<PublicationDB> publicationDBs = convertToRecordDB(publications, inputFileName);
		int numberWritten = saveRecordDBs(publicationDBs, outputFileName);
		return numberWritten;
	}

	public int saveRecordDBs(List<PublicationDB> publicationDBs, String outputFileName) {
		// FIXME: alter to validation_results? Plus date?
		outputFileName = outputFileName.replace("mark.", "markDB.");
		log.debug("Start writing {} records to file {}", publicationDBs.size(), outputFileName);
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName))) {
			bw.write(dbFields.stream().collect(Collectors.joining("\t")) + "\n");
			for (PublicationDB r : publicationDBs) {
				writeRecordForDB(r, bw);
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		log.debug("Finished writing {} records to file {}", publicationDBs.size(), outputFileName);
		return publicationDBs.size();
	}

	public List<PublicationDB> convertToRecordDB(List<Publication> publications, String inputFileName) {
		boolean hasBom = utilities.detectBom(inputFileName);

		Map<String, Publication> recordIdMap = publications.stream()
			.filter(r -> !r.getId().startsWith("-"))
			.collect(Collectors.toMap(Publication::getId, Function.identity()));

		List<PublicationDB> publicationDBs = new ArrayList<>();
		String fieldContent = null;
		String fieldName = null;
		PublicationDB publicationDB = new PublicationDB();

		try (BufferedReader br = new BufferedReader(new FileReader(inputFileName))) {
			if (hasBom) {
				br.skip(1);
			}
			String line;
			Publication publication = null;
			while ((line = br.readLine()) != null) {
				line = IORisService.unusualWhiteSpacePattern.matcher(line).replaceAll(" ");
				Matcher matcher = IORisService.risLinePattern.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3);
					switch (fieldName) {
						case "AU":
							publicationDB.getAuthorsList().add(fieldContent);
							break;
						case "C7":
							publicationDB.setArticleNumber(fieldContent);
							break;
						case "DO":
							publicationDB.getDoisList().add(fieldContent);
							break;
						case "DP":
							publicationDB.setDatabase(fieldContent);
							break;
						case "ER":
							publication = recordIdMap.get(String.valueOf(publicationDB.getId()));
							if (publication != null) {
								if (publication.getLabel() != null) {
									publicationDB.setDedupid(Integer.valueOf(publication.getLabel()));
								}
								publicationDBs.add(publicationDB);
							}
							publicationDB = new PublicationDB();
							break;
						case "ID": // EndNote Publication number
							publicationDB.setId(Integer.valueOf(fieldContent));
							break;
						case "IS":
							publicationDB.setIssue(fieldContent);
							break;
						case "PY":
							publicationDB.setPublYear(Integer.valueOf(fieldContent.trim()));
							break;
						case "SP":
							publicationDB.setPages(fieldContent);
							break;
						case "T2": // truncated at 254 characters
							publicationDB.setTitle2(fieldContent.substring(0, Math.min(fieldContent.length(), 254)));
							break;
						case "TI":
							publicationDB
								.setTitleTruncated(fieldContent.substring(0, Math.min(fieldContent.length(), 254)));
							publicationDB.setTitle(fieldContent);
							break;
						case "TY":
							publicationDB.setPublType(fieldContent);
							break;
						case "VL":
							publicationDB.setVolume(fieldContent);
							break;
						default:
							// previousFieldName = fieldName;
							break;
					}
				} else { // continuation line
					;
				}
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		return publicationDBs;
	}

	private void writeRecordForDB(PublicationDB publicationDB, BufferedWriter bw) throws IOException {
		if (!publicationDB.getAuthorsList().isEmpty()) {
			String authors = publicationDB.getAuthorsList().stream().collect(Collectors.joining("; "));
			publicationDB.setAuthorsTruncated(authors.substring(0, Math.min(authors.length(), 254)));
			publicationDB.setAuthors(authors);
		}
		if (!publicationDB.getDoisList().isEmpty()) {
			publicationDB.setDois(publicationDB.getDoisList().stream().collect(Collectors.joining("; ")));
		}
		// log.error("Line: {}", recordDB.toDBLine());
		bw.write(publicationDB.toDBLine());
	}

}
