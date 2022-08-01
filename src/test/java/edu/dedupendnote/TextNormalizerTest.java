package edu.dedupendnote;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.text.Normalizer;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.AutoCloseableSoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Record;

@TestConfiguration
public class TextNormalizerTest {
	/*
	 * Comparison of different methods to remove accents and diacritics.
	 * See also https://www.baeldung.com/java-remove-accents-from-text for a more accurate method. Not used here
	 */

	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("argumentProvider")
    void textNormalizerTest(String input, String own, String commons) {
		String ownResult = Record.normalizeToBasicLatin(input);
		String commonsResult = StringUtils.stripAccents(input);
		// System.err.println(input);
		try (AutoCloseableSoftAssertions softly = new AutoCloseableSoftAssertions()) {
			softly.assertThat(ownResult).as("Own textnormalization differs").isEqualTo(own);
			softly.assertThat(commonsResult).as("Apache Commons textnormalization differs").isEqualTo(commons);
		}
    }

    static Stream<Arguments> argumentProvider() {
		return Stream.of(     
                 arguments("ABC", "ABC", "ABC"),
                 // tests from https://github.com/apache/commons-lang/blob/eb62fccc1cbedd7f27c817593cbd7708828627c4/src/test/java/org/apache/commons/lang3/StringUtilsTrimStripTest.java
                 arguments("\u00C7\u00FA\u00EA", // Çúê
                		 "Cue",
                		 "Cue"),
                 arguments("\u00C0\u00C1\u00C2\u00C3\u00C4\u00C5\u00C7\u00C8\u00C9\u00CA\u00CB\u00CC\u00CD\u00CE\u00CF\u00D1\u00D2\u00D3\u00D4\u00D5\u00D6\u00D9\u00DA\u00DB\u00DC\u00DD",
                		 // ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝ
                		 "AAAAAACEEEEIIIINOOOOOUUUUY",
                		 "AAAAAACEEEEIIIINOOOOOUUUUY"),
                 arguments("\u0104\u0141\u00D3\u015A\u017B\u0179\u0106\u0143 \u0105\u0142\u00F3\u015B\u017C\u017A\u0107\u0144", // ĄŁÓŚŻŹĆŃ ąłóśżźćń
                		 "AOSZZCN aoszzcn", // Ł and ł are removed
                		 "ALOSZZCN aloszzcn"),
                 arguments("잊지마 넌 흐린 어둠사이 왼손으로 그린 별 하나",
                		 "       ",
                		 "잊지마 넌 흐린 어둠사이 왼손으로 그린 별 하나"),
                 arguments("æ œ ﬁ", "  ", "æ œ ﬁ"),	// we remove, Apache keeps ligatures
                 arguments("αβγδΔκ", "", "αβγδΔκ"),
                 arguments("¼", "", "¼")
          );
    }
    
}