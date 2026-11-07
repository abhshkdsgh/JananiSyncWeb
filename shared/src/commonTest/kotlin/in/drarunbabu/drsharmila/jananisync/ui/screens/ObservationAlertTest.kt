package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ObservationAlertTest {
    @Test fun urineGradesAlertAtAndAboveThreshold() {
        for ((field, prefix) in listOf("urineProtein" to "P", "urineAcetone" to "A")) {
            for (grade in 2..4) assertTrue(isAlertValue(field, prefix + "+".repeat(grade)))
            for (value in listOf("", "$prefix-", "$prefix Trace", "$prefix+", "$prefix++invalid")) {
                assertFalse(isAlertValue(field, value), "$field: $value")
            }
            assertTrue(isAlertValue(field, " ${prefix.lowercase()}+++ "))
        }
        assertFalse(isAlertValue("urineProtein", "A+++"))
        assertFalse(isAlertValue("urineAcetone", "P+++"))
    }

    @Test fun otherGradedAlertsIncludeHigherLevels() {
        for (field in listOf("caput", "moulding")) {
            assertFalse(isAlertValue(field, "++"))
            assertTrue(isAlertValue(field, "+++"))
            assertTrue(isAlertValue(field, "++++"))
        }
        assertFalse(isAlertValue("amnioticFluid", "M++"))
        assertTrue(isAlertValue("amnioticFluid", "M+++"))
        assertTrue(isAlertValue("amnioticFluid", "M++++"))
        assertTrue(isAlertValue("amnioticFluid", "B"))
    }
}
