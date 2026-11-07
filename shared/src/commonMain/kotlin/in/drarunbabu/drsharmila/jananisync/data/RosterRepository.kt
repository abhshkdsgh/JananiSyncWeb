package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.Ward
import `in`.drarunbabu.drsharmila.jananisync.data.model.Woman
import kotlinx.coroutines.flow.StateFlow

data class RosterState(
    val wards: List<Ward> = emptyList(),
    val women: List<Woman> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    /** Count of encrypted ward envelopes, available before the device key is unlocked. */
    val wardCount: Int? = null,
    val unreadableRecordIds: List<String> = emptyList()
)

data class ArchiveHistoryPage(
    val events: List<`in`.drarunbabu.drsharmila.jananisync.data.model.WomanArchiveEvent> = emptyList(),
    val nextBeforeRevision: Int? = null,
    val unreadableRecordIds: List<String> = emptyList()
)

interface RosterRepository {
    val state: StateFlow<RosterState>
    fun observe(orgId: String, includeWomen: Boolean = true)
    suspend fun addWard(ward: Ward): String
    suspend fun updateWard(ward: Ward)
    suspend fun addWoman(woman: Woman): Woman
    suspend fun setWomanArchived(woman: Woman, archived: Boolean)
    suspend fun loadArchiveHistory(womanDocumentId: String, beforeRevision: Int? = null): ArchiveHistoryPage = ArchiveHistoryPage()
    fun stopObserving()
    fun close()
}

expect fun createRosterRepository(): RosterRepository
