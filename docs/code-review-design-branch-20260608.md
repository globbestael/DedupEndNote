# Thermo-Nuclear Code Quality Review — `design` branch

**Date:** 2026-06-08  
**Branch:** `design` vs `main`  
**Reviewer:** Claude Sonnet 4.6

---

## Summary

The branch implements a batch of security improvements: UUID-based session directories, path traversal protection, server-side UUID generation, semaphore-based concurrent run limiting, configurable dedup timeout, record count cap, upload rate limiting, and NIO2 migration throughout the service layer. The security model is sound and the `String → Path` migration is done correctly. Three structural issues must be addressed before merge.

---

## Finding 1 — HIGH: `startOneFile` / `startTwoFiles` duplicate ~70 lines of semaphore + timeout + error orchestration

Both methods have an identical structure: resolve paths → check semaphore → create `progressReporter` → submit virtual-thread task → `future.get(timeout)` → handle `TimeoutException` (cancel + delete output + 503) → `finally { release }` → outer `catch (IllegalArgumentException)` → 400. The only difference is the `deduplicationService` call and the number of resolved paths.

This is textbook copy-paste that will rot: any future change to timeout handling, error messages, or semaphore semantics must be made in two places.

**Remedy:** extract a private `runDedup` helper that takes a `Callable<String>` (the dedup task), the output `Path` to delete on timeout, and the `progressReporter`. Each handler resolves its paths, then hands off to the helper:

```java
private ResponseEntity<String> runDedup(String logPrefix, Path outputPath,
        Callable<String> dedupTask, Consumer<String> progressReporter)
        throws InterruptedException, ExecutionException {
    if (!concurrentRunsSemaphore.tryAcquire()) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body("{\"result\": \"ERROR: Server is busy.\"}");
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
            try { Files.deleteIfExists(outputPath); } catch (IOException ignored) {}
            String msg = "ERROR: Deduplication timed out after " + timeoutMinutes + " minutes.";
            progressReporter.accept(msg);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"result\": \"" + msg + "\"}");
        }
    } finally {
        concurrentRunsSemaphore.release();
    }
}
```

`startOneFile` then shrinks to ~10 lines. Same for `startTwoFiles`. The duplicate 70 lines disappear.

---

## Finding 2 — MEDIUM: `uploadFile` manual `FileChannel.transferFrom` loop is complex and its "zero-copy" comment is wrong

```java
// NIO2 zero-copy transfer via the operating system
long chunkSize = 8 * 1024 * 1024;
long bytesTransferred = 0;
long fileSize = file.getSize();
while (bytesTransferred < fileSize) {
    ...
    long transferred = outputChannel.transferFrom(inputChannel, bytesTransferred, bytesToTransfer);
    if (transferred <= 0) break;
    bytesTransferred += transferred;
}
```

`FileChannel.transferFrom` only achieves zero-copy (the `sendfile`/`copy_file_range` syscall) when the **source** is also a `FileChannel`. Here the source is `Channels.newChannel(file.getInputStream())` — a channel wrapping the multipart request body. There is no zero-copy benefit. The comment is false advertising and the 15-line loop is harder to audit than the one-liner it replaced.

**Remedy:** replace the entire `FileChannel` block with:

```java
Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
return ResponseEntity.ok("{\"result\": \"File uploaded successfully\"}");
```

This also removes the imports for `Channels`, `FileChannel`, `ReadableByteChannel`, and `StandardOpenOption`.

---

## Finding 3 — MEDIUM: `removeFileExtension(String, boolean)` is dead code

The two-arg overload (with `removeAllExtensions` flag) is never called by any production or test code. The one-arg `removeFileExtension(String)` always delegates to it with `false`. This was copied from a Baeldung tutorial that included the multi-extension variant "just in case."

A public static method with a boolean mode parameter that is never passed `true` is a smell. Future readers wonder when `removeAllExtensions=true` is relevant.

**Remedy:** inline the `false` body directly into the one-arg method and delete the two-arg overload:

```java
public static String removeFileExtension(String filename) {
    if (filename == null || filename.isEmpty()) return filename;
    return filename.replaceAll("(?<!^)[.][^.]*$", "");
}
```

---

## Finding 4 — MEDIUM: `ThreadLocal<String> pendingIp` in `RateLimitInterceptor` is opaque

A static `ThreadLocal` is used to pass the client IP from `preHandle` to `afterCompletion`. This works (Spring MVC calls both on the same thread for a given request) but is harder to reason about than the idiomatic Spring approach and requires careful cleanup.

The standard Spring pattern for passing state between interceptor phases is `request.setAttribute` / `request.getAttribute`, which ties the value's lifetime to the request object and needs no explicit cleanup:

```java
@Override
public boolean preHandle(HttpServletRequest request, ...) throws IOException {
    String ip = extractIp(request);
    ...
    request.setAttribute("rateLimitIp", ip);
    return true;
}

@Override
public void afterCompletion(HttpServletRequest request, ...) {
    String ip = (String) request.getAttribute("rateLimitIp");
    if (ip != null && response.getStatus() == HttpServletResponse.SC_OK) {
        ...
    }
}
```

This removes the `static ThreadLocal`, the `pendingIp.set/get/remove` calls, and the accompanying comment explaining why `afterCompletion` doesn't need cleanup in the rejection path (that concern disappears entirely).

---

## Finding 5 — LOW: test boilerplate copied across 4 integration test classes

`PathTraversalTests`, `ConcurrentRunsTests`, `DeduplicationTimeoutTests`, and `RateLimitTests` all share the same `RestTemplate` setup, `@LocalServerPort int port`, `@MockitoBean SimpMessagingTemplate`, and `private String url(String path)` helper — ~15 lines of identical boilerplate per class, 60 lines total.

They cannot extend `AbstractIntegrationTest` (wrong web environment), but a lightweight `AbstractRandomPortIntegrationTest` base class without any `@SpringBootTest` annotation would work. Each concrete class keeps its own `@SpringBootTest(webEnvironment = RANDOM_PORT, properties = "...")` while the base provides the shared fields and setup. Spring Boot picks up the annotation from the most-derived class.

Not a blocker, but the next feature adding another RANDOM_PORT test will copy-paste again.

---

## What is clean in this diff

- The `String → Path` migration throughout the service layer and tests is done correctly and consistently.
- `UtilitiesService.resolveInSessionDir` is the right place for path traversal enforcement; the validation logic (absolute paths, `getNameCount() != 1`, newlines, `..` literals, normalize + startsWith check) is thorough.
- The session-directory model (UUID per session in `uploadDir/{UUID}/`) cleanly fixes the user file namespace collision.
- `RecordCountCapTests`, `UtilitiesServiceTest`, and `PathTraversalTests` are well-structured tests for the new security behaviors.
- `GlobalExceptionHandler` is appropriate.
- `checkRecordCap` as a helper in `DeduplicationService` is the right factoring for the one-file path.
- The `@PostConstruct`-initialized semaphore pattern is correct.
- Server-side UUID generation and injection into the Thymeleaf model is a clean improvement over the previous client-side JS workaround.

---

## Verdict

Not approvable as-is. Findings 1–3 are concrete structural issues with clear, low-risk remedies. Finding 1 (duplicate orchestration block) is the most important — it is the kind of debt that compounds with every future change to timeout or semaphore logic. Findings 2 and 3 are straightforward deletions of code that should not have been added.
