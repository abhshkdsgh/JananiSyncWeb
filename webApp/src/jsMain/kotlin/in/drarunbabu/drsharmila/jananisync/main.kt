package `in`.drarunbabu.drsharmila.jananisync

import kotlinx.browser.window
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.ComposeViewport
import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.bindToBrowserNavigation
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.WheelEvent
import kotlin.math.exp

@OptIn(ExperimentalComposeUiApi::class)
@ExperimentalBrowserHistoryApi
fun main() {
    ComposeViewport {
        var zoom by remember { mutableStateOf(1f) }
        val density = LocalDensity.current
        DisposableEffect(Unit) {
            // Capture before the canvas consumes wheel events. A non-passive
            // listener prevents browser zoom from also scaling the same gesture.
            val wheel: (Event) -> Unit = { event ->
                val wheelEvent = event as WheelEvent
                if ((wheelEvent.ctrlKey || wheelEvent.metaKey) && wheelEvent.deltaY != 0.0) {
                    event.preventDefault()
                    event.stopImmediatePropagation()
                    val pixels = wheelEvent.deltaY * when (wheelEvent.deltaMode) {
                        1 -> 16.0 // Lines
                        2 -> window.innerHeight.toDouble() // Pages
                        else -> 1.0
                    }
                    zoom = (zoom * exp(-pixels.coerceIn(-100.0, 100.0) * 0.002)).toFloat().coerceIn(0.5f, 3f)
                }
            }
            val reset: (Event) -> Unit = { event ->
                val keyEvent = event as KeyboardEvent
                if ((keyEvent.ctrlKey || keyEvent.metaKey) && keyEvent.key == "0") {
                    zoom = 1f
                    // Also allow the browser to reset its own zoom.
                }
            }
            val options = js("({capture: true, passive: false})")
            window.addEventListener("wheel", wheel, options)
            window.addEventListener("keydown", reset, true)
            onDispose {
                window.removeEventListener("wheel", wheel, true)
                window.removeEventListener("keydown", reset, true)
            }
        }
        CompositionLocalProvider(LocalDensity provides Density(density.density * zoom, density.fontScale)) {
            App(
                onNavHostReady = { navController -> navController.bindToBrowserNavigation() },
                onBrowserBack = { window.history.back() },
                onBrowserForward = { window.history.forward() }
            )
        }
    }
}
