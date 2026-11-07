- **[P2] Discount remainder is silently lost.** [DentalModels.kt:259](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/DentalModels.kt:259): same-currency amounts `[101, 101, 0]` at 40% require a discount of 81, but produce `[40, 40, 0]`. Clamping the last share discards the remaining unit. Redistribute overflow to earlier lines with available capacity.

- **[P2] Displayed planned total differs from emitted charges.** [DentalPanel.kt:241](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/DentalPanel.kt:241) displays the rounded net amount, while [DentalModels.kt:256](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/DentalModels.kt:256) rounds the discount. A 101-minor-unit line at 50% displays 51 but emits a net charge of 50. Both paths need the same rounding calculation.

- **[P2] Currency normalization happens after discount grouping.** [DentalModels.kt:254](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/DentalModels.kt:254) groups blank currency separately from `UZS`, although line 277 emits both as `UZS`. Two one-unit lines, one blank and one `UZS`, at 50% receive two units of discount instead of one. Normalize currency before grouping.

- **[P2] Required catalog search is missing.** [DentalPanel.kt:344](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/DentalPanel.kt:344) filters only by group; the picker has no search input or title/code filtering. DENTAL-04 explicitly requires catalog search, and §4.4 enables it above eight services. Group chips cannot locate a specific service within a large group.

---
**Answers (round 1, all fixed and tested; small and covered by tests, so no second round):**
1. *A clamped line lost its share of the discount:* fixed. What a line can't take goes to the lines with room, last
   first. Tested with 101, 101 and 0 at 40 % (81 in all).
2. *The total shown differed from the lines' charge:* fixed. Totals and plan lines now use one rounding: the
   discount rounds a half up and the total is the subtotal less it. Tested with 101 at 50 %. The visit's total
   changes by one minor unit at an exact half (`DentalModelsTest` updated: 100 005 at 10 % → 90 004).
3. *A blank currency was grouped apart from UZS:* fixed. The currency is normalised (upper case, blank → UZS) before
   grouping, for both the lines and the totals. Tested.
4. *No catalogue search:* added. Above 8 services the sheet has a search by title or code, with "No services match
   your search." when nothing matches.
