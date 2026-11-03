# <Screen name>

| | |
|---|---|
| Area | <area name> |
| Who sees it | Doctor / Clinic staff / both — and tier if gated (BASIC, PRO, PREMIUM) |
| Reached from | <comma list of entry points, see §2> |
| Screen type | Full screen / tab root / bottom sheet / dialog / overlay |
| Current Flutter source | `lib/...` (for engineers; designers can ignore) |
| Spec | `docs/kmp-spec/0X-….md` §Y |

## 1. Purpose
One short paragraph: what the doctor is trying to get done here and how often (daily, once at setup, rare).

## 2. Entry points
Bullet list. For each: where the user comes from, what context arrives with them (e.g. "from a push notification with an appointment id"), and whether the screen opens on top of a stack or replaces it.

## 3. Layout, top to bottom (phone portrait)
Numbered regions. For each region: what it shows, what is static vs. live data, what is tappable. Mention sticky elements (app bar, bottom action bar, FAB) and scroll behaviour.

## 4. Data and formats
Table: field · source · format rule. Dates and times are always shown in the doctor's **practice timezone**, never the phone's. Currency, phone numbers, names (first + last, fallback "Patient"), truncation/ellipsis rules, pluralisation.

## 5. Primary flow (happy path)
Numbered steps from entry to success, with the feedback the user sees at each step (skeleton → content, button → progress → banner, etc.). Add a Mermaid `flowchart TD` when there are decisions.

## 6. Screen states
Every screen must define all of these, even if "not applicable":
- First load (skeleton — which regions are placeholders)
- Empty (no data yet — illustration/copy/CTA)
- Error on load (message + Retry)
- Offline (what still works from cache, what is disabled)
- Refreshing (pull-to-refresh / background refresh — does content jump?)
- Partial data (one of several calls failed)
- Permission needed (camera, mic, notifications, location) and permission denied permanently
- Locked by tier (what is visible, what the upgrade CTA says)
- Role difference (doctor vs clinic staff)

## 7. Interactions and secondary flows
One bullet per tap/gesture: **trigger → what happens → feedback → where the user lands**. Include long-press, swipe, pull-to-refresh, back gesture, keyboard "Done".

## 8. Edge cases
Exhaustive bullets. Always consider: very long names/text, 0 / 1 / 100+ items, same-day items across midnight in practice timezone, timezone mismatch between phone and practice, slow network (>3 s), double tap on the primary button, leaving mid-save, session expired (401) mid-flow, item deleted by someone else meanwhile (404/409), deep link while a sheet is open, device rotation, system back vs in-app back, language switch while open, font scaling 200 %, screen reader focus order.

## 9. Validation and messages
Table: field/action · rule · when checked (on blur / on submit) · message (English copy) · where shown (inline under field / top banner / dialog).

## 10. Navigation out
Every exit: where it goes, whether unsaved input is kept, discarded, or asked about, and what the previous screen must refresh.

## 11. Copy
The exact English strings the screen needs (title, labels, hints, empty/error copy, button labels, banner texts). Reference string keys from `docs/kmp-spec/strings/en.json` where they exist; mark new copy as **NEW**.

## 12. Known problems in the current app (do not carry over)
Bullets of behaviour that is wrong today and must be designed differently, with a one-line reason.

## 13. Open questions for design
Bullets. Things the spec cannot decide (layout choices, which actions deserve a primary button, whether a step can be merged).
