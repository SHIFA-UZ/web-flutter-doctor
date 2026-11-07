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

## CHAT-04 · Image attachments

Built 2026-11-05. Spec 07 §1.3–1.4, ticket CHAT-04, flows 07/03 and 07/05, board `DaChat` (attach sheet, image
bubble, viewer).

- **Attach:** a paperclip left of the field opens "Attach file": Select Image (the system photo picker, no permission)
  and Take Photo (asks for the camera; refused → a strip "Camera permission denied" with Open settings). Select Document and Record Voice
  Message join with CHAT-05 and CHAT-06.
- **Compression:** the shared picker (`utils/ImagePicker`) already gives an upright JPEG, longest side 1600 px,
  quality 85 (HEIC and PNG too), so nothing is compressed again here; Flutter compressed only above 500 KB, to 1920 px.
- **Sending:** the photo shows at once from the phone with ◷ and "Uploading file..." above the composer (Flutter:
  SnackBars); it is uploaded (`upload-attachment`, part `file`), then sent as `type: image` with the URL, file name
  and size, in order with texts. Over 20 MB: "The file is too large (max 20 MB)" and nothing is sent (Flutter had no
  cap; the backend takes 25 MB): the cap is on the JPEG sent, not the original (the picker samples large originals).
  A failed upload or send keeps it with "Not sent. Tap to retry"; once uploaded, a retry only sends again.
- **Image bubble:** a 240 square cropped to fill, in the bubble's shape, the time (and my tick) on a dark pill;
  skeleton while loading, "Failed to load image" when it can't, "Image not available" without a URL. Read out as
  "Patient, 08:31, Photo". A tap opens the viewer.
- **Viewer:** black, full screen, fit, pinch zoom 0.5×–4× and pan, double tap for 2× (the documents' `zoomable`,
  given a `min`), a close button; Back closes it. Not built: swipe between photos, save/share (none in Flutter).
- **Tests:** `ThreadViewModelTest` (a photo shows at once, uploads, then sends `image` with URL, name and size; a failed
  upload keeps it and Retry uploads again; a failed send after the upload only sends again; over 20 MB nothing is sent
  and the banner says so). Screenshot
  `Chat_threadImages`.
- **Emulator (demo):** paperclip → Select Image → the system picker → a photo: it showed at once, uploaded, and was sent
  ("09:24, Photo, Read"); the demo's upload URL doesn't load (demo files never leave the phone), so the tile says
  "Failed to load image" there. Dev has no conversations to show a real photo. Demo mode is off again.

## CHAT-05 · Document attachments

Built 2026-11-05. Spec 07 §1.3–1.4, ticket CHAT-05, flow 07/06, board `DaChat` (document bubble).

- **Pick:** "Select Document" in the attach sheet. The shared document picker now takes a kind
  (`DocumentKinds.ChatAttachment`: pdf, doc, docx, jpg, jpeg, png; Android's documents UI with those types, iOS's
  picker with their `UTType`s); patient documents and certificates keep `Pdf`.
- **Send:** pdf, doc and docx go as a `document`, jpg/jpeg/png as an `image` (07 §1.4: shown as a photo); anything else
  "Only PDF, Word and photo files can be sent."; over 20 MB the size banner; a pick that can't be read "Error uploading
  file". Shown at once, uploaded with its own
  content type (`mimeOf`), sent with name and size, retry as for photos (only the send once uploaded).
- **Bubble:** an icon tile by type (PDF/Word, photo, other), the name (2 lines, "Document" without one), "242.2 KB ·
  08:31" and my tick, in the bubble's colours, at most 280 wide. Read out as "Patient, 08:31, Document,
  blood_test_results.pdf". A tap opens the URL outside the app (the in-app browser: the system shows the file or hands
  it on), "Cannot open document URL" when nothing can; without a URL the card is off with "File unavailable". No
  in-app preview (flow open question).
- **Tests:** `ThreadViewModelTest` (a PDF goes as a document with name and size, a PNG as an image, an .exe is refused
  without uploading, an unreadable pick says so), `ChatRulesTest` (B/KB/MB with one decimal). Screenshot `Chat_thread` now shows the PDF bubble.
- **Emulator (demo):** Select Document showed only pdf/jpg/png (the CSVs in Downloads hidden); the PDF went out as
  "Document, Uchrashuv hujjatlari - 2026-07-21 00_00.pdf". Opening it needs a real URL (demo files stay on the phone).
  Demo mode is off again.


## CHAT-06 · Voice messages

Built 2026-11-05. Spec 07 §1.3–1.4 and §7, ticket CHAT-06, flow 07/04, board `DaChat` (voice bubble) and the recorder
pill from the visit dictation (`RecordingPill`, now shared: level, seconds, busy, Cancel, a confirm label).

- **Record:** "Record Voice Message" in the attach sheet asks for the microphone (refused → the permission strip with
  Open settings, as for the camera) and starts at once; the pill replaces the composer (the draft stays), with the
  live level, an elapsed counter (flow §12: Flutter had none) and the flow's hint. Cancel stops and drops the file;
  Send (read out "Send", not "Process") stops and sends. A playing note pauses first, and notes
  don't play while recording (on iOS that would take the audio session from the microphone). Leaving the thread drops
  the recording (the recorder is the screen's). Going to the background (Home, the lock, a call) stops the microphone
  and keeps the clip: "Recording interrupted — send or discard?" with Send and Discard (flow §7–8 target). Until answered, the attach sheet has no "Record Voice Message" (a new one would drop the clip).
- **Send:** under 1 s "Recording too short" (flow §6 target); a recorder that couldn't finish the file "Error recording
  voice" (never a silent drop); otherwise `voice_<epochMs>.m4a` (`audio/mp4`) shows at
  once as a pending voice bubble, uploads, then sends as `voice` with `duration` in seconds; a failed send keeps the
  uploaded URL, so Retry only sends (as for photos and documents).
- **Play:** `ShifaChatVoiceBubble` with play/pause, the progress line, position and total (the message's `duration`
  until the player knows it). One note at a time (the shared `AudioPlayer`); finishing resets it. A stream that fails
  ("Couldn't play the voice message", flow §9 NEW): Android's `MediaPlayer` error, iOS's item status `failed`. No
  seeking, speed or 5-minute cap (flow open questions).
- **Tests:** `ThreadViewModelTest` (a voice note goes out as `voice` with its seconds and an m4a name; < 1 s and a
  failed stop are refused without uploading, each with its banner). Screenshots `Chat_threadVoice` (received playing, sent, pending) and `Chat_threadRecording`.
- **Emulator (demo):** recorded 12 s, sent ("Voice Message, Read", 0:12); Cancel returns the composer; playing a demo
  note (its URL is fake) showed the banner after about 40 s, when `MediaPlayer` gave up; Home while recording, back:
  the interrupted strip, Send sent the 3 s clip. The < 1 s path is too quick
  for uiautomator (unit-tested). Demo mode is off again.
- **Not built:** a loading state while a stream prepares (the icon shows pause at 0:00 until it plays or fails), the
  list preview for voice (the backend's `lastMessage` text, shown as sent; CHAT-02).

## CHAT-07 · Unread badge and open chat with a patient

Built 2026-11-05. Spec 07 §1.2 (badge), §1.6, ticket CHAT-07, flow 07/07, board `DaPatients` (overview "Quick Actions").

- **Patient → Send message:** the Overview's "Quick Actions" card (two-column tinted tiles as on the board): New
  appointment (the slot picker), Send message, Create document (the upload options). "More actions" waits for the ⋮
  sheet (no dead buttons). Send message selects the Chat tab with the thread over the list (whatever the Chat stack
  held goes); the patient stays in the Patients tab, and Back from the thread is the list (flow §10).
- **Resolution:** `POST /messages/conversations/start {recipientPatientId}` once. The backend finds the doctor's
  conversation with the patient or makes one (`findOrCreateConversation`, no message sent), so the app no longer reads
  the whole list first (flow §12 #1). While it answers, the header shows the patient's name and photo over the
  thread's skeleton (flow §3 target: no list flash); a new one shows "Say hello to …". Failure: "Failed to start
  conversation" (or the offline message) and back to the list, said only while that opening is on screen. Each
  opening asks again. Typing while it resolves goes on to the thread's draft.
- **Badge:** still polled every 10 s in front (SHELL-03); a successful read (`markRead`, from the list or the thread)
  now reads the count at once (`ChatReads`), so the pill drops without waiting; only the newest ask's answer is
  shown, so a slow poll from before the read can't put the old count back.
- **Not built:** Home's hero "Message" (flow 07/07 §2: not on the `DaHome` hero, and Flutter's caller
  `home_timeline_section.dart` is dead code, spec 02 §162); "Message patient" on the appointment detail (flow NEW,
  folder 05); the push entry (PUSH-03); composer focus on a new conversation.
- **Tests:** `PatientChatViewModelTest` (one start call with the patient; a refused start says so and gives up),
  `ShellViewModelTest` (the Chat tab on the thread over the list, Back to the list, the patient still in Patients; a
  read refreshes the badge at once). Screenshots `PatientDetail_overview` and `_sparse` now show Quick Actions.
- **Emulator (demo):** Jasur (has a conversation) → his thread; Back: the badge 3 → 2 at once, the Patients tab still
  on Jasur. Malika (none) → "Say hello to Malika Tursunova", then in the list as "No messages". Demo mode is off again.
  Dev: the start request shape checked against `/v3/api-docs` (no write: a started conversation can't be deleted).

## BE-14 · Newer messages only (2026-11-07)

The open thread's 5 s polls ask `?after=<newest id held>` and merge the answer (the server's copy of a message wins); a
full read every sixth poll keeps read ticks on older messages current. A backend that ignores `after` sends everything,
which merges the same way. Push-driven refresh waits for PUSH-02. Test: `ThreadViewModelTest`.
