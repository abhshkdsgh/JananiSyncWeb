package `in`.drarunbabu.drsharmila.jananisync.ui.screens

@JsFun("(pages, title) => typeof window.jananiChartExport === 'function' && window.jananiChartExport(pages, title)")
internal actual external fun openChartExport(pages: String, title: String): Boolean
