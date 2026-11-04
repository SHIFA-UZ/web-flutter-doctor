- **[P1] Release builds log raw exceptions.** [DocumentPicker.android.kt:42](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/androidMain/kotlin/shifa/doctor/utils/DocumentPicker.android.kt:42), `FileSaver.android.kt:34`, and `FileSaver.ios.kt:47` call `printStackTrace()` without a debug guard or redaction. File exceptions can contain document names and paths containing patient information.

- **[P1] Sharing crashes on iPad.** [TextShare.ios.kt:10](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/iosMain/kotlin/shifa/doctor/utils/TextShare.ios.kt:10) presents `UIActivityViewController` without configuring its popover anchor. Set `sourceView`/`sourceRect` or a bar-button anchor before presentation. [Apple documentation](https://developer.apple.com/documentation/uikit/uipopoverpresentationcontroller/sourceview).

- **[P2] Money formatting overflows.** [Money.kt:22](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/utils/Money.kt:22): `abs(Long.MIN_VALUE)` remains negative; adding 50 also overflows near `Long.MAX_VALUE`. Consequently, positive UZS amounts can display negative, and `Long.MIN_VALUE` produces malformed signs/fractions. Round using quotient/remainder without overflowing the magnitude.

- **[P2] Money parsing can return a negative amount for positive input.** [Money.kt:46](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/utils/Money.kt:46) checks multiplication but not the subsequent addition. `parse("92233720368547758.08", "USD")` wraps to `Long.MIN_VALUE`; include the fractional part in the overflow check.

- **[P2] A separator alone becomes a valid zero amount.** [Money.kt:41](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/utils/Money.kt:41): `"."` and `","` pass validation and return `0`, including for UZS. Require at least one digit so incomplete input cannot pass as a zero price.

- **[P2] Phone normalization silently accepts corrupted input.** [PhoneE164.kt:15](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/utils/PhoneE164.kt:15) discards every non-digit: `"abc901234567"` and `"++998901234567"` both become valid-looking Uzbek numbers. Validate allowed punctuation and prefix placement before removing formatting.

- **[P2] Document-read failures leak the input stream.** [DocumentPicker.android.kt:18](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/androidMain/kotlin/shifa/doctor/utils/DocumentPicker.android.kt:18) closes the stream only after reading, querying metadata, and invoking the callback. Any exception skips closure. Wrap the stream in `use`.

- **[P2] Saving can report success without writing bytes.** [FileSaver.android.kt:57](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/androidMain/kotlin/shifa/doctor/utils/FileSaver.android.kt:57): a null output stream skips writing, then publishes the empty entry and returns success. Treat null as failure and delete the inserted MediaStore entry on write failure.

- **[P3] Failed timezone parsing can suppress the missing-zone flag.** [PracticeClock.kt:32](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/utils/PracticeClock.kt:32): `" UTC "` fails the untrimmed `TimeZone.of` call but passes the trimmed whitelist, yielding fallback UTC with `zoneMissing == false`. Derive the flag from parsing success, or normalize before parsing.
---
**Answers (all fixed):**
1. *`printStackTrace` in pickers and savers:* replaced by `httpLogger` with the exception class only (debug builds, no file names or paths).
2. *iPad share crash:* the activity sheet's popover is anchored to the centre of the root view.
3. *Money format overflow:* uses quotient and remainder of the signed value, so there is no `abs(Long.MIN_VALUE)` and no `+ 50`; tests cover `Long.MAX_VALUE` and `Long.MIN_VALUE`.
4. *Money parse overflow:* the bound includes the fraction (`(MAX - cents) / 100`); a test covers it.
5. *"." and "," parse as zero:* at least one digit is required; tests cover it.
6. *Phone accepts junk:* only digits, spaces, `()-.` and one leading `+` (or `00`) are allowed; tests cover "abc…" and "++…".
7. *Picker stream leak:* `openInputStream(uri)?.use { … }`.
8. *Saver reports success without writing:* a null stream or a failed write is a failure, and the MediaStore entry is deleted.
9. *" UTC " sets no missing flag:* the id is trimmed before parsing, and `zoneMissing` means "didn't parse"; a test covers a padded zone.
These are local fixes covered by tests, so no second round.
