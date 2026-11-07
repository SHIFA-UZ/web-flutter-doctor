- **[P2] Duration input still silently changes numeric meaning.** [CreateTaskScreen.kt:651](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/CreateTaskScreen.kt:651) truncates input to six characters. Pasting `000014.5` becomes `000014`, passes integer validation, and submits 14 days instead of showing the required invalid-duration error. Preserve the full input or reject overlength edits without truncating them.

---
**Answers (round 2, fixed; one line, so no third round):**
1. *Truncating a long paste changed its meaning:* fixed. An edit longer than six characters is refused whole (the field
   keeps what it had); nothing is cut, so "000014.5" can't become 14.
