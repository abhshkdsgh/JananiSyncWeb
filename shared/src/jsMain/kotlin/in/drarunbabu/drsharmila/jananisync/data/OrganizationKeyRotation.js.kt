package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.FirebaseService
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.serialization.json.Json

/** Single-admin, client-only rotation. Firebase receives only ciphertext and wrapped keys. */
internal val OrganizationKeyRotation = OrganizationKeyRotationProtocol()

internal class OrganizationKeyRotationProtocol(
    private val storage: RotationStorage = OrganizationRotationStore,
    private val checkSession: (String) -> Unit = { adminId ->
        check(FirebaseService.currentUser?.uid == adminId) { "Sign in and unlock your account to resume maintenance." }
    }
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val maxRecipients = 500

    private suspend fun requireSession(adminId: String) {
        currentCoroutineContext().ensureActive()
        checkSession(adminId)
    }

    suspend fun rememberRecipient(orgId: String, adminId: String, userId: String, jwk: String,
        fingerprint: String, controlKey: String) {
        require(OrganizationKeyCrypto.fingerprintPublicKey(jwk) == fingerprint) { "The recipient's key does not match its computed fingerprint." }
        val pin = VerifiedRotationRecipient(orgId, adminId, userId, fingerprint, jwk)
        val (ciphertext, iv) = SyntheticCrypto.encrypt(json.encodeToString(VerifiedRotationRecipient.serializer(), pin), controlKey)
        requireSession(adminId)
        storage.saveRecipient(pin, ciphertext, iv)
    }

    suspend fun verifiedRecipient(orgId: String, adminId: String, userId: String, controlKey: String): VerifiedRotationRecipient {
        val profile = storage.read("organizations/$orgId/keyProfiles/$userId")
            ?: error("The member's public key profile is missing.")
        val publicKeyJwk = profile.publicKeyJwk as String
        val currentFingerprint = OrganizationKeyCrypto.fingerprintPublicKey(publicKeyJwk)
        val profileFingerprint = (profile.publicKeyFingerprint as String)
            .replace('+', '-').replace('/', '_').trimEnd('=')
        require(profileFingerprint == currentFingerprint) { "The member's public key profile is invalid." }
        val stored = storage.read("organizations/$orgId/rotationRecipients/$userId")
        if (stored == null) {
            rememberRecipient(orgId, adminId, userId, publicKeyJwk, currentFingerprint, controlKey)
            return VerifiedRotationRecipient(orgId, adminId, userId, currentFingerprint, publicKeyJwk)
        }
        val pin = json.decodeFromString(VerifiedRotationRecipient.serializer(),
            SyntheticCrypto.decrypt(stored.ciphertext as String, stored.iv as String, controlKey))
        require(pin.orgId == orgId && pin.adminId == adminId && pin.userId == userId &&
            OrganizationKeyCrypto.fingerprintPublicKey(pin.publicKeyJwk) == pin.fingerprint) {
            "A registered recipient key is invalid. Contact the organisation administrator."
        }
        require(currentFingerprint == pin.fingerprint) {
            "A member key changed since it was registered. Automatic updates are paused to protect the organisation key."
        }
        return pin
    }

    private suspend fun recipients(orgId: String, adminId: String, removedId: String, controlKey: String): List<VerifiedRotationRecipient> {
        val members = storage.members(orgId).filter { it.status == "active" && it.userId != removedId }.sortedBy { it.userId }
        require(members.count { it.role == "admin" } == 1 && members.single { it.role == "admin" }.userId == adminId) {
            "Automatic rotation requires exactly one active organisation administrator. Ask the project owner to check the organisation setup."
        }
        require(members.size in 1..maxRecipients) { "Automatic rotation supports up to $maxRecipients remaining members." }
        return members.map { member -> verifiedRecipient(orgId, adminId, member.userId, controlKey) }
    }

    suspend fun begin(orgId: String, adminId: String, removedId: String, ring: OrganizationKeyRing, controlKey: String) {
        requireSession(adminId)
        val pins = recipients(orgId, adminId, removedId, controlKey)
        val journal = OrganizationRotationJournal(orgId, SyntheticCrypto.generateOpaqueId(), adminId, removedId,
            ring.currentVersion, ring.keys.getValue(ring.currentVersion), OrganizationKeyCrypto.generateOrganizationDataKey(), pins)
        val (ciphertext, iv) = encryptJournal(journal, controlKey)
        requireSession(adminId)
        storage.begin(journal, ciphertext, iv)
    }

    private suspend fun encryptJournal(journal: OrganizationRotationJournal, controlKey: String): Pair<String, String> {
        val encrypted = SyntheticCrypto.encrypt(json.encodeToString(OrganizationRotationJournal.serializer(), journal), controlKey)
        require(encrypted.first.length <= 900_000) { "The encrypted recipient roster exceeds the supported size." }
        return encrypted
    }

    /** Resumes from authenticated server progress; no recovery phrase or plaintext key is persisted. */
    suspend fun resume(orgId: String, adminId: String, ring: OrganizationKeyRing, controlKey: String): OrganizationKeyRing? {
        requireSession(adminId)
        val root = "organizations/$orgId"
        val maintenance = storage.read("$root/keyMaintenance/current")
        if (maintenance?.active != true) return null
        require(maintenance.adminId == adminId) { "The administrator who started this update must unlock their account to resume it." }
        val operationId = maintenance.rotationId as String
        if (maintenance.awaitingAdmin == true) {
            val pins = recipients(orgId, adminId, "", controlKey)
            val pending = OrganizationRotationJournal(orgId, operationId, adminId, "", ring.currentVersion,
                ring.keys.getValue(ring.currentVersion), OrganizationKeyCrypto.generateOrganizationDataKey(), pins)
            val (ciphertext, iv) = encryptJournal(pending, controlKey)
            requireSession(adminId)
            storage.adoptDeparture(pending, ciphertext, iv)
        }
        var operation = storage.read("$root/keyRotations/$operationId") ?: error("The access-update journal is missing.")
        var journal = json.decodeFromString(OrganizationRotationJournal.serializer(),
            SyntheticCrypto.decrypt(operation.ciphertext as String, operation.iv as String, controlKey))
        require(journal.orgId == orgId && journal.adminId == adminId && journal.operationId == operationId &&
            journal.baseVersion == ring.currentVersion && journal.previousKey == ring.keys[ring.currentVersion] &&
            operation.baseVersion == journal.baseVersion && operation.removedUserId == journal.removedUserId) { "The access-update journal does not match this organisation or key version." }
        if (operation.phase == "preparing") {
            // The membership freeze is now server-confirmed. Reconcile a fresh, complete roster.
            journal = journal.copy(recipients = recipients(orgId, adminId, journal.removedUserId, controlKey))
            val (ciphertext, iv) = encryptJournal(journal, controlKey)
            requireSession(adminId)
            storage.prepare(journal, ciphertext, iv)
            operation = storage.read("$root/keyRotations/$operationId")
        }
        val ids = journal.recipients.map { it.userId }
        require(ids.size in 1..maxRecipients && ids.distinct().size == ids.size && journal.removedUserId !in ids && adminId in ids &&
            ids == (operation.recipients as Array<String>).toList() && operation.recipientCount == ids.size) { "The frozen recipient roster is invalid." }
        check(operation.phase == "wrapping") { "This access update is no longer resumable. Reopen the organisation." }
        for (index in (operation.nextIndex as Int) until ids.size) {
            requireSession(adminId)
            val pin = journal.recipients[index]
            val wrapped = OrganizationKeyCrypto.wrapOrganizationDataKey(journal.nextKey, pin.publicKeyJwk, pin.fingerprint)
            val proof = RotationGrantProof(orgId, operationId, pin.userId, journal.baseVersion + 1, pin.fingerprint, wrapped)
            val (ciphertext, iv) = SyntheticCrypto.encrypt(json.encodeToString(RotationGrantProof.serializer(), proof), controlKey)
            requireSession(adminId)
            storage.stage(journal, index, wrapped, ciphertext, iv)
        }
        // All grants must be server-confirmed and authenticated before the single activation commit.
        val activeIds = storage.members(orgId).filter { it.status == "active" }.map { it.userId }.sorted()
        require(activeIds == ids.sorted()) { "Organisation membership changed. Maintenance remains active until the roster is reconciled." }
        for ((index, pin) in journal.recipients.withIndex()) {
            val grant = storage.read("$root/keyRotations/$operationId/grants/${pin.userId}") ?: error("An access grant is missing. Retry maintenance.")
            val proof = json.decodeFromString(RotationGrantProof.serializer(),
                SyntheticCrypto.decrypt(grant.proofCiphertext as String, grant.proofIv as String, controlKey))
            require(proof == RotationGrantProof(orgId, operationId, pin.userId, journal.baseVersion + 1, pin.fingerprint, grant.wrappedKey as String) &&
                grant.userId == pin.userId && grant.keyVersion == journal.baseVersion + 1 && grant.index == index) { "An access grant failed verification. Maintenance remains active." }
        }
        val (history, historyIv) = SyntheticCrypto.encrypt(journal.previousKey, journal.nextKey)
        requireSession(adminId)
        storage.finish(journal, history, historyIv)
        return OrganizationKeyRing(journal.baseVersion + 1, ring.keys + (journal.baseVersion + 1 to journal.nextKey))
    }
}
