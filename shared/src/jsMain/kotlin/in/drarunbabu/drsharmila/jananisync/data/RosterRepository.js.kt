@file:Suppress("NON_EXPORTABLE_TYPE")

package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.FirebaseService
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebase
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebaseUnit
import `in`.drarunbabu.drsharmila.jananisync.data.model.Ward
import `in`.drarunbabu.drsharmila.jananisync.data.model.Woman
import `in`.drarunbabu.drsharmila.jananisync.data.model.WomanArchiveEvent
import `in`.drarunbabu.drsharmila.jananisync.data.model.canArchiveWomen
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RosterEnvelope(val documentId: String, val ciphertext: String, val iv: String,
    val archived: Boolean = false, val archiveRevision: Int = 0, val keyVersion: Int = 1)

@Serializable
private data class ArchiveEnvelope(val documentId: String, val womanDocumentId: String, val archived: Boolean,
    val revision: Int, val createdBy: String, val accessLevel: Int, val createdAtMillis: Long = 0,
    val ciphertext: String, val iv: String, val keyVersion: Int = 1)

internal suspend fun <T> decodeRosterRecords(kind: String, source: Array<String>, failed: MutableList<String>, decode: suspend (String) -> T): List<T> = source.mapNotNull { raw ->
            currentCoroutineContext().ensureActive()
            try { decode(raw) } catch (error: Throwable) {
                if (error is CancellationException) throw error
                val id = runCatching { JSON.parse<dynamic>(raw).documentId as? String }.getOrNull() ?: "unknown"
                failed += "$kind/$id"
                null
            }
        }

private class FirebaseRosterRepository : RosterRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableState = MutableStateFlow(RosterState())
    override val state = mutableState.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stopListening: (() -> Unit)? = null
    private var generation = 0
    private var decryptionGeneration = 0
    private var decodeJob: Job? = null
    private val plaintextCache = mutableMapOf<String, String>()
    private var cachedKey = ""
    private fun requestDecode() {
        decodeJob?.cancel()
        decodeJob = scope.launch { decryptRoster() }
    }
    private var orgId: String? = null
    private var includeWomen = true
    private var hasRosterSnapshot = false
    private var rawWards: Array<String> = emptyArray()
    private var rawWomen: Array<String> = emptyArray()

    init {
        scope.launch { OrganizationKeyVault.keys.collect { requestDecode() } }
    }

    override fun observe(orgId: String, includeWomen: Boolean) {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation code." }
        if (this.orgId == orgId && this.includeWomen == includeWomen && stopListening != null) return
        stopListening?.invoke()
        hasRosterSnapshot = false
        this.orgId = orgId
        this.includeWomen = includeWomen
        rawWards = emptyArray()
        rawWomen = emptyArray()
        val token = ++generation
        mutableState.value = RosterState(isLoading = true)
        FirebaseService.initialize()
        FirebaseService.listenToRoster(orgId, includeWomen, { wardRows, womanRows ->
            if (token != generation) return@listenToRoster
            hasRosterSnapshot = true
            rawWards = wardRows
            rawWomen = womanRows
            requestDecode()
        }, { error ->
            if (token == generation) mutableState.value = RosterState(isLoading = false, message = explainError(error))
        }).also { stopListening = it }
    }

    override suspend fun addWard(ward: Ward): String {
        val organization = orgId ?: error("Select an organisation first.")
        val key = OrganizationKeyVault.keys.value[organization].orEmpty()
        require(key.isNotBlank()) { "Unlock this organisation's encryption key before adding roster data." }
        val payload = json.encodeToString(ward.copy(wardId = ""))
        val (ciphertext, iv) = SyntheticCrypto.encrypt(payload, key)
        return FirebaseService.writeRosterEnvelope(organization, "wards", ciphertext, iv, OrganizationKeyRing.version(key)).awaitFirebase()
    }

    override suspend fun updateWard(ward: Ward) {
        val organization = orgId ?: error("Select an organisation first.")
        require(ward.wardId.isNotBlank()) { "Select a ward to configure." }
        val key = OrganizationKeyVault.keys.value[organization].orEmpty()
        require(key.isNotBlank()) { "Unlock this organisation's encryption key before editing wards." }
        val (ciphertext, iv) = SyntheticCrypto.encrypt(json.encodeToString(ward), key)
        FirebaseService.updateRosterEnvelope(organization, "wards", ward.wardId, ciphertext, iv, OrganizationKeyRing.version(key)).awaitFirebase()
    }

    override suspend fun addWoman(woman: Woman): Woman {
        val organization = orgId ?: error("Select an organisation first.")
        require(FirebaseService.currentUser != null) { "Your signed-in account is unavailable." }
        val key = OrganizationKeyVault.keys.value[organization].orEmpty()
        require(key.isNotBlank()) { "Unlock this organisation's encryption key before adding roster data." }
        val admission = woman.copy(admissionId = SyntheticCrypto.generateOpaqueId())
        require(admission.admissionId.matches(Regex("^[A-Za-z0-9_-]{22}$"))) { "Admission ID must be an opaque generated identifier." }
        val payload = json.encodeToString(admission)
        val (ciphertext, iv) = SyntheticCrypto.encrypt(payload, key)
        FirebaseService.writeRosterEnvelope(organization, "women", ciphertext, iv, OrganizationKeyRing.version(key)).awaitFirebase()
        return admission
    }

    override suspend fun setWomanArchived(woman: Woman, archived: Boolean) {
        val organization = orgId ?: error("Select an organisation first.")
        val author = FirebaseService.currentUser?.uid ?: error("Your signed-in account is unavailable.")
        require(woman.firestoreDocumentId.isNotBlank() && mutableState.value.women.any {
            it.firestoreDocumentId == woman.firestoreDocumentId && it.admissionId == woman.admissionId
        }) { "Reopen the current roster before changing archive status." }
        require(woman.isArchived != archived) { "This admission already has the requested archive status." }
        val member = FirebaseService.getDocument("organizations/$organization/members/$author").awaitFirebase()
            ?: error("Your organisation membership is unavailable.")
        val accessLevel = member.accessLevel as? Int ?: 0
        require(canArchiveWomen(accessLevel) && member.status == "active") { "Access level 60 or higher is required to archive or restore women." }
        val key = OrganizationKeyVault.keys.value[organization].orEmpty()
        require(key.isNotBlank()) { "Unlock patient records before changing archive status." }
        val actorName = (member.name as? String).orEmpty().ifBlank { author }
        val (ciphertext, iv) = SyntheticCrypto.encrypt(actorName, key)
        require(orgId == organization && FirebaseService.currentUser?.uid == author) { "Your session changed. Reopen the roster." }
        FirebaseService.setRosterWomanArchived(organization, woman.firestoreDocumentId, archived,
            woman.archiveRevision, accessLevel, ciphertext, iv, OrganizationKeyRing.version(key)).awaitFirebaseUnit()
    }

    override suspend fun loadArchiveHistory(womanDocumentId: String, beforeRevision: Int?): ArchiveHistoryPage {
        val organization = orgId ?: error("Select an organisation first.")
        val token = generation
        val key = OrganizationKeyVault.keys.value[organization].orEmpty()
        require(key.isNotBlank()) { "Unlock the organisation key to read history." }
        val rows = FirebaseService.getRosterArchiveHistory(organization, womanDocumentId, beforeRevision).awaitFirebase()
        val failures = mutableListOf<String>()
        val events = decodeRosterRecords("rosterArchiveEvents", rows, failures) { raw ->
            val envelope = json.decodeFromString<ArchiveEnvelope>(raw)
            require(envelope.womanDocumentId == womanDocumentId)
            WomanArchiveEvent(envelope.documentId, womanDocumentId, envelope.archived, envelope.revision,
                envelope.createdBy, SyntheticCrypto.decrypt(envelope.ciphertext, envelope.iv, OrganizationKeyRing.key(key, envelope.keyVersion)),
                envelope.accessLevel, envelope.createdAtMillis)
        }
        check(token == generation && organization == orgId && key == OrganizationKeyVault.keys.value[organization]) { "The organisation session changed. Reopen history." }
        val lastRevision = rows.lastOrNull()?.let { json.decodeFromString<ArchiveEnvelope>(it).revision }
        return ArchiveHistoryPage(events, lastRevision?.takeIf { rows.size == 50 }, failures)
    }

    private suspend fun decryptRoster() {
        val decryptionToken = ++decryptionGeneration
        val wardRows = rawWards
        val womanRows = rawWomen
        val organization = orgId ?: return
        val key = OrganizationKeyVault.keys.value[organization].orEmpty()
        if (cachedKey != key) { plaintextCache.clear(); cachedKey = key }
        if (key.isBlank()) {
            mutableState.value = RosterState(isLoading = false, message = "Unlock this organisation's encryption key to view its roster.",
                wardCount = if (hasRosterSnapshot) rawWards.size else null)
            return
        }
        if (!hasRosterSnapshot) {
            mutableState.value = RosterState(isLoading = true)
            return
        }
        val token = generation
        if (cachedKey != key) { plaintextCache.clear(); cachedKey = key }
        val failed = mutableListOf<String>()
        suspend fun decrypt(ciphertext: String, iv: String, version: Int): String {
            val cacheId = "$iv:$ciphertext"
            return plaintextCache[cacheId] ?: SyntheticCrypto.decrypt(ciphertext, iv, OrganizationKeyRing.key(key, version)).also { plaintextCache[cacheId] = it }
        }
        val wards = decodeRosterRecords("wards", wardRows, failed) { raw ->
            val envelope = json.decodeFromString<RosterEnvelope>(raw)
            json.decodeFromString<Ward>(decrypt(envelope.ciphertext, envelope.iv, envelope.keyVersion)).copy(wardId = envelope.documentId)
        }
        val wardById = wards.associateBy(Ward::wardId)
        val women = decodeRosterRecords("women", womanRows, failed) { raw ->
            val envelope = json.decodeFromString<RosterEnvelope>(raw)
            json.decodeFromString<Woman>(decrypt(envelope.ciphertext, envelope.iv, envelope.keyVersion)).copy(
                firestoreDocumentId = envelope.documentId, isArchived = envelope.archived, archiveRevision = envelope.archiveRevision)
        }.map { woman -> woman.copy(admittedWard = wardById[woman.admittedWard.wardId] ?: woman.admittedWard) }
        if (token == generation && decryptionToken == decryptionGeneration && key == OrganizationKeyVault.keys.value[organization]) mutableState.value = RosterState(
            wards, women, isLoading = false, wardCount = wardRows.size, unreadableRecordIds = failed)
    }

    private fun explainError(error: String): String = if (
        error.contains("permission-denied", true) || error.contains("missing or insufficient permissions", true)
    ) "Firestore denied this roster read. Confirm this account is an active admin, doctor, or nurse in the selected organisation and that the current rules are deployed."
    else error

    override fun stopObserving() {
        generation++
        decodeJob?.cancel()
        plaintextCache.clear()
        cachedKey = ""
        stopListening?.invoke()
        stopListening = null
        orgId = null
        rawWards = emptyArray()
        rawWomen = emptyArray()
        mutableState.value = RosterState(isLoading = false)
    }

    override fun close() { stopObserving(); scope.cancel() }
}

actual fun createRosterRepository(): RosterRepository = FirebaseRosterRepository()
