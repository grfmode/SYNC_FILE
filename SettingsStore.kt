package com.sync.app.domain.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sync.app.domain.context.ContextMode
import com.sync.app.domain.provider.ProviderId
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "app_settings")

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_PROVIDER = stringPreferencesKey("selected_provider")
        val KEY_MODEL = stringPreferencesKey("selected_model")
        val KEY_AUTO_FALLBACK = booleanPreferencesKey("auto_fallback")
        val KEY_TIMEOUT = stringPreferencesKey("timeout_profile")
        val KEY_CHAT_MODE = stringPreferencesKey("chat_mode")
        val KEY_STREAMING = booleanPreferencesKey("streaming_enabled")
        val KEY_SUMMARY = booleanPreferencesKey("summary_enabled")
        val KEY_CONTEXT_MODE = stringPreferencesKey("context_mode")
        val KEY_AUTO_EXTRACT = booleanPreferencesKey("auto_extract_enabled")
        val KEY_INDEX_ENABLED = booleanPreferencesKey("index_enabled")
    }

    val selectedProviderId: Flow<ProviderId> = context.settingsDataStore.data.map { prefs ->
        ProviderId.fromRaw(prefs[KEY_PROVIDER]) ?: ProviderId.Groq
    }

    val selectedModelId: Flow<String?> = context.settingsDataStore.data.map { prefs -> prefs[KEY_MODEL] }

    val autoFallbackEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_AUTO_FALLBACK] ?: true
    }

    val timeoutProfile: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_TIMEOUT] ?: "High-Speed"
    }

    val selectedChatMode: Flow<ChatMode> = context.settingsDataStore.data.map { prefs ->
        ChatMode.fromName(prefs[KEY_CHAT_MODE])
    }

    val streamingEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_STREAMING] ?: false
    }

    val summaryEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_SUMMARY] ?: true
    }

    /** Mode perakitan konteks. Default Otomatis. */
    val contextMode: Flow<ContextMode> = context.settingsDataStore.data.map { prefs ->
        ContextMode.fromName(prefs[KEY_CONTEXT_MODE])
    }

    /** Ekstraksi memori otomatis dari percakapan. Default `true`. */
    val autoExtractEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_AUTO_EXTRACT] ?: true
    }

    /** Indeks knowledge ikut dikirim ke model. Default `true`. */
    val indexEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[KEY_INDEX_ENABLED] ?: true
    }

    suspend fun saveProvider(providerId: ProviderId) {
        context.settingsDataStore.edit { it[KEY_PROVIDER] = providerId.value }
    }

    suspend fun saveModel(modelId: String) {
        context.settingsDataStore.edit { it[KEY_MODEL] = modelId }
    }

    suspend fun saveAutoFallback(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_FALLBACK] = enabled }
    }

    suspend fun saveTimeoutProfile(profile: String) {
        context.settingsDataStore.edit { it[KEY_TIMEOUT] = profile }
    }

    suspend fun saveChatMode(mode: ChatMode) {
        context.settingsDataStore.edit { it[KEY_CHAT_MODE] = mode.name }
    }

    suspend fun saveStreamingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_STREAMING] = enabled }
    }

    suspend fun saveSummaryEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_SUMMARY] = enabled }
    }

    suspend fun saveContextMode(mode: ContextMode) {
        context.settingsDataStore.edit { it[KEY_CONTEXT_MODE] = mode.name }
    }

    suspend fun saveAutoExtract(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_EXTRACT] = enabled }
    }

    suspend fun saveIndexEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_INDEX_ENABLED] = enabled }
    }
}