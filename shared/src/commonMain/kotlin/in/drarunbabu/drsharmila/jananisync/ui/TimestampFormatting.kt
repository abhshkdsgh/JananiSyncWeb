package `in`.drarunbabu.drsharmila.jananisync.ui

import kotlin.time.Instant

/** Display only: stored instants remain UTC; UI dates use the viewer's local time zone. */
fun formatUiTimestamp(iso: String): String = runCatching {
    formatUiTimestamp(Instant.parse(iso).toEpochMilliseconds())
}.getOrElse { iso }

fun formatUiTimestamp(epochMillis: Long): String {
    val parts = localTimestampParts(epochMillis)
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    fun pad(value: Int) = value.toString().padStart(2, '0')
    return "${pad(parts[2])} ${months[parts[1] - 1]} ${parts[0].toString().padStart(4, '0')} ${pad(parts[3])}:${pad(parts[4])}:${pad(parts[5])}"
}

internal expect fun localTimestampParts(epochMillis: Long): List<Int>

internal expect fun parseLocalChartTime(value: String): Long?
internal fun editableChartTime(epochMillis: Long): String {
    val p = localTimestampParts(epochMillis)
    fun pad(n: Int) = n.toString().padStart(2, '0')
    return "${p[0]}-${pad(p[1])}-${pad(p[2])} ${pad(p[3])}:${pad(p[4])}"
}

/** Offset at the selected instant, including any historical daylight-saving offset. */
internal fun chartTimezoneLabel(epochMillis: Long): String {
    val wallMillis = Instant.parse("${editableChartTime(epochMillis).replace(' ', 'T')}:00Z").toEpochMilliseconds()
    val roundedInstant = kotlin.math.floor(epochMillis / 60_000.0).toLong() * 60_000
    val offset = ((wallMillis - roundedInstant) / 60_000).toInt()
    val absolute = kotlin.math.abs(offset)
    return "UTC${if (offset >= 0) "+" else "-"}${(absolute / 60).toString().padStart(2, '0')}:${(absolute % 60).toString().padStart(2, '0')}"
}
