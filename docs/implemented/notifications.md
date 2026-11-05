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
- **Not built:** NOTIF-04 (approve / decline access inline, D-07), "View Result" (task details, M9), the "notifications
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
