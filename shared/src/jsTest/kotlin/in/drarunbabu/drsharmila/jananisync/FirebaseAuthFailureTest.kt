package `in`.drarunbabu.drsharmila.jananisync

import `in`.drarunbabu.drsharmila.jananisync.data.auth.toAuthMessage
import `in`.drarunbabu.drsharmila.jananisync.data.auth.toPasswordResetFailure
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FirebaseAuthFailureTest {
    @Test
    fun firestoreRejectionsRetainBareCodesWithoutServerDetails() = runTest {
        for (code in listOf("failed-precondition", "permission-denied", "unavailable")) {
            val reason = js("new Error('sensitive server message')")
            reason.code = code
            val failure = runCatching { Promise<String> { _, reject -> reject(reason) }.awaitFirebase() }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals("Firebase operation failed ($code).", failure.message)
        }
    }

    @Test
    fun nativeRejectionsRetainCodesForAuthMessages() = runTest {
        val cases = mapOf(
            "auth/email-already-in-use" to "An account with this email already exists. Sign in instead.",
            "auth/weak-password" to "Choose a stronger password that meets the password requirements.",
            "auth/operation-not-allowed" to "Email and password sign-in is disabled for this project.",
            "auth/network-request-failed" to "Network unavailable. Reconnect and try again."
        )
        for ((code, expected) in cases) {
            val reason = js("new Error('sensitive server message')")
            reason.code = code
            val failure = runCatching { Promise<String> { _, reject -> reject(reason) }.awaitFirebase() }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(expected, failure.message.toAuthMessage())
            assertFalse(failure.message.orEmpty().contains("sensitive"))
        }
    }

    @Test
    fun missingRejectionDetailsDoNotClaimAConnectionFailure() = runTest {
        val failure = runCatching { js("Promise.reject(null)").unsafeCast<Promise<Unit>>().awaitFirebaseUnit() }.exceptionOrNull()
        assertNotNull(failure)
        assertEquals("Authentication failed. Try again or contact your administrator.", failure.message.toAuthMessage())
    }

    @Test
    fun passwordResetDoesNotRevealMissingAccounts() = runTest {
        val reason = js("new Error('sensitive account details')")
        reason.code = "auth/user-not-found"
        val failure = runCatching { Promise<Unit> { _, reject -> reject(reason) }.awaitFirebaseUnit() }.exceptionOrNull()
        assertNotNull(failure)
        val result = failure.message.toPasswordResetFailure()
        assertTrue(result.isSuccess)
        assertEquals(
            "If an account exists for this email address, a password reset link has been sent. Check your inbox and spam folder.",
            result.message
        )
    }

    @Test
    fun passwordResetFailuresKeepActionableMessages() = runTest {
        for ((code, expected) in mapOf(
            "auth/invalid-email" to "That email address is invalid. Check it and try again.",
            "auth/too-many-requests" to "Too many attempts. Wait a while and try again.",
            "auth/network-request-failed" to "Network unavailable. Reconnect and try again."
        )) {
            val reason = js("new Error('sensitive server details')")
            reason.code = code
            val failure = runCatching { Promise<Unit> { _, reject -> reject(reason) }.awaitFirebaseUnit() }.exceptionOrNull()
            assertNotNull(failure)
            val result = failure.message.toPasswordResetFailure()
            assertFalse(result.isSuccess)
            assertEquals(expected, result.message)
            assertFalse(result.message.contains("sensitive"))
        }
    }
}
