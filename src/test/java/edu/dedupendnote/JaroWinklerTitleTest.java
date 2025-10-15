package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.services.ComparatorService;
import edu.dedupendnote.services.IOService;
import edu.dedupendnote.services.NormalizationService;

//@Slf4j
//@ExtendWith(TimingExtension.class)
@TestConfiguration
class JaroWinklerTitleTest {
	NormalizationService normalizationService = new NormalizationService();
	ComparatorService comparatorService = new ComparatorService();

	String homeDir = System.getProperty("user.home");

	String testdir = homeDir + "/dedupendnote_files";
	JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	/*
	 * This test is not useful because it uses only the input title itself, not it's variants (main title, subtitles)
	 */
	@Disabled("Compares only full title, not the parts")
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("positiveArgumentProvider")
	void jwPositiveTest(String input1, String input2, double expected) {
		Double similarity = jws.apply(normalizationService.normalizeTitle(input1),
				normalizationService.normalizeTitle(input2));
		System.err.println("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n".formatted(input1,
				normalizationService.normalizeTitle(input1), input2, normalizationService.normalizeTitle(input2)));

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(similarity).as("\nTitle1: %s\nTitle2: %s", input1, input2).isEqualTo(expected,
				within(0.01));
		softAssertions.assertThat(similarity)
				.as("\nTitle1: %s\nTitle2: %s\nGreaterThanOrEqualTo threshold", input1, input2)
				.isGreaterThanOrEqualTo(ComparatorService.TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS);
		softAssertions.assertAll();
	}

	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("positiveArgumentProvider")
	void jwFullPositiveTest(String input1, String input2, double expected) {
		Publication p1 = new Publication();
		p1.addTitles(input1, normalizationService);
		Publication p2 = new Publication();
		p2.addTitles(input2, normalizationService);

		Double highestSimilarity = 0.0;

		for (String title1 : p1.getTitles()) {
			for (String title2 : p2.getTitles()) {
				Double similarity = jws.apply(title1, title2);
				if (similarity > highestSimilarity) {
					highestSimilarity = similarity;
				}
			}
		}

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(highestSimilarity)
				.as("\nTitle1: %s\nTitle2: %s", p1.getTitles().get(0), p2.getTitles().get(0))
				.isEqualTo(expected, within(0.01));
		softAssertions.assertThat(highestSimilarity)
				.as("\nTitle1: %s\nTitle2: %s\nGreaterThanOrEqualTo threshold", p1.getTitles(), p2.getTitles())
				.isGreaterThanOrEqualTo(ComparatorService.TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS);
		softAssertions.assertAll();
	}

	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("negativeArgumentProvider")
	void jwNegativeTest(String input1, String input2, double expected) {
		Double similarity = jws.apply(normalizationService.normalizeTitle(input1),
				normalizationService.normalizeTitle(input2));
		System.err.println("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n".formatted(input1,
				normalizationService.normalizeTitle(input1), input2, normalizationService.normalizeTitle(input2)));
		assertThat(similarity).isEqualTo(expected, within(0.01))
				.isLessThan(ComparatorService.TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS);
	}

	static String revertString(String s) {
		return new StringBuilder(s).reverse().toString();
	}

	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("negativeArgumentProvider")
	void jwFullNegativeTest(String input1, String input2, double expected) {
		Publication p1 = new Publication();
		p1.addTitles(input1, normalizationService);
		Publication p2 = new Publication();
		p2.addTitles(input2, normalizationService);

		Double highestSimilarity = 0.0;
		String highestTitle1 = "";
		String highestTitle2 = "";

		for (String title1 : p1.getTitles()) {
			for (String title2 : p2.getTitles()) {
				Double similarity = jws.apply(title1, title2);
				if (similarity > highestSimilarity) {
					highestSimilarity = similarity;
					highestTitle1 = title1;
					highestTitle2 = title2;
				}
			}
		}

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(highestSimilarity).as("\nTitle1: %s\nTitle2: %s", highestTitle1, highestTitle2)
				.isEqualTo(expected, within(0.01));
		softAssertions.assertThat(highestSimilarity)
				.as("\nTitle1: %s\nTitle2: %s\nGreaterThanOrEqualTo threshold", highestTitle1, highestTitle2)
				.isLessThan(ComparatorService.TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS);
		softAssertions.assertAll();
	}

	/*
	 * FIXME: Should ther be 2 similarity scores, one for the first title (jwPositiveTest) and one for all titles (jwFullPositiveTest).
	 * FIXME: tests for Phase titles compare to nrmal threshold
	 */
	// @formatter:off
	static Stream<Arguments> positiveArgumentProvider() {
		return Stream.of(
				arguments(
						"Comments about Glisson's capsule phleboliths and portal vein thrombosis [1]",
						"COMMENTS ABOUT GLISSON CAPSULE PHLEBOLITHS AND PORTAL-VEIN THROMBOSIS", 
						0.93), // error "'s"
				arguments(
						"PORTAL VENOUS THROMBOSIS FOLLOWING SPELENECTOMY IN PORTAL-HYPERTENSION - RISKS AND MANAGEMENT",
						"PORTAL VENOUS THROMBOSIS FOLLOWING SPELENECTOMY IN PORTAL-HYPERTENSION - RISKS AND MANAGEMENT - REPLY",
						0.99),
				arguments(
						"P2X7-regulated protection from exacerbations and loss of control is independent of asthma maintenance therapy",
						"P2X<inf>7</inf>-regulated protection from exacerbations and loss of control is independent of asthma maintenance therapy",
						1.0), // "<...>" replaced by nought
				arguments(
						"Combination therapy of long-acting beta2-adrenoceptor agonists and corticosteroids for asthma",
						"Combination therapy and long-acting beta<inf>2</inf>-adrenoceptor agonists and corticosteroids for asthma",
						0.92), // "<...>" replaced by nought
				arguments("Beta 1- and beta 2-adrenoceptor polymorphisms and cardiovascular diseases",
						"beta<inf>1</inf>- and beta<inf>2</inf>-Adrenoceptor polymorphisms and cardiovascular diseases",
						0.91), // "<...>" replaced by nought
				arguments(
						"Transarterial chemoembolization and <sup>90</sup>y radioembolization for hepatocellular carcinoma: Review of current applications beyond intermediate-stage disease",
						"Transarterial Chemoembolization and Y-90 Radioembolization for Hepatocellular Carcinoma: Review of Current Applications Beyond Intermediate-Stage Disease",
						1.0), // "<...>" replaced by nought: "y" vs "y 90"
				arguments(
						"Letter: portal vein obstruction--which subset of patients could benefit the most? Authors' reply",
						"Letter: Portal vein obstruction - Which subset of patients could benefit the most?", 0.91), // bibliographic
																														// addition
				arguments(
						"90Y radioembolization using resin microspheres in patients with hepatocellular carcinoma and portal vein thrombosis",
						"90Y RADIOEMBOLIZATION USING RESIN MICROSPHERES IN PATIENTS WITH HEPATOCELLULAR CARCINOMA AND PORTAL VEIN THROMBOSIS",
						1.0), // case difference
				arguments("Post Splenectomy Outcome in beta-Thalassemia", 
						"Post Splenectomy Outcome in β-Thalassemia",
						0.96), // Greek characters vs transcription
				arguments(
						"Epidemiology and diagnosis profile of digestive cancer in teaching hospital campus of lome: About 250 cases. [French]",
						"Epidemiology and diagnosis profile of digestive cancer in teaching Hospital Campus of Lome: about 250 cases",
						1.0), // bibliographic addition in "[...]"
				// several bibliographic additions in "[...]", but still many differences at the end.
				// The first argument is NOT an erratum / the addition within square brackets should be removed?
				arguments(
						"Increased risk of asthma attacks and emergency visits among asthma patients with allergic rhinitis: a subgroup analysis of the investigation of montelukast as a partner agent for complementary therapy [corrected].[Erratum appears in Clin Exp Allergy. 2006 Feb;36(2):249]",
						"Increased risk of asthma attacks and emergency visits among asthma patients with allergic rhinitis: A subgroup analysis of the improving asthma control trial",
						1.0), // because main title is identical
				arguments(
						"[The efficacy of half of the Global Initiative for Asthma recommended dose of inhaled corticosteroids in the management of Chinese asthmatics]",
						"The efficacy of half of the Global Initiative for Asthma recommended dose of inhaled corticosteroids in the management of Chinese asthmatics. [Chinese]",
						1.0), // "[" at start doesn't delete rest
				arguments(
						"A novel subset of CD4(+) T(H)2 memory/effector cells that produce inflammatory IL-17 cytokine and promote the exacerbation of chronic allergic asthma",
						"A novel subset of CD4<sup>+</sup> T<inf>H</inf>2 memory/ effector cells that produce inflammatory IL-17 cytokine and promote the exacerbation of chronic allergic asthma",
						1.00),
				arguments(revertString(
						"NFkappaB inhibition decreases hepatocyte proliferation but does not alter apoptosis in obstructive jaundice"),
						revertString(
								"NF kappa B inhibition decreases hepatocyte proliferation but does not alter apoptosis in obstructive jaundice"),
						0.996),
				arguments(revertString("Case report. Duplication of the portal vein: a rare congenital anomaly"),
						revertString("Duplication of the portal vein - A rare congenital anomaly"), 
						0.96),
				arguments(revertString(
						"La sémantique de l'image radiologique. Intérêt du procédé de soustraction électronique en couleurs d'Oosterkamp en angiographie abdominale"),
						revertString(
								"INTERET DU PROCEDE DE SOUSTRACTION ELECTRONIQUE EN COULEURS D'OOSTERKAMP EN ANGIOGRAPHIE ABDOMINALE"),
						0.94),
				arguments(revertString(
						"La sémantique de l'image radiologique. Intérêt du procédé de soustraction électronique en couleurs d'Oosterkamp en angiographie abdominale"),
						revertString(
								"INTERET DU PROCEDE DE SOUSTRACTION ELECTRONIQUE EN COULEURS D'OOSTERKAMP EN ANGIOGRAPHIE ABDOMINALE"),
						0.94),
				arguments(
						", the Italian survival calculator to optimize donor to recipient matching and to identify the unsustainable matches in liver transplantation",
						"The Italian survival calculator to optimize donor to recipient matching and to identify the unsustainable matches in liver transplantation",
						1.0),
				arguments(revertString("The JAK2 46/1 haplotype in Budd-Chiari syndrome and portal vein thrombosis"),
						revertString(
								"JAK2 Germline Genetic Variation In Budd-Chiari Syndrome and Portal Vein Thrombosis"),
						0.91),
				arguments("Is homozygous a-thalassaemia a lethal condition in the 1990s?",
						"Is homozygous alpha-thalassaemia a lethal condition in the 1990s?", 
						0.93),
				arguments(
						"¹⁸F-FDG PET metabolic parameters and MRI perfusion and diffusion parameters in hepatocellular carcinoma: a preliminary study",
						"18F-FDG PET Metabolic Parameters and MRI Perfusion and Diffusion Parameters in Hepatocellular Carcinoma: A Preliminary Study",
						1.0), // superscript numbers
				arguments(
						"F-18-FDG PET Metabolic Parameters and MRI Perfusion and Diffusion Parameters in Hepatocellular Carcinoma: A Preliminary Study",
						"18F-FDG PET Metabolic Parameters and MRI Perfusion and Diffusion Parameters in Hepatocellular Carcinoma: A Preliminary Study",
						1.0), // variant chemical notations
				arguments(
						"(90)Y Radioembolization for Locally Advanced Hepatocellular Carcinoma with Portal Vein Thrombosis: Long-Term Outcomes in a 185-Patient Cohort",
						"Y-90 Radioembolization for Locally Advanced Hepatocellular Carcinoma with Portal Vein Thrombosis: Long-Term Outcomes in a 185-Patient Cohort",
						1.0), // variant chemical notations
				arguments("Isolated portal vein thrombosis: An exceptional complication of chronic pancreatitis",
						"ISOLATED PORTAL-VEIN THROMBOSIS - AN EXCEPTIONAL COMPLICATION OF CHRONIC-PANCREATITIS", 
						0.94),
				arguments(revertString("Complication-based learning curve in laparoscopic sleeve gastrectomy"),
						revertString("Complications of laparoscopic sleeve gastrectomy"), 
						0.90), // example of FP
				arguments(
					"Case records of the Massachusetts General Hospital. Case 35-2007. A 30-year-old man with inflammatory bowel disease and recent onset of fever and bloody diarrhea",
					"Case 35-2007: A 30-year-old man with inflammatory bowel disease and recent onset of fever and bloody diarrhea",
					1.0),
				arguments(revertString(
						"Case records of the Massachusetts General Hospital. Case 35-2007. A 30-year-old man with inflammatory bowel disease and recent onset of fever and bloody diarrhea"),
						revertString(
								"Case 35-2007: A 30-year-old man with inflammatory bowel disease and recent onset of fever and bloody diarrhea"),
						0.93),
				arguments("Timing of chest tube removal after coronary artery bypass surgery",
						"\"Timing of chest tube removal after coronary artery bypass surgery.[Erratum appears in J Card Surg. 2011 Mar;26(2):244 Note: Yeshaaiahu, Michal [corrected to Yeshayahu, Michal]]\"",
						1.0),
				arguments("Psilocybin for the Treatment of Cluster Headache",
						"Psilocybin for the Treatment of Migraine Headache", 
						0.9454),
				arguments(revertString("Psilocybin for the Treatment of Cluster Headache"),
						revertString("Psilocybin for the Treatment of Migraine Headache"), 
						0.91),
				/*
				 * Example 1 of a False Positive: Phase I and Phase I/II trial
				 */
				arguments(
						"Phase II trial of ixabepilone (IXA) and dasatinib (D) for treatment of metastatic breast cancer (MBC)",
						"Phase I/II trial of ixabepilone (Ixa) and dasatinib (D) for treatment of metastatic breast cancer (MBC)",
						0.9127), // example of False Positive
				arguments(revertString(
						"Phase II trial of ixabepilone (IXA) and dasatinib (D) for treatment of metastatic breast cancer (MBC)"),
						revertString(
								"Phase I/II trial of ixabepilone (Ixa) and dasatinib (D) for treatment of metastatic breast cancer (MBC)"),
						0.9958), // example of False Positive
				// arguments(
				// "Phase two trial of ixabepilone (IXA) and dasatinib (D) for treatment
				// of metastatic breast cancer (MBC)",
				// "Phase one/two trial of ixabepilone (Ixa) and dasatinib (D) for
				// treatment of metastatic breast cancer (MBC)",
				// 0.9128), // example of False Positive, with "I" and "II" translated
				// arguments(
				// revertString("Phase one trial of ixabepilone (IXA) and dasatinib (D)
				// for treatment of metastatic breast cancer (MBC)"),
				// revertString("Phase one/two trial of ixabepilone (Ixa) and dasatinib
				// (D) for treatment of metastatic breast cancer (MBC)"),
				// 0.9878), // example of False Positive, with "I" and "II" translated
				// arguments(
				// "Phase one one one trial of ixabepilone (IXA) and dasatinib (D) for
				// treatment of metastatic breast cancer (MBC)",
				// "Phase one one one/two two two trial of ixabepilone (Ixa) and dasatinib
				// (D) for treatment of metastatic breast cancer (MBC)",
				// 0.9052), // example of False Positive, with "I" and "II" translated and
				// tripled
				// arguments(
				// revertString("Phase one one one trial of ixabepilone (IXA) and
				// dasatinib (D) for treatment of metastatic breast cancer (MBC)"),
				// revertString("Phase one one one/two two two trial of ixabepilone (Ixa)
				// and dasatinib (D) for treatment of metastatic breast cancer (MBC)"),
				// 0.9716), // example of False Positive, with "I" and "II" translated and
				// tripled
				// arguments(
				// revertString("Phase one one one one trial of ixabepilone (IXA) and
				// dasatinib (D) for treatment of metastatic breast cancer (MBC)"),
				// revertString("Phase one one one one/two two two two trial of
				// ixabepilone (Ixa) and dasatinib (D) for treatment of metastatic breast
				// cancer (MBC)"),
				// 0.9668), // example of False Positive, with "I" and "II" translated and
				// quadrupled
				// arguments(
				// revertString("Phase one Phase one Phase one Phase one trial of
				// ixabepilone (IXA) and dasatinib (D) for treatment of metastatic breast
				// cancer (MBC)"),
				// revertString("Phase one Phase one Phase one Phase one/two two two two
				// trial of ixabepilone (Ixa) and dasatinib (D) for treatment of
				// metastatic breast cancer (MBC)"),
				// 0.9537), // example of False Positive, with "I" and "II" translated and
				// quadrupled with phase repeated
				// arguments(
				// revertString("Phase one Phase one Phase one Phase one trial of
				// ixabepilone (IXA) and dasatinib (D) for treatment of metastatic breast
				// cancer (MBC)"),
				// revertString("Phase one/two Phase one/two Phase one/two Phase one/twoof
				// ixabepilone (Ixa) and dasatinib (D) for treatment of metastatic breast
				// cancer (MBC)"),
				// 0.9416), // example of False Positive, with "I" and "II" translated and
				// quadrupled with phase and next word repeated
				// arguments(
				// "Phase one Phase one Phase one Phase one trial of ixabepilone (IXA) and
				// dasatinib (D) for treatment of metastatic breast cancer (MBC)",
				// "Phase one/two Phase one/two Phase one/two Phase one/twoof ixabepilone
				// (Ixa) and dasatinib (D) for treatment of metastatic breast cancer
				// (MBC)",
				// 0.8949), // example of False Positive, with "I" and "II" translated and
				// quadrupled with phase and next word repeated
				// arguments(
				// revertString("Phase oneoneoneone trial of ixabepilone (IXA) and
				// dasatinib (D) for treatment of metastatic breast cancer (MBC)"),
				// revertString("Phase oneoneoneone/twotwotwotwo trial of ixabepilone
				// (Ixa) and dasatinib (D) for treatment of metastatic breast cancer
				// (MBC)"),
				// 0.9684), // example of False Positive, with "I" and "II" translated and
				// quadrupled as 1 word
				arguments(
						revertString("Six-year (yr) follow-up of patients (pts) with imatinib-resistant or -intolerant chronic-phase chronic myeloid leukemia (CML-CP) receiving dasatinib"),
						revertString("Five-year follow-up of patients with imatinib-resistant or -intolerant chronic-phase chronic myeloid leukemia (CML-CP) receiving dasatinib"),
						0.9656), // example of False Positive: difference at the start
				arguments(
						"A phase 3 study of durvalumab with or without bevacizumab as adjuvant therapy in patients with hepatocellular carcinoma at high risk of recurrence after curative hepatic resection or ablation: EMERALD-2",
						"A phase 3 study of durvalumab with or without bevacizumab as adjuvant therapy in patients with hepatocellular carcinoma (HCC) who are at high risk of recurrence after curative hepatic resection",
						0.9474), // example of False Positive: difference at the end
				arguments(
						"What can psychoanalysis contribute to the current refugee crisis?: Preliminary reports from STEP-BY-STEP: A psychoanalytic pilot project for supporting refugees in a \"first reception camp\" and crisis interventions with traumatized refugees",
						"What can psychoanalysis contribute to the current refugee crisis?", 
						1.0),
				arguments(
						"Phase 2 open-label study of single-agent sorafenib in treating advanced hepatocellular carcinoma in a hepatitis B-endemic Asian population: presence of lung metastasis predicts poor response",
						"Phase 2 open-label study of single-agent sorafenib in treating advanced hepatocellular carcinoma in a hepatitis B-endemic Asian population",
						1.0),
				arguments(
					revertString("<<Except for the war's laws>>. Psychic trauma in soldiers murderers. French"),
					revertString("Except for the war's laws. Psychic trauma in soldiers murderers. French"), 
					1.0),
				arguments(
					"Was ist mit der Pfortader? Idiopathic phlethrombosis.", 
					"Was ist mit der pfortader?", 
					0.89),
				arguments(
					"RETRACTED: Evaluation of the treatment strategies on patient-derived xenograft mice of human breast tumor (Retracted Article)",
					"Evaluation of the treatment strategies on patient-derived xenograft mice of human breast tumor",
					1.0
				), // WOS retraction marking without full source
				arguments(
					"RETRACTED: Response of Breast Cancer Cells and Cancer Stem Cells to Metformin and Hyperthermia Alone or Combined (Retracted article. See vol. 20, 2025)",
					"Response of Breast Cancer Cells and Cancer Stem Cells to Metformin and Hyperthermia Alone or Combined",
					1.0
				), // WOS retraction marking with full source
				arguments(
					"RETRACTED: Isolated central retinal artery occlusion as an initial presentation of paroxysmal nocturnal hemoglobinuria and successful long-term prevention of systemic thrombosis with eculizumab (Retracted article. See vol. 58, pg. 307, 2014)",
					"Isolated central retinal artery occlusion as an initial presentation of paroxysmal nocturnal hemoglobinuria and successful long-term prevention of systemic thrombosis with eculizumab",
					1.0
				) // WOS retraction marking with full source
			);
	}
	// @formatter:on

	// @formatter:off
	static Stream<Arguments> negativeArgumentProvider() {
		return Stream.of(
				arguments(
						"The use of TIPS should be cautious in noncirrhotic patients with obliterative portal vein thrombosis",
						"The significance of nonobstructive sinusoidal dilatation of the liver: Impaired portal perfusion or inflammatory reaction syndrome",
						0.71), // unexpectedly high!
				arguments(
					"[Elimination of airborne allergens from the household environment]",
					"Eviction of airborne allergens for the household environment. [French]", 
					0.83), // different translations
				arguments(
					"[Elimination of airborne allergens from the household environment]",
					"Eviction of airborne allergens for the household environment", 
					0.83), // different translations
				arguments(
					"[Various aspects of respiratory emergencies in non-hospital practice]",
					"Some aspects of respiratory emergencies in non-hospital practice. [French]", 
					0.81), // different translations
				arguments(
						"NFkappaB inhibition decreases hepatocyte proliferation but does not alter apoptosis in obstructive jaundice",
						"NF kappa B inhibition decreases hepatocyte proliferation but does not alter apoptosis in obstructive jaundice",
						0.88), // heavy penalty on differences at start
				arguments(
					"Case report. Duplication of the portal vein: a rare congenital anomaly",
					"Duplication of the portal vein - A rare congenital anomaly", 
					0.79),
				arguments(
					"La sémantique de l'image radiologique. Intérêt du procédé de soustraction électronique en couleurs d'Oosterkamp en angiographie abdominale",
					"INTERET DU PROCEDE DE SOUSTRACTION ELECTRONIQUE EN COULEURS D'OOSTERKAMP EN ANGIOGRAPHIE ABDOMINALE",
					0.75),
				arguments(
					"The JAK2 46/1 haplotype in Budd-Chiari syndrome and portal vein thrombosis",
					"JAK2 Germline Genetic Variation In Budd-Chiari Syndrome and Portal Vein Thrombosis", 
					0.85),
				arguments(
					"Retraction notice to \"Evaluation of the treatment strategies on patient-derived xenograft mice of human breast tumor\" [Eur. J. Pharmacol. 889 (2020) 173605]",
					"Evaluation of the treatment strategies on patient-derived xenograft mice of human breast tumor",
					0.78
				), // Publication and separate rectraction notice (PubMed)
				arguments(
					"90 Y radioembolization for locally advanced hepatocellular carcinoma with portal vein thrombosis: Long-term outcomes in a 185-patient cohort",
					"Y-90 Radioembolization for Locally Advanced Hepatocellular Carcinoma with Portal Vein Thrombosis: Long-Term Outcomes in a 185-Patient Cohort",
					0.84), // just because of the space
				arguments(
					"Complication-based learning curve in laparoscopic sleeve gastrectomy",
					"Complications of laparoscopic sleeve gastrectomy", 
					0.87), // example of  False  Positive
				/*
				 * Example 1 of a False Positive: Phase I and Phase I/II trial
				 */
				// arguments(
				// "Phase one Phase one Phase one Phase one trial of ixabepilone (IXA) and
				// dasatinib (D) for treatment of metastatic breast cancer (MBC)",
				// "Phase one/two Phase one/two Phase one/two Phase one/twoof ixabepilone
				// (Ixa) and dasatinib (D) for treatment of metastatic breast cancer
				// (MBC)",
				// 0.8949), // example of False Positive, with "I" and "II" translated and
				// quadrupled with phase and next word repeated
				/*
				 * Example 2 of a False Positive: Difference at the start: Five years ...
				 * / Six year ...
				 */
				arguments(
					"Six-year (yr) follow-up of patients (pts) with imatinib-resistant or -intolerant chronic-phase chronic myeloid leukemia (CML-CP) receiving dasatinib",
					"Five-year follow-up of patients with imatinib-resistant or -intolerant chronic-phase chronic myeloid leukemia (CML-CP) receiving dasatinib",
					0.81), // example of False Positive
				/*
				 * Example 3 of a False Positive: Difference at the end
				 */
				arguments(
					revertString("A phase 3 study of durvalumab with or without bevacizumab as adjuvant therapy in patients with hepatocellular carcinoma at high risk of recurrence after curative hepatic resection or ablation: EMERALD-2"),
						revertString("A phase 3 study of durvalumab with or without bevacizumab as adjuvant therapy in patients with hepatocellular carcinoma (HCC) who are at high risk of recurrence after curative hepatic resection"),
						0.81), // example of False Positive
				arguments(
					"<<Except for the war's laws>>. Psychic trauma in soldiers murderers. French",
					"Except for the war's laws. Psychic trauma in soldiers murderers. French", 
					0.71)
		);
	}
	// @formatter:on

	/*
	 * FIXME: This is far from complete. See comment in IOService (above erratumPattern) with the examples of errata
	 * WITHOUT words as erratum / correction / corrigendum in the title.
	 * See https://github.com/globbestael/DedupEndNote/issues/32
	 */
	@Test
	void testErrata() {
		// https://stackoverflow.com/questions/47162098/is-it-possible-to-match-nested-brackets-with-a-regex-without-using-recursion-or/47162099#47162099
		String s = "Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a subgroup of children with atypical hemolytic uremic syndrome (vol 142, pg 310, 2003)";
		Pattern pattern = Pattern.compile(
				"(?=\\()(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^(]*(?=\\2$)");
		Matcher matcher = pattern.matcher(s);
		while (matcher.find()) {
			System.err.println("Found: " + matcher.group(0) + "\t ends at " + matcher.end(0) + " in string with length "
					+ s.length());
		}
		assertThat(1 * 1).isEqualTo(1);
	}

	@Test
	void testErrataFromFile() throws IOException {
		String fileName = testdir + "/all/all__ST_TI_ending_with_round_bracket.txt";
		Path path = Path.of(fileName);
		List<String> lines = Files.readAllLines(path);

		List<String> results = new ArrayList<>();

		for (String line : lines) {
			Matcher matcher = IOService.sourcePattern.matcher(line);
			if (matcher.matches()) {
				System.err.println("\t- " + matcher.group(1));
				results.add(matcher.group(1));
			}
		}

		assertThat(results).as("There are more than 10 results").hasSizeGreaterThan(10);
		assertThat(lines).as("There are more than 100 lines").hasSizeGreaterThan(100);
	}

	@Disabled("While refactoring")
	@Test
	void testPositiveCommentsFromFile() throws IOException {
		String fileName = testdir + "/all/All__comment__positive_examples.txt";
		Path path = Path.of(fileName);
		List<String> lines = Files.readAllLines(path);

		List<String> negativeResults = new ArrayList<>();
		List<String> positiveResults = new ArrayList<>();

		for (String line : lines) {
			Matcher matcher = IOService.commentPattern.matcher(line);
			if (matcher.matches()) {
				System.err.println("- Positive comment caught: " + line);
				positiveResults.add(line);
			} else {
				System.err.println("- Negative comment passed: " + line);
				negativeResults.add(line);
			}
		}

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(negativeResults)
				.as("There are positive examples which are NOT caught as normal comments").hasSize(0);
		softAssertions.assertThat((100 * positiveResults.size()) / lines.size())
				.as("Only " + (100 * positiveResults.size()) / lines.size() + "% of positive cases caught")
				.isEqualTo(100);
		softAssertions.assertAll();
	}

	@Disabled("While refactoring")
	@Test
	void testNegativeCommentsFromFile() throws IOException {
		String fileName = testdir + "/all/All__comment__negative_examples.txt";
		Path path = Path.of(fileName);
		List<String> lines = Files.readAllLines(path);

		List<String> negativeResults = new ArrayList<>();

		for (String line : lines) {
			Matcher matcher = IOService.commentPattern.matcher(line);
			if (matcher.matches()) {
				System.err.println("- Negative comment passed: " + line);
				negativeResults.add(line);
			}
		}

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(negativeResults).as("There negative examples are caught as normal comments results")
				.hasSize(0);
		softAssertions.assertThat((100 * negativeResults.size()) / lines.size())
				.as((100 * negativeResults.size()) / lines.size() + "% of negative cases are not caught").isEqualTo(0);
		softAssertions.assertAll();
	}

	@Disabled("While refactoring")
	@Test
	void testPositiveCommentsAndRepliesFromFile() throws IOException {
		String fileName = testdir + "/all/All__comment_AND_reply__positive_examples.txt";
		Path path = Path.of(fileName);
		List<String> lines = Files.readAllLines(path);

		List<String> negativeResults = new ArrayList<>();
		List<String> positiveResults = new ArrayList<>();

		for (String line : lines) {
			Matcher matcher = IOService.commentPattern.matcher(line);
			if (matcher.matches()) {
				System.err.println("- Positive comment caught: " + line);
				positiveResults.add(line);
			} else {
				System.err.println("- Negative comment passed: " + line);
				negativeResults.add(line);
			}
		}

		SoftAssertions softAssertions = new SoftAssertions();
		softAssertions.assertThat(negativeResults)
				.as("There are positive examples which are NOT caught as normal comments").hasSize(0);
		softAssertions.assertThat((100 * positiveResults.size()) / lines.size())
				.as("Only " + (100 * positiveResults.size()) / lines.size() + "% of positive cases caught")
				.isEqualTo(100);
		softAssertions.assertAll();
	}

	@Test
	void testTitleSplitter() {
		Publication publication = new Publication();
		String t1 = "Severe deficiency of the specific von Willebrand factor-cleaving protease";
		String t2 = "ADAMTS 13 activity in a subgroup of children with atypical hemolytic uremic syndrome";
		publication.addTitles(t1 + ": " + t2, normalizationService);
		List<String> titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).hasSize(3);

		publication.getTitles().clear();
		publication.addTitles(t1.substring(0, 10) + ": " + t2, normalizationService);
		titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).as("First part smaller than 50, no split").hasSize(1);

		publication.getTitles().clear();
		publication.addTitles(t1 + ": " + t2.substring(0, 10), normalizationService);
		titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).as("Second part smaller than 50, no split").hasSize(1);

		publication.getTitles().clear();
		publication.addTitles(t1.substring(0, 10) + ": " + t2.substring(0, 10), normalizationService);
		titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).as("Both parts smaller than 50, no split").hasSize(1);

		publication.getTitles().clear();
		publication.addTitles(t1 + ": " + t2.substring(0, 10) + ": " + t2.substring(11), normalizationService);
		titles = publication.getTitles();

		System.err.println(titles);
		assertThat(titles).as("Second part has embedded colon").hasSize(3);

	}
}