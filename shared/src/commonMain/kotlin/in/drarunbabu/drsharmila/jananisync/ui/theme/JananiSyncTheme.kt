package `in`.drarunbabu.drsharmila.jananisync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.AccentColour
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.AppearanceSettings
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.ThemeMode

val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }

private data class AccentPalette(
    val light: Long, val dark: Long, val container: Long, val onContainer: Long
)

private fun AccentColour.palette(): AccentPalette = when (this) {
    AccentColour.PURPLE -> AccentPalette(0xFF6750A4, 0xFFD0BCFF, 0xFFEADDFF, 0xFF21005D)
    AccentColour.BLUE -> AccentPalette(0xFF005AC1, 0xFFADC6FF, 0xFFD8E2FF, 0xFF001A41)
    AccentColour.TEAL -> AccentPalette(0xFF006A60, 0xFF80D5C7, 0xFF9EF2E3, 0xFF00201C)
    AccentColour.GREEN -> AccentPalette(0xFF386A20, 0xFF9CD67D, 0xFFB8F397, 0xFF062100)
    AccentColour.ORANGE -> AccentPalette(0xFF8B5000, 0xFFFFB86C, 0xFFFFDCC0, 0xFF2C1600)
    AccentColour.PINK -> AccentPalette(0xFF984061, 0xFFFFB0CB, 0xFFFFD9E3, 0xFF3E001D)
}

fun AccentColour.swatch(): Color = Color(palette().light)

/**
 * Scale at the dp/sp conversion boundary so all existing dimensions, Material
 * defaults, popups and canvas toPx() calls use baseline * (basicFontSize0 / 16).
 * Keep the platform fontScale so browser/system accessibility scaling still works.
 * Values passed as basicFontSize0 are normalised by the Mg components once.
 */
@Composable
fun JananiSyncTheme(settings: AppearanceSettings, content: @Composable () -> Unit) {
    val platformDensity = LocalDensity.current
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val palette = settings.accentColour.palette()
    val primary = Color(if (dark) palette.dark else palette.light)
    val onPrimary = if (dark) Color(palette.onContainer) else Color.White
    val container = Color(if (dark) palette.light else palette.container)
    val onContainer = Color(if (dark) palette.container else palette.onContainer)
    val scheme = if (dark) darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = container, onTertiaryContainer = onContainer,
        inversePrimary = Color(palette.light)
    ) else lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = primary, onTertiary = onPrimary,
        tertiaryContainer = container, onTertiaryContainer = onContainer,
        inversePrimary = Color(palette.dark)
    )
    CompositionLocalProvider(
        LocalAppearanceSettings provides settings,
        LocalDensity provides Density(platformDensity.density * settings.scale, platformDensity.fontScale)
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
