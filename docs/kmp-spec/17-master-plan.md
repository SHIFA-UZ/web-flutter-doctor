# 17 — Master plan: UX flows + KMP spec + backend contract, united

Written 2026-11-04 for the agent that implements the KMP doctor app (Claude Opus 5.5 or later). It unites the
three documents that already exist, verifies them against the live backend contract, and fixes the order of work.
Nothing in `01`–`16` or in `docs/ux-flows/` is repeated here; this file says **which parts to read for which
ticket, what changed since they were written, what the backend still lacks, and how to work**.

| Source | What it is | Written | Status on 2026-11-04 |
|---|---|---|---|
| `docs/ux-flows/` (146 screen files, 9 areas, `INDEX.md`) | **UI/UX migration doc**: behaviour per screen, every state, copy, what not to copy | 2026-10-24 | Untracked in git, never committed |
| `docs/kmp-spec/01`–`10`, `15`, `16`, `CLAUDE.kmp.md`, `strings/` | **Engineering spec**: architecture, per-feature data/API detail, 175 tickets, build order | 2026-10-24 | Untracked in git, never committed |
| `backend-kotlin-shifa/docs/api/openapi.json` (291 paths) and `docs/apps/doctor-app.md` | **Backend doc**: the live contract and what changed for this app after the backend's Phase 6 | 2026-11-01 / 2026-10-31 | Committed on backend `main` |
| `docs/backend-improvements.md` (new, this task) | **Backend gap register**: every flow the backend cannot serve yet, with the fallback the app ships meanwhile | 2026-11-04 | Untracked |

**Verification result:** every one of the 182 endpoint rows in `15-api-reference.md` exists in the OpenAPI file
with the same method. The backend has 34 doctor-side operations the spec never listed (§5). The error and session
contract changed after the spec was written (§1). 28 backend gaps remain (§3, detailed in
`docs/backend-improvements.md`); none blocks a milestone, each has a fallback.

## 0. Precedence

1. **Payloads:** the live backend (`https://api-dev.shifa.uz/v3/api-docs`, then a real call) > `15-api-reference.md` > the feature doc.
2. **Behaviour:** the flow file in `docs/ux-flows/<area>/<nn>-*.md` > the feature doc's section. The flow files were
   written after the feature docs by re-reading the code screen by screen; where they disagree, the flow file wins
   and the feature doc gets a one-line correction in the same commit.
3. **Looks:** the design canvas vendored in `docs/design/` (§8), never Figma and never the Flutter look. When a board
   lacks a state the flow file demands, build the smallest thing in the existing style and note "not in the design"
   in `docs/implemented/`.
4. **Sequencing and ticket IDs:** this file > `16-implementation-plan.md` > the feature docs' ticket lists.
5. **Workflow rules:** the workspace `CLAUDE.md` (one level above this repo) > `CLAUDE.kmp.md` > this file.

## 1. What changed since the spec was written (2026-10-24 → 2026-11-04)

Apply these on top of `CLAUDE.kmp.md`, `01` §3 and `15` §1. They come from `backend-kotlin-shifa/docs/apps/doctor-app.md`
and from the OpenAPI file; each was checked against the backend source on 2026-11-04.

| Topic | Spec says | Now | Effect on the app |
|---|---|---|---|
| Hosts | `https://shifa-doc-backend-mvp-production.up.railway.app` | Production `https://api.shifa.uz`; development `https://api-dev.shifa.uz` (deployed from backend `main`, seeded: `seed.doctor1@shifa.dev`, `seed.staff@shifa.dev`, password `Dev12345!`). The Railway host stays alive for store builds. | `ApiConfig.BASE_URL` = production. Debug builds get a Profile → Debug → "Development backend" switch, as the patient app has. All device testing runs against dev. |
| Error body | `{message}` or `{error}` (sometimes numeric) | `{error, message, status, code, requestId}`; `code` ∈ `VALIDATION, MALFORMED_BODY, BAD_REQUEST, UNAUTHORIZED, PAYMENT_REQUIRED, FORBIDDEN, NOT_FOUND, CONFLICT, RATE_LIMITED, SESSION_INVALID, SESSION_EXPIRED, TOKEN_INVALID, ACCOUNT_DISABLED, INTERNAL, UPSTREAM, UNAVAILABLE` (`shared/ErrorCode.kt`). Filters that write by hand send `{error:"<text>", code, requestId}`. | `ApiException.code` is the branch key; `message` is for logs. Show `requestId` in the error banner's detail. Never match English text. |
| Session errors | 401 ⇒ logout; 403 ⇒ logout only on three English strings or `/doctors/me*` Forbidden | **Every token the backend cannot accept is 401** (`TOKEN_INVALID`, `SESSION_EXPIRED`, `SESSION_INVALID`, `UNAUTHORIZED`). A missing token on a protected route is also 401. 403 is a role, tier or business-rule refusal (for example the video join window in `VideoController`), never a session problem. | CORE-05 simplifies: 401 ⇒ logout (if a token was sent); 403 ⇒ never logout, show the feature's own message; `ACCOUNT_DISABLED` ⇒ logout with reason. Keep the 30 s pre-flight expiry check. Clinic staff still get 403 on doctor-only routes. |
| Paging | `/notifications`, `/patients/{id}/documents`, `/patients/{id}/forms` return bare arrays | Each has a `/page` sibling (`page`, `size`, Spring `Page` shape, `sort` ignored) | Use the `/page` siblings from the start (NOTIF-02, DOCS-04, FORM-05). The bare-array versions stay for the Flutter build. |
| DTO fields | — | `TaskDto`: `doctorId`, `doctorName`, `appointmentId`, `patientTimeZone`. `PatientDocumentDto`: `uploadedByPatient`, `creatorType`, `appointmentId`, `canView`, `isSharedWithTeam`. `AppointmentDto`: `isVideoConsultation`, `signatureRequested`, `alreadySigned`, `documentIds`, `notes`, `canCancel`, `canReschedule`, `isLocked24h`, `holdExpiresAt`, `paymentStatus`, `paymentBadge`, `serviceId`. Calendar `EntryDto`: `locationId`, `locationLabel`, `paymentStatus`, `signatureRequested`, `patientSignedAt`, `briefingStatus`, `attachmentCount`. | Model them in M3–M5; drop the Flutter heuristics they replace (video by `location.contains("video")` on the appointment, document ownership by name). |
| Appointment status | 6 values | 9 on the wire: `REQUESTED, CONFIRMED, IN_PROGRESS, CANCELLED, COMPLETED, NO_SHOW, PENDING_PAYMENT, EXPIRED, PAYMENT_FAILED`. Payment status: `NOT_REQUIRED, PENDING, PAID, FAILED, REFUNDED, PAY_IN_CLINIC`. | Sealed enums with an `UNKNOWN` member; `PENDING_PAYMENT`/`EXPIRED`/`PAYMENT_FAILED` render as "awaiting payment" / muted rows (a released slot). |
| Tier | `subscription.tier` only | `GET /doctors/me` returns `subscription: {tier, features: [...]}` where `features` are the backend `SubscriptionFeature` names (`CALENDAR, DOCUMENTS, CHAT, PATIENT_MANAGEMENT, BASIC_ANALYTICS, VIDEO_CONSULTATION, AI_NOTES, SPEECH_TO_TEXT, PATIENT_BRIEFING, ASK_SHIFA_AI, REMOTE_CARE_TASKS, ADVANCED_ANALYTICS, DIFFERENTIAL_DIAGNOSIS`). New: `GET /doctor/subscription/me` (`planCode, status, currentPeriodEnd, gracePeriodEndsAt, suspendedAt`), `GET /doctor/subscription/plans`, `POST /doctor/subscription/start`. | `FeatureGate` checks `features` (fallback: tier table) and is **unresolved** until the profile loads: hide gated entries, no fail-open (decision D-03). SUB-02 shows renewal date and plan cards from the new endpoints. |
| Calendar query | `day`, `doctorId` | also `locationId` | CAL-1 passes the active location. |
| Clinic membership roles | `OWNER, CLINIC_ADMIN, RECEPTIONIST, DOCTOR, NURSE` | also `STAFF` | Enum with `UNKNOWN`; treat `STAFF` like `RECEPTIONIST` for client-side courtesy gates (server decides). |
| Profile name / DOB | "never saved" (bug 2) — backend acceptance UNVERIFIED | `PATCH /doctors/me/profile` takes `ProfileDto` and applies `firstName`, `lastName`, `dob`, `profession`, `timeZone` (checked in `DoctorController.patchProfile`). The Flutter bug was client-side. | PROF-03 needs no backend change. |
| Registration e-mail OTP | "collected but never verified" (UNVERIFIED on the backend) | Confirmed: `RegisterRequest` has no `emailOtp` field, the code is never checked. | Gap BE-03; decision D-04. |
| Reference implementation | patient app branch `shifa2.0` | The patient app is fully KMP on `main` (`MARKETING_VERSION` 1.2.4, package `shifa.uz`). | Copy `gradle/libs.versions.toml`, `data/api/*`, `data/debug/DemoBackend.kt`, `ProvideAppLocale`, the CI workflows and the test setup from the patient app's `main`, then rename the package. Read `apps-flutter-patient/docs/implemented/README.md` first: it records the pitfalls (CMP string escaping, kotlinx-datetime skew on iOS, no Ktor Logging plugin, `multiplatform-settings` keys). Copy patterns, never share modules (workspace rule). |
| Backend stack | Spring Boot 3 | Spring Boot 4, Kotlin 2, modular monolith by feature; changes are additive only and go through the backend repo on their own `feature/` branch with a PR. | Backend gaps are *requested* in `docs/backend-improvements.md`, never worked around silently. |
| Design source | A Figma file to be shared (`<FIGMA_FILE>`), tokens and components pages | A Claude Design canvas: 11 doctor-app modules, 9 flow diagrams, component specimens, a 30 Oct 2026 audit, the Shifa design-system tokens (208 custom properties) and stylesheet (40 `.sh-*` component blocks). Vendored verbatim in `docs/design/` because the design-system artifact it installed is no longer accessible. | Every "Figma" instruction in `CLAUDE.kmp.md`, `04` and `16` now means `docs/design/` (§8). D-01 is resolved: no visual ticket is blocked. |

## 2. How to work (the loop for every ticket)

**Step 0, once:** the spec folders are not in git. The first PR of the rewrite is docs-only: `docs/kmp-spec/`,
`docs/ux-flows/`, `docs/backend-improvements.md`, this file. (`docs/ux-flows.zip` and `docs/example/` are not part of
it; `docs/example/` is a copy of the patient app's spec kept for comparison and can be deleted.)

**Branches (decision D-00, default):** one long-lived integration branch `doc-app2.0` from `main`, as `CLAUDE.kmp.md`
says, **plus** one short branch per ticket group off `doc-app2.0` (`feature/kmp-m1-auth`, `fix/kmp-cal-7`), merged into
`doc-app2.0` by a PR the user reviews. This satisfies the workspace rule "no commit without approval, every change
through a PR" without losing the single rewrite branch. `main` keeps the Flutter app until REL-05.

**Per ticket:**

1. Read, in this order: workspace `CLAUDE.md` → `CLAUDE.kmp.md` (it becomes `CLAUDE.md` on `doc-app2.0` in CORE-01)
   → `docs/implemented/README.md` → this file's milestone table (§4) → the ticket row in `16` → the ticket text at the
   end of the feature doc → the flow files named in §4 → the feature doc sections → `15` for the endpoints →
   `docs/backend-improvements.md` for the gaps named in §4 → the design board and tokens named in §8.
2. Check every endpoint the ticket uses against `https://api-dev.shifa.uz/v3/api-docs` and one real GET as the seed
   doctor. Write the request/response shapes you observed into the repository test fixtures.
3. Build bottom-up: DTOs (`data/model`) → repository returning `Result<T>` → `DemoBackend` handler for every endpoint
   → repository test with Ktor `MockEngine` → `UiState` + `ViewModel` + `ViewModelTest` → `XxxScreenContent` + `XxxScreen`
   → previews → `XxxScreenshotTest` → strings in `en`, `uz`, `ru`.
4. For a flow that depends on a backend gap: build the **target** UI from the flow file, wire it to the proposed
   endpoint in `DemoBackend` only, and ship the **fallback** named in `docs/backend-improvements.md` against the real
   backend. Never block the ticket on the gap and never invent a workaround that is not in the register.
5. Run only what the ticket touched: `./gradlew ktlintFormat :composeApp:testDebugUnitTest --tests '<package>.*'`,
   `recordRoborazziDebug` for the new screenshots, `assembleDebug`, `linkDebugFrameworkIosSimulatorArm64`. Run the
   screen on the Android emulator and the iOS simulator against `api-dev` as `seed.doctor1@shifa.dev`.
6. Update `docs/implemented/README.md` (status row, area file, one log line), correct the spec or flow file where
   reality differed, append to `docs/backend-improvements.md` if you found a new gap.
7. Report: what changed, which flow files and design boards it covers, what is not built and why (gap id or decision
   id), the targeted test output, screenshots. Then **stop and ask whether to commit.** Commits are the user's,
   Conventional Commits, no agent attribution, never pushed to `main`.

**Prompt the user hands you (template):**

```
Implement <TICKET> from docs/kmp-spec/17-master-plan.md §4 (<milestone>).
Read the flow files and spec sections the §4 row names, the ticket text at the end of the feature doc,
15-api-reference.md for the endpoints, and docs/backend-improvements.md for <BE-xx>.
Verify the endpoints on https://api-dev.shifa.uz/v3/api-docs as seed.doctor1@shifa.dev.
Build data → ViewModel → screen → tests. Ship the fallback for <BE-xx>. Respect the "Fix" line.
End with the report and the commit question; do not commit.
```

**Definition of done** = `16` "Definition of done" + the flow conventions from `docs/ux-flows/00-README.md`: skeletons
that match the layout, top banners (never SnackBars), destructive confirmations with the red action on the right,
practice/clinic/patient time zone as the flow file says, Russian-length copy, 48 dp targets, content descriptions,
nothing patient-identifying in logs. Every string key the flow file marks **NEW** is added to all three locales.

## 3. Decisions to take before the milestone that needs them

Defaults are what the plan assumes; the user confirms or changes them, and the answer is recorded in
`docs/implemented/README.md` with the date and "the user's call".

| ID | Decision | Needed by | Default |
|---|---|---|---|
| D-00 | Branch model | before CORE-01 | `doc-app2.0` + short PR branches per ticket group (§2) |
| D-01 | Design source | CORE-02, CORE-03 | **Resolved 2026-11-04:** the Claude Design canvas vendored in `docs/design/` (§8). Nothing is blocked. The one open design question is whether the primary fill stays `teal-50` with white labels (fails WCAG AA, recorded in the audit as a brand choice); default: keep it |
| D-02 | Clinic staff on mobile | AUTH-08, SHELL-02 | Yes, 4-tab shell (`01/18`) |
| D-03 | Tier gating while the profile loads | SHELL-04, SUB-01 | Hide gated entries until resolved; gate on `subscription.features`, never fail open |
| D-04 | Registration e-mail code | AUTH-06 | Do not show the code dialog until BE-03 ships (a code nobody checks is a lie to the user); keep `send-email-otp` wiring ready |
| D-05 | PDFs | PDF-01, FORM-03, TP-05 | Request backend rendering (BE-01); client `expect/actual` fallback only for the appointment summary and 025-2, with `ru` |
| D-06 | Maps | PROF-07, LOC-2 | Address fields + coordinates first; native map behind `expect/actual` in a later pass; no Google key in the app (BE-15) |
| D-07 | Dead features | VIDEO-03, CHAT-03, NOTIF-04, AUTH-10 | Not ported: waiting room, typing indicator, doc-access approve/reject in the inbox, phone OTP |
| D-08 | Web target | after M2 | Flutter web keeps deploying from `main`; no desktop layouts in the KMP app |
| D-09 | PIN length | LOCK-01 | 4–6 digits, user chooses, explicit submit, salted hash (flow `01/11` recommends a fixed 6; keep 4–6 unless the user prefers 6) |
| D-10 | Version offsets | REL-01 | Read the last Play `versionCode` and TestFlight build number from the Actions history before CORE-01 |
| D-11 | Delete account for doctors | SET-01 (Privacy screen) | Store policy requires it; UI built, action goes to "contact support" until BE-02 ships |
| D-12 | Video tier gate | VIDEO-01 | Gate on `VIDEO_CONSULTATION` in `features` (backend declares it PRO); hidden while unresolved |
| D-13 | Push language and per-device tokens | PUSH-01 | Inherit the patient app's asks (BE-05, BE-06); single token until then |
| D-14 | Walk-in / patient-first booking | CAL-7 | Slot-first only (as today); "Book for this patient" from the patient record opens the calendar with the patient pre-filled (no new backend) |

## 4. Milestones: tickets ↔ flow files ↔ endpoints ↔ gaps

Sizes and dependencies are in `16`; this table adds the flow files, the verified endpoint groups (`15` §2.x) and the
gaps. "New" marks endpoints the spec did not know (full list in §5). Flow paths are relative to `docs/ux-flows/`.

### M0 — Foundation (CORE-01…09)

| Ticket | Flow files | Spec | Endpoints | Gaps / notes |
|---|---|---|---|---|
| CORE-01 skeleton, CI | — | `CLAUDE.kmp.md`; `01` §1 | — | Copy from the patient app's `main`, not `shifa2.0`. Fill the `<angle bracket>` values in `CLAUDE.kmp.md`. D-00, D-10 |
| CORE-02 tokens, CORE-03 UI kit | `01/19` (banners, offline strip), `01/15` (mini toggle), `04/02` (agenda row chips), `09` README §Layout (card rows) | `04`; `docs/design/README.md` | — | Tokens: patient app `ui/theme/` + every `--*` property of `docs/design/shifa-ds.css` without a counterpart. Kit: one composable per `.sh-*` block + the doctor-only components in `AuCodebase` + every component a flow file names in §3; states from the `…States` boards |
| CORE-04 strings | every flow's §11 (**NEW** keys) | `strings/README`, `01` §8 | `PUT /doctors/me/language` | Add the 41 keys missing everywhere, 92 `ru`, 10 `uz`, and the NEW keys from the flow files' §11 |
| CORE-05 HTTP client | `01/14` (session end), `01/19` | `01` §3, `15` §1, **§1 of this file** | all | New 401/403 rule; `code` branching; `requestId`; dev-host switch; SSE helper |
| CORE-06 storage, session | `01/12`–`01/14` | `01` §4–5 | — | Hashed PIN (bug 1) |
| CORE-07 native spikes | `05/10`–`05/11` (Daily), `07/04` (AAC), `05/13` (PDF), `08/04` (map) | `05` §9, `07` §7, `08` §12 | `POST /video/token`, new `GET /video/room/{roomName}` | Write the outcomes into `CLAUDE.md`; feeds D-05, D-06 |
| CORE-08 DemoBackend | — | `CLAUDE.kmp.md` | all | Fixtures from real dev responses; one handler per endpoint incl. the proposed shapes of BE-xx |
| CORE-09 utils | `04` README §4.2 (time rules), `09` README §Money | `03` §4, `09` §8 | — | `PracticeClock`, `Money.format` (UZS whole units, thin-space grouping), `PasswordPolicy` 8–128 |

### M1 — Launch & auth (AUTH-01…09, LOCK-01…02)

| Ticket | Flow files | Spec | Endpoints (`15` §2.1) | Gaps / notes |
|---|---|---|---|---|
| AUTH-01 splash | `01/01` | `01` §1.4, §2.9 | `GET /doctors/me` or `/me/profile` to validate the token; `GET /public/config` | **BE-04** force-update/maintenance fields: build the two frames, show them only when the fields exist |
| AUTH-02 login | `01/02`, `01/15` | `01` §2.1 | `POST /auth/login` | new `POST /auth/check-identifier` for the identifier hint |
| AUTH-03 e-mail OTP login | `01/03`, `01/04` | `01` §2.2 | `send-login-otp`, `verify-email-otp` | — |
| AUTH-04 forgot password | `01/03`, `01/04`, `01/05` | `01` §2.4 | `send-forgot-password-otp`, `forgot-password-reset` | BE-26 (pre-verify code, Low): until then the reset screen says "code wrong" and offers "Back to code" |
| AUTH-05 invite key | `01/06` | `01` §2.5 | `verify-key` | — |
| AUTH-06 create account 1 | `01/07`, `01/09` | `01` §2.6 | `check-existing-patient`, `send-email-otp`, `register` | **BE-03** (D-04); new `check-existing-doctor` |
| AUTH-07 create account 2 | `01/08`, `08/05` | `01` §2.6 | `register` (`timeZone`), `PATCH /doctors/me/profile` once, `GET /public/clinics`, `/public/professions` | Timezone becomes an explicit confirmation (flow `01/08`) |
| AUTH-08 receptionist | `01/10` | `01` §2.7–2.8 | `register-clinic-staff` | C-4 (`emailSentTo` always set) |
| AUTH-09 logout | `01/14`, `08/15` | `01` §2.10 | `PUT /doctors/me/fcm-token ""` | — |
| LOCK-01, LOCK-02 | `01/11`, `01/12`, `01/13`, `08/11` | `01` §7 | — | D-09; bug 1 |

### M2 — Shell, profile, settings (SHELL-01…05, PROF-01…08, SVC-01…02, SUB-01…02, SET-01…03)

| Ticket | Flow files | Spec | Endpoints (`15` §2.2) | Gaps / notes |
|---|---|---|---|---|
| SHELL-01…03, 05 | `01/16`, `01/17`, `01/19`, `01/20` (landing rules only) | `01` §6 | `/messages/unread-count`, `/notifications/unread/count` | Staff deep-link tab bug (flow `01/20` §6) fixed here |
| SHELL-02 staff shell | `01/18` | `01` §6.1 | `GET /me/profile`, `GET /me/clinics` | D-02 |
| SHELL-04, SUB-01 gating | `07/16`, `08` README §4.2 | `01` §5.3, `08` §5 | `GET /doctors/me` (`subscription.features`) | D-03 |
| PROF-01…04, PROF-08 | `08/01`, `08/02`, `08/03`, `08/07` | `08` §1 | `GET /doctors/me`, `PATCH /doctors/me/{profile,contact,billing,settings}`, `POST /doctors/me/{photo,certificate}` | Name/DOB persist (verified, §1). Staff read-only variant |
| PROF-05, PROF-06 | `08/02` (profession sheet), `08/05`, `08/06` | `08` §4, §1.5 | `GET /public/professions?lang=` | Timezone change warning dialog is NEW UI |
| PROF-07 location | `08/03`, `08/04` | `08` §3 | `PATCH /doctors/me/profile` (8 location keys, lat/lng) | **BE-15** geocoding proxy; D-06 |
| SVC-01, SVC-02 | `08/08` | `08` §2 | `/doctors/me/services`, `/service-groups`, `/locations` | Status-checked writes (bug 17) |
| SUB-02 subscription | `08/09` | `08` §1.7, §5.3 | `POST /doctor/subscription/checkout`, `POST /doctor/payments/stripe/connect/onboard`, new `GET /doctor/subscription/me`, `GET /doctor/subscription/plans` | BE-18 (PREMIUM checkout, Connect state) |
| SET-01…03 | `08/10`, `08/11`, `08/12`, `08/13`, `01/15` | `08` §1.8, §6 | `PATCH /doctors/me/settings`, `PUT /doctors/me/language`, `POST /auth/reset-password` | **BE-02** delete account/export (D-11); BE-16 legal URLs; BE-17 notification preferences (link to OS settings until then); new `POST /doctors/me/test-notification` for the Debug menu |

### M3 — Calendar & schedule (TZ-1…2, CAL-1…10, SCHED-1…2, LOC-1…2)

| Ticket | Flow files | Spec | Endpoints (`15` §2.4) | Gaps / notes |
|---|---|---|---|---|
| TZ-1, TZ-2 | `04/03`, `04` README §4.2, `08/05` | `03` §4 | — | Device time only for the mismatch banner (bug 9) |
| CAL-1…4 | `04/01`, `04/02`, `04/04`, `04/15` | `03` §1, §5–7 | `GET /calendar?day&locationId&doctorId` | **BE-09** `isVideo` on entries: fallback heuristic in one mapper function, marked `ponytail:`; `briefingStatus`, `attachmentCount` are new fields to show |
| CAL-5 N-day grid | `04` README §3 (not designed on phone) | `03` §1.9 | — | Tablet-only; build last in M3 or defer to a tablet pass (user's call) |
| CAL-6 slot details | `04/05`, `05/01`, `05/03` | `03` §1.7 | `GET/DELETE /appointments/{id}`, `/change`, `/notify-payment-reminder` | **BE-10** cancel reason and no-show: reason chips hidden, no-show action hidden until shipped; use `canCancel`/`canReschedule` from the DTO |
| CAL-7 booking | `04/06`, `06/02` (nested create patient) | `03` §1.6, §1.10 | `POST /schedule/book` (`BookReq`), `GET /patients/for-assignment` | D-14; past-slot-today rule from `04` README §4.2 |
| CAL-8 blocks | `04/09` | `03` §1.8 | `POST /schedule/blocks`, `/overlapping-count`, `DELETE /blocks/{id}`, new `GET /schedule/blocks?day&doctorId` | "Whose calendar" prompt for colleagues (flow `04/09`) |
| CAL-9 deep links | `01/20`, `07/10` | `03` §1.2 | — | — |
| CAL-10 staff calendars | `04/14`, `09/06` | `03` §1.12, `09` §5 | `GET /calendar?doctorId`, `resourceDoctorId` on book/blocks | C-1 (staff authorisation for change/cancel) |
| SCHED-1, SCHED-2 | `04/10`, `04/11`, `04/12` | `03` §2 | `/schedule/rules`, `/valid-range`, `/validity-periods`, `/date-specific` (+ new `PUT /date-specific/{id}`, `PATCH /valid-until`) | BE-27 (rolling validity, Low) |
| LOC-1, LOC-2 | `04/13`, `08/04` | `03` §3 | `/doctors/me/locations` | BE-15, D-06 |

### M4 — Patients, documents, forms (PAT-01…07, DOCS-01…05, FORM-01…05, CLIN-01…02)

| Ticket | Flow files | Spec | Endpoints (`15` §2.6) | Gaps / notes |
|---|---|---|---|---|
| PAT-01…03 | `06/01` | `06` §1, §6–8 | `GET /patients?pageable` | Server paging, persisted favourites (bug 15); loading + error states (flow `06/01` §12) |
| PAT-04 create | `06/02` | `06` §1.6 | `POST /patients` | Sheet stays open until the response (409 keeps the input) |
| PAT-05…07 | `06/03`, `06/04`, `06/05`, `06/11` | `06` §2 | `GET/PATCH /patients/{id}`, `/appointments`, `/create-account`, `/send-test-sms`, `/prophylaxis/settings` | Edit-details variant is NEW UI (flow `06/05`); chronic warning once per session |
| DOCS-01…05 | `06/06`, `06/07`, `06/08` | `06` §4, §9 | `/patients/{id}/documents/page`, `POST documents`, `/download`, `/request-access` | BE-21 (delete/rename, HEIC; Low); 27 categories incl. `FORM_025_2` chip |
| FORM-01…05 | `06/09`, `06/10` | `06` §3, §7 | `/patients/{id}/forms/page`, `PUT /forms/{formId}`, `/request-signature`, `/link-document`, new `DELETE /forms/{formId}`, `GET /icd10/search`, new `POST /ai/suggest-icd10` | **BE-01** 025-2 PDF (D-05); BE-22 `createdAt` (Low); the signature flow polls 10 s only while requested |
| CLIN-01, CLIN-02 | `03/22` | `02` §5 | `/clinical-engine/**`, new `POST /clinical-engine/record-usage` | — |
| (pulled forward) AI-03, AI-04, AI-05, AI-06 | `03/23` | `02` §3.4–3.5, §7.2–7.3 | `POST /ai/transcribe` | Needed by FORM-04's speech fields; build the recorder, the player and the speech field here, the rest of M10 later |

### M5 — Appointments, scribe, dental, video (APPT-01…08, SCRIBE-01…02, DENTAL-01…04, VIDEO-01…03, PDF-01…02, AI-11, TP-06)

| Ticket | Flow files | Spec | Endpoints (`15` §2.4, §2.5) | Gaps / notes |
|---|---|---|---|---|
| APPT-01, APPT-02, APPT-06 | `05/01`, `05/02` | `05` §1, §2.2, §7 | `GET /appointments/{id}`, `/briefing`, `/briefing/retry`, `/request-signature` | Use `isVideoConsultation`, `paymentStatus`, `documentIds` (bug 10) |
| AI-11, APPT-03, APPT-04, APPT-05 | `05/04`, `05/05` | `05` §2, `02` §7.1 | `/consultation-notes`, `/draft-notes`, `/scribe-status`, `POST /ai/draft/{id}/confirm|discard`, new `GET /ai/draft/{id}` | Notes autosave 2 s; staff never enter the workspace |
| SCRIBE-01, SCRIBE-02 | `05/06` | `05` §2.2–2.3 | `POST /consultations/upload-recording` (202/429) | PRO gate on `AI_NOTES` |
| DENTAL-01…04, TP-06 | `05/07`, `05/08`, `05/09`, `09/18` | `05` §4–5, `09` TP-06 | `/dental-documentation`, `/treatment-plans/{id}/fulfillment-candidates`, `/fulfill`, new `POST /clinics/{id}/appointments/{apptId}/visit-charges`, new `POST /treatment-plans/{id}/payments` | Post ticks and payments **when made**, not only at End (the new endpoints allow it); **BE-13** idempotency key to make retries safe; mixed currency handled (bug 14) |
| APPT-07 end visit | `05/12` | `05` §2.3 | `PUT /appointments/{id}/complete` (`visitCharges[]`, `awaitingScribe`) → `CompleteAppointmentResponse` | "Completed, summary pending" state with retry (bug 5) |
| APPT-08 | `06/10` (embedded mode) | `05` §5 | — | — |
| VIDEO-01…03 | `05/10`, `05/11` | `05` §3 | `POST /video/token` (no-401), new `GET /video/room/{roomName}` | Pre-join screen is NEW UI; join window −5 min … end +15 min to match the backend; D-12; BE-28 (coded token errors, Low); D-07 (no waiting room) |
| PDF-01, PDF-02 | `05/13` | `05` §6, §9 | — | **BE-01**; fallback renderer with `ru`; drop the never-populated sections (flow `05` README §4) |
| (new) follow-up booking | `05/14` | `03` §1.6 | `POST /schedule/book` | Reuses CAL-7's sheet with the patient pre-filled; BE-20 `followUpOf` (Low) |

### M6 — Home & analytics (HOME-01…05, ANALYTICS-01…03)

| Ticket | Flow files | Spec | Endpoints (`15` §2.3, §2.4) | Gaps / notes |
|---|---|---|---|---|
| HOME-01…03 | `02/01`, `02/02`, `02/08` | `02` §1 | `GET /calendar` (today), `GET /doctors/me` | Start window: 15 min in clinic, 5 min video; refresh cooldown 60 s |
| HOME-04 | `02/03`, `02/04` | `02` §1.5–1.6 | `GET /notifications/page`, `GET /tasks` | — |
| HOME-05 | `02/07` | `02` §1.9 | local list | Touch trigger (bug: unreachable on phones) |
| ANALYTICS-01…03 | `02/05`, `02/06` | `02` §2 | `/doctor/analytics/{overview,appointments-trend?days,consultation-types,engagement,sms-usage}` | BE-19 `from/to` on overview/types (Low): label KPIs "Today" until then; PREMIUM gate on `ADVANCED_ANALYTICS` |

### M7 — Chat (CHAT-01…07)

| Ticket | Flow files | Spec | Endpoints (`15` §2.8) | Gaps / notes |
|---|---|---|---|---|
| CHAT-01…03, 07 | `07/01`, `07/02`, `07/03`, `07/07` | `07` §1 | `/messages/conversations`, `/conversations/{id}`, `/start`, `/read`, `/send`, `/search`, `/unread-count` | **BE-14** cursor/paging: foreground-only 5 s polling until then; open at the newest message; Send disabled while sending (bugs 16, flow `07/02`–`07/03`) |
| CHAT-04…06 | `07/04`, `07/05`, `07/06` | `07` §1.3–1.4 | `POST /messages/upload-attachment` | Normalise every attachment URL (bug 16); AAC m4a from AI-03 |

### M8 — Notifications & push (NOTIF-01…04, PUSH-01…03)

| Ticket | Flow files | Spec | Endpoints (`15` §2.9) | Gaps / notes |
|---|---|---|---|---|
| NOTIF-01…03 | `07/08`, `02/03` | `07` §2 | `GET /notifications/page`, `PUT /{id}/read`, `/read-all`, `/unread/count` | `NotificationDto` already carries `patientName`, `appointmentStartAt`, `appointmentId`, `patientId`, `documentId`, `taskId`, `treatmentPlanId`, `patientFormId`, `documentAccessRequestId` (the spec's ask is mostly satisfied; a chat conversation id and a care-partnership id are still missing, see BE-06); C-7 (type list) |
| NOTIF-04 | — | `07` §2.1 | `/document-access-requests/{id}/approve|reject` | D-07: not ported |
| PUSH-01…03 | `07/09`, `07/10`, `01/20` | `07` §2.5 | `PUT /doctors/me/fcm-token`, new `POST /doctors/me/test-notification` | **BE-05** per-device tokens, **BE-06** localised/data-only pushes; BE-17 preferences (Medium, product first); pre-prompt sheet and "notifications off" banner are NEW UI |

### M9 — Remote-care tasks (TASK-01…05)

| Ticket | Flow files | Spec | Endpoints (`15` §2.10) | Gaps / notes |
|---|---|---|---|---|
| TASK-01…05 | `07/11`–`07/16` | `07` §3 | `/tasks`, `/tasks/templates`, `/tasks/{id}`, `/check-ins`, `PATCH /cancel` | Use `patientTimeZone`, `doctorName`, `appointmentId` from `TaskDto`; PREMIUM gate on `REMOTE_CARE_TASKS` with the upgrade sheet (`07/16`); BE-25 thresholds/templates (Low); loading and error states (flow `07/11` §12) |

### M10 — Shifa AI & Intelligence (AI-01…10, INTEL-01…10)

| Ticket | Flow files | Spec | Endpoints (`15` §2.5) | Gaps / notes |
|---|---|---|---|---|
| AI-01, AI-02 | `03/03`, `03/06` | `02` §3.2, §8 | `POST /ai/morning/stream` (SSE, `DoctorAiRequest` with `conversationId`), `POST /ai/stream`, new `POST /ai/speak`, new `POST /ai/patient-briefing/{patientId}`, `/ai/visit-facts/**`, new `GET /ai/visit-facts/patient/{id}/timeline`, new `GET /ai/clinic-patterns`, new `GET/DELETE /ai/memories` | **BE-08** standardised SSE errors (`event: error` already exists; needs `ErrorCode` values and `requestId`); route AI calls through the shared client so 401 logs out (flow `03` README §4) |
| AI-07, AI-08 (AI-04 was built in M4) | `03/04`, `03/05`, `03/06` | `02` §3.3–3.5, §3.10 | `POST /ai/transcribe` | No wake word, no live caption on mobile |
| AI-09, AI-10 | `03/20`, `03/21`, `06/11` | `02` §6, §3.2 | `patient-briefing`, `visit-facts/commit`, `timeline` | Briefing sheet is shown on mobile (never was) |
| INTEL-01…04 | `03/01`, `03/02`, `03/03` | `02` §4 | — (local session store) | Encrypt and cap the local history |
| INTEL-05…08 | `03/07`–`03/19` | `02` §4.4–4.8 | `draft/{id}/confirm|discard`, `/clinical-engine/shared/{type}` | **BE-07** hint codes: INTEL-10 localiser is the stop-gap; booking confirmation is backend-owned (`workflowId`) |
| INTEL-09, INTEL-10 | `03/04`, `03/16` | `02` §3.4–3.5, §0 | — | — |

### M11 — Clinic workspace, finance, treatment plans (CLINIC-01…09, FIN-01…07, TP-01…05)

| Ticket | Flow files | Spec | Endpoints (`15` §2.11) | Gaps / notes |
|---|---|---|---|---|
| CLINIC-01…04 | `09/01`, `09/20` | `09` §1, §6 | `GET /me/clinics`, `/clinics/{id}/overview-stats` | Workspace unlocked by membership, not tier; BE-24 (self-service clinic settings, Low; product) |
| CLINIC-05 | `09/02`, `09/03` | `09` §2, §3.8 | `/members`, `PATCH /members/{id}/revenue-share`, `PATCH /finance-settings` | C-2, C-5 (role rules) |
| CLINIC-06 | `09/05`, `06/03` (clinic context) | `09` §2 | `GET /clinics/{id}/patients?q&pageable` | Server paging exists |
| CLINIC-07 | `09/04` | `09` §2 | `/treatment-plans/catalog-items`, `/plan-services` | C-2 |
| CLINIC-08 | `09/19` | `09` §2 | `/clinics/{id}/invitations` | BE-24 resend (Low) |
| CLINIC-09 | `09/06`, `04/14` | `09` §5 | shared with CAL-10 | — |
| FIN-01, FIN-02 | `09/07` | `09` §3.1–3.2 | `/finance/dashboard?from&to` | Clinic time zone everywhere |
| FIN-03 | `09/09`, `09/10` | `09` §3.3–3.4 | `/finance/payments?page&size`, `/finance/records?status&page&size`, new `GET/PATCH /finance/records/{recordId}` | **BE-11** `from/to`: client walks pages with a cap for the month filter, marked `ponytail:` |
| FIN-04 | `09/08` | `09` §3.5 | `/finance/appointment-ledger?from&to&page&size`, `POST /finance/payments` | BE-23 `doctorId` filter (Low) |
| FIN-05 | `09/11` | `09` §3.7 | `/finance/installment-items`, `/mark-paid`, `/notify`, new `GET /installment-plans/{planId}`, `/by-treatment-plan/{tpId}` | BE-23 `lastNotifiedAt` (Low) |
| FIN-06, FIN-07 | `09/12`, `09/13` | `09` §3.6, §3.10 | `/finance/doctor-earnings?from&to` | CSV client-side; PDF per BE-01 |
| TP-01…03 | `09/14`, `09/16` | `09` §4.1, §4.3, §6–8 | `/treatment-plans?clinicId&patientId&status&planKind&q`, `/detail`, `/visits`, new `GET /treatment-plans/{id}` | Invalidate on the refresh bus (bug 8) |
| TP-04 wizard | `09/15` | `09` §4.2 | `POST /treatment-plans`, `/lines`, `/lines/append`, `/link-appointments`, `/book-slots`, `/free-slots`, `/doctor-locations`, `POST /finance/installment-plans` | **BE-12** compose: resumable `SaveStep` machine until then; installment-sum mismatch blocks activation (bug 8) |
| TP-05 PDF | `09/16` | `09` §4.4 | — | **BE-01** |
| (new) status & edit | `09/17` | `09` §4.1 | `PATCH /treatment-plans/{id}/status`, `PATCH /treatment-plans/{id}` (title, diagnosis, notes, reminder, doctors; in `15` §2.11 but unused by Flutter), `/lines/append` | Line-level qty/price edit has no endpoint (BE-24, Low); C-2 |

### M12 — Care partnerships, reports, legal (CARE-01…03, REPORT-01, LEGAL-01…02)

| Ticket | Flow files | Spec | Endpoints (`15` §2.7) | Gaps / notes |
|---|---|---|---|---|
| CARE-01…03 | `06/12`, `06/13`, `06/14` | `06` §5 | `/care-partnerships/**` (`accept`, `decline`, `cancel`, `complete`, `progress`, `partners/search?limit`) | C-8 |
| REPORT-01 | `08/14` | `08` §7 | analytics + `sms-usage?from&to` | Reuses M6 composables |
| LEGAL-01, LEGAL-02 | `08/13` | `08` §8 | — | **BE-16**; About screen ships with links to the public URLs; contract row hidden |

### M13 — Release (REL-01…05)

As in `16`. Add: the docs-only PR from §2 step 0 must already be merged; `docs/backend-improvements.md` status column
updated; `main` receives `doc-app2.0` by one PR the user opens.

## 5. Endpoints the backend has that the spec does not list

All 34 exist in `openapi.json` (2026-11-01); the count excludes the two static file routes and the three care-partnership
actions that `15` lists in one combined row. Adopt the ones in the first group; the second group is informational.

**Adopt in the tickets named:**

| Endpoint | Ticket | Use |
|---|---|---|
| `GET /notifications/page`, `GET /patients/{id}/documents/page`, `GET /patients/{id}/forms/page` | NOTIF-02, DOCS-04, FORM-05 | Server paging instead of bare arrays |
| `GET /doctor/subscription/me`, `GET /doctor/subscription/plans`, `POST /doctor/subscription/start` | SUB-02 | Renewal date, plan cards; `start` for non-Stripe gateways (confirm, BE-18) |
| `POST /auth/check-identifier`, `POST /auth/check-existing-doctor` | AUTH-02, AUTH-06 | Identifier hints; duplicate-doctor check before registration |
| `POST /doctors/me/test-notification` | PUSH-02 (Debug menu) | Real push from the backend in debug builds |
| `GET /schedule/blocks?day&doctorId`, `PUT /schedule/date-specific/{id}`, `PATCH /schedule/valid-until` | CAL-8, SCHED-2 | Blocked details without scanning the calendar; edit a date-specific rule in place |
| `GET /video/room/{roomName}` | VIDEO-01 | Room state before joining (feeds the pre-join screen) |
| `GET /ai/draft/{id}`, `POST /ai/suggest-icd10` | APPT-03, FORM-04 | Reload a draft; ICD suggestions from free text |
| `POST /clinics/{id}/appointments/{apptId}/visit-charges`, `POST /treatment-plans/{id}/payments` | DENTAL-03, TP-06, APPT-07 | Post charges and chair-side payments as they happen |
| `GET /treatment-plans/{id}` | TP-02, TP-03 | Summary without `/detail` (`PATCH /treatment-plans/{id}` for header edits is already in `15` §2.11) |
| `GET /clinics/{id}/finance/installment-plans/{planId}`, `.../by-treatment-plan/{tpId}` | FIN-05, TP-03 | Installment schedule on the plan detail |
| `GET /clinics/{id}/finance/records/{recordId}`, `PATCH .../records/{recordId}` | FIN-03 | Open and edit one record |
| `POST /ai/speak`, `POST /ai/patient-briefing/{patientId}`, `GET /ai/visit-facts/patient/{id}/timeline`, `GET /ai/clinic-patterns`, `GET/DELETE /ai/memories`, `DELETE /ai/memories/{id}` | AI-01, AI-04, AI-09, AI-10, INTEL-05 | TTS, briefing from patient detail, clinical timeline card, patterns card, "what Shifa remembers" (new setting in `03/02`, user's call) |
| `POST /clinical-engine/record-usage` | CLIN-02 | Usage ranking for the chips |
| `GET /patients/{id}/forms/{formId}`, `DELETE /patients/{id}/forms/{formId}` | FORM-02, FORM-05 | Single form load; delete (flow `06/09` long-press) |

**Informational (do not call from the doctor app):** `POST /auth/create-patient-for-doctor` (patient-app flow),
`POST /ai/clinical-rag/reindex-patient/{id}` (admin/ops), `/patientdocuments/{patientId}/{filename}` and
`/{dir}/{filename}` (static file serving, reached through document URLs only), `/api/admin/**` (parked, doc 10),
`/api/patients/me/**`, `/api/payments/**`, `/api/tasks/my-tasks/**` (patient app), `/api/webhooks/**`.

## 6. Backend gap register (summary)

Full text, proposed shapes, fallbacks and the "confirm" questions are in `docs/backend-improvements.md`. Priority
means: **High** = store policy or a milestone's "done when" depends on it; **Medium** = a flow ships degraded;
**Low** = a nicety the flow file lists as a target.

| ID | Gap | Priority | First needed by |
|---|---|---|---|
| BE-01 | Server-rendered PDFs: appointment summary, 025-2 form, treatment plan | High | M4 FORM-03, M5 PDF-01, M11 TP-05 |
| BE-02 | Doctor account deletion and data export | High | M2 SET-01 (store policy) |
| BE-03 | Registration e-mail code actually verified | High | M1 AUTH-06 |
| BE-04 | Force-update and maintenance fields in `/public/config` | High | M1 AUTH-01 |
| BE-05 | Per-device push tokens for doctors | High | M8 PUSH-01 |
| BE-06 | Pushes in the doctor's language, structured `data` payload | High | M8 PUSH-02 |
| BE-07 | Intelligence tool hints and card fields as codes, not English | Medium | M10 INTEL-05 |
| BE-08 | Standardised SSE errors (`ErrorCode`, `requestId`) on `/ai/*stream` | Medium | M10 AI-01 |
| BE-09 | `isVideo` on calendar entries | Medium | M3 CAL-1 |
| BE-10 | Cancel with a reason; mark no-show | Medium | M3 CAL-6, M5 APPT-01 |
| BE-11 | `from/to` on finance payments and records | Medium | M11 FIN-03 |
| BE-12 | Transactional treatment-plan compose | Medium | M11 TP-04 |
| BE-13 | Idempotency keys on plan payments and visit charges | Medium | M5 DENTAL-03 |
| BE-14 | Chat cursor/paging (`since`) and push-driven refresh | Medium | M7 CHAT-03 |
| BE-15 | Reverse-geocoding proxy; stop shipping the Google key | Medium | M2 PROF-07 |
| BE-16 | Doctor-facing legal: terms/privacy URLs, own contract | Medium | M2 SET-01, M12 LEGAL-01 |
| BE-17 | Notification preferences | Medium (product first) | M8 |
| BE-18 | PREMIUM checkout confirmation; Stripe Connect onboarding state | Medium | M2 SUB-02 |
| BE-19 | `from/to` on analytics overview, types, engagement | Low | M6 |
| BE-20 | `followUpOf` on booking | Low | M5 |
| BE-21 | Document delete/rename/recategorise for doctors; HEIC conversion | Low | M4 |
| BE-22 | `createdAt` on forms | Low | M4 |
| BE-23 | Installment `lastNotifiedAt`; ledger `doctorId` filter | Low | M11 |
| BE-24 | Clinic self-service: resend invitation, edit/leave/delete/create clinic, line-level plan edits | Low (product first) | M11 |
| BE-25 | Task thresholds; template CRUD | Low (product first) | M9 |
| BE-26 | Pre-verify the forgot-password code | Low | M1 |
| BE-27 | Rolling schedule validity; bulk shift on timezone change | Low | M3 |
| BE-28 | Coded `/video/token` errors and an explicit join window | Low | M5 |

Already satisfied since the spec (do not request again): error codes and `requestId`; 401 for every token problem;
paged siblings; structured notification fields; `isVideoConsultation` on `AppointmentDto`; name/DOB on the profile
PATCH; `uploadedByPatient`/`creatorType`; plan payments and visit charges outside End; plan header edit; subscription
renewal date and plans; form delete; blocked-day list; the development backend with seed accounts.

## 7. Ordering notes

- **M0 in the `16` order.** The design is available (§8), so CORE-02 and CORE-03 run as planned; nothing waits. If a
  later board turns out incomplete, the data layer of that milestone (repositories, DTOs, `DemoBackend`, ViewModels,
  tests) is built first and the screen follows.
- **CORE-07 spikes in M1**, in parallel: Daily (Android SDK + Swift bridge), FCM/APNs, AAC record/play, PDF render,
  native map pin. Their outcomes settle D-05 and D-06.
- **AI-03/04/05/06 are built in M4** (speech field for forms) and reused by M5 scribe and M7 voice messages.
- **TP-01 is built at the start of M5** (DENTAL-03 needs it).
- **Two streams after M2** as `16` says: clinical (M3 → M4 → M5 → M6 → M10) and communication/clinic (M7 → M8 → M9
  → M11 → M12). One agent runs them sequentially in the `16` order.
- **Backend gaps are raised at the start of the milestone that first needs them**, not when the ticket arrives:
  BE-01…04 before M1 ends, BE-05/06 before M7, BE-07/08 before M10, BE-11/12/13 before M11. Each gap is implemented
  in `backend-kotlin-shifa` on its own `feature/` branch, additively, with the OpenAPI file regenerated, and
  `docs/backend-improvements.md` gets a "Done (date)" line, as the patient app's register does.

## 8. Design source

`docs/design/` holds a verbatim copy of the Claude Design canvas "Shifa Doctor App"
(https://claude.ai/code/artifact/c966c819-e044-4509-8e35-85d2c1092ef7, version `1793750633-3da4`, copied 2026-11-04):
`tokens.json` (generated from the patient app's `ui/theme/`), `shifa-ds.css` (the complete token set, 208 properties,
and the 40 `.sh-*` component blocks), `canvas.json` and the 51 boards. `docs/design/README.md` maps every board to its
flow area and tickets, and says how CORE-02, CORE-03 and the screen tickets consume it. Rules:

- The board is the source for looks; the flow file stays the source for behaviour and edge cases.
- Tokens keep the patient app's names (`ShifaColors.Text.Brand`, `ShifaSpacing.Inset.Md`, …) because `tokens.json`
  was generated from them; doctor-only values (`teal-70`, `grey-550`, the `font-size` scale, `layout-*`, `chart-*`)
  are added under the same scheme.
- The audit's fixed rules apply to every screen: 44 px targets, minimum text 10, one `ActionBar` per form, one top
  `Toast`, skeletons not spinners, only cancelled visits struck through.
- `AuRecommend` items (week view, drag to move, command palette, autosave) are out of scope unless the user adds them.
- Refreshing the copy: read the artifact's `project/*` files with the Artifact tool and overwrite this folder; note
  the version in `docs/design/README.md`.
