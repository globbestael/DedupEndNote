	package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import com.github.difflib.text.DiffRow;
import com.github.difflib.text.DiffRowGenerator;

import edu.dedupendnote.domain.Publication;
import info.debatty.java.stringsimilarity.RatcliffObershelp;

//@Slf4j
//@ExtendWith(TimingExtension.class)
@TestConfiguration
class AbstracttextTest {

	// @formatter:off
	/*
	 * This test file tries to find a workable solution for comparing abstracts
	 * - JaroWinkler - JaroWinkler with cleaning of text
	 * - JaroWinkler with cleaning of text and limiting to first 200 characters
	 * - Ratcliff-Obershelp with the same kinds
	 *
	 * PRELIMINARY RESULTS: no good solution found.
	 */
	// @formatter:on

	static Stream<Arguments> negativeArgumentProvider() {
		return Stream.of(arguments(
				"to research retrospectively the efficacy of Erenumab's treatment, thus allowing to describe a summary more in line with the reality observed every day in clinical practice, relative to a sample of patients widely heterogeneous. The study aims to confirm the efficacy of Erenumab, in terms of reduction of migraine days per month, from baseline to month 12 of treatment. Additional objectives included a reduction in the number of days of symptomatic drug use and change from baseline in the Migraine Disability Assessment Score Questionnaire (MIDAS); Methods: the analysis included all patients treated for 12 months with Erenumab during the year 2019-2020. The population analyzed consists of twenty-six patients from the Neurology outpatient clinic in Fossombrone. Several quantitative and qualitative variables were recorded by reading the medical records of the patients. The MIDAS was administered to patients to assess the disability related to migraine; Results: at the end of treatment, a statistically significant reduction in the mean number of monthly migraine days, acute medication use per month, and MIDAS questionnaire score was observed; Conclusions: as a preventive treatment of episodic and chronic migraine, our analysis data confirm the efficacy of Erenumab for the prevention of the migraine. The success is achieved in 96% of cases.",
				"Migraine and epilepsy share several clinical features, and epilepsy is a comorbid condition of migraine. Clinical studies have shown that some antiepileptic drugs are effective at preventing migraine attacks. A rationale for their use in migraine prophylaxis is the hypothesis that migraine and epilepsy share several common pathogenetic mechanisms. An imbalance between excitatory glutamate-mediated transmission and GABA-mediated inhibition in specific brain areas has been postulated in these two pathological conditions. Moreover, abnormal activation of voltage-operated ionic channels has been implicated in both migraine and epilepsy. Cortical spreading depression has been found to be involved in the pathophysiology of epilepsy, in addition to the generation of migraine aura.",
				0.76),
				arguments(
						"Background: New treatment options are needed for patients with episodic migraine (EM) who fail to respond to migraine preventive medications. Fremanezumab, a fully humanized monoclonal antibody (IgG2Î”a) that selectively targets calcitonin gene‐related peptide (CGRP), is approved in the US for the preventive treatment of migraine in adults. Herein we evaluate the long‐term effect of fremanezumab on response rate, the use of acute headache medication, and disability in patients with EM who had failed to respond to at least one prior preventive migraine medication. Methods: In this 12‐month, multicenter, randomized, double‐blind, parallel‐group study, adults with EM received subcutaneous fremanezumab either quarterly (675 mg every 3 months) or monthly (225 mg monthly). This post hoc analysis was limited to EM patients who had failed at least one prior migraine preventive medication, with failure defined as lack of efficacy or intolerability. The proportions of patients achieving a â‰¥50% reduction in the monthly average number of migraine days and a â‰¥50% reduction in headache days of at least moderate severity, respectively, were measured at Month 6 and Month 12, as was the change from baseline in the monthly number of days with use of acute headache medications. The effect of fremanezumab on headache‐related disability was also assessed using the Migraine Disability Assessment (MIDAS) questionnaire. Results: Of the 206 EM patients who had failed at least one prior migraine preventive medication, 105 received quarterly dosing and 101 received monthly dosing of fremanezumab. The proportion of EM patients achieving a â‰¥50% reduction in the monthly number of migraine days was maintained at Months 6 (quarterly: 60%; monthly: 50%) and 12 (quarterly: 59%; monthly: 64%). A â‰¥50% reduction in the monthly number of headache days of at least moderate severity was reported by 61% and 54% of patients with quarterly and monthly dosing, respectively, at Month 6, and this reduction was maintained in both dosing groups at Month 12 (quarterly: 59%; monthly: 60%). The change from baseline to Month 6 in the mean monthly number of days with use of acute headache medications was ‐4.6 days with quarterly and ‐3.6 days with monthly dosing, and from baseline to Month 12 it was ‐4.7 days with quarterly and ‐4.5 days for monthly dosing. The mean change in MIDAS score from baseline to Month 6 was ‐28.4 for quarterly and ‐26.8 for monthly dosing, and from baseline to Month 12 it was ‐30.7 and ‐30.3 for fremanezumab quarterly and monthly dosing, respectively. Conclusion: These data demonstrate that long‐term treatment with fremanezumab maintained efficacy while reducing the use of acute headache medications and improving headache‐related disability in EM patients who had previously received at least one prior preventive migraine medication.",
						"Background: New treatment options are needed for patients with chronic migraine (CM) who fail to respond to migraine preventive medications. Fremanezumab, a fully humanized monoclonal antibody (IgG2Î”a) that selectively targets calcitonin gene‐related peptide (CGRP), is approved in the US for the preventive treatment of migraine in adults. Herein we evaluate the long‐term effect of fremanezumab on response rates, use of acute headache medication, and disability in patients with CM who had failed to respond at least one prior preventive migraine medication. Methods: In this 12‐month, multicenter, randomized, double‐blind, parallel‐group study, adults with CM received subcutaneous fremanezumab either quarterly (675 mg every 3 months) or monthly (225 mg every month with a starting dose of 675 mg). This post hoc analysis was limited to CM patients who had failed at least one prior migraine preventive medication, with failure defined as lack of efficacy or intolerability. The proportions of patients with a â‰¥50% reduction in the monthly number of migraine days and a â‰¥50% reduction in headache days of at least moderate severity, respectively, were measured at Month 6 and Month 12 in both fremanezumab dosing groups, as was the change from baseline in the monthly number of days with use of acute headache medications. The effect of fremanezumab on headache‐related disability was also assessed using the six‐Item Headache Impact Test (HIT‐6). Results: Of the 490 CM patients who had failed at least one prior migraine preventive medication, 247 received quarterly dosing and 243 received monthly dosing of fremanezumab. The proportion of patients with a â‰¥50% reduction in monthly migraine days was maintained at Months 6 (quarterly: 34%; monthly: 48%) and 12 (quarterly: 48%; monthly: 51%). A â‰¥50% reduction in the monthly number of headache days of at least moderate severity was reported by 40% and 51% of patients with quarterly and monthly dosing at Month 6, respectively, and this reduction was maintained in both dosing groups at Month 12 (quarterly: 48%; monthly: 52%). The change from baseline to Month 6 in the mean monthly number of days with use of acute headache medication was ‐4.7 days for quarterly and ‐6.2 days for monthly dosing, and from baseline to Month 12 it was ‐5.8 days for quarterly and ‐6.2 days for monthly dosing. The mean change from baseline to Month 6 in the HIT‐6 disability score was ‐5.5 for quarterly and ‐7.3 for monthly dosing, and from baseline to Month 12 it was ‐6.5 for quarterly and ‐8.0 for monthly dosing. Conclusion: These data demonstrate that long‐term treatment with fremanezumab maintains efficacy while reducing the use of acute headache medications and improving headache‐related disability for up to 1 year in CM patients who had failed at least one prior preventive migraine medication.",
						.957),
				arguments(
						"Background: For patients with migraine who do not demonstrate a robust initial response to migraine preventive treatments, clinicians must determine the duration of treatment persistence and timing for reassessment of treatment response. Erenumab (erenumab‐aooe in the US) is a fully human, anti‐calcitonin gene‐related peptide receptor antibody, approved for migraine prevention. While some patients experience onset of efficacy within the first week of treatment (Schwedt et al. J Headache Pain. 2018; 19:92), others respond later with continued treatment. The objective of this analysis was to evaluate the timing of response to continued erenumab treatment in patients with chronic migraine (CM). Methods: This was a post hoc analysis of data from a 3‐month, pivotal, randomized, double‐blind, placebo‐controlled study of erenumab 70 mg or 140 mg in patients with CM (NCT02066415). A â‰¥ 50% reduction from baseline in monthly migraine days (MMD) was used to define a response. We conducted time‐to‐ event analyses for patients who achieved a response to erenumab in any study month. An initial responder is defined as a patient who achieved a â‰¥ 50% reduction in MMD at month 1. For those who were not initial responders, we analyzed the likelihood of responding to erenumab in subsequent months. Patients who were not initial responders were classified as having â€œmodestâ€  (â‰¥ 30% to < 50%) or â€œno or limitedâ€  (< 30%) reductions in MMD. Results: The proportion of patients who achieved a response increased over time with continued treatment. Of the 188 and 187 patients in the 70 mg and 140 mg treatment groups, 24% (45/188) and 28% (53/187), respectively, were initial responders, having achieved a response at month 1; 57% and 54%, respectively, achieved a response at least once during the 3‐month study, with a median (IQR) time to first response of 2 (1, 2) and 1 (1, 2) months. Among patients who were not initial responders, 31% (45/143) in the 70 mg group and 25% (34/134) in the 140 mg group had achieved a â€œmodestâ€  reduction in MMD at month 1, and 69% (98/143) and 75% (100/134), respectively, had achieved a â€œno or limitedâ€  reduction in MMD at month 1. Among patients who were not initial responders, 44% (63/143) in the 70 mg group and 36% (48/134) in the 140 mg group achieved a response during the 2nd and/or 3rd month of treatment. Among patients who initially achieved a modest reduction in MMD, 64% (29/45) in the 70 mg group and 79% (27/34) in the 140 mg group achieved a response during the 2nd and/or 3rd month. Conclusion: Patients with CM who do not have an initial response to erenumab may experience improvement during subsequent months of continued treatment. Clinical benefit from preventive medications may take time to achieve, and an adequate duration of preventive treatment should be considered before assessing therapeutic outcome. The results of our analysis support recent guidance from the American Headache Society that recommends 3 months of assessment following initiation of migraine preventive treatment.",
						"Background: New treatment options are needed for patients with chronic migraine (CM) who fail to respond to migraine preventive medications. Fremanezumab, a fully humanized monoclonal antibody (IgG2Î”a) that selectively targets calcitonin gene‐related peptide (CGRP), is approved in the US for the preventive treatment of migraine in adults. Herein we evaluate the long‐term effect of fremanezumab on response rates, use of acute headache medication, and disability in patients with CM who had failed to respond at least one prior preventive migraine medication. Methods: In this 12‐month, multicenter, randomized, double‐blind, parallel‐group study, adults with CM received subcutaneous fremanezumab either quarterly (675 mg every 3 months) or monthly (225 mg every month with a starting dose of 675 mg). This post hoc analysis was limited to CM patients who had failed at least one prior migraine preventive medication, with failure defined as lack of efficacy or intolerability. The proportions of patients with a â‰¥50% reduction in the monthly number of migraine days and a â‰¥50% reduction in headache days of at least moderate severity, respectively, were measured at Month 6 and Month 12 in both fremanezumab dosing groups, as was the change from baseline in the monthly number of days with use of acute headache medications. The effect of fremanezumab on headache‐related disability was also assessed using the six‐Item Headache Impact Test (HIT‐6). Results: Of the 490 CM patients who had failed at least one prior migraine preventive medication, 247 received quarterly dosing and 243 received monthly dosing of fremanezumab. The proportion of patients with a â‰¥50% reduction in monthly migraine days was maintained at Months 6 (quarterly: 34%; monthly: 48%) and 12 (quarterly: 48%; monthly: 51%). A â‰¥50% reduction in the monthly number of headache days of at least moderate severity was reported by 40% and 51% of patients with quarterly and monthly dosing at Month 6, respectively, and this reduction was maintained in both dosing groups at Month 12 (quarterly: 48%; monthly: 52%). The change from baseline to Month 6 in the mean monthly number of days with use of acute headache medication was ‐4.7 days for quarterly and ‐6.2 days for monthly dosing, and from baseline to Month 12 it was ‐5.8 days for quarterly and ‐6.2 days for monthly dosing. The mean change from baseline to Month 6 in the HIT‐6 disability score was ‐5.5 for quarterly and ‐7.3 for monthly dosing, and from baseline to Month 12 it was ‐6.5 for quarterly and ‐8.0 for monthly dosing. Conclusion: These data demonstrate that long‐term treatment with fremanezumab maintains efficacy while reducing the use of acute headache medications and improving headache‐related disability for up to 1 year in CM patients who had failed at least one prior preventive migraine medication.",
						.858));
	}

	static Stream<Arguments> positiveArgumentProvider() {
		return Stream.of(
				// PubMed and Embase.com record for an ACP Journal Club article in Annals of Internal Medicine
				arguments(
						"Ashina M, Lanteri-Minet M, Pozo-Rosich P, et al. Safety and efficacy of eptinezumab for migraine prevention in patients with two-to-four previous preventive treatment failures (DELIVER): a multi-arm, randomised, double-blind, placebo-controlled, phase 3b trial. Lancet Neurol. 2022;21:597-607. 35716692.",
						"SOURCE CITATION: Ashina M, Lanteri-Minet M, Pozo-Rosich P, et al. Safety and efficacy of eptinezumab for migraine prevention in patients with two-to-four previous preventive treatment failures (DELIVER): a multi-arm, randomised, double-blind, placebo-controlled, phase 3b trial. Lancet Neurol. 2022;21:597-607. 35716692.",
						1.0),
				arguments(
						"to research retrospectively the efficacy of Erenumab's treatment, thus allowing to describe a summary more in line with the reality observed every day in clinical practice, relative to a sample of patients widely heterogeneous. The study aims to confirm the efficacy of Erenumab, in terms of reduction of migraine days per month, from baseline to month 12 of treatment. Additional objectives included a reduction in the number of days of symptomatic drug use and change from baseline in the Migraine Disability Assessment Score Questionnaire (MIDAS); Methods: the analysis included all patients treated for 12 months with Erenumab during the year 2019-2020. The population analyzed consists of twenty-six patients from the Neurology outpatient clinic in Fossombrone. Several quantitative and qualitative variables were recorded by reading the medical records of the patients. The MIDAS was administered to patients to assess the disability related to migraine; Results: at the end of treatment, a statistically significant reduction in the mean number of monthly migraine days, acute medication use per month, and MIDAS questionnaire score was observed; Conclusions: as a preventive treatment of episodic and chronic migraine, our analysis data confirm the efficacy of Erenumab for the prevention of the migraine. The success is achieved in 96% of cases.",
						"Background: to research retrospectively the efficacy of Erenumab's treatment, thus allowing to describe a summary more in line with the reality observed every day in clinical practice, relative to a sample of patients widely heterogeneous. The study aims to confirm the efficacy of Erenumab, in terms of reduction of migraine days per month, from baseline to month 12 of treatment. Additional objectives included a reduction in the number of days of symptomatic drug use and change from baseline in the Migraine Disability Assessment Score Questionnaire (MIDAS); Methods: the analysis included all patients treated for 12 months with Erenumab during the year 2019-2020. The population analyzed consists of twenty-six patients from the Neurology outpatient clinic in Fossombrone. Several quantitative and qualitative variables were recorded by reading the medical records of the patients. The MIDAS was administered to patients to assess the disability related to migraine; Results: at the end of treatment, a statistically significant reduction in the mean number of monthly migraine days, acute medication use per month, and MIDAS questionnaire score was observed; Conclusion(s): as a preventive treatment of episodic and chronic migraine, our analysis data confirm the efficacy of Erenumab for the prevention of the migraine. The success is achieved in 96% of cases. Copyright © 2021 by the authors. Licensee MDPI, Basel, Switzerland.",
						1.0),
				/*
				 * First string is from PubMed (PMID 34537006): char at position 149 is "\u2009"
				 * (THIN SPACE) Second string is from Cochrane (CN-02321497): char at position
				 * 149 is "\u0020" (SPACE), but 68 and 83 are "\u2010" (HYPHEN) not "\u002D"
				 * (HYPHEN-MINUS)
				 */
				arguments(
						"BACKGROUND: These subgroup analyses of a Phase 3, randomized, double-blind, placebo-controlled study evaluated the efficacy and safety of erenumab 70 mg in Japanese migraine patients with/without prior preventive treatment failure(s) (\"failed-yes\" and \"failed-no\" subgroups) and with/without concomitant preventive treatment (\"concomitant preventive-yes\" and \"concomitant preventive-no\" subgroups). METHODS: Overall, 261 patients were randomized; 130 and 131 patients to erenumab 70 mg and placebo, respectively. Subgroup analyses evaluated the change from baseline to Months 4-6 in mean monthly migraine days (MMD) (primary endpoint), achievement of a ≥50% reduction in mean MMD, and change from baseline in mean monthly acute migraine-specific medication (MSM) treatment days. Treatment-emergent adverse events were also evaluated. RESULTS: Of the 261 patients randomized, 117 (44.8%) and 92 (35.3%) patients were in the failed-yes and concomitant preventive-yes subgroups, respectively. Erenumab 70 mg demonstrated consistent efficacy across all subgroups, with greater reductions from baseline in mean MMD versus placebo at Months 4-6 (treatment difference versus placebo [95% CI], failed-yes: - 1.9 [- 3.3, - 0.4]; failed-no: - 1.4 [- 2.6, - 0.3]; concomitant preventive-yes: - 1.7 [- 3.3, 0.0]; concomitant preventive-no: - 1.6 [- 2.6, - 0.5]). Similar results were seen for achievement of ≥50% reduction in mean MMD and change from baseline in mean monthly acute MSM treatment days. The safety profile of erenumab 70 mg was similar across subgroups, and similar to placebo in each subgroup. CONCLUSION: Erenumab was associated with clinically relevant improvements in all efficacy endpoints and was well tolerated across all subgroups of Japanese migraine patients with/without prior preventive treatment failure(s) and with/without concomitant preventive treatment. TRIAL REGISTRATION: Clinicaltrials.gov . NCT03812224. Registered January 23, 2019.",
						"Background: These subgroup analyses of a Phase 3, randomized, double‐blind, placebo‐controlled study evaluated the efficacy and safety of erenumab 70 mg in Japanese migraine patients with/without prior preventive treatment failure(s) (?failed‐yes? and ?failed‐no? subgroups) and with/without concomitant preventive treatment (?concomitant preventive‐yes? and ?concomitant preventive‐no? subgroups). Methods: Overall, 261 patients were randomized; 130 and 131 patients to erenumab 70 mg and placebo, respectively. Subgroup analyses evaluated the change from baseline to Months 4?6 in mean monthly migraine days (MMD) (primary endpoint), achievement of a ?50% reduction in mean MMD, and change from baseline in mean monthly acute migraine‐specific medication (MSM) treatment days. Treatment‐emergent adverse events were also evaluated. Results: Of the 261 patients randomized, 117 (44.8%) and 92 (35.3%) patients were in the failed‐yes and concomitant preventive‐yes subgroups, respectively. Erenumab 70 mg demonstrated consistent efficacy across all subgroups, with greater reductions from baseline in mean MMD versus placebo at Months 4?6 (treatment difference versus placebo [95% CI], failed‐yes: ? 1.9 [? 3.3, ? 0.4]; failed‐no: ? 1.4 [? 2.6, ? 0.3]; concomitant preventive‐yes: ? 1.7 [? 3.3, 0.0]; concomitant preventive‐no: ? 1.6 [? 2.6, ? 0.5]). Similar results were seen for achievement of ?50% reduction in mean MMD and change from baseline in mean monthly acute MSM treatment days. The safety profile of erenumab 70 mg was similar across subgroups, and similar to placebo in each subgroup. Conclusion: Erenumab was associated with clinically relevant improvements in all efficacy endpoints and was well tolerated across all subgroups of Japanese migraine patients with/without prior preventive treatment failure(s) and with/without concomitant preventive treatment. Trial registration: Clinicaltrials.gov. NCT03812224. Registered January 23, 2019.",
						1.0),
				arguments( // THIN SPACE (\u2009) or no space in "30 mg"
						"BACKGROUND: Galcanezumab of 300 mg monthly is the FDA approved preventive medication for cluster headache (CH) during the cluster period. Compared to the 120 mg galcanezumab syringe for the treatment of migraines, the 100 mg syringe for CH has globally not been as widely available. The aim of our study was to investigate the preventive efficacy and tolerability of two 120 mg galcanezumab doses for episodic CH in clinical practices. METHODS: We evaluated patients with CH who received at least 1 dose of 240 mg (2 prefilled syringe of 120 mg) of galcanezumab in the 3 university hospitals from February 2020 to September 2021. In the patients with episodic CH, the efficacy and safety data of galcanezumab were analyzed regarding to the presence of the conventional preventive therapy at the timing of therapy of galcanezumab. The data of other subtypes of CH were separately described. RESULTS: In 47 patients with episodic CH, galcanezumab was started median 18 days after the onset of current bout (range 1-62 days) and 4 patients (10.8%) received second dose of galcanezumab. The median time to the first occurrence of 100% reduction from baseline in CH attacks per week after galcanezumab therapy was 17 days (25% to 75% quartile range: 5.0 ~ 29.5) in all patients with episodic CH, 15.5 days (3.8 ~ 22.1) in 36 patients with galcanezumab therapy add-on conventional preventive therapy, 21.0 days (12.0 ~ 31.5) in 11 patients started galcanezumab as initial preventive therapy. Among 33 patients with headache diary, the proportion of patients with 50% or more reduction in weekly CH attacks at week 3 from baseline were 78.8%. There was no significant difference in the proportion of patients with a reduction of at least 50% in weekly frequency of CH attacks at week 3 between 24 patients received galcanezumab therapy add-on conventional preventive therapy and 9 patient who received initial galcanezumab therapy. (83.3%, vs 66.7%, p = 0.36). There were no significant differences in proportion of \"very much better or \"much better\" between 36 patients received galcanezumab therapy add-on conventional preventive therapy and 11 patient who received initial GT (86.1%, vs 63.6%, p = 0.18). CONCLUSION: One 240 mg dose of galcanezumab with/without conventional therapy for the prevention of CH is considered effective and safe in clinical practices, as seen in the clinical trial of galcanezumab.",
						"BACKGROUND: Galcanezumab of 300mg monthly is the FDA approved preventive medication for cluster headache (CH) during the cluster period. Compared to the 120mg galcanezumab syringe for the treatment of migraines, the 100mg syringe for CH has globally not been as widely available. The aim of our study was to investigate the preventive efficacy and tolerability of two 120mg galcanezumab doses for episodic CH in clinical practices. METHOD(S): We evaluated patients with CH who received at least 1 dose of 240mg (2 prefilled syringe of 120mg) of galcanezumab in the 3 university hospitals from February 2020 to September 2021. In the patients with episodic CH, the efficacy and safety data of galcanezumab were analyzed regarding to the presence of the conventional preventive therapy at the timing of therapy of galcanezumab. The data of other subtypes of CH were separately described. RESULT(S): In 47 patients with episodic CH, galcanezumab was started median 18days after the onset of current bout (range 1-62days) and 4 patients (10.8%) received second dose of galcanezumab. The median time to the first occurrence of 100% reduction from baseline in CH attacks per week after galcanezumab therapy was 17days (25% to 75% quartile range: 5.0~29.5) in all patients with episodic CH, 15.5days (3.8~22.1) in 36 patients with galcanezumab therapy add-on conventional preventive therapy, 21.0days (12.0~31.5) in 11 patients started galcanezumab as initial preventive therapy. Among 33 patients with headache diary, the proportion of patients with 50% or more reduction in weekly CH attacks at week 3 from baseline were 78.8%. There was no significant difference in the proportion of patients with a reduction of at least 50% in weekly frequency of CH attacks at week 3 between 24 patients received galcanezumab therapy add-on conventional preventive therapy and 9 patient who received initial galcanezumab therapy. (83.3%, vs 66.7%, p=0.36). There were no significant differences in proportion of \"very much better or \"much better\" between 36 patients received galcanezumab therapy add-on conventional preventive therapy and 11 patient who received initial GT (86.1%, vs 63.6%, p=0.18). CONCLUSION(S): One 240mg dose of galcanezumab with/without conventional therapy for the prevention of CH is considered effective and safe in clinical practices, as seen in the clinical trial of galcanezumab. Copyright © 2022. The Author(s).",
						1.0),
				arguments(
						"Background:There is still a need for more studies to evaluate the role of vitamin D(3) in pediatric migraine prophylaxis. Objectives: We aimed to evaluate the effects and safety of vitamin D(3) supplementation to topiramate on pediatric migraine. Methods: A double-blinded prospective clinical trial was conducted on 5- to 14-year-old children with migraine. They were randomly assigned in a 1:1 ratio into 2 groups, one with vitamin D(3) supplementation (the supplementation group) and the other without vitamin D supplementation (the placebo group). The supplementation group received topiramate plus one 5000-IU dose of vitamin D(3) daily for 4 months. The placebo group received topiramate with a placebo capsule without any effective substances. The primary outcomes were a monthly frequency of headache attacks, a good response to intervention, and reduction in migraine severity, duration, and disability before and after treatment. Fifty-six children completed the trial. Vitamin D(3) supplementation to topiramate was more effective than the placebo group in the reduction of monthly frequency (6231.31 vs 9792.24 times, P = .01) and disability score for migraines (17 566.43 vs 25 187.65, P = .04). A good response was observed in 76.13% of patients in the vitamin D(3) supplementation group and 53.5% of patients in the placebo group, and vitamin D(3) supplementation was significantly more effective than placebo (P = .01). Side effects were observed in 13.3% and 20% of the intervention group and placebo groups, respectively, P = .5. Conclusion: Vitamin D(3) supplementation in pediatric migraine prophylaxis could be a well-tolerated, safe, and effective strategy.",
						"<b>Background:</b> There is still a need for more studies to evaluate the role of vitamin D<sub>3</sub> in pediatric migraine prophylaxis. <b>Objectives:</b> We aimed to evaluate the effects and safety of vitamin D<sub>3</sub> supplementation to topiramate on pediatric migraine.",
						0.99),
				arguments( // greek abbreviation
						"Objective To analyse the effects of a three-month course of progestogen-only contraception with desogestrel 75 mug on disability, headache frequency and headache intensity in migraineurs. Materials and methods Migraine disability headache questionnaires (MIDAS) were collected from 37 migraineurs during counselling, and at the end of three months treatment with desogestrel. Another ten women initiated but did not complete treatment. They are included in the overall evaluations of the effect of the regimen on migraine status. Results Desogestrel was associated with significant reductions in headache days and intensity (p < 0.001; p < 0.006), and a significant improvement in quality of life. Days missed at work and days missing leisure activities diminished (p < 0.001; p < 0.001). The MIDAS migraine disability score improved significantly (from 27.4 to 11.1 points) (p < 0.001). While 25 of the 37 women (68%) experienced a decrease of at least one grade, this level of benefit cannot be extrapolated to all initiators. When dropouts are considered, MIDAS grades decrease in 53% (25/47) of the cases. Conclusion The majority of migraineurs experienced a clinically significant reduction in headache frequency and improvement of quality of life with use of desogestrel. Prospective randomised controlled trials are needed to substantiate our results. © 2013 The European Society of Contraception and Reproductive Health.",
						"OBJECTIVE: To analyse the effects of a three-month course of progestogen-only contraception with desogestrel 75 μg on disability, headache frequency and headache intensity in migraineurs. MATERIALS AND METHODS: Migraine disability headache questionnaires (MIDAS) were collected from 37 migraineurs during counselling, and at the end of three months treatment with desogestrel. Another ten women initiated but did not complete treatment. They are included in the overall evaluations of the effect of the regimen on migraine status. RESULTS: Desogestrel was associated with significant reductions in headache days and intensity (p < 0.001; p < 0.006), and a significant improvement in quality of life. Days missed at work and days missing leisure activities diminished (p < 0.001; p < 0.001). The MIDAS migraine disability score improved significantly (from 27.4 to 11.1 points) (p < 0.001). While 25 of the 37 women (68%) experienced a decrease of at least one grade, this level of benefit cannot be extrapolated to all initiators. When dropouts are considered, MIDAS grades decrease in 53% (25/47) of the cases. CONCLUSION: The majority of migraineurs experienced a clinically significant reduction in headache frequency and improvement of quality of life with use of desogestrel. Prospective randomised controlled trials are needed to substantiate our results.",
						0.94),
				arguments( // one vs 1
						"In spite of the fact that migraines are 1 of the major problems seen by primary care providers, almost half of people with migraines do not obtain appropriate diagnosis and/or treatment. Migraine occurs in about 18% of women, and is often aggravated by hormonal shifts occurring around women's menses, during pregnancy, and during perimenopause. Quality of life with migraines is often greatly diminished, and many women miss work days and/or are less productive with migraines. Women's health care providers are very likely to see women with poorly managed migraines, but are often not comfortable diagnosing and treating their patients with headaches. A variety of self-care treatments, acute care ",
						"In spite of the fact that migraines are one of the major problems seen by primary care providers, almost half of people with migraines do not obtain appropriate diagnosis and/or treatment. Migraine occurs in about 18% of women, and is often aggravated by hormonal shifts occurring around women's menses, during pregnancy, and during perimenopause. Quality of life with migraines is often greatly diminished, and many women miss work days and/or are less productive with migraines. Women's health care providers are very likely to see women with poorly managed migraines, but are often not comfortable diagnosing and treating their patients with headaches. A variety of self-care treatments, acute care ",
						0.92));
	}

	static String revertString(String s) {
		return new StringBuilder(s).reverse().toString();
	}

	JaroWinklerSimilarity jws = new JaroWinklerSimilarity();

	RatcliffObershelp ro = new RatcliffObershelp();

	private String cleanAbstracttext(String inputtext) {
		String text = inputtext;
		text = text.replaceAll("\\. Copyright.+$", "");
		text = text.replaceAll("\\. ©.+$", "");
		 
		text = text.toLowerCase();
		text = text.replaceAll("\\<[^>]*>", "");
		// FIXME: replace with pattern
		// remove first one or two words + ": "
		// This replacement is more important with JaroWinkler distance metric than with
		// others because JW favors differences at the start of strings?
		// TODO: try with other metrics
		text = text.replaceAll(
				"^(aim(s?)|background(s?)|context|importance|introduction|objective(s?)|purpose|question|study objective|synopsis|(\\w+(\\s\\w+)?:\\s?))",
				"");
		// FIXME: replace with pattern
		text = text.replaceAll("\u2010", "\u002D");
		text = text.replaceAll("\u2009", "");
		text = text.replaceAll("\\p{Zs}+", " ");
		// remove all characters which are not letters or numbers. Some databases use
		// "\u2009" (THIN SPACE) within "30 mg", others use no character
		text = text.replaceAll("[^\\p{L}\\p{N} ]+", ""); // use "\\p{Nd}" if you want "¼" treated as a number
		text = text.strip();
		text = Publication.normalizeToBasicLatin(text);
//		if (text.length() > 200) {
//			text = text.substring(0, 199);
//		}
		return text;
	}

	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("negativeArgumentProvider")
	void jwNegativeTest(String input1, String input2, double expected) {
		String t1 = cleanAbstracttext(input1);
		String t2 = cleanAbstracttext(input2);
		Double distance = jws.apply(t1, t2);
		System.err.println("- 1: %s\n- 2: %s\n- 3: %s\n- 4: %s\n".formatted(input1, t1, input2, t2));
		assertThat(distance).isLessThanOrEqualTo(expected);
		// assertThat(distance).isLessThan(DeduplicationService.TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS);
	}

	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("positiveArgumentProvider")
	void jwPositiveTest(String input1, String input2, double expected) {
		// String diffsOfRawInput = getDiffs(input1, input2);
		String t1 = cleanAbstracttext(input1);
		String t2 = cleanAbstracttext(input2);
		Double distance = jws.apply(t1, t2);
		
		/*
		 * Awful way to get and print the differences between 2 strings to the console (instead of the JUnit tab)
		 */
		if (distance >= (expected - 0.01d) && distance <= (expected + 0.01d)) {
			assertThat(distance)
				.isEqualTo(expected, within(0.01));
		} else {
			System.err.println("Diffs: " +  getDiffs(t1,t2));
			assertThat(distance)
				.as("JWS distance too big. String: %s ...", t1.substring(0, 25))
				.isEqualTo(expected, within(0.01));
		}
		// assertThat(distance).isGreaterThanOrEqualTo(DeduplicationService.TITLE_SIMILARITY_SUFFICIENT_STARTPAGES_OR_DOIS);
	}

	@ParameterizedTest(name = "{index}: RatcliffObershelp({0}, {1})={2}")
	@MethodSource("negativeArgumentProvider")
	void ratcliffObershelpNegative(String input1, String input2, double expected) {
		System.err
				.println("RO: %.3f\tROlc: %.3f\tROclean: %.3f\tJWS: %.3f\tJWSclean: %.3f\texpected: %.3f".formatted(
			ro.similarity(input1, input2), ro.similarity(input1.toLowerCase(), input2.toLowerCase()),
			ro.similarity(cleanAbstracttext(input1), cleanAbstracttext(input2)), jws.apply(input1, input2),
			jws.apply(cleanAbstracttext(input1), cleanAbstracttext(input2)), expected));
		assertThat(1*1).isEqualTo(1);
	}

	@ParameterizedTest(name = "{index}: RatcliffObershelp({0}, {1})={2}")
	@MethodSource("negativeArgumentProvider")
	void ratcliffObershelpNegative200(String input1, String input2, double expected) {
		System.err.println("RO200: %.3f\texpected: %.3f".formatted(
			ro.similarity(input1.toLowerCase().substring(0, 200), input2.toLowerCase().substring(0, 200)),
			expected));
		assertThat(1*1).isEqualTo(1);
	}

	// formatter::off
	/*
	 * Ratcliff-Obershelp: preliminary results 
	 * - Too slow to be used in production with the full abstract, but speed with first 200 characters may be acceptable
	 * - positive examples: except for couples with/without HTML, lowercased RO looks very good 
	 * - negative examples: looks good if full length is used, but not with lowercased first 200 characters
	 */
	// formatter::on
	@ParameterizedTest(name = "{index}: RatcliffObershelp({0}, {1})={2}")
	@MethodSource("positiveArgumentProvider")
	void ratcliffObershelpPositive(String input1, String input2, double expected) {
		System.err
				.println("RO: %.3f\tROlc: %.3f\tROclean: %.3f\tJWS: %.3f\tJWSclean: %.3f\texpected: %.3f".formatted(
			ro.similarity(input1, input2), ro.similarity(input1.toLowerCase(), input2.toLowerCase()),
			ro.similarity(cleanAbstracttext(input1), cleanAbstracttext(input2)), jws.apply(input1, input2),
			jws.apply(cleanAbstracttext(input1), cleanAbstracttext(input2)), expected));
		assertThat(1*1).isEqualTo(1);
	}

	@ParameterizedTest(name = "{index}: RatcliffObershelp({0}, {1})={2}")
	@MethodSource("positiveArgumentProvider")
	void ratcliffObershelpPositive200(String input1, String input2, double expected) {
		System.err.println("RO200: %.3f\texpected: %.3f".formatted(
			ro.similarity(input1.toLowerCase().substring(0, 200), input2.toLowerCase().substring(0, 200)),
			expected));
		assertThat(1*1).isEqualTo(1);
	}

	//create a configured DiffRowGenerator
	private static DiffRowGenerator generator = DiffRowGenerator.create()
	                .showInlineDiffs(true)
	                .mergeOriginalRevised(true)
	                .inlineDiffByWord(true)
	                .oldTag(f -> "~~")      //introduce markdown style for strikethrough
	                .newTag(f -> "**")     //introduce markdown style for bold
	                .build();

	private String getDiffs(String text1, String text2) {
		//compute the differences for two test texts.
		List<DiffRow> rows = generator.generateDiffRows(List.of(text1), List.of(text2));
		 return rows.get(0).getOldLine();
	}
}