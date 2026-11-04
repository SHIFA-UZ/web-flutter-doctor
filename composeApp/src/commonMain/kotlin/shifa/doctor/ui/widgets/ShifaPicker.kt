package shifa.doctor.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.close
import shifa.doctor.generated.resources.couldNotLoadRetry
import shifa.doctor.generated.resources.ic_chev_d_20
import shifa.doctor.generated.resources.ic_x_16
import shifa.doctor.generated.resources.noResults
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaShadow
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.utils.tr

/**
 * The board's "pick" field (DaAuth `isPick`): looks like an input, opens a [ShifaPickerSheet] when tapped. [value]
 * empty shows [placeholder]; [chips] (a multi-select's picks) show as tags inside the field instead.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShifaPickField(
    label: String,
    value: String,
    placeholder: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    chips: List<String> = emptyList(),
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs)) {
    val border =
        when {
            !enabled -> ShifaColors.Border.Disabled
            isError -> ShifaColors.Border.Error
            else -> ShifaColors.Border.Default
        }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ShifaRadius.ControlMd)
            .background(if (enabled) ShifaColors.Bg.Surface else ShifaColors.Bg.Disabled)
            .border(ShifaSize.S2, border, ShifaRadius.ControlMd)
            .clickable(enabled = enabled, role = Role.DropdownList, onClick = onClick)
            .padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSpacing.Inset.Xs)
            .defaultMinSize(minHeight = ShifaSize.S30),
        horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (value.isNotEmpty() || chips.isNotEmpty()) {
                Text(label, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Tertiary)
            }
            if (chips.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(vertical = ShifaSize.S4),
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
                ) { chips.forEach { ShifaTag(it, type = ShifaTagType.Primary) } }
            } else {
                Text(
                    value.ifEmpty { placeholder },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isNotEmpty()) ShifaColors.Text.Primary else ShifaColors.Text.Tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(painterResource(Res.drawable.ic_chev_d_20), null, tint = ShifaColors.Icon.Secondary)
    }
    supportingText?.let {
        Text(
            it,
            Modifier.padding(horizontal = ShifaSpacing.Inset.Sm),
            style = ShifaTextStyles.caption(),
            color = if (isError) ShifaColors.Text.Error else ShifaColors.Text.Secondary,
        )
    }
}

/**
 * The board's picker sheet (DaAuth picker: timezone, clinic, profession): title and close, a search field, the
 * options (lazy: a timezone search lists hundreds), "No results", and an optional [footer] (Clear, Done). While the
 * list is empty, [loadingText] ("Searching…") or, with [onRetry], "Couldn't load. Retry" replace "No results"; with
 * options (a bundled fallback) the retry link sits above them. Host it in a `ModalBottomSheet`; it only draws.
 */
@Composable
fun ShifaPickerSheet(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    searchPlaceholder: String,
    items: List<OptionItem>,
    onPick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    loadingText: String? = null,
    onRetry: (() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(topStart = ShifaSize.S24, topEnd = ShifaSize.S24)
    Column(
        modifier
            .trackActivity()
            .fillMaxWidth()
            .dropShadow(shape, ShifaShadow.Tray)
            .background(ShifaColors.Bg.Surface, shape)
            .navigationBarsPadding()
            .padding(horizontal = ShifaSpacing.Inset.Md, vertical = ShifaSpacing.Inset.Sm),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = ShifaColors.Text.Primary,
            )
            ShifaActionButton(
                onClose,
                icon = Res.drawable.ic_x_16,
                contentDescription = tr(Res.string.close),
                variant = ShifaActionVariant.Secondary,
            )
        }
        ShifaSearchField(query, onQuery, searchPlaceholder)
        if (onRetry != null && items.isNotEmpty()) ShifaLink(tr(Res.string.couldNotLoadRetry), onRetry)
        Box(Modifier.fillMaxWidth().heightIn(min = ShifaSize.S120, max = ShifaSize.S320)) {
            if (items.isEmpty() && onRetry != null) {
                ShifaLink(
                    tr(Res.string.couldNotLoadRetry),
                    onRetry,
                    Modifier.align(Alignment.Center).padding(vertical = ShifaSpacing.Inset.Lg),
                )
            } else if (items.isEmpty()) {
                Text(
                    loadingText ?: tr(Res.string.noResults),
                    Modifier.fillMaxWidth().padding(vertical = ShifaSpacing.Inset.Lg),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyColumn { itemsIndexed(items) { i, item -> OptionRow(item) { onPick(i) } } }
            }
        }
        footer?.invoke(this)
    }
}
