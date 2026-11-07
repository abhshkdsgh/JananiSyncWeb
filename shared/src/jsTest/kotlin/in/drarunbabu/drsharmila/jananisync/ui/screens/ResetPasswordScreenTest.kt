package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.ResetPasswordUiState
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class ResetPasswordScreenTest {
    @Test fun prefilledEmailIsSubmittedAndControlsAreDisabledWhileSending() = awaitSkiko.then {
        runComposeUiTest {
            var state by mutableStateOf(ResetPasswordUiState())
            val submitted = mutableListOf<String>()
            var edits = 0
            var backClicks = 0
            setContent { MaterialTheme {
                ResetPasswordScreen(
                    initialEmail = "nurse@example.test",
                    state = state,
                    onSubmit = { submitted += it; state = ResetPasswordUiState(isLoading = true) },
                    onEmailChange = { edits++ },
                    onBackToLogin = { backClicks++ }
                )
            } }
            onNode(hasSetTextAction()).assertTextContains("nurse@example.test")
            onNodeWithText("Send reset link").performScrollTo().performClick()
            onNodeWithTag("reset-password-email").assertIsNotEnabled()
            onNodeWithText("Sending reset link…").assertIsNotEnabled()
            onNodeWithText("Back to log in").assertIsNotEnabled()
            runOnIdle {
                assertEquals(listOf("nurse@example.test"), submitted)
                assertEquals(0, edits)
                assertEquals(0, backClicks)
            }
        }
    }

    @Test fun resultRemainsVisibleAndEditingClearsItBeforeRetrying() = awaitSkiko.then {
        runComposeUiTest {
            val confirmation = "If an account exists, a password reset link has been sent."
            var state by mutableStateOf(ResetPasswordUiState(isSuccess = true, message = confirmation))
            val submitted = mutableListOf<String>()
            var edits = 0
            var backClicks = 0
            setContent { MaterialTheme {
                ResetPasswordScreen(
                    initialEmail = "nurse@example.test",
                    state = state,
                    onSubmit = { submitted += it },
                    onEmailChange = { edits++; state = ResetPasswordUiState() },
                    onBackToLogin = { backClicks++ }
                )
            } }
            onNodeWithText(confirmation).performScrollTo().assertIsDisplayed()
            onNodeWithText("Send reset link").performScrollTo().assertIsEnabled().performClick()
            onNodeWithText(confirmation).assertExists()
            runOnIdle { assertEquals(listOf("nurse@example.test"), submitted) }
            onNode(hasSetTextAction()).performScrollTo().performTextReplacement("doctor@example.test")
            onNodeWithText(confirmation).assertDoesNotExist()
            onNodeWithText("Send reset link").performScrollTo().performClick()
            onNodeWithText("Back to log in").performScrollTo().performClick()
            runOnIdle {
                assertEquals(1, edits)
                assertEquals(listOf("nurse@example.test", "doctor@example.test"), submitted)
                assertEquals(1, backClicks)
            }
        }
    }
}
