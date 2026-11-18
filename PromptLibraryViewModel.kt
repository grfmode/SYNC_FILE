package com.sync.app.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.domain.settings.PromptStore
import com.sync.app.domain.settings.PromptTemplate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PromptLibraryViewModel @Inject constructor(
    private val promptStore: PromptStore
) : ViewModel() {

    val prompts: StateFlow<List<PromptTemplate>> = promptStore.promptsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun addPrompt(title: String, content: String) {
        viewModelScope.launch {
            promptStore.addPrompt(title, content)
        }
    }

    fun updatePrompt(id: String, title: String, content: String) {
        viewModelScope.launch {
            promptStore.updatePrompt(id, title, content)
        }
    }

    fun deletePrompt(id: String) {
        viewModelScope.launch {
            promptStore.deletePrompt(id)
        }
    }
}