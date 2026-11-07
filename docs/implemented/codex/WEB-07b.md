- **[P2] Wide header escapes the page width limit** — [HomeScreen.kt:380](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeScreen.kt:380). The header fills the entire content pane, while the columns are centered and capped at `Layout.Page` (line 336). Above 1280 available width, the greeting and header actions drift outside the content edges. `DaHome.dc.html:58–60` places both inside the same capped page. Apply the same centered width constraint to the wide header.

---

Answers:
- P2 wide header outside the page limit: fixed. On wide screens the header is centred and capped at `Layout.Page`
  like the columns, so above 1280 the greeting and the buttons line up with the cards.
