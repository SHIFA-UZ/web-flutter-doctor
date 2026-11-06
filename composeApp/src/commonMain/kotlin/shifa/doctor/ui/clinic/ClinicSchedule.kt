package shifa.doctor.ui.clinic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import shifa.doctor.data.model.CalendarEntry
import shifa.doctor.data.model.ClinicMember
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.model.mayEditRevenueShare
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clinicCalendarIntro
import shifa.doctor.generated.resources.clinicMemberFallback
import shifa.doctor.generated.resources.clinicOpenSchedule
import shifa.doctor.generated.resources.clinicWorkspaceNoDoctors
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.generated.resources.retry
import shifa.doctor.ui.calendar.CalendarActions
import shifa.doctor.ui.calendar.CalendarContent
import shifa.doctor.ui.calendar.CalendarScope
import shifa.doctor.ui.calendar.CalendarViewModel
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.ShellActions
import shifa.doctor.ui.navigation.ShellRoute
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.utils.TimeZones
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.tr

/** The doctors who have a calendar (doctors and owners with a profile), by name: the roster of flow 04/14. */
internal fun List<ClinicMember>.calendars(): List<ClinicMember> =
    filter {
        it.doctorProfileId != null &&
            mayEditRevenueShare(
                it.role,
            )
    }.sortedBy { it.displayName.orEmpty().lowercase() }

/** A doctor's colour by their place in the roster (the board's `--chart-*` per doctor), repeating after four. */
internal fun staffColor(index: Int): Color =
    STAFF_COLORS[((index % STAFF_COLORS.size) + STAFF_COLORS.size) % STAFF_COLORS.size]

private val STAFF_COLORS =
    listOf(ShifaColors.Chart.Series1, ShifaColors.Chart.Series4, ShifaColors.Chart.Series3, ShifaColors.Chart.Series2)

/** A member's calendar in this clinic: the clinic's zone, its address as the venue (flow 04/14 §4). */
internal fun MyClinic.scheduleOf(member: ClinicMember): CalendarScope? =
    member.doctorProfileId?.let {
        CalendarScope(
            it,
            member.displayName?.takeIf(String::isNotBlank),
            timeZone,
            address?.takeIf(String::isNotBlank),
            clinicId,
        )
    }

/** The name a member's calendar is titled with: "Doctor #12" when the backend sent none. */
@Composable
internal fun memberName(member: ClinicMember): String =
    member.displayName?.takeIf {
        it.isNotBlank()
    } ?: tr(Res.string.clinicMemberFallback, member.doctorProfileId?.toString() ?: "—")

/**
 * The workspace's Calendar section (CLINIC-09, board `DaClinic` Calendar): "Book and manage each doctor's appointments.
 * Times are in Asia/Tashkent (UTC+5).", then one card per doctor (colour bar, name, role, "Open schedule") that opens
 * their calendar.
 */
@Composable
internal fun ColumnScope.ClinicCalendarSection(
    clinic: MyClinic,
    onOpen: (ClinicMember) -> Unit,
    viewModel: ClinicPeopleViewModel = rememberPeople(clinic),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.loadMembers() }
    ClinicCalendarContent(clinic, state, onOpen, viewModel::loadMembers)
}

@Composable
internal fun ColumnScope.ClinicCalendarContent(
    clinic: MyClinic,
    state: ClinicPeopleUiState,
    onOpen: (ClinicMember) -> Unit,
    retry: () -> Unit = {},
) {
    val zone = clinic.timeZone.orEmpty()
    val label = TimeZones.utcLabel(zone)?.let { "$zone ($it)" } ?: zone
    if (zone.isNotBlank()) {
        Text(
            tr(Res.string.clinicCalendarIntro, label),
            style = ShifaTextStyles.metaSmall(),
            color = ShifaColors.Text.Secondary,
        )
    }
    state.membersError?.let { e ->
        codeBanner(e)?.let { it() }
        ShifaLink(tr(Res.string.retry), retry)
    }
    val members = state.members
    when {
        members == null && state.membersError != null -> Unit
        members == null ->
            repeat(
                SKELETON_ROWS,
            ) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S24)) }
        members.calendars().isEmpty() -> EmptyState(tr(Res.string.clinicWorkspaceNoDoctors), Res.drawable.ic_users_22)
        else ->
            members.calendars().forEachIndexed { i, m ->
                Row(
                    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
                        .clickable(role = Role.Button) { onOpen(m) }
                        .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
                ) {
                    Box(Modifier.size(ShifaSize.S10, ShifaSize.S40).background(staffColor(i), ShifaRadius.Sm))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
                        Text(
                            memberName(m),
                            style = ShifaTextStyles.panelTitle(),
                            color = ShifaColors.Text.Primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(roleLabel(m.role), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
                    }
                    Text(
                        tr(Res.string.clinicOpenSchedule),
                        style = ShifaTextStyles.buttonCompact(),
                        color = ShifaColors.Text.BrandStrong,
                    )
                }
            }
    }
}

/**
 * A colleague's calendar (CAL-10 / CLINIC-09, flow 04/14 B, board `DaCalendar` calDoctor): the calendar screen titled
 * with the doctor's name, in the clinic's zone, without Block (blocks are created on one's own calendar), the filter
 * on appointments and free slots. Its days are cached under the doctor; booking, moving, cancelling and removing a
 * block act for them. Opening a patient or a visit pushes over it, so Back comes here again (Flutter re-created the
 * page from a serialized "return info").
 */
@Composable
fun ClinicDoctorScheduleScreen(
    scope: CalendarScope,
    actions: ShellActions,
    viewModel: CalendarViewModel =
        viewModel(key = "clinic-schedule-$scope") { CalendarViewModel(scope = scope) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Back from a booking or the app: the day again, within the throttle (a change already refreshed it).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load() }
    CalendarContent(
        state,
        CalendarActions(
            select = viewModel::select,
            month = viewModel::month,
            retry = { viewModel.load(force = true) },
            open = { entry ->
                val opened = currentInstant().toEpochMilliseconds()
                actions.push(
                    when (entry) {
                        is CalendarEntry.Appointment -> ShellRoute.Appointment(entry, opened, scope)
                        is CalendarEntry.FreeSlot -> ShellRoute.Booking(entry, opened, scope = scope)
                        is CalendarEntry.Blocked -> ShellRoute.Block(entry, opened, scope)
                    },
                )
            },
            back = actions.back,
            ui = viewModel::ui,
        ),
    )
}
