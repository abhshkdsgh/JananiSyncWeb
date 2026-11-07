package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation

/** Shared by both charting interfaces and their save paths. */
internal data class AdmissionColumnState(
    val openColumnIds: Set<String>,
    val isLoading: Boolean
) {
    val canStartColumn: Boolean get() = !isLoading
    val creationBlockMessage: String? get() = if (isLoading) "Time column status is still loading." else null

}

internal fun LabourCareFeed.admissionColumnState(
    sessionId: String,
    womanDocumentId: String = "",
    pendingObservation: LabourCareObservation? = null
): AdmissionColumnState {
    val clinicalObservations = (observations + listOfNotNull(pendingObservation)).filter {
        it.sessionId == sessionId && !it.isGuideHeader && it.cellKey != "skipped"
    }
    val columnIds = clinicalObservations.map { it.columnId }.filter {
        it.isNotBlank() && !it.startsWith("header-")
    }.toMutableSet()
    // A successful write can publish its control before its encrypted observation is decoded.
    if (womanDocumentId.isNotBlank()) {
        columnControls.filter {
            it.womanDocumentId == womanDocumentId && it.columnId.isNotBlank() && !it.columnId.startsWith("header-")
        }.mapTo(columnIds) { it.columnId }
    }
    val controls = columnControls.associateBy { it.columnId }
    return AdmissionColumnState(
        openColumnIds = columnIds.filterTo(linkedSetOf()) { controls[it]?.confirmed != true },
        isLoading = isLoading
    )
}
