package com.sync.app.domain.provider

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry provider yang tersedia pada runtime (P-019, extended P-035).
 *
 * Perubahan dari versi sebelumnya:
 * - Registrasi tidak lagi manual di `ProviderModule`. Registry memuat daftar
 *   [ProviderConfig] dari [RemoteProviderRepository] secara **lazy** saat
 *   pertama kali diakses (via [ensureInitialized]).
 * - Adapter dibuat oleh [ProviderFactory] berdasarkan
 *   [ProviderConfig.apiFormat].
 * - Seluruh akses (`get`, `all`, `getConfig`, `allConfigs`) bersifat
 *   **suspend** dan otomatis memanggil [ensureInitialized]. Ini menjamin
 *   pemanggil tidak pernah melihat registry kosong karena inisialisasi belum
 *   jalan.
 *
 * [P-035] Custom Provider:
 * - Setelah built-in & remote dimuat, registry juga memuat custom provider
 *   dari [CustomProviderAdapter]. Custom provider menggunakan prefix
 *   `custom:` di [ProviderId] dan **tidak pernah** menimpa provider
 *   built-in/remote.
 * - [refresh] memuat ulang custom provider tanpa membuang provider built-in
 *   (idempotent terhadap built-in, hanya rebuild custom).
 *
 * Kontrak:
 * - [ensureInitialized] idempotent dan thread-safe (mutex + flag).
 * - [refresh] aman dipanggil kapan saja setelah init untuk sinkronisasi
 *   perubahan custom provider.
 */
@Singleton
class ProviderRegistry @Inject constructor(
    private val remoteProviderRepository: RemoteProviderRepository,
    private val factory: ProviderFactory,
    private val customProviderAdapter: CustomProviderAdapter
) {

    private val mutex = Mutex()

    @Volatile
    private var initialized = false

    private val providers = mutableMapOf<ProviderId, AiProvider>()
    private val configs = mutableMapOf<ProviderId, ProviderConfig>()

    /**
     * Memuat seluruh provider dari [RemoteProviderRepository] + custom provider
     * jika belum. Idempotent — pemanggilan berulang hanya mengembalikan
     * daftar cached.
     */
    suspend fun ensureInitialized(): List<ProviderConfig> {
        if (initialized) return configs.values.toList()
        return mutex.withLock {
            if (!initialized) {
                val loaded = remoteProviderRepository.getAll()
                loaded.forEach { config ->
                    providers[config.id] = factory.create(config)
                    configs[config.id] = config
                }
                // [P-035] Muat custom provider setelah built-in/remote.
                loadCustomProvidersLocked()
                initialized = true
            }
            configs.values.toList()
        }
    }

    /**
     * [P-035] Reload custom provider dari [CustomProviderAdapter].
     *
     * Aman dipanggil:
     * - Setelah init selesai (untuk refresh setelah user CRUD custom provider).
     * - Sebelum init selesai (delegasi ke [ensureInitialized]).
     *
     * Built-in/remote provider **tidak disentuh**. Hanya custom provider
     * yang di-rebuild dari store.
     */
    suspend fun refresh() {
        if (!initialized) {
            ensureInitialized()
            return
        }
        mutex.withLock {
            // Buang semua custom provider lama.
            val customIds = configs.keys.filter { CustomProviderConfig.isCustomId(it) }
            customIds.forEach { id ->
                providers.remove(id)
                configs.remove(id)
            }
            loadCustomProvidersLocked()
        }
    }

    private suspend fun loadCustomProvidersLocked() {
        val customConfigs = try {
            customProviderAdapter.loadAll()
        } catch (_: Exception) {
            emptyList()
        }
        customConfigs.forEach { config ->
            // Defensive: jangan sampai custom override built-in/remote.
            if (configs.containsKey(config.id)) return@forEach
            providers[config.id] = factory.create(config)
            configs[config.id] = config
        }
    }

    suspend fun get(id: ProviderId): AiProvider? {
        ensureInitialized()
        return providers[id]
    }

    suspend fun all(): List<AiProvider> {
        ensureInitialized()
        return providers.values.toList()
    }

    suspend fun getConfig(id: ProviderId): ProviderConfig? {
        ensureInitialized()
        return configs[id]
    }

    suspend fun allConfigs(): List<ProviderConfig> {
        ensureInitialized()
        return configs.values.toList()
    }
}