package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;

@TestConfiguration
class DoiTest {

	@ParameterizedTest(name = "{index}: addDois({0})=({1},{2})")
	@MethodSource("argumentProvider")
	void addDoisTest(String input, int numberOfDois, Map<String, Integer> dois) {
		Publication r = new Publication();

		r.addDois(input);

		Map<String, Integer> map = r.getDois();
		assertThat(map)
			.hasSize(numberOfDois)
			.containsAllEntriesOf(dois);
	}

	static Stream<Arguments> argumentProvider() {
		return Stream.of(
				arguments("S0731-7085(15)30056-X [pii];10.1016/j.jpba.2015.07.002 [doi]", 1,
						Map.of("10.1016/j.jpba.2015.07.002", 1)),
				arguments("10.1002/(SICI)1099-0461(1998)12:1<29::AID-JBT5>3.0.CO;2-R [pii]", 1,
						Map.of("10.1002/(sici)1099-0461(1998)12:1<29::aid-jbt5>3.0.co;2-r", 1)),
				arguments("10.1007/s12035-015-9182-6 [doi];10.1007/s12035-015-9182-6 [pii]", 1,
						Map.of("10.1007/s12035-015-9182-6", 1)),
				arguments("10.1007/s12035-015-9182-6 [doi];10.1007/s12035-015-9182-6_bla [pii]", 2,
						Map.of("10.1007/s12035-015-9182-6", 1, "10.1007/s12035-015-9182-6_bla", 1)),
				arguments("10.1002/(SICI)1098-1063(1998)8:6&lt;627::AID-HIPO5&gt;3.0.CO;2-X [doi]", 1,
						Map.of("10.1002/(sici)1098-1063(1998)8:6<627::aid-hipo5>3.0.co;2-x", 1)));
	}

}