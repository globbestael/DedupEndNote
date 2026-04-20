package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import edu.dedupendnote.services.ComparisonService;
import edu.dedupendnote.services.NormalizationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j

class JWSimilarityJournalTest {
	JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	/*
	 * Jarowinkler similarity > ComparisonService.JOURNAL_SIMILARITY_NO_REPLY for normalized journals
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("positiveArgumentProvider")
	void jwPositiveTest(String input1, String input2) {
		Double similarity = jws.apply(NormalizationService.normalizeJournal(input1),
				NormalizationService.normalizeJournal(input2));
		assertThat(similarity).isGreaterThan(ComparisonService.JOURNAL_SIMILARITY_NO_REPLY);
	}

	/*
	 * Is the Jarowinkler similarity <= ComparisonService.JOURNAL_SIMILARITY_NO_REPLY for normalized journals.
	 * Does NOT compare by initials, ...
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("negativeArgumentProvider")
	void jwNegativeTest(String input1, String input2) {
		Double similarity = jws.apply(NormalizationService.normalizeJournal(input1),
				NormalizationService.normalizeJournal(input2));
		System.err.println("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n".formatted(input1,
				NormalizationService.normalizeTitle(input1), input2, NormalizationService.normalizeTitle(input2)));
		assertThat(similarity).isLessThanOrEqualTo(ComparisonService.JOURNAL_SIMILARITY_NO_REPLY);
	}

	static Stream<Arguments> negativeArgumentProvider() {
		// @formatter:off
		return Stream.of(
			arguments("British journal of surgery", "Surgery"),
			arguments("British journal of surgery", "Journal of Surgery"),
			arguments("Samj South African Medical Journal", "South African Medical Journal"), // "Samj", not"SAMJ"
			arguments("Communications in Clinical Cytometry", "Cytometry"),
			arguments("Zhonghua nei ke za zhi [Chinese journal of internal medicine]",
					"Chung-Hua Nei Ko Tsa Chih Chinese Journal of Internal Medicine"),
			arguments("Nippon Kyobu Geka Gakkai Zasshi - Journal of the Japanese Association for Thoracic Surgery",
					"[Zasshi] [Journal]. Nihon Ky?bu Geka Gakkai"),
			arguments("Rinsho Ketsueki", "[Rinshō ketsueki] The Japanese journal of clinical hematology"),
			// UTF8, but addJournals() would split this!
			arguments("British journal of surgery", "Br J Surg"), // usable for abbreviations
			arguments("JAMA", "JAMA-Journal of the American Medical Association"), // order doesn't matter
			arguments("JAMA", "Journal of the American Medical Association"),
			arguments("JAMA-Journal of the American Medical Association", "JAMA"),
			arguments("Journal of the American Medical Association", "JAMA"),
			arguments("JAMA", "Journal of the American Medical Association"),
			arguments("Journal of the American Medical Association", "JAMA"),
			arguments("Hepatology", "Hepatology International"), // !
			arguments("AJR Am J Roentgenol", "American Journal of Roentgenology"),
			arguments("BMC Surg", "Bmc Surgery"),
			arguments("Jpn J Clin Oncol", "Japanese Journal of Clinical Oncology"),
			arguments("J Hepatobiliary Pancreat Surg", "Journal of Hepato-Biliary-Pancreatic Surgery"),
			arguments("BMJ (Online)", "Bmj"),
			arguments("BMJ (Online)", "British Medical Journal"),
			arguments("Bmj", "British Medical Journal"),
			arguments("J Med Ultrason (2001)", "Journal of Medical Ultrasonics"),
			arguments("MMW Fortschr Med", "MMW Fortschritte der Medizin"),
			arguments("Behavioral and Brain Functions Vol 10 Apr 2014, ArtID 11",
					"Behavioral & Brain Functions [Electronic Resource]: BBF"),
			arguments("[Technical report] SAM-TR. USAF School of Aerospace Medicine", "[Technical report] SAM-TR"),
			arguments(
					"Prilozi (Makedonska akademija na naukite i umetnostite. Oddelenie za medicinski nauki). 36 (3) (pp 35-41), 2015. Date of Publication: 2015.",
					"Prilozi Makedonska Akademija Na Naukite I Umetnostite Oddelenie Za Medicinski Nauki"),
			arguments(
					"Prilozi (Makedonska akademija na naukite i umetnostite. Oddelenie za medicinski nauki). 36 (3) (pp 35-41), 2015. Date of Publication: 2015.",
					"Prilozi (Makedonska akademija na naukite i umetnostite"),
			// TODO: This would work if journals are also split on " - ", possibly as an extra journal variant
			// see https://github.com/globbestael/DedupEndNote/issues/50
			arguments("European Surgery - Acta Chirurgica Austriaca", "European Surgery"),
			// TODO: This would work if journals are also split on " - ", possibly as an extra journal variant
			arguments("European Surgery - Acta Chirurgica Austriaca", "Acta Chirurgica Austriaca"),
			// TODO: This would work if journals are also split on "/", possibly as an extra journal variant
			arguments(
					"Chung-Kuo Hsiu Fu Chung Chien Wai Ko Tsa Chih/Chinese Journal of Reparative & Reconstructive Surgery",
					"Zhongguo xiu fu chong jian wai ke za zhi = Zhongguo xiufu chongjian waike zazhi = Chinese journal of reparative and reconstructive surgery"),
			// Fixed: Starts with "B" and has a "B" and "A" (all case insensitive)
			arguments("BBA Clinical", "Biochimica et biophysica peracta nonclinical")
		);
		// @formatter:on
	}

	static Stream<Arguments> positiveArgumentProvider() {
		return Stream.of(arguments("British journal of surgery", "British journal of surgery"),
				arguments("JAMA-Journal of the American Medical Association",
						"JAMA-Journal of the American Medical Association"),
				arguments("Journal of Laparoendoscopic and Advanced Surgical Techniques",
						"Journal of Laparoendoscopic & Advanced Surgical Techniques"),
				arguments("Hepatogastroenterology", "Hepato-Gastroenterology"),
				arguments("Lancet Haematol", "The Lancet Haematology"),
				arguments("Hepatology", "Hepatology (Baltimore, Md.)"));
	}

}
