package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.*
import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyMaterial
import kotlinx.coroutines.*
import kotlinx.coroutines.promise
import kotlin.js.Promise

/** Keep a rejected callback promise from cancelling the caller before it can display the error. */
internal suspend fun <T> runMembershipTransaction(
    run: ((T) -> Promise<Unit>) -> Promise<Unit>,
    action: suspend (T) -> Unit
) = supervisorScope {
    val context = currentCoroutineContext()
    var callbackFailure: Throwable? = null
    try {
        run { value ->
            // Firestore may retry its callback. Only the final attempt's failure is relevant.
            callbackFailure = null
            CoroutineScope(context).promise {
                try {
                    action(value)
                } catch (error: Throwable) {
                    callbackFailure = error
                    throw error
                }
            }
        }.awaitFirebaseUnit()
    } catch (error: Throwable) {
        currentCoroutineContext().ensureActive()
        // awaitFirebase sanitizes SDK errors. Our own validation failures already carry
        // safe user-facing messages and must survive that Promise boundary unchanged.
        throw callbackFailure ?: error
    }
}

/** Self-service access changes are online transactions, never optimistic offline removals. */
internal object OrganizationMembershipStore {
    private val db get() = FirebaseService.rotationFirestore
    private fun ref(path: String) = doc(db, path)
    private suspend fun transaction(uid: String, action: suspend (FirestoreTransaction) -> Unit) {
        runMembershipTransaction<FirestoreTransaction>({ callback -> runTransaction(db, callback) }) { tx ->
            check(FirebaseService.currentUser?.uid == uid) { "Your sign-in changed. Sign in again." }
            action(tx)
        }
    }
    private suspend fun FirestoreTransaction.data(path: String): dynamic {
        val snapshot = get(ref(path)).awaitFirebase()
        return if (snapshot.exists()) snapshot.data() else null
    }

    suspend fun cancelRequest(orgId: String, uid: String) = transaction(uid) { tx ->
        val path = "organizations/$orgId/accessRequests/$uid"
        val request = tx.data(path)
        check(request?.userId == uid && request.status == "pending") { "This request is no longer pending. Refresh organisation access." }
        val update = js("({status: 'cancelled'})"); update.cancelledAt = serverTimestamp()
        tx.update(ref(path), update)
    }

    suspend fun submitRequest(orgId: String, uid: String, name: String, email: String, role: String,
        material: MemberKeyMaterial) = transaction(uid) { tx ->
        val root = "organizations/$orgId"
        val previous = tx.data("$root/accessRequests/$uid")
        val membership = tx.data("$root/members/$uid")
        val profile = tx.data("$root/keyProfiles/$uid")
        val backup = tx.data("$root/keyBackups/$uid")
        check(membership?.status != "active") {
            "You already have access to this organisation. Open your account menu and choose Switch organisation."
        }
        val rejoining = previous != null && previous.status in arrayOf("approved", "rejected") && membership?.status == "left"
        check(previous == null || previous.status == "cancelled" || rejoining) {
            "A request already exists for this organisation. Check its current status or wait for the administrator's decision."
        }
        check((profile == null) == (backup == null)) { "Your organisation recovery records are incomplete. Contact the project owner." }
        if (profile != null) check(profile.publicKeyJwk == material.publicKeyJwk && profile.publicKeyFingerprint == material.publicKeyFingerprint &&
            backup.ciphertext == material.privateKeyBackupCiphertext && backup.iv == material.privateKeyBackupIv) {
            "Reuse your existing recovery key for this organisation. Prepare secure access again."
        }
        val request = js("({status: 'pending'})")
        request.userId = uid; request.userName = name; request.userEmail = email
        request.requestedRole = role; request.createdAt = serverTimestamp()
        if (rejoining) {
            val renewal = js("({status: 'pending'})")
            renewal.createdAt = serverTimestamp()
            tx.update(ref("$root/accessRequests/$uid"), renewal)
        } else {
            tx.set(ref("$root/accessRequests/$uid"), request)
        }
        if (profile == null) {
            val publicKey = js("({})")
            publicKey.userId = uid; publicKey.publicKeyJwk = material.publicKeyJwk
            publicKey.publicKeyFingerprint = material.publicKeyFingerprint; publicKey.keyAlgorithm = material.keyAlgorithm
            publicKey.createdAt = serverTimestamp()
            val privateKey = js("({})")
            privateKey.userId = uid; privateKey.ciphertext = material.privateKeyBackupCiphertext; privateKey.iv = material.privateKeyBackupIv
            privateKey.salt = material.privateKeyBackupSalt; privateKey.kdf = material.privateKeyBackupKdf
            privateKey.kdfIterations = material.privateKeyBackupIterations; privateKey.keyAlgorithm = material.keyAlgorithm
            privateKey.updatedAt = serverTimestamp()
            tx.set(ref("$root/keyProfiles/$uid"), publicKey)
            tx.set(ref("$root/keyBackups/$uid"), privateKey)
        }
    }

    suspend fun changeRole(orgId: String, adminId: String, uid: String, role: String) = transaction(adminId) { tx ->
        require(role in arrayOf("doctor", "nurse")) { "Choose Doctor or Nurse." }
        val root = "organizations/$orgId"
        val admin = tx.data("$root/members/$adminId")
        val member = tx.data("$root/members/$uid")
        val maintenance = tx.data("$root/keyMaintenance/current")
        check(admin?.status == "active" && admin.role == "admin") { "Only this organisation's administrator can change roles." }
        check(uid != adminId && member?.userId == uid && member.role in arrayOf("doctor", "nurse") && member.status in arrayOf("active", "inactive")) {
            "Only current Doctor and Nurse memberships can change roles."
        }
        check(maintenance?.active != true) { "Wait for Access Update to finish before changing roles." }
        val update = js("({})")
        update.role = role
        tx.update(ref("$root/members/$uid"), update)
    }

    suspend fun leave(orgId: String, uid: String) = transaction(uid) { tx ->
        val root = "organizations/$orgId"
        val member = tx.data("$root/members/$uid")
        val status = tx.data("$root/keyMaintenance/current")
        val metadata = tx.data("$root/keyMetadata/current")
        check(member?.status == "active" && member.role in arrayOf("doctor", "nurse")) {
            "Only active clinical members can leave. The administrator must remain responsible for the organisation."
        }
        check(status?.active != true || status.awaitingAdmin == true) { "An Access Update is already running. Wait for it to finish before leaving." }
        val operationId = if (status?.active == true) status.rotationId as String else SyntheticCrypto.generateOpaqueId()
        val update = js("({status: 'left'})")
        update.leftAt = serverTimestamp(); update.leftDuringRotationId = operationId
        tx.update(ref("$root/members/$uid"), update)
        if (status?.active != true) {
            val maintenance = js("({active: true, awaitingAdmin: true, completed: 0, total: 0})")
            maintenance.rotationId = operationId; maintenance.adminId = metadata.updatedBy
            maintenance.departedUserId = uid; maintenance.updatedAt = serverTimestamp()
            tx.set(ref("$root/keyMaintenance/current"), maintenance)
        }
    }
}
