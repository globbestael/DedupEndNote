# Coding standards

Read by the reviewer. This file holds judgement calls only: what no compiler plugin or static analyser can decide. Mechanical rules live in tooling (NullAway, Error Prone, SpotBugs; see `CLAUDE.md`, "Code quality"), not here.

A finding against this file is a judgement call to raise, not a build failure. Implementers write toward the same rules.

## Modern Java

Flag new and changed code that uses an older idiom where the compiler `<release>` in `pom.xml` allows a newer one. Use only final language features.

- **Records** over hand-written data classes. (Records versus Lombok: open in #100.)
- **Switch expressions** with arrow labels instead of `case X:` statements, and pattern matching in `switch` and `instanceof`, including record patterns.
- **Text blocks** for multi-line strings.
- **Sequenced collections** (`getFirst()`, `getLast()`, `reversed()`).

Modernise the code being changed anyway. Leave untouched code for a dedicated PR, so a feature diff stays a feature diff.

Your knowledge of the newest Java features may lag the release target. When unsure whether a feature exists in the target release, read the JDK documentation for that release before choosing the older idiom.
