package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.OrganizationAccessUiState
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class RecoveryKeyReplacementTest {
    private val member = UserMembershipState.ActiveMember("Recover1", role = "doctor")
    private val request = MemberKeyReplacementRequest("Recover1", "doctor", "R".repeat(22), "B".repeat(43), "pending")

    @Test fun adminRecoveryRequestsAppearOnlyInsideRequestsTabAndNotOnMemberDashboard() = awaitSkiko.then {
        runComposeUiTest {
            var state by mutableStateOf(OrganizationAccessUiState(membership = member.copy(role = "admin"),
                organizationKeyExists = true, organizationKeyMetadataLoaded = true, organizationKeyUnlocked = true,
                keyReplacement = MemberKeyReplacementState("Recover1", listOf(request), loaded = true)))
            setContent { MaterialTheme {
                OrganizationAccessScreen(state, onAccessCodeChange = {}, onNameChange = {}, onRoleSelected = {},
                    onPrepareSecureAccess = {}, onRecoveryPhraseConfirmed = {}, onAdminRecoveryPhraseChange = {},
                    onInitializeOrganizationKey = {}, onUnlockOrganizationKey = {}, onGrantOrganizationKey = {},
                    onRefreshKeyGrants = {}, onSubmitRequest = {}, onApprove = { _, _ -> },
                    onUpdateMemberAccessLevel = { _, _ -> }, onSetMemberActive = { _, _ -> },
                    onSelectOrganization = {}, onRetry = {}, organizationKeyAvailable = true,
                    showAdminConsole = true, onUnlockMemberOrganizationKey = { _, _ -> })
            } }
            onNodeWithText("Requests (1)").assertExists()
            onNodeWithText("Recovery key replacement requested").assertExists()
            onNodeWithText("Members").performScrollTo().performClick()
            onNodeWithText("Recovery key replacement requested").assertDoesNotExist()
            onNodeWithText("Requests (1)").performScrollTo().performClick()
            onNodeWithText("Recovery key replacement requested").assertExists()
            runOnIdle { state = state.copy(membership = member) }
            onNodeWithText("Recovery key replacement requested").assertDoesNotExist()
            onNodeWithText("Replace lost recovery key").assertDoesNotExist()
        }
    }

    @Test fun memberMustSavePhraseBeforeSubmittingAndCanCancelPendingRequest() = awaitSkiko.then {
        runComposeUiTest {
            var state by mutableStateOf(OrganizationAccessUiState(membership = member,
                keyReplacement = MemberKeyReplacementState("Recover1", loaded = true),
                replacementSetup = MemberKeySetup("new twelve word phrase", MemberKeyMaterial("public", "fingerprint", "cipher", "iv", "salt"))))
            var submits = 0
            var cancelled = false
            setContent { MaterialTheme {
                RecoveryKeyReplacement(state, member, {}, { state = state.copy(replacementPhraseSaved = it) }, {},
                    { submits++ }, { value, approve -> cancelled = value == request && !approve }, {})
            } }
            onNodeWithText("Replace lost recovery key").performClick()
            onNodeWithText("Request administrator approval").assertIsNotEnabled()
            onNode(isToggleable()).performClick()
            onNodeWithText("Request administrator approval").assertIsEnabled().performClick()
            runOnIdle { assertEquals(1, submits); state = state.copy(replacementSetup = null,
                keyReplacement = MemberKeyReplacementState("Recover1", listOf(request), loaded = true)) }
            onNodeWithText("Cancel replacement request").performClick()
            runOnIdle { assertTrue(cancelled) }
        }
    }

    @Test fun administratorMustUnlockAndConfirmIdentityBeforeApproving() = awaitSkiko.then {
        runComposeUiTest {
            val admin = member.copy(role = "admin")
            var state by mutableStateOf(OrganizationAccessUiState(membership = admin,
                keyReplacement = MemberKeyReplacementState("Recover1", listOf(request), loaded = true)))
            var approvals = 0
            setContent { MaterialTheme {
                RecoveryKeyReplacement(state, admin, {}, {}, {}, {}, { _, approve -> if (approve) approvals++ }, {})
            } }
            onNodeWithText("Review and approve replacement").assertIsNotEnabled()
            runOnIdle { state = state.copy(organizationKeyUnlocked = true) }
            onNodeWithText("Review and approve replacement").performClick()
            runOnIdle { assertEquals(0, approvals) }
            onNodeWithText("Verified — approve replacement").performClick()
            runOnIdle { assertEquals(1, approvals); state = state.copy(maintenance = OrganizationMaintenance("Recover1", active = true)) }
            onNodeWithText("Review and approve replacement").assertIsNotEnabled()
        }
    }
}
