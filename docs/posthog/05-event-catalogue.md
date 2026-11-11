# Tracking: event catalogue

Generated 2026-11-11 from the code on `feature/posthog-tracking` (`data/tracking/events/*.kt`, `TrackScreen.kt`;
"fired from" is every file in `commonMain` that builds the event, with the class around the call when it differs
from the file). The code is the source of truth: when it changes, regenerate this file rather than editing it by hand.
How events are added is in `03-developer-guide.md`; the plan and decisions in `00-plan.md`.

- **262 events** in 12 group files, **71 screen ids** (sent as `$screen` views, `$screen_name` = the id).
- Every event also carries the super properties `app = doctor` and `app_language` (`Tracking.start`) and the SDK's
  own platform properties; they are not repeated here. A property whose value is null is left out of the event. An enum type (`TrackingRole`,
  `VisitKind`, …) is sent as its `trackName()`, a lower-case word.
- **Alert-watched names** (the five PostHog alert slots, `00-plan.md` §4; the same names as the patient app):
  `api_error`, `video_join_failed`, `booking_failed`, `visit_end_failed`, `scribe_failed`. Do not rename them.
- `reason` values are fixed words: the feature's own words listed per event or a `Throwable.toFailureReason()`
  word. `*_bucket` / `result_count` values are `countBucket` or `amountBucket` ranges, never exact values.

## Events that carry money ranges

These properties are built with `amountBucket` (`data/tracking/TrackProperties.kt`): UZS ranges `0`, `<100k`,
`100k-500k`, `500k-1m`, `1m-5m`, `5m-20m`, `>20m`, and `foreign` for any other currency. **The owner still has to
confirm these UZS ranges before release** (runbook §4); changing them changes only `amountBucket`.

| Event | Property | Fired from |
|---|---|---|
| `clinic_payment_recorded` | `amount_bucket` | `ClinicPlans` (PlanDetailViewModel), `ClinicFinance` (ClinicFinanceViewModel) |
| `clinic_installment_status_changed` | `amount_bucket` | `ClinicFinance` (ClinicFinanceViewModel) |
| `clinic_financial_record_created` | `amount_bucket` | `ClinicFinance` (ClinicFinanceViewModel) |
| `clinic_plan_created` | `total_bucket` | `PlanWizard` (PlanWizardViewModel) |
| `profile_service_saved` | `price_bucket` | `ServicesScreen` (ServicesViewModel) |
| `subscription_checkout_opened` | `price_bucket` | `BillingScreen` (BillingViewModel) |
| `visit_payment_recorded` | `amount_bucket` | `VisitViewModel` |

## ApiEvents (`ApiEvents.kt`)

Stage A (A1b): reliability, sent by `apiCall` (`data/api/ApiHelper.kt`) for calls to our own backend's API only.

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `api_error` | `endpoint`: String<br>`method`: String<br>`status`: Int<br>`error`: String<br>`code`: String? | `ApiHelper` | A backend call that failed with a response: any non-2xx (a 401 that ends the session too, as in the patient app) or a 2xx body that could not be read. [error]: `toFailureReason()`; [code]: the backend's error code (`APPOINTMENT_LOCKED_24H`), only when it has that shape. Not sent without a response (no host to tell ours from a file host, and offline polling would flood it), nor for a cancelled call. |
| `api_slow` | `endpoint`: String<br>`method`: String<br>`ms`: Long | `ApiHelper` | A backend call that succeeded but took longer than `SLOW_CALL` (5 s), body read included. |

## AuthEvents (`AuthEvents.kt`)

Group B1: splash, sign-in, e-mail and SMS codes, password reset, registration, sign-out, app lock (`ui/splash`, `login`, `emailcode`, `register`, `auth`, `applock`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `boot_gate_shown` | `gate`: String<br>`is_retry`: Boolean | `SplashViewModel` | The launch stopped at a gate. [gate]: `update` / `maintenance`. [isRetry]: after "Try again" or the store. |
| `boot_gate_failed` | `reason`: String | `SplashViewModel` | The gate couldn't be read; launch went on without it. [reason]: a `toFailureReason()` word. |
| `sign_in_succeeded` | `method`: String<br>`role`: TrackingRole | `ResetPasswordScreen` (ResetPasswordViewModel), `EmailCodeViewModels` (CodeViewModel), `LoginViewModel` | A session was opened. [method]: `password` / `email_code` / `sms_code` / `password_reset`; [role]: `doctor` / `staff`. (Registration has its own `registration_completed`.) |
| `sign_in_failed` | `method`: String<br>`reason`: String | `ResetPasswordScreen` (ResetPasswordViewModel), `EmailCodeViewModels` (CodeViewModel), `LoginViewModel` | [method] as in [SignInSucceeded]. [reason]: `invalid_input` (checked on the device) / `invalid_credentials` / `not_doctor_account` / `wrong_code` (a wrong or expired code) / `unusable_token` / a `toFailureReason()` word. |
| `auth_code_sent` | `mode`: String<br>`is_resend`: Boolean<br>`is_email_fallback`: Boolean | `EmailCodeViewModels` (CodeViewModel) | A sign-in or reset code went out. [mode]: `sign_in` / `reset` / `sms_sign_in` (as the `email_code` screen's mode). [isResend]: from the code screen. [isEmailFallback]: an SMS sign-in code went to the e-mail instead. |
| `auth_code_failed` | `mode`: String<br>`is_resend`: Boolean<br>`reason`: String | `EmailCodeViewModels` (EmailEntryViewModel), `EmailCodeViewModels` (CodeViewModel) | [mode], [isResend] as in [AuthCodeSent]. [reason]: `invalid_input` / `no_account` / `sms_unavailable` / a `toFailureReason()` word (`rate_limited`, `network`, …). |
| `invite_key_verified` | `kind`: String | `VerifyKeyScreen` (VerifyKeyViewModel) | The invitation key was accepted. [kind]: `doctor` / `receptionist`. |
| `invite_key_failed` | `reason`: String | `VerifyKeyScreen` (VerifyKeyViewModel) | [reason]: `invalid_input` (empty) / `invalid_key` / a `toFailureReason()` word. |
| `registration_form_submitted` | `is_existing_patient`: Boolean | `RegisterViewModel` | Step 1 (the doctor's form) passed its checks and moved on. [isExistingPatient]: the address or phone has a patient account, so the confirmation view opened instead of step 2. |
| `registration_form_failed` | `reason`: String | `RegisterViewModel` | Step 1 stopped. [reason]: `invalid_input` / `doctor_exists` / a `toFailureReason()` word. |
| `registration_code_sent` | `kind`: String<br>`is_resend`: Boolean | `RegisterViewModel` | The registration code went out. [kind]: `doctor` / `receptionist`. [isResend]: from the code sheet. |
| `registration_code_failed` | `kind`: String<br>`is_resend`: Boolean<br>`reason`: String | `RegisterViewModel` | [kind], [isResend] as in [RegistrationCodeSent]; [reason]: a `toFailureReason()` word. |
| `registration_completed` | `kind`: String<br>`is_existing_patient`: Boolean<br>`has_details`: Boolean<br>`is_details_saved`: Boolean? | `RegisterViewModel` | The account exists and is signed in. [kind]: `doctor` / `receptionist`. [isExistingPatient]: a patient account was confirmed (3b). [hasDetails]: step 2 had something filled in; [isDetailsSaved]: its PATCH went through (null without details). |
| `registration_failed` | `kind`: String<br>`reason`: String | `RegisterViewModel` | `register` refused or its session could not open. [kind] as above. [reason]: `invalid_input` / `wrong_code` / `doctor_exists` / `ownership_required` (the current password is asked) / `unusable_token` (the account exists but its token couldn't be used) / a `toFailureReason()` word. |
| `registration_abandoned` | `kind`: String<br>`step`: String | `RegisterViewModel` | Left before an account existed. [kind] as above; [step]: `form` / `existing_patient` / `details` / `staff`. |
| `sign_out_cancelled` | — | `SignOut` (SignOutViewModel) | The sign-out question was closed without signing out (a confirmed one is `session_ended(signed_out)`). |
| `app_unlocked` | `method`: String | `LockScreen` (LockViewModel) | [method]: `pin` / `biometric`. |
| `app_lock_pin_failed` | `source`: String<br>`attempts_left`: Int | `LockScreen` (LockViewModel), `AppLockSection` (AppLockSettingsViewModel) | A wrong PIN. [source]: `lock_screen` / `settings` (the PIN asked before turning the lock off or changing it). [attemptsLeft]: tries before the session ends. |
| `app_lock_locked_out` | `source`: String | `LockScreen` (LockViewModel), `AppLockSection` (AppLockSettingsViewModel) | The last wrong PIN ended the session (`session_ended(pin_lockout)` follows). [source] as above. |
| `app_lock_changed` | `action`: String | `AppLockSection` (AppLockSettingsViewModel) | [action]: `enable` / `disable` / `change_pin`. |
| `app_lock_change_failed` | `action`: String<br>`reason`: String | `AppLockSection` (AppLockSettingsViewModel) | [action] as in [AppLockChanged]; [reason]: `mismatch` (the repeat differed) / `store_failed`. |
| `app_lock_change_cancelled` | `action`: String | `AppLockSection` (AppLockSettingsViewModel) | The PIN sheet was closed before it finished. [action] as in [AppLockChanged]. |
| `app_lock_biometric_set` | `is_on`: Boolean | `AppLockSection` (AppLockSettingsViewModel) | Biometric unlock turned on or off in settings. |

## CalendarEvents (`CalendarEvents.kt`)

Group B3: calendar, booking, blocked time, schedule and locations (`ui/calendar`, `schedule`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `appointment_booked` | `entry`: String<br>`is_video`: Boolean<br>`is_on_behalf`: Boolean<br>`minutes`: Int | `BookingScreen` (BookingViewModel), `ThreadViewModel` | An appointment was booked. [entry]: `calendar` (calendar, Home's agenda, a colleague's schedule) / `patient` (a patient's record or the visit) / `home_video` (Home's Video Call chip) / `intelligence` (a Shifa AI card). [minutes]: the booked length. |
| `booking_failed` | `reason`: String<br>`entry`: String<br>`is_video`: Boolean<br>`is_on_behalf`: Boolean | `BookingScreen` (BookingViewModel), `ThreadViewModel` | FIXED NAME, shared with the patient app and watched by the "Booking failing" alert: every way an appointment is booked sends this one event when the booking did not happen. [reason]: `slot_taken` (400 / 409, as in the patient app) / `past_day` (the day ended before Book) / a `toFailureReason()` word. [entry]: as [AppointmentBooked]. |
| `appointment_rescheduled` | `is_video`: Boolean<br>`is_on_behalf`: Boolean<br>`entry`: String = "calendar" | `AppointmentPanel` (AppointmentPanelViewModel), `ThreadViewModel` | Moved to another free slot. [entry]: `calendar` (the appointment panel) / `intelligence` (a Shifa Intelligence card). |
| `appointment_reschedule_failed` | `reason`: String<br>`is_on_behalf`: Boolean<br>`entry`: String = "calendar" | `AppointmentPanel` (AppointmentPanelViewModel), `ThreadViewModel` | [reason]: a `toFailureReason()` word (`conflict` / `validation`: the slot was taken meanwhile). |
| `appointment_duration_changed` | `minutes`: Int<br>`is_on_behalf`: Boolean | `AppointmentPanel` (AppointmentPanelViewModel) | "Adjust duration" applied; [minutes]: the new length. |
| `appointment_duration_change_failed` | `reason`: String<br>`is_on_behalf`: Boolean | `AppointmentPanel` (AppointmentPanelViewModel) | [reason]: a `toFailureReason()` word. |
| `appointment_cancelled` | `cancel_reason`: CancelReason?<br>`is_video`: Boolean<br>`is_on_behalf`: Boolean | `AppointmentPanel` (AppointmentPanelViewModel) | [cancelReason]: the picked category (`doctor_unavailable` / `patient_request` / `rescheduled` / `other`), never the "Other" text; null when none was picked. |
| `appointment_cancel_failed` | `reason`: String<br>`is_on_behalf`: Boolean | `AppointmentPanel` (AppointmentPanelViewModel) | [reason]: a `toFailureReason()` word. |
| `appointment_no_show_marked` | `is_patient_notified`: Boolean<br>`is_video`: Boolean | `AppointmentPanel` (AppointmentPanelViewModel) | "Mark as no-show" from the appointment panel; [isPatientNotified]: the "tell the patient" switch. |
| `appointment_no_show_failed` | `reason`: String | `AppointmentPanel` (AppointmentPanelViewModel) | [reason]: `not_allowed` (`NO_SHOW_NOT_ALLOWED`: outside the backend's window) / a `toFailureReason()` word. |
| `payment_reminder_sent` | — | `AppointmentPanel` (AppointmentPanelViewModel) | The payment reminder for an unpaid video visit went out. |
| `payment_reminder_failed` | `reason`: String | `AppointmentPanel` (AppointmentPanelViewModel) | [reason]: a `toFailureReason()` word. |
| `time_blocked` | `mode`: String<br>`cancels_overlapping`: Boolean<br>`cancelled_bucket`: String | `BlockTime` (BlockViewModel) | Time blocked in the doctor's own calendar. [mode]: `day` / `hours` / `days`; [cancelsOverlapping]: the "Cancel overlapping appointments" switch; [cancelledBucket]: how many the backend cancelled, as a `countBucket`. |
| `time_block_failed` | `reason`: String<br>`mode`: String | `BlockTime` (BlockViewModel) | [reason]: a `toFailureReason()` word; [mode]: as [TimeBlocked]. |
| `time_block_removed` | `is_on_behalf`: Boolean | `BlockTime` (BlockPanelViewModel) |  |
| `time_block_remove_failed` | `reason`: String<br>`is_on_behalf`: Boolean | `BlockTime` (BlockPanelViewModel) | [reason]: a `toFailureReason()` word. |
| `schedule_saved` | `has_hours_changed`: Boolean<br>`has_period`: Boolean<br>`is_first_setup`: Boolean<br>`working_days`: Int | `ScheduleScreen` (ScheduleViewModel) | Working hours saved for one location. [hasHoursChanged]: the weekly template was replaced; [hasPeriod]: a bookable period was added (the first setup's default 60 days included); [isFirstSetup]: there was no period before; [workingDays]: weekdays with hours after the save (0–7). |
| `schedule_save_failed` | `reason`: String<br>`stage`: String | `ScheduleScreen` (ScheduleViewModel) | [reason]: a `toFailureReason()` word; [stage]: `hours` / `period` (the hours were saved, the period not). |
| `schedule_changes_discarded` | — | `ScheduleScreen` (ScheduleViewModel) | Unsaved schedule edits thrown away (leaving the screen or switching location). |
| `schedule_date_hours_added` | `days`: Int | `ScheduleScreen` (ScheduleViewModel) | Extra hours on dates added; [days]: how many dates the span covers. |
| `schedule_date_hours_add_failed` | `reason`: String | `ScheduleScreen` (ScheduleViewModel) | [reason]: a `toFailureReason()` word. |
| `schedule_date_hours_deleted` | — | `ScheduleScreen` (ScheduleViewModel) |  |
| `schedule_date_hours_delete_failed` | `reason`: String | `ScheduleScreen` (ScheduleViewModel) | [reason]: a `toFailureReason()` word. |
| `practice_location_saved` | `is_new`: Boolean<br>`is_primary`: Boolean<br>`has_pin`: Boolean | `LocationsScreen` (LocationsViewModel) | A practice location created ([isNew]) or edited; [hasPin]: a map pin is set. Never the label or address. |
| `practice_location_save_failed` | `reason`: String<br>`is_new`: Boolean | `LocationsScreen` (LocationsViewModel) | [reason]: a `toFailureReason()` word. |
| `practice_location_deleted` | — | `LocationsScreen` (LocationsViewModel) |  |
| `practice_location_delete_failed` | `reason`: String | `LocationsScreen` (LocationsViewModel) | [reason]: `in_use` (409: the only one, or it has hours or appointments) / a `toFailureReason()` word. |

## ChatEvents (`ChatEvents.kt`)

Group B7: chat and Shifa Intelligence (`ui/chat`, `intelligence`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `chat_message_sent` | `kind`: String<br>`is_retry`: Boolean | `ThreadViewModel` | A message reached the server. [kind]: `text` / `image` / `document` / `voice`; [isRetry]: after "Tap to retry". |
| `chat_message_failed` | `kind`: String<br>`reason`: String | `ThreadViewModel` | A message was not sent. [kind]: as [ChatMessageSent]; [reason]: `too_large` (over 20 MB) / `file_type` (not pdf, doc, docx or an image) / `too_short` (a voice note under a second) / `read_failed` (the picked file or the recording couldn't be read) / a `toFailureReason()` word (upload or send failed). |
| `chat_conversation_started` | `is_doctor`: Boolean | `ChatListViewModel` | A conversation started (or found) from the Chat tab's user search. [isDoctor]: with a colleague. |
| `chat_conversation_start_failed` | `reason`: String<br>`entry`: String | `ChatListViewModel`, `PatientChat` (PatientChatViewModel) | [reason]: a `toFailureReason()` word; [entry]: `search` (the Chat tab) / `patient` ("Send message" on a patient). |
| `chat_users_searched` | `result_count`: String | `ChatListViewModel` | The user search settled (300 ms after typing). [resultCount]: a `countBucket`; never the text. |
| `chat_list_sorted` | `sort`: ConversationSort | `ChatListViewModel` | The conversation list's order picked: `newest` / `oldest` / `unread_newest` / `unread_oldest`. |
| `intelligence_reply_received` | `input`: String<br>`card_type`: String?<br>`has_schedule_change`: Boolean<br>`seconds`: Int<br>`is_retry`: Boolean<br>`has_patient_context`: Boolean | `ThreadViewModel` | Shifa answered. [input]: `typed` / `starter` (a welcome card) / `voice` / `card_answer` (a cancellation card's yes or no); [cardType]: the card's type as the backend names it (`schedule`, `propose_appointment`, …), `other` for a type the app doesn't know, null for words only; [hasScheduleChange]: Shifa itself booked, moved or cancelled visits in this answer; [seconds]: from the question to the finished answer; [hasPatientContext]: the chat is about one patient. |
| `intelligence_reply_failed` | `reason`: String<br>`input`: String<br>`is_retry`: Boolean | `ThreadViewModel` | No answer. [reason]: `empty` (neither words nor a card) / `ai_unavailable` / a `toFailureReason()` word (`rate_limited` is the daily limit, `timeout` "took too long"); [input]: as [IntelligenceReplyReceived]. |
| `intelligence_reply_stopped` | `input`: String | `ThreadViewModel` | The answer being written was stopped (the mic, leaving the chat, another chat). [input]: as above. |
| `intelligence_facts_documented` | `fact_count`: String | `ThreadViewModel` | The draft-facts card's "Document everything" went through. [factCount]: a `countBucket` of the facts saved. |
| `intelligence_facts_document_failed` | `reason`: String | `ThreadViewModel` | [reason]: a `toFailureReason()` word. |
| `intelligence_proposal_dismissed` | — | `ThreadViewModel` | A booking or reschedule proposal's Cancel: nothing is booked. |
| `intelligence_cancel_answered` | `is_confirmed`: Boolean | `ThreadViewModel` | The cancellation card's "Yes, cancel" ([isConfirmed]) or "Keep them"; the backend cancels, not the app. |
| `intelligence_conversation_renamed` | — | `IntelligenceViewModel` |  |
| `intelligence_conversation_deleted` | — | `IntelligenceViewModel` |  |
| `intelligence_conversation_exported` | — | `IntelligenceViewModel` | The open chat went to the share sheet as a `.txt`. |
| `intelligence_conversation_export_failed` | `reason`: String | `IntelligenceViewModel` | [reason]: `empty` (nothing to export yet) / `share_failed` (the share sheet couldn't open). |
| `intelligence_voice_transcribed` | `mode`: String | `VoiceSession` | A take was transcribed and goes to Shifa as a question. [mode]: `hands_free` (the mic) / `dictation`. |
| `intelligence_voice_failed` | `reason`: String<br>`mode`: String? | `VoiceSession` | A take that asked nothing. [reason]: `no_speech` / `mic_unavailable` (the recorder didn't start) / `mic_denied` / `pro_required` (below PRO the buttons only say so) / `background` (the app left while recording) / a `toFailureReason()` word (the transcription failed: `forbidden`, `rate_limited`, `timeout`, …). [mode]: as [IntelligenceVoiceTranscribed], null when no take was running (`mic_denied`, `pro_required`). |
| `intelligence_voice_discarded` | — | `VoiceSession` | The dictation pill's ✕: the take was dropped. |
| `intelligence_voice_signed_off` | — | `VoiceSession` | A take that only ended the conversation ("that's it", "rahmat", …): nothing went to Shifa. |

## ClinicEvents (`ClinicEvents.kt`)

Group B8: the clinic workspace: members, invitations, patients, finance, plans, services (`ui/clinic`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `clinic_switched` | — | `ClinicWorkspace` (ClinicWorkspaceViewModel) | The switcher moved the workspace to another of the user's clinics. |
| `clinic_revenue_share_saved` | `target`: String<br>`is_cleared`: Boolean | `ClinicPeople` (ClinicPeopleViewModel) | [target]: `member` (one doctor's share) / `default` (the clinic default); [isCleared]: set back to none. |
| `clinic_revenue_share_failed` | `target`: String<br>`reason`: String | `ClinicPeople` (ClinicPeopleViewModel) | [target] as in [RevenueShareSaved]; [reason]: a `toFailureReason()` word. |
| `clinic_invitation_sent` | — | `ClinicPeople` (ClinicPeopleViewModel) | A receptionist invitation sent (never the address). |
| `clinic_invitation_failed` | `reason`: String | `ClinicPeople` (ClinicPeopleViewModel) | [reason]: `invalid_email` / `already_pending` / a `toFailureReason()` word. |
| `clinic_invitation_revoked` | — | `ClinicPeople` (ClinicPeopleViewModel) |  |
| `clinic_invitation_revoke_failed` | `reason`: String | `ClinicPeople` (ClinicPeopleViewModel) | [reason]: a `toFailureReason()` word. |
| `clinic_patients_searched` | `result_bucket`: String | `ClinicPatients` (ClinicPatientsViewModel) | A search's first page came back (never the text). [resultBucket]: `countBucket` of the total found. |
| `clinic_finance_kpi_selected` | `kpi`: String | `ClinicFinance` (ClinicFinanceViewModel) | [kpi]: `revenue` / `outstanding` / `overdue` / `rate`. |
| `clinic_finance_month_changed` | `is_all_time`: Boolean | `ClinicFinance` (ClinicFinanceViewModel) | [isAllTime]: "All time" chosen instead of a month. |
| `clinic_payment_recorded` | `source`: String<br>`method`: String<br>`amount_bucket`: String? | `ClinicPlans` (PlanDetailViewModel), `ClinicFinance` (ClinicFinanceViewModel) | A desk payment recorded. [source]: `ledger_full` (a ledger row's "Paid by …") / `ledger_custom` (the custom amount sheet) / `plan` (the plan's Record payment); [method]: `cash` / `card` / `transfer` / `other`; [amountBucket]: `amountBucket` of the amount. |
| `clinic_payment_failed` | `source`: String<br>`method`: String<br>`reason`: String | `ClinicPlans` (PlanDetailViewModel), `ClinicFinance` (ClinicFinanceViewModel) | [source], [method] as in [PaymentRecorded]; [reason]: a `toFailureReason()` word. |
| `clinic_installment_status_changed` | `status`: String<br>`method`: String?<br>`amount_bucket`: String? | `ClinicFinance` (ClinicFinanceViewModel) | An installment's status set by hand. [status]: `paid` / `pending` / `waived` / `cancelled`; [method] only for `paid` (as in [PaymentRecorded]); [amountBucket]: `amountBucket` of the installment. |
| `clinic_installment_status_failed` | `status`: String<br>`reason`: String | `ClinicFinance` (ClinicFinanceViewModel) | [status] as in [InstallmentStatusChanged]; [reason]: a `toFailureReason()` word. |
| `clinic_installment_reminder_sent` | — | `ClinicFinance` (ClinicFinanceViewModel) |  |
| `clinic_installment_reminder_failed` | `reason`: String | `ClinicFinance` (ClinicFinanceViewModel) | [reason]: a `toFailureReason()` word. |
| `clinic_financial_record_created` | `record_type`: String<br>`has_plan`: Boolean<br>`amount_bucket`: String? | `ClinicFinance` (ClinicFinanceViewModel) | A financial record created. [recordType]: `invoice` / `receipt` / `estimate` / `credit_note`; [hasPlan]: linked to a treatment plan (its totals come from the plan); [amountBucket]: `amountBucket` of the typed total, left out with a plan. |
| `clinic_financial_record_failed` | `record_type`: String<br>`reason`: String | `ClinicFinance` (ClinicFinanceViewModel) | [recordType] as in [FinancialRecordCreated]; [reason]: a `toFailureReason()` word. |
| `clinic_finance_csv_exported` | `view`: String | `ClinicFinanceViews` (FinanceActions) | The view's CSV handed to the share sheet. [view]: `dashboard` / `ledger` / `installments` / `earnings` / … |
| `clinic_finance_csv_export_failed` | `view`: String | `ClinicFinanceViews` (FinanceActions) | The share sheet could not take the CSV. [view] as in [FinanceCsvExported]. |
| `clinic_plan_status_changed` | `status`: String<br>`source`: String | `ClinicPlans` (ClinicPlansViewModel), `ClinicPlans` (PlanDetailViewModel) | [status]: the new one, `draft` / `active` / `in_progress` / `on_hold` / `completed` / `cancelled`; [source]: `list` (the clinic's plans) / `detail` (the plan's screen). |
| `clinic_plan_status_failed` | `status`: String<br>`source`: String<br>`reason`: String | `ClinicPlans` (ClinicPlansViewModel), `ClinicPlans` (PlanDetailViewModel) | [status], [source] as in [PlanStatusChanged]; [reason]: a `toFailureReason()` word. |
| `clinic_plan_pdf_exported` | `renderer`: String | `ClinicPlans` (PlanDetailViewModel) | The plan's PDF made. [renderer]: `server` (BE-01) / `app` (the fallback when the route is missing). |
| `clinic_plan_pdf_export_failed` | `reason`: String | `ClinicPlans` (PlanDetailViewModel) | [reason]: `render_failed` (the app could not lay it out) / a `toFailureReason()` word of the server's answer. |
| `clinic_plan_wizard_step_reached` | `step`: Int | `PlanWizard` (PlanWizardViewModel) | The wizard moved on to [step] (2–5: basics, team & services, payment, review). |
| `clinic_plan_wizard_abandoned` | `step`: Int | `PlanWizard` (PlanWizardViewModel) | The wizard closed with no plan made, at [step] (1–5). |
| `clinic_plan_created` | `lines_bucket`: String<br>`doctors_bucket`: String<br>`total_bucket`: String?<br>`has_initial_payment`: Boolean<br>`has_installments`: Boolean<br>`is_activated`: Boolean<br>`is_from_patient`: Boolean | `PlanWizard` (PlanWizardViewModel) | Every save request went through. [linesBucket], [doctorsBucket]: `countBucket`s; [totalBucket]: `amountBucket` of the plan's total; [isFromPatient]: the wizard started from a patient's record. |
| `clinic_plan_save_failed` | `stage`: String<br>`reason`: String | `PlanWizard` (PlanWizardViewModel) | A save request failed (the plan stays a draft once it exists). [stage]: `plan` / `lines` / `payment` / `installments` / `status`; [reason]: a `toFailureReason()` word. |
| `clinic_service_saved` | `is_new`: Boolean<br>`is_all_doctors`: Boolean | `ClinicServices` (ClinicServicesViewModel) | [isNew]: added, not edited; [isAllDoctors]: offered by every doctor of the clinic. |
| `clinic_service_save_failed` | `is_new`: Boolean<br>`reason`: String | `ClinicServices` (ClinicServicesViewModel) | [reason]: `invalid` (a field missing) / `code_taken` (checked in the app) / a `toFailureReason()` word. |
| `clinic_service_toggled` | `is_active`: Boolean | `ClinicServices` (ClinicServicesViewModel) | Deactivated or reactivated; [isActive] is the new state. |
| `clinic_service_toggle_failed` | `reason`: String | `ClinicServices` (ClinicServicesViewModel) | [reason]: a `toFailureReason()` word. |
| `clinic_service_edit_discarded` | — | `ClinicServices` (ClinicServicesViewModel) | "Discard changes?" answered with Discard in the service sheet. |

## DocumentsEvents (`DocumentsEvents.kt`)

Group B5: documents, forms (025-2) and the clinical engine (`ui/documents`, `forms`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `document_uploaded` | `kind`: String<br>`category`: DocumentCategory<br>`is_scan`: Boolean<br>`pages_bucket`: String?<br>`is_retry`: Boolean | `DocumentUpload` (DocumentUploadViewModel) | [pagesBucket]: a scan's page count as a `countBucket` range, null for a single file. |
| `document_upload_failed` | `kind`: String?<br>`category`: DocumentCategory?<br>`reason`: String | `DocumentUpload` (DocumentUploadViewModel) | [reason]: `empty_file` / `too_large` (over 25 MB) / `unsupported_type` / `scan_failed` (refused before the type is picked, so no [category]) or a `toFailureReason()` word. [kind] is null for an unsupported file. |
| `document_upload_cancelled` | `kind`: String<br>`category`: DocumentCategory | `DocumentUpload` (DocumentUploadViewModel) | A running upload stopped with Cancel. |
| `document_opened` | `kind`: String | `DocumentViewer` (DocumentViewerViewModel) | The viewer showed the file. |
| `document_open_failed` | `reason`: String | `DocumentViewer` (DocumentViewerViewModel) | [reason]: `no_access` (locked) / `gone` (deleted) / `empty_file` / a `toFailureReason()` word. |
| `document_access_requested` | `from`: String | `PatientDocuments` (PatientDocumentsViewModel) | [from]: `list` (a locked row) / `viewer`. |
| `document_access_request_failed` | `from`: String<br>`reason`: String | `PatientDocuments` (PatientDocumentsViewModel) | [reason]: `already_requested` (409) / a `toFailureReason()` word. |
| `document_saved_to_device` | `kind`: String | `DocumentViewer` (DocumentViewerViewModel) | Save from the viewer. |
| `document_save_to_device_failed` | `kind`: String | `DocumentViewer` (DocumentViewerViewModel) | The platform could not write the file. |
| `document_edited` | `category`: DocumentCategory<br>`is_title_changed`: Boolean<br>`is_category_changed`: Boolean | `PatientDocuments` (PatientDocumentsViewModel) | The edit sheet saved; [category] is the new one. |
| `document_edit_failed` | `reason`: String | `PatientDocuments` (PatientDocumentsViewModel) | [reason]: a `toFailureReason()` word. |
| `document_deleted` | `category`: DocumentCategory? | `PatientDocuments` (PatientDocumentsViewModel) | Removed (also when it was already gone on the server). |
| `document_delete_failed` | `reason`: String | `PatientDocuments` (PatientDocumentsViewModel) | [reason]: a `toFailureReason()` word. |
| `form_saved` | `form_type`: String<br>`is_new`: Boolean | `FormEditorViewModel` | The whole save went through (form, PDF, document, link). [isNew]: a form created in this editor. |
| `form_save_failed` | `form_type`: String<br>`is_new`: Boolean<br>`step`: String?<br>`reason`: String | `FormEditorViewModel` | [step]: where it stopped, `form` / `pdf` / `document` / `link`; null for `incomplete` (neither diagnosis nor complaints). [reason]: `incomplete` / `pdf_not_made` / a `toFailureReason()` word. |
| `form_signature_requested` | `form_type`: String | `FormEditorViewModel` |  |
| `form_signature_request_failed` | `form_type`: String<br>`reason`: String | `FormEditorViewModel` | [reason]: `incomplete` / a `toFailureReason()` word (the silent save first, or the request). |
| `clinical_engine_applied` | `chips_bucket`: String | `ClinicalPanel` (ClinicalPanelViewModel) | The clinical engine wrote its texts; [chipsBucket]: the picked chips as a `countBucket` range. |
| `clinical_engine_apply_failed` | `reason`: String | `ClinicalPanel` (ClinicalPanelViewModel) | [reason]: a `toFailureReason()` word. |

## HomeEvents (`HomeEvents.kt`)

Group B2: Home, the notifications inbox, Reports and Tasks (`ui/home`, `notifications`, `analytics`, `tasks`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `home_video_slot_found` | — | `HomeViewModel` | Home's Video Call chip found a free slot (the booking it opens is B3's). |
| `home_video_slot_search_failed` | `reason`: String | `HomeViewModel` | [reason]: `no_slots` (none in the next 30 days) / a `toFailureReason()` word. |
| `home_search_result_opened` | `results`: String<br>`searched`: Boolean | `HomeSearch` (PatientSearchViewModel) | A patient opened from Home's search page. [results]: how many matched, as a `countBucket`; [searched]: whether anything was typed (the list shows every patient before that). Never the text. |
| `home_attention_task_opened` | — | `HomeAttentionViewModel` | An active task tapped in Home's attention centre. |
| `notification_opened` | `type`: String<br>`target`: String<br>`source`: String | `HomeAttentionViewModel`, `NotificationsViewModel` | An inbox row, or one on Home, tapped and its target found. [type]: the row's backend type as spelled there (`APPOINTMENT_BOOKED`), `other` when it isn't a known backend type, nothing else from it; [target]: `appointment` / `patient` / `document` / `chat` / `partnership` / `task` / `inbox`, or `none` when nothing opened (the appointment is gone, its read failed, or the row leads nowhere); [source]: `inbox` / `home_attention` / `home_activity`. |
| `notifications_filter_selected` | `filter`: NotificationFilter | `NotificationsViewModel` |  |
| `notifications_marked_all_read` | `unread`: String | `NotificationsViewModel` | [unread]: the unread rows shown before, as a `countBucket`. |
| `notifications_mark_all_failed` | `reason`: String | `NotificationsViewModel` |  |
| `access_request_decided` | `approved`: Boolean | `NotificationsViewModel` | NOTIF-04: a colleague's request to see a patient's documents, approved or declined. |
| `access_request_decide_failed` | `approved`: Boolean<br>`reason`: String | `NotificationsViewModel` |  |
| `report_range_changed` | `source`: String<br>`days`: Int<br>`ends_today`: Boolean | `AnalyticsViewModel` | The doctor chose a period. [source]: `home` / `reports`; [days]: its length; [endsToday]: a "last N days" period rather than one in the past. |
| `report_exported` | `source`: String<br>`days`: Int | `AnalyticsViewModel` | The CSV reached the share sheet. [days]: the exported period's length. |
| `report_export_failed` | `source`: String<br>`reason`: String | `AnalyticsViewModel` | [reason]: `incomplete` (a block could not be read) / `share_unavailable` (no share sheet). |
| `task_created` | `category`: TaskCategory<br>`schedule_mode`: ScheduleMode<br>`slots`: Int<br>`from_template`: Boolean | `CreateTaskViewModel` | A task created for a patient. [slots]: check-ins a day (1–15); [fromTemplate]: opened from a template. |
| `task_create_failed` | `reason`: String<br>`problems`: List<String>? = null | `CreateTaskViewModel` | [reason]: `invalid` (with [problems], the form's failed checks as `TaskFormProblem` track names: `no_patient`, `no_name`, `no_slots`, `no_end_date`, `end_before_start`, `bad_duration`) / a `toFailureReason()` word. |
| `task_cancelled` | — | `TaskDetailViewModel` |  |
| `task_cancel_failed` | `reason`: String | `TaskDetailViewModel` |  |

## PatientsEvents (`PatientsEvents.kt`)

Group B4: the patients directory, the patient record and care partnerships (`ui/patients`, `care`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `patients_searched` | `result_count`: String | `PatientsScreen` (PatientsViewModel) | The directory search settled (300 ms after typing). [resultCount]: a `countBucket`; never the text. |
| `patients_filtered` | `tab`: String<br>`status`: String?<br>`is_descending`: Boolean | `PatientsScreen` (PatientsViewModel) | The segment, status filter or order changed. [tab]: `all` / `favorites` / `follow_ups`; [status]: `active` / `at_risk` / `follow_up` / …, null for all statuses. |
| `patient_picked` | `searched`: Boolean<br>`result_count`: String? | `PatientPickerSheet` | A patient picked in the shared picker. [resultCount]: a `countBucket` of the list shown, only when searched. |
| `patient_created` | `has_phone`: Boolean | `CreatePatient` (CreatePatientViewModel) |  |
| `patient_create_failed` | `reason`: String | `CreatePatient` (CreatePatientViewModel) | [reason]: `invalid_fields` (not sent) / `conflict` (a known phone) / `validation` / a `toFailureReason()` word. |
| `patient_details_edited` | — | `CreatePatient` (CreatePatientViewModel) | "Edit details" saved. |
| `patient_details_edit_failed` | `reason`: String | `CreatePatient` (CreatePatientViewModel) | [reason]: `invalid_fields` / `conflict` / `not_found` / a `toFailureReason()` word. |
| `patient_field_saved` | `field`: String<br>`is_enabled`: Boolean? | `PatientDetail` (PatientDetailViewModel) | One field of the record saved. [field]: `phones` / `allergies` / `chronic_disease` / `gender` / `blood_group` / `sms_reminders` / `sms_reminder_hours`; [isEnabled]: only for `sms_reminders`, the new setting. |
| `patient_field_save_failed` | `field`: String<br>`reason`: String | `PatientDetail` (PatientDetailViewModel) | [field] as above; [reason]: `invalid_phone` (not sent) / `conflict` (a known phone) / a `toFailureReason()` word. |
| `patient_favorite_toggled` | `is_favorite`: Boolean | `PatientDetail` (PatientDetailViewModel) |  |
| `patient_test_sms_sent` | — | `PatientDetail` (PatientDetailViewModel) |  |
| `patient_test_sms_failed` | `reason`: String | `PatientDetail` (PatientDetailViewModel) | [reason]: a `toFailureReason()` word (`forbidden`: the plan has no SMS; `server`: the provider is down). |
| `patient_account_created` | — | `PatientDetail` (PatientDetailViewModel) | A patient-app login created for the patient. |
| `patient_account_create_failed` | `reason`: String | `PatientDetail` (PatientDetailViewModel) | [reason]: `conflict` (they have one already) / a `toFailureReason()` word. |
| `patient_briefing_generated` | `is_regenerate`: Boolean | `PatientBriefing` (PatientBriefingViewModel) | [isRegenerate]: a briefing was already shown this session. |
| `patient_briefing_failed` | `is_regenerate`: Boolean<br>`reason`: String | `PatientBriefing` (PatientBriefingViewModel) | [reason]: `empty` (nothing to brief on) / `rate_limited` (the daily limit) / a `toFailureReason()` word. |
| `patient_briefing_copied` | — | `PatientBriefing` (PatientBriefingViewModel) |  |
| `prophylaxis_saved` | `is_enabled`: Boolean<br>`interval_months`: Int | `Prophylaxis` (ProphylaxisViewModel) | [intervalMonths]: 1–60, the reminder interval the clinic chose (a setting, not a clinical value). |
| `prophylaxis_save_failed` | `reason`: String | `Prophylaxis` (ProphylaxisViewModel) |  |
| `care_partner_searched` | `result_count`: String<br>`has_specialty`: Boolean | `CareViewModels` (FindPartnerViewModel) | A partner search typed by the doctor settled (not the list shown on open). [resultCount]: a `countBucket`; [hasSpecialty]: the specialty filter was used. Never the text. |
| `care_invite_sent` | `has_message`: Boolean | `CareViewModels` (FindPartnerViewModel) | [hasMessage]: the doctor wrote a message (never the message). |
| `care_invite_failed` | `reason`: String | `CareViewModels` (FindPartnerViewModel) | [reason]: `conflict` (already a partner for this patient) / a `toFailureReason()` word. |
| `care_partnerships_filtered` | `filter`: String | `CareViewModels` (PartnershipsViewModel) | [filter]: `all` / `needs_answer` / `active` / `sent` / `finished`. |
| `care_partnership_acted` | `action`: String | `CareViewModels` (PartnershipViewModel) | [action]: `accept` / `decline` / `cancel` / `complete`. |
| `care_partnership_act_failed` | `action`: String<br>`reason`: String | `CareViewModels` (PartnershipViewModel) |  |
| `care_progress_posted` | — | `CareViewModels` (PartnershipViewModel) |  |
| `care_progress_post_failed` | `reason`: String | `CareViewModels` (PartnershipViewModel) |  |

## ProfileEvents (`ProfileEvents.kt`)

Group B9: profile, settings, subscription and billing, security, legal, privacy (`ui/profile`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `profile_section_saved` | `section`: String<br>`changed_fields`: List<String> | `ContactScreen` (ContactViewModel), `ExtendedProfileScreen` (ExtendedProfileViewModel), `PersonalInfoScreen` (PersonalInfoViewModel), `BillingScreen` (BillingViewModel) | A profile form saved. [section]: `personal_info` / `contact` / `extended` / `invoicing`. [changedFields]: the names of the changed fields (`first_name`, `last_name`, `dob`, `gender`, `profession`; `phone`, `email`, `address`, `map_pin`; `biography`, `telegram`, `instagram`; `billing_name`, `billing_email`, `iban`, `tax_id`), never their values. |
| `profile_section_save_failed` | `section`: String<br>`reason`: String | `TimeZonePicker` (TimezoneViewModel), `ContactScreen` (ContactViewModel), `ExtendedProfileScreen` (ExtendedProfileViewModel), `PersonalInfoScreen` (PersonalInfoViewModel), `BillingScreen` (BillingViewModel) | A profile write that did not land. [section]: as [SectionSaved], or `time_zone` / `tags` / `certificates`. [reason]: a `toFailureReason()` word. |
| `profile_edit_discarded` | `section`: String | `ContactScreen` (ContactViewModel), `ExtendedProfileScreen` (ExtendedProfileViewModel), `PersonalInfoScreen` (PersonalInfoViewModel), `BillingScreen` (BillingViewModel) | Edits thrown away at the discard question. [section]: as [SectionSaved]. |
| `profile_file_uploaded` | `kind`: String | `ExtendedProfileScreen` (ExtendedProfileViewModel), `PersonalInfoScreen` (PersonalInfoViewModel) | [kind]: `photo` / `certificate_image` / `certificate_pdf`. |
| `profile_file_upload_failed` | `kind`: String<br>`reason`: String | `ExtendedProfileScreen` (ExtendedProfileViewModel), `PersonalInfoScreen` (PersonalInfoViewModel) | [kind]: as [FileUploaded]; [reason]: `too_large` or a `toFailureReason()` word. |
| `profile_camera_denied` | `section`: String | `ExtendedProfileScreen` (ExtendedProfileViewModel), `PersonalInfoScreen` (PersonalInfoViewModel) | The camera permission was refused while picking. [section]: `personal_info` / `extended`. |
| `profile_tags_saved` | `tag_count`: String | `ExtendedProfileScreen` (ExtendedProfileViewModel) | The service tags saved (a tag added or removed). [tagCount]: `countBucket` of the list. |
| `profile_certificate_deleted` | — | `ExtendedProfileScreen` (ExtendedProfileViewModel) |  |
| `practice_time_zone_changed` | `from_zone`: String?<br>`to_zone`: String | `TimeZonePicker` (TimezoneViewModel) | The practice time zone set (IANA ids, not personal). [fromZone] is null for a first zone. |
| `practice_time_zone_change_cancelled` | — | `TimeZonePicker` (TimezoneViewModel) | "Change practice timezone?" answered with Cancel. |
| `profile_service_saved` | `is_new`: Boolean<br>`is_free`: Boolean<br>`is_pay_later`: Boolean<br>`has_location_rate`: Boolean<br>`price_bucket`: String? | `ServicesScreen` (ServicesViewModel) | A service of the doctor's own saved. [isPayLater]: paid in clinic (PAY_LATER), not online. [priceBucket]: `amountBucket` of its first price (null when free). [hasLocationRate]: a price is limited to one location. |
| `profile_service_save_failed` | `is_new`: Boolean<br>`reason`: String | `ServicesScreen` (ServicesViewModel) | [reason]: a `toFailureReason()` word. |
| `profile_service_deleted` | — | `ServicesScreen` (ServicesViewModel) |  |
| `profile_service_delete_failed` | `reason`: String | `ServicesScreen` (ServicesViewModel) |  |
| `profile_service_groups_saved` | `group_count`: String | `ServicesScreen` (ServicesViewModel) | [groupCount]: `countBucket` of the groups after the save. |
| `profile_service_groups_save_failed` | `reason`: String | `ServicesScreen` (ServicesViewModel) |  |
| `subscription_checkout_opened` | `plan`: String<br>`from_tier`: DoctorTier?<br>`price_bucket`: String? | `BillingScreen` (BillingViewModel) | The Stripe checkout page for a plan was handed to the browser. [plan]: the plan code as the backend spells it (`PRO`, `PREMIUM`); [fromTier]: the plan held then; [priceBucket]: `amountBucket` of the plan's price. |
| `subscription_checkout_returned` | `is_tier_changed`: Boolean<br>`tier`: DoctorTier? | `BillingScreen` (BillingViewModel) | Back from the checkout: [isTierChanged] once the plan read differs from the one held before; [tier]: now. |
| `stripe_connect_opened` | — | `BillingScreen` (BillingViewModel) |  |
| `stripe_connect_returned` | `has_account`: Boolean | `BillingScreen` (BillingViewModel) | Back from the Stripe Connect onboarding. |
| `billing_link_failed` | `kind`: String<br>`reason`: String | `BillingScreen` (BillingViewModel) | A checkout or onboarding page that could not be opened. [kind]: `checkout` / `connect`; [reason]: `not_opened` (the browser refused the window) or a `toFailureReason()` word. |
| `password_changed` | — | `SecurityScreen` (SecurityViewModel) |  |
| `password_change_failed` | `reason`: String | `SecurityScreen` (SecurityViewModel) | [reason]: `wrong_current_password` or a `toFailureReason()` word. |
| `settings_saved` | `changed`: List<String><br>`country`: String?<br>`start_tab`: String? | `SettingsScreen` (SettingsViewModel) | The settings block saved. [changed]: `country` / `start_tab` / `two_fa` / `encrypted_docs`; [country] and [startTab] (a `ShellTab` key) are sent only when they changed. |
| `settings_save_failed` | `reason`: String | `SettingsScreen` (SettingsViewModel) |  |
| `settings_language_changed` | `from`: AppLanguage<br>`to`: AppLanguage | `LanguageScreen` | The language picked on the Language screen (Settings or More). |
| `usage_data_enabled` | — | `SettingsScreen` (SettingsViewModel) | The usage-data switch turned back on (D3). Turning it off is never sent: tracking is off from then on. |
| `legal_link_opened` | `link`: String | `AboutScreen` | [link]: `terms` / `privacy` / `licences`. |
| `contract_opened` | — | `AboutViewModel` |  |
| `contract_pdf_failed` | `reason`: String | `AboutViewModel` |  |
| `diagnostics_copied` | — | `AboutScreen` |  |
| `data_export_saved` | — | `PrivacyViewModel` |  |
| `data_export_failed` | `reason`: String | `PrivacyViewModel` | [reason]: `daily_limit` / `save_failed` (the file could not be written) / a `toFailureReason()` word. |
| `account_deletion_code_sent` | — | `PrivacyViewModel` | The deletion code e-mailed (Continue on the question). |
| `account_deletion_blocked` | `obligations`: List<String> | `PrivacyViewModel` | Refused while obligations are open. [obligations]: `upcoming_appointments` / `active_subscription`. |
| `account_deletion_failed` | `step`: String<br>`reason`: String | `PrivacyViewModel` | [step]: `request` / `confirm`; [reason]: `wrong_code` or a `toFailureReason()` word. |
| `account_deleted` | — | `PrivacyViewModel` | Sent before the session ends (the reset follows). |
| `account_deletion_cancelled` | `step`: String | `PrivacyViewModel` | The deletion sheets closed before the end. [step]: `ask` / `code` / `blocked`. |

## SessionTrackEvents (`SessionTrackEvents.kt`)

Stage A (A5): the session's start and end, sent by the one collector of `sessionRepository.session` and `.ended`.

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `session_started` | `role`: TrackingRole<br>`restored`: Boolean | `TrackingSession` | A session exists: after a sign-in or a restore at launch (once per process, like the patient app's `session_restored`). [role]: `doctor` / `staff`. [restored]: true for the session saved on the device at launch, false for a new sign-in, so restores never count as sign-ins. |
| `session_ended` | `reason`: SessionEndReason | `TrackingSession` | [reason]: `expired` / `unauthorized` / `account_disabled` / `signed_out` / `pin_lockout` / `inactivity` / `account_deleted`. Send it before `Tracking.reset()`, so it still belongs to the user. |

## ShellEvents (`ShellEvents.kt`)

Stage A (A6): the shell (`ui/navigation`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `tab_selected` | `tab`: String<br>`source`: String | `ShellViewModel` | A tab chosen by the doctor. [tab]: a `ShellTab` key (`home`, `calendar`, …); [source]: `bar` / `more` / `sidebar`. |
| `more_opened` | — | `ShellViewModel` |  |
| `tab_refused` | `tab`: String | `ShellViewModel` | A tab the plan or the clinic membership does not allow (it opens Home instead). [tab]: `tasks` / `clinic`. |
| `push_opened` | `type`: String?<br>`routed_to`: String | `ShellViewModel` | A tapped push, once its target is found; the patient app's event and property names (one PostHog project). [type]: the push's `type` as the backend spells it (`APPOINTMENT_REMINDER`), `other` when it isn't a known backend type, left out without one; nothing else from its data (it names patients). [routedTo]: `appointment` / `patient` / `document` / `chat` / `partnership` / `task` / `inbox`, or `home` when its appointment is gone. |

## VisitEvents (`VisitEvents.kt`)

Group B6: the visit workspace, video, the AI scribe and dictation (`ui/visit`, `speech`).

| Event | Properties (name: type) | Fired from | Notes and allowed values |
|---|---|---|---|
| `visit_opened` | `kind`: VisitKind<br>`is_dental`: Boolean<br>`is_completed`: Boolean | `VisitViewModel` | The workspace loaded for the first time. |
| `visit_mode_changed` | `mode`: String | `VisitViewModel` | [mode]: `general` / `dental` / `form0252`. |
| `visit_helper_opened` | `helper`: String<br>`is_empty`: Boolean | `VisitViewModel` | [helper]: `ai` / `form`; [isEmpty]: there was nothing to show (a banner said so). |
| `visit_icd_applied` | — | `VisitViewModel` | An AI-suggested ICD-10 code applied (never the code). |
| `visit_photo_added` | `kind`: String | `VisitViewModel` | [kind]: `before` / `after`. |
| `visit_signature_requested` | — | `VisitViewModel` |  |
| `visit_patient_signed` | — | `VisitViewModel` | The awaited signature arrived (waiting → signed while the visit was open). |
| `visit_signature_request_failed` | `reason`: String | `VisitViewModel` | [reason]: `notes_not_saved` / a `toFailureReason()` word. |
| `visit_no_show_marked` | `is_patient_notified`: Boolean | `VisitViewModel` |  |
| `visit_no_show_failed` | `reason`: String | `VisitViewModel` | [reason]: `not_allowed` (`NO_SHOW_NOT_ALLOWED`) / a `toFailureReason()` word. |
| `visit_end_started` | `kind`: VisitKind<br>`has_plan_step`: Boolean<br>`has_payment_step`: Boolean<br>`has_scribe_step`: Boolean | `VisitViewModel` | The End pipeline started, with the steps it will run. |
| `visit_payment_invalid` | — | `VisitViewModel` | End refused before it started: the payment typed at the visit is not above zero (input, not a failure). |
| `visit_end_failed` | `reason`: String<br>`step`: String | `VisitViewModel` | End stopped before the visit was completed (it stays open). [step]: `notes` / `dental` / `plan` / `payment` / `complete`; [reason]: `notes_not_saved` / `dental_not_loaded` / `dental_not_saved` / `plan_not_saved` / `payment_not_recorded` / a `toFailureReason()` word (the complete call). Alert "Visit can't be completed". |
| `visit_completed` | `kind`: VisitKind<br>`mode`: String<br>`has_scribe`: Boolean<br>`has_photos`: Boolean | `VisitViewModel` | The visit completed on the server. [mode]: as [VisitModeChanged]. |
| `visit_plan_lines_completed` | `lines_bucket`: String | `VisitViewModel` | Treatment-plan lines completed by this visit; [linesBucket] from `countBucket`. |
| `visit_payment_recorded` | `method`: String<br>`amount_bucket`: String? | `VisitViewModel` | A plan payment recorded at the visit. [method]: `cash` / `card` / `transfer`; [amountBucket] from `amountBucket`. |
| `visit_summary_saved` | `is_retry`: Boolean<br>`has_dental`: Boolean<br>`has_photos`: Boolean<br>`has_plan`: Boolean | `VisitViewModel` | The summary PDF made and filed under the patient. [isRetry]: from Retry summary. |
| `visit_summary_skipped` | `reason`: String | `VisitViewModel` | Nothing to put in a summary. [reason]: `nothing_to_save` / `ai_later` (the scribe's notes come later). |
| `visit_summary_failed` | `reason`: String<br>`is_retry`: Boolean | `VisitViewModel` | The visit is completed but its summary isn't saved (Retry summary). [reason]: `notes_not_read` / `pdf_failed` / `no_patient` / a `toFailureReason()` word (the upload). |
| `visit_notes_save_failed` | `reason`: String | `VisitNotes` | An autosave failed; once until a save succeeds again. |
| `visit_ai_draft_confirmed` | — | `VisitNotes` |  |
| `visit_ai_draft_discarded` | — | `VisitNotes` |  |
| `visit_ai_draft_failed` | `action`: String<br>`reason`: String | `VisitNotes` | [action]: `confirm` / `discard`. |
| `scribe_recording_started` | — | `Dictation` |  |
| `scribe_recording_cancelled` | — | `Dictation` |  |
| `scribe_recording_finished` | `duration_bucket`: String | `Dictation` | The scribe's recording stopped and is about to be sent; [durationBucket] from `durationBucket`. |
| `scribe_uploaded` | `is_retry`: Boolean | `VisitViewModel` | The recording reached the scribe. [isRetry]: "Upload the recording again". |
| `scribe_failed` | `reason`: String<br>`stage`: String | `VisitViewModel`, `Dictation` | [stage]: `record` (the recorder gave nothing) / `upload` / `notes` (End waited and got no notes); [reason]: `recording_failed` / `timeout` / a `toFailureReason()` word (never `forbidden` / `rate_limited` at `upload`: those are [ScribeBlocked]). Alert "Visit can't be completed". |
| `scribe_blocked` | `reason`: String | `VisitViewModel` | The upload was refused by the plan's rules, not broken (kept off the alert). [reason]: `forbidden` (no PRO) / `rate_limited` (the daily limit). |
| `scribe_notes_ready` | — | `VisitViewModel` | End waited for the scribe and its notes arrived. |
| `scribe_wait_skipped` | — | `VisitViewModel` | "Continue without waiting". |
| `video_permissions_result` | `is_camera_granted`: Boolean<br>`is_mic_granted`: Boolean | `VideoVisit` | The camera and microphone answers, when they change. |
| `video_joined` | `is_rejoin`: Boolean | `VideoVisit` |  |
| `video_join_failed` | `reason`: String | `VideoVisit` | [reason]: `too_early` / `too_late` / `payment_required` / `daily_failed` (the SDK refused) / a `toFailureReason()` word (the token). Alert "Video visits failing". |
| `video_control_toggled` | `control`: String<br>`is_on`: Boolean? | `VideoVisit` | [control]: `camera` / `mic` / `flip`; [isOn] null for `flip`. |
| `video_call_ended` | `ended_by`: String<br>`has_patient_joined`: Boolean<br>`duration_bucket`: String? = null | `VideoVisit` | The doctor's call ended. [endedBy]: `doctor` (Leave call) / `visit_closed` (Back or End from the call) / `remote` (Daily left) / `error` (the call failed). |
| `dictation_started` | `field_kind`: String | `Dictation` | [fieldKind] for every dictation event: `soap` / `free_notes` / `dental_notes` / `form` (never the field's label); `scribe` only in [DictationMicDenied] (the scribe's recording has its own events). |
| `dictation_cancelled` | `field_kind`: String | `Dictation` |  |
| `dictation_transcribed` | `field_kind`: String<br>`has_speech`: Boolean | `Dictation` | [hasSpeech]: false for "No speech detected" (nothing added). |
| `dictation_failed` | `field_kind`: String<br>`reason`: String | `Dictation` | [reason]: `recording_failed` / a `toFailureReason()` word (`forbidden` = no plan, `rate_limited`, `timeout`). |
| `dictation_mic_denied` | `field_kind`: String | `Dictation` |  |
| `dictation_reported` | — | `Dictation` |  |
| `dictation_report_failed` | `reason`: String | `Dictation` |  |
| `visit_briefing_opened` | — | `Briefing` (BriefingViewModel) |  |
| `visit_briefing_ready` | — | `Briefing` (BriefingViewModel) | Once per sheet. |
| `visit_briefing_failed` | `reason`: String | `Briefing` (BriefingViewModel) | Once until it recovers. [reason]: `generation_failed` (the backend's status) / a `toFailureReason()` word. |
| `visit_briefing_copied` | — | `Briefing` (BriefingViewModel) |  |
| `visit_facts_documented` | `facts_bucket`: String<br>`rejected_bucket`: String<br>`tasks_bucket`: String | `VisitFacts` (VisitFactsViewModel) | "Document everything"; the buckets from `countBucket`. |
| `visit_facts_document_failed` | `reason`: String | `VisitFacts` (VisitFactsViewModel) | [reason]: `notes_not_saved` / a `toFailureReason()` word. |

## Screen ids

Sent with `Tracker.screen(TrackScreen.X)` as `$screen` (`$screen_name` = the id). Who sends each one follows the
ownership table in `03-developer-guide.md` §2; "sent from" is the file that names it.

| Screen id | Enum | Owner | Sent from |
|---|---|---|---|
| `splash` | `Splash` | `App.kt` (A6) | `App` |
| `sign_in` | `SignIn` | `App.kt` (A6) | `App` |
| `email_entry` | `EmailEntry` | `App.kt` (A6) | `App` |
| `email_code` | `EmailCode` | `App.kt` (A6) | `App` |
| `reset_password` | `ResetPassword` | `App.kt` (A6) | `App` |
| `verify_key` | `VerifyKey` | `App.kt` (A6) | `App` |
| `register` | `Register` | B1 | `RegisterScreen` |
| `lock` | `Lock` | `App.kt` (A6) | `App` |
| `home` | `Home` | shell (A6) | `ShellViewModel` |
| `calendar` | `Calendar` | shell (A6) | `ShellViewModel` |
| `patients` | `Patients` | shell (A6) | `ShellViewModel` |
| `chat` | `Chat` | shell (A6) | `ShellViewModel` |
| `clinic` | `Clinic` | shell (A6) | `ShellViewModel` |
| `tasks` | `Tasks` | shell (A6) | `ShellViewModel` |
| `reports` | `Reports` | shell (A6) | `ShellViewModel` |
| `notifications` | `Notifications` | shell (A6) | `ShellViewModel` |
| `profile` | `Profile` | shell (A6) | `ShellViewModel` |
| `intelligence` | `Intelligence` | shell (A6) | `ShellViewModel` |
| `language` | `Language` | shell (A6) | `ShellViewModel` |
| `conversation` | `Conversation` | shell (A6) | `ShellViewModel` |
| `patient_chat` | `PatientChat` | shell (A6) | `ShellViewModel` |
| `patient_search` | `PatientSearch` | shell (A6) | `ShellViewModel` |
| `task` | `Task` | shell (A6) | `ShellViewModel` |
| `task_create` | `TaskCreate` | shell (A6) | `ShellViewModel` |
| `task_templates` | `TaskTemplates` | shell (A6) | `ShellViewModel` |
| `schedule` | `Schedule` | shell (A6) | `ShellViewModel` |
| `patient` | `Patient` | shell (A6) | `ShellViewModel` |
| `locations` | `Locations` | shell (A6) | `ShellViewModel` |
| `appointment` | `Appointment` | shell (A6) | `ShellViewModel` |
| `visit` | `Visit` | shell (A6) | `ShellViewModel` |
| `block` | `Block` | shell (A6) | `ShellViewModel` |
| `plan_detail` | `PlanDetail` | shell (A6) | `ShellViewModel` |
| `plan_wizard` | `PlanWizard` | shell (A6) | `ShellViewModel` |
| `find_partner` | `FindPartner` | shell (A6) | `ShellViewModel` |
| `partnerships` | `Partnerships` | shell (A6) | `ShellViewModel` |
| `partnership` | `Partnership` | shell (A6) | `ShellViewModel` |
| `doctor_schedule` | `DoctorSchedule` | shell (A6) | `ShellViewModel` |
| `booking` | `Booking` | shell (A6) | `ShellViewModel` |
| `form` | `Form` | shell (A6) | `ShellViewModel` |
| `document` | `Document` | shell (A6) | `ShellViewModel` |
| `profile_personal_info` | `ProfilePersonalInfo` | shell (A6) | `ShellViewModel` |
| `profile_contact` | `ProfileContact` | shell (A6) | `ShellViewModel` |
| `profile_timezone` | `ProfileTimezone` | shell (A6) | `ShellViewModel` |
| `profile_extended` | `ProfileExtended` | shell (A6) | `ShellViewModel` |
| `profile_services` | `ProfileServices` | shell (A6) | `ShellViewModel` |
| `profile_subscription` | `ProfileSubscription` | shell (A6) | `ShellViewModel` |
| `profile_settings` | `ProfileSettings` | shell (A6) | `ShellViewModel` |
| `profile_security` | `ProfileSecurity` | shell (A6) | `ShellViewModel` |
| `profile_about` | `ProfileAbout` | shell (A6) | `ShellViewModel` |
| `profile_privacy` | `ProfilePrivacy` | shell (A6) | `ShellViewModel` |
| `patient_overview` | `PatientOverview` | B4 | `PatientDetail` |
| `patient_medical` | `PatientMedical` | B4 | `PatientDetail` |
| `patient_appointments` | `PatientAppointments` | B4 | `PatientDetail` |
| `patient_documents` | `PatientDocuments` | B4 | `PatientDetail` |
| `patient_forms` | `PatientForms` | B4 | `PatientDetail` |
| `patient_activity` | `PatientActivity` | B4 | `PatientDetail` |
| `clinic_overview` | `ClinicOverview` | B8 | `ClinicWorkspace` |
| `clinic_calendar` | `ClinicCalendar` | B8 | `ClinicWorkspace` |
| `clinic_doctors` | `ClinicDoctors` | B8 | `ClinicWorkspace` |
| `clinic_invitations` | `ClinicInvitations` | B8 | `ClinicWorkspace` |
| `clinic_patients` | `ClinicPatients` | B8 | `ClinicWorkspace` |
| `clinic_plans` | `ClinicPlans` | B8 | `ClinicWorkspace` |
| `clinic_finance` | `ClinicFinance` | B8 | `ClinicWorkspace` |
| `clinic_services` | `ClinicServices` | B8 | `ClinicWorkspace` |
| `clinic_settings` | `ClinicSettings` | B8 | `ClinicWorkspace` |
| `finance_dashboard` | `FinanceDashboard` | B8 | `ClinicFinanceViews` |
| `finance_ledger` | `FinanceLedger` | B8 | `ClinicFinanceViews` |
| `finance_installments` | `FinanceInstallments` | B8 | `ClinicFinanceViews` |
| `finance_earnings` | `FinanceEarnings` | B8 | `ClinicFinanceViews` |
| `finance_records` | `FinanceRecords` | B8 | `ClinicFinanceViews` |
| `finance_payments` | `FinancePayments` | B8 | `ClinicFinanceViews` |
