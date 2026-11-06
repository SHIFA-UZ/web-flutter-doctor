- **[P1] Colleague refreshes invalidate the own calendar.** [BookingScreen.kt:343](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/BookingScreen.kt:343) passes `doctorId`, but [CalendarSelectors.kt:39](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/calendar/CalendarSelectors.kt:39) invalidates every cached key on the affected dates regardless of doctor. Booking, cancellation, rescheduling and block removal therefore mutate the signed-in doctor’s cache freshness. Filter invalidation by `doctorId` as well as date.

- **[P1] Reschedule month paging reads and populates the own cache.** [AppointmentPanel.kt:296](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/AppointmentPanel.kt:296) calls `calendar.month(...)` without `doctorId`. Paging months in a colleague’s reschedule sheet fetches the signed-in doctor’s calendar; the colleague’s occupancy shading remains unloaded. Pass the panel’s doctor ID.

- **[P2] Switching clinics can retain the previous clinic’s timezone.** [ClinicSchedule.kt:170](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/clinic/ClinicSchedule.kt:170) keys the shell-owned ViewModel only by doctor ID. Opening the same doctor through another clinic reuses the old scope and clock, while lines 185–187 pass the new scope to panels. The calendar and booking panel can consequently display different times for the same slot. Include clinic/route identity in the ViewModel scope.

- **[P2] The required patient-detail round trip is unreachable.** [AppointmentPanel.kt:490](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/AppointmentPanel.kt:490) renders the patient name as non-interactive text; [MainScreen.kt:620](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:620) wires only visit and document navigation. There is no patient-detail callback from the colleague panel, so CLINIC-09’s patient-detail/back acceptance path cannot be performed.

---

- **P1 colleague refreshes invalidate the own calendar:** fixed at the root, in `changed`: only the same doctor's keys
  (any location) are invalidated; a colleague's booking leaves the doctor's own days fresh. Test (no read of the own day
  after a colleague's booking).
- **P1 reschedule month paging reads the own calendar:** fixed. The panel pages and reads the colleague's days. Test.
- **P2 a clinic switch keeps the old zone:** fixed. The ViewModel is keyed by the whole scope (doctor, name, zone,
  venue, clinic).
- **P2 no patient round trip:** fixed. The panel's patient name is a link (own calendar too, where it was "not built");
  a colleague's patient opens in the clinic's scope (`CalendarScope.clinicId`), and Back returns to the panel, then the
  colleague's calendar.
