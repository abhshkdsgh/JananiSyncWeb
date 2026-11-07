package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlin.test.*

class WomanListFilteringTest {
    private val ward = Ward("ward-a", "Ward A", "", "")
    private val woman = Woman(12345, 67890, "Mary  Jane", 20000101, 0, ward, 0, "Informant",
        "+91 98765-43210", "(044) 2345 6789", "admission-a")
    private val observation = LabourCareObservation("event", woman.admissionId, woman.name, 0, "time",
        maternalVitals = MaternalVitals("time", 80, 120, 80, 37.0),
        fetalWellbeing = FetalWellbeing("time", 140, AmnioticFluidStatus.UNKNOWN),
        laborProgress = LaborProgress("time", 0.0, 0, 0))

    @Test fun searchesAllIdentifiersAndBothFormattedMobileNumbers() {
        for (query in listOf("", "  ", "mary jane", "MARY", "12345", "7890", "9876543210", "+91 98765 43210", "04423456789", "2345-6789")) {
            assertTrue(woman.matchesWomanSearch(query), query)
        }
        for (query in listOf("another woman", "999999999", "abc123", "+")) {
            assertFalse(woman.matchesWomanSearch(query), query)
        }
    }

    @Test fun statusUsesOnlyTheSelectedAdmissionAndBirthTakesPrecedence() {
        val foreignBirth = observation.copy(sessionId = "another-admission", birthConfirmed = true)
        assertEquals(WomanLabourStatus.YetToStart, admissionLabourStatus(woman.admissionId, listOf(foreignBirth), true))
        assertEquals(WomanLabourStatus.YetToStart, admissionLabourStatus(woman.admissionId, listOf(observation), true))
        val onset = observation.copy(isGuideHeader = true, labourOnset = "spontaneous")
        assertEquals(WomanLabourStatus.InLabour, admissionLabourStatus(woman.admissionId, listOf(onset), true))
        val diagnosis = observation.copy(isGuideHeader = true, activeLabourDiagnosis = "202610251000")
        assertEquals(WomanLabourStatus.InLabour, admissionLabourStatus(woman.admissionId, listOf(diagnosis), true))
        val progress = observation.copy(laborProgress = observation.laborProgress.copy(cervicalDilationCm = 5.0))
        assertEquals(WomanLabourStatus.InLabour, admissionLabourStatus(woman.admissionId, listOf(progress), true))
        val second = observation.copy(fullDilationConfirmed = true)
        assertEquals(WomanLabourStatus.SecondStage, admissionLabourStatus(woman.admissionId, listOf(onset, second), true))
        assertEquals(WomanLabourStatus.BirthGiven, admissionLabourStatus(woman.admissionId,
            listOf(onset, second, observation.copy(birthConfirmed = true)), true))
    }

    @Test fun lockedOrUnloadedRecordsNeverImplyLabourHasNotStarted() {
        assertEquals(WomanLabourStatus.Unknown, admissionLabourStatus(woman.admissionId, emptyList(), false))
        assertEquals(WomanLabourStatus.Unknown, admissionLabourStatus("", listOf(observation), true))
        assertTrue(WomanListFilter.All.matches(WomanLabourStatus.Unknown))
        assertFalse(WomanListFilter.YetToStart.matches(WomanLabourStatus.Unknown))
    }

    @Test fun inLabourIncludesSecondStageButExcludesBirthAndYetToStart() {
        assertTrue(WomanListFilter.InLabour.matches(WomanLabourStatus.InLabour))
        assertTrue(WomanListFilter.InLabour.matches(WomanLabourStatus.SecondStage))
        assertFalse(WomanListFilter.InLabour.matches(WomanLabourStatus.BirthGiven))
        assertFalse(WomanListFilter.InLabour.matches(WomanLabourStatus.YetToStart))
        assertFalse(WomanListFilter.SecondStage.matches(WomanLabourStatus.InLabour))
        assertTrue(WomanListFilter.BirthGiven.matches(WomanLabourStatus.BirthGiven))
    }
}
