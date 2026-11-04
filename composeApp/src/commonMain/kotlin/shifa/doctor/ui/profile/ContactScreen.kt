package shifa.doctor.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.DoctorContact
import shifa.doctor.data.model.ProfileUpdateRequest
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.city
import shifa.doctor.generated.resources.clinicAddress
import shifa.doctor.generated.resources.contactAndClinicAddress
import shifa.doctor.generated.resources.contactDetailsSaved
import shifa.doctor.generated.resources.contactSavedAddressFailed
import shifa.doctor.generated.resources.contactSection
import shifa.doctor.generated.resources.coordinatesNotSet
import shifa.doctor.generated.resources.country
import shifa.doctor.generated.resources.district
import shifa.doctor.generated.resources.emailOptional
import shifa.doctor.generated.resources.ic_globe_22
import shifa.doctor.generated.resources.invalidEmail
import shifa.doctor.generated.resources.invalidPhone
import shifa.doctor.generated.resources.locationSaved
import shifa.doctor.generated.resources.notSet
import shifa.doctor.generated.resources.patientsSeePhone
import shifa.doctor.generated.resources.phone
import shifa.doctor.generated.resources.phoneNumberRequired
import shifa.doctor.generated.resources.pickOnMap
import shifa.doctor.generated.resources.postalCode
import shifa.doctor.generated.resources.practiceTimezone
import shifa.doctor.generated.resources.region
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.streetAddress
import shifa.doctor.generated.resources.streetAddressHelper
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.utils.PhoneE164
import shifa.doctor.utils.TimeZones
import shifa.doctor.utils.isEmailAddress
import shifa.doctor.utils.normalizedEmail
import shifa.doctor.utils.tr

/** The editable part: contact (one PATCH) and the structured address (profile PATCH), compared with what's saved. */
data class ContactForm(
    val phone: String = "",
    val email: String = "",
    val country: String = "",
    val region: String = "",
    val district: String = "",
    val city: String = "",
    val postal: String = "",
    val street: String = "",
    // From the map pin (PROF-07); saved with the address even when no address field is filled (flow 08/03 §12).
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val contact get() = phone to email
    val address get() = listOf(country, region, district, city, postal, street, latitude, longitude)
}

data class ContactUiState(
    val saved: ContactForm = ContactForm(),
    val form: ContactForm = saved,
    // The map picker while it is open (flow 08/04); null when closed.
    val map: PinEdit? = null,
    val timeZone: String? = null,
    val phoneError: StringResource? = null,
    val emailError: StringResource? = null,
    val saving: Boolean = false,
    val banner: CodeBanner? = null,
    val askDiscard: Boolean = false,
    val done: Boolean = false,
) {
    val mapPin: Pair<Double, Double>? get() = map?.pin
    val askDiscardPin: Boolean get() = map?.asking == true

    val dirty: Boolean get() = form != saved
}

/**
 * PROF-04 (flow 08/03, DaProfile `profContact`): phone and e-mail (`PATCH /doctors/me/contact`, the whole block) and
 * the structured clinic address (`PATCH /doctors/me/profile`, only the changed keys). Save sends the dirty part(s) one
 * after the other; a failed address after a saved contact says so and keeps the address dirty (§8). Address search
 * and the map pin come with PROF-07.
 */
class ContactViewModel(
    private val profile: ProfileRepository = profileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ContactUiState())
    val state: StateFlow<ContactUiState> = _state.asStateFlow()

    init {
        // The zone saves on its own sheet over this form (PROF-06); the row follows it without touching the edits.
        viewModelScope.launch {
            profile.me.collect {
                    me ->
                _state.update { it.copy(timeZone = me?.profile?.timeZone) }
            }
        }
    }

    /** Each time the screen shows (shell-scoped, like Personal information): an edit in progress is kept. */
    fun open() {
        _state.update { s ->
            if (s.dirty && !s.done) {
                s
            } else {
                val me = profile.me.value
                val p = me?.profile
                ContactUiState(
                    saved =
                        ContactForm(
                            me?.contact?.phone?.let(PhoneE164::display).orEmpty(),
                            me?.contact?.email.orEmpty(),
                            p?.locationCountry.orEmpty(),
                            p?.locationRegion.orEmpty(),
                            p?.locationDistrict.orEmpty(),
                            p?.locationCity.orEmpty(),
                            p?.locationPostalCode.orEmpty(),
                            p?.locationStreetAddress.orEmpty(),
                            p?.latitude,
                            p?.longitude,
                        ),
                    timeZone = p?.timeZone,
                )
            }
        }
    }

    fun edit(change: (ContactForm) -> ContactForm) =
        _state.update { it.copy(form = change(it.form), phoneError = null, emailError = null) }

    fun ui(change: (ContactUiState) -> ContactUiState) = _state.update(change)

    /** The map opens on the current pin, or Tashkent's centre (flow 08/04 §3). */
    fun openMap() = _state.update { it.copy(map = PinEdit.at(it.form.latitude, it.form.longitude)) }

    fun movePin(
        latitude: Double,
        longitude: Double,
    ) = _state.update { it.copy(map = it.map?.moved(latitude, longitude)) }

    /** The pin goes into the form; nothing is saved until Save (flow 08/03 §10). */
    fun confirmMap() =
        _state.update { s ->
            val pin = s.map?.pin ?: return@update s
            s.copy(map = null, form = s.form.copy(latitude = pin.first, longitude = pin.second))
        }

    fun closeMap() = _state.update { it.copy(map = it.map?.closing()) }

    fun discardPin() = _state.update { it.copy(map = null) }

    fun keepPin() = _state.update { it.copy(map = it.map?.copy(asking = false)) }

    fun back(): Boolean {
        val s = _state.value
        if (s.mapPin != null) return false.also { closeMap() }
        return when (backStep(s.saving, s.askDiscard, s.dirty)) {
            BackStep.Close -> true
            BackStep.Ask -> false.also { _state.update { it.copy(askDiscard = true) } }
            BackStep.CloseQuestion -> false.also { _state.update { it.copy(askDiscard = false) } }
            BackStep.Stay -> false
        }
    }

    fun discard() = _state.update { it.copy(form = it.saved, askDiscard = false, done = true) }

    fun closed() = _state.update { it.copy(done = false) }

    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty) return
        val phone = PhoneE164.normalize(s.form.phone)
        val email = normalizedEmail(s.form.email)
        val phoneError =
            when {
                s.form.phone.isBlank() -> Res.string.phoneNumberRequired
                phone == null -> Res.string.invalidPhone
                else -> null
            }
        val emailError = Res.string.invalidEmail.takeIf { email.isNotEmpty() && !isEmailAddress(email) }
        if (phoneError != null || emailError != null) {
            return _state.update { it.copy(phoneError = phoneError, emailError = emailError) }
        }
        val f = s.form.copy(phone = PhoneE164.display(phone!!), email = email)
        val contactDirty = f.contact != s.saved.contact
        val addressDirty = f.address != s.saved.address
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            if (contactDirty) {
                val sent = profile.updateContact(DoctorContact(phone = phone, email = email.ifEmpty { null }))
                sent.exceptionOrNull()?.let { e -> return@launch failed(e, Res.string.saveFailed) }
                // The contact is saved even if the address fails next.
                _state.update { it.copy(saved = it.saved.copy(phone = f.phone, email = f.email)) }
            }
            if (addressDirty) {
                val o = s.saved

                fun changed(
                    new: String,
                    old: String,
                ) = new.trim().takeIf { it != old }
                val request =
                    ProfileUpdateRequest(
                        locationCountry = changed(f.country, o.country),
                        locationRegion = changed(f.region, o.region),
                        locationDistrict = changed(f.district, o.district),
                        locationCity = changed(f.city, o.city),
                        locationPostalCode = changed(f.postal, o.postal),
                        locationStreetAddress = changed(f.street, o.street),
                        // Both together whenever the pin moved.
                        latitude = f.latitude.takeIf { f.latitude != o.latitude || f.longitude != o.longitude },
                        longitude = f.longitude.takeIf { f.latitude != o.latitude || f.longitude != o.longitude },
                    )
                profile.update(request).exceptionOrNull()?.let { e ->
                    return@launch failed(
                        e,
                        if (contactDirty) Res.string.contactSavedAddressFailed else Res.string.saveFailed,
                    )
                }
            }
            val notice = if (contactDirty) Res.string.contactDetailsSaved else Res.string.locationSaved
            ShellNotice.pending.value = Notice(notice, success = true)
            _state.update { it.copy(saving = false, saved = f, form = f, done = true) }
        }
    }

    private fun failed(
        e: Throwable,
        fallback: StringResource,
    ) = _state.update {
        // After a saved contact the address message is fixed: the mapped error alone wouldn't say what was kept.
        val text = if (fallback == Res.string.contactSavedAddressFailed) fallback else e.toUserMessage(fallback)
        it.copy(saving = false, banner = CodeBanner(text, requestId = e.requestId))
    }
}

@Composable
fun ContactScreen(
    onClose: () -> Unit,
    onTimezone: () -> Unit,
    viewModel: ContactViewModel = viewModel { ContactViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.open() }
    val back = { if (viewModel.back()) onClose() }
    FormExitEffects(state.done, viewModel::closed, onClose, back)
    val map = state.map
    if (map != null) {
        MapPickerHost(
            map,
            viewModel::movePin,
            viewModel::confirmMap,
            viewModel::closeMap,
            viewModel::discardPin,
            viewModel::keepPin,
        )
    } else {
        ContactContent(
            state,
            ContactActions(
                back,
                viewModel::save,
                viewModel::edit,
                viewModel::ui,
                viewModel::discard,
                onTimezone,
                viewModel::openMap,
            ),
        )
    }
}

class ContactActions(
    val back: () -> Unit = {},
    val save: () -> Unit = {},
    val edit: ((ContactForm) -> ContactForm) -> Unit = {},
    val ui: ((ContactUiState) -> ContactUiState) -> Unit = {},
    val discard: () -> Unit = {},
    val timezone: () -> Unit = {},
    val map: () -> Unit = {},
)

@Composable
fun ContactContent(
    state: ContactUiState,
    actions: ContactActions,
) {
    val enabled = !state.saving
    val f = state.form

    @Composable
    fun Field(
        value: String,
        label: StringResource,
        set: (String) -> ContactForm,
        error: StringResource? = null,
        type: KeyboardType = KeyboardType.Text,
    ) = ShifaInputField(
        value,
        { v -> actions.edit { set(v) } },
        tr(label),
        Modifier.fillMaxWidth(),
        supportingText = error?.let { tr(it) },
        isError = error != null,
        enabled = enabled,
        keyboardOptions =
            KeyboardOptions(
                capitalization =
                    if (type == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
                keyboardType = type,
                imeAction = ImeAction.Next,
            ),
    )
    ProfileForm(
        tr(Res.string.contactAndClinicAddress),
        actions.back,
        onSave = if (state.dirty && enabled) actions.save else null,
        askDiscard = state.askDiscard,
        onDiscard = actions.discard,
        onKeep = { actions.ui { it.copy(askDiscard = false) } },
    ) {
        codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
        ShifaSection(tr(Res.string.contactSection)) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                Field(f.phone, Res.string.phone, { f.copy(phone = it) }, state.phoneError, KeyboardType.Phone)
                Field(f.email, Res.string.emailOptional, { f.copy(email = it) }, state.emailError, KeyboardType.Email)
                Text(
                    tr(Res.string.patientsSeePhone),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
        ShifaSection(tr(Res.string.clinicAddress)) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                Field(f.country, Res.string.country, { f.copy(country = it) })
                Field(f.region, Res.string.region, { f.copy(region = it) })
                Field(f.district, Res.string.district, { f.copy(district = it) })
                Field(f.city, Res.string.city, { f.copy(city = it) })
                Field(f.postal, Res.string.postalCode, { f.copy(postal = it) })
                Field(f.street, Res.string.streetAddress, { f.copy(street = it.take(STREET_MAX)) })
                // Under the field, not as its hint: the hint has room for one line only.
                Text(
                    tr(Res.string.streetAddressHelper),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
                // Set by the map pin; no reverse geocoding until the backend proxy exists (BE-15).
                Text(
                    coordinates(f.latitude, f.longitude) ?: tr(Res.string.coordinatesNotSet),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
                ShifaSecondaryButton(tr(Res.string.pickOnMap), actions.map, Modifier.fillMaxWidth(), enabled = enabled)
            }
        }
        // Address and zone are set together; the zone saves on its own screen (flow 08/03 §3.4).
        ShifaSection(null) {
            ShifaSettingsItem(
                tr(Res.string.practiceTimezone),
                actions.timezone,
                icon = Res.drawable.ic_globe_22,
                subtitle =
                    state.timeZone?.let {
                            z ->
                        TimeZones.utcLabel(z)?.let { "$z ($it)" } ?: z
                    } ?: tr(Res.string.notSet),
                divider = false,
            )
        }
    }
}

private const val STREET_MAX = 250
