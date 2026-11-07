package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.LabourCareUiState
import kotlinx.browser.document
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLImageElement
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.assertTrue

class ChartExportRasterTest {
    @Test fun dotsSurviveSvgRasterisationInBothHalves() = assertShading("<20")
    @Test fun stripesSurviveSvgRasterisationInBothHalves() = assertShading("20-40")
    @Test fun solidFillSurvivesSvgRasterisationInBothHalves() = assertShading(">40")

    private fun assertShading(duration: String): Promise<Unit> {
        val observation = LabourCareObservation("export-test", "session", "Test", 0, "2026-10-28T00:00:00Z",
            maternalVitals = MaternalVitals("", 0, 0, 0, 0.0),
            fetalWellbeing = FetalWellbeing("", 0, AmnioticFluidStatus.UNKNOWN),
            laborProgress = LaborProgress("", 0.0, 0, 0), columnId = "column", cellKey = "partographMark",
            halfHourCells = mapOf("contractionFrequencyHalf1" to "3", "contractionDurationHalf1" to duration,
                "contractionFrequencyHalf2" to "3", "contractionDurationHalf2" to duration))
        val svg = clinicalChartPages(LabourCareUiState(), listOf(observation), null, true, 12).first()
        return Promise { resolve, reject ->
            val image = document.createElement("img") as HTMLImageElement
            image.onerror = { _, _, _, _, _ -> reject(IllegalStateException("Export SVG failed to load")); null }
            image.onload = {
                try {
                    val canvas = document.createElement("canvas") as HTMLCanvasElement
                    // Match the PDF export's rasterisation resolution.
                    canvas.width = 2380
                    canvas.height = 3368
                    val context = canvas.getContext("2d") as CanvasRenderingContext2D
                    context.drawImage(image, 0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
                    // Sample inside each half's bottom box, away from all grid lines.
                    for (x in listOf(154.0, 193.0)) {
                        val pixels = context.getImageData(x * 2, 1920.0, 60.0, 24.0).data
                        var dark = 0
                        for (index in 0 until pixels.length step 4) {
                            if ((pixels.asDynamic()[index] as Int) < 128) dark++
                        }
                        assertTrue(dark > 30, "Missing $duration shading at x=$x: $dark dark pixels")
                    }
                    resolve(Unit)
                } catch (error: Throwable) { reject(error) }
                null
            }
            image.src = "data:image/svg+xml;charset=utf-8," + js("encodeURIComponent")(svg)
        }
    }
}
