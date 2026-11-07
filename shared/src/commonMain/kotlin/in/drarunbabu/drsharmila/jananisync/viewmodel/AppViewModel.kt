package `in`.drarunbabu.drsharmila.jananisync.viewmodel

import `in`.drarunbabu.drsharmila.jananisync.data.preferences.UserPreferencesRepository
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.AppearanceSettings
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.ThemeMode
import `in`.drarunbabu.drsharmila.jananisync.data.preferences.AccentColour
import kotlinx.coroutines.CancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel(
    private val preferences: UserPreferencesRepository
) : ViewModel() {

    private val mutableAppearance = MutableStateFlow<AppearanceSettings?>(null)
    val appearance: StateFlow<AppearanceSettings?> = mutableAppearance.asStateFlow()
    private val mutableAppearanceError = MutableStateFlow<String?>(null)
    val appearanceError: StateFlow<String?> = mutableAppearanceError.asStateFlow()

    fun clearAppearanceError() { mutableAppearanceError.value = null }

    private fun saveAppearance(update: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                update()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableAppearanceError.value = "Could not save appearance settings. Please try again."
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) = saveAppearance { preferences.setThemeMode(mode) }
    fun setBasicFontSize0(size: Int) = saveAppearance { preferences.setBasicFontSize0(size) }
    fun setAccentColour(colour: AccentColour) = saveAppearance { preferences.setAccentColour(colour) }

    init {
        viewModelScope.launch {
            try {
                preferences.appearance.collect { mutableAppearance.value = it }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableAppearance.value = AppearanceSettings()
                mutableAppearanceError.value = "Could not load saved appearance settings. Using defaults."
            }
        }
    }
}
