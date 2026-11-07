package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import `in`.drarunbabu.drsharmila.jananisync.data.rbac.OrganizationAccessRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class OrganizationRequestTest {
    @Test fun activeAdminJoiningAnExistingOrganizationGetsSwitchInstructionsWithoutSubmitting() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val organizations = listOf("Org12345" to "Current clinic", "NewOrg01" to "New clinic").map { (id, name) ->
                OrganizationMember(id, organizationName = name, userId = "admin", role = "admin", status = "active", joinedAt = 0)
            }
            val current = UserMembershipState.ActiveMember("Org12345", role = "admin", name = "Admin",
                availableOrganizations = organizations)
            val repository = RequestRepository().apply { membershipState.value = current }
            val model = OrganizationAccessViewModel(repository)
            model.beginJoiningOrganization()
            model.setAccessCode("NewOrg01")
            model.prepareSecureAccess()
            assertNull(model.uiState.value.preparedKeyMaterial)
            assertFalse(model.uiState.value.isPreparingKeys)
            assertTrue(model.uiState.value.message.orEmpty().contains("New clinic"))
            assertTrue(model.uiState.value.message.orEmpty().contains("Switch organisation"))
            model.submitAccessRequest()
            assertEquals(0, repository.attempts)
            assertFalse(model.uiState.value.isSubmitting)
            assertEquals(current, model.uiState.value.membership)
            model.clearSensitiveState()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun rejectedAdditionalAdminRequestBecomesUiMessageAndPreservesCurrentMembership() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            // The target membership can become active after the cached duplicate check.
            val current = UserMembershipState.ActiveMember("Org12345", role = "admin", name = "Admin")
            val repository = RequestRepository().apply { membershipState.value = current }
            val model = OrganizationAccessViewModel(repository)
            model.beginJoiningOrganization()
            model.setAccessCode("NewOrg01")
            model.prepareSecureAccess()
            model.confirmRecoveryPhraseSaved(true)
            model.submitAccessRequest()
            assertTrue(model.uiState.value.isSubmitting)
            assertEquals(1, repository.attempts)
            val message = "You already belong to this organisation. Use Switch organisation to open it."
            repository.result.completeExceptionally(IllegalStateException(message))
            assertFalse(model.uiState.value.isSubmitting)
            assertEquals(message, model.uiState.value.message)
            assertEquals(current, model.uiState.value.membership)
            assertTrue(model.uiState.value.isRequestingAdditionalOrganization)
            assertEquals(repository.material, model.uiState.value.preparedKeyMaterial)
            assertEquals("test recovery phrase", model.uiState.value.recoveryPhrase)
            assertTrue(model.uiState.value.recoveryPhraseConfirmed)
            model.clearMessage()
            assertNull(model.uiState.value.message)
            model.clearSensitiveState()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun organizationSwitchClearsFingerprintAndRejectsLateVerificationFromPreviousContext() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply { membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "doctor") }
            val model = OrganizationAccessViewModel(repository)
            model.verifyOwnFingerprint("phrase")
            assertEquals("Org12345-fingerprint", model.uiState.value.ownFingerprint)
            repository.fingerprintWait = CompletableDeferred()
            model.verifyOwnFingerprint("phrase")
            repository.membershipState.value = UserMembershipState.ActiveMember("Other001", role = "doctor")
            assertEquals("", model.uiState.value.ownFingerprint)
            assertFalse(model.uiState.value.isVerifyingFingerprint)
            repository.membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "doctor")
            repository.fingerprintWait!!.complete(Unit)
            assertEquals("", model.uiState.value.ownFingerprint)
            model.clearSensitiveState()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun joiningReusesOnlyExplicitlySelectedAndVerifiedSourceWithoutChangingCurrentFingerprint() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply { membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "doctor") }
            val model = OrganizationAccessViewModel(repository)
            model.verifyOwnFingerprint("phrase")
            model.beginJoiningOrganization()
            model.setAccessCode("NewOrg01")
            model.prepareSecureAccessFromOrganization("Other001", "wrong")
            assertNull(model.uiState.value.preparedKeyMaterial)
            assertEquals("Wrong source phrase", model.uiState.value.message)
            model.prepareSecureAccessFromOrganization("Other001", "usable source phrase")
            assertEquals(listOf("NewOrg01/Other001", "NewOrg01/Other001"), repository.sourceSetupCalls)
            assertEquals("Other001", model.uiState.value.preparedKeySourceOrgId)
            assertEquals(repository.material, model.uiState.value.preparedKeyMaterial)
            assertNull(model.uiState.value.recoveryPhrase)
            assertEquals("Org12345-fingerprint", model.uiState.value.ownFingerprint)
            assertFalse(model.uiState.value.recoveryPhraseConfirmed)
            model.clearSensitiveState()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun roleChangesRequireMatchingAdminAndClinicalMembershipAndBlockDuplicateSaves() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply { membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "doctor") }
            val model = OrganizationAccessViewModel(repository)
            val member = OrganizationMember("Org12345", userId = "member", role = "doctor", status = "active", joinedAt = 0)
            model.updateMemberRole(member, "nurse")
            assertTrue(repository.roleCalls.isEmpty())
            repository.membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "admin")
            for ((target, role) in listOf(member to "admin", member to "doctor", member.copy(role = "admin") to "nurse",
                member.copy(status = "left") to "nurse", member.copy(orgId = "Other001") to "nurse")) model.updateMemberRole(target, role)
            assertTrue(repository.roleCalls.isEmpty())
            repository.maintenanceState.value = OrganizationMaintenance("Org12345", active = true)
            model.updateMemberRole(member, "nurse")
            assertTrue(repository.roleCalls.isEmpty())
            repository.maintenanceState.value = OrganizationMaintenance("Org12345")
            repository.roleWait = CompletableDeferred()
            model.updateMemberRole(member, "nurse")
            model.updateMemberRole(member, "nurse")
            assertEquals(listOf("Org12345/member/nurse"), repository.roleCalls)
            assertEquals("member", model.uiState.value.updatingRoleForUserId)
            repository.roleWait!!.completeExceptionally(IllegalStateException("Denied"))
            assertNull(model.uiState.value.updatingRoleForUserId)
            assertEquals("Denied", model.uiState.value.message)
            model.clearSensitiveState()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun replacementRequiresSavingTheNewPhraseAndClearsItOnlyAfterConfirmedSubmission() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply {
                membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "doctor")
                replacementSubmitWait = CompletableDeferred()
            }
            val model = OrganizationAccessViewModel(repository)
            model.prepareReplacementKey()
            assertEquals("new recovery phrase", model.uiState.value.replacementSetup?.recoveryPhrase)
            model.submitKeyReplacement()
            assertEquals(0, repository.replacementSubmitCalls)
            model.confirmReplacementPhraseSaved(true)
            model.submitKeyReplacement()
            model.submitKeyReplacement()
            assertEquals(1, repository.replacementSubmitCalls)
            assertNotNull(model.uiState.value.replacementSetup)
            repository.replacementSubmitWait!!.complete(Unit)
            assertNull(model.uiState.value.replacementSetup)
            assertFalse(model.uiState.value.replacementPhraseSaved)
            assertFalse(model.uiState.value.replacementBusy)
            assertEquals("Org12345", (model.uiState.value.membership as UserMembershipState.ActiveMember).orgId)
            model.clearSensitiveState()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun failedReplacementSubmissionRetainsTheSavedPhraseForRetry() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply {
                membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "nurse")
                replacementSubmitWait = CompletableDeferred()
            }
            val model = OrganizationAccessViewModel(repository)
            model.prepareReplacementKey()
            model.confirmReplacementPhraseSaved(true)
            model.submitKeyReplacement()
            repository.replacementSubmitWait!!.completeExceptionally(IllegalStateException("Offline"))
            assertNotNull(model.uiState.value.replacementSetup)
            assertTrue(model.uiState.value.replacementPhraseSaved)
            assertFalse(model.uiState.value.replacementBusy)
            assertEquals("Offline", model.uiState.value.message)
            model.clearSensitiveState()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun lateReplacementPreparationCannotExposeAPhraseAfterSwitchingOrganizations() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply {
                membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "doctor")
                replacementPrepareWait = CompletableDeferred()
            }
            val model = OrganizationAccessViewModel(repository)
            model.prepareReplacementKey()
            repository.membershipState.value = UserMembershipState.ActiveMember("Other001", role = "doctor")
            repository.membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "doctor")
            repository.replacementPrepareWait!!.complete(Unit)
            assertNull(model.uiState.value.replacementSetup)
            assertFalse(model.uiState.value.replacementBusy)
            model.clearSensitiveState()
        } finally { Dispatchers.resetMain() }
    }

    @Test fun cancellingAPendingAdditionalRequestRetainsTheActiveOrganizationAndBlocksDuplicateClicks() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val pending = AccessRequest("NewOrg01", "user", "User", requestedRole = "nurse", status = "pending", createdAt = 0)
            val repository = RequestRepository().apply {
                membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "doctor")
                ownPendingRequests.value = listOf(pending)
                cancelWait = CompletableDeferred()
            }
            val model = OrganizationAccessViewModel(repository)
            model.cancelPendingRequest("Unknown1")
            assertTrue(repository.cancelCalls.isEmpty())
            model.cancelPendingRequest(pending.orgId)
            model.cancelPendingRequest(pending.orgId)
            assertEquals(listOf(pending.orgId), repository.cancelCalls)
            assertEquals(pending.orgId, model.uiState.value.cancellingRequestForOrgId)
            repository.cancelWait!!.complete(Unit)
            assertNull(model.uiState.value.cancellingRequestForOrgId)
            assertTrue(model.uiState.value.ownPendingRequests.isEmpty())
            assertEquals("Org12345", (model.uiState.value.membership as UserMembershipState.ActiveMember).orgId)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun leavingRequiresAnActiveNonAdminAndPreservesAccessToOtherOrganizations() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply { membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "admin") }
            val model = OrganizationAccessViewModel(repository)
            model.leaveOrganization("Org12345")
            assertTrue(repository.leaveCalls.isEmpty())
            repository.membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "nurse")
            repository.afterLeave = UserMembershipState.ActiveMember("Other001", role = "doctor")
            repository.leaveWait = CompletableDeferred()
            model.leaveOrganization("Unknown1")
            assertTrue(repository.leaveCalls.isEmpty())
            model.leaveOrganization("Org12345")
            model.leaveOrganization("Org12345")
            assertEquals(listOf("Org12345"), repository.leaveCalls)
            repository.leaveWait!!.complete(Unit)
            assertEquals("Other001", (model.uiState.value.membership as UserMembershipState.ActiveMember).orgId)
            assertEquals("Org12345", model.uiState.value.lastLeftOrganizationId)
            assertNull(model.uiState.value.leavingOrganizationId)
            model.clearLeaveEvent()
            assertNull(model.uiState.value.lastLeftOrganizationId)
        } finally { Dispatchers.resetMain() }
    }
    @Test fun manualRotationRequiresAnUnlockedAdminAndCannotStartTwiceOrDuringMaintenance() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply {
                metadataExists = true
                membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "doctor")
                rotationWait = CompletableDeferred()
            }
            val model = OrganizationAccessViewModel(repository)
            model.unlockOrganizationKey("personal phrase")
            assertTrue(model.uiState.value.organizationKeyUnlocked, "The clinical member can unlock their own access")
            model.rotateOrganizationKey()
            assertTrue(repository.rotationCalls.isEmpty(), "A doctor cannot rotate the organisation key")
            repository.membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "admin")
            assertFalse(model.uiState.value.organizationKeyUnlocked, "Admin access must be unlocked separately")
            model.rotateOrganizationKey()
            assertTrue(repository.rotationCalls.isEmpty(), "An admin without unlocked access cannot rotate")
            model.unlockOrganizationKey("personal phrase")
            repository.maintenanceState.value = OrganizationMaintenance("Org12345", active = true)
            model.rotateOrganizationKey()
            assertTrue(repository.rotationCalls.isEmpty(), "Active maintenance blocks rotation")
            repository.maintenanceState.value = OrganizationMaintenance("Org12345")
            model.rotateOrganizationKey()
            model.rotateOrganizationKey()
            assertEquals(listOf("Org12345"), repository.rotationCalls)
            assertTrue(model.uiState.value.isRotatingOrganizationKey)
            repository.rotationWait!!.complete(Unit)
            assertFalse(model.uiState.value.isRotatingOrganizationKey)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun rememberedAdminAccessCanRotateWithoutReenteringThePhrase() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply {
                metadataExists = true
                rememberedAdminAccess = true
                membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "admin")
                rotationWait = CompletableDeferred()
            }
            val model = OrganizationAccessViewModel(repository)
            assertTrue(model.uiState.value.organizationKeyUnlocked)
            model.rotateOrganizationKey()
            model.rotateOrganizationKey()
            assertEquals(listOf("Org12345"), repository.rotationCalls)
            assertTrue(model.uiState.value.isRotatingOrganizationKey)
            repository.rotationWait!!.complete(Unit)
            assertFalse(model.uiState.value.isRotatingOrganizationKey)
            model.clearSensitiveState()
        } finally { Dispatchers.resetMain() }
    }
    @Test fun switchingAccountsInSameOrganizationResetsProfileAndRestartsAdminSetup() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository().apply { metadataExists = true }
            val model = OrganizationAccessViewModel(repository)
            model.startObserving("first-user")
            repository.membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "admin", name = "First User")
            assertEquals("First User", model.uiState.value.name)
            assertTrue(model.uiState.value.organizationKeyExists)
            val oldCallback = repository.metadataCallbacks.single()
            model.setAccessCode("Org12345")
            model.beginJoiningOrganization()
            model.clearSensitiveState()
            assertEquals(OrganizationAccessUiState(), model.uiState.value)
            assertEquals(UserMembershipState.Loading, repository.membershipState.value)

            model.startObserving("second-user")
            assertEquals("", model.uiState.value.name)
            assertFalse(model.uiState.value.isRequestingAdditionalOrganization)
            assertEquals("", model.uiState.value.accessCode)
            assertEquals(UserMembershipState.Loading, model.uiState.value.membership)
            repository.membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "admin", name = "Second User")
            assertEquals("Second User", model.uiState.value.name)
            assertEquals("second-user", model.uiState.value.userUid)
            assertEquals(2, repository.metadataCallbacks.size)
            assertTrue(model.uiState.value.organizationKeyMetadataLoaded)
            oldCallback(false)
            assertTrue(model.uiState.value.organizationKeyExists)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun accountSwitchWithoutExplicitLogoutAlsoClearsPreviousProfile() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = RequestRepository()
            val model = OrganizationAccessViewModel(repository)
            model.startObserving("first")
            repository.membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "nurse", name = "First")
            model.startObserving("second")
            assertEquals("", model.uiState.value.name)
            repository.membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "nurse", name = "Second")
            assertEquals("Second", model.uiState.value.name)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun logoutCancelsPendingKeyPreparationBeforeItCanRestoreSensitiveState() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val wait = CompletableDeferred<Unit>()
            val repository = RequestRepository().apply { keySetupWait = wait }
            val model = OrganizationAccessViewModel(repository)
            model.startObserving("first")
            model.prepareSecureAccess()
            assertTrue(model.uiState.value.isPreparingKeys)
            model.clearSensitiveState()
            model.startObserving("second")
            wait.complete(Unit)
            assertNull(model.uiState.value.preparedKeyMaterial)
            assertNull(model.uiState.value.recoveryPhrase)
            assertFalse(model.uiState.value.isPreparingKeys)
            assertEquals("second", model.uiState.value.userUid)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun checksExistingKeyGrantsAndKeepsFailuresDistinctFromMissingAccess() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val wait = CompletableDeferred<Unit>()
            val repository = RequestRepository().apply {
                metadataExists = true
                keyCheckWait = wait
                existingGrants += "granted"
                failedChecks += "unknown"
                membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "admin")
                organizationMembers.value = listOf("granted", "missing", "unknown").map {
                    OrganizationMember("Org12345", userId = it, role = "nurse", status = "active", joinedAt = 0)
                }
            }
            val model = OrganizationAccessViewModel(repository)
            assertTrue(model.uiState.value.isCheckingMemberKeyGrants)
            assertTrue(model.uiState.value.membersWithCheckedKeyGrant.isEmpty())
            wait.complete(Unit)
            assertEquals(setOf("granted"), model.uiState.value.membersWithCurrentKey)
            assertEquals(setOf("granted", "missing"), model.uiState.value.membersWithCheckedKeyGrant)
            assertFalse(model.uiState.value.isCheckingMemberKeyGrants)
            repository.failedChecks.clear()
            repository.existingGrants += "unknown"
            model.refreshMemberKeyGrants()
            assertEquals(setOf("granted", "unknown"), model.uiState.value.membersWithCurrentKey)
            assertEquals(setOf("granted", "missing", "unknown"), model.uiState.value.membersWithCheckedKeyGrant)
            model.clearSensitiveState()
            assertTrue(model.uiState.value.membersWithCurrentKey.isEmpty())
            assertTrue(model.uiState.value.membersWithCheckedKeyGrant.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun approvalNeedsUnlockedAdminAndGrantsAccessWithRecoveryOnFailure() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            for (failGrant in listOf(false, true)) {
                val repository = RequestRepository().apply {
                    membershipState.value = UserMembershipState.ActiveMember("Org12345", role = "admin")
                    grantFails = failGrant
                }
                val model = OrganizationAccessViewModel(repository)
                val request = AccessRequest("Org12345", "new-user", "Nurse", requestedRole = "nurse", status = "pending", createdAt = 0)
                model.approve(request, 20)
                assertTrue(repository.approvalCalls.isEmpty())
                model.unlockOrganizationKey("personal phrase")
                model.approve(request, 20)
                assertEquals(listOf("approve", "grant"), repository.approvalCalls)
                assertNull(model.uiState.value.approvingUserId)
                if (failGrant) {
                    assertTrue(model.uiState.value.message.orEmpty().contains("Complete access setup"))
                    assertFalse("new-user" in model.uiState.value.membersWithCurrentKey)
                } else assertTrue("new-user" in model.uiState.value.membersWithCurrentKey)
            }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun rejectedRequestRetainsPhraseAndFormForRetry() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RequestRepository()
        val model = OrganizationAccessViewModel(repository)
        try {
            model.setName("Doctor")
            model.setAccessCode("Org12345")
            model.prepareSecureAccess()
            model.confirmRecoveryPhraseSaved(true)
            model.submitAccessRequest()
            model.submitAccessRequest()
            assertEquals(1, repository.attempts)
            assertTrue(model.uiState.value.isSubmitting)
            assertEquals(UserMembershipState.Unassigned, model.uiState.value.membership)
            repository.result.completeExceptionally(IllegalStateException("Request denied"))
            assertFalse(model.uiState.value.isSubmitting)
            assertEquals("test recovery phrase", model.uiState.value.recoveryPhrase)
            assertEquals(repository.material, model.uiState.value.preparedKeyMaterial)
            assertTrue(model.uiState.value.recoveryPhraseConfirmed)
            assertEquals("Request denied", model.uiState.value.message)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun acceptedRequestShowsPendingAndClearsPhrase() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val repository = RequestRepository()
        val model = OrganizationAccessViewModel(repository)
        try {
            model.setName("Doctor")
            model.setAccessCode("Org12345")
            model.prepareSecureAccess()
            model.confirmRecoveryPhraseSaved(true)
            model.submitAccessRequest()
            assertNotNull(model.uiState.value.recoveryPhrase)
            repository.result.complete(Unit)
            assertEquals(UserMembershipState.PendingApproval("Org12345", "nurse"), model.uiState.value.membership)
            assertNull(model.uiState.value.recoveryPhrase)
            assertNull(model.uiState.value.preparedKeyMaterial)
            assertFalse(model.uiState.value.isSubmitting)
            assertEquals("Access request submitted. Waiting for administrator approval.", model.uiState.value.message)
        } finally { Dispatchers.resetMain() }
    }
}

private class RequestRepository : OrganizationAccessRepository {
    var fingerprintWait: CompletableDeferred<Unit>? = null
    val sourceSetupCalls = mutableListOf<String>()
    override suspend fun ownPublicKeyFingerprint(orgId: String, recoveryPhrase: String): String {
        fingerprintWait?.await()
        return "$orgId-fingerprint"
    }
    override suspend fun createMemberKeySetupFromOrganization(targetOrgId: String, sourceOrgId: String, recoveryPhrase: String): MemberKeySetup {
        sourceSetupCalls += "$targetOrgId/$sourceOrgId"
        require(recoveryPhrase == "usable source phrase") { "Wrong source phrase" }
        return MemberKeySetup(null, material, reusingExistingKey = true)
    }
    val roleCalls = mutableListOf<String>()
    var roleWait: CompletableDeferred<Unit>? = null
    override suspend fun adminUpdateMemberRole(orgId: String, targetUserId: String, role: String) {
        roleCalls += "$orgId/$targetUserId/$role"
        roleWait?.await()
    }
    var replacementPrepareWait: CompletableDeferred<Unit>? = null
    var replacementSubmitWait: CompletableDeferred<Unit>? = null
    var replacementSubmitCalls = 0
    override suspend fun prepareReplacementKey(): MemberKeySetup {
        replacementPrepareWait?.await()
        return MemberKeySetup("new recovery phrase", material)
    }
    override suspend fun requestKeyReplacement(orgId: String, material: MemberKeyMaterial) {
        replacementSubmitCalls++
        replacementSubmitWait?.await()
    }
    override val ownPendingRequests = MutableStateFlow<List<AccessRequest>>(emptyList())
    val cancelCalls = mutableListOf<String>()
    val leaveCalls = mutableListOf<String>()
    var cancelWait: CompletableDeferred<Unit>? = null
    var leaveWait: CompletableDeferred<Unit>? = null
    var afterLeave: UserMembershipState = UserMembershipState.Unassigned
    override suspend fun cancelPendingRequest(orgId: String) {
        cancelCalls += orgId
        cancelWait?.await()
        ownPendingRequests.value = ownPendingRequests.value.filterNot { it.orgId == orgId }
        if ((membershipState.value as? UserMembershipState.PendingApproval)?.orgId == orgId) membershipState.value = UserMembershipState.Unassigned
    }
    override suspend fun leaveOrganization(orgId: String) {
        leaveCalls += orgId
        leaveWait?.await()
        membershipState.value = afterLeave
    }
    val rotationCalls = mutableListOf<String>()
    var rotationWait: CompletableDeferred<Unit>? = null
    override val maintenanceState = MutableStateFlow(OrganizationMaintenance())
    override suspend fun adminRotateOrganizationKey(orgId: String) {
        rotationCalls += orgId
        rotationWait?.await()
    }
    val material = MemberKeyMaterial("public", "fingerprint", "ciphertext", "iv", "salt")
    val result = CompletableDeferred<Unit>()
    val approvalCalls = mutableListOf<String>()
    var metadataExists = false
    var keyCheckWait: CompletableDeferred<Unit>? = null
    val existingGrants = mutableSetOf<String>()
    val failedChecks = mutableSetOf<String>()
    var grantFails = false
    var attempts = 0
    var keySetupWait: CompletableDeferred<Unit>? = null
    val metadataCallbacks = mutableListOf<(Boolean) -> Unit>()
    override val membershipState = MutableStateFlow<UserMembershipState>(UserMembershipState.Unassigned)
    override val pendingRequests = MutableStateFlow<List<AccessRequest>>(emptyList())
    override val organizationMembers = MutableStateFlow<List<OrganizationMember>>(emptyList())
    override fun observeMembershipState(userUid: String) = membershipState
    override fun selectOrganization(orgId: String?) = Unit
    override suspend fun createMemberKeySetup(): MemberKeySetup {
        // Firebase promises settle even after the initiating session has been cancelled.
        withContext(NonCancellable) { keySetupWait?.await() }
        return MemberKeySetup("test recovery phrase", material)
    }
    override suspend fun submitAccessRequest(orgId: String, requestedRole: String, name: String, keyMaterial: MemberKeyMaterial) {
        attempts++
        result.await()
        membershipState.value = UserMembershipState.PendingApproval(orgId, requestedRole)
    }
    override suspend fun adminApproveRequest(orgId: String, targetUserId: String, approvedRole: String, name: String, accessLevel: Int) { approvalCalls += "approve" }
    override fun observeOrganizationKeyExists(orgId: String, onChanged: (Boolean) -> Unit, onError: (String) -> Unit): () -> Unit {
        metadataCallbacks.add(onChanged)
        onChanged(metadataExists)
        return {}
    }
    override fun initializeOrganizationEncryption(orgId: String, keyMaterial: MemberKeyMaterial, onComplete: (String?) -> Unit) = Unit
    var rememberedAdminAccess = false
    override suspend fun unlockOrganizationEncryption(orgId: String, recoveryPhrase: String, rememberOnDevice: Boolean) {
        if (recoveryPhrase.isBlank()) {
            require(rememberOnDevice && rememberedAdminAccess) { "No remembered admin access on this device." }
        }
    }
    override suspend fun adminGrantOrganizationKey(orgId: String, targetUserId: String) {
        approvalCalls += "grant"
        if (grantFails) error("Grant failed")
        existingGrants += targetUserId
    }
    override suspend fun memberHasCurrentOrganizationKey(orgId: String, targetUserId: String): Boolean {
        keyCheckWait?.await()
        if (targetUserId in failedChecks) error("Network unavailable")
        return targetUserId in existingGrants
    }
    override fun observePendingRequests(orgId: String) = pendingRequests
    override fun observeOrganizationMembers(orgId: String) = organizationMembers
    override suspend fun adminUpdateMemberAccessLevel(orgId: String, targetUserId: String, accessLevel: Int) = Unit
    override suspend fun adminSetMemberActive(orgId: String, targetUserId: String, active: Boolean) = Unit
    override fun close() {
        ownPendingRequests.value = emptyList()
        membershipState.value = UserMembershipState.Loading
        pendingRequests.value = emptyList()
        organizationMembers.value = emptyList()
    }
}
