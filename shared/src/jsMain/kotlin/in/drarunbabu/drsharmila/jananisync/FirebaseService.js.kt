@file:Suppress("NON_EXPORTABLE_TYPE")

package `in`.drarunbabu.drsharmila.jananisync

import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyMaterial
import kotlin.js.Promise
import kotlinx.browser.window

/** Firebase services for the Kotlin/JS browser target. Call [initialize] before using Auth or Firestore. */
object FirebaseService {
    private val app by lazy {
        val config = js("({})").unsafeCast<FirebaseConfig>()
        config.apiKey = "AIzaSyAB4xRfGQ572MrUUPqkY7Tr1YgE79Hh1Uk"
        config.authDomain = "jananisyncweb.firebaseapp.com"
        config.projectId = "jananisyncweb"
        config.storageBucket = "jananisyncweb.firebasestorage.app"
        config.messagingSenderId = "390390181411"
        config.appId = "1:390390181411:web:c074de1ba5652e57e6cb96"
        config.measurementId = "G-2N1QB6P1MX"
        initializeApp(config).also { firebaseApp ->
            if (isLocalDevelopmentHost()) {
                js("globalThis.FIREBASE_APPCHECK_DEBUG_TOKEN = true")
            }
            val options = js("({})").unsafeCast<AppCheckOptions>()
            options.provider = ReCaptchaEnterpriseProvider("6LeQkQYuAAAAAPPChZyFNu3DtVpI3aaY5tgsTR4_")
            options.isTokenAutoRefreshEnabled = true
            initializeAppCheck(firebaseApp, options)
        }
    }

    private val auth by lazy { getAuth(app) }
    private val firestore by lazy { getFirestore(app) }
    internal val rotationFirestore get() = firestore
    private var offlinePersistence: Promise<Unit>? = null

    /** Initializes Firebase. Auth and Firestore are created lazily on first use. */
    fun initialize() {
        app
    }

    private fun isLocalDevelopmentHost(): Boolean =
        window.location.hostname == "localhost" || window.location.hostname == "127.0.0.1"

    /** Reports Firebase's restored auth state after SDK initialization, then future changes. */
    fun observeAuthState(onUserChanged: (FirebaseUser?) -> Unit): () -> Unit =
        onAuthStateChanged(auth, onUserChanged)

    fun awaitAuthStateReady(): Promise<Unit> = auth.authStateReady()

    /** Enables the browser's IndexedDB cache. Call once during app startup, before listeners. */
    fun enableOfflinePersistence(): Promise<Unit> = offlinePersistence
        ?: enableIndexedDbPersistence(firestore).also { offlinePersistence = it }

    /** Live ciphertext-only LCG demo feed. Each observation is append-only and scoped to one org. */
    @OptIn(ExperimentalWasmJsInterop::class)
    fun listenToSyntheticLcgObservations(
        orgId: String,
        womanDocumentId: String?,
        onData: (Array<String>, Boolean) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation code." }
        // Roster screens need current cells only; full immutable history is scoped to one admission.
        val projections = womanDocumentId == null
        val reference = if (projections) collection(firestore, "organizations/$orgId/clinicalData/v1/lcgCells")
            else query(collection(firestore, "organizations/$orgId/clinicalData/v1/lcgObservations"), where("womanDocumentId", "==", womanDocumentId))
        return onSnapshot(reference, js("({})"), { snapshot ->
            val pending = snapshot.docs.any { it.metadata.hasPendingWrites }
            val rows = Array(snapshot.docs.length) { index ->
                val item = snapshot.docs[index]
                val projection = item.data()
                val data = if (projections) projection.event else projection
                val row = js("({})")
                row.documentId = if (projections) projection.sourceEventId else item.id
                row.ciphertext = data.ciphertext
                row.iv = data.iv
                row.createdBy = data.createdBy
                row.accessLevel = data.accessLevel
                row.recordType = data.recordType
                row.columnId = data.columnId
                row.cellKey = data.cellKey
                row.revision = data.revision
                row.cellKeys = data.cellKeys
                row.schemaVersion = data.schemaVersion
                row.keyVersion = data.keyVersion
                row.womanDocumentId = data.womanDocumentId
                row.createdAtMillis = timestampMillisOrZero(data.createdAt)
                row.pending = item.metadata.hasPendingWrites
                JSON.stringify(row)
            }.distinct().toTypedArray()
            onData(rows, pending)
        }, { error -> onError(error.message ?: "Could not load synthetic LCG observations.") })
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun listenToLcgCorrections(orgId: String, womanDocumentId: String?, onData: (Array<String>) -> Unit, onError: (String) -> Unit): () -> Unit {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation code." }
        if (womanDocumentId == null) { onData(emptyArray()); return {} }
        val reference = query(collection(firestore, "organizations/$orgId/clinicalData/v1/lcgCorrections"), where("womanDocumentId", "==", womanDocumentId))
        return onSnapshot(reference, js("({})"), { snapshot ->
            val rows = Array(snapshot.docs.length) { i ->
                val item = snapshot.docs[i]
                val data = item.data()
                val row = js("({})")
                row.documentId = item.id
                row.ciphertext = data.ciphertext
                row.iv = data.iv
                row.createdBy = data.createdBy
                row.accessLevel = data.accessLevel
                row.createdAtMillis = timestampMillisOrZero(data.createdAt)
                row.correctedObservationId = data.correctedObservationId
                JSON.stringify(row)
            }
            onData(rows)
        }, { error -> onError(error.message ?: "Could not load observation corrections.") })
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun listenToLcgColumnControls(orgId: String, womanDocumentId: String?, onData: (Array<String>) -> Unit, onError: (String) -> Unit): () -> Unit {
        val columns = collection(firestore, "organizations/$orgId/clinicalData/v1/lcgColumns")
        val reference = if (womanDocumentId == null) columns else query(columns, where("womanDocumentId", "==", womanDocumentId))
        return onSnapshot(reference, js("({})"), { snapshot ->
            val rows = Array(snapshot.docs.length) { i ->
                val row = snapshot.docs[i].data()
                row.columnId = snapshot.docs[i].id
                JSON.stringify(row)
            }
            onData(rows)
        }, { error -> onError(error.message ?: "Could not load column status.") })
    }

    fun writeLcgCellEvent(
        orgId: String,
        requestedColumnId: String,
        womanDocumentId: String,
        cellKey: String,
        ciphertext: String,
        iv: String,
        accessLevel: Int,
        previous: dynamic,
        cellKeys: Array<String>,
        reasonCiphertext: String = "",
        reasonIv: String = "",
        observationTimeMillis: Double = 0.0,
        chartStartMillis: Double? = null,
        chartColumnIndex: Int = -1,
        sessionId: String = "",
        columnChartStartMillis: Double? = chartStartMillis,
        encryptedKeyVersion: Int
    ): Promise<String> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$")))
        require(cellKey.matches(Regex("^[A-Za-z][A-Za-z0-9]{0,39}$")))
        val author = auth.currentUser?.uid ?: error("Your signed-in account is unavailable.")
        val columns = collection(firestore, "organizations/$orgId/clinicalData/v1/lcgColumns")
        val columnReference = if (requestedColumnId.isBlank()) doc(columns) else doc(firestore, "organizations/$orgId/clinicalData/v1/lcgColumns/$requestedColumnId")
        val isNew = js("previous == null") as Boolean
        require(isNew || previous.columnId == requestedColumnId) {
            "The write target does not match the selected time column."
        }
        require(!requestedColumnId.startsWith("timeline-") || chartColumnIndex < 0 ||
            requestedColumnId == "timeline-$womanDocumentId-$chartColumnIndex") {
            "The chart index does not match the selected time column."
        }
        val oldRevision = if (isNew) 0 else previous.revision as Int
        val oldHighest = if (isNew) 0 else previous.highestEditorAccessLevel as Int
        val cellLevels: dynamic = if (isNew) js("({})") else js("Object.assign({}, previous.cellAccessLevels || {})")
        val oldEditors = if (isNew) emptyArray() else (previous.highestEditorUids as Array<String>)
        val isCorrection = reasonCiphertext.isNotBlank()
        require(!isCorrection || (!isNew && previous.confirmed == true && reasonIv.isNotBlank())) { "Corrections require a confirmed column and a reason." }
        val isHeader = requestedColumnId.startsWith("header-")
        require(!isHeader || cellKeys.all { key -> `in`.drarunbabu.drsharmila.jananisync.data.model.allHeaderFields.any { it.first == key } })
        require(isHeader || cellKeys.all { accessLevel >= maxOf((cellLevels[it] as? Int) ?: 0, (if (it == "oxytocinHalf1") cellLevels["oxytocin"] as? Int else if (it == "oxytocin") cellLevels["oxytocinHalf1"] as? Int else null) ?: 0) }) { "A selected cell requires a higher access level." }
        cellKeys.forEach { cellLevels[it] = accessLevel }
        val highest = maxOf(accessLevel, oldHighest)
        val editors = when {
            accessLevel > oldHighest -> arrayOf(author)
            accessLevel == oldHighest && author !in oldEditors -> oldEditors + author
            else -> oldEditors
        }
        val revision = oldRevision + 1
        val eventReference = doc(collection(firestore, "organizations/$orgId/clinicalData/v1/lcgObservations"))
        val header = js("({})")
        require(womanDocumentId.matches(Regex("^[A-Za-z0-9_-]{1,128}$")))
        header.womanDocumentId = womanDocumentId
        if (isNew) {
            header.sessionId = sessionId
            header.chartColumnIndex = chartColumnIndex
            header.chartStartMillis = columnChartStartMillis ?: 0.0
            header.timestampMillis = observationTimeMillis - if (cellKeys.any { it.endsWith("Half2") }) 1_800_000 else 0
        }
        header.cellAccessLevels = cellLevels
        header.lastEditorUid = author
        header.lastEditorAccessLevel = accessLevel
        header.highestEditorAccessLevel = highest
        header.highestEditorUids = editors
        header.revision = revision
        header.confirmed = if (isNew) false else previous.confirmed
        header.lastEditedAt = serverTimestamp()
        header.lastEventId = eventReference.id
        val event = js("({})")
        event.ciphertext = ciphertext
        event.iv = iv
        event.keyVersion = encryptedKeyVersion
        event.algorithm = "AES-GCM"
        event.schemaVersion = 2
        event.createdBy = author
        event.accessLevel = accessLevel
        event.recordType = if (isCorrection) "cellCorrection" else "cell"
        if (isCorrection) {
            event.reasonCiphertext = reasonCiphertext
            event.reasonIv = reasonIv
            event.correctedRevision = oldRevision
        }
        event.columnId = columnReference.id
        event.womanDocumentId = womanDocumentId
        event.cellKey = cellKey
        event.cellKeys = cellKeys
        event.observationTimeMillis = observationTimeMillis
        if (!isHeader && chartStartMillis != null && chartColumnIndex >= 0) {
            event.chartStartMillis = chartStartMillis
            event.chartColumnIndex = chartColumnIndex
        }
        event.revision = revision
        event.createdAt = serverTimestamp()
        val batch = writeBatch(firestore)
        if (!isHeader && chartStartMillis != null && chartColumnIndex >= 0) {
            val chart = js("({})")
            chart.startMillis = chartStartMillis
            chart.womanDocumentId = womanDocumentId
            chart.sessionId = sessionId
            batch.set(doc(firestore, "organizations/$orgId/clinicalData/v1/chartStarts/$womanDocumentId"), chart)
        }
        if (isNew) batch.set(columnReference, header) else batch.update(columnReference, header)
        batch.set(eventReference, event)
        writeCellProjections(batch, orgId, columnReference.id, womanDocumentId, eventReference.id, event, cellKeys.toList())
        return batch.commit().then { columnReference.id }
    }

    private fun writeCellProjections(batch: FirestoreWriteBatch, orgId: String, columnId: String,
        womanDocumentId: String, eventId: String, event: dynamic, keys: List<String>) {
        for (key in keys) {
            val projection = js("({})")
            projection.sourceEventId = eventId
            projection.columnId = columnId
            projection.womanDocumentId = womanDocumentId
            projection.cellKey = key
            projection.event = event
            batch.set(doc(firestore, "organizations/$orgId/clinicalData/v1/lcgCells/${columnId}~$key"), projection)
        }
    }

    /** Trusted one-time binding for historical encrypted columns; rules make this immutable. */
    fun bindLcgColumnToWoman(orgId: String, columnId: String, womanDocumentId: String): Promise<Unit> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$")))
        require(columnId.matches(Regex("^[A-Za-z0-9_-]{1,128}$")) && womanDocumentId.matches(Regex("^[A-Za-z0-9_-]{1,128}$")))
        val binding = js("({})")
        binding.womanDocumentId = womanDocumentId
        return updateDoc(doc(firestore, "organizations/$orgId/clinicalData/v1/lcgColumns/$columnId"), binding)
    }

    fun confirmLcgColumn(
        orgId: String,
        columnId: String,
        revision: Int,
        confirmerAccessLevel: Int,
        ciphertext: String,
        iv: String,
        confirmationKeys: List<String> = listOf("initials"),
        womanDocumentId: String = "",
        encryptedKeyVersion: Int
    ): Promise<Unit> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$")))
        require(columnId.isNotBlank())
        val author = auth.currentUser?.uid ?: error("Your signed-in account is unavailable.")
        val confirmationEvent = doc(collection(firestore, "organizations/$orgId/clinicalData/v1/lcgObservations"))
        val header = js("({})")
        header.confirmed = true
        header.confirmedBy = author
        header.confirmedAt = serverTimestamp()
        header.revision = revision + 1
        header.confirmationEventId = confirmationEvent.id
        val event = js("({})")
        event.ciphertext = ciphertext
        event.iv = iv
        event.keyVersion = encryptedKeyVersion
        event.algorithm = "AES-GCM"
        event.schemaVersion = 2
        event.createdBy = author
        event.accessLevel = confirmerAccessLevel
        event.recordType = "confirmation"
        event.columnId = columnId
        event.womanDocumentId = womanDocumentId
        event.cellKey = "initials"
        event.cellKeys = confirmationKeys.toTypedArray()
        event.revision = revision + 1
        event.createdAt = serverTimestamp()
        val batch = writeBatch(firestore)
        batch.update(doc(firestore, "organizations/$orgId/clinicalData/v1/lcgColumns/$columnId"), header)
        batch.set(confirmationEvent, event)
        writeCellProjections(batch, orgId, columnId, womanDocumentId, confirmationEvent.id, event, confirmationKeys)
        return batch.commit()
    }

    /** Organization-scoped encrypted roster envelopes; document IDs are generated by Firestore. */
    @OptIn(ExperimentalWasmJsInterop::class)
    fun listenToRoster(orgId: String, includeWomen: Boolean, onData: (Array<String>, Array<String>) -> Unit, onError: (String) -> Unit): () -> Unit {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation code." }
        val wards = collection(firestore, "organizations/$orgId/wards")
        val women = collection(firestore, "organizations/$orgId/women")
        var wardRows: Array<String> = emptyArray()
        var womanRows: Array<String> = emptyArray()
        var wardsLoaded = false
        var womenLoaded = !includeWomen
        fun emit() { if (wardsLoaded && womenLoaded) onData(wardRows, womanRows) }
        val stopWards = onSnapshot(wards, { snapshot ->
            wardsLoaded = true
            wardRows = Array(snapshot.docs.length) { i ->
                val row = snapshot.docs[i].data()
                row.documentId = snapshot.docs[i].id
                JSON.stringify(row)
            }
            emit()
        }, { error -> onError(error.message ?: "Could not load organisation wards.") })
        val stopWomen: () -> Unit = if (includeWomen) onSnapshot(women, { snapshot ->
            womenLoaded = true
            womanRows = Array(snapshot.docs.length) { i ->
                val row = snapshot.docs[i].data()
                row.documentId = snapshot.docs[i].id
                JSON.stringify(row)
            }
            emit()
        }, { error -> onError(error.message ?: "Could not load organisation women.") }) else ({ })
        // Archive history is loaded in bounded pages only when its dialog is opened.
        return { stopWards(); stopWomen() }
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun getRosterArchiveHistory(orgId: String, womanDocumentId: String, beforeRevision: Int?): Promise<Array<String>> {
        require(validOrganizationCode(orgId) && womanDocumentId.matches(Regex("^[A-Za-z0-9_-]{1,128}$")))
        val constraints = mutableListOf(where("womanDocumentId", "==", womanDocumentId), orderBy("revision", "desc"), limit(50))
        if (beforeRevision != null) constraints += startAfter(beforeRevision)
        return getDocs(query(collection(firestore, "organizations/$orgId/rosterArchiveEvents"), *constraints.toTypedArray())).then { snapshot ->
            Array(snapshot.docs.length) { index ->
                val item = snapshot.docs[index]
                val row = item.data()
                row.documentId = item.id
                row.createdAtMillis = timestampMillisOrZero(row.createdAt)
                JSON.stringify(row)
            }
        }
    }

    /** Atomically link the new archive state to an immutable, server-timestamped audit event. */
    fun setRosterWomanArchived(
        orgId: String, womanDocumentId: String, archived: Boolean, previousRevision: Int,
        accessLevel: Int, actorCiphertext: String, actorIv: String,
        encryptedKeyVersion: Int
    ): Promise<Unit> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$")) && womanDocumentId.matches(Regex("^[A-Za-z0-9_-]{1,128}$")))
        val author = auth.currentUser?.uid ?: error("Your signed-in account is unavailable.")
        val eventReference = doc(collection(firestore, "organizations/$orgId/rosterArchiveEvents"))
        val status = js("({})")
        status.archived = archived
        status.archiveRevision = previousRevision + 1
        status.lastArchiveEventId = eventReference.id
        val event = js("({})")
        event.womanDocumentId = womanDocumentId
        event.archived = archived
        event.revision = previousRevision + 1
        event.createdBy = author
        event.accessLevel = accessLevel
        event.createdAt = serverTimestamp()
        event.ciphertext = actorCiphertext
        event.iv = actorIv
        event.keyVersion = encryptedKeyVersion
        event.algorithm = "AES-GCM"
        event.schemaVersion = 2
        val batch = writeBatch(firestore)
        batch.update(doc(firestore, "organizations/$orgId/women/$womanDocumentId"), status)
        batch.set(eventReference, event)
        return batch.commit()
    }

    fun writeRosterEnvelope(orgId: String, collectionName: String, ciphertext: String, iv: String, encryptedKeyVersion: Int): Promise<String> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation code." }
        require(collectionName == "wards" || collectionName == "women")
        val data = js("({})")
        data.ciphertext = ciphertext
        data.iv = iv
        data.keyVersion = encryptedKeyVersion
        data.algorithm = "AES-GCM"
        data.schemaVersion = 1
        data.createdBy = auth.currentUser?.uid ?: error("Your signed-in account is unavailable.")
        data.createdAt = serverTimestamp()
        val reference = doc(collection(firestore, "organizations/$orgId/$collectionName"))
        return setDoc(reference, data).then { reference.id }
    }

    fun updateRosterEnvelope(orgId: String, collectionName: String, documentId: String, ciphertext: String, iv: String, encryptedKeyVersion: Int): Promise<Unit> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$")))
        require(collectionName == "wards" && documentId.isNotBlank())
        val data = js("({})")
        data.ciphertext = ciphertext
        data.iv = iv
        data.keyVersion = encryptedKeyVersion
        data.algorithm = "AES-GCM"
        data.schemaVersion = 1
        data.createdBy = auth.currentUser?.uid ?: error("Your signed-in account is unavailable.")
        data.createdAt = serverTimestamp()
        return updateDoc(doc(firestore, "organizations/$orgId/$collectionName/$documentId"), data)
    }

    fun writeSyntheticLcgObservation(orgId: String, authorUid: String, accessLevel: Int, ciphertext: String, iv: String): Promise<Unit> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation code." }
        val data = js("({})")
        data.ciphertext = ciphertext
        data.iv = iv
        data.keyVersion = currentDataKeyVersion(orgId)
        data.algorithm = "AES-GCM"
        data.schemaVersion = 1
        data.createdBy = authorUid
        data.accessLevel = accessLevel
        data.recordType = "observation"
        data.createdAt = serverTimestamp()
        val reference = doc(collection(firestore, "organizations/$orgId/clinicalData/v1/lcgObservations"))
        return setDoc(reference, data)
    }

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun signIn(email: String, password: String): Promise<FirebaseUser> =
        signInWithEmailAndPassword(auth, email, password).then { it.user }

    fun createAccount(email: String, password: String): Promise<FirebaseUser> =
        createUserWithEmailAndPassword(auth, email, password).then { it.user }

    fun sendPasswordResetEmail(email: String): Promise<Unit> = sendPasswordResetEmail(auth, email)

    fun sendVerificationEmail(): Promise<Unit> = auth.currentUser?.let(::sendEmailVerification)
        ?: Promise.reject(IllegalStateException("No signed-in account is available."))

    suspend fun refreshEmailVerification(): Boolean {
        val user = auth.currentUser ?: return false
        reload(user).awaitFirebaseUnit()
        getIdToken(user, true).awaitFirebase()
        return user.emailVerified
    }

    fun signOut(): Promise<Unit> = signOut(auth)

    /** Resolves to the document data, or null when the document does not exist. */
    fun getDocument(path: String): Promise<dynamic> = getDoc(doc(firestore, path)).then { snapshot ->
        if (snapshot.exists()) snapshot.data() else null
    }

    /** Reads the display name from an organization root visible to its active members. */
    fun getOrganizationName(orgId: String): Promise<String> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation identifier." }
        return getDoc(doc(firestore, "organizations/$orgId")).then { snapshot ->
            if (!snapshot.exists()) return@then "Organisation"
            val data = snapshot.data()
            val organizationName = data.organizationName
            val name = data.name
            when {
                jsTypeOf(organizationName) == "string" && organizationName.unsafeCast<String>().isNotBlank() ->
                    organizationName.unsafeCast<String>().trim()
                jsTypeOf(name) == "string" && name.unsafeCast<String>().isNotBlank() ->
                    name.unsafeCast<String>().trim()
                else -> "Organisation"
            }
        }
    }

    fun approveOrganizationAccessRequest(
        orgId: String,
        targetUserId: String,
        approvedRole: String,
        memberName: String,
        accessLevel: Int,
        adminUserId: String
    ): Promise<Unit> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) {
            "Organisation access code must be exactly 8 alphanumeric characters."
        }
        require(memberName.isNotBlank() && memberName.length <= 160) { "Member name must be 1 to 160 characters." }
        require(accessLevel in 10..99) { "Access level must be a whole number from 10 to 99." }
        val requestData = js("({})")
        requestData.status = "approved"
        requestData.reviewedBy = adminUserId
        requestData.reviewedAt = serverTimestamp()
        requestData.approvedRole = approvedRole

        val memberData = js("({})")
        memberData.userId = targetUserId
        memberData.name = memberName
        memberData.role = approvedRole
        memberData.status = "active"
        memberData.accessLevel = accessLevel
        memberData.joinedAt = serverTimestamp()

        val batch = writeBatch(firestore)
        batch.update(doc(firestore, "organizations/$orgId/accessRequests/$targetUserId"), requestData)
        batch.set(doc(firestore, "organizations/$orgId/members/$targetUserId"), memberData)
        return batch.commit()
    }

    fun listenToUserOrganizationAccess(
        userId: String,
        onData: (members: Array<String>, requests: Array<String>) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        var active = true
        fun isCurrentAccount() = active && auth.currentUser?.uid == userId
        val snapshots = `in`.drarunbabu.drsharmila.jananisync.data.rbac.OrganizationAccessSnapshots { members, requests ->
            if (isCurrentAccount()) onData(members.toTypedArray(), requests.toTypedArray())
        }

        val membersQuery = query(
            collectionGroup(firestore, "members"),
            where("userId", "==", userId)
        )
        val requestsQuery = query(
            collectionGroup(firestore, "accessRequests"),
            where("userId", "==", userId)
        )
        val options = js("({ includeMetadataChanges: true })")
        val stopMembers = onSnapshot(membersQuery, options, { snapshot ->
            if (isCurrentAccount() && !snapshot.metadata.hasPendingWrites) snapshots.members(snapshot.docs.map { it.asMembershipJson() }, snapshot.metadata.fromCache)
        }, { error ->
            if (isCurrentAccount()) onError("Organisation membership query failed: ${error.message ?: "permission denied"}")
        })
        val stopRequests = onSnapshot(requestsQuery, options, { snapshot ->
            if (isCurrentAccount() && !snapshot.metadata.hasPendingWrites) snapshots.requests(snapshot.docs.map { it.asAccessRequestJson() }, snapshot.metadata.fromCache)
        }, { error ->
            if (isCurrentAccount()) onError("Access request query failed: ${error.message ?: "permission denied"}")
        })

        return {
            active = false
            stopMembers()
            stopRequests()
        }
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun listenToPendingOrganizationAccessRequests(
        orgId: String,
        onData: (Array<String>) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        val pendingRequests = query(
            collection(firestore, "organizations/$orgId/accessRequests"),
            where("status", "==", "pending")
        )
        return onSnapshot(pendingRequests, { snapshot ->
            onData(Array(snapshot.docs.length) { index -> snapshot.docs[index].asAccessRequestJson() })
        }, { error -> onError(error.message ?: "Could not load pending access requests.") })
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    fun listenToOrganizationMembers(
        orgId: String,
        onData: (Array<String>) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        val members = collection(firestore, "organizations/$orgId/members")
        return onSnapshot(members, { snapshot ->
            onData(Array(snapshot.docs.length) { index -> snapshot.docs[index].asMembershipJson() })
        }, { error -> onError(error.message ?: "Could not load organisation members.") })
    }

    fun updateOrganizationMemberAccessLevel(orgId: String, targetUserId: String, accessLevel: Int): Promise<Unit> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation code." }
        require(accessLevel in 10..99) { "Access level must be a whole number from 10 to 99." }
        val update = js("({})")
        update.accessLevel = accessLevel
        return updateDoc(doc(firestore, "organizations/$orgId/members/$targetUserId"), update)
    }

    fun setOrganizationMemberActive(orgId: String, targetUserId: String, active: Boolean): Promise<Unit> {
        require(orgId.matches(Regex("^[A-Za-z0-9]{8}$"))) { "Invalid organisation code." }
        val update = js("({})")
        update.status = if (active) "active" else "inactive"
        return updateDoc(doc(firestore, "organizations/$orgId/members/$targetUserId"), update)
    }

    /** Reads a member's organization-scoped public key profile (admin use for key grants). */
    fun getOrganizationMemberPublicKey(orgId: String, userId: String): Promise<dynamic> {
        require(validOrganizationCode(orgId)) { "Invalid organisation code." }
        return getDoc(doc(firestore, "organizations/$orgId/keyProfiles/$userId")).then { snapshot ->
            if (snapshot.exists()) snapshot.data() else null
        }
    }

    /** Reads only the current user's recovery-encrypted private-key backup. */
    fun getOwnEncryptedKeyBackup(orgId: String, userId: String): Promise<dynamic> {
        require(validOrganizationCode(orgId)) { "Invalid organisation code." }
        require(currentUser?.uid == userId) { "Only the signed-in account can load its key backup." }
        return getDoc(doc(firestore, "organizations/$orgId/keyBackups/$userId")).then { snapshot ->
            if (snapshot.exists()) snapshot.data() else null
        }
    }

    /** Reads organization key metadata; the document contains no secret key bytes. */
    fun getOrganizationKeyMetadata(orgId: String): Promise<dynamic> {
        require(validOrganizationCode(orgId)) { "Invalid organisation code." }
        return getDoc(doc(firestore, "organizations/$orgId/keyMetadata/current")).then { snapshot ->
            if (snapshot.exists()) snapshot.data() else null
        }
    }

    /** Observes metadata presence so pre-encryption organizations cleanly report `false`. */
    fun listenToOrganizationKeyMetadata(
        orgId: String,
        onExists: (Boolean) -> Unit,
        onError: (String) -> Unit
    ): () -> Unit {
        require(validOrganizationCode(orgId)) { "Invalid organisation code." }
        return onSnapshot(
            doc(firestore, "organizations/$orgId/keyMetadata/current"),
            js("({ includeMetadataChanges: true })"),
            { snapshot -> onExists(snapshot.exists()) },
            { error -> onError(error.message ?: "Could not check the organisation's encryption setup.") }
        )
    }

    fun listenToOrganizationKeyVersion(orgId: String, onData: (dynamic) -> Unit, onError: (String) -> Unit): () -> Unit =
        onSnapshot(doc(firestore, "organizations/$orgId/keyMetadata/current"), js("({})"),
            { snapshot -> onData(if (snapshot.exists()) snapshot.data() else null) },
            { error -> onError(error.message ?: "Could not verify the current key version.") })

    /** Admin read used only to display whether an active member has the current key version. */
    fun getOrganizationMemberKeyEnvelope(orgId: String, userId: String): Promise<dynamic> {
        require(validOrganizationCode(orgId)) { "Invalid organisation code." }
        return getDoc(doc(firestore, "organizations/$orgId/keyEnvelopes/$userId")).then { snapshot ->
            if (snapshot.exists()) snapshot.data() else null
        }
    }

    /** Creates the first version of an organization's key and grants its active admin access. */
    fun initializeOrganizationKey(
        orgId: String,
        adminUserId: String,
        keyMaterial: MemberKeyMaterial,
        wrappedKey: String
    ): Promise<Unit> {
        require(validOrganizationCode(orgId)) { "Invalid organisation code." }
        require(currentUser?.uid == adminUserId) { "The active account must be the organisation admin." }
        require(wrappedKey.isNotBlank() && wrappedKey.length <= 2048) { "Invalid encrypted organisation key." }
        val metadata = js("({})")
        metadata.currentKeyVersion = 1
        metadata.algorithm = "AES-256-GCM"
        metadata.updatedAt = serverTimestamp()
        metadata.updatedBy = adminUserId
        val envelope = organizationKeyEnvelope(adminUserId, 1, wrappedKey, adminUserId)
        val publicProfile = js("({})")
        publicProfile.userId = adminUserId
        publicProfile.publicKeyJwk = keyMaterial.publicKeyJwk
        publicProfile.publicKeyFingerprint = keyMaterial.publicKeyFingerprint
        publicProfile.keyAlgorithm = keyMaterial.keyAlgorithm
        publicProfile.createdAt = serverTimestamp()
        val privateBackup = js("({})")
        privateBackup.userId = adminUserId
        privateBackup.ciphertext = keyMaterial.privateKeyBackupCiphertext
        privateBackup.iv = keyMaterial.privateKeyBackupIv
        privateBackup.salt = keyMaterial.privateKeyBackupSalt
        privateBackup.kdf = keyMaterial.privateKeyBackupKdf
        privateBackup.kdfIterations = keyMaterial.privateKeyBackupIterations
        privateBackup.keyAlgorithm = keyMaterial.keyAlgorithm
        privateBackup.updatedAt = serverTimestamp()
        val profileReference = doc(firestore, "organizations/$orgId/keyProfiles/$adminUserId")
        val backupReference = doc(firestore, "organizations/$orgId/keyBackups/$adminUserId")
        return getDoc(profileReference).then { existingProfile ->
            getDoc(backupReference).then { existingBackup ->
                if (existingProfile.exists()) {
                    require(existingProfile.data().publicKeyFingerprint == keyMaterial.publicKeyFingerprint) {
                        "The prepared admin key does not match the organisation's registered public key."
                    }
                }
                if (existingBackup.exists()) {
                    require(existingBackup.data().ciphertext == keyMaterial.privateKeyBackupCiphertext) {
                        "The prepared admin backup does not match the stored encrypted private key."
                    }
                }
                val batch = writeBatch(firestore)
                if (!existingProfile.exists()) batch.set(profileReference, publicProfile)
                if (!existingBackup.exists()) batch.set(backupReference, privateBackup)
                batch.set(doc(firestore, "organizations/$orgId/keyMetadata/current"), metadata)
                batch.set(doc(firestore, "organizations/$orgId/keyEnvelopes/$adminUserId"), envelope)
                batch.commit()
            }
        }
    }

    /** Grants the existing current organization key to an active member's public key. */
    fun grantOrganizationKeyEnvelope(
        orgId: String,
        targetUserId: String,
        keyVersion: Int,
        wrappedKey: String,
        adminUserId: String
    ): Promise<Unit> {
        require(validOrganizationCode(orgId)) { "Invalid organisation code." }
        require(currentUser?.uid == adminUserId) { "The active account must be the granting admin." }
        require(keyVersion > 0 && wrappedKey.isNotBlank() && wrappedKey.length <= 2048) {
            "Invalid organisation key envelope."
        }
        return setDoc(
            doc(firestore, "organizations/$orgId/keyEnvelopes/$targetUserId"),
            organizationKeyEnvelope(targetUserId, keyVersion, wrappedKey, adminUserId)
        )
    }

    private fun organizationKeyEnvelope(userId: String, version: Int, wrappedKey: String, grantedBy: String): dynamic {
        val envelope = js("({})")
        envelope.userId = userId
        envelope.keyVersion = version
        envelope.wrappedKey = wrappedKey
        envelope.algorithm = "RSA-OAEP-3072-SHA256"
        envelope.grantedBy = grantedBy
        envelope.createdAt = serverTimestamp()
        return envelope
    }

    private fun currentDataKeyVersion(orgId: String): Int = `in`.drarunbabu.drsharmila.jananisync.data.OrganizationKeyVault.keys.value[orgId]
        ?.let { `in`.drarunbabu.drsharmila.jananisync.data.OrganizationKeyRing.version(it) }
        ?: error("Unlock the current organisation key before writing.")

    private fun validOrganizationCode(orgId: String) = orgId.matches(Regex("^[A-Za-z0-9]{8}$"))

    private fun QueryDocumentSnapshot.asMembershipJson(): String {
        val source = data(js("({serverTimestamps: 'estimate'})"))
        val record = js("({})")
        record.orgId = ref.path.unsafeCast<String>().split("/").getOrNull(1).orEmpty()
        record.userId = source.userId
        record.name = if (jsTypeOf(source.name) == "string") source.name else "Name not provided"
        record.role = source.role
        record.status = source.status
        record.joinedAt = timestampMillisOrZero(source.joinedAt)
        record.accessLevel = if (jsTypeOf(source.accessLevel) == "number") source.accessLevel else 10
        return JSON.stringify(record).unsafeCast<String>()
    }

    private fun QueryDocumentSnapshot.asAccessRequestJson(): String {
        val source = data(js("({serverTimestamps: 'estimate'})"))
        val record = js("({})")
        record.orgId = ref.path.unsafeCast<String>().split("/").getOrNull(1).orEmpty()
        record.userId = source.userId
        record.userName = source.userName
        record.userEmail = if (jsTypeOf(source.userEmail) == "string") source.userEmail else ""
        record.requestedRole = source.requestedRole
        record.status = source.status
        record.createdAt = timestampMillisOrZero(source.createdAt)
        return JSON.stringify(record).unsafeCast<String>()
    }

    /** Handles serverTimestamp placeholders and old/malformed cached documents without crashing listeners. */
    private fun timestampMillisOrZero(value: dynamic): Double {
        if (value == null || jsTypeOf(value) == "undefined") return 0.0
        if (jsTypeOf(value) == "number") return value.unsafeCast<Double>()
        return try {
            value.toMillis() as Double
        } catch (_: Throwable) {
            0.0
        }
    }
}

internal external interface FirebaseConfig {
    var apiKey: String
    var authDomain: String
    var projectId: String
    var storageBucket: String
    var messagingSenderId: String
    var appId: String
    var measurementId: String
}

internal external interface FirebaseApp
internal external interface FirebaseAuth {
    val currentUser: FirebaseUser?
    fun authStateReady(): Promise<Unit>
}
external interface FirebaseUser { val uid: String; val email: String?; val emailVerified: Boolean }
internal external interface FirebaseFirestore
internal external interface QueryReference
internal external interface QuerySnapshot { val docs: Array<QueryDocumentSnapshot>; val metadata: SnapshotMetadata }
internal external interface QueryDocumentSnapshot {
    val ref: DocumentReference
    val id: String
    val metadata: SnapshotMetadata
    fun data(options: dynamic = definedExternally): dynamic
}
internal external interface FirebaseError { val message: String? }
internal external interface UserCredential { val user: FirebaseUser }
internal external interface DocumentReference { val path: String; val id: String }
internal external interface DocumentSnapshot {
    val metadata: SnapshotMetadata
    fun exists(): Boolean
    fun data(): dynamic
}
internal external interface SnapshotMetadata { val hasPendingWrites: Boolean; val fromCache: Boolean }
