- **[P1] Retrying an ambiguous payment failure can double-charge.** [VisitPlan.kt:267](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitPlan.kt:267) sets `recorded` only after receiving success. If the server commits but the response times out, the next End posts the same payment again without reconciliation or an idempotency key. BE-13 explicitly documents that this endpoint creates a row per call.

- **[P1] A lost fulfil response makes subsequent End attempts fail permanently for those ticks.** [VisitPlan.kt:252](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitPlan.kt:252) retains ticks on every failure and resubmits them unchanged. If fulfil committed before the timeout, the backend rejects the retry because those lines are no longer open. Refreshing candidates does not reconcile ticks or `fulfilled`, and the completed lines cannot be unticked.

- **[P1] Switching plans after payment success misattributes the payment in the PDF.** [VisitPlan.kt:180](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitPlan.kt:180) changes the selected plan without clearing or associating `recorded` with its original plan. After payment succeeds but completion fails, selecting another plan shows the original payment against that plan and locks its amount field. [VisitViewModel.kt:713](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitViewModel.kt:713) then writes that payment under the new plan’s title and currency.

- **[P2] Negative payment input becomes a positive charge.** [VisitPlan.kt:241](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitPlan.kt:241) removes the minus sign before validation: pasting `-100` produces `100`, passes the greater-than-zero check, and records a positive payment. Reject invalid input instead of changing its monetary meaning.

- **[P2] A failed totals refresh silently produces an incorrect financial summary.** [VisitPlan.kt:272](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitPlan.kt:272) ignores failure of the detail GET after payment and continues completion with pre-payment totals. [VisitViewModel.kt:709](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitViewModel.kt:709) copies those stale paid/outstanding values into the PDF alongside the successful new payment. Retrying End skips payment entirely, so it also skips this refresh.

---
**Answers (round 1, all fixed and tested; second round in DENTAL-03-r2.md):**
1. *An ambiguous payment failure could be posted twice:* fixed (refined in round 2: matched in the ledger, not by the
   plan's totals). Tested with a write whose answer was a timeout, and with one that wasn't written.
2. *A lost fulfil answer made later Ends fail:* fixed (refined in round 2: matched against this appointment's
   fulfilled lines). The next End sends only what is left. Tested.
3. *Switching plans after a payment misattributed it:* fixed. Once ticks were applied or a payment was tried for a
   plan, it can't be swapped: the picker shows it as text. Tested.
4. *A minus sign was stripped:* fixed. The amount is kept as typed. Anything that isn't a positive amount ("-100",
   "abc") shows "Enter an amount greater than 0" and is never posted as something else. Tested.
5. *A failed totals refresh left stale totals in the summary:* fixed. If the plan can't be read after the payment,
   the payment is added to the known paid and outstanding.
