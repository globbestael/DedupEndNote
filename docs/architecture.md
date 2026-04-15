# DedupEndNote — Architecture

## Data pipeline

The diagram below shows how a RIS file is transformed into a deduplicated result.
In **two-file mode** the parse step runs twice — once for the OLD file and once for the NEW file — before the combined set enters the comparison step.

```mermaid
flowchart TD
    In[/"RIS input file(s)\n.ris · .txt"/]
    Out[/"result RIS file"/]

    subgraph parse["① Parse & normalise — IOService.readPublications()"]
        direction LR
        IO_R["read RIS fields\n→ Publication objects"]
        Norm["NormalizationService\nnormalise authors · titles\nDOIs · pages · journals"]
        IO_R --> Norm
    end

    subgraph compare["② Compare — ComparisonService  (O(n²) per year bucket)"]
        direction TB
        S1["publication year  ±1"]
        S2["start page or DOI"]
        S3["authors  Jaro-Winkler > 0.67"]
        S4["title   Jaro-Winkler > 0.89 – 0.96"]
        S5["ISSN / ISBN / journal name"]
        S1 --> S2 --> S3 --> S4 --> S5
    end

    subgraph post["③ Post-process — DeduplicationService + IOService.writeOutput()"]
        direction LR
        Dedup["mark duplicates\nOR remove + enrich kept record"]
        Write["write result RIS file"]
        Dedup --> Write
    end

    In --> parse
    parse -->|"List&lt;Publication&gt;"| compare
    compare -->|"duplicate pairs"| post
    post --> Out
```

All five comparison steps must pass for a pair to be considered duplicate.
The comparison short-circuits on the first mismatch, so cheap checks (year, page/DOI) run before expensive ones (Jaro-Winkler similarity).

---

## Runtime interaction — single-file deduplication

The diagram below shows the full request/response cycle, including how progress
is pushed to the browser over a WebSocket while the deduplication runs in a
virtual thread.
In **two-file mode** the flow is identical except that `readPublications()` is
called twice (OLD file, then NEW file) before `compareSet()`.

```mermaid
sequenceDiagram
    actor Browser
    participant Ctrl as DedupEndNoteController
    participant WS as WebSocket<br/>/topic/messages-{id}
    participant DS as DeduplicationService
    participant IO as IOService
    participant CS as ComparisonService

    Browser->>Ctrl: POST /uploadFile (RIS file)
    Ctrl-->>Browser: 200 OK (file stored)

    Browser->>Ctrl: POST /startOneFile (sessionId · fileName · markMode)
    Note over Ctrl: creates Consumer<String> progressReporter<br/>that forwards messages to the WebSocket topic

    Ctrl->>DS: deduplicateOneFile(fileName, markMode, progressReporter)
    activate DS

    DS->>IO: readPublications(fileName, progressReporter)
    activate IO
    Note over IO: fast first pass counts records<br/>second pass parses + normalises
    loop one message per percentage point
        IO-->>WS: progressReporter.accept("PROGRESS: N")
        WS-->>Browser: PROGRESS: N
    end
    IO-->>DS: List<Publication>
    deactivate IO

    DS->>CS: compareSet(publications, progressReporter)
    activate CS
    loop one message per year bucket
        CS-->>WS: progressReporter.accept("PROGRESS: N")
        WS-->>Browser: PROGRESS: N
    end
    CS-->>DS: duplicate pairs identified
    deactivate CS

    Note over DS: mark / remove duplicates<br/>enrich kept records
    DS->>IO: writeOutput(publications, outputFileName, markMode)
    DS-->>WS: progressReporter.accept("DONE: N duplicates found …")
    deactivate DS
    WS-->>Browser: DONE: N duplicates found …

    Note over Browser: "Get result" button enabled

    Browser->>Ctrl: POST /getResultFile
    Ctrl-->>Browser: download result RIS file
```
