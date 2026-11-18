package com.sync.app.domain.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first // <-- IMPORT YANG DIPERBAIKI
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Serializable
data class PromptTemplate(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val content: String
)

private val Context.promptDataStore by preferencesDataStore(name = "prompt_library")

@Singleton
class PromptStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_PROMPTS = stringPreferencesKey("saved_prompts")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val promptsFlow: Flow<List<PromptTemplate>> = context.promptDataStore.data.map { prefs ->
        val raw = prefs[KEY_PROMPTS] ?: "[]"
        try {
            json.decodeFromString<List<PromptTemplate>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun saveAll(prompts: List<PromptTemplate>) {
        context.promptDataStore.edit { prefs ->
            prefs[KEY_PROMPTS] = json.encodeToString(prompts)
        }
    }

    suspend fun addPrompt(title: String, content: String) {
        // PERBAIKAN: Gunakan first() agar tidak "hang" menunggu flow selamanya
        val current = promptsFlow.first()
        val updated = current + PromptTemplate(title = title, content = content)
        saveAll(updated)
    }

    suspend fun updatePrompt(id: String, title: String, content: String) {
        // PERBAIKAN: Gunakan first()
        val current = promptsFlow.first()
        val updated = current.map {
            if (it.id == id) it.copy(title = title, content = content) else it
        }
        saveAll(updated)
    }

    suspend fun deletePrompt(id: String) {
        // PERBAIKAN: Gunakan first()
        val current = promptsFlow.first()
        saveAll(current.filterNot { it.id == id })
    }
}