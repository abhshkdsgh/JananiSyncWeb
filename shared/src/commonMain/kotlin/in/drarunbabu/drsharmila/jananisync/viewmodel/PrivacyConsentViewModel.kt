package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthRepository
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthSessionState
import `in`.drarunbabu.drsharmila.jananisync.data.privacy.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class PrivacyGate { CHECKING, REQUIRED, ACCEPTED, DENIED, ERROR }

data class PrivacyConsentUiState(
    val userId: String? = null,
    val gate: PrivacyGate = PrivacyGate.CHECKING,
    val isSaving: Boolean = false,
    val error: String? = null,
    val receipt: PrivacyConsentReceipt? = null,
    val history: List<PrivacyConsentEvent> = emptyList(),
    val historyLoading: Boolean = false,
    val historyError: String? = null
) {
    fun allowsAccount(userId: String?): Boolean = userId != null && this.userId == userId && gate == PrivacyGate.ACCEPTED && !isSaving
}

/** Account access depends on a server-confirmed receipt, never a saved local checkbox. */
class PrivacyConsentViewModel(
    private val auth: AuthRepository,
    private val repository: PrivacyConsentRepository
) : ViewModel() {
    private val mutableState = MutableStateFlow(PrivacyConsentUiState())
    val uiState = mutableState.asStateFlow()
    private var stopReceipt: (() -> Unit)? = null
    private var refreshJob: Job? = null
    private var decisionJob: Job? = null
    private var historyJob: Job? = null
    private var nextAccountIsSignup = false
    private var decisionSource = "account_gate"
    // Prevent late requests for the same UID (sign out / sign in) restoring old state.
    private var generation = 0

    init {
        viewModelScope.launch {
            auth.sessionState.map { (it as? AuthSessionState.SignedIn)?.userUid }
                .distinctUntilChanged().collect { userId ->
                    generation++
                    stopReceipt?.invoke()
                    stopReceipt = null
                    refreshJob?.cancel()
                    decisionJob?.cancel()
                    historyJob?.cancel()
                    decisionSource = if (nextAccountIsSignup && userId != null) "signup" else "account_gate"
                    if (userId != null) nextAccountIsSignup = false
                    mutableState.value = PrivacyConsentUiState(userId = userId)
                    if (userId != null) refresh()
                }
        }
    }

    fun preparingSignup() { nextAccountIsSignup = true }
    fun cancelSignupPreparation() { nextAccountIsSignup = false }

    fun refresh() {
        val uid = mutableState.value.userId ?: return
        if (mutableState.value.isSaving) return
        val token = generation
        refreshJob?.cancel()
        stopReceipt?.invoke()
        stopReceipt = null
        mutableState.value = mutableState.value.copy(gate = PrivacyGate.CHECKING, error = null)
        refreshJob = viewModelScope.launch {
            try {
                val receipt = repository.loadReceipt(uid)
                if (!isCurrent(uid, token)) return@launch
                applyReceipt(receipt)
                stopReceipt = repository.observeReceipt(uid, { updated ->
                    if (isCurrent(uid, token) && !mutableState.value.isSaving) applyReceipt(updated)
                }, { message ->
                    if (isCurrent(uid, token)) mutableState.value = mutableState.value.copy(
                        gate = PrivacyGate.ERROR, error = message)
                })
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrent(uid, token)) mutableState.value = mutableState.value.copy(
                    gate = PrivacyGate.ERROR,
                    error = "Could not verify your privacy decision with the server. Reconnect and retry.")
            }
        }
    }

    fun decide(decision: PrivacyDecision, fromSettings: Boolean = false) {
        val state = mutableState.value
        val uid = state.userId ?: return
        if (state.isSaving || state.gate == PrivacyGate.CHECKING) return
        val token = generation
        // Stop clinical use immediately on withdrawal, even if the server write later fails.
        mutableState.value = state.copy(isSaving = true, error = null,
            gate = if (decision == PrivacyDecision.DENIED) PrivacyGate.REQUIRED else state.gate)
        decisionJob = viewModelScope.launch {
            try {
                val receipt = repository.recordDecision(uid, decision, if (fromSettings) "settings" else decisionSource)
                if (!isCurrent(uid, token)) return@launch
                // Another tab may have committed a newer decision while this operation's
                // readback was returning. Re-read and replace the subscription before granting
                // access; ignored snapshots during saving must never hide a newer denial.
                mutableState.value = mutableState.value.copy(isSaving = false, receipt = receipt, gate = PrivacyGate.CHECKING)
                refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrent(uid, token)) mutableState.value = mutableState.value.copy(
                    isSaving = false, gate = PrivacyGate.ERROR,
                    error = "Your decision could not be confirmed. Access stays blocked. Reconnect and retry, or check privacy history.")
            }
        }
    }

    fun reviewAgain() {
        if (mutableState.value.gate == PrivacyGate.DENIED) {
            mutableState.value = mutableState.value.copy(gate = PrivacyGate.REQUIRED)
        }
    }

    fun loadHistory() {
        val uid = mutableState.value.userId ?: return
        if (mutableState.value.historyLoading) return
        val token = generation
        mutableState.value = mutableState.value.copy(historyLoading = true, historyError = null)
        historyJob = viewModelScope.launch {
            try {
                val events = repository.loadHistory(uid)
                if (isCurrent(uid, token)) mutableState.value = mutableState.value.copy(history = events, historyLoading = false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrent(uid, token)) mutableState.value = mutableState.value.copy(historyLoading = false,
                    historyError = "Could not load server history. Reconnect and refresh.")
            }
        }
    }

    private fun isCurrent(uid: String, token: Int): Boolean = generation == token &&
        mutableState.value.userId == uid && (auth.sessionState.value as? AuthSessionState.SignedIn)?.userUid == uid

    private fun applyReceipt(receipt: PrivacyConsentReceipt?) {
        val currentPolicy = receipt?.policyVersion == PrivacyPolicyDocument.VERSION && receipt.policySha256 == PrivacyPolicyDocument.SHA256
        val gate = when {
            !currentPolicy -> PrivacyGate.REQUIRED
            receipt.decision == PrivacyDecision.ACCEPTED -> PrivacyGate.ACCEPTED
            else -> PrivacyGate.DENIED
        }
        mutableState.value = mutableState.value.copy(receipt = receipt, gate = gate, error = null)
    }

    override fun onCleared() {
        stopReceipt?.invoke()
        super.onCleared()
    }
}
