# What's implemented

Read this before starting a task. Update it at the end of every job, in the same commit: the status table, the file
for the area you touched, and one line in the log.

Ticket IDs and order: `docs/kmp-spec/17-master-plan.md` §4 (then `16-implementation-plan.md`). What the app needs from
the backend team: [../backend-improvements.md](../backend-improvements.md). Codex reviews of each ticket:
[codex/](codex/).

| Area | File |
|---|---|
| Project, CI, HTTP client, storage, session, demo backend, utils, native spikes | [foundation.md](foundation.md) |
| Design tokens and the UI kit | [design-system.md](design-system.md) |
| Strings and localization | [strings.md](strings.md) |

## Decisions

Recorded so they are not re-litigated. IDs are from `17-master-plan.md` §3.

| ID | Decision | Whose call |
|---|---|---|
| D-00 | **One branch, `doc-app2.0`, for the whole rewrite; no per-ticket branches** (overrides §3's default of short PR branches per ticket group). Created from `origin/main` at `078af42` after the docs PR (#21) was merged. | The user's call, 2026-11-04 |
| D-01 | Design source is `docs/design/` (the vendored Claude Design canvas); the primary fill stays `teal-50` with white labels. | The user's call, 2026-11-04 (§3 default) |
| D-02 | Clinic staff use the mobile app with the 4-tab shell (`01/18`). | The user's call, 2026-11-04 (§3 default) |
| D-03 | Gated entries stay hidden until the profile has loaded; gate on `subscription.features`, never fail open. | The user's call, 2026-11-04 (§3 default) |
| D-04 | No registration e-mail code dialog until BE-03 ships; `send-email-otp` wiring kept ready. | The user's call, 2026-11-04 (§3 default) |
| D-05 | PDFs: backend rendering requested (BE-01); client `expect/actual` fallback only for the appointment summary and 025-2. | The user's call, 2026-11-04 (§3 default) |
| D-06 | Maps: address fields + coordinates first, native map behind `expect/actual` later, no Google key in the app (BE-15). | The user's call, 2026-11-04 (§3 default) |
| D-07 | Not ported: waiting room, typing indicator, doc-access approve/reject in the inbox, phone OTP. | The user's call, 2026-11-04 (§3 default) |
| D-08 | Flutter web keeps deploying from `main`; no desktop layouts in the KMP app. | The user's call, 2026-11-04 (§3 default) |
| D-09 | App-lock PIN 4–6 digits, user's choice, explicit submit, salted hash. | The user's call, 2026-11-04 (§3 default) |
| D-10 | Version offsets: `VERSION_CODE_OFFSET` = `BUILD_NUMBER_OFFSET` = 1, the Flutter formula, on the same workflow files, so the run counters keep rising (last Flutter uploads: Play run 8 → versionCode 9, TestFlight run 5 → build 6; read from the public Actions API on 2026-11-04). | The user's call, 2026-11-04 (§3 default) |
| D-11 | Doctor delete-account UI built; the action goes to "contact support" until BE-02 ships. | The user's call, 2026-11-04 (§3 default) |
| D-12 | Video gated on `VIDEO_CONSULTATION` in `features`, hidden while unresolved. | The user's call, 2026-11-04 (§3 default) |
| D-13 | Push language and per-device tokens: inherit the patient app's asks (BE-05, BE-06); single token until then. | The user's call, 2026-11-04 (§3 default) |
| D-14 | Booking is slot-first only; "Book for this patient" opens the calendar with the patient pre-filled. | The user's call, 2026-11-04 (§3 default) |

## Status

| Ticket | Status | Notes |
|---|---|---|
| CORE-01 Project skeleton, CI | Done | KMP project (`composeApp`, `shifa.doctor`, `com.shifa.doc` / `uz.shifa.doc`), the patient app's Gradle catalog and ktlint, `iosApp` host, `CLAUDE.md` from `CLAUDE.kmp.md`, `ci.yml` + the two deploy workflows ported. CI has never run. See foundation.md. |
| CORE-02 … CORE-09 | Not started | |

## Log

- 2026-11-04 · CORE-01 · KMP skeleton next to the Flutter code on `doc-app2.0`; both platforms compile; CI ported.
