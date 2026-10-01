package com.netlocker.ui.whatsnew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.netlocker.BuildConfig
import com.netlocker.domain.model.ReleaseNotesResult
import com.netlocker.domain.repository.UpdateRepository
import com.netlocker.util.PreferencesManager
import com.netlocker.util.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface WhatsNewUiState {
    data object Hidden : WhatsNewUiState
    data object Loading : WhatsNewUiState
    data class Shown(val versionName: String, val notes: String, val releaseUrl: String) : WhatsNewUiState
    data object Unavailable : WhatsNewUiState
}

/** Drives both entry points to "What's new": an automatic one-time popup right after an
 *  update (see [checkForAutoShow]) and an on-demand one from Settings (see [showManually]). */
class WhatsNewViewModel(
    private val updateRepository: UpdateRepository,
    private val preferencesManager: PreferencesManager,
    private val currentVersionName: String,
) : ViewModel() {

    private val _state = MutableStateFlow<WhatsNewUiState>(WhatsNewUiState.Hidden)
    val state: StateFlow<WhatsNewUiState> = _state.asStateFlow()

    /** Called once from the app shell on cold start. Only shows anything if the
     *  installed version actually changed since this was last recorded — never on a
     *  fresh install (nothing to announce yet), never twice for the same update, and
     *  never surfaces an error dialog the user didn't ask for (a failed fetch here just
     *  means it'll quietly retry on the next launch, since "last seen" is only updated
     *  on success). */
    fun checkForAutoShow() {
        viewModelScope.launch {
            val lastSeen = preferencesManager.whatsNewLastSeenVersion.first()
            if (lastSeen == null) {
                preferencesManager.setWhatsNewLastSeenVersion(currentVersionName)
                return@launch
            }
            if (lastSeen == currentVersionName) return@launch
            fetchAndShow(markSeenOnSuccess = true, showErrorOnFailure = false)
        }
    }

    /** Called from Settings' "What's new" row — always fetches fresh, and does surface a
     *  failure, since the user explicitly asked this time. */
    fun showManually() {
        viewModelScope.launch { fetchAndShow(markSeenOnSuccess = false, showErrorOnFailure = true) }
    }

    private suspend fun fetchAndShow(markSeenOnSuccess: Boolean, showErrorOnFailure: Boolean) {
        _state.value = WhatsNewUiState.Loading
        when (val result = updateRepository.fetchLatestReleaseNotes()) {
            is ReleaseNotesResult.Available -> {
                _state.value = WhatsNewUiState.Shown(result.versionName, result.notes, result.releaseUrl)
                if (markSeenOnSuccess) preferencesManager.setWhatsNewLastSeenVersion(currentVersionName)
            }
            ReleaseNotesResult.Unavailable -> {
                _state.value = if (showErrorOnFailure) WhatsNewUiState.Unavailable else WhatsNewUiState.Hidden
            }
        }
    }

    fun dismiss() {
        _state.value = WhatsNewUiState.Hidden
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                WhatsNewViewModel(
                    ServiceLocator.updateRepository,
                    ServiceLocator.preferencesManager,
                    BuildConfig.VERSION_NAME,
                )
            }
        }
    }
}
