# Tracking: Home, inbox, Reports, Tasks (group B2)

Events in `data/tracking/events/HomeEvents.kt`, sent from the ViewModels below through `tracker: Tracker = Tracking`
(last constructor parameter). Tests with `RecordingTracker` in each ViewModel's existing test file.

## Events

| Event | Properties | Fired by, when |
|---|---|---|
| `home_video_slot_found` | none | `HomeViewModel.findVideoSlot`, a free slot was found |
| `home_video_slot_search_failed` | `reason`: `no_slots` / `toFailureReason()` | same, none in 30 days or a failed read |
| `home_search_result_opened` | `results` (`countBucket` of matches), `searched` (Boolean) | `PatientSearchViewModel.opened`, called by `PatientSearchScreen` when a result is tapped |
| `home_attention_task_opened` | none | `HomeAttentionViewModel.openTask` |
| `notification_opened` | `type` (backend type, as spelled), `target` (`appointment` / `patient` / `document` / `chat` / `partnership` / `task` / `inbox` / `none`), `source` (`inbox` / `home_attention` / `home_activity`) | `NotificationsViewModel.open`, `HomeAttentionViewModel.open`, after `NotificationOpener.go` answered |
| `notifications_filter_selected` | `filter` (`all` / `appointments` / `tasks` / `messages`) | `NotificationsViewModel.select`, only on a change |
| `notifications_marked_all_read` | `unread` (`countBucket` of unread rows shown) | `NotificationsViewModel.markAll`, server agreed |
| `notifications_mark_all_failed` | `reason` | same, refused |
| `access_request_decided` | `approved` | `NotificationsViewModel.decide` (NOTIF-04), server agreed |
| `access_request_decide_failed` | `approved`, `reason` | same, refused |
| `report_range_changed` | `source` (`home` / `reports`), `days`, `ends_today` | `AnalyticsViewModel.setRange`, chosen by the doctor (a new day moving an untouched range is not sent) |
| `report_exported` | `source`, `days` | `AnalyticsViewModel.exported(true)`, the CSV reached the share sheet |
| `report_export_failed` | `source`, `reason`: `incomplete` / `share_unavailable` | `AnalyticsViewModel.export` (a block unreadable) / `exported(false)` |
| `task_created` | `category`, `schedule_mode` (`even` / `custom`), `slots` (check-ins a day), `from_template` | `CreateTaskViewModel.create`, server agreed |
| `task_create_failed` | `reason`: `invalid` (+ `problems`: `TaskFormProblem` track names) / `toFailureReason()` | same, form checks or server refused |
| `task_cancelled` | none | `TaskDetailViewModel.cancel`, server agreed |
| `task_cancel_failed` | `reason` | same, refused |

`source` on the Reports events comes from the ViewModel's existing `withSms` flag (only the Reports page reads SMS).

No event in this group uses `amountBucket`: Reports sends no money at all (no revenue or SMS cost).

## Screens

None: every screen of this group (`home`, `notifications`, `reports`, `tasks`, `task`, `task_create`,
`task_templates`, `patient_search`) is the shell's (A6). No `TrackScreenView` was added.

## NotificationOpener

Owned here. It sends nothing itself: the shell's `openPush` already sends `push_opened` after calling it, so tracking in
`go` would count every push twice. Each host tracks its own outcome instead (inbox and Home above). The target words
are `NotificationGo.trackTarget` in `NotificationOpener.kt`; a null result (gone appointment, failed read, nothing to
open) is the caller's word (`none` here, `home` in the shell).

## Left out, and why

- Home viewed with "has next patient" / visits today: the shell sends `home`; a schedule event per read would fire
  every minute on focus.
- Start / Continue visit and Home's quick actions: they push routes the shell already counts (`visit`, `booking`, …).
  An "opened from Home" property needs the shell (below).
- Home and inbox load failures: `api_error` covers failed backend reads.
- Tasks list search and filters (search text never; filters are low value), check-in viewed, templates chosen
  (`from_template` on `task_created` covers it), create abandoned.
- Reports trend-day taps, PREMIUM teaser / "see plans": no ViewModel action today.
- Notification `type` is the backend's spelling, the same as `push_opened` (guide §3, "values").

## Needs from the coordinator

- `ShellViewModel` keeps a private copy of `NotificationGo?.trackTarget`; it could use the public
  `NotificationGo.trackTarget` from `ui/notifications/NotificationOpener.kt` (`go?.trackTarget ?: "home"`).
- Optional: a `source` ("opened from Home") on the shell's screen views for `visit` / `booking` / `patient`, if the
  owner wants Home's quick actions and hero measured.

## Note

`NotificationsViewModelTest` "a booking is read and opens its panel …, once per tap" failed once under load (the
appointment lookup answered between the two taps, so the second tap marked the row read again); it passed on three
reruns. Pre-existing race in the test, not touched.
