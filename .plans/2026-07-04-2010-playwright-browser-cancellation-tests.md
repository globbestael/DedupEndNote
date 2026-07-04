# Plan: Playwright browser tests for cancellation (reading phase and comparison phase)

## Context

`CancellationTests` covers two cancellation scenarios at the controller HTTP level
(`AbstractRandomPortIntegrationTest`):

1. `cancelDedup_withNoRunningDeduplication_returns404` — cancel with no running task
2. `startOneFile_whenCancelledMidRun_returnsErrorWithCancelledMessage` — cancel a
   long-running (3000-record synthetic) task during reading

A third scenario — cancellation specifically during the **comparison phase** — could not
be tested at the controller level. `test805.txt` (805 records) completes comparison in
1–3 s, well before a `Thread.sleep`-based approach can fire a cancel request.

ADR-0010 deferred browser tests but left the door open when JS complexity grows or a CI
pipeline exists. The JS `dedupFinished` latch and the Cancel button visibility are
exercised only in the browser. Playwright adds both: its `containsText` assertion
auto-waits for a DOM condition, which is exactly what "wait until reading is done, then
cancel" requires.

### What the browser tests add beyond the controller tests

| Scenario | Controller test | Browser test (Playwright) |
|---|---|---|
| Cancel fires before any "Working on" messages appear | ✓ (reading phase) | ✓ |
| Cancel fires after reading is done, during comparison | ✗ impractical | ✓ |
| Cancel button appears when dedup starts | ✗ | ✓ |
| Cancel button disappears after terminal message | ✗ | ✓ |
| `dedupFinished` JS latch stops duplicate display | ✗ | ✓ |
| `#results` shows "ERROR: cancelled" | ✗ | ✓ |

---

## Prerequisites (one-time, per developer machine)

1. Add the Playwright for Java dependency to `pom.xml`.
2. Install the Chromium browser binary (once per machine — not CI yet):
   ```
   ./mvnw exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
   ```

---

## Files to create or modify

| File | Action |
|---|---|
| `pom.xml` | Add Playwright dependency; add `browser-tests` and `all-integration-tests` profiles; add exclude to `integration-tests` profile |
| `src/test/java/edu/dedupendnote/integration/browser/BrowserCancellationTests.java` | New test class |
| `CLAUDE.md` | Document new subfolder and profile |
| `docs/adr/0010-controller-tests-over-browser-tests.md` | Update status — browser tests now exist |

---

## Step 1 — Add Playwright dependency to `pom.xml`

Inside the `<dependencies>` block (with other test-scoped deps):

```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.47.0</version>
    <scope>test</scope>
</dependency>
```

## Step 2 — Maven profile changes in `pom.xml`

**Add `browser-tests` profile** inside `<profiles>`, alongside the existing profiles:

```xml
<profile>
    <id>browser-tests</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <includes>
                        <include>**/integration/browser/**/*Tests.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

**Update the existing `integration-tests` profile** to exclude the browser subfolder
(the current include `**/integration/**/*Tests.java` would otherwise pick up browser
tests, which require a separately installed browser binary):

```xml
<!-- inside integration-tests profile surefire configuration, add: -->
<excludes>
    <exclude>**/integration/browser/**/*Tests.java</exclude>
</excludes>
```

**Add `all-integration-tests` profile** — runs the full integration tree including the
browser subfolder. The include pattern is the same as `integration-tests` but with no
exclude, so it naturally picks up everything under `integration/`:

```xml
<profile>
    <id>all-integration-tests</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <includes>
                        <include>**/integration/**/*Tests.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

The `unit-tests` profile needs no change — it already excludes `**/integration/**`.

Summary of the three integration-level profiles:

| Profile | What it runs | Requires browser binary |
|---|---|---|
| `integration-tests` | `integration/` excluding `integration/browser/` | No |
| `browser-tests` | `integration/browser/` only | Yes |
| `all-integration-tests` | All of `integration/` | Yes |

## Step 3 — Create `BrowserCancellationTests.java`

New file at:
`src/test/java/edu/dedupendnote/integration/browser/BrowserCancellationTests.java`

Browser tests are integration tests — they use `@SpringBootTest(RANDOM_PORT)` and test
the same server. They live in `integration/browser/` rather than a separate peer folder.
The `browser-tests` Maven profile selects them specifically; the `integration-tests`
profile excludes the subfolder (browser binary is a prerequisite).

```java
package edu.dedupendnote.integration.browser;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BrowserCancellationTests {

    // comparison-phase test: 805 records, comparison takes several seconds
    private static final Path TEST_FILE = Path.of(
        System.getProperty("user.home", ""),
        "dedupendnote_input_files", "integration", "other", "test805.txt");

    // reading-phase test: large real dataset — reading alone takes > 1 s
    private static final Path MCKEOWN_FILE = Path.of(
        System.getProperty("user.home", ""),
        "dedupendnote_input_files", "validation", "McKeown_S_2021", "McKeown_2021.txt");

    @LocalServerPort
    private int port;

    private static Playwright playwright;
    private static Browser browser;

    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(); // headless by default
    }

    @AfterAll
    static void closeBrowser() {
        browser.close();
        playwright.close();
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext();
        page = context.newPage();
        page.navigate("http://localhost:" + port + "/");
    }

    @AfterEach
    void closePage() {
        context.close();
    }

    @Test
    @Timeout(30)
    void cancelDuringComparison_showsErrorAndHidesCancelButton() {
        // Upload file — triggers AJAX upload via the onChange handler
        page.setInputFiles("#fileUpload1", TEST_FILE);
        // Wait for the Start button to become enabled (upload acknowledged)
        page.locator("#buttonStartDeduplication").waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.ENABLED));

        page.click("#buttonStartDeduplication");

        // Wait for the Cancel button to become visible (dedup task registered)
        page.locator("#buttonCancelDeduplication").waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

        // Wait for the reading-complete progress message — indicates comparison has started
        assertThat(page.locator("#results")).containsText("bibliographic items");

        page.click("#buttonCancelDeduplication");

        // Terminal message contains "ERROR"
        assertThat(page.locator("#results")).containsText("ERROR");
        // Cancel button is hidden again after terminal message
        assertThat(page.locator("#buttonCancelDeduplication")).isHidden();
    }

    @Test
    @Timeout(60)
    void cancelDuringReading_showsErrorAndHidesCancelButton() {
        // McKeown_2021.txt is large enough that reading takes > 1 s — long enough
        // for Playwright to click Cancel before the reading phase finishes.
        page.setInputFiles("#fileUpload1", MCKEOWN_FILE);
        page.locator("#buttonStartDeduplication").waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.ENABLED));

        page.click("#buttonStartDeduplication");

        // Click Cancel immediately as soon as the button appears — before reading finishes.
        page.locator("#buttonCancelDeduplication").waitFor(
            new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.click("#buttonCancelDeduplication");

        assertThat(page.locator("#results")).containsText("ERROR");
        assertThat(page.locator("#buttonCancelDeduplication")).isHidden();
    }
}
```

---

## Step 4 — Update `CLAUDE.md`

Add a new entry to the Integration section of the test class hierarchy (browser tests
are integration tests — they belong under the Integration heading):

```
- Integration test classes in `integration/browser/` (no common Spring-context parent;
  do NOT add `@MockitoBean SimpMessagingTemplate` — real WebSocket required):
  `BrowserCancellationTests` (Playwright end-to-end: Cancel button visibility, `#results`
  WebSocket display, reading-phase and comparison-phase cancellation).
  Run with `-Pbrowser-tests`; requires Chromium binary (see Commands).
```

Update the test folder/profile table — add a new row:

| Folder | Profile | Spring context | Run frequency |
|---|---|---|---|
| `src/test/java/edu/dedupendnote/integration/browser/` | `browser-tests` | `@SpringBootTest(RANDOM_PORT)`, real WebSocket | On demand (browser binary required) |

Note: the `integration-tests` profile excludes `integration/browser/` — running
`-Pintegration-tests` will NOT execute browser tests.

Add new commands to the Commands section:
```bash
./mvnw test -Pbrowser-tests          # Run browser tests only (requires Chromium — see below)
./mvnw test -Pall-integration-tests  # Run all integration tests including browser (requires Chromium)
# One-time Chromium install:
./mvnw exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
```

---

## Step 5 — Update ADR-0010

Change the status line to:
```
**Status:** Partially superseded — 2026-07-04
```

Add a note at the top of the Decision section:
```
**Update (2026-07-04):** Browser tests were subsequently added in `BrowserCancellationTests`
using Playwright for Java. The controller-over-browser decision still applies for new
server-side exception paths; browser tests are now used specifically for UI-layer
verification (Cancel button visibility, `#results` updates, `dedupFinished` latch).
```

---

## How to verify

1. Install browser binary (once):
   ```
   ./mvnw exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium"
   ```

2. Ensure both test files exist:
   - `~/dedupendnote_input_files/integration/other/test805.txt` (used by `DeduplicationServiceTests`)
   - `~/dedupendnote_input_files/validation/McKeown_S_2021/McKeown_2021.txt` (used by `ValidationTests`)

3. Run:
   ```
   ./mvnw test -Pbrowser-tests
   ```

4. Expected output:
   ```
   Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
   ```

5. Observe (with headless disabled temporarily — `playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false))`):
   - Cancel button appears when Start is clicked
   - `#results` shows progress messages
   - After Cancel, `#results` shows "ERROR: cancelled..."
   - Cancel button disappears

---

## Important constraints

- **Do NOT add `@MockitoBean SimpMessagingTemplate`** to `BrowserCancellationTests`. The
  real `SimpMessagingTemplate` is required so WebSocket messages reach the browser's
  STOMP subscriber. The `AbstractRandomPortIntegrationTest` base class adds this mock,
  which is why `BrowserCancellationTests` must NOT extend it.

- **Import clash**: Playwright's `assertThat` is
  `com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat`, not AssertJ's.
  If both are needed, import `PlaywrightAssertions` statically and use
  `com.microsoft.playwright.assertions.LocatorAssertions` for the type. Avoid mixing
  the two static imports in the same class.

- **Browser binary not in git**: `playwright install chromium` downloads ~150 MB to
  a local cache (e.g. `~/.cache/ms-playwright`). This is a developer-machine prerequisite.
  When a CI pipeline is added, install the binary as a CI step.

- **`unit-tests` profile**: No change needed — it already excludes `**/integration/**`,
  which covers `integration/browser/` automatically.
