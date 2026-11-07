- **[P2] Sidebar badges truncate `99+`.** [ShellSidebar.kt:355](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/ShellSidebar.kt:355) fixes the badge width at 20dp, then subtracts 8dp of horizontal padding. The remaining 12dp cannot fit the capped `99+` label; `Text` clips it. Use a minimum width with content-driven expansion, as the More sheet does.

- **[P2] The footer Profile button has no accessible name in rail mode or for staff.** [ShellSidebar.kt:183](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/ShellSidebar.kt:183) creates a clickable button without a description. Its avatar has null semantics, and the text is omitted when `rail` is true or `identity` is null (lines 187–188). Screen readers encounter an unnamed button. Add a localized Profile description to the clickable container.

---

Answers:
- P2 sidebar count pill clipping "99+": fixed. The pill is at least 20 × 20 and grows with its text (padding 6), as
  More's pill does.
- P2 footer Profile button unnamed in the rail and for staff: fixed. It is described as "Profile" whenever the name
  isn't shown next to the photo.
- Also changed after the review: the Shifa Intelligence card follows DaApp (tinted, a border only while it is open)
  instead of the older Sidebar board's outline.
