package `in`.drarunbabu.drsharmila.jananisync.ui.navigation

import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClinicalNavigationTest {
    @Serializable
    private data object Settings

    private fun controller() = NavHostController().apply {
        navigatorProvider.addNavigator(ComposeNavigator())
        graph = createGraph(startDestination = SignedInRoute()) {
            composable<SignedInRoute> { }
            composable<Settings> { }
        }
    }

    @Test
    fun archivedWomanChartReturnsToArchiveScreenThenWardList() {
        val controller = controller()
        val archive = SignedInRoute(archivedWomen = true)
        controller.navigate(archive)
        for (partograph in listOf(false, true)) {
            val chart = SignedInRoute("ward-a", "a".repeat(22), partograph = partograph, returnToArchivedWomen = true)
            assertTrue(chart.isWellFormed())
            controller.navigate(chart)
            assertEquals(chart, controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
            assertEquals(archive, chart.parent())
            assertEquals(archive, chart.copy(partograph = !partograph).parent())
            assertTrue(controller.popBackStack())
            assertEquals(archive, controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
        }
        assertTrue(controller.popBackStack())
        assertEquals(SignedInRoute(), controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
    }

    @Test
    fun adminConsoleReturnsToTheSameClinicalChart() {
        val controller = controller()
        val guide = SignedInRoute("ward-a", "a".repeat(22))
        controller.navigate(guide)
        controller.navigate(SignedInRoute(adminConsole = true))
        assertTrue(controller.currentBackStackEntry!!.toRoute<SignedInRoute>().adminConsole)
        assertTrue(controller.popBackStack())
        assertEquals(guide, controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
    }

    @Test
    fun backReturnsFromGuideToWomenThenWards() {
        val controller = controller()
        val women = SignedInRoute(wardId = "ward-a")
        val guide = women.copy(admissionId = "a".repeat(22))
        controller.navigate(women)
        controller.navigate(guide)
        assertEquals(guide, controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
        assertTrue(controller.popBackStack())
        assertEquals(women, controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
        assertTrue(controller.popBackStack())
        assertEquals(SignedInRoute(), controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
        assertNull(controller.previousBackStackEntry)
    }

    @Test
    fun settingsAndJoiningReturnToTheSameClinicalDestination() {
        val controller = controller()
        val guide = SignedInRoute("ward-a", "a".repeat(22))
        controller.navigate(SignedInRoute("ward-a"))
        controller.navigate(guide)
        controller.navigate(Settings)
        assertTrue(controller.popBackStack())
        assertEquals(guide, controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
        controller.navigate(guide.copy(joiningOrganization = true))
        assertTrue(controller.popBackStack())
        assertEquals(guide, controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
    }

    @Test
    fun switchingOrganizationsClearsPreviousClinicalHistory() {
        val controller = controller()
        controller.navigate(SignedInRoute("ward-a"))
        controller.navigate(SignedInRoute("ward-a", "a".repeat(22)))
        controller.navigate(SignedInRoute()) {
            popUpTo(controller.graph.id) { inclusive = false }
            launchSingleTop = true
        }
        assertEquals(SignedInRoute(), controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
        assertNull(controller.previousBackStackEntry)
    }

    @Test
    fun partographNavigationRestoresModeAndReturnsToWomen() {
        val controller = controller()
        val women = SignedInRoute("ward-a")
        val partograph = women.copy(admissionId = "a".repeat(22), partograph = true)
        controller.navigate(women)
        controller.navigate(partograph)
        controller.navigate(Settings)
        assertTrue(controller.popBackStack())
        assertEquals(partograph, controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
        assertEquals(women, partograph.parent())
        assertTrue(controller.popBackStack())
        assertEquals(women, controller.currentBackStackEntry!!.toRoute<SignedInRoute>())
        assertTrue(!women.copy(partograph = true).isWellFormed())
    }

    @Test
    fun directDestinationsHavePredictableParents() {
        val guide = SignedInRoute("ward-a", "a".repeat(22))
        assertEquals(guide, guide.copy(joiningOrganization = true).parent())
        assertEquals(SignedInRoute("ward-a"), guide.parent())
        assertEquals(SignedInRoute(), guide.parent()!!.parent())
        assertNull(SignedInRoute().parent())
        assertTrue(!SignedInRoute(returnToArchivedWomen = true).isWellFormed())
        assertTrue(!SignedInRoute(archivedWomen = true, returnToArchivedWomen = true).isWellFormed())
    }
}
