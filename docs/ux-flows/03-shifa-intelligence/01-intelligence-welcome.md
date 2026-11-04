# Intelligence welcome

| | |
|---|---|
| Area | Shifa Intelligence |
| Who sees it | Doctor only · PRO and PREMIUM (`askShifaAi`) |
| Reached from | More sheet → "Shifa Intelligence"; app bar back arrow inside a conversation; "New conversation" from the app bar or history sheet |
| Screen type | Full screen (body of the Intelligence screen while the active conversation is empty) |
| Current Flutter source | `lib/features/intelligence/presentation/intelligence_welcome.dart`, `intelligence_screen.dart`, `intelligence_session_tile.dart` |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §4.1, §4.6, §4.7, §3.9 · tickets INTEL-02, INTEL-03 |

## 1. Purpose
First thing the doctor sees every time Intelligence opens from a cold start: a greeting, four one-tap starter
prompts and the last few conversations. Used several times a day, typically for a few seconds before typing or
tapping the mic.

## 2. Entry points
- **More sheet → Shifa Intelligence** (doctor shell). Opens Intelligence as a full-screen mode that replaces the tab
  shell; the tab bar disappears. On cold start the store restores history but always opens an *empty* conversation,
  so the welcome shows even when yesterday's threads exist. Within the same app session, leaving and re-entering
  resumes whatever conversation was active.
- **Back arrow in a conversation** → "Back" selects the first empty conversation (or creates one) → welcome.
- **"New conversation"** (app bar icon or history sheet button) → creates an empty conversation → welcome.
- No deep link or push notification opens Intelligence.

## 3. Layout, top to bottom (phone portrait)
1. **App bar** (white, sticky): back arrow (tooltip "Back to clinic" here — it leaves Intelligence), title "Shifa
   Intelligence" with one-line subtitle "Your AI assistant for clinical workflows." (ellipsised), actions: History
   (clock icon), New conversation (comment-plus icon). The overflow menu is **not** shown on the welcome.
2. **Greeting** — "Good morning, Dr Name." / "Good afternoon, …" / "Good evening, …" by the phone clock hour
   (before 12, before 17, otherwise). Name is the doctor's formatted medical name from the profile.
3. **Prompt line** — "What can I help you with today?"
4. **Four starter cards** (one column on phones; two columns only ≥ 560 dp): icon, title, one-line subtitle.
   | Card | Subtitle | Sends this text as if typed |
   |---|---|---|
   | Today's schedule | Ask about your day | "Summarize my schedule for today." |
   | Find a patient | Search records | "Help me find a patient." |
   | Manage appointments | Reschedule visits | "Help me manage my appointments." |
   | Clinical notes | Review documentation | "Help me review my clinical notes." |
   The prompt is sent in the app language (Uzbek and Russian prompt strings exist).
5. **Recent conversations** (only when at least one non-empty conversation exists): section title and a bordered
   list of up to **5** sessions, newest first. Each tile: chat icon, optional person icon (patient-linked thread),
   title, relative time, one-line preview, overflow menu (Rename / Delete). See file 02 for the tile anatomy.
6. **Composer** (sticky bottom): text field "Ask Shifa…", dictation button, mic button, send button. No patient
   context line on the welcome. Below it the disclaimer "AI-generated responses may require review."

The whole page scrolls; app bar and composer stay fixed.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Greeting | phone clock | hour < 12 morning, < 17 afternoon, else evening. Device time, not practice time (acceptable — it is about the doctor's own day) |
| Doctor name | profile first + last name | "Dr Lastname Firstname"-style formatter already used on Home; falls back to empty → "Good morning, ." today (bug, §12) |
| Session title | custom title, else short form of the first user message (≤ 48 chars, wake words stripped), else "New chat" | single line, ellipsis |
| Relative time | session `updatedAt` vs now | "Just now" < 1 min · "{n} min ago" < 60 · "1 hour ago" / "{n} hours ago" < 24 h · "Yesterday" < 2 d · "{n}d ago" < 7 d · else `d.M.yyyy` (device local) |
| Preview | last non-empty message, ≤ 90 chars on a word boundary + "…" | hidden when identical to the title |
| Patient icon | any stored card in the thread names a patient (search match, propose, documents, brief…) — clinic-wide cards (schedule, free slots, patterns, most visited) never count | person icon with tooltip "Patient conversation" |

## 5. Primary flow (happy path)
1. Doctor taps "Shifa Intelligence" in the More sheet → the shell body is replaced instantly (no transition today).
2. A centred spinner shows until the local store is loaded (usually < 100 ms). Target: skeleton of greeting + four
   cards, never a spinner.
3. Welcome renders. Doctor taps "Today's schedule".
4. The welcome is replaced by the thread (file 03) with the prompt as the first user bubble and "Reviewing…" below it.

## 6. Screen states
- **First load:** spinner until the conversation store is read (target: skeleton — greeting line, prompt line, four
  72 dp card placeholders, no recent list).
- **Empty:** no recent conversations → section 5 is simply absent; the four cards are always present. There is no
  illustration.
- **Error on load:** n/a — the store is local; a corrupt file decodes to an empty list silently and a fresh
  conversation is created.
- **Offline:** welcome renders fully from local data. Tapping a starter prompt sends and fails in the thread with
  the stream error + Retry (file 03). No offline notice on the welcome itself (target: show the global offline
  banner, keep the cards tappable).
- **Refreshing:** n/a.
- **Partial data:** profile not loaded yet → greeting without a name ("Good morning, ." today). Target: omit the
  comma and name until the profile is present.
- **Permission needed:** none on this screen (mic permission is asked when the mic is tapped, file 04).
- **Locked by tier:** below PRO the screen is never reached; the More tile shows a generic "Error" toast today.
  Target: tile shows a lock and "Available on Pro"; tapping opens the subscription screen.
- **Role difference:** clinic staff never see the tile or the screen.

## 7. Interactions and secondary flows
- **Tap starter card** → sends the prompt text → thread view, user bubble, pending assistant block → reply streams.
- **Tap recent tile** → that conversation becomes active → thread view scrolled to the end.
- **Tile overflow → Rename** → dialog "Rename conversation" with the current title prefilled, hint "Conversation
  title", Cancel / Save. Save with an empty or unchanged title does nothing. Rename keeps the time stamp (the tile
  does not jump to the top).
- **Tile overflow → Delete** → dialog "Delete conversation" / "Delete this conversation? This cannot be undone." /
  Cancel · Delete → tile disappears; if it was the last one a new empty session is created.
- **History icon** → bottom sheet (file 02).
- **New conversation icon** → creates another empty session (the welcome looks unchanged; a second "New chat" now
  exists in history — see §12).
- **Back arrow** → leaves Intelligence to the clinic shell on the last tab; refreshes appointments.
- **Mic / dictation / send** → identical to the thread (files 03–05); the first turn switches the view to the thread.
- **Pull-to-refresh:** none. **Long-press:** none.
- **System back (Android):** Intelligence is a shell mode, not a route, so system back currently acts on the shell
  (may close the app). Target: Intelligence is a pushed full-screen route; system back = app bar back.

## 8. Edge cases
- Doctor with 100+ conversations: only 5 show here; the rest are in History. Store has no cap (target: cap at 200,
  oldest trimmed).
- Very long first message → title cut at 48 chars on a word boundary with "…"; preview at 90.
- Title and preview identical (one-message thread) → preview hidden.
- Session whose only user message was a wake-word phrase → title falls back to "New chat".
- Greeting at exactly 12:00 → "Good afternoon"; at 17:00 → "Good evening".
- Device clock far from practice time → greeting may feel wrong; accepted.
- Profile load fails → greeting without name; cards still work.
- Language switched while on the welcome → greeting, card titles and relative times re-render in the new language;
  the prompt sent later is in the new language.
- Two taps on a starter card in quick succession → second tap ignored while busy.
- Font 200 %: cards grow in height (min 72 dp); two-column layout never applies on phones; titles may wrap to 2
  lines — allow wrapping, never ellipsis on card titles.
- Screen reader: each card is one button announced "Title. Subtitle"; each tile is one button "Title" (+ "Patient"
  when patient-linked); the overflow menu is a separate focus stop.
- Conversation deleted from another tile while its rename dialog is open → Save does nothing (id gone).
- Clinic staff token (role change mid-session) → the shell never shows this screen; nothing to handle here.

## 9. Validation and messages
| Field/action | Rule | When checked | Message | Where |
|---|---|---|---|---|
| Rename title | trimmed, non-empty, different from current | on Save | none (silently ignored) — target: disable Save until valid | — |
| Delete | confirmation required | on tap | "Delete this conversation? This cannot be undone." | dialog |
| Starter prompt while a turn is running | ignored | on tap | none | — |
| Open Intelligence below PRO | blocked | on tile tap | today "Error" toast; target "Available on Pro" | tile / banner |

## 10. Navigation out
- Back arrow → clinic shell (last tab). Nothing to keep; appointments, notifications and analytics are refreshed.
- Starter prompt / recent tile / any composer action → thread (same screen, body swaps).
- History → sheet on top; welcome stays underneath.
- Tier downgrade while open (profile refresh) → the voice session body renders nothing; target: show the lock state.

## 11. Copy
| Key | English |
|---|---|
| `shifaIntelligence` | Shifa Intelligence |
| `intelligenceEmptyHint` | Your AI assistant for clinical workflows. |
| `intelBackToClinic` | Back to clinic |
| `intelHistory` | History |
| `intelNewConversation` | New conversation |
| `goodMorning` / `goodAfternoon` / `goodEvening` | Good morning / Good afternoon / Good evening |
| `intelHowCanIHelp` | What can I help you with today? |
| `intelWelcomeSchedule` / `intelWelcomeScheduleDesc` | Today's schedule / Ask about your day |
| `intelWelcomeFindPatient` / `intelWelcomeFindPatientDesc` | Find a patient / Search records |
| `intelWelcomeAppointments` / `intelWelcomeAppointmentsDesc` | Manage appointments / Reschedule visits |
| `intelWelcomeNotes` / `intelWelcomeNotesDesc` | Clinical notes / Review documentation |
| `intelPromptSchedule` | Summarize my schedule for today. |
| `intelPromptFindPatient` | Help me find a patient. |
| `intelPromptManageAppointments` | Help me manage my appointments. |
| `intelPromptClinicalNotes` | Help me review my clinical notes. |
| `intelRecentConversations` | Recent conversations |
| `newChat` | New chat |
| `intelPatientConversation` | Patient conversation |
| `timeJustNow` / `timeMinAgo` / `hourAgo` / `hoursAgo` / `yesterday` / `intelDaysAgo` | Just now / {n} min ago / 1 hour ago / %s hours ago / Yesterday / {n}d ago |
| `intelRename` / `intelRenameConversation` / `intelRenameHint` | Rename / Rename conversation / Conversation title |
| `intelDeleteConversation` / `intelDeleteConversationConfirm` | Delete conversation / Delete this conversation? This cannot be undone. |
| `intelComposerHint` | Ask Shifa… |
| `intelDisclaimer` | AI-generated responses may require review. |
| **NEW** `intelLockedPro` | Available on Pro — upgrade to talk with Shifa. |

Note: `shifaIntelligence`, `intelCard*`, `intelHowCanIHelp`, `intelWelcomeSchedule`, `newChat` and several other
keys have English values only in the Dart map, not in `docs/kmp-spec/strings/en.json`; the strings export must be
refreshed before the Kotlin port.

## 12. Known problems in the current app (do not carry over)
- "New conversation" on an already-empty welcome creates a second empty session; several "New chat" rows pile up in
  History. Target: reuse the existing empty session.
- Greeting renders "Good morning, ." when the profile is not loaded.
- Centred spinner on first load.
- BASIC doctors get a generic "Error" toast instead of an upgrade message.
- Intelligence is a shell mode, not a route: Android system back does not return to the clinic.
- Conversations are stored unencrypted on the device (patient names inside).

## 13. Open questions for design
- Should the welcome show the mic as the hero action (large orb) rather than the same composer as the thread?
- Do we keep four fixed starter prompts or make them contextual (e.g. "Brief my next patient" when a visit is due)?
- Should recent conversations be grouped by day instead of a flat five?
