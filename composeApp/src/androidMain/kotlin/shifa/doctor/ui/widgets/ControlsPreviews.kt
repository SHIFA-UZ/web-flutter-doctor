@file:Suppress("ktlint:standard:max-line-length") // sample copy in previews

package shifa.doctor.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_bell_16
import shifa.doctor.generated.resources.ic_cal_plus_22
import shifa.doctor.generated.resources.ic_eye_20
import shifa.doctor.generated.resources.ic_fingerprint_22
import shifa.doctor.generated.resources.ic_more_v_16
import shifa.doctor.generated.resources.ic_plus_16
import shifa.doctor.generated.resources.ic_plus_22
import shifa.doctor.generated.resources.ic_sparkles_22
import shifa.doctor.generated.resources.ic_trash_16
import shifa.doctor.generated.resources.ic_user_plus_22
import shifa.doctor.generated.resources.ic_video_22
import shifa.doctor.ui.theme.Gallery
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing

// `.sh-btn`: every variant in its states (default, with icon, destructive, disabled, busy) and both sizes.
@Preview
@Composable
internal fun ButtonsPreview() =
    Gallery {
        val wide = Modifier.fillMaxWidth()
        ShifaPrimaryButton("Start Appointment", {}, wide, icon = Res.drawable.ic_video_22)
        ShifaPrimaryButton("Delete Document", {}, wide, destructive = true)
        ShifaPrimaryButton("Save", {}, wide, enabled = false)
        ShifaPrimaryButton("Saving", {}, wide, loading = true)
        ShifaSecondaryButton("Visit Briefing", {}, wide, icon = Res.drawable.ic_sparkles_22)
        ShifaSecondaryButton("Cancel Appointment", {}, wide, destructive = true)
        ShifaSecondaryButton("Preparing…", {}, wide, loading = true)
        ShifaSecondaryButton("Unavailable", {}, wide, enabled = false)
        ShifaAiButton("Start AI Notes", {}, wide, icon = Res.drawable.ic_sparkles_22)
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            ShifaTertiaryButton("Open Calendar", {})
            ShifaTertiaryButton("Remove", {}, destructive = true)
            ShifaTertiaryButton("Off", {}, enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            ShifaPrimaryButton("Start", {}, size = ShifaButtonSize.Compact)
            ShifaSecondaryButton("Review", {}, size = ShifaButtonSize.Compact)
            ShifaTertiaryButton("Dismiss", {}, size = ShifaButtonSize.Compact)
        }
    }

// `.sh-action` pill variants and `.sh-icon-btn` variants, with toggled and disabled states.
@Preview
@Composable
internal fun ActionAndIconButtonsPreview() =
    Gallery(background = ShifaColors.Bg.Canvas) {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            ShifaActionButton({}, text = "Join")
            ShifaActionButton({}, text = "Add", icon = Res.drawable.ic_plus_16)
            ShifaActionButton({
            }, icon = Res.drawable.ic_bell_16, contentDescription = "Notifications", variant = ShifaActionVariant.Secondary)
            ShifaActionButton({}, text = "Delete", destructive = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            ShifaActionButton({}, text = "Today", variant = ShifaActionVariant.Surface)
            ShifaActionButton({}, text = "Week", variant = ShifaActionVariant.Surface, selected = true)
            ShifaActionButton({}, text = "Suggest", variant = ShifaActionVariant.Tint)
            ShifaActionButton({}, text = "Off", enabled = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            ShifaIconButtonVariant.entries.forEach {
                ShifaIconButton(
                    Res.drawable.ic_more_v_16,
                    it.name,
                    {},
                    variant = it,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            ShifaIconButton(Res.drawable.ic_trash_16, "Delete", {}, destructive = true)
            ShifaIconButton(
                Res.drawable.ic_more_v_16,
                "Selected",
                {},
                variant = ShifaIconButtonVariant.Surface,
                selected = true,
            )
            ShifaIconButton(Res.drawable.ic_more_v_16, "Disabled", {}, enabled = false)
        }
    }

// `.sh-link`, `.sh-fab`, `.sh-spinner`.
@Preview
@Composable
internal fun LinkFabSpinnerPreview() =
    Gallery {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            ShifaLink("See all", {})
            ShifaLink("Plain", {}, variant = ShifaLinkVariant.Plain)
            ShifaLink("Quiet", {}, variant = ShifaLinkVariant.Quiet)
            ShifaLink("Remove", {}, variant = ShifaLinkVariant.Danger)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            ShifaFab(Res.drawable.ic_user_plus_22, {}, text = "Add Patient")
            ShifaFab(Res.drawable.ic_plus_22, {}, contentDescription = "Create")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            ShifaSpinner(ShifaColors.Icon.Brand)
            ShifaSpinner(ShifaColors.Icon.Brand, large = true)
        }
    }

// Text inputs: InputField states, SearchField states, Dropdown closed/open/selected/error.
@Preview
@Composable
internal fun InputsPreview() =
    Gallery {
        val wide = Modifier.fillMaxWidth()
        ShifaInputField("", {}, "First name", wide, supportingText = "As in the passport")
        ShifaInputField("Aziza", {}, "First name", wide)
        ShifaInputField("aziza@", {}, "E-mail", wide, supportingText = "Enter a valid e-mail address", isError = true)
        ShifaInputField("", {}, "Clinic", wide, enabled = false)
        ShifaInputField("secret", {}, "Password", wide, trailingIcon = {
            Icon(painterResource(Res.drawable.ic_eye_20), null, tint = ShifaColors.Icon.Secondary)
        })
        ShifaSearchField("", {}, "Search by name, ID or phone", wide)
        ShifaSearchField("Jasur", {}, "Search by name, ID or phone", wide)
        ShifaSearchField("", {}, "Search", wide, enabled = false)
        val options = listOf("Main Clinic", "Video consultation", "Chilonzor branch")
        ShifaDropdown("Location", options, null, {}, expanded = false, onExpandedChange = {}, modifier = wide)
        // Collapsed with a value: the open menu is an overlay window (design review D13), which a widget capture leaves out.
        ShifaDropdown("Location", options, "Main Clinic", {}, expanded = false, onExpandedChange = {}, modifier = wide)
        ShifaDropdown("Location", options, null, {}, false, {}, wide, isError = true)
    }

// Chips (filter, slot, grid, carousel), switch, checkbox, radio, choice cards, option list.
@Preview
@Composable
internal fun ChoicesPreview() =
    Gallery {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
            ShifaChip("All", selected = true, onClick = {})
            ShifaChip("Active", selected = false, onClick = {})
            ShifaChip("09:30", selected = true, onClick = {}, type = ShifaChipType.Slot)
            ShifaChip("10:00", selected = false, onClick = {}, type = ShifaChipType.Slot)
        }
        val times = listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", "12:30")
        ShifaChipGrid(times, Modifier.fillMaxWidth()) {
            ShifaChip(it, selected = it == "10:00", onClick = {}, type = ShifaChipType.Slot)
        }
        ShifaCarousel {
            listOf(
                "All",
                "Today",
                "Video",
                "In clinic",
                "Unpaid",
            ).forEach { ShifaChip(it, it == "Today", {}) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            ShifaSwitch(checked = false, onCheckedChange = {})
            ShifaSwitch(checked = true, onCheckedChange = {})
            ShifaCheckbox(ToggleableState.Off)
            ShifaCheckbox(ToggleableState.On)
            ShifaCheckbox(ToggleableState.Indeterminate)
            ShifaRadio(false)
            ShifaRadio(true)
        }
        ShifaCheckboxRow("Send a reminder by SMS", ToggleableState.On, {})
        ShifaChoiceCard("In clinic", selected = true, onClick = {}, description = "Main Clinic, Chilonzor")
        ShifaChoiceCard("Video call", selected = false, onClick = {}, description = "Daily room opens 10 min before")
        ShifaOptionList(
            listOf(
                OptionItem("English", selected = true),
                OptionItem("O‘zbekcha"),
                OptionItem("Ўзбекча"),
                OptionItem("Русский"),
                OptionItem("Dr. Karimova", dot = ShifaColors.Chart.Series4, separatorBefore = true),
                OptionItem("Delete", icon = Res.drawable.ic_trash_16, danger = true, separatorBefore = true),
            ),
            {},
            heading = "Language",
        )
    }

// OtpInput (empty, focused mid-entry, error), PinDots and the PinPad with biometrics.
@Preview
@Composable
internal fun OtpAndPinPreview() =
    Gallery {
        OtpInput("", {})
        OtpInput("482", {})
        OtpInput("482913", {}, error = true)
        PinDots(filled = 3, total = 6)
        PinPad({}, {}, "Delete", leftKey = Res.drawable.ic_fingerprint_22, leftKeyDescription = "Use Face ID")
    }

// Slider (revenue share) and Stepper.
@Preview
@Composable
internal fun SliderAndStepperPreview() =
    Gallery {
        ShifaSlider(70f, {}, "Doctor 70 %", "Clinic 30 %", valueField = { ShifaInputField("70", {}, "%") })
        Stepper("Step 2 of 4", "Services and teeth", current = 1, total = 4)
        Stepper("Step 4 of 4", "Payment schedule", current = 3, total = 4)
    }

// The icons the kit uses most, at their sizes, so a regenerated set shows up as a diff.
@Preview
@Composable
internal fun IconsPreview() =
    Gallery {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            listOf(
                Res.drawable.ic_cal_plus_22,
                Res.drawable.ic_user_plus_22,
                Res.drawable.ic_video_22,
                Res.drawable.ic_sparkles_22,
                Res.drawable.ic_fingerprint_22,
            ).forEach { Icon(painterResource(it), null, tint = ShifaColors.Icon.Primary) }
        }
    }
