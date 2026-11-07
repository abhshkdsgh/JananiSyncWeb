package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.ui.components.AppWindowSize
import `in`.drarunbabu.drsharmila.jananisync.ui.components.LocalAppWindowSize
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class LcgHalfHourAlertTest {
    @Test fun wideLayoutAlertsOnlyTheAbnormalHalf() = checkHalfHourAlerts(AppWindowSize.Expanded)
    @Test fun compactLayoutAlertsOnlyTheAbnormalHalf() = checkHalfHourAlerts(AppWindowSize.Compact)

    private fun checkHalfHourAlerts(size: AppWindowSize) = awaitSkiko.then {
        runComposeUiTest {
            val time = "2026-10-24T08:00:00Z"
            val observation = LabourCareObservation("event", "admission", "", 0, time,
                maternalVitals = MaternalVitals(time, 120, 80, 80, 37.0),
                fetalWellbeing = FetalWellbeing(time, 100, AmnioticFluidStatus.CLEAR),
                laborProgress = LaborProgress(time, 5.0, 3, 40), columnId = "column",
                halfHourCells = mapOf("fetalHeartRateHalf1" to "100", "fetalHeartRateHalf2" to "140"))
            setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalAppWindowSize provides size) {
                        HalfHourGridCell("fetalHeartRate", observation, 0, "", 42.dp, {}, { _, _ -> }, "",
                            editable = { false }, enabled = true)
                    }
                }
            }
            onNodeWithTag("lcg:0:fetalHeartRateHalf1:-1").assert(hasAnyDescendant(hasContentDescription("Abnormal observation")))
            onNodeWithTag("lcg:0:fetalHeartRateHalf2:-1").assert(!hasAnyDescendant(hasContentDescription("Abnormal observation")))
        }
    }

    @Test fun categoricalClearCommitsAnEmptyValueForTheSelectedHalf() = awaitSkiko.then {
        runComposeUiTest {
            val time = "2026-10-24T08:00:00Z"
            val observation = LabourCareObservation("event", "admission", "", 0, time,
                maternalVitals = MaternalVitals(time, 120, 80, 80, 37.0),
                fetalWellbeing = FetalWellbeing(time, 140, AmnioticFluidStatus.CLEAR),
                laborProgress = LaborProgress(time, 5.0, 3, 40), columnId = "column",
                halfHourCells = mapOf("fhrDecelerationHalf1" to "L"))
            val commits = mutableListOf<Pair<String, String>>()
            setContent {
                MaterialTheme {
                    HalfHourGridCell("fhrDeceleration", observation, 0, "", 42.dp, {},
                        { key, value -> commits += key to value }, "", editable = { true }, enabled = true)
                }
            }
            onNodeWithText("L").performClick()
            onNodeWithText("Clear recorded value").performClick()
            runOnIdle { assertEquals(listOf("fhrDecelerationHalf1" to ""), commits) }
        }
    }
}
