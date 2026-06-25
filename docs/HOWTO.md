# HOWTO

Documents questions for developers

## JSpecify: Find unnecessary @Nullable annotations

NullAway and JSpecify do not warn when an argument to a function is called with @Nullable annotation which is superfluous.

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

There is VS Code xtension that could work together with Qodana.

This is ***not*** implemented yet in this project.