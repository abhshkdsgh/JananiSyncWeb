package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import `in`.drarunbabu.drsharmila.jananisync.data.auth.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AccountFlowTest {
    @Test fun signupStaysBusyUntilVerificationEmailFinishesDespiteEarlyAuthEvent() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val auth = AccountFlowRepository()
            val model = SignUpViewModel(auth)
            model.createAccount(" nurse@example.test ", "password", "password")
            assertEquals(AuthSessionState.SignedIn("new-user", false), auth.sessionState.value)
            assertTrue(model.uiState.value.isLoading)
            assertFalse(model.uiState.value.emailVerificationRequired)
            model.createAccount("nurse@example.test", "password", "password")
            assertEquals(1, auth.createCalls)
            auth.creation.complete(AuthOperationResult(true, "Verification sent", true))
            assertFalse(model.uiState.value.isLoading)
            assertTrue(model.uiState.value.emailVerificationRequired)
            assertEquals("nurse@example.test", auth.createdEmail)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun checkingUnverifiedEmailAndResendingNeverIndicateVerifiedNavigation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val auth = AccountFlowRepository()
            val model = EmailVerificationViewModel(auth)
            model.resend()
            assertFalse(model.uiState.value.emailVerified)
            model.checkVerification()
            model.checkVerification()
            assertEquals(1, auth.checkCalls)
            assertTrue(model.uiState.value.isLoading)
            auth.check.complete(AuthOperationResult(false, "Not verified yet", true))
            assertFalse(model.uiState.value.emailVerified)
            assertTrue(model.uiState.value.emailVerificationRequired)
            assertFalse(model.uiState.value.isLoading)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun verifiedCheckPublishesVerifiedSessionBeforeSuccess() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val auth = AccountFlowRepository()
            val model = EmailVerificationViewModel(auth)
            model.checkVerification()
            auth.check.complete(AuthOperationResult(true, "Verified"))
            assertEquals(AuthSessionState.SignedIn("new-user", true), auth.sessionState.value)
            assertTrue(model.uiState.value.emailVerified)
            assertFalse(model.uiState.value.emailVerificationRequired)
        } finally { Dispatchers.resetMain() }
    }
}

private class AccountFlowRepository : AuthRepository {
    override val sessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.SignedOut)
    val creation = CompletableDeferred<AuthOperationResult>()
    val check = CompletableDeferred<AuthOperationResult>()
    var createCalls = 0
    var checkCalls = 0
    var createdEmail = ""
    override suspend fun createAccount(email: String, password: String): AuthOperationResult {
        createCalls++
        createdEmail = email
        sessionState.value = AuthSessionState.SignedIn("new-user", false)
        return creation.await()
    }
    override suspend fun refreshEmailVerification(): AuthOperationResult {
        checkCalls++
        val result = check.await()
        if (result.isSuccess && !result.requiresEmailVerification) {
            sessionState.value = AuthSessionState.SignedIn("new-user", true)
        }
        return result
    }
    override suspend fun resendVerificationEmail() = AuthOperationResult(true, "Sent", true)
    override suspend fun sendPasswordResetEmail(email: String): AuthOperationResult = error("Unused")
    override suspend fun signIn(email: String, password: String): AuthOperationResult = error("Unused")
    override fun currentVerifiedUserUid() = (sessionState.value as? AuthSessionState.SignedIn)?.takeIf { it.emailVerified }?.userUid
    override suspend fun signOut(): AuthOperationResult = error("Unused")
}
