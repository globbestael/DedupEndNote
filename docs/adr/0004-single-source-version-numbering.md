# ADR-0004 Single-source version numbering on project.version / spring.application.version

**Status:** Decided — implemented in commit (version-consolidation plan, 2026-06-12)  
**Date:** 2026-06-12  
**Context:** The project carried two separate version numbers with different values, causing confusion about which one was authoritative.

## Problem

Before this change the project had:

- `pom.xml` `<version>` = `0.9.7b-SNAPSHOT` — the Maven artifact version; produced a JAR named `DedupEndNote-0.9.7b-SNAPSHOT.jar`.
- `pom.xml` `<app.version>` property = `1.1.7` — a **custom** Maven property driving the user-visible version (web UI, `citation.cff`), filtered into `application.properties` as the non-standard key `app.version`.

Two facts to keep in sync, two values that had drifted. `app.version` is not a standard Spring Boot property and required a custom `AppVersionAdvice` referencing a non-standard `@Value("${app.version}")`.

## Decision

**Single source of truth: the standard Maven project version.**

- `pom.xml` `<version>` is the one place to update. Set to `1.1.7` (the old custom value; the `0.9.7b-SNAPSHOT` scheme is dropped).
- Custom `<app.version>` property removed.
- `application.properties` and `application-test.properties` use `spring.application.version=@project.version@` (standard Spring Boot property; `@…@` is Spring Boot's resource-filter delimiter, so no custom filter configuration is needed).
- `AppVersionAdvice` renamed to `ProjectVersionAdvice`; injects `projectVersion` (was `appVersion`) into Thymeleaf models via `@Value("${spring.application.version}")`.
- Thymeleaf templates: `${appVersion}` → `${projectVersion}`.
- `citation.cff` template: `version: ${project.version}` (was `${app.version}`).
- JAR: `<finalName>${project.artifactId}</finalName>` → `DedupEndNote.jar` (version removed from filename).

## Alternatives considered

### 1. Keep dual properties, just document the convention

Leave `app.version` as-is and document in CLAUDE.md that `app.version` is the user-visible version while `project.version` is the Maven artifact version.

**Rejected** because: the root problem is maintaining two numbers. Documentation of a broken dual-source model does not fix it; the next release would again require two edits in two places.

### 2. Use spring.application.version auto-populated from the jar manifest

Spring Boot can populate `spring.application.version` from the JAR's `Implementation-Version` manifest entry at runtime without any properties-file entry. This works for `java -jar` production deployments but **not** in IDE run/test scenarios where no jar manifest exists.

**Rejected** because: integration tests run without a jar, so the manifest approach would produce an empty or null version in tests. Explicit resource filtering (`@project.version@`) resolves at build time and is available in all execution contexts.

## What to watch for (conditions that would reopen this)

- A pre-release build (snapshot, RC) must be deployed alongside a release build with different user-visible branding — the single `project.version` covers both, but if `SNAPSHOT` in the version string would confuse users, a `display.version` property could be reintroduced.
