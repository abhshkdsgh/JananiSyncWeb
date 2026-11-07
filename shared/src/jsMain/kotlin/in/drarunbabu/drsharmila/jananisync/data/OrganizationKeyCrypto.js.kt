@file:Suppress("NON_EXPORTABLE_TYPE")

package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.data.model.MemberKeyMaterial
import `in`.drarunbabu.drsharmila.jananisync.awaitCrypto
import kotlin.js.JSON
import kotlin.js.Promise

/** Browser Web Crypto helpers. Plain keys stay in memory and are never sent to Firestore. */
internal object OrganizationKeyCrypto {
    private val global: dynamic get() = js("globalThis")
    private val subtle: dynamic get() = global.crypto.subtle

    suspend fun generateRecoveryPhrase(): String = Bip39Mnemonic.generate()

    suspend fun isValidRecoveryPhrase(phrase: String): Boolean = Bip39Mnemonic.isValid(phrase)

    /** Generate a member RSA keypair and encrypt its private JWK backup with the recovery phrase. */
    suspend fun createMemberKeyMaterial(userId: String, recoveryPhrase: String): MemberKeyMaterial {
        require(userId.isNotBlank()) { "A signed-in user is required to create a member key." }
        require(Bip39Mnemonic.isValid(recoveryPhrase)) {
            "The recovery phrase is not a valid 12-word BIP-39 phrase."
        }
        val algorithm = rsaAlgorithm()
        val pair = subtle.generateKey(algorithm, true, arrayOf("encrypt", "decrypt"))
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        val publicJwk = subtle.exportKey("jwk", pair.publicKey)
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        val privateJwk = subtle.exportKey("jwk", pair.privateKey)
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        val publicJwkJson = JSON.stringify(publicJwk).unsafeCast<String>()
        val privateJwkJson = JSON.stringify(privateJwk).unsafeCast<String>()

        val salt = randomBytes(16)
        val iv = randomBytes(12)
        val wrappingKey = deriveRecoveryKey(recoveryPhrase, salt)
        val encryptedPrivateJwk = subtle.encrypt(
            aesGcm(iv, userId), wrappingKey, textEncoder().encode(privateJwkJson)
        )
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        val fingerprint = publicFingerprint(publicJwk.n as String, publicJwk.e as String)

        return MemberKeyMaterial(
            publicKeyJwk = publicJwkJson,
            publicKeyFingerprint = fingerprint,
            privateKeyBackupCiphertext = encodeBase64(uint8Array(encryptedPrivateJwk)),
            privateKeyBackupIv = encodeBase64(iv),
            privateKeyBackupSalt = encodeBase64(salt)
        )
    }

    /** Reconstitute the non-exportable runtime private key from its encrypted backup. */
    suspend fun unlockMemberPrivateKey(
        userId: String,
        material: MemberKeyMaterial,
        recoveryPhrase: String
    ): dynamic {
        require(userId.isNotBlank()) { "A signed-in user is required to unlock this member key." }
        val registeredPublicJwk = JSON.parse<dynamic>(material.publicKeyJwk)
        require(publicFingerprint(registeredPublicJwk.n as String, registeredPublicJwk.e as String) == material.publicKeyFingerprint.replace('+', '-').replace('/', '_').trimEnd('=')) {
            "The registered member public key fingerprint is invalid."
        }
        val wrappingKey = deriveRecoveryKey(recoveryPhrase, decodeBase64(material.privateKeyBackupSalt))
        val privateJwkBytes = try {
            subtle.decrypt(
                aesGcm(decodeBase64(material.privateKeyBackupIv), userId),
                wrappingKey,
                decodeBase64(material.privateKeyBackupCiphertext)
            ).unsafeCast<Promise<dynamic>>().awaitCrypto()
        } catch (error: Throwable) {
            throw IllegalArgumentException(
                "That phrase did not unlock this account's private key. Enter the 12-word phrase this doctor saved when their account key was created. It is not the admin's phrase or the organisation key.",
                error
            )
        }
        val privateJwkJson = textDecoder().decode(privateJwkBytes).unsafeCast<String>()
        val privateJwk = JSON.parse<dynamic>(privateJwkJson)
        require(publicFingerprint(privateJwk.n as String, privateJwk.e as String) == material.publicKeyFingerprint.replace('+', '-').replace('/', '_').trimEnd('=')) {
            "This recovery phrase does not unlock the registered member key."
        }
        val imported = subtle.importKey(
            "jwk",
            privateJwk,
            rsaAlgorithm(),
            false,
            arrayOf("decrypt")
        ).unsafeCast<Promise<dynamic>>().awaitCrypto()
        return imported
    }

    /** Generate one random 256-bit organization key. Persist only member-wrapped copies. */
    fun generateOrganizationDataKey(): String = encodeBase64(randomBytes(32))

    suspend fun fingerprintPublicKey(publicKeyJwk: String): String {
        val jwk = JSON.parse<dynamic>(publicKeyJwk)
        return publicFingerprint(jwk.n as String, jwk.e as String)
    }

    /**
     * Domain-separated admin-only key for authenticating recipient pins and resumable journals.
     * Unlike the shared DEK, this secret cannot be reconstructed by a removed member. Its
     * source is the recovery-encrypted RSA private exponent; the derived key stays in memory.
     */
    suspend fun adminControlKey(orgId: String, userId: String, material: MemberKeyMaterial, phrase: String): String {
        val recoveryKey = deriveRecoveryKey(phrase, decodeBase64(material.privateKeyBackupSalt))
        val bytes = subtle.decrypt(aesGcm(decodeBase64(material.privateKeyBackupIv), userId), recoveryKey,
            decodeBase64(material.privateKeyBackupCiphertext)).unsafeCast<Promise<dynamic>>().awaitCrypto()
        val jwk = JSON.parse<dynamic>(textDecoder().decode(bytes) as String)
        require(publicFingerprint(jwk.n as String, jwk.e as String) == material.publicKeyFingerprint.replace('+', '-').replace('/', '_').trimEnd('=')) {
            "The recovery key does not match this account."
        }
        val secret = jwk.d as String
        val digest = subtle.digest("SHA-256", textEncoder().encode(
            "jananisync/admin-control/v1\n$orgId\n$userId\n$secret"))
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        return encodeBase64(uint8Array(digest))
    }

    suspend fun wrapOrganizationDataKey(
        dataKeyBase64: String,
        recipientPublicJwk: String,
        expectedFingerprint: String
    ): String {
        val rawKey = decodeBase64(dataKeyBase64)
        require((rawKey.length as Int) == 32) { "Organisation keys must contain 32 random bytes." }
        val jwk = JSON.parse<dynamic>(recipientPublicJwk)
        require(publicFingerprint(jwk.n as String, jwk.e as String) == expectedFingerprint.replace('+', '-').replace('/', '_').trimEnd('=')) {
            "The recipient public key fingerprint does not match the approved member key."
        }
        val publicKey = subtle.importKey(
            "jwk",
            jwk,
            rsaAlgorithm(),
            false,
            arrayOf("encrypt")
        ).unsafeCast<Promise<dynamic>>().awaitCrypto()
        val wrapped = subtle.encrypt(js("({ name: 'RSA-OAEP' })"), publicKey, rawKey)
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        return encodeBase64(uint8Array(wrapped))
    }

    suspend fun unwrapOrganizationDataKey(wrappedKeyBase64: String, privateKey: dynamic): String {
        val rawKey = try {
            subtle.decrypt(
                js("({ name: 'RSA-OAEP' })"),
                privateKey,
                decodeBase64(wrappedKeyBase64)
            ).unsafeCast<Promise<dynamic>>().awaitCrypto()
        } catch (error: Throwable) {
            throw IllegalStateException(
                "Your account key unlocked, but it could not decrypt this organisation's key grant. Ask the admin to share the key again with this exact account.",
                error
            )
        }
        require((uint8Array(rawKey).length as Int) == 32) { "The unwrapped organisation key is invalid." }
        return encodeBase64(uint8Array(rawKey))
    }

    private suspend fun deriveRecoveryKey(phrase: String, salt: dynamic): dynamic {
        require(Bip39Mnemonic.isValid(phrase)) {
            "The recovery phrase is not a valid 12-word BIP-39 phrase."
        }
        val passwordMaterial = subtle.importKey(
            "raw", textEncoder().encode(Bip39Mnemonic.normalize(phrase)), "PBKDF2", false, arrayOf("deriveKey")
        ).unsafeCast<Promise<dynamic>>().awaitCrypto()
        val parameters = js("({})")
        parameters.name = "PBKDF2"
        parameters.salt = salt
        parameters.iterations = RECOVERY_KDF_ITERATIONS
        parameters.hash = "SHA-256"
        val keyAlgorithm = js("({})")
        keyAlgorithm.name = "AES-GCM"
        keyAlgorithm.length = 256
        return subtle.deriveKey(parameters, passwordMaterial, keyAlgorithm, false, arrayOf("decrypt", "encrypt"))
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
    }

    private fun rsaAlgorithm(): dynamic {
        val algorithm = js("({})")
        algorithm.name = "RSA-OAEP"
        algorithm.modulusLength = 3072
        algorithm.publicExponent = uint8ArrayFromInts(1, 0, 1)
        algorithm.hash = "SHA-256"
        return algorithm
    }

    private fun aesGcm(iv: dynamic, userId: String? = null): dynamic {
        val algorithm = js("({})")
        algorithm.name = "AES-GCM"
        algorithm.iv = iv
        algorithm.tagLength = 128
        if (userId != null) algorithm.additionalData = textEncoder().encode(userId)
        return algorithm
    }

    private suspend fun publicFingerprint(modulus: String, exponent: String): String {
        val digest = subtle.digest("SHA-256", textEncoder().encode("$modulus.$exponent"))
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        return encodeBase64(uint8Array(digest)).replace('+', '-').replace('/', '_').trimEnd('=')
    }

    private fun randomBytes(length: Int): dynamic = global.crypto.getRandomValues(uint8Array(length))

    private fun textEncoder(): dynamic = js("new TextEncoder()")
    private fun textDecoder(): dynamic = js("new TextDecoder()")

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

    private fun uint8Array(length: Int): dynamic {
        val args = js("[]")
        args.push(length)
        return global.Reflect.construct(global.Uint8Array, args)
    }

    private fun uint8ArrayFromInts(vararg values: Int): dynamic {
        val array = uint8Array(values.size)
        values.forEachIndexed { index, value -> array[index] = value }
        return array
    }

    private const val RECOVERY_KDF_ITERATIONS = 600_000
}

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual object MemberKeyCryptoPlatform {
    actual suspend fun generateRecoveryPhrase(): String = OrganizationKeyCrypto.generateRecoveryPhrase()
    actual suspend fun isValidRecoveryPhrase(phrase: String): Boolean =
        OrganizationKeyCrypto.isValidRecoveryPhrase(phrase)

    actual suspend fun createMemberKeyMaterial(
        userId: String,
        recoveryPhrase: String
    ): MemberKeyMaterial = OrganizationKeyCrypto.createMemberKeyMaterial(userId, recoveryPhrase)
}
