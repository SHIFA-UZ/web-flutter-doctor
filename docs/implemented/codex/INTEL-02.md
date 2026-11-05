- **[P2] Leaving Intelligence skips the required appointment refresh.** [MainScreen.kt:269](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:269) wires `onLeave` directly to `actions.back`. `ShellViewModel.back()` only clears `intelligence`; it does not invalidate appointments. Home and Calendar subsequently use throttled reads, so returning can retain cached appointments. INTEL-02 explicitly requires “clinic + appointment refresh.” Invalidate appointment data when leaving.

- **[P2] History titles can disappear at large font sizes.** [IntelligenceScreen.kt:342](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/IntelligenceScreen.kt:342) keeps the title and timestamp in one row, with only the title weighted. The unweighted timestamp consumes space first; at 200% text size on narrow phones, especially with a full date and patient icon, it can leave the title effectively no width. Flow 03/02 §8 explicitly requires moving the time below the title in this case.

---

- **[P2] Leaving skipped the appointment refresh:** fixed in the shell, for every way out (Intelligence's own back,
  the system back, a tab tap): `ShellViewModel` reads the schedule again (`calendarRepository.changed` on the practice
  clock, which also tells every visit list) whenever `intelligence` turns off. `ShellViewModelTest` counts the reads.
- **[P2] Titles squeezed at large text:** fixed; from 150 % text the time goes under the title in the tile.
- Also: the shell's screenshot test now reaches the real screen, whose chats live in the keystore-backed secure
  settings that Robolectric can't open; `MainScreenContent` takes the Intelligence surface as a slot (the real screen
  by default) and the preview passes the stateless content. `Shell_intelligence.png` re-recorded.
