package `in`.drarunbabu.drsharmila.jananisync

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMaintenance
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMember
import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class AccountMenuTest {
    @Test fun multipleMembershipsOfferSwitchingAndCloseTheMenuAfterSelection() = awaitSkiko.then {
        runComposeUiTest {
            var switches = 0
            var joins = 0
            setContent { MaterialTheme {
                AccountMenu(
                    membership = adminMembership(2), fallbackName = "Admin", isSigningOut = false,
                    onSignOut = {}, onJoinAnotherOrganization = { joins++ },
                    onSwitchOrganization = { switches++ }, isLeavingOrganization = false,
                    maintenance = OrganizationMaintenance(), onLeaveOrganization = {}
                )
            } }
            onNode(hasClickAction() and hasText("Admin")).performClick()
            onNodeWithText("Switch organisation").performScrollTo().performClick()
            onNodeWithText("Switch organisation").assertDoesNotExist()
            onNodeWithText("Join another organisation").assertDoesNotExist()
            runOnIdle { assertEquals(1, switches); assertEquals(0, joins) }
        }
    }

    @Test fun switchingRequiresMultipleMembershipsAndIsDisabledDuringSignout() = awaitSkiko.then {
        runComposeUiTest {
            var count by mutableStateOf(1)
            var signingOut by mutableStateOf(false)
            var switches = 0
            setContent { MaterialTheme {
                AccountMenu(
                    membership = adminMembership(count), fallbackName = "Admin", isSigningOut = signingOut,
                    onSignOut = {}, onJoinAnotherOrganization = {}, onSwitchOrganization = { switches++ },
                    isLeavingOrganization = false, maintenance = OrganizationMaintenance(), onLeaveOrganization = {}
                )
            } }
            onNode(hasClickAction() and hasText("Admin")).performClick()
            onNodeWithText("Switch organisation").assertDoesNotExist()
            onNodeWithText("Join another organisation").assertExists()
            runOnIdle { count = 2; signingOut = true }
            onNodeWithText("Switch organisation").performScrollTo().assertIsNotEnabled()
            runOnIdle { assertEquals(0, switches); signingOut = false }
            onNodeWithText("Switch organisation").assertIsEnabled().performClick()
            runOnIdle { assertEquals(1, switches) }
        }
    }
}

private fun adminMembership(count: Int): UserMembershipState.ActiveMember {
    val organizations = (1..count).map { index ->
        OrganizationMember("Clinic0$index", organizationName = "Clinic $index", userId = "admin",
            role = "admin", status = "active", joinedAt = 0)
    }
    return UserMembershipState.ActiveMember("Clinic01", organizationName = "Clinic 1", role = "admin",
        name = "Admin", availableOrganizations = organizations)
}
