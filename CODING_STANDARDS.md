# Coding standards

Read by the reviewer. This file holds judgement calls only: what no compiler plugin or static analyser can decide. Mechanical rules live in tooling (NullAway, Error Prone, SpotBugs; see `CLAUDE.md`, "Code quality"), not here.

A finding against this file is a judgement call to raise, not a build failure. Implementers write toward the same rules.

## Modern Java

Flag new and changed code that uses an older idiom where the compiler `<release>` in `pom.xml` allows a newer one. Use only final language features.

- **Records** over hand-written data classes and over Lombok `@Value` for immutable data carriers.
- **Switch expressions** with arrow labels instead of `case X:` statements, and pattern matching in `switch` and `instanceof`, including record patterns.
- **Text blocks** for multi-line strings.
- **Sequenced collections** (`getFirst()`, `getLast()`, `reversed()`).

Use no preview features.

Modernise the code being changed anyway. Leave untouched code for a dedicated PR, so a feature diff stays a feature diff.

Your knowledge of the newest Java features may lag the release target. When unsure whether a feature exists in the target release, read the JDK documentation for that release before choosing the older idiom.

## Records versus Lombok

Immutable data carriers are records. Lombok stays only where a record cannot do the job: mutable state (`@Data`, `@Getter`/`@Setter` on classes that are filled in while parsing) or builders. Do not add `@Value` to new code; do not convert existing Lombok classes unless they are being changed anyway and are truly immutable.

## Newer Java or Spring Boot versions

The ceiling is the `<release>` in `pom.xml`; this file names no version, so a bump needs no edit here.

- A Dependabot bump of the Spring Boot parent or a raise of `<release>` is verified with the full test profiles, `-Pvalidation-tests` included, and the release notes are read for new features and deprecations.
- After a bump, adopt the new features in touched code only. A dedicated modernisation PR (e.g. with OpenRewrite) is a separate issue.
- Raising `<release>` also changes the CI JDK note (builds on a newer JDK than the target) in `CLAUDE.md` and `docs/architecture.html`.
