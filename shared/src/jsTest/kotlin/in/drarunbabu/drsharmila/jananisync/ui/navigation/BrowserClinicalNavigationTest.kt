package `in`.drarunbabu.drsharmila.jananisync.ui.navigation

import androidx.navigation.ExperimentalBrowserHistoryApi
import androidx.navigation.NavHostController
import androidx.navigation.bindToBrowserNavigation
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import kotlinx.browser.window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalBrowserHistoryApi::class)
class BrowserClinicalNavigationTest {
    @Test
    fun toolbarAndBrowserHistoryCanBeMixedWithoutBouncingBackToTheGuide() = runTest {
        val controller = NavHostController().apply {
            navigatorProvider.addNavigator(ComposeNavigator())
            graph = createGraph(startDestination = SignedInRoute()) {
                composable<SignedInRoute> { }
            }
        }
        val originalUrl = window.location.href
        val originalState = window.history.state
        window.history.replaceState(null, "", originalUrl.substringBefore('#'))
        val binding = backgroundScope.launch(Dispatchers.Default) { controller.bindToBrowserNavigation() }
        suspend fun awaitBrowser(condition: () -> Boolean) = withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (!condition()) delay(10) }
        }
        try {
            awaitBrowser { window.history.state != null }
            val women = SignedInRoute("ward-a")
            val guide = women.copy(admissionId = "a".repeat(22))
            controller.navigate(women)
            awaitBrowser { window.location.hash.contains("ward-a") }
            controller.navigate(guide)
            awaitBrowser { window.location.hash.contains("a".repeat(22)) }
            val historyLength = window.history.length

            // The toolbar delegates to the same browser operation, rather than
            // popping the controller and accidentally pushing a new history entry.
            window.history.back()
            awaitBrowser { controller.currentBackStackEntry?.toRoute<SignedInRoute>() == women }
            assertEquals(historyLength, window.history.length)
            window.history.back()
            awaitBrowser { controller.currentBackStackEntry?.toRoute<SignedInRoute>() == SignedInRoute() }
            window.history.forward()
            awaitBrowser { controller.currentBackStackEntry?.toRoute<SignedInRoute>() == women }
            window.history.forward()
            awaitBrowser { controller.currentBackStackEntry?.toRoute<SignedInRoute>() == guide }
            assertEquals(historyLength, window.history.length)
        } finally {
            binding.cancelAndJoin()
            window.history.replaceState(originalState, "", originalUrl)
        }
    }
}
