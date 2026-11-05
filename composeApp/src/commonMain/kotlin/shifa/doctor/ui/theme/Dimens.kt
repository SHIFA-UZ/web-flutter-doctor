package shifa.doctor.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

// `--inset-*`, `--gap-*`, `--stack-*`. Inset = padding, gap = between siblings in a row, stack = in a column.
object ShifaSpacing {
    object Inset {
        val Xs = 8.dp
        val Sm = 12.dp
        val Md = 16.dp
        val Lg = 24.dp
    }

    object Gap {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 24.dp
    }

    object Stack {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 16.dp
    }
}

// `--size-*`: component sizes and strokes.
object ShifaSize {
    val S1 = 1.dp
    val S2 = 2.dp
    val S3 = 3.dp
    val S4 = 4.dp
    val S6 = 6.dp
    val S8 = 8.dp
    val S10 = 10.dp
    val S12 = 12.dp
    val S14 = 14.dp
    val S16 = 16.dp
    val S18 = 18.dp
    val S20 = 20.dp
    val S22 = 22.dp
    val S24 = 24.dp
    val S26 = 26.dp
    val S28 = 28.dp
    val S30 = 30.dp
    val S32 = 32.dp
    val S36 = 36.dp
    val S40 = 40.dp
    val S48 = 48.dp
    val S52 = 52.dp
    val S56 = 56.dp
    val S60 = 60.dp
    val S64 = 64.dp
    val S72 = 72.dp
    val S76 = 76.dp
    val S80 = 80.dp
    val S88 = 88.dp
    val S90 = 90.dp
    val S96 = 96.dp
    val S100 = 100.dp
    val S112 = 112.dp
    val S120 = 120.dp
    val S140 = 140.dp
    val S160 = 160.dp
    val S180 = 180.dp
    val S220 = 220.dp
    val S240 = 240.dp
    val S280 = 280.dp
    val S320 = 320.dp

    // Control heights (`--size-control-*`) and the minimum touch target (`--size-touch`, the audit's 44 px rule).
    val ControlLg = 46.dp
    val ControlMd = 36.dp
    val ControlSm = 32.dp
    val Touch = 44.dp
}

// `--breakpoint-*`: content widths where layouts change. The KMP app is phone/tablet only (D-08); tablet-only lists
// switch at Mobile.
object ShifaBreakpoint {
    val Compact = 560.dp
    val SplitPane = 720.dp
    val Mobile = 768.dp
    val Tablet = 980.dp
    val Sidebar = 1100.dp
}

// `--blur-*`: glass surfaces and glows.
object ShifaBlur {
    val Glass = 24.dp
    val Soft = 20.dp
    val Glow = 30.dp
}

// Sizes the design sets per component, named <Component><Part>; `Layout` and `Basis` are the stylesheet's
// `--layout-*` and `--basis-*` (pane widths, dialog widths, flex bases).
object ShifaDimens {
    // `.sh-fab--docked`: the floating tab bar's height, which a docked create button sits above (bottom 16 + 82).
    val FabDockedClear = 82.dp

    object Layout {
        val Sidebar = 260.dp
        val SidebarCompact = 72.dp
        val DialogMax = 480.dp
        val ListPane = 320.dp
        val DialogSm = 360.dp
        val DialogMd = 420.dp
        val FormNarrow = 560.dp
        val Form = 640.dp
        val Reading = 720.dp
        val FormWide = 760.dp
        val Document = 900.dp
        val Page = 1280.dp
    }

    object Basis {
        val B90 = 90.dp
        val B130 = 130.dp
        val B200 = 200.dp
        val B260 = 260.dp
        val B460 = 460.dp
        val B520 = 520.dp
    }

    // buttons
    val TertiaryButtonPaddingStart = 10.dp
    val TertiaryButtonPaddingEnd = 15.dp
    val TertiaryButtonPaddingVertical = 10.dp

    // page-header
    val PageHeaderAvatar = 40.dp
    val SubHeaderAvatar = 32.dp

    // interactive-chip
    val InteractiveChipBorder = 1.dp
    val InteractiveChipIconGap = 5.dp

    // dropdown
    val DropdownMenuPaddingBottom = 13.dp

    // Not in the design (its lists are short): a long list (times of day) scrolls inside this height.
    val DropdownMenuMaxHeight = 320.dp

    // switch
    val SwitchWidth = 40.dp
    val SwitchKnob = 15.dp

    // notification
    val NotificationAvatar = 56.dp

    // chat-bubble
    val ChatBubbleMaxWidth = 360.dp
    val ChatImage = 280.dp
    val ChatVoiceWidth = 220.dp

    // skeletons: a placeholder chat bubble and a slot chip
    val ChatSkeletonHeight = 50.dp
    val ChipSkeletonHeight = 34.dp

    // section
    val SectionBorder = 1.dp

    // nav-bar
    val NavBarTileWidth = 65.dp

    // text area and signature pad
    val TextAreaHeight = 134.dp
    val SignaturePadHeight = 151.dp
    val SignatureStroke = 2.5.dp

    // The tooth map's tiles (`.dr-tooth`): a phone's 16 across, and the wider screens'.
    val ToothW = 19.dp
    val ToothH = 30.dp
    val ToothWideW = 36.dp
    val ToothWideH = 40.dp
    val ToothCrownRadius = 6.dp
    val ToothRootRadius = 10.dp

    // From this width the map uses the wider tiles (the board's container query).
    val ToothMapWide = 560.dp

    // rating
    val RatingStar = 29.dp
}

// `--radius-*`. Full (999) = CircleShape.
object ShifaRadius {
    val Xs = RoundedCornerShape(4.dp)
    val Sm = RoundedCornerShape(8.dp)
    val ControlMd = RoundedCornerShape(10.dp) // buttons, inputs, cards
    val ControlLg = RoundedCornerShape(16.dp) // sections, images
    val Xl = RoundedCornerShape(24.dp) // trays, pop-ups
    val Full = CircleShape // tags, action/icon buttons, avatars
}

internal val ShifaShapes =
    Shapes(
        extraSmall = ShifaRadius.Sm,
        small = ShifaRadius.ControlMd,
        medium = ShifaRadius.ControlLg,
        large = ShifaRadius.Xl,
        extraLarge = ShifaRadius.Xl,
    )

// `--shadow-*`, spread 0. Use: Modifier.dropShadow(ShifaRadius.ControlMd, ShifaShadow.Card)
object ShifaShadow {
    val Default = Shadow(radius = 16.dp, color = Color(0x26000000)) // buttons
    val Card = Shadow(radius = 14.dp, color = Color(0x26000000))
    val Tray = Shadow(radius = 26.dp, color = Color(0x66000000)) // trays, modals
    val Dropdown = Shadow(radius = 6.dp, color = Color(0x26000000))
    val Section = Shadow(radius = 12.7.dp, color = Color(0x26000000)) // section inside a section
    val NavBar = Shadow(radius = 41.4.dp, color = Color(0x3B000000), offset = DpOffset(2.dp, 2.dp))
    val Fab = Shadow(radius = 20.dp, color = Color(0x40000000), offset = DpOffset(0.dp, 6.dp))
}
