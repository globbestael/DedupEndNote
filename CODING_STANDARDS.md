# Coding standards

Read during review, not during implementation. This file holds judgement calls only: what no compiler plugin or static analyser can decide. Mechanical rules live in tooling (NullAway, Error Prone, SpotBugs; see `CLAUDE.md`, "Code quality"), not here.

A finding against this file is a judgement call for the reviewer to raise, not a build failure.

## Modern Java

Write new and changed code with the newest language features that the `<release>` target in `pom.xml` allows. Preview features stay off.

- **Records** for immutable data carriers: value objects, thresholds, results. (Records versus Lombok: decided in #100.)
- **Switch expressions** with arrow labels instead of `case X:` statements, and pattern matching in `switch` and `instanceof`, including record patterns.
- **Text blocks** for multi-line strings.
- **Sequenced collections** (`getFirst()`, `getLast()`, `reversed()`) where the target release has them.

Modernise the code you are changing anyway. Leave untouched code for a dedicated PR, so a feature diff stays a feature diff.

Your knowledge of the newest Java features may lag the release target. When unsure whether a feature exists in the target release, read the JDK documentation for that release before choosing the older idiom.

Moving to a newer Java or Spring Boot version: open question in #100.
