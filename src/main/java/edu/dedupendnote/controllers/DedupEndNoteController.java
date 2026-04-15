package edu.dedupendnote.controllers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.multipart.MultipartFile;

import edu.dedupendnote.domain.StompMessage;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.UtilitiesService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class DedupEndNoteController {

	@SuppressWarnings("NullAway.Init") // necessary because of lazy / late initialization?
	@Value("${upload-dir}")
	private String uploadDir;

	private final DeduplicationService deduplicationService;
	private final SimpMessagingTemplate simpMessagingTemplate;

	public DedupEndNoteController(DeduplicationService deduplicationService,
			SimpMessagingTemplate simpMessagingTemplate) {
		this.deduplicationService = deduplicationService;
		this.simpMessagingTemplate = simpMessagingTemplate;
	}

	// @formatter:off
	/*
	 * Communication between client / browser uses different techniques
	 *
	 * - in the onLoad of the web page a web socket connect and subscribe is called.
	 *   Reloading the page (e.g. with the Restart button) start a new connection and subscription. A running deduplication is NOT stopped!
	 *   FIXME: is it possible to stop these running deduplications? A server could be flooded with interrupted calls?
	 *   See a.o. https://stackoverflow.com/questions/54946096/spring-boot-websocket-how-do-i-know-when-a-client-has-unsubscribed/54948213
	 *   Is StructuredTaskScope (java 21) a solution?
	 * - files are uploaded with AJAX (uploadFile)
	 * - deduplication is started with AJAX (startOneFile|StartTwoFiles) which calls the DeduplicationService.
	 * - the DeduplicationService uses Web Sockets to report progress to the browser.
	 *
	 * Web Socket: Messages should be sent to the individual user.
	 * There is only server --> client communication (no @MessageMapping functions).
	 * - the client generates a UUID (wssessionId) via crypto.randomUUID() and subscribes to "/topic/messages-[wssessionId]"
	 *   TODO: crypto.randomUUID() only runs on localhost and secure connections.
	 * 	 As a temporary fix, a local JS function is called to generate a UUID.
	 * - the wssessionId is passed as a request parameter for startOneFile / startTwoFiles
	 * - the controller creates a Consumer<String> that routes messages to "/topic/messages-[wssessionId]" via SimpMessagingTemplate
	 * - the Consumer is passed to DeduplicationService, which calls it for each progress update
	 */
	// @formatter:on

	@PostMapping(value = "/getResultFile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public void getResultFile(@RequestParam("fileNameResultFile") String fileName,
			@RequestParam("markModeResultFile") boolean markMode, HttpServletResponse response) {
		String outputFileName = UtilitiesService.createOutputFileName(fileName, markMode);

		Path path = Path.of(uploadDir, outputFileName);
		response.setContentType("text/plain");
		response.addHeader("Content-Disposition", "attachment; filename=\"" + outputFileName + "\"");
		try {
			Files.copy(path, response.getOutputStream());
			response.getOutputStream().flush();
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	@GetMapping("/")
	public String home(HttpSession session) {
		return "index";
	}

	@GetMapping("/changelog")
	public String changelog() {
		return "changelog";
	}

	@GetMapping("/details")
	public String details() {
		return "details";
	}

	/*
	 * The use of RequestContextHolder within the executor\Service.submit is necessary to prevent the error
	 * 		Scope 'request' is not active for the current thread; consider defining a scoped proxy 
	 * 
	 * Explanation and solution in 
	 * 		https://blog.stackademic.com/how-to-overcome-spring-request-scope-issue-for-child-threads-ad3e2a30bf42
	 */
	@PostMapping(value = "/startOneFile", produces = "application/json")
	public ResponseEntity<String> startOneFile(@RequestParam("fileName_1") String inputFileName,
			@RequestParam(required = false, defaultValue = "false") boolean markMode, @RequestParam String wssessionId)
			throws Exception {
		String outputFileName = UtilitiesService.createOutputFileName(inputFileName, markMode);
		String logPrefix = "1F" + (Boolean.TRUE.equals(markMode) ? "M" : "D");

		Consumer<String> progressReporter = message -> simpMessagingTemplate
				.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
			Future<String> future = executor.submit(() -> {
				RequestContextHolder.setRequestAttributes(requestAttributes);
				return deduplicationService.deduplicateOneFile(uploadDir + File.separator + inputFileName,
						uploadDir + File.separator + outputFileName, markMode, progressReporter);
			});
			log.info("Writing to result: {}: {}", logPrefix, future.get());
			return ResponseEntity.ok("{ \"result\": " + future.get());
		}
	}

	@PostMapping(value = "/startTwoFiles", produces = "application/json")
	public ResponseEntity<String> startTwoFiles(@RequestParam String oldFile, @RequestParam String newFile,
			@RequestParam(required = false, defaultValue = "false") boolean markMode, @RequestParam String wssessionId)
			throws InterruptedException, ExecutionException {

		String logPrefix = "2F" + (Boolean.TRUE.equals(markMode) ? "M" : "D");

		Consumer<String> progressReporter = message -> simpMessagingTemplate
				.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
			Future<String> future = executor.submit(() -> {
				RequestContextHolder.setRequestAttributes(requestAttributes);
				return deduplicationService.deduplicateTwoFiles(uploadDir + File.separator + newFile,
						uploadDir + File.separator + oldFile,
						uploadDir + File.separator + UtilitiesService.createOutputFileName(newFile, markMode), markMode,
						progressReporter);
			});
			log.info("Writing to result: {}: {}", logPrefix, future.get());
			return ResponseEntity.ok("{ \"result\": " + future.get());
		}
	}

	@GetMapping("/test_results_details")
	public String testResultsDetails(HttpSession session) {
		return "test_results_details";
	}

	@GetMapping("/twofiles")
	public String twofiles(final HttpSession session) {
		return "twofiles";
	}

	@PostMapping(value = "/uploadFile", produces = "application/json")
	public ResponseEntity<String> uploadFile(@RequestParam MultipartFile file) {
		if (file.isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
					"{ \"result\": \"Failed to upload " + file.getOriginalFilename() + " because it was empty" + "\"}");
		}
		try {
			Path path = Path.of(uploadDir, file.getOriginalFilename());
			if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
				Files.delete(path);
			}
			try (InputStream inputStream = file.getInputStream()) {
				Files.copy(inputStream, path);
				return ResponseEntity
						.ok("{ \"result\": \"You successfully uploaded " + file.getOriginalFilename() + "\"}");
			}
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
					"{ \"result\": \"Failed to upload " + file.getOriginalFilename() + " => " + e.getClass() + "\"}");
		} catch (RuntimeException e) {
			log.error("RuntimeException met cause: {}", e.getCause());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body("{ \"result\": \"RuntimeException with cause " + e.getCause() + "\"}");
		}
	}
}