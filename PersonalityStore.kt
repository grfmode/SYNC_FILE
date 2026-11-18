package com.sync.app.domain.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.personalityDataStore by preferencesDataStore(name = "personality_settings")

@Singleton
class PersonalityStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_STYLE = stringPreferencesKey("personality_style")
        val KEY_WARMTH = stringPreferencesKey("personality_warmth")
        val KEY_ENTHUSIASM = stringPreferencesKey("personality_enthusiasm")
        val KEY_HEADINGS = stringPreferencesKey("personality_headings")
        val KEY_EMOJIS = stringPreferencesKey("personality_emojis")
        val KEY_CUSTOM = stringPreferencesKey("personality_custom")
    }

    val settingsFlow: Flow<PersonalitySettings> = context.personalityDataStore.data.map { prefs ->
        PersonalitySettings(
            style = prefs[KEY_STYLE]?.let { name -> PersonalityStyle.entries.find { it.name == name } } ?: PersonalityStyle.Default,
            warmth = prefs[KEY_WARMTH]?.let { name -> TraitLevel.entries.find { it.name == name } } ?: TraitLevel.Default,
            enthusiasm = prefs[KEY_ENTHUSIASM]?.let { name -> TraitLevel.entries.find { it.name == name } } ?: TraitLevel.Default,
            headings = prefs[KEY_HEADINGS]?.let { name -> TraitLevel.entries.find { it.name == name } } ?: TraitLevel.Default,
            emojis = prefs[KEY_EMOJIS]?.let { name -> TraitLevel.entries.find { it.name == name } } ?: TraitLevel.Default,
            customInstructions = prefs[KEY_CUSTOM] ?: ""
        )
    }

    suspend fun saveStyle(style: PersonalityStyle) {
        context.personalityDataStore.edit { it[KEY_STYLE] = style.name }
    }

    suspend fun saveWarmth(level: TraitLevel) {
        context.personalityDataStore.edit { it[KEY_WARMTH] = level.name }
    }

    suspend fun saveEnthusiasm(level: TraitLevel) {
        context.personalityDataStore.edit { it[KEY_ENTHUSIASM] = level.name }
    }

    suspend fun saveHeadings(level: TraitLevel) {
        context.personalityDataStore.edit { it[KEY_HEADINGS] = level.name }
    }

    suspend fun saveEmojis(level: TraitLevel) {
        context.personalityDataStore.edit { it[KEY_EMOJIS] = level.name }
    }

    suspend fun saveCustomInstructions(text: String) {
        context.personalityDataStore.edit { it[KEY_CUSTOM] = text }
    }
}