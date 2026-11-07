package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.ui.components.AccessLevelText
import `in`.drarunbabu.drsharmila.jananisync.data.model.Woman
import `in`.drarunbabu.drsharmila.jananisync.data.model.WomanArchiveEvent
import `in`.drarunbabu.drsharmila.jananisync.data.model.canArchiveWomen
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgButton
import `in`.drarunbabu.drsharmila.jananisync.ui.components.WrappingActions
import `in`.drarunbabu.drsharmila.jananisync.ui.formatUiTimestamp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun ArchivedWomenScreen(
    women: List<Woman>, accessLevel: Int,
    onSetArchived: suspend (Woman, Boolean) -> Unit,
    onSelect: (Woman) -> Unit, onPartograph: (Woman) -> Unit,
    onLoadHistory: suspend (String, Int?) -> `in`.drarunbabu.drsharmila.jananisync.data.ArchiveHistoryPage
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matches = women.filter { it.matchesWomanSearch(query) }
    var page by remember(query, matches.size) { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Archived women", style = MaterialTheme.typography.headlineSmall)
        Text("Archived admissions are kept here with their clinical records and archive history.")
        OutlinedTextField(query, { query = it.take(160) }, label = { Text("Search archived women") },
            placeholder = { Text("Name, CR, IP or either mobile number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (women.isEmpty()) Text("No archived women in this organisation.")
        else if (matches.isEmpty()) Text("No archived women match your search.")
        WrappingActions {
            TextButton(enabled = page > 0, onClick = { page-- }) { Text("Previous page") }
            Text("Page ${page + 1} of ${(matches.size / 50)+1}")
            TextButton(enabled = (page + 1) * 50 < matches.size, onClick = { page++ }) { Text("Next page") }
        }
        matches.drop(page * 50).take(50).forEach { woman ->
            key(woman.firestoreDocumentId) {
                WomanSummaryCard(
                    woman = woman, statusLabel = "Archived", showWard = true,
                    accessLevel = accessLevel, onSetArchived = onSetArchived,
                    onSelect = onSelect, onPartograph = onPartograph, onLoadHistory = onLoadHistory
                )
            }
        }
    }
}

@Composable
internal fun WomanArchiveActions(
    woman: Woman, accessLevel: Int,
    onSetArchived: suspend (Woman, Boolean) -> Unit,
    onLoadHistory: suspend (String, Int?) -> `in`.drarunbabu.drsharmila.jananisync.data.ArchiveHistoryPage
) {
    var showConfirmation by remember(woman.firestoreDocumentId, woman.isArchived) { mutableStateOf(false) }
    var showHistory by remember(woman.firestoreDocumentId) { mutableStateOf(false) }
    var saving by remember(woman.firestoreDocumentId) { mutableStateOf(false) }
    var error by remember(woman.firestoreDocumentId) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var events by remember(woman.firestoreDocumentId) { mutableStateOf<List<WomanArchiveEvent>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }
    var historyError by remember { mutableStateOf<String?>(null) }
    var nextRevision by remember { mutableStateOf<Int?>(null) }
    suspend fun loadHistory(before: Int?) {
        historyLoading = true
        historyError = null
        try {
            val page = onLoadHistory(woman.firestoreDocumentId, before)
            events = ((if (before == null) emptyList() else events) + page.events).distinctBy { it.id }.sortedByDescending { it.revision }
            nextRevision = page.nextBeforeRevision
            if (page.unreadableRecordIds.isNotEmpty()) historyError = "History incomplete: could not read ${page.unreadableRecordIds.joinToString()}."
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            historyError = when {
                failure.message.orEmpty().contains("failed-precondition") ->
                    "Archive history is unavailable because a Firestore prerequisite is not ready. Ask an administrator to check that the rosterArchiveEvents index is deployed and ready."
                failure.message.orEmpty().contains("permission-denied") ->
                    "You do not have permission to read archive history. Ask an administrator to check your organisation access and the deployed Firestore rules."
                else -> failure.message ?: "Could not load archive history."
            }
        } finally { historyLoading = false }
    }
    LaunchedEffect(showHistory, woman.firestoreDocumentId, woman.archiveRevision) { if (showHistory) loadHistory(null) }
    if (!canArchiveWomen(accessLevel) && events.isEmpty() && woman.archiveRevision == 0) return
    val action = if (woman.isArchived) "Unarchive" else "Archive"
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (canArchiveWomen(accessLevel)) ArchiveActionIcon(
            label = "$action woman", description = "$action ${woman.name}",
            icon = if (woman.isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
            enabled = !saving && woman.firestoreDocumentId.isNotBlank(),
            onClick = { error = null; showConfirmation = true }
        )
        if (events.isNotEmpty() || woman.archiveRevision > 0) {
            ArchiveActionIcon(label = "Archive history", description = "Archive history for ${woman.name}",
                icon = Icons.Outlined.History, onClick = { showHistory = true })
        }
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Could not change archive status") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("Close") } }
        )
    }
    if (showConfirmation) AlertDialog(
        onDismissRequest = { if (!saving) showConfirmation = false },
        title = { Text("$action woman?") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(woman.name)
            Text("${woman.admittedWard.wardName} · CR No. ${woman.cRNumber} · IP No. ${woman.iPNumber}")
            Text(if (woman.isArchived) "This returns her to the active list in her original ward."
                else "This moves her from the active ward list to Archived women. Her clinical records are kept.")
            AccessLevelText("Your name, access level and the time of this action will be recorded in the archive history.")
        } },
        confirmButton = { MgButton(text = if (saving) "Saving…" else "$action woman", enabled = !saving && canArchiveWomen(accessLevel), onClick = {
            if (!saving) scope.launch {
                saving = true
                try {
                    onSetArchived(woman, !woman.isArchived)
                    showConfirmation = false
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                    error = failure.message ?: "Could not change archive status. Refresh the roster and retry."
                    showConfirmation = false
                } finally { saving = false }
            }
        }) },
        dismissButton = { TextButton(enabled = !saving, onClick = { showConfirmation = false }) { Text("Cancel") } }
    )
    if (showHistory) AlertDialog(
        onDismissRequest = { showHistory = false }, title = { Text("Archive history · ${woman.name}") },
        confirmButton = { TextButton(onClick = { showHistory = false }) { Text("Close") } },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (historyLoading) item { Text("Loading history…") }
                historyError?.let { message -> item {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    TextButton(enabled = !historyLoading, onClick = { scope.launch { loadHistory(null) } }) {
                        Text("Retry")
                    }
                } }
                if (!historyLoading && events.isEmpty() && historyError == null) item { Text("No archive history.") }
                if (nextRevision != null) item {
                    TextButton(enabled = !historyLoading, onClick = { scope.launch { loadHistory(nextRevision) } }) { Text("Load older actions") }
                }
                items(events, key = { it.id }) { event ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (event.archived) "Archived" else "Unarchived", style = MaterialTheme.typography.titleSmall)
                        AccessLevelText("${event.authorName.ifBlank { event.authorId }} · access level ${event.authorAccessLevel}")
                        Text("UID: ${event.authorId}", style = MaterialTheme.typography.bodySmall)
                        Text("Revision ${event.revision} · " + if (event.createdAtMillis > 0) "Saved ${formatUiTimestamp(event.createdAtMillis)}"
                            else "Awaiting server confirmation", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}

@Composable
private fun ArchiveActionIcon(
    label: String, description: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState()
    ) {
        IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
            Icon(icon, contentDescription = description, tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        }
    }
}

/** Same compact summary and actions in both active and archived admission lists. */
@Composable
internal fun WomanSummaryCard(
    woman: Woman,
    statusLabel: String,
    accessLevel: Int,
    onSetArchived: suspend (Woman, Boolean) -> Unit,
    onSelect: (Woman) -> Unit,
    onPartograph: (Woman) -> Unit,
    showWard: Boolean = false,
    onLoadHistory: suspend (String, Int?) -> `in`.drarunbabu.drsharmila.jananisync.data.ArchiveHistoryPage
) {
    val details: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(woman.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("CR No. ${woman.cRNumber} · IP No. ${woman.iPNumber}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showWard) Text(woman.admittedWard.wardName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp)) {
                Text(statusLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
    val guideButton: @Composable () -> Unit = {
        MgButton(text = if (woman.admissionId.isBlank()) "Admission ID required" else "Open LCG",
            onClick = { onSelect(woman) }, enabled = woman.admissionId.isNotBlank())
    }
    val partographButton: @Composable () -> Unit = {
        MgButton(text = "Partograph", onClick = { onPartograph(woman) }, enabled = woman.admissionId.isNotBlank())
    }
    val archiveActions: @Composable () -> Unit = { WomanArchiveActions(woman, accessLevel, onSetArchived, onLoadHistory) }
    Card(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (maxWidth >= 600.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) { details() }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        guideButton()
                        partographButton()
                        archiveActions()
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    details()
                    WrappingActions {
                        guideButton()
                        partographButton()
                        archiveActions()
                    }
                }
            }
        }
    }
}
