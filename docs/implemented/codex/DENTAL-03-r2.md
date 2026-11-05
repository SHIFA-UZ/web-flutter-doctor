- **[P1] Aggregate totals cannot identify the attempted payment.** [VisitPlan.kt:288](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitPlan.kt:288) treats any sufficient increase in `paidMinor` as this visit’s payment. If this POST fails without committing and reception records an equal payment, retry skips this visit’s payment and falsely reports it as recorded. Reconcile against an identifiable ledger entry, not the aggregate balance.

- **[P1] A definitive payment rejection traps the visit in payment retries.** [VisitPlan.kt:291](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitPlan.kt:291) sets `paidBefore` and `locked` before posting and retains both even on a definitive 400/403 rejection. The amount cannot be corrected or cleared, the plan cannot be deselected, and every End retries the rejected payment instead of completing. Release the pending-payment lock when rejection establishes that nothing was recorded.

- **[P2] The payment method remains editable while its outcome is unresolved.** [PlanPanel.kt:341](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/PlanPanel.kt:341) leaves the method dropdown active despite `paidBefore`. After a Cash payment commits but its response is lost, changing to Card and retrying reconciles the payment as successful; the PDF then labels the original Cash payment as Card. Preserve the attempted method alongside the amount.

- **[P2] Fulfilment reconciliation attributes another visit’s work to this visit.** [VisitPlan.kt:270](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitPlan.kt:270) moves every completed tick into this visit’s `fulfilled`, without checking which appointment completed it. If another appointment completes a ticked line before this POST is rejected, the next End succeeds and the PDF incorrectly lists that line under “Completed this visit.” Reconcile against this appointment’s fulfilled line IDs.

---
**Answers (round 2, all fixed and tested):**
1. *The totals can't identify the payment:* fixed. Before posting, the app counts the ledger rows (`GET
   /clinics/{id}/finance/payments`, the newest 50) for this visit, plan, amount and method. After a failure the next
   End counts again; only a new matching row means it was recorded. A payment reception records by other means
   doesn't count. BE-13's idempotency key would still make this exact. Tested.
2. *A clear refusal trapped the visit:* fixed. A 4xx other than 408 recorded nothing, so the attempt is released:
   the amount can be changed or cleared, and the plan swapped. A 5xx, a timeout or no answer keeps it until End checks
   the ledger. Tested.
3. *The method stayed editable:* fixed. While a payment's outcome is unknown, both the amount and the method are held
   (the method shows as text), so the summary names the method that was sent. Tested.
4. *Another visit's work counted as this visit's:* fixed. After a failed fulfil, the appointment's own
   `fulfilledLineIds` are read again. Only ticked lines listed there count as applied; ticked lines completed by
   another visit are dropped. Tested.

The fixes add their own tests and touch only `VisitPlan`, so no third round.
