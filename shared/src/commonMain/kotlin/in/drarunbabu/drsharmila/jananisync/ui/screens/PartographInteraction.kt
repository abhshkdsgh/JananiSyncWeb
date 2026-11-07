package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import `in`.drarunbabu.drsharmila.jananisync.data.AdmissionColumnState
import `in`.drarunbabu.drsharmila.jananisync.data.model.AmnioticFluidStatus
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl
import `in`.drarunbabu.drsharmila.jananisync.ui.components.AdaptiveFields
import `in`.drarunbabu.drsharmila.jananisync.ui.components.WrappingActions
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.categoricalOptionsForCell
import kotlin.math.roundToInt
import kotlin.time.Instant

internal data class PartographMark(
    val field: String, val hour: Double, val timestamp: String, val columnId: String,
    val values: Map<String, String>
)

internal val partographZoomLevels = listOf(0.60f, 0.75f, 0.90f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.25f, 2.5f, 2.75f, 3f)
internal fun partographZoomStep(current: Float, zoomIn: Boolean): Float =
    if (zoomIn) partographZoomLevels.firstOrNull { it > current + 0.001f } ?: partographZoomLevels.last()
    else partographZoomLevels.lastOrNull { it < current - 0.001f } ?: partographZoomLevels.first()

/** Clear one saved cell without writing the other fields in a grouped mark. */
internal fun PartographMark.erasingValue(key: String): PartographMark? =
    if (columnId.isBlank() || key !in values) null else copy(values = mapOf(key to ""))

internal enum class PartographDuration(val storedValue: String, val label: String) {
    SHORT("<20", "<20 seconds"), MEDIUM("20-40", "20–40 seconds"), LONG(">40", ">40 seconds")
}

/** Preserve range observations as ranges, while still displaying older exact-second readings. */
internal fun partographDuration(value: String): PartographDuration? =
    PartographDuration.entries.firstOrNull { it.storedValue == value || it.label == value }
        ?: value.toIntOrNull()?.takeIf { it in 1..180 }?.let {
            when { it < 20 -> PartographDuration.SHORT; it <= 40 -> PartographDuration.MEDIUM; else -> PartographDuration.LONG }
        }

internal fun LabourCareObservation.partographDuration(half: Int): PartographDuration? =
    recordedPartographText("contractionDuration", half)?.let { partographDuration(it) }

internal data class PartographEntryColumn(val id: String, val timestamp: String, val hour: Double, val existing: Boolean)

/** Keep the selector compact while including recorded off-grid times and eligible empty hours. */
internal fun availablePartographColumns(
    observations: List<LabourCareObservation>, origin: Long, now: Long
): List<PartographEntryColumn> {
    val recorded = timedPartographObservations(observations)
        .filter { it.observation.columnId.isNotBlank() && it.epochMillis <= now }
        .groupBy { it.observation.columnId }.values.map { records -> records.maxBy { it.observation.revision } }
    val usedTimes = recorded.map { it.epochMillis }.toSet()
    val firstMissing = generateSequence(0) { it + 1 }.first { origin + it * 3_600_000L !in usedTimes }
    val afterLast = recorded.maxOfOrNull { ((it.epochMillis - origin) / 3_600_000L).toInt() + 1 } ?: 0
    val birth = recorded.filter { it.observation.birthConfirmed }.minOfOrNull { it.epochMillis }
    val empty = `in`.drarunbabu.drsharmila.jananisync.data.canonicalColumnSlots(observations, origin,
        maxOf(PartographLayout.HOURS + 1, firstMissing + 1, afterLast + 2)).mapNotNull { (index, time) ->
        val hour = (time - origin) / 3_600_000.0
        if (time > now || recorded.any { it.epochMillis == time && (it.observation.chartColumnIndex == index || it.observation.chartColumnIndex < 0) } || (birth != null && time > birth)) null
        else PartographEntryColumn(`in`.drarunbabu.drsharmila.jananisync.data.scheduledColumnId(time, index),
            Instant.fromEpochMilliseconds(time).toString(), hour, false)
    }
    return (recorded.map { record -> PartographEntryColumn(record.observation.columnId, record.observation.timestamp,
        (record.epochMillis - origin) / 3_600_000.0, true) } + empty)
        .sortedBy { Instant.parse(it.timestamp) }
}

internal val lcgHalfHourlyFields = setOf("fetalHeartRate", "fhrDeceleration", "contractionFrequency", "contractionDuration")

/** LCG's unsplit rows consume the first-half reading only, including an explicit clear. */
internal fun LabourCareObservation.lcgFirstHalfValue(field: String?): String? {
    if (field == null || field in lcgHalfHourlyFields) return null
    halfHourCells["${field}Half1"]?.let { return it }
    if ((field in recordedCellKeys || field == cellKey) && field in partographCells) return partographCells[field]
    // A later-half-only observation must not expose an unrecorded numeric default in LCG.
    if ("${field}Half2" in halfHourCells && field !in recordedCellKeys && field != cellKey && field !in partographCells) return ""
    return null
}

internal fun intervalFieldOptions(field: String, label: String, partograph: Boolean): List<Pair<String, String>> = when {
    partograph && field in partographHalfHourlyFields -> listOf(
        partographCellKey(field, 1) to "$label · first half-hour",
        partographCellKey(field, 2) to "$label · second half-hour")
    field in lcgHalfHourlyFields -> listOf("${field}Half1" to "$label · first half-hour", "${field}Half2" to "$label · second half-hour")
    else -> listOf(field to label)
}

internal val partographHalfHourlyFields = setOf(
    "fetalHeartRate", "amnioticFluid", "moulding", "cervicalDilation", "descent",
    "contractionFrequency", "contractionDuration", "oxytocin", "pulse", "systolic", "diastolic", "temperature"
)

/** Existing first-half cell keys stay compatible with LCG and recorded access levels. */
internal fun partographCellKey(field: String, half: Int): String =
    if (half == 2 || field in setOf("fetalHeartRate", "contractionFrequency", "contractionDuration", "oxytocin")) "${field}Half$half" else field

internal fun PartographMark.parameterValue(field: String): String? =
    values.entries.firstOrNull { it.key.removeSuffix("Half1").removeSuffix("Half2") == field }?.value

internal data class PartographPoint(val hour: Double, val value: Double)

/** An explicitly cleared or never recorded cell must not become a zero on a clinical plot. */
internal fun LabourCareObservation.partographValue(field: String): Double? {
    if (field.endsWith("Half1") || field.endsWith("Half2")) {
        if (field in halfHourCells) return halfHourCells[field]?.toDoubleOrNull()
        if (field.endsWith("Half2")) return null
        return partographValue(field.removeSuffix("Half1"))
    }
    if (field !in recordedCellKeys && field != cellKey && !(cellKey.isBlank() && columnId.isBlank())) return null
    if (field in partographCells) return partographCells[field]?.toDoubleOrNull()
    if (field == cellKey && editedValue != null) return editedValue.toDoubleOrNull()
    return when (field) {
        "fetalHeartRate" -> fetalWellbeing.fetalHeartRateBpm.toDouble()
        "cervicalDilation" -> laborProgress.cervicalDilationCm
        "descent" -> descent.toDoubleOrNull()
        "contractionFrequency" -> laborProgress.contractionFrequencyPer10Minutes.toDouble()
        "contractionDuration" -> laborProgress.contractionDurationSeconds.toDouble()
        "pulse" -> maternalVitals.pulseBpm.toDouble()
        "systolic" -> maternalVitals.systolicBloodPressure.toDouble()
        "diastolic" -> maternalVitals.diastolicBloodPressure.toDouble()
        else -> null
    }
}

private data class TimedPartographObservation(val observation: LabourCareObservation, val epochMillis: Long)

private fun timedPartographObservations(observations: List<LabourCareObservation>): List<TimedPartographObservation> =
    observations.filter { !it.isGuideHeader && it.cellKey != "skipped" }.mapNotNull { observation ->
        runCatching { Instant.parse(observation.timestamp).toEpochMilliseconds() }.getOrNull()
            ?.let { TimedPartographObservation(observation, it) }
    }.sortedBy { it.epochMillis }

private fun List<TimedPartographObservation>.origin(fallback: Long): Long =
    mapNotNull { `in`.drarunbabu.drsharmila.jananisync.data.chartMillis(it.observation.chartStartTimestamp) }.minOrNull() ?: firstOrNull()?.epochMillis ?: fallback

internal fun partographOrigin(observations: List<LabourCareObservation>, fallback: Long): Long =
    timedPartographObservations(observations).origin(fallback)

/** All series share the chart's chronological anchor, irrespective of timestamp offset or input order. */
internal fun partographPoints(observations: List<LabourCareObservation>, field: String, halfHourly: Boolean = field in partographHalfHourlyFields): List<PartographPoint> {
    val timed = timedPartographObservations(observations)
    val origin = timed.origin(0)
    return timed.flatMap { (observation, time) ->
        val hour = (time - origin) / 3_600_000.0
        if (halfHourly) (1..2).mapNotNull { half ->
            observation.partographValue("${field}Half$half")?.let { PartographPoint(hour + (half - 1) * 0.5, it) }
        } else listOfNotNull(observation.partographValue(field)?.let { PartographPoint(hour, it) })
    }.filter { it.hour in 0.0..12.0 }.sortedBy { it.hour }
}

/** Half-two values belong to the stored column but were observed thirty minutes later. */
internal fun PartographMark.observationTimestamp(): String = if (values.keys.any { it.endsWith("Half2") }) {
    runCatching { Instant.fromEpochMilliseconds(Instant.parse(timestamp).toEpochMilliseconds() + 1_800_000L).toString() }
        .getOrDefault(timestamp)
} else timestamp

internal fun PartographMark.editBlockMessage(
    columnState: AdmissionColumnState, controls: List<LabourColumnControl>, accessLevel: Int
): String? {
    if (columnId.isBlank() || columnId.startsWith("schedule:")) return if (columnState.canStartColumn) null else columnState.creationBlockMessage
    val control = controls.firstOrNull { it.columnId == columnId }
        ?: return "This time column is still syncing. Wait for its access details before editing."
    return when {
        !values.keys.all { control.canEditCell(it, accessLevel) } -> "The selected parameter requires a higher access level."
        else -> null
    }
}

internal fun LabourCareObservation.partographText(field: String): String {
    if (field.endsWith("Half1") || field.endsWith("Half2")) {
        if (field in halfHourCells) return halfHourCells[field].orEmpty()
        if (field.endsWith("Half2")) return ""
        return partographText(field.removeSuffix("Half1"))
    }
    partographCells[field]?.let { return it }
    if (field !in recordedCellKeys && field != cellKey && !(cellKey.isBlank() && columnId.isBlank())) return ""
    return when (field) {
        "amnioticFluid" -> fetalWellbeing.amnioticFluidFinding.ifBlank {
            when (fetalWellbeing.amnioticFluidStatus) {
                AmnioticFluidStatus.CLEAR -> "C"
                AmnioticFluidStatus.MECONIUM_STAINED -> "M"
                AmnioticFluidStatus.BLOOD_STAINED -> "B"
                AmnioticFluidStatus.ABSENT -> "A"
                AmnioticFluidStatus.INTACT -> "I"
                AmnioticFluidStatus.UNKNOWN -> ""
            }
        }
        "moulding" -> fetalWellbeing.moulding
        "oxytocin" -> oxytocin
        "medicine" -> medicine
        "intravenousFluids" -> intravenousFluids
        "temperature" -> maternalVitals.temperatureCelsius.toString()
        "urineProtein" -> maternalVitals.urineProtein
        "urineAcetone" -> maternalVitals.urineAcetone
        else -> partographValue(field)?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() }.orEmpty()
    }
}

private val oxytocinUnitsAndRate = Regex(
    """^\s*(\d+(?:\.\d+)?)\s*U/L\s*·\s*(\d+(?:\.\d+)?)\s*drops/min\s*$""",
    RegexOption.IGNORE_CASE
)

/** Use the paper's compact notation without changing stored values or interpreting free text. */
internal fun LabourCareObservation.partographDisplayText(field: String): String {
    val value = partographText(field)
    if (field.removeSuffix("Half1").removeSuffix("Half2") == "oxytocin") {
        val parts = oxytocinUnitsAndRate.matchEntire(value) ?: return value
        return "${parts.groupValues[1]}\n${parts.groupValues[2]}"
    }
    val prefix = when (field) {
        "urineProtein" -> "P"
        "urineAcetone" -> "A"
        else -> return value
    }
    return when (val grade = value.removePrefix(prefix).trim()) {
        "-" -> "−"
        "Trace" -> "Trace"
        "+", "++", "+++", "++++" -> grade
        else -> value
    }
}

/** Hit testing works in chart dp, independent of display density and horizontal scroll. */
internal fun partographHit(x: Float, y: Float, cervixField: String, vitalField: String): Pair<String, String>? {
    if (x !in PartographLayout.PLOT_LEFT..PartographLayout.PLOT_RIGHT) return null
    fun PartographSection.containsY() = y in top..<bottom
    return when {
        PartographLayout.fetal.containsY() -> "fetalHeartRate" to PartographLayout.fetal.valueAtY(y).toString()
        PartographLayout.cervix.containsY() -> cervixField to PartographLayout.cervix.valueAtY(y)
            .coerceIn(if (cervixField == "descent") 0 else 4, if (cervixField == "descent") 5 else 10).toString()
        PartographLayout.contractions.containsY() -> "contractionFrequency" to PartographLayout.contractions.valueAtY(y).toString()
        PartographLayout.vitals.containsY() -> vitalField to PartographLayout.vitals.valueAtY(y).toString()
        else -> partographLanes.firstOrNull { y >= it.top && y < it.bottom }?.let { it.field to "" }
    }
}

/** Find saved marks before interpreting a tap as a new reading; retain exact column times. */
internal fun recordedPartographMarkAt(
    observations: List<LabourCareObservation>, origin: Long, x: Float, y: Float, tolerance: Float = 18f
): PartographMark? {
    val section = listOf(PartographLayout.fetal, PartographLayout.cervix, PartographLayout.contractions, PartographLayout.vitals)
        .firstOrNull { y >= it.top - tolerance && y <= it.bottom + tolerance }
    val lane = partographLanes.firstOrNull { y >= it.top && y < it.bottom }
    val fields = when {
        lane != null -> listOf(lane.field)
        section == PartographLayout.fetal -> listOf("fetalHeartRate")
        section == PartographLayout.cervix -> listOf("cervicalDilation", "descent")
        section == PartographLayout.contractions -> listOf("contractionFrequency")
        section == PartographLayout.vitals -> listOf("pulse", "systolic", "diastolic")
        else -> return null
    }
    return observations.filter { !it.isGuideHeader && it.cellKey != "skipped" && it.columnId.isNotBlank() }
        .groupBy { it.columnId }.values.map { events -> events.maxBy { it.revision } }
        .flatMap { observation -> fields.flatMap { field ->
            val halves = if (field in partographHalfHourlyFields) 1..2 else 1..1
            halves.mapNotNull { half ->
                val value = observation.recordedPartographText(field, half)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val mark = buildPartographMarkForColumn(listOf(observation), origin, observation.columnId, field, half)
                    ?: return@mapNotNull null
                if (mark.hour !in 0.0..12.0) return@mapNotNull null
                val px = PartographLayout.hourToX(mark.hour)
                val dx = kotlin.math.abs(x - px)
                val dy = if (lane != null) 0f else {
                    val number = value.toDoubleOrNull() ?: return@mapNotNull null
                    if (field == "contractionFrequency") {
                        val right = px + PartographLayout.PLOT_WIDTH / PartographLayout.HALF_HOURS
                        if (x !in (px - tolerance)..(right + tolerance)) return@mapNotNull null
                        val top = PartographLayout.contractions.y(number)
                        if (y in top..PartographLayout.contractions.bottom) 0f else kotlin.math.abs(y - top)
                    } else kotlin.math.abs(y - section!!.y(number))
                }
                val width = if (lane != null || field == "contractionFrequency") PartographLayout.PLOT_WIDTH / PartographLayout.HALF_HOURS else 0f
                if (x < px - tolerance || x > px + width + tolerance || dy > tolerance) null
                else mark to (dx + dy)
            }
        } }.minByOrNull { it.second }?.first
}

private fun LabourCareObservation.recordedPartographText(field: String, half: Int = 1): String? {
    if (field in partographHalfHourlyFields || field == "contractionStrength") {
        val key = "${field}Half$half"
        if (key in halfHourCells) return halfHourCells[key]
        if (half == 2) return null
    }
    if (field in partographCells) return partographCells[field]
    return partographText(field).takeIf {
        field in recordedCellKeys || field == cellKey || (cellKey.isBlank() && columnId.isBlank())
    }
}

private fun partographMarkValues(field: String, half: Int, existing: LabourCareObservation?, selectedValue: String): Map<String, String> {
    val key = if (field in partographHalfHourlyFields) partographCellKey(field, half) else field
    return when (field) {
        "contractionFrequency" -> mapOf(
            key to selectedValue,
            "contractionDurationHalf$half" to existing?.recordedPartographText("contractionDuration", half).orEmpty()
        )
        "systolic", "diastolic" -> mapOf(
            partographCellKey("systolic", half) to (if (field == "systolic") selectedValue else existing?.recordedPartographText("systolic", half).orEmpty()),
            partographCellKey("diastolic", half) to (if (field == "diastolic") selectedValue else existing?.recordedPartographText("diastolic", half).orEmpty())
        )
        else -> mapOf(key to selectedValue)
    }
}

/** Parameter entry for a linked LCG column must preserve its exact recorded time. */
internal fun buildPartographMarkForColumn(
    observations: List<LabourCareObservation>, origin: Long, columnId: String, field: String, half: Int = 1
): PartographMark? {
    if (columnId.isBlank() || half !in 1..2 || (half == 2 && field !in partographHalfHourlyFields)) return null
    val existing = observations.filter { it.columnId == columnId && !it.isGuideHeader && it.cellKey != "skipped" }
        .maxByOrNull { it.revision } ?: return null
    val time = runCatching { Instant.parse(existing.timestamp).toEpochMilliseconds() }.getOrNull() ?: return null
    val value = existing.recordedPartographText(field, half).orEmpty()
    val values = partographMarkValues(field, half, existing, value)
    return PartographMark(field, (time - origin) / 3_600_000.0 + (half - 1) * 0.5, existing.timestamp, columnId, values)
}

/** A draft always starts at its scheduled whole hour, with no assumed measurements. */
internal fun buildPartographMarkForNewColumn(origin: Long, timestamp: String, field: String, targetId: String = ""): PartographMark {
    val time = Instant.parse(timestamp).toEpochMilliseconds()
    return PartographMark(field, (time - origin) / 3_600_000.0, timestamp, targetId,
        partographMarkValues(field, 1, null, ""))
}

/** Graph positions use the chosen chart start even before any LCG column exists. */
internal fun buildPartographEntryMark(
    observations: List<LabourCareObservation>, origin: Long, hour: Double, field: String, value: String,
    firstTimestampMillis: Long, selectedColumnId: String = ""
): PartographMark {
    val hasColumn = observations.any { !it.isGuideHeader && it.cellKey != "skipped" && it.columnId.isNotBlank() }
    if (hasColumn) {
        val halfHourly = field in partographHalfHourlyFields
        val subdivisions = if (halfHourly) 2 else 1
        val transition = `in`.drarunbabu.drsharmila.jananisync.data.secondStageTransition(observations)
        val stageStart = transition?.secondStageStartTimestamp?.let { `in`.drarunbabu.drsharmila.jananisync.data.chartMillis(it) }
        val stageOffset = stageStart?.let { (it - origin) / 3_600_000.0 }
        val tapIsSecondStage = stageOffset != null && hour >= stageOffset
        val tapBucket = kotlin.math.floor((hour - if (tapIsSecondStage) stageOffset else 0.0) * subdivisions + 0.000001).toInt()
        fun isSecondStage(mark: PartographMark): Boolean {
            val column = observations.firstOrNull { it.columnId == mark.columnId } ?: return false
            return transition != null && column.chartColumnIndex > transition.chartColumnIndex
        }
        fun bucket(mark: PartographMark): Int {
            val offset = if (isSecondStage(mark)) stageOffset ?: 0.0 else 0.0
            return kotlin.math.floor((mark.hour - offset) * subdivisions + 0.000001).toInt()
        }
        // Stage-relative bands can overlap first-stage bands when the second-stage
        // clock starts off-grid. Resolve the tapped side of the transition first.
        val candidates = timedPartographObservations(observations)
            .filter { it.observation.columnId.isNotBlank() }
            .flatMap { timed ->
                (if (halfHourly) 1..2 else 1..1).mapNotNull { half ->
                    buildPartographMarkForColumn(observations, origin, timed.observation.columnId, field, half)
                }
            }.filter { isSecondStage(it) == tapIsSecondStage && bucket(it) == tapBucket }
        val existing = candidates.firstOrNull { it.columnId == selectedColumnId } ?: candidates.maxByOrNull { it.timestamp }
        if (existing != null) {
            if (value.isBlank()) return existing
            val key = existing.values.keys.first { it.removeSuffix("Half1").removeSuffix("Half2") == field }
            return existing.copy(values = existing.values + (key to value))
        }
        if (stageStart != null && origin + (hour * 3_600_000).toLong() >= stageStart) {
            val stageHour = (origin + hour * 3_600_000 - stageStart) / 3_600_000.0
            val mark = buildPartographMark(observations, stageStart, stageHour, field, value)
            return identifyScheduledColumn(mark.copy(hour = mark.hour + (stageStart - origin) / 3_600_000.0), observations, origin)
        }
        return identifyScheduledColumn(buildPartographMark(observations, origin, hour, field, value), observations, origin)
    }
    return identifyScheduledColumn(buildPartographMark(observations, firstTimestampMillis, hour, field, value), observations, origin)
}

/** Empty graph targets must use the same indexed id as the available-column selector. */
private fun identifyScheduledColumn(
    mark: PartographMark, observations: List<LabourCareObservation>, origin: Long
): PartographMark {
    if (mark.columnId.isNotBlank()) return mark
    val time = `in`.drarunbabu.drsharmila.jananisync.data.chartMillis(mark.timestamp) ?: return mark
    val index = `in`.drarunbabu.drsharmila.jananisync.data.stageColumnIndex(observations, time)
        ?: `in`.drarunbabu.drsharmila.jananisync.data.canonicalColumnIndex(observations, origin, time)
        ?: return mark
    return mark.copy(columnId = `in`.drarunbabu.drsharmila.jananisync.data.scheduledColumnId(time, index))
}

internal fun buildPartographMark(
    observations: List<LabourCareObservation>, origin: Long, hour: Double, field: String, value: String
): PartographMark {
    val snapped = ((hour * 2).roundToInt() / 2.0).coerceIn(0.0, if (field == "contractionFrequency") 11.5 else 12.0)
    val time = origin + (snapped * 3_600_000).toLong()
    val halfHourly = field in partographHalfHourlyFields
    val timed = timedPartographObservations(observations)
    val existing = (timed.firstOrNull { time - it.epochMillis in -1000L..1000L }
        ?: timed.firstOrNull { halfHourly && time - it.epochMillis in 1_799_000L..1_801_000L })?.observation
    val half = if (existing != null && time - Instant.parse(existing.timestamp).toEpochMilliseconds() > 1000) 2
        else if (existing == null && halfHourly && snapped % 1.0 != 0.0) 2 else 1
    val values = partographMarkValues(field, half, existing, value.ifEmpty { existing?.recordedPartographText(field, half).orEmpty() })
    return PartographMark(field, snapped, existing?.timestamp ?: Instant.fromEpochMilliseconds(time - if (half == 2) 1_800_000 else 0).toString(), existing?.columnId.orEmpty(), values)
}

internal data class PartographNumber(val min: Double, val max: Double? = null, val decimal: Boolean = false) {
    val hint: String get() {
        fun display(value: Double) = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        val range = max?.let { "${display(min)}–${display(it)}" } ?: "${display(min)} or more"
        return if (decimal) range else "$range · whole numbers"
    }
    fun acceptsInput(value: String): Boolean = value.length <= 12 &&
        value.all { it in '0'..'9' || (decimal && it == '.') } && value.count { it == '.' } <= 1
    fun stepByOne(value: String, increase: Boolean): String? {
        if (value.isBlank()) return if (increase) (if (min % 1.0 == 0.0) min.toLong().toString() else min.toString()).takeIf(::isValid) else null
        if (!isValid(value)) return null
        val whole = value.substringBefore('.').ifBlank { "0" }.toLongOrNull() ?: return null
        val next = whole + if (increase) 1 else -1
        if (next < 0) return null
        val fraction = value.substringAfter('.', "")
        val text = if (fraction.isEmpty()) next.toString() else "$next.$fraction"
        return text.takeIf(::isValid)
    }
    fun isValid(value: String): Boolean = acceptsInput(value) && value.toDoubleOrNull()?.let {
        it.isFinite() && it >= min && (max == null || it <= max) && (decimal || it % 1.0 == 0.0)
    } == true
}

internal fun partographNumber(field: String): PartographNumber? = when (field.removeSuffix("Half1").removeSuffix("Half2")) {
    "fetalHeartRate" -> PartographNumber(80.0, 200.0)
    "pulse" -> PartographNumber(30.0, 220.0)
    "systolic" -> PartographNumber(60.0, 250.0)
    "diastolic" -> PartographNumber(30.0, 180.0)
    "cervicalDilation" -> PartographNumber(4.0, 10.0)
    "descent", "contractionFrequency" -> PartographNumber(0.0, 5.0)
    "temperature" -> PartographNumber(30.0, 45.0, decimal = true)
    "gravida" -> PartographNumber(0.0)
    "urineVolume", "rupturedHours" -> PartographNumber(0.0, decimal = true)
    else -> null
}

internal fun PartographMark.isComplete(): Boolean {
    return !values.any { (key, value) -> partographNumber(key)?.isValid(value) == false } && when (field) {
        "contractionFrequency" -> values.entries.first { it.key.startsWith("contractionFrequency") }.value.toIntOrNull() in 0..5 &&
                partographDuration(values.entries.first { it.key.startsWith("contractionDuration") }.value) != null

        "systolic", "diastolic" -> (parameterValue("systolic")?.toIntOrNull() ?: 0) in 60..250 &&
                (parameterValue("diastolic")?.toIntOrNull() ?: 0) in 30..180 && parameterValue("systolic")!!.toInt() > parameterValue("diastolic")!!.toInt()

        "fetalHeartRate" -> values.values.first().toIntOrNull() in 80..200
        "cervicalDilation" -> values.values.first().toIntOrNull() in 4..10
        "descent" -> values.values.first().toIntOrNull() in 0..5
        "pulse" -> values.values.first().toIntOrNull() in 30..220
        "temperature" -> values.values.first().toDoubleOrNull()?.let { it in 30.0..45.0 } == true
        "urineVolume", "gravida", "rupturedHours" -> values.values.first().toDoubleOrNull()?.let { it >= 0 } == true
        else -> values.values.all { it.isNotBlank() }
    }

}

@Composable
internal fun PartographParameterOverlay(mark: PartographMark, busy: Boolean, onChange: (PartographMark) -> Unit, onSave: () -> Unit, onCancel: () -> Unit, saveEnabled: Boolean = true, erasableKeys: Set<String> = emptySet(), onErase: ((String) -> Unit)? = null) {
    var erasingKey by remember(mark.columnId, mark.field, mark.timestamp) { mutableStateOf<String?>(null) }
    LaunchedEffect(mark.values) { erasingKey = null }
    Card(Modifier.widthIn(max = 680.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), elevation = CardDefaults.cardElevation(8.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Review mark · ${`in`.drarunbabu.drsharmila.jananisync.ui.formatUiTimestamp(mark.observationTimestamp())}", style = MaterialTheme.typography.titleSmall)
            AdaptiveFields {
                mark.values.forEach { (key, value) ->
                    val base = key.removeSuffix("Half1").removeSuffix("Half2")
                    val label = when (base) {
                        "fetalHeartRate" -> "Fetal heart rate (bpm)"
                        "pulse" -> "Maternal pulse (bpm)"
                        "cervicalDilation" -> "Cervical dilation (cm)"
                        "descent" -> "Head descent (fifths palpable)"
                        "contractionFrequency" -> "Count / 10 min"
                        "contractionDuration" -> "Contraction duration"
                        "systolic" -> "Systolic mmHg"
                        "diastolic" -> "Diastolic mmHg"
                        "gravida" -> "Gravida"
                        "rupturedHours" -> "Membranes ruptured (hours)"
                        else -> partographLanes.firstOrNull { it.field == base }?.label ?: base
                    }
                    val number = partographNumber(base)
                    val options = when (base) {
                        "contractionDuration" -> PartographDuration.entries.map { it.storedValue }
                        "amnioticFluid" -> listOf("I", "C", "M+", "M++", "M+++", "B", "A")
                        else -> categoricalOptionsForCell(base)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(label, style = MaterialTheme.typography.labelMedium)
                        if (number != null) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { input ->
                                    if (number.acceptsInput(input)) onChange(mark.copy(values = mark.values + (key to input)))
                                },
                                enabled = !busy,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = if (number.decimal) KeyboardType.Decimal else KeyboardType.Number),
                                isError = value.isNotBlank() && !number.isValid(value),
                                supportingText = { Text(number.hint) },
                                trailingIcon = {
                                    val increased = number.stepByOne(value, increase = true)
                                    val decreased = number.stepByOne(value, increase = false)
                                    Column(Modifier.width(48.dp)) {
                                        IconButton(enabled = !busy && increased != null, modifier = Modifier.size(48.dp), onClick = {
                                            increased?.let { onChange(mark.copy(values = mark.values + (key to it))) }
                                        }) { Icon(Icons.Default.ArrowDropUp, contentDescription = "Increase $label by 1") }
                                        IconButton(enabled = !busy && decreased != null, modifier = Modifier.size(48.dp), onClick = {
                                            decreased?.let { onChange(mark.copy(values = mark.values + (key to it))) }
                                        }) { Icon(Icons.Default.ArrowDropDown, contentDescription = "Decrease $label by 1") }
                                    }
                                }
                            )
                        } else if (options != null) {
                            var expanded by remember(key) { mutableStateOf(false) }
                            Box {
                                OutlinedButton(enabled = !busy, onClick = { expanded = true }) { Text(if (base == "contractionDuration") partographDuration(value)?.label ?: "Select" else value.ifBlank { "Select" }) }
                                DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 280.dp)) {
                                    options.forEach { option -> DropdownMenuItem(text = { Text(if (base == "contractionDuration") partographDuration(option)?.label ?: option else option.ifBlank { "Not recorded" }) }, onClick = {
                                        onChange(mark.copy(values = mark.values + (key to option))); expanded = false
                                    }) }
                                }
                            }
                        } else OutlinedTextField(value, { onChange(mark.copy(values = mark.values + (key to it.take(1000)))) }, enabled = !busy, modifier = Modifier.fillMaxWidth(), singleLine = base !in setOf("medicine", "intravenousFluids"))
                        if (onErase != null && key in erasableKeys) TextButton(enabled = !busy, onClick = { erasingKey = key }) {
                            Text("Erase saved value")
                        }
                    }
                }
            }
            if (mark.field in setOf("systolic", "diastolic")) {
                val systolic = mark.parameterValue("systolic")?.toIntOrNull()
                val diastolic = mark.parameterValue("diastolic")?.toIntOrNull()
                if (systolic != null && diastolic != null && systolic <= diastolic)
                    Text("Systolic must be greater than diastolic.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (mark.field == "contractionFrequency") Text("Duration controls shading: <20 s dotted · 20–40 s hatched · >40 s solid. Choose the duration range to set contraction shading.", style = MaterialTheme.typography.bodySmall)
            val target = erasingKey?.takeIf { it in erasableKeys }
            if (target != null && onErase != null) {
                val base = target.removeSuffix("Half1").removeSuffix("Half2")
                val label = when (base) {
                    "contractionFrequency" -> "contraction count"
                    "contractionDuration" -> "contraction duration"
                    "systolic" -> "systolic pressure"
                    "diastolic" -> "diastolic pressure"
                    else -> "selected reading"
                }
                Text("Erase the saved $label at this time? Its history will be retained.", style = MaterialTheme.typography.bodySmall)
                WrappingActions {
                    Button(enabled = !busy, onClick = { onErase(target) }) { Text(if (busy) "Erasing…" else "Confirm erase") }
                    TextButton(enabled = !busy, onClick = { erasingKey = null }) { Text("Keep value") }
                }
            } else {
                Text("Review the time and values, then confirm and save. Existing readings can be erased with their history retained.", style = MaterialTheme.typography.bodySmall)
                WrappingActions {
                    Button(enabled = !busy && saveEnabled && mark.isComplete(), onClick = onSave) { Text(if (busy) "Saving…" else "Confirm and save") }
                    TextButton(enabled = !busy, onClick = onCancel) { Text("Cancel") }
                }
            }
        }
    }
}
