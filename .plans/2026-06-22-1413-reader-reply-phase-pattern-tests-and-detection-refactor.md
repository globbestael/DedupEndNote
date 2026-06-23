# Reader reply/phase pattern tests + detection refactor

## Status

**Code complete; unit + integration green. One validation experiment test (`AuthorExperimentsTests`)
fails because of an intended behavioral change — decision pending with the user (see Verification).**
Not committed.

## Context

`BibliographicItemReader` flags special titles while reading RIS using five regex patterns:
`REPLY_PATTERN`, `ERRATUM_PATTERN`, `SOURCE_PATTERN`, `COMMENT_PATTERN` (→ `setReply(true)`) and
`PHASE_PATTERN` (→ `setPhase(true)`). Two problems were addressed:

1. **Test gaps.** `ERRATUM_PATTERN` and `PHASE_PATTERN` had no tests; `REPLY_PATTERN` was "tested"
   against a stale local copy of part of the pattern; the reader-pattern tests squatted inside
   `JWSimilarityTitleTest`, with no dedicated reader test class.
2. **Detection only on `TI`.** Reply/phase detection was hard-wired to `case "TI"`, even though title
   content also enters via `OP`/`ST`/`T3` and TI continuation lines through `addNormalizedTitle`.

User decisions: test all 5 patterns properly; create a dedicated `BibliographicItemReaderTest`;
refactor detection into `addNormalizedTitle` (behavioral, must be validated); do **not** merge the
patterns; keep curated cases in the external `~/dedupendnote_input_files` `All__*` files.

## Changes made

### A. Expose patterns — `services/BibliographicItemReader.java`
- `REPLY_PATTERN`, `ERRATUM_PATTERN`, `PHASE_PATTERN` changed from `private` to `public static final`
  (matching the already-public `SOURCE_PATTERN`/`COMMENT_PATTERN`), so the cross-package test can
  assert against the real patterns.
- Removed the `// FIXME: Can some of these 4 patterns be merged?` comment (decision: keep separate).

### B. Refactor detection into `addNormalizedTitle` (behavioral) — same file
- Added private static helper `detectReplyAndPhase(String fieldContent, BibliographicItem item)`
  holding the original reply/erratum/source/comment block (`setReply(true)` + `setTitle(raw)`) and the
  phase block.
- Called it at the **end** of `addNormalizedTitle` (after normalization, so a reply's raw title still
  overrides the normalized title).
- Removed the inline reply/phase block from `case "TI"` (kept `titleCache` / `previousFieldName`) and
  deleted the obsolete "only applied to TI field" comment.
- **Effect:** reply/phase detection now runs for every title-bearing field (`TI`, `OP`, `ST`, `T3`,
  TI continuation). Flags stay sticky (only ever set `true`).

### C. New `BibliographicItemReaderTest` + test moves
- New `src/test/java/edu/dedupendnote/unit/services/BibliographicItemReaderTest.java` (JUnit 5,
  `extends BaseTest`, `testDir = baseDir.resolve("unit")`). Contains:
  - Moved file-based tests: `testErrataFromFile` (SOURCE), `testPositiveCommentsFromFile`,
    `testNegativeCommentsFromFile`, `testPositiveCommentsAndRepliesFromFile` (COMMENT), `lineSeparator`
    (RIS_LINE_PATTERN).
  - New inline `@ValueSource` tests: `replyPattern_positives/negatives` (now against the **real**
    `REPLY_PATTERN`, lower-cased as in production), `erratumPattern_positives/negatives`,
    `phasePattern_positives/negatives`. Erratum/phase negatives use the documented non-erratum cases
    (`[Erratum appears in …]`, `[corrected]`) and `chronic-phase` text. **26 tests, all pass.**
- `JWSimilarityTitleTest.java`: removed the moved methods + the stale `checkReply` and unused imports;
  now title-JWS-similarity only.
- `testBalanceBracesPattern` moved to `TitlesNormalizationServiceTest.java` (it tests
  `TitlesNormalizationService.BALANCED_BRACES_PATTERN`, not a reader pattern).
- `DefaultTitleComparisonServiceTest.java`: the `("Reply to a title", "Some other title")` case moved
  from the negative to the positive provider — `addNormalizedTitle` now sets `isReply`, so the title
  comparison correctly short-circuits to `true`. Comment updated.

### D. Docs
- `CLAUDE.md`: `JWSimilarityTitleTest` line updated ("title JWS-similarity tests only"); added a
  `BibliographicItemReaderTest` entry to the unit hierarchy.
- `docs/algorithm.md`: special-types section notes Reply/Phase detection runs in `addNormalizedTitle`
  and applies to all title fields; pattern names listed.

(Part D of the original plan — fixing an external-file path/casing mismatch — was **not needed**: the
`All__*` files exist at `~/dedupendnote_input_files/unit/` with correct casing; the earlier report was
misled by a stale `~/dedupendnote_files` copy.)

## Verification results

| Step | Command | Result |
|---|---|---|
| Compile | `./mvnw -DskipTests test-compile` | ✅ clean (NullAway/Error Prone pass) |
| Reader/title/normalization | `./mvnw -Dtest=BibliographicItemReaderTest,JWSimilarityTitleTest,TitlesNormalizationServiceTest test` | ✅ 116 pass (reader 26) |
| Unit | `./mvnw test -Punit-tests` | ✅ 584 pass, 9 skipped (after fixing `DefaultTitleComparisonServiceTest`) |
| Integration | `./mvnw test -Pintegration-tests` | ✅ 23 pass, 1 skipped — no dedup-output regression |
| Validation | `./mvnw test -Pvalidation-tests` | ⚠️ `ValidationTests` ✅ (1 ran, 18 skipped); `AuthorExperimentsTests` ❌ |

### Open issue — `AuthorExperimentsTests` failure (decision pending)

`higherAuthorThresholdsReduceSensitivityAndIncreaseSpecificity` asserts that crippling author matching
(`AuthorThresholds(1.0,1.0,1.0)` → no author pair can match) yields sensitivity **strictly below** the
production baseline on `SRA2_Haematology`. After the refactor the experiment's sensitivity rose to
**exactly** the baseline (97.37%, 222 TP / 6 FN), so `isLessThan` fails on equality:

```
Expecting actual: 97.36842105263158  to be less than: 97.36842105263158
  at AuthorExperimentsTests.java:83
```

Interpretation: broadening reply detection to `OP`/`ST`/`T3` flags more Haematology records as
replies; their title step is skipped, so every true duplicate is now caught without the author step —
authors became non-discriminating on this dataset. This is a genuine behavioral signal from the
refactor, not a flaky test. Options put to the user:
1. **Investigate magnitude first** — count newly reply-flagged records; large fraction ⇒ patterns
   over-flag non-title content and the refactor should be narrowed.
2. **Accept + recalibrate** — recompute the production baseline under the new behavior, update the
   hardcoded `ValidationResult`, relax the assertion to `isLessThanOrEqualTo`.
3. **Narrow scope** — keep REPLY/ERRATUM/PHASE on OP/ST/T3 but restrict the broad
   `SOURCE_PATTERN`/`COMMENT_PATTERN` to `TI` only.

## Files modified
- `src/main/java/edu/dedupendnote/services/BibliographicItemReader.java`
- `src/test/java/edu/dedupendnote/unit/services/BibliographicItemReaderTest.java` (new)
- `src/test/java/edu/dedupendnote/unit/services/JWSimilarityTitleTest.java`
- `src/test/java/edu/dedupendnote/unit/services/TitlesNormalizationServiceTest.java`
- `src/test/java/edu/dedupendnote/unit/services/DefaultTitleComparisonServiceTest.java`
- `CLAUDE.md`, `docs/algorithm.md`

## How to verify
Run the four commands in the table above. The unit and integration suites should be fully green; the
`AuthorExperimentsTests` result depends on the chosen resolution for the open issue.
