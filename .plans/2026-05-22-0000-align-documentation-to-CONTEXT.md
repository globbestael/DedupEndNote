# Align documentation with CONTEXT.md (Ubiquitous Language)

## Context

CONTEXT.md and the code now use the same term — **Bibliographic Item** (`BibliographicItem`) — for the central domain object. The remaining gap is the documentation: Javadoc comments and user-facing HTML templates still use a mix of "publication(s)", "record(s)", and the camelCase leak "bibliographicItems" introduced by the code rename.

This plan groups changes by urgency and impact so you can choose which categories to execute.

---

## Category A — Critical: camelCase "bibliographicItems" in user-visible strings

**These are bugs introduced by the rename.** The DeduplicationService progress and error messages now display raw Java camelCase identifiers to the user. These must be fixed regardless of any other terminology decision.

### Before / After

| Location | Before | After |
|---|---|---|
| DeduplicationService (normal mode, one file) | `"DedupEndNote has deduplicated 500 bibliographicItems, has removed 150 duplicates, and has written 350 bibliographicItems."` | `"DedupEndNote has deduplicated 500 bibliographic items, has removed 150 duplicates, and has written 350 bibliographic items."` |
| DeduplicationService (mark mode, one file) | `"DedupEndNote has written 150 bibliographicItems with 30 duplicates marked in the Label field."` | `"DedupEndNote has written 150 bibliographic items with 30 duplicates marked in the Label field."` |
| DeduplicationService (mark mode, two files) | `"DedupEndNote has written %s bibliographicItems with %d duplicates marked in the Label field."` | `"DedupEndNote has written %s bibliographic items with %d duplicates marked in the Label field."` |
| DeduplicationService (remove mode, two files) | `"DedupEndNote removed %d bibliographicItems from the new set, and has written %d bibliographicItems."` | `"DedupEndNote removed %d bibliographic items from the new set, and has written %d bibliographic items."` |
| DeduplicationService (error: missing IDs) | `"The input file X contains bibliographicItems without IDs. The input file is not an Export as RIS-file from an EndNote library!"` | `"The input file X contains bibliographic items without IDs. The input file is not an Export as RIS-file from a reference manager (EndNote/Zotero)!"` |
| DeduplicationService (error: missing years) | `"All bibliographicItems of the input file X have no BibliographicItem Year. The input file is not an Export as RIS-file from an EndNote library!"` | `"All bibliographic items of the input file X have no publication year. The input file is not an Export as RIS-file from a reference manager (EndNote/Zotero)!"` |
| DeduplicationService (error: duplicate IDs) | `"The IDs of the bibliographicItems of input file X are not unique. The input file is not an Export as RIS-file from 1 EndNote library!"` | `"The IDs of the bibliographic items of input file X are not unique. The input file is not an Export as RIS-file from a single reference manager (EndNote/Zotero) database!"` |

**File to change:** `src/main/java/edu/dedupendnote/services/DeduplicationService.java`

---

## Category B — HTML template terminology

The HTML templates consistently use **"record(s)"** as the user-visible term for Bibliographic Items. The DDD ubiquitous language principle calls for **"bibliographic item(s)"** here too. This is the most impactful change for users and the one whose result you most need to preview before deciding.

### B1 — Replacing "records" with "bibliographic items" in UI text

Representative before/after passages (not exhaustive):

**index.html**

> **Before:** "Use [Deduplicate 2 files] to compare a new set of **records** against an existing one"
> **After:** "Use [Deduplicate 2 files] to compare a new set of **bibliographic items** against an existing one"

> **Before:** "mark the duplicate **records** (see Mark mode below)"
> **After:** "mark the duplicate **bibliographic items** (see Mark mode below)"

> **Before:** "DedupEndNote compares each pair of **records** in up to five stages..."
> **After:** "DedupEndNote compares each pair of **bibliographic items** in up to five stages..."

> **Before:** "By default, only the first **record** in each duplicate set is kept."
> **After:** "By default, only the first **bibliographic item** in each duplicate set is kept."

> **Before:** "The ID of the first **record** in each duplicate set is copied to the Label (LB) field of all duplicates."
> **After:** "The ID of the first **bibliographic item** in each duplicate set is copied to the Label (LB) field of all duplicates."

**twofiles.html**

> **Before:** "Upload the OLD **records** first, then the NEW **records**"
> **After:** "Upload the OLD **bibliographic items** first, then the NEW **bibliographic items**"

> **Before:** "1a. OLD **records**" / "1b. NEW **records**"
> **After:** "1a. OLD **bibliographic items**" / "1b. NEW **bibliographic items**"

> **Before:** "DedupEndNote will deduplicate both files, and save only the **records** from NEW_RECORDS.txt which are not present in OLD_RECORDS.txt. If a **record** occurs multiple times in NEW_RECORDS.txt, it will be saved only once."
> **After:** "DedupEndNote will deduplicate both files, and save only the **bibliographic items** from NEW_RECORDS.txt which are not present in OLD_RECORDS.txt. If a **bibliographic item** occurs multiple times in NEW_RECORDS.txt, it will be saved only once."

> **Before:** "In Mark mode the duplicate **records** in the file with new **records** are marked with the IDs of the first **record** of a set of duplicate **records**."
> **After:** "In Mark mode the duplicate **bibliographic items** in the file with new **bibliographic items** are marked with the IDs of the first **bibliographic item** of a set of duplicate **bibliographic items**."

**details.html**

> **Before:** "**Records** without a **publication year** are compared to all **records**..."
> **After:** "**Bibliographic items** without a **publication year** are compared to all **bibliographic items**..."

### B2 — "databases" → "bibliographic database" / "reference manager database"

CONTEXT.md now distinguishes read-only **bibliographic databases** (PubMed, EMBASE, …) from read-write **reference manager databases** (EndNote, Zotero). The HTML currently uses just "database" for both.

**twofiles.html (currently uses both correctly in one place but inconsistently elsewhere)**

> **Before:** "Export your existing EndNote **database** OLD as a RIS file OLD_RECORDS.txt"
> **After:** "Export your existing EndNote **reference manager database** OLD as a RIS file OLD_RECORDS.txt"

> **Before:** "You have executed a query in a **bibliographic database** (e.g. PubMed) and imported the results in an **EndNote database**."
> **After:** "You have executed a query in a **bibliographic database** (e.g. PubMed) and imported the results in an **EndNote reference manager database**." _(already uses "bibliographic database" correctly here)_

> **Before:** "You have results from several **bibliographic databases** (PubMed, Cochrane, EMBASE, ...)"
> **After:** _(already correct — no change)_

### B3 — "publications" kept as-is (real-world items)

Passages where "publication(s)" refers to the real-world scholarly item should **not** be changed:

- `"ahead-of-print publications"` — these are real-world publications, correct usage
- `"Publication year"` heading — refers to the year the real-world publication appeared, correct
- `"retracted publications"` — correct domain usage
- `"a publication is considered a reply/erratum/comment if..."` — correct domain usage

---

## Category C — Javadoc

Most Javadoc was updated by the code rename. Remaining issues are a few comments that use "publication(s)" to mean Bibliographic Item, and one that leaked "BibliographicItem Year" (from the error string).

### Before / After (representative)

**BibliographicItem.java — label field comment**

> **Before:** "the label of all duplicate **bibliographicItems** in a set receive the ID of the first **bibliographicItem**..."
> **After:** _(already uses `bibliographicItem` — no change needed; the camelCase is appropriate in a code comment)_

**BibliographicItem.java — isCochrane field Javadoc**

> **Before (line 84):** "For Cochrane **bibliographicItems** if both have a DOI..."
> **After:** _(acceptable as-is in Javadoc — refers to the class)_

**BibliographicItem.java — isReply field comment**

> **Before (line 93):** "Publications which are replies need special treatment."
> **After:** "**Bibliographic items** which are replies need special treatment."

**DeduplicationService.java — inline comments at lines 305, 326**

These still say "publications" in the old sense (meaning Bibliographic Items). To be checked during execution — minor.

---

## What to keep unchanged

- **`publicationYear`** field name, getter, setter, and any reference to "publication year" in text — this correctly refers to the year of the real-world Publication.
- **Changelog entries** — developer-maintained, no alignment needed.
- **Javadoc that uses camelCase `bibliographicItem(s)`** — appropriate in code-facing documentation.
- **`"bibliographic database"`** where already correct in HTML.

---

## Execution approach (when approved)

1. Fix DeduplicationService user-facing strings (Category A) — simple targeted edits in one file
2. Update HTML templates (Category B) — bulk find-replace of `\brecord\b`→`bibliographic item` and `\brecords\b`→`bibliographic items` in the three template files, then manually review and adjust "database" terminology
3. Update remaining Javadoc (Category C) — targeted edits
4. Run integration tests: `./mvnw test -Pintegration-tests`

---

## Key decision for the user

**Do you want "bibliographic item(s)" in the UI, or prefer to keep "record(s)"?**

"Record" is shorter and more neutral. "Bibliographic item" is precise per the domain model. Both are used in the literature-search / systematic-review community, but "record" is more common in tools like Rayyan, Covidence, and ASySD. "Bibliographic item" is the CONTEXT.md canonical term.

If you prefer "record(s)" for the UI, Category B only needs the "database" disambiguation (B2); Categories A and C remain unchanged.
