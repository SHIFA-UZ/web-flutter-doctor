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
- **Next:** the inbox screen (NOTIF-02) and the badge refresh (NOTIF-03).
