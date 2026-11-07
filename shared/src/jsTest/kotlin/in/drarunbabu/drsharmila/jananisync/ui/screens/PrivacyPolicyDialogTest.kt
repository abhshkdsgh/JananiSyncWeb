package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import `in`.drarunbabu.drsharmila.jananisync.ui.components.PrivacyPolicyDialog
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class PrivacyPolicyDialogTest {
    @Test fun exitHistoryAndOptionalReviewCancelDoNotSubmitDecisions() = awaitSkiko.then {
        runComposeUiTest {
            var saving by mutableStateOf(false)
            var decisions = 0
            var exits = 0
            var histories = 0
            var cancels = 0
            setContent { MaterialTheme {
                PrivacyPolicyDialog(true, saving, null, { decisions++ }, { decisions++ }, {},
                    onExit = { exits++ }, onHistory = { histories++ }, onCancelReview = { cancels++ })
            } }
            onNodeWithText("Sign out").performClick()
            onNodeWithText("Decision history").performClick()
            onNodeWithText("Cancel").performClick()
            runOnIdle {
                assertEquals(0, decisions)
                assertEquals(1, exits)
                assertEquals(1, histories)
                assertEquals(1, cancels)
                saving = true
            }
            onNodeWithText("Sign out").assertIsNotEnabled()
            onNodeWithText("Decision history").assertIsNotEnabled()
            onNodeWithText("Cancel").assertIsNotEnabled()
        }
    }

    @Test fun decisionIsExplicitAndCannotBeSubmittedAgainWhileSaving() = awaitSkiko.then {
        runComposeUiTest {
            var saving by mutableStateOf(false)
            var error by mutableStateOf<String?>(null)
            var accepted = 0
            var denied = 0
            setContent { MaterialTheme {
                PrivacyPolicyDialog(true, saving, error, { accepted++ }, { denied++ }, {})
            } }
            runOnIdle { assertEquals(0, accepted); assertEquals(0, denied) }
            onNodeWithText("Close").assertDoesNotExist()
            onNodeWithTag("privacy-policy-accept").performClick()
            onNodeWithTag("privacy-policy-deny").performClick()
            runOnIdle { assertEquals(1, accepted); assertEquals(1, denied); saving = true }
            onNodeWithTag("privacy-policy-accept").assertIsNotEnabled()
            onNodeWithTag("privacy-policy-deny").assertIsNotEnabled()
            runOnIdle { saving = false; error = "Decision was not saved. Retry." }
            onNodeWithTag("privacy-policy-error").assertTextEquals("Decision was not saved. Retry.")
            onNodeWithTag("privacy-policy-accept").assertIsEnabled()
            onNodeWithTag("privacy-policy-deny").assertIsEnabled()
        }
    }

    @Test fun readingPolicyDoesNotCreateDecisionAndCanBeClosed() = awaitSkiko.then {
        runComposeUiTest {
            var decisions = 0
            var closes = 0
            setContent { MaterialTheme {
                PrivacyPolicyDialog(false, false, null, { decisions++ }, { decisions++ }, { closes++ })
            } }
            onNodeWithTag("privacy-policy-accept").assertDoesNotExist()
            onNodeWithTag("privacy-policy-deny").assertDoesNotExist()
            onNodeWithText("Close").performClick()
            runOnIdle { assertEquals(0, decisions); assertEquals(1, closes) }
        }
    }
}
