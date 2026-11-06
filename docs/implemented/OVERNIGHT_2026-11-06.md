# Overnight run 2026-11-05/06: M10 end, M11, M12, final double-check

Branch `doc-app2.0` in the worktree `../web-flutter-doctor-app2`. Nothing was pushed (the branch has no upstream) and
`main` was not touched. One commit per ticket group; every commit carries only the repo's git identity, with no
attribution lines. Status per ticket: `docs/implemented/README.md`; detail in the area files; Codex reviews and the
answers in `docs/implemented/codex/<TICKET>.md`.

## Read first: two things that need you

1. **The iOS release build runs out of memory.** `./gradlew :composeApp:linkReleaseFrameworkIosArm64 --no-daemon`, the
   exact step `ios_testflight.yml` runs, fails with `java.lang.OutOfMemoryError: Java heap space` after 1 h 13 min,
   alone, on a 36 GB Mac with the 6 GB Gradle heap the patient app uses. Compiling is fine (debug links for the
   simulator pass at every commit); only the optimised release link of the framework fails. The Android release
   bundle builds (`bundleRelease`, 51 MB AAB). The doctor app is about 3.75 times the patient app (79 000 lines of
   shared Kotlin vs 21 000; 2 874 strings vs 929), and CI has never run on this branch. GitHub's `macos-latest`
   runner has about 7 GB of RAM, so raising the heap alone can't fix CI. Options, your call:
   - a larger macOS runner (paid) with a bigger heap;
   - keep debug-only code out of release builds (the demo backend, the debug menu and spikes are compiled into
     release today; `DemoBackend.kt` alone is 2 100 lines);
   - try the newer Kotlin/Native memory settings or a newer Kotlin version on a separate `chore/` branch.
   Until then a TestFlight run will fail at "Build Kotlin framework".
2. **TP-05 goes against D-05.** D-05 (your call, 2026-11-04) keeps the client-side PDF fallback to the appointment
   summary and the 025-2. TP-05 (`8b9dabb`) added a client-rendered treatment-plan PDF (Export PDF in the plan's ⋮
   menu), because the backend still has no PDF endpoint (BE-01). If you want D-05 kept, revert that one commit; the
   only other change in it is the clinic Plans tab label fix, which should be kept (see TP-05 below).

## Summary

| Tickets | Commit | Result |
|---|---|---|
| INTEL-03…06 cards (booking, cancel) | `9a5205e` | Done |
| INTEL-07 clinical cards | `d4b7de2` | Done |
| INTEL-08 open-visit card + auto-open | `9cf6e89` | Done |
| AI-04/07/08 speech player, voice orb | `2beefba` | Done |
| INTEL-09 voice in Intelligence | `71bda74` | Done (M10 complete) |
| CLINIC-01…04 workspace, overview, settings | `48b598a` | Done |
| CLINIC-05, CLINIC-08 people, revenue share, invitations | `1fab72a` | Done |
| CLINIC-06 clinic patients | `02b50f4` | Done (BE-35) |
| CLINIC-07 services, catalogue editor | `235a740` | Done |
| test hygiene (newer tests) | `1208dbd` | Done |
| CLINIC-09 + CAL-10 B colleague's calendar | `4c986ea` | Done |
| CAL-10 A staff calendars | `07fc4ee` | Done |
| FIN-01…07 clinic finance | `3338bb7` | Done (BE-36, BE-37) |
| TP-02, TP-03 plans list, plan detail | `d59cb51` | Done |
| TP-04 treatment plan wizard | `2b73b2c` | Done (the board's 5 steps; no slot booking or chart mode) |
| TP-05 treatment plan PDF | `8b9dabb` | Done, see "Read first" 2 (M11 complete) |
| CARE-01…03 care partnerships | `ed3dbdf` | Done |
| REPORT-01 Reports | `a7e8463` | Done |
| LEGAL-01 contract spike + About | `e9e1ce0` | Done; LEGAL-02 not built (no endpoint, BE-16) (M12 complete) |
| final double-check (tests) | see below | Done |

Every ticket had a Codex review; each finding was fixed or answered with evidence in its `codex/` file.

## The tickets of the last part of the night

### TP-04 · Treatment plan wizard (`2b73b2c`)
- The board's steps: patient → plan basics → care team & services (optional FDI tooth per line) → payment (desk
  payment, installment schedule that must match the balance) → review with "Activate the plan now".
- Entries: Plans → New plan, clinic Overview → New plan, a clinic patient's Quick Actions → "Create treatment plan".
- The save sends Flutter's requests in order but checks the schedule first (Flutter skipped a wrong schedule silently
  after creating the plan). A failed request keeps the draft; Retry resends only that request, so the plan is never
  created twice; closing opens the draft.
- Not built: free-slot booking and "link to visit" (not on the board), the tooth-chart mode (the board puts a tooth on
  each line), quantity per line.
- Checked in demo on the emulator end to end; request shapes checked in the backend source and the dev OpenAPI (no dev
  writes: a plan can't be deleted there).

### TP-05 · Treatment plan PDF (`8b9dabb`)
- Plan ⋮ → Export PDF → the share sheet: header, summary, symptoms, notes, lines grouped by tooth, installment
  schedules, the patient's signature block, page footers; en, uz and ru. Exported on the emulator in English and
  Russian and read back (Cyrillic correct).
- Also fixed: the clinic's "Plans" tab borrowed the subscription's `plans` key, which reads "Тарифы" / "Tariflar".

### CARE-01…03 · Care partnerships (`ed3dbdf`)
- The patient record has a ⋮ sheet (doctors only): Find therapy partner, Care partnerships.
- Find: search as you type in the doctor's own country, "Already a partner", specialty and message, Send invite →
  the partnership. List: chips, the patient's only until Show all, invites waiting for you first. Detail:
  Accept / Decline / Cancel invite / Mark completed after a question, progress updates while active.
- Partnership notifications now open the list (they did nothing before; the row carries no partnership id).
- Demo on the emulator end to end; dev checked with reads only.

### REPORT-01 · Reports (`a7e8463`)
- More → Reports and Profile → Reports: Home's Clinic performance card plus SMS reminders for the range ("not enabled"
  for a 403 or the account flag). Its own range (Flutter shared it with Home). The shared CSV now labels visit types
  "(30d)", the backend's real window.
- Dev on the emulator: KPIs, SMS "not enabled" (dev's real answer), the trend.

### LEGAL-01 · Contract spike and About (`e9e1ce0`)
- Spike: no doctor-side contract or legal endpoint exists (only the admin issue call). LEGAL-02 waits for BE-16.
- About: the version, Privacy policy (`https://shifa.uz/#privacy`, the one public page that exists; the patient app
  links it), Copy diagnostics (also seven taps on the version): version and build, platform, backend, language, time
  zone; no names or patient data. Terms, licences and support contacts are left out until real ones exist.

## Final double-check

- **Builds:** Android `assembleDebug` and iOS `linkDebugFrameworkIosSimulatorArm64` pass at every commit; ktlint is
  clean. `bundleRelease` passes. `linkReleaseFrameworkIosArm64` fails (see "Read first" 1).
- **Tests:** the full suite is 1 064 tests (unit and screenshots). Fixes made so it is reliable:
  - 27 older ViewModel tests now stop their ViewModels before resetting the Main dispatcher (the cause of the random
    "Dispatchers.Main is used concurrently" failures). The splash test was left alone: it is scheduler-driven.
  - The request logs that mock engines write from their own threads are now `CopyOnWriteArrayList` in every test
    (plain or `synchronizedList` lists raced: a size was seen before its element).
  - The intelligence booking test waits until Shifa's reply and the first booking have finished before booking again
    (the app rightly refuses a tap meanwhile; under the suite's load the test sometimes tapped too early).
  - Two clinic tests waited 5 s for a debounced search without checking it; they now advance the test clock and
    assert the request.
  - The four shell screenshots draw a neutral stand-in instead of the tabs' real screens, which read the network and
    the clock (the Reports tab made `Shell_moreTab` change daily; the staff shell's clinic tab needs device storage).
    One of these was a mistake of mine, caught and fixed: the Intelligence preview's trailing lambda bound to the new
    parameter; it now uses named arguments and its image is unchanged.
    - A calendar test asserted the order of two reads the app runs together on purpose; it now checks "once each".
  Result of the last full run (`ktlintCheck verifyRoborazziDebug`): **1 064 tests, 0 failures, every screenshot
  matching, ktlint clean** (19 min). The four runs before it each failed 1–4 different tests, which is how the races
  above were found.
- **Commit audit:** all 166 commits on the branch are authored and committed by `SheroziyCODE
  <sheroziy.saidkhodjaev@code.berlin>`; no Co-Authored-By or "Generated with" lines (the only keyword hits are content:
  "Claude Design canvas", `CLAUDE.kmp.md`, "Codex rounds"); no `.gz` files; `iosApp.xcscheme` only in `764edb1` (D-10).
  Two older subjects are longer than 72 characters (`b2da2f4`, 76; `5977cb1`, 74); left as they are rather than
  rewriting history.
- **Docs:** the README status table matches the code. Open by design: CAL-5 (tablet grid), CAL-9 (waits for push),
  VIDEO-03 (D-07), LEGAL-02 (BE-16). I edited and then reverted one line in the repo's `CLAUDE.md` (your instructions
  file); it is unchanged.

## Left for you

- The iOS release memory problem and the D-05 question above.
- PUSH-01…03 (D-15, at the end), REL-04 (remove the Flutter code), REL-05 (rollout and the PR to `main`).
- Commit and push decisions: nothing was pushed. Every commit is local on `doc-app2.0`.
