package com.sync.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.domain.settings.PersonalitySettings
import com.sync.app.domain.settings.PersonalityStore
import com.sync.app.domain.settings.PersonalityStyle
import com.sync.app.domain.settings.TraitLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonalityViewModel @Inject constructor(
    private val personalityStore: PersonalityStore
) : ViewModel() {

    val uiState: StateFlow<PersonalitySettings> = personalityStore.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PersonalitySettings()
        )

    fun saveStyle(style: PersonalityStyle) {
        viewModelScope.launch { personalityStore.saveStyle(style) }
    }

    fun saveWarmth(level: TraitLevel) {
        viewModelScope.launch { personalityStore.saveWarmth(level) }
    }

    fun saveEnthusiasm(level: TraitLevel) {
        viewModelScope.launch { personalityStore.saveEnthusiasm(level) }
    }

    fun saveHeadings(level: TraitLevel) {
        viewModelScope.launch { personalityStore.saveHeadings(level) }
    }

    fun saveEmojis(level: TraitLevel) {
        viewModelScope.launch { personalityStore.saveEmojis(level) }
    }

    fun saveCustomInstructions(text: String) {
        viewModelScope.launch { personalityStore.saveCustomInstructions(text) }
    }
}