package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.AppearanceSettings
import org.jetbrains.skiko.wasm.awaitSkiko
import org.jetbrains.skiko.InternalSkikoApi
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.OrganizationAccessUiState
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class SettingsFingerprintTest {
    @Test fun settingsLabelsOrganizationAndClosesFingerprintDialogOnSwitch() = awaitSkiko.then {
        runComposeUiTest {
            var member by mutableStateOf(UserMembershipState.ActiveMember("Clinic01", "Clinic A", "doctor"))
            setContent { MaterialTheme {
                SettingsScreen(AppearanceSettings(), {}, {}, {}, {}, authenticatedUserUid = "staff",
                    publicKeyFingerprint = "verified fingerprint", organizationAccessState = OrganizationAccessUiState(membership = member))
            } }
            onNodeWithText("Organisation: Clinic A (Clinic01)").assertExists()
            onNodeWithText("Public-key fingerprint").performScrollTo().performClick()
            onNodeWithText("Your public-key fingerprint").assertExists()
            runOnIdle { member = member.copy(orgId = "Clinic02", organizationName = "Clinic B") }
            onNodeWithText("Your public-key fingerprint").assertDoesNotExist()
            onNodeWithText("Organisation: Clinic B (Clinic02)").assertExists()
        }
    }

    @Test fun replacementIsBesideFingerprintForClinicalMembersOnly() = awaitSkiko.then {
        runComposeUiTest {
            var uid by mutableStateOf<String?>("staff")
            var membership by mutableStateOf<UserMembershipState>(UserMembershipState.ActiveMember("Recover1", role = "doctor"))
            var prepares = 0
            setContent { MaterialTheme {
                SettingsScreen(AppearanceSettings(), {}, {}, {}, {}, authenticatedUserUid = uid,
                    organizationAccessState = OrganizationAccessUiState(membership = membership,
                        keyReplacement = MemberKeyReplacementState("Recover1", loaded = true)),
                    onPrepareReplacementKey = { prepares++ })
            } }
            onNodeWithText("Public-key fingerprint").assertExists()
            onNodeWithText("Replace lost recovery key").performScrollTo().performClick()
            onNodeWithText("Generate new recovery key").performScrollTo().performClick()
            runOnIdle { assertEquals(1, prepares); membership = UserMembershipState.ActiveMember("Recover1", role = "admin") }
            onNodeWithText("Replace lost recovery key").assertDoesNotExist()
            runOnIdle { membership = UserMembershipState.ActiveMember("Recover1", role = "nurse") }
            onNodeWithText("Replace lost recovery key").assertExists()
            runOnIdle { membership = UserMembershipState.Deactivated("Recover1") }
            onNodeWithText("Replace lost recovery key").assertDoesNotExist()
            runOnIdle { membership = UserMembershipState.ActiveMember("Recover1", role = "doctor"); uid = null }
            onNodeWithText("Replace lost recovery key").assertDoesNotExist()
        }
    }

    @Test fun fingerprintEntryAndDialogAreRemovedWhenAuthenticationEnds() = awaitSkiko.then {
        runComposeUiTest {
            var userUid by mutableStateOf<String?>(null)
            val fingerprint = "A".repeat(43)
            setContent {
                MaterialTheme {
                    SettingsScreen(AppearanceSettings(), {}, {}, {}, {},
                        authenticatedUserUid = userUid, publicKeyFingerprint = fingerprint)
                }
            }
            onNodeWithText("Public-key fingerprint").assertDoesNotExist()
            runOnIdle { userUid = "staff" }
            onNodeWithText("Public-key fingerprint").performScrollTo().performClick()
            onNodeWithText("Your public-key fingerprint").assertIsDisplayed()
            onNodeWithText(fingerprint).assertIsDisplayed()
            runOnIdle { userUid = null }
            onNodeWithText("Public-key fingerprint").assertDoesNotExist()
            onNodeWithText("Your public-key fingerprint").assertDoesNotExist()
            runOnIdle { userUid = "another-user" }
            onNodeWithText("Your public-key fingerprint").assertDoesNotExist()
        }
    }

    @Test fun verificationPhraseIsSubmittedFromTheDialogAndClearedOnClose() = awaitSkiko.then {
        runComposeUiTest {
            var submitted: String? = null
            setContent {
                MaterialTheme {
                    SettingsScreen(AppearanceSettings(), {}, {}, {}, {}, authenticatedUserUid = "staff",
                        canVerifyFingerprint = true, onVerifyFingerprint = { submitted = it })
                }
            }
            onNodeWithText("Public-key fingerprint").performScrollTo().performClick()
            onNodeWithText("Your personal recovery phrase").performTextInput("synthetic recovery phrase")
            onNodeWithText("Verify and show fingerprint").performClick()
            runOnIdle { assertEquals("synthetic recovery phrase", submitted) }
            onNodeWithText("Verify and show fingerprint").assertIsNotEnabled()
            onNodeWithText("Close").performClick()
            onNodeWithText("Public-key fingerprint").performClick()
            onNodeWithText("Verify and show fingerprint").assertIsNotEnabled()
        }
    }
}
