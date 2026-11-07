package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.*
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.promise
import kotlinx.serialization.json.Json

/** Online, organization-scoped replacement. No phrase or plaintext private key leaves the browser. */
internal object MemberKeyReplacementStore {
    private val db get() = FirebaseService.rotationFirestore
    private fun ref(path: String) = doc(db, path)
    private fun model(orgId: String, data: dynamic) = MemberKeyReplacementRequest(orgId,
        data.userId as String, data.requestId as String, data.profile.publicKeyFingerprint as String, data.status as String)

    fun observe(orgId: String, uid: String, asAdmin: Boolean, onData: (MemberKeyReplacementState) -> Unit): () -> Unit {
        val options = js("({includeMetadataChanges: true})")
        val onError: (FirebaseError) -> Unit = { onData(MemberKeyReplacementState(orgId,
            error = "Could not check recovery key requests. Reconnect and retry.")) }
        return if (asAdmin) onSnapshot(query(collection(db, "organizations/$orgId/keyReplacementRequests"),
            where("status", "==", "pending")), options, { snapshot ->
            if (!snapshot.metadata.hasPendingWrites) onData(MemberKeyReplacementState(orgId,
                snapshot.docs.map { model(orgId, it.data()) }, loaded = true))
        }, onError) else onSnapshot(ref("organizations/$orgId/keyReplacementRequests/$uid"), options, { snapshot ->
            if (!snapshot.metadata.hasPendingWrites) onData(MemberKeyReplacementState(orgId,
                if (snapshot.exists()) listOf(model(orgId, snapshot.data())) else emptyList(), loaded = true))
        }, onError)
    }

    private suspend fun transaction(uid: String, action: suspend (FirestoreTransaction) -> Unit) {
        val context = currentCoroutineContext()
        runTransaction(db) { tx -> CoroutineScope(context).promise {
            check(FirebaseService.currentUser?.uid == uid) { "Your sign-in changed. Sign in again." }
            action(tx)
        } }.awaitFirebaseUnit()
    }
    private suspend fun FirestoreTransaction.data(path: String): dynamic {
        val snapshot = get(ref(path)).awaitFirebase()
        return if (snapshot.exists()) snapshot.data() else null
    }

    suspend fun submit(orgId: String, uid: String, material: MemberKeyMaterial) {
        val requestId = SyntheticCrypto.generateOpaqueId()
        transaction(uid) { tx ->
            val root = "organizations/$orgId"
            val member = tx.data("$root/members/$uid")
            val old = tx.data("$root/keyProfiles/$uid")
            val previous = tx.data("$root/keyReplacementRequests/$uid")
            check(member?.status == "active" && member.role in arrayOf("doctor", "nurse")) { "Only active doctors and nurses can request key replacement." }
            check(previous?.status != "pending") { "A replacement request is already awaiting administrator approval." }
            check(old != null && old.publicKeyFingerprint != material.publicKeyFingerprint) { "Prepare a new recovery key first." }
            val profile = js("({})")
            profile.userId = uid; profile.publicKeyJwk = material.publicKeyJwk
            profile.publicKeyFingerprint = material.publicKeyFingerprint; profile.keyAlgorithm = material.keyAlgorithm
            profile.createdAt = serverTimestamp()
            val backup = js("({})")
            backup.userId = uid; backup.ciphertext = material.privateKeyBackupCiphertext
            backup.iv = material.privateKeyBackupIv; backup.salt = material.privateKeyBackupSalt
            backup.kdf = material.privateKeyBackupKdf; backup.kdfIterations = material.privateKeyBackupIterations
            backup.keyAlgorithm = material.keyAlgorithm; backup.updatedAt = serverTimestamp()
            val data = js("({status: 'pending'})")
            data.userId = uid; data.requestId = requestId; data.previousFingerprint = old.publicKeyFingerprint
            data.profile = profile; data.backup = backup; data.createdAt = serverTimestamp()
            tx.set(ref("$root/keyReplacementRequests/$uid"), data)
        }
    }

    suspend fun dismiss(request: MemberKeyReplacementRequest, uid: String) = transaction(uid) { tx ->
        val path = "organizations/${request.orgId}/keyReplacementRequests/${request.userId}"
        val pending = tx.data(path)
        check(pending?.status == "pending" && pending.requestId == request.requestId) { "This request changed. Refresh and retry." }
        val data = js("({})")
        data.status = if (uid == request.userId) "cancelled" else "rejected"
        data.resolvedBy = uid; data.resolvedAt = serverTimestamp()
        tx.update(ref(path), data)
    }

    suspend fun approve(request: MemberKeyReplacementRequest, adminId: String, ring: String, controlKey: String) {
        val root = "organizations/${request.orgId}"
        val path = "$root/keyReplacementRequests/${request.userId}"
        val pending = OrganizationRotationStore.read(path) ?: error("This request no longer exists.")
        check(pending.status == "pending" && pending.requestId == request.requestId) { "This request changed. Refresh and retry." }
        val jwk = pending.profile.publicKeyJwk as String
        val fingerprint = OrganizationKeyCrypto.fingerprintPublicKey(jwk)
        require(fingerprint == request.fingerprint && fingerprint == pending.profile.publicKeyFingerprint) { "The replacement key fingerprint does not match. Reject this request." }
        val wrapped = OrganizationKeyCrypto.wrapOrganizationDataKey(OrganizationKeyRing.current(ring), jwk, fingerprint)
        val pin = VerifiedRotationRecipient(request.orgId, adminId, request.userId, fingerprint, jwk)
        val (ciphertext, iv) = SyntheticCrypto.encrypt(Json.encodeToString(VerifiedRotationRecipient.serializer(), pin), controlKey)
        transaction(adminId) { tx ->
            val current = tx.data(path)
            val member = tx.data("$root/members/${request.userId}")
            val profile = tx.data("$root/keyProfiles/${request.userId}")
            val metadata = tx.data("$root/keyMetadata/current")
            val maintenance = tx.data("$root/keyMaintenance/current")
            check(current?.status == "pending" && current.requestId == request.requestId &&
                current.profile.publicKeyJwk == jwk && current.profile.publicKeyFingerprint == fingerprint) { "This request changed. Refresh and retry." }
            check(member?.status == "active" && member.role in arrayOf("doctor", "nurse") && request.userId != adminId) { "Only another active clinical member's key can be replaced." }
            check(profile?.publicKeyFingerprint == current.previousFingerprint) { "The registered key changed. Ask the member to submit a new request." }
            check(maintenance?.active != true && metadata?.currentKeyVersion == OrganizationKeyRing.version(ring)) { "Finish maintenance and unlock the current organisation key before approving replacement." }
            val envelope = js("({algorithm: 'RSA-OAEP-3072-SHA256'})")
            envelope.userId = request.userId; envelope.keyVersion = OrganizationKeyRing.version(ring)
            envelope.wrappedKey = wrapped; envelope.grantedBy = adminId; envelope.createdAt = serverTimestamp()
            val recipient = js("({schemaVersion: 1})")
            recipient.ciphertext = ciphertext; recipient.iv = iv; recipient.adminId = adminId; recipient.updatedAt = serverTimestamp()
            val resolution = js("({status: 'approved'})")
            resolution.resolvedBy = adminId; resolution.resolvedAt = serverTimestamp()
            tx.set(ref("$root/keyProfiles/${request.userId}"), current.profile)
            tx.set(ref("$root/keyBackups/${request.userId}"), current.backup)
            tx.set(ref("$root/keyEnvelopes/${request.userId}"), envelope)
            tx.set(ref("$root/rotationRecipients/${request.userId}"), recipient)
            tx.update(ref(path), resolution)
        }
    }
}
