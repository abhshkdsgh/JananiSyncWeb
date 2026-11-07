package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import kotlin.time.Instant

internal const val CHART_HOUR = 3_600_000L
internal fun chartMillis(value: String): Long? = runCatching { Instant.parse(value).toEpochMilliseconds() }.getOrNull()
internal fun chartStart(observations: List<LabourCareObservation>): Long? = observations
    .filter { !it.isGuideHeader && it.cellKey != "skipped" }
    .mapNotNull { chartMillis(it.chartStartTimestamp) }.minOrNull()
    ?: observations.filter { !it.isGuideHeader && it.cellKey != "skipped" }.mapNotNull { chartMillis(it.timestamp) }.minOrNull()
internal fun scheduledColumnId(time: Long, index: Int? = null) = "schedule:$time" + (index?.let { ":$it" } ?: "")
internal fun scheduledColumnIndex(id: String): Int? = if (id.startsWith("schedule:")) id.split(':').getOrNull(2)?.toIntOrNull() else null
internal fun scheduledColumnTime(id: String): Long? = id.removePrefix("schedule:").substringBefore(':').toLongOrNull().takeIf { id.startsWith("schedule:") }
internal fun observationTime(timestamp: String, keys: Collection<String>): Long? = chartMillis(timestamp)?.let {
    it + if (keys.any { key -> key.endsWith("Half2") }) 1_800_000 else 0
}
internal fun columnTimeError(start: Long, time: Long, now: Long): String? = when {
    time > now -> "Future observations cannot be recorded. This time is not available yet."
    time < start || (time - start) % CHART_HOUR != 0L -> "Choose a scheduled hourly column from the chart start."
    else -> null
}

/** A recorded transition fixes the stage clock across clients and reloads. */
internal fun secondStageStartTime(lastFirstStageTime: Long, clickedAt: Long): Long =
    if (clickedAt - lastFirstStageTime > 2 * CHART_HOUR) lastFirstStageTime else clickedAt

internal fun secondStageTransition(records: List<LabourCareObservation>): LabourCareObservation? =
    records.firstOrNull { it.fullDilationConfirmed && it.secondStageStartTimestamp.isNotBlank() }

internal fun stageColumnIndex(records: List<LabourCareObservation>, time: Long): Int? {
    val transition = secondStageTransition(records) ?: return null
    val start = chartMillis(transition.secondStageStartTimestamp) ?: return null
    if (time < start || (time - start) % CHART_HOUR != 0L) return null
    return transition.chartColumnIndex + 1 + ((time - start) / CHART_HOUR).toInt()
}

/** The empty LCG column is a new time slot, never an alias for an open column. */
internal fun guideDraftColumnId(records: List<LabourCareObservation>, start: Long): String {
    val clinical = records.filter { !it.isGuideHeader && it.cellKey !in setOf("scheduled", "skipped") }
    val transition = secondStageTransition(clinical)
    val stageStart = transition?.secondStageStartTimestamp?.let(::chartMillis)
    val time = if (transition != null && stageStart != null) {
        val used = clinical.filter { it.chartColumnIndex > transition.chartColumnIndex }
            .mapNotNull { chartMillis(it.timestamp) }
        used.maxOrNull()?.plus(CHART_HOUR) ?: stageStart
    } else clinical.mapNotNull { chartMillis(it.timestamp) }.maxOrNull()?.plus(CHART_HOUR) ?: start
    return scheduledColumnId(time)
}

/** Carries the clicked UI cell's identity through asynchronous recomposition and saves. */
internal data class LcgCellTarget(val time: Long, val index: Int, val columnId: String) {
    fun encode(): String = "lcg:$time:$index:$columnId"
    fun matches(observation: LabourCareObservation): Boolean =
        chartMillis(observation.timestamp) == time && observation.chartColumnIndex == index &&
            (columnId.startsWith("schedule:") || observation.columnId == columnId)
}
internal fun lcgCellTarget(observation: LabourCareObservation): String = LcgCellTarget(
    chartMillis(observation.timestamp) ?: error("Invalid column time."), observation.chartColumnIndex,
    observation.columnId).encode()
internal fun parseLcgCellTarget(value: String): LcgCellTarget? {
    if (!value.startsWith("lcg:")) return null
    val parts = value.split(':', limit = 4)
    require(parts.size == 4) { "Invalid LCG cell target." }
    return LcgCellTarget(parts[1].toLong(), parts[2].toInt(), parts[3])
}

internal fun scheduledObservations(records: List<LabourCareObservation>, start: Long, count: Int,
    sessionId: String, patientLabel: String): List<LabourCareObservation> {
    if (records.any { it.chartStartTimestamp.isBlank() }) return records // Never rewrite legacy times.
    val base = records.firstOrNull() ?: LabourCareObservation("", sessionId, patientLabel, 0,
        Instant.fromEpochMilliseconds(start).toString(),
        maternalVitals = `in`.drarunbabu.drsharmila.jananisync.data.model.MaternalVitals("", 0, 0, 0, 0.0),
        fetalWellbeing = `in`.drarunbabu.drsharmila.jananisync.data.model.FetalWellbeing("", 0, `in`.drarunbabu.drsharmila.jananisync.data.model.AmnioticFluidStatus.UNKNOWN),
        laborProgress = `in`.drarunbabu.drsharmila.jananisync.data.model.LaborProgress("", 0.0, 0, 0))
    val transition = secondStageTransition(records)
    val secondStart = transition?.secondStageStartTimestamp?.let(::chartMillis)
    return (0 until count).map { index ->
        val time = if (transition != null && secondStart != null && index > transition.chartColumnIndex)
            secondStart + (index - transition.chartColumnIndex - 1) * CHART_HOUR else start + index * CHART_HOUR
        records.firstOrNull { it.chartColumnIndex == index } ?: base.copy(id = "", columnId = scheduledColumnId(time),
            timestamp = Instant.fromEpochMilliseconds(time).toString(), cellKey = "scheduled",
            chartStartTimestamp = Instant.fromEpochMilliseconds(start).toString(), chartColumnIndex = index,
            recordedCellKeys = emptySet(), halfHourCells = emptyMap(), partographCells = emptyMap(), partographMarkCells = emptyMap(),
            secondStageStartTimestamp = "", revision = 0, fullDilationConfirmed = false, birthConfirmed = false, initials = "")
    }
}

/** One schedule for both charts. Earlier first-stage gaps remain available after transition. */
internal fun canonicalColumnIndex(records: List<LabourCareObservation>, start: Long, time: Long): Int? {
    stageColumnIndex(records, time)?.let { return it }
    if (time < start || (time - start) % CHART_HOUR != 0L) return null
    val index = ((time - start) / CHART_HOUR).toInt()
    val transition = secondStageTransition(records)
    return index.takeIf { transition == null || index <= transition.chartColumnIndex }
}

internal fun canonicalColumnSlots(records: List<LabourCareObservation>, start: Long, count: Int): List<Pair<Int, Long>> {
    val transition = secondStageTransition(records)
    val stage = transition?.secondStageStartTimestamp?.let(::chartMillis)
    return (0 until count).map { index ->
        index to if (transition != null && stage != null && index > transition.chartColumnIndex)
            stage + (index - transition.chartColumnIndex - 1) * CHART_HOUR else start + index * CHART_HOUR
    }
}
