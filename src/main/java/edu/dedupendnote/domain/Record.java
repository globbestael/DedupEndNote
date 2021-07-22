package edu.dedupendnote.domain;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class Record {
	private List<String> allAuthors = new ArrayList<>();
	public List<String> authors = new ArrayList<>();
	public List<String> authorsTransposed = new ArrayList<>();
	public boolean authorsAreTransposed = false;
	public Map<String, Integer> dois = new HashMap<>();
	public String id;
	public List<String> issns = new ArrayList<>();
	public Set<String> journals = new HashSet<>();
	/*
	 * The label field is used internally to mark the duplicate lists: the label of all duplicate records in a set receive the ID of the first record of this list.
	 * If a record has no duplicates, the label is not set.
	 * It is NOT the content of the Label (EndNote field LB) of the EndNote input file.
	 * If markMode is set, this field is exported. The original content of the Label field in the EndNote export file is overwritten in this case!
	 */
	public String label;
	public String pageEnd;
	public String pageStart;
	private String pageStartForComparison;
	private boolean presentInOldFile = false;	// used when comparing 2 files
	public Integer publicationYear = 0;
	public String title;	// only set for Reply-titles
	public List<String> titles = new ArrayList<>();

	/*
	 *  Titles which are replies need special treatment. See the Pattern in the Service.
	 *  - record pairs where one of them is isReply == true, aren't compared for title (always true)
	 *  - journals are compared stricter (JaroWinkler distance = 0.93 instead of 0.9)
	 *  - in enrich() the longest title of a duplicate set is used
	 */
	private boolean isReply = false;
	private boolean isPhase = false;
	private Boolean keptRecord = true;

	// see: http://blog.crossref.org/2015/08/doi-regular-expressions.html
	private static Pattern doiPattern = Pattern.compile("\\b(10.\\d{4,9}/[-._;()<>/:a-z0-9]+)\\b");
	private static Pattern issnPattern = Pattern.compile("\\b([-\\dxX]{8,17})\\b"); // ISSN or ISBN
	static List<String> excludedJournalsParts = Arrays.asList("electronic resource", "et al.", "technical report");

	/*
	 * In Java 8 replaceAll via PATTERN.matches(s).replaceAll(replacement) is faster than s.replaceAll(replacement)
	 * See below for Java9Plus versions.
	 */
	private static Pattern quotesPattern = Pattern.compile("\"");
	private static Pattern nonInitialSquareBracketsPattern = Pattern.compile(".\\[[^\\\\]+\\]$"); 	// non initial "[...]"
	private static Pattern pointyBracketsPattern = Pattern.compile("<[^>]+>");						// "<...>"
	private static Pattern roundBracketsPattern = Pattern.compile("[\\(\\)]");						// "(" or ")"
	private static Pattern hyphenPattern = Pattern.compile("\\-");					// "(" or ")"
	private static Pattern nonAsciiLowercasePattern = Pattern.compile("[^a-z0-9]");
	private static Pattern nonAsciiPattern = Pattern.compile("[^a-z0-9]", Pattern.CASE_INSENSITIVE);
	private static Pattern multipleWhiteSpacePattern = Pattern.compile("\\s{2,}");
	private static Pattern startingArticlePattern = Pattern.compile("^(the|a|an) ");
	private static Pattern erratumPattern = Pattern.compile("^(Erratum: |Erratum to|Correction to )(.*)$");
   	// https://stackoverflow.com/questions/47162098/is-it-possible-to-match-nested-brackets-with-a-regex-without-using-recursion-or/47162099#47162099
	private static Pattern balancedBracespattern = Pattern.compile("(?=\\()(?:(?=.*?\\((?!.*?\\1)(.*\\)(?!.*\\2).*))(?=.*?\\)(?!.*?\\2)(.*)).)+?.*?(?=\\1)[^(]*(?=\\2$)");

	static public String normalizeJava8(String s) {
		s = quotesPattern.matcher(s).replaceAll("");

		Matcher matcher = erratumPattern.matcher(s);
		if (matcher.find()) {
			log.debug("Title WAS: {}", s);
			s = matcher.group(2);
			log.debug("Title IS1: {}", s);
			matcher = balancedBracespattern.matcher(s);
			while (matcher.find()) {
				if (matcher.end(0) == s.length()) {
					s = s.substring(0, matcher.start() -1);
				}
			}
			log.debug("Title IS2: {}", s);
		}
		String r = s.toLowerCase();
		r = nonInitialSquareBracketsPattern.matcher(r).replaceAll("");
		r = pointyBracketsPattern.matcher(r).replaceAll("");
		r = roundBracketsPattern.matcher(r).replaceAll("");
		r = hyphenPattern.matcher(r).replaceAll("");
		r = nonAsciiLowercasePattern.matcher(r).replaceAll(" ");
		r = r.trim();
		r = multipleWhiteSpacePattern.matcher(r).replaceAll(" ");
		r = startingArticlePattern.matcher(r).replaceAll("");
		return r.trim();
	}

	private static Pattern nonBasicLatinPattern = Pattern.compile("[^\\p{ASCII}]");
	private static Pattern ampersandPattern = Pattern.compile("&");
	private static Pattern jpnPattern = Pattern.compile("Jpn");
	private static Pattern dtschPattern = Pattern.compile("Dtsch");
	private static Pattern natlPattern = Pattern.compile("Natl");
	private static Pattern geneeskdPattern = Pattern.compile("Geneeskd");
	private static Pattern kongressbdPattern = Pattern.compile("Kongressbd");
	private static Pattern zentralblattPattern = Pattern.compile("^Zbl(\\.| )", Pattern.CASE_INSENSITIVE);
	private static Pattern jbrPattern = Pattern.compile("Jbr-btr", Pattern.CASE_INSENSITIVE);
	private static Pattern rofoPattern = Pattern.compile("^(Rofo\\-|Fortschritte .* Gebiet .* R.ntgenstrahlen)", Pattern.CASE_INSENSITIVE);	// before the latin_o_pattern because this is an exception
	private static Pattern latin_o_Pattern = Pattern.compile("o\\-(\\S)");	// before minusOrDotPattern! gastro-enterology --> gastroenterology 
	private static Pattern minusOrDotPattern = Pattern.compile("(-|\\.)");
	private static Pattern journalStartingArticlePattern = Pattern.compile("^(The|Le|La|Les|L'|Der|Die|Das|Il|Het) ");
	private static Pattern genitiveApostrophePattern = Pattern.compile("'s");
	private static Pattern nonGenitiveApostrophePattern = Pattern.compile("'");
	private static Pattern journalEndingRoundBracketsPattern = Pattern.compile("\\([^\\)]+\\)$");
	private static Pattern journalOtherRoundBracketsPattern = Pattern.compile("(\\)|\\()");
	private static Pattern journalAdditionPattern = Pattern.compile("(:|/).*$");
	private static Pattern journalSlashPattern = Pattern.compile("^(.+\\S)/(\\S.+)");
	private static Pattern supplementPattern = Pattern.compile("(\\b(Suppl|Supplement|Supplementum)\\b.*)$", Pattern.CASE_INSENSITIVE);

	static public String normalizeJournalJava8(String s) {
		String r = s;
		Matcher matcher = nonBasicLatinPattern.matcher(r);
		if (matcher.find()) {
			r = Normalizer
	        	.normalize(r, Normalizer.Form.NFD)
	        	.replaceAll("[^\\p{ASCII}]", "");
		}
		r = ampersandPattern.matcher(r).replaceAll(" ");					// we don't want "&" in the patterns
		r = jpnPattern.matcher(r).replaceAll("Japanese");					// irregular abbreviations
		r = dtschPattern.matcher(r).replaceAll("Deutsch");
		r = natlPattern.matcher(r).replaceAll("National");
		r = geneeskdPattern.matcher(r).replaceAll("Geneeskunde");
		r = kongressbdPattern.matcher(r).replaceAll("Kongressband");
		r = zentralblattPattern.matcher(r).replaceAll("Zentralblatt");
		// Cheating
		r = jbrPattern.matcher(r).replaceAll("JBR BTR");
		r = rofoPattern.matcher(r).replaceAll("Rofo ");
		// Java 8
		// https://stackoverflow.com/questions/2770967/use-java-and-regex-to-convert-casing-in-a-string
		if (latin_o_Pattern.matcher(r).find()) {							// "Gastro-Enterology" -> "Gastroenterology"
			StringBuilder sb = new StringBuilder();
		    int last = 0;
		    Matcher latin_o_matcher = latin_o_Pattern.matcher(r);
		    while (latin_o_matcher.find()) {
		        sb.append(r.substring(last, latin_o_matcher.start()));
		        sb.append("o").append(latin_o_matcher.group(1).toLowerCase());
		        last = latin_o_matcher.end();
		    }
		    sb.append(r.substring(last));
		    r = sb.toString();
		}
	    // Java 9+
		// r = latin_o_Pattern.matcher(r).replaceAll(m -> "o" + m.group(1).toLowerCase());		// "Gastro-Enterology" -> "Gastroenterology" 
		r = minusOrDotPattern.matcher(r).replaceAll(" ");
		r = journalStartingArticlePattern.matcher(r).replaceAll("");		// article at start
		r = genitiveApostrophePattern.matcher(r).replaceAll("s");			// Langenbeck's / Bailliere's / Crohn's
		r = nonGenitiveApostrophePattern.matcher(r).replaceAll(" ");		// Annales d'Urologie / Journal of Xi'an Jiaotong University (Medical Sciences)
		r = journalEndingRoundBracketsPattern.matcher(r).replaceAll("");	// "Ann Med Interne (Paris)" --> "Ann Med Interne", or "J Med Ultrason (2001)"
		r = journalOtherRoundBracketsPattern.matcher(r).replaceAll(" ");	// Some journals only have "(" without a ")", which causes regex problems
		if (r.toLowerCase().startsWith("http" )) {							// Cochrane library CENTRAL has journal name of type: Https://clinicaltrials.gov/show/nct00969397
			r = r.toLowerCase();
		} else {
			r = journalAdditionPattern.matcher(r).replaceAll(" ");			// "BJOG: An International Journal of Obstetrics and Gynaecology" --> "BJOG"
			r = nonAsciiPattern.matcher(r).replaceAll(" ");
		}
		r = multipleWhiteSpacePattern.matcher(r).replaceAll(" ");
		return r.trim();													// DO NOT lowercase (http titles are the exception)
	}

	/*
	 * TODO: From Java 9 onwards performance of String::replaceAll is much better 
	 * 
	 * But please check first:
	 * - if the performance is better than the Java 8 Pattern approach chosen
	 * - if naming the patterns isn't useful
	 * - align the Java9Plus versions with the Java8 versions!!! he Java9Plus versions are old.
	 */
	//	static public String normalizeJava9Plus(String s) {
	//		String r = s.replaceAll(".\\[[^\\\\]+\\]$", "")		// remove non initial "[...]"
	//				.replaceAll("<[^>]+>", "") 					// remove "<...>"
	//				.replaceAll("[\\(\\)]", "") 				// remove "(" and ")"
	//				.toLowerCase().replaceAll("[^a-z0-9]", " ")
	//				.trim().replaceAll("\\s{2,}", " ")
	//				.replaceAll("^(the|a|an) ", "")
	//				.trim();
	//		// System.err.println(r);
	//		if (r.equals("")) {
	//			System.err.println("Title is empty: " + s);
	//			throw new RuntimeErrorException(new Error("Empty title"));
	//		}
	//		return r;
	//	}
	//
	//	static public String normalizeJournalJava9Plus(String s) {
	//		return s.replaceAll("&", " ") 							// we don't want "&" in the patterns
	//				.replaceAll("Jpn", "Japanese")					// irregular abbreviations
	//				.replaceAll("Dtsch", "Deutsch")
	//				.replaceAll("Natl", "National")
	//				.replaceAll("Geneeskd", "Geneeskunde")
	//				.replaceAll("-", "")
	//				.replaceAll("^(The|Le|La|Der|Die|Das|Il) ", "") // article as start
	//				.replaceAll("\\(([^\\)]*)\\)$", "") 			// "Ann Med Interne (Paris)" --> "Ann Med Interne", or "J Med Ultrason (2001)"
	//				.replaceAll(".\\[[^\\\\]+\\]", "") 				// "Zhonghua wai ke za zhi [Chinese journal of surgery]" --> "Zhonghua wai ke za zhi"
	//				.replaceAll("(\\]|\\[)", "") 					// "[Technical report] SAM-TR" --> "Technical report SAM-TR"
	//				.replaceAll("(:|/) .*$", "") 					// "BJOG: An International Journal of Obstetrics and Gynaecology" --> "BJOG"
	//				.replaceAll("\\s{2,}", " ")
	//				.trim();
	//	}

	// @formatter:off
	/*
	 * addAuthors() and author comparisons
	 *
	 * Possible enhancements:
	 * ----------------------
	 * - Compare only the first ... authors
	 *   See See AuthorVariantsExperimentsTest::compareOnlyFirst10AuthorsTest()
	 *   
	 * Paths not chosen:
	 * -----------------
	 * - treat all authors longer than ... characters as groups
	 * 	 This would skip "Treatment of Hepatocellular Carcinoma with Tumour, T" which doesn't contain a group-word 
	 * 	 With length => 40 some person authors are still found:
	 * 		- Srivastava, K. Das N. Goel PBhatnagar V. [these are multiple authors]
	 * 		- Silveira, Renata Cristina de Campos Pereira	[PsycINFO]
	 * 		- Fantini, Francisca Goreth Malheiro Moraes	[PsycINFO]
	 * 		- Cardoso, Tania Aparecida Marchiori de Oliveira	[PsycINFO]
	 * 
	 * Strange cases, maybe solveable:
	 * -------------------------------
	 * - Different numbers of author
	 *   - Yamashita, Y.
	 *   - Yamashita, Y.; Takahashi, M.; Koga, Y.; Saito, R.; Nanakawa, S.; Hatanaka, Y.; Sato, N.; Nakashima, K.; Urata, J.; Yoshizumi, K.; et al.
	 *   Could be solved if the same number of authors were compared. See AuthorVariantsExperimentsTest::compareSameNumberOfAuthorsTest
	 *   But implementing this could be difficult
	 *  
	 * Strange cases, not handled yet:
	 * -------------------------------
	 * - CYK names: last and first names are often mixed up
	 *   - "Chung, J. W." and "Jin Wook, Chung"
	 *   - same publication:
	 *     - Chuan-Xing, L.; Xu, H.; Bao-Shan, H.; Yong, L.; Pei-Jian, S.; Xian-Yi, Y.; Xiao-Ning, L.; Li-Gong, L.
	 *     - Li, C. X.; He, X.; Hu, B. S.; Li, Y.; Shao, P. J.; Yu, X. Y.; Luo, X. N.; Lu, L. G.
	 *   - same publication:
	 *     - Chen, Y.; Chen, J.; Luo, B.
	 *     - Yajin, C.; Jisheng, C.; Baoming, L.
	 *   - both orders in same publication: 
	 *     - Jia-Wu, Li; Qiang, Lu; Yan, Luo; Li, Jia-Wu; Lu, Qiang; Luo, Yan [CINAHL: same 3 authors with transposed names]
	 * 	   - But this also occurs in CINAHL without transposed authors:
	 *        - Chung, R. T.; Iafrate, A. J.; Amrein, P. C.; Sahani, D. V.; Misdraji, J.; Chung, Raymond T.; Iafrate, A. John; Amrein, Philip C.; Sahani, Dushyant V.; Misdraji, Joseph
	 * - Parts run together:
	 *   - vanSpronsen, F. J.; deLangen, Z. J.; vanElburg, R. M.; Kimpen, J. L. L.
	 *   - 25 cases in BIG_SET, o.a. "DeAngelo, D. J."
	 *   - BUT: McGovern / MacGovern / FitzGerald
	 * - First author left out:
	 *   - Bureau, C.; Laurent, J.; Robic, M. A.; Christol, C.; Guillaume, M.; Ruidavets, J. B.; Ferrieres, J.; Péron, J. M.; Vinel, J. P.
	 *   - Laurent, J.; Robic, M. A.; Christol, C.; Guillaume, M.; Ruidavets, J. B.; Ferrieres, J.; Peron, J. M.; Vinel, J. P.
	 * - De, Marco L [should this be: De Marco, L?]
	 */
	// @formatter:on

	
	// "jr", "sr" can only be skipped if last name is not empty
	static private Pattern lastNameAdditionsPattern = Pattern.compile("^(jr|sr|1st|2nd|3rd|(i+))$", Pattern.CASE_INSENSITIVE);
	// public because used in tests
	// FIXME: add "No authorship, indicated"?
	static public Pattern anonymousOrGroupNamePattern = Pattern.compile("\\b(anonymous|consortium|et al|grp|group|nct|study)\\b", Pattern.CASE_INSENSITIVE);
	
	public void addAuthors(String author) {
		//	skip "Anonymous", "et al" and group authors 
		Matcher matcher = anonymousOrGroupNamePattern.matcher(author);
		if (matcher.find()) {
			return;
		}
		
		// normalize
		matcher = nonBasicLatinPattern.matcher(author);
		if (matcher.find()) {
			author = Normalizer
					.normalize(author, Normalizer.Form.NFD)
					.replaceAll("[^\\p{ASCII}]", "");
		}
		
		author = author.replaceAll("-", " ");

		// FIXME: should this be a Pattern?
		String[] parts = author.split("\\s*,\\s+"); // see testfile Non_Latin_input.txt for " , "
		if (parts.length < 2) {
			log.debug("Author {} cannot be split", author);
			this.authors.add(author);
			return;
		}
		
		// Reducing the first names to their initials, stripping everything but the initials, and leaving out the comma
		// makes JWS higher for similar names and lower for different names.
		String initials = parts[1].replaceAll("[^A-Z]", "");
		this.authors.add(parts[0] + " " + initials);

		// @formatter:off
		/*
		 *  Transposed author names:
		 *  If the last name contains a space:
		 *  - create a transposed author with the first part left out and added as the last initial
		 *  	- Cobos Mateos, J. M. 	==> Mateos JMC
		 *  	- van Boxtel, M. P. J. 	==> Boxtel MPJV
		 *  - set authorsAreTransposed to true
		 *  If the last name does not contain a space:
		 *  - create a transposed author unchanged
		 *  - do not set authorsAreTransposed
		 *  
		 *  In fillAuthors() there is a check on authorsAreTransposed. Only when set are the authorsTransposed (joined as 1 String)
		 *  added to List<String> allAuthors.
		 *  
		 *  Uses only the first transposition:
		 *  - De Brouwer de Boer, A.	==> Brouwer de Boer AD
		 *  
		 *  Paths not chosen:
		 *  - create all transpositions
		 *    See AuthorPermutationsExperimentsTest
		 */
		// @formatter:on
		if (parts[0].contains(" ")) {
			// log.error("Transposing author: {} for part: {}", author, parts[0]);
			parts[0] = parts[0].substring(0,1).toUpperCase() + parts[0].substring(1);
			String[] lastNameParts2 = parts[0].split(" ");
			List<String> lastNameParts = new ArrayList<>(Arrays.asList(lastNameParts2));
			String lastPart = lastNameParts.remove(lastNameParts.size() - 1);
			matcher = lastNameAdditionsPattern.matcher(lastPart);
			if (matcher.find()) {
				lastPart = lastNameParts.remove(lastNameParts.size() - 1);
			}
			if (!lastNameParts.isEmpty()) {
				initials += lastNameParts.stream().collect(Collectors.joining());
				initials = initials.replaceAll("[^A-Z]", "");
			}
			authorsAreTransposed = true;
			log.debug( "Author {} is transposed as {} {}", author, lastPart, initials);
			this.authorsTransposed.add(lastPart + " " + initials);
		} else {
			this.authorsTransposed.add(parts[0] + " " + initials);
		}
		
		return;
	}
	
	public Map<String, Integer> addDois(String doi) {
		if (doi.length() > 100) {  // Scopus records sometimes add Cited references in this field
			return dois;
		}
		try {
			doi = URLDecoder.decode(doi, "UTF8");
			doi = StringEscapeUtils.unescapeHtml4(doi);
		} catch (UnsupportedEncodingException e) {
			log.info(e.getMessage());
			e.printStackTrace();
		}
		Matcher matcher = doiPattern.matcher(doi.toLowerCase());
		while (matcher.find()) {
			String group = matcher.group(1);
			dois.put(group.replaceAll("\\.$", ""), 1);
		}
		return dois;
	}

	public List<String> addIssns(String issn) {
		Matcher matcher = issnPattern.matcher(issn.toUpperCase());
		while (matcher.find()) {
			String group = matcher.group(1);
			if (group.length() == 8) {
				group = group.substring(0, 4) + "-" + group.substring(4, 8);
			} else if (group.length() > 9) {
				// FIXME: convert ISBN-10 to ISBN-13?
				group = group.replaceAll("-", "");
			}
			issns.add(group);
		}
		return issns;
	}

	public Set<String> addJournals(String journal) {
		/*
		 * replace "\S/\S" with space: "Hematology/Oncology" --> "Hematology Oncology"
		 * BUT: "Chung-Kuo Hsiu Fu Chung Chien Wai Ko Tsa Chih/Chinese Journal of Reparative & Reconstructive Surgery" will NOT be split into 2 journals!
		 */
		Matcher matcher = journalSlashPattern.matcher(journal);
		while (matcher.find()) {
			journal = matcher.group(1) + " " + matcher.group(2);
		}
		
		String[] parts = journal.split("(\\[|\\]|=|/)");
		List<String> list = new ArrayList<String>(Arrays.asList(parts));
		/*
		 *  Double journals with ":"
		 *
		 * Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine
		 * becomes
		 * - Clinical Medicine Insights Circulatory, Respiratory and Pulmonary Medicine
		 * - Clinical Medicine Insights: Circulatory, Respiratory and Pulmonary Medicine
		 */
		List<String> additional = new ArrayList<>();
		for (String j : list) {
			if (j.contains(":")) {
				additional.add(j.replaceAll(":", " "));
			}
		}
		matcher = supplementPattern.matcher(journal);
		if (matcher.find()) {
			System.err.println("SupplementMatcher fired for: " + journal);
			additional.add(matcher.replaceAll(""));
		}
		
		if (! additional.isEmpty()) {
			list.addAll(additional);
		}
		/*
		 * Add the separate parts of:
		 * - ... [...]:		Zhonghua wai ke za zhi [Chinese journal of surgery]
		 * - ... / ...:		The Canadian Journal of Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques
		 * - ... = ...: 	Zhen ci yan jiu = Acupuncture research
		 */
		for (String j : list) {
			j = j.trim();
			if (! j.isEmpty() && ! excludedJournalsParts.contains(j.toLowerCase())) {
				String normalized = normalizeJournalJava8(j);
				if (! normalized.isEmpty()) {
					journals.add(normalized);
				}
			}
		}
		log.debug("Result for {}: {}", journal, journals);
		return journals;
	}
	
	public void addTitles(String title) {
		String normalized = normalizeJava8(title);
		if (!titles.contains(normalized)) {
			titles.add(normalized);
		}
	}
	
	public void parsePages(String pages) {
		if (pages == null || pages.isEmpty()) {
			return;
		}
		
		// @formatter:off
		/*
		 *  C7 (Article Number) should sometimes overrule / overwrite SP (starting and ending page)
		 *  because when C7 is present, SP often contains the number of pages, and in a few cases relative pages (1-10 for a 10 pages article).
		 *  
		 *  SE (Starting Endingd Page) should sometimes overrule / overwrite SP (SE: 1746-1746, SP: 19)
		 *  
		 *  C7 and SE occur before the SP field in an EndNote RIS file.
		 *  
		 *  Solution:
		 *  - treat C7 as pages  
		 *  - if C7 has already been called AND SE is a range of pages, then SE can overwrite the C7 data.
		 *  - if C7 or SE has already been called AND SP is a range of pages (e.g. C7: Pmid 29451177, and SP: 3-4)
		 *    then SP can overwrite the C7 data.
		 *  
		 * TODO: Should range of pages starting with "1-" be excluded? But type "1-234" occurs with books.
		 */
		// @formatter:on
		if (pageStartForComparison != null && !pages.contains("-")) {
			return;
		}
		
		 // FIXME: pageStart and pageEnd are not necessary, 1 field should be enough for output?
		 // FIXME: "UNSP ..." should be cleaned from the C7 field (WoS)? 
		pages = pages.replaceAll("(\\u2010|\\u00ad)", "-");	// Cochrane uses hyphen characters instead of minus
		// normalize starting page: W-22 --> 22, 22S --> 22, F22 --> 22
		pages = pages.replaceAll("^([^\\d]+)\\-(\\d+)", "$1$2");
		if (pages.contains("-")) {
			int indexOf = pages.indexOf("-");
			this.pageStart = pages.substring(0, indexOf);
			this.pageEnd = pages.substring(indexOf + 1);
			if (this.pageStart.length() > this.pageEnd.length()) {
				this.pageEnd = this.pageStart.substring(0, this.pageStart.length() - this.pageEnd.length()) + this.pageEnd;
			}
		} else {
			this.pageStart = pages;
		}
		// FIXME: compile a pattern 
		if (pageStart.matches(".*\\d+.*")) {
			pageStart = pageStart.replaceAll("^([^\\d]*)([\\d]+)(.*)$", "$2");
			this.pageStartForComparison = pageStart;
		}
	}


	public void addReversedTitles() {
		List<String> reversed = new ArrayList<>();
		if (titles.isEmpty()) {
			titles.add(this.id);
		}
		for (String t : titles) {
			reversed.add(new StringBuilder(t).reverse().toString());
		}
		titles.addAll(reversed);
	}

	public void fillAllAuthors( ) {
		if (authors.isEmpty()) {
			return;
		}

		String s = authors.stream().collect(Collectors.joining("; "));
		allAuthors.add(s);
		// DONT: lowercasing the names makes different authors closer to 1.0
		//allAuthors = allAuthors.toLowerCase();
		
		if (authorsAreTransposed) {
			allAuthors.add(authorsTransposed.stream().collect(Collectors.joining("; ")));
		}
	}
	
	/*
	 * getters and setters
	 */

//	public void setPublicationYear(String publicationYear) {
//		this.publicationYear = Integer.valueOf(publicationYear);
//	}
}
