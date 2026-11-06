package shifa.doctor.ui.clinic

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.model.ClinicInvitation
import shifa.doctor.data.model.ClinicMember
import shifa.doctor.data.model.MembershipRole
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.model.mayEditRevenueShare
import shifa.doctor.data.model.mayManageFinanceSettings
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.all
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.clinicDefaultShareClear
import shifa.doctor.generated.resources.clinicDefaultShareCleared
import shifa.doctor.generated.resources.clinicDoctorOpenSchedule
import shifa.doctor.generated.resources.clinicDoctorRevenueShareClear
import shifa.doctor.generated.resources.clinicDoctorRevenueShareDialogTitle
import shifa.doctor.generated.resources.clinicDoctorRevenueShareInvalid
import shifa.doctor.generated.resources.clinicDoctorRevenueShareNotSet
import shifa.doctor.generated.resources.clinicDoctorRevenueShareSave
import shifa.doctor.generated.resources.clinicDoctorRevenueShareSummary
import shifa.doctor.generated.resources.clinicDoctorsRevenueShareHint
import shifa.doctor.generated.resources.clinicDoctorsSearchHint
import shifa.doctor.generated.resources.clinicFinanceConfigureDefaultShare
import shifa.doctor.generated.resources.clinicFinanceDefaultRevenueShare
import shifa.doctor.generated.resources.clinicFinanceDefaultRevenueShareHint
import shifa.doctor.generated.resources.clinicInvitationsFailed
import shifa.doctor.generated.resources.clinicInviteConsumed
import shifa.doctor.generated.resources.clinicInviteCreateTitle
import shifa.doctor.generated.resources.clinicInviteDialogTitle
import shifa.doctor.generated.resources.clinicInviteDuplicate
import shifa.doctor.generated.resources.clinicInviteEmailLabel
import shifa.doctor.generated.resources.clinicInviteEmpty
import shifa.doctor.generated.resources.clinicInviteExpiresOn
import shifa.doctor.generated.resources.clinicInviteFailed
import shifa.doctor.generated.resources.clinicInviteGroupDone
import shifa.doctor.generated.resources.clinicInviteGroupPending
import shifa.doctor.generated.resources.clinicInviteInviteSent
import shifa.doctor.generated.resources.clinicInvitePending
import shifa.doctor.generated.resources.clinicInviteRevokeBody
import shifa.doctor.generated.resources.clinicInviteRevokeFailed
import shifa.doctor.generated.resources.clinicInviteRevokeTitle
import shifa.doctor.generated.resources.clinicInviteRevoked
import shifa.doctor.generated.resources.clinicInviteRoleLine
import shifa.doctor.generated.resources.clinicInviteSend
import shifa.doctor.generated.resources.clinicInviteSheetHint
import shifa.doctor.generated.resources.clinicMemberFallback
import shifa.doctor.generated.resources.clinicMembersFailed
import shifa.doctor.generated.resources.clinicShareDefaultLine
import shifa.doctor.generated.resources.clinicShareFailed
import shifa.doctor.generated.resources.clinicSharePercentLabel
import shifa.doctor.generated.resources.clinicShareSaved
import shifa.doctor.generated.resources.clinicShareUsesDefault
import shifa.doctor.generated.resources.clinicTableNoFilteredResults
import shifa.doctor.generated.resources.clinicWorkspaceNoDoctors
import shifa.doctor.generated.resources.enterValidEmail
import shifa.doctor.generated.resources.expired
import shifa.doctor.generated.resources.ic_calendar_18
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.revoke
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaButtonSize
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSlider
import shifa.doctor.ui.widgets.ShifaTertiaryButton
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import kotlin.math.roundToInt

/** The revenue-share sheet: one [member]'s own share, or (null) the clinic default (flow 09/03; one sheet for both). */
data class ShareSheet(
    val member: ClinicMember?,
    val text: String,
    // What applies while the field is empty: the clinic default for a doctor without their own share.
    val inherited: Int? = null,
    val busy: Boolean = false,
    val error: CodeBanner? = null,
) {
    // A whole number 0–100 as typed; "-5", "10.5" or "abc" are kept as typed and refused (flow 09/03 §8–9).
    val percent: Int? get() = text.trim().takeIf { DIGITS.matches(it) }?.toIntOrNull()?.takeIf { it in 0..100 }
    val valid: Boolean get() = percent != null

    // An empty field is "nothing typed yet", not an error; clearing has its own action.
    val showError: Boolean get() = text.isNotBlank() && !valid

    // The split the sheet shows: what's typed, else what applies (a doctor on the clinic default sees 60/40).
    val preview: Int? get() = percent ?: inherited.takeIf { text.isBlank() }

    private companion object {
        val DIGITS = Regex("""\d{1,3}""")
    }
}

data class InviteSheet(
    val email: String = "",
    val tried: Boolean = false,
    val busy: Boolean = false,
    val error: CodeBanner? = null,
)

data class ClinicPeopleUiState(
    val members: List<ClinicMember>? = null,
    val membersError: CodeBanner? = null,
    val query: String = "",
    val role: MembershipRole? = null,
    val invitations: List<ClinicInvitation>? = null,
    val invitationsError: CodeBanner? = null,
    val share: ShareSheet? = null,
    val invite: InviteSheet? = null,
    val revoking: ClinicInvitation? = null,
    val revokeBusy: Boolean = false,
    val banner: CodeBanner? = null,
) {
    /** The roles in the list, for the chips (built from the data, 09 §2). */
    val roles: List<MembershipRole> get() = members.orEmpty().map { it.role }.distinct().sortedBy { it.ordinal }

    /** Search by name, role or profile number, the role chip, then by name (flow 09/02 §3). */
    fun shown(roleLabel: (MembershipRole) -> String): List<ClinicMember> {
        val q = query.trim().lowercase()
        return members.orEmpty()
            .filter { role == null || it.role == role }
            .filter {
                q.isEmpty() ||
                    listOf(it.displayName.orEmpty(), roleLabel(it.role), it.doctorProfileId?.toString().orEmpty())
                        .any { f -> f.lowercase().contains(q) }
            }.sortedBy { it.displayName.orEmpty().lowercase() }
    }
}

/**
 * The People sections (CLINIC-05 and CLINIC-08, 09 §2–3.8, flows 09/02, 09/03, 09/19, board `DaClinic`): the clinic's
 * members with their revenue share and its sheet, and the receptionist invitations. [clinicId] is fixed: switching
 * clinics makes a new ViewModel.
 */
class ClinicPeopleViewModel(
    private val clinicId: Long,
    private val repo: ClinicsRepository = clinicsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ClinicPeopleUiState())
    val state: StateFlow<ClinicPeopleUiState> = _state.asStateFlow()

    fun loadMembers() {
        _state.update { it.copy(membersError = null) }
        viewModelScope.launch {
            repo.members(clinicId).fold(
                { list -> _state.update { it.copy(members = list) } },
                { e -> _state.update { it.copy(membersError = failure(e, Res.string.clinicMembersFailed)) } },
            )
        }
    }

    fun loadInvitations() {
        _state.update { it.copy(invitationsError = null) }
        viewModelScope.launch {
            repo.invitations(clinicId).fold(
                { list -> _state.update { it.copy(invitations = list) } },
                { e -> _state.update { it.copy(invitationsError = failure(e, Res.string.clinicInvitationsFailed)) } },
            )
        }
    }

    fun query(text: String) = _state.update { it.copy(query = text) }

    fun role(role: MembershipRole?) = _state.update { it.copy(role = role) }

    /** The share sheet for [member], or the clinic default (null), starting from the share in force there. */
    fun openShare(
        member: ClinicMember?,
        clinicDefault: Int?,
    ) = _state.update {
        it.copy(
            share =
                ShareSheet(
                    member,
                    (if (member != null) member.doctorRevenueSharePercent else clinicDefault)?.toString().orEmpty(),
                    inherited = clinicDefault.takeIf { member != null },
                ),
        )
    }

    fun shareText(text: String) =
        _state.update {
                s ->
            s.copy(share = s.share?.copy(text = text.take(MAX_CHARS), error = null))
        }

    fun closeShare() = _state.update { if (it.share?.busy == true) it else it.copy(share = null) }

    /** Save (the typed share), or [clear] (the doctor back to the clinic default / no clinic default). */
    fun saveShare(clear: Boolean = false) {
        val sheet = _state.value.share ?: return
        if (sheet.busy || (!clear && !sheet.valid)) return
        val percent = if (clear) null else sheet.percent
        _state.update { it.copy(share = sheet.copy(busy = true, error = null)) }
        viewModelScope.launch {
            val member = sheet.member
            val result =
                if (member?.doctorProfileId != null) {
                    repo.updateMemberShare(clinicId, member.doctorProfileId, percent)
                } else {
                    repo.updateDefaultShare(clinicId, percent)
                }
            result.fold(
                {
                    val done =
                        when {
                            !clear -> Res.string.clinicShareSaved
                            member != null -> Res.string.clinicShareUsesDefault
                            else -> Res.string.clinicDefaultShareCleared
                        }
                    _state.update { it.copy(share = null, banner = CodeBanner(done, error = false)) }
                    loadMembers()
                    // The default shows in Settings and in every doctor's share in force.
                    if (member == null) repo.mine()
                },
                {
                        e ->
                    _state.update {
                        it.copy(
                            share = sheet.copy(busy = false, error = failure(e, Res.string.clinicShareFailed)),
                        )
                    }
                },
            )
        }
    }

    fun openInvite() = _state.update { it.copy(invite = InviteSheet()) }

    fun inviteEmail(email: String) = _state.update { s -> s.copy(invite = s.invite?.copy(email = email, error = null)) }

    fun closeInvite() = _state.update { if (it.invite?.busy == true) it else it.copy(invite = null) }

    /** Send (a valid email, not already pending): the list reads again. */
    fun sendInvite() {
        val sheet = _state.value.invite ?: return
        if (sheet.busy) return
        if (!EMAIL.matches(sheet.email.trim()) || pendingTo(sheet.email)) {
            return _state.update { it.copy(invite = sheet.copy(tried = true)) }
        }
        _state.update { it.copy(invite = sheet.copy(busy = true, tried = true, error = null)) }
        viewModelScope.launch {
            repo.invite(clinicId, sheet.email).fold(
                {
                    _state.update {
                        it.copy(
                            invite = null,
                            banner = CodeBanner(Res.string.clinicInviteInviteSent, error = false),
                        )
                    }
                    loadInvitations()
                },
                {
                        e ->
                    _state.update {
                        it.copy(
                            invite = sheet.copy(busy = false, error = failure(e, Res.string.clinicInviteFailed)),
                        )
                    }
                },
            )
        }
    }

    fun askRevoke(invitation: ClinicInvitation?) =
        _state.update {
            if (it.revokeBusy) it else it.copy(revoking = invitation)
        }

    fun revoke() {
        val invitation = _state.value.revoking ?: return
        if (_state.value.revokeBusy) return
        _state.update { it.copy(revokeBusy = true) }
        viewModelScope.launch {
            val result = repo.revokeInvitation(clinicId, invitation.id)
            _state.update {
                it.copy(
                    revoking = null,
                    revokeBusy = false,
                    banner =
                        result.exceptionOrNull()?.let { e -> failure(e, Res.string.clinicInviteRevokeFailed) }
                            ?: CodeBanner(Res.string.clinicInviteRevoked, error = false),
                )
            }
            loadInvitations()
        }
    }

    fun dismiss() = _state.update { it.copy(banner = null) }

    /** An invitation to this address is still pending (the board's duplicate check). */
    fun pendingTo(email: String): Boolean =
        _state.value.invitations.orEmpty().any { it.pending && it.emailSentTo.equals(email.trim(), ignoreCase = true) }

    private fun failure(
        e: Throwable,
        fallback: org.jetbrains.compose.resources.StringResource,
    ) = CodeBanner(e.toUserMessage(fallback), requestId = e.requestId)

    companion object {
        private const val MAX_CHARS = 6
        val EMAIL = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")
    }
}

/** What the People content can do (the screen wires them to [ClinicPeopleViewModel]; previews leave them empty). */
class PeopleActions(
    val loadMembers: () -> Unit = {},
    val loadInvitations: () -> Unit = {},
    val query: (String) -> Unit = {},
    val role: (MembershipRole?) -> Unit = {},
    val openShare: (member: ClinicMember?, clinicDefault: Int?) -> Unit = { _, _ -> },
    val shareText: (String) -> Unit = {},
    val saveShare: (clear: Boolean) -> Unit = {},
    val closeShare: () -> Unit = {},
    val openInvite: () -> Unit = {},
    val closeInvite: () -> Unit = {},
    val inviteEmail: (String) -> Unit = {},
    val sendInvite: () -> Unit = {},
    val askRevoke: (ClinicInvitation?) -> Unit = {},
    val revoke: () -> Unit = {},
    val dismiss: () -> Unit = {},
)

private fun ClinicPeopleViewModel.actions() =
    PeopleActions(
        ::loadMembers, ::loadInvitations, ::query, ::role, ::openShare, ::shareText, { saveShare(it) }, ::closeShare,
        ::openInvite, ::closeInvite, ::inviteEmail, ::sendInvite, ::askRevoke, ::revoke, ::dismiss,
    )

@Composable
private fun rememberPeople(clinic: MyClinic): ClinicPeopleViewModel =
    viewModel(key = "clinic-people-${clinic.clinicId}") { ClinicPeopleViewModel(clinic.clinicId) }

/**
 * Doctors (flow 09/02, board `DaClinic`): who may set shares gets the hint and "Default share"; search, role chips
 * built from the data, one card per member (avatar, name, role, the share in force — a link when it can be edited —
 * and the schedule for doctors when [onSchedule] is given).
 */
@Composable
internal fun ColumnScope.DoctorsSection(
    clinic: MyClinic,
    onSchedule: ((ClinicMember) -> Unit)? = null,
    viewModel: ClinicPeopleViewModel = rememberPeople(clinic),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.loadMembers() }
    DoctorsContent(clinic, state, viewModel.actions(), onSchedule)
}

@Composable
internal fun ColumnScope.DoctorsContent(
    clinic: MyClinic,
    state: ClinicPeopleUiState,
    actions: PeopleActions,
    onSchedule: ((ClinicMember) -> Unit)?,
) {
    val canManage = clinic.mayManageFinanceSettings
    codeBanner(state.banner) { actions.dismiss() }?.let { it() }
    if (canManage) {
        Row(
            Modifier.fillMaxWidth().background(ShifaColors.Bg.InfoSubtle, ShifaRadius.ControlMd)
                .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S10),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        ) {
            Text(
                tr(Res.string.clinicDoctorsRevenueShareHint),
                Modifier.weight(1f),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Primary,
            )
            ShifaSecondaryButton(
                tr(Res.string.clinicFinanceConfigureDefaultShare),
                { actions.openShare(null, clinic.defaultDoctorRevenueSharePercent) },
                size = ShifaButtonSize.Compact,
            )
        }
    }
    val members = state.members
    // A failed read says so, also over a list kept from before (it may be out of date).
    state.membersError?.let { e ->
        codeBanner(e)?.let { it() }
        ShifaLink(tr(Res.string.retry), { actions.loadMembers() })
    }
    when {
        members == null && state.membersError != null -> Unit
        members == null ->
            repeat(
                SKELETON_ROWS,
            ) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S28)) }
        members.isEmpty() -> EmptyState(tr(Res.string.clinicWorkspaceNoDoctors), Res.drawable.ic_users_22)
        else -> {
            ShifaInputField(
                state.query,
                { actions.query(it) },
                tr(Res.string.clinicDoctorsSearchHint),
                Modifier.fillMaxWidth(),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            ) {
                ShifaChip(tr(Res.string.all), state.role == null, { actions.role(null) })
                state.roles.forEach { r -> ShifaChip(roleLabel(r), state.role == r, { actions.role(r) }) }
            }
            val labels = MembershipRole.entries.associateWith { roleLabel(it) }
            val shown = state.shown { labels.getValue(it) }
            if (shown.isEmpty()) {
                Text(
                    tr(Res.string.clinicTableNoFilteredResults),
                    style = ShifaTextStyles.bodyText(),
                    color = ShifaColors.Text.Secondary,
                )
            }
            shown.forEach { m ->
                MemberRow(m, canManage, onSchedule) { actions.openShare(m, clinic.defaultDoctorRevenueSharePercent) }
            }
        }
    }
    state.share?.let { ShareSheetView(it, clinic, actions) }
}

@Composable
private fun MemberRow(
    member: ClinicMember,
    canManage: Boolean,
    onSchedule: ((ClinicMember) -> Unit)?,
    editShare: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    ShifaAvatar(null, ShifaSize.S40)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        // An empty name reads "Doctor #12" (flow 09/02 §4; Flutter showed nothing).
        val name =
            member.displayName?.takeIf { it.isNotBlank() }
                ?: tr(Res.string.clinicMemberFallback, member.doctorProfileId?.toString() ?: "—")
        Text(name, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
        Text(roleLabel(member.role), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        // Only doctors and owners have a share (receptionists, nurses and admins never do).
        if (mayEditRevenueShare(member.role)) {
            val share = shareText(member.effectiveRevenueSharePercent)
            if (canManage && member.doctorProfileId != null) {
                ShifaLink(share, editShare)
            } else {
                Text(share, style = ShifaTextStyles.bodyText(), color = ShifaColors.Text.Secondary)
            }
        }
    }
    if (onSchedule != null && mayEditRevenueShare(member.role) && member.doctorProfileId != null) {
        ShifaIconButton(
            Res.drawable.ic_calendar_18,
            tr(Res.string.clinicDoctorOpenSchedule),
            { onSchedule(member) },
            variant = ShifaIconButtonVariant.Secondary,
        )
    }
}

@Composable
private fun shareText(percent: Int?): String =
    percent?.let { tr(Res.string.clinicDoctorRevenueShareSummary, it.toString(), (100 - it).toString()) }
        ?: tr(Res.string.clinicDoctorRevenueShareNotSet)

/**
 * The revenue-share sheet (flow 09/03, board `DaClinic` "share"): the doctor share as a number and a slider (the
 * clinic keeps the rest), "Use clinic default" for a doctor or "Clear default" for the clinic, Save. 0–100 only.
 */
@Composable
private fun ShareSheetView(
    sheet: ShareSheet,
    clinic: MyClinic,
    actions: PeopleActions,
) = ProfileSheet({ actions.closeShare() }, confirmHide = { !sheet.busy }) {
    val member = sheet.member
    ShifaDialogSheet(
        tr(
            if (member != null) {
                Res.string.clinicDoctorRevenueShareDialogTitle
            } else {
                Res.string.clinicFinanceDefaultRevenueShare
            },
        ),
        tr(Res.string.clinicDoctorRevenueShareSave),
        { actions.saveShare(false) },
        body =
            if (member != null) {
                val default =
                    clinic.defaultDoctorRevenueSharePercent?.let { "$it %" } ?: tr(
                        Res.string.clinicDoctorRevenueShareNotSet,
                    )
                "${member.displayName.orEmpty()} · ${tr(Res.string.clinicShareDefaultLine, default)}"
            } else {
                tr(Res.string.clinicFinanceDefaultRevenueShareHint)
            },
        cancelText = tr(Res.string.cancel),
        onCancel = { actions.closeShare() },
        confirmLoading = sheet.busy,
        confirmEnabled = sheet.valid,
    ) {
        ShifaInputField(
            sheet.text,
            { actions.shareText(it) },
            tr(Res.string.clinicSharePercentLabel),
            Modifier.fillMaxWidth(),
            supportingText = if (sheet.showError) tr(Res.string.clinicDoctorRevenueShareInvalid) else null,
            isError = sheet.showError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        val p = sheet.preview
        ShifaSlider(
            // The kit's slider is 0–100.
            (p ?: 0).toFloat(),
            { actions.shareText(it.roundToInt().toString()) },
            p?.let { "$it%" } ?: tr(Res.string.clinicDoctorRevenueShareNotSet),
            p?.let { "${100 - it}%" } ?: "—",
        )
        ShifaLink(
            tr(if (member != null) Res.string.clinicDoctorRevenueShareClear else Res.string.clinicDefaultShareClear),
            { actions.saveShare(true) },
            enabled = !sheet.busy,
        )
        sheet.error?.let { e -> codeBanner(e)?.let { it() } }
    }
}

/**
 * Invitations (flow 09/19, board `DaClinic`): "Invite receptionist", then Pending and Used / expired groups (email,
 * "Expires 24.10.2026 18:00" in the clinic's time zone, the status; Revoke while pending, after a confirmation).
 */
@Composable
internal fun ColumnScope.InvitationsSection(
    clinic: MyClinic,
    viewModel: ClinicPeopleViewModel = rememberPeople(clinic),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.loadInvitations() }
    InvitationsContent(clinic, state, viewModel.actions())
}

@Composable
internal fun ColumnScope.InvitationsContent(
    clinic: MyClinic,
    state: ClinicPeopleUiState,
    actions: PeopleActions,
) {
    codeBanner(state.banner) { actions.dismiss() }?.let { it() }
    ShifaPrimaryButton(tr(Res.string.clinicInviteCreateTitle), { actions.openInvite() }, Modifier.fillMaxWidth())
    val invitations = state.invitations
    val clock = PracticeClock(clinic.timeZone)
    state.invitationsError?.let { e ->
        codeBanner(e)?.let { it() }
        ShifaLink(tr(Res.string.retry), { actions.loadInvitations() })
    }
    when {
        invitations == null && state.invitationsError != null -> Unit
        invitations == null ->
            repeat(SKELETON_ROWS) {
                ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S24))
            }
        invitations.isEmpty() ->
            Text(
                tr(Res.string.clinicInviteEmpty),
                style = ShifaTextStyles.bodyText(),
                color = ShifaColors.Text.Secondary,
            )
        else -> {
            val (pending, done) = invitations.partition { it.pending && !expired(it, clock) }
            listOf(Res.string.clinicInviteGroupPending to pending, Res.string.clinicInviteGroupDone to done)
                .filter { it.second.isNotEmpty() }
                .forEach { (label, rows) ->
                    Text(tr(label).uppercase(), style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Secondary)
                    rows.forEach { InvitationRow(it, clock, actions) }
                }
        }
    }
    state.invite?.let { InviteSheetView(it, clinic, state, actions) }
    state.revoking?.let { inv ->
        ProfileSheet({ actions.askRevoke(null) }, confirmHide = { !state.revokeBusy }) {
            ShifaDialogSheet(
                tr(Res.string.clinicInviteRevokeTitle, inv.emailSentTo.orEmpty()),
                tr(Res.string.revoke),
                { actions.revoke() },
                body = tr(Res.string.clinicInviteRevokeBody),
                tone = DialogTone.Destructive,
                cancelText = tr(Res.string.cancel),
                onCancel = { actions.askRevoke(null) },
                confirmLoading = state.revokeBusy,
            )
        }
    }
}

@Composable
private fun InvitationRow(
    invitation: ClinicInvitation,
    clock: PracticeClock,
    actions: PeopleActions,
) {
    val isExpired = expired(invitation, clock)
    val live = invitation.pending && !isExpired
    Row(
        Modifier.fillMaxWidth().alpha(if (live) 1f else DONE_ALPHA)
            .background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
            .padding(horizontal = ShifaSize.S14, vertical = ShifaSize.S12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
            Text(invitation.emailSentTo ?: "—", style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
            val at = PracticeClock.parseInstant(invitation.expiresAt)?.let { clock.local(it) }
            // A used invitation's status says so; the backend sends no acceptance date.
            val sub =
                at?.takeIf {
                    !invitation.consumed
                }?.let { tr(Res.string.clinicInviteExpiresOn, "${displayDate(it.date)} ${displayTime(it.time)}") }
            sub?.let { Text(it, style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary) }
            Text(
                tr(
                    when {
                        invitation.consumed -> Res.string.clinicInviteConsumed
                        isExpired -> Res.string.expired
                        else -> Res.string.clinicInvitePending
                    },
                ),
                style = ShifaTextStyles.tableHead(),
                color = if (live) ShifaColors.Text.BrandStrong else ShifaColors.Text.Secondary,
            )
        }
        if (live) {
            ShifaTertiaryButton(tr(Res.string.revoke), {
                actions.askRevoke(invitation)
            }, destructive = true, size = ShifaButtonSize.Compact)
        }
    }
}

// Past its expiry and never used (the backend still lists it as pending).
private fun expired(
    invitation: ClinicInvitation,
    clock: PracticeClock,
): Boolean =
    !invitation.consumed &&
        (PracticeClock.parseInstant(invitation.expiresAt)?.let { it < clock.nowInstant() } ?: false)

@Composable
private fun InviteSheetView(
    sheet: InviteSheet,
    clinic: MyClinic,
    state: ClinicPeopleUiState,
    actions: PeopleActions,
) = ProfileSheet({ actions.closeInvite() }, confirmHide = { !sheet.busy }) {
    ShifaDialogSheet(
        tr(Res.string.clinicInviteDialogTitle),
        tr(Res.string.clinicInviteSend),
        { actions.sendInvite() },
        body = tr(Res.string.clinicInviteSheetHint, clinic.name),
        cancelText = tr(Res.string.cancel),
        onCancel = { actions.closeInvite() },
        confirmLoading = sheet.busy,
    ) {
        val bad = sheet.tried && !ClinicPeopleViewModel.EMAIL.matches(sheet.email.trim())
        val duplicate =
            state.invitations.orEmpty().any {
                it.pending && it.emailSentTo.equals(sheet.email.trim(), ignoreCase = true)
            }
        ShifaInputField(
            sheet.email,
            { actions.inviteEmail(it) },
            tr(Res.string.clinicInviteEmailLabel),
            Modifier.fillMaxWidth(),
            supportingText =
                when {
                    bad -> tr(Res.string.enterValidEmail)
                    duplicate -> tr(Res.string.clinicInviteDuplicate)
                    else -> null
                },
            isError = bad || duplicate,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Text(
            tr(Res.string.clinicInviteRoleLine),
            style = ShifaTextStyles.bodyText(),
            color = ShifaColors.Text.Secondary,
        )
        sheet.error?.let { e -> codeBanner(e)?.let { it() } }
    }
}

private const val SKELETON_ROWS = 3
private const val DONE_ALPHA = 0.7f
