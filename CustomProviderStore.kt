package com.sync.app.data.customprovider

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sync.app.domain.provider.CustomProviderConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.customProviderDataStore by preferencesDataStore(name = "custom_providers")

/**
 * [P-035] Penyimpanan custom provider di DataStore.
 *
 * Desain:
 * - Menggunakan **DataStore terpisah** (`custom_providers`) agar tidak
 *   bercampur dengan settings umum dan tidak perlu bump versi Room DB.
 * - Data disimpan sebagai **JSON array** dalam satu key — sederhana, cukup
 *   untuk < 20 provider (use case personal).
 * - API Key **TIDAK** disimpan di sini. Lihat [CustomProviderConfig] —
 *   key disimpan di ApiKeyStore (Keystore AES-GCM).
 *
 * Operasi CRUD idempotent dan thread-safe via DataStore's `edit {}`.
 */
@Singleton
class CustomProviderStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_CUSTOM_PROVIDERS = stringPreferencesKey("custom_providers_json")
        val JSON = Json { ignoreUnknownKeys = true; prettyPrint = false }
        val LIST_SERIALIZER = ListSerializer(CustomProviderConfig.serializer())
    }

    /** Flow berisi seluruh custom provider. Kosong jika belum ada. */
    val all: Flow<List<CustomProviderConfig>> = context.customProviderDataStore.data.map { prefs ->
        val raw = prefs[KEY_CUSTOM_PROVIDERS] ?: return@map emptyList()
        try {
            JSON.decodeFromString(LIST_SERIALIZER, raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Tambah custom provider baru dengan ID unik `custom:<8-char-uuid>`.
     *
     * @return ID yang dibuat, atau null jika gagal (mis. IO error).
     */
    suspend fun add(
        displayName: String,
        baseUrl: String,
        models: List<String>
    ): CustomProviderConfig? {
        val trimmedName = displayName.trim()
        val trimmedUrl = baseUrl.trim().trimEnd('/')
        val trimmedModels = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (trimmedName.isBlank() || trimmedUrl.isBlank()) return null

        val newConfig = CustomProviderConfig(
            id = "custom:" + UUID.randomUUID().toString().replace("-", "").take(8),
            displayName = trimmedName,
            baseUrl = trimmedUrl,
            models = trimmedModels
        )

        context.customProviderDataStore.edit { prefs ->
            val current = readList(prefs[KEY_CUSTOM_PROVIDERS])
            val updated = current + newConfig
            prefs[KEY_CUSTOM_PROVIDERS] = JSON.encodeToString(LIST_SERIALIZER, updated)
        }
        return newConfig
    }

    /** Update custom provider yang sudah ada berdasarkan [id]. Return true jika ditemukan. */
    suspend fun update(
        id: String,
        displayName: String,
        baseUrl: String,
        models: List<String>
    ): Boolean {
        val trimmedName = displayName.trim()
        val trimmedUrl = baseUrl.trim().trimEnd('/')
        val trimmedModels = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (trimmedName.isBlank() || trimmedUrl.isBlank()) return false

        var found = false
        context.customProviderDataStore.edit { prefs ->
            val current = readList(prefs[KEY_CUSTOM_PROVIDERS])
            val index = current.indexOfFirst { it.id == id }
            if (index == -1) return@edit
            found = true
            val updated = current.toMutableList().also {
                it[index] = it[index].copy(
                    displayName = trimmedName,
                    baseUrl = trimmedUrl,
                    models = trimmedModels
                )
            }
            prefs[KEY_CUSTOM_PROVIDERS] = JSON.encodeToString(LIST_SERIALIZER, updated)
        }
        return found
    }

    /** Hapus custom provider berdasarkan [id]. Return true jika ditemukan. */
    suspend fun delete(id: String): Boolean {
        var found = false
        context.customProviderDataStore.edit { prefs ->
            val current = readList(prefs[KEY_CUSTOM_PROVIDERS])
            val filtered = current.filterNot { it.id == id }
            if (filtered.size != current.size) {
                found = true
                prefs[KEY_CUSTOM_PROVIDERS] = JSON.encodeToString(LIST_SERIALIZER, filtered)
            }
        }
        return found
    }

    /** Ambil satu custom provider berdasarkan [id]. */
    suspend fun getById(id: String): CustomProviderConfig? {
        val list = allFirst()
        return list.find { it.id == id }
    }

    private suspend fun allFirst(): List<CustomProviderConfig> {
        return try {
            context.customProviderDataStore.data.first().let { prefs ->
                readList(prefs[KEY_CUSTOM_PROVIDERS])
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun readList(raw: String?): List<CustomProviderConfig> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            JSON.decodeFromString(LIST_SERIALIZER, raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}