# Finance overview (dashboard) + month filter

| | |
|---|---|
| Area | Clinic workspace · Finance |
| Who sees it | All members (owner, member doctor, staff incl. nurse). Read-only; no write actions on this view. No tier gate. |
| Reached from | Clinic workspace root → "Finance" sub-tab (first inner view "Dashboard"); More sheet → "Finance" (target lands here) |
| Screen type | Sub-tab content (Finance host with 6 inner views as chips; this file covers the host + Dashboard) |
| Current Flutter source | `lib/features/clinic/presentation/clinic_finance_tab.dart`, `finance_dashboard_view.dart`, `finance_shared.dart`, `lib/state/clinic/clinic_finance_month.dart`, `clinic_finance_providers.dart` |
| Spec | `docs/kmp-spec/09-clinic-workspace-finance-treatment-plans.md` §3, §3.1, §3.2, tickets FIN-01, FIN-02 |

## 1. Purpose
Answer "how is the clinic doing with money" in one glance: collected revenue, what is still owed, what is overdue, and how well the clinic collects — for all time or for one month — with a tap-through list under each number. Owners look daily; doctors weekly.

## 2. Entry points
- Finance sub-tab → opens on **Dashboard** (first chip). The month filter is shared by Dashboard, By appointment, Installments, Doctor earnings and Payments (Records ignores it).
- Pull-to-refresh anywhere in Finance refreshes all finance data.
- Payment / installment / plan-status changes elsewhere trigger a background refresh of this view.

## 3. Layout, top to bottom (phone portrait)
1. **Finance view switcher**: horizontally scrolling choice chips — Dashboard · By appointment · Installments · Doctor earnings · Records · Payments. Target: keep chips but make the row sticky; consider a dropdown title instead (open question).
2. **Dashboard header row**: title "Dashboard" + export icon (menu: Export CSV · Export PDF — file 13).
3. **Month filter**: outlined dropdown "Month" with "All time" + the last 18 months (labels like "October 2026", Uzbek uses Latin month names). Target: a chip-like control that opens a bottom sheet with "All time" on top and months grouped by year.
4. **KPI cards** (2 per row on phones, tappable, selected card gets a thicker border):
   - Total Revenue (green) with a one-line hint that differs for all-time vs month.
   - Outstanding (orange).
   - Overdue (red) — a **count**, not money.
   - Collection Rate (teal) — percentage with 1 decimal.
   - When a month is selected **and** the server returns share totals: Doctor share (collected) (blue, subtitle "Doctor share (gross): …") and Clinic share (collected) (purple, subtitle gross).
5. **Top doctors** (only when the server sends any): card list; each row = doctor name (resolved from members, fallback "#id"), subtitle "Visits: 12 · 60% · Doctor share (collected): 1 200 000 UZS" (or just visits when no share), trailing gross and "collected / outstanding".
6. **Drill-down section** for the selected KPI: title + hint + a card list:
   - **Revenue**, all time: recent payments (patient, "plan title (#id) · 24.10.2026 · Cash", amount green). Month: billable visits with collected > 0 (patient, "Dr · date · #plan", collected).
   - **Outstanding**, all time: treatment plans with owed > 0 (not cancelled) sorted by owed desc, then financial records with remaining > 0 (not paid/void). Month: visits with total − collected > 0.
   - **Overdue**, all time: overdue installment items (patient, "plan · #seq · Due 24.10.2026", amount red) then overdue records. Month: not tracked → explanatory empty text.
   - **Collection**, all time: plans with total > 0 sorted worst→best, rate coloured (≥ 100 green, ≥ 50 orange, else red) with "paid / total". Month: same per visit.
7. Pull-to-refresh on the whole view.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Stats | `GET /api/clinics/{id}/finance/dashboard[?from&to]` → totalRevenueMinor, outstandingMinor, overdueCount, collectionRate (0..1), currency, doctorEarningsTop[], share totals? | from/to = month start/end in the **clinic time zone**, sent as UTC instants |
| Money | minor units + currency | **One formatter**: UZS "1 250 000 UZS" (whole units, thin-space grouping, no decimals); USD/EUR/RUB "1 250.50 USD" (2 decimals, grouping). Negative never expected; show "0 UZS" not "—" |
| Collection rate | double | `rate × 100`, 1 decimal, "%" |
| Overdue | integer count | plain number |
| Month list | last 18 months from "today in clinic time zone" | Label `MMMM yyyy` localised; Uzbek: Latin month list ("oktabr, 2026") |
| Drill-down lists | payments page 0/50, plans list, records page 0/50, installment items filter=overdue, month visits (ledger page size 500) | Target: dedicated server drill-down endpoints or at least server date ranges; today all-time lists are capped at the first 50 rows |
| Dates in lists | ISO instants | dd.MM.yyyy in clinic time zone (today: component print ignoring zone) |
| Patient label | name ?? "Patient #id" ?? "—" | — |

## 5. Primary flow (happy path)
1. Open Finance → chips row; Dashboard skeleton: 4 card placeholders, list placeholder.
2. Stats arrive → cards fill; Revenue preselected; drill-down loads its list (own skeleton).
3. Owner picks "September 2026" → cards reload (keep size, values shimmer), share cards appear if available, drill-down switches to the month variant.
4. Tap "Outstanding" → drill-down swaps to outstanding list.
5. Tap a drill-down row → target: opens the plan detail / record / installment (today rows are not tappable).

## 6. Screen states
- **First load**: chips + month control visible; 4 skeleton cards (2×2); 3 skeleton list rows.
- **Empty**: cards show "0 UZS", "0 UZS", "0", "0.0%"; drill-down shows its empty copy ("No payment history yet." / "No outstanding balances." / "No overdue items." / "No treatment plans with billable amounts yet.").
- **Error on load**: banner "Couldn't load finance data." + Retry; chips stay usable.
- **Offline**: cached stats with an "offline" banner; export disabled.
- **Refreshing**: pull-to-refresh or background refresh; cards never collapse; a thin progress line under the chips.
- **Partial data**: stats ok, drill-down source fails → inline error in the drill-down card with Retry; cards stay.
- **Permission needed**: n/a.
- **Locked by tier**: n/a.
- **Role difference**: none on this view (nurse sees it too). Target: confirm whether a plain member doctor should see clinic-wide totals or only their own (open question; server returns clinic-wide today).

## 7. Interactions and secondary flows
- **Tap chip** → switches inner view (today also re-fetches every finance endpoint — target: refresh only when stale > 60 s or after a write).
- **Change month** → all month-aware views refetch; selection persists while the workspace is open (per clinic).
- **Tap KPI card** → selects it; drill-down swaps with a cross-fade; scroll position kept.
- **Tap export icon** → menu CSV/PDF → file 13.
- **Tap top-doctor row** → target: Doctor earnings view filtered to that doctor.
- **Tap drill-down row** → target: plan detail (file 16) / records view / installments view with that item highlighted.
- **Pull-to-refresh** → full refresh.

## 8. Edge cases
- Clinic with 1 doctor: "Top doctors" shows one row — still fine; hide the section when the list is empty.
- 40 doctors: top list is server-capped (count UNVERIFIED); show "See all in Doctor earnings".
- Month boundary: a payment at 23:50 on 30 Sept clinic time is September even if the owner's phone is in another zone; a payment at 00:10 on 1 Oct clinic time is October. The month list itself must start from the clinic's "today" (a phone in New York on the evening of 31 Oct still sees November if Tashkent already crossed midnight).
- Clinic currency UZS but a plan in USD → drill-down rows show their own currency; the cards show the server's currency; mixed sums are never added client-side (today the payments-view total adds across currencies — file 09).
- 1 000+ payments: all-time revenue drill-down is capped at 50 — show "Showing latest 50 · Open Payments" instead of pretending it is complete.
- Collection rate when total = 0 → "0.0%" (not NaN); plan rate for total 0 excluded from the collection list.
- Overdue with month selected → explanatory copy, not an empty list.
- Share cards appear only with a month selected (server does not send them for all time) — explain with a hint "Select a month to see revenue share".
- Payment larger than outstanding → outstanding floors at 0 server-side; collection rate may exceed 100 % for a plan → show "100%+"? Keep raw ("120.0%") and colour green.
- Slow network > 3 s → skeleton; > 10 s inline "Still loading…".
- 401 → logout; 403 (nurse hitting a restricted endpoint) → view-level "You don't have access to finance data" (server rules UNVERIFIED).
- Rotation/tablet → 4 cards in one row; drill-down beside the cards.
- 200 % font → 1 card per row; chip labels wrap to 2 lines.
- Language switch → month labels re-render (Uzbek override), hints re-render.
- Screen reader: cards as buttons "Total revenue, 12 500 000 UZS, selected"; drill-down title announced on change.

## 9. Validation and messages
| Field/action | Rule | When checked | Message (EN) | Where shown |
|---|---|---|---|---|
| Load stats | HTTP 200 | on load / month change | "Couldn't load finance data." (**NEW**) | banner + Retry |
| Load drill-down | HTTP 200 | on KPI select | "Couldn't load details." (**NEW**) + Retry | inline card |
| Export | rows exist | on tap | `clinicFinanceExportNoRows` "Nothing to export for the current filters." | banner |

## 10. Navigation out
- Chips → other finance views (files 08–12), month filter carried over.
- Drill-down rows → plan detail / records / installments (target).
- Export → share sheet (file 13).
- Leaving the workspace keeps the selected month for the session.

## 11. Copy
- Chips: `clinicFinanceDashboard` "Dashboard", `clinicFinanceByAppointment` "By appointment", `clinicFinanceInstallments` "Installments", `clinicFinanceDoctorEarnings` "Doctor earnings", `clinicFinanceRecords` "Records", `clinicFinancePayments` "Payments"
- Month: `clinicFinanceMonthFilter` "Month", `clinicFinanceMonthAllTime` "All time"
- Cards: `clinicFinanceTotalRevenue` "Total Revenue", `clinicFinanceTotalRevenueHint` "Collected revenue across billable visits", `clinicFinanceTotalRevenueHintMonth` "Collected from billable visits in the selected month (by visit date)", `clinicFinanceOutstanding` "Outstanding", `clinicFinanceOverdueCount` "Overdue", `clinicFinanceCollectionRate` "Collection Rate", `clinicFinanceDoctorShareCollected` "Doctor share (collected)", `clinicFinanceDoctorShareGross` "Doctor share (gross)", `clinicFinanceClinicShareCollected` "Clinic share (collected)", `clinicFinanceClinicShareGross` "Clinic share (gross)"
- Top doctors: `clinicFinanceDashboardDoctorEarningsTop` "Top doctors", `clinicEarningsColVisits` "Visits"
- Drill-down titles/hints: `clinicFinanceDashboardRevenueDetail` "Payments contributing to revenue", `clinicFinanceDashboardRevenueHint` "Recorded payments on treatment plans", `clinicFinanceDashboardRevenueHintMonth` "Collected per billable visit in the selected month (by visit date)", `clinicFinanceDashboardOutstandingDetail` "Outstanding balances", `clinicFinanceDashboardOutstandingHint` "Treatment plans and invoices with remaining amount due", `clinicFinanceDashboardOutstandingHintMonth` "Unpaid visit balance in the selected month (by visit date)", `clinicFinanceDashboardOverdueDetail` "Overdue items", `clinicFinanceDashboardOverdueHint` "Overdue installments and invoices past due date", `clinicFinanceDashboardOverdueHintMonth` "Overdue is tracked all-time only; switch to All time to view overdue items", `clinicFinanceDashboardCollectionDetail` "Collection breakdown", `clinicFinanceDashboardCollectionHint` "Collected / expected · collection % per treatment plan", `clinicFinanceDashboardCollectionHintMonth` "Collected / expected · collection % per visit in the selected month"
- Empty: `clinicFinanceNoPayments` "No payment history yet.", `clinicFinanceDashboardNoOutstanding` "No outstanding balances.", `clinicFinanceDashboardNoOverdue` "No overdue items.", `clinicFinanceDashboardNoOverdueMonth` "Overdue is not tracked for a selected month. Switch to All time to view overdue items.", `clinicFinanceDashboardNoCollection` "No treatment plans with billable amounts yet."
- Misc: `clinicFinanceInstallColSeq` "#", `clinicFinanceInstallDue` "Due", `patient` "Patient", `treatmentPlanTitle` "Title"
- **NEW**: "Couldn't load finance data.", "Couldn't load details.", "Showing latest 50 · Open Payments", "Select a month to see revenue share", "See all in Doctor earnings"

## 12. Known problems in the current app (do not carry over)
- All six finance views are mounted at once and every chip tap refetches ~10 endpoints; the ledger view even loads while hidden.
- All-time drill-downs are built from the first 50 payments / records, so older items are missing without any hint.
- Month visits are fetched with page size 500 — a busy clinic silently loses rows beyond that.
- Dates printed without time-zone conversion; month list correct (clinic zone) but row dates not.
- Centred spinners; raw error text; CSV/PDF headers hard-coded in English ("Metric", "Value", "Month").
- Record status "VOIDED" is excluded here while the Records view uses "VOID" (backend enum UNVERIFIED).

## 13. Open questions for design
- Six inner views as chips vs. a segmented "Overview / Visits / Installments / Earnings / Records / Payments" dropdown title?
- Do we need charts at all? Today there are none; cards + lists may be enough for v1.
- Should the month filter live in the app bar so it is visibly shared by all views?
- Tapping a drill-down row: open the item or just copy its id?
