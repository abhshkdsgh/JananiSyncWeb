package `in`.drarunbabu.drsharmila.jananisync.ui.navigation

import `in`.drarunbabu.drsharmila.jananisync.data.RosterState
import `in`.drarunbabu.drsharmila.jananisync.data.model.UserMembershipState
import `in`.drarunbabu.drsharmila.jananisync.data.model.Ward
import `in`.drarunbabu.drsharmila.jananisync.data.model.Woman
import kotlin.test.*

class RouteSecurityTest {
    private val ward = Ward("ward-a", "Ward A", "", "")
    private val otherWard = ward.copy(wardId = "ward-b")
    private val admission = "a".repeat(22)
    private val woman = Woman(1, 2, "Test", 20000101, 0, ward, 0, "", "", "", admission)
    private val roster = RosterState(listOf(ward, otherWard), listOf(woman), isLoading = false)
    private val guide = SignedInRoute(ward.wardId, admission)

    @Test
    fun archivedWomenScreenIsReadableByClinicalMembersAndRejectsConflictingRoutes() {
        val archive = SignedInRoute(archivedWomen = true)
        for (role in listOf("admin", "doctor", "nurse")) {
            assertTrue(archive.isAllowedFor(UserMembershipState.ActiveMember("OrgOne01", role = role, accessLevel = 10)))
        }
        assertFalse(archive.isAllowedFor(UserMembershipState.Unassigned))
        assertFalse(archive.copy(wardId = "ward-a").isWellFormed())
        assertFalse(archive.copy(admissionId = "a".repeat(22)).isWellFormed())
        assertFalse(archive.copy(adminConsole = true).isWellFormed())
        assertFalse(archive.copy(joiningOrganization = true).isWellFormed())
        assertFalse(archive.copy(partograph = true).isWellFormed())
        assertEquals(SignedInRoute(), archive.parent())
    }

    @Test
    fun onlyAdminsWithSuccessfullyLoadedEmptyWardsAreSentToSetup() {
        val empty = RosterState(isLoading = false)
        assertTrue(empty.needsInitialWardSetup("admin", null, false))
        assertTrue(empty.copy(message = "Unlock key", wardCount = 0).needsInitialWardSetup("admin", null, false))
        assertFalse(empty.copy(message = "Unlock key", wardCount = 1).needsInitialWardSetup("admin", null, false))
        assertFalse(empty.copy(isLoading = true).needsInitialWardSetup("admin", null, false))
        assertFalse(empty.copy(message = "Unavailable").needsInitialWardSetup("admin", null, false))
        assertFalse(roster.needsInitialWardSetup("admin", null, false))
        assertFalse(empty.needsInitialWardSetup("nurse", null, false))
        assertFalse(empty.needsInitialWardSetup("admin", null, true))
        assertFalse(empty.needsInitialWardSetup("admin", "ward-a", false))
    }

    @Test
    fun adminConsoleRejectsNonAdminsAndClinicalArguments() {
        val console = SignedInRoute(adminConsole = true)
        assertTrue(console.isAllowedFor(UserMembershipState.ActiveMember("OrgOne01", role = "admin")))
        for (role in listOf("doctor", "nurse")) {
            assertFalse(console.isAllowedFor(UserMembershipState.ActiveMember("OrgOne01", role = role)))
        }
        assertFalse(console.copy(wardId = "ward-a").isWellFormed())
        assertFalse(console.copy(joiningOrganization = true).isWellFormed())
        assertEquals(SignedInRoute(), console.parent())
    }

    @Test
    fun rejectsMalformedIdentifiersAndAdmissionsWithoutWard() {
        listOf(
            SignedInRoute(admissionId = admission),
            SignedInRoute(""), SignedInRoute("../other-org"),
            SignedInRoute("a".repeat(129)), SignedInRoute("ward-a?role=admin"),
            guide.copy(admissionId = "../private"), guide.copy(admissionId = "a".repeat(23))
        ).forEach { assertFalse(it.isWellFormed(), it.toString()) }
        assertTrue(guide.isWellFormed())
    }

    @Test
    fun deniesClinicalRoutesWithoutActiveClinicalMembership() {
        val denied = listOf(
            UserMembershipState.Loading, UserMembershipState.Unassigned,
            UserMembershipState.PendingApproval("OrgOne01", "nurse"),
            UserMembershipState.Rejected("OrgOne01"), UserMembershipState.Deactivated("OrgOne01"),
            UserMembershipState.ActiveMember("OrgOne01", role = "forged-role")
        )
        denied.forEach { assertFalse(guide.isAllowedFor(it), it.toString()) }
        for (role in listOf("admin", "doctor", "nurse")) {
            assertTrue(guide.isAllowedFor(UserMembershipState.ActiveMember("OrgOne01", role = role)))
        }
        assertTrue(SignedInRoute().isAllowedFor(UserMembershipState.ActiveMember("OrgOne01", role = "admin")))
    }

    @Test
    fun modifiedIdsCannotSelectMissingOrWrongWardAdmissions() {
        assertEquals(woman, guide.resolveSelection(roster).woman)
        assertNull(guide.copy(wardId = otherWard.wardId).resolveSelection(roster).woman)
        assertNull(guide.copy(wardId = "foreign-ward").resolveSelection(roster).woman)
        assertNull(guide.copy(admissionId = "b".repeat(22)).resolveSelection(roster).woman)
        assertNull(guide.resolveSelection(roster.copy(isLoading = true)).woman)
        assertNull(guide.resolveSelection(roster.copy(message = "Permission denied" )).woman)
        assertNull(guide.resolveSelection(RosterState(isLoading = false)).woman)
    }
}
