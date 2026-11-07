package `in`.drarunbabu.drsharmila.jananisync.data.auth

import kotlinx.coroutines.flow.StateFlow

data class AuthOperationResult(
    val isSuccess: Boolean,
    val message: String,
    val requiresEmailVerification: Boolean = false
)

sealed interface AuthSessionState {
    data object Loading : AuthSessionState
    data object SignedOut : AuthSessionState
    data class SignedIn(val userUid: String, val emailVerified: Boolean) : AuthSessionState
}

interface AuthRepository {
    val sessionState: StateFlow<AuthSessionState>
    suspend fun signIn(email: String, password: String): AuthOperationResult
    suspend fun createAccount(email: String, password: String): AuthOperationResult
    suspend fun sendPasswordResetEmail(email: String): AuthOperationResult
    suspend fun resendVerificationEmail(): AuthOperationResult
    suspend fun refreshEmailVerification(): AuthOperationResult
    fun currentVerifiedUserUid(): String?
    suspend fun signOut(): AuthOperationResult
}

expect fun createAuthRepository(): AuthRepository
