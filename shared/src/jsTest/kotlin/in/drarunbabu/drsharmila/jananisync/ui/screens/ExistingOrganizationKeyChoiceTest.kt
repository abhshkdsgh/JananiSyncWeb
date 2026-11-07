package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMember
import org.jetbrains.skiko.InternalSkikoApi
import org.jetbrains.skiko.wasm.awaitSkiko
import kotlin.test.*

@OptIn(ExperimentalTestApi::class, InternalSkikoApi::class)
class ExistingOrganizationKeyChoiceTest {
    @Test fun sourceMustBeChosenAndItsPhraseIsClearedOnSwitchAndSubmission() = awaitSkiko.then {
        runComposeUiTest {
            val sources = listOf(
                OrganizationMember("Clinic01", "Clinic A", "user", "doctor", "active", 0),
                OrganizationMember("Clinic02", "Clinic B", "user", "nurse", "active", 0))
            var submitted: Pair<String, String>? = null
            setContent { MaterialTheme { Column {
                ExistingOrganizationKeyChoice(sources, "Clinic03", true) { org, phrase -> submitted = org to phrase }
            } } }
            onNodeWithText("Verify and reuse selected key").assertIsNotEnabled()
            onNodeWithText("Selected organisation's recovery phrase").assertIsNotEnabled()
            onNodeWithText("Clinic A").performClick()
            onNodeWithText("Selected organisation's recovery phrase").performTextInput("old phrase")
            onNodeWithText("Clinic B").performClick()
            onNodeWithText("Selected organisation's recovery phrase")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            onNodeWithText("Verify and reuse selected key").assertIsNotEnabled()
            onNodeWithText("Selected organisation's recovery phrase").performTextInput("usable phrase")
            onNodeWithText("Verify and reuse selected key").performClick()
            runOnIdle { assertEquals("Clinic02" to "usable phrase", submitted) }
            onNodeWithText("Verify and reuse selected key").assertIsNotEnabled()
            onNodeWithText("Selected organisation's recovery phrase")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        }
    }
}
