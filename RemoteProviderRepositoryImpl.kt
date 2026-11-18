package com.sync.app.domain.provider

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementasi [RemoteProviderRepository] (P-019 + P-020).
 *
 * - Built-in 4 provider (Groq, Mistral, OpenRouter, Gemini) selalu tersedia.
 * - Remote JSON opsional; di-overlay di atas built-in berdasarkan [ProviderConfig.id].
 * - Cache in-memory 24 jam, timeout HTTP pendek (10 detik) agar startup ringan.
 * - Body yang tampak HTML (URL Gist salah) ditolak, fallback ke built-in.
 *
 * Format JSON yang diharapkan:
 * {
 *   "providers": [
 *     {
 *       "id": "cerebras",
 *       "displayName": "Cerebras",
 *       "baseUrl": "https://api.cerebras.ai/v1",
 *       "apiFormat": "OpenAi",
 *       "authType": "Bearer",
 *       "capabilities": ["Streaming"],
 *       "supportsStreaming": true,
 *       "tier": "Free"
 *     }
 *   ]
 * }
 *
 * Field `tier` opsional (`Free` | `Credit` | `Trial` | `Paid`). Jika kosong
 * atau tidak dikenal, default ke [ProviderTier.Free].
 */
@Singleton
class RemoteProviderRepositoryImpl @Inject constructor(
    okHttpClient: OkHttpClient,
    private val json: Json
) : RemoteProviderRepository {

    private companion object {
        const val TAG = "SYNC_PROVIDER"

        /**
         * URL raw GitHub berisi JSON provider dinamis.
         * Biarkan kosong ("") jika belum siap — hanya built-in yang dipakai.
         *
         * Contoh:
         * const val PROVIDER_URL = "https://raw.githubusercontent.com/USER/REPO/main/providers.json"
         */
        const val PROVIDER_URL = "https://raw.githubusercontent.com/noxbits-studio/SYNC/refs/heads/main/SYNC_PROVIDER.json"

        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L
    }

    private val httpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private data class CacheEntry(
        val configs: List<ProviderConfig>,
        val fetchedAt: Long
    )

    @Volatile
    private var cache: CacheEntry? = null

    override suspend fun getAll(): List<ProviderConfig> {
        val now = System.currentTimeMillis()
        cache?.let {
            if (now - it.fetchedAt < CACHE_TTL_MS) {
                return mergeBuiltIn(it.configs)
            }
        }
        val fetched = fetchRemote()
        cache = CacheEntry(fetched, now)
        return mergeBuiltIn(fetched)
    }

    override suspend fun getById(id: ProviderId): ProviderConfig? =
        getAll().firstOrNull { it.id == id }

    override suspend fun refresh(): List<ProviderConfig> {
        val fetched = fetchRemote()
        cache = CacheEntry(fetched, System.currentTimeMillis())
        return mergeBuiltIn(fetched)
    }

    // === Internal ===

    private fun mergeBuiltIn(remote: List<ProviderConfig>): List<ProviderConfig> {
        val remoteIds = remote.map { it.id }.toSet()
        val builtIn = builtInConfigs().filter { it.id !in remoteIds }
        return builtIn + remote
    }

    private fun builtInConfigs(): List<ProviderConfig> = listOf(
        ProviderConfig(
            id = ProviderId.OpenRouter,
            displayName = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            apiFormat = ApiFormat.OpenAi,
            authType = ProviderAuthType.Bearer,
            isBuiltIn = true,
            tier = ProviderTier.Free
        ),
        ProviderConfig(
            id = ProviderId.Gemini,
            displayName = "Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            apiFormat = ApiFormat.Gemini,
            authType = ProviderAuthType.QueryParam,
            isBuiltIn = true,
            tier = ProviderTier.Free
        ),
        ProviderConfig(
            id = ProviderId.Groq,
            displayName = "Groq",
            baseUrl = "https://api.groq.com/openai/v1",
            apiFormat = ApiFormat.OpenAi,
            authType = ProviderAuthType.Bearer,
            isBuiltIn = true,
            tier = ProviderTier.Free
        ),
        ProviderConfig(
            id = ProviderId.Mistral,
            displayName = "Mistral",
            baseUrl = "https://api.mistral.ai/v1",
            apiFormat = ApiFormat.OpenAi,
            authType = ProviderAuthType.Bearer,
            isBuiltIn = true,
            tier = ProviderTier.Free
        )
    )

    private suspend fun fetchRemote(): List<ProviderConfig> {
        if (PROVIDER_URL.isBlank()) {
            Log.i(TAG, "PROVIDER_URL kosong. Hanya menggunakan provider built-in.")
            return emptyList()
        }

        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(PROVIDER_URL)
                    .get()
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Fetch gagal. Code=${response.code}")
                        return@withContext emptyList()
                    }

                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) {
                        Log.w(TAG, "Body kosong.")
                        return@withContext emptyList()
                    }

                    if (body.trimStart().startsWith("<")) {
                        Log.e(TAG, "Body tampak HTML, bukan JSON. Cek URL raw GitHub.")
                        return@withContext emptyList()
                    }

                    val payload: RemoteProviderPayload = try {
                        json.decodeFromString(body)
                    } catch (e: Exception) {
                        Log.e(TAG, "JSON parse gagal: ${e.message}")
                        return@withContext emptyList()
                    }

                    payload.providers.mapNotNull { dto -> toDomain(dto) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch exception: ${e.javaClass.simpleName}: ${e.message}")
            emptyList()
        }
    }

    private fun toDomain(dto: RemoteProviderDto): ProviderConfig? {
        val providerId = ProviderId.fromRaw(dto.id) ?: return null
        if (dto.baseUrl.isBlank()) return null

        val authTypeEnum = dto.authType?.let {
            runCatching { ProviderAuthType.valueOf(it.trim()) }.getOrNull()
        } ?: ProviderAuthType.Bearer

        val apiFormatEnum = dto.apiFormat?.let {
            runCatching { ApiFormat.valueOf(it.trim()) }.getOrNull()
        } ?: ApiFormat.OpenAi

        val tierEnum = dto.tier?.let {
            runCatching { ProviderTier.valueOf(it.trim()) }.getOrNull()
        } ?: ProviderTier.Free

        val capabilitySet: Set<ProviderCapability> = dto.capabilities
            ?.mapNotNull { cap ->
                runCatching { ProviderCapability.valueOf(cap.trim()) }.getOrNull()
            }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: setOf(ProviderCapability.Streaming)

        return ProviderConfig(
            id = providerId,
            displayName = dto.displayName.ifBlank { providerId.value },
            baseUrl = dto.baseUrl.trim().trimEnd('/'),
            apiFormat = apiFormatEnum,
            authType = authTypeEnum,
            capabilities = capabilitySet,
            supportsStreaming = dto.supportsStreaming ?: true,
            isBuiltIn = false,
            tier = tierEnum
        )
    }

    // === DTO ===

    @Serializable
    private data class RemoteProviderPayload(
        val providers: List<RemoteProviderDto> = emptyList()
    )

    @Serializable
    private data class RemoteProviderDto(
        val id: String,
        val displayName: String = "",
        val baseUrl: String,
        val apiFormat: String? = null,
        val authType: String? = null,
        val capabilities: List<String>? = null,
        val supportsStreaming: Boolean? = null,
        val tier: String? = null
    )
}