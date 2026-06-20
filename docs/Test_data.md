# Test data

## 0. Warning
This file is a work in progress. The original setup of the test files used is changed to a new setup with a clearer structure and documentation.

## 1. General
The project has a nice set of tests. Esp. the integration tests and validation tests use data from EndNote (and Zotero) exports.

1. Some of the test files / Endnote databases are copied from other deduplication programs. See http://dedupendnote.nl:9777/details under Performance.
2. The Database BIG_SET was uilt speciafically for this project. A subset of the duplicates have been validated
3. A couple of databases have been added later. Usually they are user input related to a question / problem with the deduplication.

Databases under 1 and 2 have been used or the development of the program. Databases under 3 have been used to investigate later certain question (TIL Zotero as Zotero export, AI Subset for conference proceedings, ...)

***TODO:*** Fll description of the databases used. Should this be part of the user documentation?

## 2. New setup: part 1
A new folder structure was made with the help of Claude, (most of) the input files for the tests were copied to the new structure with a PowerShell script (mde by Claude). This was done in commit c2f7c65.

The first structure is:
- [HOME]/dedupendnote_files_input
    - integration
        - experiments
        - problems
            - missed_duplicates
            - Rayyan
    - unit
        - all
        - experiments
    - validation
        - AI_subset
        - ASySD
        - Clinical_trials
        - Dedupe-sweep
        - McKeown_S_2021
        - own
        - SRA2
        - TIL


Working with the new structure it became apparent that 
- the folder structure was more complex than necessary
- commented out inputs (e.g. in MissedDuplicatesTests) weren't copied
- refactoring of the tests wasn't finished yet
- there was a lot of valuable data in the old structure which wasn't copied yet / used in tests

A first correction:
```
- [HOME]/dedupendnote_files_input
    - integration
        - experiments
        - missed_duplicates     (Rayyan within problems were also missed duplicates)
    - unit                      (all files sre bigger selections for unit tests)
    - validation
        - AI_subset
        - ASySD
        - BIG_TEST              (instead of own)
        - Clinical_trials
        - Dedupe-sweep
        - McKeown_S_2021
        - SRA2
        - TIL
```

