package `in`.drarunbabu.drsharmila.jananisync.viewmodel

actual fun currentTimestampIso(): String = js("new Date().toISOString()") as String
actual fun currentTimeMillis(): Long = js("Date.now()").unsafeCast<Double>().toLong()
