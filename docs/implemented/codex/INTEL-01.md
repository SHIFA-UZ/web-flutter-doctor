- **[P2] Late updates invalidate the active chat after a doctor switch.** [ChatSessionStore.kt:120](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/intelligence/ChatSessionStore.kt:120): `ensureLoaded()` loads doctor B’s sessions, but a delayed `replaceMessages()` for doctor A still assigns A’s ID to `activeId`, even when no matching session exists. `state.active` becomes null. The same happens after deleting a chat whose turn finishes late. Ignore updates for absent sessions before changing the active ID.

- **[P2] Message updates retain the caller’s mutable list.** [ChatSessionStore.kt:124](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/intelligence/ChatSessionStore.kt:124): unlike Dart’s `List.of(messages)`, this stores the original list. Appending or clearing that list later silently changes stored state without a StateFlow emission or persistence, leaving memory and disk inconsistent. Copy the list when accepting the update.

- **[P2] Unicode whitespace breaks wake stripping on iOS.** [IntelligenceChat.kt:193](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/intelligence/IntelligenceChat.kt:193), lines 196–202: Kotlin/Native 2.3.0’s `\s` matches ASCII whitespace only, unlike [Dart’s ECMAScript regex semantics](https://api.dart.dev/dart-core/RegExp-class.html). `stripWakePhrase("Привет\u00A0Шифа, расписание")` therefore retains the greeting on iOS; Dart returns `расписание`. The same mismatch prevents starter-title normalization across nonbreaking spaces. Use an explicit shared whitespace class.

- **[P2] Recovered IDs can defeat the session cap.** [IntelligenceChat.kt:131](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/intelligence/IntelligenceChat.kt:131) assigns every missing-ID row the same ID because decoding shares one `now`. [ChatSessionStore.kt:145](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/intelligence/ChatSessionStore.kt:145) then caps by ID membership, retaining every duplicate. Loading 51 `{}` rows preserves all 51 sessions; deleting one also deletes them all. Generate unique recovered IDs or reject/deduplicate such rows before capping.

---

- **[P2] Late updates after a doctor switch / delete:** fixed. `replaceMessages` for a chat that isn't in the store
  changes nothing (the open chat stays). Test added.
- **[P2] Caller's mutable list kept:** fixed; the messages are copied (`toList()`). Test added.
- **[P2] Unicode whitespace on iOS:** fixed. Dart's `\s` set now lives in `utils/DartWhitespace.kt` (`DART_WS`), used by
  the wake-phrase and spacing patterns here and by `AiResponseText` (moved there from AI-02's private copy). Tests
  for "Привет + no-break space + Шифа" and a starter prompt with a no-break space.
- **[P2] Recovered ids defeat the cap:** fixed. A chat stored without an id gets its own (`chat_<ms>_r<index>`), and
  duplicate ids are read once; 51 id-less chats now cap to 50. Test added.
