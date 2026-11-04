package shifa.doctor.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import shifa.doctor.data.model.DoctorBilling
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.subscription.DoctorTier
import shifa.doctor.data.subscription.FeatureGate
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.billingEmail
import shifa.doctor.generated.resources.billingName
import shifa.doctor.generated.resources.ibanAccountNumber
import shifa.doctor.generated.resources.invalidEmail
import shifa.doctor.generated.resources.invalidIban
import shifa.doctor.generated.resources.invoicingDetails
import shifa.doctor.generated.resources.paymentAndInvoicingSaved
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.subscriptionAndBilling
import shifa.doctor.generated.resources.taxIdVatId
import shifa.doctor.generated.resources.yourCurrentPlan
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.utils.isEmailAddress
import shifa.doctor.utils.normalizedEmail
import shifa.doctor.utils.tr

/** The invoicing details the doctor edits; Stripe Connect and the merchant ids are echoed untouched. */
data class InvoicingForm(
    val name: String = "",
    val email: String = "",
    val iban: String = "",
    val taxId: String = "",
)

data class BillingUiState(
    val tier: DoctorTier? = null,
    val billing: DoctorBilling = DoctorBilling(),
    val saved: InvoicingForm = InvoicingForm(),
    val form: InvoicingForm = saved,
    val emailError: StringResource? = null,
    val ibanError: StringResource? = null,
    val saving: Boolean = false,
    val banner: CodeBanner? = null,
    val askDiscard: Boolean = false,
    val done: Boolean = false,
) {
    val dirty: Boolean get() = form != saved
}

/**
 * PROF-04 billing fields (flow 08/09 §3.2 and §3.6, DaProfile `profBilling`): the current plan and the invoicing
 * details card (`PATCH /doctors/me/billing`, the whole block: blanks go as null, the Stripe account and the Click /
 * Payme merchant ids are sent back as read). Plans, checkout and Stripe Connect are SUB-02.
 */
class BillingViewModel(
    private val profile: ProfileRepository = profileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    fun open() =
        _state.update { s ->
            val me = profile.me.value
            val b = me?.billing ?: DoctorBilling()
            val tier = FeatureGate.of(me?.subscription).tier
            if (s.dirty && !s.done) {
                s.copy(tier = tier)
            } else {
                val form =
                    InvoicingForm(
                        b.billingName.orEmpty(),
                        b.billingEmail.orEmpty(),
                        b.iban.orEmpty(),
                        b.taxId.orEmpty(),
                    )
                BillingUiState(tier = tier, billing = b, saved = form)
            }
        }

    fun edit(change: (InvoicingForm) -> InvoicingForm) =
        _state.update { it.copy(form = change(it.form), emailError = null, ibanError = null) }

    fun ui(change: (BillingUiState) -> BillingUiState) = _state.update(change)

    fun back(): Boolean {
        val s = _state.value
        return when (backStep(s.saving, s.askDiscard, s.dirty)) {
            BackStep.Close -> true
            BackStep.Ask -> false.also { _state.update { it.copy(askDiscard = true) } }
            BackStep.CloseQuestion -> false.also { _state.update { it.copy(askDiscard = false) } }
            BackStep.Stay -> false
        }
    }

    fun discard() = _state.update { it.copy(form = it.saved, askDiscard = false, done = true) }

    fun closed() = _state.update { it.copy(done = false) }

    /** Saves and stays: the screen holds more than this card (flow 08/09 §3.6). */
    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty) return
        val email = normalizedEmail(s.form.email)
        val iban = cleanIban(s.form.iban)
        val emailError = Res.string.invalidEmail.takeIf { email.isNotEmpty() && !isEmailAddress(email) }
        val ibanError = Res.string.invalidIban.takeIf { iban.isNotEmpty() && !isIbanOrAccount(iban) }
        if (emailError != null || ibanError != null) {
            return _state.update {
                it.copy(emailError = emailError, ibanError = ibanError)
            }
        }
        val f = InvoicingForm(s.form.name.trim(), email, iban, s.form.taxId.trim())
        // The ids this form doesn't edit come from the latest profile: a refresh may have brought a new Stripe account.
        val body =
            (profile.me.value?.billing ?: s.billing).copy(
                billingName = f.name.ifEmpty { null },
                billingEmail = f.email.ifEmpty { null },
                iban = f.iban.ifEmpty { null },
                taxId = f.taxId.ifEmpty { null },
            )
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            profile.updateBilling(body).fold(
                onSuccess = { saved ->
                    _state.update {
                        it.copy(
                            saving = false,
                            billing = saved,
                            saved = f,
                            form = f,
                            banner = CodeBanner(Res.string.paymentAndInvoicingSaved, error = false),
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            saving = false,
                            banner = CodeBanner(e.toUserMessage(Res.string.saveFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    internal companion object {
        fun cleanIban(input: String) = input.filterNot(Char::isWhitespace).uppercase()

        /**
         * An IBAN (2 letters, 2 check digits, 11–30 letters or digits: 15–34 in all) or a plain account number (10–34
         * digits; Uzbek accounts have 20). No checksum (flow 08/09 §4, open question).
         */
        fun isIbanOrAccount(value: String) = Regex("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}|[0-9]{10,34}").matches(value)
    }
}

@Composable
fun BillingScreen(
    onClose: () -> Unit,
    viewModel: BillingViewModel = viewModel { BillingViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.open() }
    val back = { if (viewModel.back()) onClose() }
    FormExitEffects(state.done, viewModel::closed, onClose, back)
    BillingContent(state, BillingActions(back, viewModel::save, viewModel::edit, viewModel::ui, viewModel::discard))
}

class BillingActions(
    val back: () -> Unit = {},
    val save: () -> Unit = {},
    val edit: ((InvoicingForm) -> InvoicingForm) -> Unit = {},
    val ui: ((BillingUiState) -> BillingUiState) -> Unit = {},
    val discard: () -> Unit = {},
)

@Composable
fun BillingContent(
    state: BillingUiState,
    actions: BillingActions,
) {
    val enabled = !state.saving
    val f = state.form

    @Composable
    fun Field(
        value: String,
        label: StringResource,
        set: (String) -> InvoicingForm,
        error: StringResource? = null,
        type: KeyboardType = KeyboardType.Text,
        caps: KeyboardCapitalization = KeyboardCapitalization.None,
    ) = ShifaInputField(
        value,
        { v -> actions.edit { set(v) } },
        tr(label),
        Modifier.fillMaxWidth(),
        supportingText = error?.let { tr(it) },
        isError = error != null,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(capitalization = caps, keyboardType = type, imeAction = ImeAction.Next),
    )
    // Save sits in the card (only this card is a form), so the header has no check.
    ProfileForm(
        tr(Res.string.subscriptionAndBilling),
        actions.back,
        onSave = null,
        askDiscard = state.askDiscard,
        onDiscard = actions.discard,
        onKeep = { actions.ui { it.copy(askDiscard = false) } },
    ) {
        codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
        // Shown once the plan is known (D-03: never guess).
        state.tier?.let { tier ->
            ShifaSection(null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileChip(tier.name.uppercase(), ShifaColors.Text.Primary, ShifaColors.Text.Inverse)
                    Text(
                        tr(Res.string.yourCurrentPlan),
                        style = ShifaTextStyles.dialogBody(),
                        color = ShifaColors.Text.Primary,
                    )
                }
            }
        }
        ShifaSection(tr(Res.string.invoicingDetails)) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                Field(f.name, Res.string.billingName, { f.copy(name = it) }, caps = KeyboardCapitalization.Words)
                Field(f.email, Res.string.billingEmail, { f.copy(email = it) }, state.emailError, KeyboardType.Email)
                Field(
                    f.iban,
                    Res.string.ibanAccountNumber,
                    { f.copy(iban = it.take(IBAN_INPUT_MAX)) },
                    state.ibanError,
                    caps = KeyboardCapitalization.Characters,
                )
                Field(f.taxId, Res.string.taxIdVatId, { f.copy(taxId = it) }, caps = KeyboardCapitalization.Characters)
                ShifaPrimaryButton(
                    tr(Res.string.save),
                    actions.save,
                    Modifier.fillMaxWidth(),
                    enabled = state.dirty,
                    loading = state.saving,
                )
            }
        }
    }
}

// 34 characters plus the spaces people type in groups of four.
private const val IBAN_INPUT_MAX = 42
