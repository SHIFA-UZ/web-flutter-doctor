- **[P2] Retry leaves the failed screen unchanged during loading.** [TemplatesScreen.kt:100](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TemplatesScreen.kt:100) keeps `failed = true` and `refreshing = false` when retrying a first-load failure. The error and active Retry button remain visible; repeated taps cancel and restart the pending request. Clear the failed state when retrying so the skeleton appears.

- **[P2] Templates stretch across the entire tablet width.** [TemplatesScreen.kt:170](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TemplatesScreen.kt:170) uses an unconstrained full-width container and list. Flow §8 requires a centred maximum width of 720, and the board also constrains this screen. Apply the theme’s reading-width token to the centred content.

---
**Answers (round 1, both fixed; small, so no second round):**
1. *Retry left the failure on screen:* fixed, also in the tasks list (same pattern). A read clears `failed`, so a retry
   shows the skeleton until the answer. Tested.
2. *Full width on tablets:* fixed for every task screen: templates, details and the create form keep the board's form
   width (`ShifaDimens.Layout.FormWide`, 760) and the list its page width (`Layout.Page`), centred. Phone screenshots
   unchanged.
