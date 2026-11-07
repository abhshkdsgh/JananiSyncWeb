package `in`.drarunbabu.drsharmila.jananisync.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import `in`.drarunbabu.drsharmila.jananisync.data.model.Ward
import `in`.drarunbabu.drsharmila.jananisync.data.model.Woman

private class UnavailableRosterRepository : RosterRepository {
    private val mutableState = MutableStateFlow(RosterState(isLoading = false, message = "The Firebase roster is available in the JS web target."))
    override val state = mutableState.asStateFlow()
    override fun observe(orgId: String, includeWomen: Boolean) = Unit
    override suspend fun addWard(ward: Ward): String = error("The Firebase roster is available in the JS web target.")
    override suspend fun updateWard(ward: Ward) = error("The Firebase roster is available in the JS web target.")
    override suspend fun addWoman(woman: Woman): Woman = error("The Firebase roster is available in the JS web target.")
    override suspend fun setWomanArchived(woman: Woman, archived: Boolean): Unit = error("The Firebase roster is available in the JS web target.")
    override fun stopObserving() = Unit
    override fun close() = Unit
}

actual fun createRosterRepository(): RosterRepository = UnavailableRosterRepository()
