package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlinx.serialization.Serializable

/** Schema 2 stores only the authorized delta. Column identity is checked against server metadata. */
@Serializable
internal data class ClinicalCellEvent(
    val sessionId: String,
    val womanDocumentId: String,
    val timestamp: String,
    val chartStartTimestamp: String,
    val chartColumnIndex: Int,
    val values: Map<String, String>,
    val isGuideHeader: Boolean = false,
    val secondStageStartTimestamp: String = "",
    val authorName: String = "",
    val correctionReason: String = ""
) {
    fun observation(): LabourCareObservation {
        var base = LabourCareObservation("", sessionId, "", 0, timestamp,
            maternalVitals = MaternalVitals("", 0, 0, 0, 0.0),
            fetalWellbeing = FetalWellbeing("", 0, AmnioticFluidStatus.UNKNOWN), laborProgress = LaborProgress("", 0.0, 0, 0),
            womanDocumentId = womanDocumentId, chartStartTimestamp = chartStartTimestamp, chartColumnIndex = chartColumnIndex,
            isGuideHeader = isGuideHeader, secondStageStartTimestamp = secondStageStartTimestamp,
            authorName = authorName, correctionReason = correctionReason)
        for ((key, value) in values) base = when (key) {
            "initials" -> base.copy(initials = value)
            "fullDilationConfirmed" -> base.copy(fullDilationConfirmed = value == "true", secondStageStartTimestamp = secondStageStartTimestamp)
            "birthConfirmed" -> base.copy(birthConfirmed = value == "true")
            else -> base.withCell(key, value)
        }
        return base.copy(partographMarkCells = values)
    }
}

internal fun LabourCareObservation.cellEvent(keys: Collection<String>) = ClinicalCellEvent(
    sessionId, womanDocumentId, timestamp, chartStartTimestamp, chartColumnIndex,
    keys.associateWith(::cellValue), isGuideHeader, secondStageStartTimestamp, authorName, correctionReason)
