package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyMaterial

actual object MemberKeyCryptoPlatform {
    actual suspend fun generateRecoveryPhrase(): String = unavailable()
    actual suspend fun isValidRecoveryPhrase(phrase: String): Boolean = unavailable()
    actual suspend fun createMemberKeyMaterial(userId: String, recoveryPhrase: String): MemberKeyMaterial = unavailable()

    private fun unavailable(): Nothing =
        error("Member key enrollment is available in the Kotlin/JS web target only.")
}
