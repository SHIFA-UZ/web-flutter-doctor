# Notifications

Area 07 §2 (flows 07/08–07/10, board `DaInbox`). Tickets NOTIF-01…04 (M8); push (PUSH-01…03) is at the very end
(D-15).

## NOTIF-01 · Notification data and type catalogue

Built 2026-11-05. Spec 07 §2.2–2.3, ticket NOTIF-01, flow 07/08 (type catalogue), board `DaInbox` (`typeOf`).

- **Endpoints** (dev `/v3/api-docs` and a real GET as the seed doctor, 46 rows of 35 types): `GET
  /notifications/page?page&size` (Spring `Page`, newest first; Flutter read the whole history, flow §12), `PUT
  /notifications/{id}/read`, `PUT /notifications/read-all`. The unread count stays in `BadgesRepository` (the shell's).
  Marking read on dev can't be undone, so writes were tested against the demo inbox only.
- **Model:** `AppNotification` (type upper-cased, ids, names trimmed, times parsed); a row without an id or a time is
  skipped, not the page. `title`/`message` are the backend's English text: the backend keeps in-app rows English and
  leaves the wording to the app (`DoctorPushI18n`).
- **Catalogue** (`data/notifications/NotificationRules.kt`, pure): every backend `Notification.Type` → a kind, its
  chip (Appointments, Tasks, Messages; the AI scribe and summary and unknown kinds only under All, the briefing
  (`APPOINTMENT_BRIEFING_READY`) under Appointments with the other `APPOINTMENT_*` types) and the tap target in Flutter's order
  (document access → the patient with the document, the viewer open when approved; chat → the Chat tab, since the row
  has no conversation id (BE-06); partnership; appointment → Calendar; a completed task; the patient; else nothing).
  Unknown `APPOINTMENT_*` / `TASK_*` types sort under their chip. Clinic staff have no Calendar, Patients or Tasks,
  so only chat leads anywhere for them. The patient-side types the dev seed also gives the doctor (installments,
  consultation payments, prophylaxis) show as "Notification" with the backend's text.
- **Look** (`ui/notifications/NotificationLook.kt`): the board's icon and colour role per kind (calendar-plus booked,
  clock reminder, wallet payment, lock document access, handshake partnership, tasks, sparkles AI, bell otherwise),
  the flow's labels plus four new ones: "Appointment reminder" (the board; Flutter called a reminder "Appointment
  booked"), "AI visit summary ready", "AI briefing ready", "Task update". Bodies are templates from the structured
  fields (patient name, appointment time, document, requesting doctor; "Patient" without a name); without the fields a
  template needs, the backend's text (spec §8 #13: never parse the English).
- **Demo:** `DemoNotifications`, nine rows (booked, chat, cancelled, task completed, access request, AI scribe,
  payment, partnership invite, general), five unread, paged, read one or all; it replaces the fixed count of 12.
- **Tests:** `NotificationCatalogueTest` (every backend type → label, icon, colour, chip, target; every kind
  covered), `NotificationRulesTest` (kinds, the chips, the targets incl. staff),
  `NotificationLookTest` (bodies, labels), `NotificationsRepositoryTest` (a real dev row, bad rows skipped, paths),
  `DemoBackendTest` (page, read one, read all, the count).

## NOTIF-02 · Inbox screen

Built 2026-11-05. Spec 07 §2.1, ticket NOTIF-02, flow 07/08, board `DaInbox` ("notifications").

- **Where:** the Notifications tab root: doctors reach it from More and the Home bell, clinic staff have it as their
  third tab.
- **Header:** "Notifications (n)" from the shell's unread count (99+, none at 0), "Mark all as read" (off only
  when no loaded row is unread and the count is known to be 0), Refresh, and Settings (Flutter's gear went to the tab it was on, flow §12; here it opens Settings).
  The board's double-tick icon isn't in the icon set: a single tick.
- **Chips:** All, Appointments, Tasks, Messages, client-side; clinic staff get no Tasks chip (flow §6 NEW).
- **List:** practice-day sections ("Today", "Yesterday", "5 Nov"); the board's card: unread tinted with its colour at
  6 % with a blue dot and a bold body, the icon, label and time ("Just now", "5 min ago", "14:32", "Yesterday 09:10",
  "1 Nov, 11:00", practice zone), the body in 3 lines. Read out as "Unread, Appointment booked, 4 min ago, …".
  Actions as on the board: a booking → View Appointment and Reschedule (its panel, which reschedules); a cancellation
  → Open Calendar (read, then the Calendar tab at its root, on the appointment's practice day or today:
  Flutter always went to today, flow §12). Staff get none (no Calendar).
- **Loading:** skeletons first; the next page as the end comes into view (rows already shown aren't repeated), with
  "Couldn't load. Retry" if it fails; pull or Refresh reads page 0 again, also on every return to the inbox, and asks the shell for the count again (a
  page asked for before a refresh is dropped). A failed
  read keeps the old list under a banner with the request id. Empty: "No notifications" or, under a chip, "No
  notifications in this category".
- **A tap** reads the row at once (back to unread with "Couldn't mark as read" if the server refuses) and opens its
  target over the inbox (Back returns to it): an appointment's Calendar panel, found by reading its practice day
  (the start from the row, else `GET /appointments/{id}`); not there or 404 → "This appointment no longer exists or
  was moved." While it looks, the row shows a spinner and ignores taps (flow §8). Document access → the patient's
  record, approved → the document viewer; chat → the Chat tab (no conversation id, BE-06). Tasks and partnerships
  have no screens yet (M9, CARE): the tap only reads them. A read refreshes the shell's count (`NotificationReads`).
- **Not built:** "View Result" (task details, M9), the "notifications
  off" banner (07/09, with push at the end), selecting the document on the patient's Documents tab, focusing the
  calendar root under an opened appointment panel (the panel opens over the inbox instead).
- **Tests:** `NotificationsViewModelTest` (paging without repeats, chips, a booking's panel from its calendar day with
  one read per tap, a gone appointment, document / chat / staff, a refused read rolls back, mark all, a failed refresh
  keeps the list). Screenshots `Notifications_inbox`, `_loading`, `_empty`, `_filterEmpty`, `_staff`.
- **Emulator:** against dev (reads only: a read can't be undone there) the seed doctor's 46 rows of 35 types showed in
  the app's wording across three pages, the oldest matching the backend's last row. In demo: View Appointment opened
  the panel and the count went 5 → 4; the cancellation said the appointment no longer exists; the access request
  opened the patient; the chat row opened the Chat tab; Mark all as read cleared every row and the count. Demo mode is
  off again.

## NOTIF-03 · Unread badge refresh

Built 2026-11-05 (with NOTIF-02). Spec 07 §2.4, ticket NOTIF-03.

- One count, the shell's (`GET /notifications/unread/count` through `BadgesRepository`), shown on the Home bell, the
  More tile, clinic staff's tab and the inbox title. Never polled. Read again on: opening the inbox and every refresh
  of it (pull, Refresh, coming back), a read or "Mark all as read" (`NotificationReads`, emitted by the repository and
  the inbox), app resume (`ShellViewModel.onResume`), and Home's refresh. A newer read cancels an older one, so a late
  answer can't put an old count back. No "token hack" (Flutter retried every 5 s without a token, fix #19).
- **Left for PUSH-02:** a foreground push reading it again (push is at the very end, D-15).
- **Tests:** `ShellViewModelTest` (a read signal reads the count again; the resume read in the polling test).

## NOTIF-04 · Approve or decline a document-access request

Built 2026-11-07 (the user's call, reversing that part of D-07). Board `DaInbox` "notifications", the `docAccess` row.

- **Who sees it:** the owner of a private document, when another doctor asks for it (`DOCUMENT_ACCESS_REQUEST`). A
  doctor-owned request had no way to be answered before: Flutter never called the routes, so the asking doctor's
  lock stayed for good. Clinic staff get no buttons (they own no documents).
- **Card:** Approve (secondary, compact) and Decline (tertiary, compact) under the text while the request waits
  (`documentAccessRequestStatus` `pending`, or none on older rows). Once answered the buttons go and the label reads
  "Document access request · Approved" / "· Declined", from the server's status after a refresh too.
- **Request:** `POST /document-access-requests/{id}/approve|reject`; one at a time (the buttons are off meanwhile).
  Success: the row is read (`PUT /notifications/{id}/read`), the banner says "Access approved" / "Access declined"
  (both shown as success: the shell's notice has no info style; the board's decline is info). A refusal (e.g.
  approving a request declined elsewhere) leaves the row waiting under "Couldn't update the request. Try again." with
  its request id. A tap on the row still opens the patient's record, as before.
- **Demo:** the inbox's request (row 505, request 3) waits; deciding it works locally and approving after a decline is
  refused, as on the backend.
- **Tests:** `NotificationsViewModelTest` (approve: one request for two taps, the row read and marked, the success
  notice; a refused decline leaves it waiting), `NotificationRulesTest` (when the buttons show), `DemoBackendTest`.
  Screenshot `Notifications_inbox` re-recorded (the request now has its buttons).

## PUSH-01…03 · Push

Built 2026-11-09 (D-15: at the very end). Spec 07 §2.3 (tap targets), §2.5; backend `docs/api/push-notifications.md`
(BE-05 device tokens, BE-06 language and `data`).

- **Bridge** (`data/push/Push.kt`): the platforms report the FCM token, pushes that arrive while the app is open and
  tapped pushes. Android: `ShifaMessagingService` (`onNewToken`, `onMessageReceived`) and `MainActivity` (singleTop:
  the tap's extras in `onCreate` and `onNewIntent`); iOS: `AppDelegate` as `UNUserNotificationCenterDelegate` and
  `MessagingDelegate`, set before launch ends so a tap that started the app arrives. The web has no push.
- **PUSH-01, the device on the account** (`PushRegistration`): `POST /doctors/me/fcm-tokens {token, platform}` once per
  session and token (again when FCM refreshes it; the backend moves a token another account had). Sign-out removes it
  with `DELETE /doctors/me/fcm-tokens/{token}`, carrying the ending session's token, in the background; a sign-in right
  after waits for that removal. A session that ends on a 401 can't remove it: the next sign-in on the device moves it.
  Clinic staff never register (the route is doctors-only). Permission is asked once the doctor is in: Android 13+
  `POST_NOTIFICATIONS` (the system stops asking after two refusals), iOS authorization, then APNs registration. Android
  channel `shifa_doctor_channel` (Flutter's id, so a doctor's settings carry over), also named in the manifest for
  pushes the system shows; icon the white mark, colour teal.
- **PUSH-02, while the app is open:** Android shows the backend's title and body (BE-06: already in the doctor's
  language) once per row, the data riding along for the tap; iOS shows a banner. The bell's count reads again,
  appointment pushes refresh the schedule, chat pushes the chat count, an open inbox its first page.
- **PUSH-03, a tap** (open, background or cold start): it waits in `Push.tapped` until the shell takes it (signed out:
  until the sign-in). `AppNotification.fromPush` reads the `data` as the inbox row (the same keys; `id`, `chatId`,
  `partnershipId` as fallbacks; `entityId` only on chat pushes), the row is marked read on the side, and
  `NotificationOpener` finds the target as the inbox does; it opens over Home at its root (`openOverHome`, shared with
  Home's attention centre). Without a row id: the inbox. A gone appointment: Home under "This appointment no longer
  exists". A later tap wins over a slower earlier one. Only an appointment waits for the profile (its practice day).
- **Debug menu → Push** (signed in): this device's token (its end only), "Send a test push" (`POST
  /doctors/me/test-notification`: every device of the doctor) and the newest inbox row of each type as a tapped push.
- **Checked** on the Pixel 9 Pro emulator against dev (seed doctor): the permission prompt, a token for the debug
  build (the refusal of 2026-11-04 is gone), `POST /doctors/me/fcm-tokens` 204, taps into the inbox, the Chat tab and,
  from a cold start, Task Details; Back returns to Home. Not yet: a delivered push, because every FCM send failed on
  the backend ("Not in GZIP format", firebase-admin 9.4.0 with Boot 4's httpclient5 5.6; fixed by firebase-admin
  9.11.0 on the backend branch `fix/firebase-admin-gzip`). iOS needs the APNs key in Firebase (Cloud Messaging →
  Apple app configuration) and a real device for a token.
- **Tests:** `PushRegistrationTest` (once per session and token, staff and no token, a failure retried, sign-out with
  the ended session's token, a sign-in after a sign-out in order), `AppNotificationFromPushTest`, `ShellViewModelTest`
  (a chat tap opens its conversation over Home and reads the row, no row → inbox, foreground pushes refresh counts and
  the schedule).

