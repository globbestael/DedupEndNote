package edu.dedupendnote.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.services.NormalizationService.AuthorRecord;

@TestConfiguration
class NormalizationServiceTest {

	@ParameterizedTest(name = "{index}: normalizeAuthor({0})={1}, {2}")
	@MethodSource("authorArgumentProvider")
	void normalizeAuthorTest(String input, AuthorRecord expected) {
		AuthorRecord result = NormalizationService.normalizeInputAuthors(input);
		assertThat(result).isEqualTo(expected);
	}

	/*
	 * This test calls IOService::addNormalizedPages because the logic there is quite intricate
	 * 
	 * TODO: is this test still relevant?
	 */
	@ParameterizedTest(name = "{index}: normalizeJournal({0})={1}")
	@MethodSource("journalArgumentProvider")
	void normalizeJournalTest(String input, String expected) {
		String result = NormalizationService.normalizeJournal(input);
		assertThat(result).isEqualTo(expected);
	}

	/*
	 * This test calls IOService::addNormalizedPages because the logic there is quite intricate and consequently should be tested.
	 * This part decides if the data of one of the input fields  (C7, SE, SP) should be used.
	 * In PagesTest there is a test of NormalizationService.normalizeInputPages irrespective of the field, so it tests how the
	 * field data is normalized IF it passes through IOService::addNormalizedPages.
	 * 
	 * See also issue #50: https://github.com/globbestael/DedupEndNote/issues/50
	 */
	@ParameterizedTest(name = "{index}: normalizePages({0})={1}")
	@MethodSource("pagesArgumentProvider")
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
					.as("PageStart comparison for %s, %s, %s".formatted(c7Input, seInput, spInput))
					.isEqualTo(expectedIsSeveralPages);
		});
		// assertThat(publication.getPageStart()).isEqualTo(expectedPageStart);
		// assertThat(publication.isSeveralPages()).isEqualTo(expectedIsSeveralPages);
	}

	static Stream<Arguments> pagesArgumentProvider() {
		// @formatter:off
		// String c7Input, String seInput, String spInput, String expectedPageStart, boolean expectedIsSeveralPages
		return Stream.of(
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
			arguments("24-25", "22-25", null, "2425", true), // is this whta we want?
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
			
			arguments(null, "", null, null, false),

			arguments(null, null, null, null, false) // last as dummy
		);
		// @formatter:on
	}

	@ParameterizedTest(name = "{index}: normalizeTitle({0})={1}")
	@MethodSource("titleArgumentProvider")
	void normalizeTitleTest(String input, String expected) {
		String result = NormalizationService.normalizeTitle(input);
		assertThat(result).isEqualTo(expected);
	}

	static Stream<Arguments> authorArgumentProvider() {
		// @formatter:off
		return Stream.of(
			arguments("Smith, Arthur", new AuthorRecord("Smith A", "Smith A", false)),
			arguments("Smith, Arthur J. C.", new AuthorRecord("Smith AJC", "Smith AJC", false)),
			arguments("Smith Jones, Arthur", new AuthorRecord("Smith Jones A", "Jones AS", true))
		);
		// @formatter:on
	}

	static Stream<Arguments> journalArgumentProvider() {
		// @formatter:off
		return Stream.of(
				arguments("The Journal of Medicine", "Journal of Medicine"),
				arguments("Jpn. J. Med.", "Japanese J Med"),
				arguments("My Journal & Co.", "My Journal Co"),
				arguments("Journal with (Parentheses)", "Journal with"),
				arguments("Journal with: A Subtitle", "Journal with"),
				arguments("Langenbeck's Archives of Surgery", "Langenbecks Archives of Surgery"),
				arguments("Annales d'Urologie", "Annales d Urologie"),
				arguments("Zbl. Chir.", "Zentralbl Chir"),
				arguments("Jbr-btr", "JBR BTR"),
				arguments("Rofo", "Rofo"),
				arguments("Gastro-Enterology", "Gastroenterology"),
				arguments("Anatomical Record. Part A, Discoveries in Molecular, Cellular, & Evolutionary Biology", "aa"),
				//	for these journals the titles are NOT normalized (IOService.skipNormalizationTitleFor)
				arguments("Molecular Imaging and Contrast Agent Database (MICAD)", "Molecular Imaging and Contrast Agent Database"),
				arguments("Natl Cancer Inst Carcinog Tech Rep Ser", "National Cancer Inst Carcinog Tech Rep Ser"),
				arguments("Natl Toxicol Program Tech Rep Ser", "National Toxicol Program Tech Rep Ser"),
				arguments("Ont Health Technol Assess Ser", "Ont Health Technol Assess Ser")
		);
		// @formatter:on
	}

	static Stream<Arguments> titleArgumentProvider() {
		// @formatter:off
		return Stream.of(
				arguments("This is a simple title.", "this is a simple title"),
				arguments("Title with [brackets] and (parentheses)", "title with brackets and parentheses"),
				arguments("  Title with   extra whitespace  ", "title with extra whitespace"),
				arguments("Title with \"quotes\" and <tags>", "title with quotes and"),
				arguments("A Title Starting With An Article", "title starting with an article"),
				arguments("The Title with Mixed Case", "title with mixed case"),
				arguments("Title with hyphenated-words", "title with hyphenatedwords"),
				arguments("Title with numbers 123 and symbols!@#", "title with numbers 123 and symbols"),
				arguments("Español y François y Deutsch", "espanol y francois y deutsch"),
				arguments("Title with (Japanese)", "title with"),
				arguments("Title with (Japanese text)", "title with"),
				arguments("11 beta-Hydroxysteroid Dehydrogenases and Hypertension in the Metabolic Syndrome", "11 betahydroxysteroid dehydrogenases and hypertension in the metabolic syndrome")
		);
		// @formatter:on
	}
}