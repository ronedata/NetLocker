package com.netlocker.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.data.usage.AppDataUsageReader
import com.netlocker.data.usage.DataUsage
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

enum class AppSort { NAME, NAME_DESC, RESTRICTED_FIRST, DATA_USAGE_DESC, DATA_USAGE_ASC }

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
    private val usageReader: AppDataUsageReader,
) : ViewModel() {

    private val controls = MutableStateFlow(Controls())
    private val usageByUid = MutableStateFlow<Map<Int, DataUsage>>(emptyMap())
    private val needsUsageAccess = MutableStateFlow(false)

    /** The sort the user asked for while usage access was still missing — applied
     *  automatically if they grant it and come back, so they don't have to re-select it. */
    private var pendingUsageSort: AppSort? = null

    val needsUsageAccessPrompt: StateFlow<Boolean> = needsUsageAccess

    val uiState: StateFlow<AppsUiState> = combine(
        observeAppsWithRulesUseCase(includeSystemApps = true),
        preferencesManager.showSystemApps,
        installedAppRepository.isLoaded,
        controls,
        usageByUid,
    ) { allApps, showSystemApps, loaded, ctl, usage ->
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
            AppSort.NAME_DESC -> matching.sortedByDescending { it.app.label.lowercase() }
            AppSort.RESTRICTED_FIRST -> matching.sortedBy { it.rule.accessState == NetworkAccessState.ALLOWED }
            AppSort.DATA_USAGE_DESC -> matching.sortedByDescending { usage[it.app.uid]?.totalBytes ?: 0L }
            AppSort.DATA_USAGE_ASC -> matching.sortedBy { usage[it.app.uid]?.totalBytes ?: 0L }
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

    fun onSortSelected(sort: AppSort) {
        val isUsageSort = sort == AppSort.DATA_USAGE_DESC || sort == AppSort.DATA_USAGE_ASC
        if (!isUsageSort) {
            pendingUsageSort = null
            controls.update { it.copy(sort = sort) }
            return
        }
        if (!usageReader.hasUsageAccess()) {
            pendingUsageSort = sort
            needsUsageAccess.value = true
            return
        }
        applyUsageSort(sort)
    }

    private fun applyUsageSort(sort: AppSort) {
        viewModelScope.launch {
            usageByUid.value = usageReader.todayUsageForAllUids() ?: emptyMap()
            controls.update { it.copy(sort = sort) }
        }
    }

    /** User tapped "Open settings" on the usage-access prompt — keep [pendingUsageSort] so
     *  [onResumeCheckUsageAccess] can apply it automatically once they come back. */
    fun dismissUsageAccessPrompt() {
        needsUsageAccess.value = false
    }

    /** User tapped "Cancel" on the prompt — drop the sort they asked for. */
    fun cancelUsageSort() {
        pendingUsageSort = null
        needsUsageAccess.value = false
    }

    /** Called on every ON_RESUME; re-applies a sort the user picked before granting usage
     *  access, in case they just came back from Android's usage-access settings screen. */
    fun onResumeCheckUsageAccess() {
        val sort = pendingUsageSort
        if (sort != null && usageReader.hasUsageAccess()) {
            pendingUsageSort = null
            applyUsageSort(sort)
        }
    }

    fun usageAccessSettingsIntent() = usageReader.usageAccessSettingsIntent()

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
                    ServiceLocator.appDataUsageReader,
                )
            }
        }
    }
}
