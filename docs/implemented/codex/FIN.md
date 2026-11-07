- **[P2] Pagination can become permanently stuck.** [ClinicFinance.kt:258](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinance.kt:258) starts a new generation without clearing `loadingMore`. Switching views/months or refreshing during a page request drops that request’s cleanup; subsequent `loadMore()` calls return immediately.

- **[P2] All-time debt details exclude debt counted by the dashboard.** [ClinicFinance.kt:319](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinance.kt:319) uses appointment rows for Outstanding and only installments for Overdue. The controller includes unlinked treatment-plan balances and outstanding/overdue invoices, so positive KPIs can display “No outstanding balances” or “No overdue items.”

- **[P2] Outstanding drill-down silently stops at 50 visits.** [ClinicFinance.kt:319](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinance.kt:319) fetches only page zero, filters afterward, and discards pagination metadata. If the newest 50 visits are paid, older unpaid visits disappear and the screen incorrectly reports no balances.

- **[P2] “Charged” is reconstructed incorrectly using floating-point money arithmetic.** [ClinicFinanceViews.kt:510](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinanceViews.kt:510) divides revenue by `collectionRate`. The controller clamps that ratio to 1: collecting 120 against charges of 100 therefore displays charges of 120. Floating-point conversion and truncation can also lose minor units.

- **[P2] Waived installments remain outstanding.** [ClinicFinanceViews.kt:811](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinanceViews.kt:811) excludes only CANCELLED from scheduled amounts, then calculates outstanding as scheduled minus PAID. Waiving an installment leaves its full amount in the debt total.

- **[P2] Cancelled installments offer an impossible payment action.** [ClinicFinanceViews.kt:1144](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinanceViews.kt:1144) always offers Mark paid; CANCELLED rows can open this menu. The controller explicitly rejects mark-paid for cancelled installments, so completing this flow always fails.

- **[P2] New records cannot be linked to treatment plans.** [ClinicFinance.kt:621](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinance.kt:621) always omits `treatmentPlanId`, and the form has no plan selector. FIN-03 and flow 10 require this supported controller branch; users cannot generate records with plan-derived totals and existing payments.

- **[P2] CSV output lacks the required UTF-8 BOM.** [Csv.kt:8](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/utils/Csv.kt:8) prefixes an empty string instead of `\uFEFF`. Cyrillic/Uzbek exports therefore retain the Excel encoding problem explicitly identified in flow 13; the test also expects BOM-less output.

- **[P2] CSV exports discard currency information.** [ClinicFinanceViews.kt:1347](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinanceViews.kt:1347) exports plain amounts without a currency column; ledger, records, installments and earnings do likewise. Mixed-currency rows become indistinguishable, contrary to flow 13.

- **[P2] Earnings exports omit the revenue split.** [ClinicFinanceViews.kt:1389](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinanceViews.kt:1389) exports only doctor, gross, collected and outstanding. Visits, share percentage and all four doctor/clinic share amounts required by FIN-06 and flow 13 are absent, preventing reconciliation of the exported earnings.

---

- **P2 paging stuck:** fixed. A reload clears `loadingMore` with the generation it drops.
- **P2 lists don't match the KPIs:** fixed. All-time Outstanding lists the plans with a balance (`GET /treatment-plans?clinicId`,
  what the backend counts); a month's lists that month's visits with a balance; Overdue lists overdue installments and
  overdue invoices. Test.
- **P2 Outstanding stops at 50:** fixed. The month's visits are read page by page (at most 10 pages of 50).
- **P2 "Charged" rebuilt with floats:** fixed by removal: the list shows Collected and the backend's rate; nothing is
  derived from the clamped ratio.
- **P2 waived installments outstanding:** fixed. Scheduled and outstanding leave out waived and cancelled ones.
- **P2 Mark paid on cancelled:** fixed. A cancelled installment's menu offers Pending / Waived only.
- **P2 records without a plan:** fixed. Once a patient is picked, their plans in the clinic are offered; with a plan the
  total comes from its lines (the backend's branch) and the amount field gives way to the hint.
- **P2 no BOM:** fixed. The BOM is written as bytes (the `﻿` literal had been lost from the source); the test
  checks the three bytes.
- **P2 CSV without currency:** fixed. Every view's export has a currency column.
- **P2 earnings CSV without the split:** fixed. Visits, share %, gross, collected, outstanding and the four split amounts.
