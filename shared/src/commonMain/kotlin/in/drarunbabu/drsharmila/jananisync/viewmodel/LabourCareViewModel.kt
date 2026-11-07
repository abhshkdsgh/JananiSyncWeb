package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.drarunbabu.drsharmila.jananisync.data.LaborDataRepository
import `in`.drarunbabu.drsharmila.jananisync.data.admissionColumnState
import `in`.drarunbabu.drsharmila.jananisync.data.partographEntrySequence
import `in`.drarunbabu.drsharmila.jananisync.data.model.AmnioticFluidStatus
import `in`.drarunbabu.drsharmila.jananisync.data.model.FetalWellbeing
import `in`.drarunbabu.drsharmila.jananisync.data.model.LaborProgress
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.MaternalVitals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import `in`.drarunbabu.drsharmila.jananisync.data.*

data class LabourCareUiState(
    val activeColumnId: String = "",
    val chartStartTimestamp: String = "",
    val chartStartConfirmed: Boolean = false,
    val orgId: String = "",
    val organizationKeyAvailable: Boolean = false,
    val patientLabel: String = "",
    val patientSessionId: String = "",
    val patientRosterDocumentId: String = "",
    val patientArchived: Boolean = false,
    val parity: String = "",
    val labourOnset: String = "",
    val activeLabourDiagnosis: String = "",
    val rupturedMembranes: String = "",
    val riskFactors: String = "",
    val systolic: String = "",
    val diastolic: String = "",
    val pulse: String = "",
    val temperature: String = "",
    val fetalHeartRate: String = "",
    val amnioticFluid: AmnioticFluidStatus = AmnioticFluidStatus.UNKNOWN,
    val amnioticFluidFinding: String = "",
    val cervicalDilation: String = "",
    val contractionFrequency: String = "",
    val contractionDuration: String = "",
    val companion: String = "",
    val painRelief: String = "",
    val oralFluid: String = "",
    val posture: String = "",
    val fhrDeceleration: String = "",
    val fetalPosition: String = "",
    val caput: String = "",
    val moulding: String = "",
    val urineProtein: String = "",
    val urineAcetone: String = "",
    val oxytocin: String = "",
    val medicine: String = "",
    val intravenousFluids: String = "",
    val descent: String = "",
    val assessment: String = "",
    val plan: String = "",
    val initials: String = "",
    val partographCells: Map<String, String> = emptyMap(),
    val isSaving: Boolean = false,
    val feed: LabourCareFeed = LabourCareFeed()
)

/** Allowed codes for fields that the Labour Care Guide records categorically. */
fun categoricalOptionsForCell(field: String): List<String>? = when (field) {
    "companion", "painRelief", "oralFluid" -> listOf("Y", "N", "D", "U")
    "posture" -> listOf("SP", "MO")
    "fhrDeceleration" -> listOf("N", "E", "L", "V")
    "amnioticFluid" -> listOf("I", "C", "M+", "M++", "M+++", "B")
    "fetalPosition" -> listOf("A", "P", "T")
    "caput", "moulding" -> listOf("0", "+", "++", "+++")
    "urineProtein" -> listOf("P-", "P Trace", "P+", "P++", "P+++")
    "urineAcetone" -> listOf("A-", "A+", "A++", "A+++", "A++++")
    else -> null
}

class LabourCareViewModel(private val repository: LaborDataRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LabourCareUiState())
    val uiState: StateFlow<LabourCareUiState> = mutableState.asStateFlow()
    private var observingUserId: String? = null
    private var selectionGeneration = 0
    private var activeColumnSelectionGeneration = 0

    private fun resetPatient() {
        repository.selectAdmission(null)
        selectionGeneration++
        pendingHeader = null
        pendingColumnId = ""
        pendingColumnObservation = null
        val state = mutableState.value
        mutableState.value = LabourCareUiState(orgId = state.orgId,
            organizationKeyAvailable = state.organizationKeyAvailable, initials = state.initials, feed = state.feed)
    }

    init {
        viewModelScope.launch { repository.feed.collect { mutableState.value = mutableState.value.copy(feed = it).withHeader() } }
        viewModelScope.launch { repository.organizationKeyAvailable.collect { available ->
            mutableState.value = mutableState.value.copy(organizationKeyAvailable = available)
        } }
    }

    fun observe(orgId: String, userId: String, accessLevel: Int) {
        if (mutableState.value.orgId == orgId && observingUserId == userId && observingAccessLevel == accessLevel) return
        resetPatient()
        observingUserId = userId
        observingAccessLevel = accessLevel
        mutableState.value = mutableState.value.copy(orgId = orgId, organizationKeyAvailable = false)
        repository.observe(orgId, userId, accessLevel)
    }
    private var observingAccessLevel: Int? = null
    fun stopObserving() {
        selectionGeneration++
        observingUserId = null
        observingAccessLevel = null
        pendingHeader = null
        pendingColumnId = ""
        pendingColumnObservation = null
        repository.stopObserving()
        mutableState.value = LabourCareUiState()
    }
    fun setRecorderName(value: String) { mutableState.value = mutableState.value.copy(initials = value.trim().take(160)) }
    suspend fun bindAdmissionColumns(women: List<`in`.drarunbabu.drsharmila.jananisync.data.model.Woman>, columnIds: Set<String>? = null) {
        repository.bindAdmissionColumns(women, columnIds)
    }

    fun selectPatient(sessionId: String, patientName: String, womanDocumentId: String = "", archived: Boolean = false) {
        if (mutableState.value.patientSessionId != sessionId) resetPatient()
        repository.selectAdmission(womanDocumentId.takeIf { it.isNotBlank() })
        val archiveStatusChanged = mutableState.value.patientArchived != archived
        if (archiveStatusChanged) {
            selectionGeneration++
            pendingHeader = null
            pendingColumnId = ""
            pendingColumnObservation = null
        }
        mutableState.value = mutableState.value.copy(isSaving = !archiveStatusChanged && mutableState.value.isSaving,
            patientLabel = patientName, patientSessionId = sessionId,
            patientRosterDocumentId = womanDocumentId, patientArchived = archived,
            chartStartTimestamp = mutableState.value.chartStartTimestamp.ifBlank { currentTimestampIso() }).withHeader()
    }
    private fun LabourCareUiState.withHeader(): LabourCareUiState {
        val header = feed.observations.firstOrNull { it.sessionId == patientSessionId && it.isGuideHeader }
            ?: feed.observations.filter { it.sessionId == patientSessionId && !it.isGuideHeader }.maxByOrNull { it.timestamp }
            ?: return this
        val start = chartStart(feed.observations.filter { it.sessionId == patientSessionId })
        return copy(chartStartTimestamp = start?.let { kotlin.time.Instant.fromEpochMilliseconds(it).toString() } ?: chartStartTimestamp,
            chartStartConfirmed = start != null || chartStartConfirmed, patientLabel = header.patientLabel.ifBlank { patientLabel },
            parity = header.partographCells["parity"] ?: header.parity.toString(),
            labourOnset = header.labourOnset, activeLabourDiagnosis = header.activeLabourDiagnosis,
            rupturedMembranes = header.rupturedMembranesDateTime, riskFactors = header.riskFactors)
    }

    fun clearSelectedPatient() { resetPatient() }
    fun setParity(value: String) { mutableState.value = mutableState.value.copy(parity = value.filter(Char::isDigit).take(2)) }
    fun setSessionField(field: String, value: String) {
        val text = value.take(64)
        mutableState.value = when (field) {
            "activeColumnId" -> {
                val id = value.take(256)
                if (id != mutableState.value.activeColumnId) activeColumnSelectionGeneration++
                mutableState.value.copy(activeColumnId = id)
            }
            "chartStartTimestamp" -> if (pendingColumnObservation?.sessionId != mutableState.value.patientSessionId && mutableState.value.feed.observations.none { it.sessionId == mutableState.value.patientSessionId && !it.isGuideHeader })
                mutableState.value.copy(chartStartTimestamp = text, chartStartConfirmed = false) else mutableState.value
            "chartStartConfirmed" -> mutableState.value.copy(chartStartConfirmed = value == "true" &&
                chartMillis(mutableState.value.chartStartTimestamp)?.let { it <= currentTimeMillis() } == true)
            "labourOnset" -> mutableState.value.copy(labourOnset = text)
            "activeLabourDiagnosis" -> mutableState.value.copy(activeLabourDiagnosis = text)
            "rupturedMembranes" -> mutableState.value.copy(rupturedMembranes = text)
            "riskFactors" -> mutableState.value.copy(riskFactors = text)
            else -> mutableState.value
        }
    }
    fun setSystolic(value: String) { mutableState.value = mutableState.value.copy(systolic = value.filter(Char::isDigit).take(3)) }
    fun setDiastolic(value: String) { mutableState.value = mutableState.value.copy(diastolic = value.filter(Char::isDigit).take(3)) }
    fun setPulse(value: String) { mutableState.value = mutableState.value.copy(pulse = value.filter(Char::isDigit).take(3)) }
    fun setTemperature(value: String) { mutableState.value = mutableState.value.copy(temperature = value.filter { it.isDigit() || it == '.' }.take(4)) }
    fun setFetalHeartRate(value: String) { mutableState.value = mutableState.value.copy(fetalHeartRate = value.filter(Char::isDigit).take(3)) }
    fun setDilation(value: String) { mutableState.value = mutableState.value.copy(cervicalDilation = value.filter { it.isDigit() || it == '.' }.take(3)) }
    fun setContractionFrequency(value: String) { mutableState.value = mutableState.value.copy(contractionFrequency = value.filter(Char::isDigit).take(2)) }
    fun setContractionDuration(value: String) { mutableState.value = mutableState.value.copy(contractionDuration = value.filter(Char::isDigit).take(3)) }

    fun setCell(field: String, value: String) {
        val options = categoricalOptionsForCell(field)
        if (options != null && value.isNotEmpty() && value !in options) return
        val text = value.take(64)
        when (field) {
            "gravida", "urineVolume", "rupturedHours" -> mutableState.value = mutableState.value.copy(partographCells = mutableState.value.partographCells + (field to text))
            "companion" -> mutableState.value = mutableState.value.copy(companion = text.take(2))
            "painRelief" -> mutableState.value = mutableState.value.copy(painRelief = text.take(2))
            "oralFluid" -> mutableState.value = mutableState.value.copy(oralFluid = text.take(2))
            "posture" -> mutableState.value = mutableState.value.copy(posture = text.take(3))
            "fetalHeartRate" -> setFetalHeartRate(value)
            "fhrDeceleration" -> mutableState.value = mutableState.value.copy(fhrDeceleration = text.take(1).uppercase())
            "amnioticFluid" -> mutableState.value = mutableState.value.copy(
                amnioticFluidFinding = text.uppercase().take(4),
                amnioticFluid = when (text.uppercase().take(1)) { "M" -> AmnioticFluidStatus.MECONIUM_STAINED; "B" -> AmnioticFluidStatus.BLOOD_STAINED; "I" -> AmnioticFluidStatus.INTACT; "U" -> AmnioticFluidStatus.UNKNOWN; else -> AmnioticFluidStatus.CLEAR }
            )
            "fetalPosition" -> mutableState.value = mutableState.value.copy(fetalPosition = text.take(1).uppercase())
            "caput" -> mutableState.value = mutableState.value.copy(caput = text.take(3))
            "moulding" -> mutableState.value = mutableState.value.copy(moulding = text.take(3))
            "pulse" -> setPulse(value)
            "systolic" -> setSystolic(value)
            "diastolic" -> setDiastolic(value)
            "temperature" -> setTemperature(value)
            "urineProtein" -> mutableState.value = mutableState.value.copy(urineProtein = text.take(7))
            "urineAcetone" -> mutableState.value = mutableState.value.copy(urineAcetone = text.take(5))
            "contractionFrequency" -> setContractionFrequency(value)
            "contractionDuration" -> setContractionDuration(value)
            "cervicalDilation" -> setDilation(value)
            "oxytocin" -> mutableState.value = mutableState.value.copy(oxytocin = text)
            "medicine" -> mutableState.value = mutableState.value.copy(medicine = value)
            "intravenousFluids" -> mutableState.value = mutableState.value.copy(intravenousFluids = value)
            "descent" -> mutableState.value = mutableState.value.copy(descent = text.filter(Char::isDigit).take(1))
            "assessment" -> mutableState.value = mutableState.value.copy(assessment = value)
            "plan" -> mutableState.value = mutableState.value.copy(plan = value)
            "initials" -> mutableState.value = mutableState.value.copy(initials = text.take(160))
        }
    }

    private var pendingHeader: LabourCareObservation? = null
    private val headerSaveMutex = Mutex()
    private val cellSaveMutex = Mutex()

    fun saveHeaderCell(field: String, value: String, expectedSessionId: String? = null, expectedOrgId: String? = null): kotlinx.coroutines.Job {
        val requestToken = selectionGeneration
        val requestSession = expectedSessionId ?: mutableState.value.patientSessionId
        val requestOrg = expectedOrgId ?: mutableState.value.orgId
        return viewModelScope.launch {
            headerSaveMutex.withLock {
                mutableState.first { !it.isSaving }
                if (requestToken != selectionGeneration || mutableState.value.patientSessionId != requestSession || mutableState.value.orgId != requestOrg) return@launch
                val state = mutableState.value
                val token = selectionGeneration
                if (state.patientArchived || state.isSaving || !state.organizationKeyAvailable || state.patientSessionId.isBlank()) return@launch
                if (field !in `in`.drarunbabu.drsharmila.jananisync.data.model.allHeaderFields.map { it.first } || !`in`.drarunbabu.drsharmila.jananisync.data.model.validHeaderValue(field, value)) return@launch
                if (field == "labourOnset" && value !in listOf("spontaneous", "induced", "")) return@launch
                if (field == "parity" && (value.length > 2 || value.any { !it.isDigit() })) return@launch
                val original = listOfNotNull(
                    state.feed.observations.firstOrNull { it.sessionId == state.patientSessionId && it.isGuideHeader },
                    pendingHeader?.takeIf { it.sessionId == state.patientSessionId }
                ).maxByOrNull { it.revision }
                if (original?.partographCells?.get(field) == value) return@launch
                val snapshot = (original ?: state.toObservation(currentTimestampIso()).copy(
                    isGuideHeader = true, recordedCellKeys = emptySet(), partographCells = mapOf("parity" to state.parity)
                )).withCell(field, value)
                mutableState.value = state.copy(isSaving = true, feed = state.feed.copy(message = null))
                try {
                    val id = repository.saveCell(original?.columnId.orEmpty(), field,
                        snapshot.copy(womanDocumentId = state.patientRosterDocumentId, authorName = state.initials, editedValue = value, partographMarkCells = emptyMap()))
                    if (token == selectionGeneration) pendingHeader = snapshot.copy(columnId = id, revision = (original?.revision ?: 0) + 1)
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    if (token == selectionGeneration) mutableState.value = mutableState.value.copy(feed = mutableState.value.feed.copy(message = error.message ?: "Could not save header cell."))
                } finally { if (token == selectionGeneration) mutableState.value = mutableState.value.copy(isSaving = false) }
            }
        }
    }

    fun saveCell(requestedColumnId: String, field: String, value: String): kotlinx.coroutines.Job {
        val clickedTarget = parseLcgCellTarget(requestedColumnId)
        val columnId = clickedTarget?.columnId ?: requestedColumnId
        val requestSelection = selectionGeneration
        val activeColumnToken = activeColumnSelectionGeneration
        return viewModelScope.launch {
            if (requestSelection != selectionGeneration) return@launch
            if (field == "initials") return@launch
            cellSaveMutex.withLock {
            mutableState.first { !it.isSaving }
            if (requestSelection != selectionGeneration) return@withLock
            val state = mutableState.value
            val token = selectionGeneration
            if (!state.organizationKeyAvailable) {
                mutableState.value = state.copy(feed = state.feed.copy(message = "Unlock this organisation's key before saving cells."))
                return@withLock
            }
            if (state.patientArchived || state.patientSessionId.isBlank() || !state.chartStartConfirmed) return@withLock
            val columns = state.feed.admissionColumnState(state.patientSessionId, state.patientRosterDocumentId, pendingColumnObservation)
            val timelineStart = chartStart(state.feed.observations.filter { it.sessionId == state.patientSessionId })
                ?: chartMillis(state.chartStartTimestamp) ?: currentTimeMillis()
            val requestedTime = scheduledColumnTime(columnId) ?: if (columnId.isBlank()) {
                val active = (state.feed.observations + listOfNotNull(pendingColumnObservation)).filter {
                    it.sessionId == state.patientSessionId && it.columnId in columns.openColumnIds
                }.maxByOrNull { chartMillis(it.timestamp) ?: Long.MIN_VALUE }
                val sequence = (state.feed.observations + listOfNotNull(pendingColumnObservation)).filter { it.sessionId == state.patientSessionId }.partographEntrySequence()
                active?.timestamp?.let(::chartMillis) ?: sequence.nextTimestampMillis(timelineStart)
            } else null
            val sessionRecords = state.feed.observations.filter { it.sessionId == state.patientSessionId && !it.isGuideHeader }
            val stageIndex = requestedTime?.let { stageColumnIndex(sessionRecords, it) }
            val targetIndex = clickedTarget?.index?.takeIf { it >= 0 } ?: stageIndex
            val effectiveColumnId = if (requestedTime != null) state.feed.observations.firstOrNull {
                it.sessionId == state.patientSessionId && !it.isGuideHeader && chartMillis(it.timestamp) == requestedTime && (targetIndex == null || it.chartColumnIndex == targetIndex)
            }?.columnId ?: pendingColumnObservation?.takeIf { chartMillis(it.timestamp) == requestedTime && (targetIndex == null || it.chartColumnIndex == targetIndex) }?.columnId.orEmpty() else columnId
            if (effectiveColumnId.isBlank() && !columns.canStartColumn) {
                mutableState.value = state.copy(feed = state.feed.copy(message = columns.creationBlockMessage))
                return@withLock
            }
            val loaded = state.feed.observations.firstOrNull {
                effectiveColumnId.isNotBlank() && it.columnId == effectiveColumnId && it.sessionId == state.patientSessionId && !it.isGuideHeader
            }
            val saved = listOfNotNull(loaded, pendingColumnObservation?.takeIf { it.columnId == effectiveColumnId && it.sessionId == state.patientSessionId })
                .maxByOrNull(LabourCareObservation::revision)
            if (effectiveColumnId.isNotBlank() && saved == null) return@withLock
            if (clickedTarget != null && saved != null && !clickedTarget.matches(saved)) {
                mutableState.value = state.copy(feed = state.feed.copy(message = "This time column changed. Select the cell again."))
                return@withLock
            }
            if (effectiveColumnId.isNotBlank()) {
                val control = state.feed.columnControls.firstOrNull { it.columnId == effectiveColumnId }
                if (control == null || !control.canEditCell(field, observingAccessLevel ?: 0)) {
                    mutableState.value = state.copy(feed = state.feed.copy(message =
                        if (control == null) "Column status is not loaded. Retry the cell save." else "This cell requires a higher access level."))
                    return@withLock
                }
            }
            val birthTime = state.feed.observations.filter { it.sessionId == state.patientSessionId && it.birthConfirmed }.mapNotNull { chartMillis(it.timestamp) }.minOrNull()
            if (saved == null && birthTime != null && (requestedTime ?: timelineStart) > birthTime) {
                mutableState.value = state.copy(feed = state.feed.copy(message = "This column is after the recorded birth.")); return@withLock
            }
            val timestamp = saved?.timestamp ?: kotlin.time.Instant.fromEpochMilliseconds(requestedTime ?: timelineStart).toString()
            clinicalEditError(mapOf(field to value), saved)?.let { error ->
                mutableState.value = state.copy(feed = state.feed.copy(message = error)); return@withLock
            }
            val observationTime = observationTime(timestamp, listOf(field)) ?: return@withLock
            if (observationTime > currentTimeMillis() || (saved == null && canonicalColumnIndex(sessionRecords, timelineStart, chartMillis(timestamp)!!) == null)) {
                mutableState.value = state.copy(feed = state.feed.copy(message = "This observation time is not available yet or is outside the chart schedule."))
                return@withLock
            }
            val snapshot = (saved?.withCell(field, value) ?: state.toObservation(timestamp).withCell(field, value)).copy(
                secondStageStartTimestamp = saved?.secondStageStartTimestamp?.takeIf { it.isNotBlank() }
                    ?: secondStageTransition(sessionRecords)?.takeIf { transition ->
                        (saved?.chartColumnIndex ?: targetIndex ?: -1) >= transition.chartColumnIndex
                    }?.secondStageStartTimestamp.orEmpty(),
                chartStartTimestamp = saved?.chartStartTimestamp ?: state.chartStartTimestamp,
                chartColumnIndex = saved?.chartColumnIndex ?: targetIndex ?: ((chartMillis(timestamp)!! - timelineStart) / CHART_HOUR).toInt(),
                observedTimestamp = kotlin.time.Instant.fromEpochMilliseconds(observationTime).toString())
            mutableState.value = state.copy(isSaving = true, feed = state.feed.copy(message = null))
            try {
                val id = repository.saveCell(effectiveColumnId, field, snapshot.copy(womanDocumentId = state.patientRosterDocumentId, authorName = state.initials, editedValue = value, partographMarkCells = emptyMap()))
                if (token == selectionGeneration) {
                    if (activeColumnToken == activeColumnSelectionGeneration || mutableState.value.activeColumnId == id ||
                        mutableState.value.activeColumnId == scheduledColumnId(chartMillis(timestamp)!!))
                        mutableState.value = mutableState.value.copy(activeColumnId = id)
                    pendingColumnId = id
                    pendingColumnObservation = snapshot.copy(columnId = id, revision = (saved?.revision ?: 0) + 1)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (token == selectionGeneration) mutableState.value = mutableState.value.copy(feed = mutableState.value.feed.copy(message = error.message ?: "Could not save this cell."))
            } finally {
                if (token == selectionGeneration) mutableState.value = mutableState.value.copy(isSaving = false)
            }
            }
        }
    }

    /** Commit every parameter of a graph mark as one encrypted, append-only event. */
    suspend fun savePartographMark(columnId: String, timestamp: String, values: Map<String, String>): Boolean {
        val activeColumnToken = activeColumnSelectionGeneration
        val state = mutableState.value
        val token = selectionGeneration
        if (state.patientArchived || state.isSaving || !state.organizationKeyAvailable || state.patientSessionId.isBlank() || !state.chartStartConfirmed || values.isEmpty()) return false
        val scheduledIndex = scheduledColumnIndex(columnId)
        val requestedId = columnId.takeUnless { it.startsWith("schedule:") }.orEmpty()
        val effectiveId = requestedId.ifBlank {
            state.feed.observations.firstOrNull {
                it.sessionId == state.patientSessionId && chartMillis(it.timestamp) == chartMillis(timestamp) &&
                    (scheduledIndex == null || it.chartColumnIndex == scheduledIndex) && !it.isGuideHeader && it.cellKey != "skipped" && it.columnId.isNotBlank()
            }?.columnId ?: pendingColumnObservation?.takeIf {
                it.sessionId == state.patientSessionId && chartMillis(it.timestamp) == chartMillis(timestamp) &&
                    (scheduledIndex == null || it.chartColumnIndex == scheduledIndex) && !it.isGuideHeader
            }?.columnId.orEmpty()
        }
        val columns = state.feed.admissionColumnState(state.patientSessionId, state.patientRosterDocumentId, pendingColumnObservation)
        if (effectiveId.isBlank() && !columns.canStartColumn) {
            mutableState.value = state.copy(feed = state.feed.copy(message = columns.creationBlockMessage))
            return false
        }
        if (effectiveId.isBlank()) {
            val sequence = (state.feed.observations + listOfNotNull(pendingColumnObservation))
                .filter { it.sessionId == state.patientSessionId }
                .partographEntrySequence(state.feed.columnControls, state.patientRosterDocumentId)
            val anchored = if (sequence.baselineMillis == null) sequence.copy(baselineMillis = chartMillis(state.chartStartTimestamp)) else sequence
            anchored.creationBlockMessage(timestamp)?.let { message ->
                mutableState.value = state.copy(feed = state.feed.copy(message = message))
                return false
            }
        }
        val loaded = state.feed.observations.firstOrNull {
            effectiveId.isNotBlank() && it.columnId == effectiveId && it.sessionId == state.patientSessionId && !it.isGuideHeader
        }
        val cached = pendingColumnObservation?.takeIf { it.columnId == effectiveId && it.sessionId == state.patientSessionId && effectiveId.isNotBlank() }
        val original = listOfNotNull(loaded, cached).maxByOrNull { it.revision }
        if (effectiveId.isNotBlank() && original == null) return false
        if (original != null && chartMillis(original.timestamp) != chartMillis(timestamp)) return false
        val observedTime = observationTime(timestamp, values.keys) ?: return false
        if (observedTime > currentTimeMillis()) {
            mutableState.value = state.copy(feed = state.feed.copy(message = "Future observations cannot be recorded.")); return false
        }
        val control = state.feed.columnControls.firstOrNull { it.columnId == effectiveId }
        if (effectiveId.isNotBlank() && (control == null || !values.keys.all { control.canEditCell(it, observingAccessLevel ?: 0) })) return false
        clinicalEditError(values, original)?.let { error ->
            mutableState.value = state.copy(feed = state.feed.copy(message = error)); return false
        }
        val snapshotBase = values.entries.fold(original ?: state.toObservation(timestamp).copy(
            recordedCellKeys = emptySet(), halfHourCells = emptyMap(), partographCells = emptyMap()
        )) { observation, (key, value) -> observation.withCell(key, value) }
        val start = chartStart(state.feed.observations.filter { it.sessionId == state.patientSessionId }) ?: chartMillis(state.chartStartTimestamp) ?: chartMillis(timestamp)!!
        val snapshot = snapshotBase.copy(chartStartTimestamp = original?.chartStartTimestamp ?: kotlin.time.Instant.fromEpochMilliseconds(start).toString(),
            chartColumnIndex = original?.chartColumnIndex ?: scheduledIndex ?: canonicalColumnIndex(
                state.feed.observations.filter { it.sessionId == state.patientSessionId }, start, chartMillis(timestamp)!!) ?: return false,
            secondStageStartTimestamp = original?.secondStageStartTimestamp ?: secondStageTransition(
                state.feed.observations.filter { it.sessionId == state.patientSessionId })?.secondStageStartTimestamp.orEmpty(),
            observedTimestamp = kotlin.time.Instant.fromEpochMilliseconds(observedTime).toString())
        mutableState.value = state.copy(isSaving = true, feed = state.feed.copy(message = null))
        return try {
            val id = repository.saveCell(effectiveId, "partographMark", snapshot.copy(
                womanDocumentId = state.patientRosterDocumentId,
                authorName = state.initials, editedValue = null, partographMarkCells = values
            ))
            if (token == selectionGeneration) {
                if (activeColumnToken == activeColumnSelectionGeneration || mutableState.value.activeColumnId == id ||
                        mutableState.value.activeColumnId == scheduledColumnId(chartMillis(timestamp)!!))
                        mutableState.value = mutableState.value.copy(activeColumnId = id)
                pendingColumnId = id
                pendingColumnObservation = snapshot.copy(columnId = id, revision = (original?.revision ?: 0) + 1)
            }
            true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (token == selectionGeneration) mutableState.value = mutableState.value.copy(feed = mutableState.value.feed.copy(message = error.message ?: "Could not save this mark. Click again to retry."))
            false
        } finally { if (token == selectionGeneration) mutableState.value = mutableState.value.copy(isSaving = false) }
    }

    private var pendingColumnId: String = ""
    private var pendingColumnObservation: LabourCareObservation? = null

    fun confirmColumn(columnId: String) = confirmColumn(columnId, false)

    fun confirmFullDilation(columnId: String) = confirmColumn(columnId, true, clickedAt = currentTimeMillis())

    fun confirmBirth(columnId: String) = confirmColumn(columnId, fullDilation = false, birth = true)

    private fun confirmColumn(columnId: String, fullDilation: Boolean, birth: Boolean = false, clickedAt: Long = currentTimeMillis()) = viewModelScope.launch {
        val state = mutableState.value
        if (state.patientArchived || state.isSaving || !state.organizationKeyAvailable) return@launch
        val token = selectionGeneration
        val original = listOfNotNull(
            state.feed.observations.firstOrNull { it.columnId == columnId && it.sessionId == state.patientSessionId },
            pendingColumnObservation?.takeIf { it.columnId == columnId && it.sessionId == state.patientSessionId }
        ).maxByOrNull(LabourCareObservation::revision) ?: return@launch
        if (original.isGuideHeader || (fullDilation && original.fullDilationConfirmed) || (birth && original.birthConfirmed)) return@launch
        val secondStageStart = if (fullDilation) kotlin.time.Instant.fromEpochMilliseconds(
            secondStageStartTime(chartMillis(original.timestamp) ?: return@launch, clickedAt)).toString()
            else original.secondStageStartTimestamp
        mutableState.value = state.copy(isSaving = true, feed = state.feed.copy(message = null))
        try {
            repository.confirmColumn(columnId, original.copy(secondStageStartTimestamp = secondStageStart, womanDocumentId = state.patientRosterDocumentId, initials = state.initials, authorName = state.initials, editedValue = state.initials, partographMarkCells = emptyMap(), fullDilationConfirmed = original.fullDilationConfirmed || fullDilation, birthConfirmed = original.birthConfirmed || birth))
            if (token == selectionGeneration && pendingColumnId == columnId) {
                pendingColumnId = ""
                // Keep the acknowledged time until observation listeners catch up, so the
                // next Partograph save cannot lose its hourly sequence after confirmation.
                pendingColumnObservation = original.copy(secondStageStartTimestamp = secondStageStart, revision = original.revision + 1,
                    fullDilationConfirmed = original.fullDilationConfirmed || fullDilation,
                    birthConfirmed = original.birthConfirmed || birth)
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (token == selectionGeneration) mutableState.value = mutableState.value.copy(feed = mutableState.value.feed.copy(message = error.message ?: "Could not confirm this column."))
        } finally { if (token == selectionGeneration) mutableState.value = mutableState.value.copy(isSaving = false) }
    }

    private fun LabourCareUiState.toObservation(timestamp: String) = LabourCareObservation(
        id = "", sessionId = patientSessionId, patientLabel = patientLabel, womanDocumentId = patientRosterDocumentId,
        parity = parity.toIntOrNull() ?: 0, timestamp = timestamp,
        labourOnset = labourOnset, activeLabourDiagnosis = activeLabourDiagnosis,
        rupturedMembranesDateTime = rupturedMembranes, riskFactors = riskFactors,
        maternalVitals = MaternalVitals(timestamp, systolic.toIntOrNull() ?: 0, diastolic.toIntOrNull() ?: 0,
            pulse.toIntOrNull() ?: 0, temperature.toDoubleOrNull() ?: 0.0, urineProtein, urineAcetone),
        fetalWellbeing = FetalWellbeing(timestamp, fetalHeartRate.toIntOrNull() ?: 0, amnioticFluid,
            fhrDeceleration, fetalPosition, caput, moulding, amnioticFluidFinding),
        laborProgress = LaborProgress(timestamp, cervicalDilation.toDoubleOrNull() ?: 0.0,
            contractionFrequency.toIntOrNull() ?: 0, contractionDuration.toIntOrNull() ?: 0),
        companion = companion, painRelief = painRelief, oralFluid = oralFluid, posture = posture,
        oxytocin = oxytocin, medicine = medicine, intravenousFluids = intravenousFluids,
        descent = descent, assessment = assessment, plan = plan, initials = "", partographCells = partographCells
    )



    fun saveObservation() = viewModelScope.launch {
        val state = mutableState.value
        if (state.patientArchived) return@launch
        val token = selectionGeneration
        val parity = state.parity.toIntOrNull()
        val sys = state.systolic.toIntOrNull(); val dia = state.diastolic.toIntOrNull()
        val pulse = state.pulse.toIntOrNull(); val temp = state.temperature.toDoubleOrNull()
        val fhr = state.fetalHeartRate.toIntOrNull(); val dilation = state.cervicalDilation.toDoubleOrNull()
        val frequency = state.contractionFrequency.toIntOrNull(); val duration = state.contractionDuration.toIntOrNull()
        val error = when {
            !state.organizationKeyAvailable -> "Unlock this organisation's key with your recovery phrase before saving."
            state.initials.isBlank() -> "Your signed-in organisation name is unavailable; reopen the Labour Care Guide before saving."
            state.patientLabel.isBlank() -> "Select a woman from the ward list first."
            state.patientSessionId.isBlank() -> "Select a woman from the ward list before saving."
            parity == null || parity !in 0..20 -> "Parity must be from 0 to 20."
            sys == null || sys !in 50..260 || dia == null || dia !in 20..160 -> "Enter valid blood pressure values."
            pulse == null || pulse !in 20..240 || temp == null || temp !in 30.0..45.0 -> "Enter valid pulse and temperature values."
            fhr == null || fhr !in 50..250 || dilation == null || dilation !in 0.0..10.0 -> "Enter valid fetal heart rate and dilation values."
            frequency == null || frequency !in 0..10 || duration == null || duration !in 0..300 -> "Enter valid contraction values."
            else -> null
        }
        if (error != null) { mutableState.value = state.copy(feed = state.feed.copy(message = error)); return@launch }
        mutableState.value = state.copy(isSaving = true, feed = state.feed.copy(message = null))
        try {
            val timestamp = currentTimestampIso()
            repository.publish(LabourCareObservation(
                id = "", sessionId = state.patientSessionId,
                patientLabel = state.patientLabel, parity = parity!!, timestamp = timestamp,
                labourOnset = state.labourOnset,
                activeLabourDiagnosis = state.activeLabourDiagnosis,
                rupturedMembranesDateTime = state.rupturedMembranes,
                riskFactors = state.riskFactors,
                maternalVitals = MaternalVitals(timestamp, sys!!, dia!!, pulse!!, temp!!, state.urineProtein, state.urineAcetone),
                fetalWellbeing = FetalWellbeing(timestamp, fhr!!, state.amnioticFluid, state.fhrDeceleration, state.fetalPosition, state.caput, state.moulding, state.amnioticFluidFinding),
                laborProgress = LaborProgress(timestamp, dilation!!, frequency!!, duration!!),
                companion = state.companion,
                painRelief = state.painRelief,
                oralFluid = state.oralFluid,
                posture = state.posture,
                oxytocin = state.oxytocin,
                medicine = state.medicine,
                intravenousFluids = state.intravenousFluids,
                descent = state.descent,
                assessment = state.assessment,
                plan = state.plan,
                initials = state.initials
            ))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (token == selectionGeneration) mutableState.value = mutableState.value.copy(feed = mutableState.value.feed.copy(message = error.message ?: "Could not encrypt and queue this observation."))
        } finally { if (token == selectionGeneration) mutableState.value = mutableState.value.copy(isSaving = false) }
    }

    suspend fun recordCorrection(columnId: String, expectedRevision: Int, values: Map<String, String>, reason: String): Boolean {
        val state = mutableState.value
        val token = selectionGeneration
        if (state.patientArchived || state.isSaving || !state.organizationKeyAvailable || state.patientSessionId.isBlank()) return false
        val original = state.feed.observations.firstOrNull { it.columnId == columnId && it.sessionId == state.patientSessionId }
            ?: return false
        if (observationTime(original.timestamp, values.keys)?.let { it > currentTimeMillis() } != false) return false
        val control = state.feed.columnControls.firstOrNull { it.columnId == columnId } ?: return false
        if (reason.isBlank() || reason.trim().length > 2000 || values.isEmpty() || values.size > 4) return false
        if (values.keys.any { !control.canCorrectCell(it, observingAccessLevel ?: 0) }) return false
        if (values.any { (key, value) ->
                if (original.isGuideHeader) key !in `in`.drarunbabu.drsharmila.jananisync.data.model.allHeaderFields.map { it.first } || !`in`.drarunbabu.drsharmila.jananisync.data.model.validHeaderValue(key, value)
                else key in `in`.drarunbabu.drsharmila.jananisync.data.model.allHeaderFields.map { it.first } && key !in setOf("gravida", "rupturedHours")
            }) return false
        if ("labourOnset" in values && values["labourOnset"] !in listOf("spontaneous", "induced", "")) return false
        if (values["parity"]?.let { it.length > 2 || it.any { char -> !char.isDigit() } } == true) return false
        if (control.revision != expectedRevision || original.revision != expectedRevision) {
            mutableState.value = state.copy(feed = state.feed.copy(message = "This column changed. Reopen the correction and review the latest values."))
            return false
        }
        clinicalEditError(values, original, original.isGuideHeader)?.let { error ->
            mutableState.value = state.copy(feed = state.feed.copy(message = error)); return false
        }
        val corrected = values.entries.fold(original) { snapshot, (key, value) -> snapshot.withCell(key, value) }
        mutableState.value = state.copy(isSaving = true, feed = state.feed.copy(message = null))
        return try {
            repository.correctCells(columnId, values, reason.trim(), corrected.copy(womanDocumentId = state.patientRosterDocumentId, authorName = state.initials))
            true
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (token == selectionGeneration) mutableState.value = mutableState.value.copy(feed = mutableState.value.feed.copy(
                message = error.message ?: "Could not save this correction."
            ))
            false
        } finally { if (token == selectionGeneration) mutableState.value = mutableState.value.copy(isSaving = false) }
    }

    override fun onCleared() { repository.close() }
}

expect fun currentTimestampIso(): String
expect fun currentTimeMillis(): Long
