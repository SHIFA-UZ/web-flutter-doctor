# Design system

Source: `docs/design/` (Claude Design canvas "Shifa Doctor App", version `1793750633-3da4`, copied 2026-11-04), on
branch `doc-app2.0`. No Figma.

## CORE-02 · Tokens (`ui/theme/`)

Built 2026-11-04. The patient app's `ui/theme/` (`main@dc2eae4`, the code `tokens.json` was generated from) copied and
renamed, then every `--*` custom property of `shifa-ds.css` without a counterpart added under the same scheme:

| Stylesheet | Kotlin |
|---|---|
| primitives (`--teal-50`, `--teal-70`, `--grey-550`, `--red-70`, `--amber-70`, `--blue-70`, …) | private vals in `Color.kt` |
| `--text-*`, `--icon-*`, `--bg-*`, `--border-*`, `--interactive-*` | `ShifaColors.Text/Icon/Bg/Border/Interactive.X`. **Doctor values differ from the patient app:** words use the 70-steps (`Text.Brand`/`Link` = teal-70, `Error` = red-70, `Warning` = amber-70, `Info` = blue-70) and `Text.Tertiary` = grey-550 (AuTokens contrast fix); icons and fills keep the 50-steps. New: `Text.BrandStrong`, `Text.WarningStrong`, `Bg.Media`, `Bg.Hover`, `Bg.Selected`. |
| `--alpha-*` | `ShifaColors.Alpha.*` (+ White15/20/25/35/40/55/65, Black25) |
| `--chart-1…4`, `--chart-grid` | `ShifaColors.Chart.Series1…4`, `Grid` |
| `--raw-*` | `ShifaColors.Raw.*` (+ VideoPreview, Map*) |
| `--inset-*`, `--gap-*`, `--stack-*` | `ShifaSpacing.Inset/Gap/Stack.*` |
| `--size-*`, `--size-control-*`, `--size-touch` | `ShifaSize.S1…S320`, `ControlLg/Md/Sm`, `Touch` (44) |
| `--radius-*` | `ShifaRadius.Xs` (new, 4) … `Full` |
| `--shadow-*` | `ShifaShadow.*` (+ `Fab`) |
| `--breakpoint-*` | `ShifaBreakpoint.*` |
| `--layout-*`, `--basis-*` | `ShifaDimens.Layout.*`, `ShifaDimens.Basis.B*` |
| `--blur-*` | `ShifaBlur.*` |
| `--font-size-*`, `--line-height-*`, `--letter-spacing-*` | `ShifaFontSize.F*`, `ShifaLineHeight.L*`, `ShifaLetterSpacing.*` |
| `tokens.json` type groups "Material slots", "Shifa text styles", "Doctor workspace" | `MaterialTheme.typography.*` (same metrics as the patient app) and `ShifaTextStyles.*` (+ `kpiValue` with tabular numerals, `kpiLabel`, `tableHead`, `tableCell`, `navItem`, `bodyText`) |
| `--font-sans` (Manrope) | `composeResources/font/manrope_*.ttf` (the patient app's files) |

`ShifaDimens` keeps only the patient app's component sizes (buttons, header, chip, dropdown, switch, notification,
chat, skeleton, section, nav bar, text area, signature, rating); its screen sizes (splash, login, profile, call) are
not copied — screen tickets add their own. `Res` is generated into `shifa.doctor.generated.resources`.

No in-app token gallery: the four previews in `androidMain/.../ui/theme/ThemePreviews.kt` (colours, alpha + raw,
typography, radius/shadow/spacing) and their screenshots `composeApp/screenshots/Theme_*.png` replace it, so a token
change shows up as a screenshot diff. `PreviewTheme` and `Gallery` (the 402 dp specimen column) live there too.
