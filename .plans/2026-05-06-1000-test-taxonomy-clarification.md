# Test taxonomy clarification — introduce validation/ package

## Context

The current two-way split (unit / integration) is misleading. `ValidationTests.checkAllTruthFiles` is not an integration test in the conventional sense: it does not assert on the externally observable behaviour of `deduplicateOneFile`. It is a performance measurement tool that compares deduplication results against manually validated truth files and reports sensitivity/specificity. Grouping it with the integration tests obscures this purpose and risks running it routinely (it is slow and requires large truth files that are not in git).

This plan introduces a third category — **validation** — and moves `ValidationTests` there. It also tightens the integration profile filter by ensuring all integration test runners follow the `*Tests.java` naming convention, and adds a corresponding Maven profile for validation-only runs.

See `.thinking/2026-05-04-test-taxonomy-and-validation.md` for the full discussion.

## Steps

### 1. Move ValidationTests to the new validation package

Move `src/test/java/edu/dedupendnote/integration/services/ValidationTests.java` to `src/test/java/edu/dedupendnote/validation/ValidationTests.java`.

Update the `package` declaration:
```java
package edu.dedupendnote.validation;
```

Update imports that resolved via same-package lookup:
- `edu.dedupendnote.integration.services.RecordDBService` (now cross-package; keep in `integration/services/` for now — will move in the extract-validation-service plan)
- `edu.dedupendnote.integration.domain.ValidationResult` (keep in `integration/domain/` for now — same reasoning)
- `edu.dedupendnote.integration.utils.MemoryAppender` (keep in `integration/utils/` — also consumed by `MissedDuplicatesTests`)
- `edu.dedupendnote.integration.AbstractIntegrationTest` (stays where it is — base class for all Spring tests)

### 2. Rename TwoFilesTest to TwoFilesTests

Rename `src/test/java/edu/dedupendnote/integration/TwoFilesTest.java` to `src/test/java/edu/dedupendnote/integration/TwoFilesTests.java`.

Update the `class` declaration:
```java
class TwoFilesTests extends AbstractIntegrationTest {
```

No import changes needed.

### 3. Update pom.xml profiles

**unit-tests profile** — extend excludes to also exclude the new validation package:
```xml
<configuration>
    <excludes>
        <exclude>**/integration/**</exclude>
        <exclude>**/validation/**</exclude>
    </excludes>
</configuration>
```

**integration-tests profile** — drop the `*Test.java` include (no longer needed after the rename of `TwoFilesTest`):
```xml
<configuration>
    <includes>
        <include>**/integration/**/*Tests.java</include>
    </includes>
</configuration>
```

**New validation-tests profile** — add after the integration-tests profile:
```xml
<profile>
    <id>validation-tests</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <includes>
                        <include>**/validation/**/*Tests.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

### 4. Update CLAUDE.md

Replace the Testing section to document the three-way taxonomy:

- **Unit** (`unit/`) — no Spring context; run every commit with `-Punit-tests`.
- **Integration** (`integration/`) — `@SpringBootTest`; asserts on the string returned by `deduplicateOneFile` or on record counts on small known inputs; run every commit with `-Pintegration-tests`.
- **Validation** (`validation/`) — measures sensitivity/specificity on large validated datasets; run on demand (before releases, after structural changes) with `-Pvalidation-tests`; requires truth files in `~/dedupendnote_files` (not in git).

Update the Test class hierarchy section:
- Move `ValidationTests` out of the integration list.
- Add a Validation section listing `ValidationTests` and describing its purpose.

Update the Commands section:
```bash
./mvnw test -Pvalidation-tests    # Run only validation tests (slow, requires truth files)
```

Update the "Keeping this file current" triggers to include:
- Test class moved between unit / integration / validation categories.

## Files modified

- `src/test/java/edu/dedupendnote/integration/services/ValidationTests.java` → moved to `src/test/java/edu/dedupendnote/validation/ValidationTests.java`; package + imports updated
- `src/test/java/edu/dedupendnote/integration/TwoFilesTest.java` → renamed to `TwoFilesTests.java`; class name updated
- `pom.xml` — unit-tests excludes extended; integration-tests `*Test.java` include dropped; new `validation-tests` profile added
- `CLAUDE.md` — Testing section updated with three-way taxonomy and new profile

## Verification

1. `./mvnw clean test-compile` — no unresolved imports or package errors.
2. `./mvnw -Punit-tests test` — same pass/fail count as before this change.
3. `./mvnw -Pintegration-tests test` — `TwoFilesTests` and other integration `*Tests.java` run; `ValidationTests` does **not** appear in the Surefire report.
4. `./mvnw -Pvalidation-tests test` — only `ValidationTests` runs (requires truth files in `~/dedupendnote_files`).
5. Confirm `DedupEndNoteApplicationTests` and `MissedDuplicatesTests` still run under `-Pintegration-tests` (they already follow `*Tests.java` naming).
