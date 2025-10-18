package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Formatter;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
class TextNormalizerTest {

	/*
	 * Comparison of different methods to remove accents and diacritics. See also
	 * https://www.baeldung.com/java-remove-accents-from-text for a more accurate method.
	 * Not used here
	 */

	public String escapeUnicode(String input) {
		StringBuilder sb = new StringBuilder(input.length());
		try (Formatter f = new Formatter(sb)) {
			for (char c : input.toCharArray()) {
				if (c < -128) {
					sb.append(c);
				} else {
					f.format("\\u%04x", (int) c);
				}
			}
		}
		return sb.toString();
	}

	/*
	 * ComparesDedupEndNote's handling of accents with Apache Commons Lang (stripAccents)
	 * 
	 * IMPORTANT: In https://github.com/apache/commons-lang/pull/1201 Normalizer.normalize(input, Normalizer.Form.NFD) was
	 * changed to Normalizer.normalize(input, Normalizer.Form.NFKD
	 * Some of the changes:
	 * - ligature fi converts to 2 separate letetrs f and i
	 * - fracture 1/4 converted to 3 characters
	 * 
	 * See https://www.unicode.org/charts/normalization/ for the way in wich ligatures are handled by the different Normalizer.forms.
	 * The ae-ligature (00E6 and 10783, only the last one in the first column!) are not resolved to 2 characters 
	 * (although 10783 is changed to 00E6 in KC and KD columns).
	 */
	@ParameterizedTest(name = "{index}: {0}")
	@MethodSource("argumentProvider")
	void textNormalizerTest(String input, String own, String commons) {
		String ownResult = NormalizationService.normalizeToBasicLatin(input);
		String commonsResult = StringUtils.stripAccents(input);

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(ownResult).as("Own textnormalization differs").isEqualTo(own);
		softAssertions.assertThat(commonsResult).as("Apache Commons textnormalization differs").isEqualTo(commons);
		softAssertions.assertAll();
		System.err.println("Input : |" + escapeUnicode(input) + "|\nOutput: |" + escapeUnicode(commonsResult)
				+ "|\nexpect: |" + escapeUnicode(commons) + "|");
	}

	static Stream<Arguments> argumentProvider() {
		// @formatter:off
		return Stream.of(
				// tests from https://github.com/apache/commons-lang/blob/eb62fccc1cbedd7f27c817593cbd7708828627c4/src/test/java/org/apache/commons/lang3/StringUtilsTrimStripTest.java
				arguments("\u00C7\u00FA\u00EA", // Çúê
						"Cue",
						"Cue"),
				arguments(
						"\u00C0\u00C1\u00C2\u00C3\u00C4\u00C5\u00C7\u00C8\u00C9\u00CA\u00CB\u00CC\u00CD\u00CE\u00CF\u00D1\u00D2\u00D3\u00D4\u00D5\u00D6\u00D9\u00DA\u00DB\u00DC\u00DD",
						// ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝ
						"AAAAAACEEEEIIIINOOOOOUUUUY", 
						"AAAAAACEEEEIIIINOOOOOUUUUY"),
				arguments(
						"\u0104\u0141\u00D3\u015A\u017B\u0179\u0106\u0143 \u0105\u0142\u00F3\u015B\u017C\u017A\u0107\u0144",
						 // ĄŁÓŚŻŹĆŃąłóśżźćń
						"AOSZZCN aoszzcn", // Ł and ł are removed
						"ALOSZZCN aloszzcn"),
				arguments( // CJK
						"잊지마 넌 흐린 어둠사이 왼손으로 그린 별 하나", 
						"       ",
						"잊지마 넌 흐린 어둠사이 왼손으로 그린 별 하나"),
				arguments( // ligatures, dedupendnote replcaes them with space, Apache keeps ligatures
						"æ œ ﬁ", 
						"  ", // dedupendnote removes ligatures
						"æ œ fi"), // Apache keeps ligatures
				arguments( // Greek: dedupnnote removes them, Apache keeps them
						"αβγδΔκ", 
						"", 
						"αβγδΔκ"),
				arguments( // fractions: dedupennote removes them, apache replaces them with separate characters
					"¼", 
					"", 
					"1⁄4"),
				/*
				 * whitespace: see https://stackoverflow.com/questions/18169006/all-the-whitespace-characters-is-it-language-independent
				 * BUT: Ioservice.unusualWhiteSpacePattern is used before Publication.normalizeToBasicLatin(), and pattern replaces them all with a normal space
				 */
				arguments(
					"\u0020,\u0085,\u00A0,\u1680,\u2000,\u2001,\u2002,\u2003,\u2004,\u2005,\u2006,\u2007,\u2008,\u2009,\u200A,\u2028,\u2029,\u202F,\u205F,\u3000",
					// Publication.normalizeToBasicLatin: only normal space is kept, rest are removed
					" ,,,,,,,,,,,,,,,,,,,",
					// Apache keeps \u0085, \u1680, \u2028 and \u2029, and converts the rest to space (\u0020)
					"\u0020,\u0085,\u0020,\u1680,\u0020,\u0020,\u0020,\u0020,\u0020,\u0020,\u0020,\u0020,\u0020,\u0020,\u0020,\u2028,\u2029,\u0020,\u0020,\u0020"
				));
		// @formatter:on
	}

	@Test
	void whiteSpaceReplacement() {
		String input = " a\u000Ca\u00A0a\u2000a";
		String output = IOService.unusualWhiteSpacePattern.matcher(input).replaceAll("A");

		assertThat(output).isEqualTo(" aAaAaAa");
	}
}