# HOWTO

Documents questions for developers

## JSpecify: Find unnecessary @Nullable annotations

NullAway and JSpecify do not warn when an argument to a function is called with unnecessary @Nullable annotation.

In BibliographicItemwriter at 2026-06-24, l.139 the argument bibliographicItem is annotated with @Nullable
```
	private void writeBibliographicItem(Map<String, String> map, @Nullable BibliographicItem bibliographicItem,
			BufferedWriter bw, DeduplicationMode mode) throws IOException {
		boolean removeMode = mode == DeduplicationMode.REMOVE;
		if (removeMode && bibliographicItem != null) {
            ... 
        }
        ...
    }
```

This function is only called at 
```
    writeBibliographicItems(...) {
        ...
        if (bibliographicItem != null && bibliographicItem.isKeptBibliographicItem()) {
            map.put(fieldName, fieldContent);
            if (mode == DeduplicationMode.MARK && bibliographicItem.getLabel() != null) {
                map.put("LB", bibliographicItem.getLabel());
            }
            writeBibliographicItem(map, bibliographicItem, bw, mode);
            numberWritten++;
        }
        ...
    }
```

Claude's answer:
```
> Can IntelliJ do this from the command line?

● Yes — IntelliJ has two options:

  1. Built-in headless inspector (idea inspect)

  "C:\Program Files\JetBrains\IntelliJ IDEA <version>\bin\idea64.exe" ^
    inspect <project-path> <profile.xml> <output-dir> -v2

  - <profile.xml> is an exported inspection profile (Settings → Editor → Inspections → Export)
  - The relevant inspection to include is "Data flow issues" (ID: DataFlowIssue, formerly ConstantConditions) — this covers "parameter is always non-null"
  - Output is XML files per inspection category in <output-dir>

  Caveats: slow cold start (loads a full IDE instance), needs a valid licence, output requires parsing.

  2. Qodana (the practical CLI option)

  Qodana (https://www.jetbrains.com/qodana/) is JetBrains' standalone analysis tool built on the same engine. It runs via Docker or a native CLI:

  docker run --rm \
    -v "$(pwd):/data/project" \
    -v "$(pwd)/qodana-results:/data/results" \
    jetbrains/qodana-jvm

  Output is HTML + JSON, integrates with CI. Free tier covers most inspections including data flow analysis.

  In practice: for a one-off check in this project, running Analyze → Inspect Code scoped to BibliographicItemWriter.java inside the IDE is faster than either CLI option. The
  command-line path is more useful for CI enforcement.
  ```

There is VS Code extension that could work together with Qodana.

This is ***not*** implemented yet in this project.

## VS Code warnings about Null type safety
Example of a warning in VS Code's Problems tab:
```
Null type safety: parameter 'this' provided via method descriptor
  Predicate<BibliographicItem>.test(BibliographicItem) needs unchecked conversion to conform to '@NonNull BibliographicItem'
```

According to Claude (2026-07-08) there is nothing wrong with the code or the set up of the project or VS Code settings
```
So the settings, the annotations, and these exact lines of code have all been stable for months. The only remaining variable is the tooling version. JSpecify @NullMarked
  support in Eclipse JDT / the vscode-java extension has been maturing over the last several releases. A recent auto-update of the Java extension (and its bundled JDT)
  started applying @NullMarked's non-null-by-default semantics more thoroughly across your packages. Once every type is non-null-by-default, JDT starts flagging the boundary
  between your @NonNull code and the un-annotated JDK functional interfaces (Predicate, Function, ToIntFunction, …) — which is exactly where unbound method references live.
  Hence the "parameter 'this' … needs unchecked conversion" messages, appearing now for code that was always the same.

  ...

  Non-destructive ways to quiet it (no code change needed)

  You don't have to touch the method references. Options, least invasive first:

  1. Downgrade just this one check. In Settings → Java › Compiler › Errors/Warnings › Null analysis, set "Unchecked conversion from non-annotated type to @NonNull type" to
  Ignore. This silences the whole JDK-boundary category while keeping real null-analysis (null dereferences, spec violations) active. Can be pinned in
  .settings/org.eclipse.jdt.core.prefs.
  2. Turn automatic null analysis off if you rely on NullAway/Maven as the source of truth anyway: "java.compile.nullAnalysis.mode": "disabled" (or "interactive"). Coarser —
  it drops IDE null analysis entirely.
  3. Only if you prefer per-site silence: convert those specific method references to lambdas (what we discussed) — but that's the one you'd rather avoid, and it isn't
  necessary.

  My recommendation is option 1: it targets exactly this false-positive category and leaves the valuable checks on, with no change to your preferred syntax.
```

If the problem reappears: check .settings\org.eclipse.jdt.core.prefs for the presence of the follwing line:
```
org.eclipse.jdt.core.compiler.problem.nullUncheckedConversion=ignore
```