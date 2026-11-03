> **Status:** analysis baseline, written 2026-10-24 from the Flutter code at commit `a5ace33` (no access to backend or Figma). It is the behavioural spec for the KMP rewrite: API shapes, flows, rules, quirks and the tickets at the end. Refine it per ticket while implementing (add Figma node IDs, confirm UNVERIFIED items against `<BASE_URL>/v3/api-docs`), and fix the doc in the same PR when the implementation differs. Paths are Flutter sources on `main`, for traceability only.

# 02 · Home dashboard, Analytics, Shifa AI / Intelligence, Clinical engine, Patient briefing

## 0. Delta since baseline — merge `4d05e61` ("shifa intelligence", PR #16, 2026-10-24)

Only this area changed (15 files, +3 415/−603). What the rewrite must pick up:

| Change | Where | Effect on the KMP spec |
|---|---|---|
| `conversationId` sent with every `POST /api/ai/morning/stream` body (the local `IntelligenceChatSession.id`) | `ai_api.dart`, `intelligence_screen.dart:190` | AI-01 request DTO gains `conversationId: String?`; INTEL-04 passes the active session id. Backend presumably keys server-side memory/cancel-confirmation state on it, so a **new local session must mint a new id** and deleting a session must not reuse one. |
| `recordingId` (`rec_<epochMs>`) minted at `VoiceRecorder.start()` and sent as a multipart field of `/api/ai/transcribe`; logged with byte/char counts only | `doctor_voice_recorder_session.dart`, `doctor_transcription_api.dart`, `home_voice_session.dart` | AI-03 exposes `recordingId`; AI-05 adds the optional field; log lines `intelligence_stt stage=UPLOAD|TRANSCRIPT_READY recordingId=… bytes=… durationSec=…` are the support-debug contract (no audio, no transcript text). |
| Web recorder switched WAV → Opus/WebM (fallback AAC) 32 kbps; comment explains WAV froze the page after 20–30 s | `doctor_voice_recorder_session.dart` | Web-only; mobile already AAC-LC `.m4a`. §10 row "Audio record" updated; nothing to port. |
| Uzbek turns start browser STT with `interimResults=false`; live caption is suppressed and the single server transcript is committed | `home_voice_session.dart` (`_recognizesUzbek`), `web_speech_conversation_*.dart` | Web-only (browser speech is not ported, AI-08). Keep the *product rule*: **for Uzbek never show a live interim caption; show the transcript once, after the server returns**. |
| `IntelligenceChatTitles.detectReplyLanguage(text)` — per-turn language detection: Cyrillic Uzbek letters → UZ, other Cyrillic → RU, 28 Uzbek marker substrings → UZ, ≥2 English function words → EN, else `null` (keep session language). Fixes Uzbek date fragments ("24-oktabr soat 8:00 ga") flipping the thread to English | `intelligence_chat_models.dart:191-253`, test `intelligence_chat_models_test.dart` | INTEL-01: port verbatim with its test (`'8:30'` → null, `"8:30 to'g'ri keladi."` → UZ, `'What is my schedule for today'` → EN). Used by `_resolveReplyLanguage` before the UI-locale fallback. |
| New `intelligence_card_text.dart` (2 266 lines incl. tables): client-side localisation of tool-card text the backend sends in English — `intelligenceStatusLabel` (COMPLETED/CONFIRMED/CANCELLED/REQUESTED/IN_PROGRESS/PENDING/SCHEDULED/NO_SHOW → l10n), `intelligenceDateLabel`/`intelligenceFieldLabel` (ISO or English dates like `Tue 24 Oct 2026` → `24 Okt 2026`), `intelligenceRangeLabel` (`Today/Tomorrow/This week/Next week/Upcoming (from …)`), `intelligencePatientLabel`/`intelligenceDocumentLabel`/`intelligenceFactTypeLabel`, `intelligenceDateTimeLabel`, `localizeIntelligenceHint` (exact-sentence + regex map of the backend's cancellation hints, booking/reschedule sentences, embedded calendar words), `intelligenceChatTimestamp` (Today/Yesterday/date in app language). 196-line golden test | `features/intelligence/presentation/intelligence_card_text.dart`, test `intelligence_card_text_test.dart` | New ticket **INTEL-10 · Card text localiser** (S, depends INTEL-05): port as `IntelligenceCardText.kt` + the golden test. It is a stop-gap: raised as two backend requests in doc 15 §4 (codes instead of sentences; ISO/enums instead of English words). Until the backend changes, the Kotlin port must match the Dart regexes exactly or uz/ru cards regress. |
| **Cancellation confirmation flow** is backend-driven and surfaced through those hints: ask-which → preview ("I can cancel N upcoming visits: A, B. [K cannot be cancelled and will be left as they are.] Say yes to cancel them. Nothing has been cancelled yet.") → user says yes → backend cancels; variants "too many (>N), narrow the period", "none eligible", "nothing cancelled", "no cancellation waiting", "could not find that visit" | `intelligence_card_text.dart:205-262` | Document in INTEL-06 (not a client intent: the client **never** cancels locally, it only localises the sentence and forwards the user's "yes" with the `<appointment_action>{token}</appointment_action>` tag already described in §3.3). UX flow: `ux-flows/03-shifa-intelligence/cancel-appointments-by-voice.md`. |
| Action cards localised: no-match / did-you-mean / other-times / confirm-time / missing-patient-or-slot / booking-failed `{code}` / reschedule-failed `{code}` / documented-facts `{count}`; `propose_reschedule` card shows "Please confirm this exact reschedule in the conversation." when no slot is pre-selected | `intelligence_action_cards.dart` | Removes most of §11.3 bullet 2. Keys listed in `strings/en.json` (`intel*`). |
| Visit facts panel: title `visitFactCards` "Visit facts from the call" + hint "Only what was spoken. Review, then document everything."; rejected/dropped group `droppedFacts` "Dropped (not said)"; primary button `documentEverything` → `documenting…`; after commit `factsAlreadyDocumented`; patient card `clinicalTimeline` + hint; fact rows `factTypeLabel · text`, `Schedule: {hint}`; Keep/Reject tooltips; commit message "Documented N facts and drafted M medication tasks. They are on the patient timeline." | `visit_facts_panel.dart` | AI-10 copy updated; `_vf(l10n, key, fallback)` pattern still used (keys exist now). |
| Voice-session copy now localised: `intelSpeakNow`, `intelPauseToSend`, `intelRecordingHold` ("Recording… tap the check when you are done. Pauses are fine."), `intelDictateHint`, `intelTapToSendRecording`, `intelTranscribingRecording`, `intelSendingRecording`, `intelNoSpeech*`, `intelTranscriptionFailed/Timeout`, `intelMicPermission[Retry]`, `intelCouldNotStart/ReopenMic`, `intelSpeechRequiresPro`, `intelSpeechProOrDenied`, `intelVoiceSlow`, `intelVoiceSkipped`, `intelStopped`, `intelTookTooLong` (75 s timeout) | `home_voice_session.dart` | §11.3 bullet 1 mostly resolved; AI-08/INTEL-09 use these keys. |
| Inline dental chart card: patient/date/service labels localised | `intelligence_inline_viewers.dart` | INTEL-07 copy. |

| **Client-side booking intents removed.** `_tryCompletePendingBooking`, `_maybeAutoBookFromTool`, `_executeBook`, `extractClockTime` and the affirmative/negation sets are gone from `home_voice_session.dart` (12 references at `a5ace33`, 0 now). `propose_appointment` / `propose_reschedule` cards now branch on `payload.workflowId`: when present the card is **read-only** ("Confirm this exact booking with Shifa." / "Please confirm this exact reschedule in the conversation.") and the **backend** books after the doctor says "yes" in the next turn, replying with `booked: true` + `statusMessage`; the local REST buttons (`POST /api/schedule/book`, `PUT /api/appointments/{id}/change`) remain only for payloads **without** `workflowId` (legacy) | `intelligence_action_cards.dart:707, 849` | §3.6 is historical. INTEL-06 shrinks: no `IntentResolver`, no feature flag; the "implicit AI booking" decision is resolved — the server owns confirmation. Keep the legacy button path until the backend always sends `workflowId`. |

Still true after the merge: `_aiSub` is declared and cancelled but **never assigned** (dead field; the HTTP stream is still not closed on stop, §11.2); `_vf` fallback pattern remains; `propose_reschedule` still omits `locationId`.


Source: `/Users/sheroziy.saidkhodjaev/Projects/Private/web-flutter-doctor` @ `a5ace33` (read-only). All paths below are relative to `lib/` unless they start with `test/`. Anything not confirmed from code is marked **UNVERIFIED**.

Key framing for the rewrite (Android + iOS only):

- The **live** Home screen is `features/home/presentation/home_screen.dart` + `home_clinical_workflow.dart` + `home_attention_center.dart` + `home_activity_feed.dart` + `home_analytics_section.dart`. Roughly half of `features/home/presentation/widgets/**` is dead (see §11).
- The **live** AI surface is `IntelligenceScreen` → `HomeVoiceSession(embedded: true)`. `HomeAiCopilot`, `AskShifaAiOverlay`, `DoctorAiPanel` are dead.
- The voice loop is built around **browser `SpeechRecognition`** ("Alexa mode"). On native mobile that branch is compiled out (`_alexaMode == false`, `home_voice_session.dart:126-127`), so the mobile behaviour to port is the *recorder mode* + *dictation mode* + server STT/TTS.
- `PatientBriefingPanel` is rendered only when `!PlatformLayout.isNativeMobile` (`features/shell/presentation/main_shell.dart:721-728`) — on today's Android/iOS the briefing result is **never shown**. Rewrite must give it a mobile surface.

---

## 1. Home screen — render order, data, states

### 1.1 Shell wiring
- `HomeScreen` is tab `DoctorShellTab.home = 1` (`features/shell/domain/doctor_shell_tab.dart`). Tabs: chat 0, home 1, calendar 2, patients 3, clinic 4, tasks 5, reports 6, notifications 7, profile 8.
- `HomeScreen` is a `ConsumerStatefulWidget` holding one piece of local state: `_selectedAppointmentId` (`home_screen.dart:28`). Selection only highlights a timeline tile; nothing else consumes it.
- `ref.listen(shellProvider)` inside `build` (`home_screen.dart:49-53`): when the tab becomes home → `refreshHomeDashboardOnTabFocus(ref)`.

### 1.2 Render order (`home_screen.dart:69-110`)
```
HomeSearchShortcut (Cmd+K / Ctrl+K → HomeSearchOverlay)           home_search_overlay.dart:133-160
└ Scaffold(bg scaffoldBackground)
  └ RefreshIndicator(onRefresh: refreshHomeDashboard)
    └ CustomScrollView(AlwaysScrollable) → SliverToBoxAdapter → Padding(Responsive.screenPadding)
      1. HomeHeader(onNotificationsTap → setTab(notifications))    home_header.dart
      2. SizedBox(AppDesignSystem.itemGap = 12)
      3. if useSinglePane:  workflow, gap(20), insightsColumn
         else:              Row[ Expanded(flex 7: workflow), 20px, Expanded(flex 3: insightsColumn) ]
           workflow       = HomeClinicalWorkflow(selectedAppointmentId, onAppointmentSelected)
           insightsColumn = Column[ HomeAttentionCenter, gap(20), HomeActivityFeed ]
      4. SizedBox(sectionGap = 20)
      5. HomeAnalyticsSection
      6. SizedBox(Responsive.bottomNavClearance + 24)
```
Responsive breakpoints (`core/layout/responsive.dart`):
- `useSinglePane` = mobile shell **or** `(width − sidebar) < 720` (`responsive.dart:88-93`). Mobile shell = native Android/iOS always, or phone web (`width < 768 && shortestSide < 600`).
- `useCompactToolbar` = mobile shell or content width `< 980` (`responsive.dart:96-101`).
- `screenPadding`: mobile shell `LTRB(12,8,12,8)`; tablet (768–1100) `LTRB(16,12,16,12)`; else `all(24)` (`responsive.dart:103-112`).
- `sectionGap` 12 (mobile/tablet) or 16 (`responsive.dart:114-115`); the Home screen itself uses the constants `AppDesignSystem.sectionGap = 20`, `itemGap = 12` (`core/theme/app_design_system.dart:75-76`).
- **On native mobile the layout is always the single-column variant.**

### 1.3 HomeHeader (`home_header.dart`)
- Data: `profileAllProvider.valueOrNull?.profile` → `firstName`, `lastName`, `photoUrl` (lines 24-30); `doctorNotificationsUnreadCountProvider` (GET `/api/notifications/unread/count`, `state/notifications/doctor_notification_actions.dart:38`).
- UI: `PersonAvatar(radius 22)`, title `l10n.home` (h1, 20sp), doctor name via `formatDoctorMedName`, 44×44 `IconButton` with `Badge(isLabelVisible: unread>0, smallSize 8, destructiveRed)` (lines 35-62, 66-94).
- No search button on this header (search is keyboard-only, §1.9).

### 1.4 HomeClinicalWorkflow (`home_clinical_workflow.dart`)
Providers watched (lines 87-102): `todayAppointmentsProvider`, `nextUpcomingAppointmentProvider`, `doctorNotificationsUnreadCountProvider`, `tasksProvider` (filter status active|draft), `profileAllProvider` (`profile['timeZone']`).

Derived (lines 96-109):
- `valid` = today's appointments with non-empty `id` and `patientName`.
- `now = getNowInTimezone(doctorTimeZone)`.
- `_classify` (lines 238-268): `current` = first non-completed appt with `start <= now < end` (times built with `timeOfDayToDateTimeInZone(appt.start, appt.day ?? today, tz)`); else `nextUp` = first non-completed with `start >= now`.
- `hero = current ?? nextUp ?? nextUpcomingAsync.valueOrNull` (today first, else next 14 days — `today_appointments_provider.dart:121-143`).
- `listItems` = `valid` where `!isCompleted`.

Render order:
1. **Metric row** (lines 114-153): three `OverviewMetricCard` (`overview_metric_card.dart`, icon 28px tile + caption label + 22sp value + secondary caption):
   - `metricToday` / `${valid.length}` / `visitsCountNoun(n)` → `setTab(calendar)`
   - `notifications` / `$unread` / `unreadCountLabel` → `setTab(notifications)`
   - `metricFollowUp` / `$followUpCount` / `followUpTask(s)` → `setTab(tasks)`
2. gap 20
3. **Next patient hero** (lines 157-172):
   - loading (either provider) → `DashboardSkeleton(height 180, lines 4)` (shimmer bars, `dashboard_card.dart:81-169`)
   - `hero != null` → `_NextPatientSlot` → `NextPatientCard` (`next_patient_card.dart`):
     - `patient = patientByIdProvider(appt.patientId)` (GET patient, `state/patients/patients_provider.dart:41`) for age.
     - `startEnabled = start <= now + 15 min || isNow` (`home_clinical_workflow.dart:299-300`).
     - `dateLabel` = `MaterialLocalizations.formatMediumDate(day)` only when the visit is not today (lines 302-306).
     - Card: title `nextPatient` (brand), `_StatusBadge` (`now` if isNow/inProgress; `confirmed`; else `waiting`), avatar r28, name (17sp, 2 lines), line `[dateLabel] · HH:mm · "age N"`, two `_MetaPill`s (visit type: `onlineVisit`/`inClinic`; place: `videoCall` or `location` or `inClinic`), `ShifaPrimaryButton(startAppointment, enabled: startEnabled)`, `ShifaSecondaryButton(openPatientCard)` (`next_patient_card.dart:47-148`). Age = full birthday-aware calc (`next_patient_card.dart:168-178`).
     - `onStart` (`home_clinical_workflow.dart:44-72`): if `patientByIdProvider` is already loaded and `general.chronicDisease` non-empty and ≠ `'None'` → `showChronicDiseaseWarning` dialog; then `ShellScope.pushNamed(context, appt.isVideo ? AppRoutes.videoCall : AppRoutes.inPerson, arguments: appt)`.
     - `onOpenChart` (lines 74-82): `onAppointmentSelected(appt)`; `setTab(patients)`; `ShellScope.pushIntoShell(patientId)` (opens detail on mobile).
   - else → `_EmptyNextPatient` (`noAppointmentsToday` + `ShifaPrimaryButton(newAppointment)` → `setTab(calendar)`) (lines 320-354).
4. gap 20
5. **Quick actions** (lines 176-205): h2 `quickActions`, 44px horizontal `ListView` of `QuickActionChip` (`quick_action_chip.dart`, pill 24r, icon 18 brand + body2):
   | chip | action (`features/home/application/home_quick_action_navigation.dart`) |
   |---|---|
   | `newAppointment` | `setTab(calendar)` (l.16-18) |
   | `videoCall` | `calendarQuickBookIntentProvider = CalendarQuickBookIntent(preferVideoConsultation: true)` then `setTab(calendar)` (l.20-24) |
   | `issuePrescription` | `openCreateRemoteCareTaskForm`: if `!doctorFeatureProvider(remoteCareTasks)` (PREMIUM) → `SnackBar(l10n.error)`; else `ShellScope.pushNamed(AppRoutes.createTask)` (l.80-94) |
   The file also has `openCreatePatientForm` (create-patient sheet), `openPatientsForDocumentUpload` (`setTab(patients)`), `openCreateTreatmentPlanForm` (clinic workspace check → `TreatmentPlanWizardSheet`) — only reached from **dead** `HomeQuickActions`/`HomeFloatingQuickActions`.
6. **Today's schedule** (lines 207-233): if `appointmentsAsync.hasError` → `Text('${l10n.error}: $e')`; else if `listItems` non-empty → h2 `todaysSchedule`, body2 `todayTimelineSubtitle`, column of `AppointmentTimelineTile` (`appointment_timeline_tile.dart`): 48px time column (`start.format(context)`), 10px dot + 2px connector (hidden on last), card with avatar r18, name, visit label (`onlineVisit` | location | `inClinic`), status chip: completed→`complete`/success, inProgress→`now`/brand, confirmed→`confirmed`/warning, cancelled→`appointmentStatusCancelled`/`Color(0xFFDC2F2F)` (hardcoded, l.222), else `waiting`/info. Tap → `onAppointmentSelected` (highlight only). Completed appts are not listed; when all are completed the section disappears (no empty state).

### 1.5 HomeAttentionCenter (`home_attention_center.dart`)
- `initState` → `Future.microtask(tasksProvider.notifier.loadTasks())` (GET `/api/tasks`, `state/tasks/task_actions.dart:94`) (l.26-32).
- Watches `profileAllProvider` (tz), `doctorNotificationsProvider` (GET `/api/notifications`), `tasksProvider`.
- `DashboardCard(title attentionRequired, subtitle attentionRequiredSubtitle)`; loading → `DashboardSkeleton(160, 3)`; error → `Text(l10n.error)`; data → items = unread notifications `.take(4)` + active tasks `.take(3)`, then `.take(6)` (l.96-125). Empty → check icon 40 + `allCaughtUp` (l.58-75).
- `_AttentionTile`: 36px icon tile (icon by type: DOCUMENT_* → description, APPOINTMENT_MISSED/CANCELLED → event_busy, else bell), title (localized via `localizedNotificationTitle`), subtitle (`localizedNotificationMessage(tz)`), time (`formatNotificationTime`), chevron (l.154-229).
- Tap notification → `markAsRead(id)` (PATCH/POST `/api/notifications/{id}/read` — see `doctor_notification_actions.dart:52`) + `navigateToNotificationTarget(n)` (l.127-130). Tap task → `ShellScope.pushNamed(AppRoutes.taskDetails, arguments: task.id)` (l.132-138).

### 1.6 HomeActivityFeed (`home_activity_feed.dart`)
- `DashboardCard(patientActivity / patientActivitySubtitle)`; loading skeleton(180,4); error `Text(l10n.error)`; data: notifications sorted by `createdAt` desc, `.take(8)`; empty → `noRecentActivity` (l.31-43).
- Row: time caption (tabular figures) | 8px dot coloured by type (`contains('DOCUMENT')`→info, `APPOINTMENT`→teal, `TASK`→warning, else tertiary) | title (w500 14) + message (2 lines). Not tappable (l.66-143).

### 1.7 HomeAnalyticsSection (`home_analytics_section.dart`) — see §2 for data
- `DashboardCard(clinicPerformance / clinicPerformanceSubtitle)` → `HomeDashboardToolbar(exporting, onExport)` → 16px → `AnalyticsKpiCards` → if `doctorFeatureProvider(advancedAnalytics)` (PREMIUM): `AppointmentsTrendChart`, `VisitTypeDonutChart`, `AnalyticsEngagementWidget` separated by `Responsive.sectionGap` (l.58-83).
- Export: `_export()` guards re-entry, calls `homeDashboardExportServiceProvider.exportCsv()`, SnackBar `exportStarted` / `exportFailed` (red) (l.27-50).

### 1.8 Date-range picker + export (`home_dashboard_toolbar.dart`, `home_dashboard_date_range_provider.dart`, `home_dashboard_export_service.dart`)
- State: `homeDashboardDateRangeProvider` (`StateNotifierProvider<DashboardDateRangeController, DashboardDateRange{start,end}>`), default `end = today`, `start = today − 6` (7 inclusive days) (`…date_range_provider.dart:24-29`). `setRange` normalises to midnight and rejects `end < start` (l.31-36). `dayCount = end−start+1`.
- Picker: `showDateRangePicker(firstDate: now−365d, lastDate: now+30d, initialDateRange: current, helpText: selectDateRange)` (`toolbar.dart:26-32`).
- Label: `formatDashboardDateRange(range, locale)` → `"d MMM – d MMM, yyyy"` with **English month abbreviations regardless of locale** (both branches of the ternary are `'d MMM'`, l.44-62).
- Layout: compact → `Column[DateRangeButton, 8, ExportButton]`; else `Row(min)` (l.48-69). Buttons: `OutlinedButton.icon(calendar_month)` / `FilledButton.icon(download)` with 16px spinner while exporting.
- Export format: **CSV** (`home_dashboard_export_service.dart:18-62`):
  ```
  SHIFA Dashboard Export
  Period,<start ISO8601>,<end ISO8601>

  Metric,Value
  Appointments today,<overview.appointmentsToday>
  Completed today,…  Cancelled today,…  New patients today,…
  Active patients (30d),<engagement.activePatients>
  Documents received (30d),<engagement.documentsReceived>
  Video consultations,<types.video>
  In-person consultations,<types.inPerson>

  Date,Appointments
  <point.date>,<point.count>   (one per trend point)
  ```
  Filename `shifa_dashboard_<yyyy-MM-dd>_<yyyy-MM-dd>.csv`. Mobile → `Share.shareXFiles([XFile.fromData(bytes, mimeType text/csv)], subject: 'Shifa dashboard export')`; web → anchor download (`features/admin/presentation/admin_pdf_downloader_web.dart`). It awaits the four analytics futures, so export fails if any analytics call fails.

### 1.9 Search overlay (`home_search_overlay.dart`)
- Trigger: `HomeSearchShortcut` wraps the screen in `Shortcuts` for `meta+K` and `control+K` (l.140-146) + `Focus(autofocus: true)`. **No touch entry point** on the live header → unreachable on mobile.
- `HomeSearchOverlay.show` → `showDialog(barrierColor black54)` → `SafariSafeDialog` → width `Responsive.overlayWidth(context, 520)`; `TextField` autofocus with hint `searchPatientsAppointmentsHint`; results from the in-memory `patientsProvider` list: empty query → first 8; else `name.toLowerCase().contains(q)` take 12 (l.50-57). Max list height 360.
- Row: `CircleAvatar(initial)`, name, `general.phone`. Tap → `Navigator.pop` + `setTab(3)` (hardcoded patients index) — **does not open the tapped patient** (l.110-113). `Escape` dismisses.

### 1.10 Refresh triggers (`features/home/application/home_dashboard_refresh.dart`)
- Module-level globals `_lastAutoRefreshAt`, `_refreshInFlight`, cooldown 60 s (l.8-11).
- `refreshHomeDashboardOnTabFocus` (l.15-37): skip if in flight or < 60 s since last; invalidates `doctorNotificationsProvider`, `doctorNotificationsUnreadCountProvider`; awaits `invalidateAppointmentRelatedProviders(ref)`; `tasksProvider.loadTasks()`.
- `refreshHomeDashboard` (pull-to-refresh, l.40-55): same + explicit `doctorAnalyticsOverviewProvider` invalidate; no cooldown.
- `invalidateAppointmentRelatedProviders` (`state/appointments/appointment_invalidation.dart:38-106`): resolves doctor tz (`meProfileProvider` → fallback `profileAllProvider`), force-reloads **today** (and optionally `appointmentDay`) in `calendarProvider`, then invalidates `todayAppointmentsProvider`, `nextUpcomingAppointmentProvider`, `doctorAnalyticsOverviewProvider`, and clinic finance / treatment-plan providers.
- `doctorAnalyticsOverviewProvider` also `ref.watch(todayAppointmentsProvider)` so KPIs refetch whenever today's list changes (`home_analytics_providers.dart:17-18`). It throws `StateError('Not authenticated')` when `authTokenProvider` is null/empty (l.12-14).
- `todayAppointmentsProvider` (`features/appointments/application/today_appointments_provider.dart:103-117`): needs `profile['timeZone']` (else `[]`); reads `calendarProvider[todayKey]` cache, loads the day if missing. `nextUpcomingAppointmentProvider` (l.121-143): `pickNextUpcomingAppointment(today)` else loops **day+1 … day+14 sequentially**, each possibly a calendar fetch.

### 1.11 Items requested but not on the live Home
- **Current appointment hero** (`home_current_appointment_hero.dart`), **timeline section** with horizontal upcoming strip + completed rows (`home_timeline_section.dart`), **reminders panel** (`home_reminders_panel.dart`), **greeting header** with summary chips / "next appointment in N min" (`home_greeting_header.dart`), **floating speed-dial** (`home_floating_quick_actions.dart`), **wrap quick actions** (`home_quick_actions.dart`), **AI copilot card** (`home_ai_copilot.dart`): none are imported by any live file (see §11.1). Their behaviour is summarised there only where it informs the rewrite (video join window rule, briefing sheet hooks).

---

## 2. Analytics

### 2.1 Endpoints (`features/home/application/doctor_analytics_service.dart`)
| Method | Path | Params | Model |
|---|---|---|---|
| GET | `/api/doctor/analytics/overview` | — | `DoctorAnalyticsOverview` |
| GET | `/api/doctor/analytics/appointments-trend` | `days` = `range.dayCount.clamp(1, 90)` (`home_analytics_providers.dart:26-27`) | `List<AppointmentTrendPoint>` |
| GET | `/api/doctor/analytics/consultation-types` | — | `ConsultationTypes` |
| GET | `/api/doctor/analytics/engagement` | — | `DoctorEngagement` |
| GET | `/api/doctor/analytics/sms-usage` | `from`, `to` = `yyyy-MM-dd` of range start/end (`l.41-50`) | `DoctorSmsUsage` |

All: non-200 → `Exception('<label>: <status> <body>')`; body decoded as UTF-8 JSON. `_api` is typed `dynamic` (`doctor_analytics_service.dart:15`).

### 2.2 Providers (`home_analytics_providers.dart`)
- `doctorAnalyticsOverviewProvider` — `FutureProvider.autoDispose`, depends on token + `todayAppointmentsProvider`.
- `doctorAnalyticsTrendProvider` — `FutureProvider` (not autoDispose), depends on date range.
- `doctorConsultationTypesProvider`, `doctorEngagementProvider` — plain `FutureProvider`, never invalidated by any refresh path.
- `doctorSmsUsageProvider` — `autoDispose`, depends on date range; consumed by `AnalyticsSmsUsageWidget`, which is used on `features/reports/presentation/reports_screen.dart`, **not** on Home.
- Lines 53-73: legacy stub providers with hardcoded numbers (`todayAnalyticsProvider`, `clinicalLoadProvider`, `aiImpactProvider`, `appointmentsTrendProvider`, `visitTypeSplitProvider`) — dead, do not port.

### 2.3 Response models (`features/home/domain/analytics_models.dart`) — all fields non-null with defaults
- `DoctorAnalyticsOverview { appointmentsToday:int=0, completedToday:int=0, cancelledToday:int=0, newPatientsToday:int=0 }`
- `AppointmentTrendPoint { date:String ('yyyy-MM-dd', ''), count:int=0 }`
- `ConsultationTypes { video:int=0, inPerson:int=0; total }`
- `DoctorEngagement { activePatients:int=0, documentsReceived:int=0 }` (30-day windows per widget copy)
- `DoctorSmsUsage { sentCount:int=0, totalCostMinor:int=0, currency:String='UZS', pricePerSmsMinor:int=500, smsRemindersAllowed:bool=false }`

### 2.4 Widgets / charts
- `AnalyticsKpiCards` (`features/home/presentation/analytics_kpi_cards.dart`): loading → 4 grey 72px boxes in a `Row`; error → `Text(l10n.error)`; data → 4 `_KpiCard` (40px tinted icon tile, 22sp value, caption title): appointmentsToday (brand), completedToday (green700), cancelledToday (orange700), newPatientsToday (indigo700). `< 600px` → `Column`, else `Row` (l.55-72).
- `AppointmentsTrendChart` (`core/widgets/appointments_trend_chart.dart`): `fl_chart` `LineChart`, height 160, curved line width 3, dots r4 white stroke, horizontal grid only, `maxY = ceil(max*1.2)` (10 if max 0), y interval `yAxisMax<=5 ? 1 : ceil(max/5)`, x labels `MM/DD` from `date.substring(5,7)/(8,10)`. Title `appointmentsLast7Days` **always** (even when the range is 30 days). Empty → `analyticsNoData`.
- `VisitTypeDonutChart` (`core/widgets/visit_type_donut_chart.dart`): `PieChart` `centerSpaceRadius 50`, `sectionsSpace 2`, sections inPerson (brand) / video (brand α0.4), radius 50; centre column: `inPerson%` (20sp bold brand), `l10n.inPerson`, `"video% videoCall"`, `"inPerson / video"` counts. Empty when total 0.
- `AnalyticsEngagementWidget` (`analytics_engagement_widget.dart`): white 16r card "engagementSummary", two `_EngagementRow` (icon, 18sp value, 11sp label): activePatients (brand), documentsReceived (teal700). Loading = grey 100px box; error = card with `l10n.error`.
- `AnalyticsContainer` (`core/widgets/analytics_container.dart`): white 16r card, 14sp bold title, 12px gap. `AnalyticsCard` (`analytics_card.dart`) is unused.
- `AnalyticsSmsUsageWidget` (`analytics_sms_usage_widget.dart`): hidden when `!smsRemindersAllowed && sentCount == 0`; shows `reportsSmsTitle`, two `_StatTile` (sent, `"<totalCostMinor> <currency>"`), rate hint with `{{price}}`/`{{currency}}` placeholders, warning when not allowed. Uses `?? 'fallback'` pattern (violates project rule).

---

## 3. Shifa AI on Home / Intelligence — full behaviour

### 3.1 Entry points & gating
- `MainShell._openIntelligenceMode` (`features/shell/presentation/main_shell.dart:283-292`): requires `DoctorFeature.askShifaAi` (PRO+, `core/subscription/doctor_subscription.dart:19-53`), else `SnackBar(l10n.error)`. Sets `workspaceModeProvider.showIntelligence()` (`state/workspace/workspace_mode_provider.dart`). While `WorkspaceMode.intelligence && !isClinicStaff` the shell body is `IntelligenceScreen` instead of the tab navigator (`main_shell.dart:690-691`). Leaving intelligence → `invalidateAppointmentRelatedProviders` (AI bookings may have changed the schedule) (`main_shell.dart:296-300`, `intelligence_screen.dart:231-234`).
- Tier resolution: `doctorTierProvider` reads `profileAll.subscription['tier']` (`BASIC|PRO|PREMIUM`), legacy `profile['subscriptionTier']`, defaults **PREMIUM** while loading/missing (`state/subscription/doctor_subscription_provider.dart:15-28`).
- Inside `HomeVoiceSession.build`: `if (!askShifaAi) return SizedBox.shrink()` (`home_voice_session.dart:2316-2317`). Any mic/dictation/wake start requires `DoctorFeature.speechToText` (PRO) else `_surfaceError(l10n 'intelSpeechRequiresPro')` (l.1186-1189, 1458-1466, 1505-1513; localised since `4d05e61`).

### 3.2 `AiApi` SSE streaming protocol (`core/api/ai_api.dart:85-260`)
Request (`_streamAiAt`):
- `POST {baseUrl}/api/ai/morning/stream` (Intelligence / morning tools) or `POST {baseUrl}/api/ai/stream` (patient-aware chat with draft). Headers = `ApiClient.buildHeaders(extra: {'Accept': 'text/event-stream'})` → `Content-Type: application/json`, `Authorization: Bearer <jwt>` (`core/api/api_client.dart:285-291`). Uses a raw `http.Request` (bypasses `ApiClient`'s 401 handling).
- Body JSON (nulls are sent as `null`): `{"question": String?, "messages": [{"role","content"}], "language": "EN|RU|UZ", "patientId": int?, "consultationId": int?}` (l.137-143).
- Non-200 → read full body; parse `{code, message, errors{k:v}}`; message = first `errors` value if present → `throw AiStreamException(code ?? 'UNKNOWN', message)` (l.147-165).

Parsing (l.167-259) — **not a standard SSE parser; replicate exactly**:
- Decode UTF-8 chunks, split on `\n`, strip one trailing `\r`.
- `event:` line → `currentEvent = rest.trim()`.
- `data:` line → append `line.substring(5)` **without trimming** (comment l.245: leading spaces are part of the token). Multiple `data:` lines join with `\n`.
- Blank line = dispatch:
  - `event: error` → JSON `{code,message}` → `throw AiStreamException` (fallback `('UNKNOWN', rawData)`).
  - `event: draft` → `AiDraftReadyEvent(AiDraftReady{draftId:'', draftLabel:'Draft', canSave:true defaults})`.
  - `event: tool` → JSON `{name, payload{}}` → `AiToolEvent(name, payload)` (skipped if `name` empty / payload not a map).
  - any other event name (or none) with non-empty data → `AiTokenEvent(data)`.
- `id:`, `retry:`, `:` comment lines are ignored. Leftover `data` at EOF without a blank line is flushed as a token (l.251-259).
- Debug builds `debugPrint` every token (l.231-233) — logs AI content.

Cancel: callers hold a `StreamSubscription` and `cancel()` it (dead copilot/overlay/`DoctorAiPanel`). `HomeVoiceSession` consumes with `await for … .timeout(75 s)` (l.1925-1933) and a generation counter `_askGen`; a "stop" only bumps the counter so later events are ignored — the HTTP connection is not explicitly closed (**UNVERIFIED** whether `package:http` closes it when the `await for` returns early). `_aiSub` (l.141) is cancelled in two places but **never assigned** → dead field.

Other `AiApi` calls (all raw `http`, headers via `buildHeaders`):
- `POST /api/ai/draft/{draftId}/confirm` body `{patientId?, appointmentId?}` → 200 else `Exception` (l.263-274).
- `POST /api/ai/draft/{draftId}/discard` (l.277-281).
- `POST /api/ai/speak` `{text, language}` with `.timeout(25 s)`; text truncated to 560 chars at last `.!?` ≥ 40 or last space (l.294-335); response bytes + `Content-Type` → `SpokenAudio{bytes, mimeType default 'audio/mpeg'}`; UZ returns Gemini `audio/wav`, EN/RU OpenAI MP3 (doc comment l.292-293). Non-200 → `AiStreamException('TTS_FAILED', …)`.
- `GET /api/ai/visit-facts/appointment/{appointmentId}` → `VisitFactsBundle{facts, patterns}` (l.337-347).
- `GET /api/ai/visit-facts/patient/{patientId}/timeline` → `{timeline:[VisitFactDto], patterns:[ClinicPatternDto]}` mapped into `VisitFactsBundle` (l.349-368).
- `POST /api/ai/visit-facts/commit` `{appointmentId, acceptFactIds?, rejectFactIds?}` → `VisitFactsCommitResult{committedFacts, createdTaskIds, patterns}` (l.370-390).
- `POST /api/ai/patient-briefing/{patientId}` `{language:'en'|'uz'|'ru'}` → `PatientBriefingResult{briefing, documentCount, appointmentCount}`; 400/403/404 → `AiStreamException('BRIEFING_ERROR', message|body)`; 429 → `('RATE_LIMIT', message)`; else `('AI_UNAVAILABLE', 'Briefing failed: HTTP n')` (l.394-432).
- `AiStreamException.userFacingMessage`: `RATE_LIMIT` → "You've reached your daily AI usage limit. Try again tomorrow." (hardcoded, l.26-31); `displayLabel` maps VALIDATION/RATE_LIMIT/SAFETY_BLOCK/AI_UNAVAILABLE (l.34-47).

### 3.3 Text chat (Intelligence composer → morning stream) (`home_voice_session.dart`)
`_sendTyped(text)` (l.2196-2218): exits hands-free/wake (stops browser speech), detects language from text (`_detectLanguageFromText`: Cyrillic → RU; Uzbek marker words/apostrophes → UZ; Latin → EN; l.1416-1441), sets `_busy`, `_liveCaption = text`, → `_askMorningAi`.

`_askMorningAi(userText, language)` (l.1830-2039):
1. `userText = stripWakePhrase(userText)` (l.1832).
2. `patientId/appointmentId` from widget args (IntelligenceScreen passes **none**).
3. Embedded: `_tryCompletePendingBooking(userText)` (l.1842) — local intent, see §3.6.
4. Non-embedded only: "document everything" command → `POST /api/ai/visit-facts/commit {appointmentId}` + invalidations + canned reply + TTS (l.1847-1895). (Not reachable from Intelligence.)
5. Build `history` (l.1897-1905): for every `_VoiceTurn`: `user: turn.userText`, `assistant: _assistantContentForApi(turn)`; append new user message. `_assistantContentForApi` (l.648-679) = spoken text + optional pseudo-XML hints from the card payload: `\n<appointment_query>{json of payload.queryContext}</appointment_query>`, `\n<appointment_list>id,id</appointment_list>` (from `payload.appointmentId`, `payload.appointment.appointmentId`, `payload.appointments[].appointmentId`), `\n<appointment_action>{actionContext.token}</appointment_action>` when `actionContext.type == 'CANCEL'`, `\n<appointment_focus>{actionContext.appointmentId}</appointment_focus>` when `type == 'FOCUS'`. **Backend prompt contract — must be replicated byte-for-byte.**
6. `focusPatientId = _focusPatientIdFromCards() ?? patientId` (l.1921; §3.7).
7. Stream `streamMorningAi(messages: history, language, patientId: focusPatientId, consultationId: appointmentId)` with 75 s timeout.
   - `AiToolEvent` → `toolName`, `cardPayload` (embedded + propose_* → `_maybeAutoBookFromTool`, §3.6); `setState`, scroll.
   - `AiTokenEvent` → append to buffer → `_streamingReply`, phase thinking.
8. End: `reply = buffer.trim()`; if empty and no card → `_error = intelEmptyReply` (l.1963-1972). `spoken` = `payload.booked == true ? payload.statusMessage ?? reply : reply.isEmpty ? payload.spokenHint ?? '' : reply` (l.1974-1978). Append `_VoiceTurn`, `_emitMessages()` (persist), `_speakReply(spoken, replyLang)` if non-empty.
9. Errors: `AiStreamException` → `e.userFacingMessage`; `TimeoutException` → "Shifa took too long to reply. Try again."; other → `e.toString()` (l.1997-2020).
10. `finally`: `_busy=false`, phase idle unless speaking; if `_handsFreeVoice` → `_scheduleHandsFreeListen(1100 ms web / 450 ms)` (l.2021-2038).

`_retryLast` (l.2220-2237): re-sends `_liveCaption` or pops and re-sends last user turn.

### 3.4 TTS playback (`_speakReply`, l.2041-2109; `_awaitTtsPlaybackDone`, l.2113-2194)
- Phase → speaking immediately; `playGen = ++_ttsPlayGen` (barge-in guard).
- `api.speak(text, language, 25 s)`; empty bytes → idle. `_player.stop()`, 60 ms settle, `setVolume(1)`, play `UrlSource(data:<mime>;base64,…)` else fallback `BytesSource` (web reliability hack).
- Wait: estimate `len*65 ms` clamp 2–28 s + 900 ms, or real duration + 900 ms (cap 30 s); poll every 120 ms for `PlayerState.completed` or `position >= duration−250 ms`; listens to `onPlayerStateChanged`/`onPlayerComplete` but does not trust a lone complete (web fires early); extra wait loop if position lags (cap 20 s).
- Errors: timeout → "Voice is slow — caption is above. Keep talking."; 502/TTS_FAILED → "Voice skipped — caption is above." (soft snackbars).

### 3.5 Voice session — recording, STT, state machine
Phases: `VoiceSessionPhase { idle, listening, thinking, speaking }` (`shifa_ai_orb.dart:12`). Orthogonal flags (l.96-157): `_busy`, `_handsFreeVoice`, `_wakeStandby`, `_wakeListening`, `_dictationHold`, `_transcribingDictation`, `_manualEndpoint`, `_heardSpeech`, `_autoFinishing`, `_gotAmplitude`, `_browserFinalInFlight`, `_handsFreeStartFails`, `_sessionLanguage` (`EN|RU|UZ`, seeded from UI locale, l.131, 1383-1393).

Three input modes:

**(A) Alexa mode** — `widget.embedded && kIsWeb && WebSpeechConversation.isSupported` (l.126-127). **Web only.** Summary for completeness: on mount `_ensureWakeStandby` (l.1178-1217) starts browser `SpeechRecognition(continuous: true, lang = UI locale)` plus the file recorder in parallel; a final utterance containing a wake phrase (`IntelligenceChatTitles.containsWakePhrase`, `intelligence_chat_models.dart:122-133`) either enters listening or (wake + command) transcribes captured audio server-side and asks. Conversation turns use `continuous: false`; final transcript is replaced by server STT of the recorded audio when available (l.952-961). Watchdog `Timer.periodic(2 s)` restarts dead recognizers (l.754-776); restart cooldown 1400 ms; 5 consecutive start failures → back to wake standby (l.1644-1661). Sign-off phrases (EN/RU/UZ list, l.1021-1107) end the loop with a canned reply per language (l.1152-1167). `WebSpeechConversation` (`core/widgets/web_speech_conversation_web.dart`) wraps `package:web` SpeechRecognition with 4 start retries, swallows `aborted/no-speech/audio-capture`, ignores `onend` for 900 ms after start. Stub returns `isSupported=false` on non-web.

**(B) Recorder mode** (what native mobile gets when tapping the mic): `_toggleListen` (l.1443-1501) → `_beginListening` (l.1593-1698):
- `_handsFreeVoice = true`, `_resetVad()`, phase listening, `_visualLevel = 0.12`, `_startListeningPulse()` (140 ms fake wave until speech heard).
- Timers: `_maxListenTimer` 45 s → `_finishListening`; `_amplitudeProbeTimer` 900 ms → if no amplitude events → `_enterManualEndpointMode` (tap stop to send).
- `_session.start(_onMicLevel)` (`DoctorVoiceRecorderSession`, §10); `no_permission` → "Microphone permission is required…"; else "Could not start the microphone…".
- VAD `_onMicLevel` (l.1253-1320): constants `_speechLevel 0.12`, `_silenceLevel 0.09`, `_silenceToSend 1900 ms`, `_minSpeechBeforeAutoSend 700 ms`, `_noSpeechCancel 20 s` (l.167-175). Peak decays `peak*0.92+level*0.08`; silence gate `= (peak*0.30).clamp(0.09, 0.40)`. Speech ≥0.12 marks `_heardSpeech`; silence ≥ gate for 1900 ms after ≥700 ms of speech → `_autoSendFromSilence` → `_finishListening`. No speech for 20 s → `_cancelListening(reason)` (hands-free off + snackbar).
- Second tap while listening → `_finishListening` (send). Tap while speaking → barge-in (`_ttsPlayGen++`, `_player.stop()`), then listen.
- `_finishListening` (l.1756-1813): phase thinking, `_transcribeCapturedAudio()` (l.1724-1754): `session.stop(discard:false)` (8 s timeout) → `readVoiceRecordingFileBytes` (8 s) → `postDoctorTranscriptionDetailed(autoDetectLanguage: true)` → drop empty / `'(No speech detected)'`. Empty → error "No speech detected…" + `_scheduleHandsFreeListen()`. Else `lang = _resolveReplyLanguage(transcript, sttLanguage)` (STT language → text heuristic → UI) and `_askMorningAi`. Errors: timeout → "Transcription timed out…"; `permission`/`403` → "Speech requires Pro, or mic permission was denied."; else "Transcription failed…".
- After the reply, `_scheduleHandsFreeListen(450 ms)` re-opens the mic (hands-free loop also on mobile) until an error/cancel/typed message clears `_handsFreeVoice`.

**(C) Dictation** (composer record button): `_toggleDictation`/`_beginDictation` (l.1504-1591): tap-to-record until tap-to-send; `_manualEndpoint = true`, max 10 min timer, pulse wave keeps bars moving on flat web levels; stop sends through the same `_finishListening`. While thinking, the same button cancels (`_askGen++`, snackbar "Stopped.").

Composer (`_IntelligenceComposer`, l.2875-3126): `TextField` 1–4 lines (disabled while busy/listening/speaking; hints `intelComposerListening`/`intelComposerSpeaking`/"Transcribing…"), dictation button (record icon → green check while holding; spinner while transcribing), mic button (stop icon red while listening; `mic` while speaking = barge-in; `hearing` in wake standby), send button. Below: `intelHandsFreeOn` hint when hands-free idle, `intelListeningForWake` in wake standby. Dictation replaces the text field with `_ComposerDictationWave` (22 bars driven by `ValueListenable<double>`).

Orb panel in the thread (`_embeddedVoicePanel`, l.2289-2304): `ShifaVoiceOrbPanel(phase, intensity: _visualLevel, status, subtitle, caption (Alexa interim), onStop while listening)`. Status copy `_voiceCopy` (l.2239-2287): listening → `intelBannerRecording` (manual) / `intelBannerPauseSend` / `intelBannerSpeakNow`; thinking → "Transcribing…" or `intelReviewing`; speaking → `voiceSpeaking` "Shifa is speaking…" + "Tap the microphone to interrupt".

### 3.6 Booking performed by the client (tool-triggered actions)
> **Historical (removed in `4d05e61`).** Kept for the legacy no-`workflowId` card buttons only; the voice-intent part ("yes" books locally) no longer exists. See §0.
Tools with side effects are executed **client-side** against regular REST endpoints; the AI only proposes.
- `_executeBook(payload, slot)` (l.449-480): `POST /api/schedule/book` `{startAt, slotMinutes (default 30), patientId, location: isVideo ? 'Video Consultation' : slot.locationLabel ?? 'Clinic', reason, isVideo, locationId? (non-video)}`; ok = 200|201; then `_refreshHomeAfterScheduleChange(startAt)` (invalidate notifications + `invalidateAppointmentRelatedProviders(appointmentDay)`).
- `_executeReschedule(payload, slot)` (l.482-508): `PUT /api/appointments/{appointmentId}/change` `{startAt, slotMinutes, locationId?}`; ok = 200|204.
- `_maybeAutoBookFromTool` (l.390-447): on `propose_appointment`/`propose_reschedule` tool event, pick `preferredSlot` or the slot whose `startLocal` equals the clock time the doctor said (`_extractClockTime` handles `9am`, `14:30`, `9 o'clock`, l.240-262); book immediately if `payload.autoConfirm == true` or named time matches; mutate payload `booked`, `statusMessage` ("Booked <name> at <time> on <speakableDateLabel|dateLabel>." / "Moved …"), `autoConfirm=false`. `_bookedStartAts` prevents double booking.
- `_tryCompletePendingBooking(userText)` (l.318-387): before calling the LLM, if the latest propose card is unbooked and the user said an affirmative (`yes|yeah|yep|yup|ok|okay|sure|confirm|please|book it|do it|да|хорошо|ha|mayli|tasdiq`, no negation `no|not|don't|cancel|another|different`, ≤48 chars, l.226-238) or named a listed slot time → book/reschedule locally, add a turn with the status message, speak it on success (failure text: "That slot could not be booked. Pick another time on the card.").
- Action-card buttons do the same calls (§4.4).

### 3.7 Patient focus heuristics (`_focusPatientIdFromCards`, l.540-599)
Latest `patient_search` card: single match → its `patientId`; multiple → the match whose name tokens (>2 chars) all appear in the last 4 user utterances. Otherwise latest card of type `propose_appointment|propose_reschedule|patient_appointments|patient_documents|document_viewer|dental_chart|patient_brief` (or tool names `list_patient_documents|open_patient_document|open_dental_chart|brief_patient`) → `payload.patientId`. Displayed as `_PatientContextLine` via `IntelligenceChatContext.summarize` (`intelligence_chat_models.dart:258-269`): clinic-wide cards (`schedule, free_slots, clinic_patterns, most_visited`, tools `get_today_schedule, list_free_slots`) never link a patient; placeholder name `'Patient'` is ignored.

### 3.8 Morning briefing stream
There is no separate "briefing" SSE; the "morning" stream (`/api/ai/morning/stream`) is the Intelligence stream itself, with tool events (`get_today_schedule`, `brief_patient`, …). The welcome card "schedule" simply sends the prompt text `intelPromptSchedule` ("Summarize my schedule for today.") through `_sendTyped` (`intelligence_welcome.dart:42-47`; `test/intelligence_welcome_test.dart:97-99`).

### 3.9 Local memory / persistence
- Live: `IntelligenceChatController` (`features/intelligence/application/intelligence_chat_store.dart`) — `SharedPreferences` key `shifa_intelligence_chats_v1:<doctorId>` (`profile['id'] ?? profile['doctorId'] ?? 'anonymous'`, l.47-52), value = JSON list of `IntelligenceChatSession`. `HomeVoiceSession.onMessagesChanged` → `replaceMessages(sessionId, messages)` after every completed turn (sets `updatedAt = now`, re-sorts desc, l.171-189). Cold start: history restored but an **empty** session is opened as welcome (created if none) (l.54-83). In-memory `_turns` hydrated from `initialMessages` by pairing `user` + following `assistant` (`home_voice_session.dart:190-224`).
- Dead: `HomeAiCopilot` key `ask_shifa_ai_conversation_v1:<doctorId>:<patientId|none>:<apptId|none>` + `home_ai_insights_expanded_v1` (`home_ai_copilot.dart:67-69`); `AskShifaAiOverlay` key `ask_shifa_ai_overlay_conversation_v1:<doctorId>:<patientId|none>` (`ask_shifa_ai_overlay.dart:54-55`). Both cap nothing on read; copilot declared `_maxConversationMessages = 15` but never applied.
- `patientBriefingContextProvider` (`state/patient_briefing_context_provider.dart`): in-memory per-patient briefing cache with in-flight dedupe; written by the briefing panel, read only by dead widgets.

### 3.10 `ShifaAiOrb` (`features/home/presentation/widgets/shifa_ai_orb.dart`)
- `shifaOrbTargetIntensity(phase, level, reduceMotion)` (l.20-47): reduceMotion → listening .2 / speaking .28 / thinking .12 / idle .05; else idle .06, thinking .16, speaking .46, listening = `level <= 0.16 ? 0.08 : (level−0.16)/(0.84)` clamped (tests `test/shifa_ai_orb_test.dart:6-60`).
- Widget: 10 s repeating `AnimationController`; smoothing `smoothed += (target−smoothed)*0.18` each tick; listening target decays toward 0.08 when no level update for 280 ms (l.119-131). `CustomPaint` (`ShifaOrbPainter`): 4 elliptical ripples, radial glow, body gradient `#F4FFFE→#C9F8F4→#3ADFD0→#00A99A`, 3 drifting blobs (white, `#8FF7F0`, teal) with phase-dependent speed, highlight, white rim; centred white disc with `assets/branding/shifa_mark.svg` (`flutter_svg`, key `shifa-ai-orb-logo`). Orb radius = 33 % of size; logo = 28 % of size.
- `ShifaVoiceOrbPanel` (l.395-526): tinted container, orb sized `(maxWidth*0.32).clamp(112,148)`, phase icon (thinking → 18px spinner; speaking → `graphic_eq`; idle → `mic_none`; listening → `mic`), status (live region, 14sp teal bold), subtitle caption, caption, optional Stop `TextButton.icon` (key `shifa-voice-orb-stop`). Respects `MediaQuery.disableAnimations`. Tests cover status/subtitle/logo/stop presence per phase.

---

## 4. Intelligence feature

### 4.1 Screens
- `IntelligenceScreen` (`features/intelligence/presentation/intelligence_screen.dart`): `Scaffold` with `AppBar` (white): back arrow (tooltip `intelBackToWelcome` when a conversation is open else `intelBackToClinic`; action: conversation open → `goToWelcome()`, else `showClinic()` + invalidate appointments, l.225-238), title `shifaIntelligence` + subtitle `intelligenceEmptyHint`, actions: history (`Icons.history`), new conversation (`add_comment_outlined`), overflow menu when a conversation is open: download / rename / delete (l.258-299). Body: spinner until `chatState.loaded && active != null`, else `HomeVoiceSession(key: ValueKey(active.id), embedded: true, hideChrome: true, initialMessages, recentSessions, onOpenSession, onRenameSession, onDeleteSession, onWelcomeVisible, onMessagesChanged)` (l.184-208). `conversationOpen = _welcomeVisible == false || active.messages.isNotEmpty`.
- History: `useSinglePane` → `showModalBottomSheet` 72 % height with `_HistoryPane`; else `Drawer(width 300)` (l.111-157, 213-219). `_HistoryPane`: title `intelHistory`, `OutlinedButton.icon(intelNewConversation)`, search field (`intelSearchConversations`, filter `session.matchesQuery`), list of `IntelligenceSessionTile` in bordered 10r boxes; empty copy `intelNoConversations` / `intelNoSearchResults` (l.340-461).
- Download (l.41-84): builds a `.txt` (`_formatConversationExport`, l.306-338: header lines, each message `--- [i] ROLE @ iso ---`, content, `toolName`, pretty `cardPayload`, then full session JSON) → `file_download.downloadBytes` (web anchor; mobile stub `admin_pdf_downloader_stub.dart` — behaviour **UNVERIFIED**, likely no-op/throw); filename `shifa-intelligence-<id>-<stamp>.txt`. Empty conversation → snackbar `intelDownloadEmpty`.
- Rename: `showIntelligenceRenameDialog` (`intelligence_session_tile.dart:34-42`, `_RenameConversationDialog` with `intelRenameConversation` / `intelRenameHint` / save). Delete: `confirmIntelligenceDelete` AlertDialog (`intelDeleteConversation`, `intelDeleteConversationConfirm`).
- Language switch while in Intelligence: `ref.listen(languageProvider)` resets `_sessionLanguage` and re-arms wake standby (web) (`home_voice_session.dart:2309-2315`).

### 4.2 Session store API (`intelligence_chat_store.dart`)
`IntelligenceChatState{sessions, activeSessionId, loaded; active}`; controller: `ensureLoaded()`, `createChat()` (new empty session prepended, becomes active), `goToWelcome()` (select first empty session or create), `selectChat(id)`, `deleteChat(id)` (if list empties → create new; if active deleted → `next.first`), `renameChat(id, title)` (sets `titleCustom = true`, keeps `updatedAt`), `replaceMessages(sessionId, messages)`. Every mutation persists the whole list.

### 4.3 Message / session models (`features/intelligence/domain/intelligence_chat_models.dart`)
- `IntelligenceChatMessage { role:'user'|'assistant', content:String, at:DateTime, toolName:String?, cardPayload:Map<String,dynamic>? }`; JSON keys `role, content, at (ISO), toolName?, cardPayload?`; `fromJson` defaults role `'user'`, content `''`, `at` now (l.3-44).
- `IntelligenceChatSession { id, title (mutable), createdAt, updatedAt (mutable), messages (mutable), titleCustom:bool=false }`; `untitled = 'New chat'`; `displayTitle` = custom title, else `shortTitle(first user message)`, else `untitled`; `preview` = last non-empty message shortened to 90 chars; `patientMark`/`patientLinked`; `matchesQuery` searches title/preview/all message content; `encodeList/decodeList` (decode sorts by `updatedAt` desc; malformed → `[]`) (l.272-384).
- `IntelligenceChatTitles`: `maxTitleLength 48`, `maxPreviewLength 90`, `starterTitles` map (EN/UZ/RU starter prompts → short titles, l.63-99), wake-word tokens `shifa|shifah|shefa|shefah|shiva|sheva|шифа` + `"she fa|fah|for|va"` (l.101-133), `stripWakePhrase` (greeting prefix regex + name regex, l.135-157), `shortTitle`, `previewLine` (word-boundary ellipsis). Tested in `test/intelligence_chat_models_test.dart`.
- `IntelligencePatientMark{linked, name}`; `IntelligenceChatContext.isPatientLinked/patientName/summarize` (§3.7).

### 4.4 Action cards (`features/intelligence/presentation/intelligence_action_cards.dart`)
`IntelligenceActionCard(toolName, payload, onDismiss?)`; `cardType = payload['cardType'] ?? toolName` (l.44-45); card = white 12r bordered box, brand title, body, red error line, green status line. `initState`: `payload.booked == true` → status = `statusMessage ?? 'Appointment booked.'`; `autoOpenDocumentId` → preselect document (l.47-58).

| cardType / toolName alias | Title key | Body | Actions |
|---|---|---|---|
| `schedule` / `get_today_schedule` | `intelCardSchedule` | `"intelAppointmentsCount: count"`, up to 8 rows `startLocal` + `"patientName · status"`; empty `intelNoAppointmentsToday` | `intelOpenCalendar` → `showClinic()` + `setTab(calendar)` |
| `patient_appointments` / `list_patient_appointments` | `intelCardPatientAppointments` | `patientName`, `rangeLabel`, count, up to 12 rows (`dateLabel|date` 100px, `startLocal` 48px, status [+ row patient name]) ; empty `intelNoPatientAppointmentsInRange` | Open calendar |
| `free_slots` / `list_free_slots` | `intelCardFreeSlots` | `dateLabel|date`, `Chip`s of `slots[].startLocal` (≤24, not tappable); empty `spokenHint ?? intelNoFreeSlots` | Open calendar |
| `patient_search` / `search_patients` | `intelCardPatients` | `fuzzy` → "Did you mean…?"/"No exact match for “query”. Did you mean…?" (hardcoded EN), rows `patientName · phone`; empty `spokenHint ?? 'No patients matched.'` | none |
| `propose_appointment` | `intelCardProposeBook` | kv `intelPatient`, `intelDate`, `intelTime` (`preferredSlot.startLocal` / `intelTimeNotSelected` / `intelChooseTime`), `error` | `preferredSlot` → `FilledButton` `intelConfirmTime{time}` → `_bookSlot`; "Other times" toggle → `OutlinedButton` per other slot; no preferred → all slots as buttons; `cancel` → dismiss. Success `intelBookedOk`; 409 `intelSlotConflict`; else "Booking failed: n" |
| `propose_reschedule` | `intelCardProposeMove` | kv patient, `intelCurrentSlot` (`currentDateLabel currentStartLocal`), `intelNewDate`, time | Confirm → `_moveSlot` (`PUT /api/appointments/{id}/change {startAt, slotMinutes}` — **no locationId**, unlike voice path); success `intelMovedOk` |
| `draft_visit_facts` / `list_draft_visit_facts` | `intelCardDraftFacts` | kv patient, rows `"factType: text"`; empty `intelNoDraftFacts` | `intelConfirmDocument` → `commitVisitFacts(appointmentId, acceptFactIds: facts[].factId)` → invalidate `visitFactsForAppointmentProvider`, `consultationNotesForAppointmentProvider('$id')`, `patientTimelineProvider`; status "Documented N facts."; `cancel` |
| `most_visited` / `get_most_visited_patients` | `intelCardMostVisited` | rows `patients[]`: `patientName|label · visitCount|count` | none |
| `clinic_patterns` / `get_clinic_patterns` | `intelCardPatterns` | rows `patterns[]`: `label|key · count` | none |
| `patient_brief` / `brief_patient` | `intelCardBrief` | plain `briefing ?? spokenHint` text (not `AiResponseText`) | none |
| `patient_documents` / `list_patient_documents` | `intelCardDocuments` | ≤12 rows `documents[]{documentId,title,date}` with selected highlight | tap → inline `IntelligenceInlineDocumentViewer(patientId, documentId, title)` under the list |
| `document_viewer` / `open_patient_document` | `intelCardDocumentViewer` | inline viewer; missing ids → `error ?? spokenHint ?? couldNotLoadDocument` | — |
| `dental_chart` / `open_dental_chart` | `intelCardDentalChart` | `IntelligenceInlineDentalChart` unless `error` and no `documentation`/`toothServiceCounts` | tooth tap shows services |
| default | `shifaIntelligence` | `spokenHint ?? error ?? ''` | — |

Each card holds its own `_busy/_status/_error/_dismissed/_showOtherTimes/_openedDocumentId` state; dismiss just hides it (`_dismissed`), the payload stays in history.

### 4.5 Inline viewers (`intelligence_inline_viewers.dart`)
- `IntelligenceInlineDocumentViewer`: `fetchDocumentDownloadWithClient(client, patientId, documentId)` (patients feature) → bytes; image detection by magic bytes / content-type / extension (jpg, jpeg, png, gif, webp) → `InteractiveViewer(Image.memory)` (0.5–4×); PDF → web `PdfViewerWeb(bytes)`, mobile `pdfx` `PdfViewPinch(PdfControllerPinch(PdfDocument.openData))`. Height 360, grey backdrop, retry button on failure (`couldNotLoadDocument`, `l10n.retry`).
- `IntelligenceInlineDentalChart`: `payload.dentition` (`'primary'` else permanent), `toothServiceCounts{fdi:int}`, `documentation.teeth{fdi:[{name|serviceName|label}]}`, `patientName · date`, `notes`; renders `DentalFdiChart` (appointments/dental) horizontally scrollable with dentition toggle; tooth tap → `dentalToothServices <fdi>` list (≤6) or `intelNoToothServices`.

### 4.6 Welcome (`intelligence_welcome.dart`)
`ListView` padding `(8,28,8,16)`: greeting `"<goodMorning|goodAfternoon|goodEvening>, <formatDoctorMedName>."` by local hour (<12, <17), `intelHowCanIHelp`, four `_WelcomeActionCard`s (`≥560px` → 2 columns): schedule (`intelWelcomeSchedule` → prompt `intelPromptSchedule`), find patient (`intelPromptFindPatient`), appointments (`intelPromptManageAppointments`), notes (`intelPromptClinicalNotes`); then `intelRecentConversations` with up to 5 non-empty sessions as `IntelligenceSessionTile` (rename/delete menu). Test expectations: card titles "Today's schedule", "Find a patient", subtitles "Ask about your day", "Search records"; tapping "Search records" sends `'Help me find a patient.'`, "Today's schedule" sends `'Summarize my schedule for today.'` (`test/intelligence_welcome_test.dart:85-99`).

### 4.7 Session tile (`intelligence_session_tile.dart`)
`intelligenceRelativeTime`: `<1 min timeJustNow`, `<60 timeMinAgo(n)`, `<24h hourAgo|hoursAgo(%s)`, `<2d yesterday`, `<7d intelDaysAgo({n})`, else `d.M.yyyy`. `intelligenceSessionLabel`: untitled → `newChat`. Tile: chat icon, person icon + tooltip `intelPatientConversation` when patient-linked, title (w600 when selected), relative time, preview (hidden when equal to title), overflow menu rename/delete (`enableMenu`).

### 4.8 `open_appointment` route (`open_appointment_route.dart`) — **not wired**
`planOpenAppointmentRoute(payload)` expects `{route: 'inPerson'|'video', appointment: {appointmentId, patientId?, patientName?, location, status, day:'yyyy-MM-dd', startLocal:'HH:mm', endLocal, startAt?, reason?, route?}}`; returns `null` when any required field is missing/invalid or `status ∈ {NO_SHOW, CANCELLED}`; builds `Appointment(id, patientName|'Patient', patientId, location, start, end, status, reason, day)`, `routeName = AppRoutes.videoCall|inPerson`, `autoOpenKey = '$id|$routeName|${status.name}|$startAt'`. `OpenAppointmentAutoOpen.claim(key)` is a process-wide set so the same visit auto-opens once (`test/open_appointment_route_test.dart`). No `open_appointment` case exists in `intelligence_action_cards.dart` and nothing in `lib/` imports this file (`grep open_appointment lib` → only its own doc comment).

---

## 5. Clinical engine (`features/clinical_engine/**`)

### 5.1 Endpoints (`data/clinical_engine_api.dart`, all via `ApiClient`, non-200 → empty list / null / `{}`)
| Method | Path | Response |
|---|---|---|
| GET | `/api/clinical-engine/groups` | `[ClinicalGroup]` |
| GET | `/api/clinical-engine/groups/{groupId}/diseases` | `[ClinicalDiseaseSummary]` |
| GET | `/api/clinical-engine/diseases/{diseaseId}` | `ClinicalDiseaseDetail` |
| GET | `/api/clinical-engine/doctors/me/top-diagnoses?limit=5` | `[ClinicalTopDiagnosis]` |
| GET | `/api/clinical-engine/chips/search?q=&locale=&limit=15` | `[ClinicalChip]` |
| GET | `/api/clinical-engine/occlusion/chips` | `[ClinicalOcclusionChip]` |
| GET | `/api/clinical-engine/shared/{type}` (`mucosa`, `xray`) | `[ClinicalSharedTemplate]` |
| POST | `/api/clinical-engine/synthesize` `{locale, diseaseId?, selections:[{chipId, variables{}}]}` | `{fields:{<field>:<text>}}` → `Map<String,String>` |

### 5.2 Models (`domain/clinical_engine_models.dart`) — `as String` casts are non-null (throw on missing)
- `ClinicalLocalizedName{ru,uz,en}` + `forLocale(locale)` (uz→uz||ru, en→en||ru, else ru).
- `ClinicalGroup{groupId, sortOrder=0, names}`; `ClinicalDiseaseSummary{diseaseId, number=0, groupId, slug, icdCodes[], names}`; `ClinicalDiseaseDetail extends Summary{chips[]}`; `ClinicalChip{chipId, field, labels, variables[], priority=50}`; `ClinicalOcclusionChip{chipId, angleClass?, icdHint?, labels, variables[], priority=50}`; `ClinicalTopDiagnosis{diseaseId, names, icdCodes[], useCount=0, lastUsedAt?}`; `ClinicalChipSelection{chipId, variables{}}` (`toJson`); `ClinicalSharedTemplate{templateId, type, field, labels, priority=50; chipId = 'shared.$templateId'}`; `ClinicalFieldMapping` constants `complaints, morbi, objective, occlusion, oral_cavity, xray, treatment_1, treatment_2, recommendations`.

### 5.3 UX (`presentation/clinical_engine_panel.dart`, `clinical_chip_panel.dart`)
- Embedded only in `features/patients/presentation/patient_form_screen.dart` (`ClinicalEnginePanel(api, locale, onApplied, initialDiseaseId, initialSelections, onSelectionsChanged)`); `onApplied` receives `{disease, synthesizedFields, allSelections}` and the form writes the fields.
- Bootstrap: `Future.wait(groups, top-diagnoses, occlusion chips, shared mucosa, shared xray)`; restore `initialDiseaseId` with `initialSelections` (no synth, `onApplied` with empty fields) (l.74-104, 106-157).
- `Card` "Clinical Engine" (hardcoded) + `TextButton.icon('Diagnosis')` → bottom sheet of groups → bottom sheet (`DraggableScrollableSheet 0.6`) of diseases with ICD subtitle → `_selectDisease(userInitiated: true)`: **selects every chip of the disease** and synthesises immediately (l.137-150, 276-314).
- No disease selected → "Top diagnoses" `ActionChip`s (top 5).
- Disease selected → name, chip search field (≥2 chars, 350 ms debounce, results as `ActionChip`s that add + immediate synth), then panels in order: Complaints, `'KASALLIK RIVOJLANISHI'` (morbi, hardcoded Uzbek), Objective, Occlusion (`ClinicalOcclusionChipPanel`, header 'Clinical Engine — Occlusion'), Oral cavity, 'Shared mucosa variants', X-ray, 'Shared x-ray templates', 'Treatment (stage 1)', 'Treatment (stage 2 / epicrisis)', Recommendations (l.405-544). All titles hardcoded English.
- `ClinicalChipPanel`: chips of one `field` sorted by `priority`, `FilterChip`s; selected chips with `variables` show `TextField`s labelled `"<variable> (<chip label>)"`. Occlusion panel variable fields are uncontrolled `TextField`s.
- Any toggle/variable change → `_notifySelectionsChanged` + `_applySynthesis()` debounced 350 ms → POST synthesize → `onApplied`. Selections are split by prefix on restore: `shared.*` → shared set, `occ.*` → occlusion set, else disease chips (l.121-136).

---

## 6. Patient briefing

- API: `AiApi.fetchPatientBriefing(patientId, language)` (§3.2). Language from `languageProvider.locale.languageCode` → `'uz'|'ru'|'en'` (`state/patient_briefing_provider.dart:43-52`).
- **No retry**: a failure sets `status = error` with `AiStreamException.userFacingMessage` or `e.toString()` (l.80-94); the user must tap "generate" again. `patientBriefingContextProvider.ensureLoaded` dedupes concurrent calls per patient and swallows errors (`patient_briefing_context_provider.dart:43-76`).
- Models: `PatientBriefingResult{briefing:String, documentCount:int, appointmentCount:int=0}` (`ai_api.dart:436-446`); `PatientBriefingState{patientId?, patientName?, status: idle|loading|success|error, briefingText?, documentCount=0, appointmentCount=0, errorMessage?; isVisible = status != idle || text non-empty}`; `PatientBriefingContextEntry{briefingText, documentCount, appointmentCount; hasText}`.
- Trigger: `patientBriefingProvider.notifier.generate(p.id, p.name)` from `features/patients/presentation/patient_detail_panel.dart:155, 1020` (also the dead `HomeAppointmentCard.onBriefing`). `close()` resets to idle.
- Panel `PatientBriefingPanel` (`core/widgets/patient_briefing_panel.dart`), rendered once in `MainShell` bottom-right, `Positioned(right:0,bottom:0)`, `bottomInset = Responsive.bottomNavClearance`, **only when `!isClinicStaff && !intelligenceActive && !PlatformLayout.isNativeMobile`** (`main_shell.dart:719-728`). Gated by `DoctorFeature.patientBriefing` (PRO). Size: height `45%|50%` of screen clamped `220–420 | 280–500`, width `screen−24` (single pane) or `overlayWidth(440).clamp(320,460)`; collapsed = 56px header. Header: chevron toggle, `"patientBriefingTitle — <patientName>"`, close. Body: loading spinner + `patientBriefingGenerating`; error icon + message; success → sources line (`patientBriefingSourcesWithAppointments(docs, appts)` / `patientBriefingSources(docs)` / `patientBriefingSourcesAppointmentsOnly(appts)`), `AiResponseText(maxWidth panelWidth−32, 14sp/1.45)`, `ShifaSecondaryButton(patientBriefingCopy)` → clipboard + `patientBriefingCopied` snackbar.

---

## 7. Consultation notes API + transcription API

### 7.1 `core/api/consultation_notes_api.dart` (via `ApiClient`)
| Fn | Method path | Body | Response / errors |
|---|---|---|---|
| `saveDoctorConsultationNotes` | `PUT /api/appointments/{id}/consultation-notes` | `{body: trimmed}` (no-op if empty) | 2xx else `Exception` |
| `getConsultationNotesForAppointment` | `GET /api/appointments/{id}/consultation-notes` | — | `[ConsultationNoteDto]`; **any non-200 → `[]`** |
| `getDraftNotesForAppointment` | `GET /api/appointments/{id}/draft-notes` | — | `[DraftNoteDto]`; non-200 → `[]` |
| `completeAppointmentVisit` | `PUT /api/appointments/{id}/complete` | `{clinicId?, doctorNotes? (trimmed, non-empty), awaitingScribe: true?}` | `CompleteAppointmentResult`; non-2xx `Exception`; unparsable body → defaults |
| `getAppointmentScribeStatus` | `GET /api/appointments/{id}/scribe-status` | — | `ScribeStatusDto`; non-200/parse error → `status 'none'` |
| `waitForScribeReady` | polls scribe-status | — | every **3 s** until `hasScribeNote` or **120 s** timeout; returns last status (l.252-271). Not cancellable. |

Models: `ConsultationNoteDto{id:int, body?, subjective?, assessment?, plan?, source:'MANUAL'|'AI_DRAFT' (default MANUAL), createdAt:''; isFromAi; displayText = stripScribeTranscript(body) or S/A/P joined by blank lines}`; `DraftNoteDto{id:String, aiLabel='Draft', body, createdAt, icdSuggestions:[IcdSuggestionDto{code, title, confidence:double?, isTop}]}`; `CompleteAppointmentResult{scribePending, hasDocumentation, hasScribeNote}`; `ScribeStatusDto{status:'none', hasDocumentation, hasScribeNote}`.
Helpers (tested in `test/consultation_notes_api_test.dart`, `test/scribe_documentation_compose_test.dart`): `joinedManualConsultationNotes` (non-AI notes joined by `\n\n`), `stripScribeTranscript` (cuts from a line starting `TRANSCRIPT:`), `composeVisitDocumentationText(scribeHeading, doctorHeading, notes, drafts, extraDoctorNotes)` → `"<scribeHeading>\n\n<AI notes + drafts>\n\n<doctorHeading>\n\n<manual + extra>"`.
Providers: `consultationNotesForAppointmentProvider(String id)`, `draftNotesForAppointmentProvider(String id)` (`features/appointments/application/consultation_notes_provider.dart`). Callers live in in-person/video screens (other analysis area).

### 7.2 `core/api/doctor_transcription_api.dart`
- `readVoiceRecordingFileBytes(path)`: web `blob:` → `http.get`, `data:` → base64; else `File(path).readAsBytes()`.
- `voiceRecordingUploadFileName(path, bytes)`: `speech_<epochMs>.<ext>`; web ext by magic bytes (`RIFF`→wav, `1A45DFA3`→webm, `ftyp`→m4a, default webm); mobile always `.m4a`.
- `normalizedTranscriptionLanguageHint(ref)`: UI locale `uz|ru|en`, else `uz`.
- `postDoctorTranscriptionDetailed(fileBytes, fileName, languageHint?, autoDetectLanguage)`: `ApiClient.postMultipart('/api/ai/transcribe', files: [MultipartFile 'file'], fields: {languageHint} unless autoDetect)`; 90 s timeout on send and on body read; **403 → `Exception('speechToTextRequiresPro')`**; 429 → `AiStreamException('RATE_LIMIT', message)`; other non-2xx → `Exception(message|body)`; 200 → `DoctorTranscriptionResult{text (trimmed), language (lowercased)?}`. Placeholder `kNoSpeechTranscriptPlaceholder = '(No speech detected)'`.
- `postDoctorTranscriptionFeedback(transcript, localeHint, audioBytes?, audioFileName?)`: `POST /api/ai/transcription-feedback` multipart `file?` + fields `{transcript, locale}`; 404 → `Exception('transcription_feedback_disabled')`; 429 → RATE_LIMIT.
- `appendTranscribedText(controller, text)`: appends with a space separator unless controller ends in space/newline; cursor to end.
- `completeDoctorTranscriptionFromRecording(context, ref, filePath, controller, onTranscriptAppended)`: snackbar `transcribing` → bytes → transcribe (UI-locale hint) → empty → `noSpeechDetected`; else append, then `PublicBackendConfig.transcriptionFeedbackEnabled()` (`GET /api/public/config`, unauthenticated, 5 s timeout, cached for process; flag `transcriptionFeedbackEnabled == true || 'true'`, `core/network/public_backend_config.dart:51-55`) → if enabled, 10 s green snackbar `transcriptionAdded\ntranscriptionReportHint` with action `transcriptionReportAction` → feedback upload → `transcriptionReportThanks`; else plain `transcriptionAdded`. Errors: RATE_LIMIT → `userFacingMessage`; `speechToTextRequiresPro` → localized key; else `"error: <msg>"`.

### 7.3 Speech-enabled inputs (`core/widgets/doctor_speech_text_field.dart`, `inline_voice_recorder_bar.dart`)
- `DoctorSpeechTextField`: `TextFormField` with a mic suffix (only when `DoctorFeature.speechToText`); recording overlays a dark pill (40 bars, cancel ✕, confirm ✓ / spinner) at the bottom and adds 40–48px bottom padding; confirm → `completeDoctorTranscriptionFromRecording`. Two styles: `standard` (suffix icon row, `suffixBeforeMic[Builder]`) and `borderlessExpanding` (mic positioned top-right). Used by patient form, in-person/video screens, SOAP section, dental panel, dead overlay.
- `InlineVoiceRecorderBar(onRecordingComplete(path, seconds), onCancel, titleLabel?, confirmButtonLabel?)`: starts recording on mount, same pill, hint `voiceRecordingFinishHint`; used by chat and in-person screen.

---

## 8. Endpoint table (whole area)

| Method | Path | Request | Response parsed | Errors handled | Timing |
|---|---|---|---|---|---|
| POST | `/api/ai/morning/stream` | JSON `{question, messages[], language, patientId, consultationId}`; `Accept: text/event-stream` | SSE: tokens, `event: tool {name,payload}`, `event: error {code,message}` | non-200 JSON `{code,message,errors}` → `AiStreamException`; stream error event | 75 s overall timeout in voice session |
| POST | `/api/ai/stream` | same | + `event: draft {draftId, draftLabel, canSave}` | same | none (dead callers) |
| POST | `/api/ai/draft/{id}/confirm` | `{patientId?, appointmentId?}` | — | non-200 `Exception` | — |
| POST | `/api/ai/draft/{id}/discard` | — | — | non-200 `Exception` | — |
| POST | `/api/ai/speak` | `{text ≤560 chars, language}` | audio bytes + `Content-Type` (`audio/mpeg` \| `audio/wav`) | non-200 `TTS_FAILED` | 25 s |
| POST | `/api/ai/transcribe` | multipart `file`, field `languageHint?` | `{text, language?}` | 403 Pro, 429 RATE_LIMIT, other `{message}` | 90 s |
| POST | `/api/ai/transcription-feedback` | multipart `file?`, `transcript`, `locale` | — | 404 disabled, 429 | — |
| GET | `/api/public/config` | — (no auth) | `{transcriptionFeedbackEnabled}` | swallowed | 5 s, cached |
| POST | `/api/ai/patient-briefing/{patientId}` | `{language}` | `{briefing, documentCount, appointmentCount}` | 400/403/404 BRIEFING_ERROR, 429 RATE_LIMIT, else AI_UNAVAILABLE | — |
| GET | `/api/ai/visit-facts/appointment/{id}` | — | `{facts[], patterns[]}` | non-200 `Exception` | — |
| GET | `/api/ai/visit-facts/patient/{id}/timeline` | — | `{timeline[], patterns[]}` | non-200 `Exception` | — |
| POST | `/api/ai/visit-facts/commit` | `{appointmentId, acceptFactIds?, rejectFactIds?}` | `{committedFacts[], createdTaskIds[], patterns[]}` | non-200 `Exception` | — |
| POST | `/api/schedule/book` | `{startAt, slotMinutes, patientId, location, reason, isVideo, locationId?}` | status only | 200/201 ok; 409 conflict (card) | — |
| PUT | `/api/appointments/{id}/change` | `{startAt, slotMinutes, locationId?}` | status only | 200/204 ok; 409 | — |
| PUT | `/api/appointments/{id}/consultation-notes` | `{body}` | — | non-2xx | — |
| GET | `/api/appointments/{id}/consultation-notes` | — | `[ConsultationNoteDto]` | non-200 → `[]` | — |
| GET | `/api/appointments/{id}/draft-notes` | — | `[DraftNoteDto]` | non-200 → `[]` | — |
| PUT | `/api/appointments/{id}/complete` | `{clinicId?, doctorNotes?, awaitingScribe?}` | `{scribePending, hasDocumentation, hasScribeNote}` | non-2xx | — |
| GET | `/api/appointments/{id}/scribe-status` | — | `{status, hasDocumentation, hasScribeNote}` | fallback `none` | poll 3 s / 120 s |
| GET | `/api/doctor/analytics/overview` | — | overview | non-200 `Exception` | refetch on today list change |
| GET | `/api/doctor/analytics/appointments-trend` | `days` 1–90 | `[{date,count}]` | non-200 | — |
| GET | `/api/doctor/analytics/consultation-types` | — | `{video,inPerson}` | non-200 | — |
| GET | `/api/doctor/analytics/engagement` | — | `{activePatients,documentsReceived}` | non-200 | — |
| GET | `/api/doctor/analytics/sms-usage` | `from,to` yyyy-MM-dd | sms usage | non-200 | — |
| GET | `/api/clinical-engine/groups` · `/groups/{g}/diseases` · `/diseases/{d}` · `/doctors/me/top-diagnoses?limit=5` · `/chips/search?q&locale&limit=15` · `/occlusion/chips` · `/shared/{type}` | — | see §5 | non-200 → empty/null | search debounce 350 ms |
| POST | `/api/clinical-engine/synthesize` | `{locale, diseaseId?, selections[]}` | `{fields{}}` | non-200 → `{}` | debounce 350 ms |
| GET | `/api/notifications` · `/api/notifications/unread/count` · `…/{id}/read` | — | notifications (other area) | — | refreshed on Home focus (60 s cooldown) |
| GET | `/api/tasks` | — | tasks (other area) | — | on Home mount + focus |
| GET | patient by id (`fetchPatientWithClient`), calendar day loads | — | other areas | — | hero/timeline |

---

## 9. Models (fields · nullability)

Already itemised: analytics (§2.3), Intelligence chat (§4.3), clinical engine (§5.2), briefing (§6), consultation notes (§7.1). Remaining:

- `AiMessage{role:String, content:String}` (`core/api/ai_message.dart`), `fromJson` tolerant (`''` defaults).
- `AiStreamEvent` sealed: `AiTokenEvent{token}`, `AiDraftReadyEvent{draft: AiDraftReady{draftId, draftLabel, canSave}}`, `AiToolEvent{name, payload: Map}`.
- `AiStreamException{code, message}`; `SpokenAudio{bytes, mimeType}`.
- `VisitFactDto{id:int, appointmentId:int?, patientId:int, factType:String ('MEDICATION'|'DIAGNOSIS'|'THERAPY'|'SYMPTOM'|…), text, quote?, scheduleHint?, status:'DRAFT'|'DROPPED'|'COMMITTED', reflectionKept:bool=true, dropReason?, remoteTaskId:int?, createdAt:String, committedAt:String?}` — `id`/`patientId` cast non-null (throw if missing).
- `ClinicPatternDto{key, label, factType, count:int}`; `VisitFactsBundle{facts[], patterns[]; keptDrafts, dropped}`; `VisitFactsCommitResult{committedFacts[], createdTaskIds:[int], patterns[]}`.
- `DoctorTranscriptionResult{text, language?}`.
- `DashboardDateRange{start:DateTime, end:DateTime; dayCount}`.
- Voice turn (in-memory) `_VoiceTurn{userText, assistantText, userAt, assistantAt?, toolName?, cardPayload?}` ↔ two `IntelligenceChatMessage`s.
- Tool payload shapes actually read (all optional/dynamic; define a sealed Kotlin model keyed by `cardType` with a `toolName` alias table):
  - common: `cardType`, `spokenHint`, `error`, `statusMessage`, `patientId:int`, `patientName`, `queryContext:any`, `actionContext{type:'CANCEL'|'FOCUS', token?, appointmentId?}`
  - propose_*: `dateLabel|date`, `speakableDateLabel`, `slots[{startAt (ISO), startLocal 'HH:mm', slotMinutes, locationId, locationLabel}]`, `preferredSlot{…}`, `autoConfirm`, `booked`, `isVideo`, `reason`, `appointmentId` (reschedule), `currentDateLabel`, `currentStartLocal`
  - schedule/patient_appointments: `count`, `appointments[{appointmentId, startLocal, patientName, status, patientId, dateLabel|date}]`, `rangeLabel`
  - free_slots: `slots[]`, `dateLabel|date` · search: `matches[{patientId, patientName, phone}]`, `fuzzy`, `query`
  - draft_visit_facts: `facts[{factId, factType, text}]`, `appointmentId` · most_visited: `patients[{patientName|label, visitCount|count}]` · clinic_patterns: `patterns[{label|key, count}]` · brief: `briefing`
  - documents: `documents[{documentId, title, date}]`, `autoOpenDocumentId` · viewer: `documentId`, `title` · dental: `dentition`, `toothServiceCounts{}`, `documentation{teeth{}}`, `date`, `notes`
  - open_appointment (unwired): see §4.8.

---

## 10. Platform-specific / third-party → KMP/CMP choice

| Concern | Flutter today | Rewrite (Android + iOS) |
|---|---|---|
| SSE streaming | `package:http` `Request.send()` + hand-rolled line parser (`ai_api.dart`) | Ktor `HttpClient.preparePost{…}.execute { body<ByteReadChannel>() }` + a small line parser that mirrors `ai_api.dart:167-259` (keep raw `data:` substring, no trimming). Don't use Ktor's SSE plugin: it applies standard leading-space stripping and would change tokens. Cancellation = coroutine cancel (closes the socket, fixing the leak). |
| Audio record | `record` 6.x: web WAV 16 kHz mono 128 kbps; mobile AAC-LC `.m4a` 32 kbps 16 kHz mono, temp dir; amplitude every 60 ms, dBFS→0..1 (`doctor_voice_recorder_session.dart:24-32, 49-79`) | `expect class VoiceRecorder` — Android `MediaRecorder` (AAC, 16 kHz, mono, 32 kbps, `.m4a`) + `maxAmplitude` poll 60 ms; iOS `AVAudioRecorder` (kAudioFormatMPEG4AAC, 16 kHz, 1 ch) + `averagePower(forChannel:)`. Normalise with the same floor/ceil (−55/−8 dB). Upload name `speech_<ms>.m4a`. |
| Audio playback (TTS) | `audioplayers` with data-URI hack + duration polling (`home_voice_session.dart:2041-2194`) | `expect class AudioPlayer` — Android `MediaPlayer`/ExoPlayer from temp file or `ByteArrayDataSource`, iOS `AVAudioPlayer(data:)`; use the native completion callback (no estimate polling). Handle `audio/wav` (UZ) and `audio/mpeg`. |
| Browser speech / wake word | `package:web` SpeechRecognition, web only | Drop. Mobile ships recorder mode + dictation + hands-free re-arm. Optional later: `SFSpeechRecognizer` / Android `SpeechRecognizer` behind `expect`. |
| Charts | `fl_chart ^0.66` (line + donut) | Compose `Canvas` (one curved path + dots + 2 arcs is ~80 lines; no dependency). If more charts arrive: Vico (multiplatform). |
| Orb | `CustomPainter` + `flutter_svg` logo | Compose `Canvas` + `rememberInfiniteTransition`; logo as `ImageVector` (convert `assets/branding/shifa_mark.svg`). |
| Persistence of chats/prefs | `shared_preferences` JSON string | `multiplatform-settings` (or DataStore) + kotlinx.serialization. Consider encrypting (chat history contains PHI). |
| CSV export | web anchor download / `share_plus` on mobile | `expect fun shareFile(bytes, name, mime)` → Android `FileProvider` + `ACTION_SEND`; iOS `UIActivityViewController`. Same for the `.txt` chat export. |
| Inline PDF viewer | `pdfx` (mobile) / web iframe | `expect` composable → Android `PdfRenderer` bitmaps, iOS `PDFKit.PDFView` via UIKitView. Images: Coil 3 / `Image(bitmap)`. |
| Markdown subset | `AiResponseText` parser | Port `parseBlocks/inlineSpans/formatForDisplay` as pure Kotlin + `AnnotatedString`; keep `test/ai_response_text_test.dart` cases as unit tests. |
| Date pickers | `showDateRangePicker` | Material3 `DateRangePicker` (CMP) — same bounds (−365 d / +30 d). |
| Clipboard | `Clipboard.setData` | `LocalClipboardManager`. |
| Timezone | `timezone` package IANA | `kotlinx-datetime` `TimeZone.of(iana)`. |
| SVG / web `dart:html` files | `admin_pdf_downloader_web.dart`, `pdf_viewer_web.dart`, `web_speech_conversation_web.dart` | Not needed. |

---

## 11. Quirks, bugs, dead code, tech debt — do NOT copy

### 11.1 Dead / unreachable (import graph checked with grep; see commands in analysis session)
- `features/home/presentation/widgets/`: `home_greeting_header.dart`, `home_timeline_section.dart` (+ `home_current_appointment_hero.dart`, `home_appointment_card.dart` which only it imports), `home_reminders_panel.dart`, `home_quick_actions.dart`, `home_floating_quick_actions.dart`, `home_ai_copilot.dart`, `ask_shifa_ai_overlay.dart` (+ `state/ask_shifa_ai_panel_provider.dart`).
- `core/widgets/doctor_ai_panel.dart`, `core/widgets/analytics_card.dart`.
- `features/intelligence/presentation/open_appointment_route.dart` (tests only).
- `home_analytics_providers.dart:53-73` stub providers with fake numbers.
- `HomeVoiceSession` non-embedded branch (`home_voice_session.dart:2355-2420, 2637-2692`: `_PulseOrb` header, `_HoldToTalkButton`, `_TypedFallback`, `_CaptionBubble`, "document everything" intent) — only `embedded: true` is ever constructed.
- `_aiSub` field (l.141) never assigned.
- `patientBriefingContextProvider` has no live reader.
- `home_quick_action_navigation.dart`: `openCreatePatientForm`, `openPatientsForDocumentUpload`, `openCreateTreatmentPlanForm` unreachable from live UI (keep the logic in mind if product wants those chips back).
- `video_call_screen.dart.backup` exists in the tree (other area).

### 11.2 Behavioural bugs / races
- Voice "stop while thinking" does not cancel the HTTP stream (gen counter only) → wasted tokens/connection (`home_voice_session.dart:1522-1531`).
- `nextUpcomingAppointmentProvider` performs up to 14 sequential day loads (`today_appointments_provider.dart:131-142`); the code comments elsewhere mention 429 rate limits from `/api/calendar`. Rewrite: one range query.
- `HomeClinicalWorkflow._startAppointment` reads `patientByIdProvider` synchronously; if not yet loaded the chronic-disease warning is silently skipped (l.47-52). Same in dead timeline.
- Search overlay result tap only switches to the Patients tab (`home_search_overlay.dart:110-113`), hardcoded index `3`, searches patients only despite "patients, appointments" hint, keyboard-only trigger.
- `formatDashboardDateRange` ignores locale (English months), dead ternary (`home_dashboard_date_range_provider.dart:44-51`).
- Trend chart title says "last 7 days" for any range; `doctorConsultationTypesProvider`/`doctorEngagementProvider`/trend are never invalidated by pull-to-refresh (only overview) → stale after bookings.
- `doctorAnalyticsOverviewProvider` throws `StateError` without a token instead of returning empty (`home_analytics_providers.dart:12-14`).
- Age in `home_current_appointment_hero.dart:216-220` / `home_appointment_card.dart:362-366` = `year − year` (wrong before birthday); `next_patient_card.dart:168-178` is correct — keep the latter.
- `refreshHomeDashboardOnTabFocus` cooldown is a process-global, survives logout/login (`home_dashboard_refresh.dart:10-11`).
- `PatientBriefingPanel` not rendered on native mobile (`main_shell.dart:723`) → feature invisible on the platforms being targeted.
- `propose_reschedule` card omits `locationId` while the voice path sends it (`intelligence_action_cards.dart:1050-1053` vs `home_voice_session.dart:496-497`).
- `IntelligenceInlineDentalChart` / document viewer run inside a `ListView` child; big cards trigger the web mic "zombie" hacks — none needed on mobile.
- `waitForScribeReady` blocks up to 120 s and is not cancellable (`consultation_notes_api.dart:252-271`).
- `getConsultationNotesForAppointment` swallows all non-200 as "no notes" (l.85).
- `WebSpeechConversation` wake-word tokens include `shiva/sheva/she va` → false wakes; irrelevant once browser speech is dropped.
- Local booking from affirmations (`_isAffirmativeUtterance` includes `please`, `ok`) books an appointment without a confirm tap — product should decide whether to keep this on mobile.

### 11.3 Non-localised / hardcoded strings (project rule violation; rewrite must add keys to en/uz/ru)
> Mostly fixed by `4d05e61` for `home_voice_session.dart`, `intelligence_action_cards.dart`, `visit_facts_panel.dart` (see §0). Remaining: clinical engine panel titles, `ai_api.dart` exception messages, analytics SMS widget, `_vf`/`_t` fallback pattern.
- `home_voice_session.dart`: every `_surfaceError(...)` text, `'Transcribing…'`, `'Sending your recording to Shifa'`, booking/reschedule replies (l.345-349, 442-444), sign-off replies (l.1152-1167), composer tooltips (l.3042-3044), `'Send'` (l.3358), `_t(l10n, key, fallback)` pattern (l.2696-2699) with keys that don't exist (`holdToTalk`, `talkWithShifa`, `voiceThinking`, `voiceThinkingSubtitle`, …).
- `intelligence_action_cards.dart`: `'Other times'`/`'Hide other times'`, `'Did you mean…?'`, `'No patients matched.'`, `'Confirm $preferredLabel'`, `'Missing patient or slot.'`, `'Booking failed: n'`, `'Documented N facts.'`, `'Appointment booked.'`, `'Document'`, `'Patient'`.
- `visit_facts_panel.dart` (`_vf` fallbacks, `'Refresh'`, `'Keep'/'Reject'`, `'Schedule: …'`, commit message), `clinical_engine_panel.dart` (all titles, `'KASALLIK RIVOJLANISHI'`), `ai_api.dart` (`AiStreamException` messages), `analytics_sms_usage_widget.dart` (`?? 'fallback'`), `appointment_timeline_tile.dart:222` hex colour, `home_appointment_card.dart:29-30` hex colour.

### 11.4 Inconsistencies to normalise
- Language codes: SSE `language` is `EN|RU|UZ` (voice) / copilot sent `UZ` for `UZ_CYRL`; briefing & transcription use lowercase `en|uz|ru`; TTS receives uppercase. Pick one mapping helper.
- Card identification uses both `cardType` and `toolName` (two spellings per card). Keep an alias table but model on `cardType`.
- `DoctorAnalyticsService._api` is `dynamic`; `AiApi` bypasses `ApiClient` (no 401 auto-logout for AI calls, no scope assertion).
- PHI in logs: debug token printing (`ai_api.dart:231`), `DoctorAiPanel` prints full answers. Chat history (incl. patient names, document ids) persisted unencrypted in prefs.
- Mixed `autoDispose` usage across analytics providers.
- `home_voice_session.dart` is a 3.4k-line God widget mixing VAD, STT, TTS, booking intents, persistence and UI; split into a state machine (`VoiceSessionController`), an `IntentResolver` (affirmative/clock-time/sign-off), a `BookingExecutor`, and pure UI.

---

## 12. Proposed tickets (buildable in listed order)

### AI-01 · AI HTTP client: SSE parser, errors, speak, briefing, visit facts [M]
Route · — · Spec refs §3.2, §8 · API: `POST /api/ai/morning/stream`, `/api/ai/stream`, `/api/ai/speak`, `/api/ai/patient-briefing/{id}`, `/api/ai/visit-facts/*`, `/api/ai/draft/{id}/confirm|discard`
Build: Ktor `AiApi` in shared module; `Flow<AiStreamEvent>` (Token/Tool/Draft) with the exact line protocol of `ai_api.dart:167-259` (raw `data:` substring, `\n` join, `event: error|draft|tool`); `AiStreamException(code,message,userFacingMessage)`; `speak()` returning bytes+mime; stream request body `{question, messages, language, patientId?, consultationId?, conversationId?}`; models `PatientBriefingResult`, `VisitFactDto`, `ClinicPatternDto`, `VisitFactsBundle`, `VisitFactsCommitResult`.
Done when: unit tests replay recorded SSE fixtures (token with leading space, multi-line data, tool event, error event, EOF flush) and non-200 JSON error; cancelling the collecting coroutine closes the connection.
Depends: core networking/auth ticket from area 01.
Fix: use `ApiClient` headers/401 handling for AI calls; no token logging.

### AI-02 · AiResponseText markdown-subset renderer [S]
Route · — · Spec refs §3.2 helpers, `core/widgets/ai_response_text.dart` · API: —
Build: pure Kotlin `parseBlocks/inlineSpans/formatForDisplay` + composable rendering heading (left brand bar), bullets, bold, spacers, selectable.
Done when: ports of `test/ai_response_text_test.dart` pass; renders a 220+ char single paragraph as sentence paragraphs.
Depends: design system.
Fix: none.

### AI-03 · Voice recorder (expect/actual) with amplitude [M]
Route · — · Spec refs §3.5, §10 · API: —
Build: `VoiceRecorder.start(onLevel)/stop(discard)`; Android `MediaRecorder` AAC 16 kHz mono 32 kbps `.m4a`; iOS `AVAudioRecorder`; 60 ms amplitude → 0..1 using floor −55 dB / ceil −8 dB; permission errors `no_permission|start_failed`; `lastRecordingDurationSeconds`.
Done when: manual test on both platforms produces a playable m4a; level stream visibly reacts; stop(discard) deletes file.
Depends: permissions helper.
Fix: drop web WAV/blob path and the 900 ms "no amplitude" probe (native always emits).

### AI-04 · Audio player (expect/actual) for TTS [S]
Route · — · Spec refs §3.4 · API: —
Build: `AudioPlayer.play(bytes, mime)` with suspend-until-complete, `stop()`, generation guard for barge-in; handles `audio/wav` and `audio/mpeg`.
Done when: play/stop/replay sequence works; completion resolves without polling hacks.
Depends: —
Fix: remove duration estimation (`65 ms/char`) and data-URI fallback.

### AI-05 · Transcription + feedback flag [S]
Route · — · Spec refs §7.2 · API: `POST /api/ai/transcribe`, `POST /api/ai/transcription-feedback`, `GET /api/public/config`
Build: multipart upload (`file`, optional `languageHint`, optional `recordingId`), 90 s timeout, 403→RequiresPro, 429→RateLimit, `(No speech detected)` handling; `PublicConfig.transcriptionFeedbackEnabled` cached; feedback upload.
Done when: unit tests for status mapping; integration against staging returns text.
Depends: AI-01 (exception types), AI-03.
Fix: one language-code mapper (`en|uz|ru`) shared with AI-09.

### AI-06 · Speech-enabled text field + inline recorder bar [S]
Route · — · Spec refs §7.3 · API: uses AI-05
Build: `SpeechTextField` (mic suffix gated by `speechToText`, recording pill with 40-bar wave, cancel/confirm, append-with-space semantics, snackbars incl. optional feedback action) and `InlineVoiceRecorderBar(onComplete(path, seconds))`.
Done when: dictating into a field appends text with correct spacing; feedback snackbar appears only when the public flag is on.
Depends: AI-03, AI-05, subscription gating.
Fix: localise all snackbar/tooltip text.

### AI-07 · Shifa orb + voice orb panel [S]
Route · — · Spec refs §3.10 · API: —
Build: Compose Canvas orb (ripples, glow, gradient body, 3 blobs, highlight, rim, centred logo) driven by `phase` + `StateFlow<Float>` level with the `shifaOrbTargetIntensity` mapping and 0.18 smoothing; `VoiceOrbPanel(status, subtitle, caption, onStop)`; reduce-motion support.
Done when: ports of `test/shifa_ai_orb_test.dart` (intensity table + panel contents per phase) pass.
Depends: design system.
Fix: none.

### AI-08 · Voice session controller (recorder mode, dictation, TTS, barge-in, hands-free) [L]
Route · — · Spec refs §3.3–§3.5 · API: AI-01 stream, AI-05 transcribe, `POST /api/ai/speak`
Build: `VoiceSessionController` state machine: phases idle/listening/thinking/speaking; VAD constants (0.12 speech, silence gate `clamp(peak*0.30, 0.09, 0.40)`, 1900 ms silence after ≥700 ms speech, 20 s no-speech cancel, 45 s max, dictation 10 min, manual endpoint); `_resolveReplyLanguage` (STT lang → text heuristic → UI); hands-free re-listen 450 ms after reply; barge-in; "stop" cancels the stream coroutine; error copy table (localised).
Done when: state-machine unit tests (silence auto-send, no-speech cancel, barge-in gen guard, dictation ignores pauses) pass; device test: tap mic → speak → reply spoken → mic re-opens.
Depends: AI-01, AI-03, AI-04, AI-05, AI-07.
Fix: no browser speech / wake word; split controller from UI; cancel HTTP on stop.

### AI-09 · Patient briefing (state + mobile panel/sheet) [S]
Route · patient detail → briefing sheet · Spec refs §6 · API: `POST /api/ai/patient-briefing/{id}`
Build: `PatientBriefingViewModel(idle/loading/success/error)`, trigger from patient detail; mobile presentation as a bottom sheet (collapsible header with patient name, sources line, `AiResponseText`, copy button, close); gate `patientBriefing`.
Done when: generate → sheet shows text; 429 shows the rate-limit message; copy puts text on clipboard.
Depends: AI-01, AI-02.
Fix: render on mobile (currently hidden); drop `patientBriefingContextProvider`.

### AI-10 · Visit facts panel + commit [S]
Route · in-person/video screens, patient chart · Spec refs §3.2, `visit_facts_panel.dart` · API: `GET /api/ai/visit-facts/appointment/{id}`, `GET …/patient/{id}/timeline`, `POST /api/ai/visit-facts/commit`
Build: `VisitFactsPanel(appointmentId, patientId)` with kept drafts (reject toggle), dropped list, patterns (top 3), "Document everything" → commit accept/reject ids → refresh; `PatientVisitTimelineCard`.
Done when: commit updates lists and shows "Documented N facts [, M medication tasks drafted]" localised.
Depends: AI-01.
Fix: localise; use `AppColors`.

### AI-11 · Consultation notes & scribe API [S]
Route · — · Spec refs §7.1 · API: `/api/appointments/{id}/consultation-notes` (GET/PUT), `/draft-notes`, `/complete`, `/scribe-status`
Build: DTOs + repository; `waitForScribeReady` as cancellable `flow` polling 3 s up to 120 s; helpers `joinedManualNotes`, `stripScribeTranscript`, `composeVisitDocumentationText`.
Done when: ports of `test/consultation_notes_api_test.dart` and `test/scribe_documentation_compose_test.dart` pass.
Depends: core networking.
Fix: surface non-200 as errors instead of empty lists.

### INTEL-01 · Chat models, titles/wake helpers, session store [M]
Route · — · Spec refs §4.2, §4.3, §3.9 · API: — (local persistence)
Build: `IntelligenceChatMessage/Session` (serialisable), `IntelligenceChatTitles` (starterTitles map, wake-phrase detection/strip, shortTitle 48, preview 90, `detectReplyLanguage` per §0), `IntelligenceChatContext` (patient link rules), `ChatSessionStore` on `multiplatform-settings` key `shifa_intelligence_chats_v1:<doctorId>` with create/select/delete/rename/replaceMessages/goToWelcome semantics.
Done when: ports of `test/intelligence_chat_models_test.dart` pass; store round-trips legacy JSON without `titleCustom`.
Depends: profile (doctor id).
Fix: encrypt-at-rest option; cap session count (none today).

### INTEL-02 · Intelligence screen shell: app bar, history, rename/delete/export [M]
Route · `/intelligence` (full-screen mode replacing the shell) · Spec refs §4.1, §3.1 · API: —
Build: entry gated by `askShifaAi`; app bar (back → welcome / clinic + appointment refresh, history, new chat, overflow download/rename/delete); history as modal bottom sheet (phone) with search, new, select, rename dialog, delete confirm; `.txt` export via share sheet.
Done when: cold start opens welcome with history available; rename keeps `updatedAt`; export shares a file with the documented format.
Depends: INTEL-01, navigation shell.
Fix: none.

### INTEL-03 · Welcome screen [S]
Route · inside INTEL-02 · Spec refs §4.6, §4.7 · API: —
Build: greeting by hour, 4 prompt cards (1 column < 560 dp), recent 5 sessions with tiles (relative time, patient icon, menu).
Done when: port of `test/intelligence_welcome_test.dart` passes (prompt texts, labels, recent preview).
Depends: INTEL-01, INTEL-02.
Fix: none.

### INTEL-04 · Chat thread + composer + streaming (text mode) [L]
Route · inside INTEL-02 · Spec refs §3.3, §3.7, §4.4 (card slot) · API: `POST /api/ai/morning/stream`, `POST /api/ai/speak`
Build: `IntelligenceChatViewModel`: turns, history builder with `<appointment_query|list|action|focus>` tags, focus-patient heuristic, 75 s timeout, empty-reply error, retry, persistence via INTEL-01; UI: user bubbles with timestamps, assistant blocks (sparkle + text + card slot), pending "Reviewing…", streaming block, error row + retry, patient context line, composer (text, send; mic/dictation buttons wired in INTEL-09), disclaimer; follow-latest auto-scroll with scroll-up detection; optional TTS of the reply (setting).
Done when: typed prompt streams tokens; tool event renders a placeholder card; reload restores the thread; history sent to backend matches a golden JSON fixture built from `_assistantContentForApi` rules.
Depends: AI-01, AI-02, INTEL-01, INTEL-02.
Fix: cancel in-flight stream on new send / leave; localise all strings.

### INTEL-05 · Read-only action cards [M]
Route · inside INTEL-04 · Spec refs §4.4 · API: —
Build: sealed `IntelligenceCard` parsed from `cardType`/`toolName` aliases: schedule, patient_appointments, free_slots, patient_search, most_visited, clinic_patterns, patient_brief, default; "Open calendar" → leave Intelligence + calendar tab.
Done when: JSON fixtures for each card type render the documented rows/empties; unknown card shows `spokenHint`.
Depends: INTEL-04.
Fix: render `patient_brief` with `AiResponseText`; card labels go through INTEL-10.

### INTEL-06 · Booking cards (server workflow + legacy buttons) [M]
Route · inside INTEL-04 · Spec refs §0, §4.4 (propose_*) · API: `POST /api/schedule/book`, `PUT /api/appointments/{id}/change` (legacy path only)
Build: propose_appointment / propose_reschedule cards with two modes keyed on `payload.workflowId`: (a) server workflow → read-only summary (patient, date, time, current slot) + localised "confirm in the conversation" line; the doctor's next "yes" goes to the LLM as a normal turn and the reply arrives with `booked: true` + `statusMessage`, which marks the card booked and refreshes appointments/notifications; (b) legacy (no `workflowId`) → preferred-slot confirm button, "Other times" toggle, all-slots list, cancel/dismiss, `BookingExecutor` with `locationId`, 409 → `intelSlotConflict`, failure `intelBookingFailed {code}`. `bookedStartAts` guard against double booking.
Done when: JSON fixtures for both modes render; legacy confirm books and marks the card; a `booked: true` reply flips a server-workflow card to booked without any client REST call; cancellation hints render via INTEL-10.
Depends: INTEL-04, INTEL-10, appointments invalidation from area 01/03.
Fix: send `locationId` in the reschedule path too; localise the two read-only hint lines (hardcoded English today).
Note: appointment **cancellation** is never executed locally. The backend drives ask-which → preview → "yes" (see §0); the client forwards the affirmative with the `<appointment_action>` token tag and renders the localised hint (INTEL-10).

### INTEL-07 · Clinical cards: draft visit facts, documents + inline viewer, dental chart [L]
Route · inside INTEL-04 · Spec refs §4.4, §4.5 · API: `POST /api/ai/visit-facts/commit`, patient document download
Build: draft_visit_facts card (confirm & document → commit accept ids → invalidate notes/facts/timeline); documents list with inline `DocumentViewer` (image zoom; PDF via `expect` renderer); document_viewer card; dental_chart card reusing the dental FDI chart composable (dentition toggle, counts, tooth services, notes).
Done when: fixtures render; PDF and image documents open inline on both platforms; commit shows "Documented N facts".
Depends: INTEL-05, AI-10, dental chart composable (area appointments), patient documents API (area patients).
Fix: localise.

### INTEL-08 · open_appointment card + auto-open [S]
Route · pushes in-person / video appointment screen · Spec refs §4.8 · API: —
Build: `planOpenAppointmentRoute(payload)` port + once-per-session claim; card with "Open visit" button; auto-open on first render when plan is valid.
Done when: port of `test/open_appointment_route_test.dart` passes; NO_SHOW/CANCELLED never navigate; remount does not re-open.
Depends: INTEL-05, appointment screens.
Fix: actually wire the card (missing today).

### INTEL-09 · Voice in Intelligence: mic, dictation, orb panel, hands-free loop, sign-off [M]
Route · inside INTEL-04 · Spec refs §3.5, §3.4 · API: via AI-08
Build: connect `VoiceSessionController` to the chat VM: live caption bubble while listening, orb panel in-thread for listening/speaking/(thinking & hands-free), composer mic/dictation buttons + hints, TTS of replies with barge-in, sign-off phrase detection ending hands-free with the canned localised reply, typed message exits hands-free.
Done when: device flow mic → transcript → reply spoken → re-listen → "thanks, that's it" ends loop; dictation with pauses sends once on tap.
Depends: AI-08, INTEL-04, INTEL-06 (spoken booking replies).
Fix: no wake word; localise sign-off replies.

### INTEL-10 · Card text localiser (status / date / range / hint sentences) [S]
Route · inside INTEL-05/06 · Spec refs §0 · API: —
Build: `IntelligenceCardText` object mirroring `intelligence_card_text.dart`: `statusLabel`, `dateLabel`, `fieldLabel`, `rangeLabel`, `patientLabel`, `documentLabel`, `factTypeLabel`, `dateTimeLabel`, `localizeHint` (exact map + the `tooMany` and `cancelPreview` regexes + templated booking/reschedule sentences + embedded calendar words), `chatTimestamp`. Keys: `intel*`, `appointmentStatus*`, `monthShort`.
Done when: port of `test/intelligence_card_text_test.dart` (6 groups) passes for en/uz/ru; an unknown sentence is returned unchanged.
Depends: INTEL-05, CORE-04 (strings).
Fix: temporary — delete once the backend sends codes/ISO (doc 15 §4). Keep the Dart regexes byte-identical until then.

### HOME-01 · Home data layer: today / next-upcoming appointments + refresh orchestration [M]
Route · — · Spec refs §1.4 derived data, §1.10 · API: calendar day(s) (area 03), `GET /api/notifications`, `/unread/count`, `GET /api/tasks`, analytics overview
Build: `TodayAppointmentsRepository` from calendar cache; `nextUpcoming` via a **single range query (today..+14 d)** instead of 14 sequential loads; classification (`current` / `nextUp`) in doctor timezone with `kotlinx-datetime`; `HomeRefresher` with tab-focus 60 s cooldown scoped to the session and pull-to-refresh full refresh; `invalidateAppointmentRelated` equivalent.
Done when: unit tests for classification (in-progress, next, none, non-UTC tz), 15-minute start window, cooldown; one network call for the look-ahead.
Depends: area 01 auth/profile, area 03 calendar repo.
Fix: no global mutable cooldown; return empty instead of throwing when unauthenticated.

### HOME-02 · Home screen skeleton: header, metric row, single-column layout, pull-to-refresh [M]
Route · tab home · Spec refs §1.2, §1.3, §1.4 (metric row) · API: via HOME-01
Build: scaffold with pull-to-refresh, `HomeHeader` (avatar, title, doctor name, notification badge → notifications tab), three metric cards (today visits → calendar, unread → notifications, follow-up active|draft → tasks), section gaps 20/12, bottom-nav clearance; skeleton shimmer component (`DashboardSkeleton`).
Done when: renders with loading/empty/data for each provider; tapping metrics switches tabs.
Depends: HOME-01, shell/tabs.
Fix: single-column only (no 7/3 split on phones); add a visible search button (feeds HOME-05).

### HOME-03 · Next patient hero, quick actions, today's schedule timeline [M]
Route · tab home → pushes in-person/video, patient detail, create task, calendar · Spec refs §1.4 (items 3–6) · API: patient by id
Build: `NextPatientCard` (status badge, avatar, date·time·age, visit/clinic pills, Start enabled ≤15 min before or now, Open patient card); empty state with "New appointment"; chronic-disease warning before start (await the patient fetch); quick action chips (new appointment, video → calendar quick-book intent, issue prescription gated PREMIUM → create task); `AppointmentTimelineTile` list of non-completed visits with status chips; error text when today's list fails.
Done when: hero picks current > next today > next 14 days; Start disabled >15 min early; chips navigate; cancelled status uses `AppColors`.
Depends: HOME-02, appointment screens, tasks create route, calendar quick-book intent.
Fix: correct age calc; await patient before warning; no hardcoded hex.

### HOME-04 · Attention center + activity feed [S]
Route · tab home → notification targets, task details · Spec refs §1.5, §1.6 · API: notifications, tasks
Build: `AttentionCenter` (unread ≤4 + active tasks ≤3, ≤6 total, mark-as-read + deep-link, empty "all caught up"); `ActivityFeed` (8 most recent notifications, type-coloured dot, time column).
Done when: items/order/limits match; tapping marks read and navigates.
Depends: HOME-02, notifications module (area 06?), tasks module.
Fix: load tasks once in the Home VM, not per widget.

### HOME-05 · Patient search overlay [S]
Route · home search button → overlay → patient detail · Spec refs §1.9 · API: local patient list
Build: full-screen search dialog, filter by name (8 default / 12 filtered), rows with initial avatar + phone; tap opens the **selected** patient detail.
Done when: typing filters; tap navigates to that patient.
Depends: HOME-02, patients list/detail route.
Fix: open the patient, not just the tab; drop keyboard-only trigger.

### ANALYTICS-01 · Analytics API, models, state [S]
Route · — · Spec refs §2.1–§2.3 · API: `/api/doctor/analytics/*`
Build: `AnalyticsApi` + DTOs; `DashboardDateRange` (default last 7 days, `dayCount`, `days.clamp(1,90)`, `from/to` yyyy-MM-dd); repository with one refresh entry point that invalidates all five datasets.
Done when: DTO parsing tests with missing fields default to 0/'UZS'/500; range math tests.
Depends: core networking.
Fix: consistent caching; refresh everything, not just overview.

### ANALYTICS-02 · KPI cards, engagement, date-range toolbar, CSV export/share [M]
Route · home "Clinic performance" card (and Reports) · Spec refs §1.7, §1.8, §2.4 · API: via ANALYTICS-01
Build: toolbar (range button with localised `d MMM – d MMM, yyyy`, bounds −365/+30 d; export button with spinner), KPI 4-up (column on phones), engagement card, CSV builder with the documented layout + share sheet; SMS usage widget for Reports.
Done when: export produces identical CSV to the Flutter layout for a fixture; picker respects bounds; strings localised in en/uz/ru.
Depends: ANALYTICS-01, HOME-02, share `expect/actual`.
Fix: locale-aware months.

### ANALYTICS-03 · Trend line + visit-type donut [M]
Route · home analytics card (PREMIUM only) · Spec refs §2.4 · API: via ANALYTICS-01
Build: Compose Canvas line chart (curved, dots, horizontal grid, `maxY = ceil(max*1.2)`, `MM/DD` labels) and donut (two arcs, centre percentages/counts); loading/error/empty states; gate `advancedAnalytics`.
Done when: screenshot tests for 7- and 30-point series; title reflects the selected range.
Depends: ANALYTICS-02.
Fix: dynamic title; no chart library unless more charts are added.

### CLIN-01 · Clinical engine API + models [S]
Route · — · Spec refs §5.1, §5.2 · API: `/api/clinical-engine/*`
Build: DTOs with `LocalizedName.forLocale`, selections serialisation, API wrapper; `ClinicalFieldMapping`.
Done when: parsing tests incl. `shared.`/`occ.` chip id prefixes; synthesize returns field map.
Depends: core networking.
Fix: nullable-safe parsing instead of `as String` throws.

### CLIN-02 · Clinical engine panel UI [L]
Route · patient form screen (embedded) · Spec refs §5.3 · API: via CLIN-01
Build: group → disease pickers (bottom sheets), top-5 chips, chip search (≥2 chars, 350 ms debounce), per-field `FilterChip` panels with variable inputs, occlusion + shared template panels, debounced synthesize (350 ms) → `onApplied`, restore from `initialDiseaseId/initialSelections` without synth, select-all-chips on user pick.
Done when: selecting a disease fills form fields; toggling a chip re-synthesises once after debounce; restoring a saved form shows prior selections.
Depends: CLIN-01, patient form screen (area patients).
Fix: localise every panel title; controlled text fields for occlusion variables.
