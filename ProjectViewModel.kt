package com.sync.app.ui.screens.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.domain.chat.ChatRepository
import com.sync.app.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    val projects: StateFlow<List<Project>> = chatRepository.observeProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun addProject(name: String) {
        viewModelScope.launch {
            chatRepository.createProject(name)
        }
    }

    fun renameProject(id: Long, newName: String) {
        viewModelScope.launch {
            chatRepository.updateProjectName(id, newName)
        }
    }

    fun deleteProject(id: Long) {
        viewModelScope.launch {
            chatRepository.deleteProject(id)
        }
    }
}