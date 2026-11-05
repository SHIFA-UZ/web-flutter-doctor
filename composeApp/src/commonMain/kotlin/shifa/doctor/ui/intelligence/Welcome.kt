package shifa.doctor.ui.intelligence

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.intelligence.ChatSession
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.goodAfternoon
import shifa.doctor.generated.resources.goodEvening
import shifa.doctor.generated.resources.goodMorning
import shifa.doctor.generated.resources.ic_cal_plus_22
import shifa.doctor.generated.resources.ic_calendar_22
import shifa.doctor.generated.resources.ic_file_text_22
import shifa.doctor.generated.resources.ic_search_22
import shifa.doctor.generated.resources.ic_sparkles_22
import shifa.doctor.generated.resources.intelHowCanIHelp
import shifa.doctor.generated.resources.intelPromptClinicalNotes
import shifa.doctor.generated.resources.intelPromptFindPatient
import shifa.doctor.generated.resources.intelPromptManageAppointments
import shifa.doctor.generated.resources.intelPromptSchedule
import shifa.doctor.generated.resources.intelRecentConversations
import shifa.doctor.generated.resources.intelWelcomeAppointments
import shifa.doctor.generated.resources.intelWelcomeAppointmentsDesc
import shifa.doctor.generated.resources.intelWelcomeFindPatient
import shifa.doctor.generated.resources.intelWelcomeFindPatientDesc
import shifa.doctor.generated.resources.intelWelcomeNotes
import shifa.doctor.generated.resources.intelWelcomeNotesDesc
import shifa.doctor.generated.resources.intelWelcomeSchedule
import shifa.doctor.generated.resources.intelWelcomeScheduleDesc
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.tr

/** A starter card: its title and line, and the prompt it sends as if typed (flow 03/01 §3). */
internal class Starter(
    val title: StringResource,
    val subtitle: StringResource,
    val prompt: StringResource,
    val icon: DrawableResource,
)

internal val starters =
    listOf(
        Starter(
            Res.string.intelWelcomeSchedule,
            Res.string.intelWelcomeScheduleDesc,
            Res.string.intelPromptSchedule,
            Res.drawable.ic_calendar_22,
        ),
        Starter(
            Res.string.intelWelcomeFindPatient,
            Res.string.intelWelcomeFindPatientDesc,
            Res.string.intelPromptFindPatient,
            Res.drawable.ic_search_22,
        ),
        Starter(
            Res.string.intelWelcomeAppointments,
            Res.string.intelWelcomeAppointmentsDesc,
            Res.string.intelPromptManageAppointments,
            Res.drawable.ic_cal_plus_22,
        ),
        Starter(
            Res.string.intelWelcomeNotes,
            Res.string.intelWelcomeNotesDesc,
            Res.string.intelPromptClinicalNotes,
            Res.drawable.ic_file_text_22,
        ),
    )

/** The greeting by the phone's hour (flow 03/01 §4): before 12 morning, before 17 afternoon, else evening. */
internal fun greetingFor(hour: Int): StringResource =
    when {
        hour < 12 -> Res.string.goodMorning
        hour < 17 -> Res.string.goodAfternoon
        else -> Res.string.goodEvening
    }

/** The five newest conversations that have something in them, for the welcome. */
internal fun recentChats(sessions: List<ChatSession>): List<ChatSession> =
    sessions.filter { it.messages.isNotEmpty() }.sortedByDescending { it.updatedAt }.take(RECENT)

/**
 * The welcome (INTEL-03, flow 03/01, board `DaIntel` welcome): the orb, "Good morning, Dr. med. Name." (no comma and
 * name until the profile is there), the four starter cards (one column on phones, two from 560 dp), and the five most
 * recent conversations with their options.
 */
@Composable
internal fun WelcomeBody(
    name: String?,
    sessions: List<ChatSession>,
    activeId: String?,
    now: Instant,
    onPrompt: (String) -> Unit,
    onOpen: (String) -> Unit,
    onOptions: (ChatSession) -> Unit,
    zone: TimeZone = TimeZone.currentSystemDefault(),
) = Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ShifaSize.S16)) {
    Column(Modifier.padding(top = ShifaSize.S8), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
        Box(
            Modifier
                .size(ShifaSize.S48)
                .background(
                    Brush.radialGradient(
                        listOf(ShifaColors.Alpha.Teal25, ShifaColors.Interactive.Primary, ShifaColors.Text.BrandStrong),
                    ),
                    ShifaRadius.Full,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(Res.drawable.ic_sparkles_22), null, tint = ShifaColors.Text.Inverse)
        }
        val greeting = tr(greetingFor(now.toLocalDateTime(zone).hour))
        Text(
            name?.takeIf { it.isNotBlank() }?.let { "$greeting, $it." } ?: "$greeting.",
            Modifier.padding(top = ShifaSize.S8).semantics { heading() },
            style = ShifaTextStyles.kpiValuePhone(),
            color = ShifaColors.Text.Primary,
        )
        Text(
            tr(Res.string.intelHowCanIHelp),
            style = ShifaTextStyles.panelTitle().copy(fontWeight = null),
            color = ShifaColors.Text.Secondary,
        )
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= ShifaDimens.Layout.FormNarrow) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
            starters.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
                    row.forEach { s -> StarterCard(s, Modifier.weight(1f), onPrompt) }
                }
            }
        }
    }
    val recent = recentChats(sessions)
    if (recent.isNotEmpty()) {
        Text(
            tr(Res.string.intelRecentConversations),
            Modifier.padding(top = ShifaSize.S8).semantics { heading() },
            style = ShifaTextStyles.buttonCompact(),
            color = ShifaColors.Text.Secondary,
        )
        recent.forEach { s -> SessionTile(s, s.id == activeId, now, { onOpen(s.id) }, { onOptions(s) }) }
    }
}

@Composable
private fun StarterCard(
    s: Starter,
    modifier: Modifier,
    onPrompt: (String) -> Unit,
) {
    val title = tr(s.title)
    val subtitle = tr(s.subtitle)
    val prompt = tr(s.prompt)
    Row(
        modifier
            .heightIn(min = ShifaSize.S72)
            .pressable({ onPrompt(prompt) }, PressVariant.Tile)
            .clearAndSetSemantics { contentDescription = "$title. $subtitle" }
            .padding(ShifaSize.S14),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    ) {
        Box(
            Modifier.size(ShifaSize.S40).background(ShifaColors.Bg.Surface, ShifaRadius.ControlMd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(s.icon), null, tint = ShifaColors.Text.BrandStrong)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(title, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
            Text(subtitle, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        }
    }
}

private const val RECENT = 5
