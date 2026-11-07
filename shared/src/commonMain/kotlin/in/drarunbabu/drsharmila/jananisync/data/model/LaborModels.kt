package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MaternalVitals(
    val timestamp: String,
    val systolicBloodPressure: Int,
    val diastolicBloodPressure: Int,
    val pulseBpm: Int,
    val temperatureCelsius: Double,
    val urineProtein: String = "",
    val urineAcetone: String = ""
)

@Serializable
data class FetalWellbeing(
    val timestamp: String,
    val fetalHeartRateBpm: Int,
    val amnioticFluidStatus: AmnioticFluidStatus,
    val heartRateDeceleration: String = "",
    val fetalPosition: String = "",
    val caput: String = "",
    val moulding: String = "",
    val amnioticFluidFinding: String = ""
)

@Serializable
enum class AmnioticFluidStatus {
    @kotlinx.serialization.SerialName("clear") CLEAR,
    @kotlinx.serialization.SerialName("meconium_stained") MECONIUM_STAINED,
    @kotlinx.serialization.SerialName("blood_stained") BLOOD_STAINED,
    @kotlinx.serialization.SerialName("absent") ABSENT,
    @kotlinx.serialization.SerialName("intact") INTACT,
    @kotlinx.serialization.SerialName("unknown") UNKNOWN
}

@Serializable
data class LaborProgress(
    val timestamp: String,
    val cervicalDilationCm: Double,
    /** Number of contractions in a 10-minute observation window. */
    val contractionFrequencyPer10Minutes: Int,
    val contractionDurationSeconds: Int
)

/** One synthetic WHO LCG-style observation. Encrypt this entire object before Firestore writes. */
@Serializable
data class LabourCareObservation(
    val id: String,
    val sessionId: String,
    val patientLabel: String,
    val parity: Int,
    val timestamp: String,
    val labourOnset: String = "",
    val activeLabourDiagnosis: String = "",
    val rupturedMembranesDateTime: String = "",
    val riskFactors: String = "",
    val maternalVitals: MaternalVitals,
    val fetalWellbeing: FetalWellbeing,
    val laborProgress: LaborProgress,
    val companion: String = "",
    val painRelief: String = "",
    val oralFluid: String = "",
    val posture: String = "",
    val oxytocin: String = "",
    val medicine: String = "",
    val intravenousFluids: String = "",
    val descent: String = "",
    val assessment: String = "",
    val plan: String = "",
    val initials: String = "",
    /** Firebase UID of the author; the Firestore envelope also records this for audit. */
    val authorId: String = "",
    /** Organization display name captured in the encrypted event; UID remains authoritative. */
    val authorName: String = "",
    /** Server timestamp in epoch milliseconds, populated from the append-only envelope on read. */
    val createdAtMillis: Long = 0L,
    /** Firestore's random document ID used as the immutable correction target. */
    val firestoreDocumentId: String = "",
    /** Access level frozen by rules when this observation was authored. */
    val authorAccessLevel: Int = 0,
    val columnId: String = "",
    val cellKey: String = "",
    /** Exact submitted text preserves explicit clears and numeric zero in the audit trail. */
    val editedValue: String? = null,
    val revision: Int = 0,
    /** Half-hour observations keyed by encrypted cell event name, e.g. fetalHeartRateHalf1. */
    val halfHourCells: Map<String, String> = emptyMap(),
    /** Marks the hour in second stage when pushing began. */
    val pushingStarted: Boolean = false,
    /** Exact cells explicitly entered; prevents zero-valued numeric defaults appearing as observations. */
    val recordedCellKeys: Set<String> = emptySet(),
    val partographCells: Map<String, String> = emptyMap(),
    /** Values committed together by one graph confirmation. */
    val partographMarkCells: Map<String, String> = emptyMap(),
    /** Required explanation for a revision made after column confirmation. */
    val correctionReason: String = "",
    /** Confirmation that ends first stage and advances the guide to second stage. */
    val fullDilationConfirmed: Boolean = false,
    val secondStageStartTimestamp: String = "",
    /** Presentation-only lock for second-stage slots awaiting confirmation. */
    @kotlinx.serialization.Transient val stageEntryLocked: Boolean = false,
    /** Birth marked by confirmation of the final second-stage column. */
    val birthConfirmed: Boolean = false,
    val isGuideHeader: Boolean = false,
    val womanDocumentId: String = "",
    /** Shared immutable chart reference; absent only on legacy observations. */
    val chartStartTimestamp: String = "",
    val chartColumnIndex: Int = -1,
    /** Actual measurement time; defaults to its scheduled cell time on new entries. */
    val observedTimestamp: String = ""
)

@Serializable
data class LabourColumnControl(
    val columnId: String,
    val lastEditorAccessLevel: Int,
    val highestEditorAccessLevel: Int,
    val highestEditorUids: List<String>,
    val revision: Int,
    val confirmed: Boolean,
    val confirmedBy: String = "",
    /** Access frozen separately for each explicitly edited cell. */
    val cellAccessLevels: Map<String, Int> = emptyMap(),
    /** Reconstructed from immutable history for columns written before per-cell metadata. */
    @kotlinx.serialization.Transient
    val legacyCellAccessLevels: Map<String, Int> = emptyMap(),
    val confirmationEventId: String = "",
    /** Frozen access from the immutable confirmation event, never current membership. */
    @kotlinx.serialization.Transient
    val confirmerAccessLevel: Int = 0,
    val womanDocumentId: String = "",
    val sessionId: String = "",
    val timestampMillis: Long? = null,
    val chartStartMillis: Long? = null,
    val chartColumnIndex: Int? = null
) {
    fun cellAccessLevel(cellKey: String): Int {
        val base = cellKey.removeSuffix("Half1")
        if (base in setOf("fetalHeartRate", "fhrDeceleration", "contractionFrequency", "contractionDuration", "oxytocin")) return maxOf(
            cellAccessLevels[base] ?: 0, cellAccessLevels["${base}Half1"] ?: 0,
            legacyCellAccessLevels[base] ?: 0, legacyCellAccessLevels["${base}Half1"] ?: 0)
        return cellAccessLevels[cellKey] ?: legacyCellAccessLevels[cellKey] ?: 0
    }

    fun canEditCell(cellKey: String, accessLevel: Int): Boolean =
        accessLevel >= cellAccessLevel(cellKey)

    /** Re-signing requires a strictly higher level than the immutable current sign-off. */
    fun canSignOff(accessLevel: Int): Boolean = accessLevel in 10..99 &&
        (!confirmed || (confirmerAccessLevel in 10..99 && accessLevel > confirmerAccessLevel))

    fun canCorrect(accessLevel: Int): Boolean =
        confirmed && accessLevel in 10..99

    fun canCorrectCell(cellKey: String, accessLevel: Int): Boolean =
        cellKey != "initials" && cellKey != "birthConfirmed" && cellKey != "fullDilationConfirmed" && confirmed && canEditCell(cellKey, accessLevel)
}

/** Append-only amendment; the referenced observation is never updated or deleted. */
@Serializable
data class ObservationCorrection(
    val id: String,
    val originalObservationId: String,
    val correctionText: String,
    val authorId: String,
    val timestamp: String,
    val createdAtMillis: Long = 0L,
    val originalDocumentId: String = ""
)


/** Only events authored for this exact cell belong in its timeline. */
fun List<LabourCareObservation>.cellEditHistory(
    sessionId: String,
    columnId: String,
    cellKey: String
): List<LabourCareObservation> = if (columnId.isBlank()) emptyList() else filter {
    it.sessionId == sessionId && it.columnId == columnId && (it.cellKey == cellKey || cellKey in it.partographMarkCells ||
        (cellKey == "oxytocinHalf1" && (it.cellKey == "oxytocin" || "oxytocin" in it.partographMarkCells)) ||
        (cellKey == "oxytocin" && (it.cellKey == "oxytocinHalf1" || "oxytocinHalf1" in it.partographMarkCells)) ||
        (cellKey == "birthConfirmed" && it.cellKey == "initials" && it.birthConfirmed) ||
        (cellKey == "fullDilationConfirmed" && it.cellKey == "initials" && it.fullDilationConfirmed))
}.sortedBy { it.revision }

/** Header cells use the same encrypted events and column controls as observation cells. */
val guideHeaderFields = listOf(
    "patientLabel" to "Name / ID", "parity" to "Parity",
    "labourOnset" to "Labour onset", "activeLabourDiagnosis" to "Active labour diagnosis",
    "rupturedMembranes" to "Ruptured membranes · date / time", "riskFactors" to "Risk factors"
)

/** Paper partograph header; the LCG retains guideHeaderFields. */
val partographHeaderFields = listOf(
    "patientLabel" to "Name", "gravida" to "Gravida (G)", "parity" to "Parity (P)",
    "livingChildren" to "Living children (L)", "gestationalWeeks" to "POG · completed weeks",
    "gestationalDays" to "POG · additional days", "rupturedHours" to "Ruptured membranes (hours)"
)
val deliveryMethods = listOf("Spontaneous vaginal", "Vacuum suction", "Forceps / other instrumental", "LSCS")
val deliveryOutcomeFields = listOf(
    "deliveryMethod" to "Delivery method", "deliveryDateTime" to "Date / time of delivery",
    "lscsIndication" to "Indication for LSCS", "babySex" to "Baby sex",
    "babyWeightGrams" to "Birth weight (g)", "babyStatus" to "Baby outcome",
    "apgar1" to "Apgar at 1 minute", "apgar5" to "Apgar at 5 minutes",
    "babyDetails" to "Baby details / resuscitation / NICU (include each baby for multiple births)"
)
val allHeaderFields = (guideHeaderFields + partographHeaderFields + deliveryOutcomeFields).distinctBy { it.first }

fun validHeaderValue(field: String, value: String): Boolean = when (field) {
    "deliveryMethod" -> value.isEmpty() || value in deliveryMethods
    "babySex" -> value in listOf("", "Male", "Female", "Indeterminate")
    "babyStatus" -> value in listOf("", "Live birth", "Stillbirth")
    "apgar1", "apgar5" -> value.isEmpty() || (value.length <= 2 && value.all { it in '0'..'9' } && value.toIntOrNull() in 0..10)
    "babyWeightGrams" -> value.isEmpty() || (value.length <= 5 && value.all { it in '0'..'9' } && (value.toIntOrNull() ?: 0) > 0)
    "gravida", "parity", "livingChildren", "gestationalWeeks" -> value.isEmpty() ||
        (value.length <= 2 && value.all { it in '0'..'9' })
    "gestationalDays" -> value.isEmpty() || (value.length == 1 && value[0] in '0'..'6')
    "rupturedHours" -> value.isEmpty() || (value.length <= 12 && value.all { it in '0'..'9' || it == '.' } &&
        value.count { it == '.' } <= 1 && value.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true)
    "labourOnset" -> value in listOf("spontaneous", "induced", "")
    else -> true
}
