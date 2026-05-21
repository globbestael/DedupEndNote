# DedupEndNote

A web application that deduplicates **Bibliographic Items** exported from reference managers, identifying **Duplicate Sets** and either removing or marking duplicates in the output.

## Language

### Core concepts

**Publication**:
A real-world scholarly item — book, journal article, conference abstract — that exists independently of any database. A conference abstract (also called meeting abstract) is considered a Publication even though it has only an abstract and no full text, because it is published in a journal before any Bibliographic Item for it is created.
_Avoid_: bibliographic item, record, reference, citation

**Bibliographic Item**:
A database record containing partial information copied from a Publication (author names, title, journal, pages, abstract — but not full text); the central object DedupEndNote operates on. The recorded information may differ from the original Publication because databases apply their own conventions (e.g. full author names reduced to initials). A Publication exists before any Bibliographic Item for it is created. _Code term_: `Publication`.
_Avoid_: publication (reserved for the real-world item), record, reference, citation

**Duplicate**:
A symmetric relation between two Bibliographic Items that represent the same Publication.
_Avoid_: match, copy

**Duplicate Set**:
A group of two or more Bibliographic Items that are all duplicates of each other.
_Avoid_: group, cluster

**Kept Bibliographic Item**:
The Bibliographic Item in a Duplicate Set that is retained in the output and is the subject of Enrichment in Remove Mode.
_Avoid_: primary, representative, canonical, first record, kept publication

**Label**:
The value written by DedupEndNote in Mark Mode to identify which Duplicate Set a Bibliographic Item belongs to; contains the ID of the Kept Bibliographic Item and overwrites the RIS LB field.
_Avoid_: marker, tag

### Input sources

**Bibliographic database**:
A read-only external source of Bibliographic Items (PubMed, EMBASE, Cochrane Library, Web of Science, ClinicalTrials.gov, etc.) that a researcher can search and export from but cannot add to or modify.
_Avoid_: source, repository, database

**Reference manager database**:
A read-write database created with reference manager software (EndNote, Zotero) containing Bibliographic Items; researchers import from Bibliographic databases or add items manually, and can modify and export items. DedupEndNote's input files are RIS exports from a Reference manager database.
_Avoid_: reference library, library, database

### Modes

**Mark Mode**:
An operating mode where all Bibliographic Items are written to output and duplicates are identified by their Label.
_Avoid_: label mode, marking mode

**Remove Mode**:
An operating mode where duplicate Bibliographic Items are removed from output and the Kept Bibliographic Item is Enriched.
_Avoid_: deduplicate mode, filter mode, default mode

**Single-file**:
A configuration where deduplication is performed within one RIS file.
_Avoid_: single input, one-file

**Two-file**:
A configuration where New Bibliographic Items are compared against Old Bibliographic Items and only the new file is output.
_Avoid_: dual-file, two-input

**New Bibliographic Items**:
The Bibliographic Items in a Two-file run that are being checked for duplicates against the Old Bibliographic Items.
_Avoid_: candidate publications, new records, new publications

**Old Bibliographic Items**:
The Bibliographic Items in a Two-file run that serve as the reference set against which New Bibliographic Items are compared.
_Avoid_: existing publications, old records, old publications, OLD_RECORDS

### Processes

**Normalization**:
The standardisation of Bibliographic Item field values (case, encoding, punctuation, identifier variants) to a form that enables reliable comparison.
_Avoid_: cleaning, preprocessing, standardisation

**Enrichment**:
The process of supplementing a Kept Bibliographic Item with data (DOI, year, pages, title) from its duplicates; occurs only in Remove Mode.
_Avoid_: merging, augmentation

### Input / Output

**RIS**:
The file format used for input and output; specifically the EndNote RIS variant, with the Zotero RIS variant also accepted.
_Avoid_: EndNote RIS, RIS format

### Bibliographic Item types

These categories characterise Bibliographic Items based on the type of Publication they represent. The deduplication algorithm applies different comparison rules depending on the category.

**Reply**:
A Bibliographic Item representing a Publication that is a reaction to another publication, including letters to the editor and author responses; currently the umbrella term covering both sub-types.
_Avoid_: response (synonym for Reply), letter (a distinct sub-type not yet promoted to its own category)

**Cochrane Review**:
A Bibliographic Item representing a systematic review from the Cochrane Library; uses DOI-only matching when both Bibliographic Items in a pair have a DOI.
_Avoid_: Cochrane publication, Cochrane record

**Phase Publication**:
A Bibliographic Item representing a clinical trial phase publication; uses more lenient title matching thresholds.
_Avoid_: phase trial, trial publication

**ClinicalTrials.gov Publication**:
A Bibliographic Item originating from the ClinicalTrials.gov bibliographic database; titles are automatically considered matching due to format variation across databases.
_Avoid_: trial record, CT.gov record

### Validation

**Sensitivity**:
The proportion of true Duplicate pairs correctly identified by the deduplication algorithm.
_Avoid_: recall, true positive rate

**Specificity**:
The proportion of true non-Duplicate pairs correctly identified as non-matching.
_Avoid_: true negative rate

**True Positive**:
A pair of Bibliographic Items correctly identified as Duplicates.
_Avoid_: correct match

**True Negative**:
A pair of Bibliographic Items correctly identified as non-Duplicates.
_Avoid_: correct non-match

**False Positive**:
A pair of Bibliographic Items incorrectly identified as Duplicates.
_Avoid_: incorrect match, over-deduplication

**False Negative**:
A pair of Bibliographic Items incorrectly identified as non-Duplicates.
_Avoid_: missed duplicate, under-deduplication

## Relationships

- A **Bibliographic Item** is a database record derived from a **Publication**
- A **Bibliographic Item** originates in a **Bibliographic database**, is imported into a **Reference manager database** (possibly with field transformations), and is exported in **RIS** format as input to DedupEndNote; alternatively it is added manually to a **Reference manager database**
- A **Duplicate Set** contains two or more **Bibliographic Items**
- A **Duplicate Set** has exactly one **Kept Bibliographic Item**
- **Enrichment** applies only in **Remove Mode** and only to the **Kept Bibliographic Item**
- In **Mark Mode**, every Bibliographic Item in a Duplicate Set receives the same **Label** (the ID of the Kept Bibliographic Item)
- **Normalization** precedes all field comparisons
- In **Two-file**, **Old Bibliographic Items** are never written to output; only **New Bibliographic Items** are output

## Example dialogue

> **Dev:** "Are two Bibliographic Items duplicates if the title fields are identical?"
> **Domain expert:** "Not necessarily — Bibliographic Items from different databases may record the same Publication differently (truncated authors, abbreviated journals, variant page formats). That is why Normalization runs first: it reduces incidental variation so the comparison reflects whether both items represent the same Publication, not whether their raw fields match."

> **Dev:** "In Two-file mode, can an Old Bibliographic Item be Enriched?"
> **Domain expert:** "No — Enrichment only applies to the Kept Bibliographic Item in Remove Mode, and Old Bibliographic Items are never written to output."

## Flagged ambiguities

- "Publication" was previously used to mean both the real-world scholarly item and the database record — resolved: **Publication** is the real-world item; **Bibliographic Item** is the database record. The code uses the class name `Publication` for what the domain calls a Bibliographic Item.
- "Bibliographic database" was previously used as a catch-all — resolved: **Bibliographic database** (read-only, e.g. PubMed) and **Reference manager database** (read-write, e.g. EndNote) are distinct concepts with different roles in the workflow.
- "Publication Set" was used to mean Duplicate Set — resolved: the full set of all input Bibliographic Items has no canonical term; **Duplicate Set** is the term for a group of duplicates.
- "Letter" and "Reply" were considered as separate terms — resolved for now: **Reply** is the umbrella term. Letter may be promoted to its own category in future.
- "RIS" could mean any RIS variant — resolved: in this project, RIS means specifically the EndNote RIS variant; the Zotero variant is also accepted with minor differences handled during reading.
