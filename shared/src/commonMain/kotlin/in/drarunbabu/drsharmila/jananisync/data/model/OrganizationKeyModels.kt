package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlinx.serialization.Serializable

/** Client-only bundle. Serialize locally if needed; split into the public profile and encrypted backup for storage. */
@Serializable
data class MemberKeyMaterial(
    val publicKeyJwk: String,
    val publicKeyFingerprint: String,
    val privateKeyBackupCiphertext: String,
    val privateKeyBackupIv: String,
    val privateKeyBackupSalt: String,
    val privateKeyBackupKdf: String = "PBKDF2-SHA256",
    val privateKeyBackupIterations: Int = 600_000,
    val keyAlgorithm: String = "RSA-OAEP-3072-SHA256"
)

/** Short-lived UI result. The phrase must be shown once, confirmed, then cleared from state. */
data class MemberKeySetup(
    val recoveryPhrase: String?,
    val keyMaterial: MemberKeyMaterial,
    val reusingExistingKey: Boolean = false
)


/** Organization-scoped replacement; the Firebase UID and clinical authorship never change. */
data class MemberKeyReplacementRequest(
    val orgId: String,
    val userId: String,
    val requestId: String,
    val fingerprint: String,
    val status: String
)

data class MemberKeyReplacementState(
    val orgId: String = "",
    val requests: List<MemberKeyReplacementRequest> = emptyList(),
    val loaded: Boolean = false,
    val error: String? = null
)
