package shifa.doctor.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.legal.DoctorContract
import shifa.doctor.data.legal.LegalLinks
import shifa.doctor.data.legal.LegalRepository
import shifa.doctor.data.legal.legalRepository
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.ProfileEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.contractLoadFailed
import shifa.doctor.ui.emailcode.CodeBanner

data class AboutUiState(
    // The backend's legal pages (null: not read, or a backend without them: the public privacy page only).
    val links: LegalLinks? = null,
    val contract: DoctorContract? = null,
    // The contract viewer: open, its PDF once read, a failure to read it.
    val viewing: Boolean = false,
    val pdf: ByteArray? = null,
    val pdfError: CodeBanner? = null,
)

/**
 * About's legal part (LEGAL-01/02, board `DaProfile` legalAbout, BE-16): Terms, Privacy and Licences as the backend
 * lists them, and "My partnership contract" when the doctor has one, opened in the app. Nothing here blocks the screen:
 * a failed read leaves the public privacy page.
 */
class AboutViewModel(
    // Clinic staff have no contract (the route is the doctor's).
    private val doctor: Boolean,
    private val language: () -> String = { languageStorage.language.value.backendCode },
    private val repo: LegalRepository = legalRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(AboutUiState())
    val state: StateFlow<AboutUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.links(language()).onSuccess { l -> _state.update { it.copy(links = l) } } }
        if (doctor) viewModelScope.launch { repo.contract().onSuccess { c -> _state.update { it.copy(contract = c) } } }
    }

    fun openContract() {
        tracker.track(ProfileEvents.ContractOpened)
        _state.update { it.copy(viewing = true) }
        if (_state.value.pdf == null) loadPdf()
    }

    fun closeContract() = _state.update { it.copy(viewing = false) }

    fun loadPdf() {
        _state.update { it.copy(pdfError = null) }
        viewModelScope.launch {
            repo.contractPdf().fold(
                { bytes -> _state.update { it.copy(pdf = bytes) } },
                { e ->
                    tracker.track(ProfileEvents.ContractPdfFailed(e.toFailureReason()))
                    _state.update {
                        it.copy(
                            pdfError =
                                CodeBanner(
                                    e.toUserMessage(Res.string.contractLoadFailed),
                                    requestId = e.requestId,
                                ),
                        )
                    }
                },
            )
        }
    }
}
