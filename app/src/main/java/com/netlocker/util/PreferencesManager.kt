package com.netlocker.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "netlocker_settings")

enum class AppTheme { SYSTEM, LIGHT, DARK }

/** In-app text size. [scale] replaces the phone's font-size setting for NetLocker's own
 *  screens; [SYSTEM] (null scale) follows the phone's setting instead. The default is
 *  [DEFAULT] — a fixed 1.0 — so an enlarged system font doesn't make the app oversized. */
enum class TextSize(val scale: Float?) {
    SMALL(0.85f),
    DEFAULT(1.0f),
    LARGE(1.15f),
    SYSTEM(null),
}

/** Persists the Settings-screen preferences (spec §22) — separate from network rules,
 *  which live in Room (see data/local); these are simple app-behavior toggles. */
class PreferencesManager(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        val AUTO_REFRESH = booleanPreferencesKey("auto_refresh")
        val MINIMAL_NOTIFICATION = booleanPreferencesKey("minimal_notification")
        val TEXT_SIZE = stringPreferencesKey("text_size")
    }

    val textSize: Flow<TextSize> = context.dataStore.data.map { prefs ->
        prefs[Keys.TEXT_SIZE]?.let { runCatching { TextSize.valueOf(it) }.getOrNull() } ?: TextSize.DEFAULT
    }

    suspend fun setTextSize(size: TextSize) {
        context.dataStore.edit { it[Keys.TEXT_SIZE] = size.name }
    }

    /** Off by default: the ordinary (low-importance) firewall notification. On = the
     *  smallest notification Android allows — see network/FirewallNotificationSpec. */
    val minimalNotification: Flow<Boolean> = context.dataStore.data.map { it[Keys.MINIMAL_NOTIFICATION] ?: false }

    suspend fun setMinimalNotification(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MINIMAL_NOTIFICATION] = enabled }
    }

    val theme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM
    }

    val showSystemApps: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_SYSTEM_APPS] ?: false }

    val autoRefresh: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_REFRESH] ?: true }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setShowSystemApps(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SYSTEM_APPS] = show }
    }

    suspend fun setAutoRefresh(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_REFRESH] = enabled }
    }
}
