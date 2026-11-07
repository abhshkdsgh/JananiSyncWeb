package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.data.cellValue
import `in`.drarunbabu.drsharmila.jananisync.data.chartMillis
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation

/** Uses only actual, currently recorded examinations; empty and erased cells are not examinations. */
internal fun cervicalDelayAlert(
    currentDilationText: String,
    history: List<LabourCareObservation>,
    nowIso: String
): Boolean {
    val dilation = currentDilationText.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return false
    val thresholdMillis = when (dilation.toInt()) {
        5 -> 6 * 3_600_000L
        6 -> 5 * 3_600_000L
        7 -> 3 * 3_600_000L
        8 -> 9_000_000L
        9 -> 2 * 3_600_000L
        else -> return false
    }
    val now = chartMillis(nowIso) ?: return false
    val examinations = history.asSequence()
        .filter { !it.isGuideHeader && it.cellKey !in setOf("scheduled", "skipped") }
        .flatMap { observation ->
            val time = chartMillis(observation.timestamp) ?: return@flatMap emptySequence()
            sequence {
                val field = "cervicalDilation"
                val firstHalfKey = "${field}Half1"
                if (firstHalfKey in observation.halfHourCells || field in observation.recordedCellKeys || field in observation.partographCells || observation.cellKey == field) {
                    val text = if (firstHalfKey in observation.halfHourCells) observation.halfHourCells[firstHalfKey] else observation.cellValue(field)
                    val value = text?.toDoubleOrNull()?.takeIf { it.isFinite() }
                    if (value != null) yield(time to value)
                }
                val second = observation.halfHourCells["cervicalDilationHalf2"]
                    ?.toDoubleOrNull()?.takeIf { it.isFinite() }
                if (second != null) yield(time + 1_800_000L to second)
            }
        }
        .filter { it.first <= now }
        .sortedByDescending { it.first }

    var plateauStart: Long? = null
    for ((time, recordedDilation) in examinations) {
        if (recordedDilation != dilation) break
        plateauStart = time
    }
    return plateauStart?.let { now - it >= thresholdMillis } ?: false
}
