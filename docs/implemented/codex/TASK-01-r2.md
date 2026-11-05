- **[P3] Demo task notification references a nonexistent task.** [DemoNotifications.kt:146](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/debug/DemoNotifications.kt:146) targets `taskId=1`, but [DemoTasks.kt:128](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/debug/DemoTasks.kt:128) seeds that patient’s “Blood pressure diary” as `501`. Resolving the notification’s task now returns the local 404. Update the notification fixture to reference `501`.

---
**Answers (round 2, fixed; one line, so no third round):**
1. *The demo task notification pointed at a task that doesn't exist:* fixed; it names task 501 (the blood-pressure
   diary it describes).
