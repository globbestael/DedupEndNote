# TODO

## Performance
- if both are isSeveralPages = false, then use a higher threshold for authors and/or title?
- if both are isSeveralPages = false, then do not use the reversed title?
- if no pages, then do not use the reversed title?
- FP because label is copied from the publication that is ***not*** the pivot: see comments in DeduplicationService::compareSet
  e.g. BIG_SET 23819 - 13006, 24143 - 13006, ASySD_SRSR_Human 20282 - 36439
+ Should "^review( article)?:" be removed from the title? 180 cases in All.txt
- FP in BIG_SET for 8111 - 36423: 1. SP same, 2 Same because one AU is empty, NO 3 (TI comparison) because reply, 4 same Journal. One of PY = 0
+ FN where id = dedupId must be changed from FN=true to FN=false (and TP=true) (and correction should be moved to dedup_id?)
  This will cause a big change in the performance data
  Is there a corollary change for FP?
- NormalizationService::addTitleWithNormalization why is title a list instead of a set?

## Spring Boot 4.0
- see also https://docs.openrewrite.org/recipes/java/spring/boot4
- OpenTelemetry: https://spring.io/blog/2025/11/18/opentelemetry-with-spring-boot


## More and more recent test files?

- Emma Wilson SynGAP: https://github.com/emma-wilson/syngap-sr/tree/main
- bib-dedup: data files: https://github.com/CoLRev-Environment/bib-dedupe/tree/main/data These are files which are also used in our ValidationTest (ASYSD_*, SRA2_*)
- dedupe-sweep: data files: https://github.com/IEBH/dedupe-sweep/tree/master/test

## Performance data
- some (a lot?) of the false positives are also bibliographic errors / are caused by bibliographic errors 
  (e.g. the DOIs are correct by the journal and pages are errors: you can also say that the data of a individual record are in conflict).
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