package com.thevaguebox.probabilistictictactoe.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemePreference { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val reducedMotion: Boolean = false,
    val theme: ThemePreference = ThemePreference.SYSTEM,
)

private val Context.settingsDataStore by preferencesDataStore(name = "pic_pac_settings")

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data.map { values ->
        AppSettings(
            sound = values[SOUND] ?: true,
            haptics = values[HAPTICS] ?: true,
            reducedMotion = values[REDUCED_MOTION] ?: false,
            theme = values[THEME]?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
                ?: ThemePreference.SYSTEM,
        )
    }

    suspend fun setSound(enabled: Boolean) = dataStore.edit { it[SOUND] = enabled }
    suspend fun setHaptics(enabled: Boolean) = dataStore.edit { it[HAPTICS] = enabled }
    suspend fun setReducedMotion(enabled: Boolean) = dataStore.edit { it[REDUCED_MOTION] = enabled }
    suspend fun setTheme(theme: ThemePreference) = dataStore.edit { it[THEME] = theme.name }

    private companion object {
        val SOUND = booleanPreferencesKey("sound")
        val HAPTICS = booleanPreferencesKey("haptics")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val THEME = stringPreferencesKey("theme")
    }
}
