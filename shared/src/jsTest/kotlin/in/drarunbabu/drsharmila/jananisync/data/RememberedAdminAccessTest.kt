package `in`.drarunbabu.drsharmila.jananisync.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RememberedAdminAccessTest {
    @Test fun adminSecretIsScopedToAccountAndOrganizationAndClearedWithAccess() = runTest {
        val uid = "remembered-admin-test"
        try {
            RememberedOrganizationKeyStore.remember(uid, "12345678", "data-key")
            RememberedOrganizationKeyStore.remember(uid, "12345678:admin", "derived-admin-secret")
            assertEquals("derived-admin-secret", RememberedOrganizationKeyStore.restore(uid, "12345678:admin"))
            assertNull(RememberedOrganizationKeyStore.restore("other-account", "12345678:admin"))
            assertNull(RememberedOrganizationKeyStore.restore(uid, "87654321:admin"))
            RememberedOrganizationKeyStore.clearOrganization(uid, "12345678")
            assertNull(RememberedOrganizationKeyStore.restore(uid, "12345678"))
            assertNull(RememberedOrganizationKeyStore.restore(uid, "12345678:admin"))
            RememberedOrganizationKeyStore.remember(uid, "12345678:admin", "derived-admin-secret")
            RememberedOrganizationKeyStore.clearUser(uid)
            assertNull(RememberedOrganizationKeyStore.restore(uid, "12345678:admin"))
        } finally {
            RememberedOrganizationKeyStore.clearUser(uid)
        }
    }
}
