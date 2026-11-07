package `in`.drarunbabu.drsharmila.jananisync.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory only key handoff between the auth/RBAC and encrypted observation repositories. */
internal object OrganizationKeyVault {
    private val mutableKeys = MutableStateFlow<Map<String, String>>(emptyMap())
    val keys: StateFlow<Map<String, String>> = mutableKeys.asStateFlow()

    fun put(orgId: String, key: String) {
        mutableKeys.value += (orgId to key)
    }

    fun remove(orgId: String) {
        mutableKeys.value -= orgId
        SyntheticCrypto.clearImportedKeys()
    }

    fun retainOrganizations(orgIds: Set<String>) {
        SyntheticCrypto.clearImportedKeys()
        mutableKeys.value = mutableKeys.value.filterKeys { it in orgIds }
    }

    fun clearAll() {
        SyntheticCrypto.clearImportedKeys()
        mutableKeys.value = emptyMap()
    }
}
