# UI strings (extracted from the Flutter doctor app)

Source: `assets/localization/{en,uz,ru}.json` merged over the embedded `_localizedValues` map in
`lib/core/localization/app_localizations.dart:25-6276` (JSON wins at runtime, see `translate()` at `:6278`).
Export date: 2026-10-24, commit `4d05e61` (refreshed after PR #16, which added 63 `intel*` / visit-fact / cancellation keys).

> Note: `scripts/export_localization_json.js` silently stops collecting keys after the first double-quoted value
> (`'todaysSchedule': "Today's schedule"`), which is why the baseline export showed 367 "missing" English keys. The
> files here were produced with a quote-safe parser; do not regenerate them with the repo script.

## Format

- One flat `{ "key": "value" }` file per locale, keys sorted, UTF-8, non-ASCII kept as-is, 2-space indent.
- Values are the exact runtime strings (Dart escapes already resolved).
- Key counts:

  | Locale | Keys | Missing vs union |
  |---|---|---|
  | en | 1919 | 1 (`calendarColleagueDoctorFallback`, referenced; add it) |
  | uz | 1910 | 10 |
  | ru | 1828 | 92 |
  | Union | 1920 | — |

- 41 keys are referenced in code but defined in **no** locale (`appLock`, `enterPin`, `confirmPin`, `incorrectPin`,
  `patientAppUsers`, `clinicStaffMobileTitle`…). They render as the raw key today. Full lists: [missing-keys.md](missing-keys.md).
- Every key is a valid identifier (no spaces). Placeholders are consistent across locales (checked).

## Fallback semantics in Flutter (do NOT copy the gaps)

`translate(key)` resolves: asset JSON for the locale → embedded map for the locale → **only for `en`** the embedded
English map → the raw key. `uz` and `ru` deliberately never fall back to English, so a missing `uz`/`ru` key shows the
raw key string. In KMP, `values/strings.xml` (English) is the default; a key missing there is a build error, which is
what we want. English is complete; fill the 92 `ru` and 10 `uz` gaps and the 41 keys that exist nowhere before converting.

## Uzbek Cyrillic

`uz` has a second script. When the locale is `uz-Cyrl`, every resolved `uz` (Latin) string is transliterated at runtime
by `transliterateUzbekLatinToCyrillicUi` (`lib/core/localization/uzbek_latin_to_cyrillic.dart`, 184 lines, with an
exception list). Decision for KMP: either port the transliterator (`utils/UzbekCyrillic.kt`, pure Kotlin, easy) and keep
one `values-uz/` folder, or ship a real `values-b+uz+Cyrl/strings.xml`. Recommendation: port the transliterator first
(zero translation work), add a real Cyrillic file later if product wants hand-tuned copy.

## Placeholder conventions (three styles coexist)

| Style | Count (en) | Example key → fragment | How Flutter fills it |
|---|---|---|---|
| `{{name}}` | 12 | `clinicFinanceShowingRows` → `{{start}}–{{end}}`; `patientsPagination` → `{{start}} {{end}} {{total}} {{noun}}` | `replaceAll('{{start}}', v)` |
| `{name}` | 27 | `otpSentToEmail` → `{email}`; `calendarTimezoneMismatchHint` → `{scheduleTimeZone}`; `patientAgeYears` → `{age}` | `replaceAll('{age}', v)` |
| `%s` | 2 | `minutesAgo`, `hoursAgo` | `replaceAll('%s', n)` |

Plurals: there are **hand-written Russian plural rules in Dart**, not in the string files
(`consultationUploadSuccess`, `patientsCountBadge`, `visitsCountBadge`, `_ruNominativePatientNoun`,
`_ruPatientNounAfterIz`, `_ruNominativeVisitNoun` at `app_localizations.dart:7020-7110`). English uses
`xxxCountLabelOne` / `xxxCountLabel` pairs. In KMP use `<plurals>` (`pluralStringResource`) for these six places; Compose
Resources handles the Russian `one/few/many` forms.

Many other strings are also built in Dart getters/functions rather than looked up (status labels
`clinicPlanStatusLabel`, `clinicPaymentStatusLabel`, document section names, appointment statuses: `:6300-7340`).
Each becomes a `when` over an enum in KMP that returns a `StringResource`.

## Converting to Compose Multiplatform resources

Target layout:

```
composeResources/values/strings.xml      <- en.json (default; complete bar one key)
composeResources/values-uz/strings.xml   <- uz.json
composeResources/values-ru/strings.xml   <- ru.json
```

Rules:

1. Keys stay camelCase (all are valid identifiers already).
2. Escaping: `&` → `&amp;`, `<` → `&lt;`, `'` → `\'`, `"` → `\"`; a value starting with `@` or `?` needs a leading `\`.
   Newlines are `\n`. CMP does not unescape `%%`, so write a literal percent as `%` unless the string has placeholders.
3. Placeholders: convert all three styles to positional `%1$s`, `%2$s`, … in first-appearance order per key (verified
   that all locales use the same placeholder names in the same order). Call as
   `stringResource(Res.string.otpSentToEmail, email)`.
4. Convert the six plural sites above to `<plurals>`.
5. Backend-returned strings (error messages) are not keys; map them through `ErrorMessages.kt` (see
   `01-architecture-and-core.md` §9).

Converter sketch (Python, run over each JSON):

```python
import json, re, html
ph = re.compile(r"\{\{(\w+)\}\}|\{(\w+)\}|%s")
def conv(v):
    seen = []
    def rep(m):
        t = m.group(0)
        if t not in seen: seen.append(t)
        return f"%{seen.index(t)+1}$s"
    v = ph.sub(rep, v)
    v = html.escape(v, quote=False).replace("'", "\\'").replace('"', '\\"')
    return ("\\" + v) if v[:1] in "@?" else v
```

`StringsTest` (ported from the patient app) must assert: every key in `values-uz`/`values-ru` exists in `values`, and
placeholder counts match across locales.
