package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.rbac.OrganizationAccessSnapshots
import kotlin.test.*

class OrganizationAccessSnapshotsTest {
    @Test fun emptyCacheForSecondAccountDoesNotShowOrganizationSignup() {
        val published = mutableListOf<Pair<List<String>, List<String>>>()
        val snapshots = OrganizationAccessSnapshots { members, requests -> published.add(members to requests) }
        snapshots.members(emptyList(), fromCache = true)
        snapshots.requests(emptyList(), fromCache = true)
        assertTrue(published.isEmpty())
        snapshots.requests(emptyList(), fromCache = false)
        assertTrue(published.isEmpty())
        snapshots.members(listOf("second-user-member"), fromCache = false)
        assertEquals(listOf("second-user-member"), published.single().first)
        assertTrue(published.single().second.isEmpty())
    }

    @Test fun genuinelyUnassignedAccountRequiresBothServerResults() {
        val published = mutableListOf<Pair<List<String>, List<String>>>()
        val snapshots = OrganizationAccessSnapshots { m, r -> published.add(m to r) }
        snapshots.members(emptyList(), fromCache = false)
        assertTrue(published.isEmpty())
        snapshots.requests(emptyList(), fromCache = false)
        assertEquals(emptyList<String>() to emptyList<String>(), published.single())
    }
}
