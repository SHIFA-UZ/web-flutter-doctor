> **Status:** analysis baseline, written 2026-10-24 from the Flutter code at commit `a5ace33` (no access to backend or Figma). It is the behavioural spec for the KMP rewrite: API shapes, flows, rules, quirks and the tickets at the end. Refine it per ticket while implementing (add Figma node IDs, confirm UNVERIFIED items against `<BASE_URL>/v3/api-docs`), and fix the doc in the same PR when the implementation differs. Paths are Flutter sources on `main`, for traceability only.

# 06 · Chat, Notifications (inbox + FCM + deep links), Remote-care Tasks

Source: Flutter app `web-flutter-doctor` (read-only analysis, 2026-10-24). All paths relative to repo root. Line refs are `path:line`. Anything not confirmed from code is marked **UNVERIFIED**.

---

## 1. Chat

### 1.1 Conversation list (left pane)

| Aspect | Behaviour | Evidence |
|---|---|---|
| Data source | `conversationsProvider` → `GET /api/messages/conversations` | `lib/state/chat/chat_providers.dart:7-15`, `lib/state/chat/chat_actions.dart:5-14` |
| Fields shown per tile | avatar (photo or initials from first+last word), `name`, `lastMessage` (or l10n `noMessages`), `HH:mm` of `lastActivity.toLocal()`, unread badge (brand pill) when `unread > 0`, selected = 2px brand border | `lib/features/chat/presentation/chat_screen.dart:1132-1249` |
| Avatar image | plain `NetworkImage`, **not** cached_network_image (rule violation) | `chat_screen.dart:1184` |
| Sort / filter | PopupMenu with 4 options: newestFirst (default), oldestFirst, unreadNewestFirst, unreadOldestFirst. Unread filter = `unread > 0`; sort key `lastActivity ?? DateTime(0)` | `chat_screen.dart:42-47, 698-776, 885-895` |
| Empty states | `noUnreadConversations` when unread filter, else `noConversations` | `chat_screen.dart:904-912` |
| Refresh | invalidate on screen open, on app resume, and every 5 s while foreground (recursive `Future.delayed`, self-rescheduling, paused when `_appInForeground == false`) | `chat_screen.dart:62-106` |
| Search users | Typing in search field switches list to `userSearchProvider(query)` → `GET /api/messages/search?q=` ; result tile: avatar, name, role label (Doctor/Patient), chat icon | `chat_screen.dart:660-696, 849-883, 1046-1129`; `chat_providers.dart:45-55` |
| Start conversation from search | 1) refetch conversations, find `participantId == user.id`; if exists just select it. 2) else `POST /api/messages/conversations/start` with `recipientDoctorId` **or** `recipientPatientId` (int) — no auto message. Then select + invalidate | `chat_screen.dart:122-168`; `chat_actions.dart:28-47` |
| Select conversation | sets `_selectedConversationId` (int), fire-and-forget `POST /api/messages/conversations/{id}/read`, invalidates list + conversation | `chat_screen.dart:108-120` |

### 1.2 Conversation screen (right pane)

| Aspect | Behaviour | Evidence |
|---|---|---|
| Layout | `PlatformLayout.useSinglePane` → mobile shell (Android/iOS always, or phone-width web) **or** content width < 720 → single pane (list OR chat with "← Chat" back row). Else `Row(flex 2 list, 24 gap, flex 3 chat)` in white 16-radius card | `chat_screen.dart:604-642, 789-847`; `lib/core/layout/responsive.dart:22, 88-93` |
| Data | `conversationProvider(id)` → `GET /api/messages/conversations/{id}` returns `{conversation, messages[]}` — **all messages in one call, no pagination/cursor** | `chat_providers.dart:17-20`; `chat_actions.dart:16-26, 116-134` |
| Polling | same 5 s timer as list also invalidates the open conversation; resume also invalidates | `chat_screen.dart:82-106` |
| Header | avatar + name; if participant is a patient (`!isDoctor`) name is brand-coloured + underlined and taps to `AppRoutes.patientsWithSelection` with `participantId` | `chat_screen.dart:939-957, 1252-1314` |
| Message list | non-reversed `ListView.builder` wrapped in `SelectionArea`, `KeyedSubtree(ValueKey('msg_{id}_{sentAtMs}'))`; typing indicator appended as last item when `_isTyping` | `chat_screen.dart:1317-1401` |
| Scroll-to-bottom | only after text send: `animateTo(maxScrollExtent + 80, 250 ms)`. No auto-scroll on new incoming or on open | `chat_screen.dart:197-205` |
| Composer | attach IconButton, `TextField(minLines 1, maxLines 4, textInputAction.send)`, `ShifaPrimaryButton(send)`; replaced by `InlineVoiceRecorderBar` when `_chatVoiceCaptureOpen` | `chat_screen.dart:972-1030` |
| Send text | `POST /api/messages/send {conversationId:int, text}`; **no optimistic insert, no `sending` state** — clears field, invalidates list + conversation, scrolls. Error → SnackBar `failedToSendMessage: $e` | `chat_screen.dart:170-214`; `chat_actions.dart:49-81` |
| Mark-read | only on selecting a conversation (not on new messages arriving while open) | `chat_screen.dart:113-115` |
| Typing indicator | **Dead**: `_isTyping`/`_typingSenderRole` are declared but never assigned; no WebSocket/typing endpoint exists. Widget is 3 pulsing dots + "{role} is typing…" | `chat_screen.dart:57-58` (only declaration), `lib/features/chat/presentation/widgets/typing_indicator.dart` |
| Unread badge in shell | `unreadCountProvider` StreamProvider: `GET /api/messages/unread-count` every 10 s (5 s retry when no token, yields 0 on error); drives bottom-nav/sidebar badge and web tab title "(n) SHIFA DOCTOR" | `chat_providers.dart:22-43`; `lib/features/shell/presentation/main_shell.dart:1249`; `lib/app/app.dart:452-455` |

### 1.3 Message types & bubble rules

Shared rules (all 4 bubbles): mine = right-aligned, brand bg, white text; theirs = left, `grey.shade200`, black87; radius 16 with 4 px "tail" corner (bottomRight when mine, bottomLeft when theirs); non-mine shows small sender-role label ("Doctor"/"Patient" from `senderRole`, **not** from `isMine`); timestamp `HH:mm` from `sentAt.toLocal()` (**device TZ, not practice TZ**); mine shows status icon: sending=12px spinner, sent=✓, delivered=✓✓ white70, read=✓✓ `blue.shade300`.

| Type | Rendering | Evidence |
|---|---|---|
| `text` | maxWidth 520, `SelectableText.rich` with linkified URLs (regex `https?://…` or `www.…`), link tap → `url_launcher` external, failure SnackBar `cannotOpenLink` | `widgets/text_message_bubble.dart:9-65, 104-183` |
| `image` | maxWidth 280; `CachedNetworkImage` 280×280 cover, grey placeholder with spinner, broken-image error tile; time/status overlay bottom-right in black54 pill; URL = `fileUrl ?? thumbnailUrl`, absolute passthrough else `normalizePhotoUrl`; tap → full-screen viewer | `widgets/image_message_bubble.dart:55-213` |
| image viewer | black `Scaffold`+AppBar, `InteractiveViewer(minScale .5, maxScale 4)`, `CachedNetworkImage` contain; pushed with raw `MaterialPageRoute` (legacy) | `image_message_bubble.dart:45-52, 216-249` |
| `voice` | maxWidth 280; play/pause `IconButton`, `LinearProgressIndicator` (position/duration), `mm:ss` position & total; total = `content.duration` s if present else player-reported; `audioplayers` `UrlSource(fileUrl)` — **URL is NOT normalized** (relative URLs would fail); each bubble owns an `AudioPlayer` (no global single-playback) | `widgets/voice_message_bubble.dart:23-104, 106-229` |
| `document` | maxWidth 320; icon by extension (pdf/doc,docx/jpg,jpeg,png,gif/other), `fileName` (2 lines) + size (`B/KB/MB` 1 decimal); tap → `canLaunchUrlString` + `launchUrlString(externalApplication)` — opens in browser/external app, no in-app download | `widgets/document_message_bubble.dart:19-94, 96-212` |
| `system` | centred italic grey 12 px `SelectableText` | `chat_screen.dart:1358-1370` |

### 1.4 Sending flows (attachment menu `chat_screen.dart:216-266`: Select image / Take photo / Select document / Record voice)

Common upload: `POST /api/messages/upload-attachment` multipart, single part named `file` (filename set), JWT bearer via `ApiClient.postMultipart`; response JSON `{url}`; any failure → `null` → SnackBar `errorUploadingFile` (`chat_screen.dart:517-545`; `lib/core/api/api_client.dart:257-279`). **No client-side size limit** anywhere. After upload: `POST /api/messages/send` with `type`, `attachmentUrl`, `attachmentName`, `fileSize`, (`duration`), then invalidate list + conversation.

| Flow | Picker | Processing | Send fields | Evidence |
|---|---|---|---|---|
| Image (gallery/camera) | `image_picker.pickImage(source, imageQuality: 100)` | non-web: `ImageCompressionService.compressImage` → skip if < 500 KB, else `FlutterImageCompress.compressAndGetFile(quality 85, minWidth 1920, minHeight 1920)` to `{path}_compressed.jpg`; web: no compression. SnackBars `compressingImage` → `uploadingFile` | `type:'image'`, `attachmentName: xfile.name`, `fileSize: bytes.length` | `chat_screen.dart:268-342`; `lib/features/chat/services/image_compression_service.dart:9-40` |
| Document | `FilePicker.pickFiles(custom, allowedExtensions [pdf,doc,docx,jpg,jpeg,png], single, withData:true)` | bytes from `PlatformFile.bytes` or path | type = `document` if ext ∈ {pdf,doc,docx} else `image` (so a png picked here becomes an image message) | `chat_screen.dart:344-429` |
| Voice | `InlineVoiceRecorderBar` → `DoctorVoiceRecorderSession` (`record` pkg) | web (since `4d05e61`): `opus` → `web_voice.webm`, fallback `aacLc` → `web_voice.m4a`, 16 kHz, mono, bitRate 32000 (WAV froze the page after 20–30 s) (returns blob:/data: URL, fetched via `http.get`); mobile: `aacLc` `.m4a`, 16 kHz, mono, bitRate 32000 into temp dir; autoGain/echoCancel/noiseSuppress on; amplitude stream every 60 ms → 40 waveform bars (dB floor/ceil web -70/-12, mobile -55/-8); duration = Stopwatch seconds | `type:'voice'`, `attachmentName: voice_{epochMs}.m4a|.wav`, `duration` s | `chat_screen.dart:431-515`; `lib/core/widgets/doctor_voice_recorder_session.dart:23-135`; `lib/core/widgets/inline_voice_recorder_bar.dart` |

Recorder UI: dark pill with live bars, ✕ cancel (discard = delete native file), ✓ submit (spinner while finishing), hint text `voiceRecordingFinishHint`; permission denied → SnackBar `microphonePermissionDenied` and auto-cancel (`inline_voice_recorder_bar.dart:44-95`).

`lib/features/chat/services/file_upload_service.dart` (adds a second `thumbnail` part) is **unused** (grep: no callers) — dead code.

### 1.5 Attachment URL resolution
`normalizePhotoUrl(url)` (`lib/core/utils/image_utils.dart:8-24`): null/blank → null; `http(s)://` → as-is; else `AppConfig.resolvedApiBaseUrl + '/' + path` (leading `/` stripped). Applied to: `ChatContact.photoUrl`, `UserSearchResult.photoUrl` (`chat_models.dart:52, 269`), image bubble URL (`image_message_bubble.dart:91-93`). **Not applied** to voice `fileUrl` or document `fileUrl`.

### 1.6 Open-chat-with-patient entry points
- `openChatWithPatient(ref, patientId)` sets `chatPendingPatientIdProvider` + `shellProvider.setTab(DoctorShellTab.chat)` (`lib/features/chat/application/open_chat_with_patient.dart:6-11`). Callers: patient detail panel "Send message" (`lib/features/patients/presentation/patient_detail_panel.dart:292`), home timeline hero (`lib/features/home/presentation/widgets/home_timeline_section.dart:102`).
- `ChatScreen` listens (`chat_screen.dart:582-589`) → `_openConversationWithPatient` (`:547-578`): refetch conversations, find `!isDoctor && participantId == patientId`, else `POST …/conversations/start {recipientPatientId}`; then `_selectConversation`.
- Push deep link: `notificationPendingConversationIdProvider` (`lib/state/shell/shell_controller.dart:19`) set in `app.dart:360-372`, consumed in `chat_screen.dart:591-598` → `_selectConversation(cid)`.

---

## 2. Notifications

### 2.1 Inbox screen (`lib/features/notifications/presentation/notifications_screen.dart`)

| Aspect | Behaviour | Evidence |
|---|---|---|
| Data | `doctorNotificationsProvider` (`FutureProvider.autoDispose`) → `GET /api/notifications` full list, **no pagination / no server filter** | `lib/state/notifications/doctor_notifications_provider.dart:12-23`; `doctor_notification_actions.dart:7-32` |
| Title | "Notifications (n)" when unread count > 0 (`doctorNotificationsUnreadCountProvider`) | `:36-38, 60-64, 96-99` |
| Toolbar | "Mark all as read" (`PUT /api/notifications/read-all`), settings icon → `shellProvider.setTab(DoctorShellTab.notifications)` (=7, which is the *same* notifications tab — effectively a no-op/quirk); back button when route can pop; compact (column) vs wide (row) | `:49-128`; `lib/features/shell/domain/doctor_shell_tab.dart` |
| Filter chips | all / appointments (`type.startsWith('APPOINTMENT_')`) / tasks (`TASK_`) / messages (`contains('MESSAGE')` or DOCUMENT_ACCESS_* or `CARE_PARTNERSHIP_*`) — client-side | `:129-161`; `notification_ui_helpers.dart:410-431` |
| Grouping | sort desc by `createdAt`, group by `dateSectionLabel` → "Today" / "Yesterday" / "Mar 5" (device local TZ, `DateTime.now()`) | `:282-301`; `notification_ui_helpers.dart:29-40` |
| Pull-to-refresh | `RefreshIndicator` invalidates list + unread count and awaits list | `:176-181` |
| Card | unread: bg `style.color @ 6%`, 8 px dot hardcoded `Color(0xFF1976D2)`; icon + type label in `style.color`; relative time (`formatNotificationTime`: "just now", "n min ago", `HH:mm` today, "Yesterday HH:mm", "Mar 5 - HH:mm"); body = `localizedNotificationMessage` maxLines 3; tap → `markAsRead(id)` (`PUT /api/notifications/{id}/read`) + `navigateToNotificationTarget` | `:343-460`; `notification_ui_helpers.dart:9-26` |
| Inline actions | `TASK_COMPLETED && taskId` → "View result"; `appointmentId && !type.contains('CANCELLED')` → "View appointment" + "Open calendar" (`setTab(2)`); `appointmentId && CANCELLED` → "Reschedule" (just navigates like tap) | `:462-538` |
| Empty / error | empty icon + text (filter-specific); error hides technical text if "Not authenticated"/"Unauthorized"/"Bad state:" and offers Retry | `:164-270, 545-588` |
| Document-access approve/reject | `DoctorNotificationsController.approveDocumentAccessRequest / rejectDocumentAccessRequest` exist (`POST /api/document-access-requests/{id}/approve|reject` then mark read) but **no UI calls them** (grep: zero callers outside the controller). The tap route sends the doctor to Patients screen with `patientId`+`documentId` instead | `doctor_notifications_provider.dart:68-78`; `doctor_notification_actions.dart:73-99`; `app.dart:334-357` |

### 2.2 Notification model (`lib/features/notifications/domain/notification_model.dart:2-73`)
`id:int, title:String, message:String, type:String, appointmentId:int?, patientId:int?, documentId:int?, documentAccessRequestId:int?, taskId:int?, documentAccessRequestStatus:String? ("pending|approved|rejected"), patientName:String?, documentTitle:String?, requestingDoctorName:String?, appointmentStartAt:DateTime?, createdAt:DateTime, readAt:DateTime?`; `isRead = readAt != null`. Non-null-safe casts on `id/title/message/type/createdAt`.

### 2.3 TYPE catalogue

Label/icon/colour from `notification_ui_helpers.dart:336-371, 375-406`; body template from `:79-156`; tap route from `app.dart:284-440` (priority order: document access > chat > care partnership > appointmentId > TASK_COMPLETED > patientId > default). Payload keys: `notificationId|id, type, appointmentId, appointmentStartAt, patientId, documentId, documentTitle, documentAccessRequestId, taskId, chatId|conversationId|entityId, partnershipId` (`notification_ui_helpers.dart:208-227`, `app.dart:289-296, 361, 376`).

| type | Label (l10n key) | Icon / colour | Body template | Tap route (args) |
|---|---|---|---|---|
| `APPOINTMENT_BOOKED_BY_PATIENT` | notificationTypeAppointmentBooked | calendar_today / #1976D2 | `notificationMessagePatientBookedAppointment(name, "d Mon yyyy", HH:mm)` using `appointmentStartAt` in **doctor practice TZ**; fallback parses English backend text; else `…NoTime(name)` | Calendar tab (2) on appointment day; sets `calendarGoToAppointmentDayProvider`/`calendarGoToAppointmentIdProvider`; day from `appointmentStartAt` or fetched (`app.dart:77-150, 396-407`) |
| `APPOINTMENT_REMINDER` | notificationTypeAppointmentBooked | same | `notificationMessageAppointmentReminder` | same (appointmentId) |
| `APPOINTMENT_RESCHEDULED_BY_PATIENT`, `APPOINTMENT_CHANGED` | notificationTypeAppointmentRescheduled | event_repeat / #1565C0 | `notificationMessagePatientRescheduled(name)` | same |
| `APPOINTMENT_PAID_BY_PATIENT` | notificationTypeAppointmentPaid | payments / #2E7D32 | `notificationMessagePatientPaid(name)` | same |
| `APPOINTMENT_CANCELLED_BY_PATIENT`, `APPOINTMENT_CANCELLED` | notificationTypeAppointmentCancelled | event_busy / #C62828 | `notificationMessagePatientCancelled(name)` | same; card shows "Reschedule" |
| `APPOINTMENT_ASSIGNED` | (default label) | default | backend message | appointment route; only listed in calendar-refresh set `lib/state/appointments/appointment_invalidation.dart:18-26` |
| `TASK_COMPLETED` | notificationTypeTaskCompleted | check_circle / #2E7D32 | backend message | `notificationPendingTaskIdProvider = taskId`, tab 5, shell pushes `AppRoutes.taskDetails(taskId:int)` (`app.dart:410-422`; `main_shell.dart:314-340`) |
| `TASK_ASSIGNED` | notificationTypeTaskAssigned | assignment / #F9A825 | backend message | no taskId branch → falls to patientId → Patients tab with `patientId` string, else Notifications screen |
| `DOCUMENT_ACCESS_REQUEST` | notificationTypeDocumentAccessRequest | description / #6A1B9A | `documentAccessRequestDetail` with `{doctorName}{documentTitle}{patientName}` | Patients tab (3) + shell push `patientsWithSelection {patientId, documentId, documentTitle, openDocumentViewer:false}` (`app.dart:334-357`) |
| `DOCUMENT_ACCESS_APPROVED` | notificationTypeDocumentAccessApproved | same | `documentAccessApprovedDetail` | same with `openDocumentViewer:true` |
| `DOCUMENT_ACCESS_REJECTED` | notificationTypeDocumentAccessRejected | same | `documentAccessRejectedDetail` | same, viewer false |
| `AI_SCRIBE_READY` | notificationTypeAiScribeReady | auto_awesome / `AppColors.primaryTeal` | backend message | invalidates `draftNotesForAppointmentProvider`/`consultationNotesForAppointmentProvider(aid)` then appointment route (`app.dart:324-328`) |
| `CARE_PARTNERSHIP_INVITE` / `_ACCEPTED` / `_PROGRESS` | notificationTypePartnership{Invite,Accepted,Progress} | handshake / #00838F | `notificationMessagePartnership{Invite,Accepted,Progress}` | shell push `carePartnershipDetail(partnershipId)` or `carePartnerships` (`app.dart:375-393`) |
| `CHAT_MESSAGE`, `NEW_MESSAGE` | notificationTypeChatMessage (NEW_MESSAGE → generic) | chat_bubble / #1565C0 (NEW_MESSAGE → grey) | `notificationMessageChat` | `notificationPendingConversationIdProvider = chatId|conversationId|entityId`, Chat tab (`app.dart:360-372`) |
| other / `GENERAL` | notificationGeneric | notifications / #616161 | backend message, or English-text heuristics (`:127-155`) | by available ids, else `pushNamed(AppRoutes.notifications)` |

Title fallback: `localizedNotificationTitle` maps raw English titles ("New Appointment Booked", "Task completed", …) when type is unknown (`:236-258`).

### 2.4 Unread count
- Notifications: `doctorNotificationsUnreadCountProvider` (`FutureProvider.autoDispose`, `GET /api/notifications/unread/count` → `{count}`; returns 0 on non-2xx). **Not polled** by design (`doctor_notifications_provider.dart:7-9`). Invalidated: after markAsRead/markAllAsRead (`:51-66`), on every foreground FCM (`app.dart:270-283`), on shell resume (`main_shell.dart:262-263`), home refresh (`lib/features/home/application/home_dashboard_refresh.dart:25-26,45-46`), after voice session (`home_voice_session.dart:518-519`). Badges: `main_shell.dart:1109, 1316`, home header widgets.
- Chat: polled 10 s (see 1.2).

### 2.5 FCM / push (`lib/core/services/push_notification_service.dart`)

| Aspect | Behaviour | Evidence |
|---|---|---|
| Init order | `main()`: `Firebase.initializeApp(DefaultFirebaseOptions)` → `FirebaseMessaging.onBackgroundMessage(top-level handler)` → `PushNotificationService().initialize()`; `UnsupportedError`/any error swallowed (app runs without push) | `lib/main.dart:24-33, 47-62` |
| `initialize()` | warm l10n from `SharedPreferences['doctor_language']`; init `flutter_local_notifications` (Android `@mipmap/ic_launcher`, iOS Darwin requests alert/badge/sound); Android 13 `requestNotificationsPermission()`; `FirebaseMessaging.requestPermission(alert,badge,sound, provisional:false)`; `getToken()` → `_onFcmTokenReady`; `onTokenRefresh` → same; `onMessage` → foreground handler; `onMessageOpenedApp` → navigation; `getInitialMessage()` stored as pending | `push_notification_service.dart:36-78` |
| Android channel | id `shifa_doctor_channel`, name "Shifa Doctor Notifications", importance high; also manifest meta `default_notification_channel_id` = same | `:146-156, 230-237`; `android/app/src/main/AndroidManifest.xml:35-37` |
| Token registration | when authenticated & not admin host & `Firebase.apps.isNotEmpty`: `PUT /api/doctors/me/fcm-token {fcmToken}` on token ready and immediately for existing token; flag reset on logout; logout best-effort `PUT …/fcm-token {fcmToken:''}` | `lib/app/app.dart:225-262`; `lib/state/auth/auth_controller.dart:387-394` |
| Foreground | dedupe by `_readNotificationIds` (ids already tapped); call `_onForegroundDataRefresh(data)` → invalidates notification list+count and `refreshCalendarFromPushPayload` for appointment types; build localized title/body from **data payload** (`localizedDoctorPushText`), show local notification (id = notificationId or epoch%100000, payload = JSON of data) | `:159-181, 207-261`; `app.dart:270-283`; `appointment_invalidation.dart:111-155` |
| Background / terminated (mobile) | FCM `notification` block shown by OS (not localized client-side); tap → `onMessageOpenedApp` / `getInitialMessage` → `_deliverPayload` → `_onNotificationTap` or queued in `_pendingLocalNotificationTap` until handler registered; `processPendingInitialMessage()` called post-frame once shell is built | `:80-121`; `app.dart:444` |
| Local-notification tap | `onDidReceiveNotificationResponse` → `jsonDecode(payload)` → same delivery | `:132-144` |
| Web | foreground: browser `Notification` API with tag `shifa_{id}`, click → focus + deliver (`push_notification_web.dart`); background: `web/firebase-messaging-sw.js` shows `payload.notification.title/body` with `/icons/Icon-192.png`, data attached but **no `notificationclick` handler** (tap doesn't deep-link); SW registered from `web/index.html:183-195`; Firebase web config duplicated in SW | `push_notification_web.dart:6-32`; `web/firebase-messaging-sw.js` |
| Deep-link routing | one global handler in `app.dart:284-440` using `navigatorKey` (root) `pushNamedAndRemoveUntil(AppRoutes.shell)` + `shellNavigatorKey`/`ShellScope.pushIntoShell` (nested) + shell tab index; best-effort `PUT /api/notifications/{id}/read` on tap (`app.dart:306-321`). In-app taps reuse the same path via `navigateToNotificationTarget` → `deliverPayloadFromApp` (`notification_ui_helpers.dart:230-233`) | |
| Language change | `refreshLocalizationCache()` on `languageProvider` change and language settings screen | `app.dart:205-206`; `lib/features/settings/presentation/language_settings_screen.dart:64` |
| iOS | `UIBackgroundModes: fetch, remote-notification, audio`; mic/camera/photo usage strings present | `ios/Runner/Info.plist:61-78` |
| Firebase project | staging `shifa-doctor-staging`, sender `55173524197`, iOS bundle `uz.shifa.doc` (keys committed in `lib/firebase_options.dart:37-62`) | |

---

## 3. Remote-care Tasks

Feature gate: `doctorFeatureProvider(DoctorFeature.remoteCareTasks)` → premium tier; tasks screen skips fetch when not allowed (would 403) (`lib/features/tasks/presentation/tasks_screen.dart:33-41`; `lib/core/subscription/doctor_subscription.dart:33,50`). Clinic-staff role never receives task deep links (`main_shell.dart:316-319`).

### 3.1 Tasks list (`tasks_screen.dart`)
- Data: `tasksProvider` (`StateNotifier<List<RemoteCareTask>>`), `loadTasks()` → `GET /api/tasks` (no params from this screen); errors swallowed with `print` (`lib/state/tasks/tasks_provider.dart:12-25`). Loaded post-frame; `RefreshIndicator` + refresh IconButton; reloaded after returning from create/template routes (`:118-124, 130-136`).
- Header: title, subtitle `remoteCareTasksSubtitle`, "Create task" (primary) → `AppRoutes.createTask`, "Use template" (secondary) → `AppRoutes.selectTemplate`. Compact = stacked full-width buttons.
- Summary cards: Active (`status==active`, blue), Completed (green), Overdue (= `status==expired`, red) (`:70-75, 195-248`).
- Filters (client-side): search (taskName or patientName contains), status dropdown (All + draft/active/completed/expired/cancelled localized), category dropdown (All + vital/medication/exercise/other). **Compact-mode category items show raw `cat.name`** (unlocalized) (`:332-336`) vs localized in wide mode (`:432-447`). `_selectedPatientId` filter variable exists but has no UI (`:26, 55-57`).
- Task card (`:599-907`): category icon (vital=monitor_heart, exercise=directions_run, medication=medication, other=assignment), taskName, patient initials avatar + name, status pill (active green / completed blue / expired orange / cancelled red / draft grey), category · "n× per day", date range (`start – end` | `start · n days` | `start`) as `yyyy-MM-dd`, progress bar `completed/total` (+ label, green when > 0). Tap → `AppRoutes.taskDetails` with `task.id` (int).
- Empty state: icon + `noTasksFound` + `createFirstRemoteTask` + Create button.

### 3.2 Task details (`task_details_screen.dart`)
- Data: `taskByIdProvider(id)` (`GET /api/tasks/{id}`, null on error) + `taskCheckInsProvider(id)` (`GET /api/tasks/{id}/check-ins`, [] on error); **polled every 15 s** via `Timer.periodic` invalidation (`:17, 32-44`). Progress comes from `task.progress` (embedded), `taskProgressProvider` (`GET /api/tasks/{id}/progress`) is defined but not used in the tasks feature (**UNVERIFIED** elsewhere).
- Info card: name + status pill; rows Patient, Category, Input type (numeric/text/boolean), Input label?, Description?, Start date, End date?, Duration (`n days`)?; Progress: bar + `completed/total` + stats Completed/Pending/Missed (`:118-250`).
- Check-ins: non-scrolling `ListView.builder(shrinkWrap)`; card: `scheduledDate yyyy-MM-dd`, `scheduledTime HH:mm`, status pill using `effectiveCheckInStatus` (pending with scheduled datetime in the past → shown as missed, computed with **device `DateTime.now()`**, not patient TZ) (`:453-466, 641-761`); completed shows `inputLabel|Value: numeric|text|yes/no` and notes. Tap → bottom sheet (`showScrollableFormBottomSheet`) with Scheduled, Status, Submitted at (converted to `patientTimeZone` via `tz`, `:560-582`), Value, Notes / "No submission received" / "Awaiting submission", Close (`:468-552`).
- Cancel: only when `status == active`; destructive secondary button → AlertDialog (No / "Yes, Cancel") → `PATCH /api/tasks/{id}/cancel {}` → invalidate task, `loadTasks()`, pop, SnackBar `taskCancelled`; error SnackBar (`:305-361`).

### 3.3 Create task (`create_task_screen.dart`)
Route args `{patientId:int?, template:TaskTemplate?}` (`lib/app/router.dart:197-204, 261-268`).

Form fields & defaults (`:34-66`): patient (required; searchable dialog over `patientsProvider`, search by name or id, shows "Name (ID: n)") (`:638-828`); taskName* (required, `pleaseEnterTaskName`); description (3 lines); category* dropdown (default vital); inputType* dropdown (default numeric); inputLabel; schedule mode `SegmentedButton` evenSpacing|customTimes (`:384-415`); evenSpacing: timesPerDay 1–15 dropdown, startTime (default 08:00, `showTimePicker`), if ≥2 interval dropdown from `[1,2,3,4,6,8,12,24]` h and `_SlotPreview` (slots = start + k·interval while < 24:00; warns "n slot(s) won't fit before midnight") (`:125-138, 418-491, 834-902`); customTimes: editable sorted list of HH:mm (add via time picker, dedupe exact, edit, remove only if > 1) (`:493-534, 907-1047`); startDate* (default now, picker range today…+365 d, locale-aware); "Use end date" checkbox (default on) → endDate picker (min startDate, default start+30 d) else durationDays numeric text (no validation); notesRequired checkbox; notesLabel (Enter submits).

Template prefill (`:69-104`): all fields; `timesPerDay.clamp(1,15)`; `intervalHours` snapped up to nearest option; `customTimes` non-empty → customTimes mode.

Validation on save (`:149-170`): form validators (task name, patient), `pleaseSelectPatient` SnackBar, customTimes non-empty.

Payload rules (`:172-218`; `lib/state/tasks/task_actions.dart:10-54`): `POST /api/tasks` `{patientId, taskName, description?, category:UPPER, timesPerDay (custom → count, clamp 1..15), startTime:"HH:mm" (custom → first slot), intervalHours only when !custom && timesPerDay>=2, customTimes:["HH:mm"] when custom, startDate:"yyyy-MM-dd", endDate? | durationDays?, inputType:UPPER, inputLabel?, notesRequired, notesLabel?}`. Success → `loadTasks()`, pop, SnackBar `taskCreated`; error SnackBar `failedToCreateTask: $e`.

### 3.4 Select template (`select_template_screen.dart`)
Local `setState` fetch of `GET /api/tasks/templates` (not a provider); loading / error+Retry / empty `noTemplatesYet` / `RefreshIndicator` list of Cards: taskName, "Category: X · Times per day: n", chevron; tap → `ShellScope.pushNamed(createTask, {template})` and **pops itself when create returns** (`:131-138`).

### 3.5 Models & enums (`lib/features/tasks/domain/task_models.dart`)
- `TaskCategory {vital, exercise, medication, other}` (API UPPERCASE, unknown → other); `TaskStatus {draft, active, completed, expired, cancelled}` (unknown → active); `TaskInputType {numeric, text, boolean}` (unknown → text); `CheckInStatus {pending, completed, missed}`.
- `TaskTemplate` (`:55-124`): `taskName, description?, category, timesPerDay, startTime?(HH:mm), intervalHours?, customTimes?[sorted], morningTime?, afternoonTime?, eveningTime? (parsed, never sent/shown → legacy), inputType, inputLabel?, notesRequired(default false), notesLabel?`.
- `RemoteCareTask` (`:127-233`): `id, patientId, patientName, patientTimeZone?, taskName, description?, category, status, timesPerDay, startTime?, intervalHours?, customTimes?, morning/afternoon/eveningTime?, startDate, endDate?, durationDays?, inputType, inputLabel?, notesRequired, notesLabel?, createdAt, progress?`.
- `TaskProgress` (`:236-261`): `totalCheckIns, completedCheckIns, pendingCheckIns, missedCheckIns`; `completionPercentage`.
- `TaskCheckIn` (`:264-318`): `id, scheduledDate, scheduledTime?, status, numericValue?(num→double), textValue?, booleanValue?, notes?, completedAt?`.
- Non-null-safe `as int/String` casts on required fields; `int.parse` on time parts (throws on bad format).

---

## 4. State / providers

| Provider | File | Type | Holds | Refresh / invalidation |
|---|---|---|---|---|
| `conversationsProvider` | `state/chat/chat_providers.dart:7` | `FutureProvider<List<ChatContact>>` (watches `authTokenProvider`, throws `StateError('Not authenticated')` without token) | conversation list | chat screen open, resume, 5 s timer, after select/send/start/upload |
| `conversationProvider(id:String)` | `:17` | `FutureProvider.family<ConversationWithMessages,String>` | conversation + all messages | same triggers for the selected id |
| `unreadCountProvider` | `:22` | `StreamProvider<int>` | chat unread | self-polls 10 s (5 s idle w/o token), restarts on token change |
| `userSearchProvider(q)` | `:45` | `FutureProvider.family<List<UserSearchResult>,String>` | search results (empty for blank q) | per query |
| `chatPendingPatientIdProvider` | `features/chat/application/open_chat_with_patient.dart:6` | `StateProvider<String?>` | patient to open | cleared by ChatScreen after handling |
| `notificationPendingConversationIdProvider` | `state/shell/shell_controller.dart:19` | `StateProvider<int?>` | conversation from push | cleared by ChatScreen |
| `notificationPendingTaskIdProvider` | `:16` | `StateProvider<int?>` | task from push | cleared by MainShell after pushing details |
| `shellProvider` | `:10` | `StateNotifierProvider<ShellController,int>` | tab index (default 1 home) | — |
| `doctorNotificationsProvider` | `state/notifications/doctor_notifications_provider.dart:12` | `FutureProvider.autoDispose<List<DoctorNotificationModel>>` ([] w/o token; calls `client.setToken(token)`) | inbox | mark read / read-all, FCM foreground, shell resume, home refresh, pull-to-refresh |
| `doctorNotificationsUnreadCountProvider` | `:25` | `FutureProvider.autoDispose<int>` | unread count | same as above |
| `doctorNotificationsControllerProvider` | `:36` | `Provider<DoctorNotificationsController>` | refresh/markAsRead/markAllAsRead/approve/reject | — |
| `notificationAutoRefreshProvider` | `:9` | `Provider.autoDispose<void>` | nothing (placeholder) | dead |
| `tasksProvider` | `state/tasks/tasks_provider.dart:28` | `StateNotifierProvider<TasksController,List<RemoteCareTask>>` | task list | `loadTasks({patientId,status})` on screen init, pull, refresh, after create/cancel/return |
| `taskByIdProvider(id:int)` | `:33` | `FutureProvider.family<RemoteCareTask?,int>` | task (null on error) | 15 s poll in details, after cancel |
| `taskCheckInsProvider(id)` | `:42` | `FutureProvider.family<List<TaskCheckIn>,int>` | check-ins ([] on error) | 15 s poll |
| `taskProgressProvider(id)` | `:51` | `FutureProvider.family<TaskProgress?,int>` | progress | unused in tasks feature |

---

## 5. Endpoints

| Method & path | Query | Request body | Response parsed | Errors handled |
|---|---|---|---|---|
| GET `/api/messages/conversations` | — | — | `[{id, participantName, participantPhotoUrl?, isDoctorParticipant?, participantId, lastMessage?, lastMessageAt?, unreadCount?}]` | non-2xx → `Exception` (`chat_actions.dart:5-14`) |
| GET `/api/messages/conversations/{id}` | — | — | `{conversation:{…}, messages:[ChatMessage]}` | non-2xx → Exception |
| POST `/api/messages/conversations/start` | — | `{recipientDoctorId:int}` or `{recipientPatientId:int}` | `ChatContact` | non-2xx → Exception |
| POST `/api/messages/send` | — | `{conversationId?:int, recipientDoctorId?:int, recipientPatientId?:int, text?, type?('text'|'image'|'voice'|'document'), attachmentUrl?, attachmentName?, thumbnailUrl?, fileSize?:int, duration?:int}` | `ChatMessage` | non-2xx → Exception |
| POST `/api/messages/conversations/{id}/read` | — | `{}` | — | non-2xx → Exception (caught & ignored by caller) |
| GET `/api/messages/search` | `q` | — | `[{id, name, photoUrl?, isDoctor?, email?, phone?}]` | non-2xx → Exception |
| GET `/api/messages/unread-count` | — | — | `{count:int}` | non-2xx → Exception → stream yields 0 |
| POST `/api/messages/upload-attachment` | — | multipart part `file` (filename) | `{url}` | any failure → null → SnackBar |
| GET `/api/notifications` | — | — | `[DoctorNotificationModel]` | 401 → "Unauthorized" Exception; other → Exception |
| GET `/api/notifications/unread/count` | — | — | `{count}` | non-2xx → 0 |
| PUT `/api/notifications/{id}/read` | — | `{}` | — | 401 / other Exception |
| PUT `/api/notifications/read-all` | — | `{}` | — | 401 / other Exception |
| POST `/api/document-access-requests/{id}/approve` · `/reject` | — | `{}` | — | 401 / other Exception (no UI caller) |
| PUT `/api/doctors/me/fcm-token` | — | `{fcmToken:string}` (`''` on logout) | ignored | logged only |
| GET `/api/tasks` | `patientId?`, `status?` (UPPER) | — | `[RemoteCareTask]` (utf8-decoded) | 401 / other Exception; swallowed by controller |
| GET `/api/tasks/templates` | — | — | `[TaskTemplate]` | 401 / other |
| POST `/api/tasks` | — | see §3.3 | `RemoteCareTask` | 401 / other |
| GET `/api/tasks/{id}` | — | — | `RemoteCareTask` | → null |
| GET `/api/tasks/{id}/progress` | — | — | `TaskProgress` | → null |
| GET `/api/tasks/{id}/check-ins` | — | — | `[TaskCheckIn]` | → [] |
| PATCH `/api/tasks/{id}/cancel` | — | `{}` | — | 401 / other Exception |

None of these validate `content-type` before `jsonDecode` (rule in CLAUDE.md), and only task actions decode UTF-8 from `bodyBytes`.

---

## 6. Models summary (chat)

- `ChatContact` (`chat_models.dart:3-63`): `id:String (from any), name:String!, photoUrl:String?, isDoctor:bool(=false), participantId:String, lastMessage:String?, lastActivity:DateTime?, unread:int(=0), messages:[] (always empty)`; `copyWith`.
- `MessageType {text,image,voice,document,system}` unknown→text; `MessageStatus {sending,sent,delivered,read}` null/unknown→sent; `SenderRole {doctor,patient}` null/unknown→doctor.
- `MessageContent`: `text?, fileUrl? (fileUrl|file_url|imageUrl|root imageUrl|attachmentUrl), thumbnailUrl?, fileName? (fileName|file_name), fileSize?:int, duration?:int`.
- `ChatMessage`: new format requires `content`+`type`; `id, chatId, senderId` (toString), `senderRole, type, content, status, sentAt=createdAt (ISO), isMine(false), isRead(false)`. Legacy format (`:212-243`) infers type from `attachmentName` extension — keep only if backend still emits it (**UNVERIFIED**).
- `UserSearchResult`: `id, name!, photoUrl?, isDoctor(false), email?, phone?`.

---

## 7. Platform / third-party → KMP equivalents

| Flutter | Used for | KMP/CMP equivalent |
|---|---|---|
| `record` ^6.1.2 (`AudioRecorder`, `RecordConfig`, `onAmplitudeChanged`) | chat voice (+ AI scribe) | `expect class AudioRecorder { start(path), stop(), amplitudeFlow }`; Android `MediaRecorder` (AAC-LC, 16 kHz mono, 32 kbps, `.m4a`) + `maxAmplitude` poll 60 ms; iOS `AVAudioRecorder` (kAudioFormatMPEG4AAC, 16 kHz, mono, 32 kbps, `isMeteringEnabled`, `averagePower(forChannel:)`) via Swift bridge. Keep same file naming `voice_{epochMs}.m4a` and `duration` seconds |
| `audioplayers` ^6.0.0 (`UrlSource`, state/position/duration streams) | voice bubble playback | `expect class AudioPlayer`: Android `ExoPlayer`/`MediaPlayer`, iOS `AVPlayer` with periodic time observer; expose `StateFlow<PlaybackState(position,duration,isPlaying)>`. Add single-active-player rule (not in Flutter) |
| `cached_network_image` | image bubbles, viewer | Coil 3 (`coil-compose` multiplatform, `AsyncImage`) with OkHttp/Ktor engine; or Kamel. Reuse auth-less URLs as today |
| `flutter_image_compress` (quality 85, 1920 box, skip <500 KB) | image before upload | Android `BitmapFactory` + `inSampleSize` + `compress(JPEG, 85)`; iOS `UIImage.jpegData(compressionQuality 0.85)` after resize (Swift bridge). Same thresholds |
| `image_picker` (gallery/camera) / `file_picker` (pdf,doc,docx,jpg,jpeg,png) | attachments | Android Photo Picker / `ActivityResultContracts.OpenDocument` with MIME list; iOS `PHPickerViewController` / `UIDocumentPickerViewController`. Return bytes + display name + size |
| `url_launcher` (document open, links) | docs + links | Android `Intent.ACTION_VIEW`; iOS `UIApplication.open` / `SFSafariViewController`. Consider in-app download+share instead (§8) |
| `http.MultipartRequest` via `ApiClient.postMultipart` | upload | Ktor `submitFormWithBinaryData` with `Authorization: Bearer` header, part name `file` |
| `firebase_messaging` + `flutter_local_notifications` | push | Android: `firebase-messaging-ktx` `FirebaseMessagingService` (onMessageReceived → NotificationCompat on channel `shifa_doctor_channel`, importance HIGH, payload extras = data map; `onNewToken` → PUT fcm-token). iOS: APNs via `UNUserNotificationCenter` + `FirebaseMessaging` Swift delegate (`messaging(_:didReceiveRegistrationToken:)`, `willPresent` → `.banner/.sound/.badge`, `didReceive` → data dict). Common: `expect fun pushEvents(): Flow<PushEvent.Token|Foreground|Tap(data)>`; one shared `NotificationRouter` (Decompose/Voyager root) replicating §2.3 priorities; pending-tap buffer for cold start (replaces `getInitialMessage`) |
| Web service worker / browser `Notification` | web only | not needed (Android+iOS target) |
| `SharedPreferences['doctor_language']` warm-up for push l10n | localized foreground pushes | multiplatform-settings; localize in shared code from `data` map |
| `timezone` pkg (`tz.getLocation`) | check-in submittedAt in patient TZ, appointment booked text in doctor TZ | kotlinx-datetime `TimeZone.of(iana)` |
| `characters` initials | avatars | `String.codePoints`/grapheme via `kotlinx` (or simple first char) |

---

## 8. Quirks, bugs, dead code, tech debt (do not copy)

1. **Typing indicator is dead UI**: `_isTyping` never set (`chat_screen.dart:57`); there is no typing transport. Drop or build on a real channel.
2. **No optimistic send / no `sending` status**; the `MessageStatus.sending` icon can never show (`chat_screen.dart:170-214`).
3. **Polling everywhere**: 5 s conversation+list refetch (`chat_screen.dart:94-106`), 10 s unread-count stream, 15 s task-details. Full-list refetch on every tick; messages list has **no pagination** (`chat_actions.dart:16-26`). KMP should add `since`/cursor params or a WebSocket/SSE (backend work; **UNVERIFIED** backend support).
4. **Timezone rule violations**: chat timestamps use `sentAt.toLocal()` (`text_message_bubble.dart:165`, `chat_screen.dart:1224`), notification time/grouping uses `DateTime.now()` + `toLocal()` (`notification_ui_helpers.dart:9-40`), check-in "missed" uses device now (`task_details_screen.dart:454-466`). Should use practice TZ (chat/notifications) / patient TZ (check-ins).
5. **HIPAA/logging**: image bubble `debugPrint('FULL MESSAGE OBJECT: $message')` + sender id on every build (`image_message_bubble.dart:59-63`); notification fetch logs ids/types (`doctor_notification_actions.dart:10-26`); push tap handler logs full payload incl. patient/doc ids (`app.dart:285-304`).
6. **Mark-read only on select**; messages that arrive while the conversation is open are not acknowledged until reselect (`chat_screen.dart:108-120`).
7. **Voice/document URLs not normalized** (`voice_message_bubble.dart:92-99`, `document_message_bubble.dart:103`) while images are; relative URLs from backend would break playback/open.
8. **Avatars use `NetworkImage`** (no cache) in list/header/search tiles (`chat_screen.dart:1090, 1184, 1276`).
9. **Picked "document" png becomes an image message** (`chat_screen.dart:401-402`) — intentional? Keep rule but document it.
10. **No attachment size limit, no MIME check** client-side; image picker `imageQuality: 100` then compress separately.
11. **Dead code**: `FileUploadService` (unused, `features/chat/services/file_upload_service.dart`), `notificationAutoRefreshProvider` (`doctor_notifications_provider.dart:9`), `approve/rejectDocumentAccessRequest` controller methods & endpoints (no UI), `taskProgressProvider` (not used by tasks feature), `_selectedPatientId` filter in tasks list, `morning/afternoon/eveningTime` template fields (parsed, never used), `ChatContact.messages` always `[]`, legacy message format branch (`chat_models.dart:212-243`, **UNVERIFIED** if backend still emits).
12. **Notifications "settings" button** switches to the notifications tab itself (`notifications_screen.dart:79-84, 118-123`) — no settings screen behind it.
13. **English-string parsing in l10n** (`notification_ui_helpers.dart:42-74, 127-168, 260-333`): client reverse-parses backend English copy to localize. KMP should rely on `type` + structured fields only; ask backend to always send `patientName`/`appointmentStartAt`.
14. **Hardcoded hex colours** in notification styles (`notification_ui_helpers.dart:375-406`, `notifications_screen.dart:404`) and recorder bar (`inline_voice_recorder_bar.dart:115`) — map to theme tokens.
15. **Compact-mode category dropdown unlocalized** (`tasks_screen.dart:332-336`); patient dropdown allocates a `TextEditingController` per build (`create_task_screen.dart:695`); `durationDays` not validated (`:590-599`).
16. **Error swallowing**: `TasksController.loadTasks` prints and leaves stale state (`tasks_provider.dart:21-24`); `taskByIdProvider` returns null → "Task not found" even on network error (`:33-40`).
17. **Web SW has no `notificationclick`** → background web pushes don't deep-link (`web/firebase-messaging-sw.js`); irrelevant for KMP but explains current behaviour.
18. **Duplicate Firebase config** committed in `firebase_options.dart` and SW; staging-only project hardcoded — KMP should take `google-services.json` / `GoogleService-Info.plist` per flavour.
19. **`client.setToken(token)` hack** inside notification providers to beat a race (`doctor_notifications_provider.dart:19-21`) — fix the auth/token plumbing instead.
20. **Start-conversation does a full conversations refetch** to find duplicates (`chat_screen.dart:131`) instead of trusting `/start` idempotency (**UNVERIFIED** whether backend `/start` returns the existing conversation).
21. **Chat `conversationsProvider` throws `StateError` when logged out** → error UI flashes during logout (`chat_providers.dart:9-12`).
22. Appointment deep link shows an **unlocalized** "Opening appointment..." dialog (`app.dart:106-121`).

---

## 9. Proposed tickets

### CHAT-01 · Chat models, API client & repository [M]
Route · — · Spec refs §1.1, §1.4, §5, §6 · API: GET /api/messages/conversations, GET /api/messages/conversations/{id}, POST /api/messages/conversations/start, POST /api/messages/send, POST /api/messages/conversations/{id}/read, GET /api/messages/search?q, GET /api/messages/unread-count, POST /api/messages/upload-attachment
Build: kotlinx-serialization models (`ChatContact`, `ChatMessage`, `MessageContent`, enums with unknown→default), `ChatRepository` over shared Ktor client (bearer), multipart upload (`file` part), URL normalizer (absolute passthrough else base+path) applied to photo, image, voice, document URLs.
Done when: unit tests parse the new-format JSON (camel + snake keys, root `imageUrl` fallback), unknown enums default, upload returns `url`, 401 propagates to auth layer.
Depends: core API client / auth tickets (other analyses).
Fix: #7 (normalize all URLs), skip legacy format unless backend confirms (#11), content-type check before decode.

### CHAT-02 · Conversation list screen [M]
Route · `chat` tab · Spec refs §1.1 · API: conversations, search, start
Build: list with avatar(Coil)/name/last message/HH:mm (practice TZ)/unread pill/selected state; sort+unread filter menu (4 options); search field switching to user results; start-or-select conversation; empty/loading/error; pull/periodic refresh via repository `Flow` (poll interval configurable, pause in background).
Done when: filter/sort match §1.1 rules; selecting marks read and updates unread; search tile shows role.
Depends: CHAT-01, shell/navigation.
Fix: #4 (practice TZ), #8 (cached avatars), #20 (rely on `/start` if backend confirms), #21.

### CHAT-03 · Conversation screen: text messages, header, mark-read [M]
Route · `chat/{conversationId}` (single pane on phones; master-detail on tablets ≥720 dp content) · Spec refs §1.2, §1.3 text/system · API: conversation, send, read
Build: message list (newest at bottom, auto-scroll on open and on own send), text bubble (mine/theirs styling, linkify, selectable), system bubble, status icons, composer (1–4 lines, send action), header tapping to patient detail when participant is patient, optimistic local `sending` message replaced on success / marked failed on error, mark-read when new incoming messages appear while open.
Done when: send shows instantly then reconciles; incoming messages auto-mark read; links open externally.
Depends: CHAT-02.
Fix: #2, #6, #4; drop typing indicator (#1).

### CHAT-04 · Image attachments: pick, compress, upload, bubble, full-screen viewer [M]
Route · same · Spec refs §1.3 image, §1.4 image · API: upload-attachment, send(type=image)
Build: attach sheet (gallery/camera/document/voice), platform pickers (expect/actual), compression (skip <500 KB; else JPEG q85, max 1920), progress snackbars, 280×280 Coil bubble with time/status overlay, pinch-zoom viewer (0.5–4×), error tiles.
Done when: >500 KB photo is uploaded compressed; bubble renders from `fileUrl ?? thumbnailUrl`; viewer zooms.
Depends: CHAT-03.
Fix: #5 (no message logging), #10 (add size cap e.g. 20 MB, MIME check), #8.

### CHAT-05 · Document attachments: pick, upload, bubble, open [S]
Route · same · Spec refs §1.3 document, §1.4 document · API: upload-attachment, send(type=document|image)
Build: document picker limited to pdf/doc/docx/jpg/jpeg/png, extension→type rule, bubble with icon/name/size, open via system viewer (download to cache + share/open on iOS).
Done when: pdf opens on both platforms; png picked here still sends as image (documented).
Depends: CHAT-04.
Fix: #7, #9 document the rule.

### CHAT-06 · Voice messages: recorder + playback [L]
Route · same · Spec refs §1.3 voice, §1.4 voice · API: upload-attachment, send(type=voice, duration)
Build: `expect/actual` recorder (AAC-LC 16 kHz mono 32 kbps `.m4a`, amplitude flow 60 ms, permission handling), inline recorder bar (40-bar waveform, cancel/confirm, hint), `expect/actual` player (position/duration/state flows), voice bubble (play/pause, progress, mm:ss, duration from payload), single active player.
Done when: record→upload→bubble playable on Android & iOS; cancel deletes temp file; mic denial shows localized error.
Depends: CHAT-03.
Fix: #7 (normalize URL), add single-playback rule.

### CHAT-07 · Chat unread badge + open-chat-with-patient entry points [S]
Route · shell badge; patient detail "Send message"; home timeline · Spec refs §1.2 unread, §1.6 · API: unread-count
Build: shared `ChatUnreadCount` flow (poll 10 s while app foreground, 0 when logged out), shell tab badge, `openChatWithPatient(patientId)` navigation intent that finds or starts the conversation.
Done when: badge updates after reading; entry points land in the right conversation.
Depends: CHAT-02, shell.
Fix: #3 (pause polling in background), #21.

### NOTIF-01 · Notification model, repository, type catalogue [S]
Route · — · Spec refs §2.2, §2.3, §5 · API: GET /api/notifications, GET /api/notifications/unread/count, PUT /api/notifications/{id}/read, PUT /api/notifications/read-all
Build: model, repository, `NotificationType` sealed mapping (label key, icon, colour token, filter bucket, body template from structured fields), `NotificationTarget` resolver implementing §2.3 priorities as pure function (testable).
Done when: table-driven tests cover every type → label/colour/filter/target; unknown type → generic/inbox.
Depends: core API.
Fix: #13 (no English parsing), #14 (theme tokens).

### NOTIF-02 · Notifications inbox screen [M]
Route · `notifications` (tab + pushable) · Spec refs §2.1 · API: list, read, read-all
Build: title with unread count, mark-all, filter chips (all/appointments/tasks/messages), date-grouped list (Today/Yesterday/d Mon in practice TZ), unread tint+dot, relative time, inline actions (View result / View appointment / Open calendar / Reschedule), tap → mark read + navigate via NOTIF-01 resolver, pull-to-refresh, empty/error/retry.
Done when: grouping and actions match §2.1; errors for unauthenticated hide technical text.
Depends: NOTIF-01, shell navigation to calendar/patients/tasks/chat.
Fix: #4, #12 (remove fake settings button or link a real settings screen), #16-style error swallowing.

### NOTIF-03 · Notification unread badge & cross-feature invalidation [S]
Route · shell/home badges · Spec refs §2.4 · API: unread/count
Build: shared `NotificationsUnread` state refreshed on: mark read/all, foreground push, app resume, home refresh, pull-to-refresh (no polling).
Done when: badge reflects count after each trigger.
Depends: NOTIF-01, PUSH-02.
Fix: #19 (no token hack).

### NOTIF-04 · Document-access request actions (approve/reject) [S] — optional
Route · inbox card · Spec refs §2.1 last row · API: POST /api/document-access-requests/{id}/approve|reject
Build: only if product confirms; render Approve/Reject on `DOCUMENT_ACCESS_REQUEST` with `documentAccessRequestStatus == pending`, then mark read.
Done when: buttons disappear after action; backend status reflected.
Depends: NOTIF-02.
Fix: #11 (currently dead endpoints — confirm with backend before building).

### PUSH-01 · FCM/APNs platform bridge + token registration [M]
Route · app start / login / logout · Spec refs §2.5 · API: PUT /api/doctors/me/fcm-token
Build: Android `FirebaseMessagingService` + channel `shifa_doctor_channel` (HIGH); iOS Swift `UNUserNotificationCenterDelegate` + `MessagingDelegate` (APNs → FCM token); `expect fun pushEvents(): Flow<PushEvent>`; permission request on first authenticated launch; upload token on ready/refresh when authenticated, clear (`''`) on logout.
Done when: token appears on backend after login; refreshed token re-uploaded; cleared on logout; no upload for admin/unauthenticated.
Depends: Firebase project per flavour (google-services / plist), auth state.
Fix: #18 (no committed keys in shared code).

### PUSH-02 · Foreground handling: localized local notification + data refresh [M]
Route · — · Spec refs §2.5 foreground · API: none
Build: on foreground push: dedupe ids already tapped, invalidate notifications list/count and appointment/calendar providers for appointment types, show local notification with title/body localized from `data` (NOTIF-01 templates, app language), payload = data map, id = notificationId.
Done when: foreground push shows localized banner; tapping routes like a cold-start tap; duplicates suppressed.
Depends: PUSH-01, NOTIF-01, NOTIF-03.
Fix: #5 (never log payload contents).

### PUSH-03 · Deep-link router (tap, background, terminated) [M]
Route · all targets: patients(selection+document viewer), chat(conversation), care partnership detail/list, calendar(appointment day/id), task details, patient, inbox · Spec refs §2.3 tap column, §2.5 · API: PUT /api/notifications/{id}/read (best effort), appointment fetch for day (existing)
Build: single `NotificationRouter` consuming `PushEvent.Tap` + in-app taps; pending-intent buffer until root navigator ready; implements priority order; resets back stack to shell then pushes nested destination; sets tab; marks read best-effort; AI_SCRIBE_READY invalidates notes.
Done when: each type in §2.3 lands on its screen from foreground, background and terminated states on both platforms; chat tap opens exact conversation; TASK_COMPLETED opens details.
Depends: PUSH-01, CHAT-03, TASK-03, calendar & patients tickets (other analyses).
Fix: #22 localize loading UI; keep router pure/testable.

### TASK-01 · Task models, repository, feature gate [S]
Route · — · Spec refs §3.5, §5 · API: GET /api/tasks(?patientId&status), GET /api/tasks/templates, GET /api/tasks/{id}, GET /api/tasks/{id}/check-ins, GET /api/tasks/{id}/progress, POST /api/tasks, PATCH /api/tasks/{id}/cancel
Build: models/enums (UPPERCASE wire, safe defaults, `HH:mm` and `yyyy-MM-dd` codecs), repository with typed errors (no null-on-error), premium feature gate helper (`remoteCareTasks`).
Done when: serialization round-trip tests incl. `customTimes`, 401 and network errors surface distinctly.
Depends: core API, subscription/profile ticket.
Fix: #16, drop unused `morning/afternoon/eveningTime` unless backend uses them (#11), content-type check.

### TASK-02 · Tasks list screen [M]
Route · `tasks` tab (premium only) · Spec refs §3.1 · API: GET /api/tasks
Build: header + Create/Use-template buttons, summary cards (active/completed/expired), search + status + category filters (all localized), task cards (icon, patient, status pill, frequency, date range, progress), empty state, pull-to-refresh, reload on return.
Done when: filters combine correctly; non-premium users never call the endpoint; cards match §3.1.
Depends: TASK-01, shell, patients list for names (embedded in task).
Fix: #15 (localization), remove dead patient filter (#11).

### TASK-03 · Task details + check-ins + cancel [M]
Route · `tasks/{id}` · Spec refs §3.2 · API: GET /api/tasks/{id}, GET /api/tasks/{id}/check-ins, PATCH /api/tasks/{id}/cancel
Build: info card, progress bar + stats, check-in list with effective status computed in **patient TZ**, check-in detail bottom sheet (submitted-at in patient TZ, value by input type, notes), cancel with confirm dialog, refresh on resume + optional 15 s poll while visible.
Done when: pending check-in past its slot (patient TZ) shows Missed; cancel updates list and pops; errors shown, not swallowed.
Depends: TASK-02.
Fix: #4 (patient TZ), #16.

### TASK-04 · Create task form (+ template prefill) [L]
Route · `tasks/create?patientId&template` · Spec refs §3.3 · API: POST /api/tasks, patients list
Build: searchable patient picker (name/id), text fields, category/input-type dropdowns, schedule mode segmented control, even-spacing (1–15/day, start time, interval options, midnight-fit preview + warning) and custom-times editor (add/edit/remove, dedupe, sorted, ≥1), start date (today…+365), end date vs duration days (validated int > 0), notes required/label, payload rules exactly as §3.3, success/error feedback, template prefill incl. interval snapping and custom-times mode.
Done when: payload snapshots match Flutter for even-spacing and custom-times cases; validation blocks missing patient/name/slots/invalid duration.
Depends: TASK-02, patients repository.
Fix: #15 (controller leak n/a in Compose; validate durationDays).

### TASK-05 · Select template screen [S]
Route · `tasks/templates` · Spec refs §3.4 · API: GET /api/tasks/templates
Build: list of templates (name, category · times/day), loading/error/retry/empty, pull-to-refresh, tap → create form with template, pop back to tasks list after creation.
Done when: template prefill reproduces §3.3 prefill rules.
Depends: TASK-04.
Fix: use repository/state holder rather than ad-hoc local fetch.
