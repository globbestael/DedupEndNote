package edu.dedupendnote.controllers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

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
import edu.dedupendnote.services.BoundedDedupRunner;
import edu.dedupendnote.services.BoundedDedupRunner.RunOutcome;
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

	// Display only: BoundedDedupRunner enforces the timeout; this composes the user-facing message.
	@Value("${dedup.timeout-minutes:20}")
	private int timeoutMinutes;

	private final DeduplicationService deduplicationService;
	private final SimpMessagingTemplate simpMessagingTemplate;
	private final BoundedDedupRunner dedupRunner;

	public DedupEndNoteController(DeduplicationService deduplicationService,
			SimpMessagingTemplate simpMessagingTemplate, BoundedDedupRunner dedupRunner) {
		this.deduplicationService = deduplicationService;
		this.simpMessagingTemplate = simpMessagingTemplate;
		this.dedupRunner = dedupRunner;
	}

	// @formatter:off
	/*
	 * Communication between client / browser uses different techniques
	 *
	 * - in the onLoad of the web page a web socket connect and subscribe is called.
	 *   Reloading the page (e.g. with the Restart button) starts a new connection and subscription.
	 *   A running deduplication can be stopped explicitly via POST /cancelDedup.
	 * - files are uploaded with AJAX (uploadFile)
	 * - deduplication is started with AJAX (startOneFile|startTwoFiles) which calls the DeduplicationService.
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
			@RequestParam("deduplicationModeResultFile") DeduplicationMode deduplicationMode,
			@RequestParam UUID wssessionId, HttpServletResponse response) {
		try {
			Path inputPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, fileName);
			Path path = UtilitiesService.createPath(inputPath, deduplicationMode.filenameSuffix(), "txt");
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
	public ResponseEntity<ApiResponse> startOneFile(@RequestParam("fileName_1") String inputFileName,
			@RequestParam(defaultValue = "REMOVE") DeduplicationMode deduplicationMode, @RequestParam UUID wssessionId) {
		try {
			Path inputPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, inputFileName);
			Consumer<String> progressReporter = message -> {
				if (!Thread.currentThread().isInterrupted()) {
					simpMessagingTemplate.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
				}
			};
			return runDedup("1F" + deduplicationMode.logCode(),
					UtilitiesService.createPath(inputPath, deduplicationMode.filenameSuffix(), "txt"),
					() -> deduplicationService.deduplicateOneFile(inputPath, deduplicationMode, progressReporter),
					progressReporter, wssessionId);
		} catch (IllegalArgumentException e) {
			log.warn("Path traversal attempt in startOneFile: {}", e.getMessage());
			return ResponseEntity.badRequest().body(new ApiResponse("Invalid filename"));
		}
	}

	@PostMapping(value = "/startTwoFiles", produces = "application/json")
	public ResponseEntity<ApiResponse> startTwoFiles(@RequestParam String oldFile, @RequestParam String newFile,
			@RequestParam(defaultValue = "REMOVE") DeduplicationMode deduplicationMode, @RequestParam UUID wssessionId) {
		try {
			Path newInputPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, newFile);
			Path oldInputPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, oldFile);
			Consumer<String> progressReporter = message -> {
				if (!Thread.currentThread().isInterrupted()) {
					simpMessagingTemplate.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
				}
			};
			return runDedup("2F" + deduplicationMode.logCode(),
					UtilitiesService.createPath(newInputPath, deduplicationMode.filenameSuffix(), "txt"),
					() -> deduplicationService.deduplicateTwoFiles(newInputPath, oldInputPath, deduplicationMode,
							progressReporter),
					progressReporter, wssessionId);
		} catch (IllegalArgumentException e) {
			log.warn("Path traversal attempt in startTwoFiles: {}", e.getMessage());
			return ResponseEntity.badRequest().body(new ApiResponse("Invalid filename"));
		}
	}

	private ResponseEntity<ApiResponse> runDedup(String logPrefix, Path outputPath, Callable<String> dedupTask,
			Consumer<String> progressReporter, UUID wssessionId) {
		RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
		RunOutcome outcome = dedupRunner.runWithLimit(wssessionId, () -> {
			// The dedup runs on a worker thread; re-attach the request scope so @RequestScope beans resolve.
			RequestContextHolder.setRequestAttributes(requestAttributes);
			return dedupTask.call();
		});
		return switch (outcome.status()) {
			case BUSY -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
					.body(new ApiResponse("ERROR: Server is busy. Please try again in a moment."));
			case COMPLETED -> {
				String result = Objects.requireNonNull(outcome.result());
				log.info("Writing to result: {}: {}", logPrefix, result);
				yield ResponseEntity.ok(new ApiResponse(result));
			}
			case TIMED_OUT -> {
				deleteQuietly(outputPath);
				String msg = "ERROR: Deduplication timed out after " + timeoutMinutes + " minutes.";
				progressReporter.accept(msg);
				yield ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiResponse(msg));
			}
			case CANCELLED -> {
				deleteQuietly(outputPath);
				String msg = Objects.requireNonNull(outcome.errorMessage());
				progressReporter.accept(msg);
				yield ResponseEntity.ok(new ApiResponse(msg));
			}
			case FAILED -> {
				String msg = Objects.requireNonNull(outcome.errorMessage());
				progressReporter.accept(msg);
				yield ResponseEntity.ok(new ApiResponse(msg));
			}
		};
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
		}
	}

	@PostMapping(value = "/cancelDedup", produces = "application/json")
	public ResponseEntity<ApiResponse> cancelDedup(@RequestParam UUID wssessionId) {
		if (!dedupRunner.cancel(wssessionId)) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND)
					.body(new ApiResponse("No running deduplication found."));
		}
		return ResponseEntity.ok(new ApiResponse("Cancellation requested."));
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
	public ResponseEntity<ApiResponse> uploadFile(@RequestParam MultipartFile file, @RequestParam UUID wssessionId) {
		if (file.isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse("Upload failed: file is empty"));
		}
		try {
			Path targetPath = UtilitiesService.resolveInSessionDir(uploadDir, wssessionId, file.getOriginalFilename());
			try {
				Files.createDirectories(UtilitiesService.getSessionDir(uploadDir, wssessionId));
			} catch (IOException e) {
				log.error("Error creating session directory", e);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("Upload failed"));
			}
			try {
				Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
				return ResponseEntity.ok(new ApiResponse("File uploaded successfully"));
			} catch (IOException e) {
				log.error("Error uploading file", e);
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiResponse("Upload failed"));
			}
		} catch (IllegalArgumentException e) {
			log.warn("Path traversal attempt in uploadFile: {}", e.getMessage());
			return ResponseEntity.badRequest().body(new ApiResponse("Invalid filename"));
		} catch (RuntimeException e) {
			log.error("Unexpected error uploading file", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiResponse("Upload failed"));
		}
	}
}