package `in`.drarunbabu.drsharmila.jananisync.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class RosterDecodeTest {
    @Test fun unreadableRecordIsReportedWithoutDiscardingOtherAdmissions() = runTest {
        val failures = mutableListOf<String>()
        val rows = arrayOf("""{"documentId":"healthy"}""", """{"documentId":"broken"}""", """{"documentId":"other"}""")
        val result = decodeRosterRecords("women", rows, failures) { raw ->
            val id = JSON.parse<dynamic>(raw).documentId as String
            if (id == "broken") error("Bad ciphertext")
            id
        }
        assertEquals(listOf("healthy", "other"), result)
        assertEquals(listOf("women/broken"), failures)
    }
    @Test fun cancellationDoesNotBecomeADataCorruptionWarning() = runTest {
        val failures = mutableListOf<String>()
        assertFailsWith<CancellationException> {
            decodeRosterRecords("women", arrayOf("{}"), failures) { throw CancellationException("Switched organisation") }
        }
        assertTrue(failures.isEmpty())
    }
}
