# Care partnership detail (accept / decline / cancel / complete, progress updates)

| | |
|---|---|
| Area | Patients, documents & forms — care partnerships |
| Who sees it | Both doctors of the partnership (initiator and partner); all tiers |
| Reached from | Care partnerships list tile; after sending an invite (replaces the Find screen); target: push/notification "partnership invite" |
| Screen type | Full screen, pushed |
| Current Flutter source | `lib/features/care_partnerships/presentation/care_partnership_screens.dart` (`CarePartnershipDetailScreen`), `lib/features/care_partnerships/data/care_partnership_repository.dart` |
| Spec | `docs/kmp-spec/06-patients-forms-documents-care.md` §5.1–5.4, tickets CARE-01, CARE-03 |

## 1. Purpose
One co-treatment: who, for which patient, why, and its state. The partner answers the invite here; the initiator can withdraw it; once active both post short progress notes and either can mark it completed. Used a few times per partnership.

## 2. Entry points
- Care partnerships list → tile (arrives with the partnership id).
- Find therapy partner → after "Send invite" (replace navigation; back goes to the patient).
- Target: notification "Dr. X invited you to co-treat a patient" → opens here; the patient's Activity feed (file 11).

## 3. Layout, top to bottom (phone portrait)
1. **App bar** — back · title "Partnership".
2. **Header block** — patient name (h2; fallback "Patient") · "Status: {status}" (today raw code → target status chip) · "{initiating doctor} ↔ {partner doctor}" (target: two avatar+name rows with role captions "Invited by" / "Partner", and the specialty requested) · the invite message (quoted) when present · a hint line by state and role:
   - PENDING, I initiated: "Invite sent. Waiting for {partner} to accept."
   - PENDING, I am the partner: "{initiator} invited you to co-treat this patient."
3. **Action row** (wrap of buttons, by state and role):
   - PENDING + partner: filled "Accept" · outlined "Decline"
   - PENDING + initiator: outlined "Cancel invite"
   - ACTIVE (either): outlined "Mark completed"
   - DECLINED / CANCELLED / COMPLETED: no actions (target: a grey terminal chip and the date it ended)
4. **Divider** · section title "Progress updates".
5. **Progress feed** — cards: author doctor name (title) · body text · trailing timestamp (today the raw ISO string cut to 16 chars, e.g. "2026-03-12T09:15" — must be practice-TZ formatted). Oldest first today; target newest at the bottom with auto-scroll, chat-like, author on the left/right by "me".
6. **Composer** (ACTIVE only): text field hint "Share therapy progress…" + filled send icon button; keyboard "send" submits.
7. Target: a **"Open patient"** link in the header (both doctors can open the patient record — the partner gains access by accepting; UNVERIFIED what exactly the partner can see, see README area rules).

## 4. Data and formats
| Field | Source | Rule |
|---|---|---|
| Status | PENDING / ACTIVE / DECLINED / CANCELLED / COMPLETED (last three UNVERIFIED names) | localised chip; unknown → "Unknown" |
| Role | `viewerRole` or id comparison | drives buttons and hints |
| Names | `initiatingDoctorName`, `partnerDoctorName`, `patientName` | fallbacks "Doctor" / "Patient" |
| Message | `message` | quoted block; hidden when empty |
| Specialty requested | `specialtyRequested` | caption under the partner row |
| Progress item | author name, body, createdAt | practice TZ "12 Mar, 09:15"; "Today 09:15" |
| Composer | body text | trimmed; empty blocked; target max 1 000 |
| Transitions | accept / decline / cancel / complete → each a POST with an empty body; 2xx = success (today only 200) | refetch after each |

## 5. Primary flow (happy path) — partner accepts and posts an update
1. Open → skeleton header + 3 skeleton cards → content.
2. Partner reads the message → taps "Accept" → confirmation dialog "Accept this partnership?" (NEW) → button progress → status chip "Active" → composer appears.
3. Types "Saw the patient today, started aligners." → send → card appears at the bottom; field clears; banner not needed.
4. Weeks later either doctor taps "Mark completed" → confirm → status "Completed"; composer disappears.

```mermaid
flowchart TD
  A[Open detail] --> B[Load partnership + progress]
  B -- error --> C[Error + Retry]
  B --> D{Status / role}
  D -- PENDING & partner --> E[Accept | Decline]
  D -- PENDING & initiator --> F[Cancel invite]
  D -- ACTIVE --> G[Mark completed + composer]
  D -- terminal --> H[Read-only]
  E -- Accept --> I[Confirm] --> J[POST accept] --> G
  E -- Decline --> K[Confirm destructive] --> L[POST decline] --> H
  F --> M[Confirm destructive] --> N[POST cancel] --> H
  G -- Mark completed --> O[Confirm] --> P[POST complete] --> H
  G -- send update --> Q[POST progress] --> G
```

## 6. Screen states
- **First load** — header skeleton (title bar, chip, two name rows) + 3 progress skeleton cards; composer hidden until loaded.
- **Empty** — no progress updates: "No progress updates yet" + hint "Share how the therapy is going; your partner sees it here." (NEW; today an empty area).
- **Error on load** — today raw error text, no Retry; target error block + Retry.
- **Offline** — cached detail read-only; action buttons and composer disabled with the offline banner on tap.
- **Refreshing** — pull-to-refresh; after each action the screen refetches (today a full spinner replaces the content — must stay in place with a progress line).
- **Partial data** — partnership loaded but progress failed: header + actions work; feed shows "Couldn’t load updates" + Retry (NEW).
- **Permission needed** — n/a.
- **Locked by tier** — none.
- **Role difference** — initiator vs partner buttons as in §3; a doctor who is neither (should not happen) sees read-only.
- **Acting** — the tapped button shows progress; other buttons disabled.

## 7. Interactions and secondary flows
- **Accept** → confirm (NEW) → POST → refetch → ACTIVE.
- **Decline** → destructive confirm "Decline this invite?" with red "Decline" on the right (NEW) → POST → terminal; back returns to the list.
- **Cancel invite** → destructive confirm "Cancel this invite?" (NEW) → POST → terminal.
- **Mark completed** → confirm "Mark this partnership as completed? Progress updates will be closed." (NEW) → POST → terminal.
- **Type + send** → POST progress → clear field → new card; failure keeps the text and shows a banner.
- **Tap "Open patient"** (target) → Patient detail; for the partner this is their first entry into the record.
- **Pull-to-refresh** → reload both calls.
- **Back** → list (or patient when arriving from Find); an unsent composer text is kept in memory only while the screen lives — ask "Discard update?" when non-empty (NEW).

## 8. Edge cases
- **Invite already answered elsewhere** (web) → action POST returns 409/400 → banner "This invite was already {accepted/declined}" (NEW) and refetch.
- **Partner declines while the initiator is viewing** → stale until refresh; after any action the refetch reveals it.
- **Initiator cancels after the partner accepted** → server should reject (ACTIVE has no cancel) — the UI never offers it.
- **Status unknown / new** → chip "Unknown", no actions, feed still visible.
- **Patient deleted** → name fallback "Patient"; "Open patient" hidden.
- **Very long message / progress body** (2 000 chars) → full text, wraps; no truncation; target max 1 000 on input.
- **100+ progress updates** → unpaged list today; lazy render; newest visible first on open (scroll to bottom).
- **Send twice quickly** → two posts; disable the button while sending.
- **Timestamps** → ISO strings cut to minutes today in device-agnostic UTC; must be practice TZ, and the partner (a different doctor) sees them in *their* practice TZ.
- **Both doctors in different time zones** → each sees their own practice TZ; the date line should include the zone only when it differs from the device (open question).
- **401** → logout. **404 partnership** (deleted) → "This partnership no longer exists" + Back (NEW).
- **Rotation** → composer text kept; **language switch** → labels/chips re-localise; **font 200 %** → action buttons wrap to full-width stack; feed cards grow.
- **Screen reader** → status announced with the title; action buttons describe the consequence ("Accept invitation to co-treat {patient}"); new progress card announced politely after sending.
- **Deep link to a detail while another detail is open** → replace.

## 9. Validation and messages
| Action | Rule | When | Message (EN) | Where |
|---|---|---|---|---|
| Load | 2xx both calls | on open | "Couldn’t load partnership" (NEW) + Retry; progress-only failure "Couldn’t load updates" | block / feed |
| Accept | confirm | tap | "Accept this partnership?" · Cancel / Accept (NEW) | dialog |
| Decline | destructive confirm | tap | "Decline this invite?" · Keep / Decline (red) (NEW) | dialog |
| Cancel invite | destructive confirm | tap | "Cancel this invite?" · Keep / Cancel invite (red) (NEW) | dialog |
| Complete | confirm | tap | "Mark this partnership as completed? Progress updates will be closed." (NEW) | dialog |
| Action failed | non-2xx | — | "Couldn’t update the partnership" (NEW; today raw exception) | top banner |
| Already answered | 409 | — | "This invite was already answered" (NEW) | top banner + refetch |
| Progress body | non-empty, ≤ 1 000 | on send | send disabled when empty | — |
| Progress failed | — | — | "Couldn’t post the update" (NEW), text kept | top banner |
| Discard composer | non-empty on back | — | "Discard update?" (NEW) | dialog |

## 10. Navigation out
- Back → Care partnerships list (refreshes) or Patient detail (when reached via Find).
- Terminal actions (decline/cancel) → stay on the read-only detail; a "Back to list" button is helpful (open question).
- "Open patient" (target) → Patient detail.

## 11. Copy
Existing keys: `carePartnershipDetail` "Partnership" · `status` "Status" · `inviteSentWaiting` "Invite sent. Waiting for {name} to accept." · `inviteReceivedFrom` "{name} invited you to co-treat this patient." · `acceptInvite` "Accept" · `declineInvite` "Decline" · `cancelPartnerInvite` "Cancel invite" · `completePartnership` "Mark completed" · `partnershipProgress` "Progress updates" · `progressUpdateHint` "Share therapy progress…" · `cancel` · `retry`.

**NEW**: status chips (as file 13) · "Invited by" / "Partner" · "Specialty requested: {x}" · "No progress updates yet" · "Share how the therapy is going; your partner sees it here." · all confirmation dialogs in §9 · "Couldn’t load partnership" · "Couldn’t load updates" · "Couldn’t update the partnership" · "Couldn’t post the update" · "This invite was already answered" · "This partnership no longer exists" · "Open patient" · "Discard update?" · "Keep".

## 12. Known problems in the current app (do not carry over)
- No confirmation before Decline / Cancel / Complete; errors are raw exception text in toasts.
- Status shown as the raw code; patient fallback "Patient #id".
- Timestamps are substrings of ISO strings in UTC, not practice-TZ formatted.
- Full-screen spinner replaces the content after every action; progress list is oldest-first with no auto-scroll.
- Only HTTP 200 counts as success; UTF-8 not applied to the response body.
- No link from the partnership to the patient record; no empty state for the feed.
- Role fallback may misattribute when the profile has not loaded.

## 13. Open questions for design
- Progress feed as chat bubbles (me right / partner left) or a plain log?
- Should accepted partners see a "shared documents" subset here (needs backend), or just a link to the patient?
- Keep terminal partnerships readable forever, or archive after 90 days?
- Do we need a reason field on Decline?
