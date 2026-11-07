package `in`.drarunbabu.drsharmila.jananisync.ui.screens

internal actual fun openChartExport(pages: String, title: String): Boolean =
    js("typeof window.jananiChartExport === 'function' && window.jananiChartExport(pages, title)") as Boolean
