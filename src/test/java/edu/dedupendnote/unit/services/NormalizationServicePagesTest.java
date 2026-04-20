package edu.dedupendnote.unit.services;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import edu.dedupendnote.services.IOService;
import edu.dedupendnote.services.NormalizationService;
import edu.dedupendnote.domain.PageRecord;
import edu.dedupendnote.domain.Publication;

class NormalizationServicePagesTest {

	/*
	 * Tests NormalizationService.normalizeInputPages for a single SP-field input.
	 * Complements normalizePagesTest_new which covers field selection (C7/SE/SP) via IOService.addNormalizedPages.
	 */
	@ParameterizedTest(name = "{index}: parsePages({0})=({1},{2},{3})")
	@MethodSource("parsePagesArgumentProvider")
	void parsePagesTest(String pages, String pageStart, String pagesOutput, boolean severalPages) {
		PageRecord normalizedPages = NormalizationService.normalizeInputPages(Map.of("SP", pages), "1");

		SoftAssertions.assertSoftly(softAssertions -> {
			softAssertions.assertThat(normalizedPages.pageStart()).as("PageStart comparison for '%s'", pages)
					.isEqualTo(pageStart);
			softAssertions.assertThat(normalizedPages.pagesOutput()).as("PagesOutput comparison for '%s'", pages)
					.isEqualTo(pagesOutput);
			softAssertions.assertThat(normalizedPages.isSeveralPages()).as("SeveralPages comparison for '%s'", pages)
					.isEqualTo(severalPages);
		});
	}

	/*
	 * Tests IOService.addNormalizedPages: the field-selection logic that chooses which
	 * of C7, SE, SP to use before calling NormalizationService.normalizeInputPages.
	 *
	 * See also issue #50: https://github.com/globbestael/DedupEndNote/issues/50
	 */
	@ParameterizedTest(name = "{index}: normalizePages({0})={1}")
	@MethodSource("addNormalizedPagesArgumentProvider")
	void normalizePagesTest_new(String c7Input, String seInput, String spInput, String expectedPageStart,
			boolean expectedIsSeveralPages) {
		Publication publication = new Publication();
		Map<String, String> pagesMap = new HashMap<>();
		if (c7Input != null) {
			pagesMap.put("C7", c7Input);
		}
		if (seInput != null) {
			pagesMap.put("SE", seInput);
		}
		if (spInput != null) {
			pagesMap.put("SP", spInput);
		}
		IOService.addNormalizedPages(pagesMap, publication);

		SoftAssertions.assertSoftly(softAssertions -> {
			softAssertions.assertThat(publication.getPageStart())
					.as("PageStart comparison for %s, %s, %s".formatted(c7Input, seInput, spInput))
					.isEqualTo(expectedPageStart);
			softAssertions.assertThat(publication.isSeveralPages())
					.as("IsSeveralPages comparison for %s, %s, %s".formatted(c7Input, seInput, spInput))
					.isEqualTo(expectedIsSeveralPages);
		});
	}

	// @formatter:off
	static Stream<Arguments> parsePagesArgumentProvider() {
		return Stream.of(
			arguments("1", "1", "1", false),
			arguments("1-2", "1", "1-2", false),
			arguments("1-3", "1", "1-3", true),
			arguments("12-4", "12", "12-14", true),
			arguments("1251-4", "1251", "1251-1254", true),
			arguments("12-14", "12", "12-14", true),
			arguments("12-4; author reply 15", "12", "12-14; author reply 15", true),
			arguments("12; author reply 15", "12", "12; author reply 15", true),
			arguments("11; author reply 11-2", "11", "11; author reply 11-2", true),
			arguments("1252-3; author reply 1252-3", "1252", "1252-1253; author reply 1252-3", true),
			arguments("1469-87; discussion 1487-9", "1469", "1469-1487; discussion 1487-9", true),
			arguments("11; discussion 11-2", "11", "11; discussion 11-2", true),
			arguments("12-14, 17-18", "12", "12-14; 17-18", true),
			arguments("S12-14", "12", "S12-S14", true),
			arguments("S12-4", "12", "S12-S14", true),
			arguments("S12-S14", "12", "S12-S14", true),
			arguments("S99-103", "99", "S99-103", true), // wrong: should be "S99-S103"
			arguments("S-12", "12", "S12", false),
			arguments("S-12-S-14", "12", "S12-S14", true),
			arguments("1165a-1166A", "1165", "1165a-1166A", false),
			arguments("11+136", "11", "11; 136", true),
			arguments("11-14+136", "11", "11-14; 136", true),
			arguments("11+136-7", "11", "11; 136-7", true),
			arguments("UNSP e12345", "12345", "e12345", true),
			arguments("371, 425", "371", "371; 425", true),
			// books / reports starting at page 1
			arguments("1-125", "125", "1-125", true),
			arguments("1-99", "1", "1-99", true),
			arguments("A1-A125", "125", "A1-A125", true),
			arguments("A1-A99", "1", "A1-A99", true),
			arguments("001-099", "1", "001-099", true),
			// leading zeros
			arguments("A1-A099", "1", "A1-A099", true),
			arguments("i-A19", "19", "i-A19", false), // range of roman and arabic numbers
			arguments("ED01-ED03", "1", "ED01-ED03", true) ,
			arguments("0-3", "3", "3", false),
			arguments("000-003", "3", "3", false),
			arguments("000", null, "", false),
			arguments("1469-1487", "1469", "1469-1487", true),
			arguments("AA59-AA60", "59", "AA59-AA60", false),
			// with plus sign
			arguments("331-+", "331", "331-+", false),
			arguments("378+384", "378", "378; 384", true),
			arguments("353-362+370", "353", "353-362; 370", true),
			arguments("211+216-222", "211", "211; 216-222", true),
			arguments("233-238+230-235+246-251", "233", "233-238; 230-235; 246-251", true),
			arguments("525-542+viii-ix", "525", "viii-ix; 525-542", true),
			arguments("e150-e178+e86-e114", "150", "e150-e178; e86-e114", true),
			arguments("I-+", "1", "I-+", false),
			arguments("i-xxii+1-131", "131", "i-xxii; 1-131", true),
			// roman numerals
			arguments("viii", "8", "viii", false),
			arguments("ii22-ii33", "22", "ii22-ii33", true),
			arguments("ii-ix", "2", "ii-ix", true),
			arguments("II206-13", "206", "II206-II213", true),
			arguments("II-221-5; discussion II-225-6", "221", "II221-II225; discussion II225-6", true),
			arguments("IX/101-8", "101", "IX/101-IX/108", true),
			arguments( "iii-iv, 1-134", "134", "iii-iv; 1-134", true),
			arguments("iii-iv, ix-xi, 1-93", "1", "iii-iv; ix-xi; 1-93", true),
			arguments("III-VIII, 1-101, back cover", "101", "III-VIII; 1-101; back cover", true),
			arguments("IV-138-IV-152", "138", "IV138-IV152", true),
			arguments("ITC1", "1", "ITC1", false),
			arguments("ITC3-1", "3", "ITC3-ITC1", false), // this is wrong: it is interpreted as a range
			arguments("IV4-12", "4", "IV4-I12", true),	// this is wrong
			// volume number within pages, in multi-volume books
			arguments("V2:553-V2:616", "553", "V2:553-V2:616", true),
			arguments("H1753-8", "1753", "H1753-H1758", true),
			arguments("Cd010073", "10073", "Cd010073", false),
			arguments("CD010073", "10073", "CD010073", false),
			arguments("N.PAG-N.PAG", null, "", false),
			// // The following case will not have pagesOutput "s0003394402007769/sco" unless the output field is C7
			arguments("Unsp s0003394402007769/sco", "3394402007769", "s0003394402007769/sco", false),
			arguments("iiv", null, "iiv", false), // invalid Roman number
			arguments("3S38-3S42", "338", "3S38-3S42", true),
			arguments("S6-97-s6-99", "697", "S697-s699", true)
			// Do we have cases of "945-+", "945-945", "945-5"?
			);
	}

	static Stream<Arguments> addNormalizedPagesArgumentProvider() {
		// String c7Input, String seInput, String spInput, String expectedPageStart, boolean expectedIsSeveralPages
		return Stream.of(
			// FIXME: Is this the result wanted? Why are both XVI and 376 not accepted?
			arguments(null, null, "A relational approach to rehabilitation: Thinking about relationships after brain injury. xvi, 376", null, false),
			arguments(null, null, null, null, false),
			arguments(null, null, "22", "22", false),
			arguments(null, null, "22-25", "22", true),
			arguments(null, null, "text", null, false),
			//
			arguments(null, "23", null, "23", false),
			arguments(null, "23", "22", "22", false),
			arguments(null, "23-25", null, "23", true),
			arguments(null, "text", "text", null, false),
			//
			arguments("24", "23", null, "24", true),
			arguments("24", "23", "22", "24", true),
			// "-" in C7: skip C7
			arguments("24-25", "22-25", null, "2425", true), // is this what we want?
			arguments("24-25", null, "22-25", "22", true),
			arguments("26-27", null, null, "2627", true),
			arguments("text", "text", "text", null, false),
			// ----------------------------
			// detailed C7
			// ----------------------------
			arguments("e12989", null, null, "12989", true),
			arguments("e00492-16", null, null, "49216", true),
			// space in C7 -> skip
			arguments("Pii s1364-6613(97)01039-5", null, null, null, false),
			arguments("Pii s1364-6613(97)01039-5", null, "28", "28", false),
			arguments("Pii s1364-6613(97)01039-5", null, "28-34", "28", true),
			arguments("Pmid 29451177", null, null, null, false),
			arguments("Pmid 29451177", null, "3-4", "3", false),
			// except if UNSP/Article
			arguments("UNSP e12989", null, null, "12989", true),
			arguments("Article 919", null, null, "919", true),
			arguments("ddu115", null, "4015", "115", true),
			arguments("ddu115", null, "4015-4023", "4015", true),
			arguments("875.e1", null, "876 e1-876 e7", "8761", true),	// probably right, pageStart from SP
			arguments("875.e1", null, "876.e1-876.e7", "8761", true),	// probably right, pageStart from SP
			arguments("e2015.00091", null, null, "201500091", true),	// C7 is part of DOI (2015 = PY)
			arguments("e2015.00091", null, "e2015.00092", "201500091", true),	// C7 is part of DOI (2015 = PY), pagesOutput taken from C7
			arguments("e2015.00091", null, "e2015.00092 - e2015.00098", "201500092", true),	// C7 is part of DOI (2015 = PY), pagesOutput taken from SP
			arguments("0028", null, "29", "28", true),
			arguments("Cd011659", null, null, "11659", true),
			arguments("Cd011659", null, "29", "11659", true),
			arguments("Cd011659", null, "29-35", "29", true),
			arguments("Bsr20190088", null, "29", "20190088", true),
			arguments("1000-7431(2019)02-0126-07", null, "126", "10007431201902012607", true),
			arguments("1000-7431(2019)02-0126-07", null, "126-132", "126", true),
			arguments("10007431(2019)02-0126-07", null, null, "10007431201902012607", true),
			arguments("0163-2116/02/0900-2017/0", null, null, "163211602090020170", true),
			arguments("01632116/02/0900-2017/0", null, null, "163211602090020170", true),
			arguments("bcr-2018-225689", null, null, "2018225689", true),
			arguments("0300060520914834", null, null, "300060520914834", true),	// > max integer

			// ----------------------------
			// detailed SE
			// ----------------------------
			arguments(null, "91", "91-101", "91", true),
			// SE as pageStart of conference abstract, SP as pageStart of individual abstract
			arguments(null, "49", "61", "61", false),
			arguments(null, "635", "644-645", "644", false),
			arguments(null, "635", "644-649", "644", true),
			arguments(null, "S419", "S420", "420", false),
			arguments(null, "A21.2", "A21", "21", false),
			arguments(null, "537p", "553p", "553", false),
			arguments(null, "e1", "14", "14", false), // conference abstracts start at e1, this abstract at (e)14
			arguments(null, "19", "3", "3", false),	// not sure what this means, 3 can not be number of pages
			arguments(null, "White, Julian. Toxinology Dept., Women's & Children's Hospital, North Adelaide, Australia. Electronic address: julian.white@adelaide.edu.au.", "53-65", "53", true),

			// ----------------------------
			// detailed SP
			// ----------------------------
			arguments("87", null, "171-172", "171", true),
			arguments("87", null, "171-182", "171", true),
			// 2 SP based on versioned DOIs can lead to the same expectedPageStart / Integer used for comparison
			arguments(null, null, "BSR20211218", "20211218", false),
			arguments(null, null, "BSR20211218C", "20211218", false),
			// only the first of the pageranges is used, not the second ("376")
			arguments(null, null, "A relational approach to rehabilitation. xvi, 376", null, false),
			arguments(null, "-",null, null, false),
			arguments(null, "-;",null, null, false),

			arguments(null, "", null, null, false),

			arguments(null, null, null, null, false) // last as dummy
		);
	}
	// @formatter:on

}
