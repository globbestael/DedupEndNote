package edu.dedupendnote;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.file.PathUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;

import edu.dedupendnote.services.comparison.DefaultAuthorsComparisonService;
import edu.dedupendnote.services.comparison.DefaultJournalComparisonService;
import edu.dedupendnote.services.comparison.DefaultPagesComparisonService;
import edu.dedupendnote.services.comparison.DefaultTitleComparisonService;
import edu.dedupendnote.services.comparison.FieldComparators;

import lombok.extern.slf4j.Slf4j;

@Controller
@SpringBootApplication
@Slf4j
public class DedupEndNoteApplication {

	/*
	 * The initialization of this variable via the @Value annototion by Spring is LATER than the statis NullAway check.
	 * Must be solved by the @SuppresWarnings annotation?
	 * 
	 * We could hardcode the vallue from applications.properties here, but this value is also initialized in DedupEndNoteController.
	 */
	@SuppressWarnings("NullAway.Init") // necessary because of lazy / late initialization?
	@Value("${upload-dir}")
	private String uploadDir; // = "upload-dir";

	public static void main(String[] args) {
		SpringApplication.run(DedupEndNoteApplication.class, args);
		log.info("DedupEndNote: the web server is reachable at http://localhost:9777");
	}

	@Bean
	public FieldComparators fieldComparators() {
		return new FieldComparators(
				new DefaultAuthorsComparisonService(),
				new DefaultTitleComparisonService(),
				new DefaultJournalComparisonService(),
				new DefaultPagesComparisonService());
	}

	@Bean
	CommandLineRunner init() {
		return args -> {
			Path uploadPath = Path.of(uploadDir);
			if (Files.exists(uploadPath)) {
				PathUtils.deleteDirectory(uploadPath);
			}
			Files.createDirectory(uploadPath);
		};
	}
}