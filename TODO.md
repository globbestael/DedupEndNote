# TODO

## Performance
- if both are isSeveralPages = false, then use a higher threshold for authors and/or title?
- FP in BIG_SET for 8111 - 36423: 1. SP same, 2 Same because one AU is empty, NO 3 (TI comparison) because reply, 4 same Journal. One of PY = 0
+ TI normalization: 
  + TI  - \d+[.:]\d* remove first part. Or should if there is a starting number, a second title without the number be added?
  + TI  - Reprint( of)?
  + TI  - .+(\(Reprinted.*$)
  + TI  - .+(\(R\))
  - TI  - .+ et al: Does it make sense to split the title into 3 parts, and to remove the second part
    - part 1
    - part 2: " et al" with all text to the previous divider (start|.|:|...) an to the next divider (end||,|...)
    - part 3 
    Or maybe split all titles with " et al" by dividers, remove the parts with " et al", and join the rest with ": "
  - TI  - Images in ...\. (hepatology|clinical medicine|...)| Image of interest|Image of the month
  - TI from Psychological Abstracts / PsyCNFO(?) uses "-" for compounds AND between main title and subtitle (several examples in McKeown test file)
    Psilocybin-assisted psychotherapy for dying cancer patients-Aiding the final trip
- T2 normalization
  - t2  - [^(\r]+\d+$:  '(' for journals with "(Berlin 2002)". 
    See also: 
    - https://clinicaltrials.gov/show/NCT01326949
    - http://www.who.int/trialsearch/Trial2.aspx?TrialID=JPRN-UMIN000015319
    - Hepatology v70 suppl.1 2019
      Should end be [\d ]+
    - Advances in Internal Medicine, Vol 42
    - PROCEEDINGS OF THE ASME INTERNATIONAL MECHANICAL ENGINEERING CONGRESS AND EXPOSITION, 2013, VOL 15
      Should it be a while loop
    - Non-Viral Vectors for Gene Therapy, Second Edition: Part 2
    - NTP Research Report on Systematic Literature Review on the Effects of Fluoride on Learning and Memory in Animal Studies: Research Report 1
    - The number can be added from the VL field: "T2  - Schizophrenia Bulletin" and "VL  - 45 (Supplement 2)"
  - Health Technology Assessment (Pubmed; "Health Technol Assess"[Journal]) don't accept 1 or Roman numeral as starting page
 - T3: 
   - how many cases are there with Alternative journal name as content? There are examples in BIG_SET for PsycINFO_OVID records
     For WoS and PsycINFO chapters in a book within a series, the book title is T2, the book series is T3. Other databases often have only the
     book series as "journal" (T2)
     What is performance if this option is left out?
     Can the presence/absence of an ISSN be used to distinguish original title from journal name? Probably not
     Can the presence/absence of an ISBN be used to distinguish original title from journal name? Probably.
     There is an attempt in IOService::readPublications for the T3 field to solve this, but this doesn't work very well for the McKeown test file.
     However, that McKeown test file has problems (because the original RIS files weren't imported correctly?) See Github issue 53

     The other test files(e.g. BIG_TEST) also have cases with T3. The sheer number of cases with Proceedings titles in T3, makes it difficult
     to see the other cases:
      - It looks as if T3 for original title was an old Medline rule (before the OT field???). Should that have any influence on the choices for 
      DedupEndNote?
      - The series title for book chapters is necessary? Especially for Scopus records?
      - PsycINFO with name of journal predecessors complicates it
- FP: in TIL_Zotero there is a case where both publications have a different DOI and in step 4b the ISSNs are compared, resulting in a FP.
  - strangely enough 1-2 N, 1-3 Y, 1-4 Y, 1-5 Y, then 2-3 N, 2-4 N, both because 1. DOIs and starting pages are NOT the same,
    then 2-5 YES with 1. Starting pages are the same, but for 2 "BSR20211218" and for 5 "BSR20211218C"
  
## Spring Boot 4.0
- see also https://docs.openrewrite.org/recipes/java/spring/boot4
- OpenTelemetry: https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot
- https://www.youtube.com/watch?v=ENpq2J5yQ8Y


## More and more recent test files?

- Emma Wilson SynGAP: https://github.com/emma-wilson/syngap-sr/tree/main
- bib-dedup: data files: https://github.com/CoLRev-Environment/bib-dedupe/tree/main/data These are files which are also used in our ValidationTest (ASYSD_*, SRA2_*)
- dedupe-sweep: data files: https://github.com/IEBH/dedupe-sweep/tree/master/test
- problems/Conference_papers_skateboarding_20251207.txt: Conference papers from Scopus on skateboarding. 325 records, 2233 duplicates removed/
  How accurate is this given (1) this publication type and (2) most records are from the same database (that many duplicates? that many FP???)
- McKeown_2021: how good is tghe quality of data
  - what is the origin of the bibliographic errors: the bibliographic database, EndNote import filter used, ...
- SRA2_Cytology_screening: most of the FNs are caused by bibliographic errors in the pages field. 
  - what is the origin of the bibliographic errors: the bibliographic database, EndNote import filter used, ...
    Can this be tested for the OVID Medline cases?
    File / publications are quite old (2000-2012).



## Performance data
- some (a lot?) of the false positives are also bibliographic errors / are caused by bibliographic errors 
  (e.g. the DOIs are correct but the journal and pages are errors: you can also say that the data of an individual record are in conflict).
  Should all the cases count as FP's / should there be subtypes: FP - real vs FP conflict?

  In the MS Access database it could be useful to have one or more fields for this information (bibliographic_error BOOLEAN, extra_information).
  Adding these fields to every TRUTH table?
  - will be a lot of work
  - most records will have empty fields
  - if old fields are refreshed, the data in these new fields can be lost
  Wouldn't it be better to have a separate table TRUTH_errors with fields:
  - id
  - table_name
  - record_id
  - bibliographic_error BOOLEAN
  - performance_error (FP / FN)?
  - extra information
And the ..._TRUTH tables should have a column with their table_name (second join field).
For services.ValidationTest::checkAllTruthFiles 
- the truth file with the linked new table should be imported
- writeFNandFPresults should also present the information of this new table
- ??? performance table should be able to show this information???

Still there is a serious problem: take the case where 
- DB X has the correct DOI, but the wrong journal, pages, ... AND this record is the first record in the RIS export
- there are several other records with the correct DOI, journal, pages, ...
With the current settings (compare by DOI before pages if severalPages) the deduplicated record will have the good DOI, but the wrong journal, pages, ...
Problems:
- the order of the export determines the performance
  UNLESS: records with bibliographic_error are left out of the performance calculation.
  - Marking the bibliographic_error in FP ("FP: 15 (bibliographic_error: 9)) doesn't solve this problem.
  - in principle each program could have an optimal order which is different from the order of the other programs.
  - published performance data of other programs do not say which order was used?
- does this leaving out of records with bibliographic_error apply to FN as well? and TP and TN?
- what's the difference between wrong data (bibliographic_error) and missing data?
  - extreme case: is a missing DOI for an record from 1960 a bibliographic_error?
- bibliographic_error depends on the algorithm used (this is neutral, not a problem?)
  - how can one compare the performance of different programs? 
    Does this mean that all records with bibliographic_error for ANY of the programs must be left out from the test file for ALL compared programs?
- marking bibliographic_error will probably only be done for the FP and FN of DedupEndNote itself. This is unfair for the other programs.

## Migraine False Positives

- limit EndNote DB own_migarine/Migraine_ALL_Mark to "Name of database = Cochrane" and "Label > 0"
- find duplicates (with only field Label) 
- there are false positives, a.o. Label 6288, 6368, 6460, 6616, 6810, ... Are they all conference proceedings?  

## EndNote 20, EndNote 21 and EndNote 2025

- https://support.alfasoft.com/hc/en-us/articles/360018135798-De-duplication-of-references-in-EndNote (latest change 22-4-2025)
- https://endnote.com/product-details/compare-previous-versions : Deduplication by DOI and/or PMCID

## SRA: Systematic Review Accelerator / TERA /dedupe-sweep
- https://sr-accelerator.com/#/deduplicator
- code: https://github.com/IEBH/dedupe-sweep
- data files: https://github.com/IEBH/dedupe-sweep/tree/master/test
- publication: Forbes, C., Greenwood, H., Carter, M. et al. Automation of duplicate record detection for systematic reviews: Deduplicator. Syst Rev 13, 206 (2024). https://doi.org/10.1186/s13643-024-02619-9 https://systematicreviewsjournal.biomedcentral.com/articles/10.1186/s13643-024-02619-9#availability-of-data-and-materials 
- Upgraded in TERA: https://tera-tools.com/ Has th deupliator been changed in TERA?

## BIG SET

subject (portal vein thrombosis) was chosen after reading https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0071838

## Other deduplication programs

### bib-dedupe

- github: https://github.com/CoLRev-Environment/bib-dedupe
- publication: https://joss.theoj.org/papers/10.21105/joss.06318
- documentation: https://colrev-environment.github.io/bib-dedupe/index.html
- data files: https://github.com/CoLRev-Environment/bib-dedupe/tree/main/data These are files which are also used in our ValidationTest (ASYSD_*, SRA2_*)
- results: https://github.com/CoLRev-Environment/bib-dedupe/blob/main/output/current_results.md
- Authors are compared based on first letters (a.o. Chinese authors) 

## Make a command line version

## Depression
----------
- IF (but only then) there are other files where the PY has been put into the pages field: replace the pages field with null.
  If this is done, then the truth file should be adapted?
  Check on BIG_SET and subset of other files: no cases found
- "Brain research" with its sections can be solved: regex like "Brain Res.*Brain Res.*"?

## DOIs: abnormal cases

- additions to base DOI
  - eLife has DOI's for different sections: http://dx.doi.org/10.7554/eLife.06487.001 (in SRSR Human)
  - PLoS ONE has DOIs for figures: https://journals.plos.org/plosone/article/figure?id=10.1371/journal.pone.0094853.g001 (in SRSR Human)
- shortDOI: https://shortdoi.org
  - the shortdoi for 10.1016/j.appet.2008.11.008 is https://doi.org/d39x9c
  - the shortDOI Service at https://shortdoi.org only creates (or returns existings) shortDOIs. No option found to return the original DOI.
  - TODO: should DedupEndNote handle shortDOIs? And how?: 
  	- a shortDOI can never be equal to a real DOI. In DeduplicationService::compareStartPageOrDoi sufficientDois should be true only if 
  	  both are not empty AND (both are of the same type (shortDOI|realDOI))

## Logback properties
Logging configuration is spread over
- logback-spring.xml
- application(...).properties

## Multipart data upload and download
https://www.baeldung.com/spring-streaming-multipart-data