package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlin.test.*

class PartographHeaderTest {
    @Test fun partographUsesItsOwnPaperHeaderWithoutChangingTheLcgHeader() {
        assertEquals(listOf("patientLabel", "parity", "labourOnset", "activeLabourDiagnosis", "rupturedMembranes", "riskFactors"), guideHeaderFields.map { it.first })
        assertEquals(listOf("patientLabel", "gravida", "parity", "livingChildren", "gestationalWeeks", "gestationalDays", "rupturedHours"), partographHeaderFields.map { it.first })
        assertEquals(allHeaderFields.size, allHeaderFields.map { it.first }.distinct().size)
    }
    @Test fun gestationDaysAreAWholeRemainderOfAWeekAndHeaderNumbersCanBeCleared() {
        for (day in 0..6) assertTrue(validHeaderValue("gestationalDays", day.toString()))
        for (value in listOf("7", "-1", "2.5", "12")) assertFalse(validHeaderValue("gestationalDays", value))
        assertTrue(validHeaderValue("gestationalDays", ""))
        assertTrue(validHeaderValue("gestationalWeeks", "39"))
        assertTrue(validHeaderValue("rupturedHours", "1.5"))
        assertFalse(validHeaderValue("rupturedHours", "NaN"))
        assertFalse(validHeaderValue("rupturedHours", "-1"))
    }
}
