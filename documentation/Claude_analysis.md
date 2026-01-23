# DedupEndNote - Codebase Analysis

## Overview

### Purpose

A **Java web application** that deduplicates EndNote and Zotero RIS export files. Designed for bibliographic researchers working with databases like PubMed, EMBASE, Web of Science, Scopus, and Cochrane Library.

### Key Features

- **Single-file deduplication**: Remove duplicates from one RIS file
- **Two-file deduplication**: Compare new records against existing ones
- **Mark mode**: Labels duplicates instead of removing them

### Technology Stack

- **Java 21** + **Spring Boot 4.0.1**
- **Thymeleaf** templates with Bootstrap 5
- **WebSocket (STOMP)** for real-time progress updates
- **Apache Commons Text** for Jaro-Winkler similarity matching

### Project Structure

```
src/main/java/edu/dedupendnote/
├── controllers/          # REST endpoints (file upload, deduplication)
├── domain/               # Data models (Publication, AuthorRecord, etc.)
├── services/             # Core business logic
├── WebConfig.java
├── WebSocketConfig.java
└── DedupEndNoteApplication.java
```

### Core Components

| Class | Responsibility |
|-------|----------------|
| `DeduplicationService` | Main orchestrator - coordinates the entire workflow |
| `IOService` | Reads/writes RIS files, parses EndNote fields |
| `ComparisonService` | 5-step comparison algorithm for duplicate detection |
| `NormalizationService` | Normalizes authors, titles, DOIs, pages, journals |
| `Publication` | Core domain model for bibliographic records |

### Application Flow

1. User uploads RIS file(s) via web interface (port 9777)
2. Backend parses and normalizes all bibliographic fields
3. Compares publication pairs using the 5-step algorithm
4. Marks/removes duplicates and enriches kept records
5. User downloads deduplicated RIS file

The application includes special handling for Cochrane reviews, clinical trials, retractions, errata, and author name variations.

---

## Deduplication Algorithm - Detailed Explanation

### Overview

The algorithm uses a **5-step sequential comparison** where **all steps must pass** for two publications to be considered duplicates. If any step fails, comparison stops immediately.

---

### The 5 Steps

#### Step 1: Starting Pages or DOIs (`ComparisonService.java:182-249`)

The first gate that checks distinguishing metadata:

| Condition | Result |
|-----------|--------|
| Both have DOIs → DOIs must match | Pass/Fail |
| Both have starting pages → Pages must match | Pass/Fail |
| **Neither has DOI nor starting page** | **Pass** (INSUFFICIENT_DATA) |

**Special handling for Cochrane reviews:**
- Year must match exactly
- DOIs compared before pages (opposite of normal)
- Very strict - returns false if nothing matches

---

#### Step 2: Author Comparison (`DefaultAuthorsComparisonService.java:23-75`)

Uses **Jaro-Winkler similarity** on author strings:

| Publication Type | Threshold |
|------------------|-----------|
| Normal | > 0.67 |
| Reply (with DOI/pages) | > 0.75 |
| Reply (without DOI/pages) | > 0.80 |

```java
// Compares all author list combinations
for (String authors1 : r1.getAllAuthors()) {
    for (String authors2 : r2.getAllAuthors()) {
        if (jws.apply(authors1, authors2) > threshold) return true;
    }
}
```

**Empty authors:** If either lacks authors but has ISBN (book), returns true.

---

#### Step 3: Title Comparison (`ComparisonService.java:251-334`)

Uses **Jaro-Winkler similarity** with context-dependent thresholds:

| Context | Threshold |
|---------|-----------|
| Phase trials (I, II, III) | > 0.96 (strictest) |
| No DOI/pages available | > 0.94 |
| Has DOI or pages | > 0.89 (most lenient) |

**Special cases:**
- **Reply publications:** Title comparison skipped entirely (returns true)
- **ClinicalTrials.gov:** Both from CTG → returns true
- Titles are **truncated to minimum length** before comparison

---

#### Step 4: Journal/ISSN/DOI Verification (`ComparisonService.java:30-180`)

Only **ONE** of these must pass (OR gate):

1. **Same DOIs** - Already confirmed in Step 1
2. **Same ISBNs/ISSNs** - Exact match required
3. **Same Journals** - Multi-strategy matching:
   - Exact match
   - Jaro-Winkler > 0.90 (0.93 for replies)
   - Abbreviation detection: "Ann Intern Med" → "Annals of Internal Medicine"
   - Initialism detection: "AJP" → "American Journal of Psychiatry"

---

### The INSUFFICIENT_DATA Concept

When publications **lack both DOI AND starting page**:

```java
if (!sufficientStartPages && !sufficientDois) {
    return true; // Pass Step 1, rely on fuzzy matching
}
```

**Rationale:** Without strong identifiers, let author/title/journal fuzzy matching decide rather than rejecting potential duplicates.

---

### Year-Based Grouping

Publications are grouped by year to reduce comparisons:

| Mode | Order | Adjacent Years |
|------|-------|----------------|
| Single file | Descending (newest first) | current + (year-1) |
| Two files | Ascending (oldest first) | current + (year+1) |

Publications with missing years (year=0) are added to adjacent groups.

---

### Special Publication Types

| Type | Detection | Special Handling |
|------|-----------|------------------|
| **Cochrane** | DOI pattern `10.1002/14651858.*` | Stricter year/DOI matching |
| **Reply/Erratum** | Title patterns | Skip title comparison, higher thresholds |
| **Phase trials** | "Phase I/II/III" in title | Title threshold = 0.96 |
| **ClinicalTrials.gov** | Source field | Skip title comparison between CTG entries |

---

### Enrichment After Deduplication

Kept publications are enriched with data from their duplicates:

1. **Gather all DOIs** from duplicate set
2. **Best title selection:**
   - Replies → longest title
   - Clinical trials → shortest title
3. **Fill missing year** from duplicates
4. **Fill missing pages** from duplicates

---

### Algorithm Flow Summary

```
┌─────────────────────────────────────────────────────────┐
│ For each publication pair (grouped by year ±1)          │
├─────────────────────────────────────────────────────────┤
│ Step 1: DOI/Page match OR insufficient data?            │
│         ↓ Pass                                          │
│ Step 2: Authors similar? (Jaro-Winkler > 0.67-0.80)     │
│         ↓ Pass                                          │
│ Step 3: Titles similar? (Jaro-Winkler > 0.89-0.96)      │
│         ↓ Pass                                          │
│ Step 4: Same DOI OR Same ISSN OR Similar journal?       │
│         ↓ Pass                                          │
│ ═══════════════════════════════════════════════════════ │
│ DUPLICATE FOUND → Mark with label, enrich kept record   │
└─────────────────────────────────────────────────────────┘
```

### Key Source Files

- `ComparisonService.java:30-334` - All comparison logic
- `DefaultAuthorsComparisonService.java:23-75` - Author matching
- `DeduplicationService.java:100-453` - Orchestration and enrichment

---

### Thresholds Summary

| Component | Normal | Reply | Notes |
|-----------|--------|-------|-------|
| **Authors** | 0.67 | 0.75-0.80 | Higher for replies (stricter) |
| **Titles (Phase)** | 0.96 | N/A | Clinical trials stricter |
| **Titles (Sufficient)** | 0.89 | N/A | With DOIs/pages |
| **Titles (Insufficient)** | 0.94 | N/A | No DOIs/pages (stricter) |
| **Journals** | 0.90 | 0.93 | Higher for replies (stricter) |
