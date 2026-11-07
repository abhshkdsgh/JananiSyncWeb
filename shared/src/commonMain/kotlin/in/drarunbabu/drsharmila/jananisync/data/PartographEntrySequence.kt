package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl
import kotlin.time.Instant

internal const val PARTOGRAPH_HOUR_MILLIS = 3_600_000L

/** Recording hours start at the baseline column, independently of cervical plot alignment. */
internal data class PartographEntrySequence(
    val baselineMillis: Long?,
    val nextHour: Int,
    val message: String? = null,
    val birthMillis: Long? = null,
    val records: List<LabourCareObservation> = emptyList(),
    val nextMillis: Long? = null
) {
    fun nextTimestampMillis(firstTimestampMillis: Long): Long =
        nextMillis ?: baselineMillis?.let { it + nextHour * PARTOGRAPH_HOUR_MILLIS } ?: firstTimestampMillis

    fun recordingHour(timestamp: String): Double? = runCatching {
        val time = Instant.parse(timestamp).toEpochMilliseconds()
        (time - (baselineMillis ?: time)) / PARTOGRAPH_HOUR_MILLIS.toDouble()
    }.getOrNull()

    fun creationBlockMessage(timestamp: String, firstTimestampMillis: Long? = null): String? {
        message?.let { return it }
        val time = runCatching { Instant.parse(timestamp).toEpochMilliseconds() }.getOrNull()
            ?: return "This column's recorded time is invalid. Select the current hour again."
        if (baselineMillis == null && firstTimestampMillis != null && time != firstTimestampMillis)
            return "Start with hour 0 at the chosen chart start time."
        if (birthMillis != null && time > birthMillis) return "This time is after the recorded birth."
        return if (baselineMillis != null && canonicalColumnIndex(records, baselineMillis, time) == null)
            "Choose an hourly column relative to the chart start." else null
    }
}

internal fun List<LabourCareObservation>.partographEntrySequence(
    controls: List<LabourColumnControl> = emptyList(),
    womanDocumentId: String = ""
): PartographEntrySequence {
    val columns = filter { !it.isGuideHeader && it.cellKey != "skipped" && it.columnId.isNotBlank() }
        .groupBy { it.columnId }.values.map { events -> events.maxBy { it.revision } }
    val knownIds = columns.map { it.columnId }.toSet()
    if (womanDocumentId.isNotBlank() && controls.any {
        it.womanDocumentId == womanDocumentId && it.columnId.isNotBlank() &&
            !it.columnId.startsWith("header-") && it.columnId !in knownIds
    }) return PartographEntrySequence(null, 0,
        "Column times are still loading. Wait for the existing columns before continuing.")
    val times = columns.map { runCatching { Instant.parse(it.timestamp).toEpochMilliseconds() }.getOrNull() }
    if (times.any { it == null }) return PartographEntrySequence(null, 0,
        "Column times are unavailable. Wait for the existing columns to load before continuing.")
    val valid = times.filterNotNull()
    val first = chartStart(columns) ?: return PartographEntrySequence(null, 0)
    // Historical off-grid/gapped entries retain their times; continue at the next whole hour.
    val used = valid.map { (it - first) / PARTOGRAPH_HOUR_MILLIS }.toSet()
    val next = generateSequence(0) { it + 1 }.first { it.toLong() !in used }
    val available = canonicalColumnSlots(columns, first, maxOf(24, (columns.maxOfOrNull { it.chartColumnIndex } ?: 0) + 2))
    return PartographEntrySequence(first, next, birthMillis = columns.filter { it.birthConfirmed }.mapNotNull { chartMillis(it.timestamp) }.minOrNull(),
        records = columns, nextMillis = available.firstOrNull { (index, time) -> columns.none {
            chartMillis(it.timestamp) == time && (it.chartColumnIndex == index || it.chartColumnIndex < 0)
        } }?.second)
}
