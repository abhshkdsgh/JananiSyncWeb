package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import `in`.drarunbabu.drsharmila.jananisync.ui.components.AccessLevelInfo
import `in`.drarunbabu.drsharmila.jananisync.ui.components.AccessLevelText
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMember
import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import `in`.drarunbabu.drsharmila.jananisync.ui.components.WrappingActions
import `in`.drarunbabu.drsharmila.jananisync.viewmodel.OrganizationAccessUiState
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMaintenance
import androidx.compose.ui.platform.testTag

@Composable
fun OrganizationAccessScreen(
    state: OrganizationAccessUiState,
    onAccessCodeChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onRoleSelected: (String) -> Unit,
    onPrepareSecureAccess: () -> Unit,
    onRecoveryPhraseConfirmed: (Boolean) -> Unit,
    onAdminRecoveryPhraseChange: (String) -> Unit,
    onInitializeOrganizationKey: () -> Unit,
    onUnlockOrganizationKey: () -> Unit,
    onGrantOrganizationKey: (OrganizationMember) -> Unit,
    onRefreshKeyGrants: () -> Unit,
    onSubmitRequest: () -> Unit,
    onApprove: (`in`.drarunbabu.drsharmila.jananisync.data.model.AccessRequest, Int) -> Unit,
    onUpdateMemberAccessLevel: (OrganizationMember, Int) -> Unit,
    onSetMemberActive: (OrganizationMember, Boolean) -> Unit,
    onSelectOrganization: (String?) -> Unit,
    onRetry: () -> Unit,
    organizationKeyAvailable: Boolean,
    showAdminConsole: Boolean = false,
    onUnlockMemberOrganizationKey: (String, Boolean) -> Unit,
    onUpdateMemberRole: (OrganizationMember, String) -> Unit = { _, _ -> },
    onResumeMaintenance: () -> Unit = {},
    onRotateOrganizationKey: () -> Unit = {},
    onCancelPendingRequest: (String) -> Unit = {},
    onLeaveOrganization: (String) -> Unit = {},
    onResolveKeyReplacement: (`in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementRequest, Boolean) -> Unit = { _, _ -> },
    onRetryKeyReplacements: () -> Unit = {},
    onPrepareSecureAccessFromOrganization: (String, String) -> Unit = { _, _ -> },
    clinicalContent: @Composable (UserMembershipState.ActiveMember) -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        if (state.membership !is UserMembershipState.PendingApproval) state.ownPendingRequests.forEach { request ->
            PendingJoiningRequest(request.orgId, request.requestedRole,
                busy = state.cancellingRequestForOrgId != null, onCancel = { onCancelPendingRequest(request.orgId) })
        }
        when (val membership = state.membership) {
            UserMembershipState.Loading -> {
                CircularProgressIndicator()
                Text("Checking your organisation access…")
            }
            UserMembershipState.Unassigned -> {
                JoinOrganizationContent(
                    state = state,
                    onAccessCodeChange = onAccessCodeChange,
                    onNameChange = onNameChange,
                    onRoleSelected = onRoleSelected,
                    onPrepareSecureAccess = onPrepareSecureAccess,
                    onPrepareSecureAccessFromOrganization = onPrepareSecureAccessFromOrganization,
                    onRecoveryPhraseConfirmed = onRecoveryPhraseConfirmed,
                    onSubmitRequest = onSubmitRequest,
                    showFirstAdminHelp = true
                )
            }
            is UserMembershipState.PendingApproval -> {
                PendingJoiningRequest(membership.orgId, membership.requestedRole,
                    busy = state.cancellingRequestForOrgId != null, onCancel = { onCancelPendingRequest(membership.orgId) })
            }
            is UserMembershipState.Rejected -> {
                Text("Access request declined", style = MaterialTheme.typography.headlineSmall)
                Text("Contact an organisation administrator if you have questions about your request.")
            }
            is UserMembershipState.Deactivated -> {
                Text("Organisation access deactivated", style = MaterialTheme.typography.headlineSmall)
                Text("Your membership is inactive. Contact an organisation administrator if you believe this was a mistake.")
            }
            is UserMembershipState.ChooseOrganization -> {
                Text("Choose an organisation", style = MaterialTheme.typography.headlineSmall)
                Text("Your account has active access to these organisations. Choose one to continue.")
                membership.organizations.forEach { organization ->
                    OrganizationChoice(organization) { onSelectOrganization(organization.orgId) }
                    LeaveOrganizationAction(organization.orgId, organization.organizationName, organization.role,
                        busy = state.leavingOrganizationId != null, enabled = true,
                        onLeave = { onLeaveOrganization(organization.orgId) })
                }
            }
            is UserMembershipState.ActiveMember -> {
                if (state.isRequestingAdditionalOrganization) {
                    JoinOrganizationContent(
                        state = state,
                        onAccessCodeChange = onAccessCodeChange,
                        onNameChange = onNameChange,
                        onRoleSelected = onRoleSelected,
                        onPrepareSecureAccess = onPrepareSecureAccess,
                        onPrepareSecureAccessFromOrganization = onPrepareSecureAccessFromOrganization,
                        onRecoveryPhraseConfirmed = onRecoveryPhraseConfirmed,
                        onSubmitRequest = onSubmitRequest,
                        isAdditionalOrganization = true
                    )
                } else {
                    val maintenanceActive = state.maintenance.orgId == membership.orgId && state.maintenance.active
                    if (
                        maintenanceActive || (membership.role == "admin"
                                && showAdminConsole
                                && !state.isRotatingOrganizationKey
                                && state.maintenance.orgId == membership.orgId
                                && state.maintenance.total > 0
                                )
                    ) OrganizationMaintenanceNotice(
                            isAdmin = membership.role == "admin", completed = state.maintenance.completed,
                            total = state.maintenance.total, message = state.maintenance.message,
                            canResume = state.organizationKeyUnlocked && state.maintenance.adminRecoveryUnlocked && !state.isOrganizationKeyBusy,
                            onResume = onResumeMaintenance, active = maintenanceActive,
                            awaitingAdmin = state.maintenance.awaitingAdmin,
                            recoveryPhrase = state.adminRecoveryPhraseInput,
                            onRecoveryPhraseChange = onAdminRecoveryPhraseChange,
                            onUnlock = { onUnlockOrganizationKey() },
                            isUnlocking = state.isOrganizationKeyBusy
                    )
                    if (maintenanceActive && membership.role != "admin") {
                        // Clinical content is unavailable until all grants activate together.
                    } else if (membership.role == "admin" && !state.organizationKeyMetadataLoaded) {
                        CircularProgressIndicator()
                        Text("Checking organisation setup…")
                    } else if (membership.role == "admin" && (maintenanceActive || showAdminConsole || !state.organizationKeyExists)) {
                        AdminApprovalContent(
                            state = state,
                            onApprove = onApprove,
                            onUpdateMemberAccessLevel = onUpdateMemberAccessLevel,
                            onUpdateMemberRole = onUpdateMemberRole,
                            onSetMemberActive = onSetMemberActive,
                            onPrepareSecureAccess = onPrepareSecureAccess,
                            onRecoveryPhraseConfirmed = onRecoveryPhraseConfirmed,
                            onAdminRecoveryPhraseChange = onAdminRecoveryPhraseChange,
                            onInitializeOrganizationKey = onInitializeOrganizationKey,
                            onUnlockOrganizationKey = onUnlockOrganizationKey,
                            onGrantOrganizationKey = onGrantOrganizationKey,
                            onRefreshKeyGrants = onRefreshKeyGrants,
                            onRotateOrganizationKey = onRotateOrganizationKey,
                            onResolveKeyReplacement = onResolveKeyReplacement,
                            onRetryKeyReplacements = onRetryKeyReplacements,
                            wardContent = { clinicalContent(membership) }
                        )
                    } else {
                        Text("Clinical dashboard", style = MaterialTheme.typography.headlineSmall)
                        if (organizationKeyAvailable) {
                            Text("Organisation encryption unlocked on this device.")
                        } else {
                            MemberOrganizationUnlock(
                                organizationId = membership.orgId,
                                isUnlocking = state.isOrganizationKeyBusy,
                                onUnlock = onUnlockMemberOrganizationKey
                            )
                        }
                        clinicalContent(membership)
                    }
                }
            }
            is UserMembershipState.Error -> {
                Text("Could not load organisation access", style = MaterialTheme.typography.headlineSmall)
                Text(membership.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Try again") }
            }
        }

    }
}

@Composable
internal fun PendingJoiningRequest(orgId: String, role: String, busy: Boolean, onCancel: () -> Unit) {
    var confirm by remember(orgId) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Access pending", style = MaterialTheme.typography.titleLarge)
            Text("Your $role request for $orgId is waiting for an administrator.")
            TextButton(onClick = { confirm = true }, enabled = !busy) { Text(if (busy) "Cancelling request…" else "Cancel joining request") }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Cancel joining request?") },
        text = { Text("Withdraw your pending request for $orgId? You can apply again using the same recovery key.") },
        confirmButton = { Button(enabled = !busy, onClick = { confirm = false; onCancel() }) { Text("Confirm cancellation") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep request") } })
}

@Composable
internal fun LeaveOrganizationAction(orgId: String, name: String, role: String, busy: Boolean,
    enabled: Boolean, onLeave: () -> Unit) {
    var confirm by remember(orgId) { mutableStateOf(false) }
    val canLeave = role != "admin" && enabled && !busy
    TextButton(onClick = { confirm = true }, enabled = canLeave) { Text(if (busy) "Leaving organisation…" else "Leave organisation") }
    if (role == "admin") Text("The administrator must remain responsible for the organisation's keys and cannot leave.", style = MaterialTheme.typography.bodySmall)
    else if (!enabled) Text("Wait for Access Update to finish before leaving.", style = MaterialTheme.typography.bodySmall)
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Leave organisation?") },
        text = { Text("Leave $name? Your organisation access will end immediately. Your clinical contributions and membership history will be retained. The administrator must come online and unlock their account to complete an Access Update for the remaining members.") },
        confirmButton = { Button(enabled = canLeave, onClick = { confirm = false; onLeave() }) { Text("Confirm leave") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Stay in organisation") } })
}

@Composable
internal fun OrganizationMaintenanceNotice(isAdmin: Boolean, completed: Int, total: Int,
    message: String?, canResume: Boolean, onResume: () -> Unit, active: Boolean = true,
    awaitingAdmin: Boolean = false, recoveryPhrase: String = "",
    onRecoveryPhraseChange: (String) -> Unit = {}, onUnlock: () -> Unit = {}, isUnlocking: Boolean = false) {
    val status = OrganizationMaintenance(active = active, completed = completed, total = total)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (active) "Maintenance in progress" else "Access Update complete", style = MaterialTheme.typography.titleLarge)
            Text("Access Update: ${status.progressPercentage}%", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth().testTag("access-update-progress"))
            Text(if (!active) "Organisation access has been updated. All prepared key grants are now available."
                else if (isAdmin && !canResume) "Rotation progress is saved at the last confirmed key grant. Enter the admin's 12-word recovery phrase to restore the keys in this browser and resume."
                else if (isAdmin) "Your organisation is updating access. Progress is saved after each confirmed key grant. Keep this page open; if the browser is interrupted, unlock your account to resume."
                else "Your organisation is updating access. Please ask your administrator to come online and unlock their account to complete the update. Clinical records will be available when maintenance finishes.")
            if (active && total > 0 && completed >= total) Text("Verifying key grants and finishing the update…")
            if (isAdmin) {
                if (total > 0) Text("Key grants prepared: $completed of $total")
                if (active) {
                    message?.let { Text(it) }
                    val needsPhrase = (!canResume && (active || awaitingAdmin)) ||
                        message?.contains("recovery phrase", ignoreCase = true) == true
                    if (needsPhrase) {
                        Text("This Chrome profile needs the admin recovery phrase to resume the key update. Unlocking will start the update automatically.")
                        OutlinedTextField(
                            value = recoveryPhrase,
                            onValueChange = onRecoveryPhraseChange,
                            label = { Text("Admin's 12-word recovery phrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            enabled = !isUnlocking,
                            modifier = Modifier.fillMaxWidth().testTag("maintenance-recovery-phrase")
                        )
                        Button(onClick = onUnlock, enabled = recoveryPhrase.isNotBlank() && !isUnlocking,
                            modifier = Modifier.testTag("maintenance-unlock-and-resume")) {
                            Text(if (isUnlocking) "Unlocking…" else "Unlock and continue update")
                        }
                    }
                    Button(onClick = onResume, enabled = canResume) { Text("Resume access update") }
                }
            }
        }
    }
}

@Composable
private fun MemberOrganizationUnlock(
    organizationId: String,
    isUnlocking: Boolean,
    onUnlock: (String, Boolean) -> Unit
) {
    var phrase by remember(organizationId) { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Access patient records on this device", style = MaterialTheme.typography.titleMedium)
            Text("Enter your personal 12-word recovery phrase that you saved when joining to access patient records.")
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it.take(512) },
                label = { Text("Your 12-word recovery phrase") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = !isUnlocking,
                modifier = Modifier.fillMaxWidth()
            )
            Text("Access is remembered on this device. Use only on a secured personal or work device and log out when finished.", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { onUnlock(phrase, true) },
                enabled = phrase.isNotBlank() && !isUnlocking
            ) { Text(if (isUnlocking) "Opening patient records…" else "Access patient records") }
        }
    }
}

@Composable
private fun JoinOrganizationContent(
    state: OrganizationAccessUiState,
    onAccessCodeChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onRoleSelected: (String) -> Unit,
    onPrepareSecureAccess: () -> Unit,
    onPrepareSecureAccessFromOrganization: (String, String) -> Unit,
    onRecoveryPhraseConfirmed: (Boolean) -> Unit,
    onSubmitRequest: () -> Unit,
    isAdditionalOrganization: Boolean = false,
    showFirstAdminHelp: Boolean = false
) {
    val activeDuplicate = if (isAdditionalOrganization) {
        (state.membership as? UserMembershipState.ActiveMember)?.availableOrganizations
            ?.firstOrNull { it.orgId == state.accessCode }
    } else null
    val pendingDuplicate = if (isAdditionalOrganization) {
        state.ownPendingRequests.firstOrNull { it.orgId == state.accessCode }
    } else null
    val duplicateMessage = when {
        activeDuplicate != null -> "You already have access to ${activeDuplicate.organizationName}. Open your account menu and choose Switch organisation."
        pendingDuplicate != null -> "A request for this organisation is already awaiting administrator review. Check the pending request shown above instead of submitting another."
        else -> null
    }
    Text(
        if (isAdditionalOrganization) "Join another organisation" else "Join your organisation",
        style = MaterialTheme.typography.headlineSmall
    )
    Text("Enter the 8-character access code provided by the organisation administrator. Organisations are not listed or searchable.")
    duplicateMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    state.message?.takeIf { it != duplicateMessage }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    OutlinedTextField(
        value = state.name,
        onValueChange = onNameChange,
        label = { Text("Your name") },
        supportingText = { Text("Shown to admins and members of this organisation.") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = state.accessCode,
        onValueChange = onAccessCodeChange,
        label = { Text("Organisation access code") },
        supportingText = { Text("8 letters or numbers") },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    FlowRow{
        Text("Request a role", style = MaterialTheme.typography.titleMedium)
        FlowRow{
            WrappingActions {
                RoleChoice("Nurse", state.requestedRole == "nurse") { onRoleSelected("nurse") }
                RoleChoice("Doctor", state.requestedRole == "doctor") { onRoleSelected("doctor") }
            }
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Zero-knowledge recovery key", style = MaterialTheme.typography.titleMedium)
            val phrase = state.recoveryPhrase
            if (state.preparedKeyMaterial == null) {
                Text("Prepare a private encryption key before requesting access. Your recovery phrase is shown only here.")
                val sources = (state.membership as? UserMembershipState.ActiveMember)?.availableOrganizations.orEmpty()
                if (sources.isNotEmpty()) {
                    ExistingOrganizationKeyChoice(sources, state.accessCode, !state.isPreparingKeys, onPrepareSecureAccessFromOrganization)
                    Text("If you have lost a previously registered phrase, you can request a replacement after this membership is approved.")
                }
                Button(onClick = onPrepareSecureAccess, enabled = !state.isPreparingKeys && duplicateMessage == null) {
                    Text(if (state.isPreparingKeys) "Preparing key…" else "Prepare secure access")
                }
            } else {
                if (phrase != null) {
                    Text("Write down these 12 words and keep them somewhere private. They cannot be recovered if lost.")
                    RecoveryPhrase(phrase)
                } else {
                    val source = (state.membership as? UserMembershipState.ActiveMember)?.availableOrganizations
                        ?.firstOrNull { it.orgId == state.preparedKeySourceOrgId }
                    Text(when {
                        source != null -> "Using the verified key from ${source.organizationName} (${source.orgId}). Use that organisation's saved phrase for this membership too."
                        state.preparedKeySourceOrgId == null -> "This organisation already has a key registered to your account. If you still have its phrase, use it after approval. If you lost it, you can still rejoin; after approval open Settings, choose Replace lost recovery key, and ask an admin to approve the replacement. You cannot access existing records until that replacement is approved."
                        else -> "This organisation already has a key registered to your account."
                    })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = state.recoveryPhraseConfirmed,
                        onCheckedChange = onRecoveryPhraseConfirmed
                    )
                    Text(when {
                        phrase != null -> "I have safely recorded my recovery phrase"
                        state.preparedKeySourceOrgId == null -> "I understand I need the old phrase or an admin-approved replacement to access records"
                        else -> "I still have the selected organisation's recovery phrase"
                    })
                }
            }
        }
    }
    Button(
        onClick = onSubmitRequest,
        enabled = !state.isSubmitting && state.accessCode.length == 8 && state.name.isNotBlank()
            && state.preparedKeyMaterial != null && state.recoveryPhraseConfirmed && duplicateMessage == null
    ) {
        Text(if (state.isSubmitting) "Submitting…" else "Request access")
    }
    Text(
        if (!isAdditionalOrganization) {
            "An organisation admin must approve your request before you can continue."
        } else {
            "An organisation admin must approve this request. Your current organisation access remains available while it is reviewed."
        }
    )
    if (showFirstAdminHelp) {
        Text(
            "First administrator? The initial admin cannot approve themselves. Ask the Firebase project owner to provision your verified account as an active admin for this organisation, then reload this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OrganizationChoice(member: OrganizationMember, onChoose: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(member.organizationName, style = MaterialTheme.typography.titleMedium)
            Text("Role: ${member.role}")
            AccessLevelText("Access level: ${member.accessLevel}")
            Button(onClick = onChoose) { Text("Continue") }
        }
    }
}

@Composable
private fun RoleChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        TextButton(onClick = onClick) { Text(label) }
    }
}

private enum class AdminTab(val label: String) {
    Requests("Requests"), Members("Members"), Wards("Wards"), Security("Security")
}

@Composable
private fun AdminApprovalContent(
    state: OrganizationAccessUiState,
    onApprove: (`in`.drarunbabu.drsharmila.jananisync.data.model.AccessRequest, Int) -> Unit,
    onUpdateMemberRole: (OrganizationMember, String) -> Unit,
    onUpdateMemberAccessLevel: (OrganizationMember, Int) -> Unit,
    onSetMemberActive: (OrganizationMember, Boolean) -> Unit,
    onPrepareSecureAccess: () -> Unit,
    onRecoveryPhraseConfirmed: (Boolean) -> Unit,
    onAdminRecoveryPhraseChange: (String) -> Unit,
    onInitializeOrganizationKey: () -> Unit,
    onUnlockOrganizationKey: () -> Unit,
    onGrantOrganizationKey: (OrganizationMember) -> Unit,
    onRefreshKeyGrants: () -> Unit,
    onRotateOrganizationKey: () -> Unit,
    onResolveKeyReplacement: (`in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementRequest, Boolean) -> Unit,
    onRetryKeyReplacements: () -> Unit,
    wardContent: @Composable () -> Unit
) {
    Text("Admin dashboard", style = MaterialTheme.typography.headlineSmall)
    val organization = state.membership as UserMembershipState.ActiveMember
    Text("Organisation: ${organization.organizationName}")
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Organisation access code", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(organization.orgId, style = MaterialTheme.typography.headlineLarge)
                }
                IconButton(onClick = { clipboard.setText(AnnotatedString(organization.orgId)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy organisation access code")
                }
            }
            Text("Share this code with staff so they can request to join your organisation.", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (!state.organizationKeyUnlocked && !state.maintenance.active) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Verify access to the admin dashboard", style = MaterialTheme.typography.titleMedium)
                if (!state.organizationKeyExists) {
                    Text("Create the organisation-wide encryption key. The key itself stays in this browser and is stored only in an envelope encrypted to your public key.")
                    if (state.preparedKeyMaterial == null) {
                        Button(onClick = onPrepareSecureAccess, enabled = !state.isPreparingKeys) {
                            Text(if (state.isPreparingKeys) "Preparing admin key…" else "Prepare admin encryption key")
                        }
                    } else {
                        state.recoveryPhrase?.let { phrase ->
                            Text("Record this 12-word recovery phrase. It cannot be recovered if lost.")
                            RecoveryPhrase(phrase)
                        } ?: Text("Reusing this account's existing member key. Confirm that you still have its recovery phrase.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.recoveryPhraseConfirmed, onRecoveryPhraseConfirmed)
                            Text(if (state.recoveryPhrase == null) "I still have my recovery phrase" else "I safely recorded the phrase")
                        }
                        Button(
                            onClick = onInitializeOrganizationKey,
                            enabled = state.recoveryPhraseConfirmed && !state.isOrganizationKeyBusy
                        ) { Text(if (state.isOrganizationKeyBusy) "Initialising…" else "Initialise organisation key") }
                        if (state.isOrganizationKeyBusy) {
                            CircularProgressIndicator()
                            Text("Saving the encryption setup. Keep this page open; it may take a few seconds.")
                        }
                    }
                } else {
                    Text("Enter your personal 12-word recovery phrase to verify access before managing members or wards.")
                    OutlinedTextField(
                        value = state.adminRecoveryPhraseInput,
                        onValueChange = onAdminRecoveryPhraseChange,
                        label = { Text("Your personal 12-word recovery phrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        enabled = !state.isOrganizationKeyBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Stores encrypted access keys, never your phrase. Use only on a secured personal device. Sign out to forget this device.", style = MaterialTheme.typography.bodySmall)
                    Button(
                        onClick = { onUnlockOrganizationKey() },
                        enabled = (state.adminRecoveryPhraseInput.isNotBlank()) && !state.isOrganizationKeyBusy
                    ) { Text(if (state.isOrganizationKeyBusy) "Verifying…" else "Verify and open dashboard") }
                }
            }
        }
    }
    if (!state.organizationKeyUnlocked) {
        Text("Verify your access above to manage members, grant access, and create or update wards.")
        state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        return
    }
    var selectedTabName by rememberSaveable(organization.orgId) { mutableStateOf(AdminTab.Requests.name) }
    val selectedTab = AdminTab.valueOf(selectedTabName)
    val recoveryRequestCount = state.keyReplacement.takeIf { it.orgId == organization.orgId }
        ?.requests?.count { it.status == "pending" } ?: 0
    PrimaryScrollableTabRow(
        selectedTabIndex = selectedTab.ordinal,
        modifier = Modifier.fillMaxWidth(),
        edgePadding = 0.dp
    ) {
        AdminTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { selectedTabName = tab.name },
                text = { Text(if (tab == AdminTab.Requests) "Requests (${state.pendingRequests.size + recoveryRequestCount})" else tab.label) }
            )
        }
    }
    when (selectedTab) {
        AdminTab.Security -> {
            ManualKeyRotationControls(state, onRotateOrganizationKey)
        }
        AdminTab.Wards -> {
            Text("Ward administration", style = MaterialTheme.typography.titleLarge)
            if (state.maintenance.active) {
                Text("Ward administration is paused during maintenance.")
            } else {
                Text("Add wards or configure existing ward details for this organisation.")
                wardContent()
            }
        }
        AdminTab.Requests -> {
            Text("Access requests", style = MaterialTheme.typography.titleLarge)
            RecoveryKeyReplacement(state, organization, {}, {}, {}, {}, onResolveKeyReplacement, onRetryKeyReplacements)
            AccessLevelText("Assign each new member a whole-number access level from 10 to 99. App permissions follow the member's role.")
            if (state.maintenance.active) {
                Text("Member changes and new approvals are paused during maintenance.")
            } else if (state.pendingRequests.isEmpty()) {
                Text("No pending membership requests.")
            } else {
                state.pendingRequests.forEach { request ->
                    var accessLevelText by remember(request.userId) { mutableStateOf("10") }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(request.userName, style = MaterialTheme.typography.titleMedium)
                            Text("Email: ${request.userEmail.ifBlank { "Unavailable for older requests" }}")
                            Text("Requested role: ${request.requestedRole}")
                            Text("User ID: ${request.userId}", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(
                                value = accessLevelText,
                                onValueChange = { accessLevelText = it.filter { character -> character.isDigit() }.take(2) },
                                label = { Text("Access level (10–99)") },
                                trailingIcon = { AccessLevelInfo() },
                                supportingText = { Text("Seniority: higher numbers mean greater seniority.") },
                                singleLine = true
                            )
                            val accessLevel = accessLevelText.toIntOrNull()
                            Button(
                                onClick = { accessLevel?.let { onApprove(request, it) } },
                                enabled = state.approvingUserId == null && state.grantingKeyToUserId == null && accessLevel != null && accessLevel in 10..99
                            ) {
                                Text(if (state.approvingUserId == request.userId) "Approving and granting access…" else "Approve and grant access as ${request.requestedRole}")
                            }
                        }
                    }
                }
            }

        }
        AdminTab.Members -> {
            Text("Organisation members", style = MaterialTheme.typography.titleLarge)
            AccessLevelText("Manage member access levels, account status, and encryption access.")
            if (state.organizationMembers.isEmpty()) {
                Text("No organisation members found.")
            } else {
                state.organizationMembers.forEach { member ->
                    var showDeactivateConfirmation by remember(member.userId) { mutableStateOf(false) }
                    var accessLevelText by remember(member.userId, member.accessLevel) {
                        mutableStateOf(member.accessLevel.toString())
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(member.name, style = MaterialTheme.typography.titleMedium)
                            Text("${member.role} · ${member.status}")
                            Text("User ID: ${member.userId}", style = MaterialTheme.typography.bodySmall)
                            if (member.role in setOf("doctor", "nurse") && member.status in setOf("active", "inactive")) {
                                MemberRoleControls(member,
                                    enabled = !state.maintenance.active && state.updatingRoleForUserId == null && state.updatingMemberStatusForUserId == null,
                                    busy = state.updatingRoleForUserId == member.userId,
                                    onSave = { role -> onUpdateMemberRole(member, role) })
                            }
                            if (member.status == "active" && member.userId != organization.availableOrganizations
                                    .firstOrNull { it.orgId == organization.orgId }?.userId && state.organizationKeyExists) {
                                if (member.userId in state.membersWithCurrentKey && member.userId in state.membersWithVerifiedRotationKey) {
                                    Text("Access setup complete. The recipient key is registered for automatic key updates.")
                                } else if (member.userId !in state.membersWithCheckedKeyGrant) {
                                    if (state.isCheckingMemberKeyGrants) Text("Checking access status…")
                                    else {
                                        Text("Access status could not be verified.")
                                        Button(onClick = onRefreshKeyGrants) { Text("Check access status") }
                                    }
                                } else if (state.organizationKeyUnlocked) {
                                    Button(
                                        onClick = { onGrantOrganizationKey(member) },
                                        enabled = state.grantingKeyToUserId == null && state.approvingUserId == null
                                    ) {
                                        Text(if (state.grantingKeyToUserId == member.userId) "Setting up access…"
                                            else if (member.userId in state.membersWithCurrentKey) "Register key for automatic updates" else "Complete access setup")
                                    }
                                } else {
                                    Text("Unlock the organisation key to manage this member's key access.")
                                }
                            }
                            if (member.status == "active" && !state.maintenance.active) {
                                OutlinedTextField(
                                    value = accessLevelText,
                                    onValueChange = { accessLevelText = it.filter { character -> character.isDigit() }.take(2) },
                                    label = { Text("Access level (10–99)") },
                                    trailingIcon = { AccessLevelInfo() },
                                    supportingText = { Text("Seniority: higher numbers mean greater seniority.") },
                                    singleLine = true
                                )
                                val accessLevel = accessLevelText.toIntOrNull()
                                Button(
                                    onClick = { accessLevel?.let { onUpdateMemberAccessLevel(member, it) } },
                                    enabled = state.updatingAccessLevelForUserId == null &&
                                        accessLevel != null && accessLevel in 10..99 && accessLevel != member.accessLevel
                                ) {
                                    Text(if (state.updatingAccessLevelForUserId == member.userId) "Saving…" else "Save access level")
                                }
                            } else {
                                AccessLevelText("Access level: ${member.accessLevel}")
                            }
                            if (member.role != "admin" && member.status != "left" && !state.maintenance.active) {
                                Button(
                                    onClick = {
                                        if (member.status == "active") showDeactivateConfirmation = true
                                        else onSetMemberActive(member, true)
                                    },
                                    enabled = state.updatingMemberStatusForUserId == null
                                ) {
                                    Text(
                                        when {
                                            state.updatingMemberStatusForUserId == member.userId -> "Updating…"
                                            member.status == "active" -> "Deactivate access"
                                            else -> "Reactivate access"
                                        }
                                    )
                                }
                            }
                            if (showDeactivateConfirmation) {
                                Text("Deactivate ${member.name}'s organisation access?")
                                Text(
                                    "Their access will be revoked and verified remaining members will automatically receive the new organisation key. Clinical access pauses during this update. Keep your account online and unlocked; interrupted progress will resume after you sign in and unlock again. Remaining members must unlock the new key when the update finishes.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                WrappingActions {
                                    Button(
                                        onClick = {
                                            showDeactivateConfirmation = false
                                            onSetMemberActive(member, false)
                                        },
                                        enabled = state.updatingMemberStatusForUserId == null
                                    ) { Text("Confirm deactivation") }
                                    TextButton(onClick = { showDeactivateConfirmation = false }) { Text("Cancel") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ManualKeyRotationControls(state: OrganizationAccessUiState, onRotate: () -> Unit) {
    val member = state.membership as? UserMembershipState.ActiveMember ?: return
    if (member.role != "admin") return
    var confirm by remember(member.orgId) { mutableStateOf(false) }
    val enabled = state.organizationKeyExists && state.organizationKeyUnlocked && !state.maintenance.active &&
        !state.isOrganizationKeyBusy && !state.isRotatingOrganizationKey && state.approvingUserId == null &&
        state.grantingKeyToUserId == null && state.updatingMemberStatusForUserId == null
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Organisation key rotation", style = MaterialTheme.typography.titleMedium)
            Text("Update the organisation key for all active members. Their memberships are retained. Clinical access pauses until Access Update finishes.")
            Button(onClick = { confirm = true }, enabled = enabled) { Text("Rotate organisation key") }
            if (state.isRotatingOrganizationKey && !state.maintenance.active) {
                Text("Access Update: 0% · Preparing member keys…")
                LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Rotate organisation key?") },
        text = { Text("All active members will receive the new key automatically using their registered Firestore public keys. Clinical access pauses during Access Update. Keep your account online and unlocked; interrupted progress resumes after you sign in and unlock again.") },
        confirmButton = { Button(enabled = enabled, onClick = { confirm = false; onRotate() }) { Text("Start Access Update") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
}

@Composable
private fun RecoveryPhrase(phrase: String) {
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(phrase, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { clipboard.setText(AnnotatedString(phrase)) }) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy recovery phrase")
        }
    }
}

@Composable
internal fun RecoveryKeyReplacement(
    state: OrganizationAccessUiState,
    member: UserMembershipState.ActiveMember,
    onPrepare: () -> Unit,
    onSaved: (Boolean) -> Unit,
    onDiscard: () -> Unit,
    onSubmit: () -> Unit,
    onResolve: (`in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementRequest, Boolean) -> Unit,
    onRetry: () -> Unit
) {
    var expanded by remember(member.orgId) { mutableStateOf(false) }
    var approveRequest by remember(member.orgId) { mutableStateOf<`in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementRequest?>(null) }
    val requests = state.keyReplacement.takeIf { it.orgId == member.orgId }
    if (member.role == "admin") {
        requests?.error?.let { Text(it); TextButton(onClick = onRetry) { Text("Retry recovery requests") } }
        requests?.requests?.filter { it.status == "pending" }?.forEach { request ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recovery key replacement requested", style = MaterialTheme.typography.titleMedium)
                    Text(state.organizationMembers.firstOrNull { it.userId == request.userId }?.name ?: request.userId)
                    Text("User ID: ${request.userId}")
                    SelectionContainer { Text("New key fingerprint: ${request.fingerprint}") }
                    Text("Verify this member's identity and compare this fingerprint with them through a trusted channel before approving.")
                    Button(onClick = { approveRequest = request }, enabled = state.organizationKeyUnlocked && !state.maintenance.active && !state.replacementBusy) {
                        Text("Review and approve replacement")
                    }
                    TextButton(onClick = { onResolve(request, false) }, enabled = !state.replacementBusy) { Text("Reject request") }
                }
            }
        }
    } else {
        TextButton(onClick = { expanded = !expanded }) { Text("Replace lost recovery key") }
        if (expanded) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Replace lost recovery key", style = MaterialTheme.typography.titleMedium)
                Text("Organisation: ${member.organizationName}")
                Text("Save each recovery phrase with its organisation name. Replacing this key does not replace keys in your other organisations.")
                Text("This replaces your key for this organisation only. Your account, clinical contributions, and permissions stay the same. An administrator must verify you and approve access. Your current key stays registered until approval.")
                when {
                    requests?.error != null -> { Text(requests.error); TextButton(onClick = onRetry) { Text("Retry") } }
                    requests?.loaded != true -> Text("Checking recovery requests…")
                    requests.requests.firstOrNull()?.status == "pending" -> {
                        val request = requests.requests.first()
                        Text("Awaiting administrator approval. Keep the new phrase you saved; your previous phrase will not unlock the replacement key.")
                        SelectionContainer { Text("New key fingerprint: ${request.fingerprint}") }
                        TextButton(onClick = { onResolve(request, false) }, enabled = !state.replacementBusy) { Text("Cancel replacement request") }
                    }
                    state.replacementSetup != null -> {
                        state.replacementSetup.recoveryPhrase?.let { RecoveryPhrase(it) }
                        SelectionContainer { Text("New key fingerprint: ${state.replacementSetup.keyMaterial.publicKeyFingerprint}") }
                        Text("Store this phrase safely before submitting. You will need it after approval.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(state.replacementPhraseSaved, onSaved, enabled = !state.replacementBusy)
                            Text("I have safely saved my new recovery phrase")
                        }
                        Button(onClick = onSubmit, enabled = state.replacementPhraseSaved && !state.replacementBusy) { Text("Request administrator approval") }
                        TextButton(onClick = onDiscard, enabled = !state.replacementBusy) { Text("Discard new key") }
                    }
                    else -> {
                        requests.requests.firstOrNull()?.status?.let { status ->
                            Text(when (status) {
                                "approved" -> "Your replacement was approved. Unlock patient records using the new phrase you saved."
                                "rejected" -> "Your request was rejected. Contact your administrator before trying again."
                                else -> "Your previous replacement request was cancelled."
                            })
                        }
                        Button(onClick = onPrepare, enabled = !state.replacementBusy) { Text(if (state.replacementBusy) "Preparing…" else "Generate new recovery key") }
                    }
                }
            }
        }
    }
    approveRequest?.let { request ->
        AlertDialog(onDismissRequest = { approveRequest = null }, title = { Text("Approve replacement recovery key?") },
            text = { Text("Confirm you have verified the member's identity and compared fingerprint ${request.fingerprint}. This replaces their registered key and grants access to existing organisation records. Their previous phrase will no longer unlock this organisation's registered key. Previously copied keys cannot be erased remotely.") },
            confirmButton = { Button(onClick = { approveRequest = null; onResolve(request, true) },
                enabled = !state.replacementBusy && state.organizationKeyUnlocked && !state.maintenance.active) { Text("Verified — approve replacement") } },
            dismissButton = { TextButton(onClick = { approveRequest = null }) { Text("Cancel") } })
    }
}

@Composable
internal fun MemberRoleControls(member: OrganizationMember, enabled: Boolean, busy: Boolean, onSave: (String) -> Unit) {
    var selectedRole by remember(member.orgId, member.userId, member.role) { mutableStateOf(member.role) }
    FlowRow{
        Text("Member role", style = MaterialTheme.typography.labelLarge)
        FlowRow{
            WrappingActions {
                listOf("doctor" to "Doctor", "nurse" to "Nurse").forEach { (role, label) ->
                    FilterChip(
                        selected = selectedRole == role,
                        onClick = { selectedRole = role },
                        enabled = enabled,
                        label = { Text(label) })
                }
                Button(onClick = { onSave(selectedRole) }, enabled = enabled && selectedRole != member.role) {
                    Text(if (busy) "Saving role…" else "Save role")
                }
            }
        }
    }
}

@Composable
internal fun ExistingOrganizationKeyChoice(sources: List<OrganizationMember>, targetOrgId: String, enabled: Boolean,
    onUseKey: (String, String) -> Unit) {
    var selectedOrgId by remember(targetOrgId) { mutableStateOf<String?>(null) }
    var phrase by remember(targetOrgId, selectedOrgId) { mutableStateOf("") }
    Text("Reuse a key you can unlock", style = MaterialTheme.typography.titleMedium)
    Text("Select the organisation whose saved recovery phrase you have. The phrase will be verified before its key is reused.")
    sources.forEach { source ->
        FilterChip(selected = selectedOrgId == source.orgId, enabled = enabled,
            onClick = { selectedOrgId = source.orgId }, label = { Text(source.organizationName) })
    }
    OutlinedTextField(value = phrase, onValueChange = { phrase = it }, label = { Text("Selected organisation's recovery phrase") },
        visualTransformation = PasswordVisualTransformation(), enabled = enabled && selectedOrgId != null,
        singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(enabled = enabled && sources.any { it.orgId == selectedOrgId } && phrase.isNotBlank() && targetOrgId.length == 8,
        onClick = {
            val source = selectedOrgId ?: return@Button
            val submitted = phrase
            phrase = ""
            onUseKey(source, submitted)
        }) { Text("Verify and reuse selected key") }
}
