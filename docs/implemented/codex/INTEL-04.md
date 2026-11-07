- **[P1] Leaving Intelligence does not cancel the stream.** [IntelligenceScreen.kt:140](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/IntelligenceScreen.kt:140) only calls `stop()` when the active ID changes; `onPauseOrDispose` is empty. The ViewModel belongs to the shell’s store, so switching tabs leaves it running and persisting replies. This violates INTEL-04’s Fix line. Cancel when the screen leaves composition.

- **[P1] Chat switching races with completion and can reopen the previous chat.** [IntelligenceScreen.kt:157](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/IntelligenceScreen.kt:157) selects immediately but defers cancellation to `LaunchedEffect`. If the old stream finishes before that effect executes, [ThreadViewModel.kt:165](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadViewModel.kt:165) unconditionally calls `replaceMessages`, which sets `activeId` back to the old chat. Stop synchronously before changing chats and reject completion for an inactive turn.

- **[P2] Long replies cannot follow the newest text reliably.** [ThreadScreen.kt:100](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadScreen.kt:100) treats any visible portion of the final item as “at bottom,” then scrolls to that item’s **start**. The entire pending question/reply occupies one item; once it exceeds the viewport, new text stays below the screen, and scrolling upward within that item still triggers automatic scrolling. Track the actual bottom distance and scroll to the content’s end.

- **[P2] Reply language leaks between conversations.** [ThreadViewModel.kt:107](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadViewModel.kt:107) falls back to one ViewModel-wide `language`, which `stop()` never resets. After a Russian conversation, opening a new chat and sending an undetectable fragment such as `8:30` sends `RU` even when the app language is Uzbek. Scope language to the active conversation.

- **[P2] Appointment-list tags differ from Flutter for decimal JSON numbers.** [ChatHistory.kt:70](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/intelligence/ChatHistory.kt:70) accepts only `longOrNull`; Flutter’s `_appointmentIds` accepts any `num` and calls `toInt()`. For `{"appointmentId":101.0}`, Flutter emits `<appointment_list>101</appointment_list>` while Kotlin omits it. The golden fixture covers only integer literals. Match Dart’s numeric conversion and add this contract case.

---

- **[P1] Leaving didn't cancel:** fixed. The running turn stops when the Intelligence screen leaves composition
  (`DisposableEffect`), so a tab tap or back to the clinic closes the stream.
- **[P1] Switching chats raced with completion:** fixed. Select, New conversation, Delete (of the open chat) and Back
  stop the turn synchronously before the store changes, and a finishing turn whose chat is no longer the open one is
  dropped instead of saved (so it can't reopen the old chat). The guard has no dedicated test (the race needs a
  stream held open across a chat switch); the emulator check switched chats and tabs mid-session.
- **[P2] Following long replies:** fixed. The list follows while the doctor's last scroll ended at the real bottom
  (`canScrollForward`), and scrolls to the end of the last item (one long reply is one item), not its start.
- **[P2] Reply language leaked between chats:** fixed; the language is kept per conversation id. Test added (a Russian
  chat, then "8:30" in a new chat goes as the app's UZ).
- **[P2] Decimal ids in `<appointment_list>`:** fixed to Dart's `num.toInt()` (101.0 → 101); the golden fixture now has
  `102.0`.
