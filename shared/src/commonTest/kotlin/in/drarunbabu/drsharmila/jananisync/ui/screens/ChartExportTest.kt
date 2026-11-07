package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.LabourCareUiState
import kotlin.test.*

class ChartExportTest {
    @Test fun birthOutcomeIsPrintedBesideFhrAndFullDetailsRemainInNotes() {
        val header = observation().copy(isGuideHeader = true, partographCells = mapOf(
            "deliveryMethod" to "LSCS", "babyStatus" to "Alive", "babySex" to "Female",
            "babyWeightGrams" to "2750", "apgar1" to "8", "apgar5" to "9",
            "lscsIndication" to "Prolonged labour", "babyDetails" to "NICU observation"))
        val pages = clinicalChartPages(LabourCareUiState(), listOf(observation()), header, true, 12)
        assertTrue(pages.first().contains(">Birth outcome</text>"))
        val outcome = Regex("<text x='58(?:[.]0)?' y='([^']+)'[^>]*>Alive</text>").find(pages.first())
            ?: fail("Birth outcome must be in the left margin")
        assertTrue(outcome.groupValues[1].toDouble() in
            (250.0 + PartographLayout.fetal.top)..(250.0 + PartographLayout.fetal.bottom))
        assertTrue(pages.drop(1).any { "Indication for LSCS: Prolonged labour" in it })
        assertTrue(pages.drop(1).any { "NICU observation" in it })
        val blank = clinicalChartPages(LabourCareUiState(), listOf(observation()), null, true, 12)
        assertFalse(blank.first().contains(">Birth outcome</text>"))
    }

    private fun contractionRects(svg: String) = Regex("<rect [^>]*fill='(?:url\\(#contraction-(?:dots|stripes)\\)|#222)'[^>]*/>")
        .findAll(svg).map { it.value }.toList()

    @Test fun contractionPatternsFillBothHalvesForEveryDurationCategory() {
        for ((duration, fill) in listOf("<20" to "url(#contraction-dots)", "20-40" to "url(#contraction-stripes)", ">40" to "#222")) {
            val o = observation().copy(halfHourCells = mapOf(
                "contractionFrequencyHalf1" to "3", "contractionDurationHalf1" to duration,
                "contractionFrequencyHalf2" to "2", "contractionDurationHalf2" to duration))
            val page = clinicalChartPages(LabourCareUiState(), listOf(o), null, true, 12).first()
            val boxes = contractionRects(page)
            assertEquals(5, boxes.size, duration)
            assertTrue(boxes.all { "fill='$fill'" in it })
            assertEquals(3, boxes.count { "x='151.5'" in it }, "First half must be shaded")
            assertEquals(2, boxes.count { "x='190.666" in it }, "Second half must be shaded")
        }
    }

    @Test fun legacyContractionValuesStillShadeAndExplicitClearsStayBlank() {
        val o = observation().copy(recordedCellKeys = setOf("contractionFrequency", "contractionDuration"),
            partographCells = mapOf("contractionFrequency" to "4", "contractionDuration" to "30"))
        fun boxes(value: LabourCareObservation) = contractionRects(clinicalChartPages(LabourCareUiState(), listOf(value), null, true, 12).first())
        assertEquals(4, boxes(o).size)
        assertTrue(boxes(o).all { "contraction-stripes" in it })
        assertTrue(boxes(o.copy(halfHourCells = mapOf("contractionFrequencyHalf1" to ""))).isEmpty())
        assertTrue(boxes(o.copy(halfHourCells = mapOf("contractionDurationHalf1" to ""))).isEmpty())
        assertTrue(boxes(o.copy(halfHourCells = mapOf("contractionFrequencyHalf1" to "0"))).isEmpty())
    }

    @Test fun secondHalfReadingsCrossingPageBoundaryAreNotLost() {
        val o = observation().copy(timestamp = "2026-10-28T11:30:00Z", chartStartTimestamp = "2026-10-28T00:00:00Z",
            halfHourCells = mapOf("contractionFrequencyHalf1" to "3", "contractionDurationHalf1" to "<20",
                "contractionFrequencyHalf2" to "2", "contractionDurationHalf2" to ">40",
                "amnioticFluidHalf2" to "M++", "fetalHeartRateHalf2" to "149"))
        val pages = clinicalChartPages(LabourCareUiState(), listOf(o), null, true, 12)
        assertEquals(2, pages.size)
        assertEquals(3, contractionRects(pages[0]).size)
        assertEquals(2, contractionRects(pages[1]).size)
        assertTrue(contractionRects(pages[1]).all { "x='151.5'" in it })
        assertFalse(pages[0].contains(">M++</text>"))
        assertTrue(pages[1].contains(">M++</text>"))
        val fetalY = 250.0 + PartographLayout.fetal.y(149.0)
        val fetalPoint = Regex("<text x='([^']+)' y='([^']+)'[^>]*>•</text>").find(pages[1])
            ?: fail("FHR must appear on the next page")
        assertEquals(146.0, fetalPoint.groupValues[1].toDouble(), 0.00001)
        assertEquals(fetalY + 4, fetalPoint.groupValues[2].toDouble(), 0.00001)
    }

    private fun assertCentered(svg: String, value: String, x: Double, y: Double) {
        val attributes = Regex("<text ([^>]*)>${Regex.escape(xml(value))}</text>")
            .find(svg)?.groupValues?.get(1) ?: fail("Missing exported cell value: $value")
        assertTrue(attributes.contains("text-anchor='middle'"), attributes)
        assertTrue(attributes.contains("dominant-baseline='middle'"), attributes)
        fun coordinate(name: String) = Regex("(?:^| )$name='([^']+)'").find(attributes)!!.groupValues[1].toDouble()
        assertEquals(x, coordinate("x"), 0.000001)
        assertEquals(y, coordinate("y"), 0.000001)
    }

    @Test fun lcgValuesAndMultilineTimeAreCenteredWithinTheirCells() {
        val o = observation().copy(halfHourCells = mapOf("fetalHeartRateHalf1" to "130", "fetalHeartRateHalf2" to "145"))
        val page = clinicalChartPages(LabourCareUiState(), listOf(o), null, false, 12).first()
        val width = 885.0 / 12
        assertCentered(page, "84", 250 + width / 2, 603.0)
        assertCentered(page, "130", 250 + width / 4, 423.0)
        assertCentered(page, "145", 250 + 3 * width / 4, 423.0)
        // Two time/stage lines straddle the cell's vertical midpoint.
        assertCentered(page, "I 1", 250 + width / 2, 270.0)
    }

    @Test fun partographLaneValuesAndContinuationReferencesAreCentered() {
        val o = observation().copy(medicine = "CENTER", recordedCellKeys = setOf("medicine"),
            partographCells = mapOf("medicine" to "CENTER"))
        val page = clinicalChartPages(LabourCareUiState(), listOf(o), null, true, 12).first()
        val lane = PartographLayout.lanes.first { it.field == "medicine" }
        assertCentered(page, "CENTER", PartographLayout.PLOT_LEFT + PartographLayout.PLOT_WIDTH / 24.0,
            250.0 + lane.top + lane.height / 2.0)
        val longNote = observation().copy(assessment = "Detailed assessment. ".repeat(100), recordedCellKeys = setOf("assessment"))
        val notePages = clinicalChartPages(LabourCareUiState(), listOf(longNote), null, false, 12)
        assertCentered(notePages.first(), "[1]", 250 + (885.0 / 12) / 2, 1284.0)
    }
    private fun observation() = LabourCareObservation("id", "session", "Test", 0, "2026-10-28T00:00:00Z",
        maternalVitals = MaternalVitals("", 0, 0, 0, 0.0),
        fetalWellbeing = FetalWellbeing("", 0, AmnioticFluidStatus.UNKNOWN),
        laborProgress = LaborProgress("", 0.0, 0, 0), columnId = "column", cellKey = "pulse",
        recordedCellKeys = setOf("pulse"), partographCells = mapOf("pulse" to "84"))

    @Test fun unrecordedDefaultsAndExplicitClearsStayBlank() {
        val o = observation()
        assertEquals("", o.displayValueFor("temperature"))
        assertEquals("", o.displayValueFor("fetalHeartRate"))
        assertEquals("", o.copy(partographCells = mapOf("pulse" to "")).displayValueFor("pulse"))
        val pages = clinicalChartPages(LabourCareUiState(patientLabel = "<script>&Test"), listOf(o), null, false, 12)
        assertTrue(pages.first().contains("&lt;script&gt;&amp;Test"))
        assertFalse(pages.first().contains("<script>"))
        assertTrue(pages.first().contains(">84</text>"))
    }

    @Test fun longClinicalTextIsPreservedInContinuationPages() {
        val note = "This is a long clinical assessment. ".repeat(40) + "END_OF_NOTE"
        val o = observation().copy(assessment = note, recordedCellKeys = setOf("assessment"))
        val pages = clinicalChartPages(LabourCareUiState(), listOf(o), null, false, 12)
        assertTrue(pages.size > 1)
        assertTrue(pages.first().contains("[1]"))
        assertTrue(pages.drop(1).joinToString("").contains("END_OF_NOTE"))
    }

    @Test fun lcgPaginatesAndPartographKeepsReadingsAfterTwelveHours() {
        val o = observation().copy(chartStartTimestamp = "2026-10-28T00:00:00Z")
        assertEquals(2, clinicalChartPages(LabourCareUiState(), List(13) { o }, null, false, 12).size)
        val later = o.copy(timestamp = "2026-10-28T13:00:00Z", partographCells = mapOf("pulse" to "91"))
        val pages = clinicalChartPages(LabourCareUiState(), listOf(o, later), null, true, 12)
        assertEquals(2, pages.size)
        // Later observation appears at hour 1 on its continuation chart.
        assertTrue(pages[1].contains("x='224.333"))
    }
}
