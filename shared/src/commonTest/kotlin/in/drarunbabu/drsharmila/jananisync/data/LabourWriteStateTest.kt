package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LabourWriteStateTest {
    private fun state() = LabourWriteState().apply {
        reset(observing = true)
        received(LabourWriteState.Source.Observations)
        received(LabourWriteState.Source.Columns)
    }

    private fun observation(revision: Int = 1, columnId: String = "column") = LabourCareObservation(
        id = "event-$revision", sessionId = "admission", patientLabel = "Test", parity = 0,
        timestamp = "2026-10-24T08:00:00Z", maternalVitals = MaternalVitals("", 0, 0, 80, 0.0),
        fetalWellbeing = FetalWellbeing("", 0, AmnioticFluidStatus.UNKNOWN), laborProgress = LaborProgress("", 0.0, 0, 0),
        columnId = columnId, revision = revision, womanDocumentId = "woman"
    )

    private fun control(revision: Int = 1, confirmed: Boolean = false, columnId: String = "column") = LabourColumnControl(
        columnId, 20, 20, listOf("author"), revision, confirmed,
        confirmedBy = if (confirmed) "author" else "", confirmerAccessLevel = if (confirmed) 20 else 0,
        cellAccessLevels = mapOf("pulse" to 20), womanDocumentId = "woman"
    )

    @Test fun bothInitialSnapshotsAreRequiredAndListenerErrorsRemainUntilTheirSourceRecovers() {
        val state = LabourWriteState()
        state.reset(observing = true)
        state.received(LabourWriteState.Source.Observations)
        assertFalse(state.ready)
        assertTrue(state.overlay(LabourCareFeed(isLoading = false)).isLoading)
        state.failed(LabourWriteState.Source.Columns, "Column listener failed")
        state.received(LabourWriteState.Source.Corrections)
        assertEquals("Column listener failed", state.overlay(LabourCareFeed()).message)
        state.received(LabourWriteState.Source.Columns)
        assertTrue(state.ready)
        assertFalse(state.overlay(LabourCareFeed()).isLoading)
        assertNull(state.retainedMessage("Column listener failed"))
        assertEquals("Save failed", state.retainedMessage("Save failed"))
        state.failed(LabourWriteState.Source.Observations, "Observation listener failed")
        state.received(LabourWriteState.Source.Columns)
        assertFalse(state.ready)
        assertEquals("Observation listener failed", state.overlay(LabourCareFeed()).message)
    }

    @Test fun serializedCreationsSeeTheSuccessfulWriteBeforeAnyListenerSnapshot() = runTest {
        val state = state()
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        var secondAllowed = true
        val first = launch {
            state.serialize { token ->
                assertTrue(state.overlay(LabourCareFeed()).admissionColumnState("admission", "woman").canStartColumn)
                gate.await()
                writes++
                state.acknowledge(token, control(), observation())
            }
        }
        runCurrent()
        val second = launch {
            state.serialize {
                secondAllowed = state.overlay(LabourCareFeed()).admissionColumnState("admission", "woman").canStartColumn
                if (secondAllowed) writes++
            }
        }
        runCurrent()
        assertEquals(0, writes)
        gate.complete(Unit)
        first.join()
        second.join()
        assertEquals(2, writes)
        assertTrue(secondAllowed)
    }

    @Test fun failedCreationDoesNotReserveAColumnAndTheQueuedAttemptCanRetry() = runTest {
        val state = state()
        val gate = CompletableDeferred<Unit>()
        var retried = false
        val failed = launch {
            assertFailsWith<IllegalStateException> {
                state.serialize {
                    gate.await()
                    error("Save failed")
                }
            }
        }
        runCurrent()
        val retry = launch {
            state.serialize {
                retried = state.overlay(LabourCareFeed()).admissionColumnState("admission", "woman").canStartColumn
            }
        }
        runCurrent()
        gate.complete(Unit)
        failed.join()
        retry.join()
        assertTrue(retried)
    }

    @Test fun queuedOldOrganizationWriteCannotRunAfterReset() = runTest {
        val state = state()
        val gate = CompletableDeferred<Unit>()
        var ranOldWrite = false
        val first = launch { state.serialize { gate.await() } }
        runCurrent()
        val queued = launch {
            assertFailsWith<IllegalArgumentException> { state.serialize { ranOldWrite = true } }
        }
        runCurrent()
        state.reset(observing = true)
        gate.complete(Unit)
        first.join()
        queued.join()
        assertFalse(ranOldWrite)
    }

    @Test fun acknowledgementCannotPublishAfterContextResetAndOldColumnsAreCleared() {
        val state = state()
        val oldToken = state.generation
        state.acknowledge(oldToken, control(), observation())
        state.reset(observing = true)
        assertTrue(state.overlay(LabourCareFeed()).observations.isEmpty())
        assertFailsWith<IllegalArgumentException> { state.acknowledge(oldToken, control(), observation()) }
        assertFalse(state.ready)
    }

    @Test fun confirmationSurvivesStaleSnapshotsAndImmediatelyAllowsTheNextColumn() {
        val state = state()
        val stale = LabourCareFeed(observations = listOf(observation()), columnControls = listOf(control()), isLoading = false)
        val confirmed = observation(2).copy(cellKey = "initials", fullDilationConfirmed = true)
        state.acknowledge(state.generation, control(2, confirmed = true), confirmed)
        repeat(2) {
            val current = state.overlay(stale)
            assertTrue(current.columnControls.single().confirmed)
            assertTrue(current.observations.single().fullDilationConfirmed)
            assertTrue(current.admissionColumnState("admission", "woman").canStartColumn)
        }
        val loadedControl = control(2, confirmed = true).copy(confirmationEventId = "confirmation-event", confirmerAccessLevel = 0)
        val current = state.overlay(stale.copy(columnControls = listOf(loadedControl)))
        assertEquals("confirmation-event", current.columnControls.single().confirmationEventId)
        assertEquals(20, current.columnControls.single().confirmerAccessLevel)
    }

    @Test fun correctionRevisionsAndCellAccessCannotBeRolledBackButNewerRemoteValuesWin() {
        val state = state()
        val corrected = observation(3).copy(maternalVitals = MaternalVitals("", 0, 0, 90, 0.0), correctionReason = "Correction")
        val correctedControl = control(3, confirmed = true).copy(cellAccessLevels = mapOf("pulse" to 25))
        state.acknowledge(state.generation, correctedControl, corrected)
        val stale = LabourCareFeed(observations = listOf(observation(2)), columnControls = listOf(control(2, confirmed = true)))
        val current = state.overlay(stale)
        assertEquals(90, current.observations.single().maternalVitals.pulseBpm)
        assertEquals(25, current.columnControls.single().cellAccessLevel("pulse"))
        val newer = corrected.copy(revision = 4, maternalVitals = corrected.maternalVitals.copy(pulseBpm = 95))
        val remote = state.overlay(stale.copy(observations = listOf(newer), columnControls = listOf(correctedControl.copy(revision = 4))))
        assertEquals(95, remote.observations.single().maternalVitals.pulseBpm)
        assertEquals(4, remote.columnControls.single().revision)
    }

    @Test fun lockedOrganizationDoesNotExposeAcknowledgedPlaintext() {
        val state = state()
        state.acknowledge(state.generation, control(), observation())
        val locked = state.overlay(LabourCareFeed(), includeAcknowledgements = false)
        assertTrue(locked.observations.isEmpty())
        assertTrue(locked.columnControls.isEmpty())
    }
}
