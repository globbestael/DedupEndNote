Starting with the following 4 observations

## 1
The current src/test/java/edu/dedupendnote/unit/services/AuthorVariantsExperimentsTest test file tests the performance of several alternative normalizations of Author input fields.
Those alternative normalizations are functions in src/test/java/edu/dedupendnote/domain/PublicationExperiment.
In the recent past there have been some refactorings where the normalization is extracted to the static functions within the class NormalizationService (and IOService::addNormalizedAuthor)

The tests in AuthorVariantsExperimentsTest are different from the current unit and integration tests:
- they measure and compare the normal normalization with the performance (similarity) of the alternative normalizations, showing the cases 
  where the normal normalization is (1) better than the alternative normalization, (2) worse, or (3) both the normal and alternative normalization are below the threshold, 
  and showing a summary
- the assertion is a dummy assertion (proving as it were that this is not a real test)

These tests are not really unit tests, but more experiments and learning tests: they show the developers how alternative normalizations for the Author field perform compared 
with the normalization currently used.

They are not really tests of normalization because they compare the JWSimilarity to a threshold: this belongs more to the comparisonService than the normalizationService and its tests.

## 2
The current src/test/java/edu/dedupendnote/integration/services/AuthorExperimentsTest test file is a minimal case for an alternative comparisonService and its functions. 
This comparisonService for the Authors is up to now the only comparisonServce which is extracted from ComparisonService.
The test file tests only if the number of duplicates found diminishes if the thresholds (AUTHOR_SIMILARITY_NO_REPLY etc) are higher. What we also want to know is how the performance
is affected if the alternative normalizations of one or more fields (see ## 1) are used.

The current test file deduplicates the same file twice, once with the normalizations and comparisons and thresholds from the production code, and once with the alternatives. 
The numbers of duplicates of the first group are known from our test file src/test/java/edu/dedupendnote/integration/services/ValidationTests 
(in Map<String, ValidationResult> validationResultsMap). So the new integartion tests should not deduplicate the test files twice.

## 3
The DeduplicationService can handle alternative implementations for the AuthorComparisonService only. Should each of the compare functions of ComparisonService used in the compareSet
function be extracted to a seperate Default...ComparisonService. 

Maybe some of these compare functions functions in compareSet could be merged. There are merged functions already 
(compareStartPagesOrDois which compares by 2 fields startingPage and dois).
The functions compareSameDois, compareIssns and compareJournals can be grouped into one function, possibly a new function which calls them with the same logic (return a() || b() || c();).
Would this make the experiments with alternative comparisons and normalizations easier?

## 4
If it becomes easier to plugin alternative comparisonservices, normalizations, thresholds, then we might presents some of these alternatives as a choice to the user of the web application.
E.g. a normal mode and a strict mode, where the strict mode would be less forgiving for false positives


END OF OBSERVATIONS

What if we made 

- Be consistent with the division between normalizationServices and comparisonServcies and their tests
- Alternative normalizations only exist for the Author field. Alternative normalizations for other fields might be worthwhile.
- How can we measure the performance of the deduplication of alternative normalization of 1 field?
- How can we measure the performance of the deduplication of alternative normalization of mor than 1 field?
- The ExperimentalAuthorsComparator is an inner class of AuthorExperimentsTests. If we use more alternatives for the comparisonservice and normalizationservices, 
  should these implementations be separate classes? The name ExperimentalAuthorsComparator should be changed to ExperimentalAuthorsComparisonService
- It should also be possible to measure the performance of alternative thresholds?

Realizing the wishes may bring about a lot of changes. It is possible that the complexity might be too great (complexity as absolute value (cyclomatic complexity?) but also 
as a more vague measure for the developer

What is your opinion about this.
For the moment I don't want you to write a plan, but to get some idea of what this would entail, possibly with a global breakdown into steps. 

================

Continuing the thinking process from .thinking/2026-05-02-experiment-infrastructure.md:

Can you write the following prompt and your answer to a new file (in MarkDown format) in this .thinking folder.

I think that the distinction unit tests versus integration tests is incomplete:
- there certainly are unit tests
- there certainly are integration tests. E.g. in the folder src/test/java/edu/dedupendnote tests like DedupEndNoteApplicationTests (function deduplicateSmallFiles, 
  deduplicate_withDuplicateIDs, deduplicate_OK) and MissedDuplicatesTests (function deduplicateMissedDuplicates). These tests test the string returned by the deduplicationService, 
  and in a sense the number of input records and the number of output records.
  
The current test checkAllTruthFiles in src/test/java/edu/dedupendnote/integration/services/ValidationTests does ***not*** test the string returned by the normal flow 
in the deduplicationService, and in this sense is ***not*** an integration test.
What this test does is:
- (1) mimicks the first part of the normal flow in the deduplicationService
- (2) compares a gold set of truth records with these deduplicated records, and stores the result of the comparisons (true positive, true negative, false positive, false negative) as
  attributes of these deduplicated records   
- (3) has it own routine to write the deduplicated records (included these extra fields TP etc in a tab delimited format
- (4) writes a summary of TP etc and scores (accuracy, precision, sensitivity etc, see Validationresult) and compares them with stored scores etc
- (5) optionally writes the False Positive and False Negative results

The previous talk about checking the performance of alternative normalizations and comparisonservices are related with what is happening in ValidationTests. 
Let's call checkAllTruthFiles in ValidationTests "ValidationOfProduction", and checking the performance of alternative normalizations and comparisonservices "ValidationOfAlternatives".

The main difference is that ValidationOfProduction tests the performance of the "alternatives" which ***are*** chosen for the production version,
while the ValidationOfAlternatives (and the alternatives in ExperimentalAuthorsComparator and AuthorVariantsExperimentsTest) test or should test the performance 
of the alternatives ***not*** chosen.
Another difference is that the ValidationOfProduction should be run regularly to see if code changes change the scores positively of negatively, whereas the
ValidationOfAlternatives are not tests which should be run regularly, but maybe a command which should be called in the web interface in admin mode?
If the ValidationOfAlternatives are really production code (part of src/main/java), then maybe most of the code for ValidationOfProduction should also be part 
of the production code and the test itself (in src/test/java) would call the code in src/main/java. Let's call this test "ValidationOfProductionTests".

The code for ValidationOfProduction and ValidationOfAlternatives does not have to be available on the production server. It would be sufficient if it can be run 
on the development server/computer. The files needed for ValidationOfProduction and ValidationOfAlternatives should ***not*** be part of github data, but be reachable 
by test code or production code for the developers.

Does this proposed division between (1) unit tests, (2) integration tests, and (3) ValidationOfProductionTestsmakes sense?

It will probably be necessary to refactor the current ValidationOfProduction so that the first parts of the flow are more in line with the normal flow in production code.
Suppose the first steps would call the normal deduplication of 1 file (should this be in MarkMode?), then pick up the file with deduplicated (or marked?) records, transform them in the
tab delimited format, .... In that case there would/could be no divergence between the real and the "test" deduplication.

Once this refactoring is done, then the ValidationOfAlternatives probably would be quite different from the current form and from the proposal you made in the previous .thinking file.
Maybe they would differ from the ValidationOfProduction in the first part (inject alternative normalizationService, ComparisionService), but would the second part (postprocessing 
of the output file, comparison with truth files, reporting the scores) be the same.

What is your opinion about this.
For the moment I don't want you to write a plan, but to get some idea of what this would entail, possibly with a global breakdown into steps. 


==============

Please switch to plan mode.
Can you write separate plan files in the .plans folder which will roughly correspond with the steps 1 - 4 you proposed in the last .thinking file, and order them in the order you proposed 
("Sequential execution (1 → 3 → 2 → 4 → 5) keeps each step independently reviewable and the tests green throughout."). I will have to rethink the step 5, and will do that later on.
Please do not start with the execution of these 4 plan files, I will call for the execution individually and one by one.
Please use the format of the plan files as described in CLAUDE.pm

## Plan for step 1 (Taxonomy clarification)
- Move ValidationTests to a new validation/ package
- Update pom.xml profile filters so that there is a seperate filter for the Test files in the new validaion package
- Can you rename the test integration files with a name containing "Test" but not "Tests", so that the profile filter for the integration tests needs only the include line 
  "<include>**/integration/**/*Tests.java</include>"
- Update CLAUDE.md with the three-way taxonomy

## Plan for step 3 (Extract shared postprocessing)
- Extract checkResults() logic from ValidationTests into a reusable ValidationService class in src/test
- The signature should accept a DeduplicationService (or an interface) as a parameter

## Plan for step 2 (Align ValidationOfProduction with production flow)
I would prefer the solution b you proposed in the section "On the divergence in the current ValidationTests.deduplicate()" of the .thinking file 
("Call deduplicateOneFile in mark mode, write the output, then re-read that output file and extract dedupids").
Can you add a comment comparable to your comment "The user's proposal — run deduplicateOneFile in mark mode, then pick up the output file — would close this gap completely, 
because the validation would exercise exactly the production code path." to the altered test function.

Please pay attention to the following:
- The existing IOService:: readPublications does **not** read the Label field ("LB") of a RIS file because we want Mark mode to add this field in the output file, 
  and be certain that no old Label field content is present in the output field. User documentation warns against this overwriting of the Label field
- for this ValidationOfProduction (and later ValidationOfAlternatives) when the output is read, the Label field should be read. This is the only case where this should happen.
  So there might be an separate readPublications(String, Consumer) call which calls readPublications(String, Consumer, Boolean includeLabelField = false), and 
  ValidationOfProduction would call readPublications(String, Consumer, Boolean includeLabelField = true).
  Please add appropriate comments to the functions.

## Plan for step 4 (ValidationOfAlternatives using shared infrastructure)
- Refactor AuthorExperimentsTests to use ValidationService.checkResults() with an injected experimental service
- Remove the double-deduplication; use known scores from validationResultsMap as baseline
- Report sensitivity/specificity rather than just duplicate counts


==============

Please leave plan mode.

I would like a new thinking session of step 5 from .thinking/2026-05-04-test-taxonomy-and-validation.md
Can you write the following prompt and your answer to a new file (in MarkDown format) in this .thinking folder.

- Make thresholds constructor-injectable in DefaultAuthorsComparisonService (and others if extracted)
  For the moment only 1 alternative threshold is used in tests (ExperimentalAuthorsComparator implements AuthorsComparisonService uses other values for AUTHOR_SIMILARITY_NO_REPLY). 
  (1) Other thresholds of (Default)AuthorsComparisonService should be pluggable. This enables threshold experiments without subclassing. Maybe thresholds need a class of their own. 
  (2) Thresholds in other (Default)*ComparisonServices must also be pluggable.
- Would this mean that the current AuthorExperimentsTests does not need a ExperimentalAuthorsComparator because it 
  uses the DefaultAthorComparisonService with alternative thrsehold? 
- Would this mean that for experiments with other comparison functions, we need seperate comparisonServices for each field / combination of fields each with their own compare function. 
  Constructor of a comparisonService would need to have a parameter reference to a defaultCompare or an alternative*Compare function
- Would this mean that for experiments with alternative normalizations, we should refactor IOService and NormalizationService so that alternative normalizations are pluggable.
  E.g. The IOService has a constructor or a setNormalizationServices where alternative normalizations can be specified (hash of lambda functions or of functions references) and
  be used for the construction of a NormalizationService.

- Choose the right name: in the previous prompts but also in the (test) code we use names like "alternatives", 
  "experiments",  "tests" and "performance". This concerns also the package name, file names and functions. 
  Can we choose 1 name, which one?

