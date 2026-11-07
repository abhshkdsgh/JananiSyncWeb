package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import `in`.drarunbabu.drsharmila.jananisync.data.*
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.LabourCareUiState
import kotlin.test.*
import org.jetbrains.skiko.wasm.awaitSkiko
import org.jetbrains.skiko.InternalSkikoApi

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class LcgPlotInteractionTest {
    @Test fun descentHourThreeDoesNotChangeHourTwo() = checkProgressPlot("descent", 4, 2, 1)
    @Test fun cervixHourThreeDoesNotChangeHourTwo() = checkProgressPlot("cervicalDilation", 6, 8, 9)

    private fun checkProgressPlot(field: String, previousValue: Int, firstValue: Int, nextValue: Int) =
        awaitSkiko.then {
            runComposeUiTest {
                val origin = 1_792_828_800_000L
                var records by mutableStateOf(List(3) { index ->
                    val time = kotlin.time.Instant.fromEpochMilliseconds(origin + index * CHART_HOUR).toString()
                    LabourCareObservation("event-$index", "admission", "Patient", 0, time,
                        maternalVitals = MaternalVitals(time, 120, 80, 80, 37.0),
                        fetalWellbeing = FetalWellbeing(time, 140, AmnioticFluidStatus.CLEAR),
                        laborProgress = LaborProgress(time, previousValue.toDouble(), 0, 0),
                        columnId = if (index == 2) scheduledColumnId(origin + index * CHART_HOUR) else "column-$index",
                        chartColumnIndex = index,
                        cellKey = if (index == 2) "scheduled" else field,
                        recordedCellKeys = if (index == 2) emptySet() else setOf(field),
                        descent = previousValue.toString())
                })
                var now by mutableStateOf(kotlin.time.Instant.fromEpochMilliseconds(origin + 2 * CHART_HOUR - 1).toString())
                var saving by mutableStateOf(false)
                var focused by mutableStateOf<HistoryCellFocus?>(null)
                val writes = mutableListOf<LcgCellTarget>()
                fun value(record: LabourCareObservation) = if (field == "descent") record.descent.toDouble()
                    else record.laborProgress.cervicalDilationCm
                val symbol = if (field == "descent") "O" else "X"
                setContent {
                    MaterialTheme {
                        CompositionLocalProvider(LocalCellFocus provides CellFocusState(focused, { focused = it }, {})) {
                        PlotMetricRows(field, field, if (field == "descent") (5 downTo 0).toList() else (10 downTo 5).toList(),
                            { "" }, records, -1, now,
                            LabourCareUiState(organizationKeyAvailable = true), rememberScrollState(),
                            { target, changedField, newValue ->
                                assertEquals(field, changedField)
                                val selected = parseLcgCellTarget(target)!!
                                writes += selected
                                records = records.map {
                                    if (selected.matches(it)) it.copy(columnId = "column-2", cellKey = field,
                                        descent = if (field == "descent") newValue else it.descent,
                                        laborProgress = if (field == "cervicalDilation")
                                            it.laborProgress.copy(cervicalDilationCm = newValue.toDouble()) else it.laborProgress,
                                        recordedCellKeys = it.recordedCellKeys + field) else it
                                }
                                saving = true
                            }, records.filter { it.cellKey != "scheduled" }.map {
                                LabourColumnControl(it.columnId, 10, 10, listOf("user"), 1, false,
                                    cellAccessLevels = mapOf(field to 10))
                            }, 10, saving)
                        }
                    }
                }
                onNodeWithTag("lcg:1:$field:$previousValue").assert(hasAnyDescendant(hasText(symbol)))
                onNodeWithTag("lcg:2:$field:$firstValue").performTouchInput { click() }
                runOnIdle {
                    assertTrue(writes.isEmpty())
                    now = kotlin.time.Instant.fromEpochMilliseconds(origin + 2 * CHART_HOUR).toString()
                }
                onNodeWithTag("lcg:2:$field:$firstValue").performTouchInput { click() }
                runOnIdle {
                    assertEquals(1, writes.size)
                    assertEquals(2, writes.single().index)
                    assertEquals(origin + 2 * CHART_HOUR, writes.single().time)
                    assertEquals(previousValue.toDouble(), value(records[1]))
                    assertEquals(firstValue.toDouble(), value(records[2]))
                }
                // A second click during a write must not enqueue another plot change.
                onNodeWithTag("lcg:2:$field:$nextValue").performTouchInput { click() }
                runOnIdle { assertEquals(1, writes.size); saving = false }
                onNodeWithTag("lcg:1:$field:$previousValue").assert(hasAnyDescendant(hasText(symbol)))
                onNodeWithTag("lcg:2:$field:$firstValue").assert(hasAnyDescendant(hasText(symbol)))
                onNodeWithTag("lcg:2:$field:$nextValue").performMouseInput { click() }
                runOnIdle {
                    assertEquals(2, writes.size)
                    assertEquals("column-2", writes.last().columnId)
                    assertEquals(previousValue.toDouble(), value(records[1]))
                    assertEquals(nextValue.toDouble(), value(records[2]))
                }
            }
        }
}
