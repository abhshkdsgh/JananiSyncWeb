package `in`.drarunbabu.drsharmila.jananisync.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    val appearance: Flow<AppearanceSettings> = dataStore.data.map { preferences ->
        AppearanceSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == preferences[THEME_KEY] } ?: ThemeMode.SYSTEM,
            basicFontSize0 = preferences[FONT_SIZE_KEY]
                ?.takeIf { it in AppearanceSettings.MIN_FONT_SIZE..AppearanceSettings.MAX_FONT_SIZE } ?: 16,
            accentColour = AccentColour.entries.firstOrNull { it.name == preferences[ACCENT_KEY] } ?: AccentColour.PURPLE
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_KEY] = mode.name }
    }

    suspend fun setBasicFontSize0(size: Int) {
        require(size in AppearanceSettings.MIN_FONT_SIZE..AppearanceSettings.MAX_FONT_SIZE)
        dataStore.edit { it[FONT_SIZE_KEY] = size }
    }

    suspend fun setAccentColour(colour: AccentColour) {
        dataStore.edit { it[ACCENT_KEY] = colour.name }
    }

    val showGreeting: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_GREETING_KEY] ?: false
    }

    suspend fun setShowGreeting(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_GREETING_KEY] = show
        }
    }

    private companion object {
        val THEME_KEY = stringPreferencesKey("appearance_theme")
        val FONT_SIZE_KEY = intPreferencesKey("appearance_font_size")
        val ACCENT_KEY = stringPreferencesKey("appearance_accent")
        val SHOW_GREETING_KEY = booleanPreferencesKey("show_greeting")
    }
}
