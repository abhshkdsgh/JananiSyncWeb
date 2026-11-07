package `in`.drarunbabu.drsharmila.jananisync.data

import kotlin.test.*
class ChartTimelineTest {
    @Test fun startWarningTimezoneOffsetIdentifiesTheSelectedInstant() {
        for (iso in listOf("2026-01-24T08:17:00Z", "2026-07-24T08:17:00Z")) {
            val time = chartMillis(iso)!!
            val wall = `in`.drarunbabu.drsharmila.jananisync.ui.editableChartTime(time).replace(' ', 'T')
            val zone = `in`.drarunbabu.drsharmila.jananisync.ui.chartTimezoneLabel(time).removePrefix("UTC")
            assertEquals(time, chartMillis("$wall:00$zone"))
        }
    }

    @Test fun scheduleUsesInstantsAndChecksHalfHoursIndependently() {
        val start = chartMillis("2026-10-24T08:00:00Z")!!
        assertEquals(start, chartMillis("2026-10-24T13:30:00+05:30"))
        assertNull(columnTimeError(start, start, start))
        assertNotNull(columnTimeError(start, start + CHART_HOUR, start))
        assertNotNull(columnTimeError(start, start + 1000, start + CHART_HOUR))
        assertEquals(start + 1800000, observationTime("2026-10-24T08:00:00Z", listOf("fetalHeartRateHalf2")))
        assertEquals(start, scheduledColumnTime(scheduledColumnId(start)))
        assertNull(scheduledColumnTime("another-column"))
    }
    @Test fun missingCellsStayBlankAndDoNotBecomeRecordedZeroes() {
        val start = chartMillis("2026-10-24T08:00:00Z")!!
        val columns = scheduledObservations(emptyList(), start, 4, "admission", "Patient")
        assertEquals(listOf(0, 1, 2, 3), columns.map { it.chartColumnIndex })
        assertTrue(columns.all { it.cellKey == "scheduled" && it.recordedCellKeys.isEmpty() })
        assertEquals(start + 3 * CHART_HOUR, chartMillis(columns.last().timestamp))
    }
}
