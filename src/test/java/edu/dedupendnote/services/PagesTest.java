package edu.dedupendnote.services;

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.services.NormalizationService.PageRecord;
import lombok.extern.slf4j.Slf4j;

@TestConfiguration
@Slf4j
class PagesTest {

	// @formatter:off
	static Stream<Arguments> argumentProvider() {
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
			arguments("1252-3; author reply 1252-3", "1252", "1252-1253; author reply 1252-3", true),
			arguments("iiv", null, "iiv", false), // invalid Roman number
			arguments("3S38-3S42", "338", "3S38-3S42", true),
			arguments("S6-97-s6-99", "697", "S697-s699", true)
			// Do we have cases of "945-+", "945-945", "945-5"?
			);
	}
	// @formatter:on

	@ParameterizedTest(name = "{index}: parsePages({0})=({1},{2},{3})")
	@MethodSource("argumentProvider")
	void parsePagesTest(String pages, String pageStart, String pagesOutput, boolean severalPages) {

		PageRecord normalizedPages = NormalizationService.normalizeInputPages(Map.of("SP", pages), "1");
		// log.error("- {}", normalizedPages);

		// assertThat(normalizedPages.pageStart()).as("PageStart comparison for " + pages).isEqualTo(pageStart);
		// assertThat(normalizedPages.pagesOutput()).as("PagesOutput comparison for " + pages).isEqualTo(pagesOutput);
		// assertThat(normalizedPages.severalPages()).as("SeveralPages comparison for " +
		// pages).isEqualTo(severalPages);

		SoftAssertions.assertSoftly(softAssertions -> {
			softAssertions.assertThat(normalizedPages.pageStart()).as("PageStart comparison for " + pages)
					.isEqualTo(pageStart);
			softAssertions.assertThat(normalizedPages.pagesOutput()).as("PagesOutput comparison for " + pages)
					.isEqualTo(pagesOutput);
			softAssertions.assertThat(normalizedPages.isSeveralPages()).as("SeveralPages comparison for " + pages)
					.isEqualTo(severalPages);
		});
	}
}