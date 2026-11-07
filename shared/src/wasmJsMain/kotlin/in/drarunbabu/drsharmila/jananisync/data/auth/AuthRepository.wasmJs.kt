package `in`.drarunbabu.drsharmila.jananisync.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

actual fun createAuthRepository(): AuthRepository = object : AuthRepository {
    private val mutableSession = MutableStateFlow<AuthSessionState>(AuthSessionState.SignedOut)
    override val sessionState = mutableSession.asStateFlow()
    override suspend fun signIn(email: String, password: String) = AuthOperationResult(
        isSuccess = false,
        message = "Firebase Authentication is configured for the Kotlin/JS web app only."
    )

    override suspend fun createAccount(email: String, password: String) = AuthOperationResult(
        isSuccess = false,
        message = "Firebase Authentication is configured for the Kotlin/JS web app only."
    )

    override suspend fun resendVerificationEmail() = AuthOperationResult(
        isSuccess = false,
        message = "Firebase Authentication is configured for the Kotlin/JS web app only."
    )

    override suspend fun sendPasswordResetEmail(email: String) = AuthOperationResult(
        isSuccess = false,
        message = "Firebase Authentication is configured for the Kotlin/JS web app only."
    )

    override suspend fun refreshEmailVerification() = AuthOperationResult(
        isSuccess = false,
        message = "Firebase Authentication is configured for the Kotlin/JS web app only."
    )

    override fun currentVerifiedUserUid(): String? = null

    override suspend fun signOut() = AuthOperationResult(
        isSuccess = false,
        message = "Firebase Authentication is configured for the Kotlin/JS web app only."
    )
}
