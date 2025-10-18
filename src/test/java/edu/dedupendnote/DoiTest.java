package edu.dedupendnote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestConfiguration;

import edu.dedupendnote.services.NormalizationService;

@TestConfiguration
class DoiTest {

	@ParameterizedTest(name = "{index}: addDois({0})=({1},{2})")
	@MethodSource("argumentProvider")
	void addDois(String input, int numberOfDois, Set<String> dois) {
		Set<String> normalized = NormalizationService.normalizeInputDois(input);

		assertThat(normalized).hasSize(numberOfDois).containsAll(dois);
	}

	static Stream<Arguments> argumentProvider() {
		return Stream.of(
				arguments("S0731-7085(15)30056-X [pii];10.1016/j.jpba.2015.07.002 [doi]", 1,
						Set.of("10.1016/j.jpba.2015.07.002")),
				arguments("10.1002/(SICI)1099-0461(1998)12:1<29::AID-JBT5>3.0.CO;2-R [pii]", 1,
						Set.of("10.1002/(sici)1099-0461(1998)12:1<29::aid-jbt5>3.0.co;2-r")),
				arguments("10.1007/s12035-015-9182-6 [doi];10.1007/s12035-015-9182-6 [pii]", 1,
						Set.of("10.1007/s12035-015-9182-6")),
				arguments("10.1007/s12035-015-9182-6 [doi];10.1007/s12035-015-9182-6_bla [pii]", 2,
						Set.of("10.1007/s12035-015-9182-6", "10.1007/s12035-015-9182-6_bla")),
				arguments("10.1002/(SICI)1098-1063(1998)8:6&lt;627::AID-HIPO5&gt;3.0.CO;2-X [doi]", 1,
						Set.of("10.1002/(sici)1098-1063(1998)8:6<627::aid-hipo5>3.0.co;2-x")));
		/*
		 * The following strings were taken from a UR field to test whether using this
		 * field would add more duplicates. Verdict: NO, more false positives with corrections etc
		 * !!! length check < 200 in addDois had to be relaxed 
		 */
		//				arguments("http://ovidsp.ovid.com/ovidweb.cgi?T=JS&CSC=Y&NEWS=N&PAGE=fulltext&D=medl&AN=22897451 AND http://ZL9EQ5LQ7V.search.serialssolutions.com/?sid=OVID:medline&id=pmid:22897451&id=doi:10.3109%2F02699052.2012.706354&issn=0269-9052&isbn=&volume=26&issue=11&spage=1297&pages=1297-306&date=2012&title=Brain+Injury&atitle=Pre-treatment+compensation+use+is+a+stronger+correlate+of+measures+of+activity+limitations+than+cognitive+impairment.&aulast=Yutsis&pid=%3Cauthor%3EYutsis+M%3BBergquist+T%3BMicklewright+J%3BGehl+C%3BSmigielski+J%3BBrown+AW%3C%2Fauthor%3E%3CAN%3E22897451%3C%2FAN%3E%3CDT%3EJournal+Article%3C%2FDT%3E",
		//						1, Set.of("10.3109/02699052.2012.706354")),
		//				arguments("http://www.ezproxy.is.ed.ac.uk/login?url=http://ovidsp.ovid.com/ovidweb.cgi?T=JS&CSC=Y&NEWS=N&PAGE=fulltext&D=emexa&AN=614023585http://openurl.ac.uk/athens:_edu//lfp/LinkFinderPlus/Display?sid=OVID:Embase&id=pmid:&id=10.1055%2Fs-0035-1557993&issn=1439-0795&isbn=&volume=25&issue=6&spage=&pages=&date=2015&title=Pharmacopsychiatry&atitle=Structural+MR+correlates+of+epigenetic+age+acceleration&aulast=Samann&pid=<author><p15/></author>&<AN><p16/></AN>",
		//						1, Set.of("10.1055/s-0035-1557993")),
		//				arguments("http://www.ezproxy.is.ed.ac.uk/login?url=http://ovidsp.ovid.com/ovidweb.cgi?T=JS&CSC=Y&NEWS=N&PAGE=fulltext&D=emexa&AN=614023674http://openurl.ac.uk/athens:_edu//lfp/LinkFinderPlus/Display?sid=OVID:Embase&id=pmid:&id=10.1055%2Fs-0035-1558033&issn=1439-0795&isbn=&volume=25&issue=6&spage=&pages=&date=2015&title=Pharmacopsychiatry&atitle=Structural+MR+correlates+of+epigenetic+age+acceleration&aulast=Samann&pid=<author><p15/></author>&<AN><p16/></AN>",
		//						1, Set.of("10.1055/s-0035-1558033")));
	}

}