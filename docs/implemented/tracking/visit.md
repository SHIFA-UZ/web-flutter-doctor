# B6: visit, video, AI scribe, dictation (2026-11-10)

Folders `ui/visit`, `ui/speech`; events in `data/tracking/events/VisitEvents.kt`. No design board or flow changes: no UI
was added. Every class that tracks takes `tracker: Tracker = Tracking` as its last constructor parameter
(`VisitViewModel`, `BriefingViewModel`, `VisitFactsViewModel`, and the helpers `VisitNotes`, `VideoVisit`,
`Dictation`); `VisitViewModel` hands its tracker to `VisitNotes` and `VideoVisit`.

**Alert names (fixed, shared with the patient app):** `video_join_failed(reason)`, `visit_end_failed(reason, step)`,
`scribe_failed(reason, stage)`. `step` on `visit_end_failed` is an extra property; the alert counts the event.

## Events

| Event | Properties | Fires in |
|---|---|---|
| `visit_opened` | `kind` (`in_person` / `video`), `is_dental`, `is_completed` | `VisitViewModel.load`, first successful read only |
| `visit_mode_changed` | `mode` (`general` / `dental` / `form0252`) | `VisitViewModel.mode` (also from `applyIcd`) |
| `visit_helper_opened` | `helper` (`ai` / `form`), `is_empty` | `VisitViewModel.helper` |
| `visit_icd_applied` | none (never the code) | `VisitViewModel.applyIcd` |
| `visit_photo_added` | `kind` (`before` / `after`) | `VisitViewModel.addPhoto`, only when a photo was added |
| `visit_signature_requested` / `visit_signature_request_failed` | `reason` (`notes_not_saved` / failure word) | `VisitViewModel.requestSignature` |
| `visit_patient_signed` | none | `VisitViewModel.watchSignature`, once, when an awaited signature turns signed (the reads stop then) |
| `visit_no_show_marked` / `visit_no_show_failed` | `is_patient_notified` / `reason` (`not_allowed` for `NO_SHOW_NOT_ALLOWED`) | `VisitViewModel.noShow` |
| `visit_payment_invalid` | none | `VisitViewModel.end`: the payment typed at the visit is not above zero, End doesn't start (input, kept off the alert) |
| `visit_end_started` | `kind`, `has_plan_step`, `has_payment_step`, `has_scribe_step` | `VisitViewModel.end` |
| **`visit_end_failed`** | `reason` (`notes_not_saved`, `dental_not_loaded`, `dental_not_saved`, `plan_not_saved`, `payment_not_recorded`, or the complete call's failure word), `step` (`notes` / `dental` / `plan` / `payment` / `complete`, passed by each failure) | `VisitViewModel.end` (`fail`) |
| `visit_plan_lines_completed` | `lines_bucket` (`countBucket`) | `end`, after the ticks are applied |
| `visit_payment_recorded` | `method` (`cash` / `card` / `transfer`), `amount_bucket` (**`amountBucket`**) | `end`, after the payment is recorded |
| `visit_completed` | `kind`, `mode`, `has_scribe`, `has_photos` | `end`, after the server completed the visit |
| `visit_summary_saved` | `is_retry`, `has_dental`, `has_photos`, `has_plan` | `summary` (the PDF filed under the patient) |
| `visit_summary_skipped` | `reason` (`nothing_to_save` / `ai_later`) | `summary` |
| `visit_summary_failed` | `reason` (`notes_not_read` / `pdf_failed` / `no_patient` / the upload's failure word), `is_retry` | `summary` (`summaryFailed`) |
| `visit_notes_save_failed` | `reason` | `VisitNotes.save`, once until a save succeeds again |
| `visit_ai_draft_confirmed` / `visit_ai_draft_discarded` / `visit_ai_draft_failed` | `action` (`confirm` / `discard`), `reason` | `VisitNotes.confirm` / `discard` |
| `scribe_recording_started` / `scribe_recording_cancelled` | none | `Dictation.start` / `cancel` for the scribe's field |
| `scribe_recording_finished` | `duration_bucket` (`durationBucket` of `VoiceRecorder.lastDurationSeconds`) | `Dictation.finishWith`, when the recorder gave audio, before the upload |
| `scribe_uploaded` | `is_retry` | `VisitViewModel.uploadScribe` / `retryScribe` |
| `scribe_blocked` | `reason` (`forbidden` = no PRO / `rate_limited` = the daily limit) | `VisitViewModel.sendScribe`: the plan's rules, not a broken scribe (kept off the alert) |
| **`scribe_failed`** | `reason` (`recording_failed` / `timeout` / failure word; at `upload` never `forbidden` / `rate_limited`, those are `scribe_blocked`), `stage` (`record` / `upload` / `notes`) | `Dictation.start` / `finishWith` (record), `VisitViewModel.sendScribe` (upload), `waitForScribe` (notes: 120 s passed or the status read failed) |
| `scribe_notes_ready` / `scribe_wait_skipped` | none | `VisitViewModel.waitForScribe` (End's scribe step) |
| `video_permissions_result` | `is_camera_granted`, `is_mic_granted` | `VideoVisit.permissions`, only when an answer changed |
| `video_joined` | `is_rejoin` | `VideoVisit` call watcher, Joining → InCall |
| **`video_join_failed`** | `reason` (`too_early` / `too_late` / `payment_required` / `daily_failed` / the token's failure word, e.g. `forbidden` for the plan gate) | `VideoVisit.join` (token) and the call watcher (Daily refused) |
| `video_control_toggled` | `control` (`camera` / `mic` / `flip`), `is_on` (left out for `flip`) | `VideoVisit.toggleCamera` / `toggleMic` / `flip` |
| `video_call_ended` | `ended_by` (`doctor` / `visit_closed` / `remote` / `error`), `has_patient_joined`, `duration_bucket` (since the join; left out when unknown) | `VideoVisit.leave` / `release` (only from the call) and the watcher (dropped) |
| `dictation_started` / `dictation_cancelled` | `field_kind` (`soap` / `free_notes` / `dental_notes` / `form`) | `Dictation.start` / `cancel` |
| `dictation_transcribed` | `field_kind`, `has_speech` | `Dictation.finish` |
| `dictation_failed` | `field_kind`, `reason` (`recording_failed` / failure word: `forbidden` = no plan, `rate_limited`, `timeout`) | `Dictation.start` / `finish` |
| `dictation_mic_denied` | `field_kind` (also `scribe`) | `Dictation.denied(field)` |
| `dictation_reported` / `dictation_report_failed` | `reason` | `Dictation.report` (a 404, no longer collected, sends nothing) |
| `visit_briefing_opened` / `visit_briefing_ready` / `visit_briefing_failed` / `visit_briefing_copied` | `reason` (`generation_failed` / failure word) | `BriefingViewModel`; ready once per sheet, failed once until it recovers (the reads repeat); both reset when the sheet opens again (`start`) |
| `visit_facts_documented` | `facts_bucket`, `rejected_bucket`, `tasks_bucket` (`countBucket`) | `VisitFactsViewModel.document` |
| `visit_facts_document_failed` | `reason` (`notes_not_saved` / failure word) | `VisitFactsViewModel.document` |

`field_kind` comes from the screens' fixed field keys (`soap-*`, `free`, `dental-notes`, `scribe`); a 025-2 form
field's key is its label, so it is only ever `form`. `Dictation.denied` now takes the field (both callers are in this
group's folders). The scribe's field constant is `SCRIBE_FIELD` in `Dictation.kt` (VisitScreen used its own copy).

**Uses `amountBucket`:** `visit_payment_recorded.amount_bucket` (the plan payment typed at the visit).

## Screens

None. `visit` is the shell's; the video stages, End sheet, briefing and photo sheets are not screens (their events
cover them).

## Left out, and why

- **Durations** of the time to join and the time waited for the scribe: not asked for yet (the call's length and the
  scribe recording's length are sent through `durationBucket`).
- Dental record autosave and the plan editor's edits: End's `dental_not_saved` covers the failure that matters; the
  record's contents are clinical values.
- Pre-join presence polling, the visit's signature polling, notes autosave successes: repeated reads, no outcome.
- Plan line ticks and visit-fact Keep / Reject taps: local until End / "Document everything", whose events carry the
  counts.
- Notes text, SOAP fields, drafts, transcripts, ICD codes, dental values, photo names, the field labels of forms:
  never sent.

## Tests

`VisitViewModelTest` (5 new: open → End → summary; failed complete, failed summary, retry; plan lines and payment;
scribe upload, retry, skipped wait; signature and no-show), `VideoVisitTest`, `DictationTest`, `VisitNotesTest`,
`BriefingViewModelTest`, `VisitFactsViewModelTest` (1 new each), all with `RecordingTracker`.

## Needs from the coordinator

None.

## Fixes after review (2026-11-11)

- The "Visit can't be completed" alert counts breakage only: an amount not above zero is `visit_payment_invalid`
  (was `visit_end_failed(invalid_amount)`), a scribe upload refused for the plan (`forbidden`) or the daily limit
  (`rate_limited`) is `scribe_blocked` (was `scribe_failed(stage = upload)`).
- `visit_end_failed.step` is passed by each failure; the dental failures are `dental` (they used to say `notes`).
- The briefing's ready / failing flags reset when the sheet opens again (the ViewModel is kept per appointment).
- New: `visit_patient_signed`, `scribe_recording_finished(duration_bucket)`.
- Tests: `VisitViewModelTest` (4 new), `BriefingViewModelTest` (1), `DictationTest` (1).
