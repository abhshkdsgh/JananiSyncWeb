package `in`.drarunbabu.drsharmila.jananisync.ui.navigation

import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Only opaque roster IDs enter browser history; names and clinical data stay in memory. */
@Serializable
@SerialName("signed-in")
internal data class SignedInRoute(
    val wardId: String? = null,
    val admissionId: String? = null,
    val joiningOrganization: Boolean = false,
    val partograph: Boolean = false,
    val adminConsole: Boolean = false,
    val archivedWomen: Boolean = false,
    val returnToArchivedWomen: Boolean = false
) {
    /** Treat address-bar arguments as untrusted identifiers, never permissions. */
    fun isWellFormed(): Boolean =
        (!returnToArchivedWomen || (admissionId != null && !archivedWomen && !adminConsole)) &&
        (!archivedWomen || (wardId == null && admissionId == null && !partograph && !joiningOrganization && !adminConsole)) &&
        (!adminConsole || (wardId == null && admissionId == null && !partograph && !joiningOrganization)) &&
        (!partograph || admissionId != null) &&
        (wardId == null || WARD_ID.matches(wardId)) &&
            (admissionId == null || (wardId != null && ADMISSION_ID.matches(admissionId)))

    fun isAllowedFor(membership: UserMembershipState): Boolean {
        return !(!isWellFormed() || membership !is UserMembershipState.ActiveMember) && if (adminConsole) membership.role == "admin"
        else membership.role in setOf("admin", "doctor", "nurse")
    }

    private companion object {
        val WARD_ID = Regex("^[A-Za-z0-9_-]{1,128}$")
        val ADMISSION_ID = Regex("^[A-Za-z0-9_-]{22}$")
    }

    /** Fallback for a directly opened destination with no preceding app entry. */
    fun parent(): SignedInRoute? = when {
        archivedWomen -> SignedInRoute()
        adminConsole -> SignedInRoute()
        joiningOrganization -> copy(joiningOrganization = false)
        admissionId != null && returnToArchivedWomen -> SignedInRoute(archivedWomen = true)
        admissionId != null -> copy(admissionId = null, partograph = false)
        wardId != null -> SignedInRoute()
        else -> null
    }
}
