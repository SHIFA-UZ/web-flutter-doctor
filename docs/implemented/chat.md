# Chat

Area 07 §1 (flows 07/01–07/07, board `DaChat`). Tickets CHAT-01…07 (M7).

## CHAT-01 · Chat data

Built 2026-11-05. Spec 07 §1.1, §1.4–1.5, ticket CHAT-01, 15 §2.8.

- **`ChatRepository`** (`data/chat/`): `conversations()`, `thread(id)` (the conversation and every message, oldest
  first; no paging yet, BE-14), `start(patientId | doctorId)` (no message is sent), `markRead(id)`, `send(request)`
  (the stored message back), `search(q)` (patients and colleagues), `upload(bytes, name, mime)` (multipart `file` →
  `{url}`; the backend takes images, pdf/doc/docx and audio up to 25 MB, the file name is made safe).
- **Models** (`ChatModels.kt`): `Conversation` and `Message` from the DTOs; a row without an id or a readable time is
  skipped, not the list. `type` and `status` read in any case (the backend sends lower case); an unknown type is text,
  or a document when it carries a file (the backend's own fallback); an unknown status is sent (read when `isRead`).
  `fromPatient` from `senderRole`.
- **File URLs** (`data/api/FileUrls.kt`, `absoluteFileUrl`): today's URLs are absolute and signed and stay as they
  are; a relative one (older rows) resolves against the chosen backend's host without `/api/`. Applied to every
  attachment, voice and documents too (Flutter only to photos and images, bug 16), to participant photos and to
  search results' photos.
- **Demo** (`data/debug/DemoChat.kt`): three conversations with demo patients (text, a pdf, a voice note; 3 unread),
  start with a patient or a colleague (one conversation per person and role), read, send (moves the conversation to
  the top), search over the demo patients and a demo colleague, upload (a demo-host URL that
  doesn't load: files never leave the phone), and the unread count from the conversations. `DemoBackendTest` covers it.
- **Dev (2026-11-05):** checked against `/v3/api-docs` and the backend's `MessageService` (lower-case types and
  statuses, signed absolute URLs, upload answering `{url, thumbnailUrl, fileName, fileSize}`); a real GET as the seed
  doctor: 0 conversations, search "Ja" finds a patient, unread 0. Starting a conversation can't be undone on dev
  (no delete), so writes were checked in demo only.
- **Tests:** `ChatRepositoryTest` (URLs, a row skipped, a thread's order and types in any case, unknown type,
  upload's multipart and send's body).
