package com.sync.app.domain.apikey

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sync.app.security.KeystoreCrypto
import com.sync.app.domain.provider.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apiKeyDataStore by preferencesDataStore(name = "api_keys")

/**
 * Implementasi P-009: API key disimpan terenkripsi di DataStore.
 *
 * - Tidak ada log.
 * - Exception pada dekripsi ditelan (return null) untuk mencegah
 *   kebocoran informasi ke log/crash.
 * - Nilai plaintext tidak pernah disimpan ke disk.
 *
 * [P-019] Migrasi transparan:
 * Sebelum refactor, key disimpan dengan `providerId.name` (mis. "Groq").
 * Setelah refactor, key disimpan dengan `providerId.value` (mis. "groq").
 * Implementasi ini membaca kedua format dan otomatis menghapus key lama
 * saat user menyimpan ulang. Tidak ada data pengguna yang hilang.
 */
@Singleton
class ApiKeyStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: KeystoreCrypto
) : ApiKeyStore {

    /**
     * Nama key DataStore legacy (dari era enum ProviderId).
     * Hanya 4 provider bawaan yang punya padanan legacy.
     */
    private fun legacyKeyName(providerId: ProviderId): String? = when (providerId.value) {
        "openrouter" -> "OpenRouter"
        "gemini" -> "Gemini"
        "groq" -> "Groq"
        "mistral" -> "Mistral"
        else -> null
    }

    override suspend fun save(providerId: ProviderId, apiKey: String) {
        val encrypted = crypto.encrypt(apiKey)
        val newKey = stringPreferencesKey(providerId.value)
        val legacyKey = legacyKeyName(providerId)?.let { stringPreferencesKey(it) }
        context.apiKeyDataStore.edit { prefs ->
            prefs[newKey] = encrypted
            if (legacyKey != null) prefs.remove(legacyKey)
        }
    }

    override suspend fun load(providerId: ProviderId): String? {
        return try {
            val newKey = stringPreferencesKey(providerId.value)
            val legacyKey = legacyKeyName(providerId)?.let { stringPreferencesKey(it) }
            val prefs = context.apiKeyDataStore.data.first()
            val encrypted = prefs[newKey] ?: legacyKey?.let { prefs[it] }
            encrypted?.let { crypto.decrypt(it) }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun delete(providerId: ProviderId) {
        val newKey = stringPreferencesKey(providerId.value)
        val legacyKey = legacyKeyName(providerId)?.let { stringPreferencesKey(it) }
        context.apiKeyDataStore.edit { prefs ->
            prefs.remove(newKey)
            if (legacyKey != null) prefs.remove(legacyKey)
        }
    }

    override suspend fun has(providerId: ProviderId): Boolean {
        val newKey = stringPreferencesKey(providerId.value)
        val legacyKey = legacyKeyName(providerId)?.let { stringPreferencesKey(it) }
        val prefs = context.apiKeyDataStore.data.first()
        return prefs.contains(newKey) || (legacyKey != null && prefs.contains(legacyKey))
    }
}