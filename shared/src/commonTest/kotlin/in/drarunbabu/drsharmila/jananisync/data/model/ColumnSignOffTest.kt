package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlin.test.*

class ColumnSignOffTest {
    private fun column(confirmed: Boolean, signer: Int = 0) = LabourColumnControl(
        "column", 80, 80, listOf("editor"), 3, confirmed,
        confirmedBy = if (confirmed) "signer" else "", confirmerAccessLevel = signer)

    @Test fun unsignedColumnCanBeSignedByClinicalStaff() {
        assertTrue(column(false).canSignOff(10))
        assertTrue(column(false).canSignOff(99))
        assertFalse(column(false).canSignOff(9))
        assertFalse(column(false).canSignOff(100))
    }

    @Test fun onlyStrictlyHigherAccessCanSupersedeCurrentSigner() {
        val signed = column(true, 40)
        assertFalse(signed.canSignOff(10))
        assertFalse(signed.canSignOff(40))
        assertTrue(signed.canSignOff(41))
        assertTrue(signed.canSignOff(99))
        assertFalse(column(true, 99).canSignOff(99))
    }

    @Test fun missingHistoricalSignerAccessCannotBeTreatedAsZero() {
        assertFalse(column(true).canSignOff(99))
    }
}
