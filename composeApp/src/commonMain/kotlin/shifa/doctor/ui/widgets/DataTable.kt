package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.ic_chev_l_16
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles

/**
 * DataTable below `breakpoint-split-pane` (every phone): each row becomes a stacked card. [main] spans the card
 * (the person cell); [cells] follow as "Label · value" pairs; [trailing] sits on the main line (a status badge).
 */
@Composable
fun DataTableCard(
    main: @Composable () -> Unit,
    cells: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlMd)
            .background(if (selected) ShifaColors.Bg.Selected else ShifaColors.Bg.Canvas)
            .then(
                if (onClick != null) {
                    Modifier.pointerHoverIcon(
                        PointerIcon.Hand,
                    ).clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(ShifaSpacing.Inset.Sm),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { main() }
            trailing?.invoke()
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Xs),
        ) {
            cells.forEach { (label, value) ->
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = ShifaColors.Text.Tertiary)) { append("$label · ") }
                        append(value)
                    },
                    style = ShifaTextStyles.kpiLabel(),
                    color = ShifaColors.Text.Primary,
                )
            }
        }
    }
}

/** A column of a [DataTable]: its heading and its share of the width. */
class TableColumn(
    val title: String,
    val weight: Float = 1f,
)

/**
 * `.dr-table` from `breakpoint-split-pane` up (WEB-07): the headings on the canvas tint, one row per item with a rule
 * under it and the hover tint; [onRowClick] makes rows buttons. [cell] draws column [column] of [row].
 */
@Composable
fun <T> DataTable(
    columns: List<TableColumn>,
    rows: List<T>,
    onRowClick: ((T) -> Unit)?,
    modifier: Modifier = Modifier,
    cell: @Composable (row: T, column: Int) -> Unit,
) = Column(modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S8)) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd)
            .padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSize.S10),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        columns.forEach {
            Text(
                it.title,
                Modifier.weight(it.weight),
                style = ShifaTextStyles.tableHead(),
                color = ShifaColors.Text.Secondary,
            )
        }
    }
    rows.forEach { row ->
        val hover = remember { MutableInteractionSource() }
        val hovered by hover.collectIsHoveredAsState()
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (hovered && onRowClick != null) ShifaColors.Bg.Hover else Color.Transparent)
                .hoverable(hover)
                .then(
                    onRowClick?.let {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(role = Role.Button) { it(row) }
                    } ?: Modifier,
                ).padding(ShifaSpacing.Inset.Sm),
            horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEachIndexed { i, column -> Box(Modifier.weight(column.weight)) { cell(row, i) } }
        }
        HorizontalDivider(thickness = ShifaSize.S1, color = ShifaColors.Border.Default)
    }
}

/** "Dilnoza Yusupova" → "DY": the first letters of the first two words. */
fun initialsOf(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

/** The person cell of a [DataTableCard]: initials disc (or photo), name and a muted line (phone, ID). */
@Composable
fun PersonCell(
    name: String,
    detail: String,
    initials: String,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
) = Row(
    modifier,
    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    verticalAlignment = Alignment.CenterVertically,
) {
    if (photoUrl != null) {
        ShifaAvatar(photoUrl, ShifaSize.S32)
    } else {
        Box(
            Modifier.size(ShifaSize.S32).background(ShifaColors.Alpha.Teal15, ShifaRadius.Full),
            contentAlignment = Alignment.Center,
        ) {
            Text(initials, style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Primary)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            name,
            style = ShifaTextStyles.tableCell().copy(fontWeight = FontWeight.Bold),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
        )
        Text(detail, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Tertiary, maxLines = 1)
    }
}

/** TableStates "loading": skeleton rows in the row's shape (avatar, two lines, a value, a status pill). */
@Composable
fun DataTableSkeleton(
    loadingDescription: String,
    modifier: Modifier = Modifier,
    rows: Int = 6,
) {
    Column(modifier.fillMaxWidth().semantics { contentDescription = loadingDescription }) {
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth().padding(ShifaSpacing.Inset.Sm),
                horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShifaSkeleton(Modifier.size(ShifaSize.S32), ShifaRadius.Full)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                    ShifaSkeleton(Modifier.fillMaxWidth(0.6f).height(ShifaSize.S12))
                    ShifaSkeleton(Modifier.fillMaxWidth(0.4f).height(ShifaSize.S10))
                }
                ShifaSkeleton(Modifier.width(ShifaSize.S90).height(ShifaSize.S24), ShifaRadius.Full)
            }
        }
    }
}

/** TableStates "filtered empty": the icon disc, the sentence and the action that clears search and chips. */
@Composable
fun DataTableEmpty(
    title: String,
    icon: DrawableResource,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSize.S48),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        Box(
            Modifier.size(ShifaSize.S56).background(ShifaColors.Bg.Canvas, ShifaRadius.Full),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = ShifaColors.Icon.Secondary)
        }
        Text(
            title,
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
            textAlign = TextAlign.Center,
        )
        ShifaSecondaryButton(actionText, onAction)
    }
}

/**
 * `.dr-table-foot` + `.dr-pager`: "1–6 of 12" and the page buttons, 44 round on phones; the current page is teal.
 * Shows at most five page numbers around the current one.
 */
@Composable
fun ShifaPager(
    summary: String,
    page: Int,
    pageCount: Int,
    onPage: (Int) -> Unit,
    previousDescription: String,
    nextDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(summary, style = ShifaTextStyles.kpiLabel(), color = ShifaColors.Text.Secondary)
        Row(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PagerButton(enabled = page > 1, description = previousDescription, onClick = { onPage(page - 1) }) {
                Icon(painterResource(Res.drawable.ic_chev_l_16), null, tint = ShifaColors.Icon.Primary)
            }
            // Three page numbers: five 44 dp touch targets plus the summary don't fit a 360 dp phone.
            val first = (page - 1).coerceIn(1, maxOf(1, pageCount - 2))
            (first..minOf(pageCount, first + 2)).forEach { p ->
                PagerButton(current = p == page, onClick = { onPage(p) }) {
                    Text(
                        "$p",
                        style = ShifaTextStyles.buttonCompact(),
                        color = if (p == page) ShifaColors.Interactive.OnPrimary else ShifaColors.Text.Primary,
                    )
                }
            }
            PagerButton(enabled = page < pageCount, description = nextDescription, onClick = { onPage(page + 1) }) {
                Icon(painterResource(Res.drawable.ic_chev_r_16), null, tint = ShifaColors.Icon.Primary)
            }
        }
    }
}

@Composable
private fun PagerButton(
    onClick: () -> Unit,
    current: Boolean = false,
    enabled: Boolean = true,
    description: String? = null,
    content: @Composable () -> Unit,
) = Box(
    Modifier
        .defaultMinSize(minWidth = ShifaSize.Touch, minHeight = ShifaSize.Touch)
        .clip(ShifaRadius.Full)
        .background(if (current) ShifaColors.Interactive.Primary else ShifaColors.Bg.Surface)
        .pointerHoverIcon(
            PointerIcon.Hand,
        ).clickable(enabled = enabled && !current, role = Role.Button, onClick = onClick)
        .semantics { description?.let { contentDescription = it } },
    contentAlignment = Alignment.Center,
) { content() }
