@file:Suppress("NON_EXPORTABLE_TYPE", "RedundantLambdaArrow")

package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.FirebaseService
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebase
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebaseUnit
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl
import `in`.drarunbabu.drsharmila.jananisync.ignoringFirebaseFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

private class FirebaseLaborDataRepository : LaborDataRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableFeed = MutableStateFlow(LabourCareFeed())
    override val feed: StateFlow<LabourCareFeed> = mutableFeed.asStateFlow()
    private val mutableKeyAvailable = MutableStateFlow(false)
    override val organizationKeyAvailable: StateFlow<Boolean> = mutableKeyAvailable.asStateFlow()
    private var orgId: String? = null
    private var userId: String? = null
    private var userAccessLevel: Int = 0
    private var key = ""
    private var stopListening: (() -> Unit)? = null
    private val decoder = LabourFeedDecoder()
    private var decodeJob: Job? = null
    private fun requestDecode() {
        decodeJob?.cancel()
        decodeJob = scope.launch { decryptRows() }
    }
    private val writeState = LabourWriteState()
    private val generation get() = writeState.generation
    private var rawRows: Array<String> = emptyArray()
    private var rawCorrections: Array<String> = emptyArray()
    private var rawColumnControls: Array<String> = emptyArray()
    private var pending = false
    private var selectedWomanId: String? = null
    override fun selectAdmission(womanDocumentId: String?) {
        if (selectedWomanId == womanDocumentId) return
        selectedWomanId = womanDocumentId
        val organization = orgId ?: return
        val author = userId ?: return
        stopListening?.invoke()
        stopListening = null
        observe(organization, author, userAccessLevel)
    }
    private val requestedMemberNames = mutableSetOf<String>()
    private val admissionBindingMutex = Mutex()
    private val completedAdmissionBindings = mutableMapOf<String, String>()

    init {
        scope.launch {
            OrganizationKeyVault.keys.collect { keys ->
                decoder.invalidate()
                key = orgId?.let(keys::get).orEmpty()
                mutableKeyAvailable.value = key.isNotBlank()
                decryptRows()
            }
        }
    }

    override fun observe(orgId: String, userId: String, accessLevel: Int) {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation code." }
        if (this.orgId == orgId && this.userId == userId && userAccessLevel == accessLevel && stopListening != null) return
        stopListening?.invoke()
        this.orgId = orgId
        this.userId = userId
        userAccessLevel = accessLevel
        key = OrganizationKeyVault.keys.value[orgId].orEmpty()
        mutableKeyAvailable.value = key.isNotBlank()
        rawRows = emptyArray()
        rawCorrections = emptyArray()
        rawColumnControls = emptyArray()
        requestedMemberNames.clear()
        completedAdmissionBindings.clear()
        decoder.invalidate()
        pending = false
        writeState.reset(observing = true)
        val token = generation
        mutableFeed.value = LabourCareFeed(isLoading = true)
        FirebaseService.initialize()
        FirebaseService.enableOfflinePersistence().ignoringFirebaseFailure().then {
            if (token == generation) {
                val stopObservations = FirebaseService.listenToSyntheticLcgObservations(orgId, selectedWomanId,
                    onData = { rows, hasPending ->
                        if (token == generation) {
                            decoder.invalidate()
                            rawRows = rows
                            pending = hasPending
                            writeState.received(LabourWriteState.Source.Observations)
                            requestDecode()
                        }
                    },
                    onError = { error -> if (token == generation) feedFailed(LabourWriteState.Source.Observations, error) }
                )
                val stopCorrections = FirebaseService.listenToLcgCorrections(orgId, selectedWomanId, { rows ->
                    if (token == generation) {
                        decoder.invalidate()
                        rawCorrections = rows
                        writeState.received(LabourWriteState.Source.Corrections)
                        requestDecode()
                    }
                }, { error -> if (token == generation) feedFailed(LabourWriteState.Source.Corrections, error) })
                val stopColumns = FirebaseService.listenToLcgColumnControls(orgId, selectedWomanId, { rows ->
                    if (token == generation) {
                        decoder.invalidate()
                        rawColumnControls = rows
                        writeState.received(LabourWriteState.Source.Columns)
                        requestDecode()
                    }
                }, { error -> if (token == generation) feedFailed(LabourWriteState.Source.Columns, error) })
                stopListening = { stopObservations(); stopCorrections(); stopColumns() }
            }
        }
    }

    override fun stopObserving() {
        decodeJob?.cancel()
        decoder.clear()
        writeState.reset(observing = false)
        requestedMemberNames.clear()
        completedAdmissionBindings.clear()
        stopListening?.invoke()
        stopListening = null
        orgId = null
        selectedWomanId = null
        userId = null
        userAccessLevel = 0
        key = ""
        mutableKeyAvailable.value = false
        rawRows = emptyArray()
        rawCorrections = emptyArray()
        rawColumnControls = emptyArray()
        pending = false
        mutableFeed.value = LabourCareFeed(isLoading = false)
    }

    override suspend fun publish(observation: LabourCareObservation) {
        val token = generation
        val organization = orgId ?: error("Select an organisation first.")
        val author = userId ?: error("Your signed-in account is unavailable.")
        require(FirebaseService.currentUser?.uid == author) { "Your sign-in changed. Reopen the Labour Care Guide." }
        val organizationKey = OrganizationKeyVault.keys.value[organization].orEmpty()
        require(organizationKey.isNotBlank()) { "Unlock this organisation's encryption key with your recovery phrase first." }
        val recordedObservation = observation.copy(
            id = observation.id.ifBlank { SyntheticCrypto.generateOpaqueId() },
            authorId = author,
            createdAtMillis = 0L,
            authorAccessLevel = userAccessLevel
        )
        val (ciphertext, iv) = SyntheticCrypto.encrypt(
            json.encodeToString(LabourCareObservation.serializer(), recordedObservation), organizationKey
        )
        mutableFeed.value = mutableFeed.value.copy(message = null)
        scope.launch {
            runCatching { FirebaseService.writeSyntheticLcgObservation(organization, author, userAccessLevel, ciphertext, iv).awaitFirebaseUnit() }
                .onFailure { error -> if (token == generation) mutableFeed.value = mutableFeed.value.copy(message = explainFirestoreError(error.message)) }
        }
    }

    override suspend fun bindAdmissionColumns(
        women: List<`in`.drarunbabu.drsharmila.jananisync.data.model.Woman>, columnIds: Set<String>?
    ) {
        val organization = orgId ?: return
        val token = generation
        admissionBindingMutex.withLock {
            if (token != generation) return@withLock
            check(userAccessLevel >= 60 && key.isNotBlank()) { "Unlock records with an authorised account before preparing them for editing." }
            val byAdmission = women.associateBy { it.admissionId }
            val observations = mutableFeed.value.observations
            val controls = mutableFeed.value.columnControls.filter {
                it.womanDocumentId.isBlank() && (columnIds == null || it.columnId in columnIds)
            }
            for ((columnId) in controls) {
                val admissions = (mutableFeed.value.cellEvents + observations).filter { it.columnId == columnId }.map { it.sessionId }.distinct()
                require(admissions.size <= 1) { "Conflicting admission history must be reviewed before this column can be linked." }
                val admission = admissions.singleOrNull()
                if (columnIds != null) check(admission != null) { "This column's clinical history is still loading." }
                if (admission == null) continue
                val woman = byAdmission[admission]
                if (columnIds != null) check(woman != null) { "This admission is no longer in the roster." }
                if (woman == null) continue
                if (woman.firestoreDocumentId.isBlank()) continue
                if (token != generation) return@withLock
                val completed = completedAdmissionBindings[columnId]
                require(completed == null || completed == woman.firestoreDocumentId) { "This column is already linked to another admission." }
                if (completed != null) continue
                FirebaseService.bindLcgColumnToWoman(organization, columnId, woman.firestoreDocumentId).awaitFirebaseUnit()
                if (token == generation) completedAdmissionBindings[columnId] = woman.firestoreDocumentId
            }
        }
    }

    private suspend fun requireWritableAdmission(observation: LabourCareObservation) {
        val organization = orgId ?: error("Select an organisation first.")
        val token = generation
        val author = userId
        require(observation.womanDocumentId.isNotBlank()) { "Reopen this admission before editing its records." }
        val woman = FirebaseService.getDocument("organizations/$organization/women/${observation.womanDocumentId}").awaitFirebase()
            ?: error("This admission is unavailable.")
        require(token == generation && orgId == organization && FirebaseService.currentUser?.uid == author) {
            "Your session changed. Reopen the admission before editing."
        }
        require(woman.archived != true) { "Archived admissions are read-only. Unarchive this woman before editing her records." }
    }

    override suspend fun saveCell(columnId: String, cellKey: String, observation: LabourCareObservation): String =
        writeState.serialize { token -> saveCellLocked(columnId, cellKey, observation, token) }

    private suspend fun saveCellLocked(columnId: String, cellKey: String, observation: LabourCareObservation, token: Int): String {
        requireWritableAdmission(observation)
        val organization = orgId ?: error("Select an organisation first.")
        val author = userId ?: error("Your signed-in account is unavailable.")
        require(FirebaseService.currentUser?.uid == author) { "Your sign-in changed. Reopen the Labour Care Guide." }
        require(cellKey.isNotBlank() && cellKey != "initials") { "Initials are set when the column is confirmed." }
        val keyText = OrganizationKeyVault.keys.value[organization].orEmpty()
        require(keyText.isNotBlank()) { "Unlock this organisation's key before saving cells." }
        val control = columnId.takeIf(String::isNotBlank)?.let { id ->
            mutableFeed.value.columnControls.firstOrNull { it.columnId == id }
                ?: error("Column status is not loaded. Retry the cell save.")
        }
        val cellKeys = if (cellKey == "partographMark") observation.partographMarkCells.keys.toList() else listOf(cellKey)
        require(cellKeys.isNotEmpty()) { "Select cells before saving." }
        val changes = cellKeys.associateWith { observation.cellValue(it) }
        require(clinicalEditError(changes, observation, observation.isGuideHeader) == null) {
            clinicalEditError(changes, observation, observation.isGuideHeader).orEmpty()
        }
        val observedTime = observationTime(observation.timestamp, cellKeys)
            ?: error("Invalid observation time.")
        require(observation.isGuideHeader || observedTime <= `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis()) { "Future observations cannot be recorded." }
        if (control != null) require(control.womanDocumentId == observation.womanDocumentId) {
            "This record needs archive protection setup before editing. Ask a senior staff member to open it."
        }
        if (control != null) {
            val current = mutableFeed.value.observations.firstOrNull { it.columnId == columnId }
                ?: error("This time column is not loaded. Retry the cell save.")
            require(observation.columnId == columnId && current.sessionId == observation.sessionId &&
                chartMillis(current.timestamp) == chartMillis(observation.timestamp) &&
                current.chartColumnIndex == observation.chartColumnIndex) {
                "The selected cell does not match this time column. Select it again."
            }
        }
        if (control != null) require(control.revision == observation.revision) {
            "This column changed. Review the latest values before saving again."
        }
        if (control != null && !observation.isGuideHeader) {
            require(cellKeys.all { control.canEditCell(it, userAccessLevel) }) {
                "Your access level must be equal to or higher than the last editor of each selected cell."
            }
        }
        require(observation.sessionId.isNotBlank()) { "Select an admission before saving." }
        if (!observation.isGuideHeader && columnId.isBlank()) requireColumnCreationAllowed(observation)
        if (control != null) require(mutableFeed.value.observations.any {
            it.columnId == columnId && it.sessionId == observation.sessionId
        }) { "This column does not belong to the selected admission." }
        val nextRevision = (control?.revision ?: 0) + 1
        val recorded = observation.copy(
            id = observation.id.ifBlank { SyntheticCrypto.generateOpaqueId() },
            columnId = columnId,
            cellKey = cellKey,
            revision = nextRevision,
            authorId = author,
            createdAtMillis = 0L,
            authorAccessLevel = userAccessLevel
        )
        val (ciphertext, iv) = SyntheticCrypto.encrypt(
            json.encodeToString(ClinicalCellEvent.serializer(), recorded.cellEvent(cellKeys)), keyText
        )
        val previous: dynamic = if (control == null) null else js("({})")
        if (control != null) {
            previous.columnId = control.columnId
            previous.revision = control.revision
            previous.highestEditorAccessLevel = control.highestEditorAccessLevel
            previous.highestEditorUids = control.highestEditorUids.toTypedArray()
            previous.confirmed = control.confirmed
            previous.cellAccessLevels = js("({})")
            control.cellAccessLevels.forEach { (key, level) -> previous.cellAccessLevels[key] = level }
        }
        writeState.requireCurrent(token)
        if (!observation.isGuideHeader && columnId.isBlank()) requireColumnCreationAllowed(observation)
        val savedColumnId = FirebaseService.writeLcgCellEvent(
            organization, if (observation.isGuideHeader) "header-${observation.sessionId}" else if (columnId.isBlank() && observation.chartColumnIndex >= 0) "timeline-${observation.womanDocumentId}-${observation.chartColumnIndex}" else columnId, observation.womanDocumentId, cellKey, ciphertext, iv, userAccessLevel, previous, cellKeys.toTypedArray(), observationTimeMillis = observedTime.toDouble(),
            // Stage-relative clocks cannot be expressed as an offset from the original chart start.
            // Keep their schedule in the encrypted observation, as for legacy timed entries.
            chartStartMillis = if (observation.secondStageStartTimestamp.isNotBlank()) null else chartMillis(observation.chartStartTimestamp)?.toDouble(),
            chartColumnIndex = observation.chartColumnIndex, sessionId = observation.sessionId,
            columnChartStartMillis = chartMillis(observation.chartStartTimestamp)?.toDouble(), encryptedKeyVersion = OrganizationKeyRing.version(keyText)
        ).awaitFirebase()
        if (token != generation) return savedColumnId
        val highest = maxOf(userAccessLevel, control?.highestEditorAccessLevel ?: 0)
        val editors = when {
            userAccessLevel > (control?.highestEditorAccessLevel ?: 0) -> listOf(author)
            userAccessLevel == (control?.highestEditorAccessLevel ?: 0) && author !in control?.highestEditorUids.orEmpty() -> control?.highestEditorUids.orEmpty() + author
            else -> control?.highestEditorUids.orEmpty()
        }
        val updatedControl = LabourColumnControl(
            columnId = savedColumnId,
            lastEditorAccessLevel = userAccessLevel,
            highestEditorAccessLevel = highest,
            highestEditorUids = editors,
            revision = nextRevision,
            confirmed = control?.confirmed ?: false,
            confirmedBy = control?.confirmedBy.orEmpty(), confirmationEventId = control?.confirmationEventId.orEmpty(),
            confirmerAccessLevel = control?.confirmerAccessLevel ?: 0,
            cellAccessLevels = control?.cellAccessLevels.orEmpty() + cellKeys.associateWith { userAccessLevel },
            legacyCellAccessLevels = control?.legacyCellAccessLevels.orEmpty(),
            womanDocumentId = observation.womanDocumentId,
            sessionId = observation.sessionId, timestampMillis = chartMillis(observation.timestamp),
            chartStartMillis = chartMillis(observation.chartStartTimestamp), chartColumnIndex = observation.chartColumnIndex
        )
        writeState.acknowledge(token, updatedControl, recorded.copy(columnId = savedColumnId))
        mutableFeed.value = writeState.overlay(mutableFeed.value, includeAcknowledgements = key.isNotBlank())
        return savedColumnId
    }

    private fun requireColumnCreationAllowed(observation: LabourCareObservation) {
        require(writeState.ready && !mutableFeed.value.isLoading) { "Wait for column status to load." }
        val existing = mutableFeed.value.observations.filter { it.sessionId == observation.sessionId && !it.isGuideHeader }
        val start = chartStart(existing)
            ?: chartMillis(observation.chartStartTimestamp)
            ?: error("Select a chart start time first.")
        val time = chartMillis(observation.timestamp) ?: error("Invalid column time.")
        val birth = existing.filter { it.birthConfirmed }.mapNotNull { chartMillis(it.timestamp) }.minOrNull()
        require(birth == null || time <= birth) { "This column is after the recorded birth." }
        require(
            time <= `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis() && canonicalColumnSlots(existing, start, observation.chartColumnIndex + 1).any { (index, scheduled) ->
                index == observation.chartColumnIndex && scheduled == time
            }) { "Choose an eligible scheduled column." }
    }

    override suspend fun confirmColumn(columnId: String, observation: LabourCareObservation) =
        writeState.serialize { token -> confirmColumnLocked(columnId, observation, token) }

    private suspend fun confirmColumnLocked(columnId: String, observation: LabourCareObservation, token: Int) {
        requireWritableAdmission(observation)
        val organization = orgId ?: error("Select an organisation first.")
        val author = userId ?: error("Your signed-in account is unavailable.")
        require(FirebaseService.currentUser?.uid == author) { "Your sign-in changed. Reopen the Labour Care Guide." }
        val control = mutableFeed.value.columnControls.firstOrNull { it.columnId == columnId }
            ?: error("Column status is not loaded. Retry confirmation.")
        require(!observation.isGuideHeader) { "Header fields remain editable and cannot be confirmed." }
        val loadedColumn = mutableFeed.value.observations.firstOrNull { it.columnId == columnId }
        val transition = when {
            observation.fullDilationConfirmed && loadedColumn?.fullDilationConfirmed != true -> "fullDilationConfirmed"
            observation.birthConfirmed && loadedColumn?.birthConfirmed != true -> "birthConfirmed"
            else -> null
        }
        // Stage transitions remain available without replacing a senior clinician's sign-off.
        if (control.confirmed && transition != null) {
            saveCellLocked(columnId, transition, observation.copy(initials = loadedColumn?.initials.orEmpty(),
                cellKey = transition, editedValue = "true"), token)
            return
        }
        require(control.canSignOff(userAccessLevel)) { "Signing an already signed column requires a higher access level than its current signer." }
        require(control.revision == observation.revision) { "This column changed. Review the latest values before confirming." }
        require(userAccessLevel in 10..99) {
            "Only clinical staff can confirm this column."
        }
        val keyText = OrganizationKeyVault.keys.value[organization].orEmpty()
        require(keyText.isNotBlank()) { "Unlock this organisation's key before confirming the column." }
        require(mutableFeed.value.observations.any {
            it.columnId == columnId && it.sessionId == observation.sessionId
        }) { "This column does not belong to the selected admission." }
        val finalObservation = observation.copy(
            id = SyntheticCrypto.generateOpaqueId(),
            columnId = columnId,
            cellKey = "initials",
            revision = control.revision + 1,
            authorId = author,
            createdAtMillis = 0L,
            authorAccessLevel = userAccessLevel
        )
        val (ciphertext, iv) = SyntheticCrypto.encrypt(
            json.encodeToString(ClinicalCellEvent.serializer(), finalObservation.cellEvent(
                listOf("initials") + listOfNotNull(transition))), keyText
        )
        writeState.requireCurrent(token)
        FirebaseService.confirmLcgColumn(
            organization, columnId, control.revision, userAccessLevel, ciphertext, iv,
            listOf("initials") + listOfNotNull(transition), observation.womanDocumentId, OrganizationKeyRing.version(keyText)
        ).awaitFirebaseUnit()
        if (token != generation) return
        writeState.acknowledge(token, control.copy(confirmed = true, confirmedBy = author,
            confirmerAccessLevel = userAccessLevel, revision = finalObservation.revision), finalObservation)
        mutableFeed.value = writeState.overlay(mutableFeed.value, includeAcknowledgements = key.isNotBlank())
    }

    override suspend fun correctCells(columnId: String, values: Map<String, String>, reason: String, observation: LabourCareObservation) =
        writeState.serialize { token -> correctCellsLocked(columnId, values, reason, observation, token) }

    private suspend fun correctCellsLocked(columnId: String, values: Map<String, String>, reason: String, observation: LabourCareObservation, token: Int) {
        requireWritableAdmission(observation)
        require(clinicalEditError(values, observation, observation.isGuideHeader) == null) {
            clinicalEditError(values, observation, observation.isGuideHeader).orEmpty()
        }
        val organization = orgId ?: error("Select an organisation first.")
        val author = userId ?: error("Your signed-in account is unavailable.")
        require(FirebaseService.currentUser?.uid == author) { "Your sign-in changed. Reopen the Labour Care Guide." }
        require(reason.trim().isNotEmpty() && reason.trim().length <= 2000) { "Enter a reason of at most 2,000 characters." }
        require(values.isNotEmpty() && values.size <= 4 && values.keys.all {
            it.matches(Regex("^[A-Za-z][A-Za-z0-9]{0,39}$")) && it != "initials" && it != "partographMark"
        }) { "Select one to four cells to correct." }
        val control = mutableFeed.value.columnControls.firstOrNull { it.columnId == columnId }
            ?: error("Column status is not loaded. Retry the correction.")
        require(control.confirmed) { "Only confirmed columns need a correction." }
        require(control.revision == observation.revision) { "This column changed. Reopen the correction and review the latest values." }
        require(values.keys.all { control.canCorrectCell(it, userAccessLevel) }) {
            "Each selected cell requires sufficient edit access."
        }
        require(mutableFeed.value.observations.any { it.columnId == columnId && it.sessionId == observation.sessionId }) {
            "This column does not belong to the selected admission."
        }
        val organizationKey = OrganizationKeyVault.keys.value[organization].orEmpty()
        require(organizationKey.isNotBlank()) { "Unlock this organisation's encryption key before correcting cells." }
        val corrected = observation.copy(
            id = SyntheticCrypto.generateOpaqueId(), columnId = columnId, cellKey = "partographMark",
            partographMarkCells = values, editedValue = null, correctionReason = reason.trim(),
            revision = control.revision + 1, authorId = author, authorAccessLevel = userAccessLevel,
            firestoreDocumentId = "", createdAtMillis = 0L
        )
        val (ciphertext, iv) = SyntheticCrypto.encrypt(json.encodeToString(ClinicalCellEvent.serializer(), corrected.cellEvent(values.keys)), organizationKey)
        val (reasonCiphertext, reasonIv) = SyntheticCrypto.encrypt(reason.trim(), organizationKey)
        val previous = js("({})")
        previous.columnId = control.columnId
        previous.revision = control.revision
        previous.highestEditorAccessLevel = control.highestEditorAccessLevel
        previous.highestEditorUids = control.highestEditorUids.toTypedArray()
        previous.confirmed = true
        previous.confirmerAccessLevel = control.confirmerAccessLevel
        previous.cellAccessLevels = js("({})")
        control.cellAccessLevels.forEach { (key, level) -> previous.cellAccessLevels[key] = level }
        writeState.requireCurrent(token)
        FirebaseService.writeLcgCellEvent(organization, columnId, observation.womanDocumentId, "partographMark", ciphertext, iv,
            userAccessLevel, previous, values.keys.toTypedArray(), reasonCiphertext, reasonIv,
            observationTimeMillis = (observationTime(observation.timestamp, values.keys) ?: error("Invalid time.")).toDouble(),
            sessionId = observation.sessionId, encryptedKeyVersion = OrganizationKeyRing.version(organizationKey)).awaitFirebase()
        if (token != generation) return
        val highest = maxOf(userAccessLevel, control.highestEditorAccessLevel)
        val editors = when {
            userAccessLevel > control.highestEditorAccessLevel -> listOf(author)
            userAccessLevel == control.highestEditorAccessLevel -> (control.highestEditorUids + author).distinct()
            else -> control.highestEditorUids
        }
        writeState.acknowledge(token, control.copy(revision = corrected.revision, lastEditorAccessLevel = userAccessLevel,
            highestEditorAccessLevel = highest, highestEditorUids = editors,
            cellAccessLevels = control.cellAccessLevels + values.keys.associateWith { userAccessLevel }), corrected)
        mutableFeed.value = writeState.overlay(mutableFeed.value, includeAcknowledgements = key.isNotBlank())
    }

    private fun feedFailed(source: LabourWriteState.Source, error: String) {
        writeState.failed(source, explainFirestoreError(error))
        mutableFeed.value = writeState.overlay(mutableFeed.value, includeAcknowledgements = key.isNotBlank())
    }

    private fun explainFirestoreError(error: String?): String {
        val detail = error.orEmpty()
        return if (detail.contains("permission-denied", ignoreCase = true) ||
            detail.contains("missing or insufficient permissions", ignoreCase = true)
        ) {
            "Firestore denied this encrypted LCG operation. Deploy the current rules with `firebase deploy --only firestore:rules`, then confirm this account is email-verified and an active admin, doctor, or nurse in the selected organisation."
        } else {
            detail.ifBlank { "Firestore could not sync the encrypted observation." }
        }
    }

    private suspend fun decryptRows() {
        val token = generation
        val activeOrgId = orgId
        val localKey = activeOrgId?.let { OrganizationKeyVault.keys.value[it] }.orEmpty()
        key = localKey
        mutableKeyAvailable.value = localKey.isNotBlank()
        val decoded = decoder.decode(localKey, rawRows, rawCorrections, rawColumnControls, pending) ?: return
        if (token != generation || activeOrgId != orgId ||
            localKey != activeOrgId?.let { OrganizationKeyVault.keys.value[it] }.orEmpty()) return
        mutableFeed.value = writeState.overlay(decoded.copy(
            memberNames = mutableFeed.value.memberNames,
            message = decoded.message ?: writeState.retainedMessage(LabourFeedDecoder.retainedMessage(mutableFeed.value.message))
        ), includeAcknowledgements = localKey.isNotBlank())
        if (localKey.isNotBlank()) {
            resolveMemberNames((decoded.cellEvents.map { it.authorId } + decoded.corrections.map { it.authorId }).toSet())
        }
    }

    /** Fetch by UID document ID under the existing member-read permissions. */
    private fun resolveMemberNames(authorIds: Set<String>) {
        val organization = orgId ?: return
        val token = generation
        authorIds.filter { it.isNotBlank() && '/' !in it && requestedMemberNames.add(it) }.forEach { uid ->
            scope.launch {
                try {
                    val member = FirebaseService.getDocument("organizations/$organization/members/$uid").awaitFirebase()
                    val name = member?.name as? String
                    if (token == generation && !name.isNullOrBlank()) {
                        mutableFeed.value = mutableFeed.value.copy(memberNames = mutableFeed.value.memberNames + (uid to name.trim()))
                    }
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    // Missing permissions or connectivity must not hide the immutable history.
                    if (token == generation) requestedMemberNames.remove(uid)
                }
            }
        }
    }

    override fun close() {
        stopObserving()
        scope.cancel()
    }
}

actual fun createLaborDataRepository(): LaborDataRepository = FirebaseLaborDataRepository()
