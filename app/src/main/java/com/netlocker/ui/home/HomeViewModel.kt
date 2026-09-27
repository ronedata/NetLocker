package com.netlocker.ui.home

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.domain.model.AppWithRule
import com.netlocker.domain.model.FirewallStatus
import com.netlocker.domain.usecase.ObserveAppsWithRulesUseCase
import com.netlocker.domain.usecase.UpdateNetworkRuleUseCase
import com.netlocker.network.FirewallController
import com.netlocker.util.PreferencesManager
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val apps: List<AppWithRule> = emptyList(),
    val searchQuery: String = "",
    val firewallStatus: FirewallStatus = FirewallStatus.Stopped,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val observeAppsWithRulesUseCase: ObserveAppsWithRulesUseCase,
    private val updateNetworkRuleUseCase: UpdateNetworkRuleUseCase,
    private val firewallController: FirewallController,
    preferencesManager: PreferencesManager,
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")

    val uiState: StateFlow<HomeUiState> = combine(
        preferencesManager.showSystemApps.flatMapLatest { showSystem -> observeAppsWithRulesUseCase(showSystem) },
        searchQuery,
        firewallController.status,
    ) { apps, query, status ->
        val filtered = if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.app.label.contains(query, ignoreCase = true) || it.app.packageName.contains(query, ignoreCase = true) }
        }
        HomeUiState(apps = filtered, searchQuery = query, firewallStatus = status, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun setWifiAllowed(packageName: String, wifiAllowed: Boolean, currentMobileDataAllowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, wifiAllowed, currentMobileDataAllowed) }
    }

    fun setMobileDataAllowed(packageName: String, currentWifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, currentWifiAllowed, mobileDataAllowed) }
    }

    /** Null means the VPN permission is already granted and [startFirewall] can be called directly. */
    fun vpnPermissionIntent(): Intent? = firewallController.vpnPermissionIntent()

    fun startFirewall() = firewallController.start()

    fun stopFirewall() = firewallController.stop()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    ServiceLocator.observeAppsWithRulesUseCase,
                    ServiceLocator.updateNetworkRuleUseCase,
                    ServiceLocator.firewallController,
                    ServiceLocator.preferencesManager,
                )
            }
        }
    }
}
