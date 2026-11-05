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
- **Tests:** `TasksViewModelTest` (counts, combined filters, the card toggle, clear; a failed refresh keeps the list).
  Screenshots `Tasks_list`, `_loading`, `_empty`, `_filterEmpty`, `_failed`.
- **Emulator (dev, reads only):** the seed doctor's 3 tasks (cancelled, active, completed) with their progress, "1 day"
  for the one-day task, counts 1/1/0; the Active card filtered to one and back.

## TASK-03 · Task details

Built 2026-11-05. Spec 07 §3.2, ticket TASK-03, flow 07/12, board `DaInbox` ("taskDetails" and its sheets).

- **Info card:** the name and status pill; the patient (a link to their record, flow §7 NEW), category, input type
  ("Numeric", "Text", "Yes/No"), input label and description when set, start and end dates ("4 Nov 2026"), the
  duration; the progress block (bar, "3/6", Completed / Pending / Missed) when the server sent progress, plus "The
  patient hasn't submitted anything yet" when nothing came in and a slot has passed (flow §8 NEW).
- **Check-ins:** newest first (flow §5 target), "5 Nov · 13:00" (the patient's wall time, as scheduled), the value as
  people write it ("120", "36.6", "Yes") with notes, the status pill; a pending one is missed once its time passed in
  the **patient's** zone (else the practice zone; Flutter used the phone's). "No check-ins scheduled yet" when none; a
  failed read of them shows its own message and Retry under a task that did load.
- **A check-in's sheet:** Scheduled, Status, then "Submitted at … (patient time)" (minutes only), the value, notes; or
  "No submission received" / "Awaiting submission"; Close.
- **Cancel Task** (active tasks only): the destructive confirm ("No" / "Yes, Cancel"), one request however often it's
  tapped; done → back to the list (which reads again) with the success banner; refused → "Failed to cancel" with the
  request id.
- **Loading:** skeleton first; 404 → "Task not found" with Back; anything else → "Couldn't load task" with the
  request id and Retry (Flutter said "not found" for every failure). Read on open, on every return and on pull
  (Flutter polled every 15 s, flow §12).
- **From the inbox:** a task notification and its "View Result" (board) open this screen in the Tasks tab over the
  list (Back and a cancel land on the list); a doctor without the plan gets the shell's "not on your plan" note. A
  403 here reads the profile again so the shell closes Tasks.
- **Zone:** the patient's; without it the practice zone, said under "Check-ins", and "Submitted at" without "(patient
  time)"; with neither, the server's statuses and times in UTC (never the phone's zone). A task deleted meanwhile
  turns into "Task not found" on the next read. The open sheet follows its check-in through a refresh; a failed refresh of the check-ins says so over the
  old list; the sheet scrolls.
- **Tests:** `TaskDetailViewModelTest` (newest first, 404 vs failure, check-ins failing alone, cancel once with the
  banner, a refused cancel, number labels), `NotificationsViewModelTest` (a completed task opens it). Screenshots
  `Task_detail`, `_checkIn`, `_cancel`, `_notFound`.
- **Emulator:** on dev (reads only) the seed task with 6 check-ins: values "120", newest first, no Cancel on a cancelled
  task, the sheet's submitted time in patient time, the patient link opening the record. In demo: Cancel → confirm →
  the list with "Task cancelled successfully" and the task cancelled; the inbox's View Result opened it. Demo mode is
  off again.
- **Backend note:** a one-day task on dev has check-ins on two days (the backend's end date is start + duration,
  inclusive); the app shows what the backend sends.

## TASK-04 · Create task

Built 2026-11-05. Spec 07 §3.3, ticket TASK-04, flow 07/13, board `DaInbox` ("taskCreate").

- **Form** (board's cards): Patient * from the picker over the doctor's own patients (the Patients tab's directory;
  search by name or id, the current one ticked; the sheet is now shared with booking, which keeps its directory of
  everyone: `ui/patients/PatientPickerSheet.kt`), Task Name *, Description, Category *
  and Input Type *, Input Label; Schedule; dates; Notes Required and Notes Label. Create sits in a sticky bar (flow §12:
  Flutter's scrolled away).
- **Schedule:** even spacing (times per day 1–15, start time, the interval when twice or more) with the day's slots
  live, orange with "n slot(s) won't fit before midnight"; or custom times (switching seeds one slot from the start;
  each a time to the minute, delete never below one, "Add time slot" an hour after the last, off at 15, a repeat
  says "That time is already in the list"). Times are picked to the minute in a sheet with the 24-hour
  time entry (a template's 07:10 stays 07:10).
- **Dates:** start date (practice today … a year ahead) and the end date in the app's inline month (default start + 30
  days, never before the start; a later start clears an end before it, flow §8; required, or the backend would pick
  31 days itself), or the duration (whole days ≥ 1, kept as typed: "1.5" is an error, not 15).
- **Create:** inline "Please select a patient" (also a banner), "Task name is required", "Add at least one time slot"
  (banner), "End date must be after the start date", "Enter a number of days (at least 1)"; one request; done → back
  with "Task created successfully" (the list reads again); refused → "Failed to create task" with the request id (a 403
  reads the profile again). Back with changes asks "Discard this task?".
- **Payload** (`data/tasks/TaskForm.kt`, pure): upper-case codes, "HH:mm", "yyyy-MM-dd"; custom → its slots and the
  first as the start; even → the interval only with two or more, and the **count that fits** (Flutter sent 15 when 8
  fit, flow §8); the end date or the duration, never both; empty texts left out.
- **Template prefill:** every field but the patient and dates; times per day 1–15; the interval snapped up to the next
  choice with "Interval adjusted to n h" (flow §6 NEW); custom times → custom mode. Opened from a template by TASK-05.
- **Not built:** a patient preset from the patient's record (flow §2's overflow entry; the ⋮ sheet isn't built), the
  patient-app hint (no backend field), "Add a patient first" in an empty picker.
- **Tests:** `TaskFormTest` (slots and clipping, every payload rule, problems, template prefill and snapping, slot
  list), `CreateTaskViewModelTest` (checks before sending, one request, success banner, refused create, repeat slot,
  back asks, a later start clears the end, the picker). Screenshots `TaskCreate_errors`, `_even`, `_custom`.
- **Emulator (demo):** picked Malika, "Weight", custom times 08:00 and 09:00, end 20 Dec → the list with "Task created
  successfully" and "Weight · 2× per day · 5 Nov – 20 Dec 2026"; Back after typing asked to discard. Booking's
  screenshots are unchanged by the picker move. Demo mode is off again.

## TASK-05 · Templates

Built 2026-11-05. Spec 07 §3.4, ticket TASK-05, flow 07/14, board `DaInbox` ("taskTemplates").

- **What it is:** `GET /tasks/templates` returns the settings of the doctor's earlier tasks (dev: the seed doctor's 3
  tasks); there is no create, edit or delete for templates anywhere (flow §1), so this is a picker.
- **Cards:** the category tile, the name (2 lines; repeats are allowed, flow §8), "Vital · 3× per day · Numeric", the
  day's times ("08:00 · 13:00 · 20:00", "+3 more" past four; legacy times included), a chevron; read out as one line.
- **States:** skeletons; "No templates yet" / "Create a task first, then it will appear here." with Create Task (the
  blank form); "Couldn't load templates" with the request id and Retry when nothing was ever read, a banner over the
  list otherwise; a 403 reads the profile again (as the other task screens). Read on open, return and pull.
- **A tap** opens the create form pre-filled ("From template: …"), one form per tap (flow §8). Back from the form returns
  here; a successful create closes the form **and** this list onto Tasks with the banner (flow §5,
  `ShellRoute.TaskCreate.fromTemplates`).
- **Labelled switches:** the form's switches had no accessible name and only the knob toggled. `ShifaSwitchRow`
  (`ui/widgets/ShifaSwitch.kt`) toggles from the whole row and is read out with its label; Settings' and Services'
  private copies now use it (their screenshots unchanged). The patient record's SMS switch still uses the bare switch
  (left as it is, outside this ticket).
- **Tests:** `TemplatesViewModelTest` (read, repeats kept, the times, a later failure keeps the list, a first failure
  isn't empty, a 403). Screenshots `TaskTemplates_list`, `_empty`.
- **Emulator:** dev's 3 templates listed with their times (reads only). In demo: a template → the form pre-filled →
  patient, 14 days → Create → the Tasks list with "Task created successfully" and the new task (the templates list
  closed). Demo mode is off again.
- **Tablets:** every task screen is centred at the board's width (the list at the page width, the others at the form
  width); a retry after a first failure shows the skeleton again.
