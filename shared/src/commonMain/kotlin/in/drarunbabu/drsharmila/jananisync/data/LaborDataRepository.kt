package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.ObservationCorrection
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl
import kotlinx.coroutines.flow.StateFlow

data class LabourCareFeed(
    val observations: List<LabourCareObservation> = emptyList(),
    /** All immutable events, before reducing to the current column snapshots. */
    val cellEvents: List<LabourCareObservation> = emptyList(),
    val corrections: List<ObservationCorrection> = emptyList(),
    val columnControls: List<LabourColumnControl> = emptyList(),
    /** Current names read from organizations/{orgId}/members/{uid}. */
    val memberNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val hasPendingWrites: Boolean = false,
    val message: String? = null
)

/** Synthetic-only encrypted LCG feed scoped to the currently selected organization. */
interface LaborDataRepository {
    val feed: StateFlow<LabourCareFeed>
    val organizationKeyAvailable: StateFlow<Boolean>
    fun observe(orgId: String, userId: String, accessLevel: Int)
    fun selectAdmission(womanDocumentId: String?) = Unit
    fun stopObserving()
    suspend fun publish(observation: LabourCareObservation)
    suspend fun saveCell(columnId: String, cellKey: String, observation: LabourCareObservation): String
    suspend fun confirmColumn(columnId: String, observation: LabourCareObservation)
    suspend fun correctCells(columnId: String, values: Map<String, String>, reason: String, observation: LabourCareObservation)
    suspend fun bindAdmissionColumns(women: List<`in`.drarunbabu.drsharmila.jananisync.data.model.Woman>, columnIds: Set<String>? = null) = Unit
    fun close()
}

expect fun createLaborDataRepository(): LaborDataRepository
