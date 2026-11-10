# Tracking B7: chat and Shifa Intelligence

Folders `ui/chat`, `ui/intelligence` (voice included); events in `data/tracking/events/ChatEvents.kt`. No design board
and no flow file: tracking has no UI. Every class that tracks takes `tracker: Tracker = Tracking` as its last
constructor parameter (`ChatListViewModel`, chat `ThreadViewModel`, `PatientChatViewModel`, `IntelligenceViewModel`,
intelligence `ThreadViewModel`, `VoiceSession`, which gets the thread's tracker); no call site changed.

Never sent: a message, prompt, reply, transcript, file or attachment name, search text, the other party's name, any
id. Sent: kinds, counts as `countBucket`, the reply's duration in whole seconds, the card's type, outcomes. `reason`
on a `*_failed` event is `toFailureReason()` unless listed otherwise.

## Events

| Event | Properties | Fires in |
|---|---|---|
| `chat_message_sent` | `kind` (`text` / `image` / `document` / `voice`), `is_retry` | chat `ThreadViewModel.deliver`, when the server took it (after the upload for an attachment) |
| `chat_message_failed` | `kind`, `reason` (`too_large`, `file_type`, `too_short`, `read_failed` for the checks before sending; else `toFailureReason()` of the upload or send) | `sendImage` / `sendDocument` / `sendVoice` refusals, `deliver` failure |
| `chat_conversation_started` | `is_doctor` (with a colleague) | `ChatListViewModel.startWith`: the person's conversation found in the list or started by the backend |
| `chat_conversation_start_failed` | `reason`, `entry` (`search` / `patient`) | `startWith` failure; `PatientChatViewModel` (a patient's "Send message") failure |
| `chat_users_searched` | `result_count` (`countBucket`) | `ChatListViewModel.type`, when the debounced search answers for the text still typed |
| `chat_list_sorted` | `sort` (`newest` / `oldest` / `unread_newest` / `unread_oldest`) | `ChatListViewModel.sort`, only when the order changes |
| `intelligence_reply_received` | `input` (`typed` / `starter` / `voice` / `card_answer`), `card_type` (the backend's card type, `IntelCard.typeOf`, when the app knows it, else `other`; left out for words only), `has_schedule_change` (Shifa itself booked, moved or cancelled in this answer), `seconds` (question to finished answer), `is_retry`, `has_patient_context` | intelligence `ThreadViewModel.finish` |
| `intelligence_reply_failed` | `reason` (`empty`, `ai_unavailable`, else `toFailureReason()`: `rate_limited` is the daily limit, `timeout`), `input`, `is_retry` | `finish` (empty answer), `fail` |
| `intelligence_reply_stopped` | `input` | `cancelTurn` while an answer is being written (the mic on "Reviewing…", leaving, another chat) |
| `intelligence_facts_documented` | `fact_count` (`countBucket`) | `documentFacts` success |
| `intelligence_facts_document_failed` | `reason` | `documentFacts` failure |
| `intelligence_proposal_dismissed` | none | `dismiss` (a proposal's Cancel) |
| `intelligence_cancel_answered` | `is_confirmed` | `answerCancel` ("Yes, cancel" / "Keep them"; the turn it sends is `input = card_answer`), only when the answer went (not while a reply or a booking runs) |
| `intelligence_conversation_renamed` / `_deleted` | none | `IntelligenceViewModel.rename` / `delete` |
| `intelligence_conversation_exported` | none | `IntelligenceViewModel.exported`, called by `IntelligenceScreen` when the share sheet opened |
| `intelligence_conversation_export_failed` | `reason` (`empty` / `share_failed`) | `export` (nothing to export) / `exportFailed` |
| `intelligence_voice_transcribed` | `mode` (`hands_free` / `dictation`) | `VoiceSession.transcribeAndAsk`, a take with words that goes to Shifa |
| `intelligence_voice_failed` | `reason` (`no_speech`, `mic_unavailable`, `mic_denied`, `pro_required`, `background`, else `toFailureReason()` of the transcription: `forbidden`, `rate_limited`, `timeout`, …), `mode` (left out for `mic_denied` / `pro_required`) | `giveUp`, `noSpeech`, `start`, `denied`, `locked`, `paused`, transcription failure |
| `intelligence_voice_discarded` | none | `discard` (the dictation pill's ✕) |
| `intelligence_voice_signed_off` | none | `signOff` ("that's it", "rahmat", …: nothing went to Shifa) |

**Reused from B3 (`CalendarEvents`), never redefined:** a proposal card's Confirm / slot button
(`ThreadViewModel.book`) sends `appointment_booked` (`entry = intelligence`, `is_video`, `is_on_behalf = false`,
`minutes` = the slot's) or `booking_failed` (`reason` `slot_taken` for 400 / 409 as the booking form, else
`toFailureReason()`; `entry = intelligence`), so the "Booking failing" alert sees AI bookings. A reschedule card sends
`appointment_rescheduled` / `appointment_reschedule_failed` (`is_on_behalf = false`). A spoken reply's turn is the
same `intelligence_reply_*` with `input = voice`.

`amountBucket`: not used by any event here.

## Screens

None. `chat`, `conversation`, `patient_chat` and `intelligence` are the shell's; the history tray, the menus, the PDF
and image viewers and the voice panel are sheets or overlays on those.

## Left out, and why

- Conversation opened, PDF / image opened, "other times", Review on the visit, Open calendar / document, a visit
  opened by an answer: navigation, covered by the shell's screen views (`conversation`, `visit`, `document`).
- A PDF that couldn't be read, list and thread read failures, the search failing: `api_error` covers them.
- New conversation (Intelligence): nothing is created until the first question; `intelligence_reply_*` counts that.
  History searched: a view change, and the text is never sent.
- Which starter card: the starter cards send their prompt as a typed text through `WelcomeBody`'s `(String) -> Unit`;
  `input = starter` says a starter was used, not which (would need a new parameter through `WelcomeBody`).
- A take started, hands-free turned off: per-take starts repeat every hands-free loop; the outcome events
  (`intelligence_voice_*`) and `input = voice` on the reply show voice use.
- A proposal with a broken slot (no instant / patient): bad card data, not a failed booking (kept out of the alert).
- `intelligence_reply_stopped` has no ViewModel test: the test engine answers streams at once, so no turn is running
  when `stop()` is called. The other events each have one.

## Needs from the coordinator

- **`appointment_rescheduled` / `appointment_reschedule_failed` have no `entry`**: an Intelligence move can't be told
  from a calendar move. Add `entry: String? = null` to both in `CalendarEvents` (B3's file) and B7 will pass
  `"intelligence"`.
- **Bookings and cancellations Shifa makes inside an answer** (the backend's workflow: the card arrives `booked` /
  `moved`, or a `cancel_result`) are not sent as `appointment_booked` / `appointment_cancelled`: the card has no
  booked length or video flag for `AppointmentBooked.minutes` / `AppointmentCancelled.isVideo` (non-null). They show as
  `intelligence_reply_received` with `has_schedule_change = true` and the `card_type`. If the funnel needs them as
  bookings, make `minutes` / `is_video` nullable there and B7 will send them.
- Optional: a shared duration range in `TrackProperties.kt` if `seconds` on `intelligence_reply_received` should be a
  bucket instead of whole seconds.
