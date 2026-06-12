# Consolidate version numbering on `project.version` / `spring.application.version`

## Context

The app carried **two** version numbers:

- `pom.xml` `<version>` = `0.9.7b-SNAPSHOT` — the Maven artifact version, used in the fat-jar filename (`DedupEndNote-0.9.7b-SNAPSHOT.jar`).
- `pom.xml` `<app.version>` = `1.1.7` — a **custom** Maven property that drove the user-facing version (web UI, `citation.cff`), filtered into `application.properties` as the non-standard Spring key `app.version`.

This was confusing: two sources of truth, and the Spring property was non-standard. The goal was a **single** version definition — `project.version` — surfaced through the **standard** Spring property `spring.application.version`. After this change there are **no references left** to `app.version`, `appVersion`, or variants (except historical `.plans/` records, which are immutable). The jar filename also drops the version (`DedupEndNote.jar`).

Decision: the unified `project.version` value is **`1.1.7`** (a release version; the old `0.9.7b-SNAPSHOT` scheme is dropped). No visible change to the web UI or citation.

## Files modified

### `pom.xml`
- `<version>0.9.7b-SNAPSHOT</version>` → `<version>1.1.7</version>`
- Removed `<app.version>1.1.7</app.version>` property (now superfluous)
- Added `<finalName>${project.artifactId}</finalName>` inside `<build>` → produces `target/DedupEndNote.jar`

### `src/main/resources/application.properties` and `src/test/resources/application.properties`
- `app.version=@app.version@` → `spring.application.version=@project.version@`

### `src/main/java/edu/dedupendnote/controllers/AppVersionAdvice.java` → deleted
### `src/main/java/edu/dedupendnote/controllers/ProjectVersionAdvice.java` → new file
- `@Value("${spring.application.version}")`
- Field/method/model attribute renamed: `appVersion` → `projectVersion`

### `src/main/cff/citation.cff`
- `version: ${app.version}` → `version: ${project.version}`

### `src/main/resources/templates/fragments.html`
- `th:text="'v' + ${appVersion}"` → `th:text="'v' + ${projectVersion}"`

### `src/main/resources/templates/index.html`
- `th:text="${appVersion}"` → `th:text="${projectVersion}"`

### `src/main/resources/templates/details.html`
- Three occurrences of `DedupEndNote-0.9.7b-SNAPSHOT.jar` → `DedupEndNote.jar`

### `README.md`
- `DedupEndNote-[VERSION].jar` → `DedupEndNote.jar`

### `CLAUDE.md`
- "Version number" section rewritten to reflect the new model

## Verification
- `./mvnw clean package -DskipTests` → produces `target/DedupEndNote.jar`; root `citation.cff` regenerated with `version: 1.1.7`
- No remaining references: `grep -rn "app\.version\|appVersion\|AppVersionAdvice"` returns nothing outside `.plans/` and `target/`
- `./mvnw test -Punit-tests`: 559 tests, 0 failures
- `./mvnw test -Pintegration-tests`: 31 tests, 0 failures
