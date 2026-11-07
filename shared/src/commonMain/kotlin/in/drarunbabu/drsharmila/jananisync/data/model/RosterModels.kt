package `in`.drarunbabu.drsharmila.jananisync.data.model

import kotlinx.serialization.Serializable

/** Organization-scoped ward metadata. [wardId] is the generated Firestore document ID, assigned after encryption. */
@Serializable
data class Ward(
    val wardId: String,
    val wardName: String,
    val wardLocation: String,
    val wardHead: String
)

/**
 * Woman/admission roster shape. Date of birth is YYYYMMDD; admissionDate and lastUpdate
 * are epoch milliseconds. Phone numbers stay strings to preserve '+' prefixes and leading zeros.
 */
@Serializable
data class Woman(
    val cRNumber: Long,
    val iPNumber: Long,
    val name: String,
    val dateOfBirth: Int,
    val admissionDate: Long,
    val admittedWard: Ward,
    val lastUpdate: Long,
    val informantName: String,
    val contactNumber: String,
    val alternateContactNumber: String,
    /** Random opaque ID created once for this admission and stored only inside encrypted roster data. */
    val admissionId: String = "",
    @kotlinx.serialization.Transient val firestoreDocumentId: String = "",
    @kotlinx.serialization.Transient val isArchived: Boolean = false,
    @kotlinx.serialization.Transient val archiveRevision: Int = 0
)

/** Immutable archive action; actor name is decrypted only on this device. */
data class WomanArchiveEvent(
    val id: String,
    val womanDocumentId: String,
    val archived: Boolean,
    val revision: Int,
    val authorId: String,
    val authorName: String,
    val authorAccessLevel: Int,
    val createdAtMillis: Long
)

fun canArchiveWomen(accessLevel: Int): Boolean = accessLevel in 60..99
