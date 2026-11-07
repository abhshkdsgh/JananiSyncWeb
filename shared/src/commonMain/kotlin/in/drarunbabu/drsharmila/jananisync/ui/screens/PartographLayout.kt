package `in`.drarunbabu.drsharmila.jananisync.ui.screens

import kotlin.math.roundToInt

/** All chart coordinates are dp; drawing, previews and hit testing share these bounds. */
internal data class PartographSection(val top: Float, val height: Float, val min: Double, val max: Double) {
    val bottom: Float get() = top + height
    fun y(value: Double): Float = top + ((max - value.coerceIn(min, max)) / (max - min)).toFloat() * height
    fun valueAtY(y: Float): Int = (max - (y - top) / height * (max - min)).roundToInt().coerceIn(min.toInt(), max.toInt())
}

internal data class PartographLane(val field: String, val label: String, val top: Float, val height: Float) {
    val bottom: Float get() = top + height
}

internal object PartographLayout {
    // Keep clinical coordinates stable while giving the chart a readable default display size.
    const val DISPLAY_SCALE = 2f
    const val PLOT_LEFT = 150f
    const val PLOT_WIDTH = 940f
    const val PLOT_RIGHT = PLOT_LEFT + PLOT_WIDTH
    const val HOURS = 12
    const val HALF_HOURS = HOURS * 2
    const val CHART_WIDTH = PLOT_RIGHT + PLOT_WIDTH / HOURS + 16f
    const val CHART_HEIGHT = 1316f
    const val HOURS_TOP = 530f
    const val HOURS_HEIGHT = 24f
    const val CLOCK_TOP = HOURS_TOP + HOURS_HEIGHT
    const val CLOCK_HEIGHT = 38f

    val fetal = PartographSection(30f, 156f, 80.0, 200.0)
    val cervix = PartographSection(270f, 260f, 0.0, 10.0)
    val contractions = PartographSection(618f, 110f, 0.0, 5.0)
    val vitals = PartographSection(958f, 180f, 60.0, 180.0)

    val lanes = listOf(
        PartographLane("amnioticFluid", "Amniotic fluid", 196f, 28f),
        PartographLane("moulding", "Moulding", 224f, 28f),
        PartographLane("oxytocin", "Oxytocin U/L\n(drops/min)", 748f, 44f),
        PartographLane("medicine", "Drugs given", 800f, 76f),
        PartographLane("intravenousFluids", "IV fluids", 876f, 64f),
        PartographLane("temperature", "Temperature °C", 1156f, 36f),
        PartographLane("urineProtein", "Urine · protein", 1200f, 32f),
        PartographLane("urineAcetone", "Urine · acetone", 1232f, 32f),
        PartographLane("urineVolume", "Urine · volume (ml)", 1264f, 32f)
    )

    fun hourToX(hour: Double): Float = PLOT_LEFT + (hour / HOURS).toFloat() * PLOT_WIDTH
    fun laneTextAnchor(hour: Double): Float = hourToX(hour)
    fun xToElapsedHour(x: Float): Double = ((x - PLOT_LEFT).toDouble() / PLOT_WIDTH * HOURS)
        .coerceIn(0.0, HOURS.toDouble())
    /** A tap belongs to the column containing it, rather than the nearest grid line. */
    fun xToHour(x: Float, halfHourly: Boolean = true): Double {
        val subdivisions = if (halfHourly) 2 else 1
        val elapsed = xToElapsedHour(x)
        return (kotlin.math.floor(elapsed * subdivisions + 0.000001) / subdivisions)
            .coerceIn(0.0, HOURS.toDouble())
    }
}

internal val partographLanes: List<PartographLane> = PartographLayout.lanes
