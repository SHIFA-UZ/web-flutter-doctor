# Activity feed ("Patient activity" card)

| | |
|---|---|
| Area | Home & analytics |
| Who sees it | Doctor only, BASIC+ |
| Reached from | Home dashboard, under the Attention required card |
| Screen type | Region (card) of a tab root |
| Current Flutter source | `lib/features/home/presentation/widgets/home_activity_feed.dart`, `lib/features/notifications/presentation/notification_ui_helpers.dart` |
| Spec | `docs/kmp-spec/02-home-ai-intelligence.md` §1.6; ticket HOME-04 |

## 1. Purpose
A read-only timeline of what recently happened around the doctor's patients (bookings, cancellations, payments, documents, chat, tasks), so the doctor can catch up after a visit without opening the inbox. Glanced at, rarely acted on.

## 2. Entry points
- Part of Home. Uses the same notification list as the Attention centre and the bell badge (one request). Refreshed with the Home refresh triggers only — it is not live despite its subtitle.

## 3. Layout, top to bottom (phone portrait)
1. Card header: "Patient activity" (h2) + subtitle "Live updates from your clinic".
2. Body, one of:
   - **Skeleton** 180 px, 4 lines.
   - **Rows**, max 8, separated by hairline dividers (20 px tall): left a relative time caption in tabular figures; then an 8 px coloured dot; then title (14 sp medium, unlimited lines today) and message (body 2, max 2 lines, ellipsis).
   - **Empty**: "No recent activity" (body 2, 24 px vertical padding, left aligned).
   - **Error**: "Error" (target: error row with Retry).
3. Rows are **not tappable** and there is no "see all" link.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Rows | all notifications (read and unread) sorted newest first, first 8 | — |
| Time | creation time | "Just now" / "{n} min ago" / "HH:mm" / "Yesterday HH:mm" / "Oct 24 - 09:30"; phone clock today → practice timezone in the rewrite |
| Dot colour | notification type text | contains DOCUMENT → info blue; APPOINTMENT → teal; TASK → amber; else grey |
| Title | localised type label; unknown type → backend English title | wrap allowed (target: 1–2 lines) |
| Message | localised message with patient name (fallback "Patient") and visit time in practice timezone | 2 lines |

### Type → row treatment (today's rules, keep them)
| Notification type (backend code) | Dot colour | Localised title | Example message |
|---|---|---|---|
| APPOINTMENT_BOOKED_BY_PATIENT | teal | New appointment | "{name} booked for 27 Oct 10:00" (visit time in practice timezone) |
| APPOINTMENT_CANCELLED / _BY_PATIENT | teal | Appointment cancelled | "{name} cancelled the visit" |
| APPOINTMENT_RESCHEDULED_BY_PATIENT / APPOINTMENT_CHANGED | teal | Appointment changed | "{name} rescheduled the visit" |
| APPOINTMENT_PAID_BY_PATIENT | teal | Payment received | "{name} paid for the visit" |
| APPOINTMENT_REMINDER / APPOINTMENT_ASSIGNED / APPOINTMENT_MISSED | teal | per type | fixed sentences |
| DOCUMENT_UPLOADED / DOCUMENT_ACCESS_REQUEST / _APPROVED / _REJECTED | blue | Document … | "{name} shared a document" |
| TASK_ASSIGNED / TASK_COMPLETED | amber | Task assigned / Task completed | task name |
| CHAT_MESSAGE / NEW_MESSAGE | grey | New message | "You have a new message" |
| CARE_PARTNERSHIP_INVITE / _ACCEPTED / _PROGRESS | grey | Care partnership … | fixed sentences |
| AI_SCRIBE_READY, unknown | grey | backend title (English) | backend message |
The colour is decided by substring ("DOCUMENT", "APPOINTMENT", "TASK") so new backend types inherit a colour automatically; design the four dot colours as semantic tokens (info / brand / warning / neutral).

## 5. Primary flow (happy path)
1. Doctor finishes a visit and returns to Home → the list reloads → the card shows "2 min ago · Appointment paid · Karimova D. paid for the visit", "09:40 · Document uploaded · …", down to 8 rows.
2. Doctor reads and scrolls on; nothing to tap. For action the doctor uses the Attention centre or the Notifications tab.

No decision points → no diagram.

## 6. Screen states
- **First load:** 180 px skeleton, 4 lines, inside the card frame.
- **Empty:** "No recent activity" (new account, or notifications purged).
- **Error on load:** "Error" today; target "Could not load activity · Retry".
- **Offline:** last loaded rows remain for the session.
- **Refreshing:** today the card collapses to a skeleton on every notification reload (including after an Attention tile is tapped); target: in-place update with new rows inserted at the top.
- **Partial data:** n/a (single source).
- **Permission needed:** n/a.
- **Locked by tier:** n/a.
- **Role difference:** doctors only.

## 7. Interactions and secondary flows
- **Tap row** → nothing today. Target: same routing as an Attention tile but **without** marking read (§13), or open the Notifications tab scrolled to the item.
- **Tap header** → nothing today; target: "See all" → Notifications tab.
- **Pull-to-refresh on Home** → reload.
- **Scroll inside the card** → none; the card is as tall as its 8 rows and scrolls with the page.
- **Text selection / copy** → not offered; patient names must not be copyable from a glance surface (HIPAA posture, §13 of doc 01).
- **Divider taps** → n/a.

### Row anatomy (target)
| Element | Rule |
|---|---|
| Time column | fixed 64 px, right-aligned, tabular figures, caption colour |
| Dot | 8 px, semantic colour token, vertically centred on the title's first line |
| Title | 14 sp medium, max 2 lines |
| Message | body 2, max 2 lines, secondary colour; omitted when empty |
| Row spacing | 12 px above/below; hairline divider between rows, none after the last |

## 8. Edge cases
- 0 notifications → empty copy; 1 → single row, no divider; 8+ → exactly 8, no "more" indicator today (target: footer link).
- Many events in the same minute → identical "Just now" captions; order is by creation time, stable.
- Time captions of different lengths ("Just now" vs "Oct 24 - 09:30") → the dot column shifts per row because the time column is intrinsic width; target: fixed 64 px time column or time moved to the right edge.
- Very long titles (unknown backend types) → wrap to many lines today; target: 2 lines max.
- Message with a long patient name + date → 2 lines ellipsis.
- Midnight rollover / timezone mismatch → captions wrong by the phone offset today; in the rewrite "Yesterday" is decided in practice timezone.
- Language switch → labels and time words re-render; messages built from English backend text for unknown types stay English.
- 200 % font → rows grow; keep the dot aligned to the first text line.
- Session expired during reload → logout.
- Notification created in the future (server clock ahead of the phone) → negative difference → "Just now" today; keep that behaviour.
- 8 rows all from one patient (bulk document upload) → repetitive titles; target: collapse consecutive same-type rows from the same patient into one ("Karimova D. uploaded 5 documents").
- Read vs unread → no visual difference in this card (by design: it is a history, not an inbox).
- Very old account with notifications purged server-side (retention policy, UNVERIFIED) → empty copy even though the doctor is active; wording must not imply "no patients".
- Screen reader: each row reads "time, title, message"; rows are not buttons.

## 9. Validation and messages
| Action | Rule | When | Message (English) | Where |
|---|---|---|---|---|
| Load | failure | on response | target **NEW** "Could not load activity" + "Retry" | inside card |

## 10. Navigation out
- None today. Target: optional row tap → notification target (see Attention centre §5), "See all" → Notifications tab. Home state kept.

## 11. Copy
`patientActivity` "Patient activity" · `patientActivitySubtitle` "Live updates from your clinic" (misleading: not live — consider "Recent updates from your clinic" **NEW**) · `noRecentActivity` "No recent activity" · `error` "Error" · time words as in the Attention centre file · notification labels/messages from doc 07. **NEW:** "See all", "Could not load activity", "Retry".

## 12. Known problems in the current app (do not carry over)
- Subtitle says "Live" but data refreshes only on Home refresh triggers (60 s cooldown) — no polling.
- Rows are dead (no tap), title has no line limit, time column misaligns dots.
- Phone-clock relative times (README bug 9).
- Card re-skeletons on every notification reload.
- Shows the same items as the Attention centre directly above it (unread ones appear twice on one screen).

## 13. Open questions for design
- Keep this card at all, given the overlap with the Attention centre and the Notifications tab? Alternative: merge into one "Inbox" card with a filter (Needs action / All).
- Make rows tappable without marking read?
- Group by day ("Today", "Yesterday") when more than 8 rows are allowed?
