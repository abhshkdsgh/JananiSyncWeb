package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private class UnavailableLaborDataRepository : LaborDataRepository {
    private val mutableFeed = MutableStateFlow(LabourCareFeed(isLoading = false, message = "The Firebase Labour Care Guide is available in the JS web target."))
    override val feed = mutableFeed.asStateFlow()
    private val mutableKeyAvailable = MutableStateFlow(false)
    override val organizationKeyAvailable = mutableKeyAvailable.asStateFlow()
    override fun observe(orgId: String, userId: String, accessLevel: Int) = Unit
    override fun stopObserving() = Unit
    override suspend fun publish(observation: LabourCareObservation) = error("The Firebase Labour Care Guide is available in the JS web target.")
    override suspend fun saveCell(columnId: String, cellKey: String, observation: LabourCareObservation): String = error("The Firebase Labour Care Guide is available in the JS web target.")
    override suspend fun confirmColumn(columnId: String, observation: LabourCareObservation) = error("The Firebase Labour Care Guide is available in the JS web target.")
    override suspend fun correctCells(columnId: String, values: Map<String, String>, reason: String, observation: LabourCareObservation) = error("The Firebase Labour Care Guide is available in the JS web target.")
    override fun close() = Unit
}

actual fun createLaborDataRepository(): LaborDataRepository = UnavailableLaborDataRepository()
