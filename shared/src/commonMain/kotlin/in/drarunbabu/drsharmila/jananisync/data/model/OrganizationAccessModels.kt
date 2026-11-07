package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** [joinedAt] and [createdAt] are epoch milliseconds mapped to Firestore Timestamp values. */
@Serializable
data class OrganizationMember(
    val orgId: String,
    val organizationName: String = "Organisation",
    val userId: String,
    val role: String,
    val status: String,
    val joinedAt: Long,
    val name: String = "Name not provided",
    val accessLevel: Int = 10
)

@Serializable
data class AccessRequest(
    val orgId: String,
    val userId: String,
    val userName: String,
    val userEmail: String = "",
    val requestedRole: String,
    val status: String,
    val createdAt: Long
)

@Serializable
sealed interface UserMembershipState {
    @Serializable
    @SerialName("loading")
    data object Loading : UserMembershipState

    @Serializable
    @SerialName("unassigned")
    data object Unassigned : UserMembershipState

    @Serializable
    @SerialName("pending_approval")
    data class PendingApproval(val orgId: String, val requestedRole: String) : UserMembershipState

    @Serializable
    @SerialName("active_member")
    data class ActiveMember(
        val orgId: String,
        val organizationName: String = "Organisation",
        val role: String,
        val name: String = "Name not provided",
        val accessLevel: Int = 10,
        val availableOrganizations: List<OrganizationMember> = emptyList()
    ) : UserMembershipState

    @Serializable
    @SerialName("choose_organization")
    data class ChooseOrganization(val organizations: List<OrganizationMember>) : UserMembershipState

    @Serializable
    @SerialName("rejected")
    data class Rejected(val orgId: String) : UserMembershipState

    @Serializable
    @SerialName("deactivated")
    data class Deactivated(val orgId: String) : UserMembershipState

    @Serializable
    @SerialName("error")
    data class Error(val message: String) : UserMembershipState
}
