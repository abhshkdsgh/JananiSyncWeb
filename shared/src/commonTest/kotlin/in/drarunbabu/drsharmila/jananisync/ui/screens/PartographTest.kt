package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.data.AdmissionColumnState
import `in`.drarunbabu.drsharmila.jananisync.data.partographEntrySequence
import kotlin.test.*
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json

class PartographTest {
    @Test fun compactLcgWidthsKeepLabelsAndExactlyOneTimeColumnInsideTheViewport() {
        for (width in listOf(240f, 280f, 320f, 360f, 580f)) {
            val columns = compactLcgColumnWidths(width.dp)
            assertEquals(width, columns.section.value + columns.observation.value + columns.alert.value + columns.time.value, 0.001f)
            assertTrue(columns.section > 0.dp)
            assertTrue(columns.observation > 0.dp)
            assertTrue(columns.alert > 0.dp)
            assertTrue(columns.time >= 96.dp)
            assertTrue(columns.time <= 160.dp)
        }
    }
    @Test fun compactLcgRestoresTheSelectedColumnAndStartsAtRecordedDataRatherThanFutureSlots() {
        val recorded = observation().copy(columnId = "recorded")
        val empty = recorded.copy(columnId = "schedule:later", cellKey = "scheduled")
        assertEquals(0, compactLcgInitialColumn(listOf(recorded, empty), "", -1))
        assertEquals(1, compactLcgInitialColumn(listOf(recorded, empty), empty.columnId, -1))
        assertEquals(0, compactLcgInitialColumn(listOf(empty), "", -1))
        assertEquals(0, compactLcgInitialColumn(emptyList(), "", 0))
    }

    @Test fun numericArrowsStepByOneWithoutRoundingFractionalDigitsOrCrossingLimits() {
        val rate = assertNotNull(partographNumber("fetalHeartRate"))
        assertEquals("141", rate.stepByOne("140", true))
        assertEquals("139", rate.stepByOne("140", false))
        assertNull(rate.stepByOne("200", true))
        assertNull(rate.stepByOne("80", false))
        assertEquals("80", rate.stepByOne("", true))
        assertNull(rate.stepByOne("", false))
        assertNull(rate.stepByOne("7", true))
        val temperature = assertNotNull(partographNumber("temperature"))
        assertEquals("38.50", temperature.stepByOne("37.50", true))
        assertEquals("36.50", temperature.stepByOne("37.50", false))
        assertNull(temperature.stepByOne("44.5", true))
        assertNull(temperature.stepByOne("30.5", false))
        val volume = assertNotNull(partographNumber("urineVolume"))
        assertEquals("1.1", volume.stepByOne("0.1", true))
        assertEquals("0.1", volume.stepByOne("1.1", false))
        assertNull(volume.stepByOne("0.1", false))
    }

    @Test fun erasePayloadTargetsOnlyOneExistingCellAndKeepsItsObservationTime() {
        val mark = PartographMark("systolic", 0.5, "2026-10-24T08:00:00Z", "column",
            mapOf("systolicHalf2" to "130", "diastolicHalf2" to "85"))
        val erased = assertNotNull(mark.erasingValue("systolicHalf2"))
        assertEquals(mapOf("systolicHalf2" to ""), erased.values)
        assertEquals("2026-10-24T08:30:00Z", erased.observationTimestamp())
        assertEquals("85", mark.values["diastolicHalf2"])
        assertNull(mark.erasingValue("pulseHalf2"))
        assertNull(mark.copy(columnId = "").erasingValue("systolicHalf2"))
    }
    @Test fun zoomStepsIncludeRequestedSmallerSizesAndWorkAfterPinching() {
        var zoom = 1f
        for (expected in listOf(0.9f, 0.75f, 0.6f, 0.6f)) {
            zoom = partographZoomStep(zoom, false)
            assertEquals(expected, zoom)
        }
        for (expected in listOf(0.75f, 0.9f, 1f)) {
            zoom = partographZoomStep(zoom, true)
            assertEquals(expected, zoom)
        }
        assertEquals(0.75f, partographZoomStep(0.83f, false))
        assertEquals(0.9f, partographZoomStep(0.83f, true))
        assertEquals(3f, partographZoomStep(3f, true))
    }

    @Test fun allPlottedObservationsSupportSecondHalfWithoutChangingFirstHalfValues() {
        val first = observation().copy(recordedCellKeys = setOf("cervicalDilation", "pulse", "systolic", "diastolic", "temperature", "amnioticFluid", "moulding", "descent"),
            partographCells = mapOf("pulse" to "80", "temperature" to "37", "amnioticFluid" to "C", "moulding" to "0", "descent" to "4"),
            halfHourCells = mapOf("pulseHalf2" to "95", "temperatureHalf2" to "38", "amnioticFluidHalf2" to "M+", "mouldingHalf2" to "+", "cervicalDilationHalf2" to "6", "descentHalf2" to "3", "systolicHalf2" to "130", "diastolicHalf2" to "85"))
        val origin = kotlin.time.Instant.parse(first.timestamp).toEpochMilliseconds()
        for (field in listOf("cervicalDilation", "descent", "pulse", "systolic", "diastolic", "temperature", "amnioticFluid", "moulding")) {
            val mark = assertNotNull(buildPartographMarkForColumn(listOf(first), origin, "column", field, 2))
            assertEquals(0.5, mark.hour)
            assertEquals(first.timestamp, mark.timestamp)
            assertTrue(mark.values.keys.all { it.endsWith("Half2") })
            assertEquals(2, intervalFieldOptions(field, field, true).size)
            assertEquals(listOf(field), intervalFieldOptions(field, field, false).map { it.first })
        }
        val pressure = assertNotNull(buildPartographMarkForColumn(listOf(first), origin, "column", "systolic", 2))
        assertEquals(mapOf("systolicHalf2" to "130", "diastolicHalf2" to "85"), pressure.values)
        assertTrue(pressure.isComplete())
        assertEquals(listOf(PartographPoint(0.0, 80.0), PartographPoint(0.5, 95.0)), partographPoints(listOf(first), "pulse"))
        assertEquals(listOf(PartographPoint(0.0, 4.0), PartographPoint(0.5, 6.0)), partographPoints(listOf(first), "cervicalDilation"))
        for (field in listOf("medicine", "intravenousFluids", "urineProtein", "urineAcetone", "urineVolume")) {
            assertFalse(field in partographHalfHourlyFields)
            assertNull(buildPartographMarkForColumn(listOf(first), origin, "column", field, 2))
            assertEquals(1, intervalFieldOptions(field, field, true).size)
        }
        val control = LabourColumnControl("column", 90, 90, listOf("senior"), 1, true,
            cellAccessLevels = mapOf("pulse" to 10, "pulseHalf2" to 90))
        assertTrue(control.canEditCell("pulse", 10))
        assertFalse(control.canEditCell("pulseHalf2", 10))
    }

    @Test fun lcgKeepsOriginalIntervalsAndNeverProjectsANewSecondHalfIntoAnHourlyRow() {
        assertEquals(setOf("fetalHeartRate", "fhrDeceleration", "contractionFrequency", "contractionDuration"), lcgHalfHourlyFields)
        for (field in partographHalfHourlyFields - lcgHalfHourlyFields) {
            val onlyLater = observation().copy(cellKey = "partographMark", recordedCellKeys = emptySet(), halfHourCells = mapOf("${field}Half2" to "later"))
            assertEquals("", onlyLater.lcgFirstHalfValue(field))
            val both = onlyLater.copy(halfHourCells = onlyLater.halfHourCells + ("${field}Half1" to "first"))
            assertEquals("first", both.lcgFirstHalfValue(field))
            assertEquals("", both.copy(halfHourCells = mapOf("${field}Half1" to "", "${field}Half2" to "later")).lcgFirstHalfValue(field))
            assertEquals(listOf(field), intervalFieldOptions(field, field, false).map { it.first })
        }
        assertEquals(listOf("fetalHeartRateHalf1", "fetalHeartRateHalf2"), intervalFieldOptions("fetalHeartRate", "FHR", false).map { it.first })
    }

    @Test fun oxytocinSupportsIndependentHalfHoursAndPreservesLegacyFirstHalf() {
        val legacy = observation().copy(cellKey = "oxytocin", recordedCellKeys = setOf("oxytocin"),
            oxytocin = "2.5 U/L · 16 drops/min")
        val origin = kotlin.time.Instant.parse(legacy.timestamp).toEpochMilliseconds()
        val first = assertNotNull(buildPartographMarkForColumn(listOf(legacy), origin, "column", "oxytocin", 1))
        val second = assertNotNull(buildPartographMarkForColumn(listOf(legacy), origin, "column", "oxytocin", 2))
        assertEquals(mapOf("oxytocinHalf1" to legacy.oxytocin), first.values)
        assertEquals(mapOf("oxytocinHalf2" to ""), second.values)
        assertEquals(0.5, second.hour)
        assertEquals(legacy.timestamp, second.timestamp)
        assertEquals("2026-10-24T08:30:00Z", second.observationTimestamp())
        val recorded = legacy.copy(halfHourCells = mapOf("oxytocinHalf1" to "2.5 U/L · 18 drops/min", "oxytocinHalf2" to "2.5 U/L · 22 drops/min"))
        assertEquals("2.5\n18", recorded.partographDisplayText("oxytocinHalf1"))
        assertEquals("2.5\n22", recorded.partographDisplayText("oxytocinHalf2"))
        assertEquals("", legacy.partographDisplayText("oxytocinHalf2"))
        val oldEvent = legacy.copy(revision = 1)
        val newEvent = recorded.copy(revision = 2, cellKey = "partographMark", partographMarkCells = mapOf("oxytocinHalf1" to "2.5 U/L · 18 drops/min"))
        assertEquals(listOf(1, 2), listOf(oldEvent, newEvent).cellEditHistory(legacy.sessionId, "column", "oxytocinHalf1").map { it.revision })
        assertTrue(listOf(oldEvent, newEvent).cellEditHistory(legacy.sessionId, "column", "oxytocinHalf2").isEmpty())
        assertEquals("", recorded.copy(halfHourCells = mapOf("oxytocinHalf1" to "")).partographDisplayText("oxytocinHalf1"))
        val tapped = buildPartographEntryMark(listOf(recorded), origin, 0.5, "oxytocin", "", origin)
        assertEquals(mapOf("oxytocinHalf2" to "2.5 U/L · 22 drops/min"), tapped.values)
        val newSecondHalf = buildPartographEntryMark(emptyList(), origin, 0.5, "oxytocin", "", origin)
        assertEquals(legacy.timestamp, newSecondHalf.timestamp)
        assertEquals(setOf("oxytocinHalf2"), newSecondHalf.values.keys)
        val control = LabourColumnControl("column", 90, 90, listOf("senior"), 1, false, cellAccessLevels = mapOf("oxytocin" to 90))
        assertFalse(control.canEditCell("oxytocinHalf1", 10))
        assertTrue(control.canEditCell("oxytocinHalf2", 10))
        assertTrue(control.canEditCell("oxytocinHalf1", 90))
    }

    @Test fun columnSelectorOffersOnlyElapsedTimesAndPreservesExistingColumnIds() {
        val first = observation().copy(columnId = "first")
        val origin = kotlin.time.Instant.parse(first.timestamp).toEpochMilliseconds()
        val offGrid = first.copy(columnId = "off-grid", timestamp = "2026-10-24T08:17:00Z")
        val future = first.copy(columnId = "future", timestamp = "2026-10-24T11:00:00Z")
        val columns = availablePartographColumns(listOf(first, offGrid, future), origin, origin + 5_400_000)
        assertEquals(listOf("first", "off-grid"), columns.filter { it.existing }.map { it.id })
        assertEquals(listOf(1.0), columns.filter { !it.existing }.map { it.hour })
        assertTrue(columns.all { kotlin.time.Instant.parse(it.timestamp).toEpochMilliseconds() <= origin + 5_400_000 })
        assertTrue(availablePartographColumns(emptyList(), origin, origin - 1).isEmpty())
    }
    @Test fun columnSelectorKeepsRecordedColumnsAndEarlierGapsAfterBirth() {
        val first = observation().copy(columnId = "first")
        val origin = kotlin.time.Instant.parse(first.timestamp).toEpochMilliseconds()
        val birth = first.copy(columnId = "birth", timestamp = "2026-10-24T10:00:00Z", birthConfirmed = true)
        val columns = availablePartographColumns(listOf(first, birth), origin, origin + 18_000_000)
        assertEquals(listOf(0.0, 1.0, 2.0), columns.map { it.hour })
        assertEquals("birth", columns.last().id)
    }

    @Test fun laterFirstReadingKeepsTheExplicitStartAsEveryPlotsOrigin() {
        val record = observation("2026-10-24T10:00:00Z", 6.0).copy(
            chartStartTimestamp = "2026-10-24T08:00:00Z", chartColumnIndex = 2)
        assertEquals(kotlin.time.Instant.parse(record.chartStartTimestamp).toEpochMilliseconds(), partographOrigin(listOf(record), 0))
        assertEquals(listOf(PartographPoint(2.0, 6.0)), partographPoints(listOf(record), "cervicalDilation"))
        assertEquals(12, firstStageSlotCount(listOf(record.copy(fullDilationConfirmed = true))))
    }

    @Test fun legacySharedBandsDefaultToLatestColumnAndCanTargetAnExplicitSelection() {
        val first = observation().copy(columnId = "confirmed-first")
        val second = observation("2026-10-24T08:10:00Z").copy(columnId = "open-second")
        val origin = kotlin.time.Instant.parse(first.timestamp).toEpochMilliseconds()
        val selected = buildPartographEntryMark(listOf(first, second), origin, 0.0, "pulse", "90", origin)
        assertEquals("open-second", selected.columnId)
        val explicitlySelected = buildPartographEntryMark(listOf(first, second), origin, 0.0, "pulse", "90", origin, "confirmed-first")
        assertEquals("confirmed-first", explicitlySelected.columnId)
        val controls = listOf(
            LabourColumnControl("confirmed-first", 10, 10, listOf("user"), 1, true),
            LabourColumnControl("open-second", 10, 10, listOf("user"), 1, false))
        assertNull(selected.editBlockMessage(AdmissionColumnState(setOf("open-second"), false), controls, 10))
        val sequence = listOf(first, second).partographEntrySequence()
        assertEquals(1, sequence.nextHour)
        assertEquals(10.0 / 60, sequence.recordingHour(second.timestamp))
    }

    @Test fun numericEntryAllowsTypingPrefixesAndEnforcesLimitsBeforeSaving() {
        val rate = assertNotNull(partographNumber("fetalHeartRateHalf2"))
        for (prefix in listOf("", "1", "14", "140")) assertTrue(rate.acceptsInput(prefix))
        for (invalid in listOf("", "79", "201", "1.4", "-100", "NaN", "Infinity")) assertFalse(rate.isValid(invalid))
        assertTrue(rate.isValid("80"))
        assertTrue(rate.isValid("200"))
        assertFalse(rate.acceptsInput("abc"))
        val temperature = assertNotNull(partographNumber("temperature"))
        assertTrue(temperature.acceptsInput("37."))
        assertTrue(temperature.isValid("37.5"))
        assertFalse(temperature.isValid("45.1"))
        assertFalse(temperature.acceptsInput("37..5"))
        assertFalse(assertNotNull(partographNumber("gravida")).isValid("1.5"))
        val mark = buildPartographMark(emptyList(), 0, 0.0, "fetalHeartRate", "201")
        assertFalse(mark.isComplete())
        assertTrue(mark.copy(values = mapOf("fetalHeartRateHalf1" to "140")).isComplete())
        assertNull(partographNumber("contractionDuration"))
    }

    @Test fun tapsAnywhereInsideATimeBandUseThatColumnAndClickedValue() {
        val time = "2026-10-24T08:00:00Z"
        val origin = kotlin.time.Instant.parse(time).toEpochMilliseconds()
        val existing = observation().copy(columnId = "column")
        for (fraction in listOf(0.01, 0.25, 0.49, 0.51, 0.75, 0.99)) {
            val x = PartographLayout.hourToX(fraction)
            val hour = PartographLayout.xToHour(x, halfHourly = false)
            assertEquals(0.0, hour)
            val hit = assertNotNull(partographHit(x, PartographLayout.cervix.y(7.0), "cervicalDilation", "pulse"))
            val mark = buildPartographEntryMark(listOf(existing), origin, hour, hit.first, hit.second, origin)
            assertEquals("column", mark.columnId)
            assertEquals(time, mark.timestamp)
            assertEquals(mapOf("cervicalDilation" to "7"), mark.values)
        }
        assertEquals(1.0, PartographLayout.xToHour(PartographLayout.hourToX(1.01), false))
        assertEquals(0.0, PartographLayout.xToHour(PartographLayout.hourToX(0.49)))
        assertEquals(0.5, PartographLayout.xToHour(PartographLayout.hourToX(0.51)))
        assertEquals(12.0, PartographLayout.xToHour(PartographLayout.PLOT_RIGHT, false))
    }

    @Test fun wholeBandTapsPreserveOffGridTimeAndPairedValues() {
        val origin = kotlin.time.Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
        val existing = observation("2026-10-24T08:17:00Z").copy(
            recordedCellKeys = setOf("systolic", "diastolic"),
            partographCells = mapOf("systolic" to "120", "diastolic" to "80"),
            halfHourCells = mapOf("contractionFrequencyHalf2" to "3", "contractionDurationHalf2" to "20-40"))
        val pressure = buildPartographEntryMark(listOf(existing), origin, 0.0, "systolic", "130", origin)
        assertEquals(existing.timestamp, pressure.timestamp)
        assertEquals(mapOf("systolic" to "130", "diastolic" to "80"), pressure.values)
        val contractions = buildPartographEntryMark(listOf(existing), origin, 0.5, "contractionFrequency", "4", origin)
        assertEquals(existing.timestamp, contractions.timestamp)
        assertEquals(mapOf("contractionFrequencyHalf2" to "4", "contractionDurationHalf2" to "20-40"), contractions.values)
        assertEquals(17.0 / 60 + 0.5, contractions.hour)
    }

    @Test fun contractionDurationRangesSupportNewAndLegacyReadings() {
        listOf("<20" to PartographDuration.SHORT, "20-40" to PartographDuration.MEDIUM,
            ">40" to PartographDuration.LONG, "19" to PartographDuration.SHORT,
            "20" to PartographDuration.MEDIUM, "40" to PartographDuration.MEDIUM,
            "41" to PartographDuration.LONG).forEach { (value, expected) ->
            assertEquals(expected, partographDuration(value))
            val mark = buildPartographMark(emptyList(), 0, 0.0, "contractionFrequency", "3")
                .let { it.copy(values = it.values + ("contractionDurationHalf1" to value)) }
            assertTrue(mark.isComplete())
            assertFalse(mark.values.keys.any { it.startsWith("contractionStrength") })
            val saved = observation().copy(halfHourCells = mark.values)
            assertEquals(expected, saved.partographDuration(1))
        }
        for (invalid in listOf("", "0", "181", "unknown")) assertNull(partographDuration(invalid))
    }

    @Test fun savedMarkHitTestingPrefillsValuesAtExactOffGridColumnTime() {
        val origin = kotlin.time.Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
        val record = observation("2026-10-24T08:17:00Z", 6.0).copy(
            recordedCellKeys = setOf("cervicalDilation", "descent", "pulse"), descent = "3",
            halfHourCells = mapOf("fetalHeartRateHalf2" to "145"))
        val hour = 17.0 / 60
        val cervical = assertNotNull(recordedPartographMarkAt(listOf(record), origin,
            PartographLayout.hourToX(hour), PartographLayout.cervix.y(6.0)))
        assertEquals("cervicalDilation", cervical.field)
        assertEquals(record.timestamp, cervical.timestamp)
        assertEquals(mapOf("cervicalDilation" to "6"), cervical.values)
        val descent = assertNotNull(recordedPartographMarkAt(listOf(record), origin,
            PartographLayout.hourToX(hour), PartographLayout.cervix.y(3.0)))
        assertEquals("descent", descent.field)
        assertEquals(mapOf("descent" to "3"), descent.values)
        val secondHalf = assertNotNull(recordedPartographMarkAt(listOf(record), origin,
            PartographLayout.hourToX(hour + 0.5), PartographLayout.fetal.y(145.0)))
        assertEquals(mapOf("fetalHeartRateHalf2" to "145"), secondHalf.values)
        assertEquals(record.timestamp, secondHalf.timestamp)
        assertNull(recordedPartographMarkAt(listOf(record), origin,
            PartographLayout.hourToX(4.0), PartographLayout.cervix.y(6.0)))
    }

    @Test fun savedMarksKeepLcgCellAccessAndConfirmedCorrectionRules() {
        val record = observation().copy(recordedCellKeys = setOf("cervicalDilation"))
        val origin = kotlin.time.Instant.parse(record.timestamp).toEpochMilliseconds()
        val mark = assertNotNull(recordedPartographMarkAt(listOf(record), origin,
            PartographLayout.hourToX(0.0), PartographLayout.cervix.y(4.0)))
        val open = AdmissionColumnState(setOf("column"), false)
        val control = LabourColumnControl("column", 10, 30, listOf("user"), 1, false,
            cellAccessLevels = mapOf("cervicalDilation" to 30))
        assertNotNull(mark.editBlockMessage(open, listOf(control), 29))
        assertNull(mark.editBlockMessage(open, listOf(control), 30))
        val confirmed = control.copy(confirmed = true, confirmerAccessLevel = 30)
        assertNull(mark.editBlockMessage(open, listOf(confirmed), 99))
        assertTrue(confirmed.canCorrectCell("cervicalDilation", 34))
        assertTrue(confirmed.canCorrectCell("cervicalDilation", 35))
    }

    @Test fun everyCervicalValueKeepsTheTimeColumnInPreviewAndSavedPlot() {
        val time = "2026-10-24T08:00:00Z"
        val origin = kotlin.time.Instant.parse(time).toEpochMilliseconds()
        for (value in 4..10) {
            val first = observation(dilation = value.toDouble())
            val records = listOf(first)
            assertEquals(origin, partographOrigin(records, 0))
            assertEquals(listOf(PartographPoint(0.0, value.toDouble())), partographPoints(records, "cervicalDilation"))
            val mark = buildPartographMark(emptyList(), origin, 2.5, "cervicalDilation", value.toString())
            assertEquals(2.5, mark.hour)
            val saved = first.copy(timestamp = mark.timestamp, cellKey = "partographMark", recordedCellKeys = mark.values.keys, halfHourCells = mark.values)
            val baseline = first.copy(cellKey = "pulse", recordedCellKeys = setOf("pulse"))
            assertEquals(listOf(PartographPoint(2.5, value.toDouble())), partographPoints(listOf(baseline, saved), "cervicalDilation"))
        }
    }

    @Test fun descentUsesTimeColumnForEveryValueAndNeverAlertLineAlignment() {
        val time = "2026-10-24T08:00:00Z"
        val origin = kotlin.time.Instant.parse(time).toEpochMilliseconds()
        val existing = observation().copy(columnId = "time-column", cellKey = "pulse",
            recordedCellKeys = setOf("pulse"), partographCells = mapOf("pulse" to "85"))
        for (value in 0..5) {
            val mark = buildPartographMark(listOf(existing), origin, 0.0, "descent", value.toString())
            assertEquals(0.0, mark.hour)
            assertEquals(time, mark.timestamp)
            assertEquals("time-column", mark.columnId)
            val later = buildPartographMark(listOf(existing), origin, 2.0, "descent", value.toString())
            assertEquals(2.0, later.hour)
            assertEquals(origin + 7_200_000, kotlin.time.Instant.parse(later.timestamp).toEpochMilliseconds())
        }
        val hit = assertNotNull(partographHit(PartographLayout.hourToX(0.0), PartographLayout.cervix.y(5.0), "descent", "pulse"))
        assertEquals("descent" to "5", hit)
        val cervical = buildPartographMark(listOf(existing), origin, 0.0, "cervicalDilation", "5")
        assertEquals(0.0, cervical.hour)
    }

    @Test fun firstGraphTapUsesChosenStartAndHalfTwoKeepsParentTime() {
        val origin = kotlin.time.Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
        for (hour in listOf(0.0, 0.5, 3.0, 12.0)) {
            val mark = buildPartographEntryMark(emptyList(), origin, hour, "fetalHeartRate", "140", origin)
            assertEquals(hour, mark.hour)
            assertEquals(origin + kotlin.math.floor(hour).toLong() * 3_600_000, kotlin.time.Instant.parse(mark.timestamp).toEpochMilliseconds())
            assertEquals(mapOf("fetalHeartRateHalf${if (hour % 1.0 == 0.0) 1 else 2}" to "140"), mark.values)
        }
    }

    @Test fun graphTapKeepsExistingColumnAndFollowingHourSelection() {
        val existing = observation().copy(columnId = "lcg-column")
        val origin = kotlin.time.Instant.parse(existing.timestamp).toEpochMilliseconds()
        val mark = buildPartographEntryMark(listOf(existing), origin, 0.0, "pulse", "85", origin + 60_000)
        assertEquals("lcg-column", mark.columnId)
        assertEquals(existing.timestamp, mark.timestamp)
        val later = buildPartographEntryMark(listOf(existing), origin, 1.0, "pulse", "90", origin + 60_000)
        assertEquals(1.0, later.hour)
        val scheduled = availablePartographColumns(listOf(existing), origin, origin + 3_600_000)
            .single { it.hour == 1.0 }
        assertFalse(scheduled.existing)
        assertEquals(scheduled.id, later.columnId)
        assertEquals(`in`.drarunbabu.drsharmila.jananisync.data.scheduledColumnId(origin + 3_600_000, 1), later.columnId)
        assertEquals(origin + 3_600_000, kotlin.time.Instant.parse(later.timestamp).toEpochMilliseconds())
    }

    @Test fun nextHourFormStartsWithBlankFirstHalfAndKeepsItsScheduledTime() {
        val time = "2026-10-24T08:00:00Z"
        val origin = kotlin.time.Instant.parse(time).toEpochMilliseconds()
        val rate = buildPartographMarkForNewColumn(origin, time, "fetalHeartRate")
        assertEquals("", rate.columnId)
        assertEquals(0.0, rate.hour)
        assertEquals(mapOf("fetalHeartRateHalf1" to ""), rate.values)
        assertFalse(rate.isComplete())
        val contractions = buildPartographMarkForNewColumn(origin, time, "contractionFrequency")
        assertEquals(setOf("contractionFrequencyHalf1", "contractionDurationHalf1"), contractions.values.keys)
        assertFalse(contractions.isComplete())
        val cervical = buildPartographMark(emptyList(), origin, 0.0, "cervicalDilation", "5")
        assertEquals(time, cervical.timestamp)
        assertEquals(0.0, cervical.hour)
        assertNull(emptyList<LabourCareObservation>().partographEntrySequence().creationBlockMessage(cervical.timestamp, origin))
    }
    @Test fun oxytocinDisplaySeparatesRecognizedUnitsAndRateWithoutChangingFreeText() {
        val combined = "2.5 U/L · 16 drops/min"
        val recorded = observation().copy(partographCells = mapOf("oxytocin" to combined))
        assertEquals("2.5\n16", recorded.partographDisplayText("oxytocin"))
        assertEquals(combined, recorded.partographText("oxytocin"))
        val freeText = "Infusion paused; see plan"
        assertEquals(freeText, recorded.copy(partographCells = mapOf("oxytocin" to freeText)).partographDisplayText("oxytocin"))
        val multiline = "Infusion paused\nSee plan"
        assertEquals(multiline, recorded.copy(partographCells = mapOf("oxytocin" to multiline)).partographDisplayText("oxytocin"))
        assertEquals("", recorded.copy(partographCells = mapOf("oxytocin" to "")).partographDisplayText("oxytocin"))
    }

    private fun observation(time: String = "2026-10-24T08:00:00Z", dilation: Double = 4.0) = LabourCareObservation(
        id = "event", sessionId = "admission", patientLabel = "Test", parity = 1,
        timestamp = time, maternalVitals = MaternalVitals(time, 120, 80, 75, 37.0),
        fetalWellbeing = FetalWellbeing(time, 140, AmnioticFluidStatus.CLEAR),
        laborProgress = LaborProgress(time, dilation, 3, 30),
        columnId = "column", cellKey = "cervicalDilation", recordedCellKeys = setOf("cervicalDilation")
    )

    @Test fun thirteenthHourAppearsAtElevenFirstStageRecordsAndSurvivesStageTransition() {
        val records = List(11) { observation().copy(columnId = "column$it") }
        assertEquals(12, firstStageSlotCount(records.take(10)))
        assertEquals(13, firstStageSlotCount(records))
        val ended = records.dropLast(1) + records.last().copy(fullDilationConfirmed = true)
        val next = observation().copy(columnId = "second-stage")
        val slots = (ended + next).withSecondStageGap()
        assertEquals(14, slots.size)
        assertEquals("skipped", slots[11].cellKey)
        assertEquals("skipped", slots[12].cellKey)
        assertEquals(next, slots[13])
        val earlyEnd = records.first().copy(fullDilationConfirmed = true)
        assertEquals(12, firstStageSlotCount(listOf(earlyEnd) + records))
    }

    @Test fun secondStageClockUsesHistoricalTimeOnlyBeyondTwoHours() {
        val last = 1_000_000L
        val hour = 3_600_000L
        assertEquals(last + hour, `in`.drarunbabu.drsharmila.jananisync.data.secondStageStartTime(last, last + hour))
        assertEquals(last + 2 * hour, `in`.drarunbabu.drsharmila.jananisync.data.secondStageStartTime(last, last + 2 * hour))
        assertEquals(last, `in`.drarunbabu.drsharmila.jananisync.data.secondStageStartTime(last, last + 2 * hour + 1))
    }

    @Test fun secondStageScheduleHasItsOwnClockAndColumnIdentity() {
        val first = observation().copy(chartStartTimestamp = observation().timestamp,
            chartColumnIndex = 0, fullDilationConfirmed = true,
            secondStageStartTimestamp = observation().timestamp)
        val start = kotlin.time.Instant.parse(first.timestamp).toEpochMilliseconds()
        val slots = `in`.drarunbabu.drsharmila.jananisync.data.scheduledObservations(
            listOf(first), start, 15, first.sessionId, first.patientLabel).withSecondStageGap()
        assertEquals(first.timestamp, slots[12].timestamp)
        assertEquals(1, slots[12].chartColumnIndex)
        assertEquals(1, `in`.drarunbabu.drsharmila.jananisync.data.stageColumnIndex(listOf(first), start))
        val live = first.copy(secondStageStartTimestamp = "2026-10-24T08:23:45Z")
        val liveSlots = `in`.drarunbabu.drsharmila.jananisync.data.scheduledObservations(
            listOf(live), start, 15, live.sessionId, live.patientLabel).withSecondStageGap()
        assertEquals("2026-10-24T08:23:45Z", liveSlots[12].timestamp)
        assertEquals("2026-10-24T09:23:45Z", liveSlots[13].timestamp)
        assertEquals(1, `in`.drarunbabu.drsharmila.jananisync.data.stageColumnIndex(listOf(live),
            kotlin.time.Instant.parse(live.secondStageStartTimestamp).toEpochMilliseconds()))
        assertEquals(null, `in`.drarunbabu.drsharmila.jananisync.data.stageColumnIndex(listOf(live), start))
        val decoded = Json.decodeFromString<LabourCareObservation>(Json.encodeToString(live))
        assertEquals(live.secondStageStartTimestamp, decoded.secondStageStartTimestamp)
    }

    @Test fun scheduledTimelineKeepsBlankFirstStageColumnsBeforeSecondStage() {
        val transition = observation().copy(chartStartTimestamp = observation().timestamp,
            chartColumnIndex = 0, fullDilationConfirmed = true)
        val start = kotlin.time.Instant.parse(transition.timestamp).toEpochMilliseconds()
        val slots = `in`.drarunbabu.drsharmila.jananisync.data.scheduledObservations(
            listOf(transition), start, 15, transition.sessionId, transition.patientLabel
        ).withSecondStageGap()
        assertEquals(transition, slots.first())
        assertTrue(slots.subList(1, 12).all { it.cellKey == "skipped" && it.columnId.isBlank() })
        assertEquals("scheduled", slots[12].cellKey)
        assertEquals(start + 3_600_000L, kotlin.time.Instant.parse(slots[12].timestamp).toEpochMilliseconds())
    }

    @Test fun fullDilationReservesRemainingFirstStageSlotsAndKeepsSecondStageRecords() {
        val first = observation().copy(fullDilationConfirmed = true)
        val second = observation("2026-10-24T09:00:00Z").copy(columnId = "second")
        val slots = listOf(first, second).withSecondStageGap()
        assertEquals(13, slots.size)
        assertEquals(first, slots[0])
        assertTrue(slots.subList(1, 12).all { it.columnId.isBlank() && it.cellKey == "skipped" })
        assertEquals(second, slots[12])
        assertEquals(12, listOf(first).withSecondStageGap().size)
        assertEquals(listOf(second), listOf(second).withSecondStageGap())
        val decoded = Json.decodeFromString<LabourCareObservation>(Json.encodeToString(first))
        assertTrue(decoded.fullDilationConfirmed)
    }

    @Test fun firstAndSubsequentExaminationsUseRecordedElapsedTime() {
        val points = partographPoints(listOf(observation(dilation = 5.0), observation("2026-10-24T10:30:00Z", 7.0)), "cervicalDilation")
        assertEquals(listOf(PartographPoint(0.0, 5.0), PartographPoint(2.5, 7.0)), points)
    }

    @Test fun halfHoursPreserveExplicitClearsAndDoNotPlotMissingDefaults() {
        val first = observation().copy(halfHourCells = mapOf("fetalHeartRateHalf1" to "", "fetalHeartRateHalf2" to "145"))
        assertEquals(listOf(PartographPoint(0.5, 145.0)), partographPoints(listOf(first), "fetalHeartRate", true))
        assertTrue(partographPoints(listOf(observation()), "pulse").isEmpty())
        assertNull(first.partographValue("fetalHeartRateHalf1"))
    }

    @Test fun legacyHourlyValuesAppearOnlyInFirstHalfHour() {
        val first = observation().copy(recordedCellKeys = setOf("fetalHeartRate"))
        assertEquals(listOf(PartographPoint(0.0, 140.0)), partographPoints(listOf(first), "fetalHeartRate", true))
    }

    @Test fun explicitNumericClearDoesNotPlotZeroAndRecordedZeroRemainsValid() {
        val cleared = observation().copy(editedValue = "", laborProgress = observation().laborProgress.copy(cervicalDilationCm = 0.0))
        assertTrue(partographPoints(listOf(cleared), "cervicalDilation").isEmpty())
        val zero = observation().copy(cellKey = "descent", editedValue = "0", descent = "0", recordedCellKeys = setOf("descent"))
        assertEquals(listOf(PartographPoint(0.0, 0.0)), partographPoints(listOf(zero), "descent"))
    }

    @Test fun clearRemainsBlankAfterAnotherCellIsEdited() {
        val cleared = observation().copy(cellKey = "medicine", editedValue = "Drug", recordedCellKeys = setOf("pulse", "medicine"), partographCells = mapOf("pulse" to ""))
        assertTrue(partographPoints(listOf(cleared), "pulse").isEmpty())
    }

    @Test fun additionalFieldsRoundTripAndOlderRecordsStillDecode() {
        val original = observation().copy(partographCells = mapOf("gravida" to "2", "urineVolume" to "200", "rupturedHours" to "3"))
        assertEquals(original, Json.decodeFromString<LabourCareObservation>(Json.encodeToString(original)))
        assertTrue(Json.decodeFromString<LabourCareObservation>(Json.encodeToString(observation())).partographCells.isEmpty())
    }

    @Test fun graphHitTestingDistinguishesPlotsAndBlankGaps() {
        assertEquals("140", partographHit(200f, 108f, "cervicalDilation", "pulse")?.second)
        assertEquals("descent", partographHit(200f, 452f, "descent", "pulse")?.first)
        assertNull(partographHit(149f, 108f, "cervicalDilation", "pulse"))
        assertNull(partographHit(200f, 260f, "cervicalDilation", "pulse"))
        assertEquals("temperature", partographHit(200f, 1174f, "cervicalDilation", "pulse")?.first)
    }

    @Test fun contractionOverlayUsesTheSameHalfHourAndRequiresDuration() {
        val existing = observation().copy(halfHourCells = mapOf("contractionDurationHalf2" to "40"))
        val mark = buildPartographMark(listOf(existing), kotlin.time.Instant.parse(existing.timestamp).toEpochMilliseconds(), 0.5, "contractionFrequency", "3")
        assertEquals("column", mark.columnId)
        assertEquals(mapOf("contractionFrequencyHalf2" to "3", "contractionDurationHalf2" to "40"), mark.values)
        assertTrue(mark.isComplete())
        assertFalse(mark.copy(values = mark.values + ("contractionDurationHalf2" to "")).isComplete())
    }

    @Test fun bloodPressureRequiresBothValuesAndSystolicAboveDiastolic() {
        val mark = buildPartographMark(emptyList(), 0, 2.0, "systolic", "120")
        assertFalse(mark.isComplete())
        assertTrue(mark.copy(values = mark.values + ("diastolic" to "80")).isComplete())
        assertFalse(mark.copy(values = mark.values + ("diastolic" to "130")).isComplete())
    }

    @Test fun firstCervicalPreviewStaysAtTheSelectedActualTime() {
        val mark = buildPartographMark(emptyList(), 0, 3.0, "cervicalDilation", "5")
        assertEquals(3.0, mark.hour)
        assertEquals(kotlin.time.Instant.fromEpochMilliseconds(3 * 3_600_000L).toString(), mark.timestamp)
        assertTrue(mark.isComplete())
    }

    @Test fun graphTimesSnapToHalfHoursAndContractionsStayInsideLastBox() {
        val mark = buildPartographMark(emptyList(), 0, 1.3, "fetalHeartRate", "140")
        assertEquals(1.5, mark.hour)
        assertEquals(kotlin.time.Instant.fromEpochMilliseconds(3_600_000L).toString(), mark.timestamp)
        assertEquals(11.5, buildPartographMark(emptyList(), 0, 12.0, "contractionFrequency", "2").hour)
    }

    @Test fun compositeMarksBelongToEachChangedCellsHistoryOnly() {
        val mark = observation().copy(cellKey = "partographMark", partographMarkCells = mapOf("systolic" to "120", "diastolic" to "80"))
        assertEquals(1, listOf(mark).cellEditHistory("admission", "column", "systolic").size)
        assertEquals(1, listOf(mark).cellEditHistory("admission", "column", "diastolic").size)
        assertTrue(listOf(mark).cellEditHistory("admission", "column", "pulse").isEmpty())
    }

    @Test fun entryGuardPreservesCellAccessWithoutConfirmationLocks() {
        val control = LabourColumnControl("column", 10, 10, listOf("user"), 1, false)
        val open = AdmissionColumnState(setOf("column", "older-column"), false)
        val newMark = buildPartographMark(emptyList(), 0, 1.0, "pulse", "80")
        assertEquals(open.creationBlockMessage, newMark.editBlockMessage(open, listOf(control), 10))
        val existingMark = newMark.copy(columnId = "column")
        assertNull(existingMark.editBlockMessage(open, listOf(control), 10))
        assertNotNull(existingMark.editBlockMessage(open, emptyList(), 10))
        assertNull(existingMark.editBlockMessage(open, listOf(control.copy(confirmed = true)), 10))
        assertNotNull(existingMark.editBlockMessage(open, listOf(control.copy(cellAccessLevels = mapOf("pulse" to 20))), 10))
    }

    @Test fun entryGuardUsesResolvedColumnAndPreservesSecondHalfAtRecordedTime() {
        val existing = observation(dilation = 5.0)
        val origin = partographOrigin(listOf(existing), 0)
        val open = AdmissionColumnState(setOf("column"), false)
        val control = LabourColumnControl("column", 10, 10, listOf("user"), 1, false)
        val secondHalf = buildPartographMark(listOf(existing), origin, 0.5, "fetalHeartRate", "145")
        assertEquals("column", secondHalf.columnId)
        assertEquals(existing.timestamp, secondHalf.timestamp)
        assertEquals(mapOf("fetalHeartRateHalf2" to "145"), secondHalf.values)
        assertNull(secondHalf.editBlockMessage(open, listOf(control), 10))
        val differentTime = buildPartographMark(listOf(existing), origin, 0.5, "pulse", "80")
        assertEquals("column", differentTime.columnId)
        assertEquals(existing.timestamp, differentTime.timestamp)
        assertEquals(mapOf("pulseHalf2" to "80"), differentTime.values)
        assertNull(differentTime.editBlockMessage(open, listOf(control), 10))
    }

    @Test fun openColumnsDoNotBlockPreviewButLoadingStatusDoes() {
        val preview = buildPartographMark(emptyList(), 0, 2.0, "pulse", "80")
        assertNull(preview.editBlockMessage(AdmissionColumnState(emptySet(), false), emptyList(), 10))
        assertNull(preview.editBlockMessage(AdmissionColumnState(setOf("remote-column"), false), emptyList(), 10))
        assertNotNull(preview.editBlockMessage(AdmissionColumnState(emptySet(), true), emptyList(), 10))
        assertNull(preview.editBlockMessage(AdmissionColumnState(emptySet(), false), emptyList(), 10))
    }

    @Test fun reversedRecordsAndMixedOffsetsUseOneChronologicalOriginForEverySeries() {
        val first = observation("2026-10-24T09:00:00+02:00", 4.0).copy(
            columnId = "first", recordedCellKeys = setOf("cervicalDilation", "pulse"))
        val second = observation("2026-10-24T08:00:00Z", 5.0).copy(
            columnId = "second", recordedCellKeys = setOf("cervicalDilation", "pulse"))
        val third = observation("2026-10-24T15:30:00+05:30", 7.0).copy(
            columnId = "third", recordedCellKeys = setOf("cervicalDilation", "pulse"))
        val reversed = listOf(third, second, first)
        assertEquals(kotlin.time.Instant.parse("2026-10-24T07:00:00Z").toEpochMilliseconds(), partographOrigin(reversed, 0))
        assertEquals(listOf(PartographPoint(0.0, 4.0), PartographPoint(1.0, 5.0), PartographPoint(3.0, 7.0)),
            partographPoints(reversed, "cervicalDilation"))
        assertEquals(listOf(0.0, 1.0, 3.0), partographPoints(reversed, "pulse").map { it.hour })
        assertEquals(partographPoints(reversed.reversed(), "pulse"), partographPoints(reversed, "pulse"))
    }

    @Test fun invalidTimesCannotHideValidRecordsOrBecomeTheChartAnchor() {
        val invalid = observation("invalid", 5.0)
        val first = observation().copy(columnId = "first", cellKey = "pulse", recordedCellKeys = setOf("pulse"))
        val later = first.copy(columnId = "later", timestamp = "2026-10-24T10:00:00Z")
        val records = listOf(invalid, later, first)
        assertEquals(kotlin.time.Instant.parse(first.timestamp).toEpochMilliseconds(), partographOrigin(records, 123))
        assertEquals(listOf(PartographPoint(0.0, 75.0), PartographPoint(2.0, 75.0)), partographPoints(records, "pulse"))
        assertEquals(123L, partographOrigin(listOf(invalid), 123))
        assertTrue(partographPoints(listOf(invalid), "cervicalDilation").isEmpty())
        val firstValidPreview = buildPartographMark(listOf(invalid), 0, 3.0, "cervicalDilation", "5")
        assertEquals(3.0, firstValidPreview.hour)
        assertEquals(kotlin.time.Instant.fromEpochMilliseconds(3 * 3_600_000L).toString(), firstValidPreview.timestamp)
    }

    @Test fun aLaterFirstCervicalExaminationDoesNotMoveEarlierReadings() {
        val early = observation().copy(columnId = "early", cellKey = "pulse", recordedCellKeys = setOf("pulse"))
        val examination = observation("2026-10-24T11:00:00Z", 5.0).copy(
            columnId = "examination", recordedCellKeys = setOf("cervicalDilation", "pulse"))
        val records = listOf(examination, early)
        assertEquals(kotlin.time.Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds(), partographOrigin(records, 0))
        assertEquals(listOf(PartographPoint(3.0, 5.0)), partographPoints(records, "cervicalDilation"))
        assertEquals(listOf(PartographPoint(0.0, 75.0), PartographPoint(3.0, 75.0)), partographPoints(records, "pulse"))
    }

    @Test fun paperSectionsAndEveryLaneHaveDistinctHitTargetsInPrintedOrder() {
        val layout = PartographLayout
        assertEquals(listOf("amnioticFluid", "moulding", "oxytocin", "medicine", "intravenousFluids", "temperature",
            "urineProtein", "urineAcetone", "urineVolume"), partographLanes.map { it.field })
        assertTrue(partographLanes.none { it.field in setOf("gravida", "rupturedHours") })
        val sections = listOf(layout.fetal.top to layout.fetal.bottom, layout.cervix.top to layout.cervix.bottom,
            layout.contractions.top to layout.contractions.bottom, layout.vitals.top to layout.vitals.bottom) +
            partographLanes.map { it.top to it.bottom }
        sections.sortedBy { it.first }.zipWithNext().forEach { (earlier, later) -> assertTrue(earlier.second <= later.first) }
        partographLanes.forEach { lane ->
            assertEquals(lane.field, partographHit(200f, (lane.top + lane.bottom) / 2, "cervicalDilation", "pulse")?.first)
        }
        assertNull(partographHit(200f, (layout.CLOCK_TOP + layout.CLOCK_HEIGHT / 2), "cervicalDilation", "pulse"))
        assertEquals("contractionFrequency", partographHit(200f, layout.contractions.top + 1f, "cervicalDilation", "pulse")?.first)
        assertEquals("systolic", partographHit(200f, layout.vitals.top + 1f, "cervicalDilation", "systolic")?.first)
    }

    @Test fun endpointLaneTextStaysAtItsRecordedTwelfthHourAndFitsWithinTheCanvas() {
        val layout = PartographLayout
        assertEquals(layout.hourToX(12.0), layout.laneTextAnchor(12.0))
        assertEquals(layout.PLOT_RIGHT, layout.laneTextAnchor(12.0))
        assertTrue(layout.laneTextAnchor(12.0) > layout.hourToX(11.0))
        assertTrue(layout.CHART_WIDTH >= layout.PLOT_RIGHT + layout.PLOT_WIDTH / layout.HOURS)
    }

    @Test fun exactColumnEntryPreservesOffGridTimeAndItsExistingMeasurements() {
        val origin = kotlin.time.Instant.parse("2026-10-24T08:00:00Z").toEpochMilliseconds()
        val offGrid = observation("2026-10-24T08:17:42.120Z").copy(
            columnId = "off-grid", cellKey = "pulse", recordedCellKeys = setOf("pulse"), partographCells = mapOf("pulse" to "85"))
        val mark = assertNotNull(buildPartographMarkForColumn(listOf(offGrid), origin, "off-grid", "pulse"))
        assertEquals(offGrid.timestamp, mark.timestamp)
        assertEquals("off-grid", mark.columnId)
        assertEquals(1_062_120.0 / 3_600_000.0, mark.hour)
        assertEquals(mapOf("pulse" to "85"), mark.values)
        val secondHalf = assertNotNull(buildPartographMarkForColumn(listOf(offGrid), origin, "off-grid", "fetalHeartRate", 2))
        assertEquals(offGrid.timestamp, secondHalf.timestamp)
        assertEquals(mark.hour + 0.5, secondHalf.hour)
        assertEquals("2026-10-24T08:47:42.120Z", secondHalf.observationTimestamp())
        assertEquals(mapOf("fetalHeartRateHalf2" to ""), secondHalf.values)
        assertFalse(secondHalf.isComplete())
        val laterPulse = assertNotNull(buildPartographMarkForColumn(listOf(offGrid), origin, "off-grid", "pulse", 2))
        assertEquals(offGrid.timestamp, laterPulse.timestamp)
        assertEquals(mapOf("pulseHalf2" to ""), laterPulse.values)
        assertNull(buildPartographMarkForColumn(listOf(offGrid), origin, "off-grid", "pulse", 3))
        assertNull(buildPartographMarkForColumn(listOf(offGrid), origin, "missing", "pulse"))
        assertNull(buildPartographMarkForColumn(listOf(offGrid.copy(isGuideHeader = true)), origin, "off-grid", "pulse"))
        assertNull(buildPartographMarkForColumn(listOf(offGrid.copy(timestamp = "invalid")), origin, "off-grid", "pulse"))
    }

    @Test fun exactColumnEntryLeavesMissingValuesBlankAndPreservesExplicitClearsAndCompositeParts() {
        val existing = observation().copy(partographCells = mapOf("pulse" to "", "systolic" to "120", "gravida" to "2"),
            halfHourCells = mapOf("contractionFrequencyHalf2" to "3", "contractionDurationHalf2" to "40"))
        val origin = kotlin.time.Instant.parse(existing.timestamp).toEpochMilliseconds()
        val pressure = assertNotNull(buildPartographMarkForColumn(listOf(existing), origin, existing.columnId, "systolic"))
        assertEquals(mapOf("systolic" to "120", "diastolic" to ""), pressure.values)
        assertFalse(pressure.isComplete())
        val contractions = assertNotNull(buildPartographMarkForColumn(listOf(existing), origin, existing.columnId, "contractionFrequency", 2))
        assertEquals(mapOf("contractionFrequencyHalf2" to "3", "contractionDurationHalf2" to "40"), contractions.values)
        assertTrue(contractions.isComplete())
        val missingContractions = assertNotNull(buildPartographMarkForColumn(listOf(existing), origin, existing.columnId, "contractionFrequency"))
        assertEquals(mapOf("contractionFrequencyHalf1" to "", "contractionDurationHalf1" to ""), missingContractions.values)
        assertFalse(missingContractions.isComplete())
        val cleared = assertNotNull(buildPartographMarkForColumn(listOf(existing), origin, existing.columnId, "pulse"))
        assertEquals(mapOf("pulse" to ""), cleared.values)
        assertFalse(cleared.isComplete())
        assertEquals(mapOf("gravida" to "2"), assertNotNull(buildPartographMarkForColumn(listOf(existing), origin, existing.columnId, "gravida")).values)
    }

    @Test fun secondHalfPreviewCrossesMidnightAndOffChartContractionsKeepTheirStoredTime() {
        val existing = observation("2026-10-24T23:45:00Z").copy(
            halfHourCells = mapOf("fetalHeartRateHalf2" to "145", "contractionFrequencyHalf2" to "3", "contractionDurationHalf2" to "40"))
        val origin = kotlin.time.Instant.parse("2026-10-24T12:00:00Z").toEpochMilliseconds()
        val fhr = assertNotNull(buildPartographMarkForColumn(listOf(existing), origin, existing.columnId, "fetalHeartRate", 2))
        assertEquals("2026-10-25T00:15:00Z", fhr.observationTimestamp())
        assertEquals(existing.timestamp, fhr.timestamp)
        assertEquals(12.25, fhr.hour)
        val contractions = assertNotNull(buildPartographMarkForColumn(listOf(existing), origin, existing.columnId, "contractionFrequency", 2))
        assertEquals(existing.timestamp, contractions.timestamp)
        assertEquals(12.25, contractions.hour)
        assertEquals(11.5, buildPartographMark(emptyList(), origin, 12.0, "contractionFrequency", "3").hour)
    }

    @Test fun urineDisplayUsesPaperGradesWithoutChangingStoredCodesOrExplicitClears() {
        listOf("P-" to "−", "P Trace" to "Trace", "P+" to "+", "P+++" to "+++").forEach { (stored, displayed) ->
            val record = observation().copy(partographCells = mapOf("urineProtein" to stored))
            assertEquals(displayed, record.partographDisplayText("urineProtein"))
            assertEquals(stored, record.partographText("urineProtein"))
        }
        val acetone = observation().copy(partographCells = mapOf("urineAcetone" to "A++++", "urineProtein" to ""))
        assertEquals("++++", acetone.partographDisplayText("urineAcetone"))
        assertEquals("", acetone.partographDisplayText("urineProtein"))
        assertEquals("", observation().partographDisplayText("urineProtein"))
    }

    @Test fun legacyAmnioticStatusIsVisibleOnlyWhenRecordedAndExplicitClearsRemainBlank() {
        val legacy = observation().copy(cellKey = "amnioticFluid", recordedCellKeys = setOf("amnioticFluid"))
        listOf(AmnioticFluidStatus.CLEAR to "C", AmnioticFluidStatus.MECONIUM_STAINED to "M",
            AmnioticFluidStatus.BLOOD_STAINED to "B", AmnioticFluidStatus.ABSENT to "A",
            AmnioticFluidStatus.INTACT to "I", AmnioticFluidStatus.UNKNOWN to "").forEach { (status, expected) ->
            assertEquals(expected, legacy.copy(fetalWellbeing = legacy.fetalWellbeing.copy(amnioticFluidStatus = status))
                .partographText("amnioticFluid"))
        }
        assertEquals("M++", legacy.copy(fetalWellbeing = legacy.fetalWellbeing.copy(amnioticFluidFinding = "M++"))
            .partographText("amnioticFluid"))
        assertEquals("", legacy.copy(partographCells = mapOf("amnioticFluid" to "")).partographText("amnioticFluid"))
        assertEquals("", observation().partographText("amnioticFluid"))
    }
}
