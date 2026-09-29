package com.netlocker.ui.appdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.data.usage.AppDataUsageReader
import com.netlocker.data.usage.DataUsage
import com.netlocker.domain.model.AppWithRule
import com.netlocker.domain.model.BlockedStat
import com.netlocker.domain.repository.BlockedStatsRepository
import com.netlocker.domain.usecase.DeleteRuleUseCase
import com.netlocker.domain.usecase.ObserveAppWithRuleUseCase
import com.netlocker.domain.usecase.SetScheduleUseCase
import com.netlocker.domain.usecase.UpdateNetworkRuleUseCase
import com.netlocker.util.PreferencesManager
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the "Today's usage" card shows — the real reading, or why there isn't one. */
sealed interface UsageUiState {
    data object Loading : UsageUiState
    data object NeedsAccess : UsageUiState
    data object Unavailable : UsageUiState
    data class Loaded(val usage: DataUsage) : UsageUiState
}

class AppDetailsViewModel(
    private val packageName: String,
    observeAppWithRuleUseCase: ObserveAppWithRuleUseCase,
    private val updateNetworkRuleUseCase: UpdateNetworkRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    private val usageReader: AppDataUsageReader,
    private val setScheduleUseCase: SetScheduleUseCase,
    blockedStatsRepository: BlockedStatsRepository,
    preferencesManager: PreferencesManager,
) : ViewModel() {

    /** Settings' Schedule master switch — while off, App Details hides the Schedule section. */
    val scheduleMasterEnabled: StateFlow<Boolean> = preferencesManager.scheduleMasterEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSchedule(enabled: Boolean, startMinute: Int, endMinute: Int, days: Int) {
        viewModelScope.launch { setScheduleUseCase(packageName, enabled, startMinute, endMinute, days) }
    }

    /** Today's blocked-connection tally for this app (null until something was blocked). */
    val blockedToday: StateFlow<BlockedStat?> = blockedStatsRepository.observeToday()
        .map { it[packageName] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val appWithRule: StateFlow<AppWithRule?> = observeAppWithRuleUseCase(packageName)
        .map<AppWithRule, AppWithRule?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _usage = MutableStateFlow<UsageUiState>(UsageUiState.Loading)
    val usage: StateFlow<UsageUiState> = _usage.asStateFlow()

    /** Re-reads today's data usage (also called when the user comes back from the system
     *  "Usage access" screen, so a fresh grant shows up without reopening the page). */
    fun loadUsage(uid: Int) {
        viewModelScope.launch {
            if (!usageReader.hasUsageAccess()) {
                _usage.value = UsageUiState.NeedsAccess
                return@launch
            }
            _usage.value = usageReader.todayUsage(uid)?.let { UsageUiState.Loaded(it) } ?: UsageUiState.Unavailable
        }
    }

    fun usageAccessSettingsIntent() = usageReader.usageAccessSettingsIntent()

    fun setWifiAllowed(wifiAllowed: Boolean, currentMobileDataAllowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, wifiAllowed, currentMobileDataAllowed) }
    }

    fun setMobileDataAllowed(currentWifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, currentWifiAllowed, mobileDataAllowed) }
    }

    /** Adds this app to the Rules tab with its current access (fully allowed unless the user
     *  already flipped a switch), so it can be managed there. */
    fun addToRules(wifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, wifiAllowed, mobileDataAllowed) }
    }

    /** Removes NetLocker's rule for this app — it returns to default (fully allowed). */
    fun resetToDefault() {
        viewModelScope.launch { deleteRuleUseCase(packageName) }
    }

    companion object {
        fun factory(packageName: String) = viewModelFactory {
            initializer {
                AppDetailsViewModel(
                    packageName,
                    ServiceLocator.observeAppWithRuleUseCase,
                    ServiceLocator.updateNetworkRuleUseCase,
                    ServiceLocator.deleteRuleUseCase,
                    ServiceLocator.appDataUsageReader,
                    ServiceLocator.setScheduleUseCase,
                    ServiceLocator.blockedStatsRepository,
                    ServiceLocator.preferencesManager,
                )
            }
        }
    }
}
