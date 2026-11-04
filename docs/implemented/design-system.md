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

## CORE-03 · UI kit (`ui/widgets/`, `ui/navigation/`)

Built 2026-11-04 on `doc-app2.0` from `docs/design/shifa-ds.css` and the boards below (design version
`1793750633-3da4`). The patient app's widgets (`main@dc2eae4`) were copied where a `.sh-*` block matches and restyled
to the doctor stylesheet; the rest is new. Phone layouts only: the web/tablet branches of the boards (sidebar, split
panes, `.dr-week`, `.dr-table` columns) wait for the tickets that need them.

**`.sh-*` blocks → composables**

| Block | Composable (file) |
|---|---|
| `.sh-btn` (primary, secondary, tertiary, AI, compact, destructive, busy) | `ShifaPrimaryButton`, `ShifaSecondaryButton`, `ShifaTertiaryButton`, `ShifaAiButton` (ShifaButtons) |
| `.sh-action` | `ShifaActionButton` · `.sh-icon-btn` → `ShifaIconButton` · `.sh-link` → `ShifaLink` · `.sh-fab` → `ShifaFab` · `.sh-spinner` → `ShifaSpinner` |
| `.sh-field`, `.sh-input` | `ShifaInputField` · `.sh-search` → `ShifaSearchField` · `.sh-dropdown`, `.sh-select` → `ShifaDropdown` |
| `.sh-chip`, `.sh-chip-grid`, `.sh-carousel` | `ShifaChip`, `ShifaChipGrid`, `ShifaCarousel` (ShifaChips) |
| `.sh-switch` | `ShifaSwitch` · `.sh-option` → `ShifaOptionList` (SelectionControls) |
| `.sh-tag` | `ShifaTag` · `.sh-status` → `ShifaStatusDisc` · `.sh-bubble-count`, `.sh-badged` → `ShifaNotificationBubble` |
| `.sh-card`, `.sh-avatar` | `ShifaCard`, `ShifaAvatar` (ShifaCards; also `ShifaSettingsItem`, `ShifaDocumentCard`, `ShifaTaskCard`) |
| `.sh-banner` | `ShifaBanner` · `.sh-section` → `ShifaSection` · `.sh-header` → `ShifaMainHeader`, `ShifaSubHeader` |
| `.sh-tray`, `.sh-sheet`, `.sh-scrim` | `ShifaTray`, `ShifaChatTray`, `ShifaPopUp`, `ShifaDialogSheet` |
| `.sh-chat`, `.sh-msg`, `.sh-chatbar`, `.sh-wave` | `ShifaChatBubble`, `ShifaChatImageBubble`, `ShifaChatVoiceBubble`, `ShifaChatThinkingBubble`, `ShifaChatBar` |
| `.sh-notif` | `ShifaNotification`, `ShifaMessageNotification` · `.sh-rating` → `ShifaRating` |
| `.sh-nav` | `ShifaNavBar` (ui/navigation) |
| `.sh-press` | `Modifier.pressable` (Pressable) |
| `.sh-icon`, `.sh-col`, `.sh-stage`, `.sh-on-surface` | layout helpers, no composable: icons are `Icon(painterResource(…))`, `LocalCardOnSurface` |

**Doctor-only components (AuCodebase)**: `ContextSwitcher`, `SegmentedTabs` (+ `UnderlineTabs`), `Stepper`,
`InstallmentRow` + `InstallmentSum` (InstallmentSchedule), `DataTableCard` + `PersonCell` + `DataTableSkeleton` +
`DataTableEmpty` + `ShifaPager` (DataTable, phone card form), `ShifaSlider`, `TreatmentPlanCard`, `OtpInput` +
`PinDots` + `PinPad`, `DentalChart` (FDI order, scrolls sideways at 36×40 teeth), `MapPicker` (placeholder grid until
CORE-07 picks the map), `TierCard`, `ShifaFab`, `DayStrip` + `AgendaSlot` + `MonthCalendar` (CalendarWeek on phones;
the 5-day `.dr-week` grid is tablet-only and left to the calendar ticket), `StatusBadge`, `AlertChip`, `Pressable`,
`ShifaOptionList`, `ActionBar` + `FormSection`, `ShifaToast`, `KpiCard` + `ShifaProgressBar`. Also from the boards:
`NextPatientHero` (Hero), `AppointmentTimeline` (Timeline), `AiInsightCard`/`InsightOutcome`/`AiInsightSkeleton`/
`VoiceSessionCard` (Insight), `AiCopilot`/`CopilotComposer`/`AiOrbButton` (Copilot), `EmptyState`, `OfflineStrip`,
`LanguageToggle`, `CallControlBar`, `ShifaCheckbox`/`ShifaRadio`/`ShifaChoiceCard`.

**States boards → previews → screenshots** (`androidMain/.../ui/widgets/*Previews.kt`,
`androidUnitTest/.../ui/widgets/*ScreenshotTest.kt`, `composeApp/screenshots/<Group>_<state>.png`, 51 images):

| Board | Phone states covered |
|---|---|
| HeroStates | idle, loading, ready, started, error, empty (`States_hero_*`) |
| InsightStates | suggestion, risk, briefing, loading, voice (listening/thinking/speaking), outcome (`States_insight_*`) |
| CopilotStates | docked empty, streaming, answered, minimised (`States_copilot_*`); the phone tray is the same column in `ShifaTray` |
| TimelineStates | now 10:27, after Start, empty day (`States_timeline_*`) |
| TableStates | rows + pager, loading, filtered empty (`Workspace_table*`) |
| DialogStates | destructive, form, AI briefing (`Feedback_dialog_*`) |

Kit galleries: `Controls_*` (buttons, actions, inputs, choices, OTP/PIN, slider/stepper, icons), `Display_*`
(badges, cards, KPIs, sections/headers, tabs, notifications, chat, nav bar), `Feedback_*` (banners, toasts, empty and
skeletons, trays), `Workspace_*` (agenda, month, dental chart, forms, plans, chrome).

**Icons**: `node scripts/design_icons.js` writes `composeResources/drawable/ic_<name>_<size>.xml` for every
`Res.drawable.ic_*` the code references, from the `ic` map in `boards/DaApp.dc.html` (`_filled`, `_bold` variants);
it deletes generated files nothing uses. The library has no `folder` or `check_square`: `file` and `tasks` stand in.

**Deviations from the patient app / board notes**
- The input's clear ✕ shows only when there is text (the boards never draw it on an empty field).
- Selected slot chips have no check icon (the stylesheet has none; it truncated "10:00" in the 4-column grid).
- `ShifaPager` shows three page numbers: five 44 dp targets plus the summary overflow a 360 dp phone.
- `MonthCalendar`'s legend takes `DayLoad`s, so legend dots always match the cell tints.
- The avatar fallback is the patient app's brand image (`img_avatar_fallback.webp`) until a doctor asset exists.
- Strings in the kit come from `strings_kit.xml` (kit-only keys) and the doctor JSON keys; CORE-04 regenerates
  `strings.xml` from `docs/kmp-spec/strings/`.
