package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResetPasswordUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val message: String? = null
)

class ResetPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ResetPasswordUiState())
    val uiState = mutableUiState.asStateFlow()

    fun sendResetLink(email: String) {
        if (mutableUiState.value.isLoading) return
        val trimmedEmail = email.trim()
        val validationMessage = when {
            trimmedEmail.isBlank() -> "Enter your username (your email address) to reset your JananiSync password."
            !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(trimmedEmail) -> "Enter a valid email address as your username."
            else -> null
        }
        if (validationMessage != null) {
            mutableUiState.value = ResetPasswordUiState(message = validationMessage)
            return
        }
        // Set this before launching so rapid submissions cannot send duplicate emails.
        mutableUiState.value = ResetPasswordUiState(isLoading = true)
        viewModelScope.launch {
            val result = authRepository.sendPasswordResetEmail(trimmedEmail)
            mutableUiState.value = ResetPasswordUiState(
                isSuccess = result.isSuccess,
                message = result.message
            )
        }
    }

    fun clearMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = null, isSuccess = false)
    }
}
