@file:Suppress("NON_EXPORTABLE_TYPE")

package `in`.drarunbabu.drsharmila.jananisync.data

import `in`.drarunbabu.drsharmila.jananisync.awaitCrypto
import kotlin.js.Promise

/** BIP-0039 128-bit entropy / 12-word English mnemonic generation and checksum validation. */
internal object Bip39Mnemonic {
    private val global: dynamic get() = js("globalThis")

    suspend fun generate(): String {
        val entropy = global.crypto.getRandomValues(uint8Array(16))
        val digest = global.crypto.subtle.digest("SHA-256", entropy)
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        val hash = uint8ArrayFromBuffer(digest)
        val bits = buildString(132) {
            for (index in 0 until 16) append(byteBits(entropy[index] as Int))
            append(byteBits(hash[0] as Int).take(4))
        }
        return (0 until 12).joinToString(" ") { position ->
            val wordIndex = bits.substring(position * 11, position * 11 + 11).toInt(2)
            bip39EnglishWords[wordIndex]
        }
    }

    suspend fun isValid(phrase: String): Boolean {
        val words = normalize(phrase).split(' ').filter(String::isNotEmpty)
        if (words.size != 12) return false
        val wordBits = StringBuilder(132)
        for (word in words) {
            val index = bip39EnglishWords.indexOf(word)
            if (index < 0) return false
            wordBits.append(index.toString(2).padStart(11, '0'))
        }
        val entropyBits = wordBits.substring(0, 128)
        val checksumBits = wordBits.substring(128, 132)
        val entropy = uint8Array(16)
        for (byteIndex in 0 until 16) {
            entropy[byteIndex] = entropyBits.substring(byteIndex * 8, byteIndex * 8 + 8).toInt(2)
        }
        val digest = global.crypto.subtle.digest("SHA-256", entropy)
            .unsafeCast<Promise<dynamic>>().awaitCrypto()
        val expectedChecksum = byteBits(uint8ArrayFromBuffer(digest)[0] as Int).take(4)
        return checksumBits == expectedChecksum
    }

    fun normalize(phrase: String): String =
        (global.String(phrase.trim().lowercase()).normalize("NFKD") as String)
            .trim()
            .split(Regex("\\s+"))
            .filter(String::isNotEmpty)
            .joinToString(" ")

    private fun byteBits(value: Int): String = (7 downTo 0).joinToString("") { shift ->
        if (((value shr shift) and 1) == 1) "1" else "0"
    }

    private fun uint8Array(length: Int): dynamic {
        val args = js("[]")
        args.push(length)
        return global.Reflect.construct(global.Uint8Array, args)
    }

    private fun uint8ArrayFromBuffer(buffer: dynamic): dynamic {
        val args = js("[]")
        args.push(buffer)
        return global.Reflect.construct(global.Uint8Array, args)
    }
}
