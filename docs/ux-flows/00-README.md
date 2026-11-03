# Shifa Doctor App — UI/UX flows, one file per screen

Audience: the UI/UX designer producing the new Figma for the KMP rewrite (Android + iOS, phone first). Each file
describes **one screen** (or one sheet/dialog when it is big enough to be designed on its own): what it is for, how
you get there, what is on it, every state it can be in, every interaction, every edge case, the exact copy, and
what is wrong in the current app so it is not copied.

These are behaviour documents, not visual specs. Visual tokens and components live in
`docs/kmp-spec/04-design-system-and-ui-kit.md` and will be filled from your Figma. Engineering detail (API shapes,
models) lives in `docs/kmp-spec/`; each flow file links to its spec section.

## Conventions used in every file

- **Template:** [`_TEMPLATE.md`](_TEMPLATE.md). Sections are always present in the same order; "n/a" is written
  explicitly so nothing is forgotten.
- **Roles:** `Doctor` (role `DOCTOR`) and `Clinic staff` (role `CLINIC_STAFF`). Staff see a reduced 4-tab shell
  (Chat, Clinic workspace, Notifications, Profile) and never see Home, personal Calendar, AI, tasks or analytics;
  they reach patients and bookings through the Clinic workspace.
- **Tiers:** BASIC < PRO < PREMIUM. When the tier is unknown the app behaves as PREMIUM (fail open). Locked
  features show the content dimmed or hidden plus an upgrade message; the flow file says which.
- **Time:** every date/time on screen is in the doctor's **practice timezone** (set in Profile), not the phone's.
  When the two differ the Calendar shows a small notice; other screens do not.
- **Languages:** English, Uzbek (Latin; Cyrillic is an automatic transliteration), Russian. Design for the longest
  of the three (usually Russian, ~30 % longer than English).
- **Feedback:** loading uses skeletons that match the final layout (never a centred spinner); errors and
  confirmations use a top banner that slides in under the app bar (never a bottom toast / SnackBar); destructive
  actions use a confirmation dialog with the destructive button in red on the right.
- **Navigation:** bottom tab bar with 5 slots for doctors (Home, Calendar, Patients, Chat, More); the More sheet
  holds Tasks, Reports, Clinic, Notifications, Profile, Shifa Intelligence. Screens pushed inside a tab keep the
  tab bar hidden; sheets slide from the bottom and are dismissible by drag and by the scrim.
- **Numbers in the files** (limits, timeouts, page sizes) are the current app's values; treat them as defaults the
  design may question, not as laws.

## Folders

| Folder | Screens | Spec |
|---|---|---|
| [01-launch-auth-shell](01-launch-auth-shell/) | splash, login, email OTP, registration, forgot password, app lock PIN + biometrics, language, force update, main shell / tabs / More sheet, global banners, push deep links | doc 01 |
| [02-home-analytics](02-home-analytics/) | home dashboard, next patient, today's schedule, attention centre, activity feed, analytics range + export, patient search overlay | doc 02 §1–2 |
| [03-shifa-intelligence](03-shifa-intelligence/) | Intelligence welcome, chat thread, voice session (mic / dictation / hands-free), every action card, cancel-by-voice, patient briefing, visit facts, clinical engine panel | doc 02 §3–7 |
| [04-calendar-schedule](04-calendar-schedule/) | calendar month/week/day, appointment create / reschedule / cancel sheets, availability setup, breaks, locations, staff calendars | doc 03 |
| [05-appointments-visit](05-appointments-visit/) | appointment details, in-person visit, video call, SOAP / scribe notes, dental chart, end visit + summary, follow-up, payment | doc 05 |
| [06-patients-documents-forms](06-patients-documents-forms/) | patient list, patient profile + timeline, add/edit patient, documents + viewer + upload, medical forms (025-2 etc.), care partnerships | doc 06 |
| [07-chat-notifications-tasks](07-chat-notifications-tasks/) | conversations, thread (text / voice / image / document), notification centre, task list, task templates, assign task | doc 07 |
| [08-profile-settings-reports-legal](08-profile-settings-reports-legal/) | profile, edit profile, location picker, timezone, subscription / tier, settings, security, reports, legal documents & consent | doc 08 |
| [09-clinic-workspace](09-clinic-workspace/) | clinic setup, doctors & roles, services & prices, finance (payments, records, outstanding), treatment plans wizard + apply in visit, clinic patients | doc 09 |

Admin panel screens are not included: the admin panel stays a web product (see `docs/kmp-spec/10-admin-parked.md`).

## Screen index
See [`INDEX.md`](INDEX.md) — generated list of every flow file with its one-line purpose, role and tier.

## How to work with these files
1. Read the folder README first; it has the area's flow map (which screen leads to which) as a Mermaid diagram.
2. Open one screen file; design all of §6 states, not only the happy path. Every state is a Figma frame.
3. Put questions from §13 (and your own) as comments on the Figma frame and we resolve them screen by screen.
4. When a design decision changes behaviour described here, tell us; we update the file so engineering builds
   what you drew, not what Flutter did.
