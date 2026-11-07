- **[P2] Schedule precedence disagrees with the backend.** [TaskModels.kt:154](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/TaskModels.kt:154) selects legacy times before a single `startTime`. With `timesPerDay=1`, `startTime="08:00"` and `eveningTime="20:00"`, it reports 20:00 although the backend generates 08:00. It also omits repeated slots when `intervalHours` is null, whereas backend `generateCheckIns` defaults to one hour. Match the backend’s precedence and interval default.

- **[P2] Missing demo tasks fall through to the real backend.** [DemoTasks.kt:57](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/debug/DemoTasks.kt:57) returns null for unknown task IDs and their check-ins. `DemoBackend` interprets null as unhandled and forwards GETs with the real authorization header. Opening an unknown task through a notification can therefore display real patient data inside demo mode. Return a local typed 404 for recognized task routes with missing IDs.

- **[P2] Unreadable task responses bypass typed errors and failure logging.** [TasksRepository.kt:47](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/tasks/TasksRepository.kt:47), also lines 66 and 71, converts a successfully decoded but unusable DTO into `IllegalStateException`. For example, HTTP 200 with `{}` bypasses `apiCall`’s `ApiException.ServerError` mapping and debug failure logging. Map these conversion failures to the shared typed error path and log only sanitized metadata.

---
**Answers (round 1, all fixed; second round run):**
1. *The schedule order differed from the backend:* fixed. `times` follows `generateCheckIns`: custom times; else the
   start (alone once a day, else every `intervalHours ?: 1`, capped by the count and midnight); else legacy. Tested.
2. *Unknown demo ids reached the real backend:* fixed at the root, for every in-memory demo. After chat, tasks and
   the inbox have answered, any other `messages`, `notifications` or `tasks` path is a local 404 (`DEMO_NOT_FOUND`);
   chat's unknown conversation had the same hole. Tested (task, check-ins, thread; nothing forwarded).
3. *An unusable 200 escaped as an untyped exception:* fixed. `Result.usable(what, map)` (next to `rows()`) turns it
   into `ApiException.ServerError`, logged as `FAILED unusable <what>` without content; the task calls and chat's
   start use it. Tested.
