package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlinx.coroutines.test.runTest
import kotlin.js.JSON
import kotlin.test.*

class OrganizationRotationTest {
    @Test fun administratorCanAdoptAnOfflineDepartureAndRotateOnlyForRemainingMembers() = runTest {
        val phrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val admin = OrganizationKeyCrypto.createMemberKeyMaterial("admin", phrase)
        val control = OrganizationKeyCrypto.adminControlKey("Rotate01", "admin", admin, phrase)
        val store = MemoryRotationStore(admin, admin)
        val protocol = OrganizationKeyRotationProtocol(store) {}
        for (id in listOf("admin", "member", "member2")) protocol.rememberRecipient("Rotate01", "admin", id,
            admin.publicKeyJwk, admin.publicKeyFingerprint, control)
        store.queueDeparture("leaving")
        assertNull(store.operation)
        val ring = OrganizationKeyRing(1, mapOf(1 to SyntheticCrypto.generateKey()))
        val result = protocol.resume("Rotate01", "admin", ring, control)!!
        assertEquals(2, result.currentVersion)
        assertEquals(setOf("admin", "member", "member2"), store.deliveries.keys)
        assertEquals("left", store.members("Rotate01").single { it.userId == "leaving" }.status)
        assertTrue(store.activated)
    }
    @Test fun manualRotationRetainsEveryActiveMemberAndCanResume() = runTest {
        val phrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val admin = OrganizationKeyCrypto.createMemberKeyMaterial("admin", phrase)
        val control = OrganizationKeyCrypto.adminControlKey("Rotate01", "admin", admin, phrase)
        val store = MemoryRotationStore(admin, admin)
        var signedIn = true
        val protocol = OrganizationKeyRotationProtocol(store) { check(signedIn) }
        for (id in listOf("admin", "leaving", "member", "member2")) {
            protocol.rememberRecipient("Rotate01", "admin", id, admin.publicKeyJwk, admin.publicKeyFingerprint, control)
        }
        val ring = OrganizationKeyRing(1, mapOf(1 to SyntheticCrypto.generateKey()))
        protocol.begin("Rotate01", "admin", "", ring, control)
        assertTrue(store.members("Rotate01").all { it.status == "active" })
        store.afterStage = { signedIn = false }
        assertFails { protocol.resume("Rotate01", "admin", ring, control) }
        store.afterStage = {}
        val recovered = OrganizationKeyCrypto.adminControlKey("Rotate01", "admin", admin, phrase)
        val result = OrganizationKeyRotationProtocol(store) {}.resume("Rotate01", "admin", ring, recovered)!!
        assertEquals(2, result.currentVersion)
        assertTrue(store.activated)
        assertTrue(store.members("Rotate01").all { it.status == "active" })
        assertEquals(setOf("admin", "leaving", "member", "member2"), store.deliveries.keys)
        assertTrue(store.deliveries.values.all { it == 1 })
    }
    @Test fun interruptedDeliveryResumesFromEncryptedProgressAfterAdminUnlock() = runTest {
        val phrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val admin = OrganizationKeyCrypto.createMemberKeyMaterial("admin", phrase)
        val memberPhrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val member = OrganizationKeyCrypto.createMemberKeyMaterial("member", memberPhrase)
        val control = OrganizationKeyCrypto.adminControlKey("Rotate01", "admin", admin, phrase)
        val store = MemoryRotationStore(admin, member)
        var authenticated = true
        val first = OrganizationKeyRotationProtocol(store) { check(authenticated) { "Signed out" } }
        for ((id, key) in listOf("admin" to admin, "member" to member, "member2" to member)) {
            first.rememberRecipient("Rotate01", "admin", id, key.publicKeyJwk, key.publicKeyFingerprint, control)
        }
        val previous = SyntheticCrypto.generateKey()
        val ring = OrganizationKeyRing(1, mapOf(1 to previous))
        first.begin("Rotate01", "admin", "leaving", ring, control)
        store.afterStage = { authenticated = false }
        assertFails { first.resume("Rotate01", "admin", ring, control) }
        assertEquals(1, store.operation.nextIndex as Int)
        assertTrue(store.maintenance.active as Boolean)
        assertFalse(store.activated)
        assertFails { SyntheticCrypto.decrypt(store.operation.ciphertext as String, store.operation.iv as String, previous) }

        // A new client instance retains no secrets from the first session.
        val unlockedControl = OrganizationKeyCrypto.adminControlKey("Rotate01", "admin", admin, phrase)
        assertEquals(control, unlockedControl)
        store.afterStage = {}
        val resumed = OrganizationKeyRotationProtocol(store) {}
        val result = resumed.resume("Rotate01", "admin", ring, unlockedControl)!!
        assertTrue(store.activated)
        assertFalse(store.maintenance.active as Boolean)
        assertEquals(2, result.currentVersion)
        assertEquals(mapOf("admin" to 1, "member" to 1, "member2" to 1), store.deliveries)
        assertFalse(store.deliveries.containsKey("leaving"))
        val privateKey = OrganizationKeyCrypto.unlockMemberPrivateKey("member", member, memberPhrase)
        val grant = store.documents.entries.single { it.key.endsWith("/grants/member") }.value
        val next = OrganizationKeyCrypto.unwrapOrganizationDataKey(grant.wrappedKey as String, privateKey)
        assertEquals(result.keys[2], next)
        assertEquals(previous, SyntheticCrypto.decrypt(store.historyCiphertext, store.historyIv, next))
        assertNotEquals(control, OrganizationKeyCrypto.adminControlKey("Other001", "admin", admin, phrase))
    }

    @Test fun missingRecipientPinsAreAutomaticallyRegisteredAndSubstitutedGrantCannotActivateRotation() = runTest {
        val phrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val admin = OrganizationKeyCrypto.createMemberKeyMaterial("admin", phrase)
        val control = OrganizationKeyCrypto.adminControlKey("Rotate01", "admin", admin, phrase)
        val store = MemoryRotationStore(admin, admin)
        val protocol = OrganizationKeyRotationProtocol(store) {}
        val ring = OrganizationKeyRing(1, mapOf(1 to SyntheticCrypto.generateKey()))
        protocol.begin("Rotate01", "admin", "leaving", ring, control)
        assertNotNull(store.documents["organizations/Rotate01/rotationRecipients/admin"])
        assertNotNull(store.documents["organizations/Rotate01/rotationRecipients/member"])
        assertNotNull(store.documents["organizations/Rotate01/rotationRecipients/member2"])
        store.afterStage = {
            store.documents.entries.firstOrNull { it.key.endsWith("/grants/member") }?.value?.wrappedKey = "AAAA"
        }
        assertFails { protocol.resume("Rotate01", "admin", ring, control) }
        assertFalse(store.activated)
        assertTrue(store.maintenance.active as Boolean)
    }

    @Test fun rotationRejectsChangedRecipientUntilReplacementIsExplicitlyRegistered() = runTest {
        val phrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val admin = OrganizationKeyCrypto.createMemberKeyMaterial("admin", phrase)
        val originalMember = OrganizationKeyCrypto.createMemberKeyMaterial("member", OrganizationKeyCrypto.generateRecoveryPhrase())
        val replacementPhrase = OrganizationKeyCrypto.generateRecoveryPhrase()
        val replacementMember = OrganizationKeyCrypto.createMemberKeyMaterial("member", replacementPhrase)
        val control = OrganizationKeyCrypto.adminControlKey("Rotate01", "admin", admin, phrase)
        val store = MemoryRotationStore(admin, originalMember)
        val protocol = OrganizationKeyRotationProtocol(store) {}
        protocol.verifiedRecipient("Rotate01", "admin", "member", control)

        val changedProfile = store.documents.getValue("organizations/Rotate01/keyProfiles/member")
        changedProfile.publicKeyJwk = replacementMember.publicKeyJwk
        changedProfile.publicKeyFingerprint = replacementMember.publicKeyFingerprint

        assertFails { protocol.verifiedRecipient("Rotate01", "admin", "member", control) }
        // Approval replaces the encrypted recipient pin along with the public profile.
        protocol.rememberRecipient("Rotate01", "admin", "member", replacementMember.publicKeyJwk, replacementMember.publicKeyFingerprint, control)
        assertEquals(replacementMember.publicKeyFingerprint, protocol.verifiedRecipient("Rotate01", "admin", "member", control).fingerprint)
        val ring = OrganizationKeyRing(1, mapOf(1 to SyntheticCrypto.generateKey()))
        protocol.begin("Rotate01", "admin", "leaving", ring, control)
        val rotated = protocol.resume("Rotate01", "admin", ring, control)!!
        val grant = store.documents.entries.single { it.key.endsWith("/grants/member") }.value
        val newPrivateKey = OrganizationKeyCrypto.unlockMemberPrivateKey("member", replacementMember, replacementPhrase)
        assertEquals(rotated.keys[2], OrganizationKeyCrypto.unwrapOrganizationDataKey(grant.wrappedKey as String, newPrivateKey))
    }
}

private class MemoryRotationStore(admin: MemberKeyMaterial, member: MemberKeyMaterial) : RotationStorage {
    val documents = mutableMapOf<String, dynamic>()
    private val roster = mutableListOf(
        OrganizationMember("Rotate01", userId = "admin", role = "admin", status = "active", joinedAt = 0),
        OrganizationMember("Rotate01", userId = "leaving", role = "nurse", status = "active", joinedAt = 0),
        OrganizationMember("Rotate01", userId = "member", role = "doctor", status = "active", joinedAt = 0),
        OrganizationMember("Rotate01", userId = "member2", role = "nurse", status = "active", joinedAt = 0))
    var operation: dynamic = null
    var maintenance: dynamic = null
    var activated = false
    val deliveries = mutableMapOf<String, Int>()
    var afterStage: () -> Unit = {}
    var historyCiphertext = ""
    var historyIv = ""
    fun queueDeparture(uid: String) {
        val index = roster.indexOfFirst { it.userId == uid }
        roster[index] = roster[index].copy(status = "left")
        maintenance = js("({active: true, awaitingAdmin: true, adminId: 'admin', completed: 0, total: 0})")
        maintenance.rotationId = SyntheticCrypto.generateOpaqueId()
        documents["organizations/Rotate01/keyMaintenance/current"] = maintenance
    }
    init {
        for ((id, key) in listOf("admin" to admin, "member" to member, "member2" to member, "leaving" to member)) {
            val profile = js("({})")
            profile.publicKeyJwk = key.publicKeyJwk
            profile.publicKeyFingerprint = key.publicKeyFingerprint
            documents["organizations/Rotate01/keyProfiles/$id"] = profile
        }
    }
    override suspend fun read(path: String): dynamic {
        val data = documents[path] ?: return null
        return JSON.parse<dynamic>(JSON.stringify(data))
    }
    override suspend fun members(orgId: String) = roster.toList()
    override suspend fun saveRecipient(pin: VerifiedRotationRecipient, ciphertext: String, iv: String) {
        val data = js("({})"); data.ciphertext = ciphertext; data.iv = iv
        documents["organizations/${pin.orgId}/rotationRecipients/${pin.userId}"] = data
    }
    override suspend fun begin(journal: OrganizationRotationJournal, ciphertext: String, iv: String) {
        operation = js("({})")
        operation.ciphertext = ciphertext; operation.iv = iv; operation.baseVersion = journal.baseVersion
        operation.removedUserId = journal.removedUserId; operation.phase = "preparing"; operation.nextIndex = 0
        maintenance = js("({active: true})"); maintenance.adminId = journal.adminId; maintenance.rotationId = journal.operationId
        documents["organizations/${journal.orgId}/keyMaintenance/current"] = maintenance
        documents["organizations/${journal.orgId}/keyRotations/${journal.operationId}"] = operation
        if (journal.removedUserId.isNotEmpty()) {
            val index = roster.indexOfFirst { it.userId == journal.removedUserId }
            roster[index] = roster[index].copy(status = "inactive")
        }
    }
    override suspend fun adoptDeparture(journal: OrganizationRotationJournal, ciphertext: String, iv: String) {
        check(maintenance.awaitingAdmin == true && maintenance.rotationId == journal.operationId)
        begin(journal, ciphertext, iv)
    }
    override suspend fun prepare(journal: OrganizationRotationJournal, ciphertext: String, iv: String) {
        operation.ciphertext = ciphertext; operation.iv = iv; operation.phase = "wrapping"
        operation.recipients = journal.recipients.map { it.userId }.toTypedArray(); operation.recipientCount = journal.recipients.size
    }
    override suspend fun stage(journal: OrganizationRotationJournal, index: Int, wrappedKey: String, proofCiphertext: String, proofIv: String) {
        val id = journal.recipients[index].userId
        val data = js("({})"); data.userId = id; data.keyVersion = journal.baseVersion + 1; data.index = index
        data.wrappedKey = wrappedKey; data.proofCiphertext = proofCiphertext; data.proofIv = proofIv
        documents["organizations/${journal.orgId}/keyRotations/${journal.operationId}/grants/$id"] = data
        operation.nextIndex = index + 1
        deliveries[id] = deliveries.getOrElse(id) { 0 } + 1
        afterStage()
    }
    override suspend fun finish(journal: OrganizationRotationJournal, ciphertext: String, iv: String) {
        activated = true; maintenance.active = false; operation.phase = "completed"
        historyCiphertext = ciphertext; historyIv = iv
    }
}
