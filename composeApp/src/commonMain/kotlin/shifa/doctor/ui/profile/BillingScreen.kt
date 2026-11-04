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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.model.DoctorBilling
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.subscription.DoctorTier
import shifa.doctor.data.subscription.FeatureGate
import shifa.doctor.data.subscription.SubscriptionPlan
import shifa.doctor.data.subscription.SubscriptionRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.billingEmail
import shifa.doctor.generated.resources.billingName
import shifa.doctor.generated.resources.checkoutFailed
import shifa.doctor.generated.resources.connectStripe
import shifa.doctor.generated.resources.couldNotLoadRetry
import shifa.doctor.generated.resources.currentPlanButton
import shifa.doctor.generated.resources.ibanAccountNumber
import shifa.doctor.generated.resources.invalidEmail
import shifa.doctor.generated.resources.invalidIban
import shifa.doctor.generated.resources.invoicingDetails
import shifa.doctor.generated.resources.paymentAndInvoicingSaved
import shifa.doctor.generated.resources.payouts
import shifa.doctor.generated.resources.payoutsHelper
import shifa.doctor.generated.resources.planChanged
import shifa.doctor.generated.resources.planManagedBySupport
import shifa.doctor.generated.resources.planUnchanged
import shifa.doctor.generated.resources.plans
import shifa.doctor.generated.resources.pricePerMonth
import shifa.doctor.generated.resources.renewsOn
import shifa.doctor.generated.resources.resumeStripe
import shifa.doctor.generated.resources.save
import shifa.doctor.generated.resources.saveFailed
import shifa.doctor.generated.resources.stripeConnected
import shifa.doctor.generated.resources.stripeNotConnected
import shifa.doctor.generated.resources.subscriptionAndBilling
import shifa.doctor.generated.resources.taxIdVatId
import shifa.doctor.generated.resources.upgradeToPlan
import shifa.doctor.generated.resources.yourCurrentPlan
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.register.displayDate
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.utils.Money
import shifa.doctor.utils.isEmailAddress
import shifa.doctor.utils.normalizedEmail
import shifa.doctor.utils.rememberInAppBrowser
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
    // SUB-02: the plans on offer, a hosted page to open, and what the doctor went to do there.
    val plans: List<SubscriptionPlan> = emptyList(),
    val opening: String? = null,
    val openUrl: String? = null,
    val away: Away? = null,
    val plansFailed: Boolean = false,
    // The paid period's end ("Renews on …"), when the backend has one.
    val renewsOn: String? = null,
) {
    val dirty: Boolean get() = form != saved
}

/** Out in the browser: a checkout (with the plan the doctor had then) or the Stripe Connect onboarding. */
sealed interface Away {
    data class Checkout(val tierBefore: DoctorTier?) : Away

    data object Connect : Away
}

/**
 * PROF-04 billing fields (flow 08/09 §3.2 and §3.6, DaProfile `profBilling`): the current plan and the invoicing
 * details card (`PATCH /doctors/me/billing`, the whole block: blanks go as null, the Stripe account and the Click /
 * Payme merchant ids are sent back as read). Plans, checkout and Stripe Connect are SUB-02.
 */
class BillingViewModel(
    private val profile: ProfileRepository = profileRepository,
    private val subscriptions: SubscriptionRepository = SubscriptionRepository(),
    private val retryDelayMs: Long = RETURN_RETRY_MS,
) : ViewModel() {
    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    fun open() {
        openForm()
        // Plans to offer; none (a failure, or an old backend) just means no upgrade buttons.
        if (_state.value.plans.isEmpty()) loadPlans()
        viewModelScope.launch {
            subscriptions.mine().onSuccess { sub ->
                val end = sub?.currentPeriodEnd?.let { runCatching { kotlinx.datetime.Instant.parse(it) }.getOrNull() }
                val zone =
                    profile.me.value?.profile?.timeZone?.let {
                        runCatching {
                            kotlinx.datetime.TimeZone.of(
                                it,
                            )
                        }.getOrNull()
                    }
                val date = end?.toLocalDateTime(zone ?: kotlinx.datetime.TimeZone.UTC)?.date
                _state.update { it.copy(renewsOn = date?.let(::displayDate)) }
            }
        }
    }

    /** The plans; a failure shows "Couldn't load. Retry" in their place. */
    fun loadPlans() {
        _state.update { it.copy(plansFailed = false) }
        viewModelScope.launch {
            subscriptions.plans().fold(
                onSuccess = { plans -> _state.update { it.copy(plans = plans) } },
                onFailure = { _state.update { it.copy(plansFailed = true) } },
            )
        }
    }

    private fun openForm() =
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
                // A Stripe request or return check still running survives leaving and reopening.
                BillingUiState(
                    tier = tier,
                    billing = b,
                    saved = form,
                    plans = s.plans,
                    plansFailed = s.plansFailed,
                    renewsOn = s.renewsOn,
                    opening = s.opening,
                    openUrl = s.openUrl,
                    away = s.away,
                )
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

    /** Upgrade: the Stripe checkout for [plan] in the in-app browser (flow 08/09 §5). */
    fun upgrade(plan: SubscriptionPlan) =
        goOut(plan.code, Away.Checkout(_state.value.tier)) { subscriptions.checkout(plan.code) }

    /** Payouts: Stripe Connect onboarding, new or resumed. */
    fun connect() = goOut(CONNECT, Away.Connect) { subscriptions.connectOnboarding() }

    private fun goOut(
        key: String,
        away: Away,
        url: suspend () -> Result<String>,
    ) {
        if (_state.value.opening != null) return
        _state.update { it.copy(opening = key, banner = null) }
        viewModelScope.launch {
            url().fold(
                onSuccess = { link -> _state.update { it.copy(opening = null, openUrl = link, away = away) } },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            opening = null,
                            banner = CodeBanner(e.toUserMessage(Res.string.checkoutFailed), requestId = e.requestId),
                        )
                    }
                },
            )
        }
    }

    /** The screen opened [openUrl]. */
    fun urlOpened() = _state.update { it.copy(openUrl = null) }

    /**
     * Back in the app after the hosted page: the profile is read again; after a checkout the plan is compared ("You're
     * now on PRO" or "No change yet"), after onboarding the Stripe account shows.
     */
    fun resumed() {
        val away = _state.value.away ?: return
        if (_state.value.openUrl != null || returning) return // not opened yet, or already checking
        returning = true
        viewModelScope.launch {
            // A payment reaches the backend by webhook, so the plan is read a few times before saying "No change yet".
            var me = profile.refresh().getOrNull()
            for (attempt in 1..RETURN_RETRIES) {
                val read = me ?: break
                val now = FeatureGate.of(read.subscription).tier
                if (away !is Away.Checkout || (now != null && now != away.tierBefore)) break
                delay(retryDelayMs)
                me = profile.refresh().getOrNull() ?: read
            }
            returning = false
            // Unread (offline): the check stays pending and runs again on the next return.
            val read = me ?: return@launch
            val tier = FeatureGate.of(read.subscription).tier
            _state.update {
                val withAccount =
                    it.copy(
                        away = null,
                        tier = tier,
                        billing = it.billing.copy(stripeConnectAccountId = read.billing.stripeConnectAccountId),
                    )
                if (away is Away.Checkout) {
                    val banner =
                        if (tier != null && tier != away.tierBefore) {
                            CodeBanner(Res.string.planChanged, error = false, args = listOf(tier.name.uppercase()))
                        } else {
                            CodeBanner(Res.string.planUnchanged, error = false)
                        }
                    withAccount.copy(banner = banner)
                } else {
                    withAccount
                }
            }
        }
    }

    private var returning = false

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
        const val CONNECT = "connect"
        const val RETURN_RETRIES = 3
        const val RETURN_RETRY_MS = 3_000L

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
    // Hosted pages open in a Custom Tab / SFSafariViewController, never the external browser (flow 08/09 §4).
    val browser = rememberInAppBrowser()
    LaunchedEffect(state.openUrl) {
        state.openUrl?.let {
            viewModel.urlOpened()
            browser(it)
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resumed() }
    BillingContent(
        state,
        BillingActions(
            back,
            viewModel::save,
            viewModel::edit,
            viewModel::ui,
            viewModel::discard,
            viewModel::upgrade,
            viewModel::connect,
            viewModel::loadPlans,
        ),
    )
}

class BillingActions(
    val back: () -> Unit = {},
    val save: () -> Unit = {},
    val edit: ((InvoicingForm) -> InvoicingForm) -> Unit = {},
    val ui: ((BillingUiState) -> BillingUiState) -> Unit = {},
    val discard: () -> Unit = {},
    val upgrade: (SubscriptionPlan) -> Unit = {},
    val connect: () -> Unit = {},
    val retryPlans: () -> Unit = {},
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
                        listOfNotNull(
                            tr(Res.string.yourCurrentPlan),
                            state.renewsOn?.let { tr(Res.string.renewsOn, it) },
                        ).joinToString(" · "),
                        style = ShifaTextStyles.dialogBody(),
                        color = ShifaColors.Text.Primary,
                    )
                }
            }
        }
        PlansSection(state, actions)
        PayoutsSection(state, actions)
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

/**
 * The plans with their monthly price: "Current plan" on the doctor's, "Upgrade to …" on higher ones, nothing on lower
 * ones (no downgrade in the app). Hidden until the plan is known (D-03). PREMIUM is admin-set: no checkout exists.
 */
@Composable
private fun PlansSection(
    state: BillingUiState,
    actions: BillingActions,
) {
    val tier = state.tier ?: return
    if (state.plans.isEmpty() && !state.plansFailed) return
    ShifaSection(tr(Res.string.plans)) {
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
            if (state.plansFailed) ShifaLink(tr(Res.string.couldNotLoadRetry), actions.retryPlans)
            state.plans.forEach { plan ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(plan.name, style = ShifaTextStyles.panelTitle(), color = ShifaColors.Text.Primary)
                        Text(
                            tr(Res.string.pricePerMonth, Money.format(plan.priceMinor, plan.currency)),
                            style = ShifaTextStyles.metaSmall(),
                            color = ShifaColors.Text.Secondary,
                        )
                    }
                    val planTier = plan.tier
                    when {
                        planTier == tier ->
                            ProfileChip(
                                tr(Res.string.currentPlanButton),
                                ShifaColors.Bg.Selected,
                                ShifaColors.Text.Brand,
                            )
                        planTier != null && planTier > tier ->
                            ShifaPrimaryButton(
                                tr(Res.string.upgradeToPlan, plan.name),
                                { actions.upgrade(plan) },
                                loading = state.opening == plan.code,
                                enabled = state.opening == null,
                            )
                    }
                }
            }
            // Nothing higher to buy: the top plan is set by Shifa.
            if (state.plans.none { (it.tier ?: tier) > tier }) {
                Text(
                    tr(Res.string.planManagedBySupport),
                    style = ShifaTextStyles.metaSmall(),
                    color = ShifaColors.Text.Secondary,
                )
            }
        }
    }
}

/** Stripe Connect: "Not connected" / "Connected · acct_••••1234" (last four only) and the onboarding button. */
@Composable
private fun PayoutsSection(
    state: BillingUiState,
    actions: BillingActions,
) = ShifaSection(tr(Res.string.payouts)) {
    Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
        val account = state.billing.stripeConnectAccountId?.takeIf { it.isNotBlank() }
        Text(
            account?.let {
                tr(
                    Res.string.stripeConnected,
                    "acct_••••" + it.takeLast(4),
                )
            } ?: tr(Res.string.stripeNotConnected),
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Primary,
        )
        Text(tr(Res.string.payoutsHelper), style = ShifaTextStyles.metaSmall(), color = ShifaColors.Text.Secondary)
        ShifaSecondaryButton(
            tr(if (account == null) Res.string.connectStripe else Res.string.resumeStripe),
            actions.connect,
            Modifier.fillMaxWidth(),
            loading = state.opening == BillingViewModel.CONNECT,
            enabled = state.opening == null,
        )
    }
}
