package com.sync.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.domain.memory.LegacyMemoryImporter
import com.sync.app.domain.provider.ProviderConfig
import com.sync.app.domain.provider.ProviderId
import com.sync.app.domain.provider.ProviderRegistry
import com.sync.app.domain.settings.ChatMode
import com.sync.app.domain.settings.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedProvider: ProviderId = ProviderId.Groq,
    val providerConfigs: List<ProviderConfig> = emptyList(),
    val autoFallback: Boolean = true,
    val streamingEnabled: Boolean = false,
    val summaryEnabled: Boolean = true,
    val autoExtract: Boolean = true,
    val indexEnabled: Boolean = true,
    val timeoutProfile: String = "High-Speed",
    val selectedChatMode: ChatMode = ChatMode.Normal
) {
    val selectedProviderDisplayName: String
        get() = providerConfigs.find { it.id == selectedProvider }?.displayName ?: selectedProvider.value
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsStore: SettingsStore,
    private val providerRegistry: ProviderRegistry,
    private val legacyImporter: LegacyMemoryImporter
) : ViewModel() {

    private val _providerConfigs = MutableStateFlow<List<ProviderConfig>>(emptyList())
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { _providerConfigs.value = providerRegistry.allConfigs() }
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsStore.selectedProviderId,
                settingsStore.autoFallbackEnabled,
                settingsStore.streamingEnabled,
                settingsStore.summaryEnabled,
                settingsStore.autoExtractEnabled,
                settingsStore.indexEnabled,
                settingsStore.timeoutProfile,
                settingsStore.selectedChatMode
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                SettingsUiState(
                    selectedProvider = values[0] as ProviderId,
                    autoFallback = values[1] as Boolean,
                    streamingEnabled = values[2] as Boolean,
                    summaryEnabled = values[3] as Boolean,
                    autoExtract = values[4] as Boolean,
                    indexEnabled = values[5] as Boolean,
                    timeoutProfile = values[6] as String,
                    selectedChatMode = values[7] as ChatMode
                )
            }.combine(_providerConfigs) { state, configs ->
                state.copy(providerConfigs = configs)
            }.collect { _uiState.value = it }
        }
    }

    fun selectProvider(providerId: ProviderId) { viewModelScope.launch { settingsStore.saveProvider(providerId) } }
    fun toggleAutoFallback(enabled: Boolean) { viewModelScope.launch { settingsStore.saveAutoFallback(enabled) } }
    fun toggleStreaming(enabled: Boolean) { viewModelScope.launch { settingsStore.saveStreamingEnabled(enabled) } }
    fun toggleSummary(enabled: Boolean) { viewModelScope.launch { settingsStore.saveSummaryEnabled(enabled) } }
    fun toggleAutoExtract(enabled: Boolean) { viewModelScope.launch { settingsStore.saveAutoExtract(enabled) } }
    fun toggleIndex(enabled: Boolean) { viewModelScope.launch { settingsStore.saveIndexEnabled(enabled) } }
    fun setTimeoutProfile(profile: String) { viewModelScope.launch { settingsStore.saveTimeoutProfile(profile) } }
    fun changeChatMode(mode: ChatMode) { viewModelScope.launch { settingsStore.saveChatMode(mode) } }

    fun importLegacyMemory() {
        viewModelScope.launch {
            val report = legacyImporter.importAll()
            _message.value = "Impor legacy: ${report.itemsImported} item, ${report.rawBlocksKept} blok mentah."
        }
    }

    fun clearMessage() { _message.value = null }
}