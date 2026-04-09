# Plan: Refactor WebSocket handling — decouple, clean up routing, tidy config

**Status: executed — compile passes, unit tests pass (4 pre-existing failures), integration tests pass (3 pre-existing failures)**

## Context

`DeduplicationService` depended on `SimpMessagingTemplate` to send progress messages during deduplication — a tight coupling (the service shouldn't know about WebSockets). The session routing used a fragile hack (extracting the SockJS transport session ID from internal URLs). The WebSocket config had an unused `/app` destination prefix and the frontend had an empty `disconnect()` function.

## Step 1: Decouple `DeduplicationService` from WebSocket ✅

**File:** `src/main/java/edu/dedupendnote/services/DeduplicationService.java`

- Removed `SimpMessagingTemplate` from constructor and field
- Removed `wsMessage()` helper method
- Replaced `String wssessionId` parameter with `Consumer<String> progressReporter` in all public methods:
  - `compareSet`, `deduplicateOneFile`, `deduplicateTwoFiles`, `searchYearOneFile`, `searchYearTwoFiles`
- Replaced all `wsMessage(wssessionId, msg)` calls with `progressReporter.accept(msg)`

**File:** `src/main/java/edu/dedupendnote/controllers/DedupEndNoteController.java`

- Injected `SimpMessagingTemplate` into the controller constructor
- Created `Consumer<String> progressReporter` lambda that routes to WebSocket:
  ```java
  Consumer<String> progressReporter = message ->
      simpMessagingTemplate.convertAndSend("/topic/messages-" + wssessionId, new StompMessage(message));
  ```
- Passed `progressReporter` to `deduplicateOneFile()` / `deduplicateTwoFiles()` instead of `wssessionId`
- Updated stale comment block to reflect new architecture

## Step 2: Clean up session routing ✅

**Files:** `src/main/resources/templates/index.html`, `src/main/resources/templates/twofiles.html`

Replaced:
```javascript
var wssessionId = /\/([^\/]+)\/websocket/.exec(socket._transport.url)[1];
```
With:
```javascript
var wssessionId = crypto.randomUUID();
```
UUID generated *before* connecting — no dependency on SockJS transport internals, always available for the hidden form field and subscription.

## Step 3: Clean up unused config and frontend ✅

**File:** `src/main/java/edu/dedupendnote/WebSocketConfig.java`
- Removed `config.setApplicationDestinationPrefixes("/app")` — no `@MessageMapping` handlers exist

**File:** `src/main/resources/templates/index.html`
- Fixed empty `disconnect()` to call `stompClient.disconnect()`

## Test updates ✅

- `AbstractIntegrationTest`: removed `wssessionId` field; kept `@MockitoBean SimpMessagingTemplate` (controller still needs it)
- All integration/unit test calls replaced `wssessionId` arg with inline no-op lambda `message -> {}`
- `AuthorExperimentsTests`: `new DeduplicationService(simpMessagingTemplate, ...)` → `new DeduplicationService(new ComparisonService())`

## Files modified

1. `src/main/java/edu/dedupendnote/services/DeduplicationService.java`
2. `src/main/java/edu/dedupendnote/controllers/DedupEndNoteController.java`
3. `src/main/java/edu/dedupendnote/WebSocketConfig.java`
4. `src/main/resources/templates/index.html`
5. `src/main/resources/templates/twofiles.html`
6. `src/test/java/edu/dedupendnote/AbstractIntegrationTest.java`
7. `src/test/java/edu/dedupendnote/DedupEndNoteApplicationTests.java`
8. `src/test/java/edu/dedupendnote/TwoFilesTest.java`
9. `src/test/java/edu/dedupendnote/MissedDuplicatesTests.java`
10. `src/test/java/edu/dedupendnote/services/AuthorExperimentsTests.java`
11. `src/test/java/edu/dedupendnote/services/ValidationTests.java`

## Verification

```bash
./mvnw clean compile test-compile    # BUILD SUCCESS
./mvnw test -Punit-tests             # 456 tests, 4 pre-existing failures (AuthorsComparisonThresholdTest, JaroWinklerTitleTest)
./mvnw test -Pintegration-tests      # 125 tests, 3 pre-existing failures (DedupEndNoteApplicationTests [2], MissedDuplicatesTests [1])
```

## Note on pre-existing failures

Identical to those documented before this refactoring — the refactoring introduced no new failures.
