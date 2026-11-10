# Shifa Doctor app: feature inventory for a PostHog event plan

Source: `/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app`, branch `fix/overnight-audit-2026-11-10`
(HEAD `719a3ea`, read as checked out, nothing modified, Gradle not run). Paths below are relative to
`composeApp/src/commonMain/kotlin/shifa/doctor/` unless they start with another folder.

Size: 331 Kotlin files in `commonMain` (63.8k lines under `ui/`), 60 in `androidMain`, 27 in `iosMain`, 31 in
`wasmJsMain`, 177 test files in `androidUnitTest`. **70 ViewModels**, 60 `*ViewModel*Test.kt` files.

---

## 1. Analytics that exists or was planned

**Nothing is there.** No PostHog, Firebase Analytics, Crashlytics, Sentry, Umami, Amplitude or Mixpanel in code, build
files, workflows or `index.html`. No `track(` call (the only `track(` hits are Daily's video tracks in
`VideoCall.android.kt` / `VideoCall.wasmJs.kt`). No tracking plan, consent text or analytics section in `docs/kmp-spec/`.

What the search did find:

| Where | What it is |
|---|---|
| `docs/implemented/foundation.md:9,16`, `docs/implemented/OVERNIGHT_2026-11-04.md:34` | The Gradle catalog and `iosApp/` were copied from the patient app **"minus PostHog"**. So PostHog was removed on purpose at project start, not forgotten. |
| `iosApp/iosApp/GoogleService-Info.plist:21` | `IS_ANALYTICS_ENABLED = false`, `IS_ADS_ENABLED = false`. |
| `composeApp/build.gradle.kts` | Firebase BOM + `firebase-messaging` only (plus the App Distribution plugin). No `firebase-analytics`. |
| `iosApp.xcodeproj` | SPM products: `FirebaseMessaging`, `Daily`. Nothing else. |
| `data/analytics/`, `ui/analytics/`, `data/model/AnalyticsModels.kt`, `DoctorFeature.BasicAnalytics/AdvancedAnalytics`, endpoints `/doctor/analytics/*` | The **product feature** "practice analytics / Reports" (KPIs, trend chart, CSV export). Not usage tracking. |
| `ui/widgets/Activity.kt` `Modifier.trackActivity()` | The inactivity clock (LOCK-02): reports every press to `SessionRepository.touch()`. Not analytics, but it is a root-level pointer hook. |
| `data/api/HttpClient.kt` `httpLogger` | Debug-only failure log (`FAILED <status> <method> <masked path> <code> requestId=`). The closest thing to an existing event stream. |

**Name collision to plan for:** the packages `data/analytics` and `ui/analytics` and the names `AnalyticsRepository`,
`AnalyticsViewModel`, `AnalyticsRange`, `AnalyticsSection`, `AnalyticsCsv` are taken by the Reports feature. The patient
app's layer is called `shifa.uz.data.analytics.Analytics`; copied as is it would sit in the same package as the Reports
repository. Use another package and prefix here (for example `data/tracking/`, `Tracker`, `TrackEvent`).

**Reference pattern (patient app, a separate repo; copying a pattern across repos is allowed, sharing code is not):**
`apps-flutter-patient/composeApp/src/commonMain/kotlin/shifa/uz/data/analytics/` has `Analytics.kt` (209 lines:
`AnalyticsSink` interface, `AnalyticsTracker` interface, `object Analytics` with `sink` null while opted out or in demo
mode, `identify` with `patient_<id>`, a debug-menu `recent` list), `AnalyticsEvent.kt` (883 lines, one typed event
catalogue), `AnalyticsScreen.kt`, `AnalyticsSettings.kt` (opt-out). Android sink: `androidMain/.../PostHogSink.kt`
(`com.posthog:posthog-android` 3.75.1). iOS sink: `iosApp/iosApp/PostHogAnalytics.swift` (71 lines) implementing the
Kotlin interface, registered from `AppDelegate` through `iosMain/.../AnalyticsBridge.kt`. Host
`https://eu.i.posthog.com`; an `app = "patient"` property on every event; the key is a `BuildConfig` field set only in
release builds. ViewModels take `analytics: AnalyticsTracker = Analytics` (25 ViewModels). Docs:
`apps-flutter-patient/docs/posthog/{README,events,privacy,setup,development,dashboards,personalisation}.md`.
The patient app has **no web target**, so the web sink is new work for the doctor app.

---

## 2. Feature map

Conventions used by every feature (from `CLAUDE.md`): `XxxScreen(viewModel = viewModel { XxxViewModel() }, …)` collects
state and wires callbacks; `XxxContent(state, actions)` is stateless. Many features keep the UiState, the ViewModel and
the screen **in one file** (for example `ui/calendar/BookingScreen.kt`). Failures become a `CodeBanner` /
`StringResource` through `e.toUserMessage()`; the `ApiException` (status, `code`, `requestId`) is available at that
point. "Actions" below counts the ViewModel's public functions (field setters included); the outcome column names the
ones worth an event.

Totals: 23 feature folders, about 75 full screens plus about 45 sheets/trays/dialogs, 70 ViewModels, about 640 public
ViewModel functions, of which roughly 190 are a network write or a meaningful decision.

### 2.1 Signed-out flow and session (top-level `Screen` in `App.kt`)

| Feature | Screens (file → composable) | ViewModel (file) → public functions | Repository calls | Outcomes worth an event |
|---|---|---|---|---|
| **Splash** (2 actions, 1 screen) | `ui/splash/SplashScreen.kt` → `SplashScreen`, `SplashScreenContent` (also the force-update and maintenance frames) | `SplashViewModel` (`ui/splash/SplashViewModel.kt`): `skip`, `retry` | `PublicConfigRepository.bootGate`, `SessionRepository.restore` | app opened; session restored / not; boot gate = update required / maintenance / none; boot-gate read failed |
| **Sign in** (5 actions, 1 screen) | `ui/login/LoginScreen.kt` → `LoginScreen`, `LoginScreenContent`; frame `ui/auth/AuthFrame.kt` (language pill) | `LoginViewModel`: `onIdentifier`, `onPassword`, `dismissBanner`, `togglePasswordVisible`, `submit` | `AuthRepository.login` → `SessionRepository.signIn` | sign-in submitted (method = password); succeeded (role); failed (invalid credentials / not a doctor account / disabled / timeout / network / unusable token); validation error; session-ended banner shown (reason) |
| **E-mail / SMS code and password reset** (14 actions, 3 screens) | `ui/emailcode/EmailCodeScreens.kt` → `EmailEntryScreen`, `CodeScreen`; `ui/emailcode/ResetPasswordScreen.kt` → `ResetPasswordScreen` | `EmailEntryViewModel`: `onEmail`, `dismissBanner`, `submit`, `consumeSent`. `CodeViewModel`: `onCode`, `dismissBanner`, `verify`, `resend`. `ResetPasswordViewModel`: `onPassword`, `onConfirm`, `togglePassword`, `toggleConfirm`, `dismissBanner`, `submit`. One `EmailCodeMode`: `SignIn`, `SmsSignIn`, `Reset` | `AuthRepository.sendLoginOtp`, `sendLoginSmsOtp`, `verifyLoginSmsOtp`, `verifyEmailOtp`, `sendForgotPasswordOtp`, `resetForgottenPassword`; `OtpCooldown`; `SessionRepository.signIn` | code requested (mode) ok / failed / rate limited; code verified / wrong / expired; resend; password reset done / failed; back out |
| **Registration** (37 actions, 2 screens + 5 pickers/sheets) | `ui/register/VerifyKeyScreen.kt` → `VerifyKeyScreen`; `ui/register/RegisterScreen.kt` → `RegisterScreen`, `RegistrationCodeSheet`, `TimeZonePicker`; `RegisterDetails.kt` → `ClinicPicker`, `ProfessionPicker`, `DobPicker`; `RegisterStaff.kt` (receptionist form) | `VerifyKeyViewModel`: `onKey`, `dismissBanner`, `submit`. `RegisterViewModel` (639 lines): 12 field setters, pickers (`openPicker`, `closePicker`, `onQuery`, `retryPicker`, `pickTimeZone`, `pickClinic`, `toggleProfession`, `clearProfessions`, `openDob`, `pickDob`, `toggleGender`, `pickGender`), `back`, `keepEditing`, `next`, `register`, `onCode`, `verifyCode`, `closeCode`, `resendCode` | `AuthRepository.verifyKey`, `isDoctor`, `existingPatient`, `sendRegistrationOtp`, `register`, `registerClinicStaff`; `CatalogRepository.professions/clinics`; `ProfileRepository.update` (step 2 PATCH) | invitation key valid (purpose doctor / receptionist) / invalid; step 1 → step 2; duplicate doctor; existing-patient confirmation shown / accepted; e-mail code sent / verified / wrong; registered (kind); register failed (code); step-2 PATCH failed (shell notice); abandoned (discard) |
| **Sign out** (3 actions, 1 sheet) | `ui/auth/SignOut.kt` → `SignOutSheet` | `SignOutViewModel`: `ask`, `cancel`, `confirm` | `SessionRepository.signOut` (→ `PushRegistration.signedOut`) | sign-out asked / cancelled / confirmed |
| **App lock (PIN, biometrics)** (10 actions, 1 screen + 1 section + PIN sheets) | `ui/applock/LockScreen.kt` → `LockScreen`; `ui/applock/AppLockSection.kt` → `AppLockSection`, `PinSheetContent` (set / repeat / verify / off) | `LockViewModel`: `onDigit`, `submit`, `onBackspace`, `onBiometricSuccess`. `AppLockSettingsViewModel`: `toggleLock`, `changePin`, `closeSheet`, `onPin`, `submit`, `setBiometric` | `AppLockRepository.check`, `unlockWithBiometrics`, `turnOn`, `turnOff`, `setBiometric`; `SessionRepository.end(PinLockout)` | lock shown; unlocked (pin / biometric); wrong PIN (attempts left); locked out → signed out; "sign out instead"; lock enabled / disabled / PIN changed; biometrics on / off |

### 2.2 Shell

| Feature | Screens | ViewModel → public functions | Repository calls | Outcomes |
|---|---|---|---|---|
| **Shell / navigation** (19 actions, 1 host + More sheet + sidebar) | `ui/navigation/MainScreen.kt` → `MainScreen`, `MainScreenContent`, `MoreSheetContent`, private `RouteContent`; `ShellSidebar.kt` (web/tablet rail and sidebar); `ShifaNavBar.kt`; `ShellNotice.kt` | `ShellViewModel` (`ui/navigation/ShellViewModel.kt`, 813 lines): `select(tab)`, `openTask`, `openCalendar`, `openChatWithPatient`, `openPath`, `openPatientFromSearch`, `push(route)`, `onForeground`, `onBackground`, `refreshNotifications`, `refreshHomeCounts`, `openPush`, `pushOpened`, `openMore`, `closeMore`, `openIntelligence`, `leaveIntelligence`, `onResume`, `back` | `ProfileRepository.refresh/meProfile`, `ClinicsRepository.mine`, `BadgesRepository.chatUnread/notificationsUnread/taskCounts`, `NotificationsRepository.markRead`, `NotificationOpener.go` | tab selected (tab, from bar / More / sidebar); route pushed (route type); More opened; Intelligence opened / left; start tab applied (saved / web path / clamped); gated tab refused (Tasks without plan, Clinic without clinic); plan changed mid-session (lost Tasks / lost AI); push opened (type, target kind, cold start or not); web deep link opened; profile read failed |

### 2.3 Doctor features (shell tabs and pushed routes)

| Feature | Screens | ViewModels → public functions | Repository calls | Outcomes |
|---|---|---|---|---|
| **Home** (12 actions, 2 screens) | `ui/home/HomeScreen.kt` → `HomeScreen`, `HomeContent`; `HomeHero.kt` (next patient, Start / Continue, quick actions); `HomeAttention.kt` (attention centre); `HomeSearch.kt` → `PatientSearchScreen` | `HomeViewModel`: `practiceZone`, `focus`, `refresh`, `findVideoSlot`, `videoSlotOpened`. `HomeAttentionViewModel`: `refresh`, `markRead`, `open`, `openTask`, `opened`. `PatientSearchViewModel`: `load`, `type` | `HomeRepository.schedule/firstFreeSlot`, `NotificationsRepository.page/markRead`, `TasksRepository.tasks`, `PatientsRepository.refreshIfStale` | home viewed (has next patient, visits today as a bucket); Start / Continue visit tapped; quick action tapped (which); video-slot search found / none; attention item opened (kind); patient search used → result opened; refresh failed |
| **Calendar and booking** (42 actions, 4 screens + 5 sheets) | `ui/calendar/CalendarScreen.kt` → `CalendarScreen`, `CalendarContent`; `MonthPicker.kt`; `WeekGrid.kt` (wide); `StaffCalendars.kt` → `StaffSection`; `AppointmentPanel.kt` → `AppointmentPanelScreen`, `AppointmentPanel`, `NoShowSheet`; `BookingScreen.kt` → `BookingScreen`; `BlockTime.kt` → `BlockTimeSheet`, `BlockPanelScreen`; `SlotPicker.kt` → `SlotPickSheet` | `CalendarViewModel`: `toggleStaff`, `retryStaff`, `select`, `month`, `ui`, `load`. `AppointmentPanelViewModel`: `back`, `ui`, `applyDuration`, `remindPayment`, `rescheduleOn`, `rescheduleMonth`, `move`, `cancel`, `noShow`. `BookingViewModel`: `ui`, `pickStart`, `openPicker`, `patientCreated`, `pickPatient`, `loadPatients`, `back`, `book`. `BlockViewModel`: `edit`, `pickDate`, `block`. `BlockPanelViewModel`: `ui`, `remove`. `SlotPickViewModel`: `tick`, `confirm`, `open`, `pick`, `month`, `select` | `CalendarRepository.day/month/previewDay`; `AppointmentsRepository.book/change/cancel/noShow/remindPayment`; `BlocksRepository.overlapping/create/remove`; `PatientsRepository.forAssignment` | day / week view switched; filter used; staff calendar toggled; appointment opened; **booked** (clinic / video, for self or a colleague, from calendar / patient / Home / Intelligence) / failed (slot taken `CONFLICT`, past day); **rescheduled**; duration changed; **cancelled**; **no-show** marked (allowed / `NO_SHOW_NOT_ALLOWED`); payment reminder sent; time blocked (day / hours / several days, cancelled-overlap count) / block removed; booking abandoned |
| **Schedule and locations** (32 actions, 2 screens + sheets) | `ui/schedule/ScheduleScreen.kt` → `ScheduleScreen`, `ScheduleContent`; `LocationsScreen.kt` → `LocationsScreen`; map via `ui/profile/MapPicker.kt` | `ScheduleViewModel`: `ui`, `load`, `selectLocation`, `discard`, `back`, `openDay`, `addRange`, `removeRange`, `copyFrom`, `applyToWeekdays`, `pickPeriodDate`, `startPeriod`, `save`, `openDateHours`, `editDateHours`, `addDateHours`, `deleteDateHours`. `LocationsViewModel`: `load`, `ui`, `open`, `edit`, `save`, `delete`, `openMap`, `movePin`, `confirmMap`, `closeMap`, `discardPin`, `keepPin`, `back` | `ScheduleRepository.rules/saveRules/periods/addPeriod/dateSpecific/addDateSpecific/deleteDateSpecific`; `LocationsRepository.refresh/save/delete` | working hours saved / failed / discarded; bookable period set; date-specific hours added / deleted; copy-day and Mon–Fri helpers used; location created / edited / deleted; map pin set |
| **Patients directory and record** (47 actions, 2 screens + record tabs + 6 sheets) | `ui/patients/PatientsScreen.kt` → `PatientsScreen`; `PatientDetail.kt` → `PatientDetailScreen` with `RecordTab` = Overview, Medical, Appointments, Documents, Forms, Activity; `PatientMedical.kt`; `PatientVisits.kt`; `CreatePatient.kt` → `CreatePatientSheet`; `PatientPickerSheet.kt`; `PatientBriefing.kt` → `PatientBriefingSheet`; `ClinicalTimeline.kt`; `Prophylaxis.kt` | `PatientsViewModel`: `load`, `type`, `ui`, `filter`, `page`, `clearFilters`, `toggleFavorite`. `PatientDetailViewModel` (26): `load`, `retryVisits`, `openBooking`, `closeBooking`, `select(tab)`, `back`, `discard`, `editPhones`, `editAllergies`, `savePhones`, `saveAllergies`, `setChronic`, `setGender`, `setBloodGroup`, `openEdit`, `edited`, `closeEdit`, `toggleFavorite`, `acknowledgeChronic`, `setSms`, `setSmsHours`, `testSms`, `createAccount`, `closeAccount`, `ui`. `CreatePatientViewModel`: `edit`, `ui`, `close`, `create`. `PatientBriefingViewModel`: `open`, `generate`, `copied`. `ClinicalTimelineViewModel`: `load`. `ProphylaxisViewModel`: `load`, `interval`, `enabled`, `save`, `dismiss` | `PatientsRepository.refresh/patient/create/update/appointments/createAccount/sendTestSms/prophylaxis/saveProphylaxis`; `FavoritesRepository.toggle`; `AiRepository.patientBriefing/timeline` | directory viewed; search used (never the text); filter / tab (All, Favorites, Follow-ups) used; patient opened (from directory / search / clinic / notification / link); record tab viewed; **patient created** / duplicate confirmation / failed; patient edited; medical field saved (which field, never the value); favourite toggled; SMS reminders on / off, test SMS sent; patient-app account created; AI summary generated / empty (400) / daily limit (429) / copied / regenerated (PRO gate); prophylaxis saved; booking started from the record |
| **Documents** (25 actions, 1 screen + 1 tab + 5 trays) | `ui/documents/PatientDocuments.kt` (record tab and visit list, `DocumentCard`); `DocumentUpload.kt` → `UploadSheets`, `UploadOptionsTray`, `UploadDetailsTray`; `DocumentViewer.kt` → `DocumentViewerScreen`; `DocumentEdit.kt` → `EditDocumentTray`, `DeleteDocumentDialog` | `PatientDocumentsViewModel`: `load`, `search`, `ui`, `startEdit`, `editDraft`, `closeEdit`, `saveEdit`, `delete`, `requestAccess`. `DocumentUploadViewModel`: `ui`, `pdf`, `photo`, `page`, `addPage`, `finishScan`, `edit`, `close`, `add`, `retry`, `cancelUpload`. `DocumentViewerViewModel`: `load`, `requestAccess`, `save`, `ui`, `release` | `DocumentsRepository.list/download/upload/edit/delete/requestAccess` | **upload** started (source pdf / photo / gallery / scan, category, page count, size bucket) / done / failed / cancelled / over 25 MB; document opened (kind image / pdf / no preview); locked → access requested; saved to device; edited; deleted |
| **Forms (025-2) and clinical engine** (33 actions, 1 screen + 1 tab + 3 sheets) | `ui/forms/PatientForms.kt` (record tab, `FormTemplateTray`); `FormEditorScreen.kt` → `FormEditorScreen`; `ClinicalPanel.kt` → `ClinicalPanel`, `ClinicalPickerSheet`; dental chart widget `ui/widgets/DentalChart.kt` | `PatientFormsViewModel`: `load`, `ui`. `FormEditorViewModel`: `load`, `edit`, `ui`, `setCell`, `addRow`, `removeRow`, `setDentition`, `searchIcd`, `pickIcd`, `clearIcd`, `applyEngine`, `openReturn`, `saveReturn`, `removeReturn`, `back`, `save`, `requestSignature`. `ClinicalPanelViewModel`: `start`, `retry`, `openPicker`, `closePicker`, `pickGroup`, `pickDisease`, `toggle`, `setVariable`, `search`, `addFromSearch`, `apply`, `consumed` | `FormsRepository.list/save/requestSignature/linkDocument/filePdfDocument/searchIcd10`; `DocumentsRepository.upload/replaceFile`; `ClinicalEngineRepository.groups/diseases/disease/topDiagnoses/searchChips/occlusionChips/sharedTemplates/synthesize/recordUsage` | form created / opened; **form saved** (new or existing; the pipeline step that failed: form → PDF → document → link → usage); signature requested; ICD-10 picked (search used, never the code text if the team treats codes as clinical data); clinical engine opened, disease picked, **Apply** used (chip count); discard |
| **Appointments / visit workspace** (32 VM actions + about 45 on the helper classes, 1 screen with panes + 6 sheets) | `ui/visit/VisitScreen.kt` (1754 lines) → `VisitScreen`, `VisitContent`, `PhotoSource`, `StaffCannotRunVisits`; `EndVisit.kt` → `EndSheet`, `EndConfirm`, `EndProgress`; `Briefing.kt` → `BriefingSheet`; `VisitFacts.kt` → `VisitFactsPanel`; `VideoPane.kt` → `PreJoin`, `VideoPane`, `LeaveCall`; `DentalPanel.kt` → `DentalServicesSheet`; `ToothMap.kt`; `PlanPanel.kt` → `PlanPanel`, `PlanFulfilSheet` | `VisitViewModel` (926 lines): `load`, `foreground`, `requestSignature`, `uploadScribe`, `retryScribe`, `openEnd`, `closeEnd`, `end`, `skipScribe`, `retrySummary`, `mode`, `helper`, `applyIcd`, `addToNotes`, `ui`, `openPlanTooth`, `photo`, `pickingPhoto`, `addPhoto`, `removePhoto`, `leave`, `askNoShow`, `notifyNoShow`, `noShow`, `leaving`. `BriefingViewModel`: `start`, `stop`, `retry`, `copied`. `VisitFactsViewModel`: `load`, `toggle`, `document`. Plain helper classes owned by the ViewModel: `VisitNotes` (`edit`, `append`, `flush`, `confirm(draft)`, `discard(draft)`), `DentalNotes` + `PlanEditor` (`edit`, `add`, `remove`, `dentition`, `discount`, `flush`), `VisitPlan` (`select`, `open`, `toggle`, `amount`, `method`, `applyTicks`, `recordPayment`), `VideoVisit` (see Video), `Autosave` | `AppointmentsRepository.detail/requestSignature/signatureUpdates/briefing/retryBriefing/noShow`; `ConsultationRepository.notes/saveNotes/drafts/confirmDraft/discardDraft/complete/uploadRecording/scribeStatus`; `DentalRepository.record/save`; `TreatmentPlansRepository.candidates/fulfill/detail`; `FinanceRepository.recordPayment`; `DocumentsRepository.upload`; `AiRepository.visitFacts/commit`; `FormsRepository` (latest 025-2) | **visit opened** (kind in-person / video, profession dental or not); visit mode switched (`VisitMode` = `General, Dental, Form0252`); notes autosave failed; AI draft confirmed / discarded; helper used (From Shifa AI, last 025-2); ICD suggestion applied; before/after photo added; signature requested / signed; **End visit**: opened, each pipeline step (`EndStep` = `Notes, Plan, Payment, Complete, Scribe, Summary`), completed, failed at step, "continue without waiting"; no-show from the visit; briefing viewed / ready / failed / copied; visit facts kept / rejected / documented; dental record saved, plan lines ticked, payment recorded at the visit (method, never the amount unless the team wants revenue data) |
| **Video call** (11 actions, panes inside the visit screen) | `ui/visit/VideoPane.kt`; platform tiles `utils/VideoCall.kt` (`expect`), `VideoCall.android.kt`, `VideoCall.ios.kt` + `iosApp/iosApp/DailyVideoCall.swift`, `VideoCall.wasmJs.kt` | `VideoVisit` (`ui/visit/VideoVisit.kt`, a plain class, stages `PreJoin, Joining, InCall, Left`): `permissions`, `join`, `toggleCamera`, `toggleMic`, `flip`, `askLeave`, `leave`, `release`, `dismissError`, `watchPresence` | `VideoRepository.token/presence`; the Daily SDK | pre-join shown; permissions granted / denied (camera, mic); patient present on pre-join; **join** started / joined / failed (token refused, `PAYMENT_REQUIRED`, plan gate `VIDEO_CONSULTATION`, SDK error); camera / mic toggled; left (duration bucket); call error |
| **AI scribe, dictation, voice** (shared, about 25 actions) | `ui/speech/SpeechTextField.kt` (mic on note fields), `ui/speech/Dictation.kt`; recording pill inside `VisitScreen.kt`; `ui/intelligence/VoiceSession.kt`, `ThreadScreen.kt` → `VoicePanel`; `ui/widgets/VoiceOrb.kt` | `Dictation` (plain class): `denied`, `start(field)`, `cancel`, `finish`, `finishWith`, `report`, `dismiss`. `VoiceSession` (plain class, with `Vad`): `attach`, `locked`, `denied`, `mic`, `record`, `stopTake`, `discard`, `handsFreeOff`, `typed`, `replied`, `turnFailed`, `retrying`, `resend`, `paused`, `dismiss`, `leave`. Scribe: `VisitViewModel.uploadScribe/retryScribe/skipScribe` | `TranscriptionRepository.transcribe/report`; `ConsultationRepository.uploadRecording/scribeUpdates`; `AiRepository.speak`; `PublicConfigRepository.transcriptionFeedbackEnabled` | dictation started (which field type) / transcribed / "no speech" / failed (403 plan, 429 limit, 502) / cancelled / reported; mic permission denied; **scribe recording** started / stopped (duration bucket) / uploaded / upload failed and retried / notes ready or timed out (120 s) / skipped; voice session started (hands-free or hold), turn completed, failed, turned off; PRO lock shown |
| **Shifa Intelligence (AI assistant)** (28 actions, 1 screen with welcome + thread, 1 tray, 12 card kinds) | `ui/intelligence/IntelligenceScreen.kt` → `IntelligenceScreen`, `IntelligenceContent`, `HistoryTray`, `SessionTile`; `Welcome.kt` → `WelcomeBody` (4 starter cards); `ThreadScreen.kt` → `ThreadList`, `Composer`, `VoicePanel`; `Cards.kt` → `ToolCard` (schedule, patient appointments, free slots, patient search, most visited, clinic patterns, patient brief, propose appointment / reschedule, cancellation, visit facts, patient documents, dental diagram, open appointment); `DownloadedDocument.kt` | `IntelligenceViewModel`: `load`, `back`, `newConversation`, `history`, `query`, `select`, `menu`, `askRename`, `renameText`, `rename`, `askDelete`, `delete`, `busy`, `export`, `exportFailed`, `dismissBanner`. `ThreadViewModel` (`ui/intelligence/ThreadViewModel.kt`): `draft`, `send`, `retry`, `book`, `documentFacts`, `openVisit`, `autoOpened`, `toggleOtherTimes`, `dismiss`, `answerCancel`, `stop` | `AiRepository.stream` (SSE) `/speak/commit`; `ChatSessionStore` (local, encrypted on phones, plain storage on web); `AppointmentsRepository.book/change`; `TranscriptionRepository` | Intelligence opened (gate `ASK_SHIFA_AI`); starter card used (which); **message sent** (typed / voice / starter, has patient context) → reply received (card type, duration bucket) / empty / limit / timeout / AI down / stopped; retry; card action (confirm booking, other times, slot picked, cancel yes / keep, document everything, open visit, open calendar, open document); conversation created / renamed / deleted / exported; history searched. Never the prompt or the reply text |
| **Chat** (19 actions, 2 screens + viewers) | `ui/chat/ChatListScreen.kt` → `ChatListScreen`; `ThreadScreen.kt` → `ThreadScreen`, `ThreadContent`; `PatientChat.kt` → `PatientChatScreen`; `ChatImages.kt` → `AttachSheet`, `ImageViewer`, `PdfViewer` | `ChatListViewModel`: `refresh`, `foreground`, `sort`, `choosingSort`, `type`, `open`, `startWith`, `retrySearch`, `opened`. `ThreadViewModel` (`ui/chat/ThreadViewModel.kt`): `refresh`, `foreground`, `draft`, `openPdf`, `closePdf`, `send`, `sendImage`, `sendDocument`, `sendVoice`, `retry`. `PatientChatViewModel` (resolves or starts the conversation) | `ChatRepository.conversations/thread/start/file/markRead/send/search/upload` (5 s polling) | chat list viewed; conversation opened (from list / patient / notification / link); conversation started with a user; **message sent** (kind text / image / document / voice) / failed / retried; attachment opened (image / pdf); sort changed; search used. Never the text, the file name or the other party's name |
| **Notifications inbox** (9 actions, 1 screen) | `ui/notifications/NotificationsScreen.kt` → `NotificationsScreen`; `NotificationLook.kt`; `NotificationOpener.kt` (target resolution, shared with Home and push) | `NotificationsViewModel`: `load`, `refresh`, `loadMore`, `select(filter)`, `open`, `opened`, `read`, `decide` (document access approve / decline), `markAll` | `NotificationsRepository.page/markRead/markAllRead/decideAccess` | inbox viewed (unread bucket); filter used; **notification opened** (type, target kind: appointment / patient / document / chat / partnership / task / inbox, target gone); access request approved / declined; mark all read |
| **Tasks / remote care** (28 actions, 4 screens + 2 sheets) | `ui/tasks/TasksScreen.kt` → `TasksScreen`; `TaskDetailScreen.kt` → `TaskDetailScreen`, `CheckInTray`, `CancelTaskDialog`; `CreateTaskScreen.kt` → `CreateTaskScreen`; `TemplatesScreen.kt` → `TemplatesScreen` | `TasksViewModel`: `refresh`, `dismissError`, `query`, `status`, `category`, `toggleStatus`, `clearFilters`. `TaskDetailViewModel`: `load`, `view(checkIn)`, `askCancel`, `cancel`. `CreateTaskViewModel`: `edit`, `ui`, `mode`, `addSlot`, `setSlot`, `removeSlot`, `startDate`, `endDate`, `openDate`, `openPicker`, `loadPatients`, `pickPatient`, `back`, `create`. `TemplatesViewModel`: `load` | `TasksRepository.tasks/task/checkIns/templates/create/cancel`; `PatientsRepository.forAssignment` | tasks viewed (PREMIUM gate `REMOTE_CARE_TASKS`); filter used; task opened; check-in viewed; **task created** (category, schedule mode, from template or not, slot count) / validation problems / failed; task cancelled; templates opened / template chosen; create abandoned |
| **Care partnerships** (23 actions, 3 screens) | `ui/care/CareScreens.kt` → `FindPartnerScreen`, `PartnershipsScreen`, `PartnershipScreen` | `ui/care/CareViewModels.kt`: `FindPartnerViewModel`: `query`, `specialty`, `search`, `pick`, `reason`, `message`, `send`, `back`, `keep`, `dismiss`. `PartnershipsViewModel`: `load`, `filter`, `showAll`, `rows`. `PartnershipViewModel`: `load`, `loadProgress`, `ask`, `act`, `draft`, `post`, `back`, `keep`, `dismiss` | `CarePartnershipsRepository.search/mine/get/invite/act/progress/addProgress` | partner search used; **invite sent** / already a partner (409) / failed; partnership accepted / declined / cancelled / completed; progress update posted; list filter used |
| **Reports / practice analytics** (5 actions, 1 screen + 1 Home section) | `ui/analytics/ReportsScreen.kt` → `ReportsScreen`; `AnalyticsSection.kt` → `AnalyticsSection` (also on Home); `AnalyticsCharts.kt` | `AnalyticsViewModel`: `refresh`, `refreshToday`, `setRange`, `export`, `exported` | `AnalyticsRepository.overview/trend/consultationTypes/engagement/smsUsage` | reports viewed; range changed (preset / custom, day count); CSV exported / share sheet not shown; PREMIUM teaser shown (`ADVANCED_ANALYTICS`) → "see plans" tapped; trend day tapped |

### 2.4 Clinic workspace (one tab, nine sections, three pushed screens)

`ui/clinic/ClinicWorkspace.kt` → `ClinicWorkspaceScreen`, `ClinicWorkspaceContent`; `ClinicSection` = `Overview, Calendar,
Doctors, Invitations, Patients, Plans, Finance, Services, Settings`. 117 public ViewModel functions, 8 ViewModels,
7713 lines: the biggest feature.

| Sub-feature | Files | ViewModel → public functions | Repository calls | Outcomes |
|---|---|---|---|---|
| **Workspace, overview, settings** | `ClinicWorkspace.kt`, `ClinicSections.kt` | `ClinicWorkspaceViewModel`: `load`, `section`, `switcher`, `select(clinicId)`, `readStats` | `ClinicsRepository.mine/selected/select/overviewStats` | workspace viewed (role doctor / staff, membership role, `canManageFinanceSettings`); section viewed; clinic switched; quick action tapped |
| **Members, revenue share, invitations** | `ClinicPeople.kt` | `ClinicPeopleViewModel`: `loadMembers`, `loadInvitations`, `query`, `role`, `openShare`, `shareText`, `closeShare`, `saveShare`, `openInvite`, `inviteEmail`, `closeInvite`, `sendInvite`, `askRevoke`, `revoke`, `dismiss`, `pendingTo` | `ClinicsRepository.members/updateMemberShare/updateDefaultShare/invitations/invite/revokeInvitation` | revenue share saved (member / clinic default; cleared) / failed; **receptionist invited** / duplicate pending / failed; invitation revoked |
| **Clinic patients** | `ClinicPatients.kt` (opens `ShellRoute.Patient(id, clinicId)`) | `ClinicPatientsViewModel`: `load`, `refresh`, `type`, `loadMore` | `ClinicsRepository.patients` (server search, pages of 50) | clinic patient list viewed; search used; load more; patient opened in clinic scope |
| **Clinic doctor schedule** | `ClinicSchedule.kt` → `ClinicDoctorScheduleScreen` (reuses `CalendarViewModel` with a `CalendarScope`) | (calendar ViewModels with `scope` / `doctorId` set) | calendar + appointments repositories on a colleague's behalf | colleague calendar opened; booked / moved / cancelled / unblocked **on behalf of** a colleague (a property on the calendar events, not new events) |
| **Finance** (dashboard, ledger, installments, earnings, records, payments, CSV export) | `ClinicFinance.kt` (812), `ClinicFinanceViews.kt` (1540); `FinanceView` = `Dashboard, Ledger, Installments, Earnings, Records, Payments`; `FinanceKpi` = `Revenue, Outstanding, Overdue, Rate` | `ClinicFinanceViewModel` (32): `view`, `month`, `query`, `chip`, `kpi`, `load`, `loadMore`, `payMenu`, `payFull`, `openCustomPay`, `editPay`, `closePay`, `savePay`, `installmentMenu`, `openMarkPaid`, `editMarkPaid`, `closeMarkPaid`, `markPaid`, `setStatus`, `remind`, `openRecord`, `editRecord`, `closeRecord`, `pickPatient`, `pickPlan`, `searchPatient`, `saveRecord`, `info`, `dismiss` | `FinanceRepository.dashboard/ledger/records/paymentsPage/installments/earnings/recordPayment/createRecord/markPaid/setInstallmentStatus/notifyInstallment`; `ClinicsRepository.patients`; `TreatmentPlansRepository.plans` | finance view switched; month changed; KPI selected; **payment recorded** (full / custom, method cash / card / transfer / other) / failed; installment marked paid / waived / cancelled / pending; installment reminder sent; **financial record created** (type, linked to plan or not); CSV exported (which view). Amounts: decide once whether buckets are allowed |
| **Treatment plans** (list, detail, wizard, PDF) | `ClinicPlans.kt` (1421) → `PlanDetailScreen`, `StatusSheetView`; `PlanWizard.kt` (1206) → `PlanWizardScreen` | `ClinicPlansViewModel`: `load`, `type`, `status`, `toggle`, `loadVisits`, `statusSheet`, `setStatus`, `dismiss`. `PlanDetailViewModel`: `load`, `menu`, `statusSheet`, `setStatus`, `openPay`, `editPay`, `closePay`, `savePay`, `exportPdf`, `pdfShared`, `dismiss`. `PlanWizardViewModel` (22): `loadDoctors`, `loadServices`, `search`, `pickPatient`, `basics`, `toggleDoctor`, `pickService`, `tooth`, `addLine`, `removeLine`, `payment`, `useInstallments`, `addInstallment`, `editInstallment`, `removeInstallment`, `pickDue`, `next`, `back`, `close`, `keepEditing`, `dismiss`, `save` | `TreatmentPlansRepository.plans/create/addLines/visits/setStatus/planServices/detail/pdf`; `FinanceRepository.createInstallmentPlan/recordPayment`; `ClinicsRepository.members/patients` | plans viewed, status filter; plan opened; **plan created** (line count, doctors count, installments yes / no, started from patient or list) / wizard step reached / abandoned at step / save failed at stage; plan status changed (to which); plan payment recorded; plan PDF exported (backend or client fallback) / failed |
| **Clinic services catalogue** | `ClinicServices.kt` (818) | `ClinicServicesViewModel`: `load`, `query`, `status`, `clearFilters`, `openAdd`, `openEdit`, `loadDoctors`, `edit`, `close`, `discard`, `keepEditing`, `save`, `menu`, `toggle` (deactivate / reactivate), `doctorHint`, `dismiss` | `TreatmentPlansRepository.planServices/catalogItems/createCatalogItem/patchCatalogItem`; `ClinicsRepository.members` | service created / edited / deactivated / reactivated; code already taken; discard |

### 2.5 Profile, settings, subscription, legal

`ui/profile/` : 17 files, 5831 lines, 11 ViewModels, 95 public functions. Routes: `ShellRoute.Profile(section)` with
`ProfileSection` = `PersonalInfo, Contact, Timezone (a sheet), Extended, Services, Subscription, Settings, Security,
About, Privacy`, plus `ShellRoute.Language`, `Schedule`, `Locations`, `Reports`, `Debug`.

| Sub-feature | File → screen | ViewModel → public functions | Repository calls | Outcomes |
|---|---|---|---|---|
| Profile root | `ProfileScreen.kt` → `ProfileScreen` (staff read-only variant) | `ProfileViewModel`: `retry`, `showStaffNote` | `ProfileRepository.refresh/meProfile/servicesCount/locationsCount`, `CatalogRepository` | profile viewed (completeness bucket); section opened (which); fix-it chip tapped |
| Personal information | `PersonalInfoScreen.kt`, `ProfessionPicker.kt` | `PersonalInfoViewModel`: `open`, `closed`, `cameraDenied`, `discard`, `edit`, `toggleProfession`, `ui`, `retryCatalog`, `back`, `save`, `upload(photo)` | `ProfileRepository.update/uploadPhoto` | saved (changed-field names only) / failed / discarded; photo uploaded / failed / camera denied |
| Contact and clinic address | `ContactScreen.kt`, `MapPicker.kt` | `ContactViewModel`: `open`, `edit`, `ui`, `openMap`, `movePin`, `confirmMap`, `closeMap`, `discardPin`, `keepPin`, `back`, `discard`, `closed`, `save` | `ProfileRepository.updateContact` | saved / failed / discarded; map pin set |
| Practice time zone | `TimeZonePicker.kt` → `PracticeTimezoneSheet` | `TimezoneViewModel`: `open`, `closed`, `query`, `pick`, `confirm`, `cancelConfirm`, `dismissBanner` | `ProfileRepository.update` | time zone changed (from → to; zones are not personal) / cancelled at the warning |
| Extended profile | `ExtendedProfileScreen.kt` | `ExtendedProfileViewModel`: `open`, `edit`, `ui`, `back`, `discard`, `closed`, `addTag`, `removeTag`, `uploadImage`, `uploadPdf`, `cameraDenied`, `deleteCertificate`, `save` | `ProfileRepository.update/saveServiceTags/saveCertificates/uploadCertificate` | biography / socials saved; tag added / removed; certificate uploaded (image / pdf) / deleted |
| Services and pricing, groups | `ServicesScreen.kt` (938) | `ServicesViewModel`: `load`, `ui`, `edit`, `open`, `back`, `closeEditor`, `save`, `delete`, `reload`, `openGroups`, `editGroups`, `deleteGroup`, `saveGroups` | `ServicesRepository.services/groups/save/delete/saveGroup/deleteGroup`; `LocationsRepository` | service created / edited / deleted (has price per location, currency); groups saved (count); clinic-managed read-only shown |
| **Subscription and billing (tier gates, checkout, Stripe Connect)** | `BillingScreen.kt` (575) | `BillingViewModel`: `open`, `loadPlans`, `edit`, `ui`, `back`, `discard`, `closed`, `upgrade(plan)`, `connect`, `urlOpened`, `resumed`, `save` | `SubscriptionRepository.plans/checkout/connectOnboarding/mine`; `ProfileRepository.updateBilling/refresh` | plans viewed (current tier); **upgrade tapped** (from tier → plan code) → checkout URL opened / failed; returned from checkout → tier changed / unchanged; Stripe Connect onboarding started / returned; invoicing details saved. Tier-gate impressions come from the gated features (`FeatureGate`, `seePlans` callbacks in `MainScreen.kt:561,955`) |
| Security (password) | `SecurityScreen.kt` (+ `AppLockSection`) | `SecurityViewModel`: `ui`, `type`, `back`, `discard`, `closed`, `change` | `AuthRepository.changePassword` | password changed / wrong current password / failed |
| Settings | `SettingsScreen.kt` | `SettingsViewModel`: `open`, `ui`, `change(edit)` (country, start tab; queued whole-block PATCH) | `ProfileRepository.updateSettings`; `LanguageStorage`; `AppLockRepository` | start tab changed (to); country changed; save failed; web: notification permission asked |
| Language | `LanguageScreen.kt` → `LanguageScreen` (no ViewModel; `LanguageStorage.set`), also the `AuthFrame` language pill and the More sheet | — | `LanguageRepository.sync` (`PUT /doctors/me/language`, doctors only) | language changed (from → to, where: auth / settings / more) |
| About, legal, contract | `AboutScreen.kt` | `AboutViewModel`: `openContract`, `closeContract`, `loadPdf` | `LegalRepository.links/contract/contractPdf` | about viewed; legal link opened (terms / privacy / licences); contract opened / PDF failed; diagnostics copied |
| Privacy and data | `PrivacyScreen.kt` → `PrivacyScreen`, `DeleteCodeSheet` | `PrivacyViewModel`: `dismissBanner`, `export`, `askDelete`, `closeDelete`, `continueDelete`, `onCode`, `confirm`, `resend` | `AccountRepository.exportData/requestDeletion/confirmDeletion`; `SessionRepository.accountDeleted`; `AppLockRepository.forget` | data export done / once-per-day limit / failed; **account deletion** asked / blocked (obligation codes `UPCOMING_APPOINTMENTS`, `ACTIVE_SUBSCRIPTION`) / code sent / confirmed / wrong code / cancelled |

### 2.6 Debug

`ui/debug/DebugMenuScreen.kt` (`DebugMenuScreen`: Demo data, Development backend, Splash gate, Native spikes),
`DebugPushSection.kt` (this device's token, backend test push, tap a push from the inbox), `SpikesScreen.kt`. No
ViewModel, no tests. Reached from Sign In (`Screen.Debug`) and Profile (`ShellRoute.Debug`), only when `isDebugBuild`.
**Never tracked**; it is the natural place for a "recent events" list like the patient app's.

### 2.7 Count per folder

| `ui/` folder | Lines | ViewModels | Public VM functions | Test files | Screens (full + sheets) |
|---|---|---|---|---|---|
| splash | 307 | 1 | 2 | 2 | 1 |
| login | 310 | 1 | 5 | 2 | 1 |
| emailcode | 701 | 3 | 14 | 4 | 3 |
| register | 1575 | 2 | 37 | 4 | 2 + 5 |
| auth (frame, sign-out, password widgets) | 466 | 1 | 3 | 2 | 0 + 1 |
| applock | 501 | 2 | 10 | 2 | 1 + 4 |
| navigation | 2608 | 1 | 19 | 2 | 1 + 1 |
| home | 2200 | 3 | 12 | 4 | 2 |
| calendar | 4288 | 6 | 42 | 8 | 4 + 5 |
| schedule | 1585 | 2 | 32 | 3 | 2 + 3 |
| patients | 4005 | 6 | 47 | 6 | 2 (+ 6 record tabs) + 6 |
| documents | 1711 | 3 | 25 | 5 | 1 + 5 |
| forms | 2313 | 3 | 33 | 4 | 1 + 3 |
| visit | 6429 | 3 (+ 5 helper classes) | 32 (+ about 45) | 9 | 1 (5 panes) + 6 |
| speech | 482 | 0 (`Dictation`) | 7 | 2 | 0 |
| intelligence | 3510 | 2 (+ `VoiceSession`) | 28 (+ 16) | 5 | 1 + 2 |
| chat | 2321 | 3 | 19 | 4 | 3 + 3 |
| notifications | 1184 | 1 | 9 | 4 | 1 |
| tasks | 2613 | 4 | 28 | 5 | 4 + 2 |
| care | 1269 | 3 | 23 | 2 | 3 |
| analytics (Reports) | 1256 | 1 | 5 | 3 | 1 |
| clinic | 7713 | 8 | 117 | 8 | 3 (+ 9 sections) + about 10 |
| profile | 5831 | 11 | 95 | 12 | 10 + 6 |
| debug | 501 | 0 | — | 0 | 2 |

---

## 3. Cross-cutting hooks

### 3.1 Navigation (where one screen-view hook can sit)

No navigation library. Two levels:

1. **`App.kt`**: `private sealed interface Screen { Splash, SignIn, EmailEntry(mode, email), EmailCode(email, mode),
   ResetPassword(email, code), VerifyKey, Register(invite), Debug, Shell }`, held in
   `var screen by remember { mutableStateOf<Screen>(Screen.Splash) }` inside `AppScreens()`, drawn by
   `Crossfade(screen)`. A `LaunchedEffect(screen)` in `AppScreens()` is the single hook for the signed-out screens.
   Caution: `EmailEntry`, `EmailCode` and `ResetPassword` carry the **e-mail address / phone and the code**, and
   `Register` the invitation; name the screen by its class and mode, never by `toString()`.
2. **`ui/navigation/ShellViewModel.kt`**: `ShellUiState(tab, stacks: Map<ShellTab, List<ShellRoute>>, moreOpen,
   intelligence, overIntelligence, staff, gate, hasClinic, …)`. The current screen is
   `state.route` (= `stack.last()`), or Intelligence when `state.intelligence`, or
   `state.overIntelligence.last()` when a screen is open over Intelligence. `ShellTab` = `Home, Calendar, Patients, Chat,
   Clinic, Tasks, Reports, Notifications, Profile`. `ShellRoute` has 26 kinds: `Root(tab)`, `Language`, `Conversation`,
   `PatientChat`, `Search`, `Task`, `TaskCreate`, `TaskTemplates`, `Debug`, `Schedule`, `Patient`, `Locations`,
   `Reports`, `Appointment`, `Visit`, `Block`, `PlanDetail`, `PlanWizard`, `FindPartner`, `Partnerships`,
   `Partnership`, `DoctorSchedule`, `Booking`, `Form`, `Document`, `Profile(section)`.
   `MainScreen.kt:397-415` draws it inside `SaveableStateProvider("${state.tab.key}/${state.stack.size}/${state.route}")`
   (no Crossfade); `RouteContent(route, …)` at `MainScreen.kt:478` is the one `when` over all routes.
   **Best single hook:** in `MainScreen()` next to the existing `val path = shellPath(state); LaunchedEffect(path) {
   showBrowserPath(path) }` (`MainScreen.kt:242`), add a `LaunchedEffect` keyed on a derived *screen name* (route kind
   + tab + intelligence flag), or collect `state.map { screenName(it) }.distinctUntilChanged()` in `ShellViewModel`.
   A pure function `screenName(ShellUiState): String` beside `shellPath` is easy to unit-test.
   - `shellPath(s)` (WEB-10) only returns `/<tab>`, `/patients/<id>`, `/chat/<id>`, `/tasks/<id>`: too coarse for
     screen names **and it contains ids**.
   - Routes are data classes holding patient names, document titles and whole `CalendarEntry.Appointment` objects:
     never send `route.toString()`.
   - Not covered by the shell route: the More sheet (`moreOpen`), record tabs inside the patient (`RecordTab`), clinic
     sections (`ClinicSection`), finance views (`FinanceView`), visit modes (`VisitMode`), profile's time-zone sheet,
     and every `ModalBottomSheet`. These need a per-feature "sub-screen viewed" call if wanted.
3. Each top-level screen and the shell sit in `ScopedViewModels { … }` (`App.kt`), a fresh `ViewModelStore` that is
   cleared when the screen leaves. `key(demo, backend) { … }` restarts everything on a debug backend switch.

### 3.2 Session lifecycle

`data/session/SessionRepository.kt` (singleton `sessionRepository`), `data/api/SessionEvents.kt`.

| Moment | Where | What is known |
|---|---|---|
| Cold-start restore | `SessionRepository.restore()` (called by `SplashViewModel`) | token valid → `Session(token, role)`; else `ended` gets `Expired` or `Inactivity` |
| Sign-in (password, e-mail code, SMS code, reset, registration) | `SessionRepository.signIn(token)`, called from `LoginViewModel`, `CodeViewModel`, `ResetPasswordViewModel`, `RegisterViewModel` | `Result<Session>`; the method is only known in the calling ViewModel |
| Sign-out | `signOut()` from `SignOutViewModel.confirm` | `SessionEndReason.SignedOut`; `signedOut(token)` unregisters the push device |
| Network end | `apiCall` → `SessionEvents.end(reason, token)` → `SessionRepository.end` | `Unauthorized` (401 with a token) or `AccountDisabled` (`code = ACCOUNT_DISABLED`); a 403 never ends it |
| Expiry / inactivity | `touch()`, `enforce()` (hourly, `App.kt`), `onForeground()` | `Expired`, `Inactivity` (30 days; `INACTIVITY_SECONDS`) |
| PIN lockout | `LockViewModel.submit`, `AppLockSettingsViewModel.submit` → `sessions.end(PinLockout, token)` | fifth wrong PIN |
| Account deleted | `PrivacyViewModel.confirm` → `accountDeleted()` | `AccountDeleted` |
| App lock | `data/applock/AppLockRepository.kt`: `locked: StateFlow<Boolean>`, `onSessionStarted/onBackground/onForeground` (locks after ≥ 5 s away), `check(pin): PinCheck { Unlocked, Wrong(attemptsLeft), LockedOut }` | lock shown / unlocked |

Two flows cover everything: **`sessionRepository.session: StateFlow<Session?>`** (identify on non-null, reset on null)
and **`sessionRepository.ended: SharedFlow<SessionEndReason>`** (replay 1; the seven reasons above; reset by the next
`signIn`). `App.kt` already collects `session` in several `LaunchedEffect`s and, on the web, `ended`. One collector in
the tracking layer (started from `App()`) gives `session_started` / `session_ended(reason)` and the identify/reset calls
with no ViewModel changes. Foreground/background: `LifecycleEventEffect(ON_START / ON_STOP)` in `App.kt` and
`ShellViewModel.onForeground/onBackground/onResume`.

### 3.3 Who the user is (person and group properties)

| Property | Source | Notes |
|---|---|---|
| Role | `Session.role` from the JWT (`utils/Jwt.role`): `DOCTOR` or `CLINIC_STAFF` (`Session.isClinicStaff`) | Known before any network call |
| User id | JWT `sub` (`Jwt.subject(token)`); doctors also `profileRepository.me.value.profile.id`; staff `MeProfile.id` | Staff **never** call `/doctors/me` (403), so a distinct id must not depend on the doctor profile. `sub` works for both |
| Subscription tier and features | `profileRepository.me` → `DoctorMe.subscription { tier, features }` → `data/subscription/FeatureGate.kt` (`DoctorTier { Basic, Pro, Premium }`, 13 `DoctorFeature`s). `ShellUiState.gate` | Unknown until the profile answers (D-03: unknown allows nothing). Changes mid-session (`ShellViewModel.onPlan`). Staff have none |
| Practice time zone | `DoctorMe.profile.timeZone` (IANA id); staff: `MeProfile.timeZone` or the clinic's (`profile.staffZone`) | Not personal data |
| Profession | `DoctorMe.profile.profession` (comma-separated catalogue names; dental detection in PROF-05) | Useful as `is_dental` |
| Country, start tab | `DoctorMe.settings.country`, `.defaultStartTab` | |
| Language | `languageStorage.language: StateFlow<AppLanguage>` (`English en`, `Uzbek uz`, `UzbekCyrillic uz-Cyrl`, `Russian ru`) | Available signed out too |
| Clinic context | `clinicsRepository.state` (`ClinicsState`, `hasWorkspace`), `ClinicsRepository.selected()`; `MyClinic { clinicId, membershipRole, isPracticeClinic, canManageFinanceSettings, timeZone, currency }`; `ShellUiState.hasClinic` | A PostHog **group** `clinic:<clinicId>` fits; the clinic **name is not needed** |
| Platform, version, build | `utils/PlatformUtils.kt`: `platformName` (`android` / `ios` / `web`), `appVersion`, `appBuild`, `isDebugBuild`, `isDevelopmentSite` | See 3.7 |
| Backend | `BackendChoice.current` (`Production` / `Development`), `DemoMode.enabled` | Release phones are always production; the web's dev site talks to the dev backend |

`profileRepository.me` (a `StateFlow`) is the one place to refresh person properties: it is re-read on every resume and
after every profile save.

### 3.4 `apiCall` and `ApiException` (generic failure reason)

`data/api/ApiHelper.kt`: `suspend inline fun <reified T> apiCall(request: () -> HttpResponse): Result<T>`.
`data/model/ApiError.kt`: `sealed class ApiException(status, code, requestId)` = `ValidationError (400/422)`,
`Unauthorized (401)`, `Forbidden (403)`, `NotFound (404)`, `Conflict (409)`, `RateLimit (429)`, `ServerError (else)`,
`NetworkError(timeout)` (status 0); `details: List<String>` holds backend detail **codes**. The exception message holds
no server text by design.

- One mapping `Throwable.toFailureReason()` (for example `network`, `timeout`, `validation`, `forbidden`, `not_found`,
  `conflict`, `rate_limited`, `server`, plus `code` and `status`) belongs beside `Throwable.toUserMessage()` in
  `data/api/ErrorMessages.kt`. `requestId` is a backend trace id, safe to send, useful for support.
- A **global** "api_request_failed" event could be emitted in `ApiHelper.failure()` / `logFailure()`, which already
  builds `status`, method, `maskIds(path)` (every path segment containing a digit or a dot becomes `{id}`), `code` and
  `requestId`. `apiCall` is `inline` with `@PublishedApi internal` helpers, so a hook there must also be
  `@PublishedApi`. SSE streams and polling log their failures outside `apiCall` (`data/api/Sse.kt`,
  `HttpClient.streamEvents`).
- 5 s / 10 s polling (chat, badges, signature, briefing, scribe) would flood a global failure event while offline:
  sample or dedupe.

### 3.5 Push open handling and deep links

- `data/push/Push.kt` (object): `device: StateFlow<Device?>`, `tapped: MutableStateFlow<Map<String,String>?>`,
  `received: SharedFlow<Map<String,String>>`; `onToken`, `onTap`, `onReceived` are called by Android
  (`androidMain/.../utils/ShifaMessagingService`, `PushNotifications.kt`, `MainActivity.openTappedPush`), iOS
  (`iosApp/iosApp/AppDelegate.swift` → `Push.shared.…`) and the web (`index.html`'s `window.shifaPush`,
  `firebase-messaging-sw.js`, `wasmJsMain/.../utils/PushToken.wasmJs.kt`).
- Tap → `MainScreen.kt:270-276` → `ShellViewModel.openPush(data)` → `AppNotification.fromPush` →
  `NotificationOpener.go` → `ShellUiState.pushGo: NotificationGo` (`Appointment, Patient, Document, Chat, Conversation,
  Partnerships, Partnership, Task, Inbox`) → `ShellActions.openOverHome`. **`openPush` is the one place for
  `push_opened`** (properties: `data["type"]`, target kind, whether the target was found). Foreground receipt:
  `ShellViewModel` init collects `pushes`.
- **Push `data` names patients** (`title`, `message`, `patientName`, `documentTitle`): only `type` may be sent.
- `data/push/PushRegistration.kt`: `register(session, device)` (`POST /doctors/me/fcm-tokens`), not for staff;
  permission prompt `utils/PushPermission.kt` (`AskPushPermission()` in `MainScreen`) → push permission granted / denied.
- Deep links: **none on the phones** (no intent filters besides the launcher, no URL schemes, no universal links).
  Web only: `browserPath()` → `ShellViewModel.openPath(path)` at start (`/calendar`, `/patients/12`, `/chat/5`,
  `/tasks/7`), `showBrowserPath` writes the address back, `main.kt` bridges browser Back to Escape. A tapped web push
  opens `/#push=<json>`.

### 3.6 Debug menu and demo mode (must never track)

- `data/debug/DemoBackend.kt`: `object DemoMode { enabled: StateFlow<Boolean>; set(on) }`, forced false unless
  `isDebugBuild`; `httpClient` switches to a `MockEngine` client when on (`data/api/HttpClient.kt:57`).
- `data/debug/BackendChoice.kt`: `Backend { Production, Development }`; debug builds read a saved choice, release
  builds use `Development` only when `isDevelopmentSite` (web at `doctor-dev.shifa.uz`).
- `isDebugBuild`: Android `BuildConfig.DEBUG`; iOS `Platform.isDebugBinary`; web `WEB_DEBUG && hostname is localhost`.
- Rules that follow: no sink when `isDebugBuild` (or a separate dev project), none while `DemoMode.enabled`, and a
  decision for `doctor-dev.shifa.uz` (a release bundle on the dev backend with seeded accounts: off, or tagged
  `environment = development`). The patient app does it as `sink = platform.takeIf { !optedOut && !demo }` and gives
  debug builds an empty key.
- `spikeAutoRun` (iOS `-spikeAutoRun` launch argument) also opens the debug screen.

### 3.7 App version and build per platform

| Platform | Version | Build | Source |
|---|---|---|---|
| Android | `BuildConfig.VERSION_NAME` (`1.0.1`, debug `-debug`) | `BuildConfig.VERSION_CODE` (`VERSION_CODE_OFFSET 1 + run_number`, passed as `-PversionBuild`) | `PlatformUtils.android.kt` |
| iOS | `CFBundleShortVersionString` (`MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig`) | `CFBundleVersion` (`BUILD_NUMBER_OFFSET 4 + run_number`) | `PlatformUtils.ios.kt` |
| Web | `WEB_VERSION` | `WEB_BUILD` (from `VERSION_BUILD_NUMBER` / `-PversionBuild`, default `1`: the web deploy passes neither, so every web build reports build 1) | Generated `build/generated/webBuildInfo/shifa/doctor/utils/WebBuildInfo.kt` (task `generateWebBuildInfo` in `composeApp/build.gradle.kts`), `Platform.wasmJs.kt` |

The version name lives in one place (`MARKETING_VERSION`). About → "Copy diagnostics" already assembles these.

---

## 4. Platform facts that decide the SDK choice

### Android
- `minSdk 24`, `compileSdk` / `targetSdk 36`, AGP 8.11.2, Kotlin 2.4.21, Compose Multiplatform 1.10.0, Ktor 3.4.3,
  JVM target 11. `applicationId com.shifa.doc`, namespace `shifa.doctor`.
- Application class exists: `androidMain/kotlin/shifa/doctor/ShifaDoctorApp.kt` (`initSecureSettings`, `appContext`,
  push channel). SDK setup goes in its `onCreate`. One activity: `MainActivity` (`FragmentActivity`, `singleTop`).
- Firebase: `google-services` plugin 4.4.2, BOM 34.19.0, `firebase-messaging` only; `composeApp/google-services.json`
  is in the repository (CI overwrites it from `DOCTOR_FIREBASE_ANDROID_CONFIG_BASE64`). Project
  `shifa-doctor-staging` for every build type.
- `buildFeatures { buildConfig = true }` is on, but there is **no `buildConfigField` yet** and the build script does
  not read `local.properties` (only `keystore.properties`, written by CI). `isMinifyEnabled = false` in release, so no
  R8 rules needed.
- Manifest: `allowBackup="false"`, a `networkSecurityConfig`, permissions INTERNET, ACCESS_NETWORK_STATE,
  POST_NOTIFICATIONS, CAMERA, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS, FOREGROUND_SERVICE(+_MICROPHONE), USE_BIOMETRIC.
- Dependencies go through `gradle/libs.versions.toml` only ("copy the catalog from the patient app and keep the two
  apps on the same versions": the patient app pins `posthog = "3.75.1"`, `com.posthog:posthog-android`).

### iOS
- Xcode host `iosApp/` with **Swift Package Manager** (no CocoaPods, no Podfile): `firebase-ios-sdk` ≥ 12.19.2
  (product `FirebaseMessaging`) and `daily-client-ios` ≥ 0.40.0 (product `Daily`). `IPHONEOS_DEPLOYMENT_TARGET = 18.2`.
  Bundle id `uz.shifa.doc`, team `NH97PGA9PT`.
- Kotlin framework `ComposeApp`, **static** (`isStatic = true`), targets `iosArm64` and `iosSimulatorArm64`.
- How Swift and Kotlin talk today (three bridges, all the same shape):
  1. `iosApp/iosApp/DailyVideoCall.swift`: a Swift class implementing the Kotlin interface `NativeVideoCall`,
     handed over in `AppDelegate` with `VideoCalls.shared.factory = { DailyVideoCall() }`
     (`iosMain/.../utils/VideoCall.ios.kt`).
  2. Push: `AppDelegate` calls the Kotlin object `Push.shared.onToken / onReceived / onTap`.
  3. `ContentView.swift` → `MainViewControllerKt.MainViewController()`.
  A PostHog sink fits bridge 1 exactly: a Swift `PostHogTracking` implementing a Kotlin `TrackingSink`, registered in
  `AppDelegate.application(_:didFinishLaunchingWithOptions:)` next to `FirebaseApp.configure()` (the patient app's
  `PostHogAnalytics.swift` + `AnalyticsBridge.kt`).
- `Info.plist`: usage strings for camera, microphone, photo library (+ add), location when-in-use, Face ID;
  `UIBackgroundModes` fetch, remote-notification, audio; `ITSAppUsesNonExemptEncryption = false`. **No
  `NSUserTrackingUsageDescription`** and **no app-level `PrivacyInfo.xcprivacy`** in `iosApp/`. Adding product
  analytics means updating the App Store privacy answers (and Google Play's Data safety form).
- Secrets reach the iOS build only as files: `GoogleService-Info.plist` (decoded from
  `DOCTOR_FIREBASE_IOS_CONFIG_BASE64`) and `Config.xcconfig` (tracked: team, bundle id, version). A PostHog key would
  be a new xcconfig value + Info.plist key, or a Kotlin constant shared by all platforms.
- `.github/workflows/ios_testflight.yml` (macos-15-intel): links `linkReleaseFrameworkIosArm64`, then
  `bundle exec fastlane beta` (`iosApp/fastlane/Fastfile`); it caches Swift packages, so a new package changes the
  cache key only.
- `iosApp.xcscheme` is committed and the IDE rewrites it locally: do not commit local edits to it.

### Web (Kotlin/Wasm)
- Entry `wasmJsMain/kotlin/shifa/doctor/main.kt` (`ComposeViewport("composeApp") { App() }`), page
  `wasmJsMain/resources/index.html`. The whole UI is **one canvas inside a shadow root**: PostHog autocapture, heatmaps
  and DOM session replay see nothing useful; only explicit `capture` calls work. `$pageview` would carry
  `location.pathname`, which contains ids (`/patients/12`).
- npm dependencies are declared in Gradle: `npm("@js-joda/timezone", "2.23.0")`, `npm("@daily-co/daily-js", "0.94.0")`
  and imported with `@JsModule` (`VideoCall.wasmJs.kt`). `posthog-js` can come the same way and ends up in
  `composeApp.js` (origin `'self'`); run `./gradlew kotlinWasmUpgradeYarnLock` after adding it (`kotlin-js-store/`).
- Existing JS bridges in `index.html`: `window.shifaPush` (`start(onToken, onReceived, tapped)`; loads the Firebase JS
  SDK 13.1.0 from `www.gstatic.com` on demand), `window.shifaPdf` (`render(box, url, onFail)`; pdf.js 4.10.38 from
  `cdn.jsdelivr.net`), `window.shifaPath` (set by `showBrowserPath`), an inline device-pixel-ratio cap. Kotlin calls
  them with `js("…")` blocks. A `window.shifaTrack` bridge in the same style is the alternative to `@JsModule`.
- **CSP** (`wasmJsMain/resources/.htaccess`, copied into the bundle): **`Content-Security-Policy-Report-Only`**, not
  enforced yet ("once a release shows no reports, rename it"). Current policy:
  `default-src 'self'`;
  `script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval' https://cdn.jsdelivr.net https://www.gstatic.com https://*.daily.co`;
  `connect-src 'self' https://api.shifa.uz https://api-dev.shifa.uz https://cdn.jsdelivr.net https://www.gstatic.com https://*.googleapis.com https://*.daily.co wss://*.daily.co https://tile.openstreetmap.org`;
  `worker-src 'self' blob: https://cdn.jsdelivr.net`; `img-src 'self' data: blob: https:`; `frame-src 'none'`.
  **PostHog is in neither list.** Needed before the policy is enforced: `connect-src https://eu.i.posthog.com` (and
  `https://eu-assets.i.posthog.com` if the SDK loads remote config or lazy chunks; then `script-src` too). Bundling
  through npm with the lazy extensions switched off keeps `script-src` unchanged.
- Other headers: HSTS, `X-Frame-Options DENY`, `Referrer-Policy strict-origin-when-cross-origin`, `nosniff`. The
  host's nginx caches `.js` for 30 days, which is why the deploy fingerprints `composeApp.js?v=<hash>`.
- Hostnames: `doctor.shifa.uz` (production folder) and `doctor-dev.shifa.uz` (development folder) get **the same
  bundle**; `isDevelopmentSite = (location.hostname === 'doctor-dev.shifa.uz')` picks the backend at run time. So a
  build-time key cannot tell the two sites apart; decide by hostname at run time.
- `WEB_DEBUG` is true only for `…Development…` Gradle tasks; `isDebugBuild = WEB_DEBUG && localhost`.
- Token storage on the web is `localStorage` (`SecureSettings.wasmJs.kt`); the Intelligence chats too (cleared when a
  session ends). posthog-js persistence would sit beside them.
- Deploy: `.github/workflows/web_cpanel_deploy.yml` (manual): `wasmJsBrowserDistribution`, fingerprint, Brotli, FTPS
  mirror. Secrets: `CPANEL_FTP_HOST/USER/PASSWORD` only; no build-time env values today.

### Build config and secrets, summarised
| Platform | Existing mechanism | Where a PostHog key would enter |
|---|---|---|
| Android | `BuildConfig` (only `DEBUG`, `VERSION_*`), `-PversionBuild` | `buildConfigField` in `release` from a Gradle property (`-Pposthog.apiKey.prod=${{ secrets.… }}` in `google_play_deploy.yml`), empty in `debug` (the patient app's way) |
| iOS | `Config.xcconfig`, `Info.plist`, files decoded from secrets | an xcconfig value → Info.plist, or read the shared Kotlin constant |
| Web | generated `WebBuildInfo.kt` (`WEB_VERSION`, `WEB_BUILD`, `WEB_DEBUG`) | one more generated constant from the same Gradle property |

The PostHog project key is a public write-only key (it ships inside every client), so one Kotlin constant generated for
all three targets is the smallest design; the release-only rule is what keeps debug builds silent. Existing CI secrets
are listed in `CLAUDE.md` ("Secrets"); none is for analytics. `ci.yml` runs ktlint + unit tests on Linux and Roborazzi
verify on macOS for every pull request, and the deploy workflows run no tests.

**SDK options this leaves:** (a) three native SDKs behind one Kotlin sink interface (Android SDK in `androidMain`,
Swift SDK through the bridge, posthog-js through `@JsModule`): what the patient app does for two platforms, with
lifecycle events, offline queue and feature flags for free; (b) one sink in `commonMain` posting to PostHog's capture
endpoint with the existing Ktor client: one implementation, no new dependency, no Swift, but queueing, retry,
anonymous ids and flags would be hand-written. The canvas-only web UI removes the usual reason to prefer posthog-js
(autocapture / replay).

---

## 5. Privacy constraints visible in code and docs

**What counts as patient data here (the code's own rules):**
- `CLAUDE.md` "Logging": "never log tokens, passwords, headers, request bodies or patient data (HIPAA: patient ids
  only)". `ApiHelper.kt`: the server's `message` is never kept "because it can name a patient"; paths are logged with
  ids masked (`maskIds`). `ApiException` carries only `status`, `code`, `requestId`, `details` codes.
- `Push.kt`: "Push data is never logged: it names patients." `firebase-messaging-sw.js` strips `title`, `message`,
  `patientName`, `documentTitle` before anything reaches the address bar.
- The web address shows patient, conversation and task **ids** (`shellPath`). Ids are allowed in debug logs but an
  analytics event with a patient id links a doctor's behaviour to an identifiable patient: decide explicitly (the
  patient app's plan allows usage facts only, "never a name, phone or email").
- `01-architecture-core-auth-shell.md:423` and `07-chat-notifications-tasks.md:275` list the Flutter app's logging of
  bodies and message objects as "HIPAA/security issue" bugs not to reproduce.

**Where names and free text flow (must never become event properties):**
- Names, phones, e-mails, birth dates, addresses: `ui/patients/*` (create/edit, phones, allergies, chronic diseases,
  blood group), `ui/register/*`, `ui/profile/*`, `LoginUiState.identifier`, `Screen.EmailEntry/EmailCode` (the address
  or phone and the code), clinic invitations (`inviteEmail`), `ShellRoute.PatientChat.name`,
  `ShellRoute.FindPartner.patientName`, `ShellRoute.Document.title`, `ShellRoute.PlanWizard.patientName`,
  `CalendarEntry.Appointment` inside `ShellRoute.Appointment`.
- Clinical free text: visit notes (SOAP, free notes, 025-2 fields, dental notes), AI drafts and visit facts,
  briefings, clinical-engine texts, ICD-10 codes, form follow-ups, booking and block reasons, no-show notes, task
  titles and instructions, care-partnership messages and progress updates, finance memos.
- Messages and AI: chat drafts, attachments and file names (`ui/chat/ThreadViewModel`), Intelligence prompts, replies
  and card contents (`ui/intelligence/ThreadViewModel`, `ChatSessionStore`), voice recordings and transcripts
  (`Dictation`, `VoiceSession`, scribe audio).
- Search boxes: every `query` / `type` / `search` function (patients, Home search, chat user search, clinic patients,
  ICD-10, partner search, Intelligence history, time zones) holds a typed name or diagnosis. Track "search used" and a
  result-count bucket, never the text.
- Money: finance amounts, prices, revenue share. Not patient data, but business-sensitive; decide on buckets.
- Images: before/after photos, documents, certificates, avatars, video frames. Screen recording / session replay on
  the phones would capture all of the above; keep replay and autocapture off.

**Tracking and consent in the spec:** nothing. No consent screen, no analytics opt-out, no cookie or tracking notice
anywhere in the doctor app or its spec. `ProfileSection.Privacy` (`ui/profile/PrivacyScreen.kt`) offers a data export
(`AccountRepository.exportData`, once a day) and account deletion with an e-mailed code; a deleted account should also
reset the SDK and, if required, delete the PostHog person. `ui/profile/AboutScreen.kt` shows the backend's legal links
(`GET /public/legal?lang=`: Terms, Privacy policy, Open-source licences; fallback `https://shifa.uz/#privacy`) and "My
partnership contract" (`LegalRepository.contract/contractPdf`). Registration has no terms checkbox in the ViewModel.
The patient app has `docs/posthog/privacy.md` and an opt-out (`AnalyticsSettings.optedOut`); whether doctors get an
opt-out switch (Settings) and whether the privacy policy and the doctor contract need a tracking clause are open
decisions. Doctors and staff are identified professionals, so a stable id (`doctor_<id>` / `staff_<id>`) is the
natural distinct id; patients are third parties and appear only as counts or types.

---

## 6. Test conventions and the cost of a tracker parameter

- **Injection:** constructor parameters with defaults, no DI framework (`class LoginViewModel(private val auth:
  AuthRepository = AuthRepository(), private val sessions: SessionRepository = sessionRepository) : ViewModel()`).
  Shared singletons are top-level `val xxxRepository` values. Screens build the ViewModel in their own file:
  `viewModel { XxxViewModel() }` or `viewModel(key = …) { XxxViewModel(id, …) }`; 64 such sites in `commonMain`, none in
  `androidMain`.
- **Fakes:** despite `CLAUDE.md` naming Mockito, **no test uses `mock`**. Tests build the real repository over a Ktor
  `MockEngine` (92 test files) and `MapSettings`, and record what was sent in plain lists
  (`private val sent = CopyOnWriteArrayList<String>()`, `urls`), with a mutable `reply` lambda for the next answer.
  Function-typed seams (`clock: () -> PracticeClock`, `language: () -> String`, `makePdf`, `now`) are replaced by
  lambdas. A recording tracker (`class RecordingTracker : Tracker { val events = mutableListOf<TrackEvent>() }`) is
  the same style and matches the patient app's "recording fake in tests".
- **Shape:** JUnit4, `Dispatchers.setMain(UnconfinedTestDispatcher())` or `StandardTestDispatcher`, Truth, Turbine in
  places, `created.stopAll()` from `androidUnitTest/kotlin/shifa/doctor/TestViewModels.kt` before `resetMain`, a
  `settle()` loop because `MockEngine` answers on another thread. Class under test is built by a private
  `vm()` / `opened()` helper per test class.
- **Churn of `tracker: Tracker = Tracking` as a last constructor parameter:** 70 ViewModels (plus 5 plain classes:
  `VideoVisit`, `VisitNotes`, `VisitPlan`, `DentalNotes`, `Dictation`, `VoiceSession` if they track themselves). With a
  default value **no existing call site changes**: 64 production sites and about 150 constructor calls in 62 test
  files keep compiling (no test passes a trailing lambda to a ViewModel constructor, so appending a parameter after a
  function-typed one is safe). Test work is only the new assertions: one helper line per test class
  (`vm()` gains `tracker = recorder`) in the 60 `*ViewModel*Test.kt` files, wherever events are asserted.
- A default of the real singleton means tests that do not pass a recorder call the real object: it must be inert
  without a platform sink (the patient app's `platform` stays null in tests and previews).
- Screenshot tests (31 `*ScreenshotTest.kt`, 31 `*Previews.kt`) use the stateless `XxxContent` composables and never
  build ViewModels: unaffected, unless a `TrackScreen`-style composable is put inside a `Content` function (keep such
  calls in `XxxScreen` or in `MainScreen`).
- ktlint fails the build (`./gradlew ktlintFormat`); `StringsTest` checks every string key in three locales and that
  every character exists in the fonts (matters only if an opt-out switch adds strings: source is
  `docs/kmp-spec/strings/{en,uz,ru}.json`, 2950 keys, generated by `scripts/strings_from_json.py`; hand-written kit
  labels in `composeResources/values*/strings_kit.xml`).
- `DemoBackendTest` requires a demo handler for every new backend endpoint; tracking adds none.

---

## 7. Suggested split for parallel work

One serial foundation step, then nine groups that touch disjoint folders. Sizes are lines under `ui/` (the matching
`data/<feature>/` folders are small and normally need no change, since events are emitted from ViewModels).

**Step 0, Foundation (one worker, merged first):**
`data/tracking/` (new: `Tracker` interface, `Tracking` object, `TrackingSink`, `Throwable.toFailureReason()`, the
event base type, one **empty event file per group**), the three sinks (`androidMain`, `iosMain` + `iosApp/*.swift` +
`project.pbxproj`, `wasmJsMain`), `App.kt` (session collector, signed-out screen views, demo/debug guard),
`ui/navigation/*` (screen views from `ShellUiState`, tab / More / Intelligence, `openPush`), `data/api/ApiHelper.kt` or
`ErrorMessages.kt`, `ShifaDoctorApp.kt`, `AppDelegate.swift`, `composeApp/build.gradle.kts`,
`gradle/libs.versions.toml`, `.htaccess` (CSP), the three deploy workflows, `ui/debug/*` (recent-events list), the
test `RecordingTracker`, and the tracking docs. Owns `ShellViewModel` (19 actions) and its test.

| Group | Folders (exclusive) | Lines | ViewModels | Notes |
|---|---|---|---|---|
| 1. Auth and lock | `ui/splash`, `ui/login`, `ui/emailcode`, `ui/register`, `ui/auth`, `ui/applock` | 3860 | 10 | Sign-in method lives here; session events come from Step 0 |
| 2. Home, inbox, reports, tasks | `ui/home`, `ui/notifications`, `ui/analytics`, `ui/tasks` | 7253 | 9 | `NotificationOpener` is shared with the shell: Step 0 tracks the push, this group the inbox and Home taps |
| 3. Calendar and schedule | `ui/calendar`, `ui/schedule` | 5873 | 8 | `CalendarScope` (on behalf of a colleague) is a property here; group 8 adds nothing for `ClinicSchedule.kt` |
| 4. Patients and care | `ui/patients`, `ui/care` | 5274 | 9 | Includes patient AI summary and clinical timeline |
| 5. Documents and forms | `ui/documents`, `ui/forms` | 4024 | 6 | Clinical engine included |
| 6. Visit, video, scribe, dictation | `ui/visit`, `ui/speech` | 6911 | 3 + 6 helper classes | The densest logic (`VisitViewModel` 926 lines, End pipeline, `VideoVisit`); `Dictation` is also used by forms, but its file is here |
| 7. Chat and Shifa Intelligence | `ui/chat`, `ui/intelligence` | 5831 | 5 + `VoiceSession` | Two `ThreadViewModel` classes with the same name in different packages |
| 8. Clinic workspace | `ui/clinic` | 7713 | 8 | Could be halved (finance = `ClinicFinance*.kt` 2352 lines; plans = `ClinicPlans.kt` + `PlanWizard.kt` 2627 lines) if a tenth worker is available |
| 9. Profile, settings, billing, legal | `ui/profile` | 5831 | 11 | Owns an opt-out switch and its strings if one is decided |

Check: 10 + 9 + 8 + 9 + 6 + 5 + 3 + 8 + 11 + 1 (shell, Step 0) = 70 ViewModels. Tests follow the same folders under
`androidUnitTest/kotlin/shifa/doctor/ui/<folder>/`, previews under `androidMain/.../ui/<folder>/`.

**Files every group would otherwise touch, and how to avoid it:**

| Shared file | Why it attracts edits | Avoidance |
|---|---|---|
| A central event catalogue (the patient app's single 883-line `AnalyticsEvent.kt`) | Every group adds events | One file per group, created empty in Step 0 (`data/tracking/events/AuthEvents.kt`, `CalendarEvents.kt`, …), or the whole catalogue is written and merged in Step 0 from the event plan and groups only call it |
| `ui/navigation/MainScreen.kt` (1133 lines; constructs every screen, passes callbacks) and `ShellViewModel.kt` | Screen views, "opened from" sources | Step 0 only. A group that needs an entry-point property (`source = home / calendar / notification`) asks for a route field instead of editing the shell, or tracks on arrival inside its own ViewModel |
| `App.kt` | Signed-out screen views, identify/reset | Step 0 only |
| `data/api/ApiHelper.kt`, `ErrorMessages.kt`, `ApiError.kt` | Failure-reason mapping | Step 0 provides `toFailureReason()`; groups only call it |
| `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `iosApp.xcodeproj/project.pbxproj`, `index.html`, `.htaccess`, workflows | SDK and key | Step 0 only |
| `docs/kmp-spec/strings/*.json` and the generated `strings.xml` (regenerated wholesale by a script) | Only if tracking adds visible text (an opt-out row, a consent note) | Group 9 alone, or Step 0; no other group needs strings |
| `docs/implemented/README.md` (status table + log line "at the end of every job, in the same commit") and the area files | The repo rule makes every job edit it | Each group writes its notes to its own new file (for example `docs/implemented/tracking/<group>.md`); the coordinator adds the README rows once at the end |
| `data/debug/DemoBackend.kt`, `DemoBackendTest` | New endpoints only | Not needed: tracking adds no backend call |
| `ui/widgets/*` (41 files, shared kit) | Tempting place for a generic "tracked button" | Leave alone; events belong in ViewModels |
| Shared ViewModels used across groups: `Dictation` (`ui/speech`, used by visit and forms), `NotificationOpener` (`ui/notifications`, used by Home and the shell), `PatientPickerSheet` / `CreatePatientSheet` (`ui/patients`, used by booking, tasks, finance), `MapPicker` (`ui/profile`, used by locations), `UploadSheets` and `PatientDocuments` (`ui/documents`, used by the visit and the patient record), `AnalyticsSection` (`ui/analytics`, shown on Home) | The owner's events fire from wherever the piece is hosted | The owning group instruments it once and takes a `source` value from its existing constructor arguments (`clinicId`, `appointmentId`, `scope`); hosts do not add their own copies |
| Git | One branch per repo is the workspace rule | Either one branch with a worktree per worker rebased in group order, or the coordinator merges group patches; with disjoint folders the only real conflicts are the files in this table |

**Decisions the event plan needs before the groups start** (they change property lists everywhere): the distinct id
(JWT `sub` works for doctors and staff); whether patient / appointment / document ids may be sent at all; whether money
is sent as buckets; whether `doctor-dev.shifa.uz` and TestFlight / internal-track builds report, and to which project
(the existing PostHog project is EU, id 332380, shared with the patient app through the `app` property); whether
doctors get an opt-out; native SDKs versus one Ktor sink.
