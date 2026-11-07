package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMember
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class MemberRoleControlsTest {
    @Test fun chooseSaveAndRefreshRoleWithMaintenanceDisablingControls() = awaitSkiko.then {
        runComposeUiTest {
            var member by mutableStateOf(OrganizationMember("RoleOrg1", userId = "member", role = "doctor", status = "active", joinedAt = 0))
            var enabled by mutableStateOf(true)
            var saved = ""
            setContent { MaterialTheme { MemberRoleControls(member, enabled, false) { saved = it } } }
            onNodeWithText("Doctor").assertIsSelected()
            onNodeWithText("Save role").assertIsNotEnabled()
            onNodeWithText("Nurse").performClick()
            onNodeWithText("Save role").performClick()
            runOnIdle { assertEquals("nurse", saved); member = member.copy(role = "nurse") }
            onNodeWithText("Nurse").assertIsSelected()
            onNodeWithText("Save role").assertIsNotEnabled()
            runOnIdle { enabled = false }
            onNodeWithText("Doctor").assertIsNotEnabled()
            onNodeWithText("Nurse").assertIsNotEnabled()
        }
    }
}
