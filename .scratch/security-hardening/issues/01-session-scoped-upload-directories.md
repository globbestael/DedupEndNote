# Session-scoped upload directories

Status: ready-for-agent

## What to build

Store each user's uploaded file and derived output under a per-session subdirectory (`upload-dir/<wssessionId>/`) instead of the flat shared upload directory. This eliminates both HIGH namespace-collision findings: two users uploading a file with the same name (e.g. `export.ris`) no longer overwrite each other's content or result.

The change cuts through every layer end-to-end:
- Controller upload endpoint: create `<wssessionId>/` subdirectory and write the incoming file there
- `UtilitiesService` path helpers (`resolveInUploadDir`, `createOutputPath`): work within the session subdirectory
- Download endpoint: resolve the file from the session subdirectory
- Cleanup: remove the session subdirectory after the result is downloaded (or on WebSocket session close)

## Acceptance criteria

- [ ] Uploaded files are written to `upload-dir/<wssessionId>/`
- [ ] Output files are created in the same session subdirectory
- [ ] The download endpoint resolves files from the session subdirectory
- [ ] Path traversal guard (`resolveInUploadDir`) still applies within the session subdirectory
- [ ] The session subdirectory is cleaned up after the result is downloaded or the WebSocket session ends
- [ ] Two concurrent users uploading `export.ris` do not interfere with each other (verified by integration test or manual test)

## Blocked by

None — can start immediately.
