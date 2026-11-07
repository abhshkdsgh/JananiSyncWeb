package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import `in`.drarunbabu.drsharmila.jananisync.ui.screens.displayValueFor

import `in`.drarunbabu.drsharmila.jananisync.data.*
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*
import `in`.drarunbabu.drsharmila.jananisync.ui.screens.lcgFirstHalfValue
import `in`.drarunbabu.drsharmila.jananisync.ui.screens.partographPoints
import `in`.drarunbabu.drsharmila.jananisync.ui.screens.PartographPoint

@OptIn(ExperimentalCoroutinesApi::class)
class PartographSaveTest {
    @Test fun secondStageUsesTheSameOffHourScheduleInBothCharts() = runTest {
        withSelectedAdmission { repository, model ->
            val firstTime = "2026-10-24T08:00:00Z"
            assertTrue(model.savePartographMark("", firstTime, mapOf("pulse" to "80")))
            val first = repository.feed.value.observations.single().copy(fullDilationConfirmed = true,
                secondStageStartTimestamp = "2026-10-24T08:15:00Z")
            repository.feed.value = repository.feed.value.copy(observations = listOf(first))
            val start = chartMillis(firstTime)!!
            val columns = `in`.drarunbabu.drsharmila.jananisync.ui.screens.availablePartographColumns(listOf(first), start, start + 2 * CHART_HOUR)
            assertTrue(columns.any { it.timestamp == "2026-10-24T08:15:00Z" && !it.existing })
            assertFalse(columns.any { it.timestamp == "2026-10-24T09:00:00Z" && !it.existing })
            assertTrue(model.savePartographMark(scheduledColumnId(start + 900_000, 1), "2026-10-24T08:15:00Z", mapOf("pulse" to "81")))
            assertEquals(1, repository.writes.last().chartColumnIndex)
            model.saveCell(scheduledColumnId(start + 4_500_000), "pulse", "82").join()
            assertEquals(2, repository.writes.last().chartColumnIndex)
            assertEquals("2026-10-24T09:15:00Z", repository.writes.last().timestamp)
        }
    }
    @Test fun secondStageAtThePreviousColumnTimeStillHasItsOwnIdentity() = runTest {
        withSelectedAdmission { repository, model ->
            val time = "2026-10-24T08:00:00Z"
            assertTrue(model.savePartographMark("", time, mapOf("pulse" to "80")))
            val first = repository.feed.value.observations.single().copy(fullDilationConfirmed = true, secondStageStartTimestamp = time)
            repository.feed.value = repository.feed.value.copy(observations = listOf(first))
            val target = scheduledColumnId(chartMillis(time)!!, 1)
            assertTrue(model.savePartographMark(target, time, mapOf("pulse" to "81")))
            assertEquals(1, repository.writes.last().chartColumnIndex)
            assertNotEquals(repository.writes.first().columnId, repository.writes.last().columnId)
        }
    }

    @Test fun equivalentTimestampFormatsCanEditTheSameColumn() = runTest {
        withSelectedAdmission { repository, model ->
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            assertTrue(model.savePartographMark("column", "2026-10-24T13:30:00+05:30", mapOf("pulse" to "81")))
            assertEquals(2, repository.writes.size)
        }
    }

    @Test fun lcgAfterPartographKeepsScheduledTimestampOrder() = runTest {
        withSelectedAdmission { repository, model ->
            model.saveCell("", "pulse", "80").join()
            val baseline = kotlin.time.Instant.parse(repository.writes.single().timestamp).toEpochMilliseconds()
            model.confirmColumn("column").join()
            val scheduled = kotlin.time.Instant.fromEpochMilliseconds(baseline + 3_600_000).toString()
            assertTrue(model.savePartographMark("", scheduled, mapOf("pulse" to "81")))
            model.confirmColumn("column-2").join()
            model.saveCell("", "pulse", "82").join()
            val latest = repository.writes.last()
            assertEquals("column-3", latest.columnId)
            assertEquals(baseline + 2 * 3_600_000, kotlin.time.Instant.parse(latest.timestamp).toEpochMilliseconds())
            val uiColumns = repository.feed.value.observations.sortedBy { kotlin.time.Instant.parse(it.timestamp) }
            assertEquals("column-3", uiColumns.last().columnId)
            assertEquals(setOf("column-3"), repository.feed.value.admissionColumnState("A", "woman-a").openColumnIds)
        }
    }

    @Test fun inspectionUiCannotShowPendingColumnUntilObservationFeedArrives() = runTest {
        withSelectedAdmission { repository, model ->
            repository.delayObservationSnapshots = true
            val time = "2026-10-24T08:00:00Z"
            assertTrue(model.savePartographMark("", time, mapOf("pulse" to "80")))
            val uiFeed = model.uiState.value.feed
            assertTrue(uiFeed.observations.isEmpty())
            assertEquals(setOf("column"), uiFeed.admissionColumnState("A", "woman-a").openColumnIds)
            assertNotNull(uiFeed.observations.partographEntrySequence(uiFeed.columnControls, "woman-a").message)
            // ViewModel has an acknowledged pending snapshot that the UI does not receive.
            assertTrue(model.savePartographMark("column", time, mapOf("pulse" to "82")))
        }
    }

    @Test fun startRequiresAcknowledgementAndCannotChangeAfterFirstClinicalSave() = runTest {
        withSelectedAdmission { repository, model ->
            model.setSessionField("chartStartTimestamp", "2026-10-24T08:00:00Z")
            assertFalse(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            assertTrue(repository.writes.isEmpty())
            model.setSessionField("chartStartConfirmed", "true")
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            model.setSessionField("chartStartTimestamp", "2026-10-23T08:00:00Z")
            assertEquals("2026-10-24T08:00:00Z", model.uiState.value.chartStartTimestamp)
            assertEquals(0, repository.writes.single().chartColumnIndex)
        }
    }
    @Test fun acknowledgedFirstSaveLocksStartEvenBeforeObservationListenerArrives() = runTest {
        withSelectedAdmission { repository, model ->
            repository.delayObservationSnapshots = true
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            model.setSessionField("chartStartTimestamp", "2026-10-23T08:00:00Z")
            assertEquals("2026-10-24T08:00:00Z", model.uiState.value.chartStartTimestamp)
        }
    }
    @Test fun lcgRejectsHourThreeClickIfItTargetsHourTwoDocument() = runTest {
        withSelectedAdmission { repository, model ->
            val origin = kotlin.time.Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
            model.saveCell("schedule:$origin", "descent", "5").join()
            model.saveCell("schedule:${origin + 3_600_000}", "descent", "4").join()
            val before = repository.writes.size
            val mismatched = LcgCellTarget(origin + 7_200_000, 2, "column-2").encode()
            model.saveCell(mismatched, "descent", "2").join()
            assertEquals(before, repository.writes.size)
            assertEquals("4", repository.feed.value.observations.first { it.columnId == "column-2" }.descent)
            assertTrue(model.uiState.value.feed.message.orEmpty().contains("time column changed"))
        }
    }

    @Test fun legacyLcgHourThreeDoesNotReuseOpenHourTwo() = runTest {
        withSelectedAdmission { repository, model ->
            val origin = kotlin.time.Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
            model.saveCell("schedule:$origin", "descent", "5").join()
            model.saveCell("schedule:${origin + 3_600_000}", "descent", "4").join()
            // Legacy charts show a new blank column rather than scheduled placeholders.
            repository.feed.value = repository.feed.value.copy(observations = repository.feed.value.observations.map {
                it.copy(chartStartTimestamp = "", chartColumnIndex = -1)
            })
            val before = repository.feed.value.observations
            val thirdColumn = guideDraftColumnId(before, origin)
            assertEquals("schedule:${origin + 7_200_000}", thirdColumn)
            model.saveCell(thirdColumn, "descent", "2").join()
            val after = repository.feed.value.observations
            assertEquals(3, after.size)
            assertEquals("4", after.first { it.columnId == "column-2" }.displayValueFor("descent"))
            assertEquals("2", after.first { it.columnId == "column-3" }.displayValueFor("descent"))
            model.saveCell("column-3", "cervicalDilation", "8").join()
            assertEquals("", repository.feed.value.observations.first { it.columnId == "column-2" }.displayValueFor("cervicalDilation"))
        }
    }

    @Test fun lcgProgressMarksStayInTheirOwnTimeColumn() = runTest {
        withSelectedAdmission { repository, model ->
            val origin = kotlin.time.Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
            model.saveCell("schedule:$origin", "cervicalDilation", "6").join()
            model.saveCell("column", "descent", "4").join()
            model.confirmColumn("column").join()
            // Reproduce draft values left by an earlier LCG plot click.
            model.setCell("cervicalDilation", "6")
            model.setCell("descent", "4")
            model.saveCell("schedule:${origin + 3_600_000}", "pulse", "80").join()
            val next = repository.writes.last()
            assertEquals("", next.displayValueFor("cervicalDilation"))
            assertEquals("", next.displayValueFor("descent"))
            model.saveCell(next.columnId, "cervicalDilation", "8").join()
            model.saveCell(next.columnId, "descent", "2").join()
            val previous = repository.feed.value.observations.first { it.columnId == "column" }
            val current = repository.feed.value.observations.first { it.columnId == next.columnId }
            assertEquals("6", previous.displayValueFor("cervicalDilation"))
            assertEquals("4", previous.displayValueFor("descent"))
            assertEquals("8", current.displayValueFor("cervicalDilation"))
            assertEquals("2", current.displayValueFor("descent"))
        }
    }

    @Test fun lcgOxytocinEditChangesOnlyFirstHalfAndPreservesLaterPartographReading() = runTest {
        withSelectedAdmission { repository, model ->
            val time = "2026-10-24T08:00:00Z"
            assertTrue(model.savePartographMark("", time, mapOf("oxytocinHalf1" to "2 U/L · 10 drops/min")))
            assertTrue(model.savePartographMark("column", time, mapOf("oxytocinHalf2" to "2 U/L · 20 drops/min")))
            model.saveCell("column", "oxytocin", "2 U/L · 12 drops/min").join()
            assertEquals("2 U/L · 12 drops/min", repository.writes.last().halfHourCells["oxytocinHalf1"])
            assertEquals("2 U/L · 20 drops/min", repository.writes.last().halfHourCells["oxytocinHalf2"])
        }
    }
    @Test fun futureStartAndFutureHalfHourAreRejected() = runTest {
        withSelectedAdmission { repository, model ->
            model.setSessionField("chartStartTimestamp", "2099-01-01T00:00:00Z")
            model.setSessionField("chartStartConfirmed", "true")
            assertFalse(model.uiState.value.chartStartConfirmed)
            assertFalse(model.savePartographMark("", "2099-01-01T00:00:00Z", mapOf("pulse" to "80")))
            val start = currentTimeMillis() - 60_000
            val time = kotlin.time.Instant.fromEpochMilliseconds(start).toString()
            model.setSessionField("chartStartTimestamp", time)
            model.setSessionField("chartStartConfirmed", "true")
            assertTrue(model.savePartographMark("", time, mapOf("fetalHeartRateHalf1" to "140")))
            assertFalse(model.savePartographMark("column", time, mapOf("fetalHeartRateHalf2" to "145")))
            assertFalse(model.savePartographMark("column", time, mapOf("pulseHalf2" to "90")))
            assertFalse(model.savePartographMark("column", time, mapOf("systolicHalf2" to "130", "diastolicHalf2" to "80")))
            assertEquals(1, repository.writes.size)
        }
    }

    @Test fun erasingAReadingPreservesHistoryOtherCellsAndTheOtherHalfHour() = runTest {
        withSelectedAdmission { repository, model ->
            val time = "2026-10-24T08:00:00Z"
            assertTrue(model.savePartographMark("", time, mapOf("pulse" to "80", "systolic" to "120", "diastolic" to "80")))
            assertTrue(model.savePartographMark("column", time, mapOf("pulseHalf2" to "95")))
            assertTrue(model.savePartographMark("column", time, mapOf("pulse" to "")))
            val erased = repository.writes.last()
            assertEquals("", erased.partographCells["pulse"])
            assertEquals("95", erased.halfHourCells["pulseHalf2"])
            assertEquals("120", erased.partographCells["systolic"])
            assertEquals("", erased.lcgFirstHalfValue("pulse"))
            assertEquals(listOf(PartographPoint(0.5, 95.0)), partographPoints(listOf(erased), "pulse"))
            val history = repository.writes.cellEditHistory("A", "column", "pulse")
            assertEquals(listOf("80", ""), history.map { it.partographMarkCells["pulse"] })
            assertTrue(model.savePartographMark("column", time, mapOf("systolic" to "")))
            assertEquals("80", repository.writes.last().partographCells["diastolic"])
            val before = repository.writes.size
            val control = repository.feed.value.columnControls.single()
            repository.feed.value = repository.feed.value.copy(columnControls = listOf(control.copy(cellAccessLevels = mapOf("pulseHalf2" to 20))))
            assertFalse(model.savePartographMark("column", time, mapOf("pulseHalf2" to "")))
            assertEquals(before, repository.writes.size)
        }
    }

    @Test fun partographHeaderPersistsObstetricHistoryGestationAndRuptureDurationSeparately() = runTest {
        withSelectedAdmission { repository, model ->
            val fields = mapOf("gravida" to "2", "parity" to "1", "livingChildren" to "1",
                "gestationalWeeks" to "39", "gestationalDays" to "2", "rupturedHours" to "1.5")
            fields.forEach { (field, value) -> model.saveHeaderCell(field, value).join() }
            val header = repository.writes.last()
            assertTrue(header.isGuideHeader)
            assertEquals("header-A", header.columnId)
            fields.forEach { (field, value) -> assertEquals(value, header.partographCells[field]) }
            assertEquals(1, header.parity)
            assertTrue(repository.feed.value.observations.all { it.isGuideHeader })
            val writes = repository.writes.size
            for ((field, value) in listOf("gestationalDays" to "7", "gravida" to "-1", "gestationalWeeks" to "39.5", "rupturedHours" to "-1"))
                model.saveHeaderCell(field, value).join()
            assertEquals(writes, repository.writes.size)
            model.saveHeaderCell("gestationalDays", "").join()
            assertEquals("", repository.writes.last().partographCells["gestationalDays"])
            assertEquals(2, repository.writes.cellEditHistory("A", "header-A", "gestationalDays").size)
            assertEquals("1.5", repository.writes.last().partographCells["rupturedHours"])
        }
    }

    @Test fun rapidHeaderAutosavesAreSerializedAndPreserveNewerValues() = runTest {
        withSelectedAdmission { repository, model ->
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            repository.gate = gate
            val first = model.saveHeaderCell("gravida", "2")
            val second = model.saveHeaderCell("parity", "1")
            val newer = model.saveHeaderCell("gravida", "3")
            assertTrue(model.uiState.value.isSaving)
            assertTrue(repository.writes.isEmpty())
            gate.complete(Unit)
            first.join(); second.join(); newer.join()
            assertEquals(3, repository.writes.size)
            assertEquals("3", repository.writes.last().partographCells["gravida"])
            assertEquals("1", repository.writes.last().partographCells["parity"])
            assertTrue(repository.writes.all { it.columnId == "header-A" })
            model.saveHeaderCell("gravida", "3").join()
            assertEquals(3, repository.writes.size)
        }
    }
    @Test fun queuedHeaderAutosavesNeverApplyToAnotherAdmission() = runTest {
        withSelectedAdmission { repository, model ->
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            repository.gate = gate
            val saving = model.saveHeaderCell("gravida", "2", expectedSessionId = "A", expectedOrgId = "org")
            val queued = model.saveHeaderCell("parity", "1", expectedSessionId = "A", expectedOrgId = "org")
            model.selectPatient("B", "Patient B", "woman-b")
            chooseStart(model)
            gate.complete(Unit)
            saving.join(); queued.join()
            assertEquals(1, repository.writes.size)
            assertEquals("A", repository.writes.single().sessionId)
            model.saveHeaderCell("parity", "1", expectedSessionId = "A", expectedOrgId = "org").join()
            assertEquals(1, repository.writes.size)
            model.saveHeaderCell("parity", "1", expectedSessionId = "B", expectedOrgId = "org").join()
            assertEquals("B", repository.writes.last().sessionId)
            assertEquals("header-B", repository.writes.last().columnId)
        }
    }

    @Test fun savingThePreviousColumnDoesNotUndoArrowNavigation() = runTest {
        withSelectedAdmission { repository, model ->
            model.saveCell("", "pulse", "80").join()
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            repository.gate = gate
            val saving = model.saveCell("column", "pulse", "90")
            val next = "schedule:${kotlin.time.Instant.parse("2026-10-24T09:00:00Z").toEpochMilliseconds()}"
            model.setSessionField("activeColumnId", next)
            gate.complete(Unit)
            saving.join()
            assertEquals(next, model.uiState.value.activeColumnId)
            assertEquals("column", repository.writes.last().columnId)
            assertEquals(90, repository.writes.last().maternalVitals.pulseBpm)
        }
    }

    @Test fun returningToANewColumnWhileSavingKeepsItsNewStableIdSelected() = runTest {
        withSelectedAdmission { repository, model ->
            val origin = kotlin.time.Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
            val first = "schedule:$origin"
            model.setSessionField("activeColumnId", first)
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            repository.gate = gate
            val saving = model.saveCell(first, "pulse", "80")
            model.setSessionField("activeColumnId", "schedule:${origin + 3600000}")
            model.setSessionField("activeColumnId", first)
            gate.complete(Unit)
            saving.join()
            assertEquals("column", model.uiState.value.activeColumnId)
            assertEquals("column", repository.writes.single().columnId)
        }
    }

    private fun chooseStart(model: LabourCareViewModel) {
        if (!model.uiState.value.chartStartConfirmed) {
            model.setSessionField("chartStartTimestamp", "2026-10-24T08:00:00Z")
            model.setSessionField("chartStartConfirmed", "true")
        }
    }

    private class Repository : LaborDataRepository {
        override val feed = MutableStateFlow(LabourCareFeed(isLoading = false))
        override val organizationKeyAvailable = MutableStateFlow(true)
        val writes = mutableListOf<LabourCareObservation>()
        val requestedColumnIds = mutableListOf<String>()
        var fail = false
        var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        var delayObservationSnapshots = false
        private var nextColumnNumber = 0
        private val acknowledgedObservations = mutableMapOf<String, LabourCareObservation>()
        override fun observe(orgId: String, userId: String, accessLevel: Int) = Unit
        override fun stopObserving() = Unit
        override suspend fun publish(observation: LabourCareObservation) = Unit
        override suspend fun saveCell(columnId: String, cellKey: String, observation: LabourCareObservation): String {
            gate?.await()
            if (fail) error("Offline save failed")
            requestedColumnIds += columnId
            val id = columnId.ifBlank {
                if (observation.isGuideHeader) "header-${observation.sessionId}" else {
                    nextColumnNumber++
                    if (nextColumnNumber == 1) "column" else "column-$nextColumnNumber"
                }
            }
            val previousControl = feed.value.columnControls.firstOrNull { it.columnId == id }
            val previousRevision = maxOf(previousControl?.revision ?: 0, acknowledgedObservations[id]?.revision ?: 0)
            val saved = observation.copy(columnId = id, cellKey = cellKey, revision = previousRevision + 1)
            writes += saved
            acknowledgedObservations[id] = saved
            val control = previousControl?.copy(revision = saved.revision)
                ?: LabourColumnControl(id, 10, 10, listOf("user"), saved.revision, false,
                    womanDocumentId = observation.womanDocumentId)
            feed.value = feed.value.copy(
                observations = if (delayObservationSnapshots) feed.value.observations
                    else feed.value.observations.filterNot { it.columnId == id } + saved,
                columnControls = feed.value.columnControls.filterNot { it.columnId == id } + control
            )
            return id
        }
        val confirmations = mutableListOf<LabourCareObservation>()
        override suspend fun confirmColumn(columnId: String, observation: LabourCareObservation) {
            if (fail) error("Confirmation failed")
            confirmations += observation
            val previousControl = feed.value.columnControls.first { it.columnId == columnId }
            val confirmed = observation.copy(columnId = columnId, cellKey = "initials", revision = previousControl.revision + 1)
            acknowledgedObservations[columnId] = confirmed
            feed.value = feed.value.copy(
                observations = if (delayObservationSnapshots) feed.value.observations
                    else feed.value.observations.filterNot { it.columnId == columnId } + confirmed,
                columnControls = feed.value.columnControls.map { control ->
                    if (control.columnId == columnId) control.copy(confirmed = true, confirmedBy = "user",
                        confirmerAccessLevel = control.highestEditorAccessLevel, revision = confirmed.revision) else control
                }
            )
        }
        val corrections = mutableListOf<LabourCareObservation>()
        override suspend fun correctCells(columnId: String, values: Map<String, String>, reason: String, observation: LabourCareObservation) {
            if (fail) error("Correction save failed")
            corrections += observation.copy(correctionReason = reason, partographMarkCells = values)
        }
        override fun close() = Unit

        fun emitObservationSnapshots() {
            feed.value = feed.value.copy(observations =
                feed.value.observations.filterNot { it.columnId in acknowledgedObservations } + acknowledgedObservations.values)
        }
    }

    @Test fun archivedAdmissionsBlockEveryClinicalMutationAndUnarchiveRestoresEditing() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 99)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            model.selectPatient("A", "Patient A", "roster-doc")
            chooseStart(model)
            model.saveCell("", "pulse", "85").join()
            val writesBefore = repository.writes.size
            model.selectPatient("A", "Patient A", "roster-doc", archived = true)
            chooseStart(model)
            assertTrue(model.uiState.value.patientArchived)
            model.saveHeaderCell("riskFactors", "Changed").join()
            model.saveCell("column", "pulse", "95").join()
            model.confirmColumn("column").join()
            model.confirmFullDilation("column").join()
            model.confirmBirth("column").join()
            model.saveObservation().join()
            assertFalse(model.savePartographMark("column", "2026-10-24T08:00:00Z", mapOf("pulse" to "95")))
            assertFalse(model.recordCorrection("column", 1, mapOf("pulse" to "95"), "Change"))
            assertEquals(writesBefore, repository.writes.size)
            assertTrue(repository.confirmations.isEmpty())
            assertTrue(repository.corrections.isEmpty())
            model.selectPatient("A", "Patient A", "roster-doc", archived = false)
            chooseStart(model)
            model.saveCell("column", "pulse", "90").join()
            assertEquals(writesBefore + 1, repository.writes.size)
            assertEquals("roster-doc", repository.writes.last().womanDocumentId)
            assertEquals(90, repository.writes.last().maternalVitals.pulseBpm)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun nextLcgColumnDoesNotReuseConfirmedColumnBeforeFeedRefresh() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.selectPatient("A", "Patient A")
            chooseStart(model)
            model.saveCell("", "pulse", "85").join()
            model.saveCell("", "temperature", "37").join()
            assertEquals(listOf("", "column"), repository.requestedColumnIds)
            repository.fail = true
            model.confirmColumn("column").join()
            repository.fail = false
            model.saveCell("", "pulse", "86").join()
            assertEquals("column", repository.requestedColumnIds.last())
            repository.delayObservationSnapshots = true
            model.confirmColumn("column").join()
            // Confirmation succeeds before its listener snapshot reaches the view model.
            model.saveCell("", "pulse", "90").join()
            assertEquals("", repository.requestedColumnIds.last())
            assertEquals(90, repository.writes.last().maternalVitals.pulseBpm)
            assertEquals("", repository.writes.last().partographCells["temperature"] ?: "")
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun switchingWomenResumesOnlyTheSelectedAdmissionsOpenLcgColumn() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.selectPatient("A", "Patient A")
            chooseStart(model)
            model.saveCell("", "pulse", "85").join()
            model.selectPatient("B", "Patient B")
            chooseStart(model)
            model.saveCell("", "temperature", "37").join()
            model.selectPatient("A", "Patient A")
            chooseStart(model)
            model.saveCell("", "pulse", "90").join()
            assertEquals(listOf("", "", "column"), repository.requestedColumnIds)
            assertEquals(listOf("A", "B", "A"), repository.writes.map { it.sessionId })
            assertEquals(2, repository.feed.value.observations.size)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun birthConfirmationPreservesCellsAndRecordsBirthOnlyOnSuccessfulSave() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            model.selectPatient("admission", "Test")
            chooseStart(model)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "85")))
            repository.fail = true
            model.confirmBirth("column").join()
            assertTrue(repository.confirmations.isEmpty())
            assertFalse(model.uiState.value.isSaving)
            repository.fail = false
            model.confirmBirth("column").join()
            val confirmed = repository.confirmations.single()
            assertTrue(confirmed.birthConfirmed)
            assertFalse(confirmed.fullDilationConfirmed)
            assertEquals(85, confirmed.maternalVitals.pulseBpm)
            val json = kotlinx.serialization.json.Json
            val restored = json.decodeFromString(LabourCareObservation.serializer(),
                json.encodeToString(LabourCareObservation.serializer(), confirmed))
            assertTrue(restored.birthConfirmed)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun fullDilationIsSavedWithConfirmationAndPreservesRecordedCells() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            model.selectPatient("admission", "Test")
            chooseStart(model)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "85")))
            model.confirmFullDilation("column").join()
            val confirmed = repository.confirmations.single()
            assertTrue(confirmed.fullDilationConfirmed)
            assertEquals(85, confirmed.maternalVitals.pulseBpm)
            repository.fail = true
            model.confirmFullDilation("column").join()
            assertEquals(1, repository.confirmations.size)
            assertNull(model.uiState.value.feed.message)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun headerSavesRetainOtherFieldsAndSyncWhenReselectingAdmission() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            model.selectPatient("A", "Patient A")
            chooseStart(model)
            model.saveHeaderCell("labourOnset", "spontaneous").join()
            model.saveHeaderCell("riskFactors", "Prior surgery").join()
            val header = repository.writes.last()
            assertTrue(header.isGuideHeader)
            assertEquals("Patient A", header.patientLabel)
            assertEquals("", header.partographCells["parity"])
            assertEquals("spontaneous", header.labourOnset)
            assertEquals("Prior surgery", header.riskFactors)
            assertEquals(setOf("labourOnset", "riskFactors"), header.recordedCellKeys)
            model.selectPatient("B", "Patient B")
            chooseStart(model)
            assertEquals("", model.uiState.value.riskFactors)
            model.selectPatient("A", "Patient A")
            chooseStart(model)
            assertEquals("Prior surgery", model.uiState.value.riskFactors)
            assertEquals("spontaneous", model.uiState.value.labourOnset)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun headerRemainsEditableAfterHigherAccessEditsAndLegacyConfirmation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            model.selectPatient("A", "Patient A")
            chooseStart(model)
            model.saveHeaderCell("riskFactors", "Original").join()
            val control = repository.feed.value.columnControls.single()
            repository.feed.value = repository.feed.value.copy(columnControls = listOf(control.copy(cellAccessLevels = mapOf("riskFactors" to 20))))
            model.saveHeaderCell("riskFactors", "Updated").join()
            assertEquals(2, repository.writes.size)
            repository.feed.value = repository.feed.value.copy(columnControls = listOf(control.copy(confirmed = true)))
            model.saveHeaderCell("parity", "2").join()
            assertEquals(3, repository.writes.size)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun headerDoesNotSupplyPendingGridColumnAndRejectsDateOnset() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            model.selectPatient("A", "Patient A")
            chooseStart(model)
            model.saveHeaderCell("labourOnset", "2026-10-24").join()
            assertTrue(repository.writes.isEmpty())
            model.saveHeaderCell("parity", "2").join()
            model.saveCell("", "pulse", "80").join()
            assertFalse(repository.writes.last().isGuideHeader)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun headerRemoteEditsAndCorrectionsIgnoreFormerConfirmerThreshold() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 15)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            model.selectPatient("A", "Patient A")
            chooseStart(model)
            model.saveHeaderCell("riskFactors", "Original").join()
            val remote = repository.writes.single().copy(riskFactors = "Remote edit", revision = 2)
            val control = repository.feed.value.columnControls.single().copy(revision = 2, confirmed = true, confirmerAccessLevel = 10)
            repository.feed.value = repository.feed.value.copy(observations = listOf(remote), columnControls = listOf(control))
            assertEquals("Remote edit", model.uiState.value.riskFactors)
            assertTrue(model.recordCorrection(remote.columnId, 2, mapOf("riskFactors" to "Corrected"), "Clinical correction"))
            assertEquals("Corrected", repository.corrections.single().riskFactors)
            repository.feed.value = repository.feed.value.copy(columnControls = listOf(control.copy(confirmerAccessLevel = 11)))
            assertTrue(model.recordCorrection(remote.columnId, 2, mapOf("riskFactors" to "Denied"), "Reason"))
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun switchingAdmissionsClearsDraftAndNeverReusesAnotherPatientsColumn() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            model.selectPatient("A", "Same name")
            chooseStart(model)
            model.setParity("2")
            model.setSessionField("riskFactors", "A only")
            model.setPulse("99")
            model.saveCell("", "pulse", "99").join()
            model.selectPatient("B", "Same name")
            chooseStart(model)
            assertEquals("", model.uiState.value.parity)
            assertEquals("", model.uiState.value.riskFactors)
            assertEquals("", model.uiState.value.pulse)
            model.saveCell("column", "pulse", "70").join()
            assertEquals(1, repository.writes.size)
            model.saveCell("", "pulse", "70").join()
            assertEquals("B", repository.writes.last().sessionId)
            assertEquals("", repository.writes.last().riskFactors)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun oldSaveCompletionCannotRestorePreviousPatientsPendingColumn() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            model.selectPatient("A", "A")
            chooseStart(model)
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            repository.gate = gate
            val oldSave = model.saveCell("", "pulse", "99")
            model.selectPatient("B", "B")
            chooseStart(model)
            gate.complete(Unit)
            oldSave.join()
            model.saveCell("", "pulse", "70").join()
            assertEquals(listOf("A", "B"), repository.writes.map { it.sessionId })
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun graphConfirmationWritesOneCompleteEventAndKeepsExistingValues() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            model.selectPatient("admission", "Test")
            chooseStart(model)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            val parameters = mapOf("contractionFrequencyHalf1" to "3", "contractionDurationHalf1" to "40")
            assertTrue(repository.writes.isEmpty())
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", parameters))
            assertEquals(1, repository.writes.size)
            val first = repository.writes.single()
            assertEquals(parameters, first.partographMarkCells)
            assertEquals(parameters, first.halfHourCells)
            assertTrue(first.recordedCellKeys.containsAll(parameters.keys))
            assertEquals("partographMark", first.cellKey)
            assertTrue(model.savePartographMark("column", first.timestamp, mapOf("pulse" to "80")))
            assertEquals(parameters, repository.writes.last().halfHourCells)
            assertEquals(mapOf("pulse" to "80"), repository.writes.last().partographMarkCells)
            assertEquals(80, repository.writes.last().maternalVitals.pulseBpm)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun confirmedColumnsAllowEditsWhileHigherAccessCellsRemainProtected() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            model.selectPatient("admission", "Test")
            chooseStart(model)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            val control = repository.feed.value.columnControls.single()
            repository.feed.value = repository.feed.value.copy(columnControls = listOf(control.copy(confirmed = true)))
            assertTrue(model.savePartographMark("column", "2026-10-24T08:00:00Z", mapOf("pulse" to "90")))
            repository.feed.value = repository.feed.value.copy(columnControls = listOf(control.copy(lastEditorAccessLevel = 20, cellAccessLevels = mapOf("pulse" to 20))))
            assertFalse(model.savePartographMark("column", "2026-10-24T08:00:00Z", mapOf("pulse" to "90")))
            assertEquals(2, repository.writes.size)
            assertFalse(model.savePartographMark("column", "2026-10-24T08:00:00Z", mapOf("pulse" to "90", "systolic" to "120")))
            assertEquals(2, repository.writes.size)
            assertTrue(model.savePartographMark("column", "2026-10-24T08:00:00Z", mapOf("systolic" to "120")))
            assertEquals(3, repository.writes.size)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun correctionUpdatesSelectedCellsAndRequiresReasonConfirmedStateAndConfirmerAccess() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 30)
            model.selectPatient("admission", "Test")
            chooseStart(model)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80", "temperature" to "37")))
            val original = repository.feed.value.observations.single()
            val control = repository.feed.value.columnControls.single().copy(cellAccessLevels = mapOf("pulse" to 90, "systolic" to 80), confirmerAccessLevel = 25)
            assertFalse(model.recordCorrection("column", 1, mapOf("pulse" to "90"), "Reason"))
            repository.feed.value = repository.feed.value.copy(columnControls = listOf(control.copy(confirmed = true)))
            assertFalse(model.recordCorrection("column", 1, mapOf("pulse" to "90"), "  "))
            repository.feed.value = repository.feed.value.copy(columnControls = listOf(control.copy(confirmed = true, confirmerAccessLevel = 26)))
            assertFalse(model.recordCorrection("column", 1, mapOf("pulse" to "90", "systolic" to "120"), "Reason"))
            repository.feed.value = repository.feed.value.copy(columnControls = listOf(control.copy(confirmed = true)))
            assertFalse(model.recordCorrection("column", 0, mapOf("pulse" to "90"), "Reason"))
            assertTrue(repository.corrections.isEmpty())
            assertTrue(model.recordCorrection("column", 1, mapOf("temperature" to "38", "diastolic" to "80"), "  Transcription error  "))
            val corrected = repository.corrections.single()
            assertEquals(80, corrected.maternalVitals.pulseBpm)
            assertEquals(80, corrected.maternalVitals.diastolicBloodPressure)
            assertEquals(38.0, corrected.maternalVitals.temperatureCelsius)
            assertEquals("Transcription error", corrected.correctionReason)
            assertEquals(mapOf("temperature" to "38", "diastolic" to "80"), corrected.partographMarkCells)
            assertTrue(repository.feed.value.columnControls.single().confirmed)
            assertEquals(80, original.maternalVitals.pulseBpm)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun failedCorrectionReturnsFailureAndCanBeRetried() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 30)
            model.selectPatient("admission", "Test")
            chooseStart(model)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            repository.feed.value = repository.feed.value.copy(columnControls = repository.feed.value.columnControls.map { it.copy(confirmed = true, confirmerAccessLevel = 25) })
            repository.fail = true
            assertFalse(model.recordCorrection("column", 1, mapOf("pulse" to "90"), "Transcription error"))
            assertFalse(model.uiState.value.isSaving)
            assertEquals("Correction save failed", model.uiState.value.feed.message)
            assertTrue(repository.corrections.isEmpty())
            repository.fail = false
            assertTrue(model.recordCorrection("column", 1, mapOf("pulse" to "90"), "Transcription error"))
            assertEquals(1, repository.corrections.size)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun failedConfirmationReturnsFailureWithoutDiscardingParameters() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            model.selectPatient("admission", "Test")
            chooseStart(model)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            repository.fail = true
            val parameters = mapOf("systolic" to "120", "diastolic" to "80")
            assertFalse(model.savePartographMark("", "2026-10-24T08:00:00Z", parameters))
            assertTrue(repository.writes.isEmpty())
            assertFalse(model.uiState.value.isSaving)
            assertEquals("Offline save failed", model.uiState.value.feed.message)
            repository.fail = false
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", parameters))
            assertEquals(parameters, repository.writes.single().partographMarkCells)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun scheduledGapsCanBeBackfilledWithoutConfirmation() = runTest {
        withSelectedAdmission { repository, model ->
            for (hour in listOf(8, 10, 9)) assertTrue(model.savePartographMark("", "2026-10-24T${hour.toString().padStart(2, '0')}:00:00Z", mapOf("pulse" to "80")))
            assertEquals(3, repository.feed.value.observations.size)
            assertTrue(repository.feed.value.columnControls.all { !it.confirmed })
        }
    }

    @Test fun bothViewsKeepTheSameStartAndColumnIdentity() = runTest {
        withSelectedAdmission { repository, model ->
            model.saveCell("", "pulse", "80").join()
            assertTrue(model.savePartographMark("", "2026-10-24T10:00:00Z", mapOf("pulse" to "82")))
            val time = kotlin.time.Instant.parse("2026-10-24T09:00:00Z").toEpochMilliseconds()
            model.saveCell("schedule:$time", "pulse", "81").join()
            assertEquals(3, repository.writes.size)
            assertTrue(repository.writes.all { it.chartStartTimestamp == "2026-10-24T08:00:00Z" })
        }
    }

    private suspend fun TestScope.withSelectedAdmission(
        block: suspend (Repository, LabourCareViewModel) -> Unit
    ) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = Repository()
        val model = LabourCareViewModel(repository)
        try {
            model.observe("org", "user", 10)
            repository.organizationKeyAvailable.value = false
            repository.organizationKeyAvailable.value = true
            model.selectPatient("A", "Patient A", "woman-a")
            chooseStart(model)
            block(repository, model)
        } finally { model.stopObserving(); Dispatchers.resetMain() }
    }

    @Test fun newScheduledColumnsAreAllowedButExistingTimeCannotMove() = runTest {
        withSelectedAdmission { repository, model ->
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            assertTrue(model.savePartographMark("", "2026-10-24T09:00:00Z", mapOf("pulse" to "81")))
            assertFalse(model.savePartographMark("column", "2026-10-24T09:00:00Z", mapOf("pulse" to "82")))
        }
    }

    @Test fun anLcgEntryBlocksAnotherGraphColumnAndAllowsMarksInItsOwnColumn() = runTest {
        withSelectedAdmission { repository, model ->
            model.saveCell("", "pulse", "80").join()
            val current = repository.writes.single()
            assertFalse(model.savePartographMark("", "2099-10-24T09:00:00Z", mapOf("pulse" to "90")))
            assertTrue(model.savePartographMark("", current.timestamp, mapOf("temperature" to "37")))
            assertEquals(listOf("", current.columnId), repository.requestedColumnIds)
            assertEquals(1, repository.feed.value.observations.size)
        }
    }

    @Test fun aGraphEntryIsResumedByLcgInsteadOfOpeningAnotherColumn() = runTest {
        withSelectedAdmission { repository, model ->
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            model.selectPatient("B", "Patient B", "woman-b")
            chooseStart(model)
            model.selectPatient("A", "Patient A", "woman-a")
            chooseStart(model)
            model.saveCell("", "temperature", "37").join()
            assertEquals(listOf("", "column"), repository.requestedColumnIds)
            assertEquals("2026-10-24T08:00:00Z", repository.writes.last().timestamp)
            assertEquals(80, repository.writes.last().maternalVitals.pulseBpm)
            assertEquals(1, repository.feed.value.observations.size)
        }
    }

    @Test fun failedConfirmationDoesNotBlockOtherColumns() = runTest {
        withSelectedAdmission { repository, model ->
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            repository.fail = true
            model.confirmColumn("column").join()
            repository.fail = false
            assertTrue(model.savePartographMark("", "2026-10-24T09:00:00Z", mapOf("pulse" to "81")))
        }
    }

    @Test fun pendingGraphColumnCanBeResumedWithoutListenerSnapshot() = runTest {
        withSelectedAdmission { repository, model ->
            repository.delayObservationSnapshots = true
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            assertTrue(model.savePartographMark("column", "2026-10-24T08:00:00Z", mapOf("temperature" to "37")))
            assertEquals(setOf("column"), repository.writes.map { it.columnId }.toSet())
        }
    }

    @Test fun confirmedCellsCanBeEditedBeforeListenerSnapshot() = runTest {
        withSelectedAdmission { repository, model ->
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            repository.delayObservationSnapshots = true
            model.confirmColumn("column").join()
            assertTrue(model.savePartographMark("column", "2026-10-24T08:00:00Z", mapOf("pulse" to "81")))
        }
    }

    @Test fun multipleOpenColumnsDoNotBlockAnotherScheduledColumn() = runTest {
        withSelectedAdmission { repository, model ->
            for (hour in 8..10) assertTrue(model.savePartographMark("", "2026-10-24T${hour.toString().padStart(2, '0')}:00:00Z", mapOf("pulse" to "80")))
            assertEquals(3, repository.feed.value.columnControls.size)
        }
    }

    @Test fun headerAndAnotherAdmissionsOpenColumnDoNotBlockANewClinicalColumn() = runTest {
        withSelectedAdmission { repository, model ->
            model.saveHeaderCell("riskFactors", "Prior surgery").join()
            val header = repository.writes.single()
            model.selectPatient("B", "Patient B", "woman-b")
            chooseStart(model)
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "90")))
            model.selectPatient("A", "Patient A", "woman-a")
            chooseStart(model)
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            assertEquals(3, repository.feed.value.observations.size)
            assertEquals(3, repository.feed.value.columnControls.map { it.columnId }.distinct().size)
            assertFalse(repository.writes.last().isGuideHeader)
            assertEquals("A", repository.writes.last().sessionId)
            assertEquals("woman-a", repository.writes.last().womanDocumentId)
            assertEquals("Prior surgery", repository.feed.value.observations.first { it.isGuideHeader }.riskFactors)
        }
    }

    @Test fun loadingClinicalStateBlocksGraphAndLcgCreationUntilItIsReady() = runTest {
        withSelectedAdmission { repository, model ->
            repository.feed.value = repository.feed.value.copy(isLoading = true)
            assertFalse(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            model.saveCell("", "temperature", "37").join()
            assertTrue(repository.writes.isEmpty())
            repository.feed.value = repository.feed.value.copy(isLoading = false)
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            assertEquals(1, repository.writes.size)
        }
    }

    @Test fun missingControlPreventsEditingThatColumn() = runTest {
        withSelectedAdmission { repository, model ->
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            repository.feed.value = repository.feed.value.copy(columnControls = emptyList())
            assertFalse(model.savePartographMark("column", "2026-10-24T08:00:00Z", mapOf("pulse" to "90")))
        }
    }

    @Test fun unknownColumnTimesPreventGraphScheduling() = runTest {
        withSelectedAdmission { repository, model ->
            repository.delayObservationSnapshots = true
            assertTrue(model.savePartographMark("", "2026-10-24T08:00:00Z", mapOf("pulse" to "80")))
            model.selectPatient("B", "Patient B", "woman-b")
            chooseStart(model)
            model.selectPatient("A", "Patient A", "woman-a")
            chooseStart(model)
            assertFalse(model.savePartographMark("", "2026-10-24T09:00:00Z", mapOf("pulse" to "81")))
        }
    }

}
