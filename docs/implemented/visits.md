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
