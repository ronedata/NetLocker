package com.netlocker.util

import android.content.Context
import com.netlocker.BuildConfig
import com.netlocker.data.local.NetLockerDatabase
import com.netlocker.data.repository.GithubUpdateRepositoryImpl
import com.netlocker.data.repository.InstalledAppRepositoryImpl
import com.netlocker.data.repository.NetworkRuleRepositoryImpl
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.repository.NetworkRuleRepository
import com.netlocker.domain.repository.UpdateRepository
import com.netlocker.domain.usecase.ObserveAppWithRuleUseCase
import com.netlocker.domain.usecase.DeleteRuleUseCase
import com.netlocker.domain.usecase.ObserveAppsWithRulesUseCase
import com.netlocker.domain.usecase.ObserveRulesWithAppsUseCase
import com.netlocker.domain.usecase.SetRuleEnabledUseCase
import com.netlocker.domain.usecase.UpdateNetworkRuleUseCase
import com.netlocker.network.ConnectionOwnerResolver
import com.netlocker.network.FirewallController
import com.netlocker.network.FirewallControllerImpl
import com.netlocker.network.RuleIndex
import com.netlocker.network.TransportMonitor
import com.netlocker.update.ApkInstaller

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

    val observeRulesWithAppsUseCase by lazy {
        ObserveRulesWithAppsUseCase(installedAppRepository, networkRuleRepository)
    }
    val setRuleEnabledUseCase by lazy {
        SetRuleEnabledUseCase(networkRuleRepository, firewallController)
    }
    val deleteRuleUseCase by lazy {
        DeleteRuleUseCase(networkRuleRepository, firewallController)
    }

    val updateRepository: UpdateRepository by lazy {
        GithubUpdateRepositoryImpl(currentVersionName = BuildConfig.VERSION_NAME)
    }
    val apkInstaller: ApkInstaller by lazy { ApkInstaller(appContext) }
    val appDataUsageReader: com.netlocker.data.usage.AppDataUsageReader by lazy {
        com.netlocker.data.usage.AppDataUsageReader(appContext)
    }
}
