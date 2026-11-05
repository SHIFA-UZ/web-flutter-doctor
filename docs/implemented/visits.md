# Appointments and visits (M5)

What is built for the visit itself: opening a visit, its notes, scribe, dental documentation, video and the summary
document. Spec `docs/kmp-spec/05-appointments-video-dental.md`; flows `docs/ux-flows/05-appointments-visit/`.

## APPT-01 · Appointment model and the visit route

Built 2026-11-04. Spec 05 §1, ticket APPT-01. No screen yet (the visit screens are APPT-04 and VIDEO-02).

- **Model** (`data/model/AppointmentModels.kt`): `AppointmentDetail` is `GET /appointments/{id}` as the doctor reads
  it. The OpenAPI `AppointmentDto` is the patient's: the doctor's response (checked on dev, appointment 7 as
  seed.doctor1) also has `patientId`, `patientName`, `patientSignedAt`, the signature image, `linkedPlanId`,
  `linkedPlanTitle`, `fulfilledLineIds`, `briefingStatus`, `attachmentCount`, and **no** `isVideoConsultation`. Times
  stay UTC strings with `start`, `end`, `signedAt` parsed (an unreadable one is null); a null list reads as empty;
  unknown keys are ignored. `signature` is None, Waiting (requested) or Signed (a date, or `alreadySigned`);
  `paymentStatus` is kept (Flutter dropped it).
- **Kind** (`VisitKind`): the backend's flag when present, else the booked location's "Video" (one function, marked
  `ponytail:`, also behind the calendar's `isVideo`). BE-09 corrected: the doctor's detail has no flag either.
- **Route** `ShellRoute.Visit(appointmentId, kind, opened)`: ids only; the screen reads the patient from the detail,
  so Flutter's `GET /calendar?day=` lookup for a missing patient id goes away. The appointment panel's Start / Continue
  opens it; until APPT-04 / VIDEO-02 it shows "This part of the record is on its way." under "Visit" / "Video Call".
  Open summary waits for PDF-01.
- A no-show, like a cancelled visit, has no main button in the panel (`ctaFor`).
- **Tests:** `AppointmentModelsTest` (3: dev's response, the kind rules, the signature states), `AppointmentRulesTest` (no-show).

## APPT-02 · Appointment detail and signature repository

Built 2026-11-04. Spec 05 §2.2, §7, ticket APPT-02. No screen yet (the footer that shows it is APPT-04).

- **Repository** (`data/appointments/AppointmentsRepository.kt`): `detail(id)` (`GET /appointments/{id}`),
  `requestSignature(id)` (`PUT …/request-signature {}`), and `signatureUpdates(id)`: the visit now, then every 10 s
  **only while** the signature is awaited; the flow ends once it is signed (or was never asked). Every read is
  emitted as a Result: a passing failure (no network, 5xx, 429) is tried again at the next tick, a lasting one (404,
  403…) ends it. The screen's ViewModel owns the one collection and starts it again after a request,
  so there is never a second timer (Flutter started one per request and per screen load).
- **Demo backend:** `GET /appointments/{id}` for the demo calendar's two visits (101 by video, 102 in the clinic, on
  the next busy day within three), 404 for others; `PUT …/request-signature` records the time (404 for an unknown
  visit) and the demo patient signs 15 s later.
- **Tests:** `AppointmentsRepositoryTest` (3: polling while awaited with a failed read in between, ending when
  signed; a visit not asked is read once, the request's PUT; a 404 ends it), `DemoBackendTest` (the demo visit and its signature).

## AI-11 · Consultation notes and scribe API

Built 2026-11-04. Spec 02 §7.1, ticket AI-11. No screen yet (APPT-03/04 use it).

- **Models** (`data/model/ConsultationModels.kt`): `ConsultationNote` (`MANUAL` or `AI_DRAFT`; `displayText` = the
  body without its transcript, else S/A/P), `DraftNote` with `IcdSuggestion`s, `CompleteRequest` (visit charges join
  with APPT-07), `CompleteResult`, `ScribeStatus`. Helpers ported: `joinedManualNotes`, `stripScribeTranscript` (cut
  at a line starting "TRANSCRIPT:"), `composeVisitDocumentationText` (Shifa AI's notes and drafts first, then the
  doctor's, each under its heading; an empty block left out).
- **Repository** (`data/consultation/ConsultationRepository.kt`): notes (read, save trimmed, blank saves nothing),
  drafts (read, confirm with the patient and visit, discard), `complete` (blank notes left out, `awaitingScribe` only
  when true), scribe status and `scribeUpdates`: every 3 s until the scribe's note is there or 120 s passed (a deadline from the start, slow reads included),
  cancelled with its collector (Flutter's wait couldn't be); a passing failure is tried again, a lasting one ends it.
  **Fix:** a failed read is a failure (Flutter showed an empty list, so a failed read looked like a visit without
  notes); one bad note doesn't drop the others.
- **Demo backend:** notes per visit in memory (one manual note, updated, as the backend upserts it; complete saves the
  doctor's notes first); the video visit (101) has one scribe draft (SOAP, an ICD suggestion,
  and a transcript the app hides) until it is saved or dropped; scribe status and complete answer from the notes.
- **Tests:** `ConsultationModelsTest` (3: Flutter's two tests ported, plus edges), `ConsultationRepositoryTest` (4:
  failures, bodies, the scribe poll, its deadline with a hanging read), `DemoBackendTest` (draft → note → complete).

## APPT-03 · Visit notes with autosave

Built 2026-11-04. Spec 05 §2.2, flow 05/05, ticket APPT-03. No screen yet (APPT-04 shows it).

- **`ui/visit/VisitNotes.kt`**, a state holder the visit's ViewModel owns: `SoapNotes` (four optional SOAP fields and
  the free notes) saved as **one body**, "Label\ntext" per filled field then the free notes (Flutter's format), with
  the labels the screen passes in the app's language (`SoapLabels`).
- **Autosave** 2 s after the last edit, and `flush()` at once when the screen leaves, switches mode or ends the
  visit (Flutter saved only then, so a crash lost the notes). Saves never overlap: an edit cancels only the wait, a
  save already sent runs to its answer before the next (so the server applies them in order); an edit made while
  saving stays unsaved and is saved by its own timer; a failed save keeps the text, marks it unsaved and says "Couldn't save the
  notes. They stay here; try again." Blank text isn't sent: the backend ignores it, so notes **can't be emptied**
  once saved (the backend keeps one manual note per visit and updates it).
- **Hydration:** the doctor's earlier notes (manual, joined) fill the free notes once, only while all five fields
  are empty; an edit before they arrive wins. A failed read says "Could not load earlier notes" (Flutter: silent).
- **Helpers and drafts:** `append` adds a helper's text after a blank line; `confirm` / `discard` a Shifa AI draft,
  one at a time per draft, then the notes and drafts are read again (only the newest read may write the lists).
- **Tests:** `VisitNotesTest` (5: hydrate once and never over typing; one save 2 s after the last edit with the
  labelled body; flush, a failed save, blank text, the cancelled timer; a confirmed draft re-reads the lists; an edit during a held save saved after it, in order).

## APPT-04 · Visit workspace

Built 2026-11-04. Flow 05/04 (and 05/05 for the notes), spec 05 §2.1–2.4, ticket APPT-04. Board `DaVisit` "VISIT
WORKSPACE" (phone: one column), "STAFF CANNOT RUN VISITS", "NOT FOUND", the notes menu and focus mode.

- **Opening** (`ui/visit/VisitViewModel.kt`, route `ShellRoute.Visit` from the calendar panel's Start / Continue):
  the visit (`GET /appointments/{id}`), then its patient, the patient's visits (the reason and the last completed
  visit before this one) and 025-2 forms; those extras can fail without blocking the visit. A dentist starts in the
  dental record (profession, on the first open only). The chronic-disease warning once per patient per session.
- **Screen** (`ui/visit/VisitScreen.kt`): header (back, name, status pill, age · ID, time · reason · place or Video
  call, the visit clock "Visit time" / "Starts in" ticking every second); patient chips (allergy, chronic disease,
  last visit or first visit; tapping opens the patient); the **notes card**: title by mode, focus mode (General), the
  notes menu (From Shifa AI, From last 025-2 form, the modes), the mode tabs (General Form, Dental visit record for
  dental professions, Form 025-2); the **helpers**: Shifa AI's drafts (text without the transcript, suggested ICD
  codes with Recommended and Apply, Discard / Save as note) and saved AI notes (carousel, + adds to notes), the last
  025-2's complaints / diagnosis / treatment (+ adds to notes); **General**: the SOAP expander and the free notes
  (APPT-03: autosaved, "Could not load earlier notes · Retry", a save error); **Form 025-2**: what the card is, the
  patient's forms (signed or not) and Open Form 025-2 (the editor, FORM-04); **documents** grouped Laboratory,
  Imaging, Clinical, Forms, Private, Other (the record's cards: open, request access), Upload (DOCS-03's sheets)
  and the finalize hint; the **footer**: Request Signature (saves the notes first), "Waiting for patient
  signature..." (read every 10 s, APPT-02) or "Patient Signed ✓".
- **Leaving** (back, system back, the patient chips, Open Form 025-2) saves the notes; when that fails: "Notes not
  saved" with Leave anyway / Stay. Going back stops the signature reads. A visit without a patient says "Patient
  record unavailable" instead of documents and upload.
  **Mode switch** saves the notes and switches: no Save / Discard question (the notes autosave, and the backend
  can't empty them, so "Discard and switch" couldn't undo anything saved). Clinic staff get "Visits are run by the
  doctor" (flow 01/18).
- **Apply** on an AI ICD suggestion switches to Form 025-2 and says "Applied: K04.0. Open Form 025-2 to finish the
  diagnosis." (the board's); carrying the code into the editor is APPT-08.
- **Not built (their tickets):** End appointment (APPT-07), the video pane (VIDEO-02; video visits open this same
  workspace), the dental record (DENTAL-02: the tab shows "on its way"), dictation mics (AI-05/06), Start AI Notes
  (SCRIBE-01), before/after photos (APPT-05), the treatment-plan panel (TP-06), tablet two panes (CAL-5's open call).
  The board's "open tasks" chip waits for M9.
- **Tests:** `VisitViewModelTest` (5: the visit with its patient, reason, last visit and forms, warned once; a
  dentist's mode and a missing visit; the signature after the notes; helpers and modes; leaving), screenshots
  `Visit_workspace`, `Visit_form0252`, `Visit_staff`.
  **Emulator (demo mode):** calendar → 6 Nov → Jasur Karimov → Start Appointment → the workspace ("Starts in",
  first visit, grouped documents); a typed note, Request Signature → "Waiting for patient signature..." → "Patient
  Signed ✓" about 15 s later.

## APPT-06 · Visit briefing sheet

Built 2026-11-04. Flow 05/02, spec 05 §7, ticket APPT-06. Board `DaVisit` "APPOINTMENT DETAILS" (the "Visit
briefing" row) and the "briefing" overlay.

- **Where:** the calendar's appointment details show a "Visit briefing" row when the patient attached documents or a
  briefing exists (not NONE / SKIPPED): its line says how many documents, "Generating…", or "Could not be generated
  · Retry". Home's button joins with HOME-03.
- **Sheet** (`ui/visit/Briefing.kt`): the subtitle; then a 3-line skeleton, "Generating briefing from attached
  documents…" (with "Still generating — you can close this and check back." after a minute), the briefing text
  (selectable; the markdown subset shows as plain text until the shared AI text renderer, `ponytail:`), the failed
  text, or the empty text; the meta line "{n} documents · generated {date time}" (practice time zone, new); the
  **attached documents** (this visit's, from the patient's list, whatever the briefing's state; tap opens the viewer); "AI summary — verify against
  the originals."; Copy (ready); Retry briefing (failed or pending, not after the visit; one at a time).
- **Reads** (`AppointmentsRepository.briefingUpdates`): every 3 s while being made, every 10 s once a minute has passed,
  ending when ready / failed / none or on a failed read; they stop when the sheet closes (Flutter's delayed callbacks
  kept firing); closing also cancels a Retry on its way.
- **Demo backend:** the video visit's briefing is ready (with the blood count attached to it), the clinic visit's
  failed; Retry makes it pending for 6 s, then ready.
- **Tests:** `AppointmentModelsTest` (the status mapping), `AppointmentsRepositoryTest` (3 s then 10 s, ends when
  ready), `BriefingViewModelTest` (2: retry once, this visit's documents only; a failed read), `DemoBackendTest`,
  screenshots `Briefing_ready`, `Briefing_failed`.

## AI-03 · Recorder level

Built 2026-11-04. Spec 02 §3.5, ticket AI-03 (the recorder itself is CORE-07's spike, `utils/VoiceRecorder`: AAC-LC
`.m4a`, 16 kHz mono 32 kbps, temp file deleted on stop).

- **`VoiceRecorder.level()`**, 0..1 for the recording pill's wave: Android `MediaRecorder.maxAmplitude` (the peak
  since the last read) as dBFS, iOS `AVAudioRecorder` metering (`averagePowerForChannel`); both through
  `levelFromDb` (Flutter's bar height: 0.08 at −55 dB and below, 1 at −8 dB and above; 0 when not recording).
  `recordingId` (made at start, sent with the transcription, AI-05) and `lastDurationSeconds`. The debug spike shows it every
  60 ms while recording. Flutter's web WAV path and "no amplitude" probe aren't needed (native always reports).
- **Checked:** the emulator records (33 KB `.m4a` in 2 s) and shows the bar, but its microphone has no input, so the
  level stays near 0; the level is still to be checked on a phone (Android and iOS).
- **Tests:** `VoiceLevelTest` (2: the dB mapping, new ids).

## AI-05 · Transcription

Built 2026-11-04. Spec 02 §7.2, ticket AI-05. No field yet (AI-06 uses it).

- **`data/speech/TranscriptionRepository.kt`:** `transcribe(audio, language, recordingId)` posts the `.m4a` as
  `file` (`audio/mp4`, named by the recording id) with `languageHint` (the app's `AppLanguage.backendCode`: en, uz
  or ru; Cyrillic Uzbek is uz) and `recordingId`, 90 s timeout; the answer's text is trimmed and its language lower-cased;
  `Transcript.empty` covers blank and "(No speech detected)". Failures stay typed for the field to say in the app's
  language: 403 (no PRO plan: `SPEECH_TO_TEXT`), 429 (daily AI limit), 502 (recognition failed); Flutter showed
  "error: <message>". `report(transcript, language, audio, recordingId)` for "Report" (404: no longer collected).
- **Public config:** `transcriptionFeedbackEnabled` ("true" or true) as a `Result`, read once per run (a failure
  too); Report shows only on a success that says true.
- **Demo backend:** a fixed sentence for any recording; reports are accepted.
- **Not checked against dev:** a transcription is a paid AI call; the request shape follows the backend's
  `DoctorAiController.transcribe` (`file`, `languageHint`, `recordingId`) and the demo.
- **Tests:** `TranscriptionRepositoryTest` (3: the multipart parts and trimmed answer, typed failures, the flag),
  `DemoBackendTest`.

## AI-06 · Speech-enabled text fields

Built 2026-11-04. Spec 02 §7.3, flow 05/05 §3, §5–§9, ticket AI-06. Board `DaVisit` general mode (the mic in each
field, the recording pill).

- **`ui/speech/Dictation.kt`** (a screen's state holder): one field records at a time (other mics wait), the live
  level every 60 ms and the time, ✕ drops the recording (nothing sent), ✓ transcribes it (AI-05, the app's language,
  the recording id) and appends it to the field that asked (`appendTranscript`: one space between, none after a space
  or a new line) through the screen's state, so text typed meanwhile stays (even when the field left the screen). Messages in the app's language: "Transcription
  added", "No speech detected", "Speech-to-text requires a Pro subscription." (403), "You've reached your daily AI
  usage limit…" (429), "Transcription timed out" / "Speech recognition failed" (Flutter: "error: <message>"),
  "Microphone permission denied", "Error recording voice"; Report under the field when the backend collects reports
  (sends the transcript and the recording; a failure says so and keeps Report, 404 hides it). Focus mode shows the
  messages too; the SOAP section doesn't collapse while one of its fields records.
- **`ui/speech/SpeechTextField.kt`:** the mic in the field (only when the plan includes `SPEECH_TO_TEXT`, followed as
  the profile loads; asks for
  the microphone first), the dark recording pill under it (✕, a 40-bar wave, the time, ✓ or a spinner while
  transcribing). `SpeechOrPlainField` + `LocalDictation`: a screen provides one dictation for its fields; previews
  get plain fields.
- **Where:** the visit's SOAP fields, free notes and focus mode (APPT-04/03); the 025-2 editor's note fields
  (FORM-04: complaints, anamnesis, examination, treatment…; the one-line fields have no mic). Chat (CHAT-06) next.
- **Not built:** the "Dictation needs PRO · Learn more" line for BASIC (the mic is simply hidden), a 5-minute cap,
  insertion at the cursor (always appended, as Flutter), treating a phone-call interruption as ✓.
- **Emulator (demo mode):** visit → mic in the notes → recording pill with its time → Process → "Transcription
  added" and the demo sentence in the notes (the emulator's microphone records silence).
- **Tests:** `DictationTest` (5: a failed report kept, one field at a time and the appended text with Report; no speech / 403 / 429 messages
  and nothing added; ✕ sends nothing, a recorder that won't start; the spacing rule), screenshot `Speech_recording`.

## SCRIBE-01 · AI scribe recording

Built 2026-11-04. Flow 05/06, spec 05 §2.2, ticket SCRIBE-01. Board `DaVisit` "AI scribe".

- **Start AI Notes** under the free notes (PRO: `AI_NOTES`; without it the button shows locked, "AI notes need
  PRO"). It asks for the microphone, then "Recording for AI Notes", the recording pill (AI-06's: ✕, live wave, the
  time, ✓) and "Stop when you are done — pauses and filler words are fine." It is a second dictation with its own
  recorder (`Dictation.finishWith` hands the audio over instead of transcribing it).
- **✓** uploads the recording (`ConsultationRepository.uploadRecording`: `audio` as `ai_notes_<recording id>.m4a`,
  `appointmentId`, `language` = the app's backend code; 180 s timeout) with a spinner in the pill; then "Recording
  uploaded. Notes will be ready in a few minutes." and the visit is marked **awaiting the scribe** (End, APPT-07,
  sends `awaitingScribe` and waits, SCRIBE-02); the button reads "Start AI Notes again". 429: "You've reached your
  daily AI usage limit…", 403: "AI notes need PRO", else "Couldn't upload the recording. It is kept: try again." with
  **Upload the recording again** (Flutter lost the audio).
- **Background:** on Android the recorder starts a foreground service of type microphone (`utils/RecordingService`,
  notification "Recording the consultation") for as long as it records (dictation too), so the consultation keeps
  recording with the screen off or another app open (Android 9+ gives a background app silence); checked on the
  emulator. iOS records in the background with its `audio` mode (not run yet). No upload overlaps another (an
  uploading flag; the retry button busy meanwhile). The screen wrapper owns the scribe's recorder; the content gets
  its state and callbacks.
- **From Shifa AI** now reads the notes and drafts again before it opens (the scribe's draft arrives while the visit
  is open; flow 05/06 §6 target).
- **Fix found on the way:** `retryScribe` first ran the upload inside a state update, whose lambda can run more than
  once; it uploaded twice. Caught by the test.
- **Not built:** the scribe in the video call (VIDEO-02), the leave-while-recording question and a duration cap (flow
  §7–8 targets), "a recording is already processing" (the backend accepts a second one and ignores it).
- **Tests:** `ConsultationRepositoryTest` (the upload's parts), `DictationTest` (`finishWith`), `VisitViewModelTest`
  (upload, the limit kept for a retry, sent again once; From Shifa AI reads again), `DemoBackendTest` (an upload gives
  the visit a draft). **Emulator (demo mode):** Start AI Notes → recording 0:05 → Process → "Recording uploaded…",
  "Start AI Notes again" → From Shifa AI shows the scribe's draft with K04.0.

## PDF-01 · Visit summary document

Built 2026-11-04. Spec 05 §6, §9, ticket PDF-01; flow 05/13. The backend should render it (BE-01, the better long-term
answer); until then it is made in the app, as the 025-2 is (FORM-03).

- **`data/consultation/VisitSummaryPdf.kt`:** portrait A4, 40 pt margins: the header (SHIFA, the clinic, when the visit
  ended, "Appointment ID {id}", a rule), the "Appointment Summary" band, the patient and visit block (name, patient
  id, date of birth, gender, doctor · profession, visit type, duration), **Clinical notes** (the documentation text:
  Shifa AI's block first, then the doctor's; flowing across pages, split between words), **Signatures** (the
  doctor's line; the patient's signature image with "Signed electronically on {date} via the SHIFA Patient App", or
  an empty line; the block is kept together on one page) and on every page the footer "Generated by Shifa · Confidential" and "Page n / N". Labels come in
  the app's language (`VisitSummaryLabels`, built by the screen): en, uz and ru (Flutter: en and uz). Flutter's
  never-filled sections (diagnosis, prescriptions, recommendations, follow-up) are left out; the dental billing and the
  plan join with DENTAL-02/03, before/after photos with APPT-05.
- **`utils/PdfFlow.kt`:** the text flow (pages, page breaks, section bands, paragraphs split between words) moved out
  of the 025-2 layout so both share it (its six tests unchanged and passing).
- **Not wired yet:** End appointment (APPT-07) makes and uploads it.
- **Checked:** the debug spike's sample (Cyrillic and Latin, 3 pages, 69 KB) on the emulator, pulled and rendered.
- **Tests:** `VisitSummaryPdfTest` (3: the signatures never split from their band; the order, the footer, no signature; long notes over pages with page numbers,
  nothing under the margin, the signature image and date), `Form0252PdfTest` (after the move).

## APPT-07 + SCRIBE-02 · End appointment

Built 2026-11-04. Flow 05/12, spec 05 §2.3, tickets APPT-07 and SCRIBE-02 (the scribe wait is a step of the same
pipeline, so both are in one change). Board `DaVisit` "end visit" and "end progress".

- **Footer:** End Appointment (destructive) beside the signature, while the visit is open.
- **Sheet** (`ui/visit/EndVisit.kt`): "End appointment", "{patient} · {time} · visit time {n} min", what will be
  saved: Notes ({n} characters / None), AI notes (Ready / Still processing — up to 2 min / None), Signature (Signed /
  Requested, not signed / Not requested); "Nothing to save…" when so; with a signature awaited, the hint, **Wait for
  signature** and **End anyway**, else Back to notes and End appointment. Dental services, plan items, payment and
  photos join with DENTAL-02/03 and APPT-05.
- **Pipeline** (`VisitViewModel.end`): Saving notes (the autosave flushed; a failure stops, the visit stays open) →
  Completing visit (`PUT …/complete` with the notes and `awaitingScribe`; a failure stops: "Couldn't end the
  appointment. It is still open: try again.") → **Waiting for AI notes (m:ss)** when a recording went to the scribe
  or the server says it is pending (every 3 s, at most 120 s, **Continue without waiting**; SCRIBE-02, cancellable,
  Flutter's dialog wasn't) → Saving summary: the visit, notes and drafts read again, the documentation text composed
  (Shifa AI first), the summary PDF made (PDF-01) and filed under the patient (`APPOINTMENT_NOTE`, "Appointment
  documentation {date} {time}", with the visit's id). Nothing written: "No items to save. Appointment ended." (or "AI
  documentation will appear shortly" while the scribe works), no PDF. Runs once at a time; the sheet can't be closed
  while it runs.
- **After completion only the summary is retried:** "Visit completed. Summary could not be saved." with **Retry
  summary** (re-read and summary again; the visit isn't completed twice) and Close, which stays on the visit with
  **Retry summary** in its footer (Flutter left the doctor stuck on a completed visit). Notes that can't be read again
  fail the summary rather than leave them out; text typed but not yet on the server is used. The calendar's day of the visit and today are read again once it is completed.
- **Done:** "Appointment Ended", the steps checked, "Appointment ended. Documentation saved."; **Done** goes back with
  that notice, **Open Summary** opens the PDF in the viewer.
- **Not built:** Book follow-up (flow 05/14, its own ticket), plan ticks and payment steps (DENTAL-03 / TP-06), a
  saved "summary job" retried on the next launch, offline disabling of End.
- **Tests:** `VisitViewModelTest` (+6: an unreadable re-read and the footer's Retry summary; the whole pipeline in order and the calendar day; nothing to save; a failed
  complete then a failed summary retried alone; the scribe step skipped), screenshots `End_confirm`, `End_progress`,
  `Visit_workspace` / `Visit_form0252` (the End button). **Emulator (demo mode):** a typed note → End Appointment →
  the checklist (13 characters, AI notes none, not requested) → End appointment → the steps → "Appointment ended.
  Documentation saved." → Open Summary shows the filed PDF (Tashkent time) → Done returns with the notice.

## APPT-08 · An AI ICD code into the 025-2

Built 2026-11-04. Spec 05 §5, ticket APPT-08. The board's 025-2 mode opens the editor (FORM-04) rather than embedding
it; this carries what the embedding was for.

- **Apply** on a Shifa AI ICD suggestion switches to Form 025-2 and keeps the code: the mode shows "ICD-10 to apply:
  K04.0 Pulpitis" (× drops it) and says "Applied: K04.0. It is filled in when you open Form 025-2."
- **Open Form 025-2** passes it in the route (`ShellRoute.Form(…, icd)`); the editor fills a **new** form's ICD-10
  code, title and system with it (as a search pick would), unsaved until the doctor saves. An existing form is left
  as it is.
- **Not built:** the in-place embedded editor with Save & Switch (the board doesn't embed it).
- **Tests:** `FormEditorViewModelTest` (a preset code on a new form, not on an existing one), `VisitViewModelTest`
  (Apply keeps the code and switches mode).

## DENTAL-01 · Tooth map and dental record

Built 2026-11-04. Spec 05 §4.1–4.2, ticket DENTAL-01, board `DaVisit` "TOOTH MAP" (`.dr-teeth`, `.dr-tooth`).

- **Record** (`data/model/DentalModels.kt`): `DentalRecord` is the `/appointments/{id}/dental-documentation` payload,
  version 2: dentition, service lines per FDI tooth key (and `__general__`), discount percent, notes.
  `DentalRecord.read` accepts any saved version: legacy keys ("UR1") become FDI through `DentalChartCodec`, a bad line
  is skipped and the rest are kept, a discount sent as text is read as a number, and `{}` means no record. `payload()`
  leaves out empty teeth and trims the notes, as Flutter does.
- **Totals per currency:** `subtotals` / `totals`. Flutter summed only the first currency's lines and silently dropped
  the rest (05 §10). The discount is clamped to 0–100 and the total rounds half up, as Dart's `round()` does.
- **Tooth map** (`ui/visit/ToothMap.kt`): the upper jaw 18→11 | 21→28 and the lower 48→41 | 31→38 (primary teeth
  55→51 | 61→65, 85→81 | 71→75), with a gap between quadrants. Each tile shows the tooth number at the root side and
  a service-count badge.
  - **Free mode:** teeth with services are filled.
  - **Plan mode** (`states`): teeth show as planned, partly done (half filled) or done.
  - The selected tooth has a focus border. On a phone the tiles are small, so all 16 fit; from 560 dp they are wider,
    and the map scrolls sideways if even that doesn't fit.
  - Each tooth has a screen-reader description. With no `onTap` the map is read-only.
- **Accessibility:** each tooth's screen-reader text includes its count and plan state, and the selected tooth is
  announced as selected.
- **Not in the board, so not built:** Flutter's anatomy captions (English only) and quadrant numerals. The dentition
  toggle goes in the DENTAL-02 panel.
- **Not built:** the panel that uses them (DENTAL-02), plan states fed from a treatment plan (DENTAL-03).
- **Tests:** `DentalModelsTest`: legacy keys (a key and its FDI twin are one tooth), bad lines, odd metadata and a text
  discount read; payload with a clamped discount; totals per currency with
  half-up rounding. Screenshots `ToothMap` (free and plan modes, primary teeth) and `ToothMap_wide`.

## DENTAL-02 · Dental visit record (free mode)

Built 2026-11-04. Spec 05 §4.3, ticket DENTAL-02, board `DaVisit` dental mode and "dental services picker". API:
`GET/PUT /appointments/{id}/dental-documentation` (checked against dev's OpenAPI and a real GET: `{}` when empty) and
`GET /doctors/me/services`.

- **Where:** a dentist's visit opens in **Dental visit record** mode (05 §2.4). It shows the intro, an Adult / Primary
  teeth toggle (Flutter's; the board has none), the tooth map with its legend (No services / Services added), the
  General / non-tooth services tile with its count, Discount (%), Clinical notes (dictation, AI-06) and the totals.
- **Services sheet:** opens from a tooth tap or the General tile. It lists the lines already added (each removable),
  group chips (All plus the doctor's groups) and the active services. Tapping a service adds it at once. The price is
  Flutter's: the first positive price, a free consultation at 0. **Save** closes the sheet. With no services it says
  "Define services under Services & Pricing first." (the board's "Add service" link isn't built: the visit has no
  route to the profile).
- **Totals per currency:** "Subtotal: 450 000 UZS + 25.00 USD · 3 line items", the discount, "Total due: …", UZS
  first. Flutter silently dropped lines in a second currency (05 §10). A discount over 100 shows "Enter a discount
  between 0 and 100." and is saved as 100.
- **Autosave** (`DentalNotes`): 1200 ms after the last change, and at once when the visit leaves, switches mode or
  ends. It shares `Autosave` with the notes (`VisitNotes` now uses it too): one save at a time, and a save already
  sent runs to its answer. The payload is Flutter's version 2 byte for byte (DENTAL-01). **Fix:** Flutter saved an
  empty record over one it failed to read. Here nothing can be edited until the record has been read: only `{}` means
  "no record yet"; any other unreadable body (also teeth that aren't lists by tooth) is a failure, shown as "Could not
  load dental record" with Retry. One bad line inside a tooth's list is still skipped.
- **Shifa AI / last 025-2 "Add to notes"** in Dental mode go to the clinical notes and stay in Dental mode (Flutter's).
- **End appointment:** in the Saving notes step a record not read yet is read first (a failure stops before
  completion with "Could not load dental record", so the summary never leaves it out), then saved (a failure stops
  with "Could not save dental documentation"). The checklist gains "Dental services: 2 line items · 393 750 UZS". The summary PDF
  gains "DENTAL VISIT — Procedures by tooth": tooth, service and amount per line (chart order, General last), the
  subtotal, discount and total per currency, then the clinical notes; labels are in the app's language. A visit with
  only dental work still gets its summary.
- **Not built:**
  - Plan mode, fulfilment and the finance card (DENTAL-03).
  - Filling the dental notes from the General notes, as Flutter did. It would print them twice in the summary.
  - The board's "Add service" link to Services & Pricing.
- **Tests:**
  - `DentalNotesTest`: load and catalogue order, the 1200 ms payload, no edits before a successful read, flush and
    failure, discount over 100.
  - `VisitViewModelTest`: a dentist's End saves the record before completing, and Add to notes goes to the dental
    notes; an unreadable record stops End before completion, and the next End goes through.
  - `VisitSummaryPdfTest`: the dental section, a discount over 100 printed as 100 %.
  - `VisitNotesTest` still passes on the shared `Autosave`.
  - Screenshots: `Visit_dental`, `Dental_services`, `End_confirm` (the dental row).
  - **Emulator (demo mode, the demo doctor switched to Dentist):** Dental mode by default; tooth 36 → Cleaning +
    Consultation → badge 2 and the totals; discount 12,5 and notes; left the visit and opened it again: all read back
    from the saved record; End shows the dental row; the summary PDF shows the dental section (393 750 UZS). Demo
    mode is off again.

## APPT-05 · Before/after photos

Built 2026-11-05. Spec 05 §2.2 and the APPT-05 ticket, flow 05/04 §4 and 05/12, board `DaVisit` (notes menu,
"photo source", photo chips). The ticket's document upload strip already exists: the workspace's Documents card
uploads with progress, a per-file failure and a refresh (APPT-04, `DocumentUpload`).

- **Adding a photo:** the notes menu has **Before Treatment** and **After Treatment** in General and Dental modes.
  Each opens Take Photo / Choose from Gallery with the hint "Added to the visit summary as a separate page." The
  pickers already compress (`PickedImage`: JPEG 85, longest side 1600 px). A refused camera shows the existing
  "Camera access is needed" banner with Open settings.
- **Chips:** "Before Treatment · photo.jpg" under the notes, one line with an ellipsis, each with a remove button that
  has a screen-reader label.
- **Kept until End:** photos stay in the visit's ViewModel until End, as in Flutter and on the board, at most 20 per
  visit ("Up to 20 photos per visit."). A picker's answer goes to the kind it was opened for, and only one picker can
  be open at a time. **Fix:** Flutter lost photos silently on back. Here, going back while the visit has photos and no
  filed summary asks first ("Photos not saved yet… If you leave now, they are lost." with Leave anyway / Stay). That
  includes after a failed summary. Going back clears them; opening the patient or the 025-2 keeps them.
- **End:** the checklist has a "Photos: 2" row. Photos alone are enough for a summary. The summary PDF gets one page
  per photo after the signatures, titled Before / After Treatment in the app's language, the photo fitted to the page.
- **Not built:** uploading photos as separate patient documents (the spec's backend-PDF variant, BE-01). The board
  puts them in the summary.
- **Tests:**
  - `VisitViewModelTest`: add, cancel and remove; a picker keeps its kind; leaving asks; photos alone make the
    summary, in order; a filed summary leaves without asking and clears them; the 20 cap; a failed summary still asks;
    the patient exit doesn't.
  - `VisitSummaryPdfTest`: one page each, with its label and page number.
  - Screenshots: `Visit_photoSource`, `End_confirm` (the Photos row), `Visit_workspace` (chips below the fold).
  - **Emulator (demo):** Before from the gallery, After from the emulator camera; both chips shown; Back asked; End
    showed Photos 2; the summary had 3 pages, the photos on pages 2 and 3. Demo mode is off again.

## TP-01 · Treatment plans data (pulled forward for the visit)

Built 2026-11-05. Spec 09 §4.5, §6–§8, ticket TP-01; only the part the visit (DENTAL-03, TP-06) needs. Endpoints
checked against dev's OpenAPI. Dev has no treatment plans (`GET /treatment-plans?clinicId=2` is `[]`), and a plan
can't be deleted once made, so the shapes are tested with fixtures and the demo backend only.

- **Models** (`data/model/TreatmentPlanModels.kt`): `TreatmentPlanSummary`, `PlanLine`, `TreatmentPlanDetail`,
  `FulfillmentCandidate`, and the `PlanStatus` / `LineStatus` / `PlanKind` enums (unknown values are `Unknown`, not a
  crash).
  - `PlanLine.tooth` reads the FDI key from the line's `specialtyMetadata` JSON string.
  - The detail reads the nested `summary` or an older flat one. One unreadable line is skipped, not the plan.
- **Repository** (`data/treatmentplans/TreatmentPlansRepository.kt`): `plans(clinicId, patientId, status, planKind)`,
  `detail`, `candidates(planId, appointmentId)` and `fulfill`.
  - Every failure is a failure. Flutter returned null or `[]`, so an error looked like "no plans".
  - Nothing is cached yet: the visit reads a patient's plans once when it opens. The ticket's caches come with the
    plan screens (TP-02), scoped to the session and backend so another account or backend can't see them.
  - One unreadable plan, line or attending doctor is skipped, not the whole list or plan.
- **Selected clinic:** `clinicsRepository.selected()` is the first membership, Flutter's default. ponytail: choosing
  and remembering a clinic comes with CLINIC-01.
- **Demo:** Jasur Karimov (12) has "Treatment of 36 and 46" in the demo clinic (1 only): root canal and crown on 36, a
  filling on 46 (done), cleaning (general). Candidates and fulfil keep their state in memory; with every line done the
  plan is COMPLETED (09 §4.5) and leaves the active list.
- **Not built (their tickets, M11):** visits, free slots, doctor locations, plan services, the wizard's writes and the
  status PATCH (TP-02…05).
- **Tests:**
  - `TreatmentPlansRepositoryTest`: the query, an odd plan or doctor skipped, a failure not empty; the detail nested
    and flat, line teeth and unknown statuses; candidates and the fulfil body.
  - `DemoBackendTest`: the demo plan in its clinic only, candidates, fulfil, completion.

## DENTAL-03 + TP-06 · The treatment plan in the visit

Built 2026-11-05. Spec 05 §4.3 plan mode and §5, tickets DENTAL-03 and TP-06; flows 05/08 and 05/09; board
`DaVisit` (treatment plan panel, dental plan mode, "plan fulfil", "Plan finances", End checklist). Endpoints checked
against dev's OpenAPI: `GET /treatment-plans…`, `…/detail`, `…/fulfillment-candidates`, `POST …/fulfill`, and
`POST /clinics/{id}/finance/payments`. Dev has no plans, so this ran in demo mode only (TP-01).

- **Panel** (`PlanPanel`, under the mode tabs, while the visit is open and the patient has active plans in the
  doctor's clinic):
  - "Part of plan #501 — …" when the visit is already linked to a plan.
  - "Treatment plan for this visit" with a picker: None / bill separately, then the plans. Untitled plans show as
    "(untitled plan) #id".
  - The progress "1/4 planned items completed".
  - In Dental mode, "Tick planned items on the teeth chart below."; in other modes a dentist gets **Open the dental
    record to tick items**.
  - The linked plan, else the only one, is chosen at open. Two or more without a link: none is chosen.
  - A failed read shows "Could not load treatment plans · Retry". Flutter showed "None" silently.
- **Plan mode** (Dental mode with a plan, `PlanChart`):
  - The chart is coloured by the plan's lines: planned, partly done, completed. A tick counts as done.
  - Cancelled lines are left out. Flutter coloured them "partly done".
  - No count badges (the board's). The screen-reader text says the count and the state.
  - The legend, and General / non-tooth services "(done/total)".
  - Banners: all items done; no open lines; lines failed to load with Retry.
  - The free-mode services, discount and totals are hidden. The clinical notes stay.
  - A tooth with no lines says "No open planned lines for this tooth."
- **Fulfil sheet** (`PlanFulfilSheet`): the tooth's lines with "1 × 800 000 UZS · Planned".
  - A line completed earlier shows checked and greyed and can't be changed.
  - Only the visit's open lines (the candidates) can be ticked.
  - Save closes the sheet; nothing is sent until End.
- **Plan finances** (`PlanFinance`):
  - Plan total, Paid and Outstanding, formatted with `Money`. Flutter showed "500000.00 UZS".
  - The payment for this visit: an "Amount (UZS)" field and a Method choice (Cash / Card (terminal) / Transfer).
    Amounts like "500 000" are parsed; Flutter silently recorded nothing for them.
  - "Enter an amount greater than 0" and a "More than the outstanding balance" warning.
  - "Recorded when you end the appointment.", then "Recorded: …" once End has recorded it.
- **End:**
  - Order: Saving notes → **Applying treatment plan…** (the ticks; a failure stops before completion: "Could not
    apply treatment plan changes. Appointment was not completed.") → **Recording payment…** (once per visit; a
    failure stops the same way) → Completing visit → ….
  - A second End sends neither the applied ticks nor a recorded payment again.
  - A failure whose answer may have been lost is checked first:
    - Ticks count as applied only if the appointment now lists them as fulfilled; lines another visit completed are
      dropped.
    - A payment counts as recorded only if a new ledger row (this visit, plan, amount, method) appeared since the
      attempt. Until then its amount and method are held.
    - A clear refusal (4xx) releases the payment.
  - Once ticks were applied or a payment was sent, the plan can't be swapped for another.
  - The checklist shows "Treatment plan items: 2 to mark completed / None ticked / No plan linked" and "Payment:
    500 000 UZS · Cash".
  - The summary PDF gets a "Treatment plan" section: the title, total / paid / outstanding (read again after the
    payment), the lines completed this visit by tooth, and the payment with its method. Ticks or a payment alone are
    enough for a summary. Flutter's builder existed three times; this is the one section (TP-06).
- **Leaving:** ticks and a typed payment, like photos, live only in the open visit. Going back with any of them asks
  ("Not saved yet… Photos, ticked plan items and the payment are saved when you end the appointment.").
- **Decisions:**
  - Ticks and the payment are sent at End, as on the board ("Recorded when you end the appointment."), not when they
    are made as 17-master-plan suggested. Duplicate payments are guarded per visit; the backend's idempotency key
    (BE-13) is still wanted for retries after a crash.
  - Choosing another plan drops the ticks and amount typed for the first, as the board does (no confirm).
- **Not built:** a "Record now" button; extra lines not in the plan; a checklist for non-dental plan visits; the
  "Summary pending" retry in the appointment details; installments.
- **Tests:**
  - `VisitPlanTest`: auto-choice (only, linked, none); a failed read; tickable lines and tooth states; ticks applied
    once with the failure kept; payment parsing ("-100" invalid), over outstanding, recorded once with the failure
    kept; a lost payment answer found in the ledger, not posted twice, the plan and method held; a 4xx releases it;
    lost fulfil answers reconciled with the appointment's lines; switching plans drops ticks.
  - `VisitViewModelTest`: End order fulfil → payment → complete; a failed payment stops before completion and the
    retry doesn't re-apply ticks; leaving asks; the summary's plan part.
  - `VisitSummaryPdfTest`: the plan section.
  - `DemoBackendTest`: plan payments and the ledger.
  - Screenshots: `Visit_dentalPlan`, `Plan_fulfil`.
  - **Emulator (demo, the demo doctor as Dentist):**
    - The plan was chosen at open, with 46 completed.
    - 36 → Root canal ticked → 36 partly done. 21 → "No open planned lines". General → cleaning ticked.
    - 500 000 Cash typed. The checklist showed "2 to mark completed" and "500 000 UZS · Cash".
    - End ran Applying treatment plan → Recording payment → Completing → Saving summary.
    - The summary showed the plan (paid 1 000 000 → 1 500 000), the two lines and the payment.
    - Demo mode is off again.

## DENTAL-04 · Dental plan editor and read-only plan chart

Built 2026-11-05. Spec 05 §4.4, ticket DENTAL-04; Flutter's `dental_plan_editor_panel.dart`,
`dental_plan_readonly_view.dart`, `test/dental_plan_editor_test.dart`. The hosts come later: the plan wizard (TP-04)
holds the editor, and the plan detail (TP-03) shows the read-only chart. Here they are built and tested on their own.

- **Editor** (`PlanEditor`, drawn by `DentalPanel(editor = true)`): the same free-mode record as the visit (DENTAL-02):
  dentition, tooth map, services sheet, general services, discount, notes, totals. It uses the clinic's plan services
  (`GET /treatment-plans/plan-services`, checked on dev), and its labels are "Plan procedures on the teeth chart…",
  "Plan notes" and "Planned total". It keeps the record until the host saves it, which takes:
  - `documentation()`: the `dentalPlanDocumentation` JSON (version 2).
  - `lines()`: the plan lines (`PlanLineRequest`).
- **Shared code instead of copies:**
  - The visit record (`DentalNotes`) and the plan editor share `DentalDraft`: edit, add, remove, dentition, discount
    and notes. The visit adds loading and autosave.
  - The services sheet now takes `CatalogEntry`s (the doctor's services or the plan services).
  - The plan's tooth states are one function, `planToothStates`, used by the visit (DENTAL-03) and the read-only
    chart.
- **Lines** (`DentalRecord.planLines`, Flutter's `buildLineRequests`):
  - Teeth in chart order, general last. Each line is `{fdi, dentition, source: PLAN_WIZARD}` (no `fdi` for a general
    line), quantity 1, PLANNED.
  - The catalogue item comes from the line, or else from its key in the plan services.
  - The discount is spread in proportion, with the rounding on the last line. What a line can't take (its price is
    less than its share) goes to the lines with room.
  - Fixes Flutter's: untitled lines are left out before spreading (Flutter could drop the remainder with them); each
    currency gets its own share (blank counts as UZS); no line gets more discount than its price.
  - One rounding for the plan total shown and the lines: the discount rounds a half up and the total is the subtotal
    less it. Flutter rounded the two differently, so the total shown could be one unit off the charge.
- **Read-only chart** (`DentalPlanReadonly`): the progress "1/4 planned items completed", the chart coloured by the
  lines' states (no count badges, no taps; the screen reader says each tooth's count and state) and the legend.
- **Search:** above 8 services the services sheet has a search by title or code, with "No services match your
  search.".
- **Not built:** Flutter's wide two-column picker; the group chips serve instead. The board's faint halo around a selected tooth: the selected tooth has the focus border only.
- **Tests:**
  - `PlanEditorTest`: Flutter's fixture (two lines with tooth and catalogue item, 30 000 discount in all); spreading
    per currency within prices, chart order, untitled lines; a clamped line's leftover, blank currency, totals equal
    to the lines; the editor's services, record JSON and lines; the shared
    tooth states.
  - `DemoBackendTest`: plan services.
  - Screenshots: `Plan_editor`, `Plan_readonly`.

## VIDEO-01 · Daily token and call client

Built 2026-11-05. Spec 05 §3.1, ticket VIDEO-01. The call client is the patient app's (`utils/VideoCall*.kt`,
`iosApp/iosApp/DailyVideoCall.swift`, both on Daily 0.40), copied rather than shared, as the workspace rules require.

- **Token** (`data/video/VideoRepository.kt`): `POST /video/token {appointmentId}` → `{token, roomUrl, roomName}`
  (checked on dev's OpenAPI). On dev, a refused request answers a bare `403 FORBIDDEN`: two real refusals, for a
  completed and a cancelled visit, gave the same answer, with the reason only in English text, which the app never
  shows.
  - A 403 doesn't end the session (the app-wide rule). A 401 does, unlike Flutter's "no-401" call: a doctor whose
    token stopped working is signed out everywhere else too.
- **Why a room can't be joined** (`videoJoinError`, from the backend's `VideoController`):
  - 402 PAYMENT_REQUIRED: "Payment is required…".
  - A 403 is explained by the visit's own window, matching the backend's. More than 5 minutes before the start: "Video
    call is not yet available…". More than 15 minutes after the end: "Video call has ended…".
  - A 403 inside the window (not this doctor's visit) and anything else: "Call error occurred".
  - BE-28 still asks for coded refusals.
- **Call client** (`utils/VideoCall`): join, leave, camera, microphone, flip and its state (phase, camera and mic on,
  patient joined, failed). `VideoTile` shows the patient's or the doctor's own camera; `rememberCallPermissions`
  asks for camera and microphone.
  - Android: `co.daily:client`.
  - iOS: Daily's Swift package (`daily-client-ios` 0.40, added to the Xcode project) behind `NativeVideoCall`,
    registered in `AppDelegate`.
  - The loudspeaker is preferred, as in the patient app. No screen share (Flutter's toggle did nothing).
  - Logging: a fixed "FAILED Daily call / join" line, in debug builds only, never the SDK's text (it can carry the
    room or the token). The patient app's copy still logs the SDK text; noted for that repo.
- **Demo:** the video visit (101) gets a room token. The demo has no Daily room, so a join fails as a real call
  error would. Other visits are refused with a bare 403.
- **Not run:** a real join. It needs a video visit inside its window on dev, and minting a token creates a Daily room
  that can't be undone there (open question 2 in the overnight report). The client is the patient app's, which runs
  in production. The screen is VIDEO-02.
- **Builds:** `assembleDebug` with `co.daily:client`; the whole iOS app with `xcodebuild` for the simulator (Daily
  0.40.0 resolved into `Package.resolved`).
- **Tests:** `VideoRepositoryTest` (the request, a refusal as Forbidden, 402 as payment, the window messages at
  their edges, a 403 inside the window, a network error); `DemoBackendTest` (the demo room and refusals).

## VIDEO-02 + VIDEO-03 · Video visit: pre-join page and the call in the workspace

Built 2026-11-05. Spec 05 §3.2–3.3, tickets VIDEO-02 and VIDEO-03; flows 05/10 and 05/11; board `DaVisit` "VIDEO
PRE-JOIN" and the video pane. A video visit (route kind Video) is the same workspace as one in person (APPT-04), with
the call on top.

- **The call lives in the visit's ViewModel** (`VideoVisit`), so it survives opening the patient's record or the 025-2
  mid-call. It is freed when the doctor goes back from the visit, ends it (End leaves the call first, flow 05/12) or
  when the ViewModel goes.
- **Pre-join page** (`PreJoin`, new UI per the board):
  - The doctor's camera preview with Flip; Camera and Microphone switches; the patient and the time; Join call and
    Cancel.
  - The status: "Video call ready", "Connecting…", or why the room can't be joined (VIDEO-01's messages).
  - Camera and microphone are asked for on open and read again on every return to the app, so a permission granted in
    Settings counts at once. Only the microphone is needed: without the camera the call goes on with it off. Without
    the microphone: "Microphone permission denied. The patient cannot hear you without it." with Open settings, and
    Join stays off.
  - Joining keeps the doctor's choices: a camera or microphone turned off before joining stays off.
- **In the workspace** (`VideoPane`, the first card):
  - Connecting; alone ("Waiting for {name} to join…" over the doctor's own camera); the patient full size with their
    name and the doctor's camera in a corner.
  - Controls: microphone, camera (each shows its off state), flip, and leave (red).
  - **Leave call** asks first ("The visit stays open until you end it."), and so does Back while joining or in the
    call. Then "You left the call" with **Rejoin**. Leaving never completes the visit; Flutter's red button didn't
    either, but said nothing.
  - A call that drops shows the same state with "Video call ended" (or "Call error occurred") and Rejoin.
  - The token request is cancelled by Leave, Back and End, so a late answer never joins. A Rejoin makes its new call
    only once the token is there.
  - The screen stays awake while joining and in the call (`KeepScreenOn`: Android `keepScreenOn`, iOS
    `idleTimerDisabled`).
- **VIDEO-03 (waiting room): not built**, per D-07 (the spec's recommendation). Flutter's page was unreachable, and
  the backend has no patient-presence endpoint.
- **Not built:** the visit facts panel (AI, its own ticket); Flutter's notes full-screen overlay (focus mode covers it);
  the scribe recorder stays offered on video visits too (Flutter had none there, 05 §10.16).
- **Not run:** a real call. It needs a dev video visit inside its window (minting a token creates a Daily room).
  The demo's video visit is tomorrow, so its Start is still off. The pre-join page, the pane and the leave state are
  covered by screenshots.
- **Builds:** `assembleDebug`, the iOS framework, and the whole iOS app with `xcodebuild` for the simulator.
- **Tests:**
  - `VideoVisitTest` (fake call):
    - Allowed → preview → token → join (the microphone kept off) → in the call when Daily says joined.
    - A refused room explained, and a failed Daily join, both back on the pre-join page.
    - Leave frees the call; a refused Rejoin makes no call; Rejoin makes a new one; release only once.
    - Without the camera the call goes on with it off, and the microphone granted in Settings counts.
    - A dropped call says so; Back while the token is on its way joins nothing.
  - Screenshots: `Video_preJoin`, `Video_inCall`, `Video_left`.
