package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Decodes one immutable snapshot; only the newest request may publish its result. */
internal class LabourFeedDecoder(
    private val decrypt: suspend (String, String, String) -> String = SyntheticCrypto::decrypt
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var revision = 0

    private val plaintextCache = mutableMapOf<String, String>()
    private var cachedKey = ""
    fun invalidate() { revision++ }
    fun clear() { invalidate(); plaintextCache.clear(); cachedKey = "" }
    private suspend fun plaintext(ciphertext: String, iv: String, key: String): String {
        val id = "$key:$iv:$ciphertext"
        return plaintextCache[id] ?: decrypt(ciphertext, iv, key).also { plaintextCache[id] = it }
    }

    suspend fun decode(
        localKey: String,
        rawRows: Array<String>,
        rawCorrections: Array<String>,
        rawColumnControls: Array<String>,
        pending: Boolean
    ): LabourCareFeed? {
        val token = ++revision
        if (cachedKey != localKey) { plaintextCache.clear(); cachedKey = localKey }
        val authorizedKeys = mutableMapOf<LabourCareObservation, Set<String>>()
        val decrypted = mutableListOf<LabourCareObservation>()
        val corrections = mutableListOf<ObservationCorrection>()
        val columnControls = mutableListOf<LabourColumnControl>()
        var decryptError: String? = null
        if (localKey.isNotBlank()) {
            rawRows.forEach { raw ->
                if (token != revision) return null
                runCatching {
                    val envelope = JSON.parse<dynamic>(raw)
                    val ciphertext = envelope.ciphertext as? String
                        ?: error("Missing observation ciphertext")
                    val iv = envelope.iv as? String ?: error("Missing observation IV")
                    val plaintext = try {
                        plaintext(ciphertext, iv, OrganizationKeyRing.key(localKey, (envelope.keyVersion as? Number)?.toInt() ?: 1))
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        decryptError = OBSERVATION_DECRYPT_ERROR
                        return@forEach
                    }
                    val schema = (envelope.schemaVersion as? Number)?.toInt() ?: 1
                    val delta = if (schema == 2) json.decodeFromString<ClinicalCellEvent>(plaintext) else null
                    val observation = delta?.observation() ?: json.decodeFromString<LabourCareObservation>(plaintext)
                    val key = envelope.cellKey as? String ?: observation.cellKey
                    val declared = if (jsTypeOf(envelope.cellKeys) != "undefined" && envelope.cellKeys != null)
                        (envelope.cellKeys as Array<String>).toSet() else null
                    val keys = declared ?: if (key == "partographMark") observation.partographMarkCells.keys else
                        setOf(key) + if (key == "initials") listOfNotNull(
                            "fullDilationConfirmed".takeIf { observation.fullDilationConfirmed },
                            "birthConfirmed".takeIf { observation.birthConfirmed }) else emptyList()
                    if (delta != null) require(declared != null && delta.values.keys == declared) { "Event cells disagree with its envelope." }
                    if (delta != null) {
                        val clinical = delta.values.filterKeys { it !in setOf("initials", "fullDilationConfirmed", "birthConfirmed") }
                        require(clinicalEditError(clinical, header = (envelope.columnId as? String).orEmpty().startsWith("header-")) == null) { "Invalid clinical values." }
                    }
                    val normalized = observation.copy(
                        womanDocumentId = if (schema == 1) observation.womanDocumentId.ifBlank { (envelope.womanDocumentId as? String).orEmpty() } else observation.womanDocumentId,
                        isGuideHeader = (envelope.columnId as? String)?.startsWith("header-") ?: observation.isGuideHeader,
                        authorId = envelope.createdBy as? String ?: observation.authorId,
                        createdAtMillis = (envelope.createdAtMillis as? Number)?.toLong() ?: observation.createdAtMillis,
                        firestoreDocumentId = envelope.documentId as? String ?: "",
                        authorAccessLevel = (envelope.accessLevel as? Number)?.toInt() ?: observation.authorAccessLevel,
                        columnId = envelope.columnId as? String ?: observation.columnId,
                        cellKey = envelope.cellKey as? String ?: observation.cellKey,
                        revision = (envelope.revision as? Number)?.toInt() ?: observation.revision
                    )
                    authorizedKeys[normalized] = keys
                    normalized
                }.onSuccess(decrypted::add).onFailure {
                    if (it is CancellationException) throw it
                    if (decryptError == null) decryptError = OBSERVATION_FORMAT_ERROR
                }
            }
            rawCorrections.forEach { raw ->
                if (token != revision) return null
                runCatching {
                    val envelope = JSON.parse<dynamic>(raw)
                    val ciphertext = envelope.ciphertext as? String ?: error("Missing correction ciphertext")
                    val iv = envelope.iv as? String ?: error("Missing correction IV")
                    val plaintext = try {
                        plaintext(ciphertext, iv, OrganizationKeyRing.key(localKey, (envelope.keyVersion as? Number)?.toInt() ?: 1))
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        if (decryptError == null) decryptError = CORRECTION_DECRYPT_ERROR
                        return@forEach
                    }
                    val correction = json.decodeFromString<ObservationCorrection>(plaintext)
                    correction.copy(
                        authorId = envelope.createdBy as? String ?: correction.authorId,
                        createdAtMillis = (envelope.createdAtMillis as? Number)?.toLong() ?: correction.createdAtMillis,
                        originalDocumentId = envelope.correctedObservationId as? String ?: correction.originalDocumentId
                    )
                }.onSuccess(corrections::add).onFailure {
                    if (it is CancellationException) throw it
                    if (decryptError == null) decryptError = CORRECTION_FORMAT_ERROR
                }
            }
            rawColumnControls.forEach { raw ->
                runCatching { json.decodeFromString<LabourColumnControl>(raw) }
                    .onSuccess(columnControls::add)
                    .onFailure { if (decryptError == null) decryptError = COLUMN_FORMAT_ERROR }
            }
        }
        if (token != revision) return null
        val controlsById = columnControls.associateBy { it.columnId }
        val grouped = decrypted.filter { it.columnId.isNotBlank() }.groupBy { it.columnId }
        val currentColumns = mutableListOf<LabourCareObservation>()
        val safeEvents = mutableListOf<LabourCareObservation>()
        grouped.forEach { (id, unsorted) ->
            val control = controlsById[id]
            var current: LabourCareObservation? = null
            for (event in unsorted.sortedBy { it.revision }) {
                val keys = authorizedKeys[event].orEmpty()
                if (control?.timestampMillis != null && (chartMillis(event.timestamp) != control.timestampMillis ||
                    event.sessionId != control.sessionId || event.womanDocumentId != control.womanDocumentId ||
                    (control.chartColumnIndex != null && event.chartColumnIndex != control.chartColumnIndex) ||
                    (!event.isGuideHeader && control.chartStartMillis != null && control.chartStartMillis > 0 && chartMillis(event.chartStartTimestamp) != control.chartStartMillis))) {
                    decryptError = OBSERVATION_FORMAT_ERROR
                    continue
                }
                // A later event cannot rename, retimestamp or move an existing column.
                if (current != null && (event.sessionId != current.sessionId || chartMillis(event.timestamp) != chartMillis(current.timestamp))) {
                    decryptError = OBSERVATION_FORMAT_ERROR
                    continue
                }
                val safe = applyCellEvent(null, event, keys).copy(
                    womanDocumentId = control?.womanDocumentId?.takeIf { it.isNotBlank() } ?: event.womanDocumentId)
                current = applyCellEvent(current, safe, keys)
                safeEvents += safe
            }
            current?.let(currentColumns::add)
        }
        val legacyObservations = decrypted.filter { it.columnId.isBlank() }
        val visibleObservations = (legacyObservations + currentColumns).sortedBy { chartMillis(it.timestamp) ?: Long.MAX_VALUE }
        val eventsByColumn = safeEvents.groupBy { it.columnId }
        if (columnControls.any { it.columnId !in eventsByColumn }) {
            decryptError = INCOMPLETE_RECORDS_ERROR
        }
        return LabourCareFeed(
            observations = visibleObservations,
            cellEvents = (safeEvents + legacyObservations).sortedWith(compareBy({ it.columnId }, { it.revision })),
            corrections = corrections.sortedBy { it.createdAtMillis },
            columnControls = columnControls.map { control ->
                val levels = mutableMapOf<String, Int>()
                eventsByColumn[control.columnId].orEmpty().forEach { event ->
                    val keys = if (event.cellKey == "partographMark") event.partographMarkCells.keys else setOf(event.cellKey)
                    keys.filter { it.isNotBlank() && it != "initials" }.forEach { levels[it] = event.authorAccessLevel }
                }
                val confirmation = eventsByColumn[control.columnId].orEmpty().firstOrNull {
                    it.cellKey == "initials" &&
                        (control.confirmationEventId.isBlank() || it.firestoreDocumentId == control.confirmationEventId) &&
                        (control.confirmedBy.isBlank() || it.authorId == control.confirmedBy)
                }
                control.copy(legacyCellAccessLevels = levels, confirmerAccessLevel = confirmation?.authorAccessLevel ?: 0)
            },
            isLoading = false,
            hasPendingWrites = pending,
            message = decryptError
                ?: if (rawRows.isNotEmpty() && localKey.isBlank()) {
                    UNLOCK_MESSAGE
                } else null
        )
    }

    companion object {
        const val OBSERVATION_DECRYPT_ERROR = "Could not decrypt all observations with this organisation's key. Confirm that this account has the current key grant."
        const val OBSERVATION_FORMAT_ERROR = "Could not read one or more observations: invalid stored data or an unsupported observation format."
        const val CORRECTION_DECRYPT_ERROR = "Could not decrypt one or more corrections with this organisation's key."
        const val CORRECTION_FORMAT_ERROR = "Could not read one or more corrections: invalid stored data or an unsupported correction format."
        const val INCOMPLETE_RECORDS_ERROR = "Clinical records are incomplete or their current-cell projections have not been migrated. Contact an administrator before recording."
        const val COLUMN_FORMAT_ERROR = "Could not read column edit status."
        const val UNLOCK_MESSAGE = "Unlock this organisation's encryption key with your recovery phrase to decrypt observations."
        private val messages = setOf(OBSERVATION_DECRYPT_ERROR, OBSERVATION_FORMAT_ERROR,
            CORRECTION_DECRYPT_ERROR, CORRECTION_FORMAT_ERROR, COLUMN_FORMAT_ERROR, INCOMPLETE_RECORDS_ERROR, UNLOCK_MESSAGE)

        fun retainedMessage(previous: String?): String? = previous?.takeUnless { it in messages }
    }
}
