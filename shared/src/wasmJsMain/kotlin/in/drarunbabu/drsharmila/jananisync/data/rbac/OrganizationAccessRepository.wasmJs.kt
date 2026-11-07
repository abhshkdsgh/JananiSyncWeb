package `in`.drarunbabu.drsharmila.jananisync.data.rbac

import `in`.drarunbabu.drsharmila.jananisync.data.model.AccessRequest
import `in`.drarunbabu.drsharmila.jananisync.data.model.OrganizationMember
import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyMaterial
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeySetup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

actual fun createOrganizationAccessRepository(): OrganizationAccessRepository =
    UnsupportedOrganizationAccessRepository()

private class UnsupportedOrganizationAccessRepository : OrganizationAccessRepository {
    override val membershipState = MutableStateFlow<UserMembershipState>(
        UserMembershipState.Error("Organization access is available in the Kotlin/JS web app only.")
    )
    override val pendingRequests = MutableStateFlow<List<AccessRequest>>(emptyList())
    override val organizationMembers = MutableStateFlow<List<OrganizationMember>>(emptyList())

    override fun observeMembershipState(userUid: String): StateFlow<UserMembershipState> = membershipState
    override fun selectOrganization(orgId: String?) = Unit
    override suspend fun createMemberKeySetup(): MemberKeySetup = unavailable()
    override suspend fun submitAccessRequest(
        orgId: String,
        requestedRole: String,
        name: String,
        keyMaterial: MemberKeyMaterial
    ) = unavailable()
    override suspend fun adminApproveRequest(
        orgId: String,
        targetUserId: String,
        approvedRole: String,
        name: String,
        accessLevel: Int
    ) = unavailable()
    override fun observeOrganizationKeyExists(
        orgId: String,
        onChanged: (Boolean) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit = unavailable()
    override fun initializeOrganizationEncryption(
        orgId: String,
        keyMaterial: MemberKeyMaterial,
        onComplete: (String?) -> Unit
    ) = unavailable()
    override suspend fun unlockOrganizationEncryption(orgId: String, recoveryPhrase: String, rememberOnDevice: Boolean) = unavailable()
    override suspend fun adminGrantOrganizationKey(orgId: String, targetUserId: String) = unavailable()
    override suspend fun memberHasCurrentOrganizationKey(orgId: String, targetUserId: String): Boolean = unavailable()
    override fun observePendingRequests(orgId: String): StateFlow<List<AccessRequest>> = pendingRequests
    override fun observeOrganizationMembers(orgId: String): StateFlow<List<OrganizationMember>> = organizationMembers
    override suspend fun adminUpdateMemberAccessLevel(orgId: String, targetUserId: String, accessLevel: Int) = unavailable()
    override suspend fun adminSetMemberActive(orgId: String, targetUserId: String, active: Boolean) = unavailable()
    override fun close() = Unit

    private fun unavailable(): Nothing = error("Organization access is available in the Kotlin/JS web app only.")
}
