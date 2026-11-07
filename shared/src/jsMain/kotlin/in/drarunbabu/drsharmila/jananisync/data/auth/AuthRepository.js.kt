package `in`.drarunbabu.drsharmila.jananisync.data.auth

import `in`.drarunbabu.drsharmila.jananisync.FirebaseService
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebase
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebaseUnit
import `in`.drarunbabu.drsharmila.jananisync.data.RememberedOrganizationKeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

actual fun createAuthRepository(): AuthRepository = FirebaseAuthRepository()

private class FirebaseAuthRepository : AuthRepository {
    private val mutableSessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.Loading)
    override val sessionState = mutableSessionState.asStateFlow()

    init {
        FirebaseService.initialize()
        FirebaseService.observeAuthState { user ->
            mutableSessionState.value = if (user == null) {
                AuthSessionState.SignedOut
            } else {
                AuthSessionState.SignedIn(user.uid, user.emailVerified)
            }
        }
        FirebaseService.awaitAuthStateReady()
            .then {
                if (mutableSessionState.value == AuthSessionState.Loading) {
                    mutableSessionState.value = FirebaseService.currentUser?.let { user ->
                        AuthSessionState.SignedIn(user.uid, user.emailVerified)
                    } ?: AuthSessionState.SignedOut
                }
                Unit
            }
            .catch {
                if (mutableSessionState.value == AuthSessionState.Loading) {
                    mutableSessionState.value = AuthSessionState.SignedOut
                }
                Unit
            }
    }

    override fun currentVerifiedUserUid(): String? = FirebaseService.currentUser
        ?.takeIf { it.emailVerified }
        ?.uid

    override suspend fun signIn(email: String, password: String): AuthOperationResult = try {
        val user = FirebaseService.signIn(email, password).awaitFirebase()
        if (user.emailVerified) {
            AuthOperationResult(isSuccess = true, message = "Signed in successfully.")
        } else {
            AuthOperationResult(
                isSuccess = false,
                message = "Verify your email address before using JananiSync.",
                requiresEmailVerification = true
            )
        }
    } catch (error: Throwable) {
        AuthOperationResult(isSuccess = false, message = error.message.toAuthMessage())
    }

    override suspend fun createAccount(email: String, password: String): AuthOperationResult = try {
        FirebaseService.createAccount(email, password).awaitFirebase()
        try {
            FirebaseService.sendVerificationEmail().awaitFirebaseUnit()
            AuthOperationResult(true, "Account created. Check your email for a verification link.", true)
        } catch (_: Throwable) {
            AuthOperationResult(true, "Account created, but the verification email could not be sent. Try resending it.", true)
        }
    } catch (error: Throwable) {
        AuthOperationResult(false, error.message.toAuthMessage())
    }

    override suspend fun resendVerificationEmail(): AuthOperationResult = try {
        FirebaseService.sendVerificationEmail().awaitFirebaseUnit()
        AuthOperationResult(true, "A new verification email has been sent.", true)
    } catch (error: Throwable) {
        AuthOperationResult(false, error.message.toAuthMessage(), true)
    }

    override suspend fun sendPasswordResetEmail(email: String): AuthOperationResult = try {
        FirebaseService.sendPasswordResetEmail(email.trim()).awaitFirebaseUnit()
        passwordResetEmailAccepted()
    } catch (error: Throwable) {
        error.message.toPasswordResetFailure()
    }

    override suspend fun refreshEmailVerification(): AuthOperationResult = try {
        val isVerified = FirebaseService.refreshEmailVerification()
        // reload/getIdToken refresh Firebase's user, but onAuthStateChanged need not emit.
        // Publish the refreshed session before navigation evaluates the verified-user guard.
        val user = FirebaseService.currentUser
        mutableSessionState.value = user?.let { AuthSessionState.SignedIn(it.uid, it.emailVerified && isVerified) }
            ?: AuthSessionState.SignedOut
        if (isVerified && user != null) {
            AuthOperationResult(true, "Email verified. Your account is ready.")
        } else {
            AuthOperationResult(false, "Your email is not verified yet. Open the link in your inbox, then try again.", true)
        }
    } catch (error: Throwable) {
        AuthOperationResult(false, error.message.toAuthMessage(), true)
    }

    override suspend fun signOut(): AuthOperationResult = try {
        val userId = FirebaseService.currentUser?.uid
        val cacheClearFailed = userId != null && runCatching {
            RememberedOrganizationKeyStore.clearUser(userId)
        }.isFailure
        FirebaseService.signOut().awaitFirebaseUnit()
        AuthOperationResult(
            isSuccess = true,
            message = if (cacheClearFailed) {
                "You have been signed out, but this browser could not clear its remembered organisation key. Close this browser profile and notify your administrator."
            } else "You have been signed out."
        )
    } catch (error: Throwable) {
        AuthOperationResult(isSuccess = false, message = error.message.toAuthMessage())
    }
}

private fun passwordResetEmailAccepted() = AuthOperationResult(
    true,
    "If an account exists for this email address, a password reset link has been sent. Check your inbox and spam folder."
)

internal fun String?.toPasswordResetFailure(): AuthOperationResult {
    // Older projects can return user-not-found instead of suppressing account existence.
    // Use the same confirmation as a successful request in either configuration.
    return if ("auth/user-not-found" in this.orEmpty()) passwordResetEmailAccepted()
    else AuthOperationResult(false, toAuthMessage())
}

internal fun String?.toAuthMessage(): String {
    val errorText = this.orEmpty()
    return when {
        "auth/email-already-in-use" in errorText -> "An account with this email already exists. Sign in instead."
        "auth/weak-password" in errorText ||
            "auth/password-does-not-meet-requirements" in errorText -> "Choose a stronger password that meets the password requirements."
        "auth/invalid-email" in errorText -> "That email address is invalid. Check it and try again."
        "auth/invalid-credential" in errorText ||
            "auth/wrong-password" in errorText ||
            "auth/user-not-found" in errorText -> "Email or password is incorrect."
        "auth/user-disabled" in errorText -> "This account is disabled. Contact your administrator."
        "auth/too-many-requests" in errorText -> "Too many attempts. Wait a while and try again."
        "auth/network-request-failed" in errorText -> "Network unavailable. Reconnect and try again."
        "auth/operation-not-allowed" in errorText -> "Email and password sign-in is disabled for this project."
        "auth/invalid-api-key" in errorText ||
            "auth/app-not-authorized" in errorText ||
            "auth/configuration-not-found" in errorText -> "Authentication is not configured correctly. Contact your administrator."
        else -> {
            val code = Regex("auth/[a-z-]+").find(errorText)?.value
            if (code != null) "Authentication failed ($code). Try again or contact your administrator."
            else "Authentication failed. Try again or contact your administrator."
        }
    }
}
