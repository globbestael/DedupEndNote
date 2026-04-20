package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import edu.dedupendnote.services.IOService;
import edu.dedupendnote.services.NormalizationService;
import edu.dedupendnote.domain.Publication;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class NormalizationServiceJournalTest {

	@ParameterizedTest(name = "{index}: normalizeJournal({0})={1}")
	@MethodSource("journalArgumentProvider")
	void normalizeJournalTest(String input, String expected) {
		String result = NormalizationService.normalizeJournal(input);
		assertThat(result).isEqualTo(expected);
	}

	/*
	 * journals which contain a slash
	 */
	@ParameterizedTest(name = "{index}: slashTest({0}, {1})")
	@MethodSource("slashArgumentProvider")
	void slashTest(String input1, List<String> list) {
		Publication p1 = new Publication();
		IOService.addNormalizedJournal(input1, p1, "T2");

		for (String j : p1.getJournals()) {
			log.error("For input '{}': {}", input1, j);
		}
		assertThat(p1.getJournals()).containsAll(list);
	}

	@Test
	void journalWithSquareBracketsAtEnd() {
		Publication p1 = new Publication();
		IOService.addNormalizedJournal("Zhonghua wai ke za zhi [Chinese journal of surgery]", p1, "T2");

		assertThat(p1.getJournals()).hasSize(3);
		assertThat(p1.getJournals()).containsAll(Set.of("Zhonghua wai ke za zhi", "Chinese journal of surgery",
				"Zhonghua wai ke za zhi Chinese journal of surgery"));
	}

	@Test
	void journalWithSquareBracketsAtStart() {
		Publication p1 = new Publication();

		IOService.addNormalizedJournal("[Rinsho ketsueki] The Japanese journal of clinical hematology", p1, "T2");

		assertThat(p1.getJournals()).hasSize(3);
		// The variant with both parts has NOT removed the leading article of second part
		assertThat(p1.getJournals()).containsAll(Set.of("Rinsho ketsueki", "Japanese journal of clinical hematology",
				"Rinsho ketsueki The Japanese journal of clinical hematology"));
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
				arguments("Anatomical Record. Part A, Discoveries in Molecular, Cellular, & Evolutionary Biology", "Anatomical Record Part A Discoveries in Molecular Cellular Evolutionary Biology"),
				//	for these journals the titles are NOT normalized (IOService.skipNormalizationTitleFor)
				arguments("Molecular Imaging and Contrast Agent Database (MICAD)", "Molecular Imaging and Contrast Agent Database"),
				arguments("Natl Cancer Inst Carcinog Tech Rep Ser", "National Cancer Inst Carcinog Tech Rep Ser"),
				arguments("Natl Toxicol Program Tech Rep Ser", "National Toxicol Program Tech Rep Ser"),
				arguments("Ont Health Technol Assess Ser", "Ont Health Technol Assess Ser")
		);
		// @formatter:on
	}

	static Stream<Arguments> slashArgumentProvider() {
		// @formatter:off
		return Stream.of(
			arguments(
				"The Canadian Journal of Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques",
				List.of(
					"Canadian Journal of Neurological Sciences",
					"Journal Canadien Des Sciences Neurologiques")),
			arguments(
					"Doklady biological sciences : proceedings of the Academy of Sciences of the USSR, Biological sciences sections / translated from Russian",
					List.of("Doklady biological sciences")),
			arguments("Polski Przeglad Chirurgiczny/ Polish Journal of Surgery",
					List.of(
						"Polski Przeglad Chirurgiczny",
						"Polish Journal of Surgery")),
			arguments("Hematology/Oncology Clinics of North America",
					List.of("Hematology Oncology Clinics of North America")),
			arguments(
					"Zhen ci yan jiu = Acupuncture research / [Zhongguo yi xue ke xue yuan Yi xue qing bao yan jiu suo bian ji]",
					List.of(
						"Zhen ci yan jiu",
						"Acupuncture research",
						"Zhongguo yi xue ke xue yuan Yi xue qing bao yan jiu suo bian ji")),
			arguments("Anatomical Record. Part A, Discoveries in Molecular, Cellular, & Evolutionary Biology",
				List.of(
					// "Anatomical Record Part A Discoveries in Molecular Cellular Evolutionary Biology",
					"Anatomical Record",
					"Part A Discoveries in Molecular Cellular Evolutionary Biology"))

		// @formatter:on
		);
	}

}
