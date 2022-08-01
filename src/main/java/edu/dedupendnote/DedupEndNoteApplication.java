package edu.dedupendnote;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileSystemUtils;

import edu.dedupendnote.services.DeduplicationService;
import lombok.extern.slf4j.Slf4j;

@Controller
@SpringBootApplication
@EnableAsync
@Slf4j
public class DedupEndNoteApplication {

	@Autowired 
	public DeduplicationService deduplicationService;
	
	public static final String ROOT = "upload-dir";

	/*
	 * DeferredResult a.o. based on - https://blog.krecan.net/2014/06/10/what-are-listenablefutures-good-for/ -
	 * http://blog.inflinx.com/2012/09/09/spring-async-and-future-report-generation-example/
	 *
	 * Exchange data via sessionAttribute? - http://blog.inflinx.com/2012/09/09/spring-async-and-future-report-generation-example/
	 */

	@Bean
	CommandLineRunner init() {
		return (args) -> {
			FileSystemUtils.deleteRecursively(new File(ROOT));
			Files.createDirectory(Paths.get(ROOT));
		};
	}

	public static void main(String[] args) {
		SpringApplication.run(DedupEndNoteApplication.class, args);
//		SpringApplication app = new SpringApplication(DedupEndNoteApplication.class);
//        app.setBanner((environment, sourceClass, out) -> {
//            out.println("DedupEndNote: the web server is reachable at http://localhost:9777");
//        });
//        app.run(args);
        log.info("DedupEndNote: the web server is reachable at http://localhost:9777");
	}
}
