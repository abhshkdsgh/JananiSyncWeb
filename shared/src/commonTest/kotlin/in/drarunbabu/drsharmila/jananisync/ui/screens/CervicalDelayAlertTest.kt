package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.data.model.AmnioticFluidStatus
import `in`.drarunbabu.drsharmila.jananisync.data.model.FetalWellbeing
import `in`.drarunbabu.drsharmila.jananisync.data.model.LaborProgress
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.MaternalVitals
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CervicalDelayAlertTest {
    @Test fun explicitLegacyFirstHalfEraseOverridesTheOldBaseValue() {
        val erased = column("2026-10-24T08:00:00Z", "5").copy(halfHourCells = mapOf("cervicalDilationHalf1" to ""))
        val current = column("2026-10-24T14:00:00Z", "5")
        assertFalse(cervicalDelayAlert("5", listOf(erased, current), current.timestamp))
    }
    @Test fun decimalProgressDoesNotCountAsAnUnchangedPlateau() {
        val first = column("2026-10-24T08:00:00Z", "5.0")
        val current = column("2026-10-24T14:00:00Z", "5.9")
        assertFalse(cervicalDelayAlert("5.9", listOf(first, current), current.timestamp))
    }
    private fun column(time: String, value: String? = null): LabourCareObservation = LabourCareObservation(
        id = time, sessionId = "admission", patientLabel = "", parity = 0, timestamp = time,
        maternalVitals = MaternalVitals("", 0, 0, 0, 0.0),
        fetalWellbeing = FetalWellbeing("", 0, AmnioticFluidStatus.UNKNOWN),
        laborProgress = LaborProgress("", value?.toDoubleOrNull() ?: 0.0, 0, 0),
        cellKey = if (value == null) "pulse" else "cervicalDilation",
        recordedCellKeys = if (value == null) setOf("pulse") else setOf("cervicalDilation"),
        partographCells = if (value == null) emptyMap() else mapOf("cervicalDilation" to value)
    )

    @Test fun gapsAndPlaceholdersNeitherStartNorInterruptAnExaminationPlateau() {
        val first = column("2026-10-24T08:00:00Z", "5")
        val gap = column("2026-10-24T09:00:00Z").copy(laborProgress = LaborProgress("", 9.0, 0, 0))
        val placeholder = first.copy(timestamp = "2026-10-24T10:00:00Z", cellKey = "scheduled")
        val current = column("2026-10-24T14:00:00Z", "5")
        assertTrue(cervicalDelayAlert("5", listOf(current, placeholder, gap, first), current.timestamp))
        assertFalse(cervicalDelayAlert("5", listOf(placeholder, gap), current.timestamp))
        assertFalse(cervicalDelayAlert("5", listOf(first, gap), "2026-10-24T13:59:59Z"))
    }

    @Test fun erasureIsNotAnExaminationAndCannotInventAZeroOrPlateau() {
        val erased = column("2026-10-24T08:00:00Z", "")
        val later = column("2026-10-24T14:00:00Z", "5")
        assertFalse(cervicalDelayAlert("5", listOf(erased, later), later.timestamp))
        val first = column("2026-10-24T07:00:00Z", "5")
        assertTrue(cervicalDelayAlert("5", listOf(first, erased, later), later.timestamp))
    }

    @Test fun laterDifferentExamEndsPlateauIncludingSecondHalfAndFutureIsIgnored() {
        val first = column("2026-10-24T08:00:00Z", "5")
        val next = column("2026-10-24T09:00:00Z", "5")
            .copy(halfHourCells = mapOf("cervicalDilationHalf2" to "6"))
        val last = column("2026-10-24T14:00:00Z", "5")
        assertFalse(cervicalDelayAlert("5", listOf(first, next, last), last.timestamp))
        assertTrue(cervicalDelayAlert("5", listOf(first, last, column("2026-10-24T15:00:00Z", "6")), last.timestamp))
    }

    @Test fun erasedSecondHalfIsIgnoredButARecordedSecondHalfUsesItsOwnTime() {
        val first = column("2026-10-24T08:00:00Z", "5")
        val erased = column("2026-10-24T09:00:00Z").copy(halfHourCells = mapOf("cervicalDilationHalf2" to ""))
        val last = column("2026-10-24T14:00:00Z", "5")
        assertTrue(cervicalDelayAlert("5", listOf(first, erased, last), last.timestamp))
        val secondHalf = column("2026-10-24T08:00:00Z").copy(halfHourCells = mapOf("cervicalDilationHalf2" to "5"))
        assertFalse(cervicalDelayAlert("5", listOf(secondHalf, last), "2026-10-24T14:29:59Z"))
        assertTrue(cervicalDelayAlert("5", listOf(secondHalf, last), "2026-10-24T14:30:00Z"))
    }

    @Test fun timestampsAreComparedAsInstantsAndThresholdIsInclusive() {
        val first = column("2026-10-24T13:30:00+05:30", "8")
        assertFalse(cervicalDelayAlert("8", listOf(first), "2026-10-24T10:29:59Z"))
        assertTrue(cervicalDelayAlert("8", listOf(first), "2026-10-24T10:30:00Z"))
        assertFalse(cervicalDelayAlert("", listOf(first), "2026-10-24T10:30:00Z"))
    }
}
