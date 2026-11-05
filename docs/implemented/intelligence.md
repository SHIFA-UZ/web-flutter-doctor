# Shifa AI & Intelligence

Area 02 §3–§8 (flows 03/*, boards `DaIntel`). Tickets AI-01…10, INTEL-01…10 (M10). AI-03, AI-05, AI-06 and AI-11 were
built with the visit workspace (see visits.md).

## AI-01 · AI HTTP client

Built 2026-11-05. Spec 02 §3.2, §8, §9, ticket AI-01. Data only; INTEL-04, AI-08, AI-09 and AI-10 put it on screen.

- **Endpoints** (dev `/v3/api-docs`, the backend's `DoctorAiController` / `PatientBriefingController`, and real calls
  as the seed doctor): `POST /ai/morning/stream` and `POST /ai/stream` (SSE, `DoctorAiRequest`: `messages`,
  `language` EN/RU/UZ, `question?`, `patientId?`, `consultationId?`, `conversationId?` ≤ 128), `POST /ai/speak`,
  `POST /ai/patient-briefing/{id}` (`{language}` en/uz/ru), `GET /ai/visit-facts/appointment/{id}`,
  `GET /ai/visit-facts/patient/{id}/timeline` (dev: `{"timeline":[],"patterns":[]}` for the seed patients),
  `POST /ai/visit-facts/commit`. Draft confirm / discard were already in `ConsultationRepository` (AI-11) and are
  reused. No paid AI call was made on dev: a stream without messages fails validation before the model runs, which
  showed the real framing (`event:error\ndata:{"code":"VALIDATION",…}`, HTTP 200, no space after `data:`).
- **SSE** (`data/api/Sse.kt`): `data:` is kept verbatim (Spring writes no space, so a token's leading space is part of
  the text; the old reader stripped it and glued words), lines join with `\n`, `id:` / `retry:` / comments ignored, an
  event the stream ends in is still delivered with its type. `error` events are typed by code (`VALIDATION` →
  ValidationError, `RATE_LIMIT(ED)` → RateLimit, else ServerError with the code). Callbacks suspend, and cancelling the
  caller closes the connection (tested).
- **Bug fixed:** `streamEvents` called `build()` inside the request builder, which resolved to
  `HttpRequestBuilder.build()` instead of the caller's block, so every stream went out as a bare GET. No screen used
  it yet; the parameter is now `configure`.
- **Repository** (`data/ai/AiRepository.kt`): `stream(request, morning, onEvent)` → `Token` / `Tool(name, payload)` /
  `Draft` (a nameless card or a draft without an id is dropped and logged without content); one turn gets 75 s
  (02 §3.3), after which it fails as a timeout `NetworkError`. `speak` cuts the text as Flutter did (560 chars, last
  `.!?` at 40+, else the last space past 40), 25 s, returns bytes + type (`audio/mpeg` when missing). `patientBriefing`
  and the visit facts keep failures typed (Flutter turned a failed fact read into an exception with the body text);
  fact rows are read one by one. Models in `data/model/AiModels.kt` (`VisitFacts.keptDrafts` / `dropped` as Flutter).
  Nothing logs AI text, tokens or transcripts.
- **Demo:** `DemoAi`: the morning stream sends a `get_today_schedule` card built from today's demo calendar, then a
  greeting in the request's language, word by word with leading spaces; `/ai/stream` ends in a draft (confirmable
  when tied to a demo visit); a question containing "fail" ends in an `AI_UNAVAILABLE` error event. Speech is half a
  second of silent WAV; the briefing is fixed; each demo visit has two kept drafts and one dropped fact, and committing
  marks them, drafts a task id for the medication and counts patterns. Unknown visits and any other `ai/` path
  answer a local 404, never the real backend (Codex review).
- **Tests:** `AiRepositoryTest` (a recorded morning fixture with leading spaces, cards, a nameless card, an empty data
  line, a draft, a two-line token, ignored lines and an EOF tail; request bodies; error event and non-2xx codes;
  cancel closes the connection; speak type and cut; briefing; facts, timeline, commit), `SseTest` updated,
  `DemoBackendTest` (the demo AI end to end, nothing forwarded).
- **Not checked on a phone:** there is no screen yet; the emulator check comes with INTEL-04.

## AI-02 · AI text renderer

Built 2026-11-05. Spec 02 §3.2 helpers, Flutter `core/widgets/ai_response_text.dart`, ticket AI-02. Board `DaIntel`
(an answer is plain 14/22 text there; headings and bullets are not drawn on the boards, so their look follows
Flutter's in the design's tokens: not in the design).

- **Widget** (`ui/widgets/AiResponseText.kt`): `#`–`###` headings and a line that is only `**…**` → a heading
  (`panelTitle`, `Text.BrandStrong`, a 3 dp `Border.Brand` bar on the left); `-`, `*`, `•` or `1.` → a bullet row
  (numbers become a dot, as in Flutter); `**bold**` inside lines (non-greedy; a lone `**` stays as typed); a run of
  blank lines → one 10 dp gap; anything else a paragraph. One line over 220 characters with 3+ sentences is split
  into a paragraph per sentence. Whitespace in these patterns is Dart's (no-break spaces count; Kotlin's `\s` is
  ASCII-only: Codex review). The whole answer is one `SelectionContainer`. Text style `aiText` (14/22, new in
  `ShifaTextStyles`, from DaIntel); callers can pass another (the visit briefing keeps `bodyText`).
- **Used by:** the visit briefing sheet (APPT-06), whose `ponytail:` plain-text stand-in is gone; next the
  Intelligence thread (INTEL-04) and the patient briefing (AI-09).
- **Tests:** `AiResponseTextTest` (Flutter's two tests plus wrapped headings, numbered and star bullets, blank runs,
  `**Reason:** text` staying a paragraph, the sentence split and line endings); screenshots `Display_ai_text`,
  `Briefing_ready` re-recorded.

## AI-09 · Patient briefing

Built 2026-11-05. Spec 02 §6, flow 03/20, ticket AI-09. Board `DaPatients` (the header's "AI summary" and the
"brief" sheet: title "Patient briefing", the patient's name under it, the text, "AI summary — verify against the
originals.", Copy). Flutter never showed this on Android or iOS.

- **Trigger:** a sparkles icon button "AI summary" under the favourite star in the patient header (the board's
  labelled button doesn't fit beside the name on a phone; the flow asks for an icon button). Shown only when the plan
  includes `PATIENT_BRIEFING` and the user isn't clinic staff; hidden until the plan is known. The board's
  more-actions sheet doesn't exist in the app yet, so the header is the only entry.
- **Sheet** (`ui/patients/PatientBriefing.kt`): loading = "Reading documents and generating briefing..." (a polite
  live region) over a heading + 6 line skeleton; ready = sources line (hidden when both counts are 0), the text through
  `AiResponseText`, the disclaimer, Copy (→ "Copied") and Regenerate; empty = "No information to brief on yet." with
  Retry briefing; failure = an error banner with the request id and Retry briefing; a failed Regenerate keeps the
  last text under the banner.
- **Rules:** the request goes in the app language at the time of the tap. The ViewModel is kept per patient in the
  shell store, so the briefing stays for the session and reopening shows it at once; a request still running when
  the sheet closes finishes and shows on reopen (Flutter dropped it and paid twice). One request at a time (Flutter
  restarted on a second tap). 60 s timeout (flow target). The backend's 400 means "no documents, visits, forms or
  notes" (`PatientBriefingService`), shown as the empty state, not an error (it replaces an older text, Codex
  review); 429 → "daily AI usage limit"; the 60 s running out → "Shifa took too long to reply"; 403
  refreshes the profile so the button goes if the plan changed. No server text is shown.
- **Kept:** the sources line uses the existing translated keys with "document(s)" (proper plurals would need new
  ru/uz forms; D-17).
- **Shared fix:** `codeBanner`'s dismiss is optional now; without one the banner has no ✕. 24 other screens still
  pass a no-op, fixed separately.
- **Checked:** emulator, demo mode: the button, the sheet with the demo briefing, Copy → Copied, close and reopen
  without a new request. Not run on dev (a briefing is a paid model call). iOS compiled, not run.
- **Tests:** `PatientBriefingViewModelTest` (first open in the app language, reopen without a request, one request at
  a time, 400 and blank → empty, 429 message and request id, a failed Regenerate keeps the text); screenshots
  `PatientBriefing_loading`, `_ready`, `_empty` (with the limit error), `PatientDetail_overview` (button shown).

## AI-10 · Visit facts panel and clinical timeline

Built 2026-11-05. Spec 02 §3.2 and §9, flow 03/21, ticket AI-10. Boards `DaVisit` (video room "Visit facts from the
call": kind pill, text, Keep / Reject chips, rejected at 55 % and struck through, "Document everything",
"already in the patient timeline") and `DaPatients` ("Clinical timeline": dot and line per fact, when).

- **Panel** (`ui/visit/VisitFacts.kt`, `VisitFactsViewModel` per visit): in the visit workspace above the notes, for
  in-person and video visits (Flutter had it only in the video call). Read on open and on every return (the scribe may
  have finished meanwhile), plus a refresh icon (flow §3). Kept drafts with the quote (italic) and schedule hint;
  Keep / Reject are local until committed and survive a reload (Flutter lost them). "Dropped (not said)" lists what
  the model dropped, with its reason as sent. Top 3 clinic patterns. "Document everything" sends the kept ids as
  `acceptFactIds` and the rejected ones as `rejectFactIds` (the backend rejects the rest of the drafts when an accept
  list is sent), is disabled while documenting or with nothing kept (Flutter documented 0), then reads again; the
  result reads "Documented {n} facts." (plural) or "Facts documented: n. Medication tasks drafted: m." in a polite
  live region. A failed commit says so with the board's copy.
- **Notes in step (Codex review):** the backend also appends the documented facts to the visit's manual note. The
  notes are saved before the commit (no commit if they can't be), and the same text is added to the open editor as
  already saved (`VisitNotes.appendSaved`), so the next autosave keeps it. The commit's answer is applied at once, so
  the drafts can't be documented twice. While the scribe is working and no facts have come, the panel reads again
  every 10 s while on screen.
- **When it shows:** only while there are facts (kept, dropped or documented), a first read in flight, or a failed
  first read (an error row with Retry; Flutter hid failures). Without the AI notes plan the panel isn't there (the
  board's "Visit facts need the Pro plan." line is left out: it would show in every visit for BASIC doctors, and the
  in-person boards have no panel at all).
- **Timeline** (`ui/patients/ClinicalTimeline.kt`, `ClinicalTimelineViewModel` per patient): on the overview under
  the clinical summary, read on every return to the chart. "Medication · text", the quote, and when it was
  documented as "22 October 2026 · 10:42" in practice time (Flutter showed the raw ISO string), then the top 4
  patterns. "No documented visit facts yet." when empty; an error row with Retry on a first failure; a failed re-read
  keeps what is shown. AI notes plan, doctors only.
- **Shared:** `factTypeLabel` (Symptom / Diagnosis / Therapy / Medication, others as sent) and `Patterns` live in
  `VisitFacts.kt` and are used by both.
- **Checked:** emulator, demo mode, visit 102 (6 Nov): three facts (one dropped), Reject the symptom, Document
  everything → "Facts documented: 1. Medication tasks drafted: 1.", the dropped one stays, then Patients → Jasur
  Karimov → the timeline lists the medication at 13:45 with the pattern. Dev has no visits, so no dev read of an
  appointment's facts (the timeline read was checked in AI-01). iOS compiled, not run.
- **Not built:** tapping a timeline row to its visit, per-fact edit.
- **Tests:** `VisitFactsViewModelTest` (read, a rejection surviving a reload, commit body and the read after, nothing
  kept can't commit, a failed commit, a failed first read, empty, the timeline keeping its rows on a failed re-read);
  screenshots `VisitFacts_ready`, `VisitFacts_done` (message, already documented, error row), `ClinicalTimeline`.

## INTEL-01 · Chat models and session store

Built 2026-11-05. Spec 02 §4.2, §4.3, §3.9, §0 (`detectReplyLanguage`), ticket INTEL-01. Data only; INTEL-02 puts it
on screen.

- **Models** (`data/intelligence/IntelligenceChat.kt`): `ChatMessage` (role, content, time, the tool name and card
  payload an assistant turn showed), `ChatSession` (`displayTitle`: the doctor's title when renamed, else the first
  user message as a short title, else "New chat"; `preview`: the latest message, 90 characters; `matchesQuery`;
  `patientMark`). Stored as Flutter stored them (ISO times, `titleCustom` optional); a bad chat or message is skipped
  instead of losing the whole history, a non-object card is dropped, a missing role is the doctor's.
- **ChatTitles:** the starter-prompt map (EN / UZ / RU), wake phrases ("Hey / Salom / Assalomu alaykum / Привет
  Shifa" and the name's mishears), `stripWakePhrase`, `shortTitle` (48, cut on a word past 24), `previewLine` (90, past
  40), and `detectReplyLanguage`: Uzbek Cyrillic letters → UZ, other Cyrillic → RU, Uzbek marker words → UZ, two
  English function words → EN, else keep the chat's language.
- **ChatContext:** a chat is about a patient from a patient card; clinic-wide cards (schedule, free slots, patterns,
  most visited) never link one, a search names the patient only for a single match, and "Patient" isn't a name.
- **Store** (`ChatSessionStore`, key `shifa_intelligence_chats_v1:<doctorId>`): a cold start opens on an empty chat
  (made if none), history kept; create, goToWelcome, select, delete (the last one leaves a new empty chat; deleting
  the open one opens the newest), rename (keeps `updatedAt`; blank ignored), replaceMessages (moves the chat to the
  top). Another doctor signing in reads their own key. Chat ids get a counter after the time, so two chats in the
  same millisecond can't share the backend's `conversationId`. A turn finishing after its chat was deleted (or another doctor signed in) changes
  nothing; stored messages are a copy of the caller's list; a stored chat without an id gets its own (Codex review).
  Whitespace in the ported patterns is Dart's (`utils/DartWhitespace.kt`, shared with `AiResponseText`).
- **Fix (ticket):** the chats name patients, so they are stored in the encrypted settings (Keychain /
  EncryptedSharedPreferences, the token's store) instead of plain preferences, and capped at 50 chats (oldest go
  first, never the open one; Flutter kept every chat). Flutter's plain-preference history isn't migrated: it was
  written by the web build, which isn't ported.
- **Tests:** `IntelligenceChatTest`: Flutter's seven tests (reply language, wake phrases, starter titles, long titles
  and previews, renamed titles kept, patient link, legacy JSON) plus Russian / Uzbek-Cyrillic detection, skipped bad
  rows, the store's semantics per doctor and the cap.

## INTEL-02 · Intelligence screen shell

Built 2026-11-05. Spec 02 §4.1, §3.1, flows 03/01 §2 and 03/02, ticket INTEL-02. Board `DaIntel` (header, history
sheet, options menu, rename and delete dialogs).

- **Entry:** More → Shifa Intelligence (SHELL-05 already gates it on `ASK_SHIFA_AI` and hides it for clinic staff);
  `MainScreen` now shows `IntelligenceScreen` instead of the placeholder, over the tab, which keeps its stack. Back
  (app bar or system): from a conversation to the welcome, from the welcome to the clinic. However Intelligence is left (back,
  a tab tap), the shell reads today's schedule again (bookings made in the chat change it; Codex review).
- **App bar:** back ("Back" / "Back to clinic"), "Shifa Intelligence" with "Your AI assistant for clinical
  workflows.", History, New conversation, and the conversation's options (Download, Rename, Delete) only while one is
  open (flow 03/02 §12: Flutter showed them on an empty chat and did nothing).
- **History sheet** (`ui/intelligence/IntelligenceScreen.kt`): New conversation, a search over titles, previews and
  every message, the chats newest first; a tile has the chat icon, a person icon for a patient chat, the title (bold
  when open, the open one tinted), how long ago ("Just now", minutes, hours, "Yesterday", "{n}d ago", else the date;
  the phone's clock), the preview unless it equals the title, and its options. Empty: "No conversations yet" / "No
  matching conversations". Screen reader: one button "Title" (+ "Patient conversation").
- **New conversation** opens the empty chat when there is one (Flutter piled up empty "New chat"s); a new one is made
  only when none is empty.
- **Rename:** the field starts with the shown title; Save is off for a blank or unchanged title; the time is kept.
  **Delete:** the destructive dialog; if a reply is streaming in that chat it asks "Stop the current reply and delete
  this conversation?" (INTEL-04 sets the busy flag). Deleting the open chat opens the newest left.
- **Download:** the `.txt` in Flutter's format (header, each message with role, time, tool and pretty card, then the
  chat as JSON; for support, not translated) through the system share sheet (`rememberFileShare`); an empty chat
  says "Nothing to download yet…", a share that can't open says "Could not download conversation" (Flutter's mobile
  build called a stub).
- **Body:** the welcome (INTEL-03) and the thread (INTEL-04) go in the body slot.
- **Checked:** emulator, demo mode: More → Shifa Intelligence, History (the welcome's empty chat listed), back to the
  clinic. iOS compiled, not run.
- **Tests:** `IntelligenceViewModelTest` (welcome on cold start, back to the welcome then the clinic, New conversation
  reusing the empty chat, search over messages, select, rename rules keeping the time, delete of the open chat, export
  name and format, the empty export); screenshots `Intelligence_history`, `Intelligence_historyEmpty`.

## INTEL-04 · Chat thread and composer

Built 2026-11-05 (before INTEL-03, so the welcome's starter cards send through a working thread). Spec 02 §3.3,
§3.7, §4.4 (card slot), flow 03/03, ticket INTEL-04. Board `DaIntel` thread and footer.

- **Turns** (`ui/intelligence/ThreadViewModel.kt`): the typed text (wake phrase stripped; blank or wake-only sends
  nothing) goes to `/ai/morning/stream` with the chat's history, the focus patient as `patientId` and the chat id as
  `conversationId`. The reply language is detected per turn (`ChatTitles.detectReplyLanguage`), else the chat's last,
  starting from the app's. Tokens grow the reply, a tool event shows its card at once (often before the text). At the
  end the question and the answer are saved to the chat (a booked card says its `statusMessage`; a reply without
  words falls back to the card's `spokenHint`). A card with `booked: true` reads the calendar again.
- **History contract** (`data/intelligence/ChatHistory.kt`): every stored message; assistant messages carry Flutter's
  hidden tags (`<appointment_query>` JSON, `<appointment_list>` ids, `<appointment_action>` cancel token,
  `<appointment_focus>` visit), byte for byte (the backend's prompt reads them; ids as Dart's `num.toInt()`). The focus patient is the newest clear
  search match (or the match whose name words the doctor said in the last four messages), else the newest patient card.
  `ChatHistoryTest` holds the golden JSON.
- **Errors:** an empty reply with no card ("Shifa had nothing to say"), the daily limit, the 75 s turn running out
  ("Shifa took too long to reply"), the AI service down (`AI_UNAVAILABLE`, new string `aiUnavailable`; the patient
  briefing uses the same mapping, `data/ai/AiErrors.kt`), else "Something went wrong"; each with Retry, which asks the
  same question again (a failed turn is never saved, so it can't appear twice).
- **One turn at a time:** Send is off while a reply runs; another chat opened, the open one deleted, back, or
  Intelligence leaving the screen stops the running turn first and closes its connection (Flutter left the stream
  open); a turn whose chat is no longer open is never saved. The delete dialog asks to stop a running reply. The reply
  language is kept per conversation (Codex review).
- **Thread UI** (`ThreadScreen.kt`): the doctor's bubbles on the right (78 %, teal 15 %), timestamps "Today · 09:41" /
  "Yesterday · 18:02" / "24 October 2026 · 08:15" on the phone's clock; Shifa's answers with the sparkle, rendered by
  `AiResponseText` once finished (plain while streaming, so a half `**` doesn't flash); "Reviewing…" until the first
  token; the list follows the newest content (to the end of a long reply) while the doctor's last scroll ended at the bottom.
- **Cards:** every card is Flutter's default one for now ("Shifa Intelligence" + the spoken line, error or status);
  INTEL-05…08 add the typed cards.
- **Composer:** the board's chat bar: a 1–4 line field (no border of its own), Send (keyboard Send too), the patient
  line above it ("Patient · Name", or "Patient"), the disclaimer under it. Inside the shell it sits above the floating
  tab bar (it was hidden under it). The mic and dictation come with INTEL-09; TTS of replies with AI-08 / INTEL-09.
- **Checked:** emulator, demo mode: typed "What is my schedule today" → the demo stream's reply and card, saved; back
  to the welcome, History shows "Today's schedule", reopened restores it; "please fail now" → the error row with Retry.
  No real AI call on dev (paid). iOS compiled, not run.
- **Tests:** `ThreadViewModelTest` (stream, card, saved turn, wake phrase stripped, language and chat id in the
  request; a booking's status line and calendar refresh, the next turn's history and focus patient; empty reply, limit
  and Retry without duplicates; AI down; blank and wake-only send nothing), `ChatHistoryTest` (golden history JSON,
  focus patient rules); screenshots `Intelligence_threadStreaming`, `Intelligence_threadError`.

## INTEL-03 · Welcome

Built 2026-11-05 (after INTEL-04). Spec 02 §4.6–4.7, flow 03/01, ticket INTEL-03. Board `DaIntel` welcome.

- **Shown** in Intelligence's body while the open chat is empty and no question is running (`WelcomeBody`,
  `ui/intelligence/Welcome.kt`), scrolling between the app bar and the composer.
- **Greeting:** the 48 dp orb (brand radial gradient, sparkles), "Good morning / afternoon / evening" by the phone's
  hour (before 12, before 17, else), then ", Dr. med. Name." from the profile (`doctorDisplayName`), or just "Good
  morning." until the profile is there (Flutter showed "Good morning, ."); "What can I help you with today?".
- **Starter cards** (title, line, icon): Today's schedule, Find a patient, Manage appointments, Clinical notes; a tap
  sends the prompt in the app's language as if typed (it becomes the thread's first question). One column on phones,
  two from 560 dp; titles wrap at large text; each card is one button "Title. Line".
- **Recent conversations:** the five newest chats that have messages, with the same tile and options (Rename,
  Delete) as History; a tap opens the chat (a running turn stops first).
- **Checked:** emulator, demo mode: the greeting with the demo doctor's name, the starters, recent chats; "Today's
  schedule" sent "Summarize my schedule for today." and the demo reply came back in the thread. iOS compiled, not run.
- **Tests:** `WelcomeTest` (greeting hours, the starters' prompts, every starter prompt in en / ru / uz shortening to
  its starter title, the five newest non-empty chats); screenshots `Intelligence_welcome`, `Intelligence_welcomeFirst`.

## INTEL-05 · Read-only action cards

Built 2026-11-05. Spec 02 §4.4, flows 03/07–03/13, ticket INTEL-05. Board `DaIntel` card (bordered box, brand title,
rows, chips, body).

- **Model** (`data/intelligence/IntelCards.kt`): `IntelCard.of(toolName, payload)` reads the type from `cardType`,
  else the tool's name (`get_today_schedule`, `list_patient_appointments`, `list_free_slots`, `search_patients`,
  `get_most_visited_patients`, `get_clinic_patterns`, `brief_patient`), tolerant of missing fields and numbers sent as
  decimals. Cards INTEL-06…08 draw (propose, cancel, visit facts, documents, dental) and unknown ones are `Other`:
  "Shifa Intelligence" and the spoken line, else the error, else the status.
- **Cards** (`ui/intelligence/Cards.kt`): **Schedule** "Appointments: N", up to 8 rows (time · patient · status),
  "No appointments today."; **Patient appointments** the patient, the period, the count, up to 12 rows (6 Nov 2026 · time
  · status, the row's patient when the header has none), "No appointments in this period."; **Free slots** the day
  and up to 24 time chips (not tappable yet), the hint or "No free slots for this day."; **Patient matches** "Did you
  mean…?" / "No exact match for “query”. Did you mean…?" for a fuzzy search, rows "name · phone", none → the hint or
  "No patients matched."; **Most visited** "name · count"; **Clinic patterns** the shared pattern rows from AI-10
  (flow 03/12: one row component in all three places); **Patient briefing** rendered with `AiResponseText` (Fix:
  Flutter showed it plain). A known card that came back with an `error` (an unknown patient, no briefing)
  shows the spoken hint or the error under its own title, never an empty list (Codex review).
- **Small targets taken:** "+N more" under capped lists (flows 03/07–09), cancelled visits muted (03/07–08),
  statuses through the calendar's status labels (raw for unknown ones). **Open calendar** leaves Intelligence for the
  Calendar tab on the card's day (`CalendarFocus`; Flutter only switched the tab), and the shell reads the schedule
  again as on any exit.
- **Not yet:** backend hint sentences, range labels ("Upcoming") and English date labels show as sent: INTEL-10 ports
  Flutter's card-text localiser. Row taps (open a visit, choose a patient, book a slot), rank numbers and "{n} visits"
  plurals are flow targets left for later.
- **Checked:** emulator, demo mode: "Today's schedule" → the schedule card (no visits on the demo's odd day), Open
  calendar → the Calendar tab. iOS compiled, not run.
- **Tests:** `IntelCardsTest` (each card from backend-shaped fixtures: type from the tool name, caps and "+N more",
  dates, fuzzy search, decimal counts, the brief, unknown cards' line), `ShellViewModelTest` (Open calendar leaves
  Intelligence); screenshot `Intelligence_cards` (every card), thread screenshots re-recorded (their search card is
  now drawn).

## INTEL-10 · Card text localiser

Built 2026-11-05 (before INTEL-06, which needs it). Spec 02 §0, ticket INTEL-10. A stop-gap: the backend is asked for
codes and ISO values instead of English sentences (doc 15 §4); until then this must match Flutter's patterns exactly.

- **Port** (`data/intelligence/CardText.kt`, about 1,750 lines): Flutter's `intelligence_card_text.dart`, its two big
  tables (exact sentences, ~100 sentence patterns with their en / uz / ru templates) converted mechanically from Dart
  and the procedural helpers ported by hand: status labels, ISO and English dates ("20 Okt 2026"), period labels ("Shu
  hafta", "the next 3 weeks", "from … until …"), placeholder names ("Patient" → the app's word), document and fact
  types, and `localizeHint` (exact keys, the cancellation preview, then the patterns, then sentence by sentence, then
  calendar words inside the text). English stays as sent.
- **No platform code:** `CardText(lang, strings, monthShort, cyrillic)`; the screen builds it with `rememberCardText()`
  from the keys it needs (`CardText.KEYS`, resolved through `tr()`, so Cyrillic Uzbek works) and the short month names.
  Its own Uzbek templates are transliterated before the names go in, as `tr()` does: a Latin patient name stays Latin.
  Templated keys are resolved with their placeholder names as arguments (the resources number them `%1$s`…), and the
  cancellation preview is a plural resource, resolved for 1, 2 and 5 and picked by the count's CLDR form (Codex review:
  it showed its key and `%1$s` before).
- **Used by** the read-only cards (statuses, names, periods, English date labels, hint and error lines) and by a
  card's reply line in the thread (a booked card's English status, a spoken hint). INTEL-06…08 use it for their cards.
- **Not ported:** `intelligenceChatTimestamp` (the thread's own `stamp()` already does "Today · 09:41").
- **Checked:** the ported golden test; emulator, demo mode, app in Uzbek: "Bugungi jadval" sent the Uzbek prompt and
  the card read "Bugungi jadval · Uchrashuvlar: 0 · Bugun uchrashuv yo‘q · Kalendarni ochish". iOS compiled, not run.
- **Tests:** `CardTextTest`, built from the generated resources the screen reads: Flutter's golden tests verbatim (labels, cancellation, reschedule, search and booking
  sentences, other hints, Today) minus the timestamp one, the resource placeholders and ru plural forms, and Cyrillic Uzbek keeping names Latin.

## INTEL-06 · Booking and cancellation cards

Built 2026-11-05. Spec 02 §0 and §4.4, flows 03/14 (book), 03/15 (move), 03/16 (cancel by voice), ticket INTEL-06.
Board `DaIntel` card (the same bordered box; buttons from the kit). The cancellation card is the flow's target card
("not in the design").

- **Model** (`IntelCards.kt`): `Proposal` for `propose_appointment` / `propose_reschedule` (patient, current slot, new
  date, slots with their UTC `startAt`, the preferred one, `workflowId`, `booked` / `moved`, the backend's status line)
  and `Cancel` for `cancel_preview` / `cancel_result` (rows "when · patient", skipped count, count, whether the backend
  waits for a "yes": `AWAITING_CONFIRMATION` with an `actionContext`). A booking-conversation turn without a proposal
  (no booking waiting, "which patient?") shows its line only.
- **Two modes, chosen by the payload (no client intent):** with `workflowId` the card only shows and says "Confirm this
  exact booking with Shifa — say or type “yes”." (new key; Flutter fell back to English); the backend books and its
  next payload (`booked` / `moved`, `workflowStatus: COMPLETED`) shows the green line. Without it the app books:
  "Confirm 10:00", "Other times" / "Hide other times", slot buttons → `POST /schedule/book` or
  `PUT /appointments/{id}/change` through `AppointmentsRepository` (calls from `ThreadViewModel`, never the UI).
- **Fixes from the flows' "do not carry over" lists:** no English location strings sent (video sends none; the slot's
  `locationId` / label otherwise), the move sends the new slot's `locationId` (the backend's `ChangeSlotReq` takes it;
  checked in dev's OpenAPI), network failures say "Booking/Reschedule failed. Check your connection and try again."
  (no exception text), the move card gets "Other times" too, superseded proposals are inactive (only the newest
  proposal is live; nothing is live while a turn runs), and a cancellation done refreshes the calendar at once, like
  a booking.
- **Saved outcomes:** the app's own booking is saved into the card (`booked`/`moved` plus the booked time), so a
  reopened chat shows it booked, not bookable; Cancel saves `dismissed` and the card stays hidden (Flutter showed it
  again). `ChatSessionStore.updateCard` changes the one message in place; a turn finishing meanwhile appends to the
  chat as it is now. The backend's "Moved the appointment to…" line (no name) shows as "Appointment moved.".
- **One change at a time** (Codex review): a chat books from one card at a time, also across leaving and coming back
  (the call isn't stopped halfway), and no question is sent while it runs; afterwards the calendar reads the touched
  days again (the visit's old day, the new slot's day, the cancelled visits' days), not only today.
- **Cancellation** (flow 03/16): the app never cancels. The card lists the visits; while the last answer waits, "Yes,
  cancel N" (destructive) and "Keep them" send the doctor's answer as if typed, in the conversation's language with
  words the backend's `AppointmentActionPhrases` takes ("yes" / "ha" / "да"; "no" / "yo'q" / "нет"). The token
  travels in the history as before (INTEL-04). A result lists what was cancelled and what could not be.
- **Demo:** the demo stream answers booking, moving and cancelling words with these cards and "yes" / "ha" / "да"
  with a result; `schedule/book` and `…/change` already succeed without storing.
- **Open (flows' design questions, the defaults taken):** the workflow card has no tappable "Yes, book" (the doctor
  says or types it); the cancel card has buttons (the flow's target) but no per-visit untick (needs the backend).
- **Checked:** emulator, demo mode: "Book Jasur tomorrow" → the proposal, Other times → 11:00 → "Appointment booked."
  with Time 11:00 (that check found the booked time missing; fixed); "Cancel Friday" → the card, "Yes, cancel 2" sent
  "yes", the result followed and the old buttons went; app in Uzbek, "Move Jasur" → "Ko‘chirish taklifi", 11:00 →
  "Uchrashuv ko‘chirildi.". Dev not used (bookings can't be undone there). iOS compiled, not run.
- **Tests:** `IntelCardsTest` (proposal, workflow, finished, no-proposal turns, cancellation shapes), `ThreadViewModelTest`
  (book body and saved card, video/move bodies, 409 / 400 / missing slot, workflow books nothing, dismiss survives the
  next turn, cancellation answers per language, a result refreshes the calendar), `CardTextTest` (visit times, status
  lines), `DemoBackendTest` (demo cards); screenshots `Intelligence_bookingCards`, `Intelligence_cancelCards`,
  `Intelligence_cards` re-recorded (the placeholder proposal is gone).

## INTEL-07 · Clinical cards: draft facts, documents, dental diagram

Built 2026-11-06. Spec 02 §4.4–4.5, flows 03/17 (draft visit facts) and 03/18 (documents, inline viewer, dental),
ticket INTEL-07. Board `DaIntel` card; the inline viewer box and the documents rows are "not in the design" (built in
the card's style).

- **Model** (`IntelCards.kt`): `DraftFacts` (`draft_visit_facts`: appointment, facts {id, type, text}), `Documents`
  (`patient_documents`: up to 12 rows, "+N more", `autoOpenDocumentId`), `Document` (`document_viewer`; missing ids →
  its line), `DentalChart` (`dental_chart`: service names per FDI tooth from `documentation.teeth`, the dentition,
  counts, notes). Error turns (no appointment, no documents, unknown patient) show their spoken line under the card's
  title. Tooth keys "36", "FDI-36" and legacy "UR8" go through `DentalChartCodec.normalizeToothKey`.
- **Fixes:** the dental record names its services under `title` (payload v2), which Flutter's card never read (it
  looked for name / serviceName / label only); without `toothServiceCounts` the entries are counted (Flutter showed no
  badge); fact types come from the string keys (Flutter had an inline table); the documented state is saved into the
  card (`documented`), so a reopened chat can't commit again; a failure says "Could not document the facts…" (no raw
  exception); 0 committed says the facts are already in the timeline.
- **Draft facts** (flow 03/17): rows "Medication: …", "Document everything" commits every listed fact
  (`POST /ai/visit-facts/commit`, `acceptFactIds`) from `ThreadViewModel.documentFacts`, Cancel hides the card for
  good; target taken: "Review on the visit" opens the visit (with its facts panel, where single facts can be kept or
  rejected).
- **Documents** (flow 03/18): rows with the file icon, title (the "Document" placeholder localised) and day; a tap (or
  the backend's pick) opens the file in the card: a grey 360 box with the authenticated download of the documents
  viewer (DOCS-05's ViewModel reused): skeleton while loading (target), the PDF's pages or the image fitted, failed +
  Retry, no access ("You don't have access to this document…"), gone, or a type it can't draw. No zoom inside the
  thread (the gesture conflict of §7); "Open full screen" opens the zoomable viewer, with save and request access.
- **Over Intelligence** (shell): a visit or document opened from a card is pushed onto a small stack over Intelligence
  (`ShellUiState.overIntelligence`); the bar hides, Back returns to the thread as it was, and the next opening of
  Intelligence starts at the thread. INTEL-08's open-visit uses the same path.
- **Dental** (flow 03/18): "Jasur Karimov · 6 Nov 2026", the visit's read-only tooth map (the shared `ToothMap`, adult
  or primary as recorded; no toggle: the chart is the one recorded), a tapped tooth lists up to 6 services, then the
  notes; a chart without services shows the hint.
- **Also fixed (INTEL-04):** the thread didn't jump to a new question when the answer came at once (the demo): the
  jump was decided after a suspension that the next state change cancelled. The follow flag is now set first.
- **Codex review:** a commit or booking still running when the doctor leaves and comes back can't be sent again; each
  card owns its document download; Intelligence keeps its state (scroll, opened document) under a screen opened from
  it; "Review on the visit" opens a video visit as video (the appointment is read first); dental notes show even
  without tooth services.
- **Not built:** "All documents" (opening the patient's documents tab) and per-fact keep/reject in the card (the visit
  panel has it); restored cards download their file again when shown (the file is released when the card leaves the
  screen, to keep memory down).
- **Checked:** emulator, demo mode: "Show Jasur documents" → the list with the CBC opened inline (the demo PDF drawn),
  Open full screen → the viewer, Back → the thread; "Document everything from the visit" → "Documented 2 facts.",
  Review on the visit → the visit with its facts panel, Back → the thread; "Open the dental chart" → tooth 46 →
  "Services for tooth 46 · Cleaning" (the second service below the fold); a question after scrolling up jumps to the bottom. iOS compiled, not run.
- **Tests:** `IntelCardsTest` (facts, documents cap and auto-open, document ids, dental titles and tooth keys, derived
  counts, error turns), `ThreadViewModelTest` (Document everything: one commit with every id, the count saved, a
  failure, no second commit), `ShellViewModelTest` (screens over Intelligence and Back), `DemoBackendTest` (demo
  cards); screenshot `Intelligence_clinicalCards`.
