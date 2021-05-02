package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.domain.Record;
import edu.dedupendnote.services.AuthorsComparator;
import edu.dedupendnote.services.DeduplicationService;

//@ExtendWith(TimingExtension.class)
//@Slf4j
@TestConfiguration
public class JaroWinklerAuthorsTest {

	JaroWinklerSimilarity jws = new JaroWinklerSimilarity();
	DeduplicationService service = new DeduplicationService();
	AuthorsComparator authorsComparator = service.getAuthorsComparator();
	
	/*
	 * Uses the real AuthorComparator and the first similarity above the AUTHOR_SIMILARITY_NO_REPLY threshold. 
	 * There may be other author lists that have higher similarity. So this test can not be used to determine a sensible threshold.
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("positiveAuthorsProvider")
    void jwFullPositiveTest_lowest_accepted_similarity(String input1, String input2, double lowestAcceptedSimilarity, double highestSimilarity) {
		Record r1 = fillRecord(input1);
		Record r2 = fillRecord(input2);

		authorsComparator.compare(r1, r2);
		Double similarity = authorsComparator.getSimilarity();
		
		assertThat(similarity)
			.as("\nAuthors1: %s\nAuthors2: %s",  r1.getAllAuthors().get(0), r2.getAllAuthors().get(0))
			.isEqualTo(lowestAcceptedSimilarity, within(0.01))
			.isGreaterThan(DeduplicationService.DefaultAuthorsComparator.AUTHOR_SIMILARITY_NO_REPLY);
    }

	/*
	 *	Uses a minimal copy of the real AuthorComparator and returns the highest similarity.
	 *	BRITTLE because of copied code! 
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={3}")
	@MethodSource("positiveAuthorsProvider")
    void jwFullPositiveTest_highest_similarity(String input1, String input2, double lowestAcceptedSimilarity, double highestSimilarity) {
		Record r1 = fillRecord(input1);
		Record r2 = fillRecord(input2);

		Double highestDistance = 0.0;
		
		for (String authors1 : r1.getAllAuthors()) {
			for (String authors2 : r2.getAllAuthors()) {
				Double distance = jws.apply(authors1, authors2);
				if (distance > highestDistance) {
					highestDistance = distance;
				}
			}
		}

		assertThat(highestDistance)
			.as("\nAuthors1: %s\nAuthors2: %s",  r1.getAllAuthors().get(0), r2.getAllAuthors().get(0))
			.isEqualTo(highestSimilarity, within(0.01))
			.isGreaterThan(DeduplicationService.DefaultAuthorsComparator.AUTHOR_SIMILARITY_NO_REPLY);
    }

	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("negativeAuthorsProvider")
    void jwFullNegativeTest(String input1, String input2, double expected) {
		Record r1 = fillRecord(input1);
		Record r2 = fillRecord(input2);

		authorsComparator.compare(r1, r2);
		Double similarity = authorsComparator.getSimilarity();

		assertThat(similarity)
			.isEqualTo(expected, within(0.01))
			.isLessThan(DeduplicationService.DefaultAuthorsComparator.AUTHOR_SIMILARITY_NO_REPLY);
    }

	/*
	 * Compares only the first authorlists
	 * FIXME: this test and jwFullNegativeTest have same results,
	 * i.e. jwFullNegativeTest has no cases where the non-first authorlists have higher similarity than the first authorlists
	 */
	@ParameterizedTest(name = "{index}: jaroWinkler({0}, {1})={2}")
	@MethodSource("negativeAuthorsProvider")
    void jwFullNegativeTest_defective(String input1, String input2, double expected) {
		Record r1 = fillRecord(input1);
		Record r2 = fillRecord(input2);

		Double distance = jws.apply(r1.getAllAuthors().get(0), r2.getAllAuthors().get(0));

		assertThat(distance)
			.isEqualTo(expected, within(0.01))
			.isLessThan(DeduplicationService.DefaultAuthorsComparator.AUTHOR_SIMILARITY_NO_REPLY);
    }

	private Record fillRecord(String authors) {
		Record r = new Record();
		List<String> authorList1 = Arrays.asList(authors.split("; "));
		authorList1.stream().forEach(a -> r.addAuthors(a));
		r.fillAllAuthors();
		return r;
	}

    static Stream<Arguments> positiveAuthorsProvider() {
		return Stream.of(
				arguments(
						"Ram, S; Lewis, LA; Rice, PA",
						"Ram, S.; Lewis, L. A.; Rice, P. A.",
						1.0, 1.0),
				arguments(
						"Okuda, K.",
						"Okuda, K.; et al.",
						1.0, 1.0),
				arguments(
						"Cobos Mateos, J. M.",
						"Mateos, J. M. C",
						0.70, 1.0), // double last names
				arguments(
						"Cobos Mateos, J. M.; Aguinaga Manzanos, M. V.; Casas Pinillos, M. S.; Gonzalez Conde, R.; Gonzalez Sanchez, J. A.; De Miguel Velasco, J. E.; Soleto Saez, E.; Suarez Mier, M. P.",
						"Mateos, J. M. C.; Manzanos, M. V. A.; Pinillos, M. S. C.; Conde, R. G.; Sanchez, J. A. G.; Velasco, J. E. D. M.; Saez, E. S.; Mier, M. P. S.",
						0.73, 1.0), // double last names
				arguments(
						"Chen, W. J.; Yuan, S. F.; Yan, Q. Y.; Xiong, J. P.; Wang, S. M.; Zheng, W. E.; Zhang, W.; Sun, H. Y.; Chen, H.; Wu, L. L.",
						"Chen, Wen-Jun; Yuan, Shao-Fei.; Yan, Qing-Yuan; Xiong, Jian-Ping.; Wang, Sen-Ming; Zheng, Wei-E.; Zhang, Wu; Sun, Hong-Yu; Chen, Hua; Wu, Li-Li",
						1.0, 1.0), // initials versus full names
				arguments(
						"Heller, C.; Schobess, R.; Kurnik, K.; Junker, R.; Gunther, G.; Kreuz, W.; Nowak-Gottl, U.; Childhood Thrombophila Study, Grp",
						"Heller, C.; Schobess, R.; Kurnik, K.; Junker, R.; Gunther, G.; Kreuz, W.; Nowak-Gottl, U.",
						1.0, 1.0),	// with and without "Grp"
				arguments(
						"Danilă, M.; Sporea, I.; Popescu, A.; şirli, R.",
						"Danila, M.; Sporea, I.; Popescu, A.; Sirli, R.",
						0.99, 0.99), // diacriticals
				arguments(
						"Lynch Jr, T. J.; Kalish, L.; Mentzer, S. J.; Decamp, M.; Strauss, G.; Sugarbaker, D. J.",
						"Lynch, T.; Kalish, L.; Mentzer, S.; Decamp, M.; Strauss, G.; Sugarbaker, D.",
						0.89, 0.99), // different numbers of initials
				arguments(
						"Lv, Y; Qi, X; Xia, J; Fan, D; Han, G",
						"Lv, Y; Qi, XS; Xia, JL; Fan, DM; Han, GH",
						0.98, 0.98),	// when initials get form "JK" instead of "J. K.": 0.89 --> 0.98
				arguments(
						"Lv, Y.; Qi, X.; Xia, J.; Fan, D.; Han, G.",
						"Lv, Y.; Qi, X. S.; Xia, J. L.; Fan, D. M.; Han, G. H.",
						0.98, 0.98),
				arguments(
						"De Joode, E. A.; Van Heugten, C. M.; Verheij, F. R. J.; van Boxtel, M. P. J.",
						"Joode, E. A.; Heugten, C. M.; Verheij, F. R.; Boxtel, M. P.",
						0.77, 0.97),	// transposition of complex last names 
				arguments(
						"Joode, E. A. D.; Heugten, C. M. V.; Verheij, F. R. J.; Boxtel, M. P. J. V.",
						"Joode, E. A.; Heugten, C. M..; Verheij, F. R.; Boxtel, M. P.",
						0.97, 0.97),	// first part of first name as last initial
				arguments(
						"Boudjema, K.; Cherqui, D.; Jaeck, D.; Chenard-Neu, M. P.; Steib, A.; Freis, G.; Becmeur, F.; Brunot, B.; Simeoni, U.; Bellocq, J. P.; et al.",
						"Boudjema, K.; Cherqui, D.; Jaeck, D.; Chenardneu, M. P.; Steib, A.; Freis, G.; Becmeur, F.; Brunot, B.; Simeoni, U.; Bellocq, J. P.; Tempe, J. D.; Wolf, P.; Cinqualbre, J.",
						0.9388, 0.93), // with "et al."
				arguments(
						"Boudjema, K.; Cherqui, D.; Jaeck, D.; Chenard-Neu, M. P.; Steib, A.; Freis, G.; Becmeur, F.; Brunot, B.; Simeoni, U.; Bellocq, J. P.",
						"Boudjema, K.; Cherqui, D.; Jaeck, D.; Chenardneu, M. P.; Steib, A.; Freis, G.; Becmeur, F.; Brunot, B.; Simeoni, U.; Bellocq, J. P.; Tempe, J. D.; Wolf, P.; Cinqualbre, J.",
						0.9371, 0.93), // without "et al."
				arguments(
						"Mateos, JM; Manzanos, MV; Pinillos, MS; Conde, R; Sanchez, JA; Velasco, JE; Saez, E; Mier, MP",
						"Mateos, JMC; Manzanos, MVA.; Pinillos, MSC; Conde, RG; Sanchez, JAG; Velasco, JED; Saez, ES.; Mier, MPS",
						0.92, 0.92), // 0.92 // double last names
						// TEST: when initials get form "JK" instead of "J. K." and all but the last first names are stripped: 0.73 --> 0.92
				arguments(
						"Lv, Y.; Qi, X. S.; He, C. Y.; Wang, Z. Y.; Yin, Z. X.; Niu, J.; Guo, W. G.; Bai, W.; Zhang, H. B.; Xie, H. H.; Yao, L. P.; Wang, J. H.; Li, T.; Wang, Q. H.; Chen, H.; Liu, H. B.; Wang, E. X.; Xia, D. D.; Luo, B. H.; Li, X. M.; Yuan, J.; Han, N.; Zhu, Y.;",
						"Lv, Y.; Qi, X.; He, C.; Wang, Z.; Yin, Z.; Niu, J.; Guo, W.; Bai, W.; Zhang, H.; Xie, H.; Yao, L.; Wang, J.; Li, T.; Wang, Q.; Chen, H.; Liu, H.; Wang, E.; Xia, D.; Luo, B.; Li, X.; Yuan, J.; Han, N.; Zhu, Y.; Xia, J.; Cai, H.; Yang, Z.; Wu, K.; Fan, D.",
						0.87, 0.87), // not truncated and 1- vs 2-initials
				arguments(
						"Schwartzberg, L. S.; Blakely, L. J.; Schnell, F.; Christianson, D.; Andrews, M.; Johns, A.; Walker, M.",
						"Schwartzberg, L. S.; Tauer, K. W.; Schnell, F. M.; Hermann, R.; Rubin, P.; Christianson, D.; Weinstein, P.; Epperson, A.; Walker, M.",
						0.85, 0.85), // 0.85 // !!! despite big differences
				arguments(
						"Schwartzberg, L. S.; Tauer, K. W.; Schnell, F. M.; Hermann, R.; Rubin, P.; Christianson, D.; Weinstein, P.; Epperson, A.; Walker, M.",
						"Schwartzberg, L. S.; Blakely, L. J.; Schnell, F.; Christianson, D.; Andrews, M.; Johns, A.; Walker, M.",
						0.85, 0.85),	// example of a false positive: 9 vs 7 names, 4 in common
				arguments(
						"Okuda, K.",
						"Okuda, K.; Orozco, H.; Garcia‐Tsao, G.; Takahashi, T.",
						0.83, 0.83),
				arguments(
						"Shah, N. P.; Kantarjian, H.; Kim, D. W.; Hochhaus, A.; Saglio, G.; Guilhot, F.; Schiffer, C. A.; Steegmann, J. L.; Mohamed, H.; Dejardin, D.; Healey, D. I.; Cortes, J. E.",
						"Shah, N. P.; Cortes, J. E.; Schiffer, C. A.; Guilhot, F.; Brummendorf, T. H.; Chen, A. C.; Healey, D.; Lambert, A.; Saglio, G.",
						0.82, 0.82),	// example of a false positive: 12 vs 9 names, 6 in common
				arguments(
						"Adriana, Bintintan; Petru Adrian, Mircea; Romeo, Chira; Georgiana, Nagy; Roberta Manzat, Saplacan; Simona, Valean",
						"Bintintan, A.; Mircea, P. A.; Chira, R.; Nagy, G.; Saplacan, R. M.; Valean, S.",
						0.71, 0.76), // 0.72 // first and last names switched
				arguments(
						"Lv, Y.; Qi, X. S.; He, C. Y.; Wang, Z. Y.; Yin, Z. X.; Niu, J.; Guo, W. G.; Bai, W.; Zhang, H. B.; Xie, H. H.; Yao, L. P.; Wang, J. H.; Li, T.; Wang, Q. H.; Chen, H.; Liu, H. B.; Wang, E. X.; Xia, D. D.; Luo, B. H.; Li, X. M.; Yuan, J.; Han, N.; Zhu, Y.",
						"Lv, Y.; Qi, X.; He, C.; Wang, Z.; Yin, Z.; Niu, J.; Guo, W.; Bai, W.; Zhang, H.; Xie, H.; et al.",
						0.69, 0.69), // truncated with "et al." and 1- vs 2-initials
				arguments(
						"Bitto, N.; Tosetti, G.; La Mura, V.; Primignani, M.",
						"Scheiner, B.; Northup, P. G.; Lisman, T.; Mandorfer, M.",
						0.68, 0.68), // 0.68 // !!! despite big differences: NO NAMES IN COMMON
				arguments(
						"Cruz-Ramon, V.; Chinchilla-Lopez, P.; Ramirez-Perez, O.; Aguilar-Olivos, N. E.; Alva-Lopez, L. F.; Fajardo-Ordonez, E.; Ponciano-Rodriguez, G.; Northup, P. G.; Intagliata, N.; Caldwell, S. H.; Qi, X. S.; Mendez-Sanchez, N.",
						"Raoul, J. L.; Decaens, T.; Burak, K.; Koskinas, J.; Villadsen, G. E.; Heurgue-Berlot, A.; Bayh, I.; Cheng, A. L.; Kudo, M.; Lee, H. C.; Nakajima, K.; Peck-Radosavljevic, M.",
						0.67, 0.67), // 0.68 // !!! despite big differences: NO NAMES IN COMMON
				arguments("", "", 1.0, 1.0)
			);
    }
    
    static Stream<Arguments> negativeAuthorsProvider() {
		return Stream.of(
				arguments(
						"Wilkinson, E. J.; Raab, S. S.",
						"Flowers, L. C.; Tadros, T. S.",
						0.58),
				arguments(
						"Bouros, Demosthenes",
						"Rosa, Ute W.",
						0.61),
				arguments(
						"Armentano, P.",
						"Clendenning, R.",
						0.56),
				arguments(
						"Carter, G. T.; Mirken, B.",
						"Finnerup, N. B.; Otto, M.; Jensen, T. S.; Sindrup, S. H.",
						0.51),
				arguments(
						"Iacoviello, L.; Donati, M. B.",
						"Jolobe, O. M.",
						0.49),
				arguments(
						"Armentano, P.",
						"Carter, G. T.; Mirken, B.",
						0.47),
				arguments(
						"Clendenning, R.",
						"Finnerup, N. B.; Otto, M.; Jensen, T. S.; Sindrup, S. H.",
						0.47),
				arguments(
						"Armentano, P.",
						"Finnerup, N. B.; Otto, M.; Jensen, T. S.; Sindrup, S. H.",
						0.43)
		);
   }
    
}