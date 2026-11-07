package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.*

/** Storage validation shared by LCG, graph entries, corrections and repository boundaries. */
internal fun clinicalValueError(field: String, value: String, header: Boolean = false): String? {
    if (value.length > 2000) return "Use at most 2,000 characters."
    if (header) return if (allHeaderFields.any { it.first == field } && validHeaderValue(field, value)) null else "Invalid header value."
    if (field.endsWith("Half1") && field !in setOf("fetalHeartRateHalf1", "fhrDecelerationHalf1", "contractionFrequencyHalf1", "contractionDurationHalf1", "oxytocinHalf1")) return "Use the original first-half cell name."
    val base = field.removeSuffix("Half1").removeSuffix("Half2")
    if (value.isEmpty()) return null // An explicit erase, never numeric zero.
    fun number(min: Double, max: Double, integer: Boolean = true): String? {
        val parsed = value.toDoubleOrNull()
        return if (parsed != null && parsed.isFinite() && parsed in min..max &&
            (!integer || (value.toIntOrNull() != null))) null else
            "Enter ${if (integer) "a whole number" else "a number"} from $min to $max."
    }
    val options = when (base) {
        "companion", "painRelief", "oralFluid" -> setOf("Y", "N", "D", "U")
        "posture" -> setOf("SP", "MO")
        "fhrDeceleration" -> setOf("N", "E", "L", "V")
        "amnioticFluid" -> setOf("I", "C", "M", "M+", "M++", "M+++", "B", "A", "U")
        "fetalPosition" -> setOf("A", "P", "T")
        "caput", "moulding" -> setOf("0", "+", "++", "+++")
        "urineProtein" -> setOf("P-", "P Trace", "P+", "P++", "P+++")
        "urineAcetone" -> setOf("A-", "A+", "A++", "A+++", "A++++")
        "pushingStarted", "fullDilationConfirmed", "birthConfirmed" -> setOf("true", "false")
        else -> null
    }
    if (options != null) return if (value in options) null else "Choose one of: ${options.joinToString()}"
    return when (base) {
        "fetalHeartRate" -> number(80.0, 200.0)
        "pulse" -> number(30.0, 220.0)
        "systolic" -> number(60.0, 250.0)
        "diastolic" -> number(30.0, 180.0)
        "temperature" -> number(30.0, 45.0, false)
        "cervicalDilation" -> number(0.0, 10.0, false)
        "descent", "contractionFrequency" -> number(0.0, 5.0)
        "contractionDuration" -> if (value in setOf("<20", "20-40", ">40")) null else number(1.0, 180.0)
        "gravida" -> number(0.0, 99.0)
        "urineVolume", "rupturedHours" -> number(0.0, 1_000_000.0, false)
        "oxytocin", "medicine", "intravenousFluids", "assessment", "plan" -> null
        else -> "Unknown clinical field."
    }
}

internal fun clinicalEditError(values: Map<String, String>, current: LabourCareObservation? = null, header: Boolean = false): String? {
    for ((key, value) in values) clinicalValueError(key, value, header)?.let { return "$key: $it" }
    if (!header) for (suffix in listOf("", "Half1", "Half2")) {
        val systolic = "systolic$suffix"
        val diastolic = "diastolic$suffix"
        if (systolic !in values && diastolic !in values) continue
        val s = (values[systolic] ?: current?.cellValue(systolic))?.toIntOrNull()
        val d = (values[diastolic] ?: current?.cellValue(diastolic))?.toIntOrNull()
        if (s != null && d != null && s > 0 && d > 0 && s <= d) return "Systolic pressure must be above diastolic pressure."
    }
    return null
}
