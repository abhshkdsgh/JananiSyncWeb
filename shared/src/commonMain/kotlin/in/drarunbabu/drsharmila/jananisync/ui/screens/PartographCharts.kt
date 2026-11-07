package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.data.AdmissionColumnState
import `in`.drarunbabu.drsharmila.jananisync.data.partographEntrySequence
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation
import `in`.drarunbabu.drsharmila.jananisync.data.model.LabourColumnControl
import `in`.drarunbabu.drsharmila.jananisync.ui.components.*
import `in`.drarunbabu.drsharmila.jananisync.ui.formatUiTimestamp
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Instant

private data class ColumnParameter(val field: String, val label: String, val half: Int = 1)
private val columnParameters = listOf(
    ColumnParameter("fetalHeartRate", "Fetal heart rate"),
    ColumnParameter("amnioticFluid", "Amniotic fluid"),
    ColumnParameter("moulding", "Moulding"),
    ColumnParameter("cervicalDilation", "Cervix · X"),
    ColumnParameter("descent", "Head descent · O"),
    ColumnParameter("contractionFrequency", "Contractions"),
    ColumnParameter("oxytocin", "Oxytocin U/L · drops/min"),
    ColumnParameter("medicine", "Drugs given"),
    ColumnParameter("intravenousFluids", "IV fluids"),
    ColumnParameter("pulse", "Pulse"),
    ColumnParameter("systolic", "Blood pressure"),
    ColumnParameter("temperature", "Temperature °C"),
    ColumnParameter("urineProtein", "Urine protein"),
    ColumnParameter("urineAcetone", "Urine acetone"),
    ColumnParameter("urineVolume", "Urine volume (ml)"),
).flatMap { parameter ->
    if (parameter.field in partographHalfHourlyFields) listOf(
        parameter.copy(label = "${parameter.label} · first half-hour"),
        parameter.copy(label = "${parameter.label} · +30 min", half = 2)
    ) else listOf(parameter)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PartographCharts(
    observations: List<LabourCareObservation>, controls: List<LabourColumnControl>, columnState: AdmissionColumnState,
    accessLevel: Int, busy: Boolean,
    onSave: suspend (String, String, Map<String, String>) -> Boolean, onConfirmColumn: (String) -> Unit,
    onCorrectColumn: (LabourCareObservation, String?) -> Unit,
    womanDocumentId: String = "", chartStartTimestamp: String = "",
    activeColumnId: String = "", onSelectColumn: (String) -> Unit = {}, nowMillis: Long = currentTimeMillis(),
    onFullDilation: (String) -> Unit = {}, onBirth: (String) -> Unit = {}
) {
    val density = LocalDensity.current
    val fallbackTime = `in`.drarunbabu.drsharmila.jananisync.data.chartMillis(chartStartTimestamp) ?: remember { currentTimeMillis() }
    val origin = `in`.drarunbabu.drsharmila.jananisync.data.chartMillis(chartStartTimestamp) ?: partographOrigin(observations, fallbackTime)
    val entrySequence = observations.partographEntrySequence(controls, womanDocumentId).let { if (it.baselineMillis == null) it.copy(baselineMillis = origin) else it }
    val nextEntryTime = entrySequence.nextTimestampMillis(fallbackTime)
    var nextParameter by remember { mutableStateOf(columnParameters.first()) }
    var nextParameterExpanded by remember { mutableStateOf(false) }
    var columnExpanded by remember { mutableStateOf(false) }
    var selectedEntryId by remember { mutableStateOf(activeColumnId) }
    LaunchedEffect(activeColumnId) { selectedEntryId = activeColumnId }
    val availableColumns = availablePartographColumns(observations, origin, nowMillis)
    val selectedEntryColumn = availableColumns.firstOrNull { it.id == selectedEntryId }
        ?: availableColumns.firstOrNull { `in`.drarunbabu.drsharmila.jananisync.data.chartMillis(it.timestamp) == nextEntryTime }
        ?: availableColumns.lastOrNull()
    val parameterOptions = columnParameters.filter { parameter -> selectedEntryColumn?.let {
        (`in`.drarunbabu.drsharmila.jananisync.data.chartMillis(it.timestamp) ?: Long.MAX_VALUE) + (parameter.half - 1) * 1_800_000L <= nowMillis
    } == true }
    val selectedParameter = nextParameter.takeIf { it in parameterOptions } ?: parameterOptions.firstOrNull()
    var cervixField by remember { mutableStateOf("cervicalDilation") }
    var vitalField by remember { mutableStateOf("pulse") }
    var preview by remember { mutableStateOf<PartographMark?>(null) }
    var chartZoom by remember { mutableStateOf(1f) }
    val currentZoom by rememberUpdatedState(chartZoom)
    val chartScale = PartographLayout.DISPLAY_SCALE * chartZoom
    val compact = LocalAppWindowSize.current == AppWindowSize.Compact
    // Keep an open form in its original presentation while adaptive constraints settle or resize.
    // Switching dialog roots during measurement can leave Compose measuring a disposed layer.
    var previewUsesSheet by remember { mutableStateOf(false) }
    var overlayAt by remember { mutableStateOf(IntOffset.Zero) }
    var message by remember { mutableStateOf<String?>(null) }
    var submitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val disabled = busy || submitting
    val transitionColumn = observations.filter { !it.isGuideHeader && it.cellKey !in setOf("scheduled", "skipped") && it.columnId.isNotBlank() }
        .maxWithOrNull(compareBy({ it.chartColumnIndex }, { `in`.drarunbabu.drsharmila.jananisync.data.chartMillis(it.timestamp) ?: 0L }))

    val signableObservations = observations.filter { observation ->
        controls.firstOrNull { it.columnId == observation.columnId }?.canSignOff(accessLevel) == true
    }
    val openObservations = observations.filter { it.columnId in columnState.openColumnIds }
    val outsideCount = observations.count { observation ->
        val time = runCatching { Instant.parse(observation.timestamp).toEpochMilliseconds() }.getOrNull()
        time != null && (time - origin) / 3_600_000.0 !in 0.0..PartographLayout.HOURS.toDouble()
    }
    fun blockMessage(mark: PartographMark): String? {
        if (`in`.drarunbabu.drsharmila.jananisync.data.observationTime(mark.timestamp, mark.values.keys)?.let { it > currentTimeMillis() } != false)
            return "Future observations cannot be recorded. This time is not available yet."
        return mark.editBlockMessage(columnState, controls, accessLevel)
            ?: if (mark.columnId.isBlank() || mark.columnId.startsWith("schedule:")) entrySequence.creationBlockMessage(mark.timestamp) else null
    }
    fun selectGraphParameter(field: String) {
        when (field) {
            "cervicalDilation", "descent" -> cervixField = field
            "pulse", "systolic", "diastolic" -> vitalField = field
        }
    }
    fun selectEntryColumn(id: String) {
        selectedEntryId = id
        onSelectColumn(id)
    }
    fun showSelectedParameter() {
        val column = selectedEntryColumn ?: return
        val parameter = selectedParameter ?: return
        val mark = if (column.existing) buildPartographMarkForColumn(observations, origin, column.id, parameter.field, parameter.half)
            else if (parameter.half == 2) buildPartographMarkForNewColumn(origin, column.timestamp, parameter.field, column.id).let { mark ->
                mark.copy(hour = column.hour + 0.5, values = mark.values.mapKeys { (key, _) -> key.removeSuffix("Half1") + "Half2" })
            }
            else buildPartographMarkForNewColumn(origin, column.timestamp, parameter.field, column.id)
        if (mark == null) { message = "This column is still loading. Try again when its recorded time is available."; return }
        blockMessage(mark)?.let { message = it; return }
        if (preview == null) previewUsesSheet = compact
        selectGraphParameter(mark.field)
        preview = mark
        selectEntryColumn(column.id)
        message = null
        overlayAt = with(density) { IntOffset((PartographLayout.PLOT_LEFT * chartScale).dp.roundToPx(), (30 * chartScale).dp.roundToPx()) }
    }
    fun erasableKeys(mark: PartographMark): Set<String> {
        val half = if (mark.values.keys.any { it.endsWith("Half2") }) 2 else 1
        val saved = buildPartographMarkForColumn(observations, origin, mark.columnId, mark.field, half) ?: return emptySet()
        return saved.values.filter { (key, value) -> value.isNotBlank() && key in mark.values &&
            mark.erasingValue(key)?.let { blockMessage(it) == null } == true }.keys
    }
    fun persistMark(mark: PartographMark, erasing: Boolean = false) {
        if (disabled) return
        blockMessage(mark)?.let { message = it; return }
        if (!erasing && !mark.isComplete()) { message = "Choose valid values for all required parameters before saving the mark."; return }
        submitting = true
        scope.launch {
            try {
                if (onSave(mark.columnId, mark.timestamp, mark.values)) {
                    preview = null
                    message = if (erasing) "Value erased. History retained." else "Mark saved."
                } else message = "The change was not saved. Check the message below and try again."
            } finally { submitting = false }
        }
    }
    fun savePreview() { preview?.let { persistMark(it) } }
    fun erasePreviewValue(key: String) {
        val mark = preview ?: return
        if (key !in erasableKeys(mark)) return
        mark.erasingValue(key)?.let { persistMark(it, erasing = true) }
    }
    val onTap by rememberUpdatedState<(Offset) -> Unit> { position ->
        if (!disabled) {
            val dx = with(density) { (position.x / chartScale).toDp().value }
            val dy = with(density) { (position.y / chartScale).toDp().value }
            val hit = partographHit(dx, dy, cervixField, vitalField)
            if (hit != null) {
                val candidate = buildPartographEntryMark(
                    observations, origin,
                    PartographLayout.xToElapsedHour(dx), hit.first, hit.second, fallbackTime, selectedEntryId
                )
                val blocked = blockMessage(candidate)
                if (blocked != null) message = blocked
                else {
                    if (preview == null) previewUsesSheet = compact
                    selectGraphParameter(candidate.field)
                    preview = candidate
                    selectEntryColumn(candidate.columnId.ifBlank {
                        `in`.drarunbabu.drsharmila.jananisync.data.scheduledColumnId(
                            `in`.drarunbabu.drsharmila.jananisync.data.chartMillis(candidate.timestamp)!!
                        )
                    })
                    message = null
                    overlayAt =
                        IntOffset(position.x.roundToInt(), (position.y + with(density) { 18.dp.toPx() }).roundToInt())
                }
            }
        }
    }
    val chartScroll = rememberScrollState()
    val chartScope = rememberCoroutineScope()
    var chartViewportWidth by remember { mutableStateOf(0) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var chartHour by remember { mutableStateOf(0) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val action = MaterialTheme.colorScheme.error
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Modified WHO Partograph", style = MaterialTheme.typography.titleSmall)
        Text("Tap anywhere within a time column: its horizontal band selects the time, and the vertical position selects the value. Plotted observations use half-hour bands; drugs, IV fluids and urine entries use whole-hour bands. Review and save in the entry form; saved readings remain editable according to per-cell access. Pinch with two fingers or use the zoom buttons; swipe to navigate. Cervix: X · head descent: O · pulse: dots · blood pressure: paired arrows. All readings plot at their recorded time columns.", style = MaterialTheme.typography.bodySmall)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter chart data", style = MaterialTheme.typography.titleSmall)
                WrappingActions {
                    Box {
                        OutlinedButton(enabled = !disabled && availableColumns.isNotEmpty(), onClick = { columnExpanded = true }) {
                            Text(selectedEntryColumn?.let { column ->
                                val hour = if (column.hour % 1.0 == 0.0) column.hour.toInt().toString() else ((column.hour * 100).roundToInt() / 100.0).toString()
                                "Hour $hour · ${formatUiTimestamp(column.timestamp)}"
                            } ?: "No available columns")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(columnExpanded, { columnExpanded = false }, Modifier.heightIn(max = 320.dp)) {
                            availableColumns.forEach { column ->
                                val hour = if (column.hour % 1.0 == 0.0) column.hour.toInt().toString() else ((column.hour * 100).roundToInt() / 100.0).toString()
                                DropdownMenuItem(text = { Text("Hour $hour · ${formatUiTimestamp(column.timestamp)}${if (column.existing) "" else " · empty"}") }, onClick = {
                                    selectEntryColumn(column.id)
                                    preview = null
                                    columnExpanded = false
                                })
                            }
                        }
                    }
                    Box {
                        OutlinedButton(enabled = !disabled && selectedParameter != null, onClick = { nextParameterExpanded = true }) {
                            Text(selectedParameter?.label ?: "Select parameter")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(nextParameterExpanded, { nextParameterExpanded = false }, Modifier.heightIn(max = 320.dp)) {
                            parameterOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                    nextParameter = option
                                    selectGraphParameter(option.field)
                                    preview = null
                                    nextParameterExpanded = false
                                })
                            }
                        }
                    }
                    Button(enabled = !disabled && selectedParameter != null && selectedEntryColumn != null &&
                        (!selectedEntryColumn.existing || controls.any { it.columnId == selectedEntryColumn.id }),
                        onClick = ::showSelectedParameter) { Text("Record parameter") }
                }
                if (selectedEntryColumn?.existing == true && controls.none { it.columnId == selectedEntryColumn.id })
                    Text("Column access details are syncing.", style = MaterialTheme.typography.bodySmall)
                Text("Choose an available time column and parameter, or tap the graph. Future readings are unavailable.", style = MaterialTheme.typography.bodySmall)
            }
        }
        entrySequence.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Contraction duration: dotted <20 s · hatched 20–40 s · solid >40 s. Each shaded box is one contraction in 10 minutes.", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (outsideCount > 0) Text("$outsideCount recorded time point(s) outside this 12-hour view; available in Cell edit history.", style = MaterialTheme.typography.bodySmall)
        if (compact) {
            Text("Cervical dilation overview · 0–12 hours · 4–10 cm", style = MaterialTheme.typography.labelMedium)
            Canvas(Modifier.fillMaxWidth().height(120.dp)) {
                val inset = 12.dp.toPx()
                val w = size.width - inset * 2
                val h = size.height - inset * 2
                for (hour in 0..PartographLayout.HOURS) drawLine(grid, Offset(inset + w * hour / PartographLayout.HOURS, inset), Offset(inset + w * hour / PartographLayout.HOURS, inset + h))
                for (cm in 4..10) drawLine(grid, Offset(inset, inset + h * (10 - cm) / 6), Offset(inset + w, inset + h * (10 - cm) / 6))
                val points = partographPoints(observations, "cervicalDilation").filter { it.value in 4.0..10.0 }.map {
                    Offset(inset + w * (it.hour / PartographLayout.HOURS).toFloat(), inset + h * ((10 - it.value) / 6).toFloat())
                }
                points.zipWithNext().forEach { (a, b) -> drawLine(primary, a, b, strokeWidth = 2.dp.toPx()) }
                points.forEach { drawCircle(primary, 4.dp.toPx(), it) }
            }
            Text("Swipe horizontally to inspect the chart, or jump to an hour.")
            WrappingActions {
                OutlinedButton(enabled = chartHour > 0, onClick = {
                    chartHour--
                    chartScope.launch { chartScroll.animateScrollTo(with(density) { (chartHour * PartographLayout.PLOT_WIDTH / PartographLayout.HOURS * chartScale).dp.roundToPx() }) }
                }) { Text("Earlier") }
                Text("Hour $chartHour")
                OutlinedButton(enabled = chartHour < PartographLayout.HOURS, onClick = {
                    chartHour++
                    chartScope.launch { chartScroll.animateScrollTo(with(density) { (chartHour * PartographLayout.PLOT_WIDTH / PartographLayout.HOURS * chartScale).dp.roundToPx() }) }
                }) { Text("Later") }
            }
        }
        if (transitionColumn != null && observations.none { it.birthConfirmed }) {
            val secondStage = observations.any { it.fullDilationConfirmed }
            OutlinedButton(enabled = !disabled && accessLevel in 10..99,
                onClick = { if (secondStage) onBirth(transitionColumn.columnId) else onFullDilation(transitionColumn.columnId) }) {
                Text(if (secondStage) "Birth: Confirm and mark birth" else "Full Dilation: Confirm and skip to second stage")
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Chart zoom · ${(chartZoom * 100).roundToInt()}%", style = MaterialTheme.typography.titleLarge)
                Text("Make the chart larger or smaller using the buttons below.", style = MaterialTheme.typography.bodyMedium)
                WrappingActions {
                    Button(
                        modifier = Modifier.heightIn(min = 48.dp),
                        enabled = chartZoom < partographZoomLevels.last(),
                        onClick = { chartZoom = partographZoomStep(chartZoom, zoomIn = true) }
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Zoom in", style = MaterialTheme.typography.titleMedium)
                    }
                    FilledTonalButton(
                        modifier = Modifier.heightIn(min = 48.dp),
                        enabled = chartZoom > partographZoomLevels.first(),
                        onClick = { chartZoom = partographZoomStep(chartZoom, zoomIn = false) }
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Zoom out", style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(modifier = Modifier.heightIn(min = 48.dp), onClick = { chartZoom = 1f }) {
                        Text("Reset to 100%")
                    }
                }
                Text("You can also pinch with two fingers to zoom. Swipe horizontally to see later time columns.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Row(Modifier.fillMaxWidth().onSizeChanged { chartViewportWidth = it.width }.horizontalScroll(chartScroll)) {
            Box(Modifier.padding(bottom = if (signableObservations.isNotEmpty()) 200.dp else 0.dp)) {
                Canvas(Modifier.width((PartographLayout.CHART_WIDTH * chartScale).dp).height((PartographLayout.CHART_HEIGHT * chartScale).dp)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    chartZoom = (currentZoom * event.calculateZoom()).coerceIn(partographZoomLevels.first(), partographZoomLevels.last())
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures { onTap(it) } }) {
                    scale(chartScale, pivot = Offset.Zero) {
                    fun x(hour: Double) = PartographLayout.hourToX(hour).dp.toPx()
                    fun y(section: PartographSection, value: Double) = section.y(value).dp.toPx()
                    val left = PartographLayout.PLOT_LEFT.dp.toPx()
                    val right = PartographLayout.PLOT_RIGHT.dp.toPx()
                    val width = PartographLayout.PLOT_WIDTH.dp.toPx()
                    fun label(value: String, x: Float, y: Float, color: Color = ink, fontSize: Float = 11f) {
                        drawText(measurer, value, Offset(x, y), style = TextStyle(color = color, fontSize = fontSize.sp))
                    }
                    fun verticalGrid(top: Float, bottom: Float, cells: Int = PartographLayout.HALF_HOURS) {
                        for (i in 0..cells) drawLine(if (cells == PartographLayout.HALF_HOURS && i % 2 != 0) grid.copy(alpha = 0.7f) else grid,
                            Offset(left + width * i / cells, top), Offset(left + width * i / cells, bottom))
                    }
                    fun chart(section: PartographSection, step: Int, title: String) {
                        val top = section.top.dp.toPx()
                        val bottom = section.bottom.dp.toPx()
                        label(title, 0f, top + 4.dp.toPx(), fontSize = 10f)
                        verticalGrid(top, bottom)
                        for (value in section.min.toInt()..section.max.toInt() step step) {
                            val at = y(section, value.toDouble())
                            drawLine(grid, Offset(left, at), Offset(right, at))
                            label(value.toString(), left - 28.dp.toPx(), at - 7.dp.toPx())
                        }
                    }
                    fun series(field: String, section: PartographSection, half: Boolean = field in partographHalfHourlyFields, symbol: String = "", color: Color = primary) {
                        var previous: Offset? = null
                        partographPoints(observations, field, half).forEach { point ->
                            if ((field == "descent" && point.value !in 0.0..5.0) || (field == "cervicalDilation" && point.value !in 4.0..10.0)) { previous = null; return@forEach }
                            val at = Offset(x(point.hour), y(section, point.value))
                            if (point.value !in section.min..section.max) label("${point.value.toInt()}${if (point.value > section.max) "↑" else "↓"}", at.x, at.y - 14.dp.toPx(), color)
                            previous?.let { drawLine(color, it, at, 2.dp.toPx()) }
                            when (symbol) {
                                "X" -> {
                                    val d = 4.dp.toPx()
                                    drawLine(color, at - Offset(d, d), at + Offset(d, d), 2.dp.toPx())
                                    drawLine(color, at + Offset(-d, d), at + Offset(d, -d), 2.dp.toPx())
                                }
                                "O" -> drawCircle(color, 4.dp.toPx(), at, style = Stroke(2.dp.toPx()))
                                else -> drawCircle(color, 3.dp.toPx(), at)
                            }
                            previous = at
                        }
                    }
                    fun pressure(hour: Double, systolic: Double, diastolic: Double, color: Color, stroke: Float = 2f) {
                        val px = x(hour)
                        val sys = y(PartographLayout.vitals, systolic)
                        val dia = y(PartographLayout.vitals, diastolic)
                        drawLine(color, Offset(px, sys), Offset(px, dia), stroke.dp.toPx())
                        val d = 4.dp.toPx()
                        drawLine(color, Offset(px - d, sys + d), Offset(px, sys), stroke.dp.toPx())
                        drawLine(color, Offset(px + d, sys + d), Offset(px, sys), stroke.dp.toPx())
                        drawLine(color, Offset(px - d, dia - d), Offset(px, dia), stroke.dp.toPx())
                        drawLine(color, Offset(px + d, dia - d), Offset(px, dia), stroke.dp.toPx())
                        for (value in listOf(systolic, diastolic)) if (value !in PartographLayout.vitals.min..PartographLayout.vitals.max) label("${value.toInt()}${if (value > PartographLayout.vitals.max) "↑" else "↓"}", px, y(PartographLayout.vitals, value) - 14.dp.toPx(), color)
                    }
                    fun contractionBoxes(hour: Double, count: Int, duration: PartographDuration?, color: Color, preview: Boolean = false) {
                        if (hour !in 0.0..<PartographLayout.HOURS.toDouble() || count !in 0..5) return
                        val section = PartographLayout.contractions
                        val cellWidth = width / PartographLayout.HALF_HOURS
                        val cellHeight = section.height.dp.toPx() / 5
                        clipRect(left, section.top.dp.toPx(), right, section.bottom.dp.toPx()) {
                            for (box in 0 until count) {
                                val boxLeft = x(hour) + 1.5.dp.toPx()
                                val boxTop = section.bottom.dp.toPx() - (box + 1) * cellHeight + 1.5.dp.toPx()
                                val boxWidth = cellWidth - 3.dp.toPx()
                                val boxHeight = cellHeight - 3.dp.toPx()
                                if (duration != null) clipRect(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight) {
                                    when (duration) {
                                        PartographDuration.LONG -> drawRect(color, Offset(boxLeft, boxTop), Size(boxWidth, boxHeight))
                                        PartographDuration.SHORT -> {
                                            var dy = 2.dp.toPx()
                                            while (dy < boxHeight) {
                                                var dx = 2.dp.toPx()
                                                while (dx < boxWidth) { drawCircle(color, 0.8.dp.toPx(), Offset(boxLeft + dx, boxTop + dy)); dx += 5.dp.toPx() }
                                                dy += 5.dp.toPx()
                                            }
                                        }
                                        else -> {
                                            var offset = 0f
                                            while (offset < boxHeight + boxWidth) {
                                                drawLine(color, Offset(boxLeft, boxTop + offset), Offset(boxLeft + boxWidth, boxTop + offset - boxWidth), 1.dp.toPx())
                                                offset += 5.dp.toPx()
                                            }
                                        }
                                    }
                                }
                                if (preview) drawRect(color, Offset(boxLeft, boxTop), Size(boxWidth, boxHeight), style = Stroke(2.dp.toPx()))
                            }
                        }
                    }
                    fun highlightColumn(hour: Double, alpha: Float, selected: Boolean = false) {
                        // Both half-hour cells belong to the same full recording column.
                        val start = kotlin.math.floor(hour + 0.000001).coerceIn(0.0, PartographLayout.HOURS - 1.0)
                        val top = PartographLayout.fetal.top.dp.toPx()
                        val bottom = PartographLayout.lanes.last().bottom.dp.toPx()
                        val at = Offset(x(start), top)
                        val area = Size(x(start + 1) - x(start), bottom - top)
                        drawRect(primary.copy(alpha = alpha), at, area)
                        if (selected) drawRect(primary.copy(alpha = 0.55f), at, area, style = Stroke(1.5.dp.toPx()))
                    }
                    for (half in 0 until PartographLayout.HALF_HOURS) {
                        val hour = half / 2.0
                        if (origin + (hour * 3_600_000).toLong() > nowMillis) {
                            drawRect(grid.copy(alpha = 0.16f), Offset(x(hour), PartographLayout.fetal.top.dp.toPx()),
                                Size(x(hour + 0.5) - x(hour), (PartographLayout.lanes.last().bottom - PartographLayout.fetal.top).dp.toPx()))
                        }
                    }
                    if (columnState.canStartColumn && entrySequence.message == null && nextEntryTime <= nowMillis &&
                        entrySequence.creationBlockMessage(Instant.fromEpochMilliseconds(nextEntryTime).toString()) == null) {
                        val hour = (nextEntryTime - origin) / 3_600_000.0
                        if (hour in 0.0..PartographLayout.HOURS.toDouble()) {
                            highlightColumn(hour, 0.08f)
                            label("Hour ${entrySequence.nextHour} · entry", x(hour) + 3.dp.toPx(), 3.dp.toPx(), primary)
                        }
                    }
                    openObservations.forEach { observation ->
                        val time = runCatching { Instant.parse(observation.timestamp).toEpochMilliseconds() }.getOrNull()
                        val hour = time?.let { (it - origin) / 3_600_000.0 }
                        if (hour != null && hour in 0.0..PartographLayout.HOURS.toDouble()) {
                            highlightColumn(hour, 0.10f)
                            label("Open", x(hour) + 3.dp.toPx(), 3.dp.toPx(), primary)
                        }
                    }
                    if (preview == null) {
                        val selectedTime = observations.firstOrNull { it.columnId == selectedEntryId }?.timestamp
                            ?.let { `in`.drarunbabu.drsharmila.jananisync.data.chartMillis(it) }
                            ?: `in`.drarunbabu.drsharmila.jananisync.data.scheduledColumnTime(selectedEntryId)
                        selectedTime?.let { time ->
                            val hour = (time - origin) / 3_600_000.0
                            if (hour in 0.0..PartographLayout.HOURS.toDouble()) highlightColumn(hour, 0.14f, selected = true)
                        }
                    }
                    preview?.let { mark ->
                        val columnHour = runCatching {
                            (Instant.parse(mark.timestamp).toEpochMilliseconds() - origin) / 3_600_000.0
                        }.getOrDefault(mark.hour)
                        if (columnHour in 0.0..PartographLayout.HOURS.toDouble()) highlightColumn(columnHour, 0.14f, selected = true)
                    }
                    chart(PartographLayout.fetal, 10, "Fetal heart\nrate / min")
                    series("fetalHeartRate", PartographLayout.fetal, half = true)
                    chart(PartographLayout.cervix, 1, "Cervix (cm) · X\n4–10 cm")
                    label("Head descent · O\n(fifths palpable)", 0f, y(PartographLayout.cervix, 3.5), fontSize = 10f)
                    val cervix = PartographLayout.cervix
                    drawLine(ink, Offset(x(0.0), y(cervix, 4.0)), Offset(x(6.0), y(cervix, 10.0)), 2.dp.toPx())
                    drawLine(action, Offset(x(4.0), y(cervix, 4.0)), Offset(x(10.0), y(cervix, 10.0)), 2.dp.toPx())
                    label("Alert", x(2.0), y(cervix, 7.0) - 18.dp.toPx())
                    label("Action", x(6.0), y(cervix, 7.0) - 18.dp.toPx(), action)
                    series("cervicalDilation", cervix, symbol = "X")
                    series("descent", cervix, symbol = "O", color = ink)
                    val hoursTop = PartographLayout.HOURS_TOP.dp.toPx()
                    val clockTop = PartographLayout.CLOCK_TOP.dp.toPx()
                    val clockBottom = (PartographLayout.CLOCK_TOP + PartographLayout.CLOCK_HEIGHT).dp.toPx()
                    label("Elapsed hours", 0f, hoursTop + 5.dp.toPx(), fontSize = 10f)
                    label("Clock time", 0f, clockTop + 6.dp.toPx(), fontSize = 10f)
                    verticalGrid(hoursTop, clockBottom, PartographLayout.HOURS)
                    for (line in listOf(hoursTop, clockTop, clockBottom)) drawLine(grid, Offset(left, line), Offset(right, line))
                    for (hour in 0 until PartographLayout.HOURS) {
                        label(hour.toString(), x(hour.toDouble()) + 5.dp.toPx(), hoursTop + 4.dp.toPx())
                        val formatted = formatUiTimestamp(origin + hour * 3_600_000L)
                        label(formatted.substringAfterLast(' ').take(5), x(hour.toDouble()) + 4.dp.toPx(), clockTop + 3.dp.toPx(), fontSize = 10f)
                        val previousDate = formatUiTimestamp(origin + (hour - 1) * 3_600_000L).substringBeforeLast(' ')
                        if (hour == 0 || formatted.substringBeforeLast(' ') != previousDate) label(formatted.take(6), x(hour.toDouble()) + 4.dp.toPx(), clockTop + 18.dp.toPx(), fontSize = 9f)
                    }
                    label("${PartographLayout.HOURS} h", right + 5.dp.toPx(), hoursTop + 4.dp.toPx())
                    val endpointTime = formatUiTimestamp(origin + PartographLayout.HOURS * 3_600_000L)
                    label(endpointTime.substringAfterLast(' ').take(5), right + 4.dp.toPx(), clockTop + 3.dp.toPx(), fontSize = 10f)
                    val precedingTime = formatUiTimestamp(origin + (PartographLayout.HOURS - 1) * 3_600_000L)
                    if (endpointTime.substringBeforeLast(' ') != precedingTime.substringBeforeLast(' ')) label(endpointTime.take(6), right + 4.dp.toPx(), clockTop + 18.dp.toPx(), fontSize = 9f)
                    val contractions = PartographLayout.contractions
                    observations.forEach { observation ->
                        val time = runCatching { Instant.parse(observation.timestamp).toEpochMilliseconds() }.getOrNull() ?: return@forEach
                        val baseHour = (time - origin) / 3_600_000.0
                        for (half in 1..2) {
                            val count = observation.partographValue("contractionFrequencyHalf$half")?.toInt() ?: continue
                            val duration = observation.partographDuration(half) ?: continue
                            val hour = baseHour + (half - 1) * 0.5
                            contractionBoxes(hour, count, duration, ink)

                        }
                    }
                    label("Contractions\nper 10 minutes", 0f, contractions.top.dp.toPx() + 10.dp.toPx(), fontSize = 10f)
                    verticalGrid(contractions.top.dp.toPx(), contractions.bottom.dp.toPx())
                    for (box in 0..5) {
                        val at = contractions.bottom.dp.toPx() - contractions.height.dp.toPx() * box / 5
                        drawLine(grid, Offset(left, at), Offset(right, at))
                        if (box > 0) label(box.toString(), left - 25.dp.toPx(), at + contractions.height.dp.toPx() / 10 - 7.dp.toPx())
                    }
                    chart(PartographLayout.vitals, 10, "Pulse (dots)\nBP (mmHg)")
                    series("pulse", PartographLayout.vitals)
                    val lower = partographPoints(observations, "diastolic")
                    partographPoints(observations, "systolic").forEach { sys -> lower.firstOrNull { it.hour == sys.hour }?.let { pressure(sys.hour, sys.value, it.value, ink) } }
                    PartographLayout.lanes.forEach { lane ->
                        val top = lane.top.dp.toPx()
                        val bottom = lane.bottom.dp.toPx()
                        val halfHourly = lane.field in partographHalfHourlyFields
                        val cells = if (halfHourly) PartographLayout.HALF_HOURS else PartographLayout.HOURS
                        val cellWidth = width / cells
                        if (lane.field == "oxytocin") {
                            label("Oxytocin (U/L)", 0f, top + 3.dp.toPx(), fontSize = 10f)
                            label("Drops/min", 0f, (top + bottom) / 2 + 3.dp.toPx(), fontSize = 10f)
                            drawLine(grid, Offset(left, (top + bottom) / 2), Offset(right, (top + bottom) / 2))
                        } else label(lane.label, 0f, top + 7.dp.toPx(), fontSize = 10f)
                        verticalGrid(top, bottom, cells)
                        drawLine(grid, Offset(left, top), Offset(right, top))
                        drawLine(grid, Offset(left, bottom), Offset(right, bottom))
                        val entries = observations.flatMap { observation ->
                            val time = runCatching { Instant.parse(observation.timestamp).toEpochMilliseconds() }.getOrNull()
                            if (time == null) emptyList() else (if (halfHourly) 1..2 else 1..1).mapNotNull { half ->
                                val hour = (time - origin) / 3_600_000.0 + (half - 1) * 0.5
                                val field = if (halfHourly) "${lane.field}Half$half" else lane.field
                                val text = observation.partographDisplayText(field)
                                if (hour in 0.0..PartographLayout.HOURS.toDouble() && text.isNotBlank())
                                    Triple(hour, text, lane.field == "oxytocin" && text != observation.partographText(field)) else null
                            }
                        }.sortedBy { it.first }
                        entries.forEachIndexed { index, (hour, text, structuredOxytocin) ->
                                val nextHour = entries.getOrNull(index + 1)?.first
                                val availableWidth = nextHour?.let { minOf(cellWidth, x(it) - x(hour)) } ?: cellWidth
                                val layout = measurer.measure(text, TextStyle(color = ink, fontSize = if (halfHourly) 9.sp else 10.sp),
                                    maxLines = if (lane.height >= 60) 4 else if (lane.height >= 40) 2 else 1, overflow = TextOverflow.Ellipsis,
                                    constraints = Constraints(maxWidth = (availableWidth - 6.dp.toPx()).toInt().coerceAtLeast(1)))
                                val textLeft = (PartographLayout.laneTextAnchor(hour) + 3).dp.toPx()
                                val textRight = if (hour == PartographLayout.HOURS.toDouble()) (PartographLayout.CHART_WIDTH - 16).dp.toPx() else right
                                if (structuredOxytocin) {
                                    val rows = text.split('\n', limit = 2)
                                    rows.forEachIndexed { row, value ->
                                        val rowLayout = measurer.measure(value, TextStyle(color = ink, fontSize = 9.sp), maxLines = 1,
                                            overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (availableWidth - 6.dp.toPx()).toInt().coerceAtLeast(1)))
                                        clipRect(left, top, textRight, bottom) { drawText(rowLayout, topLeft = Offset(textLeft, top + row * (bottom - top) / 2 + 3.dp.toPx())) }
                                    }
                                } else clipRect(left, top, textRight, bottom) { drawText(layout, topLeft = Offset(textLeft, top + 5.dp.toPx())) }
                        }
                    }
                    preview?.takeIf { it.hour in 0.0..PartographLayout.HOURS.toDouble() }?.let { mark ->
                        val color = primary.copy(alpha = 0.7f)
                        val px = x(mark.hour)
                        drawLine(color, Offset(px, PartographLayout.fetal.top.dp.toPx()), Offset(px, PartographLayout.lanes.last().bottom.dp.toPx()), 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
                        fun previewDot(value: Double, section: PartographSection, symbol: String = "") {
                            val at = Offset(px, y(section, value))
                            if (symbol == "X") {
                                val d = 6.dp.toPx()
                                drawLine(color, at - Offset(d, d), at + Offset(d, d), 3.dp.toPx())
                                drawLine(color, at + Offset(-d, d), at + Offset(d, -d), 3.dp.toPx())
                            } else drawCircle(color, 6.dp.toPx(), at, style = Stroke(3.dp.toPx()))
                            label(value.toString(), px + 8.dp.toPx(), at.y - 16.dp.toPx(), primary)
                        }
                        val value = mark.values.entries.first().value.toDoubleOrNull()
                        when (mark.field) {
                            "fetalHeartRate" -> value?.let { previewDot(it, PartographLayout.fetal) }
                            "cervicalDilation", "descent" -> value?.let { previewDot(it, cervix, if (mark.field == "cervicalDilation") "X" else "O") }
                            "pulse" -> value?.let { previewDot(it, PartographLayout.vitals) }
                            "systolic", "diastolic" -> {
                                val sys = mark.parameterValue("systolic")?.toDoubleOrNull()
                                val dia = mark.parameterValue("diastolic")?.toDoubleOrNull()
                                if (sys != null && dia != null) pressure(mark.hour, sys, dia, color, 3f)
                                else { sys?.let { previewDot(it, PartographLayout.vitals) }; dia?.let { previewDot(it, PartographLayout.vitals) } }
                            }
                            "contractionFrequency" -> {
                                val count = mark.values.entries.first { it.key.startsWith("contractionFrequency") }.value.toIntOrNull() ?: 0
                                val duration = partographDuration(mark.values.entries.first { it.key.startsWith("contractionDuration") }.value)
                                contractionBoxes(mark.hour, count, duration, color, preview = true)
                                label("$count / 10 min · ${duration?.label ?: "Select duration"}", px, contractions.top.dp.toPx() - 14.dp.toPx(), primary)
                            }
                            else -> PartographLayout.lanes.firstOrNull { it.field == mark.field }?.let { lane ->
                                val text = mark.values.values.first().ifBlank { "Select a value" }
                                val layout = measurer.measure(text, TextStyle(color = primary, fontSize = 10.sp), maxLines = 3,
                                    overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (width / (if (mark.field in partographHalfHourlyFields) PartographLayout.HALF_HOURS else PartographLayout.HOURS) - 6.dp.toPx()).toInt().coerceAtLeast(1)))
                                val textLeft = (PartographLayout.laneTextAnchor(mark.hour) + 3).dp.toPx()
                                val textRight = if (mark.hour == PartographLayout.HOURS.toDouble()) (PartographLayout.CHART_WIDTH - 16).dp.toPx() else right
                                clipRect(left, lane.top.dp.toPx(), textRight, lane.bottom.dp.toPx()) { drawText(layout, topLeft = Offset(textLeft, (lane.top + 5).dp.toPx())) }
                            }
                        }
                    }
                }
                } // Scaled chart drawing
                Column(
                    Modifier.align(Alignment.TopStart)
                        .offset(y = ((PartographLayout.cervix.top + 48) * chartScale).dp)
                        .width(((PartographLayout.PLOT_LEFT - 32) * chartScale).dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("cervicalDilation" to "Cervix X", "descent" to "Descent O").forEach { (field, title) ->
                        FilterChip(
                            selected = cervixField == field,
                            onClick = { cervixField = field; preview = null },
                            label = { Text(title, fontSize = 18.sp) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        )
                    }
                }
                Column(
                    Modifier.align(Alignment.TopStart)
                        .offset(y = ((PartographLayout.vitals.top + 48) * chartScale).dp)
                        .width(((PartographLayout.PLOT_LEFT - 32) * chartScale).dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("pulse" to "Pulse", "systolic" to "BP").forEach { (field, title) ->
                        FilterChip(
                            selected = vitalField == field || (field == "systolic" && vitalField == "diastolic"),
                            onClick = { vitalField = field; preview = null },
                            label = { Text(title, fontSize = 18.sp) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        )
                    }
                }
                observations.forEach { observation ->
                    val control = controls.firstOrNull { it.columnId == observation.columnId }
                    val time = runCatching { Instant.parse(observation.timestamp).toEpochMilliseconds() }.getOrNull()
                    val hour = time?.let { (it - origin) / 3_600_000.0 }
                    if (control?.canCorrect(accessLevel) == true && hour != null && hour in 0.0..PartographLayout.HOURS.toDouble()) {
                        IconButton(onClick = { preview = null; onCorrectColumn(observation, null) }, enabled = !disabled,
                            modifier = Modifier.align(Alignment.TopStart).offset(x = (PartographLayout.hourToX(hour) * chartScale - 24).dp, y = 0.dp).size(48.dp)) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Correct confirmed column ${formatUiTimestamp(observation.timestamp)}", modifier = Modifier.size(16.dp))
                        }
                    }
                }
                signableObservations.distinctBy { it.columnId }.forEach { observation ->
                    val time = runCatching { Instant.parse(observation.timestamp).toEpochMilliseconds() }.getOrNull()
                    val hour = time?.let { (it - origin) / 3_600_000.0 }
                    val control = controls.firstOrNull { it.columnId == observation.columnId }
                    val canConfirm = control?.canSignOff(accessLevel) == true
                    if (hour != null && hour in 0.0..PartographLayout.HOURS.toDouble()) {
                        val start = kotlin.math.floor(hour + 0.000001).coerceIn(0.0, PartographLayout.HOURS - 1.0)
                        MgButton(
                            text = "Sign off column",
                            enabled = !disabled && canConfirm,
                            onClick = { preview = null; onConfirmColumn(observation.columnId) },
                            modifier = Modifier.align(Alignment.TopStart)
                                .offset(x = (PartographLayout.hourToX(start) * chartScale).dp,
                                    y = (PartographLayout.CHART_HEIGHT * chartScale + 8).dp)
                                .width((PartographLayout.PLOT_WIDTH / PartographLayout.HOURS * chartScale).dp)
                                .heightIn(min = 48.dp)
                        )
                    }
                }
                preview?.let { mark ->
                    if (!previewUsesSheet) {
                        val overlayRequester = remember { BringIntoViewRequester() }
                        LaunchedEffect(mark.columnId, mark.field, mark.timestamp, mark.hour) {
                            withFrameNanos { }
                            overlayRequester.bringIntoView()
                        }
                        Box(Modifier
                            .offset {
                                val canvasWidth = with(density) { (PartographLayout.CHART_WIDTH * chartScale).dp.roundToPx() }
                                val canvasHeight = with(density) { (PartographLayout.CHART_HEIGHT * chartScale).dp.roundToPx() }
                                val viewportWidth = chartViewportWidth.takeIf { it > 0 } ?: canvasWidth
                                val leftBound = chartScroll.value.coerceAtMost(canvasWidth)
                                val rightBound = minOf(canvasWidth, leftBound + viewportWidth)
                                IntOffset(
                                    overlayAt.x.coerceIn(leftBound, (rightBound - overlaySize.width).coerceAtLeast(leftBound)),
                                    overlayAt.y.coerceIn(0, (canvasHeight - overlaySize.height).coerceAtLeast(0))
                                )
                            }
                            .bringIntoViewRequester(overlayRequester)
                            .zIndex(1f)
                            .widthIn(max = with(density) { chartViewportWidth.takeIf { it > 0 }?.toDp() ?: 680.dp }.coerceAtMost(680.dp))
                            .heightIn(max = PartographLayout.CHART_HEIGHT.dp)
                            .onSizeChanged { overlaySize = it }
                            .pointerInput(Unit) { detectTapGestures { } }
                            .verticalScroll(rememberScrollState())) {
                            PartographParameterOverlay(mark, disabled, { changed ->
                                preview = changed
                            }, { savePreview() }, { preview = null }, saveEnabled = blockMessage(mark) == null, erasableKeys = erasableKeys(mark), onErase = ::erasePreviewValue)
                        }
                    }
                }
            }
        }
        if (previewUsesSheet) preview?.let { mark ->
            ModalBottomSheet(onDismissRequest = { if (!disabled) preview = null }, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)) {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(bottom = 24.dp)) {
                    PartographParameterOverlay(mark, disabled, { changed ->
                        preview = changed
                    }, { savePreview() }, { preview = null }, saveEnabled = blockMessage(mark) == null, erasableKeys = erasableKeys(mark), onErase = ::erasePreviewValue)
                }
            }
        }
    }
}
