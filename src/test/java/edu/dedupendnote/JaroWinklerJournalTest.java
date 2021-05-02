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

import edu.dedupendnote.domain.Record;
import edu.dedupendnote.services.DeduplicationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
//@ExtendWith(TimingExtension.class)
@TestConfiguration
public class JaroWinklerJournalTest {

	JaroWinklerSimilarity jws = new JaroWinklerSimilarity();
	DeduplicationService deduplicationService = new DeduplicationService();

	/*
	 * TODO: Test for splitting journals: e.g "European Surgery - Acta Chirurgica Austriaca"
	 */
	
	/*
	 * Jarowinkler distance > 0.9 for normalized journals
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("positiveArgumentProvider")
    void jwPositiveTest(String input1, String input2) {
		Double distance = jws.apply(Record.normalizeJournalJava8(input1), Record.normalizeJournalJava8(input2));
		// System.err.println(String.format("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n", input1, Record.normalizeJava8(input1), input2, Record.normalizeJava8(input2)));
		assertThat(distance).isGreaterThan(0.9);
    }

	/*
	 * Jarowinkler distance <= 0.9 for normalized journals
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("negativeArgumentProvider")
    void jwNegativeTest(String input1, String input2) {
		Double distance = jws.apply(Record.normalizeJournalJava8(input1), Record.normalizeJournalJava8(input2));
		System.err.println(String.format("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n", input1, Record.normalizeJava8(input1), input2, Record.normalizeJava8(input2)));
		assertThat(distance).isLessThanOrEqualTo(0.9);
    }
	
	/*
	 * full comparison for normalized journals: positive
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("fullPositiveArgumentProvider")
    void fullPositiveTest(String input1, String input2) {
		Record r1 = new Record();
		Record r2 = new Record();
		r1.addJournals(input1);
		r2.addJournals(input2);
		
		assertThat(deduplicationService.compareJournals(r1, r2))
										.as("Journals are NOT similar: " + r1.getJournals() +  " versus " + r2.getJournals())
										.isTrue();
    }

	/*
	 * full comparison for normalized journals: negative
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("fullNegativeArgumentProvider")
    void fullNegativeTest(String input1, String input2) {
		Record r1 = new Record();
		Record r2 = new Record();
		r1.addJournals(input1);
		r2.addJournals(input2);
		
		log.debug("Result: {}", deduplicationService.compareJournals(r1, r2));
		assertThat(deduplicationService.compareJournals(r1, r2))
										.as("Journals are similar: %s versus %s", r1.getJournals(), r2.getJournals())
										.isFalse();
    }

	/*
	 * journals which contain a slash
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})")
	@MethodSource("slashArgumentProvider")
    void slashTest(String input1, List<String> list) {
		Record r1 = new Record();
		r1.addJournals(input1);
		
		assertThat(r1.getJournals()).containsAll(list);
    }

	@Test
	void journalWithSquareBracketsAtEnd() {
		Record r1 = new Record();
		r1.addJournals("Zhonghua wai ke za zhi [Chinese journal of surgery]");
		
		assertThat(r1.getJournals()).hasSize(2);
		assertThat(r1.getJournals()).contains("Zhonghua wai ke za zhi");
		assertThat(r1.getJournals()).contains("Chinese journal of surgery");
	}
	
	@Test
	void journalWithSquareBracketsAtStart() {
		Record r1 = new Record();

		r1.addJournals("[Rinshō ketsueki] The Japanese journal of clinical hematology");

		assertThat(r1.getJournals()).hasSize(2);
		assertThat(r1.getJournals()).contains("Rinsho ketsueki");
		assertThat(r1.getJournals()).contains("Japanese journal of clinical hematology");
	}
	
    static Stream<Arguments> slashArgumentProvider() {
		return Stream.of(
	    		arguments(
	    				"The Canadian Journal of Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques",
	    				Arrays.asList(
	    						"Canadian Journal of Neurological Sciences",
	    						"Journal Canadien Des Sciences Neurologiques")),
	    		arguments(
	    				"Doklady biological sciences : proceedings of the Academy of Sciences of the USSR, Biological sciences sections / translated from Russian",
	    				Arrays.asList(
	    						"Doklady biological sciences")),
	    		arguments(
	    				"Polski Przeglad Chirurgiczny/ Polish Journal of Surgery",
	    				Arrays.asList(
	    						"Polski Przeglad Chirurgiczny",
	    						"Polish Journal of Surgery")),
	    		arguments(
	    				"Hematology/Oncology Clinics of North America",
	    				Arrays.asList(
	    						"Hematology Oncology Clinics of North America")),
	    		arguments(
	    				"Zhen ci yan jiu = Acupuncture research / [Zhongguo yi xue ke xue yuan Yi xue qing bao yan jiu suo bian ji]",
	    				Arrays.asList(
	    						"Zhen ci yan jiu",
	    						"Acupuncture research",
	    						"Zhongguo yi xue ke xue yuan Yi xue qing bao yan jiu suo bian ji"))
				);
    }
    
    static Stream<Arguments> fullPositiveArgumentProvider() {
		return Stream.of(
	    		arguments(
	    				"British journal of surgery",
	    				"\"British journal of surgery\""),	// embedded quotes
	    		arguments(
	    				"British journal of surgery",
	    				"Br J Surg"),	// usable for abbreviations
	    		arguments(
	    				"JAMA",
	    				"JAMA-Journal of the American Medical Association"),	// order doesn't matter
	    		arguments(
	    				"JAMA",
	    				"Journal of the American Medical Association"),
	    		arguments(
	    				"JAMA-Journal of the American Medical Association",
	    				"JAMA"),
	    		arguments(
	    				"Journal of the American Medical Association",
	    				"JAMA"),
	    		arguments(
	    				"JAMA", 
	    				"Journal of the American Medical Association"),
	    		arguments(
	    				"Journal of the American Medical Association",
	    				"JAMA"),
	    		arguments(
	    				"Hepatology",
	    				"Hepatology International"),						// !
	    		arguments(
	    				"AJR Am J Roentgenol",
	    				"American Journal of Roentgenology"),
	    		arguments(
	    				"BMC Surg",
	    				"Bmc Surgery"),
	    		arguments(
	    				"Jpn J Clin Oncol",
	    				"Japanese Journal of Clinical Oncology"),
	    		arguments(
	    				"BMJ (Online)",
	    				"Bmj"),
	    		arguments(
	    				"BMJ (Online)",
	    				"British Medical Journal"),
	    		arguments(
	    				"Bmj",
	    				"British Medical Journal"),
	    		arguments(
	    				"J Med Ultrason (2001)",
	    				"Journal of Medical Ultrasonics"),
	    		arguments(
	    				"MMW Fortschr Med",
	    				"MMW Fortschritte der Medizin"),
	    		arguments(
	    				"Behavioral and Brain Functions Vol 10 Apr 2014, ArtID 11",
	    				"Behavioral & Brain Functions [Electronic Resource]: BBF"),
	    		arguments(
	    				"[Technical report] SAM-TR. USAF School of Aerospace Medicine",
	    				"[Technical report] SAM-TR"),
	    		arguments(
	    				"Prilozi (Makedonska akademija na naukite i umetnostite. Oddelenie za medicinski nauki). 36 (3) (pp 35-41), 2015. Date of Publication: 2015.",
	    				"Prilozi Makedonska Akademija Na Naukite I Umetnostite Oddelenie Za Medicinski Nauki"),
	    		arguments(
	    				"Prilozi (Makedonska akademija na naukite i umetnostite. Oddelenie za medicinski nauki). 36 (3) (pp 35-41), 2015. Date of Publication: 2015.",
	    				"Prilozi (Makedonska akademija na naukite i umetnostite"),
	    		arguments(
	    				"Annals of Oncology",
	    				"Annals of Hepatology"), // JaroWinkler 0.916 !!!
	    		arguments(
	    				"Rinsho Ketsueki",
	    				"Rinshō ketsueki"),	// UTF8
	    		arguments(
	    				"J Pediatr Hematol Oncol",
	    				"Journal of pediatric hematology/oncology"),
	    		arguments(
	    				"Clin Appl Thromb Hemost",
	    				"Clinical and applied thrombosis/hemostasis"),
	    		arguments(
	    				"Ann Hepatol",
	    				"Annals of Hepatology"),
	    		arguments(
	    				"Langenbecks Arch Chir Suppl Kongressbd",
	    				"Langenbecks archiv fur chirurgie. Supplement. Kongressband. Deutsche gesellschaft fur chirurgie. Kongress"),
	    		arguments(
	    				/*
	    				 * Apostrophe
	    				 * - Bailliere's vs Baillieres
	    				 * - Crohn's vs Crohns
	    				 * - Langenbeck's vs Langenbecks
	    				 */
	    				"Langenbecks Arch Surg",
	    				"Langenbeck's archives of surgery / Deutsche Gesellschaft fur Chirurgie"),
	    		arguments(
	    				"J Hepatobiliary Pancreat Surg",
	    				"Journal of Hepato-Biliary-Pancreatic Surgery"),
	    		arguments(
	    				"Acta Gastroenterol Belg",
	    				"Acta gastro-enterologica belgica"),
	    		arguments(
	    				"JBR-BTR",
	    				"Journal Belge de Radiologie"),
	    		arguments(
	    				"MMW-Fortschritte der Medizin",
	    				"MMW Fortschr Med"),
	    		arguments(
	    				"Annales de Cardiologie et d'Angeiologie",
	    				"Ann Cardiol Angeiol (Paris)"),
	    		arguments(
	    				"Annales de Cardiologie et d'Angeiologie",
	    				"Ann. Cardiol. Angeiol."),
	    		arguments(
	    				"Zentralblatt Fur Chirurgie",
	    				"ZENTRALBL. CHIR."),
	    		arguments(
	    				"Zentralblatt Fur Chirurgie",
	    				"Zentralblatt fur Chirurgie"),
	    		arguments(
	    				"Ann Fr Anesth Reanim",
	    				"Annales Francaises d Anesthesie et de Reanimation"),
	    		arguments(
	    				"Ann Fr Anesth Reanim",
	    				"Annales françaises d'anesthèsie et de rèanimation"),
	    		arguments(
	    				"Annales Francaises d Anesthesie et de Reanimation",
	    				"Annales françaises d'anesthèsie et de rèanimation"),
	    		arguments(
	    				"Zhonghua wei chang wai ke za zhi = Chinese journal of gastrointestinal surgery",
	    				"Zhonghua Weichang Waike Zazhi"),
	    		arguments(
	    				"JNCCN Journal of the National Comprehensive Cancer Network",
	    				"Journal of the National Comprehensive Cancer Network"),
	    		arguments(
	    				"Best Practice and Research: Clinical Obstetrics and Gynaecology",
	    				"Best Practice & Research in Clinical Obstetrics & Gynaecology"),
	    		arguments(
	    				"Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine",
	    				"Clin Med Insights Circ Respir Pulm Med"), 
	    		arguments(
	    				"Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine",
	    				"Clinical Medicine Insights"), 
	    		arguments(
	    				"Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine",
	    				"Clinical Medicine Insights Circulatory, Respiratory and Pulmonary Medicine"),
	    		arguments(
	    				"Revista espa?ola de anestesiolog?a y reanimaci?n", 
	    				"Revista Espanola de Anestesiologia y Reanimacion"),
	    		arguments( 
	    				"Revista Espanola de Anestesiologia y Reanimacion",
	    				"Revista espa?ola de anestesiolog?a y reanimaci?n"),
	    		arguments(
	    				"Lancet Oncology",
	    				"The lancet oncology"),
	    		arguments(
	    				"Journal De Radiologie",
	    				"Journal de Radiologie"), 	// unexpected capital
	    		arguments(
	    				"Journal De Radiologie",
	    				"J Radiol"), 				// unexpected capital
	    		// FIXME: Is this acceptable? Starts with "B" and has a "B" and "A" (all case insensitive)
	    		arguments(
	    				"BBA Clinical",
	    				"Biochimica et biophysica peracta nonclinical")
	    	);
    }

    static Stream<Arguments> fullNegativeArgumentProvider() {
		return Stream.of(
	    		arguments(
	    				"No Example yet",
	    				"The same")
		);
    }

    static Stream<Arguments> negativeArgumentProvider() {
		return Stream.of(
	    		arguments(
	    				"British journal of surgery",
	    				"Surgery"),
	    		arguments(
	    				"British journal of surgery",
	    				"Journal of Surgery"),
	    		arguments(
	    				"Samj South African Medical Journal",
	    				"South African Medical Journal"),				// "Samj", not "SAMJ"
	    		arguments(
	    				"Communications in Clinical Cytometry", 
	    				"Cytometry"),
	    		arguments(
	    				"Zhonghua nei ke za zhi [Chinese journal of internal medicine]",
	    				"Chung-Hua Nei Ko Tsa Chih Chinese Journal of Internal Medicine"),
	    		arguments(
	    				"Nippon Kyobu Geka Gakkai Zasshi - Journal of the Japanese Association for Thoracic Surgery",
	    				"[Zasshi] [Journal]. Nihon Ky?bu Geka Gakkai"),
	    		arguments(
	    				"Rinsho Ketsueki",
	    				"[Rinshō ketsueki] The Japanese journal of clinical hematology"),	// UTF8, but addJournals() would split this!
	    		arguments(
	    				"British journal of surgery",
	    				"Br J Surg"),	// usable for abbreviations
	    		arguments(
	    				"JAMA",
	    				 "JAMA-Journal of the American Medical Association"),	// order doesn't matter
	    		arguments(
	    				"JAMA",
	    				"Journal of the American Medical Association"),
	    		arguments(
	    				"JAMA-Journal of the American Medical Association",
	    				"JAMA"),
	    		arguments(
	    				"Journal of the American Medical Association",
	    				"JAMA"),
	    		arguments(
	    				"JAMA", 
	    				"Journal of the American Medical Association"),
	    		arguments(
	    				"Journal of the American Medical Association",
	    				"JAMA"),
	    		arguments(
	    				"Hepatology",
	    				"Hepatology International"),						// !
	    		arguments(
	    				"AJR Am J Roentgenol",
	    				"American Journal of Roentgenology"),
	    		arguments(
	    				"BMC Surg",
	    				"Bmc Surgery"),
	    		arguments(
	    				"Jpn J Clin Oncol",
	    				"Japanese Journal of Clinical Oncology"),
	    		arguments(
	    				"J Hepatobiliary Pancreat Surg",
	    				"Journal of Hepato-Biliary-Pancreatic Surgery"),
	    		arguments(
	    				"BMJ (Online)",
	    				"Bmj"),
	    		arguments(
	    				"BMJ (Online)",
	    				"British Medical Journal"),
	    		arguments(
	    				"Bmj",
	    				"British Medical Journal"),
	    		arguments(
	    				"J Med Ultrason (2001)",
	    				"Journal of Medical Ultrasonics"),
	    		arguments(
	    				"MMW Fortschr Med",
	    				"MMW Fortschritte der Medizin"),
	    		arguments(
	    				"Behavioral and Brain Functions Vol 10 Apr 2014, ArtID 11",
	    				"Behavioral & Brain Functions [Electronic Resource]: BBF"),
	    		arguments(
	    				"[Technical report] SAM-TR. USAF School of Aerospace Medicine",
	    				"[Technical report] SAM-TR"),
	    		arguments(
	    				"Prilozi (Makedonska akademija na naukite i umetnostite. Oddelenie za medicinski nauki). 36 (3) (pp 35-41), 2015. Date of Publication: 2015.",
	    				"Prilozi Makedonska Akademija Na Naukite I Umetnostite Oddelenie Za Medicinski Nauki"),
	    		arguments(
	    				"Prilozi (Makedonska akademija na naukite i umetnostite. Oddelenie za medicinski nauki). 36 (3) (pp 35-41), 2015. Date of Publication: 2015.",
	    				"Prilozi (Makedonska akademija na naukite i umetnostite"),
	    		// TODO: This would work if journals are also split on " - ", possibly as an extra journal variant
	    		arguments(
	    				"European Surgery - Acta Chirurgica Austriaca",
	    				"European Surgery"),
	    		// TODO: This would work if journals are also split on " - ", possibly as an extra journal variant
	    		arguments(
	    				"European Surgery - Acta Chirurgica Austriaca",
	    				"Acta Chirurgica Austriaca"),
	    		// TODO: This would work if journals are also split on "/", possibly as an extra journal variant
	    		arguments(
	    				"Chung-Kuo Hsiu Fu Chung Chien Wai Ko Tsa Chih/Chinese Journal of Reparative & Reconstructive Surgery", 
	    				"Zhongguo xiu fu chong jian wai ke za zhi = Zhongguo xiufu chongjian waike zazhi = Chinese journal of reparative and reconstructive surgery")
	    	);
    }
    
    static Stream<Arguments> positiveArgumentProvider() {
		return Stream.of(
	    		arguments(
	    				"British journal of surgery", 
	    				"British journal of surgery"),
	    		arguments(
	    				"JAMA-Journal of the American Medical Association",
	    				"JAMA-Journal of the American Medical Association"),
	    		arguments(
	    				"Journal of Laparoendoscopic and Advanced Surgical Techniques",
	    				"Journal of Laparoendoscopic & Advanced Surgical Techniques"),
	    		arguments(
	    				"Hepatogastroenterology",
	    				"Hepato-Gastroenterology"),
	    		arguments(
	    				"Lancet Haematol",
	    				"The Lancet Haematology"),
	    		arguments(
	    				"Hepatology",
	    				"Hepatology (Baltimore, Md.)")
          );
    }
}