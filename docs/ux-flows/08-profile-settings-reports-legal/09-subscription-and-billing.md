# Subscription & billing (plan, features, upgrade, payouts, invoicing)

| | |
|---|---|
| Area | Profile, settings, reports & legal |
| Who sees it | Doctor only. Visible on every tier; content differs per tier. |
| Reached from | Profile root → "Subscription & billing" row / tier badge; every "locked feature" upgrade CTA elsewhere (Tasks tab, Ask Shifa AI, speech-to-text, patient briefing, AI notes, advanced analytics) |
| Screen type | Full screen (pushed) + external browser/Custom Tab for Stripe |
| Current Flutter source | `profile_screen.dart` (panel `payment`, ~1565–1782), `lib/state/profile/profile_actions.dart` (`createStripeConnectOnboarding`, `createSubscriptionCheckout`, `patchBilling`), `lib/core/subscription/doctor_subscription.dart`, `lib/state/subscription/doctor_subscription_provider.dart` |
| Spec | `docs/kmp-spec/08-profile-settings-reports-legal.md` §1.7, §5, tickets SUB-01, SUB-02, PROF-04 (billing fields); `16-implementation-plan.md` decision "tier gating when loading: hide until resolved" |

## 1. Purpose
Show the doctor which plan they are on, what each plan unlocks, and let them upgrade (Stripe hosted checkout). The same screen holds the money-out side: Stripe Connect payout onboarding status and invoicing details (billing name, email, IBAN, tax id). Visited when a locked feature is hit, or once to set up payouts.

## 2. Entry points
- Profile root row / tier badge → pushed, scrolled to top.
- Upgrade CTA from a locked feature → pushed with the relevant plan card highlighted and a chip "Needed for: Remote-care tasks" at the top.
- Return from Stripe (Custom Tab / SFSafariViewController closes, or app-link `shifa://billing/return`) → this screen refreshes the profile and shows the new state.

## 3. Layout, top to bottom (phone portrait)
1. **App bar** — back, title "Subscription & billing".
2. **Current plan card** — badge "PRO", line "Your current plan", renewal/expiry line if the backend provides it (UNVERIFIED — it does not today, so the line is omitted), "Managed by Shifa — contact support to change" when the tier is admin-managed and no checkout is offered for the target plan.
3. **Plans** — three cards in a horizontal pager (or stacked): BASIC, PRO, PREMIUM. Each: name, one-line pitch, price line (UNVERIFIED — no price endpoint; show "See checkout for price" until one exists), feature list with ticks, and a button: "Current plan" (disabled), "Upgrade to PRO" / "Upgrade to PREMIUM" (primary), nothing on lower plans (no downgrade in app).
4. **Feature matrix** (collapsible "Compare all features") — rows × tiers from the tier matrix in the README (Calendar, Documents, Chat, Patient management, Basic analytics → BASIC; Video consultation, AI notes, Speech-to-text, Patient briefing, Ask Shifa AI → PRO; Remote-care tasks, Advanced analytics, Differential diagnosis → PREMIUM).
5. **Payouts card (Stripe Connect)** — status "Not connected" / "Connected · acct_…1234" (last 4 only), explanatory line "Patients' card payments are paid out to this account", button "Connect Stripe" / "Resume Stripe onboarding" / when connected "Open Stripe dashboard" (UNVERIFIED endpoint — omit if none).
6. **Invoicing details card** — fields Billing name, Billing email, IBAN / Account number, Tax ID / VAT ID; Save button at the card bottom (only this card has a form). Click / Payme merchant ids are not shown (round-tripped silently).

## 4. Data and formats
| Field | Source → saved | Format rule |
|---|---|---|
| Tier | `GET /api/doctors/me` → subscription.tier (`BASIC`/`PRO`/`PREMIUM`), legacy `profile.subscriptionTier` | parsed case-insensitively; missing/unknown → "unresolved" (see §6) |
| Features list | `subscription.features[]` (unused today) | if present, prefer it over the static matrix for ticks |
| Checkout | `POST /api/doctor/subscription/checkout {planCode: BASIC|PRO (PREMIUM UNVERIFIED), gateway: "STRIPE"}` → `{checkoutUrl}` | open in Custom Tab / SFSafariVC; never in the external browser |
| Connect onboarding | `POST /api/doctor/payments/stripe/connect/onboard {}` → `{onboardingUrl}` | same |
| Connect status | billing.stripeConnectAccountId | masked "acct_••••1234" |
| Billing fields | `PATCH /api/doctors/me/billing {billingName, billingEmail, iban, taxId, stripeConnectAccountId (echo), clickMerchantId (echo), paymeMerchantId (echo)}` | blanks sent as null; IBAN upper-cased, spaces stripped, basic length/country check (15–34 chars, 2 letters + 2 digits prefix), no checksum required (open question) |

## 5. Primary flow (happy path — upgrade)
1. Doctor hits the Tasks tab lock → "Upgrade to PREMIUM" → this screen with the PREMIUM card highlighted.
2. Taps "Upgrade to PREMIUM" → button ring → checkout URL → Custom Tab opens Stripe.
3. Completes payment → Stripe redirects to `shifa://billing/return` (or the doctor closes the tab) → app foregrounds → screen shows a "Checking your plan…" banner and refetches the profile.
4. Tier now PREMIUM → current-plan card updates, button becomes "Current plan", banner "You're now on PREMIUM" **NEW**; the Tasks tab appears in the shell.

```mermaid
flowchart TD
  A[Open] --> B{Tier resolved?}
  B -- no --> C[Plan cards skeleton; no CTA]
  B -- yes --> D[Current plan + cards]
  D --> E[Tap Upgrade]
  E --> F[POST checkout]
  F -- 200 url --> G[Custom Tab Stripe]
  F -- error --> H[Banner message]
  G --> I[Return to app]
  I --> J[Refetch profile]
  J -- tier changed --> K[Banner "You're now on X"]
  J -- unchanged --> L[Banner "No change yet — payments can take a minute" + Refresh]
```

## 6. Screen states
- **First load:** skeleton: plan card block, three card placeholders, two card blocks. No spinner.
- **Empty:** n/a (there is always a tier) — see unresolved below.
- **Tier unresolved** (missing/unparseable): current-plan card says "Plan not available right now" with Retry; plan cards show features but **no upgrade buttons**; elsewhere in the app features behave as PREMIUM (fail open) until resolved — README rule; the implementation plan asks to hide gated entries until resolved. This screen never claims a tier it does not know.
- **Error on load:** inherits profile error + Retry.
- **Offline:** cached tier shown; upgrade and connect buttons disabled with "Needs a connection"; invoicing form editable, Save disabled.
- **Refreshing:** after returning from Stripe: top banner "Checking your plan…" with a progress line; content does not jump; up to 3 automatic refetches 2 s apart, then the manual Refresh affordance.
- **Partial data:** billing block missing → payouts and invoicing cards show "Couldn't load" with Retry; plans still work.
- **Permission needed:** n/a.
- **Locked by tier:** this screen is the destination of locks, never locked itself. Each plan card marks features the doctor lacks with a lock glyph.
- **Role difference:** clinic staff: not reachable (no billing).

## 7. Interactions and secondary flows
- **Tap Upgrade** → checkout; the two Stripe buttons have **separate** loading states (today one shared flag disables both).
- **Tap Connect Stripe / Resume** → onboarding URL in Custom Tab; on return, refetch; status updates to Connected when `stripeConnectAccountId` arrives.
- **Tap "Compare all features"** → matrix expands inline.
- **Tap a locked feature row in the matrix** → tooltip/sheet with one sentence on what it does (copy from area 02/03/07 files).
- **Edit invoicing fields → Save** → PATCH → banner "Payment and invoicing saved"; Back with dirty → unsaved dialog.
- **Pull-to-refresh** → refetch profile (useful after paying).
- **Back** → pop; if a Custom Tab is open it is already closed by the OS.

## 8. Edge cases
- Tier downgraded by admin mid-session → next refetch flips cards; features elsewhere lock; a banner "Your plan changed to BASIC" **NEW** on this screen only.
- Subscription expired mid-session (UNVERIFIED backend signal) → same as downgrade; show "Expired on {date}" when the date is known.
- PREMIUM requested but the backend only accepts BASIC/PRO plan codes → the PREMIUM button says "Contact support" and opens the support sheet (`13`), until the backend confirms PREMIUM checkout.
- Checkout URL missing/empty in a 200 → banner "Couldn't start checkout. Try again." **NEW** (today: "Stripe subscription checkout URL is missing.").
- Custom Tab cannot open (no browser) → banner "Couldn't open Stripe. Install a browser and try again." **NEW**.
- Doctor pays but the webhook is late → "No change yet — payments can take a minute" with Refresh; do not loop forever.
- Doctor cancels in Stripe → return; refetch shows no change; no error banner (silent), just the normal screen.
- Double tap Upgrade → one request.
- Connect account exists but onboarding incomplete → status "Onboarding incomplete" (needs a backend flag, UNVERIFIED; today only the id is known) with "Resume Stripe onboarding".
- IBAN with spaces/lowercase → normalised; non-IBAN local account numbers (Uzbek 20-digit) → accepted when the prefix is not two letters (the field is "IBAN / Account number").
- Tax id formats vary by country → free text, ≤ 32 chars.
- Billing email invalid → inline error; blank allowed.
- Save billing while offline → disabled.
- 401 on any call → logout.
- Rotation → pager index kept; Custom Tab unaffected.
- Language switch → plan names stay BASIC/PRO/PREMIUM (brand terms), features translate.
- Font 200 % → plan cards stack vertically instead of paging; matrix becomes a list of rows with three small tier chips.
- Screen reader: current plan announced first; each plan card as a group with its button; the matrix as a table (row header = feature, cells = "included/not included").

## 9. Validation and messages
| Field/action | Rule | When | Message (EN) | Where |
|---|---|---|---|---|
| Upgrade | 200 with url | tap | "Couldn't start checkout. Try again." **NEW** / server `message` | top banner |
| Connect | 200 with url | tap | "Couldn't open Stripe onboarding." **NEW** / server `message` | top banner |
| Return from Stripe | tier changed | refetch | "You're now on {tier}" **NEW** / "No change yet — payments can take a minute" **NEW** | top banner |
| Billing name | ≤ 120 chars | blur | counter | inline |
| Billing email | optional, valid format | blur | `invalidEmail` | inline |
| IBAN / account | 15–34 chars after normalisation, or digits-only local account | blur | "Check the account number" **NEW** | inline |
| Tax id | ≤ 32 | typing | counter | inline |
| Save billing | 200 | after PATCH | "Payment & invoicing saved" (`paymentAndInvoicingSaved`) / server `message` | top banner |

## 10. Navigation out
- Back → Profile root (tier badge and row preview refresh).
- Custom Tab → returns here; refetch on resume.
- Support sheet (`13`) for PREMIUM when checkout is unavailable.
- Locked-feature origin: not returned to automatically; the doctor navigates back; the originating screen re-reads the tier when shown.

## 11. Copy
- Title "Subscription & billing" **NEW**; today `paymentAndInvoicing` ("Payment & Invoicing") stays as the invoicing card title.
- Tiers: `subscriptionTierBasic` ("Basic"), `subscriptionTierPro` ("Pro"), `subscriptionTierPremium` ("Premium"); "Your current plan" **NEW**, "Current plan" **NEW**, "Upgrade to {tier}" **NEW**, "Compare all features" **NEW**, "Plan not available right now" **NEW**, "Managed by Shifa — contact support to change" **NEW**, "Needed for: {feature}" **NEW**.
- Feature names: `metricReports`/`analytics` ("Analytics"), `videoCall` ("Video Call"), `tasks`, "AI notes", "Speech-to-text", "Patient briefing", "Ask Shifa AI", "Advanced analytics", "Differential diagnosis" — reuse area 02/03/07 keys where they exist; otherwise **NEW**.
- Payouts: "Stripe Connect payouts" **NEW** (today hard-coded), "Not connected" **NEW**, "Connected account" **NEW**, "Connect Stripe" **NEW**, "Resume Stripe onboarding" **NEW**, "Patients' card payments are paid out to this account" **NEW**.
- Invoicing: `billingName` ("Billing Name"), `billingEmail` ("Billing Email"), `ibanAccountNumber` ("IBAN / Account number"), `taxIdVatId` ("Tax ID / VAT ID"), `save`, `paymentAndInvoicingSaved`.
- Related existing (AI lock copy): `intelSpeechRequiresPro` ("Speech requires Pro. Upgrade to talk with Shifa.").

## 12. Known problems in the current app (do not carry over)
- The current tier is **never shown**; the "Doctor monthly subscription" card is a BASIC/PRO dropdown defaulting to BASIC plus "Start subscription checkout" — a doctor on PRO can "buy" BASIC.
- PREMIUM is not selectable; no feature comparison anywhere.
- Checkout and onboarding open in the external browser with no return handling; connected status only updates after an unrelated profile refresh.
- One shared loading flag disables both Stripe buttons.
- All Stripe/subscription strings are hard-coded English; errors are raw ("Stripe onboarding URL is missing.").
- Tier **fails open to PREMIUM** when missing or while loading (verified bug #12) — the rewrite must distinguish "unresolved" from "PREMIUM" at least on this screen and hide gated entries until resolved.
- Billing fields have no validation; Click/Payme merchant ids are invisible but echoed back.

## 13. Open questions for design
- Horizontal pager vs. stacked plan cards; where does the price come from (no endpoint)?
- Show the raw Stripe account id masked or hide it entirely behind "Connected"?
- Should Invoicing details be its own screen (it is the only form here)?
- Downgrade/cancel: in-app (needs endpoint) or "contact support"?
