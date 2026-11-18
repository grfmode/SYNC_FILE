package com.sync.app.domain.provider

import com.sync.app.domain.provider.anthropic.AnthropicProvider
import com.sync.app.domain.provider.gemini.GeminiProvider
import com.sync.app.domain.provider.openai.OpenAiCompatibleProvider
import kotlinx.serialization.json.Json
import io.ktor.client.HttpClient
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory pembuat [AiProvider] dari [ProviderConfig] (P-019).
 *
 * Pemetaan:
 * - [ApiFormat.OpenAi] → [OpenAiCompatibleProvider]
 * - [ApiFormat.Gemini] → [GeminiProvider]
 * - [ApiFormat.Anthropic] → [AnthropicProvider] [P-037]
 *
 * Factory ini dipakai oleh
 * [ProviderRegistry][com.sync.app.domain.provider.ProviderRegistry]
 * saat inisialisasi lazy untuk membangun adapter dari daftar config yang
 * dihasilkan [RemoteProviderRepository].
 */
@Singleton
class ProviderFactory @Inject constructor(
    private val client: OkHttpClient,
    private val streamingClient: HttpClient,
    private val json: Json
) {
    fun create(config: ProviderConfig): AiProvider = when (config.apiFormat) {
        ApiFormat.OpenAi -> OpenAiCompatibleProvider(
            config = config,
            client = client,
            streamingClient = streamingClient,
            json = json
        )
        ApiFormat.Gemini -> GeminiProvider(
            config = config,
            client = client,
            json = json
        )
        ApiFormat.Anthropic -> AnthropicProvider(
            config = config,
            client = client,
            json = json
        )
    }
}