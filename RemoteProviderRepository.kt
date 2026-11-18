package com.sync.app.domain.provider

/**
 * Kontrak penyedia daftar provider (P-019).
 *
 * Menggabungkan provider bawaan (hardcoded) dengan provider dinamis yang
 * ditarik dari remote JSON. Jika fetch gagal atau URL kosong, hanya
 * provider bawaan yang dikembalikan.
 *
 * Implementasi wajib:
 * - Cache hasil fetch (minimal 24 jam) agar tidak spam ke GitHub.
 * - Mengembalikan built-in lebih dulu, lalu remote (yang meng-override
 *   built-in jika [ProviderConfig.id] sama).
 */
interface RemoteProviderRepository {

    /**
     * Daftar provider final yang siap dipakai UI.
     * Urutan: built-in (yang tidak di-override) + remote.
     */
    suspend fun getAll(): List<ProviderConfig>

    /**
     * Ambil config untuk [ProviderId] tertentu.
     * Null jika tidak ada di built-in maupun remote.
     */
    suspend fun getById(id: ProviderId): ProviderConfig?

    /**
     * Paksa fetch ulang dari remote (bypass cache).
     * Dipakai saat user menekan "Refresh" di UI (future).
     */
    suspend fun refresh(): List<ProviderConfig>
}