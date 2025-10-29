package edu.dedupendnote.services;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.services.NormalizationService.PageRecord;
import lombok.extern.slf4j.Slf4j;

@TestConfiguration
@Slf4j
class PagesTest {

	// @ParameterizedTest(name = "{index}: parsePages({0})=({1},{2},{3})")
	// @MethodSource("argumentProvider")
	// void parsePagesTestOld(String pages, String start, String end, String pageForComparison) {
	// Publication p = new Publication();
	// IOService.addNormalizedPages(pages, "DUMMY", p);

	// SoftAssertions.assertSoftly(softAssertions -> {
	// softAssertions.assertThat(p.getPageStart()).as("PageStart comparison").isEqualTo(start);
	// softAssertions.assertThat(p.getPageEnd()).as("PageEnd comparison").isEqualTo(end);
	// softAssertions.assertThat(p.getPageForComparison()).as("Input '%s' has wrong pageForComparison", pages)
	// .isEqualTo(pageForComparison);
	// });
	// }

	// @formatter:off
	static Stream<Arguments> argumentProvider() {
		return Stream.of(
			arguments("1", "1", null, "1", null), 
			arguments("1-2", "1", "2", "1", null),
			arguments("12-4", "12", "14", "12", "12-14"), 
			arguments("1251-4", "1251", "1254", "1251", "1251-1254"),
			arguments("12-14", "12", "14", "12", null), 
			arguments("12-4; author reply 15", "12", "14", "12", null),
			arguments("12; author reply 15", "12", null, "12", null),
			arguments("11; author reply 11-2", "11", null, "11", null),
			arguments("1252-3; author reply 1252-3", "1252", "1253", "1252", null),
			arguments("1469-87; discussion 1487-9", "1469", "87; discussion 1487-9", "1469", null),
			arguments("11; discussion 11-2", "11", "discussion 11-2", "11", null),
			arguments("12-14, 17-18", "12", "14, 17-18", "12", null), 
			arguments("S12-14", "S12", "S14", "12", null),
			arguments("S12-4", "S12", "S14", "12", null),
			arguments("S12-S14", "S12", "S14", "12", null), 
			arguments("S99-103", "S99", "103", "99", null),
			arguments("S-12", "S12", null, "12", null), 
			arguments("S-12-S-14", "S12", "S-14", "12", null),
			arguments("1165a-1166A", "1165a", "1166A", "1165", null), 
			arguments("11+136", "11+136", null, "11", null),
			arguments("11-14+136", "11", "14+136", "11", null), 
			arguments("11+136-7", "11+136", "11+137", "11", null), // !
			arguments("UNSP e12345", "e12345", null, "12345", "e12345"), 
			arguments("371, 425", "371", "425", "371", null), 
			// books / reports starting at page 1
			arguments("1-125", "1", "125", "125", null),
			arguments("1-99", "1", "99", "1", null), 
			arguments("A1-A125", "A1", "A125", "125", null),
			arguments("A1-A99", "A1", "A99", "1", null), 
			arguments("001-099", "1", "99", "1", null),
			// leading zeros
			arguments("A1-A099", "A1", "A099", "1", null), 
			arguments("i-A19", "A19", null, "19", null),
			arguments("ED01-ED03", "ED01", "ED03", "1", null), 
			arguments("0-3", "3", null, "3", null),
			arguments("000-003", "3", null, "3", null), 
			arguments("000", "000", null, null, null),
			arguments("1469-1487", "1469", "1487", "1469", null),
			arguments("AA59-AA60", "AA59", "AA60", "59", null),
			// with plus sign
			arguments("331-+", "331", null, "331", null),
			arguments("378+384", "378", null, "378", null),
			arguments("353-362+370", "353", "362", "353", null),
			arguments("211+216-222", "211", null, "211", null),
			arguments("233-238+230-235+246-251", "233", "238", "233", null),
			arguments("525-542+viii-ix", "525", "542", "525", null),
			arguments("e150-e178+e86-e114", "e150", "e178", "150", null),
			arguments("I-+", "I", null, "1", null),
			arguments("i-xxii+1-131", "1", "131", "131", null),
			// roman numerals
			arguments("viii", "viii", null, "8", null),
			arguments("ii22-ii33", "ii22", "ii33", "22", null), 
			arguments("ii-ix", "ii", "ix", "2", null),
			arguments("II206-13", "ii206", "13", "206", null),
			arguments("II-221-5; discussion II-225-6", "II-221", "5", "221", null),
			arguments("IX/101-8", "101", "8", "101", null),
			arguments( "iii-iv, 1-134", "1", "134", "134", null),
			arguments("iii-iv, ix-xi, 1-93", "1", "93", "1", null),
			arguments("III-VIII, 1-101, back cover", "1", "101", "101", null),
			arguments("IV-138-IV-152", "IV-138", "IV-152", "138", null),
			arguments("ITC1", "ITC1", null, "1", null),
			arguments("ITC3-1", "ITC3", "1", "3", null), // this is wrong: it is interpreted as a range
			arguments("IV4-12", "IV4", "12", "4", null),
			// volume number within pages, in multi-volume books
			arguments("V2:553-V2:616", "V2:553", "V2:616", "553", null),
			arguments("H1753-8", "H1753", "8", "1753", null),
			arguments("Cd010073", "Cd010073", null, "10073", null),
			arguments("CD010073", "CD010073", null, "10073", null),
			arguments("N.PAG-N.PAG", null, null, null, ""),
			// The following case will not have pagesOutput "s0003394402007769/sco" unless the output field is C7
			arguments("Unsp s0003394402007769/sco", "0003394402007769", null, "3394402007769", null)
			// Do we have cases of "945-+", "945-945", "945-5"?
			);
	}
	// @formatter:on

	@ParameterizedTest(name = "{index}: parsePages({0})=({1},{2},{3})")
	@MethodSource("argumentProvider")
	void parsePagesTest(String pages, String start, String end, String pageForComparison, String pagesOutput) {
		PageRecord normalizedPages = NormalizationService.normalizeInputPages(pages, "DUMMY");
		log.error("- {}", normalizedPages);
		assertThat(normalizedPages.pageForComparison()).as("PageStart comparison for " + pages)
				.isEqualTo(pageForComparison);
		assertThat(normalizedPages.pagesOutput()).as("PagesOutput comparison for " + pages).isEqualTo(pagesOutput);
		// assertThat(start).isEqualTo(pageForComparison);
		// assertThat(endingPage).as("PageEnd comparison").isEqualTo(end);
		// SoftAssertions.assertSoftly(softAssertions -> {
		// softAssertions.assertThat(normalizedPages.pageStart()).as("PageStart comparison").isEqualTo(start);
		// softAssertions.assertThat(normalizedPages.pageEnd()).as("PageEnd comparison").isEqualTo(end);
		// softAssertions.assertThat(normalizedPages.pageForComparison())
		// .as("Input '%s' has wrong pageForComparison", pages).isEqualTo(pageForComparison);
		// });
	}
}