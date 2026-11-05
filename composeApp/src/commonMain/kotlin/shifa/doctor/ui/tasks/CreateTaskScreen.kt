package shifa.doctor.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.model.MAX_TIMES_PER_DAY
import shifa.doctor.data.model.PatientOption
import shifa.doctor.data.model.TaskCategory
import shifa.doctor.data.model.TaskInputType
import shifa.doctor.data.model.TaskTemplate
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.tasks.ScheduleMode
import shifa.doctor.data.tasks.TASK_DAYS_AHEAD
import shifa.doctor.data.tasks.TASK_INTERVALS
import shifa.doctor.data.tasks.TaskForm
import shifa.doctor.data.tasks.TaskFormProblem
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.category
import shifa.doctor.generated.resources.createRemoteCareTask
import shifa.doctor.generated.resources.customTimesAddSlot
import shifa.doctor.generated.resources.customTimesCount
import shifa.doctor.generated.resources.customTimesHint
import shifa.doctor.generated.resources.customTimesLabel
import shifa.doctor.generated.resources.description
import shifa.doctor.generated.resources.discard
import shifa.doctor.generated.resources.discardChangesBody
import shifa.doctor.generated.resources.discardTaskTitle
import shifa.doctor.generated.resources.done
import shifa.doctor.generated.resources.durationDays
import shifa.doctor.generated.resources.durationDaysInvalid
import shifa.doctor.generated.resources.endDate
import shifa.doctor.generated.resources.endDateBeforeStart
import shifa.doctor.generated.resources.enterInputLabel
import shifa.doctor.generated.resources.enterNotesLabel
import shifa.doctor.generated.resources.every1Hour
import shifa.doctor.generated.resources.everyNHours
import shifa.doctor.generated.resources.fromTemplate
import shifa.doctor.generated.resources.ic_clock_16
import shifa.doctor.generated.resources.ic_plus_16
import shifa.doctor.generated.resources.ic_search_16
import shifa.doctor.generated.resources.ic_trash_16
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.inputLabel
import shifa.doctor.generated.resources.inputType
import shifa.doctor.generated.resources.intervalAdjusted
import shifa.doctor.generated.resources.intervalBetweenTasks
import shifa.doctor.generated.resources.keepEditing
import shifa.doctor.generated.resources.notSet
import shifa.doctor.generated.resources.notesLabel
import shifa.doctor.generated.resources.notesRequired
import shifa.doctor.generated.resources.patient
import shifa.doctor.generated.resources.patientIdLabel
import shifa.doctor.generated.resources.pleaseEnterTaskName
import shifa.doctor.generated.resources.pleaseSelectPatient
import shifa.doctor.generated.resources.remove
import shifa.doctor.generated.resources.scheduleMode
import shifa.doctor.generated.resources.scheduleModeCustomTimes
import shifa.doctor.generated.resources.scheduleModeEvenSpacing
import shifa.doctor.generated.resources.selectEndDate
import shifa.doctor.generated.resources.selectPatient
import shifa.doctor.generated.resources.slotsPreviewClipped
import shifa.doctor.generated.resources.slotsPreviewLabel
import shifa.doctor.generated.resources.startDate
import shifa.doctor.generated.resources.startTime
import shifa.doctor.generated.resources.tapToSearch
import shifa.doctor.generated.resources.taskDescription
import shifa.doctor.generated.resources.taskName
import shifa.doctor.generated.resources.timesPerDay
import shifa.doctor.generated.resources.useEndDate
import shifa.doctor.ui.calendar.FieldLabel
import shifa.doctor.ui.calendar.MonthPicker
import shifa.doctor.ui.patients.PatientPickerSheet
import shifa.doctor.ui.patients.matching
import shifa.doctor.ui.profile.ProfileSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaDimens
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.PressVariant
import shifa.doctor.ui.widgets.SegmentedTabs
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaDropdown
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaSwitchRow
import shifa.doctor.ui.widgets.ShifaTertiaryButton
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.TabItem
import shifa.doctor.ui.widgets.pressable
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.displayTime
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

class CreateTaskActions(
    val back: () -> Unit = {},
    val edit: ((TaskForm) -> TaskForm) -> Unit = {},
    val ui: ((CreateTaskUiState) -> CreateTaskUiState) -> Unit = {},
    val mode: (ScheduleMode) -> Unit = {},
    val addSlot: () -> Unit = {},
    val setSlot: (Int, LocalTime) -> Unit = { _, _ -> },
    val removeSlot: (Int) -> Unit = {},
    val openDate: (TaskDateField?) -> Unit = {},
    val startDate: (LocalDate) -> Unit = {},
    val endDate: (LocalDate) -> Unit = {},
    val openPicker: () -> Unit = {},
    val pickPatient: (PatientOption) -> Unit = {},
    val retryPatients: () -> Unit = {},
    val create: () -> Unit = {},
    val discard: () -> Unit = {},
)

/**
 * "Create Remote Care Task" (TASK-04, flow 07/13, board `DaInbox` "taskCreate"): blank, from a template, or for a
 * patient. Leaving with changes asks first; done → back to the caller with the success banner.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CreateTaskScreen(
    opened: Long,
    template: TaskTemplate?,
    onClose: () -> Unit,
    patient: PatientOption? = null,
    // After a create: the caller may close more than the form (templates close too).
    onCreated: () -> Unit = onClose,
) {
    val me by profileRepository.me.collectAsStateWithLifecycle()
    val clock = remember(me?.profile?.timeZone) { PracticeClock(me?.profile?.timeZone) }
    val viewModel: CreateTaskViewModel =
        viewModel(key = "task-create-$opened") { CreateTaskViewModel(clock.today(), template, patient) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onCreated() }
    val back = { if (viewModel.back()) onClose() }
    BackHandler(onBack = back)
    CreateTaskContent(
        state,
        clock.today(),
        CreateTaskActions(
            back = back,
            edit = viewModel::edit,
            ui = viewModel::ui,
            mode = viewModel::mode,
            addSlot = viewModel::addSlot,
            setSlot = viewModel::setSlot,
            removeSlot = viewModel::removeSlot,
            openDate = viewModel::openDate,
            startDate = viewModel::startDate,
            endDate = viewModel::endDate,
            openPicker = viewModel::openPicker,
            pickPatient = viewModel::pickPatient,
            retryPatients = viewModel::loadPatients,
            create = viewModel::create,
            discard = onClose,
        ),
    )
}

@Composable
internal fun CreateTaskContent(
    state: CreateTaskUiState,
    today: LocalDate,
    actions: CreateTaskActions,
) = Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas).imePadding()) {
    ShifaSubHeader(tr(Res.string.createRemoteCareTask), Modifier.statusBarsPadding(), onBack = actions.back)
    val f = state.form
    Column(
        // A tablet keeps the board's form width, centred.
        Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .wrapContentWidth()
            .widthIn(max = ShifaDimens.Layout.FormWide)
            .padding(ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
    ) {
        state.templateName?.let {
            Text(
                tr(Res.string.fromTemplate, it),
                Modifier.background(
                    ShifaColors.Bg.SuccessSubtle,
                    ShifaRadius.Full,
                ).padding(horizontal = ShifaSize.S10, vertical = ShifaSize.S4),
                style = ShifaTextStyles.tableHead(),
                color = ShifaColors.Text.Primary,
            )
        }
        Section { What(state, actions) }
        Section { Schedule(state, actions) }
        Section { Dates(state, today, actions) }
        Section {
            ShifaSwitchRow(
                tr(Res.string.notesRequired),
                f.notesRequired,
                { v -> actions.edit { it.copy(notesRequired = v) } },
            )
            ShifaInputField(
                f.notesLabel,
                { v -> actions.edit { it.copy(notesLabel = v) } },
                tr(Res.string.notesLabel),
                Modifier.fillMaxWidth(),
                supportingText = tr(Res.string.enterNotesLabel),
            )
        }
    }
    // Sticky (flow §12: Flutter's button scrolled away with the form).
    Column(
        Modifier.fillMaxWidth().background(
            ShifaColors.Bg.Surface,
        ).navigationBarsPadding().padding(ShifaSpacing.Inset.Md),
    ) {
        ShifaPrimaryButton(
            tr(Res.string.createRemoteCareTask),
            actions.create,
            Modifier.fillMaxWidth(),
            loading = state.saving,
        )
    }
    if (state.picking) {
        PatientPickerSheet(
            tr(Res.string.selectPatient),
            state.patients?.matching(state.query),
            state.patientsError,
            state.query,
            { q -> actions.ui { it.copy(query = q) } },
            actions.pickPatient,
            actions.retryPatients,
            { actions.ui { it.copy(picking = false) } },
            selectedId = f.patient?.id,
        )
    }
    if (state.askDiscard) {
        ProfileSheet({ actions.ui { it.copy(askDiscard = false) } }) {
            ShifaDialogSheet(
                tr(Res.string.discardTaskTitle),
                tr(Res.string.discard),
                actions.discard,
                body = tr(Res.string.discardChangesBody),
                tone = DialogTone.Destructive,
                icon = Res.drawable.ic_trash_22,
                cancelText = tr(Res.string.keepEditing),
                onCancel = { actions.ui { it.copy(askDiscard = false) } },
            )
        }
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) =
    Column(
        Modifier.fillMaxWidth().background(ShifaColors.Bg.Surface, ShifaRadius.ControlLg).padding(ShifaSize.S14),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S12),
        content = content,
    )

@Composable
private fun Error(text: String) = Text(text, style = ShifaTextStyles.tableHead(), color = ShifaColors.Text.Error)

/** Patient, name, description, category and input type, the input label (board's first card). */
@Composable
private fun What(
    state: CreateTaskUiState,
    actions: CreateTaskActions,
) {
    val f = state.form
    FieldLabel("${tr(Res.string.patient)} *")
    val noPatient = state.shows(TaskFormProblem.NoPatient)
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(actions.openPicker, PressVariant.Tile)
            .border(
                ShifaSize.S2,
                if (noPatient) ShifaColors.Border.Error else ShifaColors.Border.Default,
                ShifaRadius.ControlMd,
            )
            .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S12),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(Res.drawable.ic_search_16), null, tint = ShifaColors.Icon.Secondary)
        val p = f.patient
        Text(
            if (p == null) {
                tr(
                    Res.string.tapToSearch,
                )
            } else {
                "${p.name.orEmpty()} (${tr(Res.string.patientIdLabel, p.id.toString())})"
            },
            style = ShifaTextStyles.dialogBody(),
            color = if (p == null) ShifaColors.Text.Tertiary else ShifaColors.Text.Primary,
        )
    }
    if (noPatient) Error(tr(Res.string.pleaseSelectPatient))
    ShifaInputField(
        f.name,
        { v -> actions.edit { it.copy(name = v) } },
        "${tr(Res.string.taskName)} *",
        Modifier.fillMaxWidth(),
        isError = state.shows(TaskFormProblem.NoName),
        supportingText = tr(Res.string.pleaseEnterTaskName).takeIf { state.shows(TaskFormProblem.NoName) },
    )
    ShifaInputField(
        f.description,
        { v -> actions.edit { it.copy(description = v) } },
        tr(Res.string.description),
        Modifier.fillMaxWidth(),
        supportingText = tr(Res.string.taskDescription),
        multiline = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
            FieldLabel("${tr(Res.string.category)} *")
            Choice(TaskCategory.entries.map { tr(it.label) }, f.category.ordinal) { i ->
                actions.edit { it.copy(category = TaskCategory.entries[i]) }
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
            FieldLabel("${tr(Res.string.inputType)} *")
            Choice(TaskInputType.entries.map { tr(it.label) }, f.inputType.ordinal) { i ->
                actions.edit { it.copy(inputType = TaskInputType.entries[i]) }
            }
        }
    }
    ShifaInputField(
        f.inputLabel,
        { v -> actions.edit { it.copy(inputLabel = v) } },
        tr(Res.string.inputLabel),
        Modifier.fillMaxWidth(),
        supportingText = tr(Res.string.enterInputLabel),
    )
}

/** A dropdown that keeps its own open state. */
@Composable
private fun Choice(
    options: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ShifaDropdown(
        options[selected],
        options,
        options[selected],
        { i ->
            open = false
            onSelect(i)
        },
        open,
        { open = it },
        modifier.fillMaxWidth(),
    )
}

/**
 * A time to the minute (07 §3.3: Flutter's time picker; a template's 07:10 stays 07:10): the field, and a sheet with the
 * keyboard time entry, 24-hour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(
    time: LocalTime,
    label: String,
    modifier: Modifier = Modifier,
    onSelect: (LocalTime) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ShifaSize.ControlLg)
            .pressable({ open = true }, PressVariant.Row)
            .border(ShifaSize.S2, ShifaColors.Border.Default, ShifaRadius.ControlMd)
            .semantics { contentDescription = "$label, ${displayTime(time)}" }
            .padding(horizontal = ShifaSize.S12),
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            displayTime(time),
            Modifier.weight(1f),
            style = ShifaTextStyles.inputText(),
            color = ShifaColors.Text.Primary,
        )
        Icon(painterResource(Res.drawable.ic_clock_16), null, tint = ShifaColors.Icon.Secondary)
    }
    if (open) {
        ProfileSheet({ open = false }) {
            ShifaTray(label, { open = false }) {
                val picker = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
                Column(
                    Modifier.fillMaxWidth().padding(ShifaSpacing.Inset.Md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
                ) {
                    TimeInput(picker)
                    ShifaPrimaryButton(
                        tr(Res.string.done),
                        {
                            open = false
                            onSelect(LocalTime(picker.hour, picker.minute))
                        },
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Even spacing (times, start, interval, the day's slots) or custom times (flow §3.8). */
@Composable
private fun Schedule(
    state: CreateTaskUiState,
    actions: CreateTaskActions,
) {
    val f = state.form
    Text(
        tr(Res.string.scheduleMode),
        Modifier.semantics {
            heading()
        },
        style = ShifaTextStyles.panelTitle(),
        color = ShifaColors.Text.Primary,
    )
    SegmentedTabs(
        listOf(TabItem(tr(Res.string.scheduleModeEvenSpacing)), TabItem(tr(Res.string.scheduleModeCustomTimes))),
        f.mode.ordinal,
        { actions.mode(ScheduleMode.entries[it]) },
        block = true,
    )
    if (f.mode == ScheduleMode.Even) {
        Row(horizontalArrangement = Arrangement.spacedBy(ShifaSize.S10)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                FieldLabel(tr(Res.string.timesPerDay))
                Choice(
                    (1..MAX_TIMES_PER_DAY).map {
                        it.toString()
                    },
                    f.timesPerDay - 1,
                ) { i -> actions.edit { it.copy(timesPerDay = i + 1) } }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ShifaSize.S6)) {
                FieldLabel(tr(Res.string.startTime))
                TimeField(f.startTime, tr(Res.string.startTime)) { t -> actions.edit { it.copy(startTime = t) } }
            }
        }
        if (f.timesPerDay >= 2) {
            FieldLabel(tr(Res.string.intervalBetweenTasks))
            val labels =
                TASK_INTERVALS.map {
                        h ->
                    if (h == 1) tr(Res.string.every1Hour) else tr(Res.string.everyNHours, h)
                }
            Choice(labels, TASK_INTERVALS.indexOf(f.intervalHours).coerceAtLeast(0)) { i ->
                actions.edit { it.copy(intervalHours = TASK_INTERVALS[i]) }
            }
            state.intervalAdjustedTo?.let {
                Text(
                    tr(Res.string.intervalAdjusted, it.toString()),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
        // The day's slots, live; orange with the count that won't fit before midnight (flow §3.8).
        val clipped = f.clipped
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    if (clipped > 0) ShifaColors.Bg.WarningSubtle else ShifaColors.Bg.SuccessSubtle,
                    ShifaRadius.ControlMd,
                )
                .padding(horizontal = ShifaSize.S12, vertical = ShifaSize.S10),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S4),
        ) {
            Text(
                "${tr(Res.string.slotsPreviewLabel)}: ${f.slots.joinToString(" · ", transform = ::displayTime)}",
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
            )
            if (clipped > 0) {
                Text(
                    tr(Res.string.slotsPreviewClipped, clipped),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.WarningStrong,
                )
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxWidth()
                .border(ShifaSize.S2, ShifaColors.Border.Default, ShifaRadius.ControlMd)
                .padding(ShifaSize.S10),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S6),
        ) {
            Text(
                tr(Res.string.customTimesLabel),
                style = ShifaTextStyles.buttonCompact(),
                color = ShifaColors.Text.Primary,
            )
            Text(
                tr(Res.string.customTimesHint),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            f.customTimes.forEachIndexed { i, t ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TimeField(
                        t,
                        tr(Res.string.customTimesLabel),
                        Modifier.weight(1f),
                    ) { new -> actions.setSlot(i, new) }
                    ShifaIconButton(
                        Res.drawable.ic_trash_16,
                        tr(Res.string.remove),
                        { actions.removeSlot(i) },
                        Modifier.size(ShifaSize.S40),
                        variant = ShifaIconButtonVariant.Secondary,
                        enabled = f.customTimes.size > 1,
                    )
                }
            }
            Text(
                trPlural(Res.plurals.customTimesCount, f.customTimes.size, f.customTimes.size.toString()),
                style = ShifaTextStyles.metaSmall(),
                color = ShifaColors.Text.Secondary,
            )
            ShifaTertiaryButton(
                tr(Res.string.customTimesAddSlot),
                actions.addSlot,
                icon = Res.drawable.ic_plus_16,
                enabled = f.customTimes.size < MAX_TIMES_PER_DAY,
            )
        }
    }
}

/** Start date, end date or duration (one of the two), with the inline month for the one open. */
@Composable
private fun Dates(
    state: CreateTaskUiState,
    today: LocalDate,
    actions: CreateTaskActions,
) {
    val f = state.form
    val last = today.plus(DatePeriod(days = TASK_DAYS_AHEAD))
    Column {
        ShifaSettingsItem(
            "${tr(Res.string.startDate)} *",
            { actions.openDate(if (state.dateOpen == TaskDateField.Start) null else TaskDateField.Start) },
            subtitle = taskDate(f.startDate),
        )
        if (state.dateOpen == TaskDateField.Start) {
            MonthPicker(
                state.month,
                f.startDate,
                today,
                { null },
                { d -> if (d in today..last) actions.startDate(d) },
                { delta -> actions.ui { it.copy(month = it.month.plus(DatePeriod(months = delta))) } },
            )
        }
        ShifaSwitchRow(
            tr(Res.string.useEndDate),
            f.useEndDate,
            { v -> actions.edit { it.copy(useEndDate = v) } },
            Modifier.padding(vertical = ShifaSize.S8),
        )
        if (f.useEndDate) {
            ShifaSettingsItem(
                tr(Res.string.endDate),
                { actions.openDate(if (state.dateOpen == TaskDateField.End) null else TaskDateField.End) },
                subtitle = f.endDate?.let { taskDate(it) } ?: tr(Res.string.notSet),
                divider = false,
            )
            if (state.dateOpen == TaskDateField.End) {
                MonthPicker(
                    state.month,
                    f.endDate ?: TaskForm.defaultEnd(f.startDate),
                    today,
                    { null },
                    { d -> if (d in f.startDate..last) actions.endDate(d) },
                    { delta -> actions.ui { it.copy(month = it.month.plus(DatePeriod(months = delta))) } },
                )
            }
            if (state.shows(TaskFormProblem.NoEndDate)) Error(tr(Res.string.selectEndDate))
            if (state.shows(TaskFormProblem.EndBeforeStart)) Error(tr(Res.string.endDateBeforeStart))
        } else {
            ShifaInputField(
                f.durationText,
                // As typed: "1.5" or "-3" are errors, never rewritten into another number of days; an edit longer than
                // any real duration is refused whole, not cut.
                { v -> if (v.length <= DURATION_CHARS) actions.edit { it.copy(durationText = v) } },
                tr(Res.string.durationDays),
                Modifier.fillMaxWidth(),
                isError = state.shows(TaskFormProblem.BadDuration),
                supportingText = tr(Res.string.durationDaysInvalid).takeIf { state.shows(TaskFormProblem.BadDuration) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
    }
}

private const val DURATION_CHARS = 6
