package `in`.drarunbabu.drsharmila.jananisync.data

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import `in`.drarunbabu.drsharmila.jananisync.data.model.cellEditHistory

class LabourFeedDecoderTest {
    private val row = """{"ciphertext":"encrypted","iv":"iv","documentId":"doc"}"""
    private val observation = """{"id":"id","sessionId":"session","patientLabel":"Test","parity":0,"timestamp":"2026-10-24T08:00:00Z","maternalVitals":{"timestamp":"","systolicBloodPressure":120,"diastolicBloodPressure":80,"pulseBpm":80,"temperatureCelsius":37.0},"fetalWellbeing":{"timestamp":"","fetalHeartRateBpm":140,"amnioticFluidStatus":"clear"},"laborProgress":{"timestamp":"","cervicalDilationCm":4.0,"contractionFrequencyPer10Minutes":3,"contractionDurationSeconds":40}}"""

    private suspend fun LabourFeedDecoder.read(
        rows: Array<String> = arrayOf(row),
        key: String = "key",
        corrections: Array<String> = emptyArray(),
        columns: Array<String> = emptyArray()
    ) = decode(key, rows, corrections, columns, false)

    @Test fun higherSignOffBecomesCurrentAndStageEventsPreserveSignerHistory() = runTest {
        val first = observation.dropLast(1) + ",\"initials\":\"First\"}"
        val senior = observation.dropLast(1) + ",\"initials\":\"Senior\"}"
        val birth = senior.dropLast(1) + ",\"birthConfirmed\":true}"
        val decoder = LabourFeedDecoder { ciphertext, _, _ ->
            when (ciphertext) { "first" -> first; "birth" -> birth; else -> senior }
        }
        val result = decoder.read(rows = arrayOf(
            """{"ciphertext":"first","iv":"iv","documentId":"first-signoff","createdBy":"first","columnId":"column","cellKey":"initials","revision":2,"accessLevel":20}""",
            """{"ciphertext":"senior","iv":"iv","documentId":"senior-signoff","createdBy":"senior","columnId":"column","cellKey":"initials","revision":3,"accessLevel":40}""",
            """{"ciphertext":"birth","iv":"iv","documentId":"birth","createdBy":"junior","columnId":"column","cellKey":"birthConfirmed","revision":4,"accessLevel":10}"""
        ), columns = arrayOf(
            """{"columnId":"column","lastEditorAccessLevel":10,"highestEditorAccessLevel":20,"highestEditorUids":["first"],"revision":4,"confirmed":true,"confirmedBy":"senior","confirmationEventId":"senior-signoff"}"""
        ))!!
        assertEquals(40, result.columnControls.single().confirmerAccessLevel)
        assertFalse(result.columnControls.single().canSignOff(40))
        assertTrue(result.columnControls.single().canSignOff(41))
        assertEquals("Senior", result.observations.single().initials)
        assertTrue(result.observations.single().birthConfirmed)
        assertEquals(listOf("first-signoff", "senior-signoff"),
            result.cellEvents.cellEditHistory("session", "column", "initials").map { it.firestoreDocumentId })
    }

    @Test fun realEncryptedObservationAcceptsItsKeyAndRejectsAnotherKey() = runTest {
        val key = SyntheticCrypto.generateKey()
        val (ciphertext, iv) = SyntheticCrypto.encrypt(observation, key)
        val encryptedRow = """{"ciphertext":"$ciphertext","iv":"$iv"}"""
        val decoder = LabourFeedDecoder()
        assertNull(decoder.read(arrayOf(encryptedRow), key)!!.message)
        assertEquals(LabourFeedDecoder.OBSERVATION_DECRYPT_ERROR,
            decoder.read(arrayOf(encryptedRow), SyntheticCrypto.generateKey())!!.message)
        assertNull(decoder.read(arrayOf(encryptedRow), key)!!.message)
    }

    @Test fun legacyHistoryProtectsOnlyEditedCellsIncludingGraphParameters() = runTest {
        val graph = observation.dropLast(1) + ",\"partographMarkCells\":{\"systolic\":\"120\",\"diastolic\":\"80\"}}"
        val decoder = LabourFeedDecoder { ciphertext, _, _ -> if (ciphertext == "graph") graph else observation }
        val result = decoder.read(rows = arrayOf(
            """{"ciphertext":"graph","iv":"iv","columnId":"column","cellKey":"partographMark","revision":1,"accessLevel":80}""",
            """{"ciphertext":"cell","iv":"iv","columnId":"column","cellKey":"pulse","revision":2,"accessLevel":10}"""
        ), columns = arrayOf(
            """{"columnId":"column","lastEditorAccessLevel":10,"highestEditorAccessLevel":80,"highestEditorUids":["senior"],"revision":2,"confirmed":false}"""
        ))!!
        val control = result.columnControls.single()
        assertFalse(control.canEditCell("systolic", 10))
        assertFalse(control.canEditCell("diastolic", 10))
        assertTrue(control.canEditCell("pulse", 10))
        assertTrue(control.canEditCell("temperature", 10))
    }

    @Test fun confirmedCellCorrectionBecomesCurrentValueAndStaysInCellHistory() = runTest {
        val corrected = observation.replace("\"pulseBpm\":80", "\"pulseBpm\":90").dropLast(1) +
            ",\"partographMarkCells\":{\"pulse\":\"90\"},\"correctionReason\":\"Transcription error\"}"
        val decoder = LabourFeedDecoder { ciphertext, _, _ -> if (ciphertext == "corrected") corrected else observation }
        val result = decoder.read(rows = arrayOf(
            """{"ciphertext":"original","iv":"iv","columnId":"column","cellKey":"pulse","revision":1,"accessLevel":10}""",
            """{"ciphertext":"original","iv":"iv","columnId":"column","cellKey":"initials","revision":2,"accessLevel":10}""",
            """{"ciphertext":"corrected","iv":"iv","recordType":"cellCorrection","columnId":"column","cellKey":"partographMark","revision":3,"accessLevel":20}"""
        ), columns = arrayOf(
            """{"columnId":"column","lastEditorAccessLevel":20,"highestEditorAccessLevel":20,"highestEditorUids":["senior"],"revision":3,"confirmed":true,"cellAccessLevels":{"pulse":20}}"""
        ))!!
        assertEquals(90, result.observations.single().maternalVitals.pulseBpm)
        assertTrue(result.columnControls.single().confirmed)
        assertEquals(10, result.columnControls.single().confirmerAccessLevel)
        assertTrue(result.columnControls.single().canCorrect(14))
        assertTrue(result.columnControls.single().canCorrect(15))
        val history = result.cellEvents.cellEditHistory("session", "column", "pulse")
        assertEquals(listOf(1, 3), history.map { it.revision })
        assertEquals(80, history.first().maternalVitals.pulseBpm)
        assertEquals("Transcription error", history.last().correctionReason)
        assertTrue(result.cellEvents.cellEditHistory("session", "column", "systolic").isEmpty())
    }

    @Test fun lowAccessSnapshotCannotReplaceUndeclaredProtectedValuesOrStageFlags() = runTest {
        val hostile = observation.replace("\"fetalHeartRateBpm\":140", "\"fetalHeartRateBpm\":199")
            .replace("\"pulseBpm\":80", "\"pulseBpm\":90").dropLast(1) +
            ",\"birthConfirmed\":true,\"fullDilationConfirmed\":true,\"initials\":\"Forged\"}"
        val decoder = LabourFeedDecoder { cipher, _, _ -> if (cipher == "hostile") hostile else observation }
        val result = decoder.read(rows = arrayOf(
            """{"ciphertext":"original","iv":"iv","documentId":"first","columnId":"column","cellKey":"fetalHeartRate","cellKeys":["fetalHeartRate"],"revision":1,"accessLevel":80}""",
            """{"ciphertext":"hostile","iv":"iv","documentId":"second","columnId":"column","cellKey":"pulse","cellKeys":["pulse"],"revision":2,"accessLevel":10}"""
        ))!!
        val current = result.observations.single()
        assertEquals(140, current.fetalWellbeing.fetalHeartRateBpm)
        assertEquals(90, current.maternalVitals.pulseBpm)
        assertFalse(current.birthConfirmed)
        assertFalse(current.fullDilationConfirmed)
        assertEquals("", current.initials)
    }
    @Test fun repeatedMetadataSnapshotsReuseSuccessfulDecryption() = runTest {
        var calls = 0
        val decoder = LabourFeedDecoder { _, _, _ -> calls++; observation }
        decoder.read(); decoder.read()
        assertEquals(1, calls)
        decoder.clear(); decoder.read()
        assertEquals(2, calls)
        decoder.read(key = ""); decoder.read()
        assertEquals(3, calls)
    }
    @Test fun schemaTwoPayloadCannotSmuggleUndeclaredCells() = runTest {
        val delta = """{"sessionId":"session","womanDocumentId":"woman","timestamp":"2026-10-24T08:00:00Z","chartStartTimestamp":"2026-10-24T08:00:00Z","chartColumnIndex":0,"values":{"pulse":"90","fetalHeartRate":"199"}}"""
        val result = LabourFeedDecoder { _, _, _ -> delta }.read(rows = arrayOf(
            """{"ciphertext":"cipher","iv":"iv","schemaVersion":2,"columnId":"column","cellKey":"pulse","cellKeys":["pulse"],"revision":1}"""
        ))!!
        assertEquals(LabourFeedDecoder.OBSERVATION_FORMAT_ERROR, result.message)
        assertTrue(result.observations.isEmpty())
    }

    @Test fun migratedLegacyPayloadUsesItsTrustedEnvelopeBinding() = runTest {
        val result = LabourFeedDecoder { _, _, _ -> observation }.read(rows = arrayOf(
            """{"ciphertext":"cipher","iv":"iv","womanDocumentId":"woman","columnId":"column","cellKey":"pulse","cellKeys":["pulse"],"revision":1}"""
        ), columns = arrayOf(
            """{"columnId":"column","lastEditorAccessLevel":10,"highestEditorAccessLevel":10,"highestEditorUids":["staff"],"revision":1,"confirmed":false,"womanDocumentId":"woman","sessionId":"session","timestampMillis":1792828800000,"chartColumnIndex":-1,"chartStartMillis":0}"""
        ))!!
        assertNull(result.message)
        assertEquals("woman", result.observations.single().womanDocumentId)
    }

    @Test fun validObservationHasNoWarning() = runTest {
        val result = LabourFeedDecoder { _, _, _ -> observation }.read()!!
        assertNull(result.message)
        assertEquals("doc", result.observations.single().firestoreDocumentId)
    }

    @Test fun oneUndecryptableObservationWarnsButPreservesReadableRecords() = runTest {
        var calls = 0
        val result = LabourFeedDecoder { _, _, _ ->
            if (++calls == 1) error("Authentication failed")
            observation
        }.read(arrayOf(row, row))!!
        assertEquals(LabourFeedDecoder.OBSERVATION_DECRYPT_ERROR, result.message)
        assertEquals(1, result.observations.size)
    }

    @Test fun missingKeyRequestsUnlockWithoutAttemptingDecryption() = runTest {
        val decoder = LabourFeedDecoder { _, _, _ -> error("Must not decrypt") }
        assertEquals(LabourFeedDecoder.UNLOCK_MESSAGE, decoder.read(key = "")!!.message)
        assertNull(decoder.read(rows = emptyArray(), key = "")!!.message)
        assertNull(decoder.read(rows = emptyArray())!!.message)
    }

    @Test fun malformedEnvelopeAndUnsupportedPlaintextAreFormatErrors() = runTest {
        val decoder = LabourFeedDecoder { _, _, _ -> "{}" }
        for (invalid in arrayOf("not json", "{}", """{"ciphertext":"encrypted"}""", row)) {
            assertEquals(LabourFeedDecoder.OBSERVATION_FORMAT_ERROR, decoder.read(arrayOf(invalid))!!.message)
        }
    }

    @Test fun correctionAndColumnFailuresDoNotBlameObservationKey() = runTest {
        val decoder = LabourFeedDecoder { _, _, _ -> error("Authentication failed") }
        assertEquals(LabourFeedDecoder.CORRECTION_DECRYPT_ERROR,
            decoder.read(rows = emptyArray(), corrections = arrayOf(row))!!.message)
        assertEquals(LabourFeedDecoder.CORRECTION_FORMAT_ERROR,
            decoder.read(rows = emptyArray(), corrections = arrayOf("{}"))!!.message)
        assertEquals(LabourFeedDecoder.COLUMN_FORMAT_ERROR,
            decoder.read(rows = emptyArray(), columns = arrayOf("{}"))!!.message)
        assertEquals(LabourFeedDecoder.OBSERVATION_DECRYPT_ERROR,
            decoder.read(corrections = arrayOf(row), columns = arrayOf("{}"))!!.message)
    }

    @Test fun successfulRetryClearsPreviousDecodeWarningAndKeepsUnrelatedErrors() = runTest {
        var fails = true
        val decoder = LabourFeedDecoder { _, _, _ -> if (fails) error("Wrong key") else observation }
        val previous = decoder.read()!!.message
        assertEquals(LabourFeedDecoder.OBSERVATION_DECRYPT_ERROR, previous)
        fails = false
        val next = decoder.read()!!
        assertNull(next.message ?: LabourFeedDecoder.retainedMessage(previous))
        assertNull(LabourFeedDecoder.retainedMessage(LabourFeedDecoder.UNLOCK_MESSAGE))
        assertEquals("Permission denied", LabourFeedDecoder.retainedMessage("Permission denied"))
    }

    @Test fun newerSnapshotDiscardsOlderFailureEvenWithTheSameKey() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val decoder = LabourFeedDecoder { _, _, _ ->
            started.complete(Unit)
            finish.await()
            error("Old record failed")
        }
        val older = async { decoder.read() }
        started.await()
        assertNull(decoder.read(rows = emptyArray())!!.message)
        finish.complete(Unit)
        assertNull(older.await())
    }

    @Test fun organizationSwitchOrStopDiscardsInflightResult() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val decoder = LabourFeedDecoder { _, _, _ ->
            started.complete(Unit)
            finish.await()
            observation
        }
        val older = async { decoder.read() }
        started.await()
        decoder.invalidate()
        finish.complete(Unit)
        assertNull(older.await())
    }

    @Test fun cancellationIsNeverConvertedIntoAKeyWarning() = runTest {
        assertFailsWith<CancellationException> {
            LabourFeedDecoder { _, _, _ -> throw CancellationException("Stopped") }.read()
        }
    }
}
