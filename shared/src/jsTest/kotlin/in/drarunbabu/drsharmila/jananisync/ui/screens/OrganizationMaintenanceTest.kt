package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.OrganizationAccessUiState
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class OrganizationMaintenanceTest {
    @Test fun membersSeeMaintenanceInsteadOfClinicalRecords() = awaitSkiko.then {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    OrganizationAccessScreen(
                        state = OrganizationAccessUiState(
                            membership = UserMembershipState.ActiveMember("Rotate01", role = "doctor"),
                            maintenance = OrganizationMaintenance("Rotate01", active = true)),
                        onAccessCodeChange = {}, onNameChange = {}, onRoleSelected = {}, onPrepareSecureAccess = {},
                        onRecoveryPhraseConfirmed = {}, onAdminRecoveryPhraseChange = {}, onInitializeOrganizationKey = {},
                        onUnlockOrganizationKey = {}, onGrantOrganizationKey = { _ -> }, onRefreshKeyGrants = {},
                        onSubmitRequest = {}, onApprove = { _, _ -> }, onUpdateMemberAccessLevel = { _, _ -> },
                        onSetMemberActive = { _, _ -> }, onSelectOrganization = {}, onRetry = {},
                        organizationKeyAvailable = true, onUnlockMemberOrganizationKey = { _, _ -> },
                        clinicalContent = { Text("Patient records") })
                }
            }
            onNodeWithText("Maintenance in progress").assertExists()
            onNodeWithText("Your organisation is updating access. Please ask your administrator to come online and unlock their account to complete the update. Clinical records will be available when maintenance finishes.").assertExists()
            onNodeWithText("Patient records").assertDoesNotExist()
        }
    }

    @Test fun adminMustUnlockBeforeResumingSavedWork() = awaitSkiko.then {
        runComposeUiTest {
            setContent { MaterialTheme {
                OrganizationMaintenanceNotice(true, 1, 3, null, false, {})
            } }
            onNodeWithText("Key grants prepared: 1 of 3").assertExists()
            onNodeWithText("Access Update: 25%").assertExists()
            onNodeWithTag("access-update-progress").assert(SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.25f, 0f..1f)))
            onNodeWithText("Resume access update").assertIsNotEnabled()
        }
    }

    @Test fun awaitingAdminHasInlineRecoveryPrompt() = awaitSkiko.then {
        runComposeUiTest {
            var phrase by mutableStateOf("")
            var unlocks = 0
            setContent { MaterialTheme {
                OrganizationMaintenanceNotice(
                    isAdmin = true, completed = 0, total = 0, message = null,
                    canResume = false, onResume = {}, awaitingAdmin = true,
                    recoveryPhrase = phrase, onRecoveryPhraseChange = { phrase = it }, onUnlock = { unlocks++ }
                )
            } }
            onNodeWithText("This Chrome profile needs the admin recovery phrase to resume the key update. Unlocking will start the update automatically.").assertExists()
            onNodeWithTag("maintenance-recovery-phrase").performTextInput("twelve words")
            onNodeWithTag("maintenance-unlock-and-resume").performClick()
            runOnIdle { assertEquals("twelve words", phrase); assertEquals(1, unlocks) }
        }
    }

    @Test fun completedUpdateShowsOneHundredPercentAndNoResumeAction() = awaitSkiko.then {
        runComposeUiTest {
            setContent { MaterialTheme { OrganizationMaintenanceNotice(true, 3, 3, null, false, {}, active = false) } }
            onNodeWithText("Access Update: 100%").assertExists()
            onNodeWithText("Access Update complete").assertExists()
            onNodeWithText("Resume access update").assertDoesNotExist()
        }
    }

    @Test fun manualRotationRequiresConfirmationAndDisablesDuringMaintenance() = awaitSkiko.then {
        runComposeUiTest {
            var state by mutableStateOf(OrganizationAccessUiState(
                membership = UserMembershipState.ActiveMember("Rotate01", role = "admin"),
                organizationKeyExists = true, organizationKeyUnlocked = true))
            var starts = 0
            setContent { MaterialTheme { ManualKeyRotationControls(state) { starts++ } } }
            onNodeWithText("Rotate organisation key").performClick()
            runOnIdle { assertEquals(0, starts) }
            onNodeWithText("Start Access Update").performClick()
            runOnIdle { assertEquals(1, starts); state = state.copy(maintenance = OrganizationMaintenance("Rotate01", active = true)) }
            onNodeWithText("Rotate organisation key").assertIsNotEnabled()
        }
    }
}
