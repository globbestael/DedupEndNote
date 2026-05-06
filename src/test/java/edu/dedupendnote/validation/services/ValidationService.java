package edu.dedupendnote.validation.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.domain.PublicationDB;
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
			List<Publication> publications, long durationMs, boolean withTracing,
			DeduplicationService deduplicationService) throws IOException {

		List<PublicationDB> truthRecords = readTruthFile(truthFileName);

		Map<String, Publication> publicationMap = publications.stream()
				.collect(Collectors.toMap(Publication::getId, Function.identity()));
		List<PublicationDB> publicationDBs = recordDBService.convertToRecordDB(publications, inputFileName);
		Map<Integer, PublicationDB> validationMap = publicationDBs.stream()
				.collect(Collectors.toMap(PublicationDB::getId, Function.identity(), (o1, o2) -> o1, TreeMap::new));
		Map<Integer, Set<Integer>> trueDuplicateSets = truthRecords.stream()
				.filter(r -> r.getDedupid() != null)
				.collect(Collectors.groupingBy(PublicationDB::getDedupid,
						Collectors.mapping(PublicationDB::getId, Collectors.toSet())));
		int tns = 0, tps = 0, fps = 0, fns = 0;
		List<String> errors = new ArrayList<>();
		Map<Integer, Integer> fpErrors = new HashMap<>();
		// Because there can be more than 1 FN-pair or FP-pair with the same kept publication, a List<List> is needed
		Map<Integer, List<List<Publication>>> fnPairs = new TreeMap<>();
		Map<Integer, List<List<Publication>>> fpPairs = new TreeMap<>();

		for (PublicationDB t : truthRecords) {
			PublicationDB v = validationMap.get(t.getId());
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
						List<Publication> pair = new ArrayList<>();
						pair.add(publicationMap.get(t.getId().toString()));
						pair.add(publicationMap.get(tDedupId.toString()));
						pair = pair.stream().sorted((p1, p2) -> Integer.valueOf(p1.getId()).compareTo(Integer.valueOf(p2.getId()))).toList();
						Integer keptId = Integer.valueOf(pair.get(0).getId());
						if (fnPairs.containsKey(keptId)) {
							fnPairs.get(keptId).add(new ArrayList<>(pair));
						} else {
							List<List<Publication>> list = new ArrayList<>();
							list.add(new ArrayList<Publication>(pair));
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
					List<Publication> pair = new ArrayList<>();
					pair.add(publicationMap.get(v.getId().toString()));
					pair.add(publicationMap.get(vDedupId.toString()));
					pair = pair.stream().sorted((p1, p2) -> Integer.valueOf(p1.getId()).compareTo(Integer.valueOf(p2.getId()))).toList();
					Integer keptId = Integer.valueOf(pair.get(0).getId());
					if (fpPairs.containsKey(keptId)) {
						fpPairs.get(keptId).add(new ArrayList<>(pair));
					} else {
						List<List<Publication>> list = new ArrayList<>();
						list.add(new ArrayList<Publication>(pair));
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
			 * There may be records in the output file with "... - ... ARE DUPLICATES" but with publication years
			 * which are more than 1 year apart,
			 * because the test of the pair does not look at the publication years
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

	public List<PublicationDB> readTruthFile(String fileName) throws IOException {
		Path path = Path.of(fileName);

		CsvMapper mapper = new CsvMapper();
		CsvSchema schema = mapper
				.schemaFor(PublicationDB.class)
				.withHeader()
				.withColumnSeparator('\t')
				.withLineSeparator("\n");
		MappingIterator<PublicationDB> it = mapper
				.readerFor(PublicationDB.class)
				.with(schema)
				.with(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
				.readValues(path.toFile());
		return it.readAll();
	}

	private void writeFNandFPresults(Map<Integer, List<List<Publication>>> pairsList, String outputFileName,
			DeduplicationService deduplicationService) {
		List<Logger> loggers = new ArrayList<>();
		List<String> loggerNames = List.of(
			"edu.dedupendnote.services.DeduplicationService",
			"edu.dedupendnote.services.ComparisonService",
			"edu.dedupendnote.services.DefaultAuthorsComparisonService",
			"edu.dedupendnote.services.ValidationTests" // add this file because of trace on publication year
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

			for (List<List<Publication>> pairs : pairsList.values()) {
				for (List<Publication> pair : pairs) {
					bw.write(pair.get(0).toString());
					bw.write("\n");
					if (pair.size() < 2) {
						bw.write("Pair contains only 1 record");
					} else {
						bw.write(pair.get(1).toString());
					}

					// deduplicate pair after writing because deduplication alters the pair
					Publication p1 = pair.get(0);
					Publication p2 = pair.get(1);
					if (p1.getPublicationYear() > 0 && p2.getPublicationYear() > 0 && Math.abs(p1.getPublicationYear() - p2.getPublicationYear()) > 1) {
						log.trace("\nStarting comparison {} - {}", p1.getId(), p2.getId());
						log.trace("- 0. Publication years are too far apart: {} and {}", p1.getPublicationYear(), p2.getPublicationYear());
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
