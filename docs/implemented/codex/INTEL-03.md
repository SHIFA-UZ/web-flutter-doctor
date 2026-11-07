- **[P2] Profile data bypasses the ViewModel.** [IntelligenceScreen.kt:144](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/IntelligenceScreen.kt:144) directly collects the global `profileRepository.me`. This violates the requested MVVM boundary and makes the greeting depend on global state even when a test supplies ViewModels. Expose the profile name through `IntelligenceUiState`.

- **[P2] The required welcome interaction test was not ported.** [WelcomeTest.kt:30](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/androidUnitTest/kotlin/shifa/doctor/ui/intelligence/WelcomeTest.kt:30) checks resource identifiers without rendering or tapping a card; the recent-chat test checks IDs only. INTEL-03’s “Done when” explicitly requires the Flutter test’s prompt, label, and recent-preview checks. These tests would pass with disconnected click handlers or missing rendered labels/previews. Add rendered assertions and taps that verify the emitted prompt text.

---

- **[P2] Profile read outside the ViewModel:** fixed. `IntelligenceViewModel` follows the profile (constructor-injected
  `ProfileRepository`) and exposes the doctor's first and last name in `IntelligenceUiState`; the screen only formats
  them. Test added.
- **[P2] No rendered / tapping welcome test:** not done as asked. Neither this app nor the patient app has Compose UI
  test support (`ui-test-junit4` / `runComposeUiTest`), and the tech stack is fixed unless the user agrees to a new
  dependency. What covers it now: `Intelligence_welcome` / `_welcomeFirst` screenshots (labels, subtitles, recent
  previews), `WelcomeTest` (prompts per card, every language's prompt shortening to its title, the five recent chats),
  and the emulator check (a starter tap sent "Summarize my schedule for today." and the reply came back). Raised to
  the user as a question (add Compose UI tests to both apps' catalogs?).
