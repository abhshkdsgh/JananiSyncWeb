package `in`.drarunbabu.drsharmila.jananisync.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OrganizationMembershipTransactionTest {
    @Test fun duplicateMembershipFailureReachesCallerWithoutCancellingItsRequest() = runTest {
        val message = "You already have active membership in this organisation. Use Switch organisation."
        val failure = runCatching {
            runMembershipTransaction<Unit>({ callback -> callback(Unit) }) { error(message) }
        }.exceptionOrNull()
        assertIs<IllegalStateException>(failure)
        assertEquals(message, failure.message)
        assertTrue(currentCoroutineContext().isActive)

        // The same caller remains usable after showing the validation snackbar.
        var completed = false
        runMembershipTransaction<Unit>({ callback -> callback(Unit) }) { completed = true }
        assertTrue(completed)
    }

    @Test fun sdkFailureStillUsesSanitizedCodeAndDoesNotCancelCaller() = runTest {
        val reason = js("new Error('sensitive server details')")
        reason.code = "permission-denied"
        val failure = runCatching {
            runMembershipTransaction<Unit>({ _ -> Promise { _, reject -> reject(reason) } }) { error("Not called") }
        }.exceptionOrNull()
        assertEquals("Firebase operation failed (permission-denied).", failure?.message)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test fun cancellingCallerCancelsTheTransactionCallback() = runTest {
        val started = CompletableDeferred<Unit>()
        val blocker = CompletableDeferred<Unit>()
        var callbackCancelled = false
        val request = async {
            runMembershipTransaction<Unit>({ callback -> callback(Unit) }) {
                started.complete(Unit)
                try {
                    blocker.await()
                } catch (error: CancellationException) {
                    callbackCancelled = true
                    throw error
                }
            }
        }
        started.await()
        request.cancel()
        request.join()
        assertTrue(callbackCancelled)
        assertTrue(request.isCancelled)
        assertFalse(blocker.isCompleted)
    }
}
