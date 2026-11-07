package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementRequest
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import `in`.drarunbabu.drsharmila.jananisync.data.model.AccessRequest
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMember
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyMaterial
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeySetup
import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMaintenance
import `in`.drarunbabu.drsharmila.jananisync.data.rbac.OrganizationAccessRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class OrganizationAccessUiState(
    val keyReplacement: MemberKeyReplacementState = MemberKeyReplacementState(),
    val replacementSetup: MemberKeySetup? = null,
    val replacementPhraseSaved: Boolean = false,
    val replacementBusy: Boolean = false,
    val userUid: String? = null,
    val membership: UserMembershipState = UserMembershipState.Loading,
    val accessCode: String = "",
    val name: String = "",
    val requestedRole: String = "nurse",
    val recoveryPhrase: String? = null,
    val ownFingerprint: String = "",
    val isVerifyingFingerprint: Boolean = false,
    val fingerprintError: String? = null,
    val preparedKeySourceOrgId: String? = null,
    val preparedKeyMaterial: MemberKeyMaterial? = null,
    val recoveryPhraseConfirmed: Boolean = false,
    val isPreparingKeys: Boolean = false,
    val isRequestingAdditionalOrganization: Boolean = false,
    val pendingRequests: List<AccessRequest> = emptyList(),
    val ownPendingRequests: List<AccessRequest> = emptyList(),
    val cancellingRequestForOrgId: String? = null,
    val leavingOrganizationId: String? = null,
    val lastLeftOrganizationId: String? = null,
    val organizationMembers: List<OrganizationMember> = emptyList(),
    val organizationKeyExists: Boolean = false,
    val organizationKeyMetadataLoaded: Boolean = false,
    val organizationKeyUnlocked: Boolean = false,
    val adminRecoveryPhraseInput: String = "",
    val membersWithCurrentKey: Set<String> = emptySet(),
    val membersWithVerifiedRotationKey: Set<String> = emptySet(),
    val maintenance: OrganizationMaintenance = OrganizationMaintenance(),
    val membersWithCheckedKeyGrant: Set<String> = emptySet(),
    val isCheckingMemberKeyGrants: Boolean = false,
    val isOrganizationKeyBusy: Boolean = false,
    val isRotatingOrganizationKey: Boolean = false,
    val grantingKeyToUserId: String? = null,
    val isSubmitting: Boolean = false,
    val approvingUserId: String? = null,
    val updatingRoleForUserId: String? = null,
    val updatingAccessLevelForUserId: String? = null,
    val updatingMemberStatusForUserId: String? = null,
    val message: String? = null
)

class OrganizationAccessViewModel(
    private val repository: OrganizationAccessRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OrganizationAccessUiState())
    val uiState: StateFlow<OrganizationAccessUiState> = mutableUiState.asStateFlow()
    private val sessionJob = SupervisorJob(viewModelScope.coroutineContext[Job])
    private val sessionScope = CoroutineScope(viewModelScope.coroutineContext + sessionJob)
    private var sessionGeneration = 0
    private var keyPreparationGeneration = 0
    private var memberKeyGrantCheck: Job? = null
    private var fingerprintContextGeneration = 0
    private var observedFingerprintOrgId: String? = null
    private fun fingerprintOrgId(membership: UserMembershipState): String? = when (membership) {
        is UserMembershipState.ActiveMember -> membership.orgId
        is UserMembershipState.PendingApproval -> membership.orgId
        else -> null
    }
    private var replacementGeneration = 0
    private var observedReplacementOrgId: String? = null
    private var observedAdminOrgId: String? = null
    private var observedMaintenanceOrgId: String? = null
    private var stopOrganizationKeyMetadataListener: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            repository.keyReplacementState.collect { state ->
                val current = mutableUiState.value
                val replaced = state.requests.firstOrNull { it.userId == current.userUid && it.status == "approved" }
                mutableUiState.value = current.copy(keyReplacement = state,
                    ownFingerprint = if (replaced != null && current.ownFingerprint != replaced.fingerprint) "" else current.ownFingerprint)
            }
        }
        viewModelScope.launch {
            repository.membershipState.collect { membership ->
                val fingerprintOrg = fingerprintOrgId(membership)
                if (fingerprintOrg != observedFingerprintOrgId) {
                    observedFingerprintOrgId = fingerprintOrg
                    fingerprintContextGeneration++
                    keyPreparationGeneration++
                    mutableUiState.value = mutableUiState.value.copy(ownFingerprint = "", fingerprintError = null,
                        isVerifyingFingerprint = false, recoveryPhrase = null, preparedKeyMaterial = null,
                        preparedKeySourceOrgId = null, recoveryPhraseConfirmed = false, isPreparingKeys = false)
                }
                mutableUiState.value = mutableUiState.value.copy(
                    membership = membership,
                    name = mutableUiState.value.name.ifBlank {
                        (membership as? UserMembershipState.ActiveMember)?.name.orEmpty()
                    }
                )
                val replacementOrg = (membership as? UserMembershipState.ActiveMember)?.orgId.orEmpty()
                if (replacementOrg != observedReplacementOrgId) {
                    replacementGeneration++
                    observedReplacementOrgId = replacementOrg
                    mutableUiState.value = mutableUiState.value.copy(replacementSetup = null, replacementPhraseSaved = false, replacementBusy = false)
                    repository.observeKeyReplacements(replacementOrg, (membership as? UserMembershipState.ActiveMember)?.role == "admin")
                }
                val adminOrgId = (membership as? UserMembershipState.ActiveMember)
                    ?.takeIf { it.role == "admin" }
                    ?.orgId
                val maintenanceOrgId = (membership as? UserMembershipState.ActiveMember)?.orgId.orEmpty()
                if (maintenanceOrgId != observedMaintenanceOrgId) {
                    observedMaintenanceOrgId = maintenanceOrgId
                    repository.observeOrganizationMaintenance(maintenanceOrgId)
                }
                if (adminOrgId != observedAdminOrgId) {
                    observedAdminOrgId = adminOrgId
                    mutableUiState.value = mutableUiState.value.copy(
                        recoveryPhrase = null,
                        preparedKeyMaterial = null,
                        recoveryPhraseConfirmed = false,
                        adminRecoveryPhraseInput = ""
                    )
                    repository.observePendingRequests(adminOrgId.orEmpty())
                    repository.observeOrganizationMembers(adminOrgId.orEmpty())
                    refreshOrganizationKeyState(adminOrgId.orEmpty())
                }
            }
        }
        viewModelScope.launch {
            repository.maintenanceState.collect { status ->
                mutableUiState.value = mutableUiState.value.copy(maintenance = status)
            }
        }
        viewModelScope.launch {
            repository.ownPendingRequests.collect { requests ->
                mutableUiState.value = mutableUiState.value.copy(ownPendingRequests = requests)
            }
        }
        viewModelScope.launch {
            repository.pendingRequests.collect { requests ->
                mutableUiState.value = mutableUiState.value.copy(pendingRequests = requests)
            }
        }
        viewModelScope.launch {
            repository.organizationMembers.collect { members ->
                if (mutableUiState.value.organizationMembers == members) return@collect
                mutableUiState.value = mutableUiState.value.copy(organizationMembers = members)
                refreshMemberKeyGrants()
            }
        }
    }

    fun startObserving(userUid: String?) {
        if (userUid.isNullOrBlank()) {
            clearSensitiveState()
            mutableUiState.value = mutableUiState.value.copy(
                membership = UserMembershipState.Error("Your verified sign-in session is unavailable. Sign in again.")
            )
            return
        }
        if (mutableUiState.value.userUid != userUid) {
            clearSensitiveState()
            mutableUiState.value = OrganizationAccessUiState(userUid = userUid)
        }
        repository.observeMembershipState(userUid)
    }

    fun prepareReplacementKey() {
        val state = mutableUiState.value
        val member = state.membership as? UserMembershipState.ActiveMember ?: return
        if (member.role !in setOf("doctor", "nurse") || state.replacementBusy) return
        replacementAction(member.orgId) { isCurrent ->
            val setup = repository.prepareReplacementKey()
            if (isCurrent())
                mutableUiState.value = mutableUiState.value.copy(replacementSetup = setup, replacementPhraseSaved = false)
        }
    }

    fun confirmReplacementPhraseSaved(saved: Boolean) {
        mutableUiState.value = mutableUiState.value.copy(replacementPhraseSaved = saved)
    }

    fun discardReplacementSetup() {
        if (!mutableUiState.value.replacementBusy) mutableUiState.value = mutableUiState.value.copy(
            replacementSetup = null, replacementPhraseSaved = false)
    }

    fun submitKeyReplacement() {
        val state = mutableUiState.value
        val member = state.membership as? UserMembershipState.ActiveMember ?: return
        val setup = state.replacementSetup ?: return
        if (!state.replacementPhraseSaved || state.replacementBusy || member.role !in setOf("doctor", "nurse")) return
        replacementAction(member.orgId) { isCurrent ->
            repository.requestKeyReplacement(member.orgId, setup.keyMaterial)
            if (!isCurrent()) return@replacementAction
            mutableUiState.value = mutableUiState.value.copy(replacementSetup = null, replacementPhraseSaved = false,
                message = "Replacement requested. Keep your new phrase safe and ask your administrator to verify and approve it.")
        }
    }

    fun resolveKeyReplacement(request: MemberKeyReplacementRequest, approve: Boolean) {
        val state = mutableUiState.value
        val member = state.membership as? UserMembershipState.ActiveMember ?: return
        if (state.replacementBusy || member.orgId != request.orgId || request.status != "pending") return
        if (approve && (member.role != "admin" || !state.organizationKeyUnlocked || state.maintenance.active)) return
        if (!approve && member.role != "admin" && request.userId != state.userUid) return
        replacementAction(request.orgId) { isCurrent ->
            repository.resolveKeyReplacement(request, approve)
            if (!isCurrent()) return@replacementAction
            mutableUiState.value = mutableUiState.value.copy(message = if (approve)
                "Recovery key replaced and existing organisation access restored. The member can unlock using their new phrase."
                else "Recovery key request closed. The registered key has not changed.")
            refreshMemberKeyGrants()
        }
    }

    fun retryKeyReplacements() {
        val member = mutableUiState.value.membership as? UserMembershipState.ActiveMember ?: return
        repository.observeKeyReplacements(member.orgId, member.role == "admin")
    }

    private fun replacementAction(orgId: String, action: suspend (() -> Boolean) -> Unit) {
        val generation = sessionGeneration
        val operation = ++replacementGeneration
        val isCurrent = { generation == sessionGeneration && operation == replacementGeneration &&
            (mutableUiState.value.membership as? UserMembershipState.ActiveMember)?.orgId == orgId }
        mutableUiState.value = mutableUiState.value.copy(replacementBusy = true, message = null)
        sessionScope.launch {
            try { action(isCurrent) }
            catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (isCurrent())
                    mutableUiState.value = mutableUiState.value.copy(message = error.message ?: "Could not replace the recovery key.")
            } finally {
                if (isCurrent())
                    mutableUiState.value = mutableUiState.value.copy(replacementBusy = false)
            }
        }
    }

    fun selectOrganization(orgId: String?) {
        repository.selectOrganization(orgId)
    }

    fun cancelPendingRequest(orgId: String) {
        val state = mutableUiState.value
        val pending = state.ownPendingRequests.any { it.orgId == orgId } ||
            (state.membership as? UserMembershipState.PendingApproval)?.orgId == orgId
        if (!pending || state.cancellingRequestForOrgId != null || state.leavingOrganizationId != null) return
        mutableUiState.value = state.copy(cancellingRequestForOrgId = orgId, message = null)
        val generation = sessionGeneration
        sessionScope.launch {
            try {
                repository.cancelPendingRequest(orgId)
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    recoveryPhrase = null, preparedKeyMaterial = null, recoveryPhraseConfirmed = false,
                    message = "Joining request cancelled. You can apply again using your existing recovery key.")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(message = error.message ?: "Could not cancel this request.")
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(cancellingRequestForOrgId = null)
            }
        }
    }

    fun leaveOrganization(orgId: String) {
        val state = mutableUiState.value
        val role = when (val membership = state.membership) {
            is UserMembershipState.ActiveMember -> if (membership.orgId == orgId) membership.role
                else membership.availableOrganizations.firstOrNull { it.orgId == orgId }?.role
            is UserMembershipState.ChooseOrganization -> membership.organizations.firstOrNull { it.orgId == orgId }?.role
            else -> null
        } ?: return
        if (role == "admin") {
            mutableUiState.value = state.copy(message = "The organisation administrator must remain responsible for its keys and cannot leave.")
            return
        }
        if (state.leavingOrganizationId != null || state.cancellingRequestForOrgId != null) return
        if (state.maintenance.orgId == orgId && state.maintenance.active && !state.maintenance.awaitingAdmin) {
            mutableUiState.value = state.copy(message = "Wait for the current Access Update to finish before leaving.")
            return
        }
        mutableUiState.value = state.copy(leavingOrganizationId = orgId, message = null)
        val generation = sessionGeneration
        sessionScope.launch {
            try {
                repository.leaveOrganization(orgId)
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    lastLeftOrganizationId = orgId, message = "You have left the organisation. Its administrator will complete the Access Update.")
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(message = error.message ?: "Could not leave this organisation.")
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(leavingOrganizationId = null)
            }
        }
    }

    fun clearLeaveEvent() { mutableUiState.value = mutableUiState.value.copy(lastLeftOrganizationId = null) }

    fun beginJoiningOrganization() {
        keyPreparationGeneration++
        mutableUiState.value = mutableUiState.value.copy(
            isRequestingAdditionalOrganization = true,
            accessCode = "",
            recoveryPhrase = null,
            preparedKeyMaterial = null,
            preparedKeySourceOrgId = null,
            recoveryPhraseConfirmed = false,
            message = null
        )
    }

    fun cancelJoiningOrganization() {
        keyPreparationGeneration++
        mutableUiState.value = mutableUiState.value.copy(
            isRequestingAdditionalOrganization = false,
            accessCode = "",
            recoveryPhrase = null,
            preparedKeyMaterial = null,
            preparedKeySourceOrgId = null,
            recoveryPhraseConfirmed = false,
            message = null
        )
    }

    fun retry(userUid: String?) {
        repository.close()
        startObserving(userUid)
    }

    fun clearSensitiveState() {
        sessionGeneration++
        sessionJob.cancelChildren()
        memberKeyGrantCheck?.cancel()
        memberKeyGrantCheck = null
        replacementGeneration++
        fingerprintContextGeneration++
        observedFingerprintOrgId = null
        observedReplacementOrgId = null
        observedAdminOrgId = null
        observedMaintenanceOrgId = null
        stopOrganizationKeyMetadataListener?.invoke()
        stopOrganizationKeyMetadataListener = null
        repository.close()
        mutableUiState.value = OrganizationAccessUiState()
    }

    fun setAccessCode(value: String) {
        val filtered = value.filter { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }.take(8)
        if (filtered != mutableUiState.value.accessCode) {
            keyPreparationGeneration++
            mutableUiState.value = mutableUiState.value.copy(accessCode = filtered, message = null, preparedKeySourceOrgId = null,
                recoveryPhrase = null, preparedKeyMaterial = null, recoveryPhraseConfirmed = false, isPreparingKeys = false)
        }
    }

    fun setName(value: String) {
        mutableUiState.value = mutableUiState.value.copy(name = value.take(160), message = null)
    }

    fun selectRole(role: String) {
        if (role !in REQUESTABLE_ROLES) return
        mutableUiState.value = mutableUiState.value.copy(requestedRole = role, message = null)
    }

    fun prepareSecureAccess() = prepareSecureAccess(null, "")

    fun prepareSecureAccessFromOrganization(sourceOrgId: String, recoveryPhrase: String) {
        if (sourceOrgId.isBlank() || recoveryPhrase.isBlank()) return
        prepareSecureAccess(sourceOrgId, recoveryPhrase)
    }

    private fun prepareSecureAccess(sourceOrgId: String?, sourcePhrase: String) {
        if (mutableUiState.value.isPreparingKeys) return
        val current = mutableUiState.value
        val targetOrg = if (!current.isRequestingAdditionalOrganization && current.membership is UserMembershipState.ActiveMember)
            current.membership.orgId else current.accessCode
        duplicateAdditionalOrganizationMessage(current, targetOrg)?.let { message ->
            mutableUiState.value = current.copy(message = message)
            return
        }
        val preparation = ++keyPreparationGeneration
        mutableUiState.value = mutableUiState.value.copy(
            isPreparingKeys = true,
            recoveryPhrase = null,
            preparedKeyMaterial = null,
            preparedKeySourceOrgId = null,
            recoveryPhraseConfirmed = false,
            message = null
        )
        sessionScope.launch {
            val generation = sessionGeneration
            try {
                val setup: MemberKeySetup = if (sourceOrgId == null) repository.createMemberKeySetupForOrganization(targetOrg)
                    else repository.createMemberKeySetupFromOrganization(targetOrg, sourceOrgId, sourcePhrase)
                if (generation == sessionGeneration && preparation == keyPreparationGeneration) mutableUiState.value = mutableUiState.value.copy(
                    recoveryPhrase = setup.recoveryPhrase,
                    preparedKeyMaterial = setup.keyMaterial,
                    preparedKeySourceOrgId = sourceOrgId,
                    ownFingerprint = if (targetOrg == fingerprintOrgId(mutableUiState.value.membership) && !setup.reusingExistingKey)
                        setup.keyMaterial.publicKeyFingerprint else mutableUiState.value.ownFingerprint,
                    recoveryPhraseConfirmed = false
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration && preparation == keyPreparationGeneration) mutableUiState.value = mutableUiState.value.copy(
                    message = error.message ?: "Could not prepare the recovery key."
                )
            } finally {
                if (generation == sessionGeneration && preparation == keyPreparationGeneration) mutableUiState.value = mutableUiState.value.copy(isPreparingKeys = false)
            }
        }
    }

    fun confirmRecoveryPhraseSaved(saved: Boolean) {
        mutableUiState.value = mutableUiState.value.copy(recoveryPhraseConfirmed = saved)
    }

    fun setAdminRecoveryPhraseInput(value: String) {
        mutableUiState.value = mutableUiState.value.copy(adminRecoveryPhraseInput = value, message = null)
    }

    fun initializeOrganizationKey() {
        val state = mutableUiState.value
        val admin = state.membership as? UserMembershipState.ActiveMember
        val material = state.preparedKeyMaterial
        if (admin?.role != "admin" || material == null || !state.recoveryPhraseConfirmed) {
            mutableUiState.value = state.copy(message = "Prepare the admin key and confirm your recovery phrase first.")
            return
        }
        if (state.isOrganizationKeyBusy) return
        mutableUiState.value = state.copy(isOrganizationKeyBusy = true, message = null)
        val generation = sessionGeneration
        repository.initializeOrganizationEncryption(admin.orgId, material) { errorMessage ->
            if (generation != sessionGeneration) return@initializeOrganizationEncryption
            if (errorMessage != null) {
                mutableUiState.value = mutableUiState.value.copy(
                    isOrganizationKeyBusy = false,
                    message = errorMessage
                )
                return@initializeOrganizationEncryption
            }
            try {
                mutableUiState.value = mutableUiState.value.copy(
                    organizationKeyExists = true,
                    organizationKeyUnlocked = false,
                    membersWithCurrentKey = admin.availableOrganizations
                        .firstOrNull { it.orgId == admin.orgId }?.userId?.let { setOf(it) } ?: emptySet(),
                    recoveryPhrase = null,
                    preparedKeyMaterial = null,
                    recoveryPhraseConfirmed = false,
                    message = "Organisation encryption is initialised. Keep the recovery phrase available for future devices."
                )
            } finally {
                mutableUiState.value = mutableUiState.value.copy(isOrganizationKeyBusy = false)
            }
        }
    }

    fun unlockOrganizationKey() {
        unlockOrganizationKey(mutableUiState.value.adminRecoveryPhraseInput)
    }

    fun unlockOrganizationKey(recoveryPhrase: String) {
        val state = mutableUiState.value
        val member = state.membership as? UserMembershipState.ActiveMember
        if (member == null) {
            mutableUiState.value = state.copy(message = "Select an active organisation before unlocking its key.")
            return
        }
        if (recoveryPhrase.isBlank()) {
            mutableUiState.value = state.copy(message = "Enter your recovery phrase to unlock the organisation key.")
            return
        }
        if (state.isOrganizationKeyBusy) return
        mutableUiState.value = state.copy(isOrganizationKeyBusy = true, adminRecoveryPhraseInput = "", message = null)
        sessionScope.launch {
            val generation = sessionGeneration
            try {
                repository.unlockOrganizationEncryption(member.orgId, recoveryPhrase.trim(), true)
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    organizationKeyUnlocked = true,
                    adminRecoveryPhraseInput = "",
                    message = "Organisation key unlocked and remembered on this device."
                )
                refreshMemberKeyGrants()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    message = error.message ?: "Could not unlock the organisation key."
                )
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(isOrganizationKeyBusy = false)
            }
        }
    }

    fun clearFingerprintError() { mutableUiState.value = mutableUiState.value.copy(fingerprintError = null) }

    fun resumeMaintenance() {
        val member = mutableUiState.value.membership as? UserMembershipState.ActiveMember ?: return
        if (member.role != "admin" || mutableUiState.value.isOrganizationKeyBusy) return
        mutableUiState.value = mutableUiState.value.copy(isOrganizationKeyBusy = true, message = null)
        val generation = sessionGeneration
        sessionScope.launch {
            try { repository.resumeOrganizationMaintenance(member.orgId) }
            catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(message = error.message)
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(isOrganizationKeyBusy = false)
            }
        }
    }

    fun rotateOrganizationKey() {
        val state = mutableUiState.value
        val admin = state.membership as? UserMembershipState.ActiveMember
        if (admin?.role != "admin" || !state.organizationKeyUnlocked) {
            mutableUiState.value = state.copy(message = "Unlock your admin account before rotating the organisation key.")
            return
        }
        if (state.isRotatingOrganizationKey || state.isOrganizationKeyBusy || state.maintenance.active ||
            state.approvingUserId != null || state.grantingKeyToUserId != null || state.updatingMemberStatusForUserId != null) return
        mutableUiState.value = state.copy(isRotatingOrganizationKey = true, message = null)
        val generation = sessionGeneration
        sessionScope.launch {
            try {
                repository.adminRotateOrganizationKey(admin.orgId)
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    message = "Access Update started. Follow its progress in the dashboard.")
                refreshMemberKeyGrants()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    message = error.message ?: "Could not start organisation key rotation.")
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(isRotatingOrganizationKey = false)
            }
        }
    }

    fun verifyOwnFingerprint(recoveryPhrase: String) {
        val state = mutableUiState.value
        if (state.isVerifyingFingerprint) return
        val orgId = when (val membership = state.membership) {
            is UserMembershipState.ActiveMember -> membership.orgId
            is UserMembershipState.PendingApproval -> membership.orgId
            else -> return
        }
        val generation = sessionGeneration
        val context = fingerprintContextGeneration
        mutableUiState.value = state.copy(isVerifyingFingerprint = true, fingerprintError = null)
        sessionScope.launch {
            try {
                val fingerprint = repository.ownPublicKeyFingerprint(orgId, recoveryPhrase)
                if (generation == sessionGeneration && context == fingerprintContextGeneration) mutableUiState.value = mutableUiState.value.copy(
                    ownFingerprint = fingerprint,
                    fingerprintError = if (fingerprint.isBlank()) "Enter your personal recovery phrase to verify your fingerprint." else null)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration && context == fingerprintContextGeneration) mutableUiState.value = mutableUiState.value.copy(
                    fingerprintError = error.message ?: "Could not verify your fingerprint.")
            } finally {
                if (generation == sessionGeneration && context == fingerprintContextGeneration) mutableUiState.value = mutableUiState.value.copy(isVerifyingFingerprint = false)
            }
        }
    }

    fun grantOrganizationKey(member: OrganizationMember) {
        val state = mutableUiState.value
        val admin = state.membership as? UserMembershipState.ActiveMember
        if (admin?.role != "admin" || admin.orgId != member.orgId || !state.organizationKeyUnlocked) {
            mutableUiState.value = state.copy(message = "Unlock this organisation's key before granting member access.")
            return
        }
        if (member.status != "active" || member.userId == admin.availableOrganizations
                .firstOrNull { it.orgId == admin.orgId }?.userId) {
            mutableUiState.value = state.copy(message = "Key grants are available only to other active members.")
            return
        }
        if (state.grantingKeyToUserId != null) return
        mutableUiState.value = state.copy(grantingKeyToUserId = member.userId, message = null)
        sessionScope.launch {
            val generation = sessionGeneration
            try {
                repository.adminGrantOrganizationKey(member.orgId, member.userId)
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    membersWithCurrentKey = mutableUiState.value.membersWithCurrentKey + member.userId,
                    message = "${member.name}'s key is set up for automatic access updates."
                )
                refreshMemberKeyGrants()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    message = error.message ?: "Could not grant the organisation key."
                )
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(grantingKeyToUserId = null)
            }
        }
    }

    private fun refreshOrganizationKeyState(orgId: String) {
        memberKeyGrantCheck?.cancel()
        mutableUiState.value = mutableUiState.value.copy(membersWithCheckedKeyGrant = emptySet(), isCheckingMemberKeyGrants = false)
        stopOrganizationKeyMetadataListener?.invoke()
        stopOrganizationKeyMetadataListener = null
        if (orgId.isBlank()) {
            mutableUiState.value = mutableUiState.value.copy(
                organizationKeyExists = false,
                organizationKeyUnlocked = false,
                membersWithCurrentKey = emptySet()
            )
            return
        }
        mutableUiState.value = mutableUiState.value.copy(
            organizationKeyExists = false,
            organizationKeyMetadataLoaded = false,
            organizationKeyUnlocked = false,
            membersWithCurrentKey = emptySet()
        )
        val generation = sessionGeneration
        var attemptedRememberedUnlock = false
        stopOrganizationKeyMetadataListener = repository.observeOrganizationKeyExists(
            orgId = orgId,
            onChanged = { exists ->
                if (generation == sessionGeneration && observedAdminOrgId == orgId) {
                    mutableUiState.value = mutableUiState.value.copy(
                        organizationKeyExists = exists,
                        organizationKeyMetadataLoaded = true,
                        organizationKeyUnlocked = exists && mutableUiState.value.organizationKeyUnlocked
                    )
                    if (exists && !attemptedRememberedUnlock) {
                        attemptedRememberedUnlock = true
                        sessionScope.launch {
                            try {
                                repository.unlockOrganizationEncryption(orgId, "", true)
                                if (generation == sessionGeneration && observedAdminOrgId == orgId) {
                                    mutableUiState.value = mutableUiState.value.copy(organizationKeyUnlocked = true)
                                    refreshMemberKeyGrants()
                                }
                            } catch (error: Throwable) {
                                if (error is CancellationException) throw error
                                // No valid remembered access: leave the phrase prompt available.
                            }
                        }
                    }
                    refreshMemberKeyGrants()
                }
            },
            onError = { message ->
                if (generation == sessionGeneration && observedAdminOrgId == orgId) {
                    mutableUiState.value = mutableUiState.value.copy(message = message)
                }
            }
        )
    }

    fun refreshMemberKeyGrants() {
        memberKeyGrantCheck?.cancel()
        val state = mutableUiState.value
        val admin = state.membership as? UserMembershipState.ActiveMember
        mutableUiState.value = state.copy(membersWithCheckedKeyGrant = emptySet(), isCheckingMemberKeyGrants = false)
        if (admin?.role != "admin" || !state.organizationKeyExists) return
        val members = state.organizationMembers.filter { it.orgId == admin.orgId && it.status == "active" }
        if (members.isEmpty()) return
        mutableUiState.value = mutableUiState.value.copy(isCheckingMemberKeyGrants = true)
        memberKeyGrantCheck = sessionScope.launch {
            val generation = sessionGeneration
            try {
                for ((_, _, userId) in members) {
                    val hasKey = try {
                        repository.memberHasCurrentOrganizationKey(admin.orgId, userId)
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        continue // Unknown is distinct from a confirmed missing grant; the UI offers retry.
                    }
                    val verified = repository.memberHasVerifiedRotationKey(admin.orgId, userId)
                    val current = mutableUiState.value
                    if ((current.membership as? UserMembershipState.ActiveMember)?.orgId != admin.orgId) return@launch
                    if (generation == sessionGeneration) mutableUiState.value = current.copy(
                        membersWithCurrentKey = if (hasKey) current.membersWithCurrentKey + userId
                            else current.membersWithCurrentKey - userId,
                        membersWithCheckedKeyGrant = current.membersWithCheckedKeyGrant + userId,
                        membersWithVerifiedRotationKey = if (verified) current.membersWithVerifiedRotationKey + userId
                            else current.membersWithVerifiedRotationKey - userId
                    )
                }
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(isCheckingMemberKeyGrants = false)
            }
        }
    }

    fun submitAccessRequest() {
        val state = mutableUiState.value
        if (state.isSubmitting) return
        duplicateAdditionalOrganizationMessage(state, state.accessCode)?.let { message ->
            mutableUiState.value = state.copy(message = message)
            return
        }
        if (state.name.isBlank()) {
            mutableUiState.value = state.copy(message = "Enter your name before requesting access.")
            return
        }
        if (!ACCESS_CODE.matches(state.accessCode)) {
            mutableUiState.value = state.copy(message = "Enter an 8-character alphanumeric access code.")
            return
        }
        val keyMaterial = state.preparedKeyMaterial
        if (keyMaterial == null || !state.recoveryPhraseConfirmed) {
            mutableUiState.value = state.copy(message = "Generate and safely record your recovery phrase before requesting access.")
            return
        }
        mutableUiState.value = state.copy(isSubmitting = true, message = null)
        sessionScope.launch {
            val generation = sessionGeneration
            try {
                repository.submitAccessRequest(state.accessCode, state.requestedRole, state.name.trim(), keyMaterial)
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    recoveryPhrase = null,
                    preparedKeyMaterial = null,
                    recoveryPhraseConfirmed = false,
                    message = "Access request submitted. Waiting for administrator approval."
                )
                if (state.membership is UserMembershipState.ActiveMember) {
                    if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                        isRequestingAdditionalOrganization = false,
                        accessCode = "",
                        message = "Access request submitted. You can continue using your active organisation while it is reviewed."
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    message = error.message ?: "Could not submit the access request."
                )
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(isSubmitting = false)
            }
        }
    }

    private fun duplicateAdditionalOrganizationMessage(state: OrganizationAccessUiState, orgId: String): String? {
        if (!state.isRequestingAdditionalOrganization || orgId.length != 8) return null
        val active = (state.membership as? UserMembershipState.ActiveMember)
            ?.availableOrganizations?.firstOrNull { it.orgId == orgId }
        if (active != null) {
            return "You already have access to ${active.organizationName}. Open your account menu and choose Switch organisation."
        }
        if (state.ownPendingRequests.any { it.orgId == orgId }) {
            return "A request for this organisation is already awaiting administrator review. Check the pending request shown above instead of submitting another."
        }
        return null
    }

    fun approve(request: AccessRequest, accessLevel: Int) {
        val state = mutableUiState.value
        val admin = state.membership as? UserMembershipState.ActiveMember
        if (admin?.role != "admin" || admin.orgId != request.orgId || !state.organizationKeyUnlocked) {
            mutableUiState.value = state.copy(message = "Verify admin access before approving requests.")
            return
        }
        if (state.approvingUserId != null || state.grantingKeyToUserId != null) return
        if (accessLevel !in MIN_ACCESS_LEVEL..MAX_ACCESS_LEVEL) {
            mutableUiState.value = mutableUiState.value.copy(message = "Access level must be a whole number from 10 to 99.")
            return
        }
        mutableUiState.value = mutableUiState.value.copy(approvingUserId = request.userId, message = null)
        sessionScope.launch {
            val generation = sessionGeneration
            var membershipApproved = false
            try {
                repository.adminApproveRequest(
                    request.orgId,
                    request.userId,
                    request.requestedRole,
                    request.userName,
                    accessLevel
                )
                membershipApproved = true
                repository.adminGrantOrganizationKey(request.orgId, request.userId)
                val current = mutableUiState.value
                if ((current.membership as? UserMembershipState.ActiveMember)?.orgId == request.orgId) {
                    if (generation == sessionGeneration) mutableUiState.value = current.copy(
                        membersWithCurrentKey = current.membersWithCurrentKey + request.userId,
                        message = "${request.userName} approved and access granted."
                    )
                    refreshMemberKeyGrants()
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    message = if (membershipApproved) {
                        "Membership approved, but access setup could not finish. Use Complete access setup on the member card to retry. ${error.message.orEmpty()}"
                    } else error.message ?: "Could not approve the access request."
                )
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(approvingUserId = null)
            }
        }
    }

    fun updateMemberRole(member: OrganizationMember, role: String) {
        val state = mutableUiState.value
        val admin = state.membership as? UserMembershipState.ActiveMember
        if (state.updatingRoleForUserId != null || state.maintenance.active) return
        if (admin?.role != "admin" || admin.orgId != member.orgId) {
            mutableUiState.value = state.copy(message = "Only this organisation's administrator can change member roles.")
            return
        }
        if (role !in REQUESTABLE_ROLES || member.role !in REQUESTABLE_ROLES || member.status !in setOf("active", "inactive") || role == member.role) return
        val generation = sessionGeneration
        mutableUiState.value = state.copy(updatingRoleForUserId = member.userId, message = null)
        sessionScope.launch {
            try {
                repository.adminUpdateMemberRole(member.orgId, member.userId, role)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(message = error.message ?: "Could not change this member's role.")
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(updatingRoleForUserId = null)
            }
        }
    }

    fun updateAccessLevel(member: OrganizationMember, accessLevel: Int) {
        if (mutableUiState.value.updatingAccessLevelForUserId != null) return
        if (accessLevel !in MIN_ACCESS_LEVEL..MAX_ACCESS_LEVEL) {
            mutableUiState.value = mutableUiState.value.copy(message = "Access level must be a whole number from 10 to 99.")
            return
        }
        val activeAdmin = mutableUiState.value.membership as? UserMembershipState.ActiveMember
        if (activeAdmin == null || activeAdmin.role != "admin" || activeAdmin.orgId != member.orgId) {
            mutableUiState.value = mutableUiState.value.copy(message = "Only this organisation's admin can change member access levels.")
            return
        }
        mutableUiState.value = mutableUiState.value.copy(updatingAccessLevelForUserId = member.userId, message = null)
        sessionScope.launch {
            val generation = sessionGeneration
            try {
                repository.adminUpdateMemberAccessLevel(member.orgId, member.userId, accessLevel)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    message = error.message ?: "Could not update this member's access level."
                )
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(updatingAccessLevelForUserId = null)
            }
        }
    }

    fun setMemberActive(member: OrganizationMember, active: Boolean) {
        if (mutableUiState.value.updatingMemberStatusForUserId != null) return
        val admin = mutableUiState.value.membership as? UserMembershipState.ActiveMember
        if (admin == null || admin.role != "admin" || admin.orgId != member.orgId) {
            mutableUiState.value = mutableUiState.value.copy(message = "Only this organisation's admin can change member access.")
            return
        }
        if (member.role !in setOf("doctor", "nurse")) {
            mutableUiState.value = mutableUiState.value.copy(message = "Admin accounts cannot be deactivated here.")
            return
        }
        if ((member.status == "active") == active) return

        mutableUiState.value = mutableUiState.value.copy(
            updatingMemberStatusForUserId = member.userId,
            message = null
        )
        sessionScope.launch {
            val generation = sessionGeneration
            try {
                repository.adminSetMemberActive(member.orgId, member.userId, active)
                if (!active) refreshMemberKeyGrants()
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    membersWithCurrentKey = if (active) mutableUiState.value.membersWithCurrentKey else emptySet(),
                    membersWithCheckedKeyGrant = if (active) mutableUiState.value.membersWithCheckedKeyGrant else emptySet(),
                    message = if (active) "${member.name} has been reactivated." else
                        "${member.name}'s access has been revoked. Key delivery will complete automatically; any interrupted progress is saved."
                )
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(
                    message = error.message ?: "Could not change this member's access."
                )
            } finally {
                if (generation == sessionGeneration) mutableUiState.value = mutableUiState.value.copy(updatingMemberStatusForUserId = null)
            }
        }
    }

    fun clearMessage() {
        mutableUiState.value = mutableUiState.value.copy(message = null)
    }

    override fun onCleared() {
        stopOrganizationKeyMetadataListener?.invoke()
        stopOrganizationKeyMetadataListener = null
        repository.close()
        super.onCleared()
    }

    private companion object {
        val ACCESS_CODE = Regex("^[A-Za-z0-9]{8}$")
        val REQUESTABLE_ROLES = setOf("doctor", "nurse")
        const val MIN_ACCESS_LEVEL = 10
        const val MAX_ACCESS_LEVEL = 99
    }
}
