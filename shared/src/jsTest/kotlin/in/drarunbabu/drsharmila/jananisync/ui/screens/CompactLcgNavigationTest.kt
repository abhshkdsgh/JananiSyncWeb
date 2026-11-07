package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.data.LabourCareFeed
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.LabourCareUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class CompactLcgNavigationTest {
    private fun observation(index: Int) = LabourCareObservation("event-$index", "session", "Synthetic", 0,
        "2026-10-24T${(8 + index).toString().padStart(2, '0')}:00:00Z",
        maternalVitals = MaternalVitals("", 120, 80, 80, 37.0),
        fetalWellbeing = FetalWellbeing("", 140, AmnioticFluidStatus.CLEAR),
        laborProgress = LaborProgress("", 5.0, 3, 40), columnId = "column-$index", cellKey = "pulse",
        recordedCellKeys = setOf("pulse"), chartColumnIndex = index)

    @Test fun arrowsSkipThePresentationGapInBothDirections() = awaitSkiko.then {
        runComposeUiTest {
            val records = listOf(observation(0), observation(1).copy(fullDilationConfirmed = true), observation(2)).withSecondStageGap()
            var state by mutableStateOf(LabourCareUiState(orgId = "Org12345", patientSessionId = "session", activeColumnId = "column-1"))
            val selected = mutableListOf<String>()
            setContent { MaterialTheme {
                Box(Modifier.width(360.dp)) {
                    CompactCareGuide(state, records, -1, 10, { _, _, _ -> }, {}, {}, {}, { _, _ -> }, {},
                        onSelectColumn = { selected += it; state = state.copy(activeColumnId = it) })
                }
            } }
            onNodeWithContentDescription("Next time column").performTouchInput { click() }
            runOnIdle { assertEquals("column-2", selected.last()) }
            onNodeWithText("SECOND STAGE").assertExists()
            onNodeWithContentDescription("Previous time column").performTouchInput { click() }
            runOnIdle { assertEquals("column-1", selected.last()) }
        }
    }

    @Test fun pagingPreservesTheInnerAndOuterScrollPositions() = checkPagingScroll(atBottom = false)
    @Test fun pagingFromARecordedColumnToAnEmptyColumnPreservesTheBottomPosition() = checkPagingScroll(atBottom = true)

    private fun checkPagingScroll(atBottom: Boolean) = awaitSkiko.then {
        runComposeUiTest {
            val records = listOf(observation(0), observation(1).let { if (atBottom) it.copy(cellKey = "scheduled") else it })
            var state by mutableStateOf(LabourCareUiState(orgId = "Org12345", patientSessionId = "session", activeColumnId = "column-0",
                feed = LabourCareFeed(columnControls = listOf(LabourColumnControl("column-0", 10, 10, listOf("staff"), 1, false)))))
            lateinit var list: androidx.compose.foundation.lazy.LazyListState
            lateinit var outer: androidx.compose.foundation.ScrollState
            lateinit var scope: CoroutineScope
            setContent { MaterialTheme {
                list = rememberLazyListState()
                outer = rememberScrollState()
                scope = rememberCoroutineScope()
                Box(Modifier.width(360.dp).height(500.dp)) {
                    Column(Modifier.fillMaxSize().verticalScroll(outer)) {
                        Spacer(Modifier.height(240.dp))
                        CompactCareGuide(state, records, -1, 10, { _, _, _ -> }, {}, {}, {}, { _, _ -> }, {},
                            onSelectColumn = { state = state.copy(activeColumnId = it) }, scrollState = list)
                        Spacer(Modifier.height(300.dp))
                    }
                }
            } }
            runOnIdle { scope.launch { outer.scrollTo(180); list.scrollToItem(if (atBottom) list.layoutInfo.totalItemsCount - 1 else 3, 24) } }
            waitForIdle()
            var index = 0; var offset = 0; var outerOffset = 0
            runOnIdle { index = list.firstVisibleItemIndex; offset = list.firstVisibleItemScrollOffset; outerOffset = outer.value }
            onNodeWithContentDescription("Next time column").performTouchInput { click() }
            waitForIdle()
            runOnIdle {
                assertEquals("column-1", state.activeColumnId)
                assertEquals(index, list.firstVisibleItemIndex, "Table row changed")
                assertEquals(offset, list.firstVisibleItemScrollOffset, "Table offset changed")
                assertEquals(outerOffset, outer.value, "Page offset changed")
            }
        }
    }
}
