# Remote-care tasks

Area 07 §3 (flows 07/11–07/16, board `DaInbox` "tasks"). Tickets TASK-01…05 (M9). PREMIUM only.

## TASK-01 · Task data, rules and gate

Built 2026-11-05. Spec 07 §3.5 and §5, ticket TASK-01.

- **Endpoints** (dev `/v3/api-docs` and real GETs as the seed doctor: 3 tasks, 6 check-ins, 3 templates): `GET
  /tasks[?patientId&status]`, `GET /tasks/{id}`, `GET /tasks/{id}/check-ins`, `GET /tasks/templates`, `POST /tasks`,
  `PATCH /tasks/{id}/cancel` (no body). `TaskDto` also carries `patientTimeZone`, `doctorName` and `appointmentId`
  (17 M9 note), all kept. Creating or cancelling can't be undone on dev: tested against the demo only.
- **Models** (`data/model/TaskModels.kt`): category, status, input type and check-in status read case-insensitively
  with the spec's fallbacks (other, active, text, pending); a row without an id, patient or start date is skipped, not
  the list; times through the schedule rules' parser (Flutter's `int.parse` threw). The legacy morning / afternoon /
  evening times are read (dev's seed tasks use them) and only feed the day's times.
- **The day's times** (`TaskSchedule.times`), in the backend's order (`generateCheckIns`): custom times (sorted); else
  the start, alone once a day or every `intervalHours` (1 when unset) up to the count and before midnight; else the
  legacy times.
- **Effective status** (`data/tasks/TaskRules.kt`): a pending check-in whose time has passed in the **patient's**
  zone shows as missed (Flutter used the phone's clock and zone, 07 §3.2); without a time, once the day is over; an
  unknown zone falls back to the practice zone.
- **Gate:** the existing `FeatureGate` / `DoctorFeature.RemoteCareTasks` (PREMIUM); the shell keeps the Tasks tab
  closed for other plans (SHELL tickets). A 403 still arrives typed (`ApiException.Forbidden`).
- **Errors:** a 200 the app can't use (no id, no start date) is a typed `ServerError`, logged without content
  (`usable()` next to `rows()`; chat's start uses it too).
- **Demo:** `DemoTasks`: a blood-pressure diary three times a day (done, missed and coming check-ins), an evening pill
  (legacy evening time), a finished walk; two templates; create and cancel in memory. It replaces the empty list. A chat, inbox or task path the demo doesn't know
  (an id it never made) answers a local 404 instead of reaching the real backend with real patient data.
- **Tests:** `TaskModelsTest` (a dev row, fallbacks, the day's times, missed in the patient's zone vs the phone's),
  `TasksRepositoryTest` (filters, skipped rows, the create payload, cancel without a body, a typed 403),
  `DemoBackendTest` (list, filters, check-ins, templates, create, cancel).

## TASK-02 · Tasks list

Built 2026-11-05. Spec 07 §3.1, ticket TASK-02, flow 07/11, board `DaInbox` ("tasks").

- **Where:** the Tasks tab root, PREMIUM doctors only (More → Tasks; the shell keeps it closed otherwise and for
  clinic staff). Read on open and on every return (from details, create, templates), pull and Refresh; Flutter's
  prefetch at shell start for every plan is gone.
- **Header:** the title once (Flutter showed it in the app bar and the page), the subtitle, Refresh, "Use Templates",
  and the board's docked "Create Task" button above the tab bar (`ShifaDimens.FabDockedClear`).
- **Summary:** Active, Completed, Overdue (expired, as the backend marks them; flow §4), counted over every task;
  tapping one filters by it, again turns it off (board). Numbers line up when a label wraps.
- **Filters:** search (task or patient name), status and category dropdowns, all localised (Flutter's phone
  dropdown showed raw codes, flow §12), combined; kept while you go to a task and back.
- **Cards** (board): the category tile (heart, activity, pill, clipboard), the name in 2 lines, the patient
  ("Patient" without a name), the status pill, "Task progress 3/6" with its bar ("0/0" without progress), the
  category, "3× per day", "4 Nov – 17 Nov 2026" (locale dates, not ISO) and the days. Read out as one line.
- **States:** skeletons first (Flutter flashed the empty card); "No tasks found" + Create Task when there are none
  (the filters hidden: nothing to filter); "No tasks match your filters" + "Clear filters" when a filter hides
  everything (flow §8); a failed read keeps the list under a closable banner with the request id and Retry (Flutter
  swallowed it); a first read that fails shows "Couldn't load tasks" with the id and Retry in the list's place, never
  the empty card. One read at a time, the newest wins. A 403 (the plan taken away on the server) reads the profile
  again, so the shell closes the tab with its "not on your plan" note.
- **Kept as is:** the patient shows the brand picture, not initials (project rule for avatars without a photo);
  Create and Use Templates stay on after a failed read (the app has no connectivity state, and those screens say
  so themselves when offline).
- **Until TASK-03…05:** a card, Create Task and Use Templates open placeholder pages.
- **Tests:** `TasksViewModelTest` (counts, combined filters, the card toggle, clear; a failed refresh keeps the list).
  Screenshots `Tasks_list`, `_loading`, `_empty`, `_filterEmpty`, `_failed`.
- **Emulator (dev, reads only):** the seed doctor's 3 tasks (cancelled, active, completed) with their progress, "1 day"
  for the one-day task, counts 1/1/0; the Active card filtered to one and back.
