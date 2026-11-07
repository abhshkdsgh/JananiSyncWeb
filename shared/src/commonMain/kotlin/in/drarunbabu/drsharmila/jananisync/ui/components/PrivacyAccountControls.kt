package `in`.drarunbabu.drsharmila.jananisync.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.PrivacyConsentUiState
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.PrivacyGate
import kotlin.time.Instant

@Composable
fun PrivacyAccountGate(
    state: PrivacyConsentUiState,
    onReview: () -> Unit,
    onRetry: () -> Unit,
    onHistory: () -> Unit,
    onSignOut: () -> Unit,
    signingOut: Boolean
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Complete your account privacy review", style = MaterialTheme.typography.headlineSmall)
        if (state.gate == PrivacyGate.CHECKING || state.isSaving) CircularProgressIndicator()
        Text(if (state.gate == PrivacyGate.DENIED)
            "Your denial was recorded. Organisation and clinical access are blocked. Your sign-in account and privacy history remain; contact the privacy officer to request account deletion."
        else "Organisation and clinical access stay blocked until acceptance is confirmed by the server. You can review the policy, record a decision, or sign out.")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.gate == PrivacyGate.DENIED) OutlinedButton(onClick = onReview, enabled = !state.isSaving) { Text("Review policy again") }
        if (state.gate == PrivacyGate.ERROR) OutlinedButton(onClick = onRetry, enabled = !state.isSaving) { Text("Retry server check") }
        OutlinedButton(onClick = onHistory, enabled = !state.isSaving) { Text("Privacy decision history") }
        TextButton(onClick = onSignOut, enabled = !state.isSaving && !signingOut) { Text(if (signingOut) "Signing out…" else "Sign out") }
    }
}

@Composable
fun PrivacyHistoryDialog(state: PrivacyConsentUiState, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 760.dp).fillMaxWidth(0.94f).fillMaxHeight(0.9f), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Privacy decision history", style = MaterialTheme.typography.titleLarge)
                Text("Newest 100 server records. Times below are UTC. You can select and copy these details. Browser/time fields supplied by the client are not independently verified.")
                if (state.historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.historyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                SelectionContainer(Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.history.isEmpty() && !state.historyLoading && state.historyError == null) Text("No decisions have been recorded for this account.")
                        state.history.forEach { event ->
                            Text(buildString {
                                appendLine("${event.decision} · policy ${event.policyVersion}")
                                appendLine("Server time: ${Instant.fromEpochMilliseconds(event.recordedAtMillis)}")
                                appendLine("Event: ${event.id}")
                                appendLine("Account: ${event.userId}")
                                appendLine("Email at decision: ${event.email} · verified: ${event.emailVerified}")
                                appendLine("Policy SHA-256: ${event.policySha256}")
                                appendLine("Source: ${event.source} · presentation ${event.presentationVersion} · schema ${event.schemaVersion}")
                                appendLine("Client time: ${event.clientOccurredAtMillis} ms since epoch")
                                appendLine("Client language: ${event.language}")
                                append("Client browser: ${event.userAgent}")
                            })
                            HorizontalDivider()
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onRefresh, enabled = !state.historyLoading) { Text("Refresh") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}
