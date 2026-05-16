# grill-with-docs session — 2026-05-16

Session goal: build up `CONTEXT.md` glossary from scratch.

---

**Publication**

Q: Is "Publication" the right canonical term for a single bibliographic record being deduplicated, or would you prefer "Record", "Reference", or something else?

A: Keep Publication.

---

**Duplicate / Duplicate Set**

Q: What do you call the relationship between the kept Publication and the others? Is "duplicate" the right word, and do you say a Publication "is a duplicate of" or "matches" another?

A: Duplicate is a relation between 2 publications, and it works in both directions (if A is a duplicate of B, B is also a duplicate of A). In non-Mark mode one of the publications in a Duplicate Set has a preferred status because it is the publication which is kept; in Mark mode that same publication has a preferred status in the sense that its publicationId is used as the label of all publications of a Duplicate Set. "Duplicate" and "is a duplicate of" are the right terms. In non-Mark mode the kept publication can be enriched, so strictly speaking it is not the same publication any more.

---

**Publication Set** (clarification)

Q: You used "Publication Set" once — is that a slip for "Duplicate Set", or a deliberate distinction?

A: My mistake; I should have used "Duplicate Set". If we use the term "Publication Set" it should be the set of all input Publications.

---

**Kept Publication**

Q: What do you call the publication that is retained and possibly enriched — is "Kept Publication" the right term, or would you prefer "Primary", "Representative", or "Canonical"?

A: Kept Publication.

---

**Remove Mode**

Q: What do you call the non-Mark mode? Options: "Remove Mode", "Deduplicate Mode", "Filter Mode".

A: Remove Mode.

---

**Enrichment**

Q: Is "Enrichment" the right term for supplementing the Kept Publication with data from its duplicates, and does it only happen in Remove Mode?

A: Enrichment only happens in Remove Mode.

---

**Single-file / Two-file**

Q: Are "Single-file" and "Two-file" the right terms for the two input configurations?

A: Single-file and Two-file.

---

**RIS format**

Q: Is "RIS" the right term, or do you think of the input as an "EndNote export"?

A: It's more complicated. RIS is the format, but multiple sources use their own variant. Exports from these sources have to be imported into EndNote or Zotero first (possibly with different import definitions) and then exported in EndNote's RIS format. DedupEndNote therefore deals with only one RIS variant: the EndNote RIS variant. The Zotero export in RIS format differs only slightly; those differences are handled while reading. Preferred term: **RIS**, with a note clarifying it means the EndNote variant. The sources that feed into EndNote are called **bibliographic databases**.

---

**Label**

Q: Is "Label" the right term for the Mark Mode marking mechanism, or is there a more precise name that distinguishes it from the general EndNote Label field?

A: Keep "Label". User documentation already warns that the Label field is overwritten in Mark Mode output.

---

**Reply**

Q: Is "Reply" the right canonical term, or would "Response" or "Letter" be more recognisable?

A: "Reply" and "Response" are synonyms; keep Reply. "Letter" is not the same: a letter is a reaction by readers to a publication, while a Reply is a reaction by the original authors to such a letter. Can I change this decision later? — Yes, always.

---

**Cochrane Review**

Q: Is "Cochrane Publication" the term your users would use, or do they say "Cochrane review" or "Cochrane record"?

A: Cochrane review.

---

**Phase Publication**

Q: Is "Phase Publication" what your users would say, or is there a more precise term?

A: Keep "Phase Publication".

---

**ClinicalTrials.gov Publication**

Q: Is "ClinicalTrials.gov Publication" the right term, or do your users have a shorter name?

A: "ClinicalTrials.gov Publication". They are publications which originally appear in the ClinicalTrials.gov bibliographic database, and are often copied in quite a different format in other bibliographic databases.

---

**Special publication types — scope**

Q: Are Reply, Cochrane Review, Phase Publication, and ClinicalTrials.gov Publication terms users would recognise, or are they internal implementation details?

A: Users would recognise these terms, although the users do not determine or change these categories (they belong as it were to the records from the bibliographic databases).

---

**Sensitivity / Specificity / True/False Positives/Negatives**

Q: Are sensitivity, specificity, and the TP/FP/TN/FN terms part of the domain language, or internal testing concepts?

A: Yes, they're part of the domain language.

---

**Normalization**

Q: Is "Normalization" a term your users and developers use, or is it purely internal?

A: Users and developers use this term. The documentation also gives several examples of normalizations.

---

**New Publications / Old Publications (Two-file)**

Q: In Two-file mode, what are the two files called? The CLAUDE.md calls them "new records" and "existing ones".

A: Name them "new publications" and "old publications". The documentation will have to be updated ("/twofiles" refers to example files as OLD_RECORDS).

---

## Outcome

`CONTEXT.md` created at the repo root covering: Publication, Duplicate, Duplicate Set, Kept Publication, Label, Mark Mode, Remove Mode, Single-file, Two-file, New Publications, Old Publications, Normalization, Enrichment, RIS, Bibliographic database, Reply, Cochrane Review, Phase Publication, ClinicalTrials.gov Publication, Sensitivity, Specificity, True/False Positive/Negative.

Outstanding follow-up: update Two-file documentation to replace `OLD_RECORDS` with "old publications".
