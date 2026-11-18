package com.sync.app.domain.provider

/**
 * Metadata provider AI.
 *
 * Sumber kebenaran untuk `displayName`, `baseUrl`, `apiFormat`, dan atribut
 * terkait. Diisi dari dua sumber:
 *
 * 1. **Built-in** — hardcoded di
 *    [RemoteProviderRepositoryImpl][com.sync.app.domain.provider.RemoteProviderRepositoryImpl]
 *    (Groq, Mistral, OpenRouter, Gemini).
 * 2. **Remote** — ditarik dari JSON di GitHub, memungkinkan penambahan
 *    provider baru secara dinamis.
 *
 * Prioritas merge: remote meng-override built-in dengan [id] yang sama.
 *
 * [apiFormat] menentukan adapter yang dibuat oleh
 * [ProviderFactory][com.sync.app.domain.provider.ProviderFactory].
 *
 * [tier] menandai status biaya provider — ditampilkan sebagai titik berwarna
 * di daftar pemilihan provider. Lihat [ProviderTier].
 */
data class ProviderConfig(
    val id: ProviderId,
    val displayName: String,
    val baseUrl: String,
    val apiFormat: ApiFormat = ApiFormat.OpenAi,
    val authType: ProviderAuthType = ProviderAuthType.Bearer,
    val capabilities: Set<ProviderCapability> = setOf(ProviderCapability.Streaming),
    val supportsStreaming: Boolean = true,
    val isBuiltIn: Boolean = false,
    val tier: ProviderTier = ProviderTier.Free
)

/**
 * Status biaya/tier provider.
 *
 * - [Free]   — ada kuota gratis permanen untuk pemakaian nyata.
 * - [Credit] — kredit awal (sekali) atau gratis terbatas, lalu berbayar.
 * - [Trial]  — hanya untuk evaluasi, bukan produksi.
 * - [Paid]   — berbayar penuh, tanpa tier gratis.
 *
 * Ditampilkan sebagai titik warna (dengan animasi pulse untuk provider aktif):
 * - 🟢 Free   → hijau   `#2E7D32`
 * - 🟡 Credit → kuning  `#F9A825`
 * - 🟠 Trial  → oranye  `#E65100`
 * - 🔴 Paid   → merah   `#C62828`
 */
enum class ProviderTier {
    Free,
    Credit,
    Trial,
    Paid
}

/**
 * Format API yang dipakai provider.
 *
 * - [OpenAi] — endpoint `/chat/completions`, `/models`, SSE `data: ...`
 *   (Groq, Mistral, OpenRouter, dan semua provider OpenAI-compatible lain).
 * - [Gemini] — endpoint `/models/{model}:generateContent`,
 *   `/models/{model}:streamGenerateContent`, autentikasi query param.
 * - [Anthropic] — endpoint `/v1/messages`, header `x-api-key`, system prompt
 *   terpisah dari array messages, SSE event `content_block_delta`.
 */
enum class ApiFormat {
    OpenAi,
    Gemini,
    Anthropic // [P-037]
}

/**
 * Skema autentikasi HTTP yang dipakai adapter.
 *
 * - [Bearer]     → header `Authorization: Bearer <apiKey>`
 * - [QueryParam] → query `?key=<apiKey>` (Gemini)
 * - [HeaderKey]  → header custom `X-Api-Key: <apiKey>` (Anthropic)
 */
enum class ProviderAuthType {
    Bearer,
    QueryParam,
    HeaderKey
}