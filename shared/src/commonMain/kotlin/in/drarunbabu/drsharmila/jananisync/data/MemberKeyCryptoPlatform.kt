package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyMaterial

/** Platform crypto API for recovery phrase and client-side member key enrollment. */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object MemberKeyCryptoPlatform {
    suspend fun generateRecoveryPhrase(): String
    suspend fun isValidRecoveryPhrase(phrase: String): Boolean
    suspend fun createMemberKeyMaterial(userId: String, recoveryPhrase: String): MemberKeyMaterial
}
