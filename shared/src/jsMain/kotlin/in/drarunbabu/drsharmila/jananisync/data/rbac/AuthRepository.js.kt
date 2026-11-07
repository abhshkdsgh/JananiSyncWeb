package `in`.drarunbabu.drsharmila.jananisync.data.rbac

import `in`.drarunbabu.drsharmila.jananisync.data.MemberKeyReplacementStore
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementRequest
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementState
import `in`.drarunbabu.drsharmila.jananisync.FirebaseService
import `in`.drarunbabu.drsharmila.jananisync.FirebaseUser
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebase
import `in`.drarunbabu.drsharmila.jananisync.awaitFirebaseUnit
import `in`.drarunbabu.drsharmila.jananisync.ignoringFirebaseFailure
import `in`.drarunbabu.drsharmila.jananisync.data.MemberKeyCryptoPlatform
import `in`.drarunbabu.drsharmila.jananisync.data.OrganizationKeyCrypto
import `in`.drarunbabu.drsharmila.jananisync.data.OrganizationKeyVault
import `in`.drarunbabu.drsharmila.jananisync.data.OrganizationKeyRing
import `in`.drarunbabu.drsharmila.jananisync.data.OrganizationKeyRotation
import `in`.drarunbabu.drsharmila.jananisync.data.OrganizationRotationStore
import `in`.drarunbabu.drsharmila.jananisync.data.OrganizationMembershipStore
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMaintenance
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import `in`.drarunbabu.drsharmila.jananisync.data.RememberedOrganizationKeyStore
import `in`.drarunbabu.drsharmila.jananisync.data.model.AccessRequest
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyMaterial
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeySetup
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMember
import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.js.jsTypeOf
import kotlin.time.Duration.Companion.milliseconds

/** Kotlin/JS repository for organization membership and admin approval workflows. */
class AuthRepository(
    private val json: Json = Json { ignoreUnknownKeys = true }
) : OrganizationAccessRepository {
    private val mutableKeyReplacementState = MutableStateFlow(MemberKeyReplacementState())
    override val keyReplacementState = mutableKeyReplacementState.asStateFlow()
    private var stopKeyReplacementListener: (() -> Unit)? = null
    private var replacementObservation = 0

    override fun observeKeyReplacements(orgId: String, asAdmin: Boolean) {
        stopKeyReplacementListener?.invoke()
        stopKeyReplacementListener = null
        val generation = ++replacementObservation
        mutableKeyReplacementState.value = MemberKeyReplacementState(orgId)
        val uid = FirebaseService.currentUser?.uid ?: return
        if (!orgId.isValidOrganizationCode()) return
        stopKeyReplacementListener = MemberKeyReplacementStore.observe(orgId, uid, asAdmin) { state ->
            if (generation == replacementObservation && FirebaseService.currentUser?.uid == uid) {
                state.requests.firstOrNull { it.userId == uid && it.status == "approved" }?.let { request ->
                    if (verifiedOwnFingerprints["$orgId/$uid"] != request.fingerprint) verifiedOwnFingerprints.remove("$orgId/$uid")
                }
                mutableKeyReplacementState.value = state
            }
        }
    }

    override suspend fun prepareReplacementKey(): MemberKeySetup {
        val user = FirebaseService.currentUser ?: error("Sign in before replacing your recovery key.")
        require(user.emailVerified) { "Verify your email first." }
        val phrase = MemberKeyCryptoPlatform.generateRecoveryPhrase()
        return MemberKeySetup(phrase, MemberKeyCryptoPlatform.createMemberKeyMaterial(user.uid, phrase))
    }

    override suspend fun requestKeyReplacement(orgId: String, material: MemberKeyMaterial) {
        val uid = FirebaseService.currentUser?.uid ?: error("Sign in again.")
        require(orgId.isValidOrganizationCode()) { "Invalid organisation code." }
        require(OrganizationKeyCrypto.fingerprintPublicKey(material.publicKeyJwk) == material.publicKeyFingerprint) { "Invalid replacement key." }
        MemberKeyReplacementStore.submit(orgId, uid, material)
    }

    override suspend fun resolveKeyReplacement(request: MemberKeyReplacementRequest, approve: Boolean) = rotationMutex.withLock {
        val uid = FirebaseService.currentUser?.uid ?: error("Sign in again.")
        if (!approve) {
            MemberKeyReplacementStore.dismiss(request, uid)
        } else {
            val admin = requireActiveOrganizationAdmin(request.orgId)
            val held = unlockedOrganizationKeys[request.orgId] ?: error("Unlock the organisation key before approving replacement.")
            val control = adminControlKeys[request.orgId] ?: error("Unlock with your recovery phrase before approving replacement.")
            MemberKeyReplacementStore.approve(request, admin.uid, held, control)
        }
    }

    // Firestore's IndexedDB persistence must be requested before starting listeners/writes.
    // If a browser has persistence unavailable (for example, another tab owns it), Firestore
    // still has its in-memory cache, so continue and let Rules/server errors surface normally.
    private val persistenceReady = FirebaseService.enableOfflinePersistence()
        .ignoringFirebaseFailure()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutableMembershipState = MutableStateFlow<UserMembershipState>(UserMembershipState.Loading)
    override val membershipState: StateFlow<UserMembershipState> = mutableMembershipState.asStateFlow()

    private val mutablePendingRequests = MutableStateFlow<List<AccessRequest>>(emptyList())
    override val pendingRequests: StateFlow<List<AccessRequest>> = mutablePendingRequests.asStateFlow()

    private val mutableOrganizationMembers = MutableStateFlow<List<OrganizationMember>>(emptyList())
    override val organizationMembers: StateFlow<List<OrganizationMember>> = mutableOrganizationMembers.asStateFlow()
    private val mutableOwnPendingRequests = MutableStateFlow<List<AccessRequest>>(emptyList())
    override val ownPendingRequests = mutableOwnPendingRequests.asStateFlow()

    private var stopMembershipListener: (() -> Unit)? = null
    private var stopAdminRequestsListener: (() -> Unit)? = null
    private var stopOrganizationMembersListener: (() -> Unit)? = null
    private var membershipObservationId = 0
    private var observedMembers: List<OrganizationMember> = emptyList()
    private var activeMemberships: List<OrganizationMember> = emptyList()
    private var observedRequests: List<AccessRequest> = emptyList()
    private var selectedOrganizationId: String? = null
    private var submittingRequest = false
    private val organizationNameCache = mutableMapOf<String, String>()
    private val organizationNameLookups = mutableSetOf<String>()
    private val verifiedOwnFingerprints = mutableMapOf<String, String>()
    private val keyVersionListeners = mutableMapOf<String, () -> Unit>()
    private val unlockedOrganizationKeys = mutableMapOf<String, String>()
    private val adminControlKeys = mutableMapOf<String, String>()
    private val rotationMutex = Mutex()
    private var stopMaintenanceListener: (() -> Unit)? = null
    private var maintenanceOrganizationId: String? = null
    private val mutableMaintenance = MutableStateFlow(OrganizationMaintenance())
    override val maintenanceState = mutableMaintenance.asStateFlow()

    override fun observeOrganizationMaintenance(orgId: String) {
        if (maintenanceOrganizationId == orgId) return
        stopMaintenanceListener?.invoke()
        maintenanceOrganizationId = orgId
        mutableMaintenance.value = OrganizationMaintenance(orgId)
        if (!orgId.isValidOrganizationCode()) return
        stopMaintenanceListener = OrganizationRotationStore.observeMaintenance(orgId) { status ->
            if (maintenanceOrganizationId != orgId) return@observeMaintenance
            mutableMaintenance.value = status.copy(adminRecoveryUnlocked = orgId in adminControlKeys)
            if (status.active && orgId in adminControlKeys) {
                scope.launch { tryResumeMaintenance(orgId) }
            }
        }
    }

    private suspend fun tryResumeMaintenance(orgId: String) {
        if (rotationMutex.isLocked) return
        try { resumeOrganizationMaintenance(orgId) }
        catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (maintenanceOrganizationId == orgId) mutableMaintenance.value = mutableMaintenance.value.copy(
                message = "Maintenance is saved. Reconnect and unlock your account to continue. ${error.message.orEmpty()}")
            // Transactions fail while offline. Retry while this authenticated, unlocked session survives.
            delay(15_000.milliseconds)
            if (maintenanceOrganizationId == orgId && mutableMaintenance.value.active && orgId in adminControlKeys && FirebaseService.currentUser != null) {
                scope.launch { tryResumeMaintenance(orgId) }
            }
        }
    }

    override suspend fun resumeOrganizationMaintenance(orgId: String) = rotationMutex.withLock {
        val admin = requireActiveOrganizationAdmin(orgId)
        val controlKey = adminControlKeys[orgId] ?: error("Unlock your account with your recovery phrase to resume maintenance.")
        val held = unlockedOrganizationKeys[orgId] ?: error("Unlock the organisation key to resume maintenance.")
        keyVersionListeners.remove(orgId)?.invoke()
        try {
            OrganizationKeyRotation.resume(orgId, admin.uid, OrganizationKeyRing.parse(held), controlKey)?.let { updated ->
                check(FirebaseService.currentUser?.uid == admin.uid && orgId in adminControlKeys) { "Your sign-in changed. Unlock the organisation again." }
                unlockedOrganizationKeys[orgId] = updated.encode()
                OrganizationKeyVault.put(orgId, updated.encode())
                RememberedOrganizationKeyStore.clearOrganization(admin.uid, orgId)
            }
        } finally { if (FirebaseService.currentUser?.uid == admin.uid && orgId in unlockedOrganizationKeys) watchKeyVersion(orgId, admin.uid) }
        Unit
    }

    override suspend fun memberHasVerifiedRotationKey(orgId: String, targetUserId: String): Boolean {
        val admin = requireActiveOrganizationAdmin(orgId)
        val controlKey = adminControlKeys[orgId] ?: return false
        return try { OrganizationKeyRotation.verifiedRecipient(orgId, admin.uid, targetUserId, controlKey); true }
        catch (error: Throwable) { if (error is CancellationException) throw error; false }
    }

    private suspend fun currentEnvelope(orgId: String, userId: String, metadata: dynamic): dynamic {
        val legacy = FirebaseService.getOrganizationMemberKeyEnvelope(orgId, userId).awaitFirebase()
        if (legacy != null && legacy.keyVersion == metadata.currentKeyVersion) return legacy
        val rotationId = metadata.rotationId as? String
        return if (!rotationId.isNullOrBlank()) OrganizationRotationStore.read("organizations/$orgId/keyRotations/$rotationId/grants/$userId") else legacy
    }

    override fun observeMembershipState(userUid: String): StateFlow<UserMembershipState> {
        close()
        val observationId = ++membershipObservationId
        stopMembershipListener?.invoke()
        stopMembershipListener = null
        observedMembers = emptyList()
        activeMemberships = emptyList()
        observedRequests = emptyList()
        selectedOrganizationId = null
        unlockedOrganizationKeys.clear()
        adminControlKeys.clear()
        verifiedOwnFingerprints.clear()
        OrganizationKeyVault.clearAll()
        mutableMembershipState.value = UserMembershipState.Loading

        val currentUser = FirebaseService.currentUser
        if (currentUser == null || currentUser.uid != userUid) {
            mutableMembershipState.value = UserMembershipState.Error("Sign in to check organisation access.")
            return membershipState
        }
        if (!currentUser.emailVerified) {
            mutableMembershipState.value = UserMembershipState.Error("Verify your email before requesting organisation access.")
            return membershipState
        }

        var cancelled = false
        var stopListeners: (() -> Unit)? = null
        stopMembershipListener = {
            cancelled = true
            stopListeners?.invoke()
        }
        persistenceReady.then {
            if (!cancelled && observationId == membershipObservationId) {
                stopListeners = FirebaseService.listenToUserOrganizationAccess(
                    userId = userUid,
                    onData = { memberDocuments, requestDocuments ->
                        if (cancelled || observationId != membershipObservationId || FirebaseService.currentUser?.uid != userUid) return@listenToUserOrganizationAccess
                        val members = memberDocuments.mapNotNull { raw ->
                            runCatching { json.decodeFromString<OrganizationMember>(raw) }.getOrNull()
                        }.map { member ->
                            member.copy(organizationName = organizationNameCache[member.orgId] ?: member.organizationName)
                        }
                        val requests = requestDocuments.mapNotNull { raw ->
                            runCatching { json.decodeFromString<AccessRequest>(raw) }.getOrNull()
                        }
                        val previousActiveMemberships = activeMemberships
                        observedMembers = members
                        activeMemberships = members.filter { it.status == STATUS_ACTIVE }.sortedBy { it.orgId }
                        val activeOrgIds = activeMemberships.map { it.orgId }.toSet()
                        val removedOrgIds = previousActiveMemberships.map { it.orgId }.toSet() - activeOrgIds
                        removedOrgIds.forEach { orgId ->
                            keyVersionListeners.remove(orgId)?.invoke()
                            unlockedOrganizationKeys.remove(orgId)
                            adminControlKeys.remove(orgId)
                            scope.launch { runCatching { RememberedOrganizationKeyStore.clearOrganization(userUid, orgId) } }
                        }
                        unlockedOrganizationKeys.keys.retainAll(activeMemberships.map { it.orgId }.toSet())
                        OrganizationKeyVault.retainOrganizations(activeOrgIds)
                        scope.launch {
                            activeMemberships.forEach { member ->
                                if (member.orgId !in unlockedOrganizationKeys) {
                                    RememberedOrganizationKeyStore.restore(userUid, member.orgId)?.let { key ->
                                        val metadata = FirebaseService.getOrganizationKeyMetadata(member.orgId).awaitFirebase()
                                        if (metadata == null || (metadata.currentKeyVersion as Int) != OrganizationKeyRing.version(key)) {
                                            RememberedOrganizationKeyStore.clearOrganization(userUid, member.orgId)
                                            return@let
                                        }
                                        if (!cancelled && observationId == membershipObservationId && FirebaseService.currentUser?.uid == userUid && activeMemberships.any { it.orgId == member.orgId && it.userId == userUid && it.status == STATUS_ACTIVE }) {
                                            unlockedOrganizationKeys[member.orgId] = key
                                            OrganizationKeyVault.put(member.orgId, key)
                                            watchKeyVersion(member.orgId, userUid)
                                        }
                                    }
                                }
                            }
                        }
                        observedRequests = requests
                        mutableOwnPendingRequests.value = requests.filter { it.status == STATUS_PENDING }.sortedBy { it.createdAt }
                        if (selectedOrganizationId != null && activeMemberships.none { it.orgId == selectedOrganizationId }) {
                            selectedOrganizationId = null
                        }
                        // Local batch snapshots arrive before server acceptance. Keep the form
                        // visible until submission settles.
                        if (!submittingRequest) mutableMembershipState.value = resolveMembershipState(members, requests)
                        loadOrganizationNames(activeMemberships, observationId)
                    },
                    onError = { message ->
                        if (!cancelled && observationId == membershipObservationId && FirebaseService.currentUser?.uid == userUid)
                            mutableMembershipState.value = UserMembershipState.Error(message)
                    }
                )
            }
        }
        return membershipState
    }

    override fun selectOrganization(orgId: String?) {
        if (orgId != null && activeMemberships.none { it.orgId == orgId }) {
            mutableMembershipState.value = UserMembershipState.Error("You do not have active access to that organisation.")
            return
        }
        selectedOrganizationId = orgId
        mutableMembershipState.value = resolveMembershipState(
            observedMembers,
            observedRequests
        )
    }

    override suspend fun createMemberKeySetup(): MemberKeySetup {
        val user = FirebaseService.currentUser
            ?: error("Sign in before preparing your encryption key.")
        require(user.emailVerified) { "Verify your email before preparing your encryption key." }
        val phrase = MemberKeyCryptoPlatform.generateRecoveryPhrase()
        val material = MemberKeyCryptoPlatform.createMemberKeyMaterial(user.uid, phrase)
        return MemberKeySetup(phrase, material, reusingExistingKey = false)
    }

    override suspend fun createMemberKeySetupFromOrganization(targetOrgId: String, sourceOrgId: String, recoveryPhrase: String): MemberKeySetup {
        val user = FirebaseService.currentUser ?: error("Sign in before preparing your key.")
        require(user.emailVerified) { "Verify your email first." }
        require(targetOrgId.isValidOrganizationCode() && sourceOrgId.isValidOrganizationCode()) { "Invalid organisation code." }
        require(activeMemberships.any { it.orgId == sourceOrgId && it.userId == user.uid && it.status == STATUS_ACTIVE }) {
            "Choose an organisation where you have active membership."
        }
        persistenceReady.awaitFirebaseUnit()
        // Read from the server so a recently replaced source key cannot silently be reused from cache.
        val sourceProfile = OrganizationRotationStore.read("organizations/$sourceOrgId/keyProfiles/${user.uid}")
            ?: error("This source organisation has no registered key. Choose another organisation or prepare a new key.")
        val sourceBackup = OrganizationRotationStore.read("organizations/$sourceOrgId/keyBackups/${user.uid}")
            ?: error("This source organisation's encrypted backup is missing.")
        val material = MemberKeyMaterial(sourceProfile.publicKeyJwk as String, sourceProfile.publicKeyFingerprint as String,
            sourceBackup.ciphertext as String, sourceBackup.iv as String, sourceBackup.salt as String,
            sourceBackup.kdf as String, sourceBackup.kdfIterations as Int, sourceProfile.keyAlgorithm as String)
        OrganizationKeyCrypto.unlockMemberPrivateKey(user.uid, material, recoveryPhrase.trim())
        val targetProfile = OrganizationRotationStore.read("organizations/$targetOrgId/keyProfiles/${user.uid}")
        val targetBackup = OrganizationRotationStore.read("organizations/$targetOrgId/keyBackups/${user.uid}")
        check((targetProfile == null) == (targetBackup == null)) { "Your target organisation's recovery records are incomplete." }
        if (targetProfile != null) {
            require(targetProfile.publicKeyJwk == material.publicKeyJwk && targetProfile.publicKeyFingerprint == material.publicKeyFingerprint &&
                targetBackup.ciphertext == material.privateKeyBackupCiphertext && targetBackup.iv == material.privateKeyBackupIv) {
                "The target organisation already has a different key. Use its existing recovery phrase; joining cannot replace a registered key."
            }
        }
        check(FirebaseService.currentUser?.uid == user.uid) { "Your sign-in changed. Sign in again." }
        return MemberKeySetup(null, material, reusingExistingKey = true)
    }

    override suspend fun createMemberKeySetupForOrganization(orgId: String): MemberKeySetup {
        if (!orgId.isValidOrganizationCode()) return createMemberKeySetup()
        val uid = FirebaseService.currentUser?.uid ?: error("Sign in before preparing your key.")
        val profile = FirebaseService.getOrganizationMemberPublicKey(orgId, uid).awaitFirebase()
        val backup = FirebaseService.getOwnEncryptedKeyBackup(orgId, uid).awaitFirebase()
        check((profile == null) == (backup == null)) { "Your recovery records are incomplete. Contact the project owner." }
        if (profile != null) return MemberKeySetup(null, loadMemberKeyMaterial(orgId, uid), reusingExistingKey = true)
        return createMemberKeySetup()
    }

    override suspend fun cancelPendingRequest(orgId: String) {
        require(orgId.isValidOrganizationCode()) { "Invalid organisation code." }
        val uid = FirebaseService.currentUser?.uid ?: error("Sign in before cancelling a request.")
        persistenceReady.awaitFirebaseUnit()
        OrganizationMembershipStore.cancelRequest(orgId, uid)
    }

    override suspend fun leaveOrganization(orgId: String) {
        require(orgId.isValidOrganizationCode()) { "Invalid organisation code." }
        val uid = FirebaseService.currentUser?.uid ?: error("Sign in before leaving an organisation.")
        val generation = membershipObservationId
        persistenceReady.awaitFirebaseUnit()
        OrganizationMembershipStore.leave(orgId, uid)
        if (generation != membershipObservationId || FirebaseService.currentUser?.uid != uid) return
        unlockedOrganizationKeys.remove(orgId)
        adminControlKeys.remove(orgId)
        keyVersionListeners.remove(orgId)?.invoke()
        OrganizationKeyVault.remove(orgId)
        RememberedOrganizationKeyStore.clearOrganization(uid, orgId)
    }

    override suspend fun submitAccessRequest(
        orgId: String,
        requestedRole: String,
        name: String,
        keyMaterial: MemberKeyMaterial
    ) {
        val user = FirebaseService.currentUser
            ?: error("Sign in before requesting organisation access.")
        require(user.emailVerified) { "Verify your email before requesting organisation access." }
        val userEmail = user.email?.trim()?.takeIf { it.isNotEmpty() }
            ?: error("Your signed-in account does not have an email address.")
        require(orgId.isValidOrganizationCode()) { "Enter the 8-character organisation access code." }
        require(requestedRole == ROLE_DOCTOR || requestedRole == ROLE_NURSE) {
            "Access requests must use the doctor or nurse role. An admin must grant admin access."
        }
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) {
            "Enter a name between 1 and $MAX_NAME_LENGTH characters."
        }

        val observationId = membershipObservationId
        fun isCurrentAccount() = observationId == membershipObservationId && FirebaseService.currentUser?.uid == user.uid
        val alreadyHasActiveMembership = activeMemberships.isNotEmpty()
        val previousMembership = mutableMembershipState.value
        submittingRequest = true
        try {
            check(activeMemberships.none { it.orgId == orgId && it.userId == user.uid && it.status == STATUS_ACTIVE }) {
                "You already have access to this organisation. Open your account menu and choose Switch organisation."
            }
            check(observedRequests.none { it.orgId == orgId && it.status == STATUS_PENDING }) {
                "A request for this organisation is already awaiting administrator review. Check its current status instead of submitting another."
            }
            persistenceReady.awaitFirebaseUnit()
            check(isCurrentAccount()) { "Your sign-in session changed. Sign in again." }
            OrganizationMembershipStore.submitRequest(
                orgId = orgId,
                uid = user.uid,
                name = name.trim(),
                email = userEmail,
                role = requestedRole,
                material = keyMaterial
            )
            if (!isCurrentAccount()) return
            mutableMembershipState.value = if (alreadyHasActiveMembership) {
                resolveMembershipState(observedMembers, observedRequests)
            } else {
                UserMembershipState.PendingApproval(orgId, requestedRole)
            }
        } catch (error: Throwable) {
            // The ViewModel owns submission errors and retains the prepared key for retry.
            // A rejected batch must never be reported as a successful access request.
            if (isCurrentAccount()) mutableMembershipState.value = previousMembership
            if (error is CancellationException) throw error
            val message = when {
                "already have access to this organisation" in error.message.orEmpty() -> error.message.orEmpty()
                "already awaiting administrator review" in error.message.orEmpty() -> error.message.orEmpty()
                "permission-denied" in error.message.orEmpty() ->
                    "The request was denied. Check the organisation code. If it is correct, ask the administrator to check access rules or an existing request for this account."
                "unavailable" in error.message.orEmpty() ->
                    "The request could not reach the server. Check your connection and try again."
                else -> "Could not submit the access request. ${error.message.orEmpty()}"
            }
            throw IllegalStateException(message, error)
        } finally {
            if (isCurrentAccount()) submittingRequest = false
        }
    }

    /** Admin-only in practice: Firestore Rules verify the caller's active admin membership. */
    override suspend fun adminApproveRequest(
        orgId: String,
        targetUserId: String,
        approvedRole: String,
        name: String,
        accessLevel: Int
    ) {
        val admin = FirebaseService.currentUser
            ?: error("Sign in with an organisation admin account to approve requests.")
        require(admin.emailVerified) { "Verify your email before approving access requests." }
        require(orgId.isValidOrganizationCode()) { "Enter the 8-character organisation access code." }
        require(targetUserId.isNotBlank()) { "The target user ID is missing." }
        require(approvedRole in ALLOWED_ROLES) { "Choose admin, doctor, or nurse as the approved role." }
        require(name.isNotBlank() && name.length <= MAX_NAME_LENGTH) { "Member name must be 1 to $MAX_NAME_LENGTH characters." }
        require(accessLevel in MIN_ACCESS_LEVEL..MAX_ACCESS_LEVEL) { "Access level must be a whole number from 10 to 99." }

        // Approval and key delivery currently use separate Firestore commits. Check every
        // local and remote prerequisite for delivery first, so an admin who only has a
        // remembered organisation key cannot approve a member and leave them without access.
        validateOrganizationKeyGrantReady(orgId, targetUserId)
        persistenceReady.awaitFirebaseUnit()
        FirebaseService.approveOrganizationAccessRequest(
            orgId = orgId,
            targetUserId = targetUserId,
            approvedRole = approvedRole,
            memberName = name.trim(),
            accessLevel = accessLevel,
            adminUserId = admin.uid
        ).awaitFirebaseUnit()
    }

    private suspend fun validateOrganizationKeyGrantReady(orgId: String, targetUserId: String) {
        requireActiveOrganizationAdmin(orgId)
        val dataKey = unlockedOrganizationKeys[orgId]
            ?: error("Unlock the organisation's key with your recovery phrase before approving access.")
        adminControlKeys[orgId]
            ?: error("Unlock the organisation with your recovery phrase before approving access.")
        persistenceReady.awaitFirebaseUnit()
        val metadata = FirebaseService.getOrganizationKeyMetadata(orgId).awaitFirebase()
            ?: error("Initialise the organisation encryption key before approving access.")
        val profile = FirebaseService.getOrganizationMemberPublicKey(orgId, targetUserId).awaitFirebase()
            ?: error("The member's public key profile is missing. Ask them to submit a new access request.")
        val keyVersion = metadata.currentKeyVersion as Int
        require(OrganizationKeyRing.version(dataKey) == keyVersion) {
            "Unlock the current organisation key before approving access."
        }
        val publicKeyJwk = profile.publicKeyJwk as String
        val fingerprint = OrganizationKeyCrypto.fingerprintPublicKey(publicKeyJwk)
        val profileFingerprint = (profile.publicKeyFingerprint as String)
            .replace('+', '-').replace('/', '_').trimEnd('=')
        require(profileFingerprint == fingerprint) { "The member's public key profile is invalid." }
        OrganizationKeyCrypto.wrapOrganizationDataKey(
            dataKeyBase64 = OrganizationKeyRing.current(dataKey),
            recipientPublicJwk = publicKeyJwk,
            expectedFingerprint = fingerprint
        )
    }

    override fun observeOrganizationKeyExists(
        orgId: String,
        onChanged: (Boolean) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        var cancelled = false
        var stopListener: (() -> Unit)? = null
        persistenceReady.then {
            if (!cancelled) {
                try {
                    stopListener = FirebaseService.listenToOrganizationKeyMetadata(
                        orgId = orgId,
                        onExists = { exists -> if (!cancelled) onChanged(exists) },
                        onError = { message -> if (!cancelled) onError(message) }
                    )
                } catch (error: Throwable) {
                    if (!cancelled) onError(error.message ?: "Could not observe the organisation's encryption setup.")
                }
            }
            Unit
        }
        return {
            cancelled = true
            stopListener?.invoke()
            stopListener = null
        }
    }

    override fun initializeOrganizationEncryption(
        orgId: String,
        keyMaterial: MemberKeyMaterial,
        onComplete: (String?) -> Unit
    ) {
        val admin = try {
            requireActiveOrganizationAdmin(orgId)
        } catch (error: Throwable) {
            onComplete(error.message ?: "Could not verify organisation admin access.")
            return
        }

        val observationId = membershipObservationId
        fun isCurrentAccount() = observationId == membershipObservationId && FirebaseService.currentUser?.uid == admin.uid
        // Firestore promises are handled with JS callbacks here. Awaiting them through
        // kotlinx.coroutines was producing a completed-continuation ClassCastException
        // on this click path in the Kotlin/JS runtime.
        persistenceReady.then {
            scope.launch {
                if (!isCurrentAccount()) return@launch
                val dataKey = try {
                    OrganizationKeyCrypto.generateOrganizationDataKey()
                } catch (error: Throwable) {
                    if (isCurrentAccount()) onComplete(error.message ?: "Could not generate the organisation key.")
                    return@launch
                }
                if (!isCurrentAccount()) return@launch
                val wrappedKey = try {
                    OrganizationKeyCrypto.wrapOrganizationDataKey(
                        dataKeyBase64 = dataKey,
                        recipientPublicJwk = keyMaterial.publicKeyJwk,
                        expectedFingerprint = keyMaterial.publicKeyFingerprint
                    )
                } catch (error: Throwable) {
                    if (isCurrentAccount()) onComplete(error.message ?: "Could not encrypt the organisation key for the admin.")
                    return@launch
                }

                if (!isCurrentAccount()) return@launch
                val metadataPromise: dynamic = FirebaseService.getOrganizationKeyMetadata(orgId)
                metadataPromise.then(
                    metadataReady@ { metadata: dynamic ->
                        if (!isCurrentAccount()) return@metadataReady
                        if (metadata != null) {
                            onComplete("This organisation already has an encryption key. Unlock it or use the key rotation workflow.")
                        } else {
                            val writePromise: dynamic = FirebaseService.initializeOrganizationKey(
                                orgId, admin.uid, keyMaterial, wrappedKey
                            )
                            writePromise.then(
                                keyWritten@ { _: dynamic ->
                                    if (!isCurrentAccount()) return@keyWritten
                                    verifiedOwnFingerprints["$orgId/${admin.uid}"] = keyMaterial.publicKeyFingerprint
                                    unlockedOrganizationKeys[orgId] = dataKey
                                    OrganizationKeyVault.put(orgId, dataKey)
                                    watchKeyVersion(orgId, admin.uid)
                                    onComplete(null)
                                },
                                { reason: dynamic ->
                                    if (isCurrentAccount()) onComplete(organizationEncryptionWriteError(reason))
                                }
                            )
                        }
                        Unit
                    },
                    { _: dynamic ->
                        if (isCurrentAccount()) onComplete("Could not check the organisation's encryption status. Check your connection and permissions.")
                    }
                )
            }
            Unit
        }
    }

    override suspend fun unlockOrganizationEncryption(orgId: String, recoveryPhrase: String, rememberOnDevice: Boolean) {
        val observationId = membershipObservationId
        val user = FirebaseService.currentUser ?: error("Sign in to unlock this organisation's key.")
        require(activeMemberships.any { it.orgId == orgId && it.userId == user.uid && it.status == STATUS_ACTIVE }) {
            "You must be an active member of this organisation to unlock its key."
        }
        persistenceReady.awaitFirebaseUnit()
        val metadata = FirebaseService.getOrganizationKeyMetadata(orgId).awaitFirebase()
            ?: error("This organisation has not initialised its encryption key yet.")
        val envelope = currentEnvelope(orgId, user.uid, metadata)
            ?: error("The administrator has not finished setting up your organisation key. Ask them to complete access setup for your member account.")
        val material = loadMemberKeyMaterial(orgId, user.uid)
        val expectedVersion = metadata.currentKeyVersion as Int
        require((envelope.keyVersion as Int) == expectedVersion) {
            "Your organisation key grant is out of date. Ask an admin to refresh it."
        }
        // Persist only the derived admin secret, never the recovery phrase or RSA private key.
        val isAdmin = activeMemberships.any { it.orgId == orgId && it.userId == user.uid && it.role == "admin" }
        if (isAdmin && recoveryPhrase.isBlank() && rememberOnDevice) {
            val saved = RememberedOrganizationKeyStore.restore(user.uid, "$orgId:admin")
                ?: error("No remembered admin access on this device. Enter your recovery phrase first.")
            val record = JSON.parse<dynamic>(saved)
            require(record.fingerprint == material.publicKeyFingerprint && record.version == expectedVersion &&
                record.wrappedKey == envelope.wrappedKey) {
                RememberedOrganizationKeyStore.clearOrganization(user.uid, orgId)
                "Remembered access has changed. Enter your recovery phrase again."
            }
            val dataKey = RememberedOrganizationKeyStore.restore(user.uid, orgId)
                ?: error("Remembered access is unavailable. Enter your recovery phrase again.")
            require(OrganizationKeyRing.version(dataKey) == expectedVersion) { "Enter your recovery phrase again." }
            check(observationId == membershipObservationId && FirebaseService.currentUser?.uid == user.uid) { "Your sign-in changed." }
            adminControlKeys[orgId] = record.controlKey as String
            unlockedOrganizationKeys[orgId] = dataKey
            verifiedOwnFingerprints["$orgId/${user.uid}"] = material.publicKeyFingerprint
            OrganizationKeyVault.put(orgId, dataKey)
            watchKeyVersion(orgId, user.uid)
            if (maintenanceOrganizationId == orgId) mutableMaintenance.value = mutableMaintenance.value.copy(adminRecoveryUnlocked = true)
            scope.launch { tryResumeMaintenance(orgId) }
            return
        }
        val privateKey = OrganizationKeyCrypto.unlockMemberPrivateKey(user.uid, material, recoveryPhrase)
        verifiedOwnFingerprints["$orgId/${user.uid}"] = material.publicKeyFingerprint.replace('+', '-').replace('/', '_').trimEnd('=')
        val currentKey = OrganizationKeyCrypto.unwrapOrganizationDataKey(envelope.wrappedKey as String, privateKey)
        val dataKey = loadKeyHistory(orgId, expectedVersion, currentKey)
        check(observationId == membershipObservationId && FirebaseService.currentUser?.uid == user.uid) { "Your sign-in session changed. Sign in again." }
        if (rememberOnDevice) {
            RememberedOrganizationKeyStore.remember(user.uid, orgId, dataKey)
        } else {
            RememberedOrganizationKeyStore.clearOrganization(user.uid, orgId)
        }
        check(observationId == membershipObservationId && FirebaseService.currentUser?.uid == user.uid) { "Your sign-in session changed. Sign in again." }
        unlockedOrganizationKeys[orgId] = dataKey
        OrganizationKeyVault.put(orgId, dataKey)
        watchKeyVersion(orgId, user.uid)
        if (activeMemberships.any { it.orgId == orgId && it.userId == user.uid && it.role == "admin" }) {
            val controlKey = OrganizationKeyCrypto.adminControlKey(orgId, user.uid, material, recoveryPhrase)
            check(observationId == membershipObservationId && FirebaseService.currentUser?.uid == user.uid) { "Your sign-in changed. Sign in again." }
            if (rememberOnDevice) {
                val record = js("({})")
                record.controlKey = controlKey
                record.fingerprint = material.publicKeyFingerprint
                record.version = expectedVersion
                record.wrappedKey = envelope.wrappedKey
                RememberedOrganizationKeyStore.remember(user.uid, "$orgId:admin", JSON.stringify(record))
                if (observationId != membershipObservationId || FirebaseService.currentUser?.uid != user.uid) {
                    RememberedOrganizationKeyStore.clearOrganization(user.uid, orgId)
                    error("Your sign-in changed.")
                }
            }
            adminControlKeys[orgId] = controlKey
            if (maintenanceOrganizationId == orgId) {
                mutableMaintenance.value = mutableMaintenance.value.copy(adminRecoveryUnlocked = true)
            }
            if (!memberHasVerifiedRotationKey(orgId, user.uid)) {
                OrganizationKeyRotation.rememberRecipient(orgId, user.uid, user.uid, material.publicKeyJwk,
                    material.publicKeyFingerprint.replace('+', '-').replace('/', '_').trimEnd('='), controlKey)
            }
            scope.launch { tryResumeMaintenance(orgId) }
        }
    }

    override suspend fun adminGrantOrganizationKey(orgId: String, targetUserId: String) {
        val admin = requireActiveOrganizationAdmin(orgId)
        val dataKey = unlockedOrganizationKeys[orgId]
            ?: error("Unlock the organisation's key with your recovery phrase before granting access.")
        val controlKey = adminControlKeys[orgId]
            ?: error("Unlock with your recovery phrase once more to verify recipient keys for automatic updates.")
        persistenceReady.awaitFirebaseUnit()
        val metadata = FirebaseService.getOrganizationKeyMetadata(orgId).awaitFirebase()
            ?: error("Initialise the organisation encryption key first.")
        val profile = FirebaseService.getOrganizationMemberPublicKey(orgId, targetUserId).awaitFirebase()
            ?: error("The member's public key profile is missing. Ask them to submit a new access request.")
        val keyVersion = metadata.currentKeyVersion as Int
        require(OrganizationKeyRing.version(dataKey) == keyVersion) { "Unlock the current organisation key before granting access." }
        val publicKeyJwk = profile.publicKeyJwk as String
        val fingerprint = OrganizationKeyCrypto.fingerprintPublicKey(publicKeyJwk)
        val profileFingerprint = (profile.publicKeyFingerprint as String)
            .replace('+', '-').replace('/', '_').trimEnd('=')
        require(profileFingerprint == fingerprint) { "The member's public key profile is invalid." }
        val currentEnvelope = currentEnvelope(orgId, targetUserId, metadata)
        val wrappedKey = OrganizationKeyCrypto.wrapOrganizationDataKey(
            dataKeyBase64 = OrganizationKeyRing.current(dataKey),
            recipientPublicJwk = publicKeyJwk,
            expectedFingerprint = fingerprint
        )
        OrganizationKeyRotation.rememberRecipient(orgId, admin.uid, targetUserId, publicKeyJwk, fingerprint, controlKey)
        // Keep this suspend read separate from ?.active: Kotlin/JS otherwise omits
        // its suspension check, letting the read and grant resume this coroutine twice.
        val maintenance = OrganizationRotationStore.read("organizations/$orgId/keyMaintenance/current")
        if (maintenance?.active == true) return
        if (currentEnvelope != null && currentEnvelope.keyVersion == keyVersion) return
        FirebaseService.grantOrganizationKeyEnvelope(
            orgId = orgId,
            targetUserId = targetUserId,
            keyVersion = keyVersion,
            wrappedKey = wrappedKey,
            adminUserId = admin.uid
        ).awaitFirebaseUnit()
    }

    override suspend fun memberHasCurrentOrganizationKey(orgId: String, targetUserId: String): Boolean {
        persistenceReady.awaitFirebaseUnit()
        val metadata = FirebaseService.getOrganizationKeyMetadata(orgId).awaitFirebase() ?: return false
        val envelope = currentEnvelope(orgId, targetUserId, metadata) ?: return false
        return (envelope.keyVersion as Int) == (metadata.currentKeyVersion as Int)
    }

    private suspend fun loadKeyHistory(orgId: String, version: Int, currentKey: String): String {
        val keys = mutableMapOf(version to currentKey)
        for (old in version - 1 downTo 1) {
            val link = FirebaseService.getDocument("organizations/$orgId/keyHistory/$old").awaitFirebase()
                ?: error("Key history is incomplete at version $old. Contact an administrator.")
            require((link.wrappingVersion as Int) == old + 1 && (link.keyVersion as Int) == old)
            keys[old] = `in`.drarunbabu.drsharmila.jananisync.data.SyntheticCrypto.decrypt(
                link.ciphertext as String, link.iv as String, keys.getValue(old + 1))
        }
        return OrganizationKeyRing(version, keys).encode()
    }

    private fun watchKeyVersion(orgId: String, userUid: String) {
        keyVersionListeners.remove(orgId)?.invoke()
        keyVersionListeners[orgId] = FirebaseService.listenToOrganizationKeyVersion(orgId, { metadata ->
            val held = unlockedOrganizationKeys[orgId]
            if (held != null && (metadata == null || (metadata.currentKeyVersion as Int) !=
                    OrganizationKeyRing.version(held))) {
                unlockedOrganizationKeys.remove(orgId)
                OrganizationKeyVault.remove(orgId)
                scope.launch { RememberedOrganizationKeyStore.clearOrganization(userUid, orgId) }
            }
        }, { _ ->
            unlockedOrganizationKeys.remove(orgId)
            OrganizationKeyVault.remove(orgId)
        })
    }

    override suspend fun ownPublicKeyFingerprint(orgId: String, recoveryPhrase: String): String {
        val uid = FirebaseService.currentUser?.uid ?: error("Sign in first.")
        if (recoveryPhrase.isBlank()) return verifiedOwnFingerprints["$orgId/$uid"].orEmpty()
        val material = loadMemberKeyMaterial(orgId, uid)
        OrganizationKeyCrypto.unlockMemberPrivateKey(uid, material, recoveryPhrase)
        val fingerprint = material.publicKeyFingerprint.replace('+', '-').replace('/', '_').trimEnd('=')
        verifiedOwnFingerprints["$orgId/$uid"] = fingerprint
        return fingerprint
    }

    override suspend fun adminRotateOrganizationKey(orgId: String) = rotateKey(orgId)

    private suspend fun rotateKey(orgId: String, deactivatedUserId: String = "") {
        val admin = requireActiveOrganizationAdmin(orgId)
        val held = unlockedOrganizationKeys[orgId] ?: error("Unlock the organisation key before starting an access update.")
        val controlKey = adminControlKeys[orgId] ?: error("Unlock with your recovery phrase before automatic key updates.")
        rotationMutex.withLock {
            OrganizationKeyRotation.begin(orgId, admin.uid, deactivatedUserId, OrganizationKeyRing.parse(held), controlKey)
        }
        scope.launch { tryResumeMaintenance(orgId) }
    }

    private suspend fun loadMemberKeyMaterial(orgId: String, userId: String): MemberKeyMaterial {
        val profile = FirebaseService.getOrganizationMemberPublicKey(orgId, userId).awaitFirebase()
            ?: error("Your public key profile is missing from this organisation.")
        val backup = FirebaseService.getOwnEncryptedKeyBackup(orgId, userId).awaitFirebase()
            ?: error("Your encrypted private-key backup is missing.")
        return MemberKeyMaterial(
            publicKeyJwk = profile.publicKeyJwk as String,
            publicKeyFingerprint = profile.publicKeyFingerprint as String,
            privateKeyBackupCiphertext = backup.ciphertext as String,
            privateKeyBackupIv = backup.iv as String,
            privateKeyBackupSalt = backup.salt as String,
            privateKeyBackupKdf = backup.kdf as String,
            privateKeyBackupIterations = backup.kdfIterations as Int,
            keyAlgorithm = profile.keyAlgorithm as String
        )
    }

    private fun requireActiveOrganizationAdmin(orgId: String): FirebaseUser {
        val admin = FirebaseService.currentUser ?: error("Sign in as an organisation admin.")
        require(admin.emailVerified) { "Verify your email before managing organisation keys." }
        require(activeMemberships.any {
            it.orgId == orgId && it.userId == admin.uid && it.role == "admin" && it.status == STATUS_ACTIVE
        }) { "Only this organisation's active admin can manage its encryption key." }
        return admin
    }

    /** Collection listener is constrained by Firestore Rules to active admins of [orgId]. */
    override fun observePendingRequests(orgId: String): StateFlow<List<AccessRequest>> {
        stopAdminRequestsListener?.invoke()
        mutablePendingRequests.value = emptyList()
        if (orgId.isBlank()) return pendingRequests

        var cancelled = false
        var stopListener: (() -> Unit)? = null
        stopAdminRequestsListener = {
            cancelled = true
            stopListener?.invoke()
        }
        persistenceReady.then {
            if (!cancelled) {
                stopListener = FirebaseService.listenToPendingOrganizationAccessRequests(
                    orgId = orgId,
                    onData = { documents ->
                        if (cancelled) return@listenToPendingOrganizationAccessRequests
                        mutablePendingRequests.value = documents.mapNotNull { raw ->
                            runCatching { json.decodeFromString<AccessRequest>(raw) }.getOrNull()
                        }.sortedBy(AccessRequest::createdAt)
                    },
                    onError = { error ->
                        if (!cancelled) mutableMembershipState.value = UserMembershipState.Error(error)
                    }
                )
            }
        }
        return pendingRequests
    }

    override fun observeOrganizationMembers(orgId: String): StateFlow<List<OrganizationMember>> {
        stopOrganizationMembersListener?.invoke()
        stopOrganizationMembersListener = null
        mutableOrganizationMembers.value = emptyList()
        if (!orgId.isValidOrganizationCode()) return organizationMembers

        var cancelled = false
        var stopListener: (() -> Unit)? = null
        stopOrganizationMembersListener = {
            cancelled = true
            stopListener?.invoke()
        }
        persistenceReady.then {
            if (!cancelled) {
                stopListener = FirebaseService.listenToOrganizationMembers(
                    orgId = orgId,
                    onData = { documents ->
                        if (cancelled) return@listenToOrganizationMembers
                        mutableOrganizationMembers.value = documents.mapNotNull { raw ->
                            runCatching { json.decodeFromString<OrganizationMember>(raw) }.getOrNull()
                        }.sortedBy(OrganizationMember::name)
                    },
                    onError = { error -> if (!cancelled) mutableMembershipState.value = UserMembershipState.Error(error) }
                )
            }
        }
        return organizationMembers
    }

    override suspend fun adminUpdateMemberRole(orgId: String, targetUserId: String, role: String) {
        val admin = requireActiveOrganizationAdmin(orgId)
        require(targetUserId.isNotBlank()) { "The target member is missing." }
        require(role in arrayOf("doctor", "nurse")) { "Choose Doctor or Nurse." }
        persistenceReady.awaitFirebaseUnit()
        OrganizationMembershipStore.changeRole(orgId, admin.uid, targetUserId, role)
    }

    override suspend fun adminUpdateMemberAccessLevel(orgId: String, targetUserId: String, accessLevel: Int) {
        val admin = FirebaseService.currentUser
            ?: error("Sign in with an organisation admin account to change access levels.")
        require(admin.emailVerified) { "Verify your email before changing access levels." }
        require(orgId.isValidOrganizationCode()) { "Invalid organisation code." }
        require(targetUserId.isNotBlank()) { "The target member is missing." }
        require(accessLevel in MIN_ACCESS_LEVEL..MAX_ACCESS_LEVEL) { "Access level must be a whole number from 10 to 99." }
        persistenceReady.awaitFirebaseUnit()
        FirebaseService.updateOrganizationMemberAccessLevel(orgId, targetUserId, accessLevel).awaitFirebaseUnit()
    }

    override suspend fun adminSetMemberActive(orgId: String, targetUserId: String, active: Boolean) {
        val admin = FirebaseService.currentUser
            ?: error("Sign in with an organisation admin account to change member access.")
        require(admin.emailVerified) { "Verify your email before changing member access." }
        require(orgId.isValidOrganizationCode()) { "Invalid organisation code." }
        require(targetUserId.isNotBlank()) { "The target member is missing." }
        persistenceReady.awaitFirebaseUnit()
        if (active) FirebaseService.setOrganizationMemberActive(orgId, targetUserId, true).awaitFirebaseUnit()
        else rotateKey(orgId, targetUserId)
    }

    override fun close() {
        replacementObservation++
        stopKeyReplacementListener?.invoke()
        stopKeyReplacementListener = null
        mutableKeyReplacementState.value = MemberKeyReplacementState()
        stopMaintenanceListener?.invoke()
        stopMaintenanceListener = null
        maintenanceOrganizationId = null
        mutableMaintenance.value = OrganizationMaintenance()
        membershipObservationId++
        keyVersionListeners.values.forEach { it() }
        keyVersionListeners.clear()
        scope.coroutineContext.cancelChildren()
        stopMembershipListener?.invoke()
        stopMembershipListener = null
        stopAdminRequestsListener?.invoke()
        stopAdminRequestsListener = null
        stopOrganizationMembersListener?.invoke()
        stopOrganizationMembersListener = null
        observedMembers = emptyList()
        activeMemberships = emptyList()
        observedRequests = emptyList()
        selectedOrganizationId = null
        unlockedOrganizationKeys.clear()
        adminControlKeys.clear()
        verifiedOwnFingerprints.clear()
        OrganizationKeyVault.clearAll()
        submittingRequest = false
        organizationNameCache.clear()
        organizationNameLookups.clear()
        mutablePendingRequests.value = emptyList()
        mutableOrganizationMembers.value = emptyList()
        mutableOwnPendingRequests.value = emptyList()
        mutableMembershipState.value = UserMembershipState.Loading
    }

    private fun resolveMembershipState(
        members: List<OrganizationMember>,
        requests: List<AccessRequest>
    ): UserMembershipState {
        val active = members.filter { it.status == STATUS_ACTIVE }.sortedBy { it.orgId }
        if (active.isNotEmpty()) {
            if (active.size == 1) return active.single().asActiveState(active)
            val chosen = active.firstOrNull { it.orgId == selectedOrganizationId }
            if (chosen != null) return chosen.asActiveState(active)
            return UserMembershipState.ChooseOrganization(active)
        }
        requests.firstOrNull { it.status == STATUS_PENDING }?.let { request ->
            return UserMembershipState.PendingApproval(request.orgId, request.requestedRole)
        }
        requests.firstOrNull { it.status == STATUS_REJECTED }?.let { request ->
            return UserMembershipState.Rejected(request.orgId)
        }
        members.filter { it.status == STATUS_INACTIVE }.minByOrNull { it.orgId }?.let { member ->
            return UserMembershipState.Deactivated(member.orgId)
        }
        return UserMembershipState.Unassigned
    }

    private fun OrganizationMember.asActiveState(
        availableOrganizations: List<OrganizationMember>
    ) = UserMembershipState.ActiveMember(
        orgId = orgId,
        organizationName = organizationName,
        role = role,
        name = name,
        accessLevel = accessLevel,
        availableOrganizations = availableOrganizations
    )

    private fun loadOrganizationNames(members: List<OrganizationMember>, observationId: Int) {
        members.map(OrganizationMember::orgId).distinct().forEach { orgId ->
            if (organizationNameCache.containsKey(orgId) || !organizationNameLookups.add(orgId)) return@forEach
            FirebaseService.getOrganizationName(orgId)
                .then { organizationName ->
                    if (observationId == membershipObservationId) organizationNameLookups.remove(orgId)
                    if (observationId == membershipObservationId && organizationName.isNotBlank()) {
                        organizationNameCache[orgId] = organizationName
                        observedMembers = observedMembers.map { member ->
                            if (member.orgId == orgId) member.copy(organizationName = organizationName) else member
                        }
                        activeMemberships = observedMembers.filter { it.status == STATUS_ACTIVE }.sortedBy { it.orgId }
                        mutableMembershipState.value = resolveMembershipState(observedMembers, observedRequests)
                    }
                    Unit
                }
                .catch {
                    if (observationId == membershipObservationId) organizationNameLookups.remove(orgId)
                    Unit
                }
        }
    }

    private fun String.isValidOrganizationCode(): Boolean =
        matches(Regex("^[A-Za-z0-9]{8}$"))

    private companion object {
        const val ROLE_DOCTOR = "doctor"
        const val ROLE_NURSE = "nurse"
        const val STATUS_ACTIVE = "active"
        const val STATUS_INACTIVE = "inactive"
        const val STATUS_PENDING = "pending"
        const val STATUS_REJECTED = "rejected"
        const val MAX_NAME_LENGTH = 160
        const val MIN_ACCESS_LEVEL = 10
        const val MAX_ACCESS_LEVEL = 99
        val ALLOWED_ROLES = setOf("admin", ROLE_DOCTOR, ROLE_NURSE)
    }
}

private fun organizationEncryptionWriteError(reason: dynamic): String {
    val code = if (jsTypeOf(reason?.code) == "string") reason.code.unsafeCast<String>() else ""
    if (code == "permission-denied") {
        return "Firestore denied the encryption setup. Publish the current firestore.rules to the jananisyncweb project, then retry."
    }
    val message = if (jsTypeOf(reason?.message) == "string") reason.message.unsafeCast<String>() else ""
    return message.takeIf(String::isNotBlank)
        ?: "Firestore could not save the encryption setup. Check your connection and retry."
}

actual fun createOrganizationAccessRepository(): OrganizationAccessRepository = AuthRepository()
