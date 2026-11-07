package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import `in`.drarunbabu.drsharmila.jananisync.data.auth.*
import `in`.drarunbabu.drsharmila.jananisync.data.privacy.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyConsentViewModelTest {
    @Test fun existingAccountNeedsServerReceiptAndExactPolicyHash() = runTest {
        withMain {
            val auth = PrivacyTestAuth()
            val store = PrivacyTestStore()
            val vm = PrivacyConsentViewModel(auth, store)
            auth.sessionState.value = AuthSessionState.SignedIn("alice", true)
            assertEquals(PrivacyGate.CHECKING, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.allowsAccount("alice"))
            store.loading.complete(receipt().copy(policySha256 = "outdated"))
            assertEquals(PrivacyGate.REQUIRED, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.allowsAccount("alice"))
            store.observer?.invoke(receipt())
            assertTrue(vm.uiState.value.allowsAccount("alice"))
            assertFalse(vm.uiState.value.allowsAccount("other-account"))
        }
    }

    @Test fun explicitSignupDecisionWaitsForCommitAndDuplicateClickDoesNotAppend() = runTest {
        withMain {
            val auth = PrivacyTestAuth()
            val store = PrivacyTestStore()
            val vm = PrivacyConsentViewModel(auth, store)
            vm.preparingSignup()
            auth.sessionState.value = AuthSessionState.SignedIn("alice", false)
            store.loading.complete(null)
            vm.decide(PrivacyDecision.ACCEPTED)
            vm.decide(PrivacyDecision.ACCEPTED)
            assertEquals(1, store.decisions.size)
            assertEquals("signup", store.decisions.single().third)
            assertFalse(vm.uiState.value.allowsAccount("alice"))
            store.committing.complete(receipt())
            assertTrue(vm.uiState.value.allowsAccount("alice"))
            // Consent does not itself assert email verification.
            assertEquals(AuthSessionState.SignedIn("alice", false), auth.sessionState.value)
        }
    }

    @Test fun denialImmediatelyBlocksAndOldObserverCannotRestoreAccessWhileSaving() = runTest {
        withMain {
            val auth = PrivacyTestAuth()
            val store = PrivacyTestStore()
            val vm = PrivacyConsentViewModel(auth, store)
            auth.sessionState.value = AuthSessionState.SignedIn("alice", true)
            store.loading.complete(receipt())
            assertTrue(vm.uiState.value.allowsAccount("alice"))
            vm.decide(PrivacyDecision.DENIED, fromSettings = true)
            store.observer?.invoke(receipt())
            assertFalse(vm.uiState.value.allowsAccount("alice"))
            assertEquals("settings", store.decisions.single().third)
            store.committing.complete(receipt(PrivacyDecision.DENIED))
            assertEquals(PrivacyGate.DENIED, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.allowsAccount("alice"))
            vm.reviewAgain()
            assertEquals(PrivacyGate.REQUIRED, vm.uiState.value.gate)
        }
    }

    @Test fun failedWritesStayBlockedAndNeverFabricateAcceptance() = runTest {
        withMain {
            val auth = PrivacyTestAuth()
            val store = PrivacyTestStore()
            val vm = PrivacyConsentViewModel(auth, store)
            auth.sessionState.value = AuthSessionState.SignedIn("alice", true)
            store.loading.complete(null)
            vm.decide(PrivacyDecision.ACCEPTED)
            store.committing.completeExceptionally(IllegalStateException("offline"))
            assertEquals(PrivacyGate.ERROR, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.allowsAccount("alice"))
            assertNotNull(vm.uiState.value.error)
            assertNull(vm.uiState.value.receipt)
        }
    }

    @Test fun newerConcurrentDenialWinsOverReturningAcceptance() = runTest {
        withMain {
            val auth = PrivacyTestAuth()
            val store = PrivacyTestStore()
            val vm = PrivacyConsentViewModel(auth, store)
            auth.sessionState.value = AuthSessionState.SignedIn("alice", true)
            store.loading.complete(null)
            vm.decide(PrivacyDecision.ACCEPTED)
            // A separate tab's newer server decision arrives before our readback returns.
            store.serverReceiptAfterCommit = receipt(PrivacyDecision.DENIED).copy(lastEventId = "newer-denial", updatedAtMillis = 1_800_000_000_001)
            store.observer?.invoke(store.serverReceiptAfterCommit)
            store.committing.complete(receipt())
            assertEquals(PrivacyGate.DENIED, vm.uiState.value.gate)
            assertEquals("newer-denial", vm.uiState.value.receipt?.lastEventId)
            assertFalse(vm.uiState.value.allowsAccount("alice"))
        }
    }

    @Test fun serverFailureAndLiveDenialBothRevokeAccess() = runTest {
        withMain {
            val auth = PrivacyTestAuth()
            val store = PrivacyTestStore()
            val vm = PrivacyConsentViewModel(auth, store)
            auth.sessionState.value = AuthSessionState.SignedIn("alice", true)
            store.loading.complete(receipt())
            store.observer?.invoke(receipt(PrivacyDecision.DENIED))
            assertEquals(PrivacyGate.DENIED, vm.uiState.value.gate)
            store.observer?.invoke(receipt())
            store.observerError?.invoke("Permission denied")
            assertEquals(PrivacyGate.ERROR, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.allowsAccount("alice"))
        }
    }

    @Test fun lateOldSessionCallbackCannotUnlockAnotherAccountOrLeakHistory() = runTest {
        withMain {
            val auth = PrivacyTestAuth()
            val store = PrivacyTestStore()
            val vm = PrivacyConsentViewModel(auth, store)
            auth.sessionState.value = AuthSessionState.SignedIn("alice", true)
            store.loading.complete(receipt())
            val oldCallback = store.observer
            vm.loadHistory()
            auth.sessionState.value = AuthSessionState.SignedOut
            store.loading = CompletableDeferred()
            auth.sessionState.value = AuthSessionState.SignedIn("bob", true)
            oldCallback?.invoke(receipt())
            assertEquals("bob", vm.uiState.value.userId)
            assertFalse(vm.uiState.value.allowsAccount("bob"))
            assertTrue(vm.uiState.value.history.isEmpty())
            assertTrue(store.unsubscribed)
            store.loading.complete(null)
            assertEquals(PrivacyGate.REQUIRED, vm.uiState.value.gate)
        }
    }

    @Test fun failedInitialCheckDoesNotTreatCachedOrMissingStateAsAcceptance() = runTest {
        withMain {
            val auth = PrivacyTestAuth()
            val store = PrivacyTestStore()
            val vm = PrivacyConsentViewModel(auth, store)
            auth.sessionState.value = AuthSessionState.SignedIn("alice", true)
            store.loading.completeExceptionally(IllegalStateException("server unavailable"))
            assertEquals(PrivacyGate.ERROR, vm.uiState.value.gate)
            assertFalse(vm.uiState.value.allowsAccount("alice"))
        }
    }

    private suspend fun TestScope.withMain(test: suspend () -> Unit) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try { test() } finally { Dispatchers.resetMain() }
    }
}

private fun receipt(decision: PrivacyDecision = PrivacyDecision.ACCEPTED) = PrivacyConsentReceipt(
    "event-1", decision, PrivacyPolicyDocument.VERSION, PrivacyPolicyDocument.SHA256, 1_800_000_000_000)

private class PrivacyTestStore : PrivacyConsentRepository {
    var loading = CompletableDeferred<PrivacyConsentReceipt?>()
    val committing = CompletableDeferred<PrivacyConsentReceipt>()
    val decisions = mutableListOf<Triple<String, PrivacyDecision, String>>()
    var observer: ((PrivacyConsentReceipt?) -> Unit)? = null
    var observerError: ((String) -> Unit)? = null
    var unsubscribed = false
    var serverReceiptAfterCommit: PrivacyConsentReceipt? = null
    override suspend fun loadReceipt(userId: String) = loading.await()
    override suspend fun recordDecision(userId: String, decision: PrivacyDecision, source: String): PrivacyConsentReceipt {
        decisions += Triple(userId, decision, source)
        return committing.await().also { loading = CompletableDeferred(serverReceiptAfterCommit ?: it) }
    }
    override suspend fun loadHistory(userId: String): List<PrivacyConsentEvent> = emptyList()
    override fun observeReceipt(userId: String, onReceipt: (PrivacyConsentReceipt?) -> Unit, onError: (String) -> Unit): () -> Unit {
        observer = onReceipt
        observerError = onError
        return { unsubscribed = true }
    }
}

private class PrivacyTestAuth : AuthRepository {
    override val sessionState = MutableStateFlow<AuthSessionState>(AuthSessionState.SignedOut)
    override fun currentVerifiedUserUid(): String? = (sessionState.value as? AuthSessionState.SignedIn)?.takeIf { it.emailVerified }?.userUid
    override suspend fun signIn(email: String, password: String): AuthOperationResult = error("Unused")
    override suspend fun createAccount(email: String, password: String): AuthOperationResult = error("Unused")
    override suspend fun sendPasswordResetEmail(email: String): AuthOperationResult = error("Unused")
    override suspend fun resendVerificationEmail(): AuthOperationResult = error("Unused")
    override suspend fun refreshEmailVerification(): AuthOperationResult = error("Unused")
    override suspend fun signOut(): AuthOperationResult = error("Unused")
}
