- **[P2] Legacy aliases duplicate billable services.** [DentalModels.kt:111](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/DentalModels.kt:111) groups normalized keys and concatenates their lists. A saved record containing the same service under `UR1` and `11` becomes two services and doubles the subtotal. Flutter’s documentation loader assigns one list per resolved key; it does not concatenate aliases.

- **[P2] Saved discounts disagree with calculated totals.** [DentalModels.kt:77](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/DentalModels.kt:77) leaves `discountPercent` unchanged, although `totals` clamps it. For example, `150` produces a zero total but serializes a 150% discount. Flutter’s `_toPayload()` uses `_discountValue()`, which clamps to 0–100. Normalize the discount in the payload too.

- **[P2] Malformed metadata throws instead of being tolerated.** [DentalModels.kt:97](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/DentalModels.kt:97) and line 122 call `jsonPrimitive` without checking the element type. `{"dentition":{},"teeth":{}}` or `{"notes":[]}` throws `IllegalArgumentException`, defeating `read()`’s nullable, tolerant contract and preventing otherwise valid services from loading.

- **[P2] Plan progress and selection are inaccessible.** [ToothMap.kt:159](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/ToothMap.kt:159) communicates selection only through a border; plan status is communicated only through fill. Semantics expose only `description`, whose default contains tooth/count. Screen-reader users cannot distinguish planned, partial, completed, or selected teeth. Expose localized state descriptions and selection semantics.

- **[P2] Required localized anatomy captions and dentition control are absent.** [ToothMap.kt:49](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/ToothMap.kt:49) accepts a fixed dentition without a change callback, and its body renders only tooth rows. DENTAL-01 explicitly requires a dentition segmented toggle and localized anatomy captions; its Fix line specifically requires caption localization. Neither is implemented.

---
**Answers (round 1; small, tested fixes, so no second round):**
1. *Legacy and FDI keys for the same tooth were added up:* fixed. The later key wins, as in Flutter's loader. Tested.
2. *The payload kept a discount over 100:* fixed. `payload()` clamps it to 0–100, as Flutter's `_discountValue()` does. Tested.
3. *Odd metadata threw:* fixed. `dentition` and `notes` are read only when they are plain values. Tested with `{}` / `[]`.
4. *Plan state and selection were visual only:* fixed. `describe` now gets the tooth's plan state, so the panel can
   say "planned / partly done / completed", and the selected tooth carries `selected` semantics.
5. *No anatomy captions and no dentition toggle:* by design. The board (`DaVisit` "TOOTH MAP", the UI's source of
   truth) has neither. Flutter's English captions aren't ported, so there is nothing to localise. The dentition toggle
   goes in the DENTAL-02 panel, which owns the record it changes.
