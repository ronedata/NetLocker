package com.netlocker.ui.datausage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.data.usage.AppDataUsageReader
import com.netlocker.data.usage.DataUsage
import com.netlocker.domain.model.InstalledApp
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class AppUsageRow(val app: InstalledApp, val usage: DataUsage)

sealed interface DataUsageUiState {
    data object Loading : DataUsageUiState
    data object NeedsAccess : DataUsageUiState
    data object Unavailable : DataUsageUiState
    data class Loaded(val rows: List<AppUsageRow>, val totalWifiBytes: Long, val totalMobileBytes: Long) : DataUsageUiState
}

/** A single screen listing every app's data usage today, instead of checking one app at a
 *  time on its own App Details page. Reads the same [AppDataUsageReader] source (and needs
 *  the same "Usage access" permission) as App Details and the Apps tab's usage sorts. */
class DataUsageViewModel(
    private val installedAppRepository: InstalledAppRepository,
    private val usageReader: AppDataUsageReader,
) : ViewModel() {

    private val _state = MutableStateFlow<DataUsageUiState>(DataUsageUiState.Loading)
    val state: StateFlow<DataUsageUiState> = _state.asStateFlow()

    /** Re-checks permission and reloads — called on first composition and whenever the
     *  screen resumes (e.g. returning from the usage-access settings page). */
    fun load() {
        viewModelScope.launch {
            _state.value = DataUsageUiState.Loading
            if (!usageReader.hasUsageAccess()) {
                _state.value = DataUsageUiState.NeedsAccess
                return@launch
            }
            val usageByUid = usageReader.todayUsageForAllUids()
            if (usageByUid == null) {
                _state.value = DataUsageUiState.Unavailable
                return@launch
            }
            val apps = installedAppRepository.observeInstalledApps(includeSystemApps = true).first()
            val rows = apps.mapNotNull { app ->
                val usage = usageByUid[app.uid] ?: return@mapNotNull null
                if (usage.totalBytes <= 0L) null else AppUsageRow(app, usage)
            }.sortedByDescending { it.usage.totalBytes }
            _state.value = DataUsageUiState.Loaded(
                rows = rows,
                totalWifiBytes = rows.sumOf { it.usage.wifiBytes },
                totalMobileBytes = rows.sumOf { it.usage.mobileBytes },
            )
        }
    }

    fun usageAccessSettingsIntent() = usageReader.usageAccessSettingsIntent()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                DataUsageViewModel(ServiceLocator.installedAppRepository, ServiceLocator.appDataUsageReader)
            }
        }
    }
}
