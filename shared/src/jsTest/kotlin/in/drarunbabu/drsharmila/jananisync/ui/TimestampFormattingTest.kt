package `in`.drarunbabu.drsharmila.jananisync.ui

import kotlin.js.Date
import kotlin.test.Test
import kotlin.test.assertEquals

class TimestampFormattingTest {
    @Test
    fun formatsLocalTimeWithEnglishMonthAndZeroPadding() {
        assertEquals("04 Jan 2026 03:02:01", formatUiTimestamp(Date(2026, 0, 4, 3, 2, 1).getTime().toLong()))
        assertEquals("24 Oct 2026 14:35:09", formatUiTimestamp(Date(2026, 9, 24, 14, 35, 9).getTime().toLong()))
    }

    @Test
    fun offsetsRepresentingSameInstantDisplayIdentically() {
        assertEquals(formatUiTimestamp("2026-10-24T20:30:00Z"), formatUiTimestamp("2026-10-25T02:00:00+05:30"))
    }

    @Test
    fun incompleteLegacyTimestampsRemainReadable() {
        assertEquals("", formatUiTimestamp(""))
        assertEquals("unknown", formatUiTimestamp("unknown"))
    }
}
