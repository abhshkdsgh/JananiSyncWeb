package `in`.drarunbabu.drsharmila.jananisync.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppearanceSettingsTest {
    private class MemoryStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun defaultsAndSettingsSurviveRepositoryRecreationWithoutChangingOtherPreferences() = runTest {
        val store = MemoryStore()
        val firstSession = UserPreferencesRepository(store)
        assertEquals(AppearanceSettings(), firstSession.appearance.first())
        firstSession.setShowGreeting(true)
        firstSession.setThemeMode(ThemeMode.DARK)
        firstSession.setBasicFontSize0(20)
        firstSession.setAccentColour(AccentColour.TEAL)
        val nextSession = UserPreferencesRepository(store)
        assertEquals(AppearanceSettings(ThemeMode.DARK, 20, AccentColour.TEAL), nextSession.appearance.first())
        assertEquals(true, nextSession.showGreeting.first())
        nextSession.setThemeMode(ThemeMode.SYSTEM)
        assertEquals(AppearanceSettings(ThemeMode.SYSTEM, 20, AccentColour.TEAL), firstSession.appearance.first())
    }

    @Test
    fun invalidStoredValuesUseDefaultsAndInvalidWritesAreRejected() = runTest {
        val store = MemoryStore()
        store.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[stringPreferencesKey("appearance_theme")] = "unknown"
                this[stringPreferencesKey("appearance_accent")] = "unknown"
                this[intPreferencesKey("appearance_font_size")] = 0
            }
        }
        val repository = UserPreferencesRepository(store)
        assertEquals(AppearanceSettings(), repository.appearance.first())
        assertFailsWith<IllegalArgumentException> { repository.setBasicFontSize0(11) }
        assertFailsWith<IllegalArgumentException> { repository.setBasicFontSize0(25) }
    }

    @Test
    fun densityScalesDimensionsAndTextExactlyOnceWhileKeepingPlatformFontScale() {
        for (size in listOf(12, 16, 20, 24)) {
            val settings = AppearanceSettings(basicFontSize0 = size)
            val platformDensity = 2f
            val platformFontScale = 1.25f
            with(Density(platformDensity * settings.scale, platformFontScale)) {
                assertEquals(48f * settings.scale * platformDensity, 48.dp.toPx(), 0.001f)
                assertEquals(28f * settings.scale * platformDensity * platformFontScale, 28.sp.toPx(), 0.001f)
                val componentFont = (size / settings.scale).sp
                assertEquals(size * platformDensity * platformFontScale, componentFont.toPx(), 0.001f)
            }
        }
    }
}
