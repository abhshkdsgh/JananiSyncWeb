package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val requiresEmailVerification: Boolean = false,
    val message: String? = null
)

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableUiState.asStateFlow()

    fun signIn(email: String, password: String) {
        if (mutableUiState.value.isLoading) return
        viewModelScope.launch {
            mutableUiState.value = LoginUiState(isLoading = true)
            val result = authRepository.signIn(email.trim(), password)
            mutableUiState.value = LoginUiState(
                isSignedIn = result.isSuccess,
                requiresEmailVerification = result.requiresEmailVerification,
                message = result.message
            )
        }
    }

    fun clearMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = null)
    }
}
