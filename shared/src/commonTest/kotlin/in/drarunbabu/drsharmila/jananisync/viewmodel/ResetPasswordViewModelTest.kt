package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthOperationResult
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthRepository
import `in`.drarunbabu.drsharmila.jananisync.data.auth.AuthSessionState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {
    @Test fun invalidEmailNeverSendsAResetRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = ResetPasswordRepository()
            val model = ResetPasswordViewModel(auth)
            for (email in listOf("", "   ", "nurse", "nurse@", "nurse@ example.test")) {
                model.sendResetLink(email)
                assertFalse(model.uiState.value.isLoading)
                assertFalse(model.uiState.value.isSuccess)
                assertNotNull(model.uiState.value.message)
            }
            runCurrent()
            assertTrue(auth.sentEmails.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun loadingStartsImmediatelyAndDuplicateSubmissionsAreIgnored() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = ResetPasswordRepository()
            val model = ResetPasswordViewModel(auth)
            model.sendResetLink("nurse@example.test")
            assertTrue(model.uiState.value.isLoading)
            model.sendResetLink("other@example.test")
            runCurrent()
            assertEquals(listOf("nurse@example.test"), auth.sentEmails)
            assertTrue(model.uiState.value.isLoading)
            model.sendResetLink("other@example.test")
            runCurrent()
            assertEquals(1, auth.sentEmails.size)
            auth.response.complete(AuthOperationResult(true, "Check your email."))
            runCurrent()
            assertFalse(model.uiState.value.isLoading)
            assertTrue(model.uiState.value.isSuccess)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun successTrimsEmailAndLeavesTheUserSignedOut() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = ResetPasswordRepository()
            val model = ResetPasswordViewModel(auth)
            model.sendResetLink("  nurse@example.test  ")
            runCurrent()
            assertEquals(listOf("nurse@example.test"), auth.sentEmails)
            val confirmation = "If an account exists, a password reset link has been sent."
            auth.response.complete(AuthOperationResult(true, confirmation))
            runCurrent()
            assertTrue(model.uiState.value.isSuccess)
            assertEquals(confirmation, model.uiState.value.message)
            assertEquals(AuthSessionState.SignedOut, auth.sessionState.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun aFailedRequestCanBeRetriedAndClearsPreviousFeedback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = ResetPasswordRepository()
            val model = ResetPasswordViewModel(auth)
            model.sendResetLink("nurse@example.test")
            runCurrent()
            auth.response.complete(AuthOperationResult(false, "Network unavailable. Reconnect and try again."))
            runCurrent()
            assertFalse(model.uiState.value.isLoading)
            assertFalse(model.uiState.value.isSuccess)
            assertEquals("Network unavailable. Reconnect and try again.", model.uiState.value.message)

            auth.response = CompletableDeferred()
            model.sendResetLink("nurse@example.test")
            assertTrue(model.uiState.value.isLoading)
            assertNull(model.uiState.value.message)
            runCurrent()
            assertEquals(2, auth.sentEmails.size)
            auth.response.complete(AuthOperationResult(true, "Check your email."))
            runCurrent()
            assertTrue(model.uiState.value.isSuccess)
            assertEquals("Check your email.", model.uiState.value.message)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun clearingFeedbackResetsSuccessWithoutEndingAnInFlightRequest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = ResetPasswordRepository()
            val model = ResetPasswordViewModel(auth)
            model.sendResetLink("nurse@example.test")
            runCurrent()
            model.clearMessage()
            assertTrue(model.uiState.value.isLoading)
            auth.response.complete(AuthOperationResult(true, "Check your email."))
            runCurrent()
            model.clearMessage()
            assertFalse(model.uiState.value.isSuccess)
            assertNull(model.uiState.value.message)
            assertFalse(model.uiState.value.isLoading)
        } finally { Dispatchers.resetMain() }
    }
}

private class ResetPasswordRepository : AuthRepository {
    override val sessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.SignedOut)
    var response = CompletableDeferred<AuthOperationResult>()
    val sentEmails = mutableListOf<String>()

    override suspend fun sendPasswordResetEmail(email: String): AuthOperationResult {
        sentEmails += email
        return response.await()
    }

    override suspend fun signIn(email: String, password: String): AuthOperationResult = error("Unused")
    override suspend fun createAccount(email: String, password: String): AuthOperationResult = error("Unused")
    override suspend fun resendVerificationEmail(): AuthOperationResult = error("Unused")
    override suspend fun refreshEmailVerification(): AuthOperationResult = error("Unused")
    override fun currentVerifiedUserUid(): String? = null
    override suspend fun signOut(): AuthOperationResult = error("Unused")
}
