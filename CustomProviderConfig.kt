package com.sync.app.domain.provider

import kotlinx.serialization.Serializable

/**
 * [P-035] Konfigurasi provider custom yang ditambahkan user secara manual.
 *
 * Berbeda dari [ProviderConfig] yang memuat metadata lengkap untuk adapter
 * (auth type, capabilities, dll), model ini hanya menyimpan data minimum
 * yang dibutuhkan untuk membuat [OpenAiCompatibleProvider]:
 *
 * - `baseUrl` — endpoint OpenAI-compatible (mis. `http://192.168.1.10:11434/v1`)
 * - `models`  — daftar model manual opsional sebagai fallback jika endpoint
 *               `/models` tidak tersedia.
 *
 * API Key **tidak** disimpan di sini — disimpan di
 * [com.sync.app.domain.apikey.ApiKeyStore] (Keystore AES-GCM) dengan key
 * = `ProviderId("custom:<uuid>")`.
 *
 * [id] menggunakan format `custom:<8-char-uuid>` agar:
 * - Tidak bertabrakan dengan [ProviderId.builtIn] atau remote provider.
 * - Terdeteksi sebagai custom provider tanpa perlu flag tambahan.
 */
@Serializable
data class CustomProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val models: List<String>
) {
    /**
     * Konversi ke [ProviderConfig] untuk dipakai [ProviderFactory].
     *
     * Asumsi aman:
     * - [ApiFormat.OpenAi] — semua custom provider mengikuti format OpenAI.
     * - [ProviderAuthType.Bearer] — standar OpenAI (`Authorization: Bearer ...`).
     * - Streaming didukung — fallback non-streaming sudah ditangani
     *   [com.sync.app.domain.chat.usecase.ChatSessionUseCase] jika perlu.
     * - Tier `Free` (netral, hijau) sesuai keputusan pengguna.
     * - [isBuiltIn] = false — menandai ini bukan provider bawaan.
     * - Capabilities hanya `Streaming` — karena `ProviderCapability.Chat`
     *   tidak ada di enum. Custom provider pada dasarnya adalah chat-only
     *   OpenAI-compatible.
     */
    fun toProviderConfig(): ProviderConfig = ProviderConfig(
        id = ProviderId(id),
        displayName = displayName,
        baseUrl = baseUrl,
        apiFormat = ApiFormat.OpenAi,
        authType = ProviderAuthType.Bearer,
        capabilities = setOf(ProviderCapability.Streaming),
        supportsStreaming = true,
        isBuiltIn = false,
        tier = ProviderTier.Free
    )

    companion object {
        private const val ID_PREFIX = "custom:"

        /** Cek apakah sebuah [ProviderId] adalah custom provider (prefix `custom:`). */
        fun isCustomId(id: ProviderId): Boolean = id.value.startsWith(ID_PREFIX)

        /** Cek apakah sebuah string adalah custom provider ID. */
        fun isCustomId(value: String): Boolean = value.startsWith(ID_PREFIX)
    }
}