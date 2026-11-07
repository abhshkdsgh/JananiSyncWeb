package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import `in`.drarunbabu.drsharmila.jananisync.data.*
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import org.jetbrains.skiko.wasm.awaitSkiko
import org.jetbrains.skiko.InternalSkikoApi
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class PartographStageActionTest {
    @Test fun stageButtonsUseTheSharedColumnAndDisappearAfterBirth() = awaitSkiko.then {
        runComposeUiTest {
            val time = "2026-10-24T08:00:00Z"
            var observation by mutableStateOf(LabourCareObservation("event", "admission", "Synthetic", 0, time,
                maternalVitals = MaternalVitals(time, 120, 80, 80, 37.0),
                fetalWellbeing = FetalWellbeing(time, 140, AmnioticFluidStatus.CLEAR),
                laborProgress = LaborProgress(time, 4.0, 3, 40), columnId = "column", cellKey = "pulse",
                chartStartTimestamp = time, chartColumnIndex = 0))
            val control = LabourColumnControl("column", 10, 10, listOf("staff"), 1, false)
            val actions = mutableListOf<String>()
            setContent {
                MaterialTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        PartographCharts(listOf(observation), listOf(control), AdmissionColumnState(setOf("column"), false),
                            10, false, { _, _, _ -> true }, {}, { _, _ -> }, chartStartTimestamp = time,
                            nowMillis = chartMillis(time)!! + CHART_HOUR,
                            onFullDilation = { id -> actions += "dilation:$id"; observation = observation.copy(fullDilationConfirmed = true, secondStageStartTimestamp = time) },
                            onBirth = { id -> actions += "birth:$id"; observation = observation.copy(birthConfirmed = true) })
                    }
                }
            }
            onNodeWithText("Full Dilation: Confirm and skip to second stage").performScrollTo().performClick()
            onNodeWithText("Birth: Confirm and mark birth").performScrollTo().performClick()
            onNodeWithText("Birth: Confirm and mark birth").assertDoesNotExist()
            assertEquals(listOf("dilation:column", "birth:column"), actions)
        }
    }
}
