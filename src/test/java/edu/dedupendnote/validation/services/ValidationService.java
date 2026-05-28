package edu.dedupendnote.validation.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MappingIterator;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import edu.dedupendnote.domain.BibliographicItem;
import edu.dedupendnote.domain.BibliographicItemDB;
import edu.dedupendnote.integration.utils.MemoryAppender;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.validation.domain.ValidationResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ValidationService {

	@Autowired
	RecordDBService recordDBService;

	List<Pattern> tracePatterns = List.of(Pattern.compile("- (0|1|2|3|4). .+"),
			Pattern.compile("\\d+ - \\d+ ARE (NOT )?DUPLICATES"));

	public ValidationResult checkResults(
			String setName, String inputFileName, String outputFileName, String truthFileName,
			List<BibliographicItem> bibliographicItems, long durationMs, boolean withTracing,
			DeduplicationService deduplicationService) throws IOException {

		List<BibliographicItemDB> truthRecords = readTruthFile(truthFileName);

		Map<Integer, BibliographicItem> publicationMap = bibliographicItems.stream()
				.collect(Collectors.toMap(BibliographicItem::getId, Function.identity()));
		List<BibliographicItemDB> publicationDBs = recordDBService.convertToRecordDB(bibliographicItems, inputFileName);
		Map<Integer, BibliographicItemDB> validationMap = publicationDBs.stream()
				.collect(Collectors.toMap(BibliographicItemDB::getId, Function.identity(), (o1, o2) -> o1, TreeMap::new));
		Map<Integer, Set<Integer>> trueDuplicateSets = truthRecords.stream()
				.filter(r -> r.getDedupid() != null)
				.collect(Collectors.groupingBy(BibliographicItemDB::getDedupid,
						Collectors.mapping(BibliographicItemDB::getId, Collectors.toSet())));
		int tns = 0, tps = 0, fps = 0, fns = 0;
		List<String> errors = new ArrayList<>();
		Map<Integer, Integer> fpErrors = new HashMap<>();
		// Because there can be more than 1 FN-pair or FP-pair with the same kept bibliographicItem, a List<List> is needed
		Map<Integer, List<List<BibliographicItem>>> fnPairs = new TreeMap<>();
		Map<Integer, List<List<BibliographicItem>>> fpPairs = new TreeMap<>();

		for (BibliographicItemDB t : truthRecords) {
			BibliographicItemDB v = validationMap.get(t.getId());
			if (v == null) {
				continue;
			}
			v.setValidated(t.isValidated());
			v.setUnsolvable(t.isUnsolvable());
			Integer tDedupId = t.getDedupid();
			Integer vDedupId = v.getDedupid();
			log.debug("Comparing {} with truth {} and validation {}", t.getId(), t.getDedupid(), v.getDedupid());
			if (vDedupId == null) {
				if (tDedupId == null || (t.getId() != null && t.getId().equals(tDedupId))) {
					v.setTrueNegative(true);
					tns++;
				} else {
					v.setFalseNegative(true);
					fns++;
					v.setCorrection(tDedupId);
					if (t.getId() != null && !t.getId().equals(tDedupId)) {
						List<BibliographicItem> pair = new ArrayList<>();
						pair.add(publicationMap.get(t.getId()));
						pair.add(publicationMap.get(tDedupId));
						pair = pair.stream().sorted(Comparator.comparing(BibliographicItem::getId)).toList();
						Integer keptId = pair.get(0).getId();
						if (fnPairs.containsKey(keptId)) {
							fnPairs.get(keptId).add(new ArrayList<>(pair));
						} else {
							List<List<BibliographicItem>> list = new ArrayList<>();
							list.add(new ArrayList<BibliographicItem>(pair));
							fnPairs.put(keptId, list);
						}
					}
				}
			} else if ((trueDuplicateSets.containsKey(tDedupId) && trueDuplicateSets.get(tDedupId).contains(vDedupId))
					|| (v.getId() != null && v.getId().equals(vDedupId))) {
				v.setTruePositive(true);
				tps++;
			} else {
				v.setFalsePositive(true);
				fps++;
				v.setCorrection(tDedupId);
				errors.add("FALSE POSITIVES: \n- TRUTH " + t + "\n- CURRENT " + v + "\n");
				fpErrors.put(v.getId(), vDedupId);
				if (v.getId() != null && !v.getId().equals(vDedupId)) {
					List<BibliographicItem> pair = new ArrayList<>();
					pair.add(publicationMap.get(v.getId()));
					pair.add(publicationMap.get(vDedupId));
					pair = pair.stream().sorted(Comparator.comparing(BibliographicItem::getId)).toList();
					Integer keptId = pair.get(0).getId();
					if (fpPairs.containsKey(keptId)) {
						fpPairs.get(keptId).add(new ArrayList<>(pair));
					} else {
						List<List<BibliographicItem>> list = new ArrayList<>();
						list.add(new ArrayList<BibliographicItem>(pair));
						fpPairs.put(keptId, list);
					}
				}
			}
		}
		recordDBService.saveRecordDBs(publicationDBs, outputFileName);
		long uniqueDuplicates = publicationDBs.stream()
				.filter(r -> r.isTruePositive() == true && r.getDedupid() != null && r.getDedupid().equals(r.getId()))
				.count();

		System.err.println("File " + setName + " has unique duplicates: " + uniqueDuplicates);
		ValidationResult validationResult = new ValidationResult(setName, tps, fns, tns, fps, durationMs, (int) uniqueDuplicates);

		if (withTracing) {
			/*
			 * There may be records in the output file with "... - ... ARE DUPLICATES" but with bibliographicItem years
			 * which are more than 1 year apart,
			 * because the test of the pair does not look at the bibliographicItem years
			 */
			new File(inputFileName + "_FP_Analysis.txt").delete();
			new File(inputFileName + "_FN_Analysis.txt").delete();
			if (!fnPairs.isEmpty()) {
				validationResult.setFnPairs(fnPairs);
				writeFNandFPresults(fnPairs, inputFileName + "_FN_Analysis.txt", deduplicationService);
			}
			if (!fpPairs.isEmpty()) {
				/*
				 * There may be records in the output file with the same DOI but an error in the journal and/or pages
				 */
				validationResult.setFpPairs(fpPairs);
				writeFNandFPresults(fpPairs, inputFileName + "_FP_Analysis.txt", deduplicationService);
			}
		}
		if (!errors.isEmpty()) {
			System.err.println("File " + setName + " has FALSE POSITIVES!");
			errors.stream().forEach(System.err::println);
			System.err.println("These are the FP recordIDs for " + setName);
			fpErrors.entrySet().forEach(System.err::println);
		}

		return validationResult;
	}

	public List<BibliographicItemDB> readTruthFile(String fileName) throws IOException {
		Path path = Path.of(fileName);

		CsvMapper mapper = new CsvMapper();
		CsvSchema schema = mapper
				.schemaFor(BibliographicItemDB.class)
				.withHeader()
				.withColumnSeparator('\t')
				.withLineSeparator("\n");
		MappingIterator<BibliographicItemDB> it = mapper
				.readerFor(BibliographicItemDB.class)
				.with(schema)
				.with(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
				.readValues(path.toFile());
		return it.readAll();
	}

	private void writeFNandFPresults(Map<Integer, List<List<BibliographicItem>>> pairsList, String outputFileName,
			DeduplicationService deduplicationService) {
		List<Logger> loggers = new ArrayList<>();
		List<String> loggerNames = List.of(
			"edu.dedupendnote.services.DeduplicationService",
			"edu.dedupendnote.services.ComparisonService",
			"edu.dedupendnote.services.DefaultAuthorsComparisonService",
			"edu.dedupendnote.services.ValidationTests" // add this file because of trace on bibliographicItem year
			);
		Level oldLevel = null;

		try (BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName))) {
			MemoryAppender memoryAppender = new MemoryAppender();
			for (String loggerName : loggerNames) {
				Logger logger = (Logger) LoggerFactory.getLogger(loggerName);
				oldLevel = logger.getLevel();
				logger.setLevel(Level.TRACE);
				logger.addAppender(memoryAppender);
				loggers.add(logger);
			}
			memoryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
			memoryAppender.start();

			for (List<List<BibliographicItem>> pairs : pairsList.values()) {
				for (List<BibliographicItem> pair : pairs) {
					bw.write(pair.get(0).toString());
					bw.write("\n");
					if (pair.size() < 2) {
						bw.write("Pair contains only 1 record");
					} else {
						bw.write(pair.get(1).toString());
					}

					// deduplicate pair after writing because deduplication alters the pair
					BibliographicItem p1 = pair.get(0);
					BibliographicItem p2 = pair.get(1);
					if (p1.getPublicationYear() > 0 && p2.getPublicationYear() > 0 && Math.abs(p1.getPublicationYear() - p2.getPublicationYear()) > 1) {
						log.trace("\nStarting comparison {} - {}", p1.getId(), p2.getId());
						log.trace("- 0. BibliographicItem years are too far apart: {} and {}", p1.getPublicationYear(), p2.getPublicationYear());
						log.trace("{} - {} ARE NOT DUPLICATES", p1.getId(), p2.getId());
					} else {
						deduplicationService.compareSet(pair, p1.getPublicationYear(), true, message -> {});
					}

					bw.write("\nANALYSIS:\n");
					for (String s : memoryAppender.filterByPatterns(tracePatterns, Level.TRACE)) {
						bw.write(s + "\n");
					}
					bw.write("\n=======================\n");

					memoryAppender.reset();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			for (Logger logger : loggers) {
				logger.setLevel(oldLevel);
			}
		}
	}

}
