package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.ui.components.AccessLevelText
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgButton
import `in`.drarunbabu.drsharmila.jananisync.ui.components.AdaptiveFields
import `in`.drarunbabu.drsharmila.jananisync.ui.components.WrappingActions
import `in`.drarunbabu.drsharmila.jananisync.ui.components.LocalAppWindowSize
import `in`.drarunbabu.drsharmila.jananisync.ui.components.AppWindowSize
import `in`.drarunbabu.drsharmila.jananisync.ui.formatUiTimestamp
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.unit.Constraints
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import `in`.drarunbabu.drsharmila.jananisync.ui.components.DateTimeField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.drarunbabu.drsharmila.jananisync.data.model.cellEditHistory
import `in`.drarunbabu.drsharmila.jananisync.data.admissionColumnState
import `in`.drarunbabu.drsharmila.jananisync.data.model.AmnioticFluidStatus
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.LabourCareUiState
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.categoricalOptionsForCell
import `in`.drarunbabu.drsharmila.jananisync.data.*
import `in`.drarunbabu.drsharmila.jananisync.ui.chartTimezoneLabel
import `in`.drarunbabu.drsharmila.jananisync.ui.editableChartTime
import `in`.drarunbabu.drsharmila.jananisync.ui.parseLocalChartTime
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimestampIso
import `in`.drarunbabu.drsharmila.jananisync.data.model.guideHeaderFields
import `in`.drarunbabu.drsharmila.jananisync.data.model.partographHeaderFields
import `in`.drarunbabu.drsharmila.jananisync.data.model.deliveryOutcomeFields
import `in`.drarunbabu.drsharmila.jananisync.data.model.deliveryMethods
import `in`.drarunbabu.drsharmila.jananisync.data.model.allHeaderFields
import `in`.drarunbabu.drsharmila.jananisync.data.model.validHeaderValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private val LocalFirstStageSlots = staticCompositionLocalOf { 12 }
private val FIRST_STAGE_SLOTS: Int
    @Composable get() = LocalFirstStageSlots.current
private val LocalSecondStageSlots = staticCompositionLocalOf { 3 }
private val SECOND_STAGE_SLOTS: Int
    @Composable get() = LocalSecondStageSlots.current
private val LocalGuideReadOnly = staticCompositionLocalOf { false }
private val LocalPartograph = staticCompositionLocalOf { false }
private val TOTAL_SLOTS: Int
    @Composable get() = if (LocalPartograph.current) 12 else FIRST_STAGE_SLOTS + SECOND_STAGE_SLOTS
private val groupWidth = 42.dp
private val labelWidth = 114.dp
private val scaleWidth = 32.dp
private val thresholdWidth = 70.dp
private val timeCellWidth = 76.dp
private val plotRowHeight = 30.dp
private val historyBadgeSize = 24.dp
private val tableLine: Color
    @Composable get() = MaterialTheme.colorScheme.outlineVariant
private val tableHeader: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest
private val alertRed: Color
    @Composable get() = MaterialTheme.colorScheme.error
private val halfHourFields = lcgHalfHourlyFields

internal data class HistoryCellFocus(val columnKey: String, val slot: Int, val field: String, val level: Int = -1)
internal data class CellFocusState(
    val focused: HistoryCellFocus?,
    val select: (HistoryCellFocus) -> Unit,
    val openHistory: (HistoryCellFocus) -> Unit
)
internal val LocalCellFocus = staticCompositionLocalOf { CellFocusState(null, {}, {}) }

private fun historyFieldLabel(field: String): String {
    val base = field.removeSuffix("Half1").removeSuffix("Half2")
    val label = guideRows.firstOrNull { it.field == base }?.label?.replace("\n", " ") ?: when (base) {
        "cervicalDilation" -> "Cervical dilation"
        "descent" -> "Descent"
        "pushingStarted" -> "Pushing started"
        else -> base
    }
    return label + when {
        field.endsWith("Half1") -> " · first half-hour"
        field.endsWith("Half2") -> " · second half-hour"
        else -> ""
    }
}

private fun LabourCareObservation.focusKey(): String = columnId.ifBlank { firestoreDocumentId.ifBlank { id } }

/** Observe presses before child editors, without consuming their input or triggering saves. */
@Composable
private fun FocusableHistoryCell(
    observation: LabourCareObservation?, slot: Int, field: String, level: Int = -1,
    content: @Composable () -> Unit
) {
    val selectionColour = MaterialTheme.colorScheme.primary
    val focusState = LocalCellFocus.current
    val target = HistoryCellFocus(observation?.focusKey() ?: "draft:$slot", slot, field, level)
    val isFocused = focusState.focused?.let { it.columnKey == target.columnKey && it.field == field && it.level == level } == true
    val selectTarget by rememberUpdatedState { focusState.select(target) }
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    val historyLabel = "View ${historyFieldLabel(field)} edit history, ${slotName(slot)}"
    Box(Modifier
        .testTag("lcg:$slot:$field:$level")
        .hoverable(hoverSource)
        .pointerInput(target) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var moved = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                    if ((pointer.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                    if (!pointer.pressed) {
                        if (!moved) selectTarget()
                        break
                    }
                } while (true)
            }
        }
        .onFocusChanged { if (it.hasFocus) selectTarget() }
        .semantics {
            selected = isFocused
            onClick(label = "Focus cell for edit history") { selectTarget(); true }
        }
        .drawWithContent {
            drawContent()
            if (isFocused) {
                val width = 2.dp.toPx()
                drawRect(selectionColour, topLeft = Offset(width / 2, width / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - width, size.height - width), style = Stroke(width))
            }
        }
    ) {
        key(observation?.sessionId, observation?.timestamp, slot, field, level) { content() }
        // The badge overlays the cell without changing value or alert layout.
        if (hovered || isFocused) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp)) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text(historyLabel) } },
                    state = rememberTooltipState()
                ) {
                    Box(
                        Modifier.size(historyBadgeSize)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                            .clickable(role = Role.Button, onClickLabel = historyLabel) { focusState.openHistory(target) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = historyLabel,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

private data class GuideRow(val group: String, val label: String, val alert: String, val field: String? = null, val height: Dp = 42.dp)

private fun sectionGroups(rows: List<GuideRow>): List<List<GuideRow>> {
    val sections = mutableListOf<MutableList<GuideRow>>()
    rows.forEach { row ->
        if (row.group.isNotBlank() || sections.isEmpty()) sections.add(mutableListOf())
        sections.last().add(row)
    }
    return sections
}

private val guideRows = listOf(
    GuideRow("SUPPORT", "Companion", "N", "companion"),
    GuideRow("", "Pain relief", "N", "painRelief"),
    GuideRow("", "Oral fluid", "N", "oralFluid"),
    GuideRow("", "Posture", "SP", "posture"),
    GuideRow("BABY", "Baseline FHR", "<110, ≥160", "fetalHeartRate"),
    GuideRow("", "FHR deceleration", "L", "fhrDeceleration"),
    GuideRow("", "Amniotic fluid", "M+++, B", "amnioticFluid"),
    GuideRow("", "Fetal position", "P, T", "fetalPosition"),
    GuideRow("", "Caput", "+++", "caput"),
    GuideRow("", "Moulding", "+++", "moulding"),
    GuideRow("WOMAN", "Pulse", "<60, ≥120", "pulse"),
    GuideRow("", "Systolic BP", "<80, ≥140", "systolic"),
    GuideRow("", "Diastolic BP", "≥90", "diastolic"),
    GuideRow("", "Temperature °C", "<35, ≥37.5", "temperature"),
    GuideRow("", "Urine protein", "P++, A++", "urineProtein"),
    GuideRow("", "Urine acetone", "P++, A++", "urineAcetone"),
    GuideRow("LABOUR", "Contractions / 10 min", "≤2, >5", "contractionFrequency"),
    GuideRow("", "Duration (sec)", "<20, >60", "contractionDuration"),
    GuideRow("MEDICATION", "Oxytocin\n(U/L · drops/min)", "", "oxytocin", 56.dp),
    GuideRow("", "Medicine", "", "medicine", 76.dp),
    GuideRow("", "IV fluids", "", "intravenousFluids", 52.dp),
    GuideRow("SHARED DECISION\nMAKING", "Assessment", "", "assessment", 132.dp),
    GuideRow("", "Plan", "Shared decision", "plan", 104.dp),
    GuideRow("", "Initials", "", "initials")
)


private val partographRows = listOf(
    GuideRow("DETAILS", "Gravida", "", "gravida"),
    GuideRow("", "Membranes ruptured (hours)", "", "rupturedHours"),
    GuideRow("BABY", "Fetal heart rate", "", "fetalHeartRate"),
    GuideRow("", "Amniotic fluid", "", "amnioticFluid"),
    GuideRow("", "Moulding", "", "moulding"),
    GuideRow("LABOUR", "Cervix (cm) · X", "", "cervicalDilation"),
    GuideRow("", "Descent · O", "", "descent"),
    GuideRow("", "Contractions / 10 min", "", "contractionFrequency"),
    GuideRow("", "Duration (seconds)", "", "contractionDuration"),
    GuideRow("MEDICATION", "Oxytocin U/L · drops/min", "", "oxytocin", 56.dp),
    GuideRow("", "Drugs given", "", "medicine", 76.dp),
    GuideRow("", "IV fluids", "", "intravenousFluids", 52.dp),
    GuideRow("WOMAN", "Pulse", "", "pulse"),
    GuideRow("", "Systolic BP", "", "systolic"),
    GuideRow("", "Diastolic BP", "", "diastolic"),
    GuideRow("", "Temperature °C", "", "temperature"),
    GuideRow("URINE", "Protein", "", "urineProtein"),
    GuideRow("", "Acetone", "", "urineAcetone"),
    GuideRow("", "Volume (ml)", "", "urineVolume"),
    GuideRow("", "Initials", "", "initials")
)

@Composable
fun ObservationsHostScreen(
    state: LabourCareUiState,
    onUnlockOrganizationKey: (String, Boolean) -> Unit,
    isUnlockingOrganizationKey: Boolean,
    onCellSave: (String, String, String) -> Unit,
    onConfirmColumn: (String) -> Unit,
    onConfirmFullDilation: (String) -> Unit,
    onConfirmBirth: (String) -> Unit,
    onPartographSave: suspend (String, String, Map<String, String>) -> Boolean,
    signedInUserId: String,
    onCorrection: suspend (String, Int, Map<String, String>, String) -> Boolean,
    accessLevel: Int,
    onSessionChange: (String, String) -> Unit,
    onCellChange: (String, String) -> Unit,
    patientLabelReadOnly: Boolean = false,
    readOnly: Boolean = state.patientArchived,
    partograph: Boolean = false,
    hospitalNumber: String = "",
    admissionTime: String = ""
) {
    val horizontalScroll = rememberScrollState()
    var recoveryPhrase by remember(state.orgId) { mutableStateOf("") }
    var correctionTarget by remember(state.orgId) { mutableStateOf<LabourCareObservation?>(null) }
    var showHistory by remember(signedInUserId, state.orgId, state.patientSessionId, state.organizationKeyAvailable) { mutableStateOf(false) }
    var correctionField by remember(state.orgId) { mutableStateOf<String?>(null) }
    LaunchedEffect(state.organizationKeyAvailable) {
        if (state.organizationKeyAvailable) recoveryPhrase = ""
    }
    var nowIso by remember { mutableStateOf(currentTimestampIso()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30.seconds)
            nowIso = currentTimestampIso()
        }
    }
    val patientSessionId = state.patientSessionId.ifBlank { "${state.orgId}-${state.patientLabel}" }
    LaunchedEffect(state.orgId, patientSessionId) {
        correctionTarget = null
        correctionField = null
    }
    val sessionObservations = state.feed.observations
        .filter { it.sessionId == patientSessionId && !it.isGuideHeader }
        .sortedBy { runCatching { kotlin.time.Instant.parse(it.timestamp).toEpochMilliseconds() }.getOrDefault(Long.MAX_VALUE) }
    val firstStageSlots = if (partograph) 12 else firstStageSlotCount(sessionObservations)
    var requestedSecondStageSlots by rememberSaveable(state.orgId, patientSessionId, signedInUserId) {
        mutableStateOf(3)
    }
    val timelineStart = chartStart(sessionObservations) ?: chartMillis(state.chartStartTimestamp)
    val timelineObservations = if (partograph) sessionObservations else if (timelineStart != null && state.chartStartConfirmed && sessionObservations.all { it.chartStartTimestamp.isNotBlank() })
        scheduledObservations(sessionObservations, timelineStart, maxOf(firstStageSlots + requestedSecondStageSlots, (sessionObservations.maxOfOrNull { it.chartColumnIndex } ?: -1) + 2), patientSessionId, state.patientLabel).withSecondStageGap(firstStageSlots)
        else sessionObservations.withSecondStageGap(firstStageSlots)
    val observations = timelineObservations.mapIndexed { index, observation ->
        if (!partograph && index >= firstStageSlots && sessionObservations.none { it.fullDilationConfirmed })
            observation.copy(stageEntryLocked = true) else observation
    }
    val header = state.feed.observations.firstOrNull { it.sessionId == patientSessionId && it.isGuideHeader }
    var headerHistoryField by remember(patientSessionId) { mutableStateOf<String?>(null) }
    headerHistoryField?.let { field ->
        CellHistoryDialog(state, listOfNotNull(header), HistoryCellFocus(header?.focusKey() ?: "header", 0, field, 0),
            partograph, onDismiss = { headerHistoryField = null },
            canCorrect = { _, _ -> false },
            onCorrect = { column, key -> correctionTarget = column; correctionField = key; headerHistoryField = null })
    }
    val columnState = state.feed.admissionColumnState(patientSessionId, state.patientRosterDocumentId)
    val birthRecorded = sessionObservations.any { it.birthConfirmed }
    val secondStageSlots = maxOf(3, requestedSecondStageSlots, observations.size - firstStageSlots)
    val canAddSecondStageColumn = !readOnly && !partograph && !birthRecorded &&
        sessionObservations.any { it.fullDilationConfirmed } &&
        observations.size >= firstStageSlots + secondStageSlots && columnState.canStartColumn
    val draftSlot = if (observations.any { it.cellKey == "scheduled" }) -1 else if (!readOnly && !birthRecorded && (partograph || observations.size < firstStageSlots || sessionObservations.any { it.fullDilationConfirmed }) && observations.size < (if (partograph) 12 else firstStageSlots + secondStageSlots) && columnState.canStartColumn) observations.size else -1

    // Legacy charts expose their next column without a stored observation yet.
    // Bind its save target now, rather than asking saveCell to reuse an open column.
    val draftColumnId = guideDraftColumnId(sessionObservations, timelineStart ?: chartMillis(state.chartStartTimestamp) ?: 0L)
    val saveGuideCell: (String, String, String) -> Unit = { columnId, field, value ->
        onCellSave(columnId.ifBlank { draftColumnId }, field, value)
    }

    var pendingConfirmation by remember(state.orgId, patientSessionId, signedInUserId, partograph) {
        mutableStateOf<Pair<String, GuideConfirmation>?>(null)
    }
    LaunchedEffect(readOnly) {
        if (readOnly) {
            pendingConfirmation = null
            correctionTarget = null
            correctionField = null
        }
    }
    val requestColumnConfirmation: (String) -> Unit = { pendingConfirmation = it to GuideConfirmation.Column }
    val requestFullDilationConfirmation: (String) -> Unit = { pendingConfirmation = it to GuideConfirmation.FullDilation }
    val requestBirthConfirmation: (String) -> Unit = { pendingConfirmation = it to GuideConfirmation.Birth }
    pendingConfirmation?.let { (columnId, action) ->
        val observation = sessionObservations.firstOrNull { it.columnId == columnId }
        val control = state.feed.columnControls.firstOrNull { it.columnId == columnId }
        val canConfirm = !readOnly && observation != null && control != null && (control.canSignOff(accessLevel) || action != GuideConfirmation.Column) &&
            state.organizationKeyAvailable && !state.isSaving && !state.feed.isLoading &&
            accessLevel in 10..99
        GuideConfirmationDialog(
            action = action,
            patient = state.patientLabel,
            timestamp = observation?.timestamp.orEmpty(),
            enabled = canConfirm,
            onDismiss = { pendingConfirmation = null },
            onConfirm = {
                if (canConfirm) {
                    pendingConfirmation = null
                    when (action) {
                        GuideConfirmation.Column -> onConfirmColumn(columnId)
                        GuideConfirmation.FullDilation -> onConfirmFullDilation(columnId)
                        GuideConfirmation.Birth -> onConfirmBirth(columnId)
                    }
                }
            }
        )
    }

    // Keep separate selections when navigating between patients or organizations in this UI session.
    val selections = rememberSaveable(signedInUserId, saver = mapSaver<MutableMap<String, HistoryCellFocus>>(
        save = { entries -> entries.mapValues { (_, cell) -> listOf(cell.columnKey, cell.slot.toString(), cell.field, cell.level.toString()) } },
        restore = { saved -> mutableStateMapOf<String, HistoryCellFocus>().apply {
            saved.forEach { (key, value) ->
                val parts = value as List<*>
                this[key] = HistoryCellFocus(parts[0] as String, (parts[1] as String).toInt(), parts[2] as String, (parts[3] as String).toInt())
            }
        } }
    )) { mutableStateMapOf() }
    val selectionKey = "${state.orgId}:$patientSessionId"
    val focused = selections[selectionKey]
    // A draft acquires its stable column ID on its first save.
    LaunchedEffect(observations.map { it.focusKey() }, focused) {
        if (focused?.columnKey?.startsWith("draft:") == true) {
            observations.getOrNull(focused.slot)?.let { selections[selectionKey] = focused.copy(columnKey = it.focusKey()) }
        }
    }
    if (showHistory && state.organizationKeyAvailable) {
        CellHistoryDialog(state, observations, focused, partograph, onDismiss = { showHistory = false },
            canCorrect = { column, field -> !readOnly && state.feed.columnControls.firstOrNull { it.columnId == column.columnId }?.canCorrectCell(field, accessLevel) == true },
            onCorrect = { column, field -> correctionTarget = column; correctionField = field; showHistory = false })
    }

    CompositionLocalProvider(LocalGuideReadOnly provides (readOnly || !state.chartStartConfirmed), LocalFirstStageSlots provides firstStageSlots, LocalSecondStageSlots provides secondStageSlots, LocalPartograph provides partograph, LocalCellFocus provides CellFocusState(
        focused = focused,
        select = { cell -> selections[selectionKey] = cell; onSessionChange("activeColumnId", cell.columnKey) },
        openHistory = { cell ->
            selections[selectionKey] = cell
            showHistory = true
        }
    )) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (partograph) "MODIFIED WHO PARTOGRAPH" else "WHO LABOUR CARE GUIDE",
                style = MaterialTheme.typography.titleLarge
            )
            ChartExportActions(state, observations, header, partograph, firstStageSlots)
            var showStartWarning by remember(patientSessionId) { mutableStateOf(false) }
            val waitingForColumns = sessionObservations.isEmpty() && state.feed.columnControls.any {
                it.womanDocumentId == state.patientRosterDocumentId && !it.columnId.startsWith("header-")
            }
            if (waitingForColumns) Text("Loading existing chart times…", style = MaterialTheme.typography.bodySmall)
            else if (sessionObservations.isEmpty()) {
                val selectedStart = chartMillis(state.chartStartTimestamp)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chart start time", style = MaterialTheme.typography.titleMedium)
                        DateTimeField(value = selectedStart?.let(::editableChartTime).orEmpty(),
                            onValueChange = { text -> parseLocalChartTime(text)?.let { onSessionChange("chartStartTimestamp", kotlin.time.Instant.fromEpochMilliseconds(it).toString()) } },
                            label = "Start date and time (${selectedStart?.let(::chartTimezoneLabel).orEmpty()})", enabled = !readOnly && !state.isSaving)
                        if (selectedStart != null && selectedStart > `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis())
                            Text("Choose the current time or a past time.", color = MaterialTheme.colorScheme.error)
                        WrappingActions {
                            TextButton(enabled = !readOnly, onClick = { onSessionChange("chartStartTimestamp", currentTimestampIso()) }) { Text("Use current time") }
                            Button(enabled = !readOnly && selectedStart != null && selectedStart <= `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis() && !state.chartStartConfirmed,
                                onClick = { showStartWarning = true }) { Text("Set chart start") }
                        }
                        Text(if (state.chartStartConfirmed) "Chart start selected. Save the first observation to establish it permanently."
                            else "Choose a start time before recording. The same hourly schedule is used in LCG and partograph.")
                    }
                }
            } else Text("Chart start: ${timelineStart?.let { formatUiTimestamp(it) }.orEmpty()} · shared by LCG and partograph", style = MaterialTheme.typography.bodySmall)
            if (showStartWarning) AlertDialog(
                onDismissRequest = { showStartWarning = false },
                title = { Text("Set up this chart?") },
                text = { Text("The chart will be set up according to ${formatUiTimestamp(state.chartStartTimestamp)} (${chartMillis(state.chartStartTimestamp)?.let(::chartTimezoneLabel).orEmpty()}). Every following column represents one hour, with half-hour readings where applicable, in both LCG and partograph. After the first observation is saved this start time cannot be changed here. Check the date and time, particularly for a previous delivery.") },
                confirmButton = { Button(onClick = { onSessionChange("chartStartConfirmed", "true"); showStartWarning = false }) { Text("Use this start time") } },
                dismissButton = { TextButton(onClick = { showStartWarning = false }) { Text("Back") } })
            val headerFields: @Composable () -> Unit = {
                (if (partograph) partographHeaderFields else guideHeaderFields).chunked(2).forEach { fields ->
                    AdaptiveFields {
                        fields.forEach { (field, label) ->
                            val fallback = when (field) {
                                "patientLabel" -> state.patientLabel
                                "parity" -> state.parity
                                "labourOnset" -> state.labourOnset
                                "activeLabourDiagnosis" -> state.activeLabourDiagnosis
                                "rupturedMembranes" -> state.rupturedMembranes
                                "riskFactors" -> state.riskFactors
                                else -> ""
                            }
                            HeaderCell(patientSessionId, field, label, header?.historyValue(field) ?: fallback,
                                enabled = !readOnly && state.organizationKeyAvailable && !state.feed.isLoading,
                                modifier = Modifier.weight(1f),
                                onSave = { onCellSave(header?.columnId.orEmpty(), field, it) },
                                onHistory = { headerHistoryField = field },
                                readOnly = field == "patientLabel" && patientLabelReadOnly)
                        }
                    }
                }
            }
            if (partograph) Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdaptiveFields {
                        Column(Modifier.weight(1f)) {
                            Text("Hospital number", style = MaterialTheme.typography.labelMedium)
                            Text(hospitalNumber.ifBlank { "Not recorded" })
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Date of admission", style = MaterialTheme.typography.labelMedium)
                            Text(admissionTime.substringBeforeLast(' ', admissionTime).ifBlank { "Not recorded" })
                        }
                        Column(Modifier.weight(1f)) {
                            Text("Time of admission", style = MaterialTheme.typography.labelMedium)
                            Text(admissionTime.substringAfterLast(' ', admissionTime).ifBlank { "Not recorded" })
                        }
                    }
                    headerFields()
                    val obstetric = listOf(
                        "gravida" to "G",
                        "parity" to "P",
                        "livingChildren" to "L"
                    ).joinToString(" ") { (field, prefix) ->
                        val value = header?.historyValue(field) ?: if (field == "parity") state.parity else ""
                        "$prefix${value.ifBlank { "—" }}"
                    }
                    val weeks = header?.historyValue("gestationalWeeks").orEmpty()
                    val days = header?.historyValue("gestationalDays").orEmpty()
                    Text("$obstetric · POG ${weeks.ifBlank { "—" }}+${days.ifBlank { "—" }} weeks", style = MaterialTheme.typography.bodyMedium)
                    val recordedDetails = listOf("gravida" to "Gravida", "rupturedHours" to "Membranes ruptured (hours)")
                    recordedDetails.forEach { (field, label) ->
                        val source = sessionObservations.lastOrNull { field in it.partographCells || field in it.recordedCellKeys }
                        source?.takeIf { header?.partographCells?.containsKey(field) != true }?.let {
                            Text("Earlier observation · $label: ${it.partographText(field).ifBlank { "Not recorded" }} · recorded ${formatUiTimestamp(it.timestamp)}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else headerFields()
            if (!state.organizationKeyAvailable) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Unlock clinical data on this device", style = MaterialTheme.typography.titleSmall)
                        Text("Enter this doctor's personal 12-word recovery phrase, saved when their account key was created. Do not enter the admin's phrase or an organisation key. The phrase unlocks this device's private key and is never sent to Firestore.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = recoveryPhrase,
                            onValueChange = { recoveryPhrase = it.take(512) },
                            label = { Text("12-word recovery phrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Text("Access is remembered on this device. Use only on a secured personal or work device and log out when finished.", style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { onUnlockOrganizationKey(recoveryPhrase, true) },
                            enabled = recoveryPhrase.isNotBlank() && !isUnlockingOrganizationKey
                        ) { Text(if (isUnlockingOrganizationKey) "Unlocking…" else "Unlock organisation key") }
                    }
                }
            } else {
                Text("Organisation encryption unlocked on this device", style = MaterialTheme.typography.labelMedium)
            }
            TextButton(onClick = { showHistory = true }, enabled = state.organizationKeyAvailable && (observations.isNotEmpty() || focused != null)) {
                Text("Cell edit history")
            }

            AdaptiveFields {
            Text(if (partograph) "12-hour chart · half-hour FHR and contractions" else "ALERT  ·  TIME  ·  first stage 1–$firstStageSlots h  ·  second stage 1–$secondStageSlots h", style = MaterialTheme.typography.labelMedium)
                Text(if (readOnly) "Read only · history available" else if (state.isSaving) "Saving cell…" else if (partograph) "Tap to preview · review and confirm to save" else "Cells save when you finish editing them")
            }

            if (canAddSecondStageColumn) {
                MgButton(
                    text = "Add second-stage column",
                    enabled = !readOnly && state.organizationKeyAvailable && !state.isSaving && !state.feed.isLoading,
                    onClick = { requestedSecondStageSlots = secondStageSlots + 1 }
                )
            }

            if (partograph && state.organizationKeyAvailable) key(state.orgId, patientSessionId, signedInUserId, readOnly) {
                PartographCharts(observations, state.feed.columnControls, columnState, accessLevel,
                    readOnly || !state.chartStartConfirmed || state.isSaving || state.feed.isLoading, onPartographSave, requestColumnConfirmation,
                    onCorrectColumn = { observation, field -> correctionTarget = observation; correctionField = field },
                    womanDocumentId = state.patientRosterDocumentId, chartStartTimestamp = state.chartStartTimestamp, activeColumnId = state.activeColumnId,
                    onSelectColumn = { onSessionChange("activeColumnId", it) }, nowMillis = chartMillis(nowIso) ?: 0L,
                    onFullDilation = requestFullDilationConfirmation, onBirth = requestBirthConfirmation)
            }

            if (!partograph && LocalAppWindowSize.current == AppWindowSize.Compact) {
                CompactCareGuide(state, observations, draftSlot, accessLevel,
                    saveGuideCell, requestColumnConfirmation, requestFullDilationConfirmation, requestBirthConfirmation, { slot, field ->
                        selections[selectionKey] = HistoryCellFocus(observations.getOrNull(slot)?.focusKey() ?: "draft:$slot", slot, field)
                        showHistory = true
                    }, onCorrectColumn = { correctionTarget = it; correctionField = null },
                    onSelectColumn = { onSessionChange("activeColumnId", it) }, nowMillis = chartMillis(nowIso) ?: 0L)
            }
            if (!partograph && LocalAppWindowSize.current != AppWindowSize.Compact) Card(shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(bottom = 72.dp)) {
                    stickyHeader(key = "chart-header") {
                        Column(Modifier.fillMaxWidth().background(tableHeader)) {
                        GridHeaderRow(horizontalScroll, phaseHeader = true, observations = observations)
                        GridHeaderRow(
                            horizontalScroll,
                            phaseHeader = false,
                            observations = observations,
                            draftSlot = draftSlot,
                            columnControls = state.feed.columnControls,
                            accessLevel = accessLevel,
                            correctionEnabled = !readOnly && state.organizationKeyAvailable && !state.isSaving,
                            onCorrectColumn = { correctionTarget = it; correctionField = null }
                        )
                        }
                    }
                    items(sectionGroups(if (partograph) partographRows else guideRows.take(18)), key = { "upper-${it.first().group}" }) { rows ->
                        Box {
                        Row(Modifier.fillMaxWidth()) {
                            FixedCell(rows.first().group, groupWidth,
                                height = rows.fold(0.dp) { total, row -> total + row.height },
                                background = MaterialTheme.colorScheme.surfaceContainerHigh, bold = true)
                            Column(Modifier.weight(1f)) {
                            rows.forEach { row ->
                        GridObservationRow(
                            row = row,
                            observations = observations,
                            draftSlot = draftSlot,
                            nowIso = nowIso,
                            state = state,
                            horizontalScroll = horizontalScroll,
                            onCellChange = onCellChange,
                            onCellSave = saveGuideCell,
                            columnControls = state.feed.columnControls,
                            accessLevel = accessLevel,
                            //signedInUserId = signedInUserId,
                            isSaving = state.isSaving || readOnly,
                            includeSection = false
                        )
                            }
                            }
                        }
                        if (!partograph && rows.any { it.field == "companion" }) {
                            val height = rows.fold(0.dp) { total, row -> total + row.height }
                            val scrollOffset = with(LocalDensity.current) { horizontalScroll.value.toDp() }
                            // Only birth markers receive input; empty columns must let touches reach their editors.
                            Box(Modifier.padding(start = groupWidth + labelWidth + scaleWidth + thresholdWidth)
                                .fillMaxWidth().height(height).clipToBounds()) {
                                observations.forEachIndexed { slot, observation ->
                                    if (slot >= FIRST_STAGE_SLOTS && observation.birthConfirmed) {
                                        val birthHistory = LocalCellFocus.current
                                        Box(Modifier.offset(x = timeCellWidth * slot.toFloat() - scrollOffset)) {
                                            FocusableHistoryCell(observation, slot, "birthConfirmed") {
                                                Box(Modifier.width(timeCellWidth).height(height)
                                                    .background(MaterialTheme.colorScheme.surface)
                                                    .clickable { birthHistory.openHistory(HistoryCellFocus(observation.focusKey(), slot, "birthConfirmed")) }
                                                    .border(0.5.dp, tableLine), contentAlignment = Alignment.Center) {
                                                    Text("BIRTH", modifier = Modifier.rotate(-90f),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        style = MaterialTheme.typography.titleLarge)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                    if (!partograph) item(key = "labour-progress") {
                        LabourProgressPlotGroup(
                        observations = observations,
                        draftSlot = draftSlot,
                        nowIso = nowIso,
                        state = state,
                        horizontalScroll = horizontalScroll,
                            onCellSave = saveGuideCell,
                        columnControls = state.feed.columnControls,
                        accessLevel = accessLevel,
                        isSaving = state.isSaving || readOnly
                        )
                    }
                    items(sectionGroups(if (partograph) emptyList() else guideRows.drop(18)), key = { "lower-${it.first().group}" }) { rows ->
                        Row(Modifier.fillMaxWidth()) {
                            FixedCell(rows.first().group, groupWidth,
                                height = rows.fold(0.dp) { total, row -> total + row.height },
                                background = MaterialTheme.colorScheme.surfaceContainerHigh, bold = true)
                            Column(Modifier.weight(1f)) {
                            rows.forEach { row ->
                        GridObservationRow(
                            row = row,
                            observations = observations,
                            draftSlot = draftSlot,
                            nowIso = nowIso,
                            state = state,
                            horizontalScroll = horizontalScroll,
                            onCellChange = onCellChange,
                            onCellSave = saveGuideCell,
                            columnControls = state.feed.columnControls,
                            accessLevel = accessLevel,
                            isSaving = state.isSaving || readOnly,
                            includeSection = false
                        )
                            }
                            }
                        }
                    }
                    item(key = "column-confirmations") {
                        ColumnConfirmRow(
                            observations = observations,
                            controls = state.feed.columnControls,
                            accessLevel = accessLevel,
                            onConfirm = requestColumnConfirmation,
                            isSaving = state.isSaving || readOnly,
                            horizontalScroll = horizontalScroll
                        )
                    }
                }
                FloatingStageAction(
                    observations, state.feed.columnControls, accessLevel,
                    readOnly || state.isSaving || state.feed.isLoading || !state.organizationKeyAvailable,
                    requestFullDilationConfirmation, requestBirthConfirmation,
                    Modifier.align(Alignment.BottomEnd).padding(12.dp)
                )
                }
            }
            if (!partograph) {
                val transitionSlot = observations.indexOfFirst { it.fullDilationConfirmed }
                observations.getOrNull(transitionSlot)?.let { transition ->
                    MgButton(
                        text = "Skipped to second stage · ${formatUiTimestamp(transition.timestamp)} · View history",
                        onClick = {
                            selections[selectionKey] = HistoryCellFocus(transition.focusKey(), transitionSlot, "fullDilationConfirmed")
                            showHistory = true
                        }
                    )
                }
            }
            if (!partograph && LocalAppWindowSize.current != AppWindowSize.Compact) Text( "In the NOW · EDIT column, tap a cervical level to plot X and a descent level to plot O.", style = MaterialTheme.typography.bodySmall)
            Text(
                when {
                    readOnly -> "This admission is read only. Clinical records and history remain available."
                    state.feed.isLoading -> "Loading encrypted observation grid…"
                    state.feed.hasPendingWrites -> "Saved in this browser; waiting for a connection to sync."
                    birthRecorded -> "Birth recorded. Labour observations are complete."
                    partograph -> columnState.creationBlockMessage ?: "${observations.size} recorded time points. Save marks in one time column, then sign off the column when its observations are complete."
                    draftSlot < 0 -> columnState.creationBlockMessage ?: if (canAddSecondStageColumn) "Add a second-stage column to continue recording observations." else "Select an eligible time column. Future observations are unavailable."
                    else -> "${observations.size} encrypted column(s). Each cell is saved separately; sign off a column when it is complete."
                }, style = MaterialTheme.typography.bodySmall
            )
            state.feed.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delivery outcome and baby details", style = MaterialTheme.typography.titleMedium)
                    deliveryOutcomeFields.filter { (field, _) ->
                        field != "lscsIndication" || header?.historyValue("deliveryMethod") == "LSCS"
                    }.chunked(2).forEach { fields ->
                        AdaptiveFields {
                            fields.forEach { (field, label) ->
                                HeaderCell(patientSessionId, field, label, header?.historyValue(field).orEmpty(),
                                    enabled = !readOnly && state.organizationKeyAvailable && !state.feed.isLoading,
                                    modifier = Modifier.weight(1f),
                                    onSave = { onCellSave(header?.columnId.orEmpty(), field, it) },
                                    onHistory = { headerHistoryField = field })
                            }
                        }
                    }
                    if (header?.historyValue("deliveryMethod") == "LSCS" && header.historyValue("lscsIndication").isBlank()) {
                        Text("Record the indication for LSCS.", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                ChartExportActions(state, observations, header, partograph, firstStageSlots)
            }
        }
    }
    correctionTarget?.takeUnless { readOnly }?.let { original ->
        ConfirmedCellCorrectionDialog(state, original, correctionField, accessLevel, onCorrection, partograph = partograph,
            onDismiss = { correctionTarget = null; correctionField = null })
    }
}

@Composable
private fun GridHeaderRow(
    horizontalScroll: ScrollState,
    phaseHeader: Boolean,
    observations: List<LabourCareObservation>,
    draftSlot: Int = -1,
    columnControls: List<`in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl> = emptyList(),
    accessLevel: Int = 0,
    correctionEnabled: Boolean = false,
    onCorrectColumn: (LabourCareObservation) -> Unit = {}
) {
    Row(Modifier.fillMaxWidth().background(tableHeader)) {
        FixedCell(if (phaseHeader) "" else "SECTION", groupWidth, header = true, height = if (phaseHeader) 28.dp else 70.dp)
        // The level column is blank for ordinary observations, so let the
        // observation heading span that space. Plot rows still show their
        // clinically meaningful level values in the same aligned width.
        FixedCell(if (phaseHeader) "" else "OBSERVATION", labelWidth + scaleWidth, header = true, height = if (phaseHeader) 28.dp else 70.dp)
        FixedCell(if (phaseHeader) "" else "ALERT", thresholdWidth, header = true, height = if (phaseHeader) 28.dp else 70.dp)
        Row(Modifier.weight(1f).horizontalScroll(horizontalScroll)) {
            if (phaseHeader) {
                GridCell(if (LocalPartograph.current) "MODIFIED WHO PARTOGRAPH · HOURS" else "ACTIVE FIRST STAGE", width = timeCellWidth * FIRST_STAGE_SLOTS.toFloat(), height = 28.dp, header = true)
                if (!LocalPartograph.current) GridCell("SECOND STAGE", width = timeCellWidth * SECOND_STAGE_SLOTS.toFloat(), height = 28.dp, header = true)
            } else repeat(TOTAL_SLOTS) { index ->
                val title = when {
                    observations.getOrNull(index)?.cellKey == "scheduled" -> "${slotName(index)}\n${observations[index].timestamp.timeLabel()}\n${if (chartCellAvailable(observations[index], "pulse")) "EMPTY · EDIT" else "FUTURE"}"
                    index == draftSlot -> "NEXT · EDIT"
                    index < observations.size && observations[index].cellKey != "skipped" -> {
                        val observation = observations[index]
                        val control = columnControls.firstOrNull { it.columnId == observation.columnId }
                        if (control != null && !control.confirmed) {
                            "${slotName(index)}\n${observation.timestamp.timeLabel()}\nOPEN · EDIT"
                        } else "${slotName(index)}\n${observation.timestamp.timeLabel()}"
                    }
                    else -> slotName(index)
                }
                Box(Modifier.width(timeCellWidth).height(70.dp)) {
                    GridCell(title, width = timeCellWidth, height = 70.dp, header = true)
                    val observation = observations.getOrNull(index)
                    val control = columnControls.firstOrNull { it.columnId == observation?.columnId }
                    if (observation != null && control?.canCorrect(accessLevel) == true) {
                        IconButton(onClick = { onCorrectColumn(observation) }, enabled = correctionEnabled,
                            modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Correct confirmed column ${slotName(index)}", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GridObservationRow(
    row: GuideRow,
    observations: List<LabourCareObservation>,
    draftSlot: Int,
    nowIso: String,
    state: LabourCareUiState,
    horizontalScroll: ScrollState,
    onCellChange: (String, String) -> Unit,
    onCellSave: (String, String, String) -> Unit,
    columnControls: List<`in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl>,
    accessLevel: Int,
    isSaving: Boolean,
    includeSection: Boolean = true
) {
    val readOnly = LocalGuideReadOnly.current
    val sectionColor = if (row.group.isNotBlank()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
    Row(Modifier.fillMaxWidth()) {
        if (includeSection) FixedCell(row.group, groupWidth, height = row.height, background = sectionColor, bold = row.group.isNotBlank())
        FixedCell(row.label, labelWidth + scaleWidth, height = row.height, bold = true)
        FixedCell(if (LocalPartograph.current) "" else row.alert, thresholdWidth, height = row.height, background = MaterialTheme.colorScheme.surfaceContainerLow, fontSize = 10.sp)
        Row(Modifier.weight(1f).horizontalScroll(horizontalScroll)) {
            repeat(TOTAL_SLOTS) { index ->
                val existing = observations.getOrNull(index)
                val isDraft = existing?.cellKey == "scheduled" || (index == draftSlot && observations.size < TOTAL_SLOTS)
                val control = existing?.columnId?.let { id -> columnControls.firstOrNull { it.columnId == id } }
                val hasEditAccess = control?.canEditCell(row.field.orEmpty(), accessLevel) == true
                // Keep the editor composed while a save or key refresh temporarily
                // disables input. Swapping it for GridCell discards its local draft.
                val canEdit = !readOnly && (isDraft || hasEditAccess) && chartCellAvailable(existing, row.field.orEmpty())
                val editable = canEdit && !isSaving && state.organizationKeyAvailable
                val value = when {
                    isDraft -> if (existing?.cellKey == "scheduled") "" else state.valueFor(row.field)
                    else -> existing?.displayValueFor(row.field).orEmpty()
                }
                val alert = !LocalPartograph.current && if (row.field == "cervicalDilation") {
                    if (isDraft) cervicalDelayAlert(state.cervicalDilation, observations, nowIso)
                    else existing != null && cervicalDelayAlert(
                        value,
                        observations.take(index + 1),
                        existing.timestamp
                    )
                } else isAlertValue(row.field, value)
                if (row.field != null && row.field != "initials" && row.field in halfHourFields) {
                    HalfHourGridCell(
                            field = row.field,
                            observation = existing,
                            slot = index,
                            draftValue = if (isDraft && existing?.cellKey != "scheduled") state.valueFor(row.field) else "",
                            height = row.height,
                            onValueChange = { },
                            onCommit = { key, newValue -> onCellSave(existing?.columnId.orEmpty(), key, newValue) },
                            hint = cellHint(row.field),
                            editable = { key -> !readOnly && (isDraft || control?.canEditCell(key, accessLevel) == true) && chartCellAvailable(existing, key) },
                            enabled = !isSaving && state.organizationKeyAvailable
                    )
                } else {
                    FocusableHistoryCell(existing, index, row.field.orEmpty()) {
                        if (canEdit && row.field != null && row.field != "initials") {
                            EditableGridCell(
                                row.field, value, { onCellChange(row.field, it) },
                                { newValue -> onCellSave(existing?.columnId.orEmpty(), row.field, newValue) },
                                cellHint(row.field), row.height, alert, enabled = !isSaving && state.organizationKeyAvailable
                            )
                        } else {
                            val shown = if (row.field == "initials" && existing != null && existing.cellKey != "skipped" && control?.confirmed != true) "Awaiting sign-off" else value
                            GridCell(shown, height = row.height, background = if (editable) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface, alert = alert)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HalfHourGridCell(
    field: String,
    observation: LabourCareObservation?,
    slot: Int,
    draftValue: String,
    height: Dp,
    onValueChange: (String) -> Unit,
    onCommit: (String, String) -> Unit,
    hint: String,
    editable: (String) -> Boolean,
    enabled: Boolean,
    width: Dp = timeCellWidth
) {
    val gridLine = tableLine
    val gridSurface = MaterialTheme.colorScheme.surface
    val appearanceScale = `in`.drarunbabu.drsharmila.jananisync.ui.theme.LocalAppearanceSettings.current.scale
    Row(
        Modifier.width(width).height(height).drawWithContent {
            drawContent()
            drawLine(
                color = gridSurface,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 2f * appearanceScale
            )
            drawLine(
                color = gridLine,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1f * appearanceScale,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * appearanceScale, 3f * appearanceScale))
            )
        }
    ) {
        repeat(2) { half ->
            val key = "${field}Half${half + 1}"
            val value = observation?.let { saved ->
                saved.halfHourCells[key] ?: if (
                    half == 0 && (field in saved.recordedCellKeys ||
                        (saved.recordedCellKeys.isEmpty() && (saved.cellKey == field || (saved.cellKey.isBlank() && saved.columnId.isBlank()))))
                ) saved.valueFor(field) else ""
            } ?: if (half == 0) draftValue else ""
            FocusableHistoryCell(observation, slot, key) {
                if (editable(key) && chartCellAvailable(observation, key)) EditableGridCell(
                    field = field,
                    value = value,
                    onValueChange = onValueChange,
                    onCommit = { onCommit(key, it) },
                    hint = hint,
                    height = height,
                    alert = !LocalPartograph.current && isAlertValue(field, value),
                    enabled = enabled,
                    width = width / 2f
                ) else GridCell(value, width = width / 2f, height = height, alert = !LocalPartograph.current && isAlertValue(field, value))
            }
        }
    }
}

@Composable
private fun LabourProgressPlotGroup(
    observations: List<LabourCareObservation>,
    draftSlot: Int,
    nowIso: String,
    state: LabourCareUiState,
    horizontalScroll: ScrollState,
    onCellSave: (String, String, String) -> Unit,
    columnControls: List<`in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl>,
    accessLevel: Int,
    isSaving: Boolean
) {
    val levels = (10 downTo 5).toList() + (5 downTo 0).toList()
    Row(Modifier.fillMaxWidth()) {
        FixedCell(
            "LABOUR\nPROGRESS",
            groupWidth,
            height = plotRowHeight * levels.size.toFloat(),
            background = MaterialTheme.colorScheme.surfaceContainerHigh,
            bold = true
        )
        Column(Modifier.weight(1f)) {
            PlotMetricRows(
                label = "Cervix\n[Plot X]\nSecond stage: P marks pushing onset",
                field = "cervicalDilation",
                levels = (10 downTo 5).toList(),
                alertForLevel = { level -> when (level) {
                    9 -> "≥2 h"
                    8 -> "≥2.5 h"
                    7 -> "≥3 h"
                    6 -> "≥5 h"
                    5 -> "≥6 h"
                    else -> ""
                } },
                observations = observations,
                draftSlot = draftSlot,
                nowIso = nowIso,
                state = state,
                horizontalScroll = horizontalScroll,
                onCellSave = onCellSave,
                columnControls = columnControls,
                accessLevel = accessLevel,
                isSaving = isSaving
            )
            PlotMetricRows(
                label = "Descent\n[Plot O]",
                field = "descent",
                levels = (5 downTo 0).toList(),
                alertForLevel = { "" },
                observations = observations,
                draftSlot = draftSlot,
                nowIso = nowIso,
                state = state,
                horizontalScroll = horizontalScroll,
                onCellSave = onCellSave,
                columnControls = columnControls,
                accessLevel = accessLevel,
                isSaving = isSaving
            )
        }
    }
}

@Composable
internal fun PlotMetricRows(
    label: String,
    field: String,
    levels: List<Int>,
    alertForLevel: (Int) -> String,
    observations: List<LabourCareObservation>,
    draftSlot: Int,
    nowIso: String,
    state: LabourCareUiState,
    horizontalScroll: ScrollState,
    onCellSave: (String, String, String) -> Unit,
    columnControls: List<`in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl>,
    accessLevel: Int,
    isSaving: Boolean
) {
    val readOnly = LocalGuideReadOnly.current
    val availableNow = chartMillis(nowIso) ?: `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis()
    Row(Modifier.fillMaxWidth()) {
        FixedCell(label, labelWidth, height = plotRowHeight * levels.size.toFloat(), bold = true)
        Column(Modifier.weight(1f)) {
            levels.forEach { level ->
                Row(Modifier.fillMaxWidth()) {
                    FixedCell(level.toString(), scaleWidth, height = plotRowHeight, bold = true)
                    FixedCell(
                        alertForLevel(level),
                        thresholdWidth,
                        height = plotRowHeight,
                        background = MaterialTheme.colorScheme.surfaceContainerLow,
                        fontSize = 9.sp
                    )
                    Row(Modifier.weight(1f).horizontalScroll(horizontalScroll)) {
                        repeat(TOTAL_SLOTS) { index ->
                            val observation = observations.getOrNull(index)
                            val draft = observation?.cellKey == "scheduled" || (index == draftSlot && observations.size < TOTAL_SLOTS)
                            val control = observation?.columnId?.let { id -> columnControls.firstOrNull { it.columnId == id } }
                            val canEditColumn = !readOnly && state.organizationKeyAvailable && chartCellAvailable(observation, field, availableNow) && (
                                (draft && chartCellAvailable(observation, field, availableNow)) || (control?.canEditCell(if (field == "cervicalDilation" && index >= FIRST_STAGE_SLOTS) "pushingStarted" else field, accessLevel) == true)
                            )
                            val secondStageCervix = field == "cervicalDilation" && index >= FIRST_STAGE_SLOTS
                            val pushingMarkerCell = secondStageCervix && level == levels.first()
                            val editable = canEditColumn && (!secondStageCervix || pushingMarkerCell)
                            val rawValue = if (secondStageCervix || draft) "" else observation?.displayValueFor(field).orEmpty()
                            val plotted = if (pushingMarkerCell) observation?.pushingStarted == true else {
                                !secondStageCervix && rawValue.toDoubleOrNull() == level.toDouble()
                            }
                            val alert = !secondStageCervix && plotted && field == "cervicalDilation" && if (draft) {
                                cervicalDelayAlert(rawValue, observations, nowIso)
                            } else if (observation != null) {
                                cervicalDelayAlert(rawValue, observations.take(index + 1), observation.timestamp)
                            } else false
                            FocusableHistoryCell(observation, index, if (pushingMarkerCell) "pushingStarted" else field, level) {
                                PlotCell(
                                    symbol = if (secondStageCervix) "P" else if (field == "cervicalDilation") "X" else "O",
                                    selected = plotted,
                                    editable = editable,
                                    busy = isSaving,
                                    disabled = secondStageCervix && !pushingMarkerCell,
                                    alert = alert,
                                    onClick = {
                                        if (editable) {
                                            if (pushingMarkerCell) {
                                                onCellSave(observation?.let(::lcgCellTarget).orEmpty(), "pushingStarted", if (plotted) "" else "true")
                                            } else {
                                                val newValue = if (plotted) "" else level.toString()
                                                onCellSave(observation?.let(::lcgCellTarget).orEmpty(), field, newValue)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlotCell(
    symbol: String,
    selected: Boolean,
    editable: Boolean,
    busy: Boolean = false,
    disabled: Boolean = false,
    alert: Boolean,
    width: Dp = timeCellWidth,
    height: Dp = plotRowHeight,
    onClick: () -> Unit
) {
    Box(
        Modifier.width(width)
            .height(height)
            .border(0.5.dp, if (disabled) MaterialTheme.colorScheme.outlineVariant else tableLine)
            .background(if (editable) MaterialTheme.colorScheme.surfaceContainer else if (disabled) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface)
            .clickable(enabled = editable && !busy, onClick = onClick)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (alert) AlertValueEllipse(Modifier.fillMaxSize())
        if (selected) Text(symbol, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun ColumnConfirmRow(
    observations: List<LabourCareObservation>,
    controls: List<`in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl>,
    accessLevel: Int,
    onConfirm: (String) -> Unit,
    isSaving: Boolean,
    horizontalScroll: ScrollState
) {
    Row(Modifier.fillMaxWidth()) {
        FixedCell("", groupWidth + labelWidth + scaleWidth + thresholdWidth, height = 42.dp)
        Row(Modifier.weight(1f).horizontalScroll(horizontalScroll)) {
            repeat(TOTAL_SLOTS) { index ->
                val observation = observations.getOrNull(index)
                val control = observation?.columnId?.let { id -> controls.firstOrNull { it.columnId == id } }
                val canConfirm = control?.canSignOff(accessLevel) == true
                Box(Modifier.width(timeCellWidth).height(42.dp).border(0.5.dp, tableLine), contentAlignment = Alignment.Center) {
                    if (control != null) {
                        MgButton(
                            onClick = { onConfirm(control.columnId) },
                            enabled = canConfirm && !isSaving,
                            text = (if (control.canSignOff(accessLevel)) "Sign off column" else "Signed off"),
                            times = 0.625f
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FloatingStageAction(
    observations: List<LabourCareObservation>,
    controls: List<`in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl>,
    accessLevel: Int,
    busy: Boolean,
    onFullDilation: (String) -> Unit,
    onBirth: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val index = observations.indexOfLast { it.columnId.isNotBlank() && it.cellKey != "scheduled" }
    val column = observations.getOrNull(index) ?: return
    val control = controls.firstOrNull { it.columnId == column.columnId } ?: return
    if (observations.any { it.birthConfirmed }) return
    val firstStage = index < FIRST_STAGE_SLOTS
    if (firstStage && observations.any { it.fullDilationConfirmed }) return
    Surface(modifier = modifier.widthIn(max = 420.dp), shape = RoundedCornerShape(8.dp), shadowElevation = 6.dp) {
        MgButton(
            text = if (firstStage) "Full Dilation: Confirm and skip to second stage" else "Birth: Confirm and mark birth",
            onClick = { if (firstStage) onFullDilation(control.columnId) else onBirth(control.columnId) },
            enabled = !busy && accessLevel in 10..99
        )
    }
}

@Composable
private fun FixedCell(
    text: String,
    width: Dp,
    header: Boolean = false,
    height: Dp = 42.dp,
    background: Color = MaterialTheme.colorScheme.surface,
    bold: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp
) {
    Box(
        Modifier.width(width).height(height).border(0.5.dp, tableLine).background(background).padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = if (header) 9.sp else fontSize, textAlign = TextAlign.Center), fontWeight = if (bold || header) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal, lineHeight = 12.sp)
    }
}

@Composable
private fun GridCell(text: String, width: Dp = timeCellWidth, height: Dp = 42.dp, header: Boolean = false, background: Color = MaterialTheme.colorScheme.surface, alert: Boolean = false) {
    Box(
        Modifier.width(width).height(height).border(0.5.dp, tableLine).background(background).padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (alert && text.isNotBlank()) {
            AlertValueEllipse(Modifier.fillMaxSize())
        }
        Text(text, style = MaterialTheme.typography.labelMedium.copy(fontSize = if (header) 10.sp else 12.sp, textAlign = TextAlign.Center, lineHeight = 13.sp))
    }
}

@Composable
private fun EditableGridCell(
    field: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: (String) -> Unit,
    hint: String,
    height: Dp = 42.dp,
    alert: Boolean = false,
    enabled: Boolean = true,
    width: Dp = timeCellWidth
) {
    val compactEditor = LocalAppWindowSize.current == AppWindowSize.Compact
    val options = categoricalOptionsForCell(field)
    var expanded by remember(field) { mutableStateOf(false) }
    // Keep an in-progress edit stable while the encrypted feed refreshes. A
    // listener update can change `value` during typing (for example after a
    // key rotation); keying this state by value would discard the user's text.
    var editedValue by remember(field) { mutableStateOf(value) }
    var hadFocus by remember(field) { mutableStateOf(false) }
    var isDirty by remember(field) { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (value == editedValue) {
            isDirty = false
        } else if (!isDirty) {
            editedValue = value
        }
    }
    val invalid = clinicalValueError(field, editedValue)
    Box(
        Modifier.width(width).height(height).border(0.5.dp, tableLine).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        if (options == null) {
            if (alert && value.isNotBlank()) AlertValueEllipse(Modifier.fillMaxSize())
            if (value.isBlank()) Text(hint, color = MaterialTheme.colorScheme.outline, fontSize = if (compactEditor) 12.sp else 9.sp, textAlign = TextAlign.Center)
            val multiline = field in setOf("medicine", "intravenousFluids", "assessment", "plan")
            BasicTextField(
                value = editedValue,
                onValueChange = { editedValue = it; isDirty = true; onValueChange(it) },
                singleLine = !multiline,
                textStyle = TextStyle(
                    fontSize = if (compactEditor) 16.sp else 12.sp,
                    textAlign = if (multiline) TextAlign.Start else TextAlign.Center,
                    color = if (invalid != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    lineHeight = 15.sp
                ),
                enabled = enabled,
                modifier = (if (multiline) Modifier.fillMaxSize().padding(5.dp) else Modifier.fillMaxWidth())
                    .onFocusChanged { focus ->
                        if (focus.isFocused) hadFocus = true
                        else if (hadFocus) {
                            hadFocus = false
                            if (invalid == null && isDirty) onCommit(editedValue)
                        }
                    }
            )
        } else {
            Box(
                Modifier.fillMaxSize().clickable(enabled = enabled) { expanded = true },
                contentAlignment = Alignment.Center
            ) {
                if (alert && value.isNotBlank()) AlertValueEllipse(Modifier.fillMaxSize())
                Text(
                    value.ifBlank { "Select" },
                    color = if (value.isBlank()) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(end = 10.dp)
                )
                Text("▾", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                if (value.isNotBlank()) DropdownMenuItem(
                    text = { Text("Clear recorded value") },
                    enabled = enabled,
                    onClick = {
                        onValueChange("")
                        onCommit("")
                        expanded = false
                    }
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        enabled = enabled,
                        onClick = {
                            onValueChange(option)
                            onCommit(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertValueEllipse(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .semantics { contentDescription = "Abnormal observation" }
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .background(alertRed.copy(alpha = 0.08f), shape)
            .border(1.5.dp, alertRed, shape)
    )
}

@Composable
private fun slotName(index: Int): String = if (index < FIRST_STAGE_SLOTS) {
    "Hour $index"
} else "Hour $index · S${index - FIRST_STAGE_SLOTS + 1}"
private fun String.timeLabel(): String = formatUiTimestamp(this).let { formatted ->
    if (formatted.length == 20) formatted.replaceRange(11, 12, "\n") else formatted
}
private fun cellHint(field: String): String = when (field) {
    "companion", "painRelief", "oralFluid" -> "Y/N/D"
    "posture" -> "SP/MO"
    "fetalHeartRate" -> "bpm"
    "fhrDeceleration" -> "N/E/L/V"
    "amnioticFluid" -> "C/M/B/I"
    "fetalPosition" -> "P/T"
    "caput", "moulding" -> "+"
    "cervicalDilation" -> "cm"
    "descent" -> "0–5"
    "contractionFrequency" -> "count"
    "contractionDuration" -> "sec"
    else -> ""
}

private fun LabourCareUiState.valueFor(field: String?): String = when (field) {
    "companion" -> companion
    "painRelief" -> painRelief
    "oralFluid" -> oralFluid
    "posture" -> posture
    "fetalHeartRate" -> fetalHeartRate
    "fhrDeceleration" -> fhrDeceleration
    "amnioticFluid" -> amnioticFluidFinding
    "fetalPosition" -> fetalPosition
    "caput" -> caput
    "moulding" -> moulding
    "pulse" -> pulse
    "systolic" -> systolic
    "diastolic" -> diastolic
    "temperature" -> temperature
    "urineProtein" -> urineProtein
    "urineAcetone" -> urineAcetone
    "contractionFrequency" -> contractionFrequency
    "contractionDuration" -> contractionDuration
    "cervicalDilation" -> cervicalDilation
    "oxytocin" -> oxytocin
    "medicine" -> medicine
    "intravenousFluids" -> intravenousFluids
    "descent" -> descent
    "assessment" -> assessment
    "plan" -> plan
    "initials" -> initials
    else -> partographCells[field].orEmpty()
}

private fun LabourCareObservation.valueFor(field: String?): String = if (cellKey == "skipped" || cellKey == "scheduled") "" else lcgFirstHalfValue(field) ?: when (field) {
    "patientLabel" -> patientLabel
    "parity" -> partographCells[field] ?: parity.toString()
    "labourOnset" -> labourOnset
    "activeLabourDiagnosis" -> activeLabourDiagnosis
    "rupturedMembranes" -> rupturedMembranesDateTime
    "riskFactors" -> riskFactors
    "companion" -> companion
    "painRelief" -> painRelief
    "oralFluid" -> oralFluid
    "posture" -> posture
    "fetalHeartRate" -> fetalWellbeing.fetalHeartRateBpm.toString()
    "fhrDeceleration" -> fetalWellbeing.heartRateDeceleration
    "amnioticFluid" -> fetalWellbeing.amnioticFluidFinding.ifBlank { when (fetalWellbeing.amnioticFluidStatus) { AmnioticFluidStatus.CLEAR -> "C"; AmnioticFluidStatus.MECONIUM_STAINED -> "M"; AmnioticFluidStatus.BLOOD_STAINED -> "B"; AmnioticFluidStatus.ABSENT -> "A"; AmnioticFluidStatus.INTACT -> "I"; AmnioticFluidStatus.UNKNOWN -> "U" } }
    "fetalPosition" -> fetalWellbeing.fetalPosition
    "caput" -> fetalWellbeing.caput
    "moulding" -> fetalWellbeing.moulding
    "pulse" -> maternalVitals.pulseBpm.toString()
    "systolic" -> maternalVitals.systolicBloodPressure.toString()
    "diastolic" -> maternalVitals.diastolicBloodPressure.toString()
    "temperature" -> maternalVitals.temperatureCelsius.toString()
    "urineProtein" -> maternalVitals.urineProtein
    "urineAcetone" -> maternalVitals.urineAcetone
    "contractionFrequency" -> laborProgress.contractionFrequencyPer10Minutes.toString()
    "contractionDuration" -> laborProgress.contractionDurationSeconds.toString()
    "cervicalDilation" -> laborProgress.cervicalDilationCm.toString()
    "oxytocin" -> oxytocin
    "medicine" -> medicine
    "intravenousFluids" -> intravenousFluids
    "descent" -> descent
    "assessment" -> assessment
    "plan" -> plan
    "initials" -> initials
    else -> partographCells[field].orEmpty()
}

internal fun LabourCareObservation.displayValueFor(field: String?): String {
    if (cellKey == "scheduled") return ""
    lcgFirstHalfValue(field)?.let { return it }
    if (isGuideHeader && allHeaderFields.any { it.first == field }) return valueFor(field)
    if (field != "initials" && cellKey.isNotBlank() && field !in recordedCellKeys &&
        !(recordedCellKeys.isEmpty() && field == cellKey)
    ) return ""
    return valueFor(field)
}

/** WHO alert criteria for observation cells. The separate ALERT reference column is never circled. */
private fun String.hasPlusGradeAtLeast(prefix: String, threshold: Int): Boolean {
    if (!startsWith(prefix)) return false
    val grade = removePrefix(prefix)
    return grade.length >= threshold && grade.all { it == '+' }
}

internal fun isAlertValue(field: String?, rawValue: String): Boolean {
    val value = rawValue.trim().uppercase()
    return value.isNotEmpty() && when (field) {
        "companion", "painRelief", "oralFluid" -> value == "N"
        "posture" -> value == "SP"
        "fetalHeartRate" -> value.toDoubleOrNull()?.let { it !in 110.0..<160.0 } == true
        "fhrDeceleration" -> value == "L"
        "amnioticFluid" -> value.hasPlusGradeAtLeast("M", 3) || value == "B"
        "fetalPosition" -> value == "P" || value == "T"
        "caput", "moulding" -> value.hasPlusGradeAtLeast("", 3)
        "pulse" -> value.toIntOrNull()?.let { it !in 60..<120 } == true
        "systolic" -> value.toIntOrNull()?.let { it !in 80..<140 } == true
        "diastolic" -> value.toIntOrNull()?.let { it >= 90 } == true
        "temperature" -> value.toDoubleOrNull()?.let { it !in 35.0..<37.5 } == true
        "urineProtein" -> value.hasPlusGradeAtLeast("P", 2)
        "urineAcetone" -> value.hasPlusGradeAtLeast("A", 2)
        "contractionFrequency" -> value.toIntOrNull()?.let { it !in 3..5 } == true
        "contractionDuration" -> value.toIntOrNull()?.let { it !in 20..60 } == true
        else -> false
    }
}

/** History reads the exact event key; unrelated full-column snapshots are excluded. */
@Composable
private fun CellHistoryDialog(
    state: LabourCareUiState,
    observations: List<LabourCareObservation>,
    focused: HistoryCellFocus?,
    partograph: Boolean,
    onDismiss: () -> Unit,
    canCorrect: (LabourCareObservation, String) -> Boolean,
    onCorrect: (LabourCareObservation, String) -> Unit
) {
    val initialColumn = observations.indexOfFirst { it.focusKey() == focused?.columnKey }
    var selectedColumn by remember { mutableStateOf(if (focused == null) 0 else initialColumn) }
    var selectedField by remember { mutableStateOf(focused?.field ?: if (partograph) "fetalHeartRateHalf1" else "companion") }
    LaunchedEffect(initialColumn) {
        // The first save can finish after the history button has opened the dialog.
        if (selectedColumn < 0 && initialColumn >= 0) selectedColumn = initialColumn
    }
    var columnsExpanded by remember { mutableStateOf(false) }
    var fieldsExpanded by remember { mutableStateOf(false) }
    val column = observations.getOrNull(selectedColumn)
    val isHeader = column?.isGuideHeader == true || (selectedColumn < 0 && focused?.columnKey == "header")
    val fields = if (isHeader) ((if (partograph) partographHeaderFields else guideHeaderFields) + deliveryOutcomeFields) else (
        (if (partograph) partographRows else guideRows).mapNotNull { row -> row.field?.let { it to row.label.replace("\n", " ") } } +
            listOf("cervicalDilation" to "Cervical dilation", "descent" to "Descent", "pushingStarted" to "Pushing started",
                "birthConfirmed" to "Birth marked", "fullDilationConfirmed" to "Skip to second stage")
        ).distinctBy { it.first }.flatMap { (field, label) -> intervalFieldOptions(field, label, partograph) }
    LaunchedEffect(isHeader, selectedColumn) {
        if (fields.none { it.first == selectedField }) selectedField = fields.first().first
    }
    val events = column?.let { state.feed.cellEvents.cellEditHistory(it.sessionId, it.columnId, selectedField) }.orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cell edit history") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    TextButton(onClick = { columnsExpanded = true }) {
                        Text(when {
                            column?.isGuideHeader == true -> "Column: Header"
                            selectedColumn < 0 && focused?.columnKey == "header" -> "Column: Header"
                            selectedColumn < 0 -> "Column: focused unsaved cell"
                            else -> "Column: ${slotName(selectedColumn)} · ${column?.timestamp?.let(::formatUiTimestamp).orEmpty()}"
                        })
                    }
                    DropdownMenu(expanded = columnsExpanded, onDismissRequest = { columnsExpanded = false }) {
                        observations.forEachIndexed { index, observation ->
                            DropdownMenuItem(text = { Text(if (observation.isGuideHeader) "Header" else "${slotName(index)} · ${formatUiTimestamp(observation.timestamp)}") }, onClick = {
                                selectedColumn = index
                                columnsExpanded = false
                            })
                        }
                    }
                }
                Box {
                    TextButton(onClick = { fieldsExpanded = true }) { Text("Row: " + fields.firstOrNull { it.first == selectedField }?.second.orEmpty()) }
                    DropdownMenu(expanded = fieldsExpanded, onDismissRequest = { fieldsExpanded = false }) {
                        fields.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { selectedField = key; fieldsExpanded = false })
                        }
                    }
                }
                if (column != null && canCorrect(column, selectedField)) {
                    TextButton(onClick = { onCorrect(column, selectedField) }, enabled = !state.isSaving) { Text("Correct this cell") }
                }
                if (state.feed.hasPendingWrites) Text("Some changes are awaiting server confirmation.")
                if (column == null) Text("This cell has no saved history yet.")
                else if (column.columnId.isBlank()) Text("Earlier history unavailable for this legacy observation.")
                else if (events.isEmpty()) Text("No saved entries for this cell.")
                LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(events.size) { index ->
                        val event = events[index]
                        val value = event.historyValue(selectedField).ifBlank { "(blank)" }
                        Column {
                            Text(if (index == 0) "Initial entry: $value" else "${events[index - 1].historyValue(selectedField).ifBlank { "(blank)" }} → $value")
                            AccessLevelText("${state.feed.memberNames[event.authorId] ?: event.authorName.takeIf { it.isNotBlank() } ?: event.authorId} · access level ${event.authorAccessLevel}", style = MaterialTheme.typography.bodySmall)
                            Text("Revision ${event.revision} · " + if (event.createdAtMillis > 0) "Saved ${formatUiTimestamp(event.createdAtMillis)}" else "Server save time unavailable", style = MaterialTheme.typography.bodySmall)
                            if (selectedField == "fullDilationConfirmed") {
                                Text("Skipped to second stage · UID: ${event.authorId}", style = MaterialTheme.typography.bodySmall)
                                Text("Full-dilation column time: ${formatUiTimestamp(event.timestamp)}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (selectedField == "birthConfirmed") {
                                Text("Birth marked · UID: ${event.authorId}", style = MaterialTheme.typography.bodySmall)
                                Text("Birth column time: ${formatUiTimestamp(event.timestamp)}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (selectedField == "initials") Text(if (event.birthConfirmed) "Column sign-off · BIRTH marked" else if (event.fullDilationConfirmed) "Column sign-off · skipped to second stage" else "Column sign-off", style = MaterialTheme.typography.labelSmall)
                            if (event.correctionReason.isNotBlank()) Text("Correction reason: ${event.correctionReason}", style = MaterialTheme.typography.bodySmall)
                            state.feed.corrections.filter { it.originalDocumentId == event.firestoreDocumentId }.forEach { correction ->
                                Text("Correction by ${state.feed.memberNames[correction.authorId] ?: correction.authorId}: ${correction.correctionText}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun LabourCareObservation.historyValue(key: String): String = when {
    key in partographMarkCells -> partographMarkCells[key].orEmpty()
    editedValue != null && cellKey == key -> editedValue
    key == "fullDilationConfirmed" -> if (fullDilationConfirmed) "Full dilation confirmed · skipped to second stage" else "Not confirmed"
    key == "birthConfirmed" -> if (birthConfirmed) "BIRTH" else "Not marked"
    key == "pushingStarted" -> if (pushingStarted) "Yes" else "No"
    key == "oxytocinHalf1" -> partographText(key)
    key.contains("Half") -> halfHourCells[key].orEmpty()
    else -> displayValueFor(key)
}

/** Compact LCG keeps the table geometry and pages only its time column. */
internal data class CompactLcgColumnWidths(val section: Dp, val observation: Dp, val alert: Dp, val time: Dp)
internal fun compactLcgColumnWidths(available: Dp): CompactLcgColumnWidths {
    val section = (available * 0.11f).coerceAtMost(42.dp)
    val alert = (available * 0.17f).coerceAtMost(70.dp)
    val time = (available * 0.34f).coerceAtLeast(96.dp).coerceAtMost(available * 0.5f).coerceAtMost(160.dp)
    return CompactLcgColumnWidths(section, available - section - alert - time, alert, time)
}

internal fun compactLcgInitialColumn(observations: List<LabourCareObservation>, activeId: String, draftSlot: Int): Int {
    val active = observations.indexOfFirst { it.columnId == activeId && activeId.isNotBlank() && it.cellKey != "skipped" }
    if (active >= 0) return active
    val recorded = observations.indexOfLast { it.columnId.isNotBlank() && it.cellKey !in setOf("scheduled", "skipped") }
    return if (recorded >= 0) recorded else draftSlot.coerceAtLeast(0)
}

/** Presentation gaps remain in the chart/export, but are not mobile paging destinations. */
internal fun compactLcgAdjacentColumn(observations: List<LabourCareObservation>, current: Int,
    direction: Int, draftSlot: Int): Int? {
    require(direction == -1 || direction == 1)
    val last = maxOf(observations.lastIndex, draftSlot)
    var candidate = current + direction
    while (candidate in 0..last) {
        if (observations.getOrNull(candidate)?.cellKey != "skipped") return candidate
        candidate += direction
    }
    return null
}

@Composable
internal fun CompactCareGuide(
    state: LabourCareUiState, observations: List<LabourCareObservation>, draftSlot: Int,
    accessLevel: Int,
    onSave: (String, String, String) -> Unit, onConfirm: (String) -> Unit,
    onFullDilation: (String) -> Unit, onBirth: (String) -> Unit,
    onHistory: (Int, String) -> Unit,
    onCorrectColumn: (LabourCareObservation) -> Unit,
    onSelectColumn: (String) -> Unit = {}, nowMillis: Long = `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis(),
    scrollState: LazyListState = rememberLazyListState()
) {
    val readOnly = LocalGuideReadOnly.current
    val lastSlot = maxOf(0, observations.lastIndex, draftSlot)
    var selected by rememberSaveable(state.orgId, state.patientSessionId) {
        mutableStateOf(compactLcgInitialColumn(observations, state.activeColumnId, draftSlot))
    }
    val boundedSlot = selected.coerceIn(0, lastSlot)
    val slot = if (observations.getOrNull(boundedSlot)?.cellKey == "skipped") {
        compactLcgAdjacentColumn(observations, boundedSlot, 1, draftSlot)
            ?: compactLcgAdjacentColumn(observations, boundedSlot, -1, draftSlot) ?: boundedSlot
    } else boundedSlot
    val previousSlot = compactLcgAdjacentColumn(observations, slot, -1, draftSlot)
    val nextSlot = compactLcgAdjacentColumn(observations, slot, 1, draftSlot)
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.activeColumnId, observations.map { it.columnId }) {
        val active = observations.indexOfFirst { it.columnId == state.activeColumnId && state.activeColumnId.isNotBlank() }
        if (active >= 0) selected = active
    }
    val observation = observations.getOrNull(slot) ?: if (slot == draftSlot) {
        val start = chartMillis(state.chartStartTimestamp) ?: nowMillis
        val next = observations.partographEntrySequence().nextTimestampMillis(start)
        scheduledObservations(emptyList(), next, 1, state.patientSessionId, state.patientLabel).first()
    } else null
    val isDraft = observation?.cellKey == "scheduled" || slot == draftSlot
    val control = state.feed.columnControls.firstOrNull { it.columnId == observation?.columnId }
    val isFirstStage = slot < FIRST_STAGE_SLOTS
    val birthTime = observations.filter { it.birthConfirmed }.mapNotNull { chartMillis(it.timestamp) }.minOrNull()
    fun canEdit(key: String): Boolean = !readOnly &&
        chartCellAvailable(observation, key) &&
        (!isDraft || birthTime == null || (chartMillis(observation?.timestamp.orEmpty()) ?: Long.MAX_VALUE) <= birthTime) &&
        (isDraft || control?.canEditCell(key, accessLevel) == true)
    fun moveColumn(next: Int) {
        val row = scrollState.firstVisibleItemIndex
        val offset = scrollState.firstVisibleItemScrollOffset
        focusManager.clearFocus()
        selected = next.coerceIn(0, lastSlot)
        onSelectColumn(observations.getOrNull(selected)?.columnId ?: "draft:$selected")
        // Cancel focus-driven scrolling and anchor the same row through the next remeasure.
        scrollState.requestScrollToItem(row, offset)
    }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(4.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val tableWidth = maxWidth
            val widths = compactLcgColumnWidths(tableWidth)
            val density = LocalDensity.current
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val cellStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            fun rowHeight(row: GuideRow): Dp {
                fun textHeight(text: String, width: Dp) = with(density) {
                    measurer.measure(text, cellStyle, constraints = Constraints(maxWidth = (width.toPx() - 6.dp.toPx()).toInt().coerceAtLeast(1))).size.height.toDp()
                }
                return maxOf(64.dp, row.height, textHeight(row.label, widths.observation) + 8.dp, textHeight(row.alert, widths.alert) + 8.dp)
            }
            @Composable
            fun observationSection(rows: List<GuideRow>) {
                val heights = rows.map(::rowHeight)
                Row(Modifier.fillMaxWidth()) {
                    FixedCell(rows.first().group, widths.section, height = heights.fold(0.dp) { total, height -> total + height },
                        background = MaterialTheme.colorScheme.surfaceContainerHigh, bold = true)
                    Column {
                        rows.forEachIndexed { index, row ->
                            val height = heights[index]
                            val field = row.field.orEmpty()
                            Row {
                                FixedCell(row.label, widths.observation, height = height, bold = true)
                                FixedCell(row.alert, widths.alert, height = height, background = MaterialTheme.colorScheme.surfaceContainerLow, fontSize = 10.sp)
                                if (field in halfHourFields) {
                                    HalfHourGridCell(field, observation, slot, "", height, {},
                                        { key, value -> onSave(observation?.columnId.orEmpty(), key, value) }, cellHint(field),
                                        editable = { key -> canEdit(key) }, enabled = !state.isSaving && state.organizationKeyAvailable, width = widths.time)
                                } else FocusableHistoryCell(observation, slot, field) {
                                    val value = if (isDraft) "" else observation?.displayValueFor(field).orEmpty()
                                    val alert = isAlertValue(field, value)
                                    if (field != "initials" && canEdit(field)) EditableGridCell(field, value, {},
                                        { onSave(observation?.columnId.orEmpty(), field, it) }, cellHint(field), height,
                                        alert = alert, enabled = !state.isSaving && state.organizationKeyAvailable, width = widths.time)
                                    else GridCell(if (field == "initials" && control?.confirmed == false) "Awaiting sign-off" else value,
                                        width = widths.time, height = height, alert = alert)
                                }
                            }
                        }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp).testTag("compact-lcg-table"), state = scrollState) {
                stickyHeader(key = "compact-table-header") {
                    Column(Modifier.fillMaxWidth().background(tableHeader)) {
                        GridCell(if (isFirstStage) "ACTIVE FIRST STAGE" else "SECOND STAGE", width = tableWidth,
                            height = 28.dp, header = true, background = tableHeader)
                        Row {
                            val headingHeight = 112.dp
                            FixedCell("SEC\nTION", widths.section, header = true, height = headingHeight, background = tableHeader)
                            FixedCell("OBSERVATION", widths.observation, header = true, height = headingHeight, background = tableHeader)
                            FixedCell("ALERT", widths.alert, header = true, height = headingHeight, background = tableHeader)
                            Column(Modifier.width(widths.time).height(headingHeight).border(0.5.dp, tableLine).background(tableHeader)) {
                                Row(Modifier.fillMaxWidth().height(28.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(slotName(slot), modifier = Modifier.weight(1f), fontSize = 11.sp, maxLines = 1)
                                    if (observation != null && control?.canCorrect(accessLevel) == true) IconButton(
                                        enabled = !readOnly && !state.isSaving, onClick = { focusManager.clearFocus(); onCorrectColumn(observation) },
                                        modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Outlined.Edit, contentDescription = "Correct ${slotName(slot)}", modifier = Modifier.size(18.dp))
                                    }
                                }
                                Text(observation?.timestamp?.timeLabel() ?: "New observation", modifier = Modifier.fillMaxWidth(),
                                    fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center)
                                Text(when {
                                    observation == null -> "Not started"
                                    observation.cellKey == "skipped" -> "Skipped"
                                    observation.birthConfirmed -> "Birth recorded"
                                    !chartCellAvailable(observation, "pulse") -> "Future"
                                    control?.confirmed == true -> "Signed off"
                                    isDraft -> "Empty"
                                    else -> "Open"
                                }, modifier = Modifier.fillMaxWidth(), fontSize = 9.sp, textAlign = TextAlign.Center)
                                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { previousSlot?.let(::moveColumn) }, enabled = previousSlot != null, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous time column")
                                    }
                                    IconButton(onClick = { nextSlot?.let(::moveColumn) }, enabled = nextSlot != null, modifier = Modifier.size(40.dp)) {
                                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next time column")
                                    }
                                }
                            }
                        }
                    }
                }
                items(sectionGroups(guideRows.take(18)), key = { "compact-upper-${it.first().group}" }) { rows ->
                    key(slot) { observationSection(rows) }
                }
                item(key = "compact-progress") {
                    key(slot) {
                        val progressHeight = 48.dp
                        Row(Modifier.fillMaxWidth()) {
                            FixedCell("LABOUR\nPROGRESS", widths.section, height = progressHeight * 12,
                                background = MaterialTheme.colorScheme.surfaceContainerHigh, bold = true)
                            Column {
                                listOf("cervicalDilation" to (10 downTo 5).toList(), "descent" to (5 downTo 0).toList()).forEach { (field, levels) ->
                                    val scale = (widths.observation * 0.25f).coerceAtMost(32.dp)
                                    Row {
                                        FixedCell(if (field == "descent") "Descent\n[Plot O]" else "Cervix (cm)\n[Plot X]\nSecond stage: P", widths.observation - scale,
                                            height = progressHeight * levels.size, bold = true)
                                        Column {
                                            levels.forEach { level ->
                                                val pushing = field == "cervicalDilation" && !isFirstStage
                                                val key = if (pushing) "pushingStarted" else field
                                                val value = if (isDraft) "" else observation?.displayValueFor(field).orEmpty()
                                                val plotted = if (pushing) level == levels.first() && observation?.pushingStarted == true else value.toDoubleOrNull() == level.toDouble()
                                                val alert = !pushing && plotted && field == "cervicalDilation" && observation != null &&
                                                    cervicalDelayAlert(value, observations.take(slot + 1), observation.timestamp)
                                                Row {
                                                    FixedCell(level.toString(), scale, height = progressHeight)
                                                    FixedCell(if (field == "cervicalDilation") when (level) {
                                                        9 -> "≥2 h"; 8 -> "≥2.5 h"; 7 -> "≥3 h"; 6 -> "≥5 h"; 5 -> "≥6 h"; else -> ""
                                                    } else "", widths.alert, height = progressHeight, background = MaterialTheme.colorScheme.surfaceContainerLow, fontSize = 10.sp)
                                                    FocusableHistoryCell(observation, slot, key, level) {
                                                        PlotCell(symbol = if (pushing) "P" else if (field == "cervicalDilation") "X" else "O", selected = plotted,
                                                            editable = canEdit(key) && state.organizationKeyAvailable && (!pushing || level == levels.first()), busy = state.isSaving, disabled = pushing && level != levels.first(),
                                                            alert = alert, width = widths.time, height = progressHeight, onClick = {
                                                                onSave(observation?.let(::lcgCellTarget).orEmpty(), key, if (pushing) { if (plotted) "" else "true" } else if (plotted) "" else level.toString())
                                                            })
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                items(sectionGroups(guideRows.drop(18)), key = { "compact-lower-${it.first().group}" }) { rows ->
                    key(slot) { observationSection(rows) }
                }
                item(key = "compact-confirmation") {
                    // Keep the scroll extent identical for recorded and empty columns.
                    Row {
                        FixedCell("Time column", widths.section + widths.observation + widths.alert, height = 80.dp, bold = true)
                        Box(Modifier.width(widths.time).height(80.dp).border(0.5.dp, tableLine), contentAlignment = Alignment.Center) {
                            if (control != null) Button(onClick = { focusManager.clearFocus(); onConfirm(control.columnId) },
                                enabled = !readOnly && !state.isSaving && state.organizationKeyAvailable && control.canSignOff(accessLevel),
                                contentPadding = PaddingValues(6.dp)) { Text(if (control.canSignOff(accessLevel)) "Sign off column" else "Signed off", fontSize = 11.sp, textAlign = TextAlign.Center) }
                            else Text("Not recorded", fontSize = 11.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                item(key = "compact-stage-action") {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    if (control != null && observation != null && observations.none { it.birthConfirmed } &&
                        slot == observations.indexOfLast { it.columnId.isNotBlank() && it.cellKey != "scheduled" } &&
                        (!isFirstStage || observations.none { it.fullDilationConfirmed })) {
                        MgButton(text = if (isFirstStage) "Confirm full dilation" else "Confirm birth", modifier = Modifier.fillMaxWidth(),
                            enabled = !readOnly && !state.isSaving && state.organizationKeyAvailable && accessLevel in 10..99,
                            onClick = { focusManager.clearFocus(); if (isFirstStage) onFullDilation(control.columnId) else onBirth(control.columnId) })
                    }
                    if (observation?.birthConfirmed == true) TextButton(onClick = { onHistory(slot, "birthConfirmed") }) { Text("Birth · View history") }
                    }
                }
            }
        }
    }
}


@Composable
private fun ConfirmedCellCorrectionDialog(
    state: LabourCareUiState,
    original: LabourCareObservation,
    initialField: String?,
    accessLevel: Int,
    onSave: suspend (String, Int, Map<String, String>, String) -> Boolean,
    onDismiss: () -> Unit,
    partograph: Boolean = false
) {
    val control = state.feed.columnControls.firstOrNull { it.columnId == original.columnId }
    val fields = ((if (partograph) partographHeaderFields else guideHeaderFields) + deliveryOutcomeFields + (guideRows + partographRows).mapNotNull { row -> row.field?.let { it to row.label.replace('\n', ' ') } } +
        listOf("cervicalDilation" to "Cervical dilation", "descent" to "Descent", "pushingStarted" to "Pushing started"))
        .distinctBy { it.first }.flatMap { (field, label) -> intervalFieldOptions(field, label, partograph) }
        .filter { item -> (if (original.isGuideHeader) item.first in ((if (partograph) partographHeaderFields else guideHeaderFields) + deliveryOutcomeFields).map { it.first }
            else item.first !in allHeaderFields.map { it.first } || item.first in setOf("gravida", "rupturedHours")) && control?.canCorrectCell(item.first, accessLevel) == true }
    var values by remember(original.columnId, original.revision) {
        mutableStateOf(if (initialField != null) mapOf(initialField to original.correctionInputValue(initialField)) else emptyMap())
    }
    var reason by remember(original.columnId, original.revision) { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val busy = state.isSaving || submitting
    val stale = control?.revision != original.revision
    val validationError = clinicalEditError(values, original, original.isGuideHeader)
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Correct confirmed cells") },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
        confirmButton = {
            Button(enabled = !busy && !stale && state.organizationKeyAvailable && reason.isNotBlank() && values.isNotEmpty() && validationError == null &&
                values.keys.all { control.canCorrectCell(it, accessLevel) },
                onClick = {
                    submitting = true
                    scope.launch {
                        try { if (onSave(original.columnId, original.revision, values, reason.trim())) onDismiss() }
                        finally { submitting = false }
                    }
                }) { Text(if (busy) "Saving…" else "Save correction") }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatUiTimestamp(original.timestamp))
                AccessLevelText("Choose up to four cells per correction. Your access level must meet the recorded access level of every selected cell.", style = MaterialTheme.typography.bodySmall)
                if (fields.isEmpty()) AccessLevelText("No cells are eligible for correction at your access level.")
                if (stale) Text("This column changed. Close and reopen the correction to review the latest values.")
                Box {
                    TextButton(onClick = { expanded = true }, enabled = !busy && !stale && values.size < 4 && fields.any { it.first !in values }) {
                        Text("Add cell")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        fields.filter { it.first !in values }.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                values = values + (key to original.correctionInputValue(key)); expanded = false
                            })
                        }
                    }
                }
                values.forEach { (key, value) ->
                    val label = fields.firstOrNull { it.first == key }?.second ?: key
                    Text("Previous: ${original.historyValue(key).ifBlank { "(blank)" }}", style = MaterialTheme.typography.bodySmall)
                    if (key == "pushingStarted") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = value == "true", onCheckedChange = { values = values + (key to if (it) "true" else "") }, enabled = !busy && !stale)
                            Text(label)
                        }
                    } else if (partograph && key.removeSuffix("Half1").removeSuffix("Half2") == "contractionDuration") {
                        var durationExpanded by remember(key) { mutableStateOf(false) }
                        Box {
                            OutlinedButton(enabled = !busy && !stale, onClick = { durationExpanded = true }) {
                                Text(partographDuration(value)?.label ?: "Select duration")
                            }
                            DropdownMenu(durationExpanded, { durationExpanded = false }) {
                                PartographDuration.entries.forEach { duration ->
                                    DropdownMenuItem(text = { Text(duration.label) }, onClick = {
                                        values = values + (key to duration.storedValue)
                                        durationExpanded = false
                                    })
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(value = value, onValueChange = { values = values + (key to it) },
                            label = { Text(label) }, enabled = !busy && !stale, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { values = values - key }, enabled = !busy) { Text("Remove cell") }
                }
                OutlinedTextField(value = reason, onValueChange = { reason = it.take(2000) },
                    label = { Text("Reason (required)") }, minLines = 2, enabled = !busy, modifier = Modifier.fillMaxWidth())
                validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.feed.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    )
}

private fun LabourCareObservation.correctionInputValue(key: String): String =
    if (key == "pushingStarted") { if (pushingStarted) "true" else "" } else historyValue(key)

@Composable
private fun HeaderCell(
    sessionId: String, field: String, label: String, value: String, enabled: Boolean, modifier: Modifier,
    onSave: (String) -> Unit, onHistory: () -> Unit, readOnly: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    var draft by remember(sessionId, field) { mutableStateOf(value) }
    var previousValue by remember(sessionId, field) { mutableStateOf(value) }
    var focused by remember(sessionId, field) { mutableStateOf(false) }
    LaunchedEffect(value) {
        // Acknowledging an earlier autosave must not replace a newer local edit.
        if (draft == previousValue) draft = value
        previousValue = value
    }
    fun saveIfChanged(input: String) {
        if (!readOnly && input != value && validHeaderValue(field, input)) onSave(input)
    }
    val historyIcon: @Composable () -> Unit = {
        IconButton(onClick = { focusManager.clearFocus(); onHistory() }) {
            Icon(Icons.Outlined.History, contentDescription = "View $label edit history")
        }
    }
    Column(modifier) {
        when (field) {
            "deliveryMethod", "babySex", "babyStatus" -> {
                var expanded by remember { mutableStateOf(false) }
                val options = when (field) {
                    "deliveryMethod" -> deliveryMethods
                    "babySex" -> listOf("Male", "Female", "Indeterminate")
                    else -> listOf("Live birth", "Stillbirth")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                            Text("$label: ${draft.ifBlank { "Select" }}")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            (listOf("") + options).forEach { option ->
                                DropdownMenuItem(text = { Text(option.ifBlank { "Clear" }) }, onClick = {
                                    focusManager.clearFocus(); draft = option; saveIfChanged(option); expanded = false
                                })
                            }
                        }
                    }
                    historyIcon()
                }
            }
            "labourOnset" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    historyIcon()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("spontaneous", "induced").forEach { onset ->
                        FilterChip(selected = draft.equals(onset, ignoreCase = true), enabled = enabled,
                            onClick = { focusManager.clearFocus(); draft = onset; saveIfChanged(onset) }, label = { Text(onset.replaceFirstChar { it.uppercase() }) })
                    }
                }
            }
            "deliveryDateTime", "activeLabourDiagnosis", "rupturedMembranes" -> DateTimeField(draft, { selected ->
                draft = selected
                saveIfChanged(selected)
            }, label, enabled = enabled, trailingContent = historyIcon)
            else -> {
                val numeric = field in setOf("gravida", "parity", "livingChildren", "gestationalWeeks", "gestationalDays", "rupturedHours")
                OutlinedTextField(draft, { input ->
                    if (!numeric || (input.length <= 12 && input.all { it in '0'..'9' || (field == "rupturedHours" && it == '.') } && input.count { it == '.' } <= 1)) draft = input
                }, label = { Text(label) }, enabled = enabled,
                    readOnly = readOnly, modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
                        val leftField = focused && !state.isFocused
                        focused = state.isFocused
                        if (leftField) saveIfChanged(draft)
                    }, singleLine = field !in setOf("riskFactors", "babyDetails", "lscsIndication"),
                    trailingIcon = historyIcon,
                    keyboardOptions = KeyboardOptions(keyboardType = if (field == "rupturedHours") KeyboardType.Decimal else if (numeric) KeyboardType.Number else KeyboardType.Text),
                    isError = !validHeaderValue(field, draft),
                    supportingText = if (numeric) ({ Text(when (field) {
                        "gestationalDays" -> "0–6 days"
                        "rupturedHours" -> "0 or more hours"
                        else -> "0–99 · whole numbers"
                    }) }) else null)
            }
        }
    }
}

/** Empty presentation slots keep second stage under S1 without creating clinical records. */
internal fun firstStageSlotCount(observations: List<LabourCareObservation>): Int {
    val end = observations.indexOfFirst { it.fullDilationConfirmed }
    val firstStageCount = if (end >= 0) (observations[end].chartColumnIndex.takeIf { it >= 0 } ?: end) + 1 else (observations.maxOfOrNull { it.chartColumnIndex + 1 } ?: observations.size).coerceAtLeast(observations.size)
    return if (firstStageCount >= 11) 13 else 12
}

internal fun List<LabourCareObservation>.withSecondStageGap(
    firstStageSlots: Int = firstStageSlotCount(this)
): List<LabourCareObservation> {
    val end = indexOfFirst { it.fullDilationConfirmed }
    if (end < 0 || end >= firstStageSlots - 1) return this
    val placeholder = this[end].copy(id = "", columnId = "", timestamp = "", cellKey = "skipped",
        initials = "", halfHourCells = emptyMap(), recordedCellKeys = emptySet(), partographCells = emptyMap(),
        fullDilationConfirmed = false, birthConfirmed = false)
    return take(end + 1) + List(firstStageSlots - end - 1) { placeholder } + drop(end + 1)
}

private enum class GuideConfirmation(val title: String, val consequence: String) {
    Column("Sign off column", "This records your sign-off for this time column. An existing sign-off can only be superseded by a higher access level. Earlier sign-offs remain in history. Cells remain editable according to their individual access levels."),
    FullDilation("Confirm full dilation", "This records full dilation and advances the guide to the second stage. This transition cannot be undone."),
    Birth("Confirm birth", "This marks birth at this column time. Earlier observations can still be completed; new observations after this time are unavailable.")
}

@Composable
private fun GuideConfirmationDialog(
    action: GuideConfirmation,
    patient: String,
    timestamp: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(action.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Patient: $patient")
                Text("Time column: ${formatUiTimestamp(timestamp)}")
                Text(action.consequence)
                if (!enabled) Text("Confirmation unavailable. Wait for saving to finish and check your access and column status.")
            }
        },
        confirmButton = {
            MgButton(onClick = onConfirm, enabled = enabled, text = action.title)
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun chartCellAvailable(observation: LabourCareObservation?, key: String,
    nowMillis: Long = `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis()): Boolean =
    (observation == null || (observation.cellKey != "skipped" && !observation.stageEntryLocked)) &&
    (observation == null || observationTime(observation.timestamp, listOf(key))?.let {
        it <= nowMillis
    } == true)
