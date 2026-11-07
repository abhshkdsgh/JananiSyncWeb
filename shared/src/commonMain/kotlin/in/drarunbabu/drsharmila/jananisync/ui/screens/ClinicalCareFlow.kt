package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.ui.components.AdaptiveFields
import `in`.drarunbabu.drsharmila.jananisync.ui.components.WrappingActions
import `in`.drarunbabu.drsharmila.jananisync.ui.navigation.SignedInRoute
import `in`.drarunbabu.drsharmila.jananisync.ui.navigation.resolveSelection
import `in`.drarunbabu.drsharmila.jananisync.ui.navigation.needsInitialWardSetup
import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import `in`.drarunbabu.drsharmila.jananisync.data.model.Ward
import `in`.drarunbabu.drsharmila.jananisync.data.model.Woman
import `in`.drarunbabu.drsharmila.jananisync.data.createRosterRepository
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.LabourCareUiState
import `in`.drarunbabu.drsharmila.jananisync.data.AdmissionBindingPreparation
import `in`.drarunbabu.drsharmila.jananisync.data.pendingAdmissionBindings
import `in`.drarunbabu.drsharmila.jananisync.ui.components.MgButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import `in`.drarunbabu.drsharmila.jananisync.ui.components.DateTimeField
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.currentTimeMillis

/** Organization-scoped roster navigation: ward -> woman -> encrypted LCG observations. */
@Composable
fun ClinicalCareFlow(
    member: UserMembershipState.ActiveMember,
    adminConsole: Boolean = false,
    archivedWomen: Boolean = false,
    onArchivedWomen: () -> Unit = {},
    onEmptyOrganization: () -> Unit = {},
    isCurrentDestination: Boolean,
    wardId: String?,
    admissionId: String?,
    onWardSelected: (Ward) -> Unit,
    onWomanSelected: (Woman) -> Unit,
    onPartographSelected: (Woman) -> Unit,
    partograph: Boolean,
    onSwitchChart: () -> Unit,
    state: LabourCareUiState,
    onPatientSelected: (Woman) -> Unit,
    onBindAdmissionColumns: suspend (List<Woman>, Set<String>?) -> Unit,
    onPatientCleared: () -> Unit,
    onUnlockOrganizationKey: (String, Boolean) -> Unit,
    isUnlockingOrganizationKey: Boolean,
    onCellSave: (String, String, String) -> Unit,
    onConfirmColumn: (String) -> Unit,
    onConfirmFullDilation: (String) -> Unit,
    onConfirmBirth: (String) -> Unit,
    onPartographSave: suspend (String, String, Map<String, String>) -> Boolean,
    signedInUserId: String,
    onCorrection: suspend (String, Int, Map<String, String>, String) -> Boolean,
    onSessionChange: (String, String) -> Unit,
    onCellChange: (String, String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val rosterRepository = remember(member.orgId, signedInUserId) { createRosterRepository() }
    val roster by rosterRepository.state.collectAsState()
    LaunchedEffect(member.orgId) { rosterRepository.observe(member.orgId) }
    val needsWardSetup = !archivedWomen && roster.needsInitialWardSetup(member.role, wardId, adminConsole)
    LaunchedEffect(needsWardSetup, isCurrentDestination) {
        if (needsWardSetup && isCurrentDestination) onEmptyOrganization()
    }
    DisposableEffect(rosterRepository) { onDispose { rosterRepository.close() } }
    val selection = SignedInRoute(wardId, admissionId).resolveSelection(roster)
    val selectedWard = selection.ward
    val selectedWoman = selection.woman
    LaunchedEffect(isCurrentDestination, member.orgId, wardId, admissionId, selectedWoman?.admissionId, selectedWoman?.name, selectedWoman?.isArchived) {
        if (!isCurrentDestination) return@LaunchedEffect
        if (selectedWoman != null) onPatientSelected(selectedWoman)
        else onPatientCleared()
    }

    val preparation = remember(member.orgId, signedInUserId) { AdmissionBindingPreparation() }
    val bindingFailures by preparation.failures.collectAsState()
    val preparationRunning by preparation.running.collectAsState()
    val pendingBindings = pendingAdmissionBindings(roster.women, state.feed)
    val preparationEnabled = isCurrentDestination && member.accessLevel >= 60 &&
        state.orgId == member.orgId && !roster.isLoading && roster.message == null &&
        state.organizationKeyAvailable && !state.feed.isLoading
    val latestBindings by rememberUpdatedState(if (preparationEnabled) pendingBindings else emptyList())
    val latestWomen by rememberUpdatedState(roster.women)
    val latestBind by rememberUpdatedState(onBindAdmissionColumns)
    val preparationScope = rememberCoroutineScope()
    LaunchedEffect(preparation) {
        // Sequential collection lets an in-flight write finish when its own snapshot arrives.
        snapshotFlow { latestBindings }.collect { bindings ->
            preparation.prepare(bindings) { binding ->
                val woman = latestWomen.firstOrNull { it.firestoreDocumentId == binding.womanDocumentId }
                    ?: error("The admission is no longer in this roster.")
                latestBind(listOf(woman), setOf(binding.columnId))
            }
        }
    }
    val setWomanArchived: suspend (Woman, Boolean) -> Unit = { woman, archived ->
        check(!state.feed.isLoading) { "Wait for clinical records to finish loading before changing archive status." }
        onBindAdmissionColumns(listOf(woman), null)
        rosterRepository.setWomanArchived(woman, archived)
    }

    if (roster.unreadableRecordIds.isNotEmpty()) {
        Text("Roster incomplete: ${roster.unreadableRecordIds.size} records could not be read. Ask an administrator to review: ${roster.unreadableRecordIds.joinToString()}",
            color = MaterialTheme.colorScheme.error)
    }
    val admittedSessions = roster.women.associate { it.firestoreDocumentId to it.admissionId }
    val mismatchedColumns = state.feed.columnControls.filter { control ->
        control.womanDocumentId.isNotBlank() && control.sessionId.isNotBlank() && admittedSessions[control.womanDocumentId]?.let { it != control.sessionId } == true
    }.map { it.columnId }.toSet()
    val verifiedState = state.copy(feed = state.feed.copy(
        observations = state.feed.observations.filter { it.columnId !in mismatchedColumns },
        cellEvents = state.feed.cellEvents.filter { it.columnId !in mismatchedColumns }))
    if (mismatchedColumns.isNotEmpty()) Text("Some clinical columns have an invalid admission binding and were withheld. Contact an administrator.", color = MaterialTheme.colorScheme.error)
    when {
        needsWardSetup -> Text("Opening ward setup…")
        roster.isLoading -> Text("Loading organisation roster…")
        roster.message != null -> Text(roster.message!!, color = MaterialTheme.colorScheme.error)
        wardId != null && selectedWard == null -> Text("This page is unavailable. Use Back to return.")
        admissionId != null && selectedWoman == null -> Text("This page is unavailable. Use Back to return.")
        archivedWomen -> ArchivedWomenScreen(
            women = roster.women.filter { it.isArchived },
            accessLevel = member.accessLevel,
            onLoadHistory = rosterRepository::loadArchiveHistory,
            onSetArchived = setWomanArchived,
            onSelect = onWomanSelected,
            onPartograph = onPartographSelected
        )
        wardId == null -> WardListScreen(
            wards = roster.wards,
            women = roster.women.filterNot { it.isArchived },
            archivedCount = roster.women.count { it.isArchived },
            onArchivedWomen = onArchivedWomen,
            onSelect = onWardSelected,
            isAdmin = member.role == "admin" && adminConsole,
            onAddWard = { ward -> rosterRepository.addWard(ward) },
            onUpdateWard = { ward -> rosterRepository.updateWard(ward) }
        )

        selectedWoman == null -> WomanListScreen(
            ward = selectedWard!!,
            women = roster.women.filterNot { it.isArchived },
            accessLevel = member.accessLevel,
            onLoadHistory = rosterRepository::loadArchiveHistory,
            onSetArchived = setWomanArchived,
            observations = verifiedState.feed.observations,
            clinicalStatusAvailable = state.orgId == member.orgId && state.organizationKeyAvailable &&
                !state.feed.isLoading && state.feed.message == null,
            canAdmit = member.role in setOf("admin", "doctor", "nurse"),
            onAdmit = { woman -> rosterRepository.addWoman(woman) },
            onSelect = onWomanSelected,
            onPartograph = onPartographSelected
        )

        state.orgId != member.orgId || state.patientSessionId != selectedWoman.admissionId ->
            Text("Opening care guide…")

        else -> {
            val ward = selectedWard!!
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val womanBindings = pendingBindings.filter { it.womanDocumentId == selectedWoman.firestoreDocumentId }
                val missingBindings = womanBindings.isNotEmpty()
                val failedBindings = womanBindings.filter { it in bindingFailures }
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${ward.wardName} · ${selectedWoman.name}", style = MaterialTheme.typography.titleSmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(when {
                            selectedWoman.isArchived -> "Archived admission · Read only"
                            failedBindings.isNotEmpty() -> "Read only · Record preparation failed"
                            missingBindings && member.accessLevel >= 60 -> "Read only · Preparing older records…"
                            missingBindings -> "Read only · Senior staff setup needed"
                            else -> if (partograph) "Partograph" else "Labour Care Guide"
                        }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    MgButton(
                        text = if (partograph) "View LCG" else "View Partograph",
                        enabled = !state.isSaving,
                        onClick = {
                            focusManager.clearFocus()
                            onSwitchChart()
                        }
                    )
                    if (!selectedWoman.isArchived && failedBindings.isNotEmpty() && member.accessLevel >= 60) {
                        MgButton(text = "Retry", enabled = !preparationRunning, onClick = {
                            preparationScope.launch {
                                preparation.prepare(failedBindings, retry = true) { binding ->
                                    latestBind(listOf(selectedWoman), setOf(binding.columnId))
                                }
                            }
                        })
                    }
                }
                WomanArchiveActions(selectedWoman, member.accessLevel, setWomanArchived, rosterRepository::loadArchiveHistory)
                key(member.orgId, signedInUserId, selectedWoman.admissionId) {
                ObservationsHostScreen(
                    state = verifiedState,
                    onUnlockOrganizationKey = onUnlockOrganizationKey,
                    isUnlockingOrganizationKey = isUnlockingOrganizationKey,
                    onCellSave = onCellSave,
                    onConfirmColumn = onConfirmColumn,
                    onConfirmFullDilation = onConfirmFullDilation,
                    onConfirmBirth = onConfirmBirth,
                    onPartographSave = onPartographSave,
                    signedInUserId = signedInUserId,
                    onCorrection = onCorrection,
                    accessLevel = member.accessLevel,
                    onSessionChange = onSessionChange,
                    onCellChange = onCellChange,
                    patientLabelReadOnly = true,
                    readOnly = selectedWoman.isArchived || missingBindings,
                    partograph = partograph,
                    hospitalNumber = selectedWoman.iPNumber.toString(),
                    admissionTime = `in`.drarunbabu.drsharmila.jananisync.ui.formatUiTimestamp(selectedWoman.admissionDate),
                )
                }
            }
        }
    }
}

@Composable
private fun WardListScreen(
    wards: List<Ward>, women: List<Woman>, onSelect: (Ward) -> Unit,
    archivedCount: Int, onArchivedWomen: () -> Unit,
    isAdmin: Boolean, onAddWard: suspend (Ward) -> String, onUpdateWard: suspend (Ward) -> Unit
) {
    var editing by remember { mutableStateOf<Ward?>(null) }
    var wardName by remember { mutableStateOf("") }
    var wardLocation by remember { mutableStateOf("") }
    var wardHead by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Select a ward", style = MaterialTheme.typography.headlineSmall)
        if (isAdmin) {
            Text("Ward administration · changes are encrypted and scoped to this organisation.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(wardName, { wardName = it.take(120) }, label = { Text("Ward name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(wardLocation, { wardLocation = it.take(160) }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(wardHead, { wardHead = it.take(160) }, label = { Text("Ward head") }, modifier = Modifier.fillMaxWidth())
            WrappingActions {
                Button(enabled = !saving && wardName.isNotBlank(), onClick = {
                    val target = editing
                    val draft = Ward(target?.wardId.orEmpty(), wardName.trim(), wardLocation.trim(), wardHead.trim())
                    scope.launch {
                        saving = true
                        runCatching { if (target == null) onAddWard(draft) else onUpdateWard(draft) }
                            .onSuccess { message = if (target == null) "Ward added." else "Ward updated."; editing = null; wardName = ""; wardLocation = ""; wardHead = "" }
                            .onFailure { message = it.message ?: "Ward could not be saved." }
                        saving = false
                    }
                }) { Text(if (saving) "Saving…" else if (editing == null) "Add ward" else "Save ward") }
                if (editing != null) TextButton(onClick = { editing = null; wardName = ""; wardLocation = ""; wardHead = "" }) { Text("Cancel") }
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (wards.isEmpty()) Text("No wards are configured for this organisation.")
        wards.forEach { ward ->
            Card(modifier = Modifier.fillMaxWidth()) {
                AdaptiveFields(Modifier.padding(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(ward.wardName, style = MaterialTheme.typography.titleMedium)
                        Text(ward.wardLocation)
                        Text("Ward head: ${ward.wardHead}")
                        Text("${women.count { it.admittedWard.wardId == ward.wardId }} women")
                    }
                    if (isAdmin) {
                        TextButton(onClick = { editing = ward; wardName = ward.wardName; wardLocation = ward.wardLocation; wardHead = ward.wardHead }) { Text("Configure") }
                    } else Button(onClick = { onSelect(ward) }) { Text("Open ward") }
                }
            }
        }
        MgButton(
            text = "Archived women ($archivedCount)", onClick = onArchivedWomen
        )
    }
}

@Composable
private fun WomanListScreen(
    ward: Ward, women: List<Woman>,
    accessLevel: Int,
    onSetArchived: suspend (Woman, Boolean) -> Unit,
    observations: List<`in`.drarunbabu.drsharmila.jananisync.data.model.LabourCareObservation>,
    onLoadHistory: suspend (String, Int?) -> `in`.drarunbabu.drsharmila.jananisync.data.ArchiveHistoryPage,
    clinicalStatusAvailable: Boolean,
    canAdmit: Boolean, onAdmit: suspend (Woman) -> Woman, onSelect: (Woman) -> Unit, onPartograph: (Woman) -> Unit
) {
    var searchQuery by rememberSaveable(ward.wardId) { mutableStateOf("") }
    // Each opening chooses a fresh default once clinical status has loaded.
    var selectedFilter by remember(ward.wardId) { mutableStateOf<WomanListFilter?>(null) }
    val wardWomen = women.filter { it.admittedWard.wardId == ward.wardId }
    val observationsByAdmission = remember(observations) { observations.groupBy { it.sessionId } }
    val statuses = wardWomen.associate {
        it.admissionId to admissionLabourStatus(it.admissionId, observationsByAdmission[it.admissionId].orEmpty(), clinicalStatusAvailable)
    }
    val defaultFilter = defaultWomanListFilter(statuses.values)
    val activeFilter = if (clinicalStatusAvailable) selectedFilter ?: defaultFilter else WomanListFilter.All
    LaunchedEffect(ward.wardId, clinicalStatusAvailable, defaultFilter) {
        if (clinicalStatusAvailable && selectedFilter == null) selectedFilter = defaultFilter
    }
    val searchedWomen = wardWomen.filter { it.matchesWomanSearch(searchQuery) }
    val visibleWomen = searchedWomen.filter { activeFilter.matches(statuses.getValue(it.admissionId)) }
    var page by remember(ward.wardId, searchQuery, activeFilter, visibleWomen.size) { mutableStateOf(0) }
    var showAdmission by remember(ward.wardId) { mutableStateOf(false) }
    var crNumber by remember(ward.wardId) { mutableStateOf("") }
    var ipNumber by remember(ward.wardId) { mutableStateOf("") }
    var name by remember(ward.wardId) { mutableStateOf("") }
    var dateOfBirth by remember(ward.wardId) { mutableStateOf("") }
    var informant by remember(ward.wardId) { mutableStateOf("") }
    var contact by remember(ward.wardId) { mutableStateOf("") }
    var alternateContact by remember(ward.wardId) { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var admissionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(ward.wardName, style = MaterialTheme.typography.headlineSmall)
        Text("Select a woman to open her Labour Care Guide or Partograph.")
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it.take(160) },
            label = { Text("Search women") },
            placeholder = { Text("Name, CR, IP or either mobile number") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = if (searchQuery.isNotEmpty()) ({ TextButton(onClick = { searchQuery = "" }) { Text("Clear") } }) else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        WrappingActions {
            WomanListFilter.entries.forEach { filter ->
                val count = searchedWomen.count { filter.matches(statuses.getValue(it.admissionId)) }
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { selectedFilter = filter },
                    enabled = filter == WomanListFilter.All || clinicalStatusAvailable,
                    label = { Text(if (clinicalStatusAvailable || filter == WomanListFilter.All) "${filter.label} ($count)" else filter.label) }
                )
            }
        }
        Text(if (clinicalStatusAvailable) "Status reflects recorded labour onset, diagnosis and progress. In labour includes second stage."
            else "Labour status is unavailable until clinical records are unlocked and loaded.", style = MaterialTheme.typography.bodySmall)
        Text("${visibleWomen.size} of ${wardWomen.size} women", style = MaterialTheme.typography.labelMedium)
        if (canAdmit && !showAdmission) {
            Button(onClick = { showAdmission = true; admissionError = null }) { Text("Admit a woman") }
        }
        if (canAdmit && showAdmission) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("New admission · ${ward.wardName}", style = MaterialTheme.typography.titleMedium)
                    Text("Details are encrypted before they are stored.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(name, { name = it.take(160) }, label = { Text("Woman's name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    AdaptiveFields {
                        OutlinedTextField(crNumber, { crNumber = it.filter(Char::isDigit).take(18) }, label = { Text("CR number") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(ipNumber, { ipNumber = it.filter(Char::isDigit).take(18) }, label = { Text("IP number") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    DateTimeField(dateOfBirth, { dateOfBirth = it }, label = "Date of birth", modifier = Modifier.fillMaxWidth(), dateOnly = true, initialYearsAgo = 20)
                    OutlinedTextField(informant, { informant = it.take(160) }, label = { Text("Informant name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(contact, { contact = it.take(40) }, label = { Text("Contact number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(alternateContact, { alternateContact = it.take(40) }, label = { Text("Alternate contact number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    admissionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    WrappingActions {
                        Button(enabled = !saving && name.isNotBlank() && crNumber.toLongOrNull() != null && ipNumber.toLongOrNull() != null && dateOfBirth.length == 8,
                            onClick = {
                                val now = currentTimeMillis()
                                val draft = Woman(
                                    cRNumber = crNumber.toLong(), iPNumber = ipNumber.toLong(), name = name.trim(),
                                    dateOfBirth = dateOfBirth.toInt(), admissionDate = now, admittedWard = ward,
                                    lastUpdate = now, informantName = informant.trim(), contactNumber = contact.trim(),
                                    alternateContactNumber = alternateContact.trim()
                                )
                                scope.launch {
                                    saving = true
                                    runCatching { onAdmit(draft) }
                                        .onSuccess { showAdmission = false; admissionError = null }
                                        .onFailure { admissionError = it.message ?: "Could not save this admission. Check that the organisation key is unlocked." }
                                    saving = false
                                }
                            }) { Text(if (saving) "Saving…" else "Save admission") }
                        TextButton(enabled = !saving, onClick = { showAdmission = false; admissionError = null }) { Text("Cancel") }
                    }
                }
            }
        }
        if (wardWomen.isEmpty()) Text("No women are assigned to this ward.")
        else if (visibleWomen.isEmpty()) Text("No women match your search and filter.")
        WrappingActions{
            TextButton(enabled = page > 0, onClick = { page-- }) { Text("Previous page") }
            Text("Page ${page + 1} of ${(visibleWomen.size / 50)+1}")
            TextButton(enabled = (page + 1) * 50 < visibleWomen.size, onClick = { page++ }) { Text("Next page") }
        }
        visibleWomen.drop(page * 50).take(50).forEach { woman ->
            key(woman.firestoreDocumentId.ifBlank { woman.admissionId }) {
                WomanSummaryCard(
                    woman = woman,
                    statusLabel = statuses.getValue(woman.admissionId).label,
                    accessLevel = accessLevel,
                    onLoadHistory = onLoadHistory,
                    onSetArchived = onSetArchived,
                    onSelect = onSelect,
                    onPartograph = onPartograph
                )
            }
        }
    }
}
