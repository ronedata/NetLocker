package com.netlocker.util

import android.content.Context
import com.netlocker.data.local.NetLockerDatabase
import com.netlocker.data.repository.InstalledAppRepositoryImpl
import com.netlocker.data.repository.NetworkRuleRepositoryImpl
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.repository.NetworkRuleRepository
import com.netlocker.domain.usecase.ObserveAppWithRuleUseCase
import com.netlocker.domain.usecase.ObserveAppsWithRulesUseCase
import com.netlocker.domain.usecase.UpdateNetworkRuleUseCase
import com.netlocker.network.ConnectionOwnerResolver
import com.netlocker.network.FirewallController
import com.netlocker.network.FirewallControllerImpl
import com.netlocker.network.RuleIndex
import com.netlocker.network.TransportMonitor

/**
 * Hand-rolled composition root. NetLocker's dependency graph is small and static
 * enough that a DI framework (Hilt/Koin) would be pure overhead with no real payoff
 * — see spec §26 "no unnecessary dependencies". Everything here is a plain lazy
 * singleton scoped to the process, initialized once from [com.netlocker.NetLockerApplication].
 */
object ServiceLocator {

    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val database by lazy { NetLockerDatabase.getInstance(appContext) }

    val networkRuleRepository: NetworkRuleRepository by lazy {
        NetworkRuleRepositoryImpl(database.appRuleDao())
    }

    val installedAppRepository: InstalledAppRepository by lazy {
        InstalledAppRepositoryImpl(appContext)
    }

    val preferencesManager: PreferencesManager by lazy { PreferencesManager(appContext) }

    val firewallController: FirewallController by lazy { FirewallControllerImpl(appContext) }

    // Shared with NetLockerVpnService so the running tunnel and the rest of the app
    // observe the exact same rule/transport state.
    val ruleIndex: RuleIndex by lazy { RuleIndex(installedAppRepository, networkRuleRepository) }
    val transportMonitor: TransportMonitor by lazy { TransportMonitor(appContext) }
    fun newConnectionOwnerResolver(): ConnectionOwnerResolver = ConnectionOwnerResolver(appContext)

    val observeAppsWithRulesUseCase by lazy {
        ObserveAppsWithRulesUseCase(installedAppRepository, networkRuleRepository)
    }
    val observeAppWithRuleUseCase by lazy {
        ObserveAppWithRuleUseCase(installedAppRepository, networkRuleRepository)
    }
    val updateNetworkRuleUseCase by lazy {
        UpdateNetworkRuleUseCase(networkRuleRepository, firewallController)
    }
}
