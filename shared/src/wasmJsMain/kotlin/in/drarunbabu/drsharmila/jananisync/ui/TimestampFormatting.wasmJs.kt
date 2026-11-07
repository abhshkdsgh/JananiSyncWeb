package `in`.drarunbabu.drsharmila.jananisync.ui

@JsFun("(millis) => { const d = new Date(millis); return [d.getFullYear(), d.getMonth() + 1, d.getDate(), d.getHours(), d.getMinutes(), d.getSeconds()].join('|'); }")
private external fun localDateParts(millis: Double): String

internal actual fun localTimestampParts(epochMillis: Long): List<Int> =
    localDateParts(epochMillis.toDouble()).split('|').map(String::toInt)

@JsFun("(value) => new Date(value.replace(' ', 'T')).getTime()")
private external fun parseChartDate(value: String): Double
internal actual fun parseLocalChartTime(value: String): Long? = parseChartDate(value).takeIf { it.isFinite() }?.toLong()
