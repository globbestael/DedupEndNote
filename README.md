# DedupEndNote
Deduplication of EndNote RIS files

Possible actions:
- deduplicate one file
- deduplicate two files (NEW-RECORDS and OLD-RECORDS): which deduplicated records occur in NEW-RECORDS?
- mark the duplicates of one file

## Why DedupEndNote?
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

## What does DedupEndNote do?
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
  * Insufficient data: If one or both DOIs are missing and one or both of the starting pages are missing, the answer is YES.
    This is important because of PubMed ahead of print publications.
3. __Authors:__ Is the <a href="https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance" target="_new">Jaro-Winkler similarity</a> of the authors > 0.67?<br/>
  * Preprocessing: The author "Anonymous," is treated as no author.
  * Preprocessing: First names are reduced to initials ("Moorthy, Ranjith K." to "Moorthy, R. K.").
  * Preprocessing: All authors from each record are joined by "; ".
  * Insufficient data: If one or both records have no authors, the answer is YES
    (except if one of the records is a reply (see below) and one of the records has no starting page or DOI).
4. __Title:__ Is the <a href="https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance" target="_new">Jaro-Winkler similarity</a>
   of (one of) the normalized titles > 0.9?<br/>
The fields Original publication (OP), Short Title (ST), Title (TI) and sometimes Book section (T3, see below) are treated as titles.
Because the Jaro-Winkler similarity algorithm puts a heavy penalty on differences at the beginning of a string, the normalized titles are also reversed.
  * Preprocessing: The titles are normalized (converted to lower case, text between "<...>" removed,
    all characters which are not letters or numbers are replaced by a space character, ...).
  * Insufficient data: If one of the records is a reply (see below), the titles are not compared / the answer is YES
    (but the Jaro-Winkler similarity of the authors should be > 0.75 and the comparison between the journals is more strict).
> *Reply:* a publication is considered a reply if the title (field TI) contains "reply", or contains "author(...)respon(...)",
or is nothing but "response" (all case insensitive).

> *T3 field:* Especially EMBASE (OVID) uses this field for (1) Conference title (majority of cases),
(2) an alternative journal title, and (3) original (non English) title.
Case 1 (identified as containing a number or "Annual", "Conference", "Congress", "Meeting" or "Society") is skipped.
All other T3 fields are treated as Journals and as titles.


5. __ISSN or Journal:__ Are they the same (ISSN) or similar (Journal)?<br/>
The fields Journal / Book Title (T2), Alternate Journal (J2) and sometimes Book section (T3, see below) are treated as journals, ISBNs as ISSNs.
All ISSns and journal titles (including abbreviations) in the records are used.
Abbreviated and full journal titles are compared in a sensible way (see examples below).
If the ISSns are different or one or both records have no ISSN, the journals are compared.
  * Preprocessing: ISSNs are normalized (dash between position 4 and 5 for ISSN, no dashes in ISBNs, both uppercased).
  * Preprocessing: Journal titles of the form "Zhonghua wai ke za zhi [Chinese journal of surgery]" or
    "Zhonghua wei chang wai ke za zhi = Chinese journal of gastrointestinal surgery" or
    "The Canadian Journal of Neurological Sciences / Le Journal Canadien Des Sciences Neurologiques" are split into 2 journal titles.
  * Preprocessing: the journal titles are normalized (hyphens, dots and apostrophes are replaced with space,
    end part between round or square brackets is removed, initial article is removed, ...).

If two records get 5 YES answers, they are considered duplicates.
Only the first record of a set of duplicate records is copied to the output file.

Moreover: When writing the output file, the following fields can be changed:

  * Author (AU):
    * if the (only) author is "Anonymous", the author is omitted
  * DOI (DO):
    * the DOIs of the removed duplicate records are copied to the saved record and deduplicated.
      The DOI field is important for finding the full text in EndNote.
    * DOIs of the form "10.1038/ctg.2014.12", "http://dx.doi.org/10.1038/ctg.2014.12", ... are rewritten
      in the prescribed form "https://doi.org/10.1038/ctg.2014.12". DOIs of this form are clickable links in EndNote.
  * Publication year (PY):
    * if the saved record has no value for its Publication year but one of the removed duplicate records has,
      the first not empty Publication year of the duplicates is copied to the saved record.
  * Starting page (SP) and Article Number (C7):
    * the article number is put in the Pages field (SP) if the Pages field is empty or does not contain a "-", overwriting the Pages field content.
    * the article number field (C7) is omitted
    * if the saved record has no value for its Pages field (e.g. PubMed ahead of print publications) but one of the removed duplicate records has,
      the first not empty pages of the duplicates are copied to the saved record.
    * the Pages field gets an unabbreviated form: e.g. "482-91" is rewritten as "482-491".
    * if the ending page is the same as the starting page, only the starting page is written ("192" instead of "192-192").
  * Title (TI):
    * If the publication is a reply, the title is replaced with the longest title from the duplicates
      (e.g. "Reply from the authors" is replaced by "Coagulation parameters and portal vein thrombosis in cirrhosis Reply")
      
The output file is a new RIS file which can be imported into a new EndNote database.

DedupEndNote is slower than EndNote in deduplicating records because its comparisons are more time consuming.
EndNote can deduplicate a EndNote database of ca. 15,000 records in less dan 5 seconds. DedupEndNote needs
around 20 seconds to deduplicate the export file in RIS format (115MB).


## Performance

Data are from:

* [SRA] Rathbone, J., Carter, M., Hoffmann, T. et al.
  Better duplicate detection for systematic reviewers: evaluation of Systematic Review Assistant-Deduplication Module.
  Syst Rev 4, 6 (2015). [https://doi.org/10.1186/2046-4053-4-6](https://doi.org/10.1186/2046-4053-4-6)<br>
  The data sets are available at [https://osf.io/dyvnj/](https://osf.io/dyvnj/)
* [McKeown] McKeown, S., Mir, Z.M.
  Considerations for conducting systematic reviews: evaluating the performance of different methods for de-duplicating references.
  Syst Rev 10, 38 (2021). [https://doi.org/10.1186/s13643-021-01583-y](https://doi.org/10.1186/s13643-021-01583-y)
* [BIG_SET] Own test database for DedupEndNote on portal vein thrombosis (52,828 records, with 3647 records validated)


<table border="1">
	<colgroup>
		<col></col>
		<col></col>
		<col span="7" style="text-align:right">
	</colgroup>
    <thead>
        <tr>
            <th>Name</th>            <th>Tool</th>
            <th>True pos</th>
            <th>False neg</th>
            <th>Sensitivity</th>
            <th>True neg</th>
            <th>False pos</th>
            <th>Specificity</th>
            <th>Accuracy</th>
        </tr>
    </thead>
    <tbody>
        <tr>
            <td rowspan="3">SRA: Cytology screening<br>1856 rec</td>
            <td>EndNote</td>
            <td style="text-align: right;">885</td>
            <td style="text-align: right;">518</td>
            <td style="text-align: right;">61.3%</td>
            <td style="text-align: right;">452</td>
            <td style="text-align: right;">1</td>
            <td style="text-align: right; color: red;">99.8%</td>
            <td style="text-align: right;">72.0%</td>
        </tr>
        <tr>
            <td>SRA-DM</td>
            <td style="text-align: right;">1265</td>
            <td style="text-align: right;">139</td>
            <td style="text-align: right;">90.1%</td>
            <td style="text-align: right;">452</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right;">92.5%</td>
        </tr>
        <tr>
            <td>DedupEndNote</td>
            <td style="text-align: right;">1360</td>
            <td style="text-align: right;">60</td>
            <td style="text-align: right; font-weight: bold;">95.8%</td>
            <td style="text-align: right;">436</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td xxstyle="text-align: right; font-weight: bold;" align="right"><strong>96.8%</strong></td>
        </tr>
		<tr>
			<td cellspan="9" style="height: 15px;"></td>
		</tr>
        <tr>
            <td rowspan="3">SRA: Haematology (1415 rec)</td>
            <td>EndNote</td>
            <td style="text-align: right;">159</td>
            <td style="text-align: right;">87</td>
            <td style="text-align: right;">64.6%</td>
            <td style="text-align: right;">1165</td>
            <td style="text-align: right;">4</td>
            <td style="text-align: right; color: red;">99.7%</td>
            <td style="text-align: right;">93.6%</td>
        </tr>  
        <tr>
            <td>SRA-DM</td>
            <td style="text-align: right;">208</td>
            <td style="text-align: right;">38</td>
            <td style="text-align: right;">84.6%</td>
            <td style="text-align: right;">1169</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right;">97.3%</td>
        </tr>
        <tr>
            <td>DedupEndNote</td>
            <td style="text-align: right;">222</td>
            <td style="text-align: right;">14</td>
            <td style="text-align: right; font-weight: bold;">94.1%</td>
            <td style="text-align: right;">1179</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right; font-weight: bold;">99.0%</td>
        </tr>  
		<tr>
			<td cellspan="9" style="height: 15px;"></td>
		</tr>
        <tr>
            <td rowspan="3">SRA: Respiratory<br>1988 rec</td>
            <td>EndNote</td>
            <td style="text-align: right;">410</td>
            <td style="text-align: right;">391</td>
            <td style="text-align: right;">51.2%</td>
            <td style="text-align: right;">1185</td>
            <td style="text-align: right;">2</td>
            <td style="text-align: right; color: red;">99.8%</td>
            <td style="text-align: right;">80.2%</td>
        </tr>
        <tr>
            <td>SRA-DM</td>
            <td style="text-align: right;">674</td>
            <td style="text-align: right;">125</td>
            <td style="text-align: right;">84.4%</td>
            <td style="text-align: right;">1189</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right;">93.7%</td>
        </tr>  
        <tr>
            <td>DedupEndNote</td>
            <td style="text-align: right;">758</td>
            <td style="text-align: right;">42</td>
            <td style="text-align: right; font-weight: bold;">94.8%</td>
            <td style="text-align: right;">1188</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right; font-weight: bold;">97.9%</td>
        </tr>
		<tr>
			<td cellspan="9" style="height: 15px;"></td>
		</tr>
        <tr>
            <td rowspan="3">SRA: Stroke<br>1292 rec</td>
            <td>EndNote</td>
            <td style="text-align: right;">372</td>
            <td style="text-align: right;">134</td>
            <td style="text-align: right;">73.5%</td>
            <td style="text-align: right;">784</td>
            <td style="text-align: right;">2</td>
            <td style="text-align: right; color: red;">99.7%</td>
            <td style="text-align: right;">89.5%</td>
        </tr>  
        <tr>
            <td>SRA-DM</td>
            <td style="text-align: right;">426</td>
            <td style="text-align: right;">81</td>
            <td style="text-align: right;">84.0%</td>
            <td style="text-align: right;">785</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right;">93.7%</td>
        </tr>
        <tr>
            <td>DedupEndNote</td>
            <td style="text-align: right;">504</td>
            <td style="text-align: right;">6</td>
            <td style="text-align: right; font-weight: bold;">98.8%</td>
            <td style="text-align: right;">782</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right; font-weight: bold;">99.5%</td>
        </tr> 
		<tr>
			<td cellspan="9" style="height: 15px;"></td>
		</tr>
        <tr>
            <td rowspan="7">McKeown<br>3130 rec</td>
            <td>OVID</td>
            <td style="text-align: right;">1982</td>
            <td style="text-align: right;">90</td>
            <td style="text-align: right;">95.7%</td>
            <td style="text-align: right;">1058</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right;">97.1%</td>
        </tr>
        <tr>
            <td>EndNote</td>
            <td style="text-align: right;">1541</td>
            <td style="text-align: right;">531</td>
            <td style="text-align: right;">74.4%</td>
            <td style="text-align: right;">850</td>
            <td style="text-align: right;">208</td>
            <td style="text-align: right; color: red;">80.3%</td>
            <td style="text-align: right;">76.4%</td>
        </tr>
        <tr>
            <td>Mendeley</td>
            <td style="text-align: right;">1877</td>
            <td style="text-align: right;">195</td>
            <td style="text-align: right;">90.6%</td>
            <td style="text-align: right;">1041</td>
            <td style="text-align: right;">17</td>
            <td style="text-align: right; color: red;">98.4%</td>
            <td style="text-align: right;">93.2%</td>
        </tr>
        <tr>
            <td>Zotero</td>
            <td style="text-align: right;">1473</td>
            <td style="text-align: right;">599</td>
            <td style="text-align: right;">71.1%</td>
            <td style="text-align: right;">1038</td>
            <td style="text-align: right;">20</td>
            <td style="text-align: right; color: red;">98.1%</td>
            <td style="text-align: right;">80.2%</td>
        </tr>
        <tr>
            <td>Covidence</td>
            <td style="text-align: right;">1952</td>
            <td style="text-align: right;">120</td>
            <td style="text-align: right;">94.2%</td>
            <td style="text-align: right;">1056</td>
            <td style="text-align: right;">2</td>
            <td style="text-align: right; color: red;">99.8%</td>
            <td style="text-align: right;">96.1%</td>
        </tr>
        <tr>
            <td>Rayyan</td>
            <td style="text-align: right;">2023</td>
            <td style="text-align: right;">49</td>
            <td style="text-align: right; font-weight: bold;">97.6%</td>
            <td style="text-align: right;">1006</td>
            <td style="text-align: right;">52</td>
            <td style="text-align: right; color: red;">95.1%</td>
            <td style="text-align: right;">96.8%</td>
        </tr>
        <tr>
            <td>DedupEndNote</td>
            <td style="text-align: right;">2002</td>
            <td style="text-align: right;">70</td>
            <td style="text-align: right;">96.6%</td>
            <td style="text-align: right;">1058</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right; font-weight: bold;">97.8%</td>
        </tr>
		<tr>
			<td cellspan="9" style="height: 15px;"></td>
		</tr>
        <tr>
            <td>BIG_SET<br>3647 rec</td>
            <td>DedupEndNote</td>
            <td style="text-align: right;">2779</td>
            <td style="text-align: right;">148</td>
            <td style="text-align: right;">94.9</td>
            <td style="text-align: right;">720</td>
            <td style="text-align: right;">0</td>
            <td style="text-align: right;">100.0%</td>
            <td style="text-align: right;">95.9%</td>
        </tr>
    </tbody>
</table>

## Limitations
* Input file size: The maximum size of the input file is limited to 150MB.
* Encoding: The program assumes that the input file is encoded as UTF-8.
* If authors AND (all) titles AND (all) journal names for a record use a non-Latin script, results for this record may be inaccurate.
* The input file must be an export from ONE EndNote database: the ID fields are used internally for identifying the records, so they have to be unique.
* The program has been developed and tested for biomedical databases (PubMed, EMBASE, ...) and some general databases (Web of Science, Scopus). Deduplicating records from other databases is not garanteed to work.
* Records for each publication year are compared to records from the same and the following year: a record from 2016 is compared to the records from 2015 (when treating the records from 2015) and from 2016 and 2017 (when treating the records from 2016). A PubMed ahead-of-print record from 2013 and a corresponding record from 2017 (when it was 'officially' published) will not be compared (and possibly deduplicated).
* Bibliographic databases are not always very accurate in the starting page of a publication. Because starting page is part of the comparisons, DedupEndNote misses the duplicates when bibliographic databases don't agree on the starting page (and one or both records have no DOIs).