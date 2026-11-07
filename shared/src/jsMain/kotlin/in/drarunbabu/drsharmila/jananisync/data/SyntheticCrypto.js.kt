package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.awaitCrypto
import kotlin.js.Promise

internal object SyntheticCrypto {
    private val global: dynamic get() = js("globalThis")

    private val importedKeys = mutableMapOf<String, dynamic>()
    private var keyGeneration = 0
    fun clearImportedKeys() { keyGeneration++; importedKeys.clear() }
    private suspend fun importedKey(keyText: String): dynamic {
        val existing = importedKeys[keyText]
        if (existing != null) return existing
        val generation = keyGeneration
        val raw = decodeBase64(keyText)
        require(raw.length == 32) { "Invalid key." }
        val key = global.crypto.subtle.importKey("raw", raw, js("({ name: 'AES-GCM' })"), false,
            arrayOf("encrypt", "decrypt")).unsafeCast<Promise<dynamic>>().awaitCrypto()
        if (generation == keyGeneration) importedKeys[keyText] = key
        return key
    }

    fun generateKey(): String {
        val bytes = global.crypto.getRandomValues(uint8Array(32))
        return encodeBase64(bytes)
    }

    /** A random, opaque, non-secret identifier suitable for admissions and observations. */
    fun generateOpaqueId(): String {
        val bytes = global.crypto.getRandomValues(uint8Array(16))
        return encodeBase64(bytes).replace('+', '-').replace('/', '_').trimEnd('=')
    }

    suspend fun encrypt(plaintext: String, keyText: String): Pair<String, String> {
        val cryptoKey = importedKey(OrganizationKeyRing.current(keyText))
        val iv = global.crypto.getRandomValues(uint8Array(12))
        val algorithm = js("({})")
        algorithm.name = "AES-GCM"
        algorithm.iv = iv
        val encoded = js("new TextEncoder()").unsafeCast<dynamic>().encode(plaintext)
        val encrypted = global.crypto.subtle.encrypt(algorithm, cryptoKey, encoded)
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        return encodeBase64(uint8Array(encrypted)) to encodeBase64(iv)
    }

    suspend fun decrypt(ciphertext: String, ivText: String, keyText: String): String {
        val cryptoKey = importedKey(OrganizationKeyRing.current(keyText))
        val algorithm = js("({})")
        algorithm.name = "AES-GCM"
        algorithm.iv = decodeBase64(ivText)
        val plaintext = global.crypto.subtle.decrypt(
            algorithm, cryptoKey, decodeBase64(ciphertext)
        ).unsafeCast<Promise<dynamic>>().awaitCrypto()
        return js("new TextDecoder()").unsafeCast<dynamic>().decode(plaintext) as String
    }

    private fun decodeBase64(value: String): dynamic {
        val binary = global.atob(value.trim()) as String
        val bytes = uint8Array(binary.length)
        for (index in binary.indices) bytes[index] = binary[index].code
        return bytes
    }

    private fun encodeBase64(bytes: dynamic): String {
        val length = bytes.length as Int
        val binary = StringBuilder(length)
        for (index in 0 until length) binary.append((bytes[index] as Int).toChar())
        return global.btoa(binary.toString()) as String
    }

    private fun uint8Array(value: dynamic): dynamic {
        val arguments = js("[]")
        arguments.push(value)
        return global.Reflect.construct(global.Uint8Array, arguments)
    }
}
