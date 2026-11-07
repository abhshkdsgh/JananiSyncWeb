package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CellEditHistoryTest {
    private val event = LabourCareObservation(
        id = "event", sessionId = "patient", patientLabel = "Synthetic", parity = 0, timestamp = "clinical-time",
        maternalVitals = MaternalVitals("clinical-time", 0, 0, 0, 0.0),
        fetalWellbeing = FetalWellbeing("clinical-time", 0, AmnioticFluidStatus.UNKNOWN),
        laborProgress = LaborProgress("clinical-time", 0.0, 0, 0),
        columnId = "column", cellKey = "pulse", revision = 1
    )

    @Test
    fun secondStageHistoryUsesOnlyTheTransitionConfirmationAndPreservesAttribution() {
        val transition = event.copy(cellKey = "initials", fullDilationConfirmed = true, revision = 5,
            authorId = "doctor-uid", authorName = "Doctor", createdAtMillis = 234567L)
        val history = listOf(
            transition, event.copy(fullDilationConfirmed = true, revision = 6),
            transition.copy(sessionId = "other"), transition.copy(columnId = "other"),
            transition.copy(fullDilationConfirmed = false)
        ).cellEditHistory("patient", "column", "fullDilationConfirmed")
        assertEquals(listOf(transition), history)
        assertEquals("doctor-uid", history.single().authorId)
        assertEquals(234567L, history.single().createdAtMillis)
        val control = LabourColumnControl("column", 20, 20, listOf("doctor-uid"), 5, true,
            confirmerAccessLevel = 20)
        kotlin.test.assertFalse(control.canCorrectCell("fullDilationConfirmed", 99))
    }

    @Test
    fun birthHistoryUsesOnlyTheImmutableBirthConfirmationAndPreservesAttribution() {
        val birth = event.copy(cellKey = "initials", birthConfirmed = true, revision = 5,
            authorId = "midwife-uid", authorName = "Midwife", createdAtMillis = 123456L)
        val history = listOf(
            birth, event.copy(birthConfirmed = true, revision = 6),
            birth.copy(sessionId = "other"), birth.copy(columnId = "other"),
            birth.copy(birthConfirmed = false)
        ).cellEditHistory("patient", "column", "birthConfirmed")
        assertEquals(listOf(birth), history)
        assertEquals("midwife-uid", history.single().authorId)
        assertEquals(123456L, history.single().createdAtMillis)
    }

    @Test
    fun confirmationPreservesPerCellEditingAccess() {
        val control = LabourColumnControl("column", 90, 90, listOf("senior"), 2, false,
            cellAccessLevels = mapOf("pulse" to 90, "fetalHeartRateHalf1" to 50))
        kotlin.test.assertFalse(control.canEditCell("pulse", 10))
        assertTrue(control.canEditCell("systolic", 10))
        assertTrue(control.canEditCell("pulse", 90))
        kotlin.test.assertFalse(control.canEditCell("fetalHeartRateHalf1", 10))
        assertTrue(control.canEditCell("fetalHeartRateHalf2", 10))
        assertTrue(control.copy(confirmed = true).canEditCell("systolic", 99))
        kotlin.test.assertFalse(control.copy(legacyCellAccessLevels = mapOf("temperature" to 70)).canEditCell("temperature", 10))
    }

    @Test
    fun correctionEligibilityUsesCellAccessRatherThanConfirmerAccess() {
        val control = LabourColumnControl("column", 90, 90, listOf("senior"), 5, true,
            cellAccessLevels = mapOf("pulse" to 90), confirmerAccessLevel = 20)
        assertTrue(control.canCorrect(24))
        assertTrue(control.canCorrect(25))
        kotlin.test.assertFalse(control.canCorrectCell("pulse", 25))
        assertTrue(control.canCorrectCell("systolic", 25))
        kotlin.test.assertFalse(control.canCorrectCell("initials", 99))
        kotlin.test.assertFalse(control.copy(confirmed = false).canCorrect(99))
        assertTrue(control.copy(confirmerAccessLevel = 0).canCorrect(99))
    }

    @Test
    fun excludesOtherCellsPatientsAndColumnsAndSortsByRevision() {
        val edits = listOf(
            event.copy(revision = 4, editedValue = "0"),
            event.copy(cellKey = "initials", revision = 5),
            event.copy(sessionId = "another-patient"),
            event.copy(columnId = "another-column"),
            event.copy(editedValue = "")
        ).cellEditHistory("patient", "column", "pulse")
        assertEquals(listOf(1, 4), edits.map { it.revision })
        assertEquals(listOf("", "0"), edits.map { it.editedValue })
    }

    @Test
    fun halfHourKeysAreIndependentAndLegacyColumnsHaveNoCellHistory() {
        val edits = listOf(event.copy(cellKey = "fetalHeartRateHalf1"), event.copy(cellKey = "fetalHeartRateHalf2"))
        assertEquals(1, edits.cellEditHistory("patient", "column", "fetalHeartRateHalf1").size)
        assertTrue(listOf(event.copy(columnId = "")).cellEditHistory("patient", "", "pulse").isEmpty())
    }
}
