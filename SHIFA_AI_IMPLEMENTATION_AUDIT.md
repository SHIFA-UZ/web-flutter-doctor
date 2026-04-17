# Shifa AI Implementation — Technical & Product Assessment

**Scope:** Shifa doctor app, Shifa doctor backend, Shifa patient app (reference).  
**Basis:** Current codebase only; no assumptions about unimplemented features.  
**Date:** February 2025.

---

## STEP 1 — Current AI Capabilities

### 1.1 AI-Related Components

| Component | Location | Purpose |
|-----------|----------|---------|
| **OpenAI streaming assistant** | Backend: `OpenAiResponsesService.kt`, `DoctorAiController.kt` | Single LLM feature: streaming “Shifa AI” doctor support |
| **MedicalPromptBuilder** | Backend: `com.shifa.ai.MedicalPromptBuilder.kt` | Builds system prompt (safety rules, doctor name, response language) and optional patient-context prompt |
| **RedFlagDetector** | Backend: `com.shifa.ai.RedFlagDetector.kt` | Keyword-based emergency filter; blocks request and returns fixed message if match |
| **PatientAiContextBuilder** | Backend: `com.shifa.ai.PatientAiContextBuilder.kt` | Builds read-only patient context (age, language, last 5 document titles/dates) for prompt |
| **TextCleaner** | Doctor app: `lib/core/utils/text_cleaner.dart` | Post-processes streamed text (spacing, split words, numbering) for display |
| **AiApi / streamAi** | Doctor app: `lib/core/api/ai_api.dart`, `ai_api_provider.dart` | Calls `POST /api/ai/stream` with Bearer JWT, parses SSE |
| **DoctorAiPanel** | Doctor app: `lib/core/widgets/doctor_ai_panel.dart` | Standalone “Ask Shifa AI” panel (no patient context) |
| **Home screen AI** | Doctor app: `lib/features/home/presentation/home_screen.dart` | In-context AI panel with optional patient selection and language (EN/RU/UZ) |

**Unused / dead code:**

- `MedicalCodeSuggestion.kt`, `CodingSystem.kt` — defined but never referenced (no AI coding feature).

---

### 1.2 Per-Feature Description (What Exists)

**Feature: Streaming “Shifa AI” doctor assistant**

| Attribute | Detail |
|-----------|--------|
| **What it does** | Streams a single-turn, non-diagnostic “medical support” reply from an LLM. No chat history; each request is independent. |
| **Where triggered** | (1) Home screen — collapsible AI panel, optional “selected patient”; (2) `DoctorAiPanel` — “Ask Shifa AI” panel (no patient). |
| **Input** | Text question (`question`), output language enum (`EN` / `RU` / `UZ`), optional `patientId`. When `patientId` is set, backend adds abstracted patient context (age, language, recent document titles/dates; appointment summaries are always empty). |
| **Output** | SSE stream of raw text tokens; client accumulates and displays. Home uses `TextCleaner.normalizeAiResponse` on completion; `DoctorAiPanel` uses `TextCleaner.cleanText`. |
| **Auto-save / review** | **No.** Output is display-only; not persisted in DB. No human-in-the-loop or approval step. |
| **Model / provider** | **OpenAI** `gpt-4o-mini` (config: `application*.yml`). URL: `https://api.openai.com/v1/chat/completions`. |
| **Where data is sent** | Question + (when patient selected) patient context sent to **Shifa backend**; backend forwards to **OpenAI** (third-party). |

**No other AI features found:** no speech-to-text, no document classification, no AI triage logic, no SOAP/notetaking, no prescription drafting, no calendar optimization, no differential-diagnosis assistant. Patient app has no AI; it only contains a `TextCleaner` class (unused in patient flows).

---

## STEP 2 — Architecture & Data Flow

### 2.1 Data Flow (Single AI Feature)

```
User (doctor) → Flutter (Home / DoctorAiPanel)
  → HTTPS + JWT → Backend POST /api/ai/stream
  → DoctorAiController (DoctorPrincipal)
  → PatientAiContextBuilder.build(patientId) [if patientId != null]
  → OpenAiResponsesService.streamDoctorAssistant(...)
  → RedFlagDetector.hasRedFlags(question + context) → [if true] fixed emergency message, return
  → SimpleRateLimiter.tryAcquire() → [if false] rate-limit message, return
  → Build messages: [system (MedicalPromptBuilder), optional patient context, user question]
  → OkHttp POST https://api.openai.com/v1/chat/completions (stream=true)
  → Parse SSE → emit tokens
  → SseEmitter sends tokens to client
  → Flutter accumulates tokens, applies TextCleaner on done (Home) or on display (DoctorAiPanel)
```

### 2.2 Security & Data Handling

| Question | Answer |
|----------|--------|
| **Patient data to external APIs?** | **Yes.** When a patient is selected, age, language, and recent document titles/dates are included in the prompt sent to **OpenAI** (third-party). |
| **Encryption in transit?** | **Yes.** Client–backend and backend–OpenAI use HTTPS. |
| **Encryption at rest (AI)?** | **N/A.** AI responses are not stored; only streaming to client. |
| **AI interactions logged?** | **Minimal.** Backend logs: OpenAI SSE status code; debug-level token spacing; startup “OpenAI key present/length/prefix” (log.error — avoid logging key material). **No** audit log of question, patientId, or response content. |
| **Human-in-the-loop?** | **No.** Output is not saved; no review or approval step. |
| **Who can call /api/ai?** | **Effectively doctors only.** SecurityConfig uses `.authenticated()` for `/api/ai/**`; controller expects `DoctorPrincipal`. Patient tokens would not satisfy the principal type. |

### 2.3 Risk Highlights

- **GDPR / data processing:** Sending patient-derived context (age, language, document metadata) to OpenAI implies a data-processing relationship; need BAA/DPA and clear disclosure if applicable.
- **Data leakage:** Prompt (including patient context) is in OpenAI request; provider policy and retention apply.
- **Prompt injection:** No server-side sanitization or length limit on `question`; a malicious or malformed input could try to override instructions (mitigated only by model behavior).
- **Hallucination:** No structured output schema; no validation or “do not use for diagnosis” watermark in persisted form (only in prompt). Risk is product/UX rather than stored data (nothing stored).

---

## STEP 3 — Quality & Limitation Assessment

### 3.1 Prompt Quality

- **Structured?** Yes: single system prompt (safety rules, no diagnosis/prescription, emergency escalation, formatting note) + optional patient-context block + user question.
- **Deterministic?** No: temperature 0.2, no seed; outputs vary.
- **Edge cases:** Red-flag list is keyword-based and broad (e.g. “child”, “pregnant”) — can over-block benign queries. No explicit handling for empty question, very long question, or non-EN/RU/UZ language beyond the three enum values.

### 3.2 Language Support

- **Uzbek / Russian / English:** Supported via `OutputLanguage` (EN, RU, UZ) passed into the system prompt (“Language: ${language.name}”).
- **Mixed-language robustness:** Not explicitly instructed; relies on model behavior. No client-side language detection.

### 3.3 Reliability

- **Hallucination:** Possible; no output schema or validation.
- **Structure / formatting:** Free-form text; client-side `TextCleaner` fixes known streaming artifacts (spaces, split words, numbering). Formatting is not guaranteed.
- **Stability:** SSE parsing and token spacing logic on backend aim for stable display; no retries or fallback on OpenAI errors (client sees error message).

### 3.4 Scalability

- **10k users:** Not designed for it: one blocking SSE call per request; controller uses `Thread { runBlocking { ... } }` per stream; global `SimpleRateLimiter` (e.g. 60/min) is per process.
- **Blocking:** Yes: synchronous OkHttp call in a coroutine; no non-blocking queue or backpressure.
- **Queue:** None. Under load, new requests either hit rate limit or compete for threads.

---

## STEP 4 — Maturity Classification

**Current level: Level 1 – Basic prompt wrapper**

- Single use case: one-shot streaming Q&A with a fixed system prompt.
- No structured clinical workflow (no SOAP, no triage, no prescriptions).
- No integration with clinical records beyond read-only context injection (and no appointment summaries).
- No AI infrastructure (no logging, no review flow, no fine-tuning, no multi-step pipelines).

So the implementation is a **single-purpose, prompt-based streaming assistant** with safety wording and emergency keyword blocking, not yet a structured clinical or AI-powered medical infrastructure.

---

## STEP 5 — Gap Analysis vs. Roadmap

| Target capability | Implemented? | Quality | What’s missing |
|-------------------|-------------|--------|----------------|
| **AI notetaking (structured SOAP)** | No | — | No SOAP schema, no note generation, no save to records. |
| **AI document classification** | No | — | Only document titles/dates in context; no classification or tagging. |
| **AI triage (non-diagnostic)** | Partial | Low | RedFlagDetector is keyword blocklist only; no severity or routing logic. |
| **Intelligent patient history scan** | Partial | Low | Last 5 document titles + dates (and empty appointment summaries); no summarization or reasoning. |
| **AI calendar optimization** | No | — | No AI in schedule/calendar code. |
| **Speech-to-text pipeline** | No | — | No STT in codebase. |
| **Differential diagnosis assistant** | No | — | Prompt explicitly forbids diagnosis; no such feature. |
| **Prescription drafting** | No | — | Prompt forbids prescribing; no drafting flow. |

---

## STEP 6 — Prioritized Improvement Plan

### Phase 1 – Immediate (stability, formatting, hallucination control)

| # | Action | Complexity | Risk | Impact |
|---|--------|-------------|------|--------|
| 1.1 | **Stop logging API key material** — Remove or redact key prefix in startup log (use only “present”/“length” if needed). | Low | High (key exposure) | High |
| 1.2 | **Add server-side input limits** — Max length for `question` (e.g. 2k–4k chars), reject oversized or empty body. | Low | Medium (injection/abuse) | Medium |
| 1.3 | **Tighten RedFlagDetector** — Narrow or contextualize keywords (e.g. avoid blocking all “child” queries); consider allowlist for “pediatric” context. | Medium | Medium (over-blocking) | Medium |
| 1.4 | **Structured error handling** — Return consistent SSE error payload or status so client can show clear “AI unavailable” vs “rate limit” vs “safety block”. | Low | Low | Medium |

### Phase 2 – Structural (prompt architecture, logging, review)

| # | Action | Complexity | Risk | Impact |
|---|--------|-------------|------|--------|
| 2.1 | **Audit logging** — Log (with no PHI in plain text if possible) request metadata: doctor id, patientId yes/no, language, timestamp, red-flag hit, rate-limit hit. Do not log full question/response in production. | Medium | Low | High (compliance, debugging) |
| 2.2 | **Prompt versioning / A/B** — Store prompts in config or DB with version id; log version per request. | Medium | Low | Medium |
| 2.3 | **Optional “review before use”** — Allow saving AI reply to draft or note with “AI-generated, verify before use” and require explicit doctor action to add to record. | High | Low | High (safety, liability) |

### Phase 3 – Intelligence (fine-tuning, multilingual)

| # | Action | Complexity | Risk | Impact |
|---|--------|-------------|------|--------|
| 3.1 | **Explicit multilingual instructions** — Add instructions for Uzbek/Russian and mixed-language in system prompt; consider separate prompts per language. | Low | Low | Medium |
| 3.2 | **Structured output (optional)** — For a dedicated “summary” or “suggestions” endpoint, use JSON mode or post-parse to constrain format and reduce hallucination. | Medium | Medium | Medium |
| 3.3 | **Fill appointment summaries** — Implement `appointmentSummaries` in `PatientAiContextBuilder` so context includes recent visits. | Low | Low | Medium |

### Phase 4 – Advanced (speech-to-text, AI scribe)

| # | Action | Complexity | Risk | Impact |
|---|--------|-------------|------|--------|
| 4.1 | **Speech-to-text pipeline** — Integrate STT (e.g. device or cloud) and send transcript as `question` or as dedicated “voice note” path. | High | Medium (PHI in speech) | High |
| 4.2 | **AI scribe / SOAP draft** — New flow: structured prompt + optional STT → SOAP-style draft → review → save to record. | High | High (clinical accuracy) | High |

---

## STEP 7 — Final Summary

### 7.1 Overall AI quality score: **3/10**

- One feature (streaming Q&A), no clinical workflow integration, no audit trail, patient data sent to third party without persisted controls, scalability and reliability are limited.

### 7.2 Biggest architectural weakness

- **No auditability and no control over downstream use of data.** AI requests (and optional patient context) are sent to OpenAI with no logging of content, no retention policy implemented in-app, and no human-in-the-loop. Architecture is “fire-and-forget” from a governance perspective.

### 7.3 Biggest product opportunity

- **Patient-context-aware assistant is already wired** (age, language, document list). Filling appointment summaries and adding a single “save as draft note” with explicit review step would turn the same pipeline into a real clinical aid without new infra.

### 7.4 Most urgent risk to fix

- **Reducing exposure of API key** (startup log) and **adding request/content audit logging** (metadata only) so usage is traceable and compliant.

### 7.5 Recommended next 3 engineering actions

1. **Remove or redact any API key from logs** and add server-side **input validation** (max length, non-empty) on `DoctorAiRequest.question`.
2. **Introduce audit logging** for `/api/ai/stream`: doctor id, hasPatientId, language, timestamp, redFlagHit, rateLimitHit (no full question/response).
3. **Implement `appointmentSummaries`** in `PatientAiContextBuilder` and add a **“Save as draft”** flow so the doctor can optionally save the AI reply as a draft note with a clear “verify before use” label.

---

*Assessment based solely on current Shifa doctor app and backend codebase.*
