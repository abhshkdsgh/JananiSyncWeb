package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlinx.serialization.json.Json
import kotlin.test.*

class WomanArchiveTest {
    @Test fun archivePermissionsHaveTheSameBoundaryForEveryRole() {
        for (level in listOf(-1, 0, 10, 59, 100)) assertFalse(canArchiveWomen(level))
        for (level in listOf(60, 61, 99)) assertTrue(canArchiveWomen(level))
    }

    @Test fun legacyWomenDefaultToActiveAndServerArchiveStateIsNotEmbeddedInEncryptedRosterPayloads() {
        val woman = Woman(123, 456, "Test", 20000101, 0, Ward("ward-a", "A", "", ""), 0, "", "", "", "admission-a")
        val original = Json.encodeToString(Woman.serializer(), woman)
        val legacy = Json.decodeFromString(Woman.serializer(), original)
        assertFalse(legacy.isArchived)
        assertEquals(0, legacy.archiveRevision)
        val archived = woman.copy(firestoreDocumentId = "opaque-doc", isArchived = true, archiveRevision = 2)
        assertEquals(original, Json.encodeToString(Woman.serializer(), archived))
        assertEquals(woman.admissionId, archived.admissionId)
        assertEquals(woman.admittedWard, archived.admittedWard)
    }
}
