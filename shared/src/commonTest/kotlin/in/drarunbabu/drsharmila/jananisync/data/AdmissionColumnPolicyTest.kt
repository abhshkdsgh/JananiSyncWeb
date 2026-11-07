package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlin.test.*

class AdmissionColumnPolicyTest {
    private fun observation(id: String = "column", session: String = "admission") = LabourCareObservation(
        id = "event-$id", columnId = id, sessionId = session, patientLabel = "Test", parity = 0,
        timestamp = "2026-10-24T08:00:00Z", maternalVitals = MaternalVitals("", 120, 80, 80, 37.0),
        fetalWellbeing = FetalWellbeing("", 140, AmnioticFluidStatus.CLEAR),
        laborProgress = LaborProgress("", 4.0, 3, 30)
    )

    private fun control(id: String = "column", confirmed: Boolean = false, woman: String = "woman") =
        LabourColumnControl(id, 10, 10, listOf("user"), 1, confirmed, womanDocumentId = woman)

    @Test fun loadedStatusAllowsCreationWithoutConfirmingOtherColumns() {
        assertFalse(LabourCareFeed().admissionColumnState("admission").canStartColumn)
        val empty = LabourCareFeed(isLoading = false)
        assertTrue(empty.admissionColumnState("admission").canStartColumn)
        assertNull(empty.admissionColumnState("admission").creationBlockMessage)
        val missingStatus = empty.copy(observations = listOf(observation()))
        assertEquals(setOf("column"), missingStatus.admissionColumnState("admission").openColumnIds)
        assertTrue(missingStatus.admissionColumnState("admission").canStartColumn)
        val confirmed = missingStatus.copy(columnControls = listOf(control(confirmed = true)))
        assertTrue(confirmed.admissionColumnState("admission").canStartColumn)
    }

    @Test fun acknowledgedControlRemainsDiscoverableBeforeItsObservationArrives() {
        val feed = LabourCareFeed(columnControls = listOf(control()), isLoading = false)
        assertEquals(setOf("column"), feed.admissionColumnState("admission", "woman").openColumnIds)
        assertTrue(feed.admissionColumnState("admission", "woman").canStartColumn)
        assertTrue(feed.admissionColumnState("another-admission", "another-woman").canStartColumn)
        // Blank admission bindings cannot be used to attach arbitrary controls to a patient.
        assertTrue(feed.admissionColumnState("admission").canStartColumn)
    }

    @Test fun headersPlaceholdersAndOtherAdmissionsDoNotBlockEntry() {
        val feed = LabourCareFeed(
            observations = listOf(
                observation("header-admission").copy(isGuideHeader = true),
                observation("").copy(cellKey = "skipped"),
                observation("legacy").copy(cellKey = "skipped"),
                observation("other", "another-admission")
            ),
            columnControls = listOf(control("header-admission"), control("other", woman = "another-woman")),
            isLoading = false
        )
        assertTrue(feed.admissionColumnState("admission", "woman").canStartColumn)
    }

    @Test fun pendingObservationIsScopedToItsAdmissionAndRespectsConfirmation() {
        val feed = LabourCareFeed(isLoading = false)
        val pending = observation()
        assertTrue(feed.admissionColumnState("admission", pendingObservation = pending).canStartColumn)
        assertTrue(feed.admissionColumnState("another-admission", pendingObservation = pending).canStartColumn)
        assertTrue(feed.copy(columnControls = listOf(control(confirmed = true)))
            .admissionColumnState("admission", pendingObservation = pending).canStartColumn)
    }

    @Test fun openColumnTrackingIsIndependentOfCreationEligibility() {
        val feed = LabourCareFeed(
            observations = listOf(observation("one"), observation("two")),
            columnControls = listOf(control("one"), control("two")), isLoading = false
        )
        assertEquals(setOf("one", "two"), feed.admissionColumnState("admission").openColumnIds)
        val oneRemaining = feed.copy(columnControls = listOf(control("one", confirmed = true), control("two")))
        assertEquals(setOf("two"), oneRemaining.admissionColumnState("admission").openColumnIds)
        assertTrue(feed.copy(columnControls = listOf(control("one", true), control("two", true)))
            .admissionColumnState("admission").canStartColumn)
    }
}
