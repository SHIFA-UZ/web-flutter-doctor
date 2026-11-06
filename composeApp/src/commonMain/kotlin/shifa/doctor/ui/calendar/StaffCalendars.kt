package shifa.doctor.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.calendar.DayKey
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.ClinicMember
import shifa.doctor.data.model.MyClinic
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.calendarStaffCalendars
import shifa.doctor.generated.resources.clinicMemberFallback
import shifa.doctor.generated.resources.couldNotLoadThisDay
import shifa.doctor.generated.resources.ic_check_12
import shifa.doctor.generated.resources.mySchedule
import shifa.doctor.generated.resources.noItemsForThisDay
import shifa.doctor.generated.resources.retry
import shifa.doctor.ui.clinic.scheduleOf
import shifa.doctor.ui.clinic.staffColor
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaListSkeleton
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.tr

/**
 * The doctor's clinic roster for the Calendar's "Staff calendars" (CAL-10 A, flow 04/14 §3 A): the selected clinic, its
 * doctors and owners by name ([roster]), the signed-in doctor ([ownId], always shown) and the colleagues shown too.
 */
data class StaffCalendars(
    val clinic: MyClinic,
    val roster: List<ClinicMember>,
    val ownId: Long,
    val visible: Set<Long> = emptySet(),
) {
    /** The colleagues shown below the doctor's own day, in roster order. */
    val colleagues: List<ClinicMember>
        get() = roster.filter { it.doctorProfileId != ownId && it.doctorProfileId in visible }

    /** Colleagues' times are in the clinic's zone (flow 04/14 §4). */
    val clock: PracticeClock get() = PracticeClock(clinic.timeZone)

    fun color(doctorId: Long?): Color = staffColor(roster.indexOfFirst { it.doctorProfileId == doctorId })

    fun scope(member: ClinicMember): CalendarScope? = clinic.scheduleOf(member)

    companion object {
        /** Remembered per doctor and clinic (Flutter's key). */
        fun key(
            ownId: Long,
            clinicId: Long,
        ) = "calendar_visible_staff_v1:$ownId:$clinicId"
    }
}

/** "Staff calendars" and a chip per doctor ("My schedule" first, always on), the board's carousel. */
@Composable
internal fun StaffChips(
    staff: StaffCalendars,
    onToggle: (Long) -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
    Text(
        tr(Res.string.calendarStaffCalendars),
        style = ShifaTextStyles.legend().copy(fontWeight = FontWeight.Bold),
        color = ShifaColors.Text.Secondary,
    )
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        val own = staff.roster.firstOrNull { it.doctorProfileId == staff.ownId }
        (listOfNotNull(own) + staff.roster.filter { it != own }).forEach { m ->
            val id = m.doctorProfileId ?: return@forEach
            val on = id == staff.ownId || id in staff.visible
            StaffChip(
                if (id == staff.ownId) tr(Res.string.mySchedule) else memberLabel(m),
                staff.color(id),
                on,
            ) { onToggle(id) }
        }
    }
}

@Composable
private fun StaffChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) = Row(
    Modifier
        .clip(ShifaRadius.Full)
        .background(if (selected) color.copy(alpha = TINT) else ShifaColors.Bg.Surface)
        .border(
            ShifaDimens.InteractiveChipBorder,
            if (selected) color else ShifaColors.Border.Default,
            ShifaRadius.Full,
        )
        .selectable(selected, role = Role.Checkbox, onClick = onClick)
        .padding(start = ShifaSize.S4, end = ShifaSize.S12, top = ShifaSize.S4, bottom = ShifaSize.S4),
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    verticalAlignment = Alignment.CenterVertically,
) {
    Box(
        Modifier.size(ShifaSize.S20).background(if (selected) color else ShifaColors.Border.Default, ShifaRadius.Full),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(painterResource(Res.drawable.ic_check_12), null, tint = ShifaColors.Icon.Inverse)
    }
    Text(label, style = ShifaTextStyles.chipFilter(), color = ShifaColors.Text.Primary, maxLines = 1)
}

/** A section's head when more than one calendar shows: the doctor's colour dot and name in it (flow 04/14 §3.3). */
@Composable
internal fun StaffHeader(
    name: String,
    color: Color,
) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
    Box(Modifier.size(ShifaSize.S10).background(color, ShifaRadius.Full))
    Text(
        name,
        style = ShifaTextStyles.buttonCompact(),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** A colleague's day under the doctor's own: their rows in the clinic's zone, or "Couldn't load this day" + Retry. */
@Composable
internal fun StaffSection(
    member: ClinicMember,
    state: CalendarUiState,
    actions: CalendarActions,
) = Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
    val staff = state.staff ?: return@Column
    val id = member.doctorProfileId ?: return@Column
    StaffHeader(memberLabel(member), staff.color(id))
    val day = state.cache.days[DayKey(state.selected, id)]
    val failed = DayKey(state.selected, id) in state.staffFailed
    when {
        day == null && failed -> {
            Text(tr(Res.string.couldNotLoadThisDay), style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Error)
            ShifaLink(tr(Res.string.retry), { actions.retryStaff(id) })
        }
        day == null -> ShifaListSkeleton(count = 2)
        else -> {
            val rows = day.filter(state.filter::shows)
            if (rows.isEmpty()) {
                Text(
                    tr(Res.string.noItemsForThisDay),
                    Modifier.fillMaxWidth().background(
                        ShifaColors.Bg.Surface,
                        ShifaRadius.ControlMd,
                    ).padding(ShifaSpacing.Inset.Lg),
                    style = ShifaTextStyles.dialogBody(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            val scope = staff.scope(member)
            rows.forEach { entry: CalendarEntry ->
                AgendaRow(entry, staff.clock, state.details) { scope?.let { actions.openColleague(entry, it) } }
            }
        }
    }
}

@Composable
private fun memberLabel(m: ClinicMember): String =
    m.displayName?.takeIf { it.isNotBlank() } ?: tr(
        Res.string.clinicMemberFallback,
        m.doctorProfileId?.toString() ?: "—",
    )

private const val TINT = 0.12f
