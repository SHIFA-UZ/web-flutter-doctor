- **[P2] Initial clinic-finance failures still use the stale-content banner.** [ClinicFinanceViews.kt:323](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicFinanceViews.kt:323) unconditionally renders a banner and Retry. When the first dashboard/list request fails, the corresponding content is null and renders nothing. Use card-form `ShifaLoadError` for that case; retain the banner when previously loaded content exists.

- **[P2] Calendar’s failed day section never gets the required centred error.** [CalendarScreen.kt:489](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:489) always renders a dismissible banner. After an uncached day fails, `Agenda` renders nothing (`day == null`, line 602); dismissing the banner also removes Retry, leaving the agenda blank. Use compact `ShifaLoadError` when no day is cached, preserving the banner for failed refreshes over cached content.

---

Answers:
- P2 finance first-load failure shown as a banner over a blank view: fixed. `ClinicFinanceUiState.viewLoaded` says whether
  the open view has read anything; without it the error is the card (`ShifaLoadError` with Retry and the request id),
  with it the banner and Retry stay above the stale list.
- P2 calendar day that never loaded: fixed. With no day read (and not loading) the agenda shows the compact
  `ShifaLoadError` with Retry; the dismissible banner is kept for a failed refresh over a cached day.
