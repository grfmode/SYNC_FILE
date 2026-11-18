package com.sync.app.domain.chat.usecase

import com.sync.app.data.customprovider.CustomProviderStore
import com.sync.app.domain.apikey.ApiKeyStore
import com.sync.app.domain.provider.AiModel
import com.sync.app.domain.provider.CustomProviderConfig
import com.sync.app.domain.provider.ProviderConfig
import com.sync.app.domain.provider.ProviderId
import com.sync.app.domain.provider.ProviderRegistry
import com.sync.app.domain.settings.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P-026 Fase 3: Use Case untuk katalog model & konfigurasi provider.
 *
 * Memindahkan tanggung jawab dari ChatViewModel:
 * - Load models dari provider aktif
 * - Pilih model, ganti provider
 * - Simpan API key
 * - Cache providerConfigs, availableModels, hasApiKey, isLoadingModels, modelLoadError
 *
 * BUKAN tanggung jawab:
 * - Streaming (StreamManager)
 * - Attachment (AttachmentManager)
 * - Derived state UI (isMultimodalSupported) — tetap di ViewModel karena
 *   membutuhkan combine `settingsStore` × `availableModels` × `providerRegistry`
 *
 * [P-035] Custom Provider:
 * - Untuk provider dengan prefix `custom:`, coba ambil model otomatis dari
 *   endpoint OpenAI-compatible `/models`.
 * - Jika endpoint tidak tersedia/gagal, gunakan daftar model manual yang
 *   tersimpan sebagai fallback.
 * - API key custom tetap opsional untuk endpoint lokal.
 */
@Singleton
class ModelCatalogUseCase @Inject constructor(
    private val providerRegistry: ProviderRegistry,
    private val apiKeyStore: ApiKeyStore,
    private val settingsStore: SettingsStore,
    private val customProviderStore: CustomProviderStore
) {

    private val _providerConfigs = MutableStateFlow<List<ProviderConfig>>(emptyList())
    val providerConfigs: StateFlow<List<ProviderConfig>> = _providerConfigs.asStateFlow()

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _availableModels = MutableStateFlow<List<AiModel>>(emptyList())
    val availableModels: StateFlow<List<AiModel>> = _availableModels.asStateFlow()

    private val _isLoadingModels = MutableStateFlow(false)
    val isLoadingModels: StateFlow<Boolean> = _isLoadingModels.asStateFlow()

    private val _modelLoadError = MutableStateFlow<String?>(null)
    val modelLoadError: StateFlow<String?> = _modelLoadError.asStateFlow()

    private var loadModelsJob: Job? = null
    private var initialized = false

    /**
     * Bootstrap satu kali: muat daftar provider dan sinkronkan `hasApiKey`.
     * Idempotent.
     */
    suspend fun initialize() {
        if (initialized) return
        initialized = true
        try {
            _providerConfigs.value = providerRegistry.allConfigs()
            val providerId = settingsStore.selectedProviderId.first()
            _hasApiKey.value = apiKeyStore.has(providerId)
        } catch (_: Exception) {
            // Silent — UI akan menampilkan empty state
        }
    }

    /**
     * [P-035] Refresh daftar provider setelah custom provider di-CRUD.
     * Dipanggil dari UI setelah add/update/delete provider custom.
     */
    suspend fun refreshProviderConfigs() {
        try {
            providerRegistry.refresh()
            _providerConfigs.value = providerRegistry.allConfigs()
        } catch (_: Exception) {
            // Silent
        }
    }

    fun loadModels(scope: CoroutineScope) {
        loadModelsJob?.cancel()
        loadModelsJob = scope.launch {
            _isLoadingModels.value = true
            _modelLoadError.value = null
            try {
                val providerId = settingsStore.selectedProviderId.first()
                val apiKey = apiKeyStore.load(providerId)

                _hasApiKey.value = !apiKey.isNullOrBlank()
                val providerDisplayName = displayNameOf(providerId)

                // Custom provider: fetch otomatis dari endpoint /models.
                if (CustomProviderConfig.isCustomId(providerId)) {
                    loadCustomProviderModels(providerId, apiKey.orEmpty())
                    return@launch
                }

                // Provider built-in/remote: wajib API key.
                if (apiKey.isNullOrBlank()) {
                    _modelLoadError.value =
                        "API Key untuk $providerDisplayName belum diatur."
                    _availableModels.value = emptyList()
                    return@launch
                }
                val provider = providerRegistry.get(providerId)
                if (provider == null) {
                    _modelLoadError.value =
                        "Provider $providerDisplayName tidak tersedia."
                    _availableModels.value = emptyList()
                    return@launch
                }
                provider.listModels(apiKey).fold(
                    onSuccess = { models ->
                        _availableModels.value = models.sortedBy { it.displayName.lowercase() }
                        _modelLoadError.value = null
                    },
                    onFailure = { error ->
                        _modelLoadError.value = error.message ?: "Gagal memuat model."
                        _availableModels.value = emptyList()
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _modelLoadError.value = e.message ?: "Gagal memuat model."
                _availableModels.value = emptyList()
            } finally {
                _isLoadingModels.value = false
            }
        }
    }

    /**
     * [P-035] Muat model untuk custom provider dari [CustomProviderStore].
     *
     * API key **tidak** diperlukan di sini — validasi API key terjadi saat
     * chat request dikirim (fallback error di ChatSessionUseCase). Ini
     * memungkinkan custom provider lokal (Ollama/LM Studio) tanpa auth.
     */
    private suspend fun loadCustomProviderModels(providerId: ProviderId, apiKey: String) {
        val config = customProviderStore.getById(providerId.value)
        if (config == null) {
            _availableModels.value = emptyList()
            _modelLoadError.value = "Provider custom tidak ditemukan."
            return
        }

        val provider = providerRegistry.get(providerId)
        val fetchedModels = provider?.listModels(apiKey)?.getOrNull().orEmpty()
        if (fetchedModels.isNotEmpty()) {
            _availableModels.value = fetchedModels.sortedBy { it.displayName.lowercase() }
            _modelLoadError.value = null
            return
        }

        // Fallback untuk provider yang tidak menyediakan endpoint /models.
        val manualModels = config.models.map { modelId ->
            AiModel(id = modelId, displayName = modelId, providerId = providerId)
        }
        _availableModels.value = manualModels.sortedBy { it.displayName.lowercase() }
        _modelLoadError.value = if (manualModels.isEmpty()) {
            "Tidak bisa mengambil daftar model otomatis. Provider mungkin tidak menyediakan endpoint /models."
        } else null
    }

    suspend fun selectModel(model: AiModel) {
        settingsStore.saveModel(model.id)
    }

    suspend fun changeProvider(providerId: ProviderId, scope: CoroutineScope) {
        val current = settingsStore.selectedProviderId.first()
        if (current == providerId) return
        settingsStore.saveProvider(providerId)
        settingsStore.saveModel("")
        _availableModels.value = emptyList()
        _modelLoadError.value = null
        _hasApiKey.value = apiKeyStore.has(providerId)
        loadModels(scope)
    }

    suspend fun saveApiKey(apiKey: String, scope: CoroutineScope) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) return
        val providerId = settingsStore.selectedProviderId.first()
        apiKeyStore.save(providerId, trimmed)
        _hasApiKey.value = true
        loadModels(scope)
    }

    private suspend fun displayNameOf(providerId: ProviderId): String =
        providerRegistry.getConfig(providerId)?.displayName ?: providerId.value
}