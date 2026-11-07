- **[P2] System messages still cannot be selected or copied.** [ThreadScreen.kt:344](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:344) returns plain `Text` before reaching the new `SelectionContainer`. Round 1’s selection finding remains unresolved for system text, which spec §1.3 explicitly makes selectable.

- **[P2] Message links are inaccessible to screen-reader users.** [ThreadScreen.kt:374](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:374) uses `clearAndSetSemantics` on the enclosing column, replacing descendant semantics with only a description. This removes the link actions created at line 391 from the accessibility tree: TalkBack/VoiceOver can announce the URL but cannot activate it. Preserve descendant actions while providing the bubble’s spoken context.

---
**Answers (round 2, both fixed):**
1. *System lines weren't selectable:* fixed. They are in a `SelectionContainer` too.
2. *Screen readers lost the links:* fixed. The bubble now only says who, when and how it went ("Patient, 08:47",
   "06:17, Read"); a text stays its own node after it, so its links can be activated; the visible role and time are
   hidden from screen readers so nothing is read twice; an attachment still says what it is in words. Checked in demo
   with the accessibility tree.

