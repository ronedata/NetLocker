package com.netlocker.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.domain.model.ALL_DAYS_MASK
import com.netlocker.domain.model.InstalledApp
import com.netlocker.domain.model.RuleCounts
import com.netlocker.domain.model.RuleFilter
import com.netlocker.domain.model.countRules
import com.netlocker.domain.repository.BlockedStatsRepository
import com.netlocker.domain.repository.InstalledAppRepository
import com.netlocker.domain.usecase.DeleteRuleUseCase
import com.netlocker.domain.usecase.ObserveRulesWithAppsUseCase
import com.netlocker.domain.usecase.RuleWithApp
import com.netlocker.domain.usecase.SetRuleEnabledUseCase
import com.netlocker.domain.usecase.SetScheduleUseCase
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
    /** Connection attempts blocked today, by package name. */
    val blockedToday: Map<String, Int> = emptyMap(),
    /** Settings' Schedule master switch — while off, no schedule UI is shown here at all. */
    val scheduleMasterEnabled: Boolean = false,
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
    blockedStatsRepository: BlockedStatsRepository,
    private val updateNetworkRuleUseCase: UpdateNetworkRuleUseCase,
    private val setRuleEnabledUseCase: SetRuleEnabledUseCase,
    private val setScheduleUseCase: SetScheduleUseCase,
    private val deleteRuleUseCase: DeleteRuleUseCase,
    preferencesManager: PreferencesManager,
) : ViewModel() {

    private val controls = MutableStateFlow(Controls())

    private val baseState = combine(
        observeRulesWithApps(),
        installedAppRepository.observeInstalledApps(includeSystemApps = true),
        installedAppRepository.isLoaded,
        controls,
        blockedStatsRepository.observeToday(),
    ) { rules, allApps, loaded, ctl, blocked ->
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
            blockedToday = blocked.mapValues { it.value.count },
            isLoading = !loaded,
        )
    }

    val uiState: StateFlow<RulesUiState> = combine(
        baseState,
        preferencesManager.scheduleMasterEnabled,
    ) { state, scheduleMasterEnabled ->
        state.copy(scheduleMasterEnabled = scheduleMasterEnabled)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RulesUiState())

    fun onFilterSelected(filter: RuleFilter) = controls.update { it.copy(filter = filter) }

    fun onQueryChange(query: String) = controls.update { it.copy(query = query) }

    /** Creates or edits a rule (and its schedule in one go). Saving always (re)enables the
     *  rule — see NetworkRuleRepository.setRule. */
    fun saveRule(
        packageName: String,
        wifiAllowed: Boolean,
        mobileDataAllowed: Boolean,
        scheduleEnabled: Boolean = false,
        scheduleStartMinute: Int = 0,
        scheduleEndMinute: Int = 0,
        scheduleDays: Int = ALL_DAYS_MASK,
    ) {
        viewModelScope.launch {
            updateNetworkRuleUseCase(packageName, wifiAllowed, mobileDataAllowed)
            setScheduleUseCase(packageName, scheduleEnabled, scheduleStartMinute, scheduleEndMinute, scheduleDays)
        }
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
                    ServiceLocator.blockedStatsRepository,
                    ServiceLocator.updateNetworkRuleUseCase,
                    ServiceLocator.setRuleEnabledUseCase,
                    ServiceLocator.setScheduleUseCase,
                    ServiceLocator.deleteRuleUseCase,
                    ServiceLocator.preferencesManager,
                )
            }
        }
    }
}
