# DedupEndNote

A web application that deduplicates bibliographic **Publications** exported from reference managers, identifying **Duplicate Sets** and either removing or marking duplicates in the output.

## Language

### Core concepts

**Publication**:
A single bibliographic record being processed for deduplication.
_Avoid_: record, reference, citation, item

**Duplicate**:
A symmetric relation between two Publications that represent the same bibliographic item.
_Avoid_: match, copy

**Duplicate Set**:
A group of two or more Publications that are all duplicates of each other.
_Avoid_: Publication Set, group, cluster

**Kept Publication**:
The Publication in a Duplicate Set that is retained in the output and is the subject of Enrichment in Remove Mode.
_Avoid_: primary, representative, canonical, first record

**Label**:
The value written by DedupEndNote in Mark Mode to identify which Duplicate Set a Publication belongs to; contains the ID of the Kept Publication and overwrites the RIS LB field.
_Avoid_: marker, tag

### Modes

**Mark Mode**:
An operating mode where all Publications are written to output and duplicates are identified by their Label.
_Avoid_: label mode, marking mode

**Remove Mode**:
An operating mode where duplicate Publications are removed from output and the Kept Publication is Enriched.
_Avoid_: deduplicate mode, filter mode, default mode

**Single-file**:
A configuration where deduplication is performed within one RIS file.
_Avoid_: single input, one-file

**Two-file**:
A configuration where New Publications are compared against Old Publications and only the new file is output.
_Avoid_: dual-file, two-input

**New Publications**:
The Publications in a Two-file run that are being checked for duplicates against the Old Publications.
_Avoid_: candidate publications, new records

**Old Publications**:
The Publications in a Two-file run that serve as the reference set against which New Publications are compared.
_Avoid_: existing publications, old records, OLD_RECORDS

### Processes

**Normalization**:
The standardisation of bibliographic field values (case, encoding, punctuation, identifier variants) to a form that enables reliable comparison.
_Avoid_: cleaning, preprocessing, standardisation

**Enrichment**:
The process of supplementing a Kept Publication with data (DOI, year, pages, title) from its duplicates; occurs only in Remove Mode.
_Avoid_: merging, augmentation

### Input / Output

**RIS**:
The file format used for input and output; specifically the EndNote RIS variant, with the Zotero RIS variant also accepted.
_Avoid_: EndNote RIS, RIS format

**Bibliographic database**:
An external source of Publications (PubMed, EMBASE, Cochrane Library, ClinicalTrials.gov, etc.) from which records are exported via a reference manager before processing.
_Avoid_: source, repository

### Publication types

**Reply**:
A Publication that is a reaction to another publication, including letters to the editor and author responses; currently the umbrella term covering both sub-types.
_Avoid_: response (synonym for Reply), letter (a distinct sub-type not yet promoted to its own category)

**Cochrane Review**:
A systematic review from the Cochrane Library; uses DOI-only matching when both Publications in a pair have a DOI.
_Avoid_: Cochrane publication, Cochrane record

**Phase Publication**:
A clinical trial phase publication; uses more lenient title matching thresholds.
_Avoid_: phase trial, trial publication

**ClinicalTrials.gov Publication**:
A Publication originating from the ClinicalTrials.gov bibliographic database; titles are automatically considered matching due to format variation across databases.
_Avoid_: trial record, CT.gov record

### Validation

**Sensitivity**:
The proportion of true Duplicate pairs correctly identified by the deduplication algorithm.
_Avoid_: recall, true positive rate

**Specificity**:
The proportion of true non-Duplicate pairs correctly identified as non-matching.
_Avoid_: true negative rate

**True Positive**:
A pair of Publications correctly identified as Duplicates.
_Avoid_: correct match

**True Negative**:
A pair of Publications correctly identified as non-Duplicates.
_Avoid_: correct non-match

**False Positive**:
A pair of Publications incorrectly identified as Duplicates.
_Avoid_: incorrect match, over-deduplication

**False Negative**:
A pair of Publications incorrectly identified as non-Duplicates.
_Avoid_: missed duplicate, under-deduplication

## Relationships

- A **Duplicate Set** contains two or more **Publications**
- A **Duplicate Set** has exactly one **Kept Publication**
- **Enrichment** applies only in **Remove Mode** and only to the **Kept Publication**
- In **Mark Mode**, every Publication in a Duplicate Set receives the same **Label** (the ID of the Kept Publication)
- **Normalization** precedes all field comparisons
- In **Two-file**, **Old Publications** are never written to output; only **New Publications** are output
- A **Publication** originates from a **Bibliographic database** via a reference manager and is exchanged in **RIS** format

## Example dialogue

> **Dev:** "In Two-file mode, can an Old Publication be Enriched?"
> **Domain expert:** "No — Enrichment only applies to the Kept Publication in Remove Mode, and Old Publications are never written to output."

## Flagged ambiguities

- "Publication Set" was used to mean Duplicate Set — resolved: **Publication Set** means the full set of all input Publications; **Duplicate Set** is the term for a group of duplicates.
- "Letter" and "Reply" were considered as separate terms — resolved for now: **Reply** is the umbrella term. Letter may be promoted to its own category in future.
- "RIS" could mean any RIS variant — resolved: in this project, RIS means specifically the EndNote RIS variant; the Zotero variant is also accepted with minor differences handled during reading.
