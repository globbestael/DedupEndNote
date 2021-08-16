package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.dedupendnote.BaseTest;
import edu.dedupendnote.domain.Record;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AuthorsBaseTest extends BaseTest {
	String homeDir = System.getProperty("user.home");
	String testdir = homeDir + "/dedupendnote_files";
	List<Triple> localTriples = new ArrayList<>();
	
	@Data
	public class Triple {
		String authors1;
		String authors2;
		double jws = 0.0;
		double expJws = 0.0;
		
		public String toString() {
			return "- " + authors1 + "\n- " + authors2 + "\n- " + jws + "\n";
		}
	}
	/*
	 * The file validated_authors_pairs.txt was build with the validated records in the database with queries as:
	 * (example is for BIG_SET_TRUTH_validated_authors_pairs
	 * 
	 * SELECT DISTINCT BIG_SET_TRUTH.authors, BIG_SET_TRUTH_1.authors
	 * FROM BIG_SET_TRUTH INNER JOIN BIG_SET_TRUTH AS BIG_SET_TRUTH_1 ON BIG_SET_TRUTH.dedupid = BIG_SET_TRUTH_1.dedupid
	 * WHERE (((BIG_SET_TRUTH_1.authors_truncated)<>[BIG_SET_TRUTH].[authors_truncated]) AND ((BIG_SET_TRUTH.id)>0) AND ((BIG_SET_TRUTH_1.id)<>[BIG_SET_TRUTH].[id] And (BIG_SET_TRUTH_1.id)>[BIG_SET_TRUTH].[id]) AND ((BIG_SET_TRUTH.Validated)=True));
	 * 
	 * The whole file is created on the TRUTH files for BIG_SET, Cytology_screening, Haemtology and Respiratory
	 * 
	 * Because of MS Access limitations (authors is a Long Text / Memo field) the file contains duplicate records
	 */
	protected List<Triple> getValidatedAuthorsPairs() throws IOException {
		String fileName = testdir + "/experiments/validated_authors_pairs.txt";
		localTriples.clear();
		Path path = Paths.get(fileName);
		Stream<String> lines = Files.lines(path);
		lines.forEach(l -> {
			String[] parts = l.split("\t");
			if (Record.anonymousOrGroupNamePattern.matcher(parts[0]).find() || Record.anonymousOrGroupNamePattern.matcher(parts[1]).find()) {
				return;
			}
			Triple triple = new Triple();
			localTriples.add(triple);
			triple.setAuthors1(parts[0]);
			triple.setAuthors2(parts[1]);
		});
		lines.close();
		log.debug("There were {} triples", localTriples.size());
		// deduplicate
		localTriples = localTriples.stream().distinct().collect(Collectors.toList());
		log.debug("There are {} triples", localTriples.size());

		assertThat(localTriples)
			.as("There are more than 100 authors pairs")
			.hasSizeGreaterThan(100);

		return localTriples;
	}

	// Assumes that triples is sorted on getJws() descending
	protected static double percentile(List<Triple> triples, double percentile) {
		assertThat(triples.get(0).getJws())
			.as("The triples are sorted on getJws() descending")
			.isGreaterThan(triples.get(triples.size() - 1).getJws());
	    int index = (int) Math.ceil(percentile / 100.0 * triples.size());
	    return triples.get(index-1).getJws();
	}
	
	// Assumes that triples is sorted on getExpJws() descending
	protected static double expPercentile(List<Triple> triples, double percentile) {
		assertThat(triples.get(0).getExpJws())
			.as("The triples are sorted on getExpJws() descending")
			.isGreaterThan(triples.get(triples.size() - 1).getExpJws());
	    int index = (int) Math.ceil(percentile / 100.0 * triples.size());
	    return triples.get(index-1).getExpJws();
	}
}
