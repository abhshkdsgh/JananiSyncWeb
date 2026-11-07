package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.*

internal fun LabourCareObservation.withCell(field: String, value: String): LabourCareObservation {
        val recorded = recordedCellKeys + field
        if (field.endsWith("Half1") || field.endsWith("Half2")) {
            return copy(halfHourCells = halfHourCells + (field to value), recordedCellKeys = recorded)
        }
        if (field == "pushingStarted") return copy(pushingStarted = value == "true", recordedCellKeys = recorded)
        return when (field) {
        "gravida", "urineVolume", "rupturedHours" -> copy(partographCells = partographCells + (field to value))
        "patientLabel" -> copy(patientLabel = value)
        "parity" -> copy(parity = value.toIntOrNull() ?: 0)
        "labourOnset" -> copy(labourOnset = value)
        "activeLabourDiagnosis" -> copy(activeLabourDiagnosis = value)
        "rupturedMembranes" -> copy(rupturedMembranesDateTime = value)
        "riskFactors" -> copy(riskFactors = value)
        "companion" -> copy(companion = value)
        "painRelief" -> copy(painRelief = value)
        "oralFluid" -> copy(oralFluid = value)
        "posture" -> copy(posture = value)
        "fetalHeartRate" -> copy(fetalWellbeing = fetalWellbeing.copy(fetalHeartRateBpm = value.toIntOrNull() ?: 0))
        "fhrDeceleration" -> copy(fetalWellbeing = fetalWellbeing.copy(heartRateDeceleration = value))
        "amnioticFluid" -> copy(fetalWellbeing = fetalWellbeing.copy(amnioticFluidFinding = value))
        "fetalPosition" -> copy(fetalWellbeing = fetalWellbeing.copy(fetalPosition = value))
        "caput" -> copy(fetalWellbeing = fetalWellbeing.copy(caput = value))
        "moulding" -> copy(fetalWellbeing = fetalWellbeing.copy(moulding = value))
        "pulse" -> copy(maternalVitals = maternalVitals.copy(pulseBpm = value.toIntOrNull() ?: 0))
        "systolic" -> copy(maternalVitals = maternalVitals.copy(systolicBloodPressure = value.toIntOrNull() ?: 0))
        "diastolic" -> copy(maternalVitals = maternalVitals.copy(diastolicBloodPressure = value.toIntOrNull() ?: 0))
        "temperature" -> copy(maternalVitals = maternalVitals.copy(temperatureCelsius = value.toDoubleOrNull() ?: 0.0))
        "urineProtein" -> copy(maternalVitals = maternalVitals.copy(urineProtein = value))
        "urineAcetone" -> copy(maternalVitals = maternalVitals.copy(urineAcetone = value))
        "contractionFrequency" -> copy(laborProgress = laborProgress.copy(contractionFrequencyPer10Minutes = value.toIntOrNull() ?: 0))
        "contractionDuration" -> copy(laborProgress = laborProgress.copy(contractionDurationSeconds = value.toIntOrNull() ?: 0))
        "cervicalDilation" -> copy(laborProgress = laborProgress.copy(cervicalDilationCm = value.toDoubleOrNull() ?: 0.0))
        "oxytocin" -> copy(oxytocin = value)
        "medicine" -> copy(medicine = value)
        "intravenousFluids" -> copy(intravenousFluids = value)
        "descent" -> copy(descent = value)
        "assessment" -> copy(assessment = value)
        "plan" -> copy(plan = value)
        else -> this
        }.copy(recordedCellKeys = recorded, partographCells = partographCells + (field to value)).let { updated ->
            if (field in setOf("fetalHeartRate", "fhrDeceleration", "contractionFrequency", "contractionDuration", "oxytocin") && "${field}Half1" in halfHourCells)
                updated.copy(halfHourCells = updated.halfHourCells + ("${field}Half1" to value)) else updated
        }
    }

/** Exact stored text, without display formatting or an invented value for an absent cell. */
internal fun LabourCareObservation.cellValue(key: String): String {
    partographMarkCells[key]?.let { return it }
    if (cellKey == key && editedValue != null) return editedValue
    if (key.endsWith("Half1") || key.endsWith("Half2")) return halfHourCells[key].orEmpty()
    partographCells[key]?.let { return it }
    return when (key) {
        "patientLabel" -> patientLabel
        "parity" -> parity.toString()
        "labourOnset" -> labourOnset
        "activeLabourDiagnosis" -> activeLabourDiagnosis
        "rupturedMembranes" -> rupturedMembranesDateTime
        "riskFactors" -> riskFactors
        "companion" -> companion
        "painRelief" -> painRelief
        "oralFluid" -> oralFluid
        "posture" -> posture
        "fetalHeartRate" -> fetalWellbeing.fetalHeartRateBpm.toString()
        "fhrDeceleration" -> fetalWellbeing.heartRateDeceleration
        "amnioticFluid" -> fetalWellbeing.amnioticFluidFinding.ifBlank { when (fetalWellbeing.amnioticFluidStatus) {
            AmnioticFluidStatus.CLEAR -> "C"
            AmnioticFluidStatus.MECONIUM_STAINED -> "M"
            AmnioticFluidStatus.BLOOD_STAINED -> "B"
            AmnioticFluidStatus.ABSENT -> "A"
            AmnioticFluidStatus.INTACT -> "I"
            AmnioticFluidStatus.UNKNOWN -> "U"
        } }
        "fetalPosition" -> fetalWellbeing.fetalPosition
        "caput" -> fetalWellbeing.caput
        "moulding" -> fetalWellbeing.moulding
        "pulse" -> maternalVitals.pulseBpm.toString()
        "systolic" -> maternalVitals.systolicBloodPressure.toString()
        "diastolic" -> maternalVitals.diastolicBloodPressure.toString()
        "temperature" -> maternalVitals.temperatureCelsius.toString()
        "urineProtein" -> maternalVitals.urineProtein
        "urineAcetone" -> maternalVitals.urineAcetone
        "contractionFrequency" -> laborProgress.contractionFrequencyPer10Minutes.toString()
        "contractionDuration" -> laborProgress.contractionDurationSeconds.toString()
        "cervicalDilation" -> laborProgress.cervicalDilationCm.toString()
        "oxytocin" -> oxytocin
        "medicine" -> medicine
        "intravenousFluids" -> intravenousFluids
        "descent" -> descent
        "assessment" -> assessment
        "plan" -> plan
        "initials" -> initials
        "pushingStarted" -> pushingStarted.toString()
        "fullDilationConfirmed" -> fullDilationConfirmed.toString()
        "birthConfirmed" -> birthConfirmed.toString()
        else -> ""
    }
}

internal fun LabourCareObservation.emptyCells() = copy(patientLabel = "", parity = 0,
    labourOnset = "", activeLabourDiagnosis = "", rupturedMembranesDateTime = "", riskFactors = "",
    maternalVitals = MaternalVitals("", 0, 0, 0, 0.0),
    fetalWellbeing = FetalWellbeing("", 0, AmnioticFluidStatus.UNKNOWN),
    laborProgress = LaborProgress("", 0.0, 0, 0), companion = "", painRelief = "", oralFluid = "", posture = "",
    oxytocin = "", medicine = "", intravenousFluids = "", descent = "", assessment = "", plan = "", initials = "",
    halfHourCells = emptyMap(), recordedCellKeys = emptySet(), partographCells = emptyMap(), partographMarkCells = emptyMap(),
    fullDilationConfirmed = false, secondStageStartTimestamp = "", birthConfirmed = false, pushingStarted = false)

/** Only the event's authorized fields may change the visible column. */
internal fun applyCellEvent(previous: LabourCareObservation?, event: LabourCareObservation, keys: Set<String>): LabourCareObservation {
    // The second-stage clock is column metadata carried in each encrypted
    // snapshot, not a cell value. Keep it when constructing the first merged
    // view so later edits continue to use the stage-relative schedule.
    var merged = previous ?: event.emptyCells().copy(secondStageStartTimestamp = event.secondStageStartTimestamp)
    for (key in keys) merged = when (key) {
        "initials" -> merged.copy(initials = event.initials)
        "fullDilationConfirmed" -> merged.copy(fullDilationConfirmed = merged.fullDilationConfirmed || event.fullDilationConfirmed,
            secondStageStartTimestamp = if (merged.fullDilationConfirmed) merged.secondStageStartTimestamp else event.secondStageStartTimestamp)
        "birthConfirmed" -> merged.copy(birthConfirmed = merged.birthConfirmed || event.birthConfirmed)
        else -> merged.withCell(key, event.cellValue(key))
    }
    return merged.copy(id = event.id, cellKey = event.cellKey, revision = event.revision, editedValue = event.editedValue,
        authorId = event.authorId, authorName = event.authorName, authorAccessLevel = event.authorAccessLevel,
        firestoreDocumentId = event.firestoreDocumentId, createdAtMillis = event.createdAtMillis,
        secondStageStartTimestamp = merged.secondStageStartTimestamp.ifBlank { event.secondStageStartTimestamp },
        partographMarkCells = event.partographMarkCells.filterKeys { it in keys }, correctionReason = event.correctionReason)
}
