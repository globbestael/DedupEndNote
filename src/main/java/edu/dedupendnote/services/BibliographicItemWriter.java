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
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.BibliographicItem;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BibliographicItemWriter {

	// @formatter:off
	/**
	 * - PageStart (PG) and DOIs (DO) are replaced or inserted, but written at the same place as in the input file to
	 *   make comparisons between input file and output file easier.
	 * - Absent BibliographicItem year (PY) is replaced if there is one found in a duplicate record.
	 * - Author (AU) Anonymous is skipped.
	 * - Title (TI) is replaced with the longest duplicate title when it contains "Reply".
	 * - Article Number (C7) is skipped.
	 * - Absent Journal Name (T2) is copied from J2 (or filed in based on DOI foor SSRN): for embase.com records
	 *   (but no check on this origin!)
	 *
	 * bibliographicItems are read into a TreeMap, with continuation lines added.
	 * writebibliographicItems(...) does the replacements, and writes to the output file.
	 */
	// @formatter:on
	public int writeDeduplicatedBibliographicItems(List<BibliographicItem> bibliographicItems, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<BibliographicItem> bibliographicItemsToKeep = bibliographicItems.stream().filter(BibliographicItem::isKeptBibliographicItem).toList();
		log.debug("Publications to be kept: {}", bibliographicItemsToKeep.size());

		Map<Integer, BibliographicItem> recordIdMap = bibliographicItems.stream()
			.filter(p -> p.getId() > 0)
			.collect(Collectors.toMap(BibliographicItem::getId, Function.identity()));

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
			BibliographicItem bibliographicItem = null;
			int phantomId = 0;
			String realId = null;

			while ((line = br.readLine()) != null) {
				lineNumber++;
				line = NormalizationService.normalizeHyphensAndWhitespace(line);
				Matcher matcher = BibliographicItemReader.RIS_LINE_PATTERN.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3);
					previousFieldName = "XYZ";
					switch (fieldName) {
					case "ER":
						map.put(fieldName, fieldContent);
						phantomId++;
						if (realId == null) {
							bibliographicItem = recordIdMap.get(phantomId);
							if (bibliographicItem != null) {
								bibliographicItem.setId(phantomId);
								map.put("ID", Integer.toString(phantomId));
							}
						}
						if (bibliographicItem != null && bibliographicItem.isKeptBibliographicItem()) {
							writeBibliographicItem(map, bibliographicItem, bw, true);
							numberWritten++;
						}
						map.clear();
						realId = null;
						break;
					case "ID": // EndNote BibliographicItem number
						map.put(fieldName, fieldContent);
						realId = fieldContent;
						bibliographicItem = recordIdMap.get(Integer.parseInt(realId));
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
            String message = "IOException while writing deduplicated records to %s at line %d: %s".formatted(outputFileName, lineNumber, e.getMessage());
            log.error(message, e);
            throw new RuntimeException(message, e);
		}
		log.debug("Finished writing to file. # records: {}", numberWritten);
		return numberWritten;
	}

	public int writeMarkedBibliographicItems(List<BibliographicItem> bibliographicItems, String inputFileName, String outputFileName) {
		log.debug("Start writing to file {}", outputFileName);
		List<BibliographicItem> bibliographicItemsToKeep = bibliographicItems.stream().filter(BibliographicItem::isKeptBibliographicItem).toList();
		log.debug("Publications to be kept: {}", bibliographicItemsToKeep.size());

		Map<Integer, BibliographicItem> recordIdMap = bibliographicItems.stream()
			.filter(p -> p.getId() > 0)
			.collect(Collectors.toMap(BibliographicItem::getId, Function.identity()));

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
			BibliographicItem bibliographicItem = null;
			int phantomId = 0;
			String realId = null;

			while ((line = br.readLine()) != null) {
				line = NormalizationService.normalizeHyphensAndWhitespace(line);
				Matcher matcher = BibliographicItemReader.RIS_LINE_PATTERN.matcher(line);
				if (matcher.matches()) {
					fieldName = matcher.group(1);
					fieldContent = matcher.group(3);
					previousFieldName = "XYZ";
					switch (fieldName) {
					case "ER":
						phantomId++;
						if (realId == null) {
							bibliographicItem = recordIdMap.get(phantomId);
							if (bibliographicItem != null) {
								bibliographicItem.setId(phantomId);
							}
							map.put("ID", Integer.toString(phantomId));
						}
						if (bibliographicItem != null && bibliographicItem.isKeptBibliographicItem()) {
							map.put(fieldName, fieldContent);
							if (bibliographicItem.getLabel() != null) {
								map.put("LB", bibliographicItem.getLabel());
							}
							writeBibliographicItem(map, bibliographicItem, bw, false);
							numberWritten++;
						}
						map.clear();
						realId = null;
						break;
					case "ID": // EndNote BibliographicItem number
						map.put(fieldName, fieldContent);
						realId = fieldContent;
						bibliographicItem = recordIdMap.get(Integer.parseInt(realId));
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
	private void writeBibliographicItem(Map<String, String> map, @Nullable BibliographicItem bibliographicItem, BufferedWriter bw, boolean enhance)
			throws IOException {
		if (enhance && bibliographicItem != null) {
			if (!bibliographicItem.getDois().isEmpty()) {
				map.put("DO", "https://doi.org/"
						+ bibliographicItem.getDois().stream().collect(Collectors.joining("\nhttps://doi.org/")));
			}
			if (bibliographicItem.getPagesOutput() == null || bibliographicItem.getPagesOutput().isEmpty()) {
				map.remove("SP");
			} else {
				map.put("SP", bibliographicItem.getPagesOutput());
			}
			if (bibliographicItem.isReply() || bibliographicItem.getTitle() != null) {
				map.put("TI", bibliographicItem.getTitle());
				map.put("ST", bibliographicItem.getTitle());
			}
			if (bibliographicItem.isClinicalTrialGov()) {
				map.put("TY", "JOUR");
				map.put("T2", "https://clinicaltrials.gov");
				String url = "https://clinicaltrials.gov/study/" + bibliographicItem.getPageStart();
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
			if (bibliographicItem.getAuthors().isEmpty() && ("Anonymous".equals(map.get("AU")) || "Nct".equals(map.get("AU")))) {
				map.remove("AU");
			}
			if (!map.containsKey("PY") && bibliographicItem.getPublicationYear() != 0) {
				map.put("PY", Integer.toString(bibliographicItem.getPublicationYear()));
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
}
