package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Publication;
import edu.dedupendnote.services.DeduplicationService;

//@Slf4j
//@ExtendWith(TimingExtension.class)
@TestConfiguration
public class JaroWinklerTitleTest {

	JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("positiveArgumentProvider")
	void jwPositiveTest(String input1, String input2, double expected) {
		Double distance = jws.apply(Publication.normalizeJava8(input1), Publication.normalizeJava8(input2));
		System.err.println(String.format("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n", input1,
				Publication.normalizeJava8(input1), input2, Publication.normalizeJava8(input2)));
		assertThat(distance).isEqualTo(expected, within(0.01));
		assertThat(distance)
			.isGreaterThanOrEqualTo(DeduplicationService.TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS);
	}

	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("positiveArgumentProvider")
	void jwFullPositiveTest(String input1, String input2, double expected) {
		Publication p1 = new Publication(); p1.addTitles(input1);
		Publication p2 = new Publication(); p2.addTitles(input2);

		Double highestDistance = 0.0;

		for (String title1 : p1.getTitles()) {
			for (String title2 : p2.getTitles()) {
				Double distance = jws.apply(title1, title2);
				if (distance > highestDistance) {
					highestDistance = distance;
				}
			}
		}

		assertThat(highestDistance)
			.as("\nTitle1: %s\nTitle2: %s", p1.getTitles().get(0), p2.getTitles().get(0))
			.isEqualTo(highestDistance, within(0.01))
			.isGreaterThanOrEqualTo(DeduplicationService.TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS);
	}

	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("negativeArgumentProvider")
	void jwNegativeTest(String input1, String input2, double expected) {
		Double distance = jws.apply(Publication.normalizeJava8(input1), Publication.normalizeJava8(input2));
		System.err.println(String.format("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n", input1,
				Publication.normalizeJava8(input1), input2, Publication.normalizeJava8(input2)));
		assertThat(distance).isEqualTo(expected, within(0.01));
		assertThat(distance).isLessThan(DeduplicationService.TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS);
	}

	static String revertString(String s) {
		return new StringBuilder(s).reverse().toString();
	}

	static Stream<Arguments> positiveArgumentProvider() {
		return Stream.of(
				arguments("Comments about Glisson's capsule phleboliths and portal vein thrombosis [1]",
						"COMMENTS ABOUT GLISSON CAPSULE PHLEBOLITHS AND PORTAL-VEIN THROMBOSIS", 0.93), // error
																										// "'s"
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
				arguments("Post Splenectomy Outcome in beta-Thalassemia", "Post Splenectomy Outcome in β-Thalassemia",
						0.96), // Greek characters vs transcription
				arguments(
						"Epidemiology and diagnosis profile of digestive cancer in teaching hospital campus of lome: About 250 cases. [French]",
						"Epidemiology and diagnosis profile of digestive cancer in teaching Hospital Campus of Lome: about 250 cases",
						1.0), // bibliographic addition in "[...]"
				arguments(
						"Increased risk of asthma attacks and emergency visits among asthma patients with allergic rhinitis: a subgroup analysis of the investigation of montelukast as a partner agent for complementary therapy [corrected].[Erratum appears in Clin Exp Allergy. 2006 Feb;36(2):249]",
						"Increased risk of asthma attacks and emergency visits among asthma patients with allergic rhinitis: A subgroup analysis of the improving asthma control trial",
						0.94), // unexpected!
				// several bibliographic additions in "[...]", but still many differences
				// at the end
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
						revertString("Duplication of the portal vein - A rare congenital anomaly"), 0.96),
				arguments(revertString(
						"La sémantique de l'image radiologique. Intérêt du procédé de soustraction électronique en couleurs d'Oosterkamp en angiographie abdominale"),
						revertString(
								"INTERET DU PROCEDE DE SOUSTRACTION ELECTRONIQUE EN COULEURS D'OOSTERKAMP EN ANGIOGRAPHIE ABDOMINALE"),
						0.91),
				arguments(revertString(
						"La sémantique de l'image radiologique. Intérêt du procédé de soustraction électronique en couleurs d'Oosterkamp en angiographie abdominale"),
						revertString(
								"INTERET DU PROCEDE DE SOUSTRACTION ELECTRONIQUE EN COULEURS D'OOSTERKAMP EN ANGIOGRAPHIE ABDOMINALE"),
						0.91),
				arguments(
						", the Italian survival calculator to optimize donor to recipient matching and to identify the unsustainable matches in liver transplantation",
						"The Italian survival calculator to optimize donor to recipient matching and to identify the unsustainable matches in liver transplantation",
						1.0),
				arguments(revertString("The JAK2 46/1 haplotype in Budd-Chiari syndrome and portal vein thrombosis"),
						revertString(
								"JAK2 Germline Genetic Variation In Budd-Chiari Syndrome and Portal Vein Thrombosis"),
						0.91),
				arguments(
						"Erratum: Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a sub-group of children with atypical hemolytic uremic syndrome (The Journal of Pediatrics (M",
						"Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a subgroup of children with atypical hemolytic uremic syndrome (vol 142, pg 310, 2003)",
						0.96), // FIXME: treat Erratum. !!! The first string does not end
								// with a balanced group of round braces !!!
				arguments( // Erratum, without ending brace-group deleted, but starting
							// "Erratum: " deleted
						"Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a sub-group of children with atypical hemolytic uremic syndrome (The Journal of Pediatrics (M",
						"Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a subgroup of children with atypical hemolytic uremic syndrome (vol 142, pg 310, 2003)",
						0.9618), // FIXME: treat Erratum
				arguments( // Erratum, with ending brace-group deleted only when starting
							// with "Erratum: ", and starting "Erratum: " deleted
						"Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a sub-group of children with atypical hemolytic uremic syndrome",
						"Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a subgroup of children with atypical hemolytic uremic syndrome (vol 142, pg 310, 2003)",
						0.98), // FIXME: treat Erratum
				arguments( // Erratum, with ending brace-group deleted
						"Erratum: Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a sub-group of children with atypical hemolytic uremic syndrome",
						"Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a subgroup of children with atypical hemolytic uremic syndrome",
						1.0),
				arguments( // Erratum, with ending brace-group deleted, and starting
							// "Erratum: " deleted
						"Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a sub-group of children with atypical hemolytic uremic syndrome",
						"Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a subgroup of children with atypical hemolytic uremic syndrome",
						1.0),
				arguments(
						"Erratum to Low Alpha-Fetoprotein Levels Are Associated with Improved Survival in Hepatocellular Carcinoma Patients with Portal Vein Thrombosis (Dig Dis Sci, DOI 10.1007/s10620-015-3922-3)",
						"Erratum to: Low Alpha-Fetoprotein Levels Are Associated with Improved Survival in Hepatocellular Carcinoma Patients with Portal Vein Thrombosis",
						1.0),
				arguments("Is homozygous a-thalassaemia a lethal condition in the 1990s?",
						"Is homozygous alpha-thalassaemia a lethal condition in the 1990s?", 0.93),
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
						"ISOLATED PORTAL-VEIN THROMBOSIS - AN EXCEPTIONAL COMPLICATION OF CHRONIC-PANCREATITIS", 0.94),
				arguments(revertString("Complication-based learning curve in laparoscopic sleeve gastrectomy"),
						revertString("Complications of laparoscopic sleeve gastrectomy"), 0.90), // example
																									// of
																									// False
																									// Positive
				arguments(revertString(
						"Case records of the Massachusetts General Hospital. Case 35-2007. A 30-year-old man with inflammatory bowel disease and recent onset of fever and bloody diarrhea"),
						revertString(
								"Case 35-2007: A 30-year-old man with inflammatory bowel disease and recent onset of fever and bloody diarrhea"),
						0.93),
				arguments("Timing of chest tube removal after coronary artery bypass surgery",
						"\"Timing of chest tube removal after coronary artery bypass surgery.[Erratum appears in J Card Surg. 2011 Mar;26(2):244 Note: Yeshaaiahu, Michal [corrected to Yeshayahu, Michal]]\"",
						1.0),
				arguments("Psilocybin for the Treatment of Cluster Headache",
						"Psilocybin for the Treatment of Migraine Headache", 0.9454),
				arguments(revertString("Psilocybin for the Treatment of Cluster Headache"),
						revertString("Psilocybin for the Treatment of Migraine Headache"), 0.91),
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
				arguments(revertString(
						"Six-year (yr) follow-up of patients (pts) with imatinib-resistant or -intolerant chronic-phase chronic myeloid leukemia (CML-CP) receiving dasatinib"),
						revertString(
								"Five-year follow-up of patients with imatinib-resistant or -intolerant chronic-phase chronic myeloid leukemia (CML-CP) receiving dasatinib"),
						0.9656), // example of False Positive: difference at the start
				arguments(
						"A phase 3 study of durvalumab with or without bevacizumab as adjuvant therapy in patients with hepatocellular carcinoma at high risk of recurrence after curative hepatic resection or ablation: EMERALD-2",
						"A phase 3 study of durvalumab with or without bevacizumab as adjuvant therapy in patients with hepatocellular carcinoma (HCC) who are at high risk of recurrence after curative hepatic resection",
						0.9474), // example of False Positive: difference at the end
				arguments(
						"<<Except for the war's laws>>. Psychic trauma in soldiers murderers. French",
						"Except for the war's laws. Psychic trauma in soldiers murderers. French",
						1.0),
				arguments(
						"What can psychoanalysis contribute to the current refugee crisis?: Preliminary reports from STEP-BY-STEP: A psychoanalytic pilot project for supporting refugees in a \"first reception camp\" and crisis interventions with traumatized refugees",
						"What can psychoanalysis contribute to the current refugee crisis?",
						0.9)
				);
	}

	static Stream<Arguments> negativeArgumentProvider() {
		return Stream.of(arguments("British journal of surgery", "Br J Surg", 0.6), // not
																					// usable
																					// for
																					// some
																					// journal
																					// abbreviations
				arguments(
						"The use of TIPS should be cautious in noncirrhotic patients with obliterative portal vein thrombosis",
						"The significance of nonobstructive sinusoidal dilatation of the liver: Impaired portal perfusion or inflammatory reaction syndrome",
						0.71), // unexpectedly high!
				arguments("[Elimination of airborne allergens from the household environment]",
						"Eviction of airborne allergens for the household environment. [French]", 0.83), // different
																											// translations
				arguments("[Elimination of airborne allergens from the household environment]",
						"Eviction of airborne allergens for the household environment", 0.83), // different
																								// translations
				arguments("[Various aspects of respiratory emergencies in non-hospital practice]",
						"Some aspects of respiratory emergencies in non-hospital practice. [French]", 0.81), // different
																												// translations
				arguments(
						"NFkappaB inhibition decreases hepatocyte proliferation but does not alter apoptosis in obstructive jaundice",
						"NF kappa B inhibition decreases hepatocyte proliferation but does not alter apoptosis in obstructive jaundice",
						0.88), // heavy penalty on differences at start
				arguments("Case report. Duplication of the portal vein: a rare congenital anomaly",
						"Duplication of the portal vein - A rare congenital anomaly", 0.79),
				arguments(
						"La sémantique de l'image radiologique. Intérêt du procédé de soustraction électronique en couleurs d'Oosterkamp en angiographie abdominale",
						"INTERET DU PROCEDE DE SOUSTRACTION ELECTRONIQUE EN COULEURS D'OOSTERKAMP EN ANGIOGRAPHIE ABDOMINALE",
						0.75),
				arguments("The JAK2 46/1 haplotype in Budd-Chiari syndrome and portal vein thrombosis",
						"JAK2 Germline Genetic Variation In Budd-Chiari Syndrome and Portal Vein Thrombosis", 0.85),
				arguments(
						"RETRACTED: Isolated central retinal artery occlusion as an initial presentation of paroxysmal nocturnal hemoglobinuria and successful long-term prevention of systemic thrombosis with eculizumab (Retracted article. See vol. 58, pg. 307, 2014)",
						"Isolated central retinal artery occlusion as an initial presentation of paroxysmal nocturnal hemoglobinuria and successful long-term prevention of systemic thrombosis with eculizumab",
						0.77),
				arguments(revertString(
						"RETRACTED: Isolated central retinal artery occlusion as an initial presentation of paroxysmal nocturnal hemoglobinuria and successful long-term prevention of systemic thrombosis with eculizumab (Retracted article. See vol. 58, pg. 307, 2014)"),
						revertString(
								"Isolated central retinal artery occlusion as an initial presentation of paroxysmal nocturnal hemoglobinuria and successful long-term prevention of systemic thrombosis with eculizumab"),
						0.77),
				arguments("Was ist mit der Pfortader? Idiopathic phlethrombosis.", "Was ist mit der pfortader?", 0.898), // NOT
																															// >
																															// 0.90,
																															// will
																															// not
																															// be
																															// accepted
				arguments(revertString(
						"Erratum: Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a sub-group of children with atypical hemolytic uremic syndrome (The Journal of Pediatrics (M"),
						revertString(
								"Severe deficiency of the specific von Willebrand factor-cleaving protease (ADAMTS 13) activity in a subgroup of children with atypical hemolytic uremic syndrome (vol 142, pg 310, 2003)"),
						0.77), // FIXME: treat Erratum
				arguments(
						"90 Y radioembolization for locally advanced hepatocellular carcinoma with portal vein thrombosis: Long-term outcomes in a 185-patient cohort",
						"Y-90 Radioembolization for Locally Advanced Hepatocellular Carcinoma with Portal Vein Thrombosis: Long-Term Outcomes in a 185-Patient Cohort",
						0.84), // just because of the space
				arguments("Complication-based learning curve in laparoscopic sleeve gastrectomy",
						"Complications of laparoscopic sleeve gastrectomy", 0.87), // example
																					// of
																					// False
																					// Positive
				arguments(
						"Case records of the Massachusetts General Hospital. Case 35-2007. A 30-year-old man with inflammatory bowel disease and recent onset of fever and bloody diarrhea",
						"Case 35-2007: A 30-year-old man with inflammatory bowel disease and recent onset of fever and bloody diarrhea",
						0.84),
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
				arguments(revertString(
						"A phase 3 study of durvalumab with or without bevacizumab as adjuvant therapy in patients with hepatocellular carcinoma at high risk of recurrence after curative hepatic resection or ablation: EMERALD-2"),
						revertString(
								"A phase 3 study of durvalumab with or without bevacizumab as adjuvant therapy in patients with hepatocellular carcinoma (HCC) who are at high risk of recurrence after curative hepatic resection"),
						0.81) // example of False Positive
		);
	}

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
	}

}