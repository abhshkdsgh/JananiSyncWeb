package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlin.test.*
import kotlin.time.Instant

class PartographEntrySequenceTest {
    private val baseline = Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
    private fun column(hour: Double, id: String = "column-$hour") = LabourCareObservation(
        id = id, columnId = id, sessionId = "admission", patientLabel = "Test", parity = 0,
        timestamp = Instant.fromEpochMilliseconds(baseline + (hour * PARTOGRAPH_HOUR_MILLIS).toLong()).toString(),
        maternalVitals = MaternalVitals("", 120, 80, 80, 37.0),
        fetalWellbeing = FetalWellbeing("", 140, AmnioticFluidStatus.CLEAR), laborProgress = LaborProgress("", 4.0, 3, 30)
    )

    @Test fun initialEntryIsHourZeroAndCannotStartAtALaterGraphPosition() {
        val sequence = emptyList<LabourCareObservation>().partographEntrySequence()
        assertEquals(0, sequence.nextHour)
        assertEquals(baseline, sequence.nextTimestampMillis(baseline))
        assertNull(sequence.creationBlockMessage(column(0.0).timestamp, baseline))
        assertNotNull(sequence.creationBlockMessage(column(0.5).timestamp, baseline))
        assertNotNull(sequence.creationBlockMessage(column(3.0).timestamp, baseline))
    }

    @Test fun allScheduledWholeHoursRemainAvailableForBackfill() {
        val sequence = listOf(column(0.0)).partographEntrySequence()
        assertEquals(1, sequence.nextHour)
        assertNull(sequence.creationBlockMessage(column(1.0).timestamp))
        for (hour in listOf(0.0, 2.0, 6.0)) assertNull(sequence.creationBlockMessage(column(hour).timestamp))
        for (hour in listOf(0.5, 1.5)) assertNotNull(sequence.creationBlockMessage(column(hour).timestamp))
        val next = listOf(column(1.0), column(0.0)).partographEntrySequence()
        assertEquals(2, next.nextHour)
        assertNull(next.creationBlockMessage(column(2.0).timestamp))
        assertNull(next.creationBlockMessage(column(3.0).timestamp))
    }

    @Test fun existingTimesArePreservedAndFirstMissingHourIsSuggested() {
        val offGrid = listOf(column(0.0), column(5.0 + 17.0 / 60)).partographEntrySequence()
        assertEquals(1, offGrid.nextHour)
        assertNull(offGrid.creationBlockMessage(column(6.0).timestamp))
        assertNotNull(offGrid.creationBlockMessage(column(6.0 + 17.0 / 60).timestamp))
        assertEquals(1, listOf(column(0.0), column(3.0)).partographEntrySequence().nextHour)
    }

    @Test fun headerSkippedAndLegacyUncontrolledEventsDoNotConsumeAnEntryHour() {
        val sequence = listOf(column(0.0).copy(isGuideHeader = true), column(1.0).copy(cellKey = "skipped"),
            column(2.0).copy(columnId = "")).partographEntrySequence()
        assertEquals(0, sequence.nextHour)
    }

    @Test fun offsetsAndNewerSnapshotsUseTheSameRecordedTimes() {
        val first = column(0.0).copy(timestamp = "2026-10-24T13:30:00+05:30")
        val sequence = listOf(first, first.copy(revision = 2)).partographEntrySequence()
        assertEquals(1, sequence.nextHour)
        assertNull(sequence.creationBlockMessage("2026-10-24T14:30:00+05:30"))
        assertEquals(0.0, sequence.recordingHour(first.timestamp))
    }

    @Test fun invalidTimesAndBirthPreventNewOrdinaryColumns() {
        assertNotNull(listOf(column(0.0).copy(timestamp = "invalid")).partographEntrySequence()
            .creationBlockMessage(column(1.0).timestamp))
        assertNotNull(listOf(column(0.0).copy(birthConfirmed = true)).partographEntrySequence()
            .creationBlockMessage(column(1.0).timestamp))
    }

    @Test fun evenConfirmedColumnsMustHaveLoadedTimesBeforeAdvancing() {
        val control = LabourColumnControl("missing", 10, 10, listOf("user"), 2, true, womanDocumentId = "woman")
        assertNotNull(listOf(column(0.0)).partographEntrySequence(listOf(control), "woman")
            .creationBlockMessage(column(1.0).timestamp))
        assertNull(listOf(column(0.0)).partographEntrySequence(listOf(control), "other-woman")
            .creationBlockMessage(column(1.0).timestamp))
        assertNull(listOf(column(0.0)).partographEntrySequence(listOf(control.copy(columnId = "header-admission")), "woman")
            .creationBlockMessage(column(1.0).timestamp))
    }
}
