package com.sync.app.domain.apikey

import com.sync.app.domain.provider.ProviderId

/**
 * Kontrak penyimpanan API key per provider.
 *
 * Implementasi bertanggung jawab atas enkripsi at-rest.
 * Pemanggil tidak boleh menyimpan hasil [load] ke log, crash report,
 * atau serialisasi UI.
 */
interface ApiKeyStore {
    suspend fun save(providerId: ProviderId, apiKey: String)
    suspend fun load(providerId: ProviderId): String?
    suspend fun delete(providerId: ProviderId)
    suspend fun has(providerId: ProviderId): Boolean
}