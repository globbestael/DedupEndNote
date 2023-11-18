package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
public class BaseTest {

	protected JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	protected Double getHighestSimilarity(List<String> listAuthors1, List<String> listAuthors2) {
		Double highestSimilarity = 0.0;

		for (String authors1 : listAuthors1) {
			for (String authors2 : listAuthors2) {
				Double distance = jws.apply(authors1, authors2);
				if (distance > highestSimilarity) {
					highestSimilarity = distance;
				}
			}
		}
		return highestSimilarity;
	}

	@Test
	void fillerBaseTest() {
		assertThat(1*1).isEqualTo(1);
	}
}
