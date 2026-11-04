# Cancel appointment dialog

| | |
|---|---|
| Area | 04 Calendar & schedule |
| Who sees it | Doctor (own and colleagues' appointments); Clinic staff via the clinic doctor schedule screen (server authority UNVERIFIED). No tier gate. |
| Reached from | [05-appointment-preview-sheet](05-appointment-preview-sheet.md) → "Cancel" button |
| Screen type | Confirmation dialog |
| Current Flutter source | `lib/features/calendar/presentation/calendar_screen.dart` (Cancel button in `CalendarSlotDetailsPanel.build`), `lib/state/calendar/calendar_controller.dart` (`cancelAppointment`) |
| Spec | `docs/kmp-spec/03-calendar-schedule.md` §1.7 "Cancel", §6 (DELETE `/api/appointments/{id}`); ticket CAL-6 |

## 1. Purpose
Free the slot and tell the patient the visit is off. A destructive action that must be deliberate but quick. A few times a week.

## 2. Entry points
- Preview sheet → **Cancel** (red). Enabled only when the appointment has not ended, is not COMPLETED and not CANCELLED.
- No swipe, long-press or list-level cancel. Blocks that cancel overlapping appointments are a different flow ([09](09-block-time.md)).

## 3. Layout, top to bottom (phone portrait)
Today: Material alert dialog.
1. Title "Cancel Appointment".
2. Body "Are you sure you want to cancel this appointment?"
3. Actions right-aligned: text **No** · text **Yes, Cancel**.

Target (area convention: destructive confirmation, destructive button red on the right):
1. Title "Cancel appointment?"
2. Summary line: patient name · date · time (practice TZ) so the doctor confirms the right one.
3. Optional **reason** field (chips: "Doctor unavailable", "Patient request", "Rescheduled elsewhere", "Other" + free text) — **does not exist today; the DELETE sends no body.** Needs a backend change (UNVERIFIED) — design it as optional.
4. Info line: "The patient will be notified." (patient-app push on `APPOINTMENT_CANCELLED` — happens server-side; UNVERIFIED for walk-ins without an app) — there is no "notify patient" toggle today.
5. Buttons: secondary **Keep appointment** (left) · destructive **Cancel appointment** (right, red).

Button states (target): "Cancel appointment" shows a spinner and both buttons lock while the request runs; the dialog closes only on success; on failure it stays open with the error line under the buttons so the doctor can retry without re-opening the preview.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Appointment id | entry `appointmentId` (numeric) | required; button hidden/disabled without it |
| Eligibility | not past (UTC end < now in practice TZ), status ∉ {COMPLETED, CANCELLED} | evaluated when the preview renders |
| Request | DELETE `/api/appointments/{id}` | 200/204 = success; no body, no reason |
| After success | day force-refreshed; Home today list, next appointment, analytics, clinic finance/treatment-plan providers invalidated | the row either disappears or shows status CANCELLED, depending on the backend (UNVERIFIED which) |

Time rules: "past" is computed on the UTC end against now in the practice timezone; an in-person visit that ended an hour ago today can still be *started* (same-day grace) but can no longer be *cancelled* — inconsistent (open question). No cancellation deadline / minimum notice exists.

## 5. Primary flow (happy path)
1. Preview → Cancel → dialog.
2. Tap "Yes, Cancel" → dialog closes → Cancel button shows a spinner (all preview buttons disabled).
3. DELETE succeeds → day refreshed → preview closes → banner "Appointment cancelled successfully" → the slot is free again in the agenda (or the row shows Cancelled).

## 6. Screen states
- **First load:** instant.
- **Empty:** n/a.
- **Error on load:** n/a.
- **Offline / request failure:** banner "Failed to cancel: <exception>" → target: "Couldn't cancel. Check your connection and try again." Preview stays open, buttons re-enabled.
- **Refreshing:** while the DELETE is in flight the preview is locked (`_saving`).
- **Partial data:** no id → Cancel disabled.
- **Permission needed:** n/a.
- **Locked by tier:** n/a.
- **Role difference:** staff/colleague context sends the same DELETE without a doctor id; the backend must authorise via clinic membership (UNVERIFIED). If forbidden (403) the staff user sees the error banner (403 does not log out staff).

## 7. Interactions and secondary flows
- **No / back / scrim** → dialog closes, nothing happens.
- **Yes, Cancel** → request (single-flight).
- Target **Undo**: not possible today (no re-book endpoint); the banner has no action.
- **Tap the summary line** (target) → nothing; it is informational. Do not make the patient name a link here (keeps the dialog a single decision).
- **Reason chips** (target) → single select; "Other" reveals the free-text field; the field is optional and never blocks the action.
- **Swipe down / drag** → not applicable (dialog, not sheet); the scrim tap dismisses = Keep appointment.

## 8. Edge cases
- **Already cancelled by the patient (404/409):** error banner with the server text; day refreshed on the next action only → target: refresh the day on any 4xx and close the preview with "This appointment was already cancelled".
- **Appointment in progress (IN_PROGRESS):** Cancel is **enabled** today (only past/completed/cancelled disable it) → target: disable with hint "End the visit instead".
- **Video appointment paid (PAID):** cancellation may imply a refund — no UI today; target: info line "Payment was received; refunds are handled in Finance" (UNVERIFIED policy).
- **Double tap on Yes:** dialog closes on first tap; `_saving` guards the second.
- **Network drop after the server processed it:** banner says failed; next refresh shows it cancelled. Error path should force-refresh the day.
- **401:** logout.
- **Deep link while the dialog is open:** calendar changes underneath; the dialog still targets the original id — correct; target: close dialogs on deep link.
- **Rotation:** dialog persists.
- **200 % font:** buttons stack (Material does this automatically for overflow); title wraps.
- **Language switch:** n/a (dialog is modal; language toggle is not reachable).
- **Screen reader:** title, body, then "No, button", "Yes, Cancel, button". Target: the destructive button announced last.
- **Cancelling the last appointment of a blocked period:** independent.
- **Cancel when the preview was opened for a colleague:** the banner should name the doctor ("Dr X's appointment cancelled") — not today.
- **Appointment starting in the next 5 minutes:** no special warning today; target: the summary line shows "Starts in 4 min" so a late cancel is deliberate.
- **Multi-slot appointment (booked across 3 consecutive slots):** one DELETE frees all three slots; the agenda shows three free-slot rows again (backend regenerates slots — UNVERIFIED that the split follows the schedule grain).
- **Appointment created by a block-with-cancel elsewhere (already cancelled server-side) but the day is stale:** Cancel appears enabled; DELETE returns 4xx; see the 404/409 case.
- **Slow network (> 3 s):** the preview sits locked with a spinner on the Cancel button and no other feedback; target: the dialog stays open with its own progress state.
- **Staff calendars visible, cancelling a colleague's appointment:** the own calendar's today is refreshed by the invalidation hub, the colleague's section is refreshed by the panel's "saved" callback — both happen; fine.
- **Screen reader focus after success:** focus should land on the calendar header (or the banner), not on the now-missing row.

## 9. Validation and messages
| Field/action | Rule | When checked | Message (English) | Where shown |
|---|---|---|---|---|
| Eligibility | not past ∧ status ∉ {COMPLETED, CANCELLED} | on preview build | button disabled; hint "Past appointments cannot be changed or cancelled." | under primary button |
| Confirm | user confirms | dialog | "Are you sure you want to cancel this appointment?" | dialog |
| Request ok | 200/204 | on response | "Appointment cancelled successfully" | top banner (today SnackBar) |
| Request fail | — | on response | "Failed to cancel: <reason>" → target "Couldn't cancel. Try again." | top banner |
| (target) Reason | optional, ≤ 200 chars | on submit | — | inline |
| In progress (target) | status ≠ IN_PROGRESS | on preview build | "End the visit instead" | hint under disabled button |
| Already cancelled (target) | 404/409 on DELETE | on response | "This appointment was already cancelled" | banner; preview closes |

## 10. Navigation out
- **Success →** preview closes → calendar day refreshed; Home and analytics invalidated.
- **No / failure →** back to the preview.

## 11. Copy
- `cancelAppointment` "Cancel Appointment"; `cancelConfirm` "Are you sure you want to cancel this appointment?"; `no` "No"; `yesCancel` "Yes, Cancel"; `appointmentCancelled` "Appointment cancelled successfully"; `failedToCancel` "Failed to cancel"; `pastAppointmentNoChange` "Past appointments cannot be changed or cancelled.".
- **NEW:** "Cancel appointment?" · "Keep appointment" · "The patient will be notified." · "Reason (optional)" · "Doctor unavailable" · "Patient request" · "Other" · "This appointment was already cancelled" · "End the visit instead".

## 12. Known problems in the current app (do not carry over)
- "No / Yes, Cancel" text buttons — the destructive action is not visually distinct and the double meaning of "Cancel" (dismiss vs cancel the appointment) is confusing.
- No reason, no patient-notification information, no summary of which appointment is being cancelled.
- In-progress visits can be cancelled from the calendar.
- Error banner carries raw exception text; failure path does not refresh.
- Cancel disabled for a visit that ended an hour ago while Start is still allowed for it.

## 13. Open questions for design
- Reason capture: worth asking the backend for, or keep it one tap?
- Undo window (5 s banner action) vs confirmation dialog — the backend has no "un-cancel", so confirmation stays unless we add it.
- Should cancelled appointments stay visible in the agenda (muted) for the day?
- Should the dialog differ for video appointments that are PAID (mention refund handling) — depends on the finance policy (UNVERIFIED)?
- One dialog for doctor and staff, or should staff see "Cancel on behalf of Dr {name}" in the title?
