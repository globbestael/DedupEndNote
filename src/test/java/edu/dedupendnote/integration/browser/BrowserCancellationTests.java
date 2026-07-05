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
import com.microsoft.playwright.BrowserType;
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
		page.locator("#buttonCancelDeduplication").waitFor(
			new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));

		// Wait for a live in-progress STOMP message (reading or comparison phase).
		// assertThat().containsText() would also match the DONE message — by then the
		// Cancel button is already hidden. waitForFunction with the !startsWith('DONE')
		// guard catches the "Working on Y for N bibliographic items" messages instead,
		// while the button is still visible and comparison is still running.
		page.waitForFunction(
			"() => { const t = document.getElementById('results').textContent;"
			+ " return t.includes('bibliographic items') && !t.startsWith('DONE'); }");

		page.click("#buttonCancelDeduplication");

		assertThat(page.locator("#results")).containsText("ERROR");
		assertThat(page.locator("#buttonCancelDeduplication")).isHidden();
	}

	@Test
	@Timeout(60)
	void cancelDuringReading_showsErrorAndHidesCancelButton() {
		// McKeown_2021.txt is large enough that reading takes > 1 s — long enough
		// for Playwright to click Cancel before the reading phase finishes.
		page.setInputFiles("#fileUpload1", MCKEOWN_FILE);
		assertThat(page.locator("#buttonStartDeduplication")).isEnabled();

		page.click("#buttonStartDeduplication");

		// Click Cancel immediately as soon as the button appears — before reading finishes.
		page.locator("#buttonCancelDeduplication").waitFor(
			new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
		page.click("#buttonCancelDeduplication");

		assertThat(page.locator("#results")).containsText("ERROR");
		assertThat(page.locator("#buttonCancelDeduplication")).isHidden();
	}
}
