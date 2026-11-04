# 04 — Design System and UI Kit

> **2026-11-04:** design no longer comes from Figma. The source is the Claude Design canvas vendored in `docs/design/` (tokens, stylesheet, 51 boards); see `docs/design/README.md` and `17-master-plan.md` §8. Figma node references below are historical.

Status: **skeleton, waiting for the Figma file.** The new doctor app takes every colour, text style, spacing, radius,
shadow, icon and component from the new Figma file, not from Flutter. This doc has three jobs:

1. §1–§4 fix the **token structure and Kotlin naming** so that the Figma tokens drop straight into `ui/theme/`
   (same scheme as the patient app's `CLAUDE.md`). The value columns are filled in ticket **CORE-02** once the Figma
   file is shared.
2. §5 is the **component inventory** the doctor app needs, derived from what the Flutter screens actually use. It is
   the checklist against which the Figma Components page is compared in **CORE-03**: everything in §5 either exists
   in Figma, or is built "in the existing style" and noted as "not in Figma" in `docs/implemented/`.
3. Appendix A records the **legacy Flutter tokens** for comparison only (so a reviewer can tell a deliberate redesign
   from an accidental drift). Never port them.

---

## 1. Sources of truth

| Source | What it gives |
|---|---|
| Figma `<FIGMA_FILE>` → Tokens page `<node>` | Variables: `semantic/color/*`, `semantic/space/*`, radius, shadow, size; text styles |
| Figma `<FIGMA_FILE>` → Components page `<node>` | Every reusable component with variants and states |
| Figma screens (per feature doc) | Screen layouts; each feature doc lists its node IDs |
| `docs/implemented/` | Which node IDs are built, and what was built "not in Figma" |

Rules (from the patient app, unchanged):

- Figma is light-mode only; there is no dark scheme.
- No raw hex/dp/sp in screens or widgets. Tokens are named 1:1 after Figma variables.
- Load the `figma-design-to-code` skill before `get_design_context`.
- Icons and images are Figma exports in `composeResources/drawable` (`ic_<name>_<size>`, `img_*`).

---

## 2. Colour tokens → `ui/theme/Color.kt`

Kotlin shape (fill from Figma variables; keep Figma's names, PascalCase the path):

```kotlin
object ShifaColors {
    object Text        { val Primary; val Secondary; val Tertiary; val Inverse; val Brand; val Error; val Success; val Warning; val Link /* … */ }
    object Icon        { val Primary; val Secondary; val Brand; val Inverse; val Disabled /* … */ }
    object Bg          { val Page; val Surface; val SurfaceSecondary; val Brand; val BrandSoft; val Error; val ErrorSoft; val Success; val Warning; val Info; val Ai /* … */ }
    object Border      { val Default; val Strong; val Brand; val Error; val Focus /* … */ }
    object Interactive { val Primary; val PrimaryHover; val PrimaryPressed; val PrimaryDisabled; val Secondary; val Destructive; val DestructivePressed /* … */ }
    object Alpha       { val Scrim; val Overlay; val Shadow /* … */ }
    object Raw         { /* values Figma sets without a variable: <Component><Part>, e.g. CalendarSlotBookedFill */ }
}
```

Fill-in table (one row per Figma variable):

| Figma variable | Kotlin | Hex | Used for |
|---|---|---|---|
| `semantic/color/text/primary` | `ShifaColors.Text.Primary` | `<TBD>` | |
| … | | | |

Doctor-app specific colour needs the Figma tokens page **must** cover (ask for them if missing):

- **Appointment status** colours: requested / confirmed / in-progress / completed / cancelled / no-show, each as fill +
  text (calendar cells, cards, chips).
- **Payment status**: paid / pending / overdue / partially paid (finance, treatment plans, appointment cards).
- **Task category / status** colours (remote-care tasks).
- **Calendar**: slot free / booked / blocked / outside-hours / today / selected / timezone-mismatch; plus a palette of
  **staff colours** for multi-doctor calendars (Flutter uses a fixed list in `calendar_staff_colors.dart`).
- **Chart series** palette (appointments trend, visit types, finance): at least 6 ordered series colours + grid/axis.
- **AI / Shifa Intelligence** accent (orb, AI cards, copilot bubbles), distinct from the brand teal.
- **Chat**: own vs other bubble fills, unread badge.
- **Dental chart**: tooth condition colours (healthy, caries, filling, missing, crown, planned…) if the dental module
  is kept.
- **Semantic**: success / warning / error / info backgrounds and texts (banners, badges, patient warnings).

`Theme.kt` maps a handful of these onto `lightColorScheme(primary, onPrimary, error, surface, …)` for Material
components; everything else is read from `ShifaColors` directly. Never use dynamic colour on Android 12+.

---

## 3. Typography → `ui/theme/Typography.kt`

- Font family: `<from Figma>` (Flutter asked for "SF Pro Display" but never bundled it, so the shipped app used the
  platform font). Put the Figma font files in `composeResources/font` and expose `ShifaFontFamily`.
- Map Figma text styles to Material roles **and** keep the Figma names in `ShifaTextStyles` so screen specs can be
  ported literally.

| Figma text style | `MaterialTheme.typography.*` | size / line-height / weight / tracking | Typical use |
|---|---|---|---|
| `<Display>` | `displaySmall` | `<TBD>` | KPI numbers on Home |
| `<H1>` | `headlineMedium` | | Screen titles |
| `<H2>` | `titleLarge` | | Section titles, card titles |
| `<H3>` | `titleMedium` | | List item titles |
| `<Body L>` | `bodyLarge` | | |
| `<Body M>` | `bodyMedium` | | Default text |
| `<Body S>` | `bodySmall` | | Secondary text |
| `<Label L/M/S>` | `labelLarge/Medium/Small` | | Buttons, chips, badges, tabs |
| `<Caption>` | — (`ShifaTextStyles.Caption`) | | Timestamps, hints |
| `<Mono>` | — (`ShifaTextStyles.Mono`) | | Tokens/keys in admin & debug (optional) |

Use `Modifier.lineBox(style)` where text height must match Figma's line boxes. Set `LineHeightStyle(Center, None)` and
`includeFontPadding = false` so Compose matches Figma line boxes on Android.

Doctor-app specific typographic needs: dense **table/ledger** rows (finance, patients directory), **calendar grid**
labels (hour gutter, 3–7 column day headers), **chat bubbles**, **AI streaming text** (markdown-ish: bold, lists,
headings; Flutter renders this in `AiResponseText`), **PDF** is out of scope for Compose typography (see PDF ticket).

---

## 4. Spacing, sizes, radius, shadow → `Dimens.kt`

```kotlin
object ShifaSpacing { object Inset { /* page/card paddings */ }  object Gap { /* in-row gaps */ }  object Stack { /* vertical rhythm */ } }
object ShifaSize    { /* icon sizes, avatar sizes, touch targets, nav bar height, top bar height */ }
object ShifaRadius  { /* Xs, Sm, Md, Lg, Xl, Full */ }
object ShifaShadow  { /* Card, Elevated, Sheet, as DropShadow specs */ }
object ShifaDimens  { /* raw Figma sizes per component: <Component><Part> */ }
```

| Figma variable | Kotlin | Value |
|---|---|---|
| `semantic/space/inset/*` | `ShifaSpacing.Inset.*` | `<TBD>` |
| `semantic/space/gap/*` | `ShifaSpacing.Gap.*` | |
| `semantic/space/stack/*` | `ShifaSpacing.Stack.*` | |
| radius `*` | `ShifaRadius.*` | |
| shadow `*` | `ShifaShadow.*` | |

Layout facts to confirm in Figma: phone frame width (360/390), tablet behaviour (the Flutter web app is a
master–detail desktop layout; the KMP app is phone-first, tablet = same layout with wider gutters unless Figma says
otherwise), bottom nav height and safe-area handling, top bar height, modal sheet corner radius.

---

## 5. Component inventory (what the doctor app needs)

Each row: component → Kotlin name in `ui/widgets/` → variants/states required → where the Flutter app uses it today
(so the implementer can check behaviour). "Flutter source" is for behaviour only, never for looks.

### 5.1 Foundations

| Component | Kotlin | Variants / states | Flutter source (behaviour) |
|---|---|---|---|
| Button | `ShifaButton` | primary / secondary (outline) / tertiary (text) / destructive; sizes M, S; icon-left, icon-only; loading; disabled; fill vs hug width | `core/widgets/shifa_button.dart` (`ShifaPrimaryButton` 95 uses, `ShifaSecondaryButton` 45, `ShifaActionButton` 8, `ShifaIconButton`) |
| Icon button | `ShifaIconButton` | primary / secondary / plain; badge dot; disabled | same file `:399`; `app_page_back_button.dart` |
| Text field | `ShifaInputField` | default / focused / error / disabled; leading & trailing icon; multiline; counter; password toggle; **mic trailing action** (speech-to-text) | 103 `TextField` + 76 `TextFormField`; `doctor_speech_text_field.dart` (23 uses) |
| Phone field | `ShifaPhoneField` + `ShifaPhoneList` | country code, E.164 validation, add/remove rows | `multiple_phone_fields.dart`, `phone_e164.dart` |
| OTP code field | `ShifaOtpField` | 6 cells, error, resend countdown | auth OTP screens |
| Searchable dropdown | `ShifaSearchableDropdown` | single select with search, grouped (profession), long list (IANA time zones), clinic list; loading; empty | `searchable_profession_dropdown.dart`, `searchable_timezone_dropdown.dart`, `searchable_clinic_dropdown.dart` |
| Select / menu | `ShifaSelect`, `ShifaMenu` | bottom-sheet picker on phone | `PopupMenuButton` (2), dropdown-like dialogs |
| Checkbox / switch / radio | `ShifaCheckbox`, `ShifaSwitch`, `ShifaRadio` | on/off/disabled, with label | 5 `Checkbox`; settings toggles are custom |
| Chip | `ShifaChip` | filter (selectable), choice, input (removable), status (read-only, colour-coded), AI suggestion chip | 25 `Chip`, 8 `FilterChip`, 6 `ChoiceChip`, 3 `ActionChip`, `quick_action_chip.dart`, `clinical_chip_panel.dart` |
| Badge | `ShifaBadge` | count (nav, chat, notifications), dot, status pill | `Badge` (5), unread counters in shell |
| Avatar | `ShifaAvatar` | sizes XS–XL, photo / initials / brand fallback, status ring | `person_avatar.dart` |
| Banner | `ShifaBanner` | info / warning / error / success, inline, with action; patient-warning variant (chronic disease) | `patient_warning_utils.dart`; today errors are 410 `SnackBar`s → **replace with banners** |
| Skeleton | `ShifaSkeleton`, `ShifaCardSkeleton`, `ShifaListSkeleton`, `ShifaCalendarSkeleton` | shimmer | today 155 `CircularProgressIndicator` → **replace with skeletons** |
| Progress | `ShifaProgressBar`, `ShifaSpinner` (buttons only) | determinate (uploads, task progress), indeterminate | 19 `LinearProgressIndicator` |
| Empty state | `ShifaEmptyState` | illustration + title + body + action | many inline implementations |
| Divider, section header | `ShifaDivider`, `ShifaSectionHeader` | with trailing action ("See all") | home sections |
| Tooltip | — (Material) | | 30 `Tooltip` (mostly web hover; drop on phone) |

### 5.2 Navigation & containers

| Component | Kotlin | Variants / states | Flutter source |
|---|---|---|---|
| Bottom nav bar | `ShifaNavBar` | 5–6 tabs with badges; clinic-staff reduced set; centre AI action if Figma has it | `main_shell.dart` (tabs: Chat, Home, Calendar, Patients, Tasks, Profile) |
| Top app bar | `ShifaTopBar` | title / back / actions / search mode / large greeting header (Home) | `home_header.dart`, `home_greeting_header.dart`, `app_page_back_button.dart` |
| Tabs / segmented control | `ShifaTabs`, `ShifaSegmented` | scrollable tabs (patient detail, clinic workspace), 2–3 segment pills | 3 `TabBar`, custom pills |
| Card | `ShifaCard` | default / clickable / selected / elevated / AI-gradient | `dashboard_card.dart`, `analytics_card.dart`, `AppDesignSystem.cardDecoration/aiCardDecoration` |
| Bottom sheet (tray) | `ShifaTray` | handle, title, scrollable body, sticky footer buttons; full-height variant for wizards | `scrollable_sheet_dialog.dart`, `safari_safe_dialog.dart`, `treatment_plan_wizard_sheet.dart`, `create_patient_sheet.dart` |
| Dialog / pop-up | `ShifaPopUp` | confirm / destructive confirm / info; with text input | 58 `AlertDialog`, 9 `showDialog` |
| List item | `ShifaListItem` | leading avatar/icon, title, subtitle, trailing value/chevron/switch; swipe actions? | 95 `ListTile` |
| Table / ledger row | `ShifaTableRow` (phone: stacked key–value card; no horizontal tables) | header, sortable?, pagination footer | `clinic_table_shell.dart` (9 uses), 5 `DataTable`, finance views |
| Pagination | `ShifaPagination` | prev/next + "x–y of z" | patients directory, clinic tables |
| Stepper / wizard header | `ShifaStepper` | n steps, current, done | treatment-plan wizard, registration |
| Search bar / overlay | `ShifaSearchBar` | with recent results overlay | `home_search_overlay.dart` |
| FAB / quick actions | `ShifaFab`, `ShifaQuickActions` | expandable speed-dial | `home_floating_quick_actions.dart` |
| Language toggle | `ShifaLanguageToggle` | en / uz / ru pill | `language_mini_toggle.dart` |

### 5.3 Domain components (feature docs own the behaviour)

| Component | Kotlin | Notes | Flutter source |
|---|---|---|---|
| Appointment card | `AppointmentCard` | status + payment chips, patient avatar, time in practice TZ, actions (start, open, call) | `home_appointment_card.dart`, `appointment_timeline_tile.dart` |
| Current-appointment hero | `CurrentAppointmentHero` | live timer, start/join | `home_current_appointment_hero.dart` |
| Next patient card | `NextPatientCard` | | `next_patient_card.dart` |
| KPI / metric card | `MetricCard` | value, delta, sparkline? | `overview_metric_card.dart`, `analytics_kpi_cards.dart` |
| Charts | `TrendLineChart`, `DonutChart`, `BarChart` | series palette from §2; axis labels; empty | `appointments_trend_chart.dart`, `visit_type_donut_chart.dart`, finance charts (fl_chart) |
| Calendar month | `ShifaCalendarMonth` | day cells with occupancy dots/fill, selected, today, out-of-month | `table_calendar` usage in `calendar_screen.dart` |
| Calendar day/week grid | `ShifaScheduleGrid` | hour gutter, 1–7 columns, slot cells (free/booked/blocked), overlapping events layout, now-line, staff colours | `calendar_week_grid_view.dart`, `calendar_overlap_layout.dart` |
| Time-slot picker | `SlotPicker` | consecutive slots, duration chips | `consecutive_slot_range.dart` |
| Date / time pickers | `ShifaDatePicker`, `ShifaTimePicker` | Figma-styled; practice-TZ aware | Material pickers today |
| Patient list item / header | `PatientListItem`, `PatientHeader` | warnings, chronic badge, phone, last visit | `patients_directory_panel.dart`, `patient_detail_overview.dart` |
| Document card / viewer | `DocumentCard`, `DocumentViewer` | category icon, PDF/image, download/share | `document_viewer_screen.dart`, `document_category.dart` |
| Form field renderer | `MedicalFormField` | per field type of the 025-2 / templates | `patient_form_screen.dart`, `patient_form_models.dart` |
| Task card | `TaskCard`, `TaskProgressBar` | category, status, progress, check-ins | `tasks_screen.dart`, `task_models.dart` |
| Chat bubbles | `TextBubble`, `ImageBubble`, `VoiceBubble`, `DocumentBubble`, `TypingIndicator` | own/other, time, status | `chat/presentation/widgets/*` |
| Composer | `ChatComposer` | text, attach, hold-to-record, send | `chat_screen.dart`, `inline_voice_recorder_bar.dart` |
| Voice recorder | `VoiceRecorderBar`, `VoiceRecorderSession` | recording, paused, level meter, timer, cancel | `inline_voice_recorder_bar.dart`, `doctor_voice_recorder_session.dart` |
| AI orb & voice session | `ShifaAiOrb`, `VoiceSessionSheet` | idle / listening / thinking / speaking animations | `shifa_ai_orb.dart`, `home_voice_session.dart` |
| AI message / action card | `AiResponseText`, `AiActionCard` | streaming text, markdown subset, action cards (open appointment, create task, …) | `ai_response_text.dart`, `intelligence_action_cards.dart` |
| Patient briefing panel | `PatientBriefingPanel` | loading / ready / failed+retry | `patient_briefing_panel.dart` |
| Clinical chips panel | `ClinicalChipPanel` | diagnosis chips, groups | `clinical_chip_panel.dart` |
| SOAP section | `SoapSection` | S/O/A/P editors with mic, AI draft state | `consultation_soap_section.dart` |
| Dental FDI chart | `DentalChart` | 32 teeth, surfaces, condition colours, read-only vs edit | `dental_fdi_chart.dart` |
| Finance widgets | `MoneyText`, `LedgerRow`, `InstallmentRow` | currency formatting (UZS/USD), overdue | `finance_shared.dart` |
| Notification row | `NotificationRow` | type icon/colour, unread, actions (approve/reject access) | `notification_ui_helpers.dart` |
| Video call controls | `CallControls`, `ParticipantTile` | mute, camera, flip, end; notes side sheet | `video_call_screen.dart` |
| Signature pad | `SignaturePad` | draw / clear / fonts | `partner_signature_image.dart` (legal), request-signature flows |
| Map / location picker | `LocationPicker` | map pin + address search | `location_picker_widget.dart` (platform-native maps) |
| App lock keypad | `PinKeypad` | 4–6 digits, error shake, biometric button | `app_lock_layer.dart` |
| Version footer / debug entry | `AppVersionFooter` | | profile bottom |

### 5.4 Icons

The Flutter app uses **249 distinct Material icons** (`grep -rhoE 'Icons\.[a-z_0-9]+' lib | sort -u`). The Figma
icon set must cover them or the Components page must define the replacement. CORE-03 produces the mapping table
`Material icon → Figma export name` and exports `ic_<name>_24.xml` (plus `_16`/`_20` where Figma sizes differ).

### 5.5 Assets & fonts

| Asset | Flutter | KMP |
|---|---|---|
| Logo | `assets/branding/shifa_logo.png`, `shifa_mark.svg`, `assets/images/shifa_logo.svg` | re-export from Figma as `img_logo*`; app icon without alpha |
| UI font | none bundled (theme names "SF Pro Display", falls back to system) | Figma font files in `composeResources/font` |
| PDF font | `DejaVuSans.ttf` (Cyrillic) | only if PDFs are rendered on device (see PDF decision) |
| Signature fonts | `DancingScript-Regular.ttf`, `Sacramento-Regular.ttf` | only for the legal contract PDF (see LEGAL tickets) |
| Illustrations (empty states, splash) | none | from Figma |

---

## 6. Material theme wiring (`Theme.kt`)

```kotlin
@Composable fun ShifaTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = lightColorScheme(
        primary = ShifaColors.Interactive.Primary, onPrimary = ShifaColors.Text.Inverse,
        error = ShifaColors.Bg.Error, surface = ShifaColors.Bg.Surface, onSurface = ShifaColors.Text.Primary,
        background = ShifaColors.Bg.Page, outline = ShifaColors.Border.Default, /* … */
    ),
    typography = ShifaTypography, shapes = Shapes(/* from ShifaRadius */), content = content,
)
```

Only Material components we actually host (`ModalBottomSheet`, `Dialog`, `Scaffold`, `TextField` internals,
date/time pickers if not custom) read the scheme; everything visible is drawn by `ui/widgets`.

---

## 7. Porting notes

- **SnackBars → banners.** 410 `SnackBar` call sites in Flutter. Rule from the patient app: errors inline in
  `ShifaBanner`, never a SnackBar. Success confirmations that today are SnackBars become either a banner that
  auto-dismisses or nothing (the state change is visible).
- **Spinners → skeletons.** 155 `CircularProgressIndicator`s. Every first load is a skeleton; the spinner survives only
  inside buttons.
- **Desktop layouts → phone layouts.** The Flutter web app uses master–detail split panes (patients, chat, clinic
  workspace), `DataTable`s and hover tooltips. On phone these become list → detail navigation, stacked key–value rows
  and no hover. Figma decides; the feature docs list where the Flutter layout was desktop-only.
- **Raw colours.** 55 distinct raw `Color(0x…)` literals exist in Flutter features (status colours, chart colours,
  dental colours). None are ported; each needs a token in §2.
- **Accessibility.** Every icon-only control gets a `contentDescription` from strings; touch targets ≥ 48 dp (Flutter
  used `shrinkWrap` tap targets on buttons, do not copy).

---

## Appendix A — Legacy Flutter tokens (comparison only, never port)

`lib/core/theme/app_colors.dart`, `lib/core/theme/app_design_system.dart`, `lib/app/theme.dart`.

| Token | Hex | Role in Flutter |
|---|---|---|
| `primaryTeal` | `#00BBB0` | brand |
| `primaryLight` | `#59C2BC` | pressed/hover |
| `secondaryLight` | `#CCF1EF` | secondary pressed |
| `primaryDark` | `#129B8A` | |
| `primaryAi` | `#26BAA4` | AI accents |
| `destructiveRed` / `destructiveLight` / `destructiveSecondaryLight` | `#DC2F2F` / `#EB5454` / `#F5C1C1` | |
| `disabledGrey` | `#C6C6C6` | |
| `cardboard` (scaffold) | `#F5F5F5` | page board behind white cards |
| `textPrimary` / `textSecondary` / `textTertiary` | `#1A1A1A` / `#6B7280` / `#9CA3AF` | |
| `border` | `#E5E7EB` | 1 px card border |
| `background` / `backgroundSecondary` / `backgroundTertiary` | `#FFFFFF` / `#F9FAFB` / `#F3F4F6` | |
| `success` / `warning` / `info` | `#059669` / `#D97706` / `#0284C7` | |

Typography (`AppDesignSystem`): display 28/700/1.2 (−0.5 tracking), h1 22/600/1.25, h2 16/600/1.3, body1 15/400/1.45,
body2 13/400/1.4 (secondary colour), caption 11/500/1.35 (tertiary). Font family requested "SF Pro Display" (not
bundled).

Shape & spacing: `cardRadius 20`, `cardRadiusSm 14`, `cardPadding 20`, `sectionGap 20`, `itemGap 12`; inputs radius
12, buttons radius 8; `cardShadow` = black 4 % blur 12 offset (0,2) + black 2 % blur 4 offset (0,1); `elevatedShadow`
= teal 8 % blur 24 offset (0,8); AI card = teal 6 % → white → `primaryAi` 4 % gradient with teal 18 % border.

Material theme (`theme.dart`): M3, `scaffoldBackgroundColor = cardboard`, AppBar flat on cardboard, Card white with
`border` 1 px radius 20, inputs filled white radius 12 (focused teal 2 px), ElevatedButton teal radius 8 padding 10.
