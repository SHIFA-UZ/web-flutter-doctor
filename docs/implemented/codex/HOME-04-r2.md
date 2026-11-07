- **[P2] Entitlement changes are dropped during an active refresh.** [HomeAttentionViewModel.kt:88](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeAttentionViewModel.kt:88) updates `lastWithTasks` before entering the gate, which rejects even forced calls while running. If the plan arrives during the initial notification read, tasks remain unloaded; a downgrade during a task read can leave task tiles visible. Replay the latest entitlement after the active read and discard results for obsolete entitlements.

- **[P2] Older unread notifications still disappear beyond 100 rows.** [HomeAttentionViewModel.kt:120](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeAttentionViewModel.kt:120) stops after five pages regardless of `last`. With 100 read notifications followed by unread ones, Attention still incorrectly shows “All caught up!” The pagination fix must reach unread items or exhaustion, or explicitly represent an incomplete result.

- **[P2] Marking displayed notifications read does not replenish Attention.** [HomeAttentionViewModel.kt:132](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeAttentionViewModel.kt:132) only changes local read flags. Pagination stops once four unread items are found (line 126); marking those four read can therefore show “All caught up!” while subsequent pages contain unread notifications. Continue fetching to replenish the unread slots after successful marks.

---
**Answers (round 2, all fixed; tested, no third round):**
1. *A plan change during a read was dropped:* fixed. The gate is replaced by the ViewModel's own reader: a forced or
   plan-changing ask during a read runs once more when it ends, always with the latest plan (a downgrade drops the
   tasks). Tested.
2. *Unread beyond the page cap:* answered with an explicit state. Reading goes on to four unread or the end, up to ten
   pages; stopping short with fewer than four sets `incomplete`, and the card says "More unread notifications are in
   the inbox." instead of "All caught up!" (the inbox pages on).
3. *Marking the shown ones read didn't refill:* fixed. After a successful mark that leaves fewer than four unread while
   the list wasn't read to the end, the card reads again (forced).
