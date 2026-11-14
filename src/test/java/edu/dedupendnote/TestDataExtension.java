package edu.dedupendnote;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;

/**
 * Fails fast, with one clear message, when the test data is missing or older than the version pinned in
 * {@code testdata.ref}. The check runs once per JVM; every test class then reports the same result.
 *
 * <ul>
 * <li>Directory missing: fail (locally and in CI).</li>
 * <li>Local copy <em>behind</em> the pin (the pinned commit is not an ancestor of {@code HEAD}): fail, run
 * {@code bin/sync-testdata.sh}.</li>
 * <li>Local copy <em>ahead</em> of the pin (a fixture change not yet pinned): warn and run.</li>
 * <li>{@code CI=true}: the pin is not checked, the workflow checks out exactly the pinned commit.</li>
 * </ul>
 */
public class TestDataExtension implements BeforeAllCallback {

	private static final Logger logger = Logger.getLogger(TestDataExtension.class.getName());

	private static final Path PIN_FILE = Path.of("testdata.ref");
	private static final String CHECKED = "checked";
	private static final int GIT_TIMEOUT_SECONDS = 30;

	private record GitResult(int exitCode, String output) {
	}

	@Override
	public void beforeAll(ExtensionContext context) {
		// "" means OK, anything else is the failure message
		String problem = context.getRoot()
			.getStore(Namespace.create(TestDataExtension.class))
			.computeIfAbsent(CHECKED, key -> check(), String.class);
		if (!problem.isEmpty()) {
			throw new IllegalStateException(problem);
		}
	}

	private static String check() {
		Path dir = TestData.baseDir();
		if (!Files.isDirectory(dir)) {
			return "Test data not found at %s. Clone https://github.com/globbestael/dedupendnote-testdata there, or point to it with -D%s=<dir> or the environment variable %s, then run bin/sync-testdata.sh (see docs/testing-guide.md)."
				.formatted(dir, TestData.PROPERTY, TestData.ENV_VARIABLE);
		}
		if ("true".equalsIgnoreCase(System.getenv("CI")) || !Files.isRegularFile(PIN_FILE)) {
			return "";
		}
		try {
			String pin = Files.readString(PIN_FILE, StandardCharsets.UTF_8).trim();
			if (pin.isEmpty() || !Files.exists(dir.resolve(".git"))) {
				logger.warning(() -> "Cannot check the version of the test data at %s: no pin or not a git working copy."
					.formatted(dir));
				return "";
			}
			GitResult ancestor = git(dir, "merge-base", "--is-ancestor", pin, "HEAD");
			if (ancestor.exitCode() == 0) {
				String head = git(dir, "rev-parse", "HEAD").output();
				if (!head.equals(pin)) {
					logger.warning(() -> "Test data at %s is ahead of the pin in %s (HEAD %s, pin %s)."
						.formatted(dir, PIN_FILE, head, pin));
				}
				return "";
			}
			return "Test data at %s is behind the version pinned in %s (%s). Run bin/sync-testdata.sh."
				.formatted(dir, PIN_FILE, pin);
		} catch (IOException e) {
			logger.warning(() -> "Cannot check the version of the test data (is git on the PATH?): " + e);
			return "";
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "Interrupted while checking the version of the test data.";
		}
	}

	private static GitResult git(Path dir, String... args) throws IOException, InterruptedException {
		String[] command = new String[args.length + 3];
		command[0] = "git";
		command[1] = "-C";
		command[2] = dir.toString();
		System.arraycopy(args, 0, command, 3, args.length);
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			throw new IOException("git timed out");
		}
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
		return new GitResult(process.exitValue(), output);
	}
}
