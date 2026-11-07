package `in`.drarunbabu.drsharmila.jananisync.data.preferences

enum class ThemeMode(val label: String) {
    SYSTEM("System default"), LIGHT("Light"), DARK("Dark")
}

enum class AccentColour(val label: String) {
    PURPLE("Purple"), BLUE("Blue"), TEAL("Teal"), GREEN("Green"), ORANGE("Orange"), PINK("Pink")
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val basicFontSize0: Int = 16,
    val accentColour: AccentColour = AccentColour.PURPLE
) {
    val scale: Float get() = basicFontSize0 / 16f

    companion object {
        const val MIN_FONT_SIZE = 12
        const val MAX_FONT_SIZE = 24
    }
}
