package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.services.DeduplicationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// @ExtendWith(TimingExtension.class)
@TestConfiguration
class JaroWinklerJournalTest {

	JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	DeduplicationService deduplicationService = new DeduplicationService();

	/*
	 * TODO: Test for splitting journals: e.g
	 * "European Surgery - Acta Chirurgica Austriaca"
	 */

	/*
	 * Jarowinkler similarity > 0.9 for normalized journals
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("positiveArgumentProvider")
	void jwPositiveTest(String input1, String input2) {
		Double similarity = jws.apply(Publication.normalizeJournalJava8(input1),
				Publication.normalizeJournalJava8(input2));
		// System.err.println(String.format("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n",
		// input1, Publication.normalizeJava8(input1), input2,
		// Publication.normalizeJava8(input2)));
		assertThat(similarity).isGreaterThan(DeduplicationService.JOURNAL_SIMILARITY_REPLY);
	}

	/*
	 * Jarowinkler similarity <= 0.9 for normalized journals
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("negativeArgumentProvider")
	void jwNegativeTest(String input1, String input2) {
		Double similarity = jws.apply(Publication.normalizeJournalJava8(input1),
				Publication.normalizeJournalJava8(input2));
		System.err.println("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n".formatted(input1,
				Publication.normalizeTitleJava8(input1), input2, Publication.normalizeTitleJava8(input2)));
		assertThat(similarity).isLessThanOrEqualTo(DeduplicationService.JOURNAL_SIMILARITY_REPLY);
	}

	/*
	 * full comparison for normalized journals: positive
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("fullPositiveArgumentProvider")
	void fullPositiveTest(String input1, String input2) {
		Publication r1 = new Publication();
		Publication r2 = new Publication();
		log.debug("==================================================================");

		r1.addJournals(input1);
		r2.addJournals(input2);

		assertThat(deduplicationService.compareJournals(r1, r2))
				.as("Journals are NOT similar: " + r1.getJournals() + " versus " + r2.getJournals()).isTrue();
	}

	/*
	 * full comparison for normalized journals: negative
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("fullNegativeArgumentProvider")
	void fullNegativeTest(String input1, String input2) {
		Publication r1 = new Publication();
		Publication r2 = new Publication();
		r1.addJournals(input1);
		r2.addJournals(input2);

		log.debug("Result: {}", deduplicationService.compareJournals(r1, r2));
		assertThat(deduplicationService.compareJournals(r1, r2))
				.as("Journals are similar: %s versus %s", r1.getJournals(), r2.getJournals()).isFalse();
	}

	/*
	 * Journals with very long titles produce very long Patterns, esp. in
	 * compareJournals_FirstAsInitialism()
	 */
	@Test
	void JournalFullCapsTest() {
		Publication r1 = new Publication();
		Publication r2 = new Publication();
		// r1.addJournals("ARTHROSCOPY-THE JOURNAL OF ARTHROSCOPIC AND RELATED SURGERY");
		// r2.addJournals("Arthroscopy : the journal of arthroscopic & related surgery :
		// official publication of the Arthroscopy Association of North America and the
		// International Arthroscopy Association");
		r1.addJournals("Ann Intern Med");
		r2.addJournals("ANNALS OF INTERNAL MEDICINE");

		log.debug("Result: {}", deduplicationService.compareJournals(r1, r2));
		assertThat(deduplicationService.compareJournals(r1, r2))
				.as("Journals are similar: %s versus %s", r1.getJournals(), r2.getJournals()).isTrue();
	}

	/*
	 * journals which contain a slash
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("slashArgumentProvider")
	void slashTest(String input1, List<String> list) {
		Publication r1 = new Publication();
		r1.addJournals(input1);

		assertThat(r1.getJournals()).containsAll(list);
	}

	@Test
	void journalWithSquareBracketsAtEnd() {
		Publication r1 = new Publication();
		r1.addJournals("Zhonghua wai ke za zhi [Chinese journal of surgery]");

		assertThat(r1.getJournals()).hasSize(2);
		assertThat(r1.getJournals()).contains("Zhonghua wai ke za zhi");
		assertThat(r1.getJournals()).contains("Chinese journal of surgery");
	}

	@Test
	void journalWithSquareBracketsAtStart() {
		Publication r1 = new Publication();

		r1.addJournals("[Rinshō ketsueki] The Japanese journal of clinical hematology");

		assertThat(r1.getJournals()).hasSize(2);
		assertThat(r1.getJournals()).contains("Rinsho ketsueki");
		assertThat(r1.getJournals()).contains("Japanese journal of clinical hematology");
	}

	static Stream<Arguments> slashArgumentProvider() {
		return Stream.of(arguments(
				"The Canadian Journal of Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques",
				Arrays.asList("Canadian Journal of Neurological Sciences",
						"Journal Canadien Des Sciences Neurologiques")),
				arguments(
						"Doklady biological sciences : proceedings of the Academy of Sciences of the USSR, Biological sciences sections / translated from Russian",
						Arrays.asList("Doklady biological sciences")),
				arguments("Polski Przeglad Chirurgiczny/ Polish Journal of Surgery",
						Arrays.asList("Polski Przeglad Chirurgiczny", "Polish Journal of Surgery")),
				arguments("Hematology/Oncology Clinics of North America",
						Arrays.asList("Hematology Oncology Clinics of North America")),
				arguments(
						"Zhen ci yan jiu = Acupuncture research / [Zhongguo yi xue ke xue yuan Yi xue qing bao yan jiu suo bian ji]",
						Arrays.asList("Zhen ci yan jiu", "Acupuncture research",
								"Zhongguo yi xue ke xue yuan Yi xue qing bao yan jiu suo bian ji")));
	}

	static Stream<Arguments> fullPositiveArgumentProvider() {
		return Stream.of(arguments("British journal of surgery", "\"British journal of surgery\""), // embedded
																									// quotes
				arguments("British journal of surgery", "Br J Surg"), // usable for
																		// abbreviations
				arguments("JAMA", "JAMA-Journal of the American Medical Association"), // order
																						// doesn't
																						// matter
				arguments("JAMA", "Journal of the American Medical Association"),
				arguments("JAMA-Journal of the American Medical Association", "JAMA"),
				arguments("Journal of the American Medical Association", "JAMA"),
				arguments("JAMA", "Journal of the American Medical Association"),
				arguments("Journal of the American Medical Association", "JAMA"),
				arguments("Hepatology", "Hepatology International"), // !
				arguments("AJR Am J Roentgenol", "American Journal of Roentgenology"),
				arguments("BMC Surg", "Bmc Surgery"),
				arguments("Jpn J Clin Oncol", "Japanese Journal of Clinical Oncology"),
				arguments("BMJ (Online)", "Bmj"), arguments("BMJ (Online)", "British Medical Journal"),
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
				arguments("Annals of Oncology", "Annals of Hepatology"), // JaroWinkler
																			// 0.916 !!!
				arguments("Rinsho Ketsueki", "Rinshō ketsueki"), // UTF8
				arguments("J Pediatr Hematol Oncol", "Journal of pediatric hematology/oncology"),
				arguments("Clin Appl Thromb Hemost", "Clinical and applied thrombosis/hemostasis"),
				arguments("Ann Hepatol", "Annals of Hepatology"),
				arguments("Langenbecks Arch Chir Suppl Kongressbd",
						"Langenbecks archiv fur chirurgie. Supplement. Kongressband. Deutsche gesellschaft fur chirurgie. Kongress"),
				arguments(
						/*
						 * Apostrophe - Bailliere's vs Baillieres - Crohn's vs Crohns -
						 * Langenbeck's vs Langenbecks
						 */
						"Langenbecks Arch Surg",
						"Langenbeck's archives of surgery / Deutsche Gesellschaft fur Chirurgie"),
				arguments("J Hepatobiliary Pancreat Surg", "Journal of Hepato-Biliary-Pancreatic Surgery"),
				arguments("Acta Gastroenterol Belg", "Acta gastro-enterologica belgica"),
				arguments("JBR-BTR", "Journal Belge de Radiologie"),
				arguments("MMW-Fortschritte der Medizin", "MMW Fortschr Med"),
				arguments("Annales de Cardiologie et d'Angeiologie", "Ann Cardiol Angeiol (Paris)"),
				arguments("Annales de Cardiologie et d'Angeiologie", "Ann. Cardiol. Angeiol."),
				arguments("Zentralblatt Fur Chirurgie", "ZENTRALBL. CHIR."),
				arguments("Zentralblatt Fur Chirurgie", "Zentralblatt fur Chirurgie"),
				arguments("Ann Fr Anesth Reanim", "Annales Francaises d Anesthesie et de Reanimation"),
				arguments("Ann Fr Anesth Reanim", "Annales françaises d'anesthèsie et de rèanimation"),
				arguments("Annales Francaises d Anesthesie et de Reanimation",
						"Annales françaises d'anesthèsie et de rèanimation"),
				arguments("Zhonghua wei chang wai ke za zhi = Chinese journal of gastrointestinal surgery",
						"Zhonghua Weichang Waike Zazhi"),
				arguments("JNCCN Journal of the National Comprehensive Cancer Network",
						"Journal of the National Comprehensive Cancer Network"),
				arguments("Best Practice and Research: Clinical Obstetrics and Gynaecology",
						"Best Practice & Research in Clinical Obstetrics & Gynaecology"),
				arguments("Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine",
						"Clin Med Insights Circ Respir Pulm Med"),
				arguments("Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine",
						"Clinical Medicine Insights"),
				arguments("Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine",
						"Clinical Medicine Insights Circulatory, Respiratory and Pulmonary Medicine"),
				arguments("Revista espa?ola de anestesiolog?a y reanimaci?n",
						"Revista Espanola de Anestesiologia y Reanimacion"),
				arguments("Revista Espanola de Anestesiologia y Reanimacion",
						"Revista espa?ola de anestesiolog?a y reanimaci?n"),
				arguments("Lancet Oncology", "The lancet oncology"),
				arguments("Journal De Radiologie", "Journal de Radiologie"), // unexpected
																				// capital
				arguments("Journal De Radiologie", "J Radiol"), // unexpected capital
				arguments("Rofo", "Röfo"),
				arguments("Rofo", "RöFo : Fortschritte auf dem Gebiete der Röntgenstrahlen und der Nuklearmedizin"),
				arguments("Rofo",
						"RoFo Fortschritte auf dem Gebiet der Rontgenstrahlen und der Bildgebenden Verfahren"),
				arguments("RöFo : Fortschritte auf dem Gebiete der Röntgenstrahlen und der Nuklearmedizin",
						"RoFo Fortschritte auf dem Gebiet der Rontgenstrahlen und der Bildgebenden Verfahren"),
				arguments("Rofo",
						"Rofo-Fortschritte auf dem Gebiet der Rontgenstrahlen und der Bildgebenden Verfahren"),
				arguments("Fortschritte auf dem Gebiet der Rontgenstrahlen und der Bildgebenden Verfahren",
						"Rofo-Fortschritte auf dem Gebiet der Rontgenstrahlen und der Bildgebenden Verfahren"),
				arguments("Rofo", "Fortschritte auf dem Gebiet der Rontgenstrahlen"),
				arguments("Zentralbl Allg Pathol", "Zbl. Allg. Path. Path. Anat."),
				arguments("Zentralblatt für allgemeine Pathologie u. pathologische Anatomie",
						"Zbl. Allg. Path. Path. Anat."),
				arguments("Acta chirurgica Scandinavica. Supplementum", "Acta Chirurgica Scandinavica"),
				arguments("Acta Chir Scand Suppl", "Acta Chirurgica Scandinavica"),
				arguments("Pleura Peritioneum", "PLEURA AND PERITIONEUM"),
				arguments("Clin.Neuropharmacol.",
						"Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date of Publication: 1989."),
				arguments("J.Neurosci.",
						"Journal of Neuroscience.29 (37) ()(pp 11451-11460) 2009.Date of Publication: 16 Sep 2009."),
				arguments("Arzneimittel-Forschung/Drug Research.55 (3) ()(pp 153-159) 2005.Date of Publication: 2005.",
						"Arzneimittelforschung."),
				arguments("Zhen.Ci.Yan.Jiu.",
						"Zhen ci yan jiu = Acupuncture research / [Zhongguo yi xue ke xue yuan Yi xue qing bao yan jiu suo bian ji].39 (2) ()(pp 136-141) 2014.Date of Publication: Apr 2014."),
				arguments("RSC Adv", "RSC ADVANCES"), arguments("Pleura Peritoneum", "PLEURA AND PERITONEUM"),
				arguments("AJNR Am J Neuroradiol", "AMERICAN JOURNAL OF NEURORADIOLOGY"),
				arguments("Samj South African Medical Journal", "South African Medical Journal"), // "Samj",
																									// not
																									// "SAMJ"
				arguments("Adv Physiol Educ", "American Journal of Physiology - Advances in Physiology Education"),
				arguments("Altern Lab Anim", "ATLA Alternatives to Laboratory Animals"),
				arguments("Antiinflamm Antiallergy Agents Med Chem",
						"Anti-Inflammatory and Anti-Allergy Agents in Medicinal Chemistry"),
				arguments("Paediatr Drugs", "Pediatric Drugs"),
				arguments("Birth Defects Res C Embryo Today", "Birth Defects Research Part C - Embryo Today: Reviews"),
				arguments("Birth Defects Res C Embryo Today", "BIRTH DEFECTS RESEARCH PART C-EMBRYO TODAY-REVIEWS"),
				arguments("Clinical neuropharmacology.12 Suppl 2 ()(pp v-xii; S1-105) 1989.Date of Publication: 1989.",
						"Clinical neuropharmacology"),
				arguments("Ann Fr Anesth Reanim", "ANNALES FRANCAISES D ANESTHESIE ET DE REANIMATION"),
				arguments("Klin Monbl Augenheilkd", "KLINISCHE MONATSBLATTER FUR AUGENHEILKUNDE"),
				arguments("European Respiratory Journal. Conference: European Respiratory Society Annual Congress",
						"European Respiratory Journal"),
				arguments("Asian Pacific Digestive Week 2014. Bali Indonesia.", "Asian Pacific Digestive Week"),
				arguments(
						"12th World Congress of the International Hepato-Pancreato-Biliary Association. Sao Paulo Brazil.",
						"12th World Congress of the International Hepato-Pancreato-Biliary Association. Sao Paulo Brazil."),
				arguments( // this one matches because of Publication.journalExtraPattern
						"International Liver Transplantation Society 15th Annual International Congress. New York, NY United States.",
						"International Liver Transplantation Society"),
				arguments( // this one matches because IOService.conferencePattern adds a
							// second journal
						"International Liver Transplantation Society. Annual International Congress. New York, NY United States.",
						"International Liver Transplantation Society"),
				arguments("Neuroendocrinology Letters.35 (2) ()(pp 129-136)", "Neuroendocrinology Letters"), arguments( // JWS
																														// 0.90952380
																														// !!
						"Journal of Thoracic Oncology", "Journal of Clinical Oncology"),
				arguments( // see issue #1
						"ADHD-ATTENTION DEFICIT AND HYPERACTIVITY DISORDERS", "Atten Defic Hyperact Disord"),
				arguments("European Child and Adolescent Psychiatry", "European Child & Adolescent Psychiatry"),
				arguments("Bull Acad Natl Med", "Bulletin de l'Académie nationale de médecine"));
	}

	static Stream<Arguments> fullNegativeArgumentProvider() {
		return Stream.of(
				// Fixed: Patterns for journals used ".*(\\b|)", but this was changed to
				// ".*\\b".
				// This made up example was positive: Starts with "B" and has a "B" and
				// "A" (all case insensitive)
				arguments("BBA Clinical", "Biochimica et biophysica peracta nonclinical"), arguments( // see
																										// issue
																										// #1
						"The Journal of the Kentucky Medical Association.95 (4) ()(pp 145-148) 1997.Date of Publication: Apr 1997.",
						"J.Ky.Med.Assoc."),
				arguments( // "sports" in abbreviation, "sport" in full version
						"Asia Pac J Sports Med Arthrosc Rehabil Technol",
						"ASIA-PACIFIC JOURNAL OF SPORT MEDICINE ARTHROSCOPY REHABILITATION AND TECHNOLOGY"),
				arguments( // different first letter --> compareJournals_...() not tried
						"Macedonian Journal of Medical Sciences", "Open Access Macedonian Journal of Medical Sciences"),
				arguments( // different first letter --> compareJournals_...() not tried
						"Rev Sci Tech", "OIE Revue Scientifique et Technique"),
				arguments( // different first letter --> compareJournals_...() not tried
						"Prz Menopauzalny", "MENOPAUSE REVIEW-PRZEGLAD MENOPAUZALNY"),
				arguments("Biol Aujourdhui", "Biologie Aujourd'hui"),
				arguments("Brain Res Brain Res Rev", "BRAIN RESEARCH REVIEWS"),
				arguments("Clinical Neurosurgery", "NEUROSURGERY"),
				arguments("Int Urogynecol J Pelvic Floor Dysfunct", "INTERNATIONAL UROGYNECOLOGY JOURNAL"), arguments( // "Part
																														// D"
																														// vs
																														// "D"
						"Comp Biochem Physiol Part D Genomics Proteomics",
						"COMPARATIVE BIOCHEMISTRY AND PHYSIOLOGY D-GENOMICS & PROTEOMICS"),
				arguments( // with and without "London"
						"Philos Trans R Soc Lond B Biol Sci",
						"Philosophical Transactions of the Royal Society B: Biological Sciences"),
				arguments("No other examples yet", "The same"));
	}

	static Stream<Arguments> negativeArgumentProvider() {
		return Stream.of(arguments("British journal of surgery", "Surgery"),
				arguments("British journal of surgery", "Journal of Surgery"),
				arguments("Samj South African Medical Journal", "South African Medical Journal"), // "Samj",
																									// not
																									// "SAMJ"
				arguments("Communications in Clinical Cytometry", "Cytometry"),
				arguments("Zhonghua nei ke za zhi [Chinese journal of internal medicine]",
						"Chung-Hua Nei Ko Tsa Chih Chinese Journal of Internal Medicine"),
				arguments("Nippon Kyobu Geka Gakkai Zasshi - Journal of the Japanese Association for Thoracic Surgery",
						"[Zasshi] [Journal]. Nihon Ky?bu Geka Gakkai"),
				arguments("Rinsho Ketsueki", "[Rinshō ketsueki] The Japanese journal of clinical hematology"), // UTF8,
																												// but
																												// addJournals()
																												// would
																												// split
																												// this!
				arguments("British journal of surgery", "Br J Surg"), // usable for
																		// abbreviations
				arguments("JAMA", "JAMA-Journal of the American Medical Association"), // order
																						// doesn't
																						// matter
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
				arguments("BMJ (Online)", "Bmj"), arguments("BMJ (Online)", "British Medical Journal"),
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
				// TODO: This would work if journals are also split on " - ", possibly as
				// an extra journal variant
				arguments("European Surgery - Acta Chirurgica Austriaca", "European Surgery"),
				// TODO: This would work if journals are also split on " - ", possibly as
				// an extra journal variant
				arguments("European Surgery - Acta Chirurgica Austriaca", "Acta Chirurgica Austriaca"),
				// TODO: This would work if journals are also split on "/", possibly as an
				// extra journal variant
				arguments(
						"Chung-Kuo Hsiu Fu Chung Chien Wai Ko Tsa Chih/Chinese Journal of Reparative & Reconstructive Surgery",
						"Zhongguo xiu fu chong jian wai ke za zhi = Zhongguo xiufu chongjian waike zazhi = Chinese journal of reparative and reconstructive surgery"),
				// Fixed: Starts with "B" and has a "B" and "A" (all case insensitive)
				arguments("BBA Clinical", "Biochimica et biophysica peracta nonclinical"));
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