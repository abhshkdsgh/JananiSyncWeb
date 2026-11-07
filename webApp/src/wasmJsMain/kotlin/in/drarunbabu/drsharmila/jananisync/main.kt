@file:OptIn(ExperimentalWasmJsInterop::class)

package `in`.drarunbabu.drsharmila.jananisync

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation

@JsFun("() => window.history.back()")
private external fun browserBack()

@JsFun("() => window.history.forward()")
private external fun browserForward()

@OptIn(ExperimentalComposeUiApi::class, ExperimentalBrowserHistoryApi::class)
fun main() {
    ComposeViewport {
        App(
            onNavHostReady = { navController -> navController.bindToBrowserNavigation() },
            onBrowserBack = { browserBack() },
            onBrowserForward = { browserForward() }
        )
    }
}
