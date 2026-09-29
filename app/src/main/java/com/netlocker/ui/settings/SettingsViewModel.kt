package com.netlocker.ui.settings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.domain.model.AppUpdate
import com.netlocker.domain.model.UpdateCheckResult
import com.netlocker.domain.repository.BlockedEventsRepository
import com.netlocker.domain.repository.UpdateRepository
import com.netlocker.update.ApkInstaller
import com.netlocker.update.DownloadOutcome
import com.netlocker.util.AppTheme
import com.netlocker.util.PreferencesManager
import com.netlocker.util.ServiceLocator
import com.netlocker.util.TextSize
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val showSystemApps: Boolean = false,
    val autoRefresh: Boolean = true,
    val minimalNotification: Boolean = false,
    val textSize: TextSize = TextSize.DEFAULT,
    val autoStartOnBoot: Boolean = false,
    val scheduleMasterEnabled: Boolean = false,
    val showBlockedDestinations: Boolean = false,
)

/** Never assumes success — mirrors exactly what the GitHub check / download actually
 *  returned at each step (spec's "no fake success" principle applies to updates too). */
/** `combine` has no built-in 4-tuple; this is only used to shuttle four booleans out of
 *  one `combine` call above. */
private data class Quadruple<out A, out B, out C, out D>(val first: A, val second: B, val third: C, val fourth: D)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val update: AppUpdate) : UpdateUiState
    data object NeedsInstallPermission : UpdateUiState
    data object Downloading : UpdateUiState
    data class Error(val message: String) : UpdateUiState
}

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val updateRepository: UpdateRepository,
    private val apkInstaller: ApkInstaller,
    private val blockedEventsRepository: BlockedEventsRepository,
) : ViewModel() {

    // Two groups, because `combine` only takes five typed flows at once.
    private val displayPrefs = combine(
        preferencesManager.theme,
        preferencesManager.showSystemApps,
        preferencesManager.autoRefresh,
        preferencesManager.textSize,
    ) { theme, showSystemApps, autoRefresh, textSize ->
        SettingsUiState(theme = theme, showSystemApps = showSystemApps, autoRefresh = autoRefresh, textSize = textSize)
    }

    private val moreSwitches = combine(
        preferencesManager.minimalNotification,
        preferencesManager.autoStartOnBoot,
        preferencesManager.scheduleMasterEnabled,
        preferencesManager.showBlockedDestinations,
    ) { minimalNotification, autoStartOnBoot, scheduleMasterEnabled, showBlockedDestinations ->
        Quadruple(minimalNotification, autoStartOnBoot, scheduleMasterEnabled, showBlockedDestinations)
    }

    val uiState: StateFlow<SettingsUiState> = combine(displayPrefs, moreSwitches) { display, more ->
        display.copy(
            minimalNotification = more.first,
            autoStartOnBoot = more.second,
            scheduleMasterEnabled = more.third,
            showBlockedDestinations = more.fourth,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(theme: AppTheme) = viewModelScope.launch { preferencesManager.setTheme(theme) }
    fun setShowSystemApps(show: Boolean) = viewModelScope.launch { preferencesManager.setShowSystemApps(show) }
    fun setAutoRefresh(enabled: Boolean) = viewModelScope.launch { preferencesManager.setAutoRefresh(enabled) }

    fun setTextSize(size: TextSize) = viewModelScope.launch { preferencesManager.setTextSize(size) }
    fun setMinimalNotification(enabled: Boolean) = viewModelScope.launch { preferencesManager.setMinimalNotification(enabled) }

    fun setAutoStartOnBoot(enabled: Boolean) = viewModelScope.launch { preferencesManager.setAutoStartOnBoot(enabled) }

    fun setScheduleMasterEnabled(enabled: Boolean) = viewModelScope.launch { preferencesManager.setScheduleMasterEnabled(enabled) }

    /** Turning this off deletes every already-logged destination immediately — the point
     *  of the toggle is that this data shouldn't exist once the user says so, not just
     *  that it's hidden while off. */
    fun setShowBlockedDestinations(enabled: Boolean) = viewModelScope.launch {
        preferencesManager.setShowBlockedDestinations(enabled)
        if (!enabled) blockedEventsRepository.clearAll()
    }

    private val _updateState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    private val _installIntentRequests = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val installIntentRequests: SharedFlow<Intent> = _installIntentRequests

    private var pendingUpdate: AppUpdate? = null

    fun checkForUpdate() {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Checking
            _updateState.value = when (val result = updateRepository.checkForUpdate()) {
                UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheckResult.UpdateAvailable -> {
                    pendingUpdate = result.update
                    UpdateUiState.Available(result.update)
                }
                is UpdateCheckResult.Error -> UpdateUiState.Error(result.message)
            }
        }
    }

    /** Called when the user taps "Update" on an [UpdateUiState.Available] card. */
    fun startUpdate() {
        val update = pendingUpdate ?: return
        if (!apkInstaller.hasInstallPermission()) {
            _updateState.value = UpdateUiState.NeedsInstallPermission
            return
        }
        downloadAndInstall(update)
    }

    /** Called after the user returns from the "Install unknown apps" system screen
     *  ([ApkInstaller.requestInstallPermissionIntent]) — re-checks for real rather than
     *  assuming they granted it. */
    fun onReturnedFromInstallPermissionScreen() {
        val update = pendingUpdate ?: return
        _updateState.value = if (apkInstaller.hasInstallPermission()) {
            downloadAndInstall(update)
            UpdateUiState.Downloading
        } else {
            UpdateUiState.Available(update) // still not granted — let them retry deliberately
        }
    }

    fun requestInstallPermissionIntent(): Intent = apkInstaller.requestInstallPermissionIntent()

    private fun downloadAndInstall(update: AppUpdate) {
        viewModelScope.launch {
            _updateState.value = UpdateUiState.Downloading
            val downloadId = apkInstaller.enqueueDownload(update.downloadUrl, update.versionName)
            when (val outcome = apkInstaller.awaitDownload(downloadId)) {
                DownloadOutcome.Success -> {
                    _installIntentRequests.tryEmit(apkInstaller.installIntentFor(downloadId))
                    _updateState.value = UpdateUiState.Idle
                }
                is DownloadOutcome.Failed -> _updateState.value = UpdateUiState.Error(outcome.reason)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel(
                    ServiceLocator.preferencesManager,
                    ServiceLocator.updateRepository,
                    ServiceLocator.apkInstaller,
                    ServiceLocator.blockedEventsRepository,
                )
            }
        }
    }
}
