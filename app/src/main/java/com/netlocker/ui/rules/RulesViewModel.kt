package com.netlocker.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.domain.model.InstalledApp
import com.netlocker.domain.model.RuleCounts
import com.netlocker.domain.model.RuleFilter
import com.netlocker.domain.model.countRules
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.usecase.DeleteRuleUseCase
import com.netlocker.domain.usecase.ObserveRulesWithAppsUseCase
import com.netlocker.domain.usecase.RuleWithApp
import com.netlocker.domain.usecase.SetRuleEnabledUseCase
import com.netlocker.domain.usecase.UpdateNetworkRuleUseCase
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RulesUiState(
    /** Rules after applying the selected filter and search query. */
    val rules: List<RuleWithApp> = emptyList(),
    val counts: RuleCounts = RuleCounts(),
    /** Number of *enabled* rules — what the header calls "active". */
    val activeCount: Int = 0,
    val totalCount: Int = 0,
    val filter: RuleFilter = RuleFilter.ALL,
    val query: String = "",
    /** Installed apps that don't have a rule yet — the candidates for "Add Rule". */
    val addableApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
)

private data class Controls(val filter: RuleFilter = RuleFilter.ALL, val query: String = "")

/**
 * Rules tab state. It observes the same Room table the Apps tab writes to, so a toggle
 * flipped on either tab shows up on the other immediately — there is no second copy of
 * the data to fall out of sync.
 */
class RulesViewModel(
    observeRulesWithApps: ObserveRulesWithAppsUseCase,
    installedAppRepository: InstalledAppRepository,
    private val updateNetworkRuleUseCase: UpdateNetworkRuleUseCase,
    private val setRuleEnabledUseCase: SetRuleEnabledUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
) : ViewModel() {

    private val controls = MutableStateFlow(Controls())

    val uiState: StateFlow<RulesUiState> = combine(
        observeRulesWithApps(),
        installedAppRepository.observeInstalledApps(includeSystemApps = true),
        installedAppRepository.isLoaded,
        controls,
    ) { rules, allApps, loaded, ctl ->
        val ruledPackages = rules.map { it.rule.packageName }.toSet()
        val query = ctl.query.trim()

        val visible = rules
            .filter { ctl.filter.matches(it.rule.status) }
            .filter {
                query.isEmpty() ||
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.rule.packageName.contains(query, ignoreCase = true)
            }

        RulesUiState(
            rules = visible,
            counts = countRules(rules.map { it.rule }),
            activeCount = rules.count { it.rule.isEnabled },
            totalCount = rules.size,
            filter = ctl.filter,
            query = ctl.query,
            addableApps = allApps
                .filter { it.packageName !in ruledPackages }
                .sortedWith(compareBy({ it.isSystemApp }, { it.label.lowercase() })),
            isLoading = !loaded,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    fun onFilterSelected(filter: RuleFilter) = controls.update { it.copy(filter = filter) }

    fun onQueryChange(query: String) = controls.update { it.copy(query = query) }

    /** Creates or edits a rule. Saving always (re)enables it — see NetworkRuleRepository.setRule. */
    fun saveRule(packageName: String, wifiAllowed: Boolean, mobileDataAllowed: Boolean) {
        viewModelScope.launch { updateNetworkRuleUseCase(packageName, wifiAllowed, mobileDataAllowed) }
    }

    fun setEnabled(packageName: String, enabled: Boolean) {
        viewModelScope.launch { setRuleEnabledUseCase(packageName, enabled) }
    }

    fun deleteRule(packageName: String) {
        viewModelScope.launch { deleteRuleUseCase(packageName) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                RulesViewModel(
                    ServiceLocator.observeRulesWithAppsUseCase,
                    ServiceLocator.installedAppRepository,
                    ServiceLocator.updateNetworkRuleUseCase,
                    ServiceLocator.setRuleEnabledUseCase,
                    ServiceLocator.deleteRuleUseCase,
                )
            }
        }
    }
}
