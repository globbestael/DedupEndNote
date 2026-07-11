# Consolidate MemoryAppender log-capture setup into a shared fixture

**Status:** executed (D1 adopted: behavior-correct logger list; test-hierarchy note added). Not yet committed.
**Filename timestamp** is provisional — rename to the actual commit time (`YYYY-MM-DD-HHMM`) when this plan is committed.

**Verification result:** `./mvnw test-compile` clean; `MissedDuplicatesTests` — Tests run: 3, Failures: 0, Errors: 0. Trace capture confirmed to now include Journal/Pages/Title comparison steps that the stale list previously dropped.

## Context

Two classes attach a `MemoryAppender` to a set of Logback loggers, raise them to
`TRACE`, run a deduplication, and pull out the matching trace lines:

- `edu.dedupendnote.integration.MissedDuplicatesTests` (test class, `@BeforeEach` setup)
- `edu.dedupendnote.validation.services.ValidationService#writeFNandFPresults` (helper that writes `_FN_Analysis.txt` / `_FP_Analysis.txt`)

Both hand-roll the same six-line ritual (build appender → loop logger names →
save level → `setLevel(TRACE)` → `addAppender` → `start`) and both carry their own
copy of the logger-name list and the `tracePatterns` list. The copy-paste has
already drifted and produced real defects (below).

### Defects this refactor fixes

1. **Stale logger list in `ValidationService`.** Its list points at the *old*
   package `edu.dedupendnote.services.DefaultAuthorsComparisonService` (the class
   now lives in `edu.dedupendnote.services.comparison.*`) and omits the Journal /
   Pages / Title comparison services entirely. Result: the FN/FP analysis files
   silently capture trace from `DeduplicationService` only — the per-field
   comparison trace is dropped. `MissedDuplicatesTests` has the correct list.

2. **`oldLevel` restore bug (`ValidationService.java:195, :245`).** `oldLevel` is a
   single scalar overwritten each loop iteration, so the `finally` restores *every*
   logger to whatever the *last* logger's original level was. Must be tracked
   per-logger.

3. **Appender never detached.** Both sites `addAppender` but neither calls
   `detachAppender`. `writeFNandFPresults` creates a fresh `MemoryAppender` on every
   call and adds it without removing the previous one, so over a validation run the
   loggers accumulate many live appenders. Restoring the level does not remove the
   appender.

4. **`MissedDuplicatesTests` never restores anything.** No `@AfterEach`; it leaves
   the loggers pinned at `TRACE` with the appender attached for the rest of the JVM
   (cross-test pollution risk in the shared Spring context).

5. **Vestigial `ValidationTests` logger name.** Both lists contain
   `edu.dedupendnote.services.ValidationTests`. The real class is
   `edu.dedupendnote.validation.ValidationTests`, and it emits **no** `log.trace`
   (verified). The step-0 (`- 0.`) "years too far apart" trace that the `0` in
   ValidationService's pattern is meant to catch is actually emitted by
   `ValidationService` itself (`ValidationService.java:225`), whose logger is in
   neither list — so that pattern branch is currently dead. See decision D1 below.

## Design

Introduce one `AutoCloseable` fixture that owns the whole lifecycle and holds the
canonical constants. `MemoryAppender` stays a pure event buffer (SRP); the
attach/restore/detach wiring lives in the new class.

New file: `src/test/java/edu/dedupendnote/integration/utils/TraceLogCapture.java`

```java
package edu.dedupendnote.integration.utils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * Captures TRACE-level deduplication trace from a fixed set of loggers into a
 * {@link MemoryAppender}, restoring every logger's original level and detaching
 * the appender on {@link #close()}. Single source of truth for the logger list
 * and the trace-line patterns shared by MissedDuplicatesTests and ValidationService.
 */
public final class TraceLogCapture implements AutoCloseable {

    // @formatter:off
    /** Loggers whose TRACE output makes up a deduplication comparison trace. */
    public static final List<String> TRACE_LOGGER_NAMES = List.of(
        "edu.dedupendnote.services.DeduplicationService",
        "edu.dedupendnote.services.comparison.DefaultAuthorsComparisonService",
        "edu.dedupendnote.services.comparison.DefaultJournalComparisonService",
        "edu.dedupendnote.services.comparison.DefaultPagesComparisonService",
        "edu.dedupendnote.services.comparison.DefaultTitleComparisonService",
        "edu.dedupendnote.validation.services.ValidationService"  // emits the step-0 year pre-check trace
    );
    // @formatter:on

    /** Step 0 = year pre-check (years too far apart); steps 1-4 = the comparison algorithm. */
    public static final List<Pattern> TRACE_PATTERNS = List.of(
        Pattern.compile("- (0|1|2|3|4). .+"),
        Pattern.compile("\\d+ - \\d+ ARE (NOT )?DUPLICATES"));

    private final MemoryAppender appender;
    private final Map<Logger, Level> savedLevels;

    private TraceLogCapture(MemoryAppender appender, Map<Logger, Level> savedLevels) {
        this.appender = appender;
        this.savedLevels = savedLevels;
    }

    public static TraceLogCapture attach() {
        return attach(TRACE_LOGGER_NAMES);
    }

    public static TraceLogCapture attach(List<String> loggerNames) {
        MemoryAppender appender = new MemoryAppender();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        Map<Logger, Level> savedLevels = new LinkedHashMap<>();
        for (String name : loggerNames) {
            Logger logger = (Logger) LoggerFactory.getLogger(name);
            savedLevels.put(logger, logger.getLevel()); // may be null (= inherit)
            logger.setLevel(Level.TRACE);
            logger.addAppender(appender);
        }
        appender.start();
        return new TraceLogCapture(appender, savedLevels);
    }

    /** The underlying buffer, for callers that need other MemoryAppender queries. */
    public MemoryAppender appender() {
        return appender;
    }

    /** Trace lines matching {@link #TRACE_PATTERNS}, in log order. */
    public List<String> tracedMessages() {
        return appender.filterByPatterns(TRACE_PATTERNS, Level.TRACE);
    }

    /** Clear captured events without detaching (for reuse within one capture). */
    public void reset() {
        appender.reset();
    }

    @Override
    public void close() {
        appender.stop();
        savedLevels.forEach((logger, level) -> {
            logger.detachAppender(appender);
            logger.setLevel(level); // null legally restores "inherit from parent"
        });
    }
}
```

Notes:
- `LinkedHashMap` permits null values, and `logger.setLevel(null)` legally restores
  the "inherit from parent" state — so an originally-unset level round-trips
  correctly (better than the current code, which forced a concrete level).
- Combining the buffer query into `tracedMessages()` keeps `TRACE_PATTERNS`
  private-by-convention to the fixture; call sites no longer name the patterns.

## Changes

### 1. `TraceLogCapture` (new)
As above.

### 2. `MissedDuplicatesTests`
- Delete the `tracePatterns` field, the `loggerNames` list, the `Level oldLevel`
  logic, and the whole `addMemoryAppender()` body.
- Replace the `memoryAppender` field with a `TraceLogCapture capture` field.
- `@BeforeEach void addLogCapture() { capture = TraceLogCapture.attach(); }`
- Add `@AfterEach void detachLogCapture() { capture.close(); }` (fixes defect 4).
- In `deduplicateMissedDuplicates`, replace
  `memoryAppender.filterByPatterns(tracePatterns, Level.TRACE)` with
  `capture.tracedMessages()` (two call sites: the `System.err.println` and the
  assertion).
- Remove now-unused imports (`Pattern`, `Level`, `Logger`, `LoggerContext`,
  `LoggerFactory`, `ArrayList`, `MemoryAppender`).
- Delete the `FIXME: There is a big overlap with ValidationTests::writeFNandFPresults`
  comment (the overlap is now gone).

### 3. `ValidationService#writeFNandFPresults`
- Delete the `tracePatterns` field (move to the fixture) and the local
  `loggerNames` / `loggers` / `oldLevel` / manual attach / `finally` restore.
- Fold the capture into the existing try-with-resources:
  ```java
  try (BufferedWriter bw = Files.newBufferedWriter(outputPath);
       TraceLogCapture capture = TraceLogCapture.attach()) {
      ...
      for (String s : capture.tracedMessages()) { bw.write(s + "\n"); }
      ...
      capture.reset();
  }
  ```
  This removes the entire `finally` block (fixes defects 2 & 3).
- Remove now-unused imports (`Level`, `Logger`, `LoggerContext`, `LoggerFactory`,
  `Pattern` if no longer used elsewhere in the file — verify).

## Decisions to confirm before executing

**D1 — Include `ValidationService`'s own logger (step-0 trace)?**
The plan's canonical list *adds* `edu.dedupendnote.validation.services.ValidationService`
and *drops* the vestigial `...ValidationTests` entry, because ValidationTests emits
no trace and ValidationService is what actually emits the `- 0.` line. Consequence:
the `_FN_Analysis.txt` / `_FP_Analysis.txt` files will now include step-0 lines
that currently never appear, plus the Journal/Pages/Title steps that the stale list
dropped. These files are validation-run artifacts (not asserted in tests), so this
is a content improvement, not a test break — but it *is* a visible output change.
- If you want the literal minimal fix instead (just correct the package to
  `edu.dedupendnote.validation.ValidationTests` and keep it, matching
  MissedDuplicatesTests), the `0` pattern branch stays dead. Not recommended.
- Recommended: adopt the list as written (ValidationService in, ValidationTests
  out). Optionally keep a corrected `edu.dedupendnote.validation.ValidationTests`
  entry too if you expect that class to emit trace later — harmless but currently
  inert.

**D2 — Class name / location.** Proposed `TraceLogCapture` in the existing
`integration/utils` package (next to `MemoryAppender`). It is used from both a
`validation` class and an `integration` class, so `integration.utils` is a slightly
odd home but matches where `MemoryAppender` already lives. Alternative: rename the
package to `edu.dedupendnote.testutils`. Recommend keeping it in place to avoid
churn; revisit only if more shared test utilities appear.

## Documentation impact (assessed — minimal)

Checked against the "Keeping this file current" triggers in `CLAUDE.md`:

- **CLAUDE.md** — no change required. No test class is renamed/added/deleted (the
  fixture is a helper, not a test class), no service/algorithm/threshold/domain term
  changes, no build command changes. The `integration/utils` helper package is not
  documented there today. (Optional: a one-line "test utilities" mention could be
  added, but it is not triggered.)
- **docs/architecture.html** — no change. Documents the production pipeline and
  service map; a test-only fixture does not belong.
- **docs/algorithm.md** — no change. The `0|1|2|3|4` step numbering is unchanged;
  step 0 already existed in the code, only its capture is being fixed.
- **docs/test-hierarchy.md** — no change required (it lists test *classes*;
  `MemoryAppender` and the new `TraceLogCapture` are helpers and are not listed
  today). Optional: add a short "Test utilities" note naming both. Left out unless
  requested.
- **CONTEXT.md / docs/adr/** — no change. No domain term or architectural decision
  changes; this is an internal test-support refactor.

Net: no mandatory doc edits. The only judgment call is whether to add an optional
test-utilities note to `docs/test-hierarchy.md`.

## Verification

1. Compile (NullAway + Error Prone are build errors):
   `./mvnw -q -DskipTests test-compile` — expect clean; watch for unused-import and
   null-safety failures (use `tail -20` / grep `NullAway|error:|BUILD`, not `tail -5`).
2. `./mvnw -Dtest=MissedDuplicatesTests -Pintegration-tests test` — all three
   parameterized cases still pass; the `isGreaterThan(0)` assertion holds (adding
   loggers only adds messages).
3. Confirm no logger pollution: after the run, `MissedDuplicatesTests` loggers are
   restored (the new `@AfterEach` guarantees detach + level restore).
4. Optional (slow): run a single validation set and eyeball a generated
   `_FN_Analysis.txt` / `_FP_Analysis.txt` — it should now contain step-0 lines and
   Journal/Pages/Title comparison trace that were previously missing.

## Files touched

- `src/test/java/edu/dedupendnote/integration/utils/TraceLogCapture.java` (new)
- `src/test/java/edu/dedupendnote/integration/MissedDuplicatesTests.java`
- `src/test/java/edu/dedupendnote/validation/services/ValidationService.java`
- (no production `src/main` changes; no doc changes required)
