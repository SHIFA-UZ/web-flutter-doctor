package shifa.doctor.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_logout_22
import shifa.doctor.generated.resources.ic_search_22
import shifa.doctor.generated.resources.ic_sparkles_18
import shifa.doctor.generated.resources.navPractice
import shifa.doctor.generated.resources.needsProPlan
import shifa.doctor.generated.resources.planPremium
import shifa.doctor.generated.resources.searchPatients
import shifa.doctor.generated.resources.shifaIntelligence
import shifa.doctor.generated.resources.signOut
import shifa.doctor.generated.resources.talkWithAi
import shifa.doctor.generated.resources.unreadBadge
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaAppIcon
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.utils.tr

/** Who the sidebar's footer shows (the doctor's profile; staff show their photo only). */
data class ShellIdentity(
    val name: String,
    val subtitle: String? = null,
    val photoUrl: String? = null,
)

/**
 * WEB-06: the wide-screen chrome instead of the bottom bar and More (DaApp `webNav`, boards `Sidebar` and
 * `ShellTablet`): every destination in one list, Shifa Intelligence and the person at the bottom. [rail] is the
 * 72-wide icon column (768–1099), otherwise the full sidebar.
 */
@Composable
internal fun ShellSidebar(
    state: ShellUiState,
    actions: ShellActions,
    identity: ShellIdentity?,
    rail: Boolean,
    search: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .width(if (rail) ShifaDimens.Layout.SidebarCompact else ShifaDimens.Layout.Sidebar)
        .fillMaxHeight()
        .background(ShifaColors.Bg.Surface),
) {
    Column(
        Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (rail) ShifaSize.S12 else ShifaSize.S14,
                vertical = ShifaSize.S20,
            ),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S14),
    ) {
        Row(
            Modifier.fillMaxWidth().then(if (rail) Modifier else Modifier.padding(horizontal = ShifaSize.S6)),
            horizontalArrangement = if (rail) Arrangement.Center else Arrangement.spacedBy(ShifaSize.S10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShifaAppIcon(ShifaSize.S32)
            if (!rail) {
                Text(
                    "Shifa",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = ShifaColors.Text.Primary,
                )
                state.gate.tier?.let { tier ->
                    Text(
                        tier.name.lowercase().replaceFirstChar { it.uppercase() },
                        Modifier
                            .background(ShifaColors.Bg.Canvas, ShifaRadius.Full)
                            .padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S2),
                        style = ShifaTextStyles.badgeCount(),
                        color = ShifaColors.Text.Secondary,
                    )
                }
            }
        }
        if (!state.staff) {
            SearchButton(tr(Res.string.searchPatients), rail, search)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            sidebarEntries(state).forEach { entry ->
                when (entry) {
                    is SideEntry.Group ->
                        if (rail) {
                            HorizontalDivider(
                                Modifier.padding(vertical = ShifaSize.S8),
                                ShifaSize.S1,
                                ShifaColors.Border.Default,
                            )
                        } else {
                            Text(
                                tr(entry.label),
                                Modifier.padding(
                                    start = ShifaSpacing.Inset.Sm,
                                    top = ShifaSize.S10,
                                    bottom = ShifaSize.S4,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = ShifaColors.Text.Secondary,
                            )
                        }
                    is SideEntry.Item ->
                        SideItem(
                            entry,
                            selected = !state.intelligence && state.tab == entry.tab,
                            rail = rail,
                        ) { actions.tapTab(entry.tab, "sidebar") }
                }
            }
        }
    }
    HorizontalDivider(thickness = ShifaSize.S1, color = ShifaColors.Border.Default)
    val profileLabel = tr(ShellTab.Profile.label)
    Column(
        Modifier.padding(
            start = if (rail) ShifaSize.S12 else ShifaSize.S14,
            end = if (rail) ShifaSize.S12 else ShifaSize.S14,
            top = ShifaSize.S12,
            bottom = ShifaSize.S16,
        ),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    ) {
        // Gated like More's row: hidden until the plan is known (D-03).
        if (!state.staff && state.gate.known) IntelligenceCard(state, actions, rail)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (rail) Arrangement.Center else Arrangement.spacedBy(ShifaSize.S10),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier
                    .then(if (rail) Modifier else Modifier.weight(1f))
                    .clip(ShifaRadius.ControlMd)
                    .pointerHoverIcon(
                        PointerIcon.Hand,
                    ).clickable(role = Role.Button) { actions.tapTab(ShellTab.Profile, "sidebar") }
                    // The name is the label when shown; the rail and staff show the photo only.
                    .then(
                        if (rail || identity == null) {
                            Modifier.semantics { contentDescription = profileLabel }
                        } else {
                            Modifier
                        },
                    ),
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShifaAvatar(identity?.photoUrl ?: state.avatarUrl, ShifaSize.S40)
                if (!rail && identity != null) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                        Text(
                            identity.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = ShifaColors.Text.Primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        identity.subtitle?.let {
                            Text(
                                it,
                                style = ShifaTextStyles.metaSmall(),
                                color = ShifaColors.Text.Secondary,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            if (!rail) {
                Box(
                    Modifier
                        .size(ShifaSize.S36)
                        .clip(ShifaRadius.Full)
                        .background(ShifaColors.Bg.Canvas)
                        .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = actions.signOut),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_logout_22),
                        tr(Res.string.signOut),
                        tint = ShifaColors.Icon.Secondary,
                    )
                }
            }
        }
    }
}

private sealed interface SideEntry {
    data class Item(
        val tab: ShellTab,
        val count: Int = 0,
        val locked: Boolean = false,
    ) : SideEntry

    data class Group(
        val label: StringResource,
    ) : SideEntry
}

/** DaApp `webTabs`: doctors get every destination (Clinic with a clinic, Tasks once the plan is known); staff four. */
private fun sidebarEntries(state: ShellUiState): List<SideEntry> {
    fun item(tab: ShellTab) = SideEntry.Item(tab, state.count(tab))
    if (state.staff) return listOf(ShellTab.Chat, ShellTab.Clinic, ShellTab.Notifications, ShellTab.Profile).map(::item)
    return buildList {
        addAll(
            listOf(
                ShellTab.Home,
                ShellTab.Calendar,
                ShellTab.Patients,
                ShellTab.Chat,
                ShellTab.Notifications,
            ).map(::item),
        )
        if (state.gate.known) {
            add(
                if (state.canTasks) item(ShellTab.Tasks) else SideEntry.Item(ShellTab.Tasks, locked = true),
            )
        }
        add(SideEntry.Group(Res.string.navPractice))
        if (state.hasClinic == true) add(item(ShellTab.Clinic))
        add(item(ShellTab.Reports))
        add(item(ShellTab.Profile))
    }
}

@Composable
private fun SideItem(
    entry: SideEntry.Item,
    selected: Boolean,
    rail: Boolean,
    onClick: () -> Unit,
) {
    val label = tr(entry.tab.label)
    // "Chat, 3 unread" (flow 01/16 §8), as the bar says it.
    val description = if (entry.count > 0) "$label, ${tr(Res.string.unreadBadge, entry.count)}" else label
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(ShifaSize.Touch)
            .clip(ShifaRadius.ControlMd)
            .background(
                when {
                    selected -> ShifaColors.Bg.Selected
                    hovered -> ShifaColors.Bg.Hover
                    else -> Color.Transparent
                },
            ).hoverable(hover)
            .pointerHoverIcon(PointerIcon.Hand).selectable(
                selected = selected,
                enabled = !entry.locked,
                role = Role.Tab,
                onClick = onClick,
            ).semantics {
                contentDescription = description
            }.then(if (rail) Modifier else Modifier.padding(horizontal = ShifaSpacing.Inset.Sm)),
        contentAlignment = if (rail) Alignment.Center else Alignment.CenterStart,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (selected) entry.tab.selectedIcon else entry.tab.icon),
                contentDescription = null,
                tint =
                    when {
                        entry.locked -> ShifaColors.Icon.Disabled
                        selected -> ShifaColors.Text.BrandStrong
                        else -> ShifaColors.Icon.Primary
                    },
            )
            if (!rail) {
                Text(
                    label,
                    Modifier.weight(1f),
                    style =
                        ShifaTextStyles.dialogBody().copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        ),
                    color = if (entry.locked) ShifaColors.Text.Tertiary else ShifaColors.Text.Primary,
                )
                if (entry.locked) {
                    Text(
                        tr(Res.string.planPremium),
                        Modifier
                            .background(ShifaColors.Bg.Canvas, ShifaRadius.Full)
                            .padding(horizontal = ShifaSize.S8, vertical = ShifaSize.S2),
                        style = ShifaTextStyles.badgeCount(),
                        color = ShifaColors.Text.Secondary,
                    )
                } else if (entry.count > 0) {
                    CountPill(entry.count)
                }
            }
        }
        // The rail has no room for the number: a dot on the icon, the number in the description.
        if (rail && entry.count > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = ShifaSize.S8, end = ShifaSize.S12)
                    .size(ShifaSize.S8)
                    .background(ShifaColors.Interactive.Primary, ShifaRadius.Full),
            )
        }
    }
}

@Composable
private fun CountPill(count: Int) =
    Box(
        Modifier
            .defaultMinSize(minWidth = ShifaSize.S20, minHeight = ShifaSize.S20)
            .background(ShifaColors.Interactive.Primary, ShifaRadius.Full)
            .padding(horizontal = ShifaSize.S6),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            countLabel(count),
            style = ShifaTextStyles.badgeCount(),
            color = ShifaColors.Interactive.OnPrimary,
            maxLines = 1,
        )
    }

/** The search button above the list (DaApp `da-search`, `.sh-search`): a field that opens the patient search. */
@Composable
private fun SearchButton(
    label: String,
    rail: Boolean,
    onClick: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .height(ShifaSize.Touch)
        .clip(ShifaRadius.Full)
        .background(ShifaColors.Bg.Surface)
        .border(ShifaSize.S2, ShifaColors.Border.Default, ShifaRadius.Full)
        .pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }
        .then(if (rail) Modifier else Modifier.padding(horizontal = ShifaSpacing.Inset.Sm)),
    horizontalArrangement = if (rail) Arrangement.Center else Arrangement.spacedBy(ShifaSize.S8),
    verticalAlignment = Alignment.CenterVertically,
) {
    Icon(painterResource(Res.drawable.ic_search_22), null, tint = ShifaColors.Icon.Secondary)
    if (!rail) Text(label, style = ShifaTextStyles.chatInput(), color = ShifaColors.Text.Tertiary)
}

/** DaApp `da-ai`: Shifa Intelligence, with "Needs the Pro plan" when the plan lacks it. */
@Composable
private fun IntelligenceCard(
    state: ShellUiState,
    actions: ShellActions,
    rail: Boolean,
) = Row(
    Modifier
        .fillMaxWidth()
        .clip(ShifaRadius.ControlMd)
        // DaApp `sh-press--tint`; the border marks it open (the selected press state).
        .background(ShifaColors.Bg.SuccessSubtle)
        .then(
            if (state.intelligence) {
                Modifier.border(
                    ShifaSize.S2,
                    ShifaColors.Border.Focus,
                    ShifaRadius.ControlMd,
                )
            } else {
                Modifier
            },
        )
        .pointerHoverIcon(
            PointerIcon.Hand,
        ).clickable(enabled = state.canIntelligence, role = Role.Button, onClick = actions.openIntelligence)
        .padding(if (rail) ShifaSize.S6 else ShifaSize.S10),
    horizontalArrangement = if (rail) Arrangement.Center else Arrangement.spacedBy(ShifaSize.S10),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(
        Modifier.size(ShifaSize.S30).background(ShifaColors.Interactive.Primary, ShifaRadius.Full),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(Res.drawable.ic_sparkles_18),
            tr(Res.string.shifaIntelligence),
            tint = ShifaColors.Icon.Inverse,
        )
    }
    if (!rail) {
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(
                tr(Res.string.shifaIntelligence),
                style = MaterialTheme.typography.titleSmall,
                color = ShifaColors.Text.Primary,
            )
            Text(
                tr(if (state.canIntelligence) Res.string.talkWithAi else Res.string.needsProPlan),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
        }
    }
}
