package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.AccentColour
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.AppearanceSettings
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.ThemeMode
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgButton
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgText
import `in`.drarunbabu.drsharmila.jananisync.ui.theme.swatch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppearanceSettings,
    onThemeMode: (ThemeMode) -> Unit,
    onFontSize: (Int) -> Unit,
    onAccentColour: (AccentColour) -> Unit,
    onDone: () -> Unit,
    onPrivacyPolicy: () -> Unit = {},
    onPrivacyDecision: () -> Unit = {},
    onPrivacyHistory: () -> Unit = {},
    authenticatedUserUid: String? = null,
    publicKeyFingerprint: String = "",
    canVerifyFingerprint: Boolean = false,
    isVerifyingFingerprint: Boolean = false,
    fingerprintError: String? = null,
    onVerifyFingerprint: (String) -> Unit = {},
    onDismissFingerprint: () -> Unit = {},
    organizationAccessState: `in`.drarunbabu.drsharmila.jananisync.viewmodel.OrganizationAccessUiState? = null,
    onPrepareReplacementKey: () -> Unit = {},
    onReplacementPhraseSaved: (Boolean) -> Unit = {},
    onDiscardReplacementSetup: () -> Unit = {},
    onSubmitKeyReplacement: () -> Unit = {},
    onResolveKeyReplacement: (`in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementRequest, Boolean) -> Unit = { _, _ -> },
    onRetryKeyReplacements: () -> Unit = {}
) {
    // Commit the slider when released so changing density cannot move it mid-drag.
    var fontSize by remember(settings.basicFontSize0) { mutableStateOf(settings.basicFontSize0.toFloat()) }
    val membership = organizationAccessState?.membership
    val organizationLabel = when (membership) {
        is `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState.ActiveMember -> "${membership.organizationName} (${membership.orgId})"
        is `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState.PendingApproval -> membership.orgId
        else -> null
    }
    var showFingerprint by remember(authenticatedUserUid, organizationLabel) { mutableStateOf(false) }
    if (authenticatedUserUid != null && showFingerprint) {
        PublicKeyFingerprintDialog(publicKeyFingerprint, canVerifyFingerprint, isVerifyingFingerprint,
            fingerprintError, onVerifyFingerprint, organizationLabel = organizationLabel, onDismiss = {
                showFingerprint = false
                onDismissFingerprint()
            })
    }
    Column(
        Modifier.widthIn(max = 720.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MgText(text= "Settings", basicFontSize0 = settings.basicFontSize0, times = 1.21f, isBold = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onPrivacyPolicy) { Text("Privacy policy") }
            if (authenticatedUserUid != null) {
                OutlinedButton(onClick = onPrivacyDecision) { Text("Change privacy decision") }
                OutlinedButton(onClick = onPrivacyHistory) { Text("Privacy decision history") }
            }
        }
        if (authenticatedUserUid != null) {
            organizationLabel?.let { Text("Organisation: $it", style = MaterialTheme.typography.titleMedium) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDismissFingerprint(); showFingerprint = true }) {
                    Text("Public-key fingerprint")
                }
                val member = membership as? `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState.ActiveMember
                if (organizationAccessState != null && member != null && member.role in setOf("doctor", "nurse")) {
                    RecoveryKeyReplacement(organizationAccessState, member, onPrepareReplacementKey, onReplacementPhraseSaved,
                        onDiscardReplacementSetup, onSubmitKeyReplacement, onResolveKeyReplacement, onRetryKeyReplacements)
                }
            }
            HorizontalDivider()
        }
        MgText(text= "Appearance is saved in this browser on this device, across sessions and logins.", basicFontSize0 = settings.basicFontSize0)
        MgText(text= "Theme", basicFontSize0 = settings.basicFontSize0, times = 1.21f, isBold = true)
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                ChoiceRow(mode.label, settings.themeMode == mode, { onThemeMode(mode) })
            }
        }
        HorizontalDivider()
        MgText(text= "Font size: ${fontSize.roundToInt()}", basicFontSize0 = settings.basicFontSize0, times = 1.21f, isBold = true)
        Slider(
            value = fontSize,
            onValueChange = { fontSize = it.roundToInt().toFloat() },
            onValueChangeFinished = { onFontSize(fontSize.roundToInt()) },
            valueRange = AppearanceSettings.MIN_FONT_SIZE.toFloat()..AppearanceSettings.MAX_FONT_SIZE.toFloat(),
            steps = AppearanceSettings.MAX_FONT_SIZE - AppearanceSettings.MIN_FONT_SIZE - 1,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Font size" },
        )
        MgText(
            text= "Text and interface sizes change together.",
            basicFontSize0 = settings.basicFontSize0
        )
        HorizontalDivider()
        MgText(text = "Accent colour", basicFontSize0 = settings.basicFontSize0, times = 1.21f, isBold = true)
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AccentColour.entries.forEach { colour ->
                ChoiceRowColour(colour = colour, label = colour.label, settings.accentColour == colour, { onAccentColour(colour) },
                    modifier = Modifier.width(IntrinsicSize.Max).heightIn(min = 48.dp)) {
                    Box(Modifier.size(24.dp).background(colour.swatch(), CircleShape))
                }
            }
        }
        HorizontalDivider()
        MgButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            text = "Done",
            basicFontSize0 = settings.basicFontSize0
        )
    }
}

@Composable
private fun PublicKeyFingerprintDialog(
    fingerprint: String,
    canVerify: Boolean,
    verifying: Boolean,
    error: String?,
    onVerify: (String) -> Unit,
    organizationLabel: String?,
    onDismiss: () -> Unit
) {
    // Keep the recovery secret inside this dialog and discard it on submit or close.
    var recoveryPhrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your public-key fingerprint") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                organizationLabel?.let { Text("Organisation: $it") }
                when {
                    fingerprint.isNotBlank() -> {
                        SelectionContainer { Text(fingerprint) }
                        Text("Share this fingerprint with your administrator through a separate authenticated channel.")
                    }
                    canVerify -> {
                        OutlinedTextField(
                            value = recoveryPhrase,
                            onValueChange = { recoveryPhrase = it },
                            label = { Text("Your personal recovery phrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !verifying,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(enabled = recoveryPhrase.isNotBlank() && !verifying, onClick = {
                            val phrase = recoveryPhrase
                            recoveryPhrase = ""
                            onVerify(phrase)
                        }) { Text(if (verifying) "Verifying…" else "Verify and show fingerprint") }
                    }
                    else -> Text("Verify your email and prepare your organisation encryption key to view its fingerprint.")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    )
}

@Composable
private fun ChoiceRowColour(colour: AccentColour, label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), swatch: @Composable () -> Unit = {}) {
    Row(
        modifier
            .selectable(selected = selected, onClick = onClick)
            .padding(vertical = 8.dp)
            .let{
                if(selected)
                    it.background(
                        color = colour.swatch().copy(alpha = 0.25f),
                        shape = MaterialTheme.shapes.small
                    )
                else it
            }
            .border(2.dp, color = colour.swatch(), MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp)
        ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        swatch()
        Text(label, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), swatch: @Composable () -> Unit = {}){
    Row(
        modifier
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 8.dp)
        ,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RadioButton(selected = selected, onClick = null)
        swatch()
        Text(label, modifier = Modifier.weight(1f))
    }
}
