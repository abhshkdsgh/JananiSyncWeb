@file:Suppress("NON_EXPORTABLE_TYPE")

package `in`.drarunbabu.drsharmila.jananisync.data.privacy

import `in`.drarunbabu.drsharmila.jananisync.DocumentReference
import `in`.drarunbabu.drsharmila.jananisync.FirebaseService
import `in`.drarunbabu.drsharmila.jananisync.FirebaseUser
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebase
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebaseUnit
import `in`.drarunbabu.drsharmila.jananisync.collection
import `in`.drarunbabu.drsharmila.jananisync.doc
import `in`.drarunbabu.drsharmila.jananisync.getDocFromServer
import `in`.drarunbabu.drsharmila.jananisync.getDocsFromServer
import `in`.drarunbabu.drsharmila.jananisync.limit
import `in`.drarunbabu.drsharmila.jananisync.orderBy
import `in`.drarunbabu.drsharmila.jananisync.onSnapshot
import `in`.drarunbabu.drsharmila.jananisync.query
import `in`.drarunbabu.drsharmila.jananisync.runTransaction
import `in`.drarunbabu.drsharmila.jananisync.serverTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.promise
import kotlin.js.jsTypeOf

actual fun createPrivacyConsentRepository(): PrivacyConsentRepository = FirebasePrivacyConsentRepository()

private class FirebasePrivacyConsentRepository : PrivacyConsentRepository {
    private val db get() = FirebaseService.rotationFirestore

    private fun requireCurrentAccount(userId: String): FirebaseUser {
        require(userId.isNotBlank() && userId.length <= 128) { "Invalid account identifier." }
        return FirebaseService.currentUser?.takeIf { it.uid == userId }
            ?: error("Your sign-in changed. Sign in again to review your privacy choices.")
    }

    private fun receiptReference(userId: String): DocumentReference = doc(db, "users/$userId/privacy/current")

    override suspend fun loadReceipt(userId: String): PrivacyConsentReceipt? {
        requireCurrentAccount(userId)
        val snapshot = getDocFromServer(receiptReference(userId)).awaitFirebase()
        requireCurrentAccount(userId)
        check(!snapshot.metadata.fromCache && !snapshot.metadata.hasPendingWrites) {
            "Reconnect to check your privacy decision with the server."
        }
        return if (snapshot.exists()) receipt(snapshot.data(), userId) else null
    }

    override fun observeReceipt(
        userId: String,
        onReceipt: (PrivacyConsentReceipt?) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        requireCurrentAccount(userId)
        var active = true
        fun isCurrentAccount(): Boolean = active && FirebaseService.currentUser?.uid == userId
        val stop = onSnapshot(receiptReference(userId), js("({includeMetadataChanges: true})"), { snapshot ->
            if (isCurrentAccount() && !snapshot.metadata.fromCache && !snapshot.metadata.hasPendingWrites) {
                val result = runCatching {
                    if (snapshot.exists()) receipt(snapshot.data(), userId) else null
                }
                if (isCurrentAccount()) {
                    result.fold(onSuccess = onReceipt, onFailure = {
                        onError("Your privacy decision could not be verified. Reconnect and retry.")
                    })
                }
            }
        }, { _ ->
            if (isCurrentAccount()) onError("Your privacy decision could not be checked. Reconnect and retry.")
        })
        return { active = false; stop() }
    }

    override suspend fun recordDecision(
        userId: String,
        decision: PrivacyDecision,
        source: String
    ): PrivacyConsentReceipt {
        val user = requireCurrentAccount(userId)
        require(source in setOf("signup", "account_gate", "settings")) { "Invalid privacy decision source." }
        val email = user.email ?: error("Your account email is unavailable. Sign in again.")
        require(email.isNotBlank() && email.length <= 320) { "Your account email is invalid." }
        // Keep one event ID and one set of client observations across transaction retries.
        val eventReference = doc(collection(db, "users/$userId/privacyEvents"))
        val currentReference = receiptReference(userId)
        val wireDecision = if (decision == PrivacyDecision.ACCEPTED) "accepted" else "denied"
        val clientTime = js("Date.now()").unsafeCast<Double>()
        val language = browserString("language", 64)
        val userAgent = browserString("userAgent", 512)
        val context = currentCoroutineContext()

        // Transactions fail offline: unlike a queued batch, a stale offline acceptance cannot
        // unexpectedly replace a later denial after this browser reconnects.
        runTransaction(db) { transaction ->
            CoroutineScope(context).promise {
                requireCurrentAccount(userId)
                // Reading current serializes decisions made by multiple browser tabs.
                transaction.get(currentReference).awaitFirebase()
                val existing = transaction.get(eventReference).awaitFirebase()
                check(!existing.exists()) { "This privacy event has already been recorded. Refresh your history." }
                requireCurrentAccount(userId)

                val event = js("({})")
                event.schemaVersion = 1
                event.userId = userId
                event.email = email
                event.emailVerified = user.emailVerified
                event.policyVersion = PrivacyPolicyDocument.VERSION
                event.policySha256 = PrivacyPolicyDocument.SHA256
                event.decision = wireDecision
                event.source = source
                event.presentationVersion = PrivacyPolicyDocument.PRESENTATION_VERSION
                event.createdAt = serverTimestamp()
                event.clientOccurredAtMillis = clientTime
                event.language = language
                event.userAgent = userAgent

                val current = js("({})")
                current.userId = userId
                current.lastEventId = eventReference.id
                current.decision = wireDecision
                current.policyVersion = PrivacyPolicyDocument.VERSION
                current.policySha256 = PrivacyPolicyDocument.SHA256
                current.updatedAt = serverTimestamp()

                transaction.set(eventReference, event)
                transaction.set(currentReference, current)
            }
        }.awaitFirebaseUnit()

        // Never report success from a local pending-write snapshot or a client timestamp.
        val confirmed = loadReceipt(userId)
            ?: error("The server receipt is unavailable. Reconnect and check your privacy history.")
        check(confirmed.lastEventId == eventReference.id && confirmed.decision == decision) {
            "Your privacy decision changed in another session. Refresh to check the latest choice."
        }
        return confirmed
    }

    override suspend fun loadHistory(userId: String): List<PrivacyConsentEvent> {
        requireCurrentAccount(userId)
        val snapshot = getDocsFromServer(query(collection(db, "users/$userId/privacyEvents"),
            orderBy("createdAt", "desc"), limit(100))).awaitFirebase()
        requireCurrentAccount(userId)
        check(!snapshot.metadata.fromCache && !snapshot.metadata.hasPendingWrites) {
            "Reconnect to load your privacy history from the server."
        }
        return snapshot.docs.map { document ->
            check(!document.metadata.hasPendingWrites) { "A privacy decision is still awaiting server confirmation." }
            val data = document.data()
            check(data.userId == userId) { "The privacy history belongs to a different account." }
            PrivacyConsentEvent(
                id = document.id,
                userId = userId,
                policyVersion = requiredString(data.policyVersion),
                policySha256 = requiredString(data.policySha256),
                decision = decision(data.decision),
                source = requiredString(data.source),
                recordedAtMillis = serverMillis(data.createdAt),
                email = requiredString(data.email),
                emailVerified = data.emailVerified as Boolean,
                clientOccurredAtMillis = (data.clientOccurredAtMillis as Double).toLong(),
                language = requiredString(data.language),
                userAgent = requiredString(data.userAgent),
                schemaVersion = data.schemaVersion as Int,
                presentationVersion = data.presentationVersion as Int
            )
        }
    }

    private fun receipt(data: dynamic, userId: String): PrivacyConsentReceipt {
        check(data.userId == userId) { "The privacy receipt belongs to a different account." }
        return PrivacyConsentReceipt(
            lastEventId = requiredString(data.lastEventId),
            decision = decision(data.decision),
            policyVersion = requiredString(data.policyVersion),
            policySha256 = requiredString(data.policySha256),
            updatedAtMillis = serverMillis(data.updatedAt)
        )
    }

    private fun decision(value: dynamic): PrivacyDecision = when (value as? String) {
        "accepted" -> PrivacyDecision.ACCEPTED
        "denied" -> PrivacyDecision.DENIED
        else -> error("The privacy decision is invalid. Contact support.")
    }

    private fun requiredString(value: dynamic): String {
        check(jsTypeOf(value) == "string") { "A privacy record is incomplete. Contact support." }
        return value as String
    }

    private fun serverMillis(value: dynamic): Long {
        check(value != null && jsTypeOf(value.toMillis) == "function") {
            "The privacy record has no confirmed server timestamp. Reconnect and retry."
        }
        return (value.toMillis() as Double).toLong()
    }

    private fun browserString(field: String, maxLength: Int): String {
        val browser = js("globalThis.navigator")
        val value: dynamic = if (browser == null) null else browser[field]
        return (value as? String).orEmpty().take(maxLength)
    }
}
