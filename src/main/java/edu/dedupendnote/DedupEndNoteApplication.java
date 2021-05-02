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

	@GetMapping("/")
	public String home(HttpSession session) {
		return "index";
	}

	// FIXME: use resources
	@GetMapping("/developers")
	public String developers(HttpSession session) {
		return "developers";
	}

	@GetMapping("/faq")
	public String faq(HttpSession session) {
		return "faq";
	}

	@GetMapping("/justification")
	public String justification() {
		return "justification";
	}

	@GetMapping("/test_results_details")
	public String test_results_details(HttpSession session) {
		return "test_results_details";
	}

	@GetMapping("/twofiles")
	public String twofiles(HttpSession session) {
		return "twofiles";
	}

	@GetMapping("/recordstatus.json")
	@ResponseBody
	public String recordStatus(HttpSession session) {
		String result = (String) session.getAttribute("result");
		log.info("Session: {}", result);
		return "{ \"result\": \"" + result + "\" }";
	}

	@PostMapping(value = "/getResultFile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public void getResultFile(@RequestParam("fileName") String fileName, @RequestParam("markModeResultFile") boolean markMode, HttpServletResponse response) {
		String outputFileName = createOutputFileName(fileName, markMode);

		Path path = Paths.get(ROOT, outputFileName);
		response.setContentType("text/plain");
		response.addHeader("Content-Disposition", "attachment; filename=\"" + outputFileName + "\"");
		try {
			Files.copy(path, response.getOutputStream());
			response.getOutputStream().flush();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public static String createOutputFileName(String fileName, boolean markMode) {
		String extension = StringUtils.getFilenameExtension(fileName);
		String outputFileName = fileName.replaceAll("." + extension + "$", (markMode ? "_mark." : "_deduplicated.") + extension);
		return outputFileName;
	}

	@PostMapping(value = "/upload", produces = "application/json")
	public ResponseEntity<String> handleFormUpload(@RequestParam("file") MultipartFile file, @RequestParam(name = "markMode", required = false) boolean markMode, HttpSession session) {
		log.info("markMode is: {}", markMode);
		if (!file.isEmpty()) {
			try {
				Path path = Paths.get(ROOT, file.getOriginalFilename());
				if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
					Files.delete(path);
				}
				Files.copy(file.getInputStream(), path);
				String inputFileName = file.getOriginalFilename();
				String outputFileName = createOutputFileName(inputFileName, markMode);
				startDeduplication(inputFileName, outputFileName, markMode, session);
				return ResponseEntity.ok("{ \"result\": \"You successfully uploaded " + file.getOriginalFilename() + "\"}");
			} catch (IOException | RuntimeException e) {
				e.printStackTrace();
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
						.body("{ \"result\": \"Failed to upload " + file.getOriginalFilename() + " => " + e.getClass() + "\"}");
			}
		} else {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("{ \"result\": \"Failed to upload " + file.getOriginalFilename() + " because it was empty" + "\"}");
		}
	}

	/*
	 * Deduplicating 2 files in 2 steps:
	 *  - "/uploadTwoFiles": upload 2 files (MultiPartFile's)
	 *  - "/startTwoFiles": read the 2 fileNames and the number indicating which file contains the old records
	 */
	@PostMapping(value = "/uploadTwoFiles", produces = "application/json")
	public ResponseEntity<String> handleMultipleFormUpload(@RequestParam("files") MultipartFile[] files, HttpSession session) {
		log.info("file 0: {}", files[0].getOriginalFilename());
		log.info("file 1: {}", files[1].getOriginalFilename());

		if (!files[0].isEmpty() && !files[1].isEmpty()) {
			for (int i = 0; i < files.length; i++) {
				try {
					Path path = Paths.get(ROOT, files[i].getOriginalFilename());
					if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
						Files.delete(path);
					Files.copy(files[i].getInputStream(), path);
				} catch (IOException | RuntimeException e) {
					e.printStackTrace();
					return ResponseEntity.status(HttpStatus.BAD_REQUEST)
							.body("{ \"result\": \"Failed to upload " + files[i].getOriginalFilename() + " => " + e.getClass() + "\"}");
				}
			}
		} else {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("{ \"result\": \"Failed to upload " + files[0].getOriginalFilename() + " and " + files[1].getOriginalFilename()
							+ " because at least one of them was empty" + "\"}");
		}
		// startDeduplication(files[0].getOriginalFilename(), files[1].getOriginalFilename(),
		// createOutputFileName(files[0].getOriginalFilename()), session);
		return ResponseEntity.ok("{ \"result\": \"You successfully uploaded " + files[0].getOriginalFilename() + " and "
				+ files[1].getOriginalFilename() + "\"}");
	}

	@PostMapping(value = "/startTwoFiles", produces = "application/json")
	public ResponseEntity<String> startTwoFiles(@RequestParam("fileName_1_hidden") String fileName_1,
			@RequestParam("fileName_2_hidden") String fileName_2,
			@RequestParam("fileNumber") Integer fileNumber, @RequestParam(name = "markMode", required = false) boolean markMode, HttpSession session) {
		log.info("file 0: {}", fileName_1);
		log.info("file 1: {}", fileName_2);
		log.info("file number: {}", fileNumber);
		log.info("markMode: {}", markMode);
		String oldFile = (fileNumber == 0 ? fileName_1 : fileName_2);
		String newFile = (fileNumber == 0 ? fileName_2 : fileName_1);
		startDeduplication(newFile, oldFile, createOutputFileName(newFile, markMode), markMode, session);
		return ResponseEntity.ok("{ \"result\": \"Deduplication of " + oldFile + " and " + newFile + " has started\"}");
	}

	private String startDeduplication(String inputFileName, String outputFileName, boolean markMode, HttpSession session) {
		// Create DeferredResult with timeout 5s
		DeferredResult<String> result = new DeferredResult<>(5000L);

		// Let's call the backend
		ListenableFuture<String> future = deduplicationService.deduplicateOneFileAsync(ROOT + File.separator + inputFileName,
				ROOT + File.separator + outputFileName, markMode, session);

		future.addCallback(
				new ListenableFutureCallback<String>() {
					@Override
					public void onSuccess(String response) {
						// Will be called in thread
						log("Success");
						log.info("Writing to result: {}", response);
						result.setResult(response);
					}

					@Override
					public void onFailure(Throwable t) {
						result.setErrorResult(t.getMessage());
					}
				});
		// Return the thread to servlet container,
		// the response will be processed by another thread.
		return "index";
	}

	private String startDeduplication(String newInputFileName, String oldInputFileName, String outputFileName, boolean markMode, HttpSession session) {
		// Create DeferredResult with timeout 5s
		DeferredResult<String> result = new DeferredResult<>(5000L);

		// Let's call the backend
		ListenableFuture<String> future =
				deduplicationService.deduplicateTwoFilesAsync(ROOT + File.separator + newInputFileName, ROOT + File.separator + oldInputFileName,
						ROOT + File.separator + outputFileName, markMode, session);

		future.addCallback(
				new ListenableFutureCallback<String>() {
					@Override
					public void onSuccess(String response) {
						// Will be called in thread
						log("Success");
						log.info("Writing to result: {}", response);
						result.setResult(response);
					}

					@Override
					public void onFailure(Throwable t) {
						result.setErrorResult(t.getMessage());
					}
				});
		// Return the thread to servlet container,
		// the response will be processed by another thread.
		return "index";
	}

	private static void log(Object message) {
		System.out.println(String.format("%s %s ", Thread.currentThread().getName(), message));
	}
}
