# jQuery 3 → 4 upgrade

## Context

jQuery 3.7.0 (webjar `org.webjars:jquery:3.7.0`) upgraded to jQuery 4.0.0.
The jQuery Migrate 4.0.2 plugin is used as a transition aid to surface any
remaining deprecated API usage in the browser console.

Reference: https://jquery.com/upgrade-guide/4.0/

## Dependency map before this change

| Library | Version | Loaded via |
|---|---|---|
| jQuery | 3.7.0 | webjar |
| jQuery UI | 1.14.2 | webjar |
| blueimp-file-upload | 10.32.0 | webjar |

## Phase 1 — Mechanical changes (this commit)

### Breaking change fixed
- `jQuery.parseJSON(jqXHR.responseText)` → `JSON.parse(jqXHR.responseText)` in
  `index.html` and `twofiles.html`. `jQuery.parseJSON` was removed in jQuery 4.0.

### jQuery loading switched to CDN
`fragments.html` and `test_results_details.html`: replaced
`@{/webjars/jquery/jquery.min.js}` with:
```html
<script src="https://code.jquery.com/jquery-4.0.0.js"></script>
<script src="https://code.jquery.com/jquery-migrate-4.0.2.js"></script>
```
The webjar `org.webjars:jquery:3.7.0` dependency in `pom.xml` is intentionally
left in place until Phase 3 (it does no harm while unused).

### Dead code removed
`result.html` — never returned by any controller; referenced jQuery 1.11 and
Bootstrap 3. Deleted.

## Phase 2 — Browser console review (manual step)

Run the app (`./mvnw spring-boot:run`) and open each page in a browser with the
developer console open. jQuery Migrate 4.0.2 prints `JQMIGRATE:` prefixed
warnings for any deprecated or removed API usage still present in the code or
in the loaded plugins.

**Pages to check:**
- `/` (index) — file upload + dedup flow (core path)
- `/twofiles` — two-file variant
- `/test_results_details` (internal test page)

**Highest risk: blueimp-file-upload**  
The plugin's `$('#upload_form').fileupload({...})` call is the most likely
source of Migrate warnings. If the plugin is incompatible with jQuery 4.0,
consider replacing it with a native `fetch`-based upload (the upload handler
on the server side does not need to change).

**Lower risk: jQuery UI 1.14.2**  
Released specifically to support jQuery 4.0 migration; should be clean.

Record any `JQMIGRATE:` warnings found and fix them before Phase 3.

## Phase 3 — Switch to webjar and remove Migrate (after Phase 2 is clean)

1. Verify `org.webjars.npm:jquery:4.0.0` exists on Maven Central.
2. In `pom.xml`: replace `org.webjars:jquery:3.7.0` with
   `org.webjars.npm:jquery:4.0.0` (note: groupId may change to `org.webjars.npm`).
3. In `fragments.html` and `test_results_details.html`: replace the two CDN
   `<script>` tags with:
   ```html
   <script th:src="@{/webjars/jquery/jquery.min.js}"></script>
   ```
4. Run integration tests and do a final browser smoke test with no Migrate
   warnings expected.
