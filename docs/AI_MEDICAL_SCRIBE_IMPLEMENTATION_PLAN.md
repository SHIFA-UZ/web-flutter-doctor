# AI Medical Scribe — Technical Implementation Plan

**Document:** Step-by-step implementation plan for AI medical scribe integration  
**Reference:** [AI_MEDICAL_SCRIBE_INTEGRATION_REPORT.md](./AI_MEDICAL_SCRIBE_INTEGRATION_REPORT.md)  
**Date:** March 2025  
**Status:** Design only — no implementation

---

## Executive Summary

This plan describes how to implement an AI medical scribe for two scenarios:

1. **Video Call** — Daily.co recording → webhook → transcription → AI summary → `ai_draft_notes`
2. **In-Person** — Doctor presses "Start AI Notes" → audio recording → upload → transcription → AI summary → `ai_draft_notes`

Existing systems are reused: `ai_draft_notes`, `consultation_notes`, `DoctorAiController`, `OpenAiResponsesService`, `consultation_notes_provider`.

---

## 1. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                    AI MEDICAL SCRIBE — END-TO-END ARCHITECTURE                                │
├─────────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                               │
│  SCENARIO 1: VIDEO CALL                          SCENARIO 2: IN-PERSON                       │
│  ┌─────────────────────┐                       ┌─────────────────────┐                      │
│  │ Daily.co room        │                       │ InPersonAppointment │                      │
│  │ (recording enabled)  │                       │ Screen              │                      │
│  │                      │                       │ "Start AI Notes"    │                      │
│  └──────────┬──────────┘                       │ [record package]    │                      │
│             │                                  └──────────┬──────────┘                      │
│             │ recording.ready-to-download                 │                                  │
│             ▼                                             │ multipart audio                  │
│  ┌─────────────────────┐                                  │                                  │
│  │ DailyWebhookController│                                  │                                  │
│  │ POST /api/webhooks/  │                                  │                                  │
│  │      daily           │                                  │                                  │
│  └──────────┬──────────┘                                  │                                  │
│             │                                             ▼                                  │
│             │                                  ┌─────────────────────┐                      │
│             │                                  │ ConsultationController│                     │
│             │                                  │ POST /api/consultations│                     │
│             │                                  │      /upload-recording│                     │
│             │                                  └──────────┬──────────┘                      │
│             │                                             │                                  │
│             └─────────────────────┬───────────────────────┘                                  │
│                                   │                                                          │
│                                   ▼                                                          │
│  ┌─────────────────────────────────────────────────────────────────────────────────────┐    │
│  │                    ScribePipelineService (@Async)                                     │    │
│  │  ┌──────────────┐   ┌──────────────────┐   ┌──────────────────┐   ┌─────────────┐  │    │
│  │  │ ScribeRecord- │   │ Transcription-   │   │ MedicalSummary-  │   │ AiDraftNote │  │    │
│  │  │ ingService    │──►│ Service (Whisper)│──►│ Service (LLM)    │──►│ Service     │  │    │
│  │  │ (download)    │   │                  │   │ (SOAP prompt)   │   │ createDraft │  │    │
│  │  └──────────────┘   └──────────────────┘   └──────────────────┘   └─────────────┘  │    │
│  └─────────────────────────────────────────────────────────────────────────────────────┘    │
│                                   │                                                          │
│                                   ▼                                                          │
│  ┌─────────────────────────────────────────────────────────────────────────────────────┐    │
│  │  ai_draft_notes (doctorId, patientId, consultationId=appointmentId, ai_response_text)│    │
│  │  consultation_notes_provider fetches → Doctor reviews → Confirm/Discard (existing)   │    │
│  └─────────────────────────────────────────────────────────────────────────────────────┘    │
│                                                                                               │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. New Backend Services

### 2.1 ScribePipelineService

**Package:** `com.shifa.service`  
**File:** `ScribePipelineService.kt`

**Responsibilities:**
- Orchestrate the full pipeline: recording input → transcription → summarization → draft creation
- Invoke `ScribeRecordingService`, `TranscriptionService`, `MedicalSummaryService`, `AiDraftNoteService` in sequence
- Run asynchronously via `@Async` to avoid blocking webhook/upload handlers
- Handle failures at each stage (log, optionally retry, mark pipeline as failed)
- Accept context: `appointmentId`, `doctorId`, `patientId`, and either (a) `recordingDownloadUrl`/`s3Key` for video, or (b) `localFilePath` for in-person upload

**Key methods:**
```kotlin
@Async
fun processVideoRecording(roomName: String, recordingId: String, s3Key: String?, downloadUrl: String?)

@Async  
fun processInPersonRecording(appointmentId: Long, doctorId: Long, patientId: Long, localFilePath: Path)
```

**Dependencies:** ScribeRecordingService, TranscriptionService, MedicalSummaryService, AiDraftNoteService, AppointmentRepository

---

### 2.2 ScribeRecordingService

**Package:** `com.shifa.service`  
**File:** `ScribeRecordingService.kt`

**Responsibilities:**
- Download recording from Daily.co (or S3) to a temporary local file
- Extract audio from video if needed (Daily cloud recordings can be `cloud-audio-only` or `cloud`; prefer `cloud-audio-only` for STT)
- Store temporarily under `{storageRoot}/scribe-temp/{uuid}.{ext}` 
- Delete temp file after pipeline completes (or on failure after a delay)
- Support two input types:
  - **Video:** HTTP download from Daily recording URL, or S3 GetObject using `s3_key` from webhook
  - **In-person:** File already on disk from multipart upload

**Key methods:**
```kotlin
fun downloadFromUrl(url: String): Path
fun downloadFromS3(bucket: String, key: String): Path  
fun ensureAudioFormat(inputPath: Path): Path  // Convert to format Whisper accepts (e.g. mp3, wav)
fun cleanupTempFile(path: Path)
```

**Configuration:** `ScribeProperties` — temp dir, S3 bucket (if used), retention minutes

---

### 2.3 TranscriptionService (Speech-to-Text)

**Package:** `com.shifa.service`  
**File:** `TranscriptionService.kt`

**Responsibilities:**
- Call OpenAI Whisper API to transcribe audio to text
- Accept audio file (mp3, wav, m4a, etc. — Whisper supports many formats)
- Return plain text transcript

**Request/Response structures:**
```kotlin
// Input: Path to audio file
// Output: transcript text

data class TranscriptionResult(
    val transcript: String,
    val language: String?,
    val durationSeconds: Double?
)

fun transcribe(audioPath: Path): TranscriptionResult
```

**Implementation:** Use OpenAI Whisper API `POST https://api.openai.com/v1/audio/transcriptions` with `model=whisper-1`, `file` (multipart), optional `language`, `response_format=json`.

**Configuration:** Reuse `OpenAiProperties` for API key; add `whisperModel` if needed (default `whisper-1`).

---

### 2.4 MedicalSummaryService

**Package:** `com.shifa.service`  
**File:** `MedicalSummaryService.kt`

**Responsibilities:**
- Take raw transcript and produce structured SOAP-style clinical notes
- Use OpenAI Chat Completions (same as `OpenAiResponsesService`) with a dedicated scribe prompt
- Return JSON with `subjective`, `assessment`, `plan`, `body` — compatible with `StructuredNoteParser` and `ConsultationNote`

**Prompt design:**
```
System: You are a medical scribe. Convert the following consultation transcript into structured 
SOAP clinical notes. Output valid JSON only: {"subjective":"...","assessment":"...","plan":"...","body":"..."}.
Do not diagnose or prescribe. Preserve medical terminology. Be concise.
User: [transcript]
```

**Request/Response:**
```kotlin
data class ScribeSummaryRequest(
    val transcript: String,
    val language: OutputLanguage = OutputLanguage.EN
)

data class ScribeSummaryResult(
    val subjective: String?,
    val assessment: String?,
    val plan: String?,
    val body: String
)

fun summarize(request: ScribeSummaryRequest): ScribeSummaryResult
```

**Implementation:** Non-streaming Chat Completions call; parse JSON response; validate structure; fallback to `body`-only if parse fails.

---

### 2.5 Service Dependency Graph

```
ScribePipelineService
    ├── ScribeRecordingService
    ├── TranscriptionService
    ├── MedicalSummaryService
    ├── AiDraftNoteService (existing)
    └── AppointmentRepository
```

---

## 3. Daily Webhook Handling

### 3.1 Endpoint

**Method:** `POST`  
**Path:** `/api/webhooks/daily`  
**Controller:** `DailyWebhookController` (new)  
**File:** `src/main/kotlin/com/shifa/web/DailyWebhookController.kt`

**Authentication:** None (webhooks are called by Daily.co). Use **webhook signature verification** (Daily sends `Daily-Webhook-Signature` header) to ensure authenticity.

### 3.2 Event: `recording.ready-to-download`

Daily.co sends this when a recording is finished and ready for download.  
**Note:** Event type is `recording.ready-to-download`, not `recording.completed`.

**Payload structure (from Daily.co docs):**
```json
{
  "version": "1.0",
  "type": "recording.ready-to-download",
  "id": "evt_xxx",
  "event_ts": 1234567890,
  "payload": {
    "type": "cloud" | "cloud-audio-only" | "raw-tracks",
    "recording_id": "rec_xxx",
    "room_name": "appointment-123",
    "start_ts": 1234567890,
    "status": "finished",
    "max_participants": 2,
    "duration": 300,
    "s3_key": "recordings/room/rec_xxx.mp4"
  }
}
```

### 3.3 Extracting Fields

| Field | Location | Purpose |
|-------|----------|---------|
| `room_name` | `payload.room_name` | Map to appointmentId |
| `recording_id` | `payload.recording_id` | For Daily API lookup if needed |
| `s3_key` | `payload.s3_key` | Download from S3 (if user configures S3 in Daily) |
| `type` | `payload.type` | Prefer `cloud-audio-only` for STT |

**Download URL options:**
1. **Daily Recording API:** `GET /recordings/{recording_id}` may return a `download_url` — verify in Daily docs.
2. **S3:** If Daily is configured with user's S3 bucket, use `s3_key` + AWS SDK to download.
3. **Daily-hosted:** Some Daily plans provide a direct download URL — check recording object.

**Implementation:** Support configurable strategy (S3 vs Daily API) via `ScribeProperties`.

### 3.4 Mapping `room_name` → `appointmentId`

**Convention (from VideoController.kt:178):**
```kotlin
val roomName = request.roomName ?: "appointment-${request.appointmentId}"
```

So `room_name` format is `appointment-{appointmentId}` (e.g. `appointment-42`).

**Parsing:**
```kotlin
fun parseAppointmentIdFromRoomName(roomName: String): Long? {
    return roomName.removePrefix("appointment-").toLongOrNull()
}
```

**Lookup:** Use `AppointmentRepository.findById(appointmentId)` to get `doctorId`, `patientId`. If appointment not found or cancelled, skip processing and log.

---

### 3.5 Webhook Controller Flow

```
1. Receive POST body (JSON)
2. Verify Daily-Webhook-Signature (if configured)
3. Parse type from payload
4. If type != "recording.ready-to-download" → return 200 (ack)
5. Extract room_name, recording_id, s3_key
6. appointmentId = parseAppointmentIdFromRoomName(room_name)
7. If appointmentId == null → log, return 200
8. Fetch appointment; if not found/cancelled → log, return 200
9. Trigger ScribePipelineService.processVideoRecording(...) @Async
10. Return 200 immediately (do not wait for pipeline)
```

---

## 4. Recording Download Pipeline

### 4.1 Steps

| Step | Action | Service |
|------|--------|---------|
| 1 | Receive recording reference (URL or s3_key) | ScribePipelineService |
| 2 | Download to temp file | ScribeRecordingService |
| 3 | Ensure audio format (convert video to audio if needed) | ScribeRecordingService |
| 4 | Pass file path to TranscriptionService | ScribePipelineService |
| 5 | Transcribe → text | TranscriptionService |
| 6 | Summarize → SOAP JSON | MedicalSummaryService |
| 7 | Create ai_draft_notes | AiDraftNoteService |
| 8 | Cleanup temp file | ScribeRecordingService |

### 4.2 Audio Extraction

- **cloud-audio-only:** Already audio; may be mp4 container with audio track — FFmpeg or similar to extract to wav/mp3.
- **cloud:** Video+audio; extract audio track.
- **In-person:** Client sends WAV (web) or AAC (mobile) — Whisper accepts both.

**Libraries:** Consider `ffmpeg` CLI (ProcessBuilder) or Java lib like `jave2` for audio extraction. Alternatively, Whisper accepts mp4 directly — test first.

---

## 5. TranscriptionService (Whisper API)

### 5.1 API Call

```
POST https://api.openai.com/v1/audio/transcriptions
Content-Type: multipart/form-data
Authorization: Bearer {OPENAI_API_KEY}

file: <audio file>
model: whisper-1
language: (optional, e.g. "en")
response_format: json
```

### 5.2 Response

```json
{
  "text": "Full transcript text..."
}
```

### 5.3 Implementation Notes

- Use OkHttp `MultipartBody` for file upload
- Set reasonable timeouts (e.g. 60s for short recordings, 120s for longer)
- Handle rate limits (retry with backoff)
- Max file size: 25 MB (Whisper limit)

---

## 6. MedicalSummaryService

### 6.1 Prompt Design

**System prompt:**
```
You are a medical scribe. Convert the following doctor-patient consultation transcript 
into structured SOAP clinical notes. 

Output ONLY valid JSON with these keys: subjective, assessment, plan, body.
- subjective: Patient's reported symptoms, history, chief complaint
- assessment: Clinical findings, possible causes (do not diagnose)
- plan: Recommended next steps, follow-up, prescriptions mentioned
- body: Full note if sections don't fit, or combined text

Rules: Preserve medical terminology. Be concise. Do not add information not in the transcript.
Do not diagnose or prescribe. If transcript is empty or unclear, return empty strings.
```

**User message:** `[transcript]`

### 6.2 JSON Output

```json
{
  "subjective": "...",
  "assessment": "...",
  "plan": "...",
  "body": "..."
}
```

### 6.3 Integration with AiDraftNoteService

`AiDraftNoteService.createDraft` expects `aiResponseText`. We can either:
- **Option A:** Store JSON string as `aiResponseText` and extend `StructuredNoteParser` to handle JSON.
- **Option B:** Convert `ScribeSummaryResult` to the same text format that `StructuredNoteParser` expects (section headers like "SUBJECTIVE:", "ASSESSMENT:", etc.).

**Recommendation:** Option B — format as:
```
SUBJECTIVE:
{subjective}

ASSESSMENT:
{assessment}

PLAN:
{plan}

{body}
```

This keeps `StructuredNoteParser` and `ConsultationNote` unchanged.

---

## 7. Draft Creation (ai_draft_notes)

### 7.1 Ensuring appointmentId, patientId, doctorId

**AiDraftNoteService.createDraft** signature:
```kotlin
fun createDraft(
    doctorId: Long,
    patientId: Long?,
    consultationId: Long?,  // maps to appointmentId in confirm flow
    aiResponseText: String
): AiDraftNote
```

**For scribe pipeline:**
- `doctorId` — from `appointment.doctor.id`
- `patientId` — from `appointment.patient.id` (required for consultation note)
- `consultationId` — pass `appointmentId` (used as appointment when confirming)

**New overload (optional):**
```kotlin
fun createScribeDraft(
    doctorId: Long,
    patientId: Long,
    appointmentId: Long,
    aiResponseText: String,
    aiLabel: String = "AI Scribe - Consultation Summary"
): AiDraftNote
```

Or reuse `createDraft` with `consultationId = appointmentId`, `patientId = appointment.patient.id`.

### 7.2 ai_label

Use a distinct label for scribe drafts so the UI can show "From AI Scribe" vs "From Shifa AI".  
Example: `aiLabel = "AI Scribe - Consultation Summary"`.

---

## 8. In-Person Audio Upload

### 8.1 Endpoint

**Method:** `POST`  
**Path:** `/api/consultations/upload-recording`  
**Controller:** `ConsultationController` (new) or extend `AppointmentController`  
**File:** `src/main/kotlin/com/shifa/web/ConsultationController.kt`

### 8.2 Request

**Content-Type:** `multipart/form-data`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `audio` | File | Yes | Audio file (wav, mp3, m4a, etc.) |
| `appointmentId` | Long | Yes | Appointment ID |

### 8.3 Flow

1. Authenticate (DoctorPrincipal)
2. Validate appointment exists and belongs to doctor
3. Validate file size (e.g. max 25 MB for Whisper)
4. Save to temp: `{storageRoot}/scribe-temp/{uuid}.{ext}`
5. Trigger `ScribePipelineService.processInPersonRecording(appointmentId, doctorId, patientId, path)` @Async
6. Return 202 Accepted with `{ "message": "Recording received. Notes will be ready in a few minutes.", "jobId": "uuid" }`

### 8.4 Security

- Only doctors can upload
- Appointment must belong to the doctor
- Rate limit: e.g. 5 uploads per appointment per hour

---

## 9. Background Processing

### 9.1 @Async Approach

**Enable async:**
```kotlin
@Configuration
@EnableAsync
class AsyncConfig
```

**ScribePipelineService:**
```kotlin
@Service
class ScribePipelineService(...) {
    @Async
    fun processVideoRecording(...) { ... }
    
    @Async
    fun processInPersonRecording(...) { ... }
}
```

**Thread pool:** Configure `TaskExecutor` with bounded queue and thread pool to avoid overload.

### 9.2 Alternative: Dedicated Job Queue

If `@Async` is insufficient (e.g. need retries, persistence across restarts):
- Add Spring Batch, or
- Use a simple DB-backed queue (e.g. `scribe_jobs` table with status) + scheduled poller

**For MVP:** `@Async` is sufficient. Upgrade to queue if needed.

### 9.3 Idempotency

For Daily webhook: `recording.ready-to-download` can be sent multiple times. Use `recording_id` as idempotency key — skip if already processed (store in `scribe_processed_recordings` or similar).

---

## 10. New Endpoints Summary

| Method | Path | Auth | Purpose |
|--------|------|------|---------|
| POST | `/api/webhooks/daily` | Webhook signature | Receive Daily recording events |
| POST | `/api/consultations/upload-recording` | Doctor JWT | Upload in-person audio |

---

## 11. Database Changes

### 11.1 Optional: Scribe Job Tracking

If idempotency or audit is needed:

**Migration V43:**
```sql
CREATE TABLE IF NOT EXISTS scribe_processed_recordings (
  recording_id   VARCHAR(255) PRIMARY KEY,
  appointment_id BIGINT NOT NULL,
  ai_draft_note_id UUID NULL REFERENCES ai_draft_notes(id),
  status        VARCHAR(32) NOT NULL,  -- PROCESSING, COMPLETED, FAILED
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at  TIMESTAMPTZ NULL
);
```

**Otherwise:** No schema changes. `ai_draft_notes` and `consultation_notes` already support the flow.

---

## 12. Processing Pipeline (Detailed)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ScribePipelineService.processVideoRecording                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│  1. appointmentId = parseAppointmentIdFromRoomName(roomName)                  │
│  2. appointment = appointmentRepo.findById(appointmentId)                    │
│  3. If !appointment → log, return                                            │
│  4. doctorId = appointment.doctor.id, patientId = appointment.patient.id     │
│  5. tempPath = scribeRecordingService.download(...)                          │
│  6. try {                                                                    │
│       transcript = transcriptionService.transcribe(tempPath)                 │
│       summary = medicalSummaryService.summarize(transcript)                   │
│       formattedText = formatAsScribeNote(summary)                             │
│       aiDraftNoteService.createDraft(doctorId, patientId, appointmentId,    │
│                                     formattedText)                            │
│     } finally { scribeRecordingService.cleanupTempFile(tempPath) }           │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 13. Failure Handling

| Failure Point | Handling |
|---------------|----------|
| Webhook parse error | Log, return 200 (avoid Daily retries) |
| Appointment not found | Log, return 200 |
| Download failure | Log, optionally retry 1x; if still fail, store in dead-letter or alert |
| Transcription failure | Log, do not create draft; cleanup temp |
| Summary failure | Log, optionally create draft with raw transcript as body |
| AiDraftNoteService failure | Log, retry once; cleanup temp |
| Temp file cleanup | Always in `finally`; schedule delayed cleanup for orphaned files |

### 13.1 Observability

- Structured logging: `recording_id`, `appointment_id`, `stage`, `error`
- Metrics: pipeline_duration, transcription_duration, failure_count
- Optional: Push notification to doctor if pipeline fails

---

## 14. Configuration

### 14.1 New Properties

```yaml
# application.yml
scribe:
  temp-dir: ${STORAGE_ROOT:./public-storage/images}/scribe-temp
  temp-retention-minutes: 60
  max-audio-size-bytes: 26214400  # 25 MB
  daily:
    webhook-secret: ${DAILY_WEBHOOK_SECRET:}  # For signature verification
  s3:  # If using S3 for Daily recordings
    bucket: ${DAILY_RECORDING_S3_BUCKET:}
    region: ${AWS_REGION:us-east-1}
```

### 14.2 Daily.co Room Configuration

In `DailyVideoService.kt`:
- Set `enable_recording: true` in `RoomProperties`
- Configure Daily.co dashboard: webhook URL = `https://api.example.com/api/webhooks/daily`
- Configure S3 bucket in Daily (if using S3) or verify Daily provides download URL

---

## 15. Estimated Implementation Steps

| Phase | Steps | Est. Effort |
|-------|-------|-------------|
| **1. Foundation** | Add `@EnableAsync`, `ScribeProperties`, `TranscriptionService`, `MedicalSummaryService` | 1–2 days |
| **2. Recording** | `ScribeRecordingService` (download from URL/S3), `ScribePipelineService` | 1–2 days |
| **3. Daily Webhook** | `DailyWebhookController`, room_name→appointmentId, trigger pipeline | 0.5–1 day |
| **4. Enable Recording** | `DailyVideoService`: enable_recording=true, configure Daily dashboard | 0.5 day |
| **5. In-Person Upload** | `ConsultationController`, multipart upload, trigger pipeline | 1 day |
| **6. AiDraftNoteService** | Ensure createDraft supports scribe (consultationId=appointmentId) | 0.5 day |
| **7. Frontend** | "Start AI Notes" button, record package, upload to new endpoint | 1–2 days |
| **8. Testing & Polish** | E2E tests, failure handling, idempotency | 1–2 days |

**Total:** ~7–11 days

---

## 16. File Checklist

| File | Action |
|------|--------|
| `ScribePipelineService.kt` | Create |
| `ScribeRecordingService.kt` | Create |
| `TranscriptionService.kt` | Create |
| `MedicalSummaryService.kt` | Create |
| `DailyWebhookController.kt` | Create |
| `ConsultationController.kt` | Create |
| `ScribeProperties.kt` | Create |
| `AsyncConfig.kt` | Create |
| `DailyVideoService.kt` | Modify (enable_recording) |
| `AiDraftNoteService.kt` | Minor (ensure consultationId used for appointment) |
| `application.yml` | Add scribe config |
| `in_person_appointment_screen.dart` | Add "Start AI Notes" UI |
| `video_call_screen.dart` | Optional: same button |

---

*End of implementation plan. No code has been implemented.*
