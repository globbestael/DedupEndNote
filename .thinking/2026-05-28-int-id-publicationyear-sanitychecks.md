# `int id`, `int publicationYear`, and the doSanityChecks reduction

_2026-05-28_

---

## Question

After changing `BibliographicItem.id` from `String` to `@Nullable Integer`:

**id**: There are three cases in `IOService::readBibliographicItems` for the `ID` field of the input file:
1. `ID` field absent → program assumes Zotero export, assigns sequential IDs starting at 1.
2. `ID` field present but not parseable as int → currently the `NumberFormatException` is caught at the outer try-catch, logged, and the partial list is returned; the caller does NOT know the read was incomplete.
3. `ID` field present and parseable → normal case.

Case 2 should stop deduplication with an appropriate message. `readBibliographicItems` should signal this via a new exception.  
Since cases 1 and 3 always produce a non-null id, `@Nullable Integer id` can become `int id`. Which old null tests should become `id == 0`?

**publicationYear**: Can `Integer publicationYear = 0` also become `int publicationYear`?

**doSanityChecks**: After the above, do the three existing checks still make sense?

---

## Answer

### The exception mechanism for case 2

Currently the outer `catch (NumberFormatException e)` in `readBibliographicItems` (line ~498) logs the error and falls through, returning the partial list. The fix is to intercept specifically inside `case "ID"`, before the outer handler sees it:

```java
case "ID":
    try {
        bibliographicItem.setId(Integer.parseInt(fieldContent));
    } catch (NumberFormatException e) {
        throw new InvalidRisFileException(
            "The input file contains ID fields which are not numbers. " +
            "The input file is not an Export as RIS-file from an EndNote library!");
    }
    break;
```

`InvalidRisFileException` should extend `RuntimeException` (unchecked), so the method signature of `readBibliographicItems` does not need to change. The callers in `deduplicateOneFile` (which wraps the full pipeline) can catch it and convert it to the string return value that `doSanityChecks` already uses for other errors:

```java
} catch (InvalidRisFileException e) {
    progressReporter.accept(e.getMessage());
    return e.getMessage();
}
```

Place `InvalidRisFileException` in `edu.dedupendnote.services` (or a `domain.exceptions` package if the project ever grows one).

### `int id` — which null tests change, and which simply disappear

With `int id`, the Lombok default is `0`. IOService always assigns IDs ≥ 1 (sequential for Zotero, parsed for EndNote). The value 0 means "not yet assigned within the current record being constructed."

| Location | Current test | What to do |
|---|---|---|
| `readBibliographicItems` case "ER" (line ~241) | `if (bibliographicItem.getId() == null)` | → `if (bibliographicItem.getId() == 0)` — the one legitimate `== 0` check |
| `containsBibliographicItemsWithoutId` (line 236) | `r.getId() == null` | Remove the method entirely (see doSanityChecks section) |
| DeduplicationService old-file negation (lines 296–299) | `Integer id = r.getId(); if (id != null) { r.setId(-id); }` | → `r.setId(-r.getId())` — null guard disappears |
| DeduplicationService label assignment (lines 196–200) | `Integer pivotId = pivot.getId(); if (pivotId != null) { pivot.setLabel(...); p.setLabel(...); }` | → direct: `pivot.setLabel(String.valueOf(pivot.getId())); p.setLabel(String.valueOf(pivot.getId()))` |

Summary: **one** check becomes `== 0`; **two** null guards simply become unconditional operations.

### `int publicationYear`

`private Integer publicationYear = 0` → `private int publicationYear` (default `0` by Java spec).

No logic changes. All existing comparisons (`== 0`, `> 0`, arithmetic) work identically. The change removes autoboxing at every call site and makes the "always has a value, 0 means absent/invalid" intent clearer at the type level.

### doSanityChecks reduction

| Check | Current | After the changes |
|---|---|---|
| `containsBibliographicItemsWithoutId` | Guards against `id == null` from a failed Zotero-detection path | **Remove**: with `int`, null is impossible; and the NumberFormatException case now throws before returning, so this can never fire |
| `containsOnlyBibliographicItemsWithoutPublicationYear` | Returns an error if ALL records have `publicationYear == 0` | **Remove**: a dataset where no record has a publication year is valid (e.g., ahead-of-print exports). Add a comment explaining why: the check was removed because a publication year of 0 on every record is acceptable—year-bucketing simply falls back to the year 0 bucket and comparisons still run |
| `containsDuplicateIds` | Returns an error if IDs are not unique | **Keep**: duplicate IDs would silently corrupt deduplication results |

After the changes `doSanityChecks` contains a single active check (`containsDuplicateIds`) and the two removed checks become inline comments explaining why they were dropped.

### Implementation order

1. Create `InvalidRisFileException extends RuntimeException`.
2. Change `BibliographicItem.id` from `@Nullable Integer` to `int`.
3. Update `readBibliographicItems` case "ID" to throw `InvalidRisFileException`; update case "ER" null check.
4. Update the null guards in `DeduplicationService`.
5. Change `BibliographicItem.publicationYear` from `Integer` to `int`.
6. Simplify `doSanityChecks` (remove two checks, add comments).
7. The callers of `readBibliographicItems` in `deduplicateOneFile` and `deduplicateTwoFiles` add a catch for `InvalidRisFileException`.
