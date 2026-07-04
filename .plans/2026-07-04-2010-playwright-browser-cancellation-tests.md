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
| `pom.xml` | Add Playwright dependency + `browser-tests` Maven profile |
| `src/test/java/edu/dedupendnote/browser/BrowserCancellationTests.java` | New test class |
| `CLAUDE.md` | Document new test folder and profile |
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

## Step 2 — Add `browser-tests` Maven profile to `pom.xml`

Inside `<profiles>`, alongside the existing `unit-tests`, `integration-tests`,
`validation-tests` profiles:

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
                        <include>**/browser/**/*Tests.java</include>
                    </includes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

Also ensure the existing `unit-tests` profile exclusion list excludes
`**/browser/**` so browser tests never run under `-Punit-tests`:

```xml
<!-- inside unit-tests profile surefire excludes -->
<exclude>**/browser/**</exclude>
```

## Step 3 — Create `BrowserCancellationTests.java`

New file at:
`src/test/java/edu/dedupendnote/browser/BrowserCancellationTests.java`

```java
package edu.dedupendnote.browser;

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

    // test file: 805 records, comparison takes several seconds — long enough to cancel
    private static final Path TEST_FILE = Path.of(
        System.getProperty("user.home", ""),
        "dedupendnote_input_files", "integration", "other", "test805.txt");

    private static final Path LARGE_FILE = Path.of(
        System.getProperty("user.home", ""),
        "dedupendnote_input_files", "integration", "other", "cancellation_read_test.ris");

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
    @Timeout(30)
    void cancelDuringReading_showsErrorAndHidesCancelButton() {
        // LARGE_FILE must be pre-generated — see "Setup note" below.
        page.setInputFiles("#fileUpload1", LARGE_FILE);
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

### Setup note for `LARGE_FILE`

`test805.txt` (805 records) reads in < 200 ms — too fast to cancel during reading even
with Playwright's browser speed. The reading-phase test needs a file where reading takes
at least 1 s. Options (choose one):

**Option A — pre-generate a large RIS file (recommended):**
Add a `@BeforeAll` that generates a 10 000-record synthetic RIS file to
`~/dedupendnote_input_files/integration/other/cancellation_read_test.ris` if it does not
already exist. Use the same synthetic record shape as `CancellationTests.generateRis()`:
same year, page, author, unique journal per record. Once the file exists it is reused
across runs. This avoids storing a large binary in git.

```java
@BeforeAll
static void generateLargeFile() throws Exception {
    if (Files.exists(LARGE_FILE)) return;
    Files.createDirectories(LARGE_FILE.getParent());
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= 10_000; i++) {
        sb.append("TY  - JOUR\n")
          .append("ID  - ").append(i).append("\n")
          .append("TI  - Effect of treatment on patient outcomes trial ").append(i).append("\n")
          .append("AU  - Smith, John\n")
          .append("PY  - 2020\n")
          .append("SP  - 100\n")
          .append("JO  - Journal of Medicine ").append(i).append("\n")
          .append("ER  - \n");
    }
    Files.writeString(LARGE_FILE, sb.toString());
}
```

**Option B — skip the reading-phase test if the controller test already covers it:**
`CancellationTests.startOneFile_whenCancelledMidRun_returnsErrorWithCancelledMessage`
covers the reading-phase interrupt at the HTTP level. The browser test for
reading-phase cancellation adds Cancel-button visibility — if that coverage is not
required, skip this test and keep only `cancelDuringComparison_showsErrorAndHidesCancelButton`.

---

## Step 4 — Update `CLAUDE.md`

Add a new row in the test class hierarchy table (under Validation, or a new "Browser" section):

```
**Browser (`edu.dedupendnote.browser.*`)**
- **`browser/BrowserCancellationTests`** — Playwright browser tests verifying the Cancel
  button UI, WebSocket `#results` updates, and end-to-end cancellation flow in a real
  Chromium browser; requires Playwright browser binaries installed separately.
  Run with `-Pbrowser-tests`.
```

Add a new row to the test folder/profile table:

| Folder | Profile | Spring context | Run frequency |
|---|---|---|---|
| `src/test/java/edu/dedupendnote/browser/` | `browser-tests` | `@SpringBootTest(RANDOM_PORT)`, real WebSocket | On demand (browser binary required) |

Add a new command to the Commands section:
```bash
./mvnw test -Pbrowser-tests    # Run browser tests (requires: mvnw exec:java -e -D exec.mainClass=com.microsoft.playwright.CLI -D exec.args="install chromium")
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

2. Ensure test file exists at `~/dedupendnote_input_files/integration/other/test805.txt`
   (already used by `DeduplicationServiceTests`).

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

- **Existing `unit-tests` profile**: Verify the exclusion list in the `unit-tests`
  surefire configuration already excludes `**/browser/**`, or add it. The path-based
  filter currently excludes `**/integration/**` and `**/validation/**`; browser tests
  would otherwise be picked up by the default surefire configuration with no profile.
