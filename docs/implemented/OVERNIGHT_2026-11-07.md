# Overnight run 2026-11-06/07: M14 web (WEB-04…08, WEB-10 part) and the UI audit

Branch `doc-app2.0` in the worktree `../web-flutter-doctor-app2`. Nothing was pushed and `main` was not touched. One
commit per ticket group, the repo's git identity only, no attribution lines. Status per ticket:
`docs/implemented/README.md`; Codex reviews and the answers in `docs/implemented/codex/<TICKET>.md`.

## Read first: what needs you

1. **Hosting the web build (WEB-09).** The production bundle (`./gradlew :composeApp:wasmJsBrowserDistribution
   -Pkotlin.daemon.jvmargs=-Xmx8g`, output `composeApp/build/dist/wasmJs/productionExecutable/`) is 29 MB on disk, of
   which two `.wasm` files are 15.1 MB (the app) and 8.6 MB (Skia). Gzipped the whole first load is about 7 MB. On
   cPanel, make sure `.wasm` is served as `application/wasm` and that gzip (or Brotli) is on for `.wasm`, `.js` and
   `.json` (`.htaccess`: `AddType application/wasm .wasm` plus `mod_deflate` for those types); without compression
   the first visit downloads 23 MB. The `.js.map` files need not be uploaded.
2. **Safari and Firefox were not tested.** Firefox isn't installed on this Mac, and Safari's driver needs a one-time
   `sudo safaridriver --enable`. Everything web was checked in headless Chrome only.
3. **Briefing retry (your call).** `visit/Briefing.kt` and `patients/PatientBriefing.kt` keep their own "Retry
   briefing" button: it has its own label, a loading state, and serves the Pending state too. Moving them onto the
   kit's `ShifaLoadError` would need a label and loading parameter on the kit component. Say if you want that.

## Summary

| Work | Commits | Result |
|---|---|---|
| WEB-04…08, WEB-07a–j, WEB-10 (browser Back) | `a47c363` … `2635499` | Done (see README rows) |
| Lines over 120 characters (ktlint failed the full suite) | `154ff86` | Fixed |
| UI audit (your request: errors, retry centring, error screens, phone and web) | `7940f69` | Done, details below |

Tests: the full suite (`verifyRoborazziDebug`, 1 071 tests: unit and screenshot) passed except for 15 screenshots that
changed on purpose; those were looked at and re-recorded. After the last fixes, lint, the Android, wasm and iOS
simulator compiles, and the calendar, clinic, home and utils tests pass.

## UI audit

All 21 contact sheets of phone screenshots were reviewed, plus the web build at 1440, 1024 and 600 wide earlier in
the run. Findings and what happened to each:

**Errors and Retry (your example).** They were shown five ways: a grey pill, a link under a banner, a lone red
line, an info icon and an EmptyState with a button. Now there is one kit component, `ShifaLoadError`
(`ui/widgets/Feedback.kt`):
- a whole screen or list that didn't load: a card with a red alert disc, the message, the request id and a Retry
  button, centred;
- a section that didn't load (a panel, a sheet's list, a timeline): the compact form, the message and a Retry link
  centred where the section would go;
- a failed refresh over content that is still shown: the existing banner with Retry under it, start-aligned.

About 45 sites across Patients, Tasks, Calendar, Chat, Clinic, Visit, Forms, Documents, Care, Schedule, Profile,
Home and Analytics were moved onto it. Codex then found two first-load cases that still showed only a banner over a
blank area (clinic finance views and a calendar day never read); both now show the card or compact form.

**Error screens exist everywhere.** Every list or screen that loads from the backend has an error state with Retry.
Not-found screens (patient, task, document) show a message with Back.

**Other fixes:**
- Patient not found and the staff visit screen showed their message at the top of a card stretched to the bottom;
  stretched empty and error states now centre their content.
- Home metric titles were cut on phones ("Notific…"): the card now uses the board's words, "Unread" over
  "notifications". "Follow-…" stays cut, as in the board; two lines broke it mid-word on Android.
- English strings: a finished appointment reads "Completed" (was "Complete"), and the profile reads "62% complete"
  (was "62 %").
- Settings: three rows had icons and the rest didn't, so labels started at different points. The board has no
  icons, so none now.
- Analytics at large text broke "Documents" mid-word: the engagement tiles stack.
- Clinic finance installment totals ran off the right edge: they wrap now.
- Calendar detail chips squeezed "Paid" into "Pa / id": they wrap now.
- Tooth map: the service count badge covered a lower tooth's number; it now sits at the corner away from the number.
- Phone numbers showed raw in some places ("+998900000014"). `PhoneE164.display` now groups them in the patients
  table and list, the patient record, clinic patients, clinic contact, the plan wizard, finance sheets, Profile and
  Intelligence cards. Input fields keep what was typed.
- Chat list: an attachment's preview is the backend's English word ("Photo", "Voice message", "Document"); the app
  now shows it in the doctor's language. Demo data no longer shows a file name there.
- A voice note's time sat under its bubble while every other bubble has it inside; it's inside now, as on the board.

**Checked and left as they are:**
- Intelligence error screenshot "content above the header": the preview stacks two states on purpose.
- Bottom bar shows Calendar under Intelligence: Intelligence is an overlay on the current tab.
- Notifications' "Open Calendar" indent: same as the board (a borderless button).
- The sparkle under the patient's star: it's the briefing button. The star's ring is its selected state.
- "Remind patient to pay" overflow: not reproduced; it lines up with the cards.
- Email sign-in form in the lower half: it is centred vertically, like the login.
- Staff chips and Care tabs run off the edge: they scroll (pickers).
- Time-zone banner line height: it's the kit banner's text style; changing it would change every banner.

## Not done

- WEB-10 remaining: paths per route (deep links), first-load time on a real host (depends on WEB-09
  compression), Safari and Firefox, keyboard focus order.
- Web error states were not exercised live: demo mode can't fail requests, and dev would need a sign-in.
  The layout is the same shared code the phone screenshots cover.
- Yours, as agreed: WEB-00 (admin repo), WEB-09 (cPanel deploy), PUSH-01…03, the iOS release OOM decision, D-05 vs
  TP-05.
