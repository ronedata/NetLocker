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
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val SCHEDULE_MASTER_ENABLED = booleanPreferencesKey("schedule_master_enabled")
        val SHOW_BLOCKED_DESTINATIONS = booleanPreferencesKey("show_blocked_destinations")
        val QUICK_SETTINGS_TILE_ADDED = booleanPreferencesKey("quick_settings_tile_added")
    }

    /** Off by default: whether the Quick Settings tile is currently added to the panel.
     *  Only ever set from real system callbacks (FirewallTileService's onTileAdded /
     *  onTileRemoved, and the result of requestAddTileService) — never assumed. */
    val quickSettingsTileAdded: Flow<Boolean> = context.dataStore.data.map { it[Keys.QUICK_SETTINGS_TILE_ADDED] ?: false }

    suspend fun setQuickSettingsTileAdded(added: Boolean) {
        context.dataStore.edit { it[Keys.QUICK_SETTINGS_TILE_ADDED] = added }
    }

    /** Off by default: whether each blocked attempt's destination is logged (App Details
     *  → "Blocked today" → recent attempts). This is sensitive — close to a connection
     *  log — so nothing is written unless this is on, and turning it back off deletes
     *  everything already logged; see ServiceLocator's wiring of BlockedAttemptTracker and
     *  SettingsViewModel.setShowBlockedDestinations. */
    val showBlockedDestinations: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_BLOCKED_DESTINATIONS] ?: false }

    suspend fun setShowBlockedDestinations(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_BLOCKED_DESTINATIONS] = enabled }
    }

    /** Off by default: the master switch for the whole "block on a schedule" feature.
     *  While off, no per-app schedule (however set) is enforced, and no per-app schedule
     *  UI is shown — see network/ScheduleEvaluator. */
    val scheduleMasterEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.SCHEDULE_MASTER_ENABLED] ?: false }

    suspend fun setScheduleMasterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCHEDULE_MASTER_ENABLED] = enabled }
    }

    /** Off by default: the firewall only restarts by itself after a reboot/update if the
     *  user opted in (see network/BootReceiver). */
    val autoStartOnBoot: Flow<Boolean> = context.dataStore.data.map { it[Keys.AUTO_START_ON_BOOT] ?: false }

    suspend fun setAutoStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_START_ON_BOOT] = enabled }
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
