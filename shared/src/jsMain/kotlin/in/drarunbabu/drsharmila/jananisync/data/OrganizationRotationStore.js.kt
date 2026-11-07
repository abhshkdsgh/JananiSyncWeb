package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.*
import `in`.drarunbabu.drsharmila.jananisync.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.promise

/** Server-confirmed reads and online transactions: rotation writes are never queued offline. */
internal interface RotationStorage {
    suspend fun read(path: String): dynamic
    suspend fun members(orgId: String): List<OrganizationMember>
    suspend fun saveRecipient(pin: VerifiedRotationRecipient, ciphertext: String, iv: String)
    suspend fun begin(journal: OrganizationRotationJournal, ciphertext: String, iv: String)
    suspend fun adoptDeparture(journal: OrganizationRotationJournal, ciphertext: String, iv: String)
    suspend fun prepare(journal: OrganizationRotationJournal, ciphertext: String, iv: String)
    suspend fun stage(journal: OrganizationRotationJournal, index: Int, wrappedKey: String, proofCiphertext: String, proofIv: String)
    suspend fun finish(journal: OrganizationRotationJournal, ciphertext: String, iv: String)
}

internal object OrganizationRotationStore : RotationStorage {
    private val db get() = FirebaseService.rotationFirestore
    private fun ref(path: String) = doc(db, path)

    override suspend fun read(path: String): dynamic {
        val snapshot = getDocFromServer(ref(path)).awaitFirebase()
        return if (snapshot.exists()) snapshot.data() else null
    }

    override suspend fun members(orgId: String): List<OrganizationMember> {
        val snapshot = getDocsFromServer(collection(db, "organizations/$orgId/members")).awaitFirebase()
        require(snapshot.docs.size <= 1000) { "This organisation exceeds the supported rotation roster size. Contact the project owner before removing members." }
        return snapshot.docs.map { document ->
            val data = document.data()
            require(data.userId == document.id && data.status in arrayOf("active", "inactive", "left")) { "An organisation membership is invalid. Ask the project owner to review it." }
            OrganizationMember(orgId = orgId, userId = data.userId as String, role = data.role as String,
                status = data.status as String, joinedAt = (data.joinedAt.toMillis() as Double).toLong(),
                name = (data.name as? String).orEmpty().ifBlank { data.userId as String }, accessLevel = data.accessLevel as Int)
        }
    }

    fun observeMaintenance(orgId: String, onData: (OrganizationMaintenance) -> Unit): () -> Unit =
        onSnapshot(ref("organizations/$orgId/keyMaintenance/current"), js("({includeMetadataChanges: true})"), { snapshot ->
            if (!snapshot.metadata.hasPendingWrites) {
                val data = if (snapshot.exists()) snapshot.data() else null
                onData(OrganizationMaintenance(orgId, data?.active == true,
                    (data?.completed as? Int) ?: 0, (data?.total as? Int) ?: 0,
                    awaitingAdmin = data?.awaitingAdmin == true))
            }
        }, { _ -> onData(OrganizationMaintenance(orgId, active = true,
            message = "Organisation access could not be checked. Reconnect to continue.")) })

    private suspend fun transaction(adminId: String, action: suspend (FirestoreTransaction) -> Unit) {
        val context = currentCoroutineContext()
        runTransaction(db) { tx -> CoroutineScope(context).promise {
            check(FirebaseService.currentUser?.uid == adminId) { "Sign in again to resume the access update." }
            action(tx)
        } }.awaitFirebaseUnit()
    }

    private suspend fun FirestoreTransaction.data(path: String): dynamic {
        val snapshot = get(ref(path)).awaitFirebase()
        return if (snapshot.exists()) snapshot.data() else null
    }

    override suspend fun saveRecipient(pin: VerifiedRotationRecipient, ciphertext: String, iv: String) = transaction(pin.adminId) { tx ->
        val data = js("({})")
        data.ciphertext = ciphertext; data.iv = iv; data.adminId = pin.adminId
        data.schemaVersion = 1; data.updatedAt = serverTimestamp()
        tx.set(ref("organizations/${pin.orgId}/rotationRecipients/${pin.userId}"), data)
    }

    override suspend fun begin(journal: OrganizationRotationJournal, ciphertext: String, iv: String) = transaction(journal.adminId) { tx ->
        val root = "organizations/${journal.orgId}"
        val metadata = tx.data("$root/keyMetadata/current")
        val maintenance = tx.data("$root/keyMaintenance/current")
        val removed = if (journal.removedUserId.isNotBlank()) tx.data("$root/members/${journal.removedUserId}") else null
        check(metadata.currentKeyVersion == journal.baseVersion && maintenance?.active != true) { "Another access update has started. Reopen the organisation." }
        check(journal.removedUserId.isEmpty() || (removed?.status == "active" && removed.role != "admin")) { "This member is no longer eligible for removal." }
        val data = js("({})")
        data.adminId = journal.adminId; data.removedUserId = journal.removedUserId
        data.baseVersion = journal.baseVersion; data.targetVersion = journal.baseVersion + 1
        data.phase = "preparing"; data.recipients = journal.recipients.map { it.userId }.toTypedArray()
        data.recipientCount = journal.recipients.size; data.nextIndex = 0
        data.ciphertext = ciphertext; data.iv = iv; data.createdAt = serverTimestamp(); data.updatedAt = serverTimestamp()
        tx.set(ref("$root/keyRotations/${journal.operationId}"), data)
        val status = js("({})")
        status.active = true; status.rotationId = journal.operationId; status.adminId = journal.adminId
        status.completed = 0; status.total = journal.recipients.size; status.updatedAt = serverTimestamp()
        tx.set(ref("$root/keyMaintenance/current"), status)
        if (journal.removedUserId.isNotEmpty()) {
            val inactive = js("({status: 'inactive'})")
            tx.update(ref("$root/members/${journal.removedUserId}"), inactive)
        }
    }

    override suspend fun adoptDeparture(journal: OrganizationRotationJournal, ciphertext: String, iv: String) = transaction(journal.adminId) { tx ->
        val root = "organizations/${journal.orgId}"
        val status = tx.data("$root/keyMaintenance/current")
        val metadata = tx.data("$root/keyMetadata/current")
        val previous = tx.data("$root/keyRotations/${journal.operationId}")
        check(status?.active == true && status.awaitingAdmin == true && status.rotationId == journal.operationId &&
            status.adminId == journal.adminId && metadata.currentKeyVersion == journal.baseVersion && previous == null) {
            "The pending Access Update changed. Reload to resume it."
        }
        val operation = js("({phase: 'preparing', nextIndex: 0, removedUserId: ''})")
        operation.adminId = journal.adminId; operation.baseVersion = journal.baseVersion; operation.targetVersion = journal.baseVersion + 1
        operation.recipients = journal.recipients.map { it.userId }.toTypedArray(); operation.recipientCount = journal.recipients.size
        operation.ciphertext = ciphertext; operation.iv = iv; operation.createdAt = serverTimestamp(); operation.updatedAt = serverTimestamp()
        tx.set(ref("$root/keyRotations/${journal.operationId}"), operation)
        val maintenance = js("({active: true, awaitingAdmin: false, completed: 0})")
        maintenance.rotationId = journal.operationId; maintenance.adminId = journal.adminId
        maintenance.total = journal.recipients.size; maintenance.updatedAt = serverTimestamp()
        tx.set(ref("$root/keyMaintenance/current"), maintenance)
    }

    override suspend fun prepare(journal: OrganizationRotationJournal, ciphertext: String, iv: String) = transaction(journal.adminId) { tx ->
        val root = "organizations/${journal.orgId}"
        val current = tx.data("$root/keyRotations/${journal.operationId}")
        check(current?.phase == "preparing" && current.nextIndex == 0) { "This update has already been prepared. Reload to resume." }
        val data = js("({})")
        data.phase = "wrapping"; data.recipients = journal.recipients.map { it.userId }.toTypedArray()
        data.recipientCount = journal.recipients.size; data.ciphertext = ciphertext; data.iv = iv
        data.updatedAt = serverTimestamp()
        tx.update(ref("$root/keyRotations/${journal.operationId}"), data)
        val status = js("({})")
        status.total = journal.recipients.size; status.updatedAt = serverTimestamp()
        tx.update(ref("$root/keyMaintenance/current"), status)
    }

    override suspend fun stage(journal: OrganizationRotationJournal, index: Int, wrappedKey: String,
        proofCiphertext: String, proofIv: String) = transaction(journal.adminId) { tx ->
        val root = "organizations/${journal.orgId}"
        val current = tx.data("$root/keyRotations/${journal.operationId}")
        if ((current.nextIndex as Int) > index) return@transaction // Another tab already committed this slot.
        check(current.phase == "wrapping" && current.nextIndex == index) { "Rotation progress changed. Retry to resume." }
        val recipient = journal.recipients[index]
        val data = js("({})")
        data.userId = recipient.userId; data.keyVersion = journal.baseVersion + 1
        data.wrappedKey = wrappedKey; data.algorithm = "RSA-OAEP-3072-SHA256"
        data.grantedBy = journal.adminId; data.createdAt = serverTimestamp(); data.index = index
        data.proofCiphertext = proofCiphertext; data.proofIv = proofIv
        tx.set(ref("$root/keyRotations/${journal.operationId}/grants/${recipient.userId}"), data)
        val progress = js("({})")
        progress.nextIndex = index + 1; progress.updatedAt = serverTimestamp()
        tx.update(ref("$root/keyRotations/${journal.operationId}"), progress)
        val status = js("({})")
        status.completed = index + 1; status.updatedAt = serverTimestamp()
        tx.update(ref("$root/keyMaintenance/current"), status)
    }

    override suspend fun finish(journal: OrganizationRotationJournal, ciphertext: String, iv: String) = transaction(journal.adminId) { tx ->
        val root = "organizations/${journal.orgId}"
        val current = tx.data("$root/keyRotations/${journal.operationId}")
        val metadata = tx.data("$root/keyMetadata/current")
        check(current.phase == "wrapping" && current.nextIndex == journal.recipients.size && metadata.currentKeyVersion == journal.baseVersion) {
            "The access update is incomplete or changed. Retry to resume."
        }
        val history = js("({})")
        history.keyVersion = journal.baseVersion; history.wrappingVersion = journal.baseVersion + 1
        history.ciphertext = ciphertext; history.iv = iv; history.createdBy = journal.adminId; history.createdAt = serverTimestamp()
        tx.set(ref("$root/keyHistory/${journal.baseVersion}"), history)
        val version = js("({})")
        version.currentKeyVersion = journal.baseVersion + 1; version.rotationId = journal.operationId
        version.updatedBy = journal.adminId; version.updatedAt = serverTimestamp()
        tx.update(ref("$root/keyMetadata/current"), version)
        val done = js("({phase: 'completed'})")
        done.updatedAt = serverTimestamp()
        tx.update(ref("$root/keyRotations/${journal.operationId}"), done)
        val status = js("({active: false})")
        status.updatedAt = serverTimestamp()
        tx.update(ref("$root/keyMaintenance/current"), status)
    }
}
