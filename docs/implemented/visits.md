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
