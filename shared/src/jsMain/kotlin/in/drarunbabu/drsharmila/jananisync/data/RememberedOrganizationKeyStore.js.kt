@file:Suppress("NON_EXPORTABLE_TYPE")

package `in`.drarunbabu.drsharmila.jananisync.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.js.Promise
import kotlin.js.jsTypeOf

/** Optional same-browser convenience cache. The AES wrapping key is non-extractable and stored in IndexedDB. */
internal object RememberedOrganizationKeyStore {
    private const val DATABASE = "jananisync-device-keys-v1"
    private const val STORE = "wrapped-organization-keys"
    private val global: dynamic get() = js("globalThis")

    suspend fun remember(userId: String, orgId: String, organizationKeyBase64: String) {
        val database = openDatabase()
        var wrappingKey: dynamic = get(database, deviceKeyId(userId))
        if (wrappingKey == null || jsTypeOf(wrappingKey) == "undefined") {
            wrappingKey = generateWrappingKey()
            put(database, deviceKeyId(userId), wrappingKey)
        }
        val iv = global.crypto.getRandomValues(uint8Array(12))
        val aad = "jananisync:v1:$userId:$orgId"
        val params = js("({})")
        params.name = "AES-GCM"
        params.iv = iv
        params.additionalData = textEncoder().encode(aad)
        val encrypted = awaitPromise(global.crypto.subtle.encrypt(
            params,
            wrappingKey,
            textEncoder().encode(organizationKeyBase64)
        ).unsafeCast<Promise<dynamic>>())
        val record = js("({})")
        record.iv = encodeBase64(iv)
        record.ciphertext = encodeBase64(uint8Array(encrypted))
        put(database, orgKeyId(userId, orgId), record)
    }

    suspend fun restore(userId: String, orgId: String): String? = runCatching {
        val database = openDatabase()
        val wrappingKey = get(database, deviceKeyId(userId)) ?: return null
        val record = get(database, orgKeyId(userId, orgId)) ?: return null
        val params = js("({})")
        params.name = "AES-GCM"
        params.iv = decodeBase64(record.iv as String)
        params.additionalData = textEncoder().encode("jananisync:v1:$userId:$orgId")
        val plaintext = awaitPromise(global.crypto.subtle.decrypt(
            params,
            wrappingKey,
            decodeBase64(record.ciphertext as String)
        ).unsafeCast<Promise<dynamic>>())
        textDecoder().decode(plaintext).unsafeCast<String>()
    }.getOrNull()

    suspend fun clearOrganization(userId: String, orgId: String) {
        val database = openDatabase()
        delete(database, orgKeyId(userId, orgId))
        delete(database, orgKeyId(userId, "$orgId:admin"))
    }

    suspend fun clearUser(userId: String) {
        val database = openDatabase()
        try {
            suspendCancellableCoroutine { continuation ->
                val transaction = database.transaction(arrayOf(STORE), "readwrite")
                val fail: (dynamic) -> Unit = {
                    if (continuation.isActive) continuation.resumeWithException(
                        IllegalStateException("Could not clear this device's remembered organisation keys.")
                    )
                }
                // Cursor exhaustion does not guarantee that the deletes have committed.
                transaction.oncomplete = { _: dynamic ->
                    if (continuation.isActive) continuation.resume(Unit)
                }
                transaction.onerror = fail
                transaction.onabort = fail
                val request = transaction.objectStore(STORE).openCursor()
                request.onerror = fail
                request.onsuccess = { _: dynamic ->
                    if (continuation.isActive) {
                        try {
                            val cursor = request.result
                            if (jsTypeOf(cursor) != "undefined" && cursor != null) {
                                val key = cursor.key as? String ?: ""
                                if (key == deviceKeyId(userId) || key.startsWith("organization:$userId:")) cursor.delete()
                                // cursor is already dynamic: call the native method directly.
                                cursor["continue"]()
                            }
                        } catch (error: Throwable) {
                            runCatching { transaction.abort() }
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    }
                }
            }
        } finally {
            database.close()
        }
    }

    private suspend fun generateWrappingKey(): dynamic {
        val algorithm = js("({})")
        algorithm.name = "AES-GCM"
        algorithm.length = 256
        return awaitPromise(global.crypto.subtle.generateKey(algorithm, false, arrayOf("encrypt", "decrypt")).unsafeCast<Promise<dynamic>>())
    }

    private suspend fun openDatabase(): dynamic = suspendCancellableCoroutine { continuation ->
        val request = global.indexedDB.open(DATABASE, 1)
        request.onupgradeneeded = { _: dynamic ->
            val database = request.result
            if (!database.objectStoreNames.contains(STORE)) database.createObjectStore(STORE)
            Unit
        }
        request.onerror = { _: dynamic ->
            if (continuation.isActive) continuation.resumeWithException(
                IllegalStateException("This browser could not open secure device storage.")
            )
        }
        request.onsuccess = { _: dynamic ->
            if (continuation.isActive) continuation.resume(request.result)
        }
    }

    private suspend fun get(database: dynamic, key: String): dynamic {
        val request = database.transaction(arrayOf(STORE), "readonly").objectStore(STORE).get(key)
        return awaitRequest(request)
    }

    private suspend fun put(database: dynamic, key: String, value: dynamic) {
        val request = database.transaction(arrayOf(STORE), "readwrite").objectStore(STORE).put(value, key)
        awaitRequest(request)
    }

    private suspend fun delete(database: dynamic, key: String) {
        val request = database.transaction(arrayOf(STORE), "readwrite").objectStore(STORE).delete(key)
        awaitRequest(request)
    }

    private suspend fun awaitRequest(request: dynamic): dynamic = suspendCancellableCoroutine { continuation ->
        request.onerror = { _: dynamic ->
            if (continuation.isActive) continuation.resumeWithException(
                IllegalStateException("Secure device storage operation failed.")
            )
        }
        request.onsuccess = { _: dynamic ->
            if (continuation.isActive) continuation.resume(request.result)
        }
    }

    private suspend fun awaitPromise(promise: Promise<dynamic>): dynamic = suspendCancellableCoroutine { continuation ->
        promise.then(
            { result: dynamic -> if (continuation.isActive) continuation.resume(result) },
            { error: dynamic ->
                if (continuation.isActive) continuation.resumeWithException(
                    IllegalStateException(error.message as? String ?: "Web Crypto operation failed.")
                )
            }
        )
    }

    private fun encodeBase64(bytes: dynamic): String {
        var binary = ""
        for (index in 0 until (bytes.length as Int)) binary += global.String.fromCharCode(bytes[index]).unsafeCast<String>()
        return global.btoa(binary).unsafeCast<String>()
    }

    private fun decodeBase64(value: String): dynamic {
        val binary = global.atob(value).unsafeCast<String>()
        val bytes = uint8Array(binary.length)
        for (index in binary.indices) bytes[index] = binary[index].code
        return bytes
    }

    private fun uint8Array(value: dynamic): dynamic {
        val arguments = js("[]")
        arguments.push(value)
        return global.Reflect.construct(global.Uint8Array, arguments)
    }

    private fun textEncoder(): dynamic = js("new TextEncoder()")

    private fun textDecoder(): dynamic = js("new TextDecoder()")

    private fun deviceKeyId(userId: String) = "device:$userId"
    private fun orgKeyId(userId: String, orgId: String) = "organization:$userId:$orgId"
}
