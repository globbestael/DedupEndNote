package edu.dedupendnote.validation.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.BibliographicItemDB;
import edu.dedupendnote.services.BibliographicItemReader;
import edu.dedupendnote.services.NormalizationService;
import edu.dedupendnote.services.UtilitiesService;
import lombok.extern.slf4j.Slf4j;

/**
 * Validation-only write methods, used by ValidationTests and RecordDBService.
 * Lives in the test source tree; not loaded in production.
 */
@Slf4j
@Service
@Profile("test")
public class ValidationIOService {

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
	 * @param truthRecords   List<BibliographicItemDB> of validated records (TAB delimited export file from validation DB)
	 * @param outputFileName filename of a RIS file
	 */
	public void writeRisWithTRUTH(List<BibliographicItemDB> truthRecords, String inputFileName, String outputFileName) {
		int numberWritten = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();

		Map<Integer, BibliographicItemDB> truthMap = truthRecords.stream()
				.collect(Collectors.toMap(BibliographicItemDB::getId, Function.identity()));

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
				Matcher matcher = BibliographicItemReader.RIS_LINE_PATTERN.matcher(line);
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
									BibliographicItemDB bibliographicItemDB = truthMap.get(id);
									if (bibliographicItemDB.getDedupid() != null) {
										map.put("LB", bibliographicItemDB.getDedupid().toString());
									} else {
										map.put("LB", "");
									}
									map.put("CA", "Duplicate");
								} else {
									map.remove("CA");
									map.remove("LB");
								}
							} else {
								map.put("CA", "Unknown");
							}
							writeRecord(map, bw);
							numberWritten++;
						}
						map.clear();
						break;
					case "ID": // EndNote BibliographicItem number
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

	public void writeRisWithTRUTH_forDS(List<BibliographicItemDB> truthRecords, String inputFileName, String outputFileName) {
		int numberWritten = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();

		Map<Integer, BibliographicItemDB> truthMap = truthRecords.stream()
				.collect(Collectors.toMap(BibliographicItemDB::getId, Function.identity()));

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
				Matcher matcher = BibliographicItemReader.RIS_LINE_PATTERN.matcher(line);
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
								map.put("CA", map.getOrDefault("CA", "").toUpperCase());
								if (truthMap.get(id).isTruePositive()) {
									BibliographicItemDB bibliographicItemDB = truthMap.get(id);
									if (bibliographicItemDB.getDedupid() != null) {
										map.put("LB", bibliographicItemDB.getDedupid().toString());
									} else {
										map.put("LB", "");
									}
								} else {
									map.remove("LB");
								}
							} else {
								map.put("CA", map.getOrDefault("CA", "").toLowerCase());
							}
							writeRecord(map, bw);
							numberWritten++;
						}
						map.clear();
						break;
					case "ID": // EndNote BibliographicItem number
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

	// Duplicates the non-enhance path of BibliographicItemWriter.writeBibliographicItem.
	private void writeRecord(Map<String, String> map, BufferedWriter bw) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("TY  - ").append(map.get("TY")).append("\n");
		map.forEach((k, v) -> {
			if (!k.matches("(ER|ID|TY|XYZ)")) {
				sb.append(k).append("  - ").append(v).append("\n");
			}
		});
		sb.append("ID  - ").append(map.get("ID")).append("\n");
		sb.append("ER  - ").append("\n\n");
		bw.write(sb.toString());
	}
}
