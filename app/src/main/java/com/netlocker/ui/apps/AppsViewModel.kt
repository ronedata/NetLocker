package com.netlocker.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.domain.model.AppCategory
import com.netlocker.domain.model.AppWithRule
import com.netlocker.domain.model.NetworkAccessState
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.usecase.ObserveAppsWithRulesUseCase
import com.netlocker.domain.usecase.UpdateNetworkRuleUseCase
import com.netlocker.util.PreferencesManager
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppCategoryFilter { ALL, GAMES, SOCIAL, SYSTEM }

enum class AppSort { NAME, RESTRICTED_FIRST }

data class AppsUiState(
    val apps: List<AppWithRule> = emptyList(),
    val counts: Map<AppCategoryFilter, Int> = emptyMap(),
    val category: AppCategoryFilter = AppCategoryFilter.ALL,
    val query: String = "",
    val sort: AppSort = AppSort.NAME,
    /** True until the very first installed-app scan finishes. */
    val isLoading: Boolean = true,
)

private data class Controls(
    val category: AppCategoryFilter = AppCategoryFilter.ALL,
    val query: String = "",
    val sort: AppSort = AppSort.NAME,
)

class AppsViewModel(
    observeAppsWithRulesUseCase: ObserveAppsWithRulesUseCase,
    private val updateNetworkRuleUseCase: UpdateNetworkRuleUseCase,
    private val installedAppRepository: InstalledAppRepository,
    preferencesManager: PreferencesManager,
) : ViewModel() {

    private val controls = MutableStateFlow(Controls())

    val uiState: StateFlow<AppsUiState> = combine(
        observeAppsWithRulesUseCase(includeSystemApps = true),
        preferencesManager.showSystemApps,
        installedAppRepository.isLoaded,
        controls,
    ) { allApps, showSystemApps, loaded, ctl ->
        val userApps = allApps.filterNot { it.app.isSystemApp }
        val systemApps = allApps.filter { it.app.isSystemApp }

        val counts = mapOf(
            AppCategoryFilter.ALL to if (showSystemApps) allApps.size else userApps.size,
            AppCategoryFilter.GAMES to userApps.count { it.app.category == AppCategory.GAME },
            AppCategoryFilter.SOCIAL to userApps.count { it.app.category == AppCategory.SOCIAL },
            AppCategoryFilter.SYSTEM to systemApps.size,
        )

        val inCategory = when (ctl.category) {
            // Searching looks through system apps too, even when they're hidden from the plain
            // list — otherwise a preinstalled app like Chrome can't be found by name.
            AppCategoryFilter.ALL -> if (showSystemApps || ctl.query.isNotBlank()) allApps else userApps
            AppCategoryFilter.GAMES -> userApps.filter { it.app.category == AppCategory.GAME }
            AppCategoryFilter.SOCIAL -> userApps.filter { it.app.category == AppCategory.SOCIAL }
            AppCategoryFilter.SYSTEM -> systemApps
        }

        val query = ctl.query.trim()
        val matching = if (query.isEmpty()) {
            inCategory
        } else {
            inCategory.filter {
                it.app.label.contains(query, ignoreCase = true) ||
                    it.app.packageName.contains(query, ignoreCase = true)
            }
        }

        val sorted = when (ctl.sort) {
            AppSort.NAME -> matching // already user-apps-first, alphabetical from the use case
            AppSort.RESTRICTED_FIRST -> matching.sortedBy { it.rule.accessState == NetworkAccessState.ALLOWED }
        }

        AppsUiState(
            apps = sorted,
            counts = counts,
            category = ctl.category,
            query = ctl.query,
            sort = ctl.sort,
            isLoading = !loaded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun onCategorySelected(category: AppCategoryFilter) = controls.update { it.copy(category = category) }

    fun onQueryChange(query: String) = controls.update { it.copy(query = query) }

    fun onSortSelected(sort: AppSort) = controls.update { it.copy(sort = sort) }

    fun setWifiAllowed(packageName: String, allowed: Boolean, currentMobileDataAllowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, allowed, currentMobileDataAllowed) }
    }

    fun setMobileDataAllowed(packageName: String, currentWifiAllowed: Boolean, allowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, currentWifiAllowed, allowed) }
    }

    fun refreshApps() {
        viewModelScope.launch { installedAppRepository.refresh() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                AppsViewModel(
                    ServiceLocator.observeAppsWithRulesUseCase,
                    ServiceLocator.updateNetworkRuleUseCase,
                    ServiceLocator.installedAppRepository,
                    ServiceLocator.preferencesManager,
                )
            }
        }
    }
}
