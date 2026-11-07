package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AccountUiState(
    val isLoading: Boolean = false,
    val emailVerificationRequired: Boolean = false,
    val emailVerified: Boolean = false,
    val message: String? = null
)

class SignUpViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = mutableUiState.asStateFlow()

    fun createAccount(email: String, password: String, confirmPassword: String) {
        if (mutableUiState.value.isLoading) return
        if (password != confirmPassword) {
            mutableUiState.value = AccountUiState(message = "Passwords do not match.")
            return
        }
        viewModelScope.launch {
            mutableUiState.value = AccountUiState(isLoading = true)
            val result = authRepository.createAccount(email.trim(), password)
            mutableUiState.value = AccountUiState(
                emailVerificationRequired = result.requiresEmailVerification,
                message = result.message
            )
        }
    }

    fun clearMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = null)
    }
}

class EmailVerificationViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AccountUiState(emailVerificationRequired = true))
    val uiState: StateFlow<AccountUiState> = mutableUiState.asStateFlow()

    fun resend() = perform(AuthRepository::resendVerificationEmail)

    fun checkVerification() = perform(AuthRepository::refreshEmailVerification)

    private fun perform(operation: suspend AuthRepository.() -> `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthOperationResult) {
        if (mutableUiState.value.isLoading) return
        viewModelScope.launch {
            mutableUiState.value = mutableUiState.value.copy(isLoading = true, message = null)
            val result = authRepository.operation()
            mutableUiState.value = AccountUiState(
                emailVerificationRequired = result.requiresEmailVerification,
                emailVerified = result.isSuccess && !result.requiresEmailVerification,
                message = result.message
            )
        }
    }

    fun clearMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = null)
    }
}
