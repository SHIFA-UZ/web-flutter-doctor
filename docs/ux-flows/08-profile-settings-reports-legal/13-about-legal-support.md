# About, legal & support (version, terms, privacy, licences, contract, support)

| | |
|---|---|
| Area | Profile, settings, reports & legal |
| Who sees it | Doctor and Clinic staff. No tier gate. |
| Reached from | Settings → "About, legal & support"; Profile root version footer; Privacy & data → "Privacy policy"; Subscription → "Contact support" |
| Screen type | Full screen (pushed); documents open in an in-app web/PDF viewer |
| Current Flutter source | n/a — no About/legal screen exists. Related: version `pubspec.yaml` (`1.0.0+3`); legal PDF generator `lib/features/legal/pdf/*` is **admin-only** (issues the early-partner contract from the admin panel) |
| Spec | `docs/kmp-spec/08-profile-settings-reports-legal.md` §8 (legal contract — admin-side; LEGAL-01 spike, LEGAL-02 conditional "My partnership contract" viewer) |

## 1. Purpose
The place for everything a doctor needs to read rather than do: app version for support calls, terms, privacy policy, open-source licences (store requirement when applicable), the doctor's own partnership contract (if product says yes), and how to reach support. Visited rarely; must work offline for the static parts.

## 2. Entry points
- Settings → row → pushed.
- Profile root footer (version) → pushed.
- Privacy & data → "Privacy policy" → pushed with the policy opened directly.
- Subscription → "Contact support" → pushed with the support card scrolled into view (or the support sheet opened directly).
- Login screen footer "Terms · Privacy" (area 01) opens the same documents without this screen.

## 3. Layout, top to bottom (phone portrait)
1. **App bar** — back, title "About".
2. **Header** — app icon, "Shifa Doctor", "Version 1.0.0 (3)" (version name + build number from the platform package info), environment tag only in non-production builds ("Staging").
3. **Card "Legal"** — rows: "Terms of service", "Privacy policy", "Open-source licences". Each opens an in-app viewer (web view for hosted HTML, PDF viewer for PDFs) with a share button. Document URLs are served by the backend/config so they can change without an app release (UNVERIFIED — no config key exists today; `GET /api/public/config` currently only returns the maps key).
4. **Card "My partnership contract"** (conditional on LEGAL-01 = yes) — row with contract number and effective date ("SHIFA-0461 · from 01.06.2026"), chevron → PDF viewer via `GET /api/doctors/me/early-partner-contract.pdf` (UNVERIFIED, to be built server-side). Hidden when no contract is issued (404) or when the feature is not approved.
5. **Card "Support"** — rows: "Contact support" (opens a sheet: Telegram support chat, email, phone — values from config, UNVERIFIED), "Report a problem" (prefilled email/Telegram message with version, device, OS, doctor id — never patient data), "Rate the app" (store listing).
6. **Footer** — "© Shifa {year}", "Made in Uzbekistan" optional; a hidden 7-tap on the version copies diagnostics (build, device id, API base URL) to the clipboard with a banner "Diagnostics copied".

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Version | platform package info | "Version {name} ({build})" e.g. "Version 1.0.0 (3)"; CI continues the Flutter numbering (versionCode/TestFlight build = 1 + run number, area 00 open decision 4) |
| Document URLs | remote config (UNVERIFIED) with bundled fallback HTML per language | language-specific documents in en/uz/ru; uz-Cyrl falls back to uz |
| Contract | `GET /api/doctors/me/early-partner-contract.pdf` (UNVERIFIED) + metadata `{contractNumber, effectiveDate, termMonths}` | number as issued ("SHIFA-0461"), date `dd.MM.yyyy`; the PDF content is Uzbek Latin today, rendered server-side |
| Support contacts | remote config (UNVERIFIED) | phone tappable (tel:), email (mailto:), Telegram (t.me link) |
| Diagnostics | local | version, OS, device model, locale, practice timezone, doctor id — no names, no patient data |

## 5. Primary flow (happy path)
1. Open → header and rows render instantly (local); the contract row appears after its metadata call (≤ 1 s) or stays hidden.
2. Tap "Privacy policy" → in-app viewer with the document in the app language; share icon top-right.
3. Back → About → Back → Settings.

Support path: tap "Report a problem" → sheet with the prefilled message preview and "Send via Telegram" / "Send via email" → external app opens → return.

```mermaid
flowchart TD
  A[Open About] --> B{Contract enabled & issued?}
  B -- yes --> C[Show contract row]
  B -- no / 404 --> D[Hide row]
  A --> E[Tap legal row]
  E --> F{Online or cached?}
  F -- yes --> G[In-app viewer]
  F -- no --> H[Bundled fallback copy, banner "Showing offline copy"]
  A --> I[Tap Contact support] --> J[Support sheet → external app]
```

## 6. Screen states
- **First load:** instant; contract row has no placeholder (appears when known).
- **Empty:** no contract → row hidden; no support config → support card shows the bundled defaults (UNVERIFIED values — do not invent; "Contact support" becomes a disabled row with "Support contacts unavailable" until config exists).
- **Error on load:** legal document fails to load → viewer shows "Couldn't load the document" + Retry + "Open in browser".
- **Offline:** header, licences and bundled legal copies work; contract PDF only if cached (viewer caches the last download); support rows open external apps (may fail — OS handles).
- **Refreshing:** n/a.
- **Partial data:** contract metadata ok but PDF 500 → viewer error with Retry.
- **Permission needed:** n/a.
- **Locked by tier:** n/a.
- **Role difference:** clinic staff: no contract row (contracts are issued to doctors); everything else identical.

## 7. Interactions and secondary flows
- **Tap legal row** → viewer; share → OS share sheet with the URL (or the PDF file).
- **Tap contract row** → PDF viewer; share/download → OS share sheet with the PDF.
- **Tap Contact support** → sheet with the available channels; each opens externally.
- **Tap Report a problem** → compose sheet; the diagnostics block is visible and editable before sending.
- **Tap Rate the app** → in-app review prompt if available, else store page.
- **7 taps on version** → copy diagnostics.
- **Back** → pop.

## 8. Edge cases
- Document URL returns HTML with its own navigation → viewer shows only the document; external links inside open in the system browser.
- Very long licence list (hundreds of packages) → licences screen is a virtualised list grouped by package with expandable text.
- Contract issued after the screen was opened → appears on next open (no live refresh needed).
- Contract 404 vs 403 → both hide the row (403 for staff).
- PDF 5 MB on a slow connection → viewer progress bar; cancel returns to About.
- Device has no email client → "Send via email" hidden; no Telegram → opens t.me in the browser.
- Telegram link opens the app but the support account is unavailable → nothing we can detect.
- Store review prompt quota exhausted → fall back to opening the store page.
- Language switch → legal documents reload in the new language on next open; uz-Cyrl shows the uz (Latin) document with a one-line notice "This document is available in Uzbek (Latin)" **NEW**.
- Font 200 % → rows wrap; the viewer respects system font scaling for HTML (inject a base font size).
- Rotation → viewer keeps scroll position.
- Screen reader: version readable; rows "Terms of service, button"; the PDF viewer exposes page numbers; the 7-tap gesture is not accessible — also expose "Copy diagnostics" inside Report a problem.
- Environment tag must never show in production builds.

## 9. Validation and messages
| Field/action | Rule | When | Message (EN) | Where |
|---|---|---|---|---|
| Legal doc load | 200 | open | "Couldn't load the document" **NEW** + `retry` + "Open in browser" **NEW** | viewer |
| Offline doc | cached/bundled | open | "Showing offline copy" **NEW** | viewer banner |
| Contract load | 200 | tap | "Couldn't load your contract" **NEW** + `retry` | viewer |
| Diagnostics | — | 7 taps | "Diagnostics copied" **NEW** | banner |
| Support contacts | config present | tap | "Support contacts unavailable" **NEW** | row caption |

## 10. Navigation out
- Back → Settings or Profile root (whichever pushed it).
- Viewers → back to About.
- External apps (Telegram, mail, store, browser) → leave the app; return lands here.

## 11. Copy
- Title "About" **NEW**; "Shifa Doctor" (brand); "Version {name} ({build})" **NEW**; "Staging" **NEW**.
- Legal: "Legal" **NEW**, "Terms of service" **NEW**, "Privacy policy" **NEW**, "Open-source licences" **NEW**.
- Contract: "My partnership contract" **NEW**, "{number} · from {date}" **NEW**, "Couldn't load your contract" **NEW**.
- Support: "Support" **NEW**, "Contact support" **NEW**, "Report a problem" **NEW**, "Rate the app" **NEW**, "Send via Telegram" **NEW**, "Send via email" **NEW**, "Diagnostics copied" **NEW**.
- Existing: `retry`, `close`, `networkError`.

## 12. Known problems in the current app (do not carry over)
- No About, version, legal or support screen at all; the version is only in `pubspec.yaml`.
- The only legal document in the codebase (early-partner contract) is generated **client-side in the admin web panel** with the supplier's legal identity, signature fonts and a hard-coded "(olti)" term in the Dart code; doctors never see it. Do not port the generator — if doctors should see the contract, the backend renders it (LEGAL-01/02).
- No support entry point exists; doctors currently reach support outside the app.

## 13. Open questions for design
- Does product want the partnership contract visible to doctors (LEGAL-01)? If yes, is "view/share" enough or is an in-app acceptance/signature required (none exists; the admin flow auto-signs the partner)?
- Terms/privacy as hosted HTML (editable without release) or bundled PDFs (works offline)?
- Which support channel is primary in Uzbekistan — Telegram is the likely answer; confirm.
