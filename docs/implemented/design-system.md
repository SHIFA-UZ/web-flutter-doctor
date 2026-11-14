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
| `.sh-switch` | `ShifaSwitch` · `.sh-option` → `ShifaOptionList` / `OptionRow` (SelectionControls; `sub` line) · board picker → `ShifaPickerSheet` + `ShifaPickField` (ShifaPicker.kt, AUTH-06) |
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

## Design review 12 Nov 2026 (branch `fix/design-review-polish`)

The designer's review ("Doctor app review.pdf", web app, 28 findings; the file received has D1–D18 only). The live
canvas was compared with `docs/design/` on 2026-11-14 (version `1794642449-c7c4`): the boards, tokens and stylesheet are
unchanged except `DaApp`, where the "Turn on notifications?" dialog was dragged into the Home branch (a hand edit, not
a design change), so the vendored copy stays as it is. Fixed at the shared component, not per screen:

| Finding | What changed | Where |
|---|---|---|
| D1 splash | The blobs scale with the frame (from the design's 390 × 844) and fill a tablet or a browser window; `index.html`'s loading frame does the same. | `SplashScreen.kt`, `index.html` |
| D2 Home gaps | One gap (`Stack.Md`, 16) between every Home panel, both directions. | `HomeScreen.kt` |
| D3 panel links | A panel's link sits at the right of its heading, one style (`ShifaLink`). Clinic performance: the range is a compact secondary, Export a tertiary. | `HomeAttention.kt`, `AnalyticsSection.kt` |
| D4, D5 shadows | Primary, secondary, AI, icon and action buttons are flat; only the FAB, trays and menus are raised. Quick actions use the neutral action pill with 16 icons. | `ShifaButtons.kt`, `HomeHero.kt` |
| D6 date range | `ShifaSheetHost` + `ShifaDialogSheet` + the shared `MonthCalendar` (new `ranged` days, `card = false`); Save primary, Cancel secondary. The Material single-date picker stays for birth dates (its year list) with the app's buttons. | `AnalyticsSection.kt`, `ShifaCalendar.kt`, `ShifaDatePicker.kt` |
| D7, D8, D18 headers | `ShifaPageToolbar` + `ShifaToolbarAction` on Home, Calendar, Patients, Chat, Notifications, Tasks, Reports, Clinic. Rule: quick actions are white 44 discs in the order refresh, search, filter/settings, bell; the page's commit action is a primary button and comes last. "Mark all as read" is a toolbar action. | `ShifaPageHeader.kt` and each screen |
| D9, D10 calendar | Wide panes: Today, previous / next, the title and a Day / Week / Month switch (`CalendarNav`); `TimeGrid` (hour lines, one separator per day, today as a teal disc) for the day and the week; `MonthGrid` with each day's first visits; no side month card. Phones keep the month card and agenda. | `WeekGrid.kt`, `CalendarScreen.kt` |
| D11, D12 trays on desktop | `ShifaSheetHost`: a bottom tray below 560, a centred dialog (max 480, four corners, actions side by side) from there. Every sheet in the app goes through it. | `ShifaTray.kt`, `Feedback.kt` |
| D13 dropdown | The menu floats over the form (Material `DropdownMenu` in the kit's look) and flips above when there is no room. | `ShifaDropdown.kt` |
| D14, D15 chat panes | The conversation is a raised white surface on the canvas; the empty pane is an `EmptyState` with "Open patients". | `MainScreen.kt` |
| D16, D17 thread | Messages and composer in a centred 720 column; the composer is the chat tray's attach and send buttons around a bordered field. | `ThreadScreen.kt` |

Not in the design: the month grid's cells and the calendar bar (built from kit parts after the review's "familiar
calendar" note). Open: D11 asks for an anchored menu for Filters on desktop; it opens as the centred dialog. D19–D28
(Notifications, Remote-care tasks, Reports, Profile and settings) wait for the rest of the review; the review's open
questions (D21, D25, D26, the button-shadow token) are the designer's to answer. An expanded dropdown no longer shows in
a widget screenshot (the menu is its own window).

