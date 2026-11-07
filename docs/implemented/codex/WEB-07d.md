- **[P2] Wide filter chips become unreachable with enlarged text.** [PatientsScreen.kt:488](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/patients/PatientsScreen.kt:488) puts all three chips in a non-wrapping, non-scrolling `Row`, with the sort chip reserving width alongside it. At the 768-wide shell breakpoint, Russian labels with 200% text scaling exhaust the available width and clip the trailing Follow-ups chip. Use a wrapping layout or horizontal scrolling so every filter remains visible and actionable.

---

Answers:
- P2 wide filter chips clipped with large text: fixed. The three chips wrap (FlowRow) beside the sort chip, as the
  phone's do, so every filter stays visible at 768 in Russian with large text.
