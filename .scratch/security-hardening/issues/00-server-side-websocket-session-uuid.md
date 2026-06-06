# Server-side WebSocket session UUID

Status: ready-for-agent

## What to build

Generate the per-workflow UUID server-side and embed it into the HTML at render time via Thymeleaf, removing the client-side `generateUUID()` / `Math.random()` fallback entirely.

End-to-end path through all layers:

- `home()` and `twofiles()` each call `UUID.randomUUID()`, add it to the model as `"wssessionId"`, and replace the now-unused `HttpSession session` parameter with `Model model`.
- Both HTML templates embed the UUID into **every** hidden field that carries it: the start form (already present), the upload form (new), and the result form (new). This is done with a Thymeleaf expression at render time — no JavaScript assignment needed.
- The WebSocket subscription path uses the Thymeleaf-injected value, not the JS-generated one. The `generateUUID()` function and the `Math.random()` fallback block are removed from both pages.
- `startOneFile` and `startTwoFiles` change their `@RequestParam String wssessionId` to `@RequestParam UUID wssessionId`. Spring validates the UUID format automatically and rejects malformed values with 400, making the parameter non-null and format-guaranteed at the method boundary. WebSocket routing uses `wssessionId.toString()`.

`uploadFile` and `getResultFile` do **not** yet use the UUID — that is issue 01.

## Acceptance criteria

- [ ] UUID generated via `UUID.randomUUID()` in `home()` and `twofiles()`; `HttpSession session` parameter removed from both methods
- [ ] UUID embedded via Thymeleaf in the start form, upload form, and result form of both `index.html` and `twofiles.html`
- [ ] `generateUUID()` JS function and `Math.random()` fallback removed from both pages
- [ ] WebSocket subscription (`/topic/messages-<uuid>`) uses the Thymeleaf-injected value
- [ ] `startOneFile` and `startTwoFiles` accept `@RequestParam UUID wssessionId`
- [ ] All existing integration tests pass

## Blocked by

None — can start immediately.
