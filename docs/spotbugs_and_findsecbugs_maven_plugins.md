# SpotBugs and Find Security Bugs Maven plugins

## Links
- [SpotBugs](https://find-sec-bugs.github.io/)
  - [Maven plugin](https://spotbugs.readthedocs.io/en/latest/maven.html) 
- [Find Security Bugs](https://find-sec-bugs.github.io/)
- See [ADR-006](adr/0006-findsecbugs-owasp-sast.md) for the reason why SpotBugs and Find Security Bugs Maven plugins are used in this project.

## spotbugs-security-include.xml limited to security category
In [Maven plugin](https://spotbugs.readthedocs.io/en/latest/maven.html):
```
    Optionally, you can limit the research to the security category by adding files like below

    spotbugs-security-include.xml

<FindBugsFilter>
    <Match>
        <Bug category="SECURITY"/>
    </Match>
</FindBugsFilter>
```
## spotbugs-security-exclude.xml limited to patterns found
Our spotbugs-security-exclude.xml has BUG elements with @patterns like "CRLF_INJECTION_LOGS".
These patterns are from the Find Security Bugs plugin and are described on the [Bugs patterns](https://find-sec-bugs.github.io/bugs.htm) page.


Spotbugs-security-exclude.xml contains only the patterns that SpotBugs actually triggered on this codebase during the first scan. For each one we made a triage decision — false positive or accepted risk — and documented the reasoning. It is a registry of "found, triaged, suppressed," not a pre-emptive list of patterns we think won't be relevant.

The include file is different in nature — it is a pure instruction:
```
  <Match><Bug category="SECURITY"/></Match>
```
This tells SpotBugs to only report bugs in the SECURITY category at all, regardless of what it finds. Without it, SpotBugs would also report CORRECTNESS, PERFORMANCE, STYLE, MALICIOUS_CODE, etc. bugs — which are out of scope here.

So the two files have different characters:
```
┌───────────────────────────────┬────────────────────────────────────┬──────────────────────────────────────────────────────────┐
│             File              │             Character              │                      Content basis                       │
├───────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────────────────────────┤
│ spotbugs-security-include.xml │ Pure instruction — scopes the scan │ Category SECURITY, chosen upfront                        │
├───────────────────────────────┼────────────────────────────────────┼──────────────────────────────────────────────────────────┤
│ spotbugs-security-exclude.xml │ Registry + suppression instruction │ Only patterns actually found and triaged on this project │
└───────────────────────────────┴────────────────────────────────────┴──────────────────────────────────────────────────────────┘
```
A consequence: if future code introduces a pattern like SQL_INJECTION or HARD_CODE_PASSWORD that isn't currently in the exclude file, it will fail the build — which is exactly the desired behavior. The exclude file won't silently suppress it because it was never found and registered.

## Report of the scan
The file target/spotbugsXml.xml if a report of the scan.

IF there are errors there will be:
- a BugInstance element with a @category="SECURITY" and a @type like "CRLF_INJECTION_LOGS", with a child elements for all errors
- a BugPattern element with a @category="SECURITY" and a @type like "CRLF_INJECTION_LOGS", with a child element
Details which contains the same / comparable (?) information as https://find-sec-bugs.github.io/bugs.htm

## What to do with new errors
The current comments in spotbugs-security-exclude.xml were written by Claude when Cluade was asked to add these plugins to the POM.

If new errors occur when Maven runs the verify target, the output will contain content like:

```
[INFO] --- spotbugs:4.10.2.0:check (default) @ DedupEndNote ---
[INFO] BugInstance size is 38
[INFO] Error size is 0
[INFO] Total bugs: 38
[ERROR] Low: This use of org/slf4j/Logger.warn(Ljava/lang/String;Ljava/lang/Object;)V might be used to include CRLF characters into log messages [edu.dedupendnote.controllers.DedupEndNoteController] At DedupEndNoteController.java:[line 107] CRLF_INJECTION_LOGS
[ERROR] Low: This use of org/slf4j/Logger.info(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V might be used to include CRLF characters into log messages [edu.dedupendnote.controllers.DedupEndNoteController] At DedupEndNoteController.java:[line 190] CRLF_INJECTION_LOGS
```
The above error was triggered by commenting out the follwing paragraph in the exclude file
```
    <Match>
        <Bug pattern="CRLF_INJECTION_LOGS"/>
    </Match>
```

The maven output has a.o:
```
To see bug detail using the Spotbugs GUI, use the following command "mvn spotbugs:gui"
```
This GUI is quite good.

Claude's suggestion:

For getting help with a new finding in the future: the simplest path is to run the scan, copy the error line from the output, and paste it into the conversation. Something like:
```
  SpotBugs found a new error: [ERROR] Medium: ... PATH_TRAVERSAL_IN at FileController.java:[line 42]
```
From that I can look up the pattern, read the flagged code, and tell you whether it is a real vulnerability to fix or a false positive to exclude — and if it is the latter, write the exclusion block with the explanation comment in the same style as the existing ones.

The same workflow applies if the scan fails after a future code change: paste the [ERROR] line and I will triage it.


## Questions
- how to prevent formatting of these 2 XML files. Esp. for the comments in the exclude file
- The Find Security Bugs plugin has in its XML reports @category (always "SECURITY"?) and @type (e.g. "CRLF_INJECTION_LOGS"). The SpotBugs GUI allows
  grouping of the bugs by Category, Bug Kind, Bug Pattern. On SpotBug's [Bug descriptions](https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html) there is a 2 level structure (e.g. "BAD_PRACTICE" / "Dm: Method invokes System.exit(…) (DM_EXIT)" https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html#dm-method-invokes-system-exit-dm-exit ). On level 1 there is a "SECURITY" (same as in Find Security Bugs) but these are separate categories (?). Does Find Security Bugs overwrite the patterns  in the Security category of SpotBugs?
- The non-security categories of tests can be run by adding a Match element for each other category in https://spotbugs.readthedocs.io/en/latest/bugDescriptions.html Tested this with STYLE category. I didn't understand some of the reported errors. For the moment no other categories activated but SECURITY.