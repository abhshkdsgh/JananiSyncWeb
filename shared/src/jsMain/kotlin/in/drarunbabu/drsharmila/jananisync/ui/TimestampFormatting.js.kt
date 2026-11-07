package `in`.drarunbabu.drsharmila.jananisync.ui

import kotlin.js.Date

internal actual fun localTimestampParts(epochMillis: Long): List<Int> {
    val date = Date(epochMillis.toDouble())
    return listOf(date.getFullYear(), date.getMonth() + 1, date.getDate(), date.getHours(), date.getMinutes(), date.getSeconds())
}

internal actual fun parseLocalChartTime(value: String): Long? {
    val millis = Date(value.replace(' ', 'T')).getTime()
    return if (millis.isFinite()) millis.toLong() else null
}
