package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.data.chartMillis
import `in`.drarunbabu.drsharmila.jananisync.ui.*
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgButton
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgText
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.LabourCareUiState
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.time.Instant

internal expect fun openChartExport(pages: String, title: String): Boolean

@Composable
internal fun ChartExportActions(state: LabourCareUiState, observations: List<LabourCareObservation>,
    header: LabourCareObservation?, partograph: Boolean, firstStageSlots: Int) {
    var failed by remember(state.orgId, state.patientSessionId) { mutableStateOf(false) }
    MgButton(
        enabled = state.organizationKeyAvailable && !state.feed.isLoading
                && state.feed.message == null && !state.isSaving
                && observations.any { it.cellKey !in listOf("scheduled", "skipped") },
        onClick = {
            val pages = clinicalChartPages(state, observations, header, partograph, firstStageSlots)
            failed = !openChartExport(Json.encodeToString(ListSerializer(String.serializer()), pages),
                if (partograph) "Modified WHO Partograph" else "WHO Labour Care Guide")
        },
        text = "Export PDF / Print"
    )
    if (failed) MgText(text= "Allow pop-ups for this site, then try exporting again.", textColor = MaterialTheme.colorScheme.error)
}

internal fun xml(value: String): String = value.replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F]"), "").replace("&", "&amp;").replace("<", "&lt;")
    .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

/** A separate white paper surface; unaffected by app theme, zoom or viewport. */
private class Paper {
    val body = StringBuilder()
    val notes = mutableListOf<String>()
    fun line(x: Number, y: Number, xx: Number, yy: Number) { body.append("<path d='M$x $y L$xx $yy' fill='none' stroke='#222' stroke-width='0.7'/>") }
    fun box(x: Number, y: Number, w: Number, h: Number, fill: String = "none") { body.append("<rect x='$x' y='$y' width='$w' height='$h' fill='$fill' stroke='#222' stroke-width='0.7'/>") }
    fun text(value: String, x: Number, y: Number, size: Int = 12, centered: Boolean = false) {
        val alignment = if (centered) " text-anchor='middle' dominant-baseline='middle'" else ""
        body.append("<text x='$x' y='$y' font-family='Arial,sans-serif' font-size='$size' fill='#111'$alignment>${xml(value)}</text>")
    }
    fun cell(value: String, x: Double, y: Double, w: Double, h: Double, size: Int = 10) {
        if (value.isBlank()) return
        val count = (w / (size * 0.9)).toInt().coerceAtLeast(1)
        val lines = value.lines().flatMap { it.chunked(count) }
        val capacity = ((h - 4) / (size + 2)).toInt().coerceAtLeast(1)
        val displayedLines = if (lines.size > capacity) {
            notes.add(value)
            listOf("[${notes.size}]")
        } else lines
        val lineHeight = size + 2.0
        val firstLineY = y + h / 2 - (displayedLines.size - 1) * lineHeight / 2
        displayedLines.forEachIndexed { i, s ->
            text(s, x + w / 2, firstLineY + i * lineHeight, size, centered = true)
        }
    }
    fun svg() = "<svg xmlns='http://www.w3.org/2000/svg' width='1190' height='1684' viewBox='0 0 1190 1684'><rect width='1190' height='1684' fill='white'/>$body<rect x='0' y='1658' width='1190' height='26' fill='white'/><text x='595' y='1676' text-anchor='middle' font-family='Arial,sans-serif' font-size='13' fill='#9b1c1c'>Work in progress — Dummy data only. Not for clinical use.</text></svg>"
}

internal fun clinicalChartPages(state: LabourCareUiState, observations: List<LabourCareObservation>,
    header: LabourCareObservation?, partograph: Boolean, firstStageSlots: Int): List<String> {
    val pages = mutableListOf<String>()
    val origin = observations.mapNotNull { chartMillis(it.chartStartTimestamp) }.minOrNull()
        ?: observations.mapNotNull { chartMillis(it.timestamp) }.minOrNull() ?: 0L
    val chunks = if (partograph) {
        val lastHour = observations.mapNotNull { o -> chartMillis(o.timestamp)?.let { time ->
            time + if (o.halfHourCells.any { (key, value) -> key.endsWith("Half2") && value.isNotBlank() }) 1_800_000L else 0L
        } }.maxOrNull()?.let { (it - origin) / 3600000L } ?: 0L
        (0..(lastHour / 12).toInt()).map { page ->
            val pageStart = origin + page * 12 * 3600000L
            // A column's second-half reading can belong on the following page.
            observations.filter { o -> chartMillis(o.timestamp)?.let { it + 1_800_000L >= pageStart && it < pageStart + 12 * 3600000L } == true }
                .map { it.copy(chartStartTimestamp = Instant.fromEpochMilliseconds(pageStart).toString()) }
        }
    } else observations.chunked(12)
    chunks.forEachIndexed { pageIndex, columns ->
        val p = Paper()
        val title = if (partograph) "The modified WHO Partograph" else "WHO LABOUR CARE GUIDE"
        p.text(title, 340, 35, 22)
        val fields = if (partograph) partographHeaderFields else guideHeaderFields
        fields.forEachIndexed { i, (key, label) ->
            val value = header?.displayValueFor(key) ?: when (key) {
                "patientLabel" -> state.patientLabel; "parity" -> state.parity
                "labourOnset" -> state.labourOnset; "activeLabourDiagnosis" -> state.activeLabourDiagnosis
                "rupturedMembranes" -> state.rupturedMembranes; "riskFactors" -> state.riskFactors
                else -> ""
            }
            p.cell("$label: $value", 25.0 + (i % 2) * 575, 48.0 + (i / 2) * 32, 560.0, 31.0, 12)
        }
        val start = columns.mapNotNull { chartMillis(it.chartStartTimestamp) }.minOrNull()
            ?: columns.mapNotNull { chartMillis(it.timestamp) }.minOrNull()
        p.text("Chart start: ${start?.let { formatUiTimestamp(it) + " " + chartTimezoneLabel(it) }.orEmpty()}", 25, 195, 12)
        p.text("Saved observations only | Page ${pageIndex + 1} | Blank cells = no recorded value", 25, 216, 11)
        if (partograph) drawPartograph(p, columns, origin + pageIndex * 12 * 3600000L, header) else drawLcg(p, columns, pageIndex * 12, firstStageSlots)
        p.text("JananiSync | ${state.patientLabel} | ${state.patientSessionId}", 25, 1655, 10)
        pages.add(p.svg())
        // Keep every character of free text: cells refer to numbered continuation notes.
        val outcome = deliveryOutcomeFields.filter { (key, _) ->
            key != "lscsIndication" || header?.displayValueFor("deliveryMethod") == "LSCS"
        }.mapNotNull { (key, label) ->
            header?.displayValueFor(key)?.takeIf { it.isNotBlank() }?.let { "$label: $it" }
        }
        val noteLines = (p.notes + outcome).flatMapIndexed { index, note ->
            ("[${index + 1}] $note").lines().flatMap { it.chunked(75) } + ""
        }
        noteLines.chunked(85).forEach { lines ->
            val notes = Paper()
            notes.text("$title - continuation notes (chart page ${pageIndex + 1})", 25, 35, 18)
            notes.text(state.patientLabel + " | " + state.patientSessionId, 25, 60)
            lines.forEachIndexed { i, line -> notes.text(line, 25, 90 + i * 18, 14) }
            pages.add(notes.svg())
        }
    }
    return pages
}

private fun drawLcg(p: Paper, columns: List<LabourCareObservation>, offset: Int, firstStage: Int) {
    val left = 250.0; val width = 885.0; val cw = width / 12
    fun row(label: String, alert: String, y: Double, h: Double, value: (LabourCareObservation, Int) -> String, halves: Boolean = false) {
        p.box(25, y, 1110, h); p.line(195, y, 195, y + h); p.line(left, y, left, y + h)
        p.cell(label, 25.0, y, 170.0, h); p.cell(alert, 195.0, y, 55.0, h, 9)
        for (i in 0..12) p.line(left + cw * i, y, left + cw * i, y + h)
        columns.forEachIndexed { i, o ->
            val x = left + cw * i
            if (halves) p.line(x + cw / 2, y, x + cw / 2, y + h)
            for (half in 1..(if (halves) 2 else 1)) {
                val raw = value(o, half)
                val cellX = x + (half - 1) * cw / 2
                val cellW = cw / (if (halves) 2 else 1)
                p.cell(raw, cellX, y, cellW, h)
                val field = when (label) {
                    "SUPPORT: Companion" -> "companion"; "BABY: FHR" -> "fetalHeartRate"
                    "WOMAN: Pulse" -> "pulse"; "Systolic BP" -> "systolic"; "Diastolic BP" -> "diastolic"
                    "Temperature °C" -> "temperature"; "Pain relief" -> "painRelief"; "Oral fluid" -> "oralFluid"
                    "Posture" -> "posture"; "Deceleration" -> "fhrDeceleration"; "Amniotic fluid" -> "amnioticFluid"
                    "Fetal position" -> "fetalPosition"; "Caput" -> "caput"; "Moulding" -> "moulding"
                    "Contractions / 10 min" -> "contractionFrequency"; "Duration (seconds)" -> "contractionDuration"
                    else -> null
                }
                if (field != null && isAlertValue(field, raw)) p.body.append("<ellipse cx='${cellX + cellW / 2}' cy='${y + h / 2}' rx='${cellW / 2 - 1}' ry='${h / 2 - 2}' fill='none' stroke='#b00020' stroke-width='1.4'/>")
            }
        }
    }
    row("TIME / STAGE", "ALERT", 240.0, 48.0, { o, _ ->
        val i = columns.indexOf(o) + offset
        val stage = if (i < firstStage) "I ${i + 1}" else "II ${i - firstStage + 1}"
        "${chartMillis(o.timestamp)?.let { editableChartTime(it).takeLast(5) }.orEmpty()}\n$stage${if (o.pushingStarted) " PUSH" else ""}"
    })
    val rows = listOf(
        Triple("SUPPORT: Companion", "N", "companion"), Triple("Pain relief", "N", "painRelief"),
        Triple("Oral fluid", "N", "oralFluid"), Triple("Posture", "SP", "posture"),
        Triple("BABY: FHR", "<110 >=160", "fetalHeartRate"), Triple("Deceleration", "L", "fhrDeceleration"),
        Triple("Amniotic fluid", "M+++ B", "amnioticFluid"), Triple("Fetal position", "P T", "fetalPosition"),
        Triple("Caput", "+++", "caput"), Triple("Moulding", "+++", "moulding"),
        Triple("WOMAN: Pulse", "<60 >=120", "pulse"), Triple("Systolic BP", "<80 >=140", "systolic"),
        Triple("Diastolic BP", ">=90", "diastolic"), Triple("Temperature °C", "<35 >=37.5", "temperature"),
        Triple("Urine protein / acetone", "P++ A++", "urine"),
        Triple("Contractions / 10 min", "<=2 >5", "contractionFrequency"), Triple("Duration (seconds)", "<20 >60", "contractionDuration")
    )
    var y = 288.0
    rows.forEach { (label, alert, field) ->
        row(label, alert, y, 30.0, { o, half ->
            when (field) {
                "urine" -> listOf(
                    o.displayValueFor("urineProtein"),
                    o.displayValueFor("urineAcetone")
                ).filter { it.isNotBlank() }.joinToString(" / ")
                in lcgHalfHourlyFields -> o.halfHourCells["${field}Half$half"]
                    ?: if (half == 1) o.displayValueFor(field) else ""
                else -> o.displayValueFor(field)
            }
        }, field in lcgHalfHourlyFields)
        y += 30
    }
    for ((field, title, max) in listOf(Triple("cervicalDilation", "Cervix (X)", 10), Triple("descent", "Descent (O)", 5))) {
        val h = if (max == 10) 180.0 else 108.0
        p.text(title, 28, y + 14, 11)
        val min = if (max == 10) 5 else 0
        val step = h / (max - min + 1)
        val thresholds = mapOf(5 to ">=6h", 6 to ">=5h", 7 to ">=3h", 8 to ">=2.5h", 9 to ">=2h")
        for (v in max downTo min) {
            val yy = y + (max - v) * step
            p.text(v.toString(), 155, yy + 12, 10)
            if (max == 10) p.text(thresholds[v].orEmpty(), 198, yy + 12, 9)
            p.box(left, yy, width, step)
        }
        for (i in 0..12) p.line(left + i * cw, y, left + i * cw, y + h)
        columns.forEachIndexed { i, o ->
            val v = o.displayValueFor(field).toDoubleOrNull()
            if (v != null && v in min.toDouble()..max.toDouble()) p.text(if (field == "descent") "O" else "X", left + i * cw + cw / 2, y + (max - v) * step + step / 2, 12, centered = true)
        }
        y += h
    }
    for ((field, label, h) in listOf(Triple("oxytocin", "MEDICATION: Oxytocin", 42.0), Triple("medicine", "Medicine", 54.0),
        Triple("intravenousFluids", "IV fluids", 42.0), Triple("assessment", "SHARED: Assessment", 120.0),
        Triple("plan", "Plan", 120.0), Triple("initials", "Initials", 35.0))) {
        row(label, "", y, h, { o, _ -> o.displayValueFor(field) }); y += h
    }
}

private fun drawPartograph(p: Paper, observations: List<LabourCareObservation>, start: Long?, header: LabourCareObservation?) {
    val l = PartographLayout
    val dy = 250.0
    fun x(hour: Double) = l.hourToX(hour).toDouble()
    fun y(section: PartographSection, v: Double) = dy + section.y(v)
    fun grid(s: PartographSection, step: Int, title: String) {
        p.text(title, 25, dy + s.top + 10, 11)
        for (i in 0..24) p.line(x(i / 2.0), dy + s.top, x(i / 2.0), dy + s.bottom)
        for (v in s.min.toInt()..s.max.toInt() step step) { p.line(x(0.0), y(s, v.toDouble()), x(12.0), y(s, v.toDouble())); p.text("$v", 119, y(s, v.toDouble()) + 4, 10) }
    }
    fun series(field: String, s: PartographSection, symbol: String) {
        var prev: Pair<Double, Double>? = null
        partographPoints(observations, field).filter { it.hour < 12.0 }.forEach { point ->
            val xx = x(point.hour); val yy = y(s, point.value)
            prev?.let { p.line(it.first, it.second, xx, yy) }
            p.text(symbol, xx - 4, yy + 4, 13)
            if (point.value !in s.min..s.max) p.text(point.value.toString(), xx, yy - 8, 10)
            prev = xx to yy
        }
    }
    grid(l.fetal, 10, "")
    p.text("Fetal heart rate", l.PLOT_LEFT, dy + l.fetal.top - 10, 11)
    // The original paper partograph places the birth summary in the FHR margin.
    // Keep it clear of the numeric axis; long entries refer to full continuation notes.
    val birthSummary = listOf(
        "deliveryMethod" to "", "deliveryDateTime" to "",
        "babyStatus" to "", "babySex" to "",
        "babyWeightGrams" to "Weight (g): ",
        "apgar1" to "Apgar 1 min: ", "apgar5" to "Apgar 5 min: "
    ).mapNotNull { (field, label) ->
        header?.displayValueFor(field)?.takeIf { it.isNotBlank() }?.let {
            label + when (it) {
                "Spontaneous vaginal" -> "SVD"
                "Vacuum suction" -> "Vacuum"
                "Forceps / other instrumental" -> "Instrumental"
                else -> it
            }
        }
    }
    if (birthSummary.isNotEmpty()) {
        p.text("Birth outcome", 58, dy + l.fetal.top + 4, 10, centered = true)
        val requiredHeights = birthSummary.map { value ->
            value.lines().sumOf { (it.length + 13) / 14 }.coerceAtLeast(1) * 10.0 + 4
        }
        val availableHeight = l.fetal.height - 14.0
        val fits = requiredHeights.sum() <= availableHeight
        val spare = (availableHeight - requiredHeights.sum()) / birthSummary.size
        var top = dy + l.fetal.top + 14
        birthSummary.forEachIndexed { index, value ->
            val rowHeight = if (fits) requiredHeights[index] + spare else availableHeight / birthSummary.size
            p.cell(value, 5.0, top, 106.0, rowHeight, 8)
            top += rowHeight
        }
    }
    series("fetalHeartRate", l.fetal, "•")
    grid(l.cervix, 1, "Cervix X / Descent O")
    p.line(x(0.0), y(l.cervix, 4.0), x(6.0), y(l.cervix, 10.0))
    p.line(x(4.0), y(l.cervix, 4.0), x(10.0), y(l.cervix, 10.0))
    p.text("Alert", x(2.0), y(l.cervix, 6.0) - 8); p.text("Action", x(7.0), y(l.cervix, 7.0) - 8)
    series("cervicalDilation", l.cervix, "X"); series("descent", l.cervix, "O")
    grid(l.contractions, 1, "Contractions / 10 min")
    p.text("<20s dotted | 20–40s hatched | >40s solid", 150, dy + l.contractions.bottom + 15, 10)
    // Use vector patterns rather than font glyphs so print and PDF rasterisation
    // preserve the same shading in both halves of every hour.
    p.body.append("<defs><pattern id='contraction-dots' patternUnits='userSpaceOnUse' width='5' height='5'><circle cx='2.5' cy='2.5' r='0.8' fill='#222'/></pattern>" +
        "<pattern id='contraction-stripes' patternUnits='userSpaceOnUse' width='6' height='6'><path d='M-1 1 L1 -1 M0 6 L6 0 M5 7 L7 5' stroke='#222' stroke-width='1'/></pattern></defs>")
    observations.forEach { o ->
        val hour = chartMillis(o.timestamp)?.let { (it - (start ?: it)) / 3600000.0 } ?: return@forEach
        for (half in 1..2) {
            val count = o.partographValue("contractionFrequencyHalf$half")?.toInt() ?: continue
            val at = hour + (half - 1) * 0.5
            if (at !in 0.0..<12.0 || count !in 0..5) continue
            val top = y(l.contractions, count.toDouble())
            val xx = x(at); val w = l.PLOT_WIDTH / 24.0
            val duration = o.partographDuration(half)
            if (count == 0) continue
            if (duration == null) { p.text("?", xx + 5, top + 12); continue }
            val fill = when (duration) {
                PartographDuration.SHORT -> "url(#contraction-dots)"
                PartographDuration.MEDIUM -> "url(#contraction-stripes)"
                PartographDuration.LONG -> "#222"
            }
            for (box in 0 until count) {
                val boxTop = y(l.contractions, (box + 1).toDouble()) + 1.5
                val boxHeight = l.contractions.height / 5.0 - 3
                val visibleWidth = minOf(w, x(12.0) - xx) - 3
                if (visibleWidth > 0) p.box(xx + 1.5, boxTop, visibleWidth, boxHeight, fill)
            }
        }
    }
    grid(l.vitals, 10, "Pulse • / BP ↕")
    series("pulse", l.vitals, "•")
    val sys = partographPoints(observations, "systolic").filter { it.hour < 12.0 }.associateBy { it.hour }
    partographPoints(observations, "diastolic").filter { it.hour < 12.0 }.forEach { d -> sys[d.hour]?.let { s ->
        p.line(x(d.hour), y(l.vitals, s.value), x(d.hour), y(l.vitals, d.value))
        p.text("∧", x(d.hour) - 4, y(l.vitals, s.value) + 7); p.text("∨", x(d.hour) - 4, y(l.vitals, d.value))
    } }
    for ((field, label, top1, height) in l.lanes) {
        val top = dy + top1
        val halves = field in partographHalfHourlyFields
        val cells = if (halves) 24 else 12
        p.text(label.replace('\n', ' '), 25, top + 13, 10)
        p.box(l.PLOT_LEFT, top, l.PLOT_WIDTH, height)
        for (i in 0..cells) p.line(x(i * 12.0 / cells), top, x(i * 12.0 / cells), top + height)
        observations.forEach { o ->
            val hour = chartMillis(o.timestamp)?.let { (it - (start ?: it)) / 3600000.0 } ?: return@forEach
            for (half in 1..(if (halves) 2 else 1)) {
                val at = hour + (half - 1) * 0.5
                if (at !in 0.0..<12.0) continue
                p.cell(o.partographDisplayText(if (halves) "${field}Half$half" else field), x(at), top,
                    l.PLOT_WIDTH.toDouble() / cells, height.toDouble(), 9)
            }
        }
    }
    p.text("Hours", 60, dy + l.HOURS_TOP + 17)
    p.text("Time", 60, dy + l.CLOCK_TOP + 17)
    for (i in 0 until 12) {
        val xx = x(i.toDouble()); val w = l.PLOT_WIDTH / 12.0
        p.box(xx, dy + l.HOURS_TOP, w, l.HOURS_HEIGHT)
        p.cell("$i", xx, dy + l.HOURS_TOP, w, l.HOURS_HEIGHT.toDouble(), 12)
        p.box(xx, dy + l.CLOCK_TOP, w, l.CLOCK_HEIGHT)
        p.cell(start?.let { editableChartTime(it + i * 3600000L).takeLast(5) }.orEmpty(), xx, dy + l.CLOCK_TOP, w, l.CLOCK_HEIGHT.toDouble(), 11)
    }
}
