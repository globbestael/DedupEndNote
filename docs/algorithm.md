# DedupEndNote — Deduplication Algorithm

This document describes the 5-step comparison algorithm, threshold values, special-publication-type handling, year-based grouping, and enrichment logic.

- For domain term definitions (Bibliographic Item, Duplicate Set, Reply, Cochrane Review, Mark/Remove Mode, etc.) see [CONTEXT.md](../CONTEXT.md).
- For the pipeline overview, service map, and sequence diagram see [docs/architecture.html](architecture.html).

---

## The 5-step algorithm

All five steps must pass for two Bibliographic Items to be declared Duplicates. The comparison short-circuits on the first mismatch — cheap checks (year, page/DOI) run before expensive Jaro-Winkler similarity steps.

The four field-comparison services (`DefaultPagesComparisonService`, `DefaultAuthorsComparisonService`, `DefaultTitleComparisonService`, `DefaultJournalComparisonService`) are bundled in the `FieldComparators` record, which is passed to `DeduplicationService` for easy test substitution.

---

### Step 1 — Publication year

Implemented in `DeduplicationService.compareSet()` as the year-bucket pre-filter.

- Years within ±1 of each other pass.
- Bibliographic Items with no year (year = 0) are added to adjacent year buckets so they are still compared.
- **Cochrane Reviews:** year must match exactly (strict, not ±1).

---

### Step 2 — Starting page or DOI

Implemented in `DefaultPagesComparisonService`.

| Condition | Result |
|---|---|
| Both have DOIs → DOIs must match exactly | Pass / Fail |
| Both have starting pages → pages must match exactly | Pass / Fail |
| Neither has DOI nor starting page | **Pass** (INSUFFICIENT_DATA — see below) |

**Cochrane Reviews** receive special treatment:
- DOIs are compared before starting pages (opposite of the normal order).
- If neither DOI nor page matches, the result is Fail (no INSUFFICIENT_DATA leniency).

`DefaultPagesComparisonService` hosts the static helper `compareSameDois`.

---

### Step 3 — Authors

Implemented in `DefaultAuthorsComparisonService`.

Uses Jaro-Winkler similarity on up to the first 40 authors. All combinations of the two items' author lists are tried; the pair passes if any combination exceeds the threshold.

| Context | Threshold |
|---|---|
| Normal | > 0.67 |
| Reply (with DOI or pages — step 2 passed on data) | > 0.75 |
| Reply (INSUFFICIENT_DATA — step 2 passed vacuously) | > 0.80 |

**Books (ISBN present, no authors):** if either Bibliographic Item has no authors but has an ISBN, this step returns true — the journal step (step 5) will do the ISBN comparison instead.

Thresholds are injectable via the `AuthorThresholds` record (used in validation experiments).

---

### Step 4 — Title

Implemented in `DefaultTitleComparisonService`.

Uses Jaro-Winkler similarity on normalized titles. Titles are truncated to the shorter of the two lengths before comparison.

| Context | Threshold |
|---|---|
| Phase Publication (e.g. "Phase I/II/III" in title) | > 0.96 (strictest) |
| INSUFFICIENT_DATA (no DOI or starting page) | > 0.94 |
| Has DOI or starting page | > 0.89 (most lenient) |

**Special skips (returns true without comparison):**
- **Replies:** title comparison is skipped entirely.
- **ClinicalTrials.gov:** if both Bibliographic Items originate from ClinicalTrials.gov, title comparison is skipped (format varies too much across databases).

Thresholds are injectable via the `TitleThresholds` record.

---

### Step 5 — ISBN / ISSN / Journal name

Implemented in `DefaultJournalComparisonService`.

This is an **OR gate** — only one of the following must pass:

1. **Same DOIs** — already matched in step 2; step 5 returns true immediately.
2. **Same ISBNs / ISSNs** — exact match required.
3. **Same journal names** — multi-strategy matching:
   - Exact match.
   - Jaro-Winkler > 0.90 (> 0.93 for Replies).
   - **Abbreviation detection:** e.g. `"Ann Intern Med"` → `"Annals of Internal Medicine"`.
   - **Initialism detection:** e.g. `"AJP"` → `"American Journal of Psychiatry"`.

`DefaultJournalComparisonService` hosts the static helper `compareIssns`.

Thresholds are injectable via the `JournalThresholds` record.

---

## INSUFFICIENT_DATA

When a Bibliographic Item has neither a DOI nor a starting page, step 2 passes vacuously. The rationale: without strong identifiers, it is better to let the fuzzy-matching steps (authors, title, journal) decide rather than rejecting potential Duplicates outright. The effect is that title and author thresholds are raised (see step 3 and step 4 above) to compensate for the missing anchor.

```
If neither DOI nor starting page is present:
  → step 2 passes ("INSUFFICIENT_DATA")
  → author threshold raised to 0.80 for Replies
  → title threshold raised to 0.94
```

---

## Year-based grouping

`DeduplicationService.compareSet()` groups Bibliographic Items by year to avoid an O(n²) comparison across the full corpus. Adjacent-year pairs are still compared because databases sometimes record different years for the same Publication.

| Configuration | Sort order | Adjacent groups checked |
|---|---|---|
| Single-file | Descending (newest first) | current year + (year − 1) |
| Two-file | Ascending (oldest first) | current year + (year + 1) |

Bibliographic Items with no year (year = 0) are added to the adjacent-year buckets so they are not silently excluded.

---

## Special publication types

These categories affect which comparison rules apply. Detected during normalization (`BibliographicItemReader`) and stored as boolean flags on `BibliographicItem`.

Reply and Phase detection runs in `BibliographicItemReader.addNormalizedTitle`, so it is applied to **every** title-bearing field (`TI`, `OP`, `ST`, `T3`, and the `TI` continuation line), not only `TI`. The flags are sticky — once set by any title field they are never cleared.

| Type | Detection | Effect on algorithm |
|---|---|---|
| **Cochrane Review** | DOI matches pattern `10.1002/14651858.*` | Year must match exactly; DOI compared before pages in step 2; no INSUFFICIENT_DATA leniency |
| **Reply** | Any title field matches reply/letter/erratum/comment patterns (`REPLY_PATTERN`, `ERRATUM_PATTERN`, `SOURCE_PATTERN`, `COMMENT_PATTERN`) | Step 4 (title) skipped; higher author threshold in step 3; stricter journal threshold in step 5 |
| **Phase Publication** | Any title field matches `PHASE_PATTERN` ("Phase I", "Phase II", "Phase III", "Phase 1..4") | Title threshold raised to 0.96 (strictest) |
| **ClinicalTrials.gov Publication** | Source field indicates ClinicalTrials.gov origin | Step 4 (title) skipped when both items originate from CTG; titles normalised in enrichment |

Types are not mutually exclusive — a Bibliographic Item can be simultaneously a Reply and a Phase Publication.

---

## Enrichment (Remove Mode only)

After the comparison phase, `EnrichmentService` enriches each Kept Bibliographic Item with data gathered from the rest of its Duplicate Set. Enrichment only runs in Remove Mode.

1. **DOIs** — all DOIs from the Duplicate Set are merged onto the Kept Bibliographic Item.
2. **Title selection:**
   - Replies and Errata → the **longest** title in the Duplicate Set is used.
   - ClinicalTrials.gov Publications → the **shortest** title is used (most canonical form).
   - Others → the Kept Bibliographic Item's own title is retained unless a duplicate provides a longer one.
3. **Publication year** — filled in from a duplicate if the Kept Bibliographic Item has no year.
4. **Pages** — filled in from a duplicate if the Kept Bibliographic Item has no pages or an abbreviated range.

---

## Thresholds summary

| Field | Normal | Reply | Special |
|---|---|---|---|
| **Authors** (Jaro-Winkler) | > 0.67 | > 0.75 (with DOI/pages), > 0.80 (INSUFFICIENT_DATA) | — |
| **Title** (Jaro-Winkler) | > 0.89 (with DOI/pages), > 0.94 (INSUFFICIENT_DATA) | skipped | > 0.96 for Phase Publications |
| **Journal** (Jaro-Winkler) | > 0.90 | > 0.93 | — |

---

## Algorithm flow

```
For each Bibliographic Item pair (grouped by year ±1)
│
├─ Step 1: Year within ±1? (exact for Cochrane Reviews)
│    ↓ pass
├─ Step 2: DOI or starting page match? (or INSUFFICIENT_DATA)
│    ↓ pass
├─ Step 3: Authors similar? (Jaro-Winkler, threshold depends on Reply/INSUFFICIENT_DATA)
│    ↓ pass
├─ Step 4: Title similar? (Jaro-Winkler, threshold depends on context; skipped for Replies/CTG)
│    ↓ pass
└─ Step 5: Same DOI OR same ISSN/ISBN OR similar journal name?
     ↓ pass
═══════════════════════════════════════
DUPLICATE FOUND
→ Mark with Label (Mark Mode)
→ Enrich Kept Bibliographic Item with data from Duplicate Set (Remove Mode)
```
