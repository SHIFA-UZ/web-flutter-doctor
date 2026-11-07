- **[P2] “Send message” bypasses the split layout.** Only `Conversation` routes enter `ChatPanes` at `ui/navigation/MainScreen.kt:654–664`; `PatientChat` still renders directly at `:644–652`. CHAT-07 installs that route on the Chat tab (`ShellViewModel.kt:474–484`), and its resolved state directly renders `ThreadScreen` (`ui/chat/PatientChat.kt:81–87`). On wide screens, this entry point therefore loses the conversation list and selection.

- **[P2] Selecting a conversation resets the visible list’s scroll position.** `ui/navigation/MainScreen.kt:849–852` creates the list inside the current route and replaces that route with a fresh timestamp on every selection. The enclosing `SaveableStateProvider` is keyed by the route (`:356`), while `ui/chat/ChatListScreen.kt:179–189` uses the `LazyColumn`’s locally remembered scroll state. Scroll down and open a conversation: the left pane returns to the top, potentially hiding the selected card. Keep the list’s scroll state under a stable Chat-tab identity.

---

Answers:
- P2 "Send message" bypassing the split: fixed. A `PatientChat` route on the Chat tab also goes through `ChatPanes`
  (`threadOpen = true`, no selection until it resolves); opened from another tab it stays full screen.
- P2 list scroll reset on every selection: fixed. The list's `LazyListState` lives in `ChatListViewModel`, which
  outlives the route change, so the list keeps its position while the open conversation changes.
