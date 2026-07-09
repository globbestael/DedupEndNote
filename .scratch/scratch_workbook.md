Can we switch to a thinking mode

We have used the skill grill-with-docs to write a.o. the file CONTEXT.md
Looking back on this file (based on my answers) I'm not very happy with some of the answers I gave, especially not the one centering on Publication: I have mixed up Publication and bibliographic item/record.

# Some better answers

But first 2 other terms:
- bibliographic database: a database with bibliographic items. A researcher can search and export bibliographic items, but cannot add or change them. E.g. PubMed, EMBASE, Web of Science
- reference manager database: a database made with reference manager software (e.g. EndNote, Zotero) containing bibliographic items. A researcher usually imports items exported from bibliographic databases, but can also add them manually. Both types of bibliographic items can be changed and exported by a researcher. These exports of bilbioraphic items from reference manager databases are the input files for DedupEndNote

Back to Publication and Bibliographic item:

- A publication is e.g. a book or an article in a journal.
- A bibliographic item / record is a record in a bibliographic database or a reference manager database with partial information copied from a publication (e.g. author names, title, journal, journal issue, journal volume, starting page, abstract). The full text of the publication is NOT part of the bibliographic record. The information copied in a bibliographic record can differ from the information in the publication because bibliographic databases and reference manager databases may have their own conventions for recording this type of information (e.g. original author names of the format last name + first names can be changed to a format lastname + initials).

In time a publication exists BEFORE corresponding bibliographic items for this publication are added to bibliographic databases of reference manager databases.

Normally a publication has full text and an abstract (a bibliographic database or reference manager database normally imports only the abstract). The only type of publication which has only an abstract is a Conference abstract (also called Meeting abstract). Because this is published in a journal (exists BEFORE the bibliographic item) it is considered a publication.

# What I would like your opinion about

## Change CONTEXT.md
The current CONTEXT.md will change quite a lot. Can you rewrite CONTEXT.md by yourself or will you need the skill grill-with-docs for this?

## Terminology in CONTEXT.md is not used in current code
If we want to use the same terminology in the code as in CONTEXT.md, this could be a big refactoring.
How useful would this be?
If it is useful, what is the best way to make his refactoring: use the skill to-prd (and then to-issues) or can you do this

## Terminology in CONTEXT.md is not used in current user-facng documentation
The user-facing documentation in the folder src/main/resources/templates (index.html, twofiles.html and details.html) does not use the terminology of CONTEXT.md.
- Is it better to alter the documentation
- If so, is it better to use the skill to-prd (and later to-issues) or can you do this


 ==============

Can we switch to a thinking mode again.

Please stay in thinking mode.
Forget my previous question and your answers. I want to redo this completely.

## The id field of BibliographicItem
The BibliographicItem POJO now has a @Nullable Integer id.
There are 3 cases in IOService::readBibliographicItems for the field ID of the input files:
1. the field ID is not present: the program assumes that the input file is a Zotero export file, and gives each record a new ID (starting with 1)
2. the field is present but can't be parsed as an int: an exception is thrown, the current record is skipped, only the bibliographic items which had been read up to now are returned. The receiving function does ***NOT*** know that not all bibliographic items have been read.
3. the field is present and can be parsed as an int: normal case

The second case should stop the deduplication with an appropriate message ("The input file contains ID fields which are not numbers. The input file is not an Export as RIS-file from an EndNote library!". The function IOService::readBibliographicItems should be able to signal this exceptional situation, maybe by throwing a new kind of Exception with the above message as its message.

Since the other cases always produce a non-null id, the "@Nullable Integer id" can be changed to a simple "int id"?
Which old null tests should be altered to a test id == 0?


## The publicationYear field of BibliographicItem
The field publicationYear can probably be changed to an int.

## Other doSanityChecks?
Only then (after IOService::readBibliographicItems) is DeduplicationService::doSanityChecks called on the returned List<BibliographicItems>.
This functions does 3 tests:
1. containsBibliographicItemsWithoutId: this cannot occur after IOService::readBibliographicItems if the next exception is caught after the call.
2. containsOnlyBibliographicItemsWithoutPublicationYear: tests whether ALL bibliographicItems had either no input field Publication Year ("PY") or a Publication Year < 1850. 
3. containsDuplicateIds

The first test is now superfluous?
The second test should be skipped (all bibliographic items with a publicationYear = 0 is an acceptable situation). The test can be removed but a comment ablout the acceptability must be added.
Th third test is the only one which should be kept.



 
The second test is not what we want:
	- if ALL input records have no field "PY", then the input file can be considered as not EndNote and not Zotero, and the program can exit with an appropriate message
	- if ALL input records have a field "PY" with a year < 1850, then the program should proceed.

This makes the publicationYear field comparable to the id field: would a @NonNull annotation also catch this situation?
Of course the present initialization as "Integer publicationYear = 0;" should be altered.


>>>>>>>> also throw exception at first record without a PY? NO: Embase OVID (BIG_SET) has records without PY
>>>>>>>> Can PY field contain other data that 4 numbers?
>>>>>>>> Should there be a check on negative years? NO, because < 1850 is changed to 0

## Other doSanityChecks?
Would this mean that only the 3 test (containsDuplicateIds) should be called in doSanityChecks?

 
# id of BibliographicItem as int instead of Integer?
Since a @NonNull annotation does make sense on a int, we cannot change the id from an Integer to an int. 
If we do not use the annotation @NonNull, then the value 0  would be possible for int. Can a constraint (id != 0) be used as an alternative?



--------------------


Please switch to plan mode.

Consider renaming Similarity*Test and JWSimilarity*Test files to a Default*ComparisonService naming scheme:
This would also require updating the three-category taxonomy in CLAUDE.md.

An maximal list would be:
- SimilarityIssnTest        → DefaultJournalComparisonServiceIssnTest  (compareIssns lives on DefaultJournalComparisonService)
- SimilarityJournalTest     → DefaultJournalComparisonServiceTest
- SimilarityTitleTest       → DefaultTitleComparisonServiceTest
- JWSimilarityAbstractTest  → AbstractComparisonServiceJwsTest         (abstract base; rename together with subclasses)
- JWSimilarityAuthorTest    → DefaultAuthorsComparisonServiceJwsTest
- JWSimilarityJournalTest   → DefaultJournalComparisonServiceJwsTest
- JWSimilarityTitleTest     → DefaultTitleComparisonServiceJwsTest

But the above proposal is probably too extreme.

If a test function calls one of the Default*ComparisonService().compare() functions and tests the boolean return value, then it belongs to a test class with a name 
Default*ComparisonServiceTest. If however the test function has assertions on a Double (a JaroWinkler Similarity score) then it should stay in the class it belongs to and this class should not ne renamed.
It is possible that test functions of different origin test classes will have to be moved to the same renamed test class.
If is also possible that tests classes which are moved to another test class, will have the same or similar name as an existing tests class. Please add comments to these classes. 



About the renamings:
- if test function does NOT use a Default*ComparisonService().compare() function, then do not rename the test class to a Default*ComparisonServiceTest pattern but keep a (JW)Similarity*Test type name.
- if tests of one or more test classes call the same Default*ComparisonService().compare function, please put the tests in the same Default*ComparisonServiceTest class
About the comments added about the companion boolean-compare() tests: Can you mark these comments with "TODO:"

Sorry, I hadn't payed atention to DefaultJournalComparisonService.compareIssns(), this case can be treated in the same way as the Default*ComparisonService.compare() functions


---------------
Please switch to plan mode.

There are OWASP 10 risks in this program.

Can you analyze those risks ? And prioritize the steps to be taken.

----------------
What is your opinion on these 2 alternatives?

public ResponseEntity<String> consecutiveTryCatch(@RequestParam MultipartFile file) {
	Path path;
	try {
		path = UtilitiesService.resolveInUploadDir(uploadDir, file.getOriginalFilename());
	} catch (IllegalArgumentException e) {
		log.warn("Path traversal attempt in uploadFile: {}", e.getMessage());
		return ResponseEntity.badRequest().body("{\"result\": \"Invalid filename\"}");
	}
	try {
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			Files.delete(path);
		}
		try (InputStream inputStream = file.getInputStream()) {
			Files.copy(inputStream, path);
			return ResponseEntity.ok("{\"result\": \"File uploaded successfully\"}");
		}
	} catch (IOException e) {
		log.error("Error uploading file", e);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"result\": \"Upload failed\"}");
	} catch (RuntimeException e) {
		log.error("Unexpected error uploading file", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"result\": \"Upload failed\"}");
	}
}

public ResponseEntity<String> combinedTryCatch(@RequestParam MultipartFile file) {
	try {
		Path path = UtilitiesService.resolveInUploadDir(uploadDir, file.getOriginalFilename());
		if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			Files.delete(path);
		}
		try (InputStream inputStream = file.getInputStream()) {
			Files.copy(inputStream, path);
			return ResponseEntity.ok("{\"result\": \"File uploaded successfully\"}");
		}
	} catch (IllegalArgumentException e) {
		log.warn("Path traversal attempt in uploadFile: {}", e.getMessage());
		return ResponseEntity.badRequest().body("{\"result\": \"Invalid filename\"}");
	} catch (IOException e) {
		log.error("Error uploading file", e);
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"result\": \"Upload failed\"}");
	} catch (RuntimeException e) {
		log.error("Unexpected error uploading file", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"result\": \"Upload failed\"}");
	}
}

---------
Let's swicth to thinking mode.
The application often uses "fileName" (also "inputFileName" and "outputFileName" etc) where a pathName is meant. Wouldn't it be better to be consistent in the whole application, 
at least in the code and the comments. For messages to the user and the documentation this distinction may not be relevant: if you find cases where a real pathName is meant, please give an overview 
of these cases.
What's your opinion on this.
Any suggestions how we can tackle this.

----------
There are a couple of cases in the program where a new filename (String) is constructed based on another filename.
Example (ValidationTests l.143):
	Path fpAnalysisPath = inputPath.resolveSibling(inputPath.getFileName() + "_FP_Analysis.txt");
The problem with the above code is that the new filename still contains the orginal extension as part of the name ("*/own.ris" gets changed to "*/owned.ris_FP_Analysis.txt").
Most cases will use path.resolveSibling, but there may be real String comcatenations (String c = StringA + StringB).
There may be cases where only the extension is replaced.

I added a static function "String removeFileExtension(String filename, boolean removeAllExtensions)" to src/main/java/services/UtilitiesService, and
a static function "String removeFileExtension(String filename)". The last one can be used to solve that problem
("*/own.ris" will be changed to "*/owned_FP_Analysis.txt").

Can you look into this
===============

There are several places within the program where filenames are constructed based on other filenames, and where a small set of filenames are hard coded which have related names.
# Constructed filenames
Example (ValidationTests l.690):
```
Path markPath = inputPath.resolveSibling(UtilitiesService.removeFileExtension(inputPath.getFileName().toString()) + "_mark.txt");
```

In UtilitiesService the functions createOutputFileName and createOutputPath use another way to get similar results. In principl they ould use the above method.

# sets of related filenames
In ValidationTests there is a lot of duplication in functions which create Paths where the filenames are related in a systematic way.
E.g.
```
	void createInitialTruthFile_McKeown_2021() {
		Path dir = testDir.resolve("McKeown_S_2021/dedupendnote_files");
		Path inputPath = dir.resolve("McKeown_2021.txt");
		Path outputPath = dir.resolve("McKeown_2021_for_truth.txt");
		createInitialTruthFile(inputPath, outputPath);
	}
```
and
 
```
	ValidationResult checkResults_SRA2_Stroke() throws IOException {
		Path truthPath = testDir.resolve("SRA2/Stroke_TRUTH.txt");
		Path inputPath = testDir.resolve("SRA2/Stroke.txt");
		Path outputPath = testDir.resolve("SRA2/Stroke_to_validate.txt");

		return checkResults("SRA2_Stroke", inputPath, outputPath, truthPath);
	}
```

and 

```
	void createInitialTruthFile_ASySD_Diabetes() {
		Path dir = testDir.resolve("ASySD/dedupendnote_files");
		Path inputPath = dir.resolve("Diabetes.txt");
		Path asysdInputPath = dir.resolve("Diabetes_asysd_gold.txt");
		Path outputPath = dir.resolve("Diabetes_for_truth.txt");
		createInitialTruthFile(inputPath, asysdInputPath, outputPath);
	}
```

# new utility function?
Maybe a new function in UtilitiesService could help:
possible functionName createPath.
3 arguments:
- Path (this path should refer to a file)
- String for the addition to the fileName, e.g. "_mark", possibly null, empty and blank string is considered the same as null
- String new extension, cannot be null, empty or blank

The function removes the last extension of the filename before adding the non-null addition and adding the new extension.

I want to examine if this new function would help to:
- remove the duplication of the first item
- change the hardcoded Paths in "sets of related filenames" to standard calls to this new function, and replace the functions like "checkResults_SRA2_Stroke()" with calls to 
  a function like 'checkResultsFor(testDir.resolve("SRA2/Stroke.txt"))" 
  and "createInitialTruthFile_ASySD_Diabetes()" with 'createInitialTruthFileFor(testDir.resolve("ASySD/dedupendnote_files/Diabetes.txt"))".

I you see better solutions, please let me know

Once ready, please write a plan in the .plans/ folder. Do not start executing the plan yet.


--------------

 ┌────────────────────────────────┬────────────────────────────────────────────────────────────────────────────────┐
  │             Import             │                                  Why it stays                                  │
  ├────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────┤
  │ java.io.IOException            │ Standard Java exception; NIO2 throws it too — it's not going away              │
  ├────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────┤
  │ java.io.BufferedReader /       │ Still needed as declared types — Files.newBufferedReader/Writer return these   │
  │ BufferedWriter                 │ types                                                                          │
  ├────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────┤
  │ java.io.StringReader           │ In-memory string wrapping; no NIO2 equivalent                                  │
  ├────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────┤
  │ java.io.InputStream            │ Controller uses it for Files.copy(path, response.getOutputStream())            │
  ├────────────────────────────────┼────────────────────────────────────────────────────────────────────────────────┤
  │ java.io.File                   │ DedupEndNoteApplication passes new File(uploadDir) to Spring's                 │
  │                                │ FileSystemUtils.deleteRecursively, which has no Path overload                  │
  └────────────────────────────────┴────────────────────────────────────────────────────────────────────────────────┘
  
----------------
Sorry, now that I see the implmented plan, I see that I want to make some changes to the plan.

Can you revert the changes to the code (including the html files).
My questions / remarks on the implementation.

1. UtilitiesService::resolveInUploadDir still exists, but this is contrary to expectations.
Originally files were upload to, written to, read from uploadDir. With this plan the files would live in a folder within uploadDir, the folder name being based on the sessionId.
There are checks on the value of sessionId, but sessionId

A whole new function resolveInSessionDir is created

=====================
The Java code uses an enum DeduplicationMode. The html files (index.html and twofiles.html) use a checkbox field markMode. The controller functions startOneFile and startTwoFiles have a boolean markMode argument and transform this in a DeduplicationMode enum value.
We have a old ("markMode") and a new naming (DeduplicationMode). We should completely switch to the new naming and type (old was a String, new is a DeduplicationMode enum)
- if the HTML files / forms would use a radio button deduplicationMode with the 2 possible values, the java functions could use a DeduplicationMode argument. The name of this java argument should not be markMode any more
- the HTML files have a hidden field markModeResultFile: this could be renamed in the HTML files and in the controller deduplicationModeResultFile
- the HTML files have a lot of functions (and maybe variables and id's) with "mark", these should only be related to marking certain parts of the form visibly as DONE or not DONE.
- in the controller the first argument of runDedup is related to the enum (e.g. "1F" + (mode == DeduplicationMode.MARK ? "M" : "D")). This should use the same functionality as for
  DeduplicationMode.filenameSuffix()
If in the HTML files there are still references to DeduplicationMode with names like "mark" (except for "MARK"), please shown them.

uploadTimestamps: {0:0:0:0:0:0:0:1=1781095152021}	Difference: 11935 from now 1781095163956
uploadTimestamps: {0:0:0:0:0:0:0:1=1781095163965}	Difference: 7746 from now 1781095171711

================
The application is using more than 1 version number, and is not using the standard Spring name for the version property.

- The POM file has a project.version with a value "0.9.7b-SNAPSHOT". This could / should be the place where the version number is defined once for the whole application
- This project.version value is part of the JAR name. I want to use a JAR name without that version.part
- The Maven POM property "app.version" is superfluous and can probably be removed.
- The application*.properties files are Spring files and should use the standard Spring property "spring.application.version"
- The AppVersionAdvice class will probably have to be renamed (to ProjectVersionAdvice?)
- The index.html, citation.cff and fragments.html will probably have to change
I would expect that after these changes there will be nor references left to "app.version", "appVersion", and variants.
The Claude file will probably have to be updated

--------------------
The project has several files (CLAUDE.md, CONTEXT.md, docs/architecture.html, docs/Claude_analysis.md) with
  technical information about the project. Please disregard any other files with such information, they will probably
  be removed soon. 
With the files mentioned:
How can this information be reordered so that there is less duplication. Splitting the files, renaming them, moving them are all allowed actions.
BUT: DO NOT OVERWRITE THE EXISTING FILES, but place the new files in the existing .scratch folder, give them names temporary names, mention in these files the preferred names and location.
If you think it is necessary / useful to add other files, please say so. I think it would be good to add ADR's which have hardly been used up to now (see docs/adr folder).
Please write a plan file in the .plans folder.
======================
Especially the edu.dedupendnote.services package has a lot of files. Would it be worhwhile to restructure this package? E.g. by introducing subpackages, sister packages, renaming files by using certain patterns, ... 
===============
I want to upgrade the Javascript library jquery (version 3.x) to the latest version 4.0.0
According to https://jquery.com/upgrade-guide/4.0/#start-here-if-currently-using-jquery-3-x I should use the JQuery Migrate 3.x Plugin.

According to https://github.com/jquery/jquery-migrate/ the present script line for jquery should be replaced with
```
<script src="https://code.jquery.com/jquery-4.0.0.js"></script>
<script src="https://code.jquery.com/jquery-migrate-4.0.2.js"></script>
```
Ultimately I want to use a webjar for jquery 4.0.0

I don't know if other jquery libraries (for file-upload, ui, ...) will have to upgrade too.

Can you make this change, or should I do this manually (a.o. checking for message from the JQuery Migrate 3.x Plugin in the browser console).
If you can do this, can you write a plan for this in the .plans folder
===============
The plugin blueimp-file-upload is no longer actively maintained (GitHub project is archived) so we should look for an alternative way.
You said in the previous plan: "If the plugin is incompatible with jQuery 4.0,
consider replacing it with a native `fetch`-based upload (the upload handler
on the server side does not need to change)."
Can you help with this replacement. 
The current code uses a limit of 1 file upload, checks the file size BEFORE uploading, shows progress, is part of a stepwise procedure (with success the current step is marked as DONE, ...). I want to keep that functionality, the same or equivalent.
Replacement of this library with another is allowed, but I do not want to switch to a full JavaScript framework.


One of the answers:
	The one behaviour change worth noting: the combined-size check in twofiles.html had 150 * 1024 * 2014 (likely a typo for 1024) — I corrected it to 150 * 1024 * 1024. The
	old threshold was ~295 MB instead of 150 MB.
	
----------------
config/spotbugs-security-exclude.xml has only a small set of the Bug Patterns of https://find-sec-bugs.github.io/bugs.htm.
Are these only the bugs (patterns) found while scanning the project?
  Meaning that the include file is not an instruction to these plugins ("include the following patterns of bugs when you scan the project"), 
  but a registration of (1) the errors found by the plugins, and (2) the decision to disregard them, and (3) the description of the error and
  the reason why they are disregarded.

To see bug detail using the Spotbugs GUI, use the following command "mvn spotbugs:gui"

[ERROR] Low: This use of org/slf4j/Logger.error(Ljava/lang/String;[Ljava/lang/Object;)V might be used to include CRLF characters into log messages [edu.dedupendnote.services.BibliographicItemReader, edu.dedupendnote.services.BibliographicItemReader, edu.dedupendnote.services.BibliographicItemReader, edu.dedupendnote.services.BibliographicItemReader, edu.dedupendnote.services.BibliographicItemReader, edu.dedupendnote.services.BibliographicItemReader, edu.dedupendnote.services.BibliographicItemReader, edu.dedupendnote.services.BibliographicItemReader, edu.dedupendnote.services.BibliographicItemReader] At BibliographicItemReader.java:[line 511]At BibliographicItemReader.java:[line 344]At BibliographicItemReader.java:[line 379]At BibliographicItemReader.java:[line 415]At BibliographicItemReader.java:[line 416]At BibliographicItemReader.java:[line 417]At BibliographicItemReader.java:[line 418]At BibliographicItemReader.java:[line 422]At BibliographicItemReader.java:[line 436] CRLF_INJECTION_LOGS

<!--
    <Match>
        <Bug pattern="CRLF_INJECTION_LOGS"/>
    </Match>
-->
----------------
I want to clean up the input and output files used in the tests.
- I want to make a new base directory with a number of subfolders with input files
- current input files would be copied to he new subfolders
- I think (but am not certain) that a 2-level structure (base dir and subfolders) will be enough, but if you think that a different, more elaborate structure would be better, please say so
- there may be cases that a test function needs more than 1 input file
- this basedir folder could be zipped and distributed to other developpers who (of course) will run the tests and need these files
- some of the tests are @Disabled but their input files are relevant
- output files normally get the same base directory and subfolder as the input files
For a start I would like to have a list of testfile + test function + full pathname of the input file, in a tab delimited format.

There is at least 1 case where several tests share the same input file (getValidatedAuthorsPairs). Could you flatten the structure, in this case if there 3 tests which use this input file, split the test file and test function over 3 separate lines, so that first field always is the test class, 2nd the test function, 3rd the pathname of the input file

The tests are group in unit, integration and validation.  Can you add that folder as a new first field.
Does considering this type of test lead to any suggestion to order the input files in a different way?
Can you output the tab delimited file to a file in the .scratch/ folder

----
Two questions:
- The baseDir is initialized in the tests with "protected Path baseDir = Path.of(System.getProperty("user.home", "")).resolve("dedupendnote_input_files");". This occurs more than once. Wouldn't it better to initialize baseDir via src/test/resources/application.properties? Or is there a technical reason to initialize that varaiable in Java code?
- In a couple of tests the initTestDir() function has not only @BeforeEach but also @Override. Could it be that the overriden initTestDir() is superfluous and could be deleted?

--------------
The BibliographicItemReader uses REPLY_PATTERN, ERRATUM_PATTERN, SOURCE_PATTERN and COMMENT_PATTERN to set the isReply attribute of a BibliographicItemReader.
Some of these pattern are tested in JWSimilarityTitleTest.
- shouldn't the other patterns not be tested?
- for the patterns tested we use not an ArgumentProvider, but have made a selection of cases in an external text file (name starting with "All_"). Should we do this for the other patterns too? 
- Is this the right test class for this? Or should there be a separate test class for BibliographicItemReader?
- In BibliographicItemReader::readBibliographicItems in the big switch statement at the case "TI" (l. 411ff) all 4 patterns are used on the fieldContent to see if this.setReply(true) should be called. BibliographicItem.titles is however not only set by the "TI" input field, but also by "OP", "ST" and "T3" (see e.g. the call to addNormalizedTitle()). Two questions:
  - is it useful to combine those 4 patterns into 1 (big, ugly?) pattern
  - should this check for isReply via these 4 patterns and the check of the PHASE_PATTERN not be applied to those other title input fields ("OP", ...) too, and therefore be moved to the addNormalizeTitle function
-------------------  
For inline coverage in VS Code, install the Coverage Gutters extension, then run any test command above. It reads target/site/jacoco/jacoco.xml and overlays
red/green/yellow indicators per line — use Coverage Gutters: Watch from the command palette to auto-refresh.
---------
Can you switch to plan mode.
The BibliographicItemWriter service uses a boolean variable enhance. This variable probably mirrors the values of the enum DeduplicationMode (DeduplicationMode.REMOVE -> enhance = true, DeduplicationMode.MARK -> enhance = false).
If this so, wouldn't it be better to use this enum in BibliographicItemEWriter. There may be more cases.

-----------------
Some tests (especially validationTests?) write output files. If something goed wrong n a test (e.g. error while reading the file or normalizing a field) the output file of a previous run may still exist. Later on, a developer might think that output file shows the current result of that test run.
Shouldn't the output files be deleted (or truncated) at the start of the test

----------------
What's your take on using static classes.
e.g. in DeduplicationService the constructor has 4 arguments. The last 3 services could be changed to static services?
```
	public DeduplicationService(FieldComparators fieldComparators, BibliographicItemReader bibliographicItemReader,
			BibliographicItemWriter bibliographicItemWriter, EnrichmentService enrichmentService) {
		this.bibliographicItemReader = bibliographicItemReader;
		this.bibliographicItemWriter = bibliographicItemWriter;
		this.fieldComparators = fieldComparators;
		this.enrichmentService = enrichmentService;
	}
```

A class like BibliographicItemReader has a number of static functions. Should these functions really be static (they are not side effecr free?)
================
Muse on the following please. DedupEndNote is a web application, and users have to manually upload one or two input
  files for deduplication. What options are there to make it possible for users to programatically upload a file and
  receive the deduplicated file. Let's start with the easiest of choices: upload 1 file and use the REMOVE method.
  
And what if we would want the 4 present options: 1 or 2 files, REMOVE of MARK?

------------------------

Shouldn't the 2 calls to the enrichmentservice both happen either in the BibliographicItemWriter or in the DeduplicationService? I think the first (both in the Writer service) is to be preferred.
The current BibliographicItemWriter has no knowledge of the Consumer<String> progressReporter, but the BibliographicItemReader already uses this progressReporter.
Could have someinfluence on tests.
===================
I'm not sure about the different steps, a.o. because it could be that the old code was doing too much (e.g. trying to enrich too many bibliographicItems).

There is possibly a duplicate registration in bibliographic item. If so, it may be better to solve this before moving enrichment to the BibliographicItemWriter.

If 2 files are compared, the fact that a bibliographicItem was read from the first file, is recorded (1) in the boolean field isPresentInOldFile with the value true and (2) in the int id field by using the negative value of the id read in. When later in the comparison phase a duplicate bibliographicItem is found, the id (possibly negative) of the other bibliographicItem is copied into the label field. In the output phase (or enrichment phase if this is not yet part of the output phase) the negative value of the label field (startsWith("-")) is used to see if a bibliographicItem is a duplicate of a bibliographicItem from the first file.

- boolean isPresentInOldFile
- int id: when negative then bibliographicItem was from the first file
- String label: when starting with "-" then found as duplicate from a bibliographicItem from the first file

First analysis:
- isPresentInOldFile seems not really used (except in DeduplicationService::deduplicateTwofFiles, line 337 and 348) and it looks as if the test if the bibliographicItem comes from the first file, is always tested by or could be replaced by "getId() < 0". The boolean can be deleted?
- There seems to be no good reason why the label field is not an Integer. The type int may be a problem: a bibliographicItem without any deduplicates should have an empty label field. Using an Integer field instead of an int field could be safer.



Going on with moving enrichment to the bibliographicItemWriter

1 addition: The EnrichmentService::enrich only enriches bibliographicItems WITHIN a duplicateSet, except for the last for loop at line 137: It might be good to extract this loop to a separate enrichCochrane() function, and remove the if at line 110

The way I think it should be:

- deduplicateOneFile
	- MARK mode: 
		- enrich(): none
		- enrichCochrane(): none
		- enrichMap(): none
		- output(): ALL bibliographicItems
	- REMOVE mode: 
		- enrich(): all bibliographicItems with a nonEmpty label field (i.e. which belong to a duplicateSet)
		- enrichCochrane(): all bibliographicItems with isCochrane == true
		- enrichMap(): all bibliographicItems with isKeptBibliographicItem == true
		- output(): ALL bibliographicItems with isKeptBibliographicItem == true

- deduplicateTwoFiles
	- MARK mode: 
		- enrich(): none
		- enrichCochrane(): none
		- enrichMap(): none
		- output(): ALL bibliographicItems with id > 0
	- REMOVE mode: 
		- enrich(): all bibliographicItems with id > 0 AND a nonEmpty label field (i.e. which belong to a duplicateSet)
		- enrichCochrane(): all bibliographicItems with id > 0 AND isCochrane == true
		- enrichMap(): all bibliographicItems with id > 0 AND isKeptBibliographicItem == true
		- output(): ALL bibliographicItems with id > 0 AND isKeptBibliographicItem == true

The selection for enrichMap() is always the same as the selection for the corresponding output()?

Questions: 
- Suppose that in deduplicateTwoFiles all bibliographicItems from the first files would get isKeptBibliographicItem = false, could the same selection be used in
  both deduplicateOneFile and deduplicateTwoFiles?
- isKeptBibliographicItem seems to be only in the selection for REMOVE mode. But the initial value of isKeptBibliographicItem = true. Does this mean that the same selection
  could be used for MARK mode?
  
=============

Can we discuss this further?
First I wanted to answer that I wasn't convinced yet.
Reasons:
A. you group (1) which items are kept, (2) with what corrected data, and (3) how to render a kept item to RIS fields, as: enrich() + enrichCochrane() = 1 + 2, and enrichMap() = 3. In how far is this grouping a consequence of the presence of lines like "bibliographicItemList.stream().forEach(r -> r.setKeptBibliographicItem(false));" in the current enrich() function. In other words: suppose this setting of isKeptBibliographicItem had occurred before this enrich function, would the grouping have been the same, or would the enrich (without the setting of isKeptBibliographicItem) been moved to the EnrichmentService?

B. is more a thinking experiment:
The current name DeduplicationService is maybe wrong: this service (1) calls the BibliographicItemReader, (2) deduplicates the List of BibliographicItems, and (3) calls the BibliographicItemWriter. Only the second action is the real DeduplicationService. Its task are: 
- group the list of bibliographicItems into duplicateSets. A duplicateSet can contain 1 bibliographicItem, meaning that that bibliographicItenm is unique
- mark one of the bibliographicItems within a duplicateSet as the preferred one (isKeptBibliographicItem). Originally the plan was to mark the first encountered bibliographicItem in a duplicateSet as the preferred one. If the current code still uses that ground rule, then setting isKeptBibliographicItem is not necessary: if we use a LinkedHashSet, the getFirst() would always get us the preferred one.
If we take the last output action not as outputting a bibliographicItem but as outputting a duplicateSet, then enrich() can definitely belong to this output phase: in a DeduplicationMode.REMOVE (which basically means "Give us the bibliographicRecords that represents the duplicateSets") we shouldn't assume that the program gives us one the bibliographicItems of a duplicateSet, it could be a made up bibliographicItem as long as it is a fair/good representation of a duplicateSet.

---------------------
data about Github clones
GET https://api.github.com/repos/globbestael/DedupEndNote/traffic/clones

or in PowerShell:
irm -Uri "https://api.github.com/repos/globbestael/DedupEndNote/traffic/clones"

but this needs an authentication token: see https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app

see also: REST API endpoints for repository traffic https://docs.github.com/en/rest/metrics/traffic?utm_source=ld246.com&apiVersion=2026-03-10

==================
- do we know what the influence of the first letter in a JWS comparison
  on l 76 (after JWS comparison) we disallow this case-insensitive difference
- size of Patterns, esp with backtracking (".*")
  - count the number of spaces if the split is on words
  - count of uppercase letters if the split is on letters
- what to do in the case in cases where are too many spaces/letters? 
  - check for every comparison between j1 and j2 if there too many: rather NOT
  - fill in a pattern which cannot match, e.g. randomUUID?
  - ask Claude
- there are cases (but not many) where pattern 2 and pattern 3 are both filled in: can this be avoided
- does "ACP Journal Club	\bACP.*\bJournal.*\bClub.*	null	null" have no third pattern?
- why is "N Y State J Med	\bN.*\bY.*\bState.*\bJ.*\bMed.*	null	null" skipped in latest version
- idem for: "BJOG An International Journal of Obstetrics Gynaecology	\bBJOG.*\bAn.*\bInternational.*\bJournal.*\bof.*\bObstetrics.*\bGynaecology.*	null	null"
- what is the normalized title for "OP  - 嵌合抗原受体t细胞疗法在乳腺癌中的应用进展." (TIL ID 1142)
- 2026-07-09T12:51:12.108+02:00 ERROR 3580 --- [main] e.d.validation.ValidationTests           : - Validating BIG_SET
2026-07-09T12:51:23.380+02:00 ERROR 3580 --- [main] e.d.s.c.DefaultTitleComparisonService    : For publ 22193 or 10772 the titles are too short: '1' or 'portal vein thrombosis with superior mesenteric venous thrombosis a case report and review of the literature'

