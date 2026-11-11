# Tracking B3: calendar, booking, blocked time, schedule and locations

Folders `ui/calendar`, `ui/schedule`; events in `data/tracking/events/CalendarEvents.kt`. No design board and no flow
file: tracking has no UI. Every ViewModel that tracks takes `tracker: Tracker = Tracking` as its last constructor
parameter (`BookingViewModel`, `AppointmentPanelViewModel`, `BlockViewModel`, `BlockPanelViewModel`,
`ScheduleViewModel`, `LocationsViewModel`); no call site changed.

Common properties: `is_on_behalf` is true when the appointment, slot or block is a colleague's (`CalendarScope` /
`doctorId` set: the clinic doctor schedule, CAL-10). `reason` on a `*_failed` event is `toFailureReason()` unless
listed otherwise. No ids, no patient, no booking reason or note, no address or location label, no money.

## Events

| Event | Properties | Fires in |
|---|---|---|
| `appointment_booked` | `entry`, `is_video`, `is_on_behalf`, `minutes` (booked length) | `BookingViewModel.book`, on success |
| `booking_failed` | `reason` (`slot_taken` for 400 / 409, `past_day`, or `toFailureReason()`), `entry`, `is_video`, `is_on_behalf` | `BookingViewModel.book`: on failure, or when Book is pressed after the day has ended (nothing is sent to the backend) |
| `appointment_rescheduled` | `is_video`, `is_on_behalf` | `AppointmentPanelViewModel.move`, success |
| `appointment_reschedule_failed` | `reason`, `is_on_behalf` | `move`, failure |
| `appointment_duration_changed` | `minutes` (new length), `is_on_behalf` | `applyDuration`, success |
| `appointment_duration_change_failed` | `reason`, `is_on_behalf` | `applyDuration`, failure |
| `appointment_cancelled` | `cancel_reason` (`doctor_unavailable` / `patient_request` / `rescheduled` / `other`, left out when none was picked; never the "Other" text), `is_video`, `is_on_behalf` | `cancel`, success |
| `appointment_cancel_failed` | `reason`, `is_on_behalf` | `cancel`, failure |
| `appointment_no_show_marked` | `is_patient_notified`, `is_video` | `noShow`, success |
| `appointment_no_show_failed` | `reason` (`not_allowed` for `NO_SHOW_NOT_ALLOWED`) | `noShow`, failure |
| `payment_reminder_sent` | none | `remindPayment`, success |
| `payment_reminder_failed` | `reason` | `remindPayment`, failure |
| `time_blocked` | `mode` (`day` / `hours` / `days`), `cancels_overlapping`, `cancelled_bucket` (`countBucket` of the appointments the backend cancelled) | `BlockViewModel.block`, success |
| `time_block_failed` | `reason`, `mode` | `block`, failure |
| `time_block_removed` | `is_on_behalf` | `BlockPanelViewModel.remove`, success |
| `time_block_remove_failed` | `reason`, `is_on_behalf` | `remove`, failure |
| `schedule_saved` | `has_hours_changed`, `has_period` (a bookable period was added, the first setup's default included), `is_first_setup`, `working_days` (0–7) | `ScheduleViewModel.save`, success |
| `schedule_save_failed` | `reason`, `stage` (`hours` / `period`: the hours went through, the period did not) | `save`, failure |
| `schedule_changes_discarded` | none | `ScheduleViewModel.discard` (leaving or switching location with unsaved edits) |
| `schedule_date_hours_added` | `days` (dates the span covers) | `addDateHours`, success |
| `schedule_date_hours_add_failed` | `reason` | `addDateHours`, failure |
| `schedule_date_hours_deleted` | none | `deleteDateHours`, success |
| `schedule_date_hours_delete_failed` | `reason` | `deleteDateHours`, failure |
| `practice_location_saved` | `is_new`, `is_primary`, `has_pin` | `LocationsViewModel.save`, success |
| `practice_location_save_failed` | `reason`, `is_new` | `save`, failure |
| `practice_location_deleted` | none | `delete`, success |
| `practice_location_delete_failed` | `reason` (`in_use` for 409: the only one, or it has hours or appointments) | `delete`, failure |

**Booking funnel and the alert.** `booking_failed` is the fixed name shared with the patient app (`reason` like the
patient app's, `slot_taken` included) and is watched by the "Booking failing" alert. It must be the only failure event
for any booking path in the doctor app: other groups reuse `CalendarEvents.BookingFailed` / `AppointmentBooked` (a
second class with the same name fails the catalogue test). Funnel: the shell's `booking` screen view →
`appointment_booked` / `booking_failed`.

`entry`: `patient` when the form opened with a patient (a patient's record, or "book next" from the visit),
`home_video` for Home's Video Call chip, `calendar` otherwise (the calendar tab, Home's agenda free slot, a colleague's
schedule); `intelligence` is reserved for the Shifa AI card (B7). The form only knows its own arguments, so Home's agenda
and the calendar are both `calendar`, and record and visit are both `patient` (see Needs).

`amountBucket`: not used by any event here.

## Screens

None. Every id in these folders is the shell's (`calendar`, `booking`, `appointment`, `block`, `schedule`,
`locations`, `doctor_schedule`); the block sheet and the slot picker are sheets over a shell screen.

## Left out, and why

- Day / week switch, agenda filter, staff calendar toggled, month paging, appointment opened: navigation and view
  changes, not outcomes (the shell's screen views cover opening the panel).
- Booking abandoned: derivable as `booking` screen views without `appointment_booked` / `booking_failed`.
- No patient picked on Book: a form hint, not a failed booking (it would add noise to the alert).
- Copy-day and Mon–Fri helpers, ranges added/removed: edits inside the form; the result is `schedule_saved`
  (`working_days`, `has_hours_changed`).
- Map pin moved / kept / discarded: the result is `has_pin` on `practice_location_saved`.
- Reschedule-sheet and slot-picker day reads, location and schedule load failures: covered by `api_error`.
- No-show from the visit (B6's `VisitViewModel.noShow`) is not here: B6 sends its own or reuses
  `CalendarEvents.AppointmentNoShowMarked`.

## Needs from the coordinator

- **B7 (Intelligence `ThreadViewModel.book`)**: send `CalendarEvents.AppointmentBooked("intelligence", …)` and
  `CalendarEvents.BookingFailed(reason, "intelligence", …)` instead of defining its own booking events, so
  `booking_failed` stays one class and the alert sees AI bookings. Likewise for its reschedule / cancel card actions if
  B7 tracks them (`appointment_rescheduled`, `appointment_cancelled` are taken).
- **B6 (`VisitViewModel.noShow`)**: `appointment_no_show_marked` / `appointment_no_show_failed` are taken; reuse them
  or name the visit's own `visit_no_show_*`.
- **Exact booking entry (optional)**: a `source` field on `ShellRoute.Booking` (`calendar` / `home_agenda` /
  `home_video` / `patient_record` / `visit` / `clinic_schedule`) passed to `BookingScreen` → `BookingViewModel` would
  replace the derived `entry`; until then the values above.

## Tests

`BookingViewModelTest` (taken range → `slot_taken`, then booked; a colleague's past day → `past_day`, `is_on_behalf`),
`AppointmentPanelViewModelTest` (duration and cancel failed then done, payment reminder; no-show refused as
`not_allowed`, then marked; reschedule), `BlockViewModelTest` (block failed / done, a colleague's block remove failed /
done), `ScheduleViewModelTest` (period failed at `stage = period`, then saved; extra hours added / deleted; discard),
`LocationsViewModelTest` (save failed / done with pin; delete `in_use` / done). All with `RecordingTracker`.
