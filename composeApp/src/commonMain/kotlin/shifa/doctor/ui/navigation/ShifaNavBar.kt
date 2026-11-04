package shifa.doctor.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaAvatar

/** A tab of [ShifaNavBar]. [selectedIcon] is the bolder stroke the board draws for the current tab. */
data class NavBarItem(
    val label: String,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource = icon,
    val badge: Int = 0,
    // Screen-reader text with the count ("Chat, 3 unread"); the badge itself is hidden from it.
    val description: String = label,
    // The staff bar's Profile slot: the person's photo instead of the icon (flow 01/18 §3).
    val avatar: Boolean = false,
    val avatarUrl: String? = null,
)

/**
 * `.sh-nav` / NavBar board: the phone's liquid-glass bottom bar, 64 tall, tabs sharing the width. The shell
 * (SHELL-01) decides the tabs: Home, Calendar, Patients, Chat, More for doctors; fewer for clinic staff.
 */
@Composable
fun ShifaNavBar(
    items: List<NavBarItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(ShifaSize.S64)
            .dropShadow(ShifaRadius.Full, ShifaShadow.NavBar)
            .background(
                // The glass gradient over the blur fallback (`.sh-nav` without backdrop-filter is Alpha.White70).
                Brush.linearGradient(
                    listOf(ShifaColors.Alpha.White65, ShifaColors.Alpha.White35, ShifaColors.Alpha.White55),
                ),
                ShifaRadius.Full,
            ).background(ShifaColors.Alpha.White70, ShifaRadius.Full)
            .border(ShifaSize.S1, ShifaColors.Alpha.White70, ShifaRadius.Full)
            .padding(horizontal = ShifaSize.S6),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val isSelected = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = ShifaSize.Touch)
                    .selectable(
                        selected = isSelected,
                        role = Role.Tab,
                        onClick = { onSelect(i) },
                        interactionSource = null,
                        indication = ripple(bounded = false),
                    ).semantics { contentDescription = item.description },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
                ) {
                    if (item.avatar) {
                        ShifaAvatar(item.avatarUrl, ShifaSize.S24)
                    } else {
                        Icon(
                            painterResource(if (isSelected) item.selectedIcon else item.icon),
                            contentDescription = null,
                            tint = if (isSelected) ShifaColors.Icon.Brand else ShifaColors.Icon.Primary,
                        )
                    }
                    Text(
                        item.label,
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            ),
                        color = if (isSelected) ShifaColors.Text.Brand else ShifaColors.Text.Primary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                if (item.badge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .offset(x = ShifaSize.S14)
                            .defaultMinSize(minWidth = ShifaSize.S16)
                            .height(ShifaSize.S16)
                            .background(ShifaColors.Interactive.Primary, ShifaRadius.Full)
                            .padding(horizontal = ShifaSize.S4),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            countLabel(item.badge),
                            style = ShifaTextStyles.badgeCount(),
                            color = ShifaColors.Interactive.OnPrimary,
                        )
                    }
                }
            }
        }
    }
}
