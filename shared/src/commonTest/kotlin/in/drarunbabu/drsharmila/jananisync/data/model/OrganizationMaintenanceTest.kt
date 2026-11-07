package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlin.test.*

class OrganizationMaintenanceTest {
    @Test fun progressIncludesFinalActivationAndNeverCompletesDuringMaintenance() {
        val preparing = OrganizationMaintenance(active = true, total = 3)
        assertEquals(0, preparing.progressPercentage)
        assertEquals(25, preparing.copy(completed = 1).progressPercentage)
        assertEquals(75, preparing.copy(completed = 3).progressPercentage)
        assertEquals(100, preparing.copy(active = false, completed = 3).progressPercentage)
        assertTrue(OrganizationMaintenance(active = true, total = 500, completed = 500).progressPercentage < 100)
        assertEquals(0, OrganizationMaintenance(active = true).progressPercentage)
        assertEquals(0, preparing.copy(completed = -1).progressPercentage)
    }
}
