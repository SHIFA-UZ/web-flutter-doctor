# Tracking: patients and care (B4)

Folders `ui/patients`, `ui/care`; events in `data/tracking/events/PatientsEvents.kt`. Every ViewModel that tracks takes
`tracker: Tracker = Tracking` as its last constructor parameter. No event carries a name, phone, search text, message,
clinical value or any id; searches send only the result count as a `countBucket` range.

## Events

| Event | Properties | Fires from |
|---|---|---|
| `patients_searched` | `result_count` (`countBucket`) | `PatientsViewModel.type`, when the 300 ms debounce settles on non-blank text and the directory has loaded (else every count is `0`) |
| `patients_filtered` | `tab` (`all` / `favorites` / `follow_ups`), `status` (`active` / `at_risk` / `follow_up` / `unknown`, left out for all), `is_descending` | `PatientsViewModel.filter`, only when the segment, status or order really changed |
| `patient_picked` | `searched`, `result_count` (`countBucket` of the list shown, only when searched) | `PatientPickerSheet` (the pick callback, not the tray), so booking and the new-task screen are covered once |
| `patient_created` | `has_phone` | `CreatePatientViewModel.create`, success without `editing` |
| `patient_create_failed` | `reason`: `invalid_fields` (nothing sent) / `conflict` (a known phone) / `validation` / a `toFailureReason()` word | same, failure |
| `patient_details_edited` | – | `CreatePatientViewModel.create` with `editing` ("Edit details") |
| `patient_details_edit_failed` | `reason`: `invalid_fields` / `conflict` / `not_found` / a `toFailureReason()` word | same, failure |
| `patient_field_saved` | `field`: `phones` / `allergies` / `chronic_disease` / `gender` / `blood_group` / `sms_reminders` / `sms_reminder_hours`; `is_enabled` (only `sms_reminders`) | `PatientDetailViewModel.patch` success (every Medical-info and SMS save goes through it) |
| `patient_field_save_failed` | `field` as above; `reason`: `invalid_phone` (nothing sent) / `conflict` / a `toFailureReason()` word | `patch` failure; `savePhones` local validation |
| `patient_favorite_toggled` | `is_favorite` | `PatientDetailViewModel.toggleFavorite` |
| `patient_test_sms_sent` / `patient_test_sms_failed` | `reason` (`forbidden`: no SMS in the plan; `server`) | `PatientDetailViewModel.testSms` |
| `patient_account_created` / `patient_account_create_failed` | `reason` (`conflict`: has one already) | `PatientDetailViewModel.createAccount` |
| `patient_briefing_generated` | `is_regenerate` | `PatientBriefingViewModel.generate`, a non-blank summary |
| `patient_briefing_failed` | `is_regenerate`, `reason`: `empty` (400 or blank: nothing to brief on) / `rate_limited` (daily limit) / `timeout` / … | same, otherwise |
| `patient_briefing_copied` | – | `PatientBriefingViewModel.copied` |
| `prophylaxis_saved` | `is_enabled`, `interval_months` (1–60, a reminder setting) | `ProphylaxisViewModel.save` |
| `prophylaxis_save_failed` | `reason` | same |
| `care_partner_searched` | `result_count` (`countBucket`), `has_specialty` | `FindPartnerViewModel.search`, only when the doctor typed a name or specialty (not the list on open) |
| `care_invite_sent` | `has_message` | `FindPartnerViewModel.send` |
| `care_invite_failed` | `reason` (`conflict`: already a partner) | same |
| `care_partnerships_filtered` | `filter`: `all` / `needs_answer` / `active` / `sent` / `finished` | `PartnershipsViewModel.filter`, on a change |
| `care_partnership_acted` / `care_partnership_act_failed` | `action`: `accept` / `decline` / `cancel` / `complete`; `reason` | `PartnershipViewModel.act` |
| `care_progress_posted` / `care_progress_post_failed` | `reason` | `PartnershipViewModel.post` |

No event uses `amountBucket` (nothing in these folders is money).

## Screens

`PatientDetailScreen` sends `patient_overview`, `patient_medical`, `patient_appointments`, `patient_documents`,
`patient_forms`, `patient_activity` through `TrackScreenView(state.tab.trackScreen)`: once on open, again on each tab
change, and again when the record comes back to the front (the same rule as the shell's `patient`, which it adds to).
`patients`, `patient`, `find_partner`, `partnerships`, `partnership` are the shell's and are not sent here.

## Left out, and why

- Directory and record reads, refresh failures, visits / timeline / prophylaxis reads: `api_error` from `apiCall` covers
  failed reads; a successful read says nothing new.
- `PatientsViewModel.toggleFavorite`: no UI calls it (the directory has no star), only tests.
- Opening the booking sheet, the edit sheet, the ⋮ sheet, the AI summary sheet: taps without an outcome; the booking
  outcome is B3's, the opened routes are the shell's.
- Discarding typed phones / allergies, closing the create sheet with input: the discard is decided in the screen's
  sheet callbacks, not in a ViewModel; not tracked.
- Chronic-disease warning shown: a fact about the patient's health, never sent.
- `ClinicalTimelineViewModel`: read-only.
- Patient opened "from" (directory / search / clinic / notification / link): only the shell knows the source.
- AI summary PRO gate shown: drawn by the screen from the tier, no ViewModel action; the gate's funnel is the profile's `subscription_checkout_opened`.

## Needs from the coordinator

- An "opened from" property on the shell's `patient` screen view (directory / search / clinic / notification / link),
  if wanted.
- Hosts of `PatientPickerSheet` (B3 booking, B2 tasks) add nothing: the pick is tracked in the sheet.
