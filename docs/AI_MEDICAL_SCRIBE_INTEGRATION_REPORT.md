# AI Medical Scribe Integration — Technical Architecture Report

**Document:** Technical analysis for integrating AI note-taking and automatic consultation summaries  
**Scope:** Doctor Web Application (Flutter + Kotlin backend)  
**Date:** March 2025  
**Status:** Analysis only — no implementation

---

## Executive Summary

This report analyzes the Doctor Web Application codebase to identify how AI medical scribe functionality could be integrated for two scenarios:

1. **Video Call Appointments** — Record/capture Daily.co conversations and auto-summarize into structured clinical notes  
2. **Face-to-Face Appointments** — "Start AI Notes" button to record via microphone and summarize into clinical notes

The analysis covers appointment architecture, video call infrastructure (Daily.co), media handling, backend APIs, notes system, storage, security, and integration opportunities.

---

## 1. Appointment Flow

### 1.1 Appointment Model & Schema

**Frontend (Flutter):**
| File | Model | Key Fields |
|------|-------|------------|
| `lib/features/appointments/domain/appointment_models.dart` | `Appointment` | `id`, `patientName`, `patientId`, `location`, `start`/`end` (TimeOfDay), `status`, `photoUrl` |
| Status enum | `requested`, `confirmed`, `cancelled`, `completed` | — |
| Type detection | `isVideo` | `location.toLowerCase().contains('video')` |

**Backend (Kotlin):**
| File | Table | Key Fields |
|------|-------|------------|
| `src/main/kotlin/com/shifa/domain/Appointment.kt` | `appointments` | `id`, `doctor_id`, `patient_id`, `start_at`, `end_at` (Instant/UTC), `location`, `reason`, `status`, `signature_requested`, `patient_signature_image`, `patient_signed_at` |
| Status enum | `REQUESTED`, `CONFIRMED`, `CANCELLED`, `COMPLETED` | — |

### 1.2 Appointment Lifecycle

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        APPOINTMENT LIFECYCLE                                     │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                  │
│  Calendar Screen                    Waiting Room              Video / In-Person  │
│  (calendar_screen.dart)             (waiting_room_screen)     (video_call /      │
│         │                                    │                 in_person_screen) │
│         │  Select appointment                │  "Open room"         │           │
│         ├──────────────────────────────────►│                     │           │
│         │                                    │  Navigate            │           │
│         │                                    ├─────────────────────►│           │
│         │                                    │                      │           │
│         │                                    │                      │  Doctor   │
│         │                                    │                      │  conducts │
│         │                                    │                      │  consult  │
│         │                                    │                      │           │
│         │                                    │                      │  "End     │
│         │                                    │                      │  Appt"   │
│         │                                    │                      │     │     │
│         │                                    │                      │     ▼     │
│         │                                    │                      │  _endAppointment() │
│         │                                    │                      │  - Save PDF       │
│         │                                    │                      │  - Upload doc     │
│         │                                    │                      │  - PUT /complete  │
│         │                                    │                      │  - Pop to shell    │
│                                                                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Appointment Types & Entry Points

| Type | Detection | Entry Point | Screen |
|------|-----------|-------------|--------|
| **Video** | `location` contains "Video" | Calendar → Waiting Room → "Open room" | `VideoCallScreen` |
| **In-person** | `location` = clinic name | Calendar → Direct | `InPersonAppointmentScreen` |

### 1.4 Key Files & Functions

| Component | File Path | Key Functions |
|-----------|-----------|---------------|
| Calendar list | `lib/features/calendar/presentation/calendar_screen.dart` | `_loadDay`, `_entriesFor`, `onChanged` (day selection) |
| Calendar state | `lib/state/calendar/calendar_controller.dart` | `loadDay`, `bookFreeSlotRemote`, `cancelAppointment` |
| Waiting room | `lib/features/appointments/presentation/waiting_room_screen.dart` | "Open room" → navigates to VideoCallScreen |
| Video call | `lib/features/appointments/presentation/video_call_screen.dart` | `_initializeVideoCall`, `_endAppointment`, `_endVideoCall` |
| In-person | `lib/features/appointments/presentation/in_person_appointment_screen.dart` | `_endAppointment`, `_fetchSignatureStatus` |
| Today list | `lib/features/appointments/application/today_appointments_provider.dart` | Fetches today's appointments for Home |
| Router | `lib/app/router.dart` | `AppRoutes.videoCall`, `AppRoutes.inPerson`, `AppRoutes.waitingRoom` |

### 1.5 Appointment Start/End Flow

**Start:**
- Doctor selects appointment from Calendar or Today list
- Video: Calendar → Waiting Room → "Open room" → VideoCallScreen
- In-person: Calendar → InPersonAppointmentScreen

**End (`_endAppointment`):**
1. **Video:** `_endVideoCall()` first (leave Daily room)
2. Build `AppointmentPdfData` from notes, before/after images
3. Generate PDF via `generateAppointmentPdf`
4. Upload via `uploadPatientDocumentWithClient` → `POST /api/patients/{patientId}/documents`
5. `PUT /api/appointments/{id}/complete`
6. Invalidate providers, pop to shell

---

## 2. Video Call Architecture

### 2.1 Daily.co Integration Overview

| Layer | File | Purpose |
|-------|------|---------|
| Backend config | `application.yml`, `application-prod.yml` | `daily.apiKey`, `daily.apiUrl` |
| Backend service | `src/main/kotlin/com/shifa/service/DailyVideoService.kt` | Room creation, token generation |
| Backend controller | `src/main/kotlin/com/shifa/web/VideoController.kt` | `POST /api/video/token` |
| Frontend service | `lib/core/services/daily_video_service.dart` | Calls backend for token |
| Web embed | `lib/features/appointments/presentation/daily_video_embed_web.dart` | iframe with `roomUrl?t=token` |
| Mobile | `daily_flutter` package | `CallClient`, `join()`, `leave()` |
| Stub (web) | `lib/core/services/daily_flutter_stub.dart` | No-op for web (uses iframe) |

### 2.2 Room Creation & Token Flow

```
Doctor App                    Backend                      Daily.co API
    │                            │                              │
    │  POST /api/video/token      │                              │
    │  { appointmentId }         │                              │
    ├──────────────────────────►│                              │
    │                            │  GET /rooms/{roomName}        │
    │                            ├─────────────────────────────►│
    │                            │  (if 404) POST /rooms        │
    │                            │  RoomProperties(enable_recording: false)
    │                            │◄─────────────────────────────┤
    │                            │  generateToken()             │
    │                            ├─────────────────────────────►│
    │  { token, roomUrl }        │                              │
    │◄──────────────────────────┤                              │
    │  Join (iframe or CallClient)                              │
```

### 2.3 Room Properties (DailyVideoService.kt)

```kotlin
data class RoomProperties(
    val exp: Long? = null,
    val enable_chat: Boolean = true,
    val enable_screenshare: Boolean = true,
    val enable_recording: Boolean = false,   // ← Recording disabled
    val max_participants: Int = 2
)
```

### 2.4 Call Start/End (Frontend)

| Platform | Implementation | File |
|----------|----------------|------|
| **Web** | `DailyVideoEmbedWeb` iframe: `roomUrl?t=token`, `allow="camera; microphone; display-capture"` | `daily_video_embed_web.dart` |
| **Mobile** | `CallClient` from `daily_flutter`; `join(roomUrl, token)`; `leave()` on end | `video_call_screen.dart` |

### 2.5 Recording Status

- **Current:** `enable_recording: false` in `RoomProperties` (DailyVideoService.kt:58, 171)
- **Webhooks:** None configured for Daily.co (recording-complete, participant-left, etc.)
- **Enabling recording:** Change `enable_recording` to `true`; Daily.co supports cloud recording; webhooks needed for post-call processing

### 2.6 API Keys & Config

| Config | Location | Env Var |
|--------|----------|---------|
| API Key | `DailyProperties` | `DAILY_API_KEY` |
| API URL | `DailyProperties` | `DAILY_API_URL` (default `https://api.daily.co/v1`) |
| Domain | Hardcoded in DailyVideoService | `shifauz.daily.co` |

---

## 3. Daily.co Integration (Detailed)

### 3.1 File Paths

| File | Purpose |
|------|---------|
| `c:\shifa-doctor-backend\src\main\kotlin\com\shifa\service\DailyVideoService.kt` | Room get/create, token generation |
| `c:\shifa-doctor-backend\src\main\kotlin\com\shifa\config\DailyProperties.kt` | API key/URL config |
| `c:\shifa-doctor-backend\src\main\kotlin\com\shifa\web\VideoController.kt` | Token endpoint, join window validation |
| `c:\shifa_doc_app_v1\lib\core\services\daily_video_service.dart` | Frontend token fetch |
| `c:\shifa_doc_app_v1\lib\features\appointments\presentation\daily_video_embed_web.dart` | Web iframe embed |
| `c:\shifa_doc_app_v1\lib\features\appointments\presentation\daily_video_embed_stub.dart` | Stub for non-web |
| `c:\shifa_doc_app_v1\lib\features\appointments\presentation\video_call_screen.dart` | Video UI, init, end |

### 3.2 Recording Enablement

- **Easy change:** Set `enable_recording: true` in `RoomProperties`
- **Daily.co cloud recording:** Requires Daily.co plan with recording; recordings stored by Daily; webhook `recording.completed` provides download URL
- **Where to capture:** Backend webhook endpoint to receive `recording.completed` → download audio/video → send to STT/AI pipeline

### 3.3 Webhook Opportunities

- Daily.co supports webhooks for: `recording.started`, `recording.completed`, `participant.joined`, `participant.left`, `meeting.ended`
- **Current:** No webhook handlers in backend
- **Integration point:** Add `POST /api/webhooks/daily` (or similar) to receive recording URLs and trigger AI processing

---

## 4. Media Handling

### 4.1 Audio Recording (Existing)

| Use Case | Library | File | Format |
|----------|---------|------|--------|
| Chat voice messages | `record` | `lib/features/chat/presentation/widgets/voice_recording_dialog.dart` | Web: WAV 16kHz mono; Mobile: AAC-LC 44.1kHz |
| Permission | `AudioRecorder.hasPermission()` | Same | Before start |

**Voice flow:**
1. `_VoiceRecordingDialog` → `_audioRecorder.start(RecordConfig(...), path: ...)`
2. `onRecordingComplete(filePath, durationSeconds)` → parent
3. Chat uploads via `POST /api/messages/upload-attachment`
4. `sendMessageWithClient` with `type: 'voice'`, `attachmentUrl`, `duration`

### 4.2 Microphone Capture

- **Video:** Daily.co iframe `allow="camera; microphone; display-capture"` — no direct app access to call audio
- **Chat:** `record` package — full control for standalone recording
- **In-person:** No current microphone capture; would need new "Start AI Notes" flow using `record` or similar

### 4.3 Image Capture

- **Library:** `image_picker`
- **Usage:** Video and in-person screens for before/after treatment images
- **Storage:** Included in PDF, uploaded as patient document

### 4.4 Media Upload Endpoints

| Endpoint | Controller | Purpose |
|----------|------------|---------|
| `POST /api/patients/{patientId}/documents` | PatientDocumentController | PDFs, images |
| `POST /api/messages/upload-attachment` | ChatAttachmentController | Chat attachments, voice |

---

## 5. Notes System

### 5.1 Where Doctors Write Notes

| Location | File | Mode |
|----------|------|------|
| Video call | `video_call_screen.dart` | General notes (`_notesController`) + 025-2 form + AI consultation carousel |
| In-person | `in_person_appointment_screen.dart` | Same |

### 5.2 Notes Panel Modes

- **General:** Free-text `TextField` + before/after images + AI consultation notes carousel
- **025-2:** `AppointmentForm0252Panel` — structured dental form (complaints, diagnosis, treatment)

### 5.3 Consultation Notes Schema (Backend)

**Table:** `consultation_notes` (V42 migration)

| Column | Type | Description |
|--------|------|-------------|
| id | BIGSERIAL | PK |
| doctor_id | BIGINT | FK doctor_profiles |
| patient_id | BIGINT | FK patient_profiles |
| appointment_id | BIGINT | FK appointments (nullable) |
| ai_draft_note_id | UUID | FK ai_draft_notes (nullable) |
| subjective | TEXT | SOAP S |
| assessment | TEXT | SOAP A |
| plan | TEXT | SOAP P |
| body | TEXT | Full note |
| created_at | TIMESTAMPTZ | |
| source | VARCHAR(32) | `MANUAL` \| `AI_DRAFT` |

**Entity:** `src/main/kotlin/com/shifa/domain/ConsultationNote.kt`

### 5.4 AI Draft Notes Schema

**Table:** `ai_draft_notes`

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | PK |
| doctor_id | BIGINT | FK |
| patient_id | BIGINT | Nullable |
| consultation_id | BIGINT | Nullable |
| ai_response_text | TEXT | AI output |
| ai_label | VARCHAR(255) | |
| status | VARCHAR(32) | GENERATED, CONFIRMED, DISCARDED |
| model_version, prompt_version | VARCHAR(64) | |

### 5.5 Notes APIs

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/appointments/{id}/consultation-notes` | Fetch notes for appointment |
| POST | `/api/ai/stream` | SSE stream AI response → creates ai_draft_notes |
| POST | `/api/ai/draft/{id}/confirm` | Confirm draft → creates consultation_notes with source=AI_DRAFT |
| POST | `/api/ai/draft/{id}/discard` | Discard draft |

### 5.6 AI Flow (Current)

1. Doctor asks Shifa AI (text prompt) → `POST /api/ai/stream`
2. Backend streams via SSE; creates `ai_draft_notes` row
3. Doctor confirms → `POST /api/ai/draft/{id}/confirm` with `patientId`, `appointmentId` → creates `consultation_notes`
4. Notes shown in appointment panel with "From Shifa AI" badge

**Provider:** `consultationNotesForAppointmentProvider` in `consultation_notes_provider.dart`

---

## 6. Backend APIs

### 6.1 Appointment Endpoints

| Method | Path | Controller | Purpose |
|--------|------|------------|---------|
| GET | `/api/appointments/{id}` | AppointmentController | Single appointment (signature status) |
| GET | `/api/appointments/{id}/consultation-notes` | AppointmentController | Consultation notes |
| PUT | `/api/appointments/{id}/request-signature` | AppointmentController | Request patient signature |
| PUT | `/api/appointments/{id}/complete` | AppointmentController | Mark completed |
| PUT | `/api/appointments/{id}/change` | AppointmentController | Change slot |
| DELETE | `/api/appointments/{id}` | AppointmentController | Cancel |

### 6.2 Files/Media Endpoints

| Method | Path | Controller | Purpose |
|--------|------|------------|---------|
| GET | `/api/patients/{patientId}/documents` | PatientDocumentController | List documents |
| POST | `/api/patients/{patientId}/documents` | PatientDocumentController | Upload document |
| PUT | `/api/patients/{patientId}/documents/{documentId}` | PatientDocumentController | Update |
| GET | `/api/patients/{patientId}/documents/{documentId}/download` | PatientDocumentController | Download |
| POST | `/api/messages/upload-attachment` | ChatAttachmentController | Chat attachment (incl. voice) |

### 6.3 AI Endpoints

| Method | Path | Controller | Purpose |
|--------|------|------------|---------|
| POST | `/api/ai/stream` | DoctorAiController | SSE AI stream |
| POST | `/api/ai/draft/{id}/confirm` | DoctorAiController | Confirm draft → consultation note |
| POST | `/api/ai/draft/{id}/discard` | DoctorAiController | Discard draft |
| GET | `/api/ai/draft/{id}` | DoctorAiController | Get draft |

### 6.4 Video Endpoints

| Method | Path | Controller | Purpose |
|--------|------|------------|---------|
| POST | `/api/video/token` | VideoController | Get Daily.co token |
| GET | `/api/video/room/{roomName}` | VideoController | Room info |

### 6.5 Database Tables (Relevant)

| Table | Migration | Purpose |
|-------|------------|---------|
| appointments | V1 | Appointments |
| patient_documents | V1 | Document metadata |
| consultation_notes | V42 | Confirmed notes |
| ai_draft_notes | V42 | AI drafts before confirm |

---

## 7. Storage

### 7.1 Current Implementation

| Storage Type | Path | Service | Served At |
|--------------|------|---------|-----------|
| Patient documents | `{storageRoot}/patientdocuments/{patientId}/` | PatientDocumentStorageService | `/patientdocuments/{patientId}/{filename}` |
| Chat attachments | `{storageRoot}/chat-attachments/` | ChatAttachmentController | `/chat-attachments/{filename}` |
| Doctor photos | `{storageRoot}/doctors/` | DoctorPhotoStorageService | `/doctors/{filename}` |
| Patient photos | `{storageRoot}/patients/` | PatientPhotoStorageService | `/patients/{filename}` |

### 7.2 Configuration

| Env | Default | File |
|-----|---------|------|
| storageRoot (dev) | `./public-storage/images` | application.yml |
| storageRoot (prod) | `/app/storage/images` | application-prod.yml |
| STORAGE_ROOT | Override | Env var |

### 7.3 Storage Type

- **Current:** Local filesystem / Railway volume
- **Planned:** AWS S3 (see `PRODUCTION_STORAGE_REMINDER.md`, `FILE_STORAGE_SUMMARY.md`)
- **Recordings:** Not yet stored; Daily.co would store recordings if enabled; backend would need to download and optionally re-store

---

## 8. Notification / Event System

### 8.1 FCM (Firebase Cloud Messaging)

- **Service:** `com.shifa.service.FcmService`
- **Usage:** `sendPatientNotification()`, `sendToToken()` for appointment events
- **Event types:** `SIGNATURE_REQUESTED`, `APPOINTMENT_CANCELLED`, `APPOINTMENT_CHANGED`

### 8.2 Schedulers

- **ReminderNotificationScheduler** — appointment reminders
- **AiDraftNoteCleanupScheduler** — cleanup old AI drafts

### 8.3 Queues & Background Jobs

- No message queue (RabbitMQ, Kafka) in codebase
- No generic background job framework (e.g. Celery, Bull)
- AI processing is synchronous (SSE stream)

### 8.4 Webhooks

- No Daily.co webhooks
- No generic webhook handlers
- **Gap:** No async pipeline for post-call AI processing

---

## 9. Security & Compliance

### 9.1 Authentication

- **Doctors:** Firebase phone OTP → JWT from `/api/auth/login`
- **Patients:** Same flow
- **Backend:** JWT via `JwtAuthFilter`; `DoctorPrincipal` / `PatientPrincipal`

### 9.2 Authorization (RBAC)

| Role | Endpoints |
|------|-----------|
| DOCTOR | `/api/patients/**`, `/api/calendar/**`, `/api/appointments/**`, `/api/doctors/me/**`, `/api/schedule/**`, `/api/tasks/**` |
| PATIENT | `/api/patients/me/**`, `/api/tasks/my-tasks/**` |
| ADMIN | `/api/admin/**` |
| Shared (authenticated) | `/api/notifications/**`, `/api/ai/**`, `/api/video/**` |

### 9.3 Patient Data Protection

- **Documents:** `ensurePatientAccess()` — doctor must have appointment or be creator
- **Document access:** `DocumentAccessService` — request/grant for locked documents
- **Appointments:** Doctor/patient access scoped to own data

### 9.4 Compliance Notes

- No explicit HIPAA/GDPR implementation in code
- Daily.co HIPAA BAA mentioned in docs
- Patient context sent to OpenAI when patient selected (see SHIFA_AI_IMPLEMENTATION_AUDIT.md)
- **Recordings:** Would contain PHI; require consent, encryption, access controls

---

## 10. Integration Opportunities

### 10.1 Video Call Scenario

| Capability | Status | Integration Point |
|------------|--------|-------------------|
| Enable Daily.co recording | Not enabled | Set `enable_recording: true` in DailyVideoService |
| Receive recording URL | Not implemented | Add webhook endpoint for `recording.completed` |
| Download recording | N/A | Backend downloads from Daily URL after webhook |
| Transcribe audio | Not implemented | New service: STT (e.g. Whisper, Google STT) |
| Summarize to notes | Partially | Extend `OpenAiResponsesService` or new scribe service |
| Save as consultation note | Exists | `POST /api/ai/draft/{id}/confirm` or new endpoint to create `consultation_notes` with `source=AI_DRAFT` |
| Link to appointment | Exists | `consultation_notes.appointment_id` |

**Suggested flow:**
1. Enable Daily.co recording; configure webhook URL
2. Add `POST /api/webhooks/daily` to receive `recording.completed`
3. Background job: download recording → STT → LLM summarization → create `ai_draft_notes` or `consultation_notes`
4. Doctor reviews/edits in existing notes panel; confirms to save

### 10.2 Face-to-Face Scenario

| Capability | Status | Integration Point |
|------------|--------|-------------------|
| "Start AI Notes" button | Not implemented | Add to `InPersonAppointmentScreen` (and optionally VideoCallScreen) |
| Microphone recording | Exists (chat) | Reuse `record` package; new flow for consultation |
| Upload audio | Exists | `POST /api/messages/upload-attachment` or new `POST /api/consultations/upload-recording` |
| Transcribe | Not implemented | Same STT service as video |
| Summarize | Partially | Same LLM pipeline |
| Save as note | Exists | Same as video |

**Suggested flow:**
1. Add "Start AI Notes" button → start `record` (similar to voice chat)
2. "Stop" → upload audio to new or existing endpoint
3. Backend: STT → LLM → create `ai_draft_notes` with `appointmentId`
4. Doctor sees draft in notes carousel; confirms to save as `consultation_notes`

### 10.3 Shared Components

- **Consultation notes schema:** Already supports `source=AI_DRAFT`, `appointment_id`, SOAP fields
- **AI draft flow:** Confirm/discard pattern exists; can be reused for scribe drafts
- **Document storage:** Patient documents for PDFs; could add `consultation_recordings/` for raw audio if needed

---

## 11. Potential Risks

| Risk | Mitigation |
|------|-------------|
| **Recording consent** | Implement explicit consent before recording; document in UI |
| **PHI in recordings** | Encrypt at rest; restrict access; audit logs |
| **Daily.co costs** | Recording and storage may incur extra cost; verify plan |
| **STT accuracy** | Medical terminology; consider medical-specific models |
| **Latency** | Post-call processing is async; set expectations (e.g. "Notes ready in 2–5 min") |
| **Webhook reliability** | Idempotency; retries; dead-letter handling |
| **No background queue** | May need to add (e.g. Spring `@Async`, or job queue) for reliable processing |

---

## 12. Suggested Integration Points

### 12.1 Architecture Diagram (Proposed)

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                         AI MEDICAL SCRIBE — PROPOSED FLOW                          │
├──────────────────────────────────────────────────────────────────────────────────┤
│                                                                                   │
│  VIDEO CALL                          FACE-TO-FACE                                │
│  ┌─────────────┐                     ┌─────────────┐                             │
│  │ Daily.co    │                     │ "Start AI   │                             │
│  │ room        │                     │  Notes"     │                             │
│  │ (recording  │                     │  [record]   │                             │
│  │  enabled)   │                     │  package    │                             │
│  └──────┬──────┘                     └──────┬──────┘                             │
│         │                                    │                                     │
│         │ recording.completed                │ Upload audio                        │
│         ▼                                    ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐                     │
│  │              Backend Webhook / Upload Endpoint            │                     │
│  └─────────────────────────────┬───────────────────────────┘                     │
│                                │                                                   │
│                                ▼                                                   │
│  ┌─────────────────────────────────────────────────────────┐                     │
│  │  Background Pipeline:  Download → STT → LLM Summarize   │                     │
│  └─────────────────────────────┬───────────────────────────┘                     │
│                                │                                                   │
│                                ▼                                                   │
│  ┌─────────────────────────────────────────────────────────┐                     │
│  │  Create ai_draft_notes (appointment_id, patient_id)      │                     │
│  │  OR consultation_notes (source=AI_DRAFT)                 │                     │
│  └─────────────────────────────┬───────────────────────────┘                     │
│                                │                                                   │
│                                ▼                                                   │
│  ┌─────────────────────────────────────────────────────────┐                     │
│  │  Doctor reviews in existing notes panel → Confirm/Discard │                     │
│  └─────────────────────────────────────────────────────────┘                     │
│                                                                                   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

### 12.2 File-Level Hook Points

| Component | File | Hook |
|-----------|------|------|
| Video room config | `DailyVideoService.kt` | Set `enable_recording: true`; add webhook URL to room |
| Webhook receiver | New: `DailyWebhookController.kt` | `POST /api/webhooks/daily` |
| In-person UI | `in_person_appointment_screen.dart` | Add "Start AI Notes" button; reuse `record` |
| Video UI | `video_call_screen.dart` | Optional: same button for local backup recording |
| Notes display | `consultation_notes_provider.dart` | Already shows AI drafts; no change needed |
| Confirm flow | `DoctorAiController.kt` / `AiDraftNoteService` | Extend for scribe drafts with `appointmentId` |

### 12.3 New Backend Components (Conceptual)

- **Recording webhook handler** — Receive Daily.co events
- **Recording download service** — Fetch from Daily URL
- **STT service** — Transcribe audio (Whisper API, Google STT, etc.)
- **Scribe LLM service** — Summarize transcript to SOAP/structured format
- **Background job** — Orchestrate: webhook → download → STT → LLM → save draft
- **Upload endpoint** — For in-person audio: `POST /api/consultations/upload-recording` (or similar)

---

## Appendix: Key File Paths Summary

| Category | Path |
|----------|------|
| Appointment model (FE) | `lib/features/appointments/domain/appointment_models.dart` |
| Appointment model (BE) | `src/main/kotlin/com/shifa/domain/Appointment.kt` |
| Calendar screen | `lib/features/calendar/presentation/calendar_screen.dart` |
| Video call screen | `lib/features/appointments/presentation/video_call_screen.dart` |
| In-person screen | `lib/features/appointments/presentation/in_person_appointment_screen.dart` |
| Daily embed (web) | `lib/features/appointments/presentation/daily_video_embed_web.dart` |
| Daily service (BE) | `src/main/kotlin/com/shifa/service/DailyVideoService.kt` |
| Video controller | `src/main/kotlin/com/shifa/web/VideoController.kt` |
| Voice recording | `lib/features/chat/presentation/widgets/voice_recording_dialog.dart` |
| Consultation notes | `src/main/kotlin/com/shifa/domain/ConsultationNote.kt` |
| AI draft notes | `src/main/kotlin/com/shifa/domain/AiDraftNote.kt` |
| Doctor AI controller | `src/main/kotlin/com/shifa/web/DoctorAiController.kt` |
| Patient document storage | `src/main/kotlin/com/shifa/service/PatientDocumentStorageService.kt` |
| Consultation notes provider | `lib/features/appointments/application/consultation_notes_provider.dart` |

---

*End of report. No implementation has been performed.*
