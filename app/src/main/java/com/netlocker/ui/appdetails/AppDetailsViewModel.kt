package com.netlocker.ui.appdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.domain.model.AppWithRule
import com.netlocker.domain.usecase.DeleteRuleUseCase
import com.netlocker.domain.usecase.ObserveAppWithRuleUseCase
import com.netlocker.domain.usecase.UpdateNetworkRuleUseCase
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppDetailsViewModel(
    private val packageName: String,
    observeAppWithRuleUseCase: ObserveAppWithRuleUseCase,
    private val updateNetworkRuleUseCase: UpdateNetworkRuleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
) : ViewModel() {

    val appWithRule: StateFlow<AppWithRule?> = observeAppWithRuleUseCase(packageName)
        .map<AppWithRule, AppWithRule?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setWifiAllowed(wifiAllowed: Boolean, currentMobileDataAllowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, wifiAllowed, currentMobileDataAllowed) }
    }

    fun setMobileDataAllowed(currentWifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, currentWifiAllowed, mobileDataAllowed) }
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
                )
            }
        }
    }
}
