package shifa.doctor.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.care.PartnershipAction
import shifa.doctor.data.model.CarePartnerHit
import shifa.doctor.data.model.CarePartnership
import shifa.doctor.data.model.CareProgress
import shifa.doctor.data.model.PartnershipStatus
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.acceptInvite
import shifa.doctor.generated.resources.all
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.cancelPartnerInvite
import shifa.doctor.generated.resources.careAcceptBody
import shifa.doctor.generated.resources.careAcceptTitle
import shifa.doctor.generated.resources.careAlreadyPartner
import shifa.doctor.generated.resources.careCancelBody
import shifa.doctor.generated.resources.careCancelTitle
import shifa.doctor.generated.resources.careCompleteBody
import shifa.doctor.generated.resources.careCompleteTitle
import shifa.doctor.generated.resources.careComposerHint
import shifa.doctor.generated.resources.careDeclineBody
import shifa.doctor.generated.resources.careDeclineTitle
import shifa.doctor.generated.resources.careDoctorFallback
import shifa.doctor.generated.resources.careDoctorName
import shifa.doctor.generated.resources.careEmptyHint
import shifa.doctor.generated.resources.careFilterActive
import shifa.doctor.generated.resources.careFilterFinished
import shifa.doctor.generated.resources.careFilterNeedsAnswer
import shifa.doctor.generated.resources.careFilterSent
import shifa.doctor.generated.resources.careFilteredEmpty
import shifa.doctor.generated.resources.careForPatient
import shifa.doctor.generated.resources.careGone
import shifa.doctor.generated.resources.careHintInvited
import shifa.doctor.generated.resources.careHintWaiting
import shifa.doctor.generated.resources.careInviteName
import shifa.doctor.generated.resources.careInvitedBy
import shifa.doctor.generated.resources.careNoDoctors
import shifa.doctor.generated.resources.careNoDoctorsHint
import shifa.doctor.generated.resources.careNoUpdates
import shifa.doctor.generated.resources.careNoUpdatesHint
import shifa.doctor.generated.resources.careOpenPatient
import shifa.doctor.generated.resources.carePartnerRole
import shifa.doctor.generated.resources.carePartnershipDetail
import shifa.doctor.generated.resources.carePartnerships
import shifa.doctor.generated.resources.carePendingNeedsAnswer
import shifa.doctor.generated.resources.carePendingWaiting
import shifa.doctor.generated.resources.careSendUpdate
import shifa.doctor.generated.resources.careShifaDoctor
import shifa.doctor.generated.resources.careShowAll
import shifa.doctor.generated.resources.careSpecialty
import shifa.doctor.generated.resources.careSpecialtyLine
import shifa.doctor.generated.resources.careSpecialtyRequested
import shifa.doctor.generated.resources.careStatusActive
import shifa.doctor.generated.resources.careStatusCancelled
import shifa.doctor.generated.resources.careStatusCompleted
import shifa.doctor.generated.resources.careStatusDeclined
import shifa.doctor.generated.resources.careStatusPending
import shifa.doctor.generated.resources.careUpdatesFailed
import shifa.doctor.generated.resources.careWith
import shifa.doctor.generated.resources.completePartnership
import shifa.doctor.generated.resources.declineInvite
import shifa.doctor.generated.resources.findPartnerForPatient
import shifa.doctor.generated.resources.findTherapyPartner
import shifa.doctor.generated.resources.ic_check_16
import shifa.doctor.generated.resources.ic_chev_r_16
import shifa.doctor.generated.resources.ic_handshake_18
import shifa.doctor.generated.resources.ic_send_18
import shifa.doctor.generated.resources.ic_users_22
import shifa.doctor.generated.resources.noCarePartnerships
import shifa.doctor.generated.resources.partnerInviteMessage
import shifa.doctor.generated.resources.partnershipProgress
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.retry
import shifa.doctor.generated.resources.sendPartnerInvite
import shifa.doctor.generated.resources.unknown
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.ShellActions
import shifa.doctor.ui.navigation.ShellRoute
import shifa.doctor.ui.profile.DiscardChangesSheet
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ActionBar
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.EmptyState
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaChip
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSkeleton
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.StatusBadge
import shifa.doctor.ui.widgets.StatusTone
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.currentInstant
import shifa.doctor.utils.displayDate
import shifa.doctor.utils.tr

private fun opened() = currentInstant().toEpochMilliseconds()

private fun myDoctorId(): Long? = profileRepository.me.value?.profile?.id

/** A backend time as "06.11.2026, 14:05" in the practice zone (Flutter cut the raw string). */
private fun whenText(raw: String?): String? {
    val at = raw?.let(PracticeClock::parseInstant) ?: return null
    val local = PracticeClock(profileRepository.me.value?.profile?.timeZone).local(at)
    return "${displayDate(
        local.date,
    )}, ${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}

private fun dayText(raw: String?): String? =
    raw?.let(PracticeClock::parseInstant)?.let {
        displayDate(PracticeClock(profileRepository.me.value?.profile?.timeZone).local(it).date)
    }

// ===================== Find therapy partner =====================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FindPartnerScreen(
    patientId: Long,
    patientName: String,
    opened: Long,
    actions: ShellActions,
) {
    val vm =
        viewModel(key = "find-partner-$patientId-$opened") {
            FindPartnerViewModel(patientId, { profileRepository.me.value?.profile?.locationCountry })
        }
    val state by vm.state.collectAsStateWithLifecycle()
    // Sent: the partnership's detail replaces this screen (back goes to the patient).
    LaunchedEffect(state.sent) {
        state.sent?.let {
            actions.back()
            actions.push(ShellRoute.Partnership(it.id, opened()))
        }
    }
    val back = { if (vm.back()) actions.back() }
    BackHandler(onBack = back)
    FindPartnerContent(
        state,
        patientName,
        FindPartnerActions(
            back, vm::query, vm::specialty, vm::search, vm::pick, vm::reason, vm::message, vm::send, vm::dismiss,
        ),
    )
    if (state.askDiscard) DiscardChangesSheet(onDiscard = actions.back, onKeep = vm::keep)
}

class FindPartnerActions(
    val back: () -> Unit = {},
    val query: (String) -> Unit = {},
    val specialty: (String) -> Unit = {},
    val retry: () -> Unit = {},
    val pick: (CarePartnerHit) -> Unit = {},
    val reason: (String) -> Unit = {},
    val message: (String) -> Unit = {},
    val send: () -> Unit = {},
    val dismiss: () -> Unit = {},
)

/** Board `DaPatients` partnerFind (phone): context, name and specialty, the doctors, and the invite bar once one is picked. */
@Composable
fun FindPartnerContent(
    state: FindPartnerUiState,
    patientName: String,
    actions: FindPartnerActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).imePadding()) {
    ShifaSubHeader(tr(Res.string.findTherapyPartner), Modifier.statusBarsPadding(), onBack = actions.back)
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).wrapContentWidth()
            .widthIn(max = ShifaDimens.Layout.FormWide).padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
    ) {
        Text(
            tr(Res.string.findPartnerForPatient, patientName),
            style = ShifaTextStyles.caption(),
            color = ShifaColors.Text.Secondary,
        )
        ShifaInputField(state.query, actions.query, tr(Res.string.careDoctorName), Modifier.fillMaxWidth())
        ShifaInputField(state.specialty, actions.specialty, tr(Res.string.careSpecialty), Modifier.fillMaxWidth())
        codeBanner(state.banner) { actions.dismiss() }?.invoke()
        val hits = state.hits
        when {
            state.error != null -> {
                codeBanner(state.error)?.invoke()
                ShifaLink(tr(Res.string.retry), actions.retry)
            }
            hits == null ->
                repeat(
                    SKELETONS,
                ) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S28)) }
            hits.isEmpty() && !state.searching ->
                EmptyState(
                    tr(Res.string.careNoDoctors),
                    Res.drawable.ic_users_22,
                    body = tr(Res.string.careNoDoctorsHint),
                )
            else ->
                hits.forEach {
                        h ->
                    DoctorRow(h, state.selected?.id == h.id, h.id in state.partners) { actions.pick(h) }
                }
        }
    }
    state.selected?.let { sel ->
        ActionBar(state = tr(Res.string.careInviteName, sel.name)) { m ->
            Column(m, verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Sm)) {
                ShifaInputField(
                    state.reason,
                    actions.reason,
                    tr(Res.string.careSpecialtyRequested),
                    Modifier.fillMaxWidth(),
                    enabled = !state.sending,
                )
                ShifaInputField(
                    state.message,
                    actions.message,
                    tr(Res.string.partnerInviteMessage),
                    Modifier.fillMaxWidth(),
                    enabled = !state.sending,
                    multiline = true,
                    areaMinHeight = ShifaSize.S56,
                )
                ShifaPrimaryButton(
                    tr(Res.string.sendPartnerInvite),
                    actions.send,
                    Modifier.fillMaxWidth(),
                    loading = state.sending,
                )
            }
        }
    }
}

@Composable
private fun DoctorRow(
    hit: CarePartnerHit,
    selected: Boolean,
    already: Boolean,
    onClick: () -> Unit,
) = Row(
    Modifier
        .fillMaxWidth()
        .background(if (selected) ShifaColors.Bg.SuccessSubtle else ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .then(
            if (selected) Modifier.border(ShifaSize.S2, ShifaColors.Border.Focus, ShifaRadius.ControlLg) else Modifier,
        )
        .pressable(onClick)
        .padding(ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    ShifaAvatar(hit.avatarUrl, ShifaSize.S40)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            hit.name.ifEmpty {
                tr(Res.string.careDoctorFallback)
            },
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Primary,
        )
        Text(
            hit.subtitle.ifEmpty { tr(Res.string.careShifaDoctor) },
            style = ShifaTextStyles.caption(),
            color = ShifaColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (already) StatusBadge(tr(Res.string.careAlreadyPartner), tone = StatusTone.Info)
    }
    Icon(
        painterResource(if (selected) Res.drawable.ic_check_16 else Res.drawable.ic_chev_r_16),
        null,
        tint = if (selected) ShifaColors.Icon.Brand else ShifaColors.Text.Secondary,
    )
}

// ===================== Care partnerships list =====================

@Composable
fun PartnershipsScreen(
    patientId: Long?,
    patientName: String?,
    opened: Long,
    actions: ShellActions,
) {
    val vm = viewModel(key = "partnerships-$patientId-$opened") { PartnershipsViewModel(patientId, ::myDoctorId) }
    val state by vm.state.collectAsStateWithLifecycle()
    // Back from a partnership (accepted, cancelled…): read again.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }
    PartnershipsContent(
        state,
        vm.rows(state),
        patientName.takeIf { patientId != null && !state.showAll },
        myDoctorId(),
        PartnershipsActions(
            back = actions.back,
            refresh = { vm.load(refresh = true) },
            filter = vm::filter,
            showAll = vm::showAll,
            open = { actions.push(ShellRoute.Partnership(it.id, opened())) },
            find =
                patientId?.let { id ->
                    { actions.push(ShellRoute.FindPartner(id, patientName.orEmpty(), opened())) }
                },
        ),
    )
}

class PartnershipsActions(
    val back: () -> Unit = {},
    val refresh: () -> Unit = {},
    val filter: (PartnershipFilter) -> Unit = {},
    val showAll: () -> Unit = {},
    val open: (CarePartnership) -> Unit = {},
    val find: (() -> Unit)? = null,
)

private fun filterLabel(f: PartnershipFilter): StringResource =
    when (f) {
        PartnershipFilter.All -> Res.string.all
        PartnershipFilter.NeedsAnswer -> Res.string.careFilterNeedsAnswer
        PartnershipFilter.Active -> Res.string.careFilterActive
        PartnershipFilter.Sent -> Res.string.careFilterSent
        PartnershipFilter.Finished -> Res.string.careFilterFinished
    }

/** The status chip: a pending one says whose answer it waits for (the board's). */
@Composable
private fun statusChip(
    p: CarePartnership,
    me: Long?,
): Pair<String, StatusTone> =
    when (p.status) {
        PartnershipStatus.Pending ->
            when (p.iInitiated(me)) {
                false -> tr(Res.string.carePendingNeedsAnswer) to StatusTone.Warn
                true ->
                    tr(Res.string.carePendingWaiting, p.otherName(me) ?: tr(Res.string.careDoctorFallback)) to
                        StatusTone.Info
                // The side unknown: nothing guessed (flow 06/13 §8).
                null -> tr(Res.string.careStatusPending) to StatusTone.Info
            }
        PartnershipStatus.Active -> tr(Res.string.careStatusActive) to StatusTone.Brand
        PartnershipStatus.Declined -> tr(Res.string.careStatusDeclined) to StatusTone.Muted
        PartnershipStatus.Cancelled -> tr(Res.string.careStatusCancelled) to StatusTone.Muted
        PartnershipStatus.Completed -> tr(Res.string.careStatusCompleted) to StatusTone.Muted
        PartnershipStatus.Unknown -> tr(Res.string.unknown) to StatusTone.Muted
    }

/** Board `DaPatients` partnerships (phone): the patient filter, chips, and a row per partnership. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnershipsContent(
    state: PartnershipsUiState,
    rows: List<CarePartnership>?,
    // Set while the list shows only this patient's.
    patientName: String?,
    me: Long?,
    actions: PartnershipsActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
    ShifaSubHeader(tr(Res.string.carePartnerships), Modifier.statusBarsPadding(), onBack = actions.back)
    PullToRefreshBox(state.refreshing, actions.refresh, Modifier.weight(1f)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        ) {
            patientName?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                ) {
                    Text(
                        tr(Res.string.careForPatient, it),
                        Modifier.weight(1f),
                        style = ShifaTextStyles.dialogBody(),
                        color = ShifaColors.Text.Primary,
                    )
                    ShifaLink(tr(Res.string.careShowAll), actions.showAll)
                }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S6),
            ) {
                PartnershipFilter.entries.forEach { f ->
                    ShifaChip(tr(filterLabel(f)), state.filter == f, { actions.filter(f) })
                }
            }
            when {
                state.error != null && state.all == null -> {
                    codeBanner(state.error)?.invoke()
                    ShifaLink(tr(Res.string.retry), actions.refresh)
                }
                rows == null ->
                    repeat(
                        SKELETONS,
                    ) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S28)) }
                state.all.isNullOrEmpty() || (rows.isEmpty() && state.filter == PartnershipFilter.All) ->
                    EmptyState(
                        tr(Res.string.noCarePartnerships),
                        Res.drawable.ic_users_22,
                        body = tr(Res.string.careEmptyHint),
                        action =
                            actions.find?.let { go ->
                                {
                                    ShifaPrimaryButton(
                                        tr(Res.string.findTherapyPartner),
                                        go,
                                        icon = Res.drawable.ic_handshake_18,
                                    )
                                }
                            },
                    )
                rows.isEmpty() -> EmptyState(tr(Res.string.careFilteredEmpty), Res.drawable.ic_users_22)
                else -> {
                    state.error?.let { codeBanner(it)?.invoke() }
                    rows.forEach { p -> PartnershipRow(p, me) { actions.open(p) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PartnershipRow(
    p: CarePartnership,
    me: Long?,
    onClick: () -> Unit,
) = Row(
    Modifier.fillMaxWidth().background(
        ShifaColors.Bg.Surface,
        ShifaRadius.ControlLg,
    ).pressable(onClick).padding(ShifaSize.S12),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S12),
) {
    Box {
        ShifaAvatar(null, ShifaSize.S40)
        if (p.needsMyAnswer(me)) {
            Box(
                Modifier.align(
                    Alignment.TopEnd,
                ).size(ShifaSize.S10).background(ShifaColors.Text.Warning, ShifaRadius.Full),
            )
        }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S2)) {
        Text(
            p.patientName?.takeIf(String::isNotBlank) ?: tr(Res.string.patient),
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            listOfNotNull(
                tr(Res.string.careWith, p.otherName(me) ?: tr(Res.string.careDoctorFallback)),
                p.specialtyRequested?.takeIf(String::isNotBlank),
            ).joinToString(" · "),
            style = ShifaTextStyles.caption(),
            color = ShifaColors.Text.Secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The date goes under a long chip instead of being squeezed beside it.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val (label, tone) = statusChip(p, me)
            StatusBadge(label, tone = tone)
            dayText(p.updatedAt ?: p.createdAt)?.let {
                Text(it, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
            }
        }
    }
    Icon(painterResource(Res.drawable.ic_chev_r_16), null, tint = ShifaColors.Text.Secondary)
}

// ===================== Partnership detail =====================

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PartnershipScreen(
    id: Long,
    opened: Long,
    actions: ShellActions,
) {
    val vm = viewModel(key = "partnership-$id-$opened") { PartnershipViewModel(id) }
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }
    val back = { if (vm.back()) actions.back() }
    BackHandler(onBack = back)
    PartnershipContent(
        state,
        myDoctorId(),
        PartnershipActions(
            back = back,
            retry = vm::load,
            retryProgress = vm::loadProgress,
            ask = vm::ask,
            act = vm::act,
            draft = vm::draft,
            post = vm::post,
            dismiss = vm::dismiss,
            openPatient = { actions.push(ShellRoute.Patient(it)) },
        ),
    )
    if (state.askDiscard) DiscardChangesSheet(onDiscard = actions.back, onKeep = vm::keep)
}

class PartnershipActions(
    val back: () -> Unit = {},
    val retry: () -> Unit = {},
    val retryProgress: () -> Unit = {},
    val ask: (PartnershipAction?) -> Unit = {},
    val act: () -> Unit = {},
    val draft: (String) -> Unit = {},
    val post: () -> Unit = {},
    val dismiss: () -> Unit = {},
    val openPatient: (Long) -> Unit = {},
)

/** Board `DaPatients` partnerDetail (phone): the header card with its actions, the updates, the composer when active. */
@Composable
fun PartnershipContent(
    state: PartnershipUiState,
    me: Long?,
    actions: PartnershipActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).imePadding()) {
    ShifaSubHeader(tr(Res.string.carePartnershipDetail), Modifier.statusBarsPadding(), onBack = actions.back)
    val p = state.partnership
    Column(
        Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    ) {
        codeBanner(state.banner) { actions.dismiss() }?.invoke()
        when {
            state.notFound -> EmptyState(tr(Res.string.careGone), Res.drawable.ic_users_22)
            p == null && state.error != null -> {
                codeBanner(state.error)?.invoke()
                ShifaLink(tr(Res.string.retry), actions.retry)
            }
            p == null -> repeat(SKELETONS) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S28)) }
            else -> {
                HeaderCard(p, me, state.acting, actions)
                Text(
                    tr(Res.string.partnershipProgress),
                    style = ShifaTextStyles.panelTitle(),
                    color = ShifaColors.Text.Primary,
                )
                Updates(state, me, actions)
            }
        }
    }
    if (p?.status == PartnershipStatus.Active) {
        ActionBar { m ->
            Row(
                m,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
            ) {
                ShifaInputField(
                    state.draft,
                    actions.draft,
                    tr(Res.string.careComposerHint),
                    Modifier.weight(1f),
                    enabled = !state.posting,
                    clearable = false,
                )
                ShifaIconButton(
                    Res.drawable.ic_send_18,
                    tr(Res.string.careSendUpdate),
                    actions.post,
                    enabled = state.draft.isNotBlank() && !state.posting,
                )
            }
        }
    }
    state.confirm?.let { a -> p?.let { ConfirmAction(a, it, me, state.acting, actions) } }
}

@Composable
private fun HeaderCard(
    p: CarePartnership,
    me: Long?,
    acting: Boolean,
    actions: PartnershipActions,
) = Column(
    Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSpacing.Inset.Md),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S10),
) {
    val doctor = tr(Res.string.careDoctorFallback)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8)) {
        Text(
            p.patientName?.takeIf(String::isNotBlank) ?: tr(Res.string.patient),
            Modifier.weight(1f),
            style = ShifaTextStyles.panelTitle(),
            color = ShifaColors.Text.Primary,
        )
        ShifaLink(tr(Res.string.careOpenPatient), { actions.openPatient(p.patientId) })
    }
    val (label, tone) = statusChip(p, me)
    StatusBadge(label, tone = tone)
    Person(tr(Res.string.careInvitedBy), p.initiatingDoctorName ?: doctor)
    Person(tr(Res.string.carePartnerRole), p.partnerDoctorName ?: doctor)
    p.specialtyRequested?.takeIf(String::isNotBlank)?.let {
        Text(
            tr(Res.string.careSpecialtyLine, it),
            style = ShifaTextStyles.caption(),
            color = ShifaColors.Text.Secondary,
        )
    }
    p.message?.takeIf(String::isNotBlank)?.let {
        Text(
            "“${it.trim()}”",
            Modifier.fillMaxWidth().background(ShifaColors.Bg.Canvas, ShifaRadius.ControlMd).padding(ShifaSize.S10),
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Primary,
        )
    }
    val mine = p.iInitiated(me)
    if (p.status == PartnershipStatus.Pending && mine != null) {
        Text(
            if (mine) {
                tr(
                    Res.string.careHintWaiting,
                    p.partnerDoctorName ?: doctor,
                )
            } else {
                tr(Res.string.careHintInvited, p.initiatingDoctorName ?: doctor)
            },
            style = ShifaTextStyles.caption(),
            color = ShifaColors.Text.Secondary,
        )
    }
    // The actions by status and side (a doctor on neither side sees none).
    val buttons: List<Pair<PartnershipAction, StringResource>> =
        when {
            p.status == PartnershipStatus.Pending && mine == false ->
                listOf(
                    PartnershipAction.Accept to Res.string.acceptInvite,
                    PartnershipAction.Decline to Res.string.declineInvite,
                )
            p.status == PartnershipStatus.Pending && mine == true ->
                listOf(
                    PartnershipAction.Cancel to Res.string.cancelPartnerInvite,
                )
            p.status == PartnershipStatus.Active && mine != null ->
                listOf(
                    PartnershipAction.Complete to Res.string.completePartnership,
                )
            else -> emptyList()
        }
    buttons.forEach { (a, text) ->
        if (a == PartnershipAction.Accept) {
            ShifaPrimaryButton(tr(text), { actions.ask(a) }, Modifier.fillMaxWidth(), enabled = !acting)
        } else {
            ShifaSecondaryButton(
                tr(text),
                { actions.ask(a) },
                Modifier.fillMaxWidth(),
                enabled = !acting,
                destructive = a.destructive,
            )
        }
    }
}

@Composable
private fun Person(
    role: String,
    name: String,
) = Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
    ShifaAvatar(null, ShifaSize.S32)
    Column {
        Text(role, style = ShifaTextStyles.caption(), color = ShifaColors.Text.Secondary)
        Text(name, style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
    }
}

@Composable
private fun ColumnScope.Updates(
    state: PartnershipUiState,
    me: Long?,
    actions: PartnershipActions,
) {
    val list = state.progress
    // A failed refresh says so above what was read before.
    if (state.progressFailed && list != null) {
        Text(tr(Res.string.careUpdatesFailed), style = ShifaTextStyles.caption(), color = ShifaColors.Text.Error)
        ShifaLink(tr(Res.string.retry), actions.retryProgress)
    }
    when {
        state.progressFailed && list == null -> {
            Text(tr(Res.string.careUpdatesFailed), style = ShifaTextStyles.caption(), color = ShifaColors.Text.Error)
            ShifaLink(tr(Res.string.retry), actions.retryProgress)
        }
        list == null -> repeat(2) { ShifaSkeleton(Modifier.fillMaxWidth().padding(vertical = ShifaSize.S20)) }
        list.isEmpty() ->
            EmptyState(
                tr(Res.string.careNoUpdates),
                Res.drawable.ic_users_22,
                body = tr(Res.string.careNoUpdatesHint),
            )
        else -> list.forEach { u -> Update(u, u.authorDoctorId == me) }
    }
}

/** One update, chat-like: mine on the right in the brand tint. */
@Composable
private fun ColumnScope.Update(
    u: CareProgress,
    mine: Boolean,
) = Column(
    Modifier
        .align(if (mine) Alignment.End else Alignment.Start)
        .fillMaxWidth(BUBBLE_WIDTH)
        .background(if (mine) ShifaColors.Bg.SuccessSubtle else ShifaColors.Bg.Surface, ShifaRadius.ControlLg)
        .padding(ShifaSize.S12),
    verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
) {
    Text(
        listOfNotNull(
            u.authorDoctorName?.takeIf(String::isNotBlank) ?: tr(Res.string.careDoctorFallback),
            whenText(u.createdAt),
        )
            .joinToString(" · "),
        style = ShifaTextStyles.caption(),
        color = ShifaColors.Text.Secondary,
    )
    Text(u.body, style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Primary)
}

/** The question before an action (flow §7): Decline and Cancel are destructive. */
@Composable
private fun ConfirmAction(
    a: PartnershipAction,
    p: CarePartnership,
    me: Long?,
    acting: Boolean,
    actions: PartnershipActions,
) = ProfileSheet({ actions.ask(null) }, confirmHide = { !acting }) {
    val other = p.otherName(me) ?: tr(Res.string.careDoctorFallback)
    val patient = p.patientName?.takeIf(String::isNotBlank) ?: tr(Res.string.patient)
    val (title, body, button) =
        when (a) {
            PartnershipAction.Accept ->
                Triple(
                    Res.string.careAcceptTitle,
                    tr(Res.string.careAcceptBody, patient),
                    Res.string.acceptInvite,
                )
            PartnershipAction.Decline ->
                Triple(
                    Res.string.careDeclineTitle,
                    tr(Res.string.careDeclineBody, other),
                    Res.string.declineInvite,
                )
            PartnershipAction.Cancel ->
                Triple(
                    Res.string.careCancelTitle,
                    tr(Res.string.careCancelBody, other),
                    Res.string.cancelPartnerInvite,
                )
            PartnershipAction.Complete ->
                Triple(
                    Res.string.careCompleteTitle,
                    tr(Res.string.careCompleteBody),
                    Res.string.completePartnership,
                )
        }
    ShifaDialogSheet(
        tr(title),
        tr(button),
        actions.act,
        body = body,
        tone = if (a.destructive) DialogTone.Destructive else DialogTone.None,
        cancelText = tr(Res.string.cancel),
        onCancel = { actions.ask(null) },
        confirmLoading = acting,
    )
}

private val PartnershipAction.destructive get() = this == PartnershipAction.Decline || this == PartnershipAction.Cancel

private const val SKELETONS = 4
private const val BUBBLE_WIDTH = 0.85f
