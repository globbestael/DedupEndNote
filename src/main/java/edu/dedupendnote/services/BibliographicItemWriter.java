package edu.dedupendnote.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.services.normalization.NormalizationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class BibliographicItemWriter {

	private final EnrichmentService enrichmentService;

	public BibliographicItemWriter(EnrichmentService enrichmentService) {
		this.enrichmentService = enrichmentService;
	}

	// Fields are read into a TreeMap (continuation lines merged), written with TY first and ID/ER last.
	// In Remove Mode, enrich() synthesises the duplicate-set representation, enrichCochrane() and
	// enrichMap() apply further corrections before the fields are written. C7 (Article Number) is skipped.
	public int writeBibliographicItems(List<BibliographicItem> bibliographicItems, Path inputPath, Path outputPath,
			DeduplicationMode mode, Consumer<String> progressReporter) {
		log.debug("Start writing to file {}", outputPath);
		if (mode == DeduplicationMode.REMOVE) {
			progressReporter.accept("Enriching the " + bibliographicItems.size() + " deduplicated results");
			enrichmentService.enrich(bibliographicItems);
			enrichmentService.enrichCochrane(bibliographicItems);
		}
		List<BibliographicItem> bibliographicItemsToKeep = bibliographicItems.stream()
				.filter(BibliographicItem::isKeptBibliographicItem).toList();
		log.debug("Publications to be kept: {}", bibliographicItemsToKeep.size());
		if (mode == DeduplicationMode.REMOVE) {
			progressReporter.accept("Saving the " + bibliographicItemsToKeep.size() + " deduplicated results");
		}

		Map<Integer, BibliographicItem> recordIdMap = bibliographicItems.stream().filter(p -> p.getId() > 0)
				.collect(Collectors.toMap(BibliographicItem::getId, Function.identity()));

		int numberWritten = 0;
		int lineNumber = 0;
		String fieldContent = null;
		String fieldName = null;
		String previousFieldName = "XYZ";
		Map<String, String> map = new TreeMap<>();

		boolean hasBom = UtilitiesService.detectBom(inputPath);

		try (BufferedWriter bw = Files.newBufferedWriter(outputPath);
				BufferedReader br = Files.newBufferedReader(inputPath)) {
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
							if (mode == DeduplicationMode.MARK && bibliographicItem.getLabel() != null) {
								map.put("LB", String.valueOf(bibliographicItem.getLabel()));
							}
							writeBibliographicItem(map, bibliographicItem, bw, mode);
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
						if (mode == DeduplicationMode.MARK) {
							break; // drop stale label; the computed label is added at ER
						}
						// REMOVE mode: fall through to default so existing LB is preserved
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
			String message = "IOException while writing bibliographic items to %s at line %d: %s"
					.formatted(outputPath.getFileName(), lineNumber, e.getMessage());
			log.error(message, e);
			throw new RuntimeException(message, e);
		}
		log.debug("Finished writing to file. # records: {}", numberWritten);
		return numberWritten;
	}

	/*
	 * Ordering of an EndNote export RIS file: the fields are ordered
	 * alphabetically, except for TY (first), and ID and ER (last fields)
	 */
	private void writeBibliographicItem(Map<String, String> map, BibliographicItem bibliographicItem, BufferedWriter bw,
			DeduplicationMode mode) throws IOException {
		boolean removeMode = mode == DeduplicationMode.REMOVE;
		if (removeMode) {
			enrichmentService.enrichMap(map, bibliographicItem);
		}
		// in REMOVE mode C7 (Article number) is skipped; in MARK mode C7 is kept
		String skipFields = removeMode ? "(C7|ER|ID|TY|XYZ)" : "(ER|ID|TY|XYZ)";
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
