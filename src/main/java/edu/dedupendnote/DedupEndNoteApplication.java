package edu.dedupendnote;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Controller;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

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
