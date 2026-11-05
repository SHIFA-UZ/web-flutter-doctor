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
