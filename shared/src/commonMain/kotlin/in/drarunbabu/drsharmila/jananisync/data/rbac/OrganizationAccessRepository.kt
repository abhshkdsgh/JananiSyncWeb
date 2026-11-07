package `in`.drarunbabu.drsharmila.jananisync.data.rbac

import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementRequest
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyReplacementState
import `in`.drarunbabu.drsharmila.jananisync.data.model.AccessRequest
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMember
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyMaterial
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeySetup
import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMaintenance

private val noKeyReplacements = MutableStateFlow(MemberKeyReplacementState())
private val noMaintenance = MutableStateFlow(OrganizationMaintenance())
private val noOwnRequests = MutableStateFlow<List<AccessRequest>>(emptyList())

/** Platform implementation backed by Firebase Auth and Cloud Firestore on Kotlin/JS. */
interface OrganizationAccessRepository {
    val keyReplacementState: StateFlow<MemberKeyReplacementState> get() = noKeyReplacements
    fun observeKeyReplacements(orgId: String, asAdmin: Boolean) = Unit
    suspend fun prepareReplacementKey(): MemberKeySetup = error("Key replacement is unavailable on this platform.")
    suspend fun requestKeyReplacement(orgId: String, material: MemberKeyMaterial): Unit = error("Key replacement is unavailable on this platform.")
    suspend fun resolveKeyReplacement(request: MemberKeyReplacementRequest, approve: Boolean): Unit = error("Key replacement is unavailable on this platform.")
    val membershipState: StateFlow<UserMembershipState>
    val pendingRequests: StateFlow<List<AccessRequest>>
    val organizationMembers: StateFlow<List<OrganizationMember>>
    val maintenanceState: StateFlow<OrganizationMaintenance> get() = noMaintenance
    val ownPendingRequests: StateFlow<List<AccessRequest>> get() = noOwnRequests
    suspend fun cancelPendingRequest(orgId: String): Unit = error("Request cancellation is unavailable on this platform.")
    suspend fun leaveOrganization(orgId: String): Unit = error("Leaving an organisation is unavailable on this platform.")
    suspend fun createMemberKeySetupFromOrganization(targetOrgId: String, sourceOrgId: String, recoveryPhrase: String): MemberKeySetup = error("Verified key reuse is unavailable on this platform.")
    suspend fun createMemberKeySetupForOrganization(orgId: String): MemberKeySetup = createMemberKeySetup()
    fun observeOrganizationMaintenance(orgId: String) = Unit
    suspend fun resumeOrganizationMaintenance(orgId: String) = Unit
    suspend fun adminRotateOrganizationKey(orgId: String): Unit = error("Manual rotation is unavailable on this platform.")
    suspend fun memberHasVerifiedRotationKey(orgId: String, targetUserId: String): Boolean = false

    fun observeMembershipState(userUid: String): StateFlow<UserMembershipState>
    fun selectOrganization(orgId: String?)
    suspend fun createMemberKeySetup(): MemberKeySetup
    suspend fun submitAccessRequest(orgId: String, requestedRole: String, name: String, keyMaterial: MemberKeyMaterial)
    suspend fun adminApproveRequest(orgId: String, targetUserId: String, approvedRole: String, name: String, accessLevel: Int)
    fun observeOrganizationKeyExists(
        orgId: String,
        onChanged: (Boolean) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit
    fun initializeOrganizationEncryption(
        orgId: String,
        keyMaterial: MemberKeyMaterial,
        onComplete: (errorMessage: String?) -> Unit
    )
    suspend fun unlockOrganizationEncryption(orgId: String, recoveryPhrase: String, rememberOnDevice: Boolean = false)
    suspend fun adminGrantOrganizationKey(orgId: String, targetUserId: String)
    suspend fun ownPublicKeyFingerprint(orgId: String, recoveryPhrase: String = ""): String = ""
    suspend fun memberHasCurrentOrganizationKey(orgId: String, targetUserId: String): Boolean
    fun observePendingRequests(orgId: String): StateFlow<List<AccessRequest>>
    fun observeOrganizationMembers(orgId: String): StateFlow<List<OrganizationMember>>
    suspend fun adminUpdateMemberRole(orgId: String, targetUserId: String, role: String): Unit = error("Role changes are unavailable on this platform.")
    suspend fun adminUpdateMemberAccessLevel(orgId: String, targetUserId: String, accessLevel: Int)
    suspend fun adminSetMemberActive(orgId: String, targetUserId: String, active: Boolean)
    fun close()
}

expect fun createOrganizationAccessRepository(): OrganizationAccessRepository
