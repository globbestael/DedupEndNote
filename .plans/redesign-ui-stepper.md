# Plan: Redesign UI — visual stepper with inline progress

**Status: executed — compile passes, same pre-existing test failures as before**

## Context

The original interface had three equal-looking grey boxes, a disconnected progress bar below them, and a heavy left-nav column. Goals:
- Steps are visually distinct by state (active / done / waiting)
- Progress bar appears inside step 2, right below the button that triggered it
- File type is always visible as subtitle text in step 1
- All steps remain visible at once (no popups, no hidden steps)
- Same design applied to both `index.html` and `twofiles.html`

---

## Design

Each step is a card with a colored left border and a numbered circle:

| State | Left border | Circle | Background | Opacity |
|---|---|---|---|---|
| `step-waiting` | grey | grey | light grey | 0.65 |
| `step-active` | Bootstrap primary (blue) | blue + white text | white | 1.0 |
| `step-done` | Bootstrap success (green) | green ✓ | very light green | 1.0 |

Initial states on page load:
- `index.html`: step1 = active, step2 = waiting, step3 = waiting
- `twofiles.html`: step1 = active (with step1a sub-card active, step1b sub-card waiting), step2 = waiting, step3 = waiting

Progress bar + results message move **into step 2**, appearing below the "Start deduplication" button (hidden until upload completes).

Layout changes:
- Remove the fixed `col-3` left sidebar entirely
- Add a slim Bootstrap 5 `navbar` at the top: title + version on the left, links on the right; **fixed-top** so it stays visible while scrolling
  - `index.html`: "Deduplicate 2 files" / "Details ↗" / "Changelog ↗" / "Restart" (primary button)
  - `twofiles.html`: "Home" / "Details ↗" / "Changelog ↗" / "Restart" (primary button); no FAQ link
- Content uses `col-8 offset-2` (centered) with `padding-top: 4.5rem` to clear the fixed navbar
- Page titles in the container: `index.html` → "Deduplicate 1 file"; `twofiles.html` → "Deduplicate 2 files"
- `index.html`: link to "Deduplicate 2 files" shown as regular text below the container title (removed from step 1 card)
- Documentation (Introduction, How it works, etc.) moved into a Bootstrap 5 `accordion` at the bottom
- Version number (`v1.1.5`) shown in the navbar brand (not in the page `<h1>`)
- "Restart" button styled as `btn-primary` (matches the action buttons in the container); other navbar links are `btn-outline-secondary`

Step number circle is rendered as a `<span class="step-number">` **inside** the `<h4>` element, making number and title a single heading unit. The `<h4>` uses `display: flex; align-items: center` to align the circle and text.

The "Choose file" file input is visually replaced by a `<label class="btn btn-outline-secondary btn-sm">Choose file</label>` linked to a hidden `<input type="file" class="d-none">`. After a successful upload the filename (basename only) is shown in a `<span>` next to the button. `disableButton`/`enableButton` helpers also toggle Bootstrap's `.disabled` class on any associated `label[for]`.

---

## Changes made

### `src/main/resources/static/common-style.css` ✅

Added stepper CSS:
- `.step-card` (waiting state), `.step-card.step-active`, `.step-card.step-done`
- `.step-number` circle badge (2 rem, flex-centered)
- Per-state circle colours via `.step-waiting .step-number`, `.step-active .step-number`, `.step-done .step-number`
- `.step-header` (margin only) + `.step-header h4, .step-header h3` (flex, aligned — step-number lives inside h4)
- `.step-subtitle`
- `.step-subcard`, `.step-subcard.step-active`, `.step-subcard.step-done` (for twofiles 1a/1b)

### `src/main/resources/templates/index.html` ✅

- Replaced `col-3` sidebar + `col-7` content with slim `fixed-top` navbar + `col-8 offset-2` centered content
- Navbar brand: `DedupEndNote v1.1.5`; right side: Deduplicate 2 files / Details ↗ / Changelog ↗ / **Restart** (btn-primary)
- Container title: `<h1>Deduplicate 1 file</h1>` with a `<p class="text-muted">` link to "Deduplicate 2 files" below it
- Three `.step-card` divs (step1 active, step2 waiting, step3 waiting on load)
- Step number circle inside `<h4>`
- File input replaced by styled label + hidden `<input class="d-none">`; filename shown in `#fileName_display` span after upload
- Progress area (`#step2-progress`) moved inside step 2, initially `d-none`, shown when upload completes
- `disableButton`/`enableButton` extended to toggle `.disabled` on associated labels
- New `markAsActive(id)` helper
- `fileupload.done`: sets `#fileName_display`, adds `markAsDone('#step1')`, `markAsActive('#step2')`, `showDiv('#step2-progress')`
- STOMP `DONE` handler: adds `markAsActive('#step3')`
- Documentation moved to Bootstrap 5 accordion (Introduction · How it works · Output · Mark mode · How to cite · Issues and feature requests)

### `src/main/resources/templates/twofiles.html` ✅

- Same layout changes as `index.html`
- Container title: `<h1>Deduplicate 2 files</h1>`
- Navbar brand: `DedupEndNote v1.1.5`; right side: Home / Details ↗ / Changelog ↗ / **Restart** (btn-primary); FAQ link removed
- Step 1 card contains step1a / step1b sub-cards side by side (`.step-subcard`)
  - step1a active on load, step1b waiting (label starts with `.disabled` class)
- File inputs in sub-cards: styled label + hidden `<input class="d-none">`; filenames shown in `#fileName_old_display` / `#fileName_new_display` spans after each upload
- `disableButton`/`enableButton` extended to toggle `.disabled` on labels
- After 1a upload: `#fileName_old_display` set, step1a → done, step1b → active (label `.disabled` removed)
- After 1b upload: `#fileName_new_display` set, step1b → done, step1 → done, step2 → active, `#step2-progress` shown
- STOMP `DONE` handler: step2 → done, step3 → active
- Documentation in Bootstrap 5 accordion (Steps · Why deduplicate 2 files? · Mark mode · Caveat)

## Files modified

1. `src/main/resources/static/common-style.css`
2. `src/main/resources/templates/index.html`
3. `src/main/resources/templates/twofiles.html`

## Verification

```bash
./mvnw clean compile test-compile    # BUILD SUCCESS
# Manual checks:
# 1. index.html: title "Deduplicate 1 file"; navbar fixed with version + Changelog link; step 1 highlighted (blue), steps 2+3 grayed
# 2. Upload file: "Choose file" button matches other buttons; filename appears next to button; step 1 turns green, step 2 activates
# 3. Click Start: progress bar fills inline in step 2
# 4. DONE: step 2 turns green, step 3 activates
# 5. Get result: downloads file
# 6. twofiles.html: title "Deduplicate 2 files"; no FAQ in navbar; same flow; 1a/1b sub-steps with filename display
# 7. Restart button (btn-primary) visually distinct from outline nav links
# 8. Scroll down: navbar remains visible (fixed-top)
```
