- **[P2] KPI labels are clipped at tablet widths.** [AnalyticsSection.kt:272](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/analytics/AnalyticsSection.kt:272) forces four tiles across at 768px. After the rail, page/card padding, gaps and icons, each label has about 73px. “Appointments” alone measures about 81px in the bundled font, so “Appointments Today” requires three lines; `maxLines = 2` at line 323 hides part of the label. Allow taller labels or adapt the tile arrangement to available width.

- **[P2] Enlarged text collapses the Tasks heading.** [TasksScreen.kt:230](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksScreen.kt:230) gives the title only the space remaining after three unweighted actions. At 768px with 200% text size, the Uzbek button labels alone require approximately 644px, exceeding the header’s available width before button padding, icons and gaps. The title/subtitle receive zero width and Create Task is truncated. Wrap or stack the actions when their measured widths leave insufficient space.

---

Answers:
- P2 KPI labels clipped at tablet widths: fixed. The four tiles go in one row only from 900 wide (`Layout.Document`),
  two by two below that, so each label keeps room for its two lines.
- P2 Tasks header collapsing with large text: fixed. The actions sit beside the title only from 900 wide at normal
  text size (font scale up to 1.3); otherwise the title comes first and the three actions wrap under it.
