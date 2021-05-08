# DedupEndNote
Deduplication of EndNote RIS files

- deduplicates one file
- deduplicates two files (NEW-RECORDS and OLD-RECORDS): which deduplicated records occur in NEW-RECORDS?
- marks the duplicates in one file

##Why DedupEndNote?
Deduplication in EndNote misses many duplicate records.
Building and maintaining a Journals List within Endnote can partly solve this problem,
but there remain lots of cases where EndNote is too unforgiving when comparing records.
Some bibliographic databases offer deduplication for their own databases
(OVID: Medline and EMBASE), but this does not help PubMed, Cochrane or Web of Science users.

DedupEndNote deduplicates an EndNote RIS file and writes a new RIS file with the unique records,
which can be imported into a new EndNote database.
It is more forgiving than EndNote itself when comparing records,
but tests have shown that it identifies many more duplicates (see below under "Test results").

The program has been tested on EndNote databases with records from:

- CINAHL (EBSCOHost)
- Cochrane Library (Trials)
- EMBASE (OVID)
- Medline (OVID)
- PsycINFO (OVID)
- PubMed
- Scopus
- Web of Science

##What does DedupEndNote do?
Each pair of records is compared in 5 different ways. The general rule is:

<table border="1">
    <thead>
        <tr>
            <th>Comparison</th><th>Result</th><th>Action</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td rowspan="3">1 ... 5</td>
            <td>YES</td>
            <td rowspan="2">go to next comparison if present,<br/>else mark the records as duplicates</td>
        </tr>
        <tr>
            <td>(insufficient data for comparison)</td>
        </tr>
        <tr>
            <td>NO</td>
            <td>stop comparisons for this pair of record</td>
        </tr>
    </tbody>
</table>

The following comparisons are used (in this order, chosen for performance reasons):

1. __Publication year:__ Are they at most 1 year apart?
  * Insufficient data: Records without a publication year are compared to all records unless they have been identified as a duplicate.
2. __Starting page or DOI:__ Are they the same?<br/>
If the starting pages are different or one or both are absent, the DOIs are compared.
  * Preprocessing: Article number is treated as a starting page if starting page itself is empty or contains "-".
  * Preprocessing: Starting pages are compared only for number: "S123" and "123" are considered the same.
  * Preprocessing: In DOIs 'http://dx.doi.org/', 'http://doi.org/', ... are left out.
  * Insufficient data: If one or both DOIs are missing and one or both of the starting pages are missing, the answer is YES. This is important because of PubMed ahead of print publications.
3. __Authors:__ Is the <a href="https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance" target="_new">Jaro-Winkler similarity</a> of the authors > 0.67?<br/>
  * Preprocessing: The author "Anonymous," is treated as no author.
  * Preprocessing: First names are reduced to initials ("Moorthy, Ranjith K." to "Moorthy, R. K.").
  * Preprocessing: All authors from each record are joined by "; ".
  * Insufficient data: If one or both records have no authors, the answer is YES (except if one of the records is a reply (see below) and one of the records has no starting page or DOI).
4. __Title:__ Is the <a href="https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance" target="_new">Jaro-Winkler similarity</a> of (one of) the normalized titles > 0.9?<br/>
The fields Original publication (OP), Short Title (ST), Title (TI) and sometimes Book section (T3, see below) are treated as titles.
Because the Jaro-Winkler similarity algorithm puts a heavy penalty on differences at the beginning of a string, the normalized titles are also reversed.
  * Preprocessing: The titles are normalized (converted to lower case, text between "<...>" removed, all characters which are not letters or numbers are replaced by a space character, ...).
  * Insufficient data: If one of the records is a reply (see below), the titles are not compared / the answer is YES (but the Jaro-Winkler similarity of the authors should be > 0.75 and the comparison between the journals is more strict).
5. __ISSN or Journal:__ Are they the same (ISSN) or similar (Journal)?<br/>
The fields Journal / Book Title (T2), Alternate Journal (J2) and sometimes Book section (T3, see below) are treated as journals, ISBNs as ISSNs. All ISSns and journal titles (including abbreviations) in the records are used.
Abbreviated and full journal titles are compared in a sensible way (see examples below).
If the ISSns are different or one or both records have no ISSN, the journals are compared.
  * Preprocessing: ISSNs are normalized (dash between position 4 and 5 for ISSN, no dashes in ISBNs, both uppercased).
  * Preprocessing: Journal titles of the form "Zhonghua wai ke za zhi [Chinese journal of surgery]" or "Zhonghua wei chang wai ke za zhi = Chinese journal of gastrointestinal surgery" or "The Canadian Journal of Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques" are split into 2 journal titles.
  * Preprocessing: the journal titles are normalized (hyphens, dots and apostrophes are replaced with space, end part between round or square brackets is removed, initial article is removed, ...).
    
If two records get 5 YES answers, they are considered duplicates.
Only the first record of a set of duplicate records is copied to the output file.

Moreover: When writing the output file, the following fields can be changed:

  * Author (AU):
    * if the (only) author is "Anonymous", the author is omitted
  * DOI (DO):
    * the DOIs of the removed duplicate records are copied to the saved record and deduplicated. The DOI field is important for finding the full text in EndNote.
    * DOIs of the form "10.1038/ctg.2014.12", "http://dx.doi.org/10.1038/ctg.2014.12", ... are rewritten in the prescribed form "https://doi.org/10.1038/ctg.2014.12". DOIs of this form are clickable links in EndNote.
  * Publication year (PY):
    * if the saved record has no value for its Publication year but one of the removed duplicate records has, the first not empty Publication year of the duplicates is copied to the saved record.
  * Starting page (SP) and Article Number (C7):
    * the article number is put in the Pages field (SP) if the Pages field is empty or does not contain a "-", overwriting the Pages field content.
    * the article number field (C7) is omitted
    * if the saved record has no value for its Pages field (e.g. PubMed ahead of print publications) but one of the removed duplicate records has, the first not empty pages of the duplicates are copied to the saved record.
    * the Pages field gets an unabbreviated form: e.g. "482-91" is rewritten as "482-491".
    * if the ending page is the same as the starting page, only the starting page is written ("192" instead of "192-192").
  * Title (TI):
    * If the publication is a reply, the title is replaced with the longest title from the duplicates (e.g. "Reply from the authors" is replaced by "Coagulation parameters and portal vein thrombosis in cirrhosis Reply")
      
The output file is a new RIS file which can be imported into a new EndNote database.

DedupEndNote is slower than EndNote in deduplicating records because its comparisons are more time consuming. EndNote can deduplicate a EndNote database of ca. 15,000 records in less dan 5 seconds. DedupEndNote needs around 20 seconds to deduplicate the export file in RIS format (115MB).

> *T3 field:* Especially EMBASE (OVID) uses this field for (1) Conference title (majority of cases), (2) an alternative journal title, and (3) original (non English) title. Case 1 (identified as containing a number or "Annual", "Conference", "Congress", "Meeting" or "Society") is skipped. All other T3 fields are treated as Journals and as titles.

> *Reply:* a publication is considered a reply if the title (field TI) contains "reply", or contains "author(...)respon(...)", or is nothing but "response" (all case insensitive).

DedupEndNote has borrowed several ideas from: Yu Jiang, Can Lin, Weiyi Meng, Clement Yu, Aaron M. Cohen and Neil R. Smalheiser: Rule-based deduplication of article records from bibliographic databases, in: Database 2014, ID bat086, doi:10.1093/database/bat086