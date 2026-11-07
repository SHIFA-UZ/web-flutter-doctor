- **[P1] A pending fact commit can be submitted twice after navigation.** [ThreadViewModel.kt:204](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadViewModel.kt:204) guards only with transient `cards[index].busy`. `stop()` clears that state while the commit coroutine continues. Start a slow commit, open “Review on the visit,” return, and tap again: a second POST is sent before `documented` is persisted. Track pending commits by chat/card independently of screen state.

- **[P2] Duplicate document cards cancel and clear each other’s viewer.** [DownloadedDocument.kt:60](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/DownloadedDocument.kt:60) gives every occurrence of the same document the same ViewModel, but each occurrence independently calls `load()` and `release()` at lines 63–64. When one card leaves the LazyColumn composition, it cancels the shared download and resets the other visible card to `Loading`; that card’s load effect does not restart. Use per-instance ownership or reference-count shared downloads.

- **[P2] Back from a pushed route loses the thread’s position and selected document.** [MainScreen.kt:282](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:282) removes Intelligence from composition, but only pushed routes have a `SaveableStateProvider`; the Intelligence branch at line 290 has none. Returning recreates its list and card selection state, and initial follow-bottom scrolls to the latest message. Preserve Intelligence under a stable saveable-state key.

- **[P2] “Review on the visit” misroutes video appointments.** [IntelligenceScreen.kt:234](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/IntelligenceScreen.kt:234) hardcodes `VisitKind.InPerson` for every draft-facts appointment. `MainScreen` uses this value to enable the video workspace, so facts from a video consultation open a workspace without its call controls. Resolve the appointment’s actual kind before routing.

- **[P2] Dental notes disappear when no tooth has services.** [Cards.kt:638](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/Cards.kt:638) returns immediately when `counts` is empty, skipping both the diagram and `card.notes` at line 662. The backend can return valid documentation containing notes or general services with empty tooth counts. Render those notes even when no teeth are marked.

---

- **P1 commit sent twice after leaving and returning:** fixed. `ThreadViewModel.committing` keeps the running commits
  by chat and card, not cleared by `stop()`; back in the chat the card shows it still running. Test: "a commit still
  running after leaving and coming back is not sent again".
- **P2 two cards of one document sharing a download:** fixed. Each card owns its download (the ViewModel key carries
  the card's position), so one leaving doesn't cancel or reset the other.
- **P2 thread position lost after Back:** fixed. Intelligence sits under its own `SaveableStateProvider` in the shell,
  and the thread's follow flag is saveable, so Back from a visit or document finds the thread where it was.
- **P2 video visit opened as in-person:** fixed. "Review on the visit" reads the appointment first
  (`ThreadViewModel.openVisit`) and opens it as what it is; in the clinic if it can't be read (the screen reads it
  again anyway). Test: "Review on the visit opens a video visit as video, others in the clinic".
- **P2 dental notes hidden without tooth services:** fixed; the hint and the notes both show.
