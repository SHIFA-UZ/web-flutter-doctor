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
