package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AdmissionBindingPreparationTest {
    private val first = AdmissionColumnBinding("column-a", "roster-a", "admission-a")
    private val second = AdmissionColumnBinding("column-b", "roster-a", "admission-a")

    @Test fun failedPreparationIsNotRetriedOrClearedByRepeatedFeedUpdates() = runTest {
        val preparation = AdmissionBindingPreparation()
        var attempts = 0
        preparation.prepare(listOf(first)) { attempts++; error("Permission denied") }
        repeat(5) { preparation.prepare(listOf(first)) { attempts++ } }
        preparation.prepare(emptyList()) { attempts++ }
        assertEquals(1, attempts)
        assertEquals(setOf(first), preparation.failures.value)
        preparation.prepare(listOf(second)) { attempts++ }
        assertEquals(setOf(first), preparation.failures.value)
        preparation.prepare(listOf(first), retry = true) { attempts++ }
        assertEquals(3, attempts)
        assertTrue(preparation.failures.value.isEmpty())
    }

    @Test fun ownSnapshotCannotRestartAnInFlightWriteAndOtherColumnsWait() = runTest {
        val preparation = AdmissionBindingPreparation()
        val gate = CompletableDeferred<Unit>()
        val calls = mutableListOf<AdmissionColumnBinding>()
        val original = launch { preparation.prepare(listOf(first)) { calls += it; gate.await() } }
        runCurrent()
        assertTrue(preparation.running.value)
        val snapshot = launch { preparation.prepare(listOf(first, second)) { calls += it } }
        runCurrent()
        assertEquals(listOf(first), calls)
        gate.complete(Unit)
        original.join()
        snapshot.join()
        assertEquals(listOf(first, second), calls)
        assertFalse(preparation.running.value)
    }

    @Test fun cancelledAttemptCanBePreparedAgainWithoutBeingRecordedAsFailure() = runTest {
        val preparation = AdmissionBindingPreparation()
        val attempt = launch { preparation.prepare(listOf(first)) { awaitCancellation() } }
        runCurrent()
        attempt.cancelAndJoin()
        assertTrue(preparation.failures.value.isEmpty())
        var completed = false
        preparation.prepare(listOf(first)) { completed = true }
        assertTrue(completed)
    }

    @Test fun planningIgnoresCellEditsAndKeepsWarningsScopedToTheirAdmission() {
        val woman = Woman(1, 2, "Test", 20000101, 0, Ward("ward", "Ward", "", ""), 0, "", "", "", "admission-a",
            firestoreDocumentId = "roster-a")
        val observation = LabourCareObservation("event", "admission-a", "Test", 0, "time",
            maternalVitals = MaternalVitals("time", 0, 0, 0, 0.0),
            fetalWellbeing = FetalWellbeing("time", 0, AmnioticFluidStatus.UNKNOWN),
            laborProgress = LaborProgress("time", 0.0, 0, 0), columnId = "column-a")
        val control = LabourColumnControl("column-a", 60, 60, listOf("author"), 1, false)
        val feed = LabourCareFeed(observations = listOf(observation), columnControls = listOf(control), isLoading = false)
        assertEquals(listOf(first), pendingAdmissionBindings(listOf(woman), feed))
        val updated = feed.copy(observations = listOf(observation.copy(revision = 2, editedValue = "90")),
            columnControls = listOf(control.copy(revision = 2)))
        assertEquals(pendingAdmissionBindings(listOf(woman), feed), pendingAdmissionBindings(listOf(woman), updated))
        assertTrue(pendingAdmissionBindings(listOf(woman.copy(admissionId = "different")), feed).isEmpty())
        assertTrue(pendingAdmissionBindings(listOf(woman), feed.copy(columnControls = listOf(control.copy(womanDocumentId = "roster-a")))).isEmpty())
    }
}
