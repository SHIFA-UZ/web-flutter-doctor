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

## CHAT-02 · Conversation list

Built 2026-11-05. Spec 07 §1.1, ticket CHAT-02, flow 07/01, board `DaChat` (list pane).

- **Where:** the Chat tab's root (`ui/chat/ChatListScreen`), for doctors and clinic staff alike (no tier gate). A card
  or a search result opens `ShellRoute.Conversation(id)`, a placeholder until CHAT-03.
- **Cards:** photo (the brand fallback without one), name ("Patient"/"Doctor" when blank, flow §8), last message or "No
  messages" (bold and dark while unread), the last activity in the **practice** zone: "08:24" today, "Yesterday",
  else "3 Nov" (Flutter: the phone's "HH:mm" even for old chats), and the unread pill capped at "99+". Read out as
  "Name, last message, time, N unread".
- **Order and filter:** a sheet "Filter conversations" from the sliders button (Newest first by default, Oldest
  first, Unread only newest/oldest; sentence case now); the button turns brand while a non-default choice is on.
  The choice lasts the session.
- **Search:** "Search doctors and patients" asks the server 300 ms after typing stops (Flutter: each keystroke);
  results show photo, name and "Doctor"/"Patient". A result opens the existing conversation with that person and
  role, else starts one (no message is sent); the search clears. "Failed to start conversation" on failure.
- **Freshness:** read on coming back, on pull-to-refresh, and 5 s after each answer **only while the app is in front**
  (BE-14: no push-driven refresh yet; nothing polls behind the app's back). A read already out is never cancelled by
  the next one, so a slow backend still loads. Opening a card marks it read on the server and
  drops its pill at once; the tab badge follows at the shell's next count.
- **States:** skeleton cards on the first read; "No conversations yet" with "Search for a patient or doctor above to
  start one."; with the unread filter "No unread conversations" with "Switch the filter to see all conversations.";
  a failed read keeps the cards and shows "Couldn't load conversations" with Retry; "No users found"; a failed search
  "Could not load" with Retry.
- **Not built:** the tablet two-pane layout (phone app); the offline banner (flow §6 target; the error banner covers a
  failed read).
- **Tests:** `ChatRulesTest` (order and filter, today/yesterday/date on the practice's day, 99+),
  `ChatListViewModelTest` (reads only in front, search after typing stops, a known person opens their conversation and
  marks it read, a new one starts, the unread filter). Screenshots `Chat_list`, `Chat_loading`, `Chat_empty`,
  `Chat_search`.
- **Emulator:** dev: "No conversations yet", "Ja" found a patient (not tapped: starting can't be undone). Demo: three
  cards ("Dilnoza Rahimova, blood_test_results.pdf, 08:24, 2 unread" …), opening Jasur's cleared its pill and the tab
  badge went from 3 to 2. Demo mode is off again.

## CHAT-03 · Conversation thread

Built 2026-11-05. Spec 07 §1.2–1.3, ticket CHAT-03, flow 07/02, board `DaChat` (thread pane).

- **Where:** `ShellRoute.Conversation(id, opened, known)` pushed over the list (the tab bar hides); `known` is the
  list's card, so the header shows before the read. `ui/chat/ThreadScreen` + `ThreadViewModel`, one per conversation
  for the session, so a draft and a failed text survive going back to the list.
- **Header:** back, photo, the name and "Patient"/"Doctor"; a patient's name is brand, underlined and opens their
  record (Back returns to the thread); a colleague's is plain.
- **Messages:** every message, oldest at the top, opened at the **newest** (Flutter opened at the oldest), with a
  divider per practice day ("Yesterday", "Today", "3 Nov"). The board's bubbles: mine right on a light grey, theirs
  left on light teal with "Patient"/"Doctor" above, at most 82 % wide; the time (practice time, Flutter: the phone's)
  and on mine the board's ticks (✓ sent, ✓✓ delivered/read) inside. Links (`http(s)://`, `www.`) are underlined and
  open in the browser; long-press selects and copies. System lines are centred italic. Images, voice notes and documents show as a labelled bubble
  ("📄 blood_test_results.pdf") until CHAT-04…06 give them their own. Screen readers hear "Patient, 08:37" and then
  the text as its own node (so its links work), or the attachment's type and name in words.
- **Sending:** Send is off while the trimmed text is empty (Flutter sent empty texts); the text shows at once with ◷
  and the field clears (a second tap has nothing to send); texts go one at a time, in order; the server's message
  takes its place. A failure keeps it with "!" and "Not sent. Tap to retry" in red (Flutter: a SnackBar with the raw
  error); a tap sends it again once.
- **Freshness and read:** read on open and 5 s after each answer **only while in front** (BE-14); marked read on open
  and again whenever something new comes from the other side (Flutter: only on open); a failed acknowledgement is
  tried again at the next answer. A sent message stays even if a read that started before it answered lacks it. A new message from them while
  scrolled up shows "↓ New message" instead of jumping; own sends scroll to the bottom.
- **States:** skeleton bubbles on the first read; "Say hello to {name}" for a new conversation; "Couldn't load
  messages" with Retry (a failed refresh keeps the messages and shows the banner).
- **Not built:** typing indicator (no transport), offline banner, "This conversation is no longer available", long-text
  "Show more" (open question), suppressing the push for the open conversation (PUSH tickets).
- **Tests:** `ThreadViewModelTest` (read on open and on a new incoming only, optimistic send in order with the field
  cleared and blanks refused, a failure kept and retried), `ChatRulesTest` (a divider per practice day across
  midnight, pending last). Screenshots `Chat_thread`, `Chat_threadEmpty`, `Chat_threadLoading`.
- **Emulator (demo):** opened Dilnoza's thread at the newest, sent "Thanks Dilnoza, see you on Friday" (appeared at the
  bottom, ticked), the header opened her record and Back returned. Demo mode is off again. Dev has no conversations.

