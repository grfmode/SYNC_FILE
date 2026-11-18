package com.sync.app.domain.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private val Context.remotePromptDataStore by preferencesDataStore(name = "remote_prompt_cache")

/**
 * Sumber prompt persona (T-021 / P-018 / P-024).
 * Menarik prompt dari remote (GitHub Raw) dan mengembalikan prompt yang
 * akan disuntikkan ke systemPrompt.
 *
 * T-045: Menambahkan persistent cache (DataStore) + TTL 24 Jam.
 * Mendukung Personality Styles & Traits.
 */
interface RemotePromptRepository {
    suspend fun resolvePrompt(mode: ChatMode): String

    /** Alias eksplisit untuk assembler konteks; perilaku sama dengan [resolvePrompt]. */
    suspend fun resolveBasePrompt(mode: ChatMode): String = resolvePrompt(mode)

    suspend fun resolvePersonalityMaps(): Pair<Map<String, String>, Map<String, String>>
}

@Serializable
data class RemotePromptPayload(
    val prompts: Map<String, String> = emptyMap(),
    val personality_styles: Map<String, String> = emptyMap(),
    val personality_traits: Map<String, String> = emptyMap()
)

@Singleton
class RemotePromptRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    okHttpClient: OkHttpClient,
    private val json: Json
) : RemotePromptRepository {

    private companion object {
        const val TAG = "SYNC_PROMPT"
        const val PROMPT_URL = "https://raw.githubusercontent.com/noxbits-studio/SYNC/refs/heads/main/SYNC_PROMPT.json"
        const val CACHE_TTL_MS = 24L * 60L * 60L * 1000L

        /** Cache yang lebih tua dari ini tidak lagi dipakai saat fetch gagal. */
        const val CACHE_MAX_STALE_MS = 30L * 24L * 60L * 60L * 1000L

        val KEY_CACHED_JSON = stringPreferencesKey("cached_json")
        val KEY_CACHED_TIMESTAMP = longPreferencesKey("cached_timestamp")
    }

    private val promptHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun resolvePrompt(mode: ChatMode): String {
        val fallback = mode.systemPrompt
        if (PROMPT_URL.isBlank()) return fallback

        val payload = getCachedOrFetch()

        // Cek di payload.prompts, jika tidak ada coba di root (backward compatibility)
        val resolved = payload.prompts[mode.name.lowercase()] ?: payload.prompts[mode.name]

        return resolved ?: fallback
    }

    override suspend fun resolvePersonalityMaps(): Pair<Map<String, String>, Map<String, String>> {
        if (PROMPT_URL.isBlank()) return Pair(emptyMap(), emptyMap())

        val payload = getCachedOrFetch()
        return Pair(payload.personality_styles, payload.personality_traits)
    }

    private suspend fun getCachedOrFetch(): RemotePromptPayload {
        val now = System.currentTimeMillis()

        // Satu koleksi DataStore untuk kedua key.
        val snapshot = context.remotePromptDataStore.data.first()
        val cachedJson = snapshot[KEY_CACHED_JSON]
        val cachedTime = snapshot[KEY_CACHED_TIMESTAMP] ?: 0L

        if (cachedJson != null && now - cachedTime < CACHE_TTL_MS) {
            try {
                return json.decodeFromString<RemotePromptPayload>(cachedJson)
            } catch (e: Exception) {
                Log.w(TAG, "Cache prompt rusak, akan fetch ulang", e)
            }
        }

        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(PROMPT_URL)
                    .get()
                    .header("Accept", "application/json")
                    .build()

                promptHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext fallbackPayload(cachedJson, cachedTime, now)
                    }
                    val body = response.body?.string()
                    if (body.isNullOrBlank() || body.trimStart().startsWith("<")) {
                        return@withContext fallbackPayload(cachedJson, cachedTime, now)
                    }

                    val parsed = try {
                        json.decodeFromString<RemotePromptPayload>(body)
                    } catch (e: Exception) {
                        Log.w(TAG, "Payload prompt tidak valid", e)
                        return@withContext fallbackPayload(cachedJson, cachedTime, now)
                    }

                    context.remotePromptDataStore.edit { prefs ->
                        prefs[KEY_CACHED_JSON] = body
                        prefs[KEY_CACHED_TIMESTAMP] = now
                    }

                    parsed
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fetch prompt gagal", e)
            fallbackPayload(cachedJson, cachedTime, now)
        }
    }

    /**
     * Cache hanya dipakai bila belum melewati [CACHE_MAX_STALE_MS]; setelah itu
     * prompt bawaan aplikasi yang berlaku agar prompt usang tidak bertahan selamanya.
     */
    private fun fallbackPayload(cachedJson: String?, cachedTime: Long, now: Long): RemotePromptPayload {
        if (cachedJson == null) return RemotePromptPayload()
        if (cachedTime <= 0L || now - cachedTime > CACHE_MAX_STALE_MS) return RemotePromptPayload()
        return try {
            json.decodeFromString(cachedJson)
        } catch (_: Exception) {
            RemotePromptPayload()
        }
    }
}