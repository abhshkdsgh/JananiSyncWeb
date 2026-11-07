package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlinx.serialization.Serializable

data class OrganizationMaintenance(
    val orgId: String = "",
    val active: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val message: String? = null,
    val awaitingAdmin: Boolean = false,
    val adminRecoveryUnlocked: Boolean = false
) {
    /** Grant preparation plus one final verification/activation step. */
    val progress: Float get() = when {
        total <= 0 -> 0f
        !active && completed >= total -> 1f
        else -> completed.coerceIn(0, total) / (total.toFloat() + 1f)
    }
    val progressPercentage: Int get() = (progress * 100).toInt()
}

/** These models are encrypted with an admin-only key before leaving the browser. */
@Serializable
internal data class VerifiedRotationRecipient(
    val orgId: String,
    val adminId: String,
    val userId: String,
    val fingerprint: String,
    val publicKeyJwk: String
)

@Serializable
internal data class OrganizationRotationJournal(
    val orgId: String,
    val operationId: String,
    val adminId: String,
    val removedUserId: String, // Empty for a manual rotation that retains every member.
    val baseVersion: Int,
    val previousKey: String,
    val nextKey: String,
    val recipients: List<VerifiedRotationRecipient>
)

@Serializable
internal data class RotationGrantProof(
    val orgId: String,
    val operationId: String,
    val userId: String,
    val keyVersion: Int,
    val fingerprint: String,
    val wrappedKey: String
)
