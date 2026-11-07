package `in`.drarunbabu.drsharmila.jananisync.data.privacy

enum class PrivacyDecision { ACCEPTED, DENIED }

/** An account-authenticated decision, not proof that the account holder read the notice. */
data class PrivacyConsentEvent(
    val id: String,
    val userId: String,
    val policyVersion: String,
    val policySha256: String,
    val decision: PrivacyDecision,
    val source: String,
    val recordedAtMillis: Long,
    val email: String,
    val emailVerified: Boolean,
    val clientOccurredAtMillis: Long,
    val language: String,
    val userAgent: String,
    val schemaVersion: Int = 1,
    val presentationVersion: Int = 1
)

/** Latest decision confirmed by the server, including its immutable history-event reference. */
data class PrivacyConsentReceipt(
    val lastEventId: String,
    val decision: PrivacyDecision,
    val policyVersion: String,
    val policySha256: String,
    val updatedAtMillis: Long
)

interface PrivacyConsentRepository {
    /** Server-only: failure or no receipt must keep account access blocked. */
    suspend fun loadReceipt(userId: String): PrivacyConsentReceipt?

    /**
     * Watches server-confirmed changes for this signed-in account. Cached and pending
     * snapshots cannot grant access; callers should perform [loadReceipt] for startup.
     * Disconnecting alone does not withdraw a previously confirmed choice.
     */
    fun observeReceipt(
        userId: String,
        onReceipt: (PrivacyConsentReceipt?) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit

    /** Atomically appends an immutable event and changes the current receipt, online only. */
    suspend fun recordDecision(userId: String, decision: PrivacyDecision, source: String): PrivacyConsentReceipt

    /** Returns up to 100 newest server-confirmed events belonging to this account. */
    suspend fun loadHistory(userId: String): List<PrivacyConsentEvent>
}

expect fun createPrivacyConsentRepository(): PrivacyConsentRepository
