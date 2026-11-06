# M14 — Web (the KMP doctor app in the browser) and the end of the Flutter code

The user's calls, 2026-11-06:
- The doctor app gets a web build from the same KMP code. It is a new milestone, and it **reuses the existing
  components**: the same ViewModels, repositories and kit widgets, with desktop layouts composed from them (no second
  UI, no copied screens).
- All Flutter code leaves this repository. The admin panel (`admin.shifa.uz`, Flutter only, ADMIN-* parked) moves to
  its **own repository** first, so it keeps running.
- Libraries are upgraded to versions with web (wasm) support **in both apps**, keeping one shared version catalog.
- Web hosting moves from Firebase Hosting to **cPanel**, deployed with **rsync over SSH**.
- Push (PUSH-01…03) stays for later.

Design source: the Claude Design project (`docs/design/`), board `DaApp` "Doctor App — Web (full prototype)",
1440×960, and the module boards' desktop branches. Shell breakpoints from `DaApp`: phone below 768 (bottom bar), icon
rail 768–1099 (`ShellTablet`), sidebar from 1100 (`Sidebar`).

## Tickets

| ID | Title | Depends | Notes |
|---|---|---|---|
| WEB-00 | Admin panel to its own repository | — | A snapshot of the Flutter app from `main` with its history, deploying only the admin target (to cPanel like the doctor web). Creating the GitHub repository and pushing need the user's go-ahead and its name. |
| WEB-01 | Remove the Flutter code from `doc-app2.0` | WEB-00 | `lib/`, `android/`, `ios/`, `web/`, `windows/`, `test/`, `pubspec.*`, `analysis_options.yaml`, Flutter assets and fonts, `firebase.json` hosting targets, `deploy_web.yml` (replaced by WEB-09). Keep `docs/`, `docs/contracts`, `scripts/` that still apply. `main` keeps the Flutter web running until `doc-app2.0` is merged. (Was REL-04.) |
| WEB-02 | Library upgrades for wasm, both apps | — | Ktor 2.3 → 3.x, kotlinx-coroutines 1.7 → 1.10, kotlinx-datetime 0.5 → 0.6.x (retire the iOS `Clock` workaround if the skew is gone), multiplatform-settings 1.1 → 1.3, Kamel 0.9 → 1.x, kotlinx-serialization as needed. Patient app on its own `chore/` branch from `main`; doctor app on `doc-app2.0`. Full test suites green in both before moving on. |
| WEB-03 | Design sync | — | Vendor the canvas's current `DaApp`, `DaAuth`, `DaProfile`, `DaVisit` and `shifa-ds.css` (they changed since CORE-02); note what changed for the phone screens already built. |
| WEB-04 | wasmJs target that compiles | WEB-02 | `wasmJs { browser() }` in `composeApp`, a `webMain`/`wasmJsMain` source set, `index.html`, fonts; a web actual for each of the 34 `expect`s, honest stubs first (a feature without a web version is hidden, never a dead button). Login reaches the shell in Chrome against the dev backend. |
| WEB-05 | Web platform pieces | WEB-04 | Token storage (decide: `localStorage` vs session-only; no secure enclave on web), file picking and download/share, PDF view (the browser's viewer) and render, audio playback, voice recording (MediaRecorder), clipboard, in-app browser (new tab), maps (open in a map site). Daily video on web is its own ticket (WEB-08). |
| WEB-06 | Responsive shell | WEB-04 | One `ShellViewModel`; the chrome by width: bottom bar / icon rail / sidebar (boards `NavBar`, `ShellTablet`, `Sidebar`, `DaApp`); content width limits from `--layout-*` tokens; hover and pointer states for the kit; keyboard focus order. |
| WEB-07a…j | Desktop layouts per area | WEB-06 | Reuse the screens' content composables in desktop arrangements per the boards: a Auth, b Home, c Calendar (week grid; CAL-5's N-day grid), d Patients list (DataTable) and record, e Chat (list + thread), f Notifications and Tasks, g Visit workspace, h Intelligence, i Clinic (finance tables, plans), j Profile, Settings, Reports, About. Each: screenshot tests at desktop width, checked in the browser. |
| WEB-08 | Video on web | WEB-05 | Daily's JavaScript SDK behind the existing `NativeVideoCall` interface. |
| WEB-09 | cPanel deployment | WEB-04 | GitHub Actions: build the wasm distribution, rsync over SSH to the cPanel folder of the doctor site; `.htaccess` with the SPA rewrite, `application/wasm` MIME type and cache headers. Secrets: host, port, user, private key, target path (names in the workflow). Replaces `deploy_web.yml`'s Firebase Hosting. |
| WEB-10 | Web QA | WEB-07, WEB-09 | Chrome, Safari, Firefox; keyboard; the browser back button and deep links (paths per route); load time of the first screen. |

## Order

WEB-03 and WEB-02 first (the design and the compile gate), then WEB-04 → WEB-05/06 → WEB-07 area by area →
WEB-08 → WEB-09 → WEB-10. WEB-00 → WEB-01 can run any time once the admin repository exists.

## Decided 2026-11-06 (the user's calls)

- WEB-00: the admin repository is `SHIFA-UZ/web-flutter-admin` (private), created and pushed with `main`'s history.
- WEB-05: the session token lives in the browser's `localStorage` (doctor tokens expire after 12 hours anyway).
- Commits: one per ticket on `doc-app2.0` (and the patient app's `chore/` branch); nothing pushed in the app
  repositories until the user says so.
- WEB-03 result: the canvas changed only by the new Shifa app icon (charcoal gradient for the doctor app, teal for the
  patient app, the white Shifa mark) replacing the "S" placeholder in `DaApp`, `DaAuth`, `DaProfile`, `DaVisit`, and
  `shifa-ds.css`'s `.sh-appicon`; new boards: the 4 Nov component audit (`Au4*`, 18) and the app icon explorations
  (`Ico*`, 3). The phone screens drawing the "S" (login header, About) take the new mark with WEB-07a/j.

## Open inputs from the user

- WEB-09: the cPanel SSH host, port, user and the folder of each site (doctor, admin); whether `doctor.shifa.uz` and
  `admin.shifa.uz` DNS already point at the cPanel server.
- WEB-05: where the session token lives on web (`localStorage` keeps doctors signed in across tabs and restarts;
  session storage signs them out when the tab closes).
