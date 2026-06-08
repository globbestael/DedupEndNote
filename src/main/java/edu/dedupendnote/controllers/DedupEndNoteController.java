package edu.dedupendnote.controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.multipart.MultipartFile;

import edu.dedupendnote.domain.DeduplicationMode;
import edu.dedupendnote.domain.StompMessage;
import edu.dedupendnote.services.DeduplicationService;
import edu.dedupendnote.services.UtilitiesService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class DedupEndNoteController {

	@SuppressWarnings("NullAway.Init") // necessary because of lazy / late initialization?
	@Value("${upload-dir}")
	private String uploadDir;

	@Value("${dedup.max-concurrent-runs:4}")
	private int maxConcurrentRuns;

	@Value("${dedup.timeout-minutes:20}")
	private int timeoutMinutes;

	@SuppressWarnings("NullAway.Init")
	private Semaphore concurrentRunsSemaphore;

	@PostConstruct
	void initSemaphore() {
		concurrentRunsSemaphore = new Semaphore(maxConcurrentRuns);
	}

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
	 * - the server generates a UUID (wssessionId) via UUID.randomUUID() in home() / twofiles() and injects it
	 *   into the Thymeleaf model; the template embeds it into all form hidden fields at render time.
	 * - the wssessionId is passed as a request parameter for all upload/start/result endpoints
	 * - the controller creates a Consumer<String> that routes messages to "/topic/messages-[wssessionId]" via SimpMessagingTemplate
	 * - the Consumer is passed to DeduplicationService, which calls it for each progress update
	 */
	// @formatter:on

	@PostMapping(value = "/getResultFile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public void getResultFile(@RequestParam("fileNameResultFile") String fileName,
			@RequestParam("markModeResultFile") boolean markMode, @RequestParam UUID wssessionId,
			HttpServletResponse response) {
		DeduplicationMode mode = DeduplicationMode.from(markMode);
		try {
			Path inputPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, fileName);
			Path path = UtilitiesService.createPath(inputPath, mode.filenameSuffix(), "txt");
			String safeFileName = path.getFileName().toString().replaceAll("[\"\\r\\n]", "_");
			response.setContentType("text/plain");
			response.addHeader("Content-Disposition", "attachment; filename=\"" + safeFileName + "\"");
			Files.copy(path, response.getOutputStream());
			response.getOutputStream().flush();
		} catch (IllegalArgumentException e) {
			log.warn("Path traversal attempt in getResultFile: {}", e.getMessage());
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
		} catch (IOException ex) {
			log.error("Error sending result file", ex);
			response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}
	}

	@GetMapping("/")
	public String home(Model model) {
		model.addAttribute("wssessionId", UUID.randomUUID());
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
			@RequestParam(required = false, defaultValue = "false") boolean markMode, @RequestParam UUID wssessionId)
			throws InterruptedException, ExecutionException {
		DeduplicationMode mode = DeduplicationMode.from(markMode);
		try {
			Path inputPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, inputFileName);
			Consumer<String> progressReporter = message -> simpMessagingTemplate
					.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
			return runDedup("1F" + (mode == DeduplicationMode.MARK ? "M" : "D"),
					UtilitiesService.createPath(inputPath, mode.filenameSuffix(), "txt"),
					() -> deduplicationService.deduplicateOneFile(inputPath, mode, progressReporter),
					progressReporter);
		} catch (IllegalArgumentException e) {
			log.warn("Path traversal attempt in startOneFile: {}", e.getMessage());
			return ResponseEntity.badRequest().body("{\"result\": \"Invalid filename\"}");
		}
	}

	@PostMapping(value = "/startTwoFiles", produces = "application/json")
	public ResponseEntity<String> startTwoFiles(@RequestParam String oldFile, @RequestParam String newFile,
			@RequestParam(required = false, defaultValue = "false") boolean markMode, @RequestParam UUID wssessionId)
			throws InterruptedException, ExecutionException {
		DeduplicationMode mode = DeduplicationMode.from(markMode);
		try {
			Path newInputPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, newFile);
			Path oldInputPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, oldFile);
			Consumer<String> progressReporter = message -> simpMessagingTemplate
					.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
			return runDedup("2F" + (mode == DeduplicationMode.MARK ? "M" : "D"),
					UtilitiesService.createPath(newInputPath, mode.filenameSuffix(), "txt"),
					() -> deduplicationService.deduplicateTwoFiles(newInputPath, oldInputPath, mode, progressReporter),
					progressReporter);
		} catch (IllegalArgumentException e) {
			log.warn("Path traversal attempt in startTwoFiles: {}", e.getMessage());
			return ResponseEntity.badRequest().body("{\"result\": \"Invalid filename\"}");
		}
	}

	private ResponseEntity<String> runDedup(String logPrefix, Path outputPath, Callable<String> dedupTask,
			Consumer<String> progressReporter) throws InterruptedException, ExecutionException {
		if (!concurrentRunsSemaphore.tryAcquire()) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
					.body("{\"result\": \"ERROR: Server is busy. Please try again in a moment.\"}");
		}
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
			Future<String> future = executor.submit(() -> {
				RequestContextHolder.setRequestAttributes(requestAttributes);
				return dedupTask.call();
			});
			try {
				String result = future.get(timeoutMinutes, TimeUnit.MINUTES);
				log.info("Writing to result: {}: {}", logPrefix, result);
				return ResponseEntity.ok("{ \"result\": " + result);
			} catch (TimeoutException e) {
				future.cancel(true);
				try {
					Files.deleteIfExists(outputPath);
				} catch (IOException ignored) {}
				String msg = "ERROR: Deduplication timed out after " + timeoutMinutes + " minutes.";
				progressReporter.accept(msg);
				return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"result\": \"" + msg + "\"}");
			}
		} finally {
			concurrentRunsSemaphore.release();
		}
	}

	@GetMapping("/test_results_details")
	public String testResultsDetails() {
		return "test_results_details";
	}

	@GetMapping("/twofiles")
	public String twofiles(Model model) {
		model.addAttribute("wssessionId", UUID.randomUUID());
		return "twofiles";
	}

	@PostMapping(value = "/uploadFile", produces = "application/json")
	public ResponseEntity<String> uploadFile(@RequestParam MultipartFile file, @RequestParam UUID wssessionId) {
		if (file.isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"result\": \"Upload failed: file is empty\"}");
		}
		try {
			Path targetPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, file.getOriginalFilename());
			try {
				Files.createDirectories(UtilitiesService.getSessionDir(uploadDir, wssessionId));
			} catch (IOException e) {
				log.error("Error creating session directory", e);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"result\": \"Upload failed\"}");
			}
			try {
				Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
				return ResponseEntity.ok("{\"result\": \"File uploaded successfully\"}");
			} catch (IOException e) {
				log.error("Error uploading file", e);
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("{\"result\": \"Upload failed\"}");
			}
		} catch (IllegalArgumentException e) {
			log.warn("Path traversal attempt in uploadFile: {}", e.getMessage());
			return ResponseEntity.badRequest().body("{\"result\": \"Invalid filename\"}");
		} catch (RuntimeException e) {
			log.error("Unexpected error uploading file", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"result\": \"Upload failed\"}");
		}
	}
}