package `in`.drarunbabu.drsharmila.jananisync.data

import kotlin.test.*

class ClinicalValidationTest {
    @Test fun rejectsInvalidClinicalValuesAndAllowsExplicitErasure() {
        for ((field, value) in listOf("fetalHeartRate" to "abc", "pulse" to "9999", "temperatureHalf2" to "NaN",
            "cervicalDilation" to "Infinity", "contractionFrequencyHalf1" to "2.5", "amnioticFluid" to "unexpected",
            "pulseHalf1" to "80")) assertNotNull(clinicalValueError(field, value), "$field accepted $value")
        for ((field, value) in listOf("fetalHeartRateHalf1" to "140", "temperatureHalf2" to "37.5", "contractionDurationHalf2" to "20-40",
            "pulse" to "", "urineVolume" to "0")) assertNull(clinicalValueError(field, value), "$field rejected $value")
    }
    @Test fun validatesEachHalfOfBloodPressureIndependently() {
        assertNotNull(clinicalEditError(mapOf("systolic" to "80", "diastolic" to "90")))
        assertNull(clinicalEditError(mapOf("systolic" to "120", "diastolic" to "80", "systolicHalf2" to "100", "diastolicHalf2" to "70")))
        assertNotNull(clinicalEditError(mapOf("systolicHalf2" to "80", "diastolicHalf2" to "80")))
        assertNull(clinicalEditError(mapOf("systolic" to "", "diastolic" to "80")))
    }
}
