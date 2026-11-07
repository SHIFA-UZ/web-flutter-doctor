- **[P1] Day-divider keys can collide and crash the thread.** [ChatRules.kt:100](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/chat/ChatRules.kt:100) appends pending messages after all server messages. A failed message from yesterday, followed by today’s server messages, produces day sections yesterday → today → yesterday. [ThreadScreen.kt:273](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:273) keys both yesterday dividers identically, violating LazyColumn’s unique-key requirement.

- **[P2] Failed read acknowledgements are never retried for the same incoming message.** [ThreadViewModel.kt:93](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadViewModel.kt:93) advances `readUpTo` before `markRead` succeeds and ignores its result. After a network failure, subsequent successful polls skip acknowledgement until another incoming message arrives; the displayed message remains unread server-side.

- **[P2] An older poll response can erase a successfully sent bubble.** [ThreadViewModel.kt:85](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadViewModel.kt:85) replaces the entire message list without reconciling concurrent sends. If a GET snapshots the thread before a send but returns after that send succeeds, it removes the acknowledged message after its pending bubble has already been cleared. The message disappears until a later successful poll.

- **[P2] Reopening a conversation loses its draft and failed-send retry state.** [ThreadScreen.kt:121](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:121) keys the ViewModel by `opened`, which [MainScreen.kt:402](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:402) regenerates on every list selection. Type a draft or fail a send, return to the list, then reopen: a fresh ViewModel hides that text. Flow §7 explicitly requires drafts to survive these exits for the session.

- **[P2] Message text cannot be selected or copied.** [ThreadScreen.kt:387](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:387) renders ordinary `Text` without a surrounding `SelectionContainer`; system text does the same at line 345. Long-press selection required by CHAT-03 and flow §7 is absent.

---
**Answers (round 1, all fixed and tested; a P1 and a ViewModel change, so a second round follows):**
1. *Day-divider keys could collide:* fixed. Each divider carries its position (`ThreadRow.Day.ordinal`) and is keyed
   by it, so an old failed text after today's messages gets a second "Yesterday" with its own key. Tested.
2. *A failed read acknowledgement was never retried:* fixed. The read mark moves only when `markRead` succeeds, so the
   next answer tries again. Tested (fails once, then goes through, then stops).
3. *An older read could erase a sent message:* fixed. Messages this screen sent stay until a read returns them, so a
   read that started before the send answered can't take one away. Tested with a held read.
4. *Reopening lost the draft and failed texts:* fixed. The thread's ViewModel is one per conversation for the session
   (keyed by its id), so going back to the list and returning keeps both (flow 07/02 §7).
5. *Text couldn't be selected:* fixed. Each bubble's text (and a text on its way) is in a `SelectionContainer`:
   long-press selects and copies; links still open.

