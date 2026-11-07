package `in`.drarunbabu.drsharmila.jananisync.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Encoded only in memory or inside the encrypted remembered-device store. */
@Serializable
internal data class OrganizationKeyRing(val currentVersion: Int, val keys: Map<Int, String>) {
    fun encode(): String = Json.encodeToString(serializer(), this)
    companion object {
        fun parse(value: String): OrganizationKeyRing = if (value.startsWith("{")) Json.decodeFromString(serializer(), value)
            else OrganizationKeyRing(1, mapOf(1 to value))
        fun current(value: String): String { val ring = parse(value); return ring.keys.getValue(ring.currentVersion) }
        fun version(value: String): Int = parse(value).currentVersion
        fun key(value: String, version: Int): String = parse(value).keys[version] ?: error("Unlock the current organisation key to read this key version.")
    }
}
