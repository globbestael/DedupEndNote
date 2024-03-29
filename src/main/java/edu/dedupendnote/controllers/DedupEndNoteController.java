package edu.dedupendnote.controllers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.multipart.MultipartFile;

import edu.dedupendnote.services.DeduplicationService;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class DedupEndNoteController {

	// @formatter:off
	/*
	 * Communication between client / browser uses different techniques
	 *
	 * - in the onLoad of the web page a web socket connect and subscribe is called.
	 *   Reloading the page (e.g. with the Restart button) start a new connection and subscription. A running deduplication is NOT stopped!
	 *   FIXME: is it possible to stop these running deduplications? A server could be flooded with interrupted calls?
	 *   See a.o. https://stackoverflow.com/questions/54946096/spring-boot-websocket-how-do-i-know-when-a-client-has-unsubscribed/54948213
	 * - files are uploaded with AJAX (uploadFile)
	 * - deduplication is started with AJAX (startOneFile|StartTwoFiles) which calls the DeduplicationService asynchronously with DeferredResult and ListenableFuture.
	 *   The return value of the function (the DeferredResult) is not used
	 * - the DeduplicationService uses Web Sockets to report progress to the browser.
	 *
	 * DeferredResult etc based on - https://blog.krecan.net/2014/06/10/what-are-listenablefutures-good-for/ -
	 * http://blog.inflinx.com/2012/09/09/spring-async-and-future-report-generation-example/
	 *
	 * Web Socket: Messages should be sent to the individual user.
	 * Normally a user would send a message to the server. The @MessageMapping function could retrieve the Web Socket SessionId with a @HeaderId argument of the function.
	 * The called function could use @SendToUser, and the client could subscribe to "/user/...".
	 * Spring would translate a URL as "/user/target/messages" for user "john" to "/target/messsages-john"
	 * But:
	 * - there is only server --> client communication (no @MessageMapping functions)
	 * - messages are sent from within the service.
	 * Therefore:
	 * - the client extracts the Web Socket SessionId (wssessionId) and subscribes to "/target/messages-[wssessionId]"
	 * - the wssessionId is passed as an argument for startOneFile / startTwoFiles
	 * - startDeduplication passes the wssessionId along to the DeduplicationService
	 * - the DeduplicationService sends the messages to "/target/messages-[wssessionId]"
	 * See https://www.generacodice.com/en/articolo/2284280/spring-websockets-sendtouser-without-login
	 */
	// @formatter:on

	public static String createOutputFileName(String fileName, boolean markMode) {
		String extension = StringUtils.getFilenameExtension(fileName);
		String outputFileName = fileName.replaceAll("." + extension + "$",
				(markMode ? "_mark." : "_deduplicated.") + extension);
		return outputFileName;
	}

	private static void log(Object message) {
		System.out.println(String.format("%s %s ", Thread.currentThread().getName(), message));
	}

	@Autowired
	public DeduplicationService deduplicationService;

	@Value("upload-dir")
	private String uploadDir;

	@PostMapping(value = "/getResultFile", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public void getResultFile(@RequestParam("fileNameResultFile") String fileName,
			@RequestParam("markModeResultFile") boolean markMode, HttpServletResponse response) {
		String outputFileName = createOutputFileName(fileName, markMode);

		Path path = Paths.get(uploadDir, outputFileName);
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

	@GetMapping("/justification")
	public String justification() {
		return "justification";
	}

	private String startDeduplication(String inputFileName, String outputFileName, boolean markMode, String wssessionId)
			throws Exception {
		// FIXME: cancelling a running deduplication in this way, doesn't work yet
		// if (runningFutures.containsKey(wssessionId)) {
		// String s = "You have a deduplication running. It will be stopped ad replaced
		// with this new one";
		// simpMessagingTemplate.convertAndSend("/topic/messages-" + wssessionId, new
		// StompMessage(s));
		// Thread.sleep(5000); // simulated delay
		// boolean cancelled = runningFutures.get(wssessionId).cancel(true);
		// runningFutures.remove(wssessionId);
		// simpMessagingTemplate.convertAndSend("/topic/messages-" + wssessionId, new
		// StompMessage("Running deduplication was cancelled"));
		// }
		// simpMessagingTemplate.convertAndSend("/topic/messages-" + wssessionId, new
		// StompMessage("Starting the service at /topic/messages-[sessionId] for
		// sessionID: " + wssessionId));

		// Create DeferredResult with timeout 5s
		DeferredResult<String> result = new DeferredResult<>(5000L);
		String logPrefix = "1F" + (markMode ? "M" : "D");

		// Let's call the backend
		ListenableFuture<String> future = deduplicationService.deduplicateOneFileAsync(
				uploadDir + File.separator + inputFileName, uploadDir + File.separator + outputFileName, markMode,
				wssessionId);
		// runningFutures.put(wssessionId, future);

		future.addCallback(new ListenableFutureCallback<String>() {
			@Override
			public void onFailure(Throwable t) {
				// runningFutures.remove(wssessionId);
				result.setErrorResult(t.getMessage());
			}

			@Override
			public void onSuccess(String response) {
				// Will be called in thread
				log("Success");
				log.info("Writing to result: {}: {}", logPrefix, response);
				// runningFutures.remove(wssessionId);
				result.setResult(response);
			}
		});
		// Return the thread to servlet container,
		// the response will be processed by another thread.
		return "index";
	}

	private String startDeduplication(String newInputFileName, String oldInputFileName, String outputFileName,
			boolean markMode, String wssessionId) {

		// Create DeferredResult with timeout 5s
		DeferredResult<String> result = new DeferredResult<>(5000L);
		String logPrefix = "2F" + (markMode ? "M" : "D");

		// Let's call the backend
		ListenableFuture<String> future = deduplicationService.deduplicateTwoFilesAsync(
				uploadDir + File.separator + newInputFileName, uploadDir + File.separator + oldInputFileName,
				uploadDir + File.separator + outputFileName, markMode, wssessionId);
		// runningFutures.put(wssessionId, future);

		future.addCallback(new ListenableFutureCallback<String>() {
			@Override
			public void onFailure(Throwable t) {
				// runningFutures.remove(wssessionId);
				result.setErrorResult(t.getMessage());
			}

			@Override
			public void onSuccess(String response) {
				// Will be called in thread
				log("Success");
				log.info("Writing to result: {}: {}", logPrefix, response);
				// runningFutures.remove(wssessionId);
				result.setResult(response);
			}
		});
		// Return the thread to servlet container,
		// the response will be processed by another thread.
		return "index";
	}

	@PostMapping(value = "/startOneFile", produces = "application/json")
	public ResponseEntity<String> startOneFile(@RequestParam("fileName_1") String inputFileName,
			@RequestParam(name = "markMode", required = false) boolean markMode,
			@RequestParam("wssessionId") String wssessionId) throws Exception {
		String outputFileName = createOutputFileName(inputFileName, markMode);
		startDeduplication(inputFileName, outputFileName, markMode, wssessionId);
		return ResponseEntity.ok("{ \"result\": \"Deduplication of " + inputFileName + " has started\"}");
	}

	@PostMapping(value = "/startTwoFiles", produces = "application/json")
	public ResponseEntity<String> startTwoFiles(@RequestParam("oldFile") String oldFile,
			@RequestParam("newFile") String newFile,
			@RequestParam(name = "markMode", required = false) boolean markMode,
			@RequestParam("wssessionId") String wssessionId) {
		startDeduplication(newFile, oldFile, createOutputFileName(newFile, markMode), markMode, wssessionId);
		return ResponseEntity.ok("{ \"result\": \"Deduplication of " + oldFile + " and " + newFile + " has started\"}");
	}

	@GetMapping("/test_results_details")
	public String test_results_details(HttpSession session) {
		return "test_results_details";
	}

	@GetMapping("/twofiles")
	public String twofiles(final HttpSession session) {
		return "twofiles";
	}

	@PostMapping(value = "/uploadFile", produces = "application/json")
	public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
					"{ \"result\": \"Failed to upload " + file.getOriginalFilename() + " because it was empty" + "\"}");
		}
		try {
			Path path = Paths.get(uploadDir, file.getOriginalFilename());
			if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
				Files.delete(path);
			}
			try (InputStream inputStream = file.getInputStream()) {
				Files.copy(inputStream, path);
				return ResponseEntity.ok("{ \"result\": \"You successfully uploaded " + file.getOriginalFilename() + "\"}");
			}
		} catch (IOException e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
					"{ \"result\": \"Failed to upload " + file.getOriginalFilename() + " => " + e.getClass() + "\"}");
		} catch (RuntimeException e) {
			log.error( "RuntimeException met cause: {}", e.getCause());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
					"{ \"result\": \"RuntimeException with cause " + e.getCause() + "\"}");
		}
	}

}
