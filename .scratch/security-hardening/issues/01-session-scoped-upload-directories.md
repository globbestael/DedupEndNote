# Session-scoped upload directories

Status: ready-for-agent

## What to build

Store each user's uploaded file and derived output under a per-workflow subdirectory (`upload-dir/<wssessionId>/`) instead of the flat shared upload directory. This eliminates both HIGH namespace-collision findings: two users uploading a file with the same name (e.g. `export.ris`) no longer overwrite each other's content or result. Two browser tabs from the same user are also fully isolated because each page load produces its own UUID (see issue 00).

With issue 00 in place, the UUID is already embedded by Thymeleaf into all three forms (upload, start, result) at page-render time. `uploadFile` and `getResultFile` therefore receive `@RequestParam UUID wssessionId` with no additional JavaScript wiring or form changes needed here.

End-to-end path through all layers:

- `UtilitiesService`: add `getSessionDir(String uploadDir, UUID sessionId)` and `resolveInSessionDir(String uploadDir, UUID sessionId, @Nullable String userFileName)`. Because a `UUID` contains only hex digits and hyphens, it is safe as a directory name by construction — no path-traversal validation is needed for the session ID component, only for the filename.
- `uploadFile`: add `@RequestParam UUID wssessionId`; create `upload-dir/<uuid>/` with `Files.createDirectories` (idempotent — safe for the two-files workflow where two sequential uploads share the same UUID and therefore the same directory); write the uploaded file there.
- `startOneFile` / `startTwoFiles`: already have `UUID wssessionId` (from issue 00); resolve input paths within the session subdirectory.
- `getResultFile`: add `@RequestParam UUID wssessionId`; resolve the result file within the session subdirectory.
- **No cleanup after download**: the upload directory is wiped on daily server restart, which is sufficient.

## Acceptance criteria

- [ ] Uploaded files are written to `upload-dir/<wssessionId>/`
- [ ] Output files are created in the same session subdirectory
- [ ] The download endpoint resolves files from the session subdirectory
- [ ] Path traversal guard still applies to the filename component within the session subdirectory
- [ ] Two concurrent users uploading `export.ris` do not interfere with each other
- [ ] Two browser tabs from the same user do not interfere with each other
- [ ] In the two-files workflow, both the OLD and NEW file uploads succeed into the same session subdirectory
- [ ] All existing integration tests pass

## Blocked by

- `00-server-side-websocket-session-uuid.md`
