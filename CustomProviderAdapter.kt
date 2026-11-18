package com.sync.app.domain.provider

import com.sync.app.data.customprovider.CustomProviderStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [P-035] Jembatan antara [CustomProviderStore] dan [ProviderRegistry].
 *
 * Tanggung jawab:
 * - Mengubah daftar [CustomProviderConfig] (dari DataStore) menjadi daftar
 *   [ProviderConfig] yang bisa dipakai [ProviderFactory] untuk membuat
 *   [OpenAiCompatibleProvider].
 * - Menyediakan snapshot sinkron untuk [ProviderRegistry.ensureInitialized].
 *
 * Adapter ini **stateless** terhadap registry — registry yang menyimpan hasil
 * konversi. Setiap kali store berubah (add/update/delete), registry harus
 * memanggil [loadAll] lagi (lihat `ProviderRegistry.refresh()`).
 *
 * Custom provider **tidak** menyimpan model list di [ProviderConfig] —
 * model list diambil langsung dari [CustomProviderConfig.models] oleh
 * `ModelCatalogUseCase` saat provider custom dipilih. Ini sengaja, karena
 * `ProviderConfig` adalah metadata provider, bukan katalog model runtime.
 */
@Singleton
class CustomProviderAdapter @Inject constructor(
    private val store: CustomProviderStore
) {

    /**
     * Muat seluruh custom provider sebagai [ProviderConfig] siap-pakai.
     *
     * @return daftar kosong jika belum ada custom provider atau IO error.
     */
    suspend fun loadAll(): List<ProviderConfig> {
        return try {
            store.all.first().map { it.toProviderConfig() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Cek apakah [id] mengarah ke custom provider.
     */
    fun isCustom(id: ProviderId): Boolean = CustomProviderConfig.isCustomId(id)
}