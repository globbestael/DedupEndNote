package edu.dedupendnote;

import java.nio.file.Path;

/**
 * Resolves the directory with the test input files (the {@code dedupendnote-testdata} repository).
 *
 * <p>Order of precedence:
 * <ol>
 * <li>system property {@value #PROPERTY}</li>
 * <li>environment variable {@value #ENV_VARIABLE} (used by CI)</li>
 * <li>{@code ~/dedupendnote_input_files}</li>
 * </ol>
 *
 * <p>This is plain Java instead of an {@code application.properties} key because unit tests don't start a Spring
 * context. {@link TestDataExtension} checks that the directory exists and has the pinned version.
 */
public final class TestData {

	public static final String PROPERTY = "dedup.testdata.dir";
	public static final String ENV_VARIABLE = "DEDUP_TESTDATA_DIR";

	private TestData() {
	}

	public static Path baseDir() {
		String value = System.getProperty(PROPERTY);
		if (value == null || value.isBlank()) {
			value = System.getenv(ENV_VARIABLE);
		}
		if (value == null || value.isBlank()) {
			return Path.of(System.getProperty("user.home", "")).resolve("dedupendnote_input_files");
		}
		return Path.of(value);
	}
}
