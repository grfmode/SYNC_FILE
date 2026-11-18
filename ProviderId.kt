package com.sync.app.domain.provider

/**
 * Identitas provider AI yang didukung.
 *
 * [P-019] Diubah dari `enum class` menjadi `value class` agar dapat menampung
 * provider dinamis (ID arbitrer) yang ditarik dari remote JSON.
 *
 * - Metadata (displayName, baseUrl, authType) dipindahkan ke [ProviderConfig].
 * - Nilai di-normalisasi ke lowercase agar konsisten sebagai map key dan
 *   kompatibel dengan ID di remote JSON.
 * - Data lama di [com.sync.app.domain.settings.SettingsStore] yang menyimpan
 *   `enum.name` (mis. "Groq") dimigrasikan via [fromRaw].
 */
@JvmInline
value class ProviderId(val value: String) {

    override fun toString(): String = value

    companion object {
        val OpenRouter = ProviderId("openrouter")
        val Gemini     = ProviderId("gemini")
        val Groq       = ProviderId("groq")
        val Mistral    = ProviderId("mistral")

        /** Daftar provider bawaan (hardcoded). Selalu tersedia sebagai fallback. */
        val builtIn: List<ProviderId> = listOf(OpenRouter, Gemini, Groq, Mistral)

        /**
         * Normalisasi string mentah (dari DataStore, JSON, atau UI) menjadi
         * [ProviderId].
         *
         * - trim spasi
         * - lowercase (kompatibel dengan data lama seperti "Groq")
         * - null jika kosong
         */
        fun fromRaw(raw: String?): ProviderId? {
            if (raw.isNullOrBlank()) return null
            return ProviderId(raw.trim().lowercase())
        }
    }
}