- **[P1] Cancellation previews display a resource key instead of the confirmation.** [Cards.kt:311](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/Cards.kt:311) resolves only string resources, but `intelCancelPreview` is a **plural** resource (`values/plurals.xml:44`). The lookup falls back to `"intelCancelPreview"`, so `CardText.kt:165` loses the visit count, patient list and confirmation instructions. Resolve the plural using the captured count.

- **[P2] Resource-backed substitutions leave literal `%1$s` placeholders on screen.** [CardText.kt:157](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/intelligence/CardText.kt:157) replaces `{count}`, but the Compose resource uses `%1$s`; `rememberCardText()` calls `tr()` without arguments. The same mismatch affects upcoming ranges, skipped cancellations, additional visits and visit dates (`CardText.kt:138,166,1638–1648`). For example, a cancellation limit renders “That matches more than %1$s visits…”. Adapt the resource formatting contract. `CardTextTest.kt:18–23` loads the old JSON with brace placeholders, so its golden assertions conceal both integration defects.

---

- **[P1] Cancellation preview showed its key:** confirmed and fixed. `intelCancelPreview` is a plural resource here
  (it replaced Flutter's string with `{visitWord}`). `CardText` takes a plural lookup; the screen resolves the plural
  for the counts 1, 2 and 5 (`trPlural`) and `CardText.pluralSample` maps any count onto the same CLDR form (ru: one /
  few / many; en, uz: one / other). The preview now reads "Могу отменить 5 предстоящих визитов…" etc.
- **[P2] `%1$s` on screen:** confirmed and fixed. The generated resources number placeholders in English order;
  `rememberCardText` resolves each templated key (`CardText.PLACEHOLDERS`) with its `{name}`s as arguments, so the text
  comes back with `{name}` placeholders for `CardText` to fill.
- **Test hid both:** fixed. `CardTextTest` now builds `CardText` from the generated `values*/strings.xml` and
  `plurals.xml` with the same placeholder and plural contract as the screen (not from the JSON); the golden tests pass
  on that path, plus a new test for the placeholders, the ru plural forms and the samples.
