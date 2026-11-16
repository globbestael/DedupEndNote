package edu.dedupendnote.integration.browser;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitForSelectorState;

import edu.dedupendnote.TestData;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BrowserCancellationTests {

	// comparison-phase test: 805 records, comparison takes several seconds
	private static final Path TEST_FILE = TestData.baseDir().resolve("integration/regression/test805.txt");

	// reading-phase test: generated file of this many records. Reading runs at roughly 20,000
	// records/s on a developer machine (measured), so this keeps the reading phase busy for several
	// seconds — far longer than the time Playwright needs to click Cancel. Must stay below the
	// dedup.max-records limit (100,000), or the run is rejected before reading starts.
	private static final int READING_TEST_RECORDS = 90_000;

	@LocalServerPort
	private int port;

	private static Playwright playwright;
	private static Browser browser;

	private BrowserContext context;
	private Page page;

	@BeforeAll
	static void launchBrowser() {
		playwright = Playwright.create();
		boolean headless = !Boolean.parseBoolean(System.getProperty("playwright.headed", "false"));
		browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
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
		assertThat(page.locator("#buttonStartDeduplication")).isEnabled();

		page.click("#buttonStartDeduplication");

		// Wait for the Cancel button to become visible (dedup task registered)
		page.locator("#buttonCancelDeduplication")
				.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

		// Wait for a live in-progress STOMP message (reading or comparison phase).
		// assertThat().containsText() would also match the DONE message — by then the
		// Cancel button is already hidden. waitForFunction with the !startsWith('DONE')
		// guard catches the "Working on Y for N bibliographic items" messages instead,
		// while the button is still visible and comparison is still running.
		page.waitForFunction("() => { const t = document.getElementById('results').textContent;"
				+ " return t.includes('bibliographic items') && !t.startsWith('DONE'); }");

		page.click("#buttonCancelDeduplication");

		assertThat(page.locator("#results")).containsText("ERROR");
		assertThat(page.locator("#buttonCancelDeduplication")).isHidden();
	}

	@Test
	@Timeout(60)
	void cancelDuringReading_showsErrorAndHidesCancelButton(@TempDir Path tempDir) throws IOException {
		Path largeFile = tempDir.resolve("large_reading_test.txt");
		Files.writeString(largeFile, generateRis(READING_TEST_RECORDS));

		page.setInputFiles("#fileUpload1", largeFile);
		assertThat(page.locator("#buttonStartDeduplication")).isEnabled();

		page.click("#buttonStartDeduplication");

		// The reader reports "PROGRESS: n" while reading, and the page shows it in the progress bar.
		// Wait for the first non-zero value so Cancel is clicked while the reading phase is running.
		page.waitForFunction("() => { const b = document.querySelector('#progress .progress-bar');"
				+ " return b && Number(b.getAttribute('aria-valuenow')) > 0; }");

		page.click("#buttonCancelDeduplication");

		assertThat(page.locator("#results")).containsText("ERROR");
		assertThat(page.locator("#buttonCancelDeduplication")).isHidden();
	}

	// Minimal records: one per journal and title, so nothing is special-cased by the reader.
	private static String generateRis(int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i <= count; i++) {
			sb.append("TY  - JOUR\n")
					.append("ID  - ").append(i).append("\n")
					.append("TI  - Effect of treatment on patient outcomes a multicentre randomised controlled trial ")
					.append(i).append("\n")
					.append("AU  - Smith, John\n")
					.append("PY  - 2020\n")
					.append("SP  - 100\n")
					.append("JO  - Journal of Clinical Medicine ").append(i).append("\n")
					.append("ER  - \n");
		}
		return sb.toString();
	}
}
