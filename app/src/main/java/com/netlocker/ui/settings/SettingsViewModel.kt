package com.netlocker.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.util.AppTheme
import com.netlocker.util.PreferencesManager
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val showSystemApps: Boolean = false,
    val autoRefresh: Boolean = true,
)

class SettingsViewModel(private val preferencesManager: PreferencesManager) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesManager.theme,
        preferencesManager.showSystemApps,
        preferencesManager.autoRefresh,
    ) { theme, showSystemApps, autoRefresh ->
        SettingsUiState(theme, showSystemApps, autoRefresh)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(theme: AppTheme) = viewModelScope.launch { preferencesManager.setTheme(theme) }
    fun setShowSystemApps(show: Boolean) = viewModelScope.launch { preferencesManager.setShowSystemApps(show) }
    fun setAutoRefresh(enabled: Boolean) = viewModelScope.launch { preferencesManager.setAutoRefresh(enabled) }

    companion object {
        val Factory = viewModelFactory {
            initializer { SettingsViewModel(ServiceLocator.preferencesManager) }
        }
    }
}
