# TODO

## More and more recent test files?

- Emma Wilson SynGAP: https://github.com/emma-wilson/syngap-sr/tree/main
- bib-dedup: data files: https://github.com/CoLRev-Environment/bib-dedupe/tree/main/data These are files which are also used in our ValidationTest (ASYSD_*, SRA2_*)
- dedupe-sweep: data files: https://github.com/IEBH/dedupe-sweep/tree/master/test

## ClinicalTrials.gov records
- Available from
  - http://clinicaltrials.gov (has RIS export)
  - Cochrane Library
  - Embase OVID
  - Embase.com
- Deduplication will only work after preprocessing?
  - add T2 from J2 in Embase.com
  - alter Cochrane journal name of form "https://clinicaltrials.gov/show/NCT02821026" to "clinicaltrials.gov" (or should this be "https://clinicaltrials.gov")?
  - extract the ClinicalTrials.gov ID from ??? and use it as article number / starting page?
- some of the preprocessing will also be part of enrichment
- what about WHO trials (from Cochrane): http://www.who.int/trialsearch/Trial2.aspx?TrialID=EUCTR2019-001806-40-DK

## Migraine False Positives

- limit EndNote DB Migraine_ALL_Mark to "Name of database = Cochrane" and "Label > 0"
- find duplicates (with only field Label) 
- there are false positives, a.o. Label 6288, 6368, 6460, 6616, 6810, ... Are they all conference proceedings?  

## EndNote 20, EndNote 21 and EndNote 2025

- https://support.alfasoft.com/hc/en-us/articles/360018135798-De-duplication-of-references-in-EndNote (latest change 22-4-2025)
- https://endnote.com/product-details/compare-previous-versions : Deduplication by DOI and/or PMCID

## Zotero
- better testing
- change documentation
- too late to change the application name to DedupRIS?

## SRA: Systematic Review Accelerator / TERA /dedupe-sweep
- https://sr-accelerator.com/#/deduplicator
- code: https://github.com/IEBH/dedupe-sweep
- data files: https://github.com/IEBH/dedupe-sweep/tree/master/test
- publication: Forbes, C., Greenwood, H., Carter, M. et al. Automation of duplicate record detection for systematic reviews: Deduplicator. Syst Rev 13, 206 (2024). https://doi.org/10.1186/s13643-024-02619-9 https://systematicreviewsjournal.biomedcentral.com/articles/10.1186/s13643-024-02619-9#availability-of-data-and-materials 
- Upgraded in TERA: https://tera-tools.com/ Has th deupliator been changed in TERA?

## Retractions

- Retracted publications: BIG_SET 7921 set and 42247
- Article: Shi, Qianling et al., More Consideration is Needed for Retracted Non-Cochrane Systematic Reviews in Medicine: A Systematic Review,
  in: Journal of Clinical Epidemiology, Volume 0, Issue 0
  https://www.jclinepi.com/article/S0895-4356(21)00198-0/fulltext#relatedArticles

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

## Cochrane reviews

What is preferred way?
- user should not lose new updates (see also living reviews)
- since it is is not possible to apply this rule only for the last versions, it applies always
 
Prefer DOI above starting page because different versions of a review have different DOIs.
==> there should be a boolean Cochrane attribute
Question: are there other journals with similar versions of articles?
Current practice of comparing starting page (Cd003229) above DOI (10.1002/14651858.cd003229.pub3) merges different versions of same or consecutive years

The BIG_SET database has no examples of Cochrane reviews from Medline_OVID!
The ASySD_SRSR_Human database has cases for database "Ovid Technologies":
- issue: CD005488, starting page: empty, DOI: http://dx.doi.org/10.1002/14651858.CD005488.pub2
- issue: empty, starting page: CD008237, DOI: empty   (is this EMBASE_OVID?)

Using DOI before starting page won't help in all cases:
- if Cochrane AND starting page empty AND issue LIKE 'CD.*' ==> use ISSUE to starting page (and keep all characters, don't limit to numbers)
- add PY to starting page ==> with absent DOIs no more merges over consecutive years

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