# Record-count cap before dedup

Status: done

## What to build

Reject input files that exceed a configurable maximum record count before full parsing and the O(n²) comparison loop. `BibliographicItemReader` already contains a `countRecords()` fast-pass that scans for `ER  -` markers without building `BibliographicItem` objects — use that to gate entry.

Return a structured error (same shape as `InvalidRisFileException`) when the count exceeds the limit so the UI can display a clear message. The limit should be configurable via `application.properties`.

## Acceptance criteria

- [ ] `countRecords()` (or equivalent) is called before `readBibliographicItems()` in the dedup pipeline
- [ ] Files exceeding the limit are rejected with a clear error message returned to the client
- [ ] The limit is configurable via `application.properties` (e.g. `dedup.max-records=50000`)
- [ ] Files within the limit pass through unchanged
- [ ] Existing integration tests still pass

## Blocked by

None — can start immediately.
