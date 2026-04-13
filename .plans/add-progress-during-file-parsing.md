# Plan: Add progress reporting during file parsing

**Status: executed — compile passes, same pre-existing test failures as before**

## Context

The web interface shows a progress bar during the comparison phase (year-by-year deduplication), but nothing during file parsing by `IOService.readPublications()`. For large files (e.g. 37,741 records), reading and normalizing can take noticeable time with no visual feedback.

## Approach

Two-pass strategy: fast first pass to count total records (count `"ER  - "` lines), then during the actual parsing pass emit `"PROGRESS: N"` messages as records are processed. Progress is throttled to at most 101 messages (one per percentage point).

## Changes made

### Step 1 — `IOService.readPublications` ✅

**File:** `src/main/java/edu/dedupendnote/services/IOService.java`

- Added `Consumer<String> progressReporter` parameter
- Added private `countRecords(String fileName)` helper that scans for `"ER  - "` lines
- At the start of `readPublications`, calls `countRecords()` to get the total
- In `case "ER":`, after `publications.add(publication)`, emits `"PROGRESS: N"` when the percentage changes

### Step 2 — `DeduplicationService` call sites ✅

**File:** `src/main/java/edu/dedupendnote/services/DeduplicationService.java`

Passed `progressReporter` to all three `ioService.readPublications(...)` calls:
- `deduplicateOneFile`: `readPublications(inputFileName, progressReporter)`
- `deduplicateTwoFiles`: `readPublications(oldInputFileName, progressReporter)` and `readPublications(newInputFileName, progressReporter)`

### Step 3 — `ValidationTests` direct call ✅

**File:** `src/test/java/edu/dedupendnote/services/ValidationTests.java`

Updated direct call to `ioService.readPublications(inputFileName, message -> {})`.

## Files modified

1. `src/main/java/edu/dedupendnote/services/IOService.java`
2. `src/main/java/edu/dedupendnote/services/DeduplicationService.java`
3. `src/test/java/edu/dedupendnote/services/ValidationTests.java`

## Verification

```bash
./mvnw clean compile test-compile    # BUILD SUCCESS
./mvnw test -Punit-tests             # 456 tests, 4 pre-existing failures unchanged
```
