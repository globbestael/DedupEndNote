package edu.dedupendnote.unit.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import edu.dedupendnote.services.SessionDirectoryReaper;

/** Deterministic reaping logic, driven by a fixed Clock. No Spring context. */
class SessionDirectoryReaperTest {

	private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");
	private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@TempDir
	Path uploadDir;

	private Path sessionDir(String name, Duration age) throws IOException {
		Path dir = Files.createDirectory(uploadDir.resolve(name));
		Files.setLastModifiedTime(dir, FileTime.from(NOW.minus(age)));
		return dir;
	}

	@Test
	void reap_deletesDirsOlderThanMaxAge_sparesYoungerOnes() throws IOException {
		Path oldDir = sessionDir("11111111-1111-4111-8111-old", Duration.ofHours(30));
		Path youngDir = sessionDir("22222222-2222-4222-8222-young", Duration.ofHours(1));

		new SessionDirectoryReaper(uploadDir, Duration.ofHours(24), FIXED_CLOCK).reap();

		assertThat(oldDir).doesNotExist();
		assertThat(youngDir).exists();
	}

	@Test
	void reap_deletesStaleDirRecursively_includingContents() throws IOException {
		Path oldDir = Files.createDirectory(uploadDir.resolve("stale"));
		Files.writeString(oldDir.resolve("t1.txt"), "data");
		Files.writeString(oldDir.resolve("t1_deduplicated.txt"), "result");
		// Set the timestamp AFTER writing the files, which would otherwise bump it.
		Files.setLastModifiedTime(oldDir, FileTime.from(NOW.minus(Duration.ofHours(48))));

		new SessionDirectoryReaper(uploadDir, Duration.ofHours(24), FIXED_CLOCK).reap();

		assertThat(oldDir).doesNotExist();
	}

	@Test
	void reap_ignoresLooseFilesAndKeepsBoundaryYoungDir() throws IOException {
		Files.writeString(uploadDir.resolve("loose.txt"), "not a session dir");
		Path justYoung = sessionDir("33333333-3333-4333-8333-boundary", Duration.ofHours(23));

		new SessionDirectoryReaper(uploadDir, Duration.ofHours(24), FIXED_CLOCK).reap();

		assertThat(uploadDir.resolve("loose.txt")).exists(); // only directories are reaped
		assertThat(justYoung).exists();
	}

	@Test
	void reap_missingUploadDir_doesNotThrow() {
		SessionDirectoryReaper reaper = new SessionDirectoryReaper(uploadDir.resolve("does-not-exist"),
				Duration.ofHours(24), FIXED_CLOCK);

		assertThatCode(reaper::reap).doesNotThrowAnyException();
	}
}
