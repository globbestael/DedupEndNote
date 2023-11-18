package edu.dedupendnote;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileSystemUtils;

import lombok.extern.slf4j.Slf4j;

@Controller
@SpringBootApplication
// @EnableAsync
@Slf4j
public class DedupEndNoteApplication {

	public static void main(String[] args) {
		SpringApplication.run(DedupEndNoteApplication.class, args);
		log.info("DedupEndNote: the web server is reachable at http://localhost:9777");
	}

	@Value("${upload-dir}")
	private String uploadDir;

	@Bean
	CommandLineRunner init() {
		return args -> {
			FileSystemUtils.deleteRecursively(new File(uploadDir));
			Files.createDirectory(Paths.get(uploadDir));
		};
	}
}
