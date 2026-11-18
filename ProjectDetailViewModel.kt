package com.sync.app.ui.screens.projects

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.domain.chat.AttachmentManager
import com.sync.app.domain.chat.ChatRepository
import com.sync.app.model.AttachmentUi
import com.sync.app.model.Conversation
import com.sync.app.model.Project
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val attachmentManager: AttachmentManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val projectId: Long = checkNotNull(savedStateHandle["projectId"])

    val project: StateFlow<Project?> = chatRepository.observeProjects()
        .map { list -> list.find { it.id == projectId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val conversations: StateFlow<List<Conversation>> = chatRepository.observeConversationsByProject(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val projectAttachments: StateFlow<List<AttachmentUi>> = chatRepository.observeProjectAttachments(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun removeFromProject(conversationId: Long) {
        viewModelScope.launch {
            chatRepository.updateConversationProject(conversationId, null)
        }
    }

    // [P-030e] Fungsi untuk update Judul & Deskripsi sekaligus
    fun updateProjectInfo(newName: String, newDescription: String) {
        viewModelScope.launch {
            val current = project.value ?: return@launch
            
            // 1. Update Nama jika berubah
            if (newName.isNotBlank() && newName != current.name) {
                chatRepository.updateProjectName(projectId, newName.trim())
            }
            
            // 2. Update Deskripsi
            chatRepository.updateProjectDetails(
                id = projectId,
                description = newDescription.trim().ifBlank { null },
                systemPrompt = current.systemPrompt
            )
        }
    }

    fun saveInstruction(instruction: String) {
        viewModelScope.launch {
            val current = project.value ?: return@launch
            chatRepository.updateProjectDetails(
                id = projectId,
                description = current.description,
                systemPrompt = instruction.trim().ifBlank { null }
            )
        }
    }

    fun clearProjectMemory() {
        viewModelScope.launch {
            chatRepository.updateProjectMemory(projectId, null)
        }
    }

    fun addAttachment(uri: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            attachmentManager.loadAttachment(uri).onSuccess { att ->
                if (projectAttachments.value.any { it.uri == att.uri }) {
                    onResult("File sudah ada di project ini")
                } else {
                    chatRepository.addProjectAttachment(projectId, att)
                    onResult("File berhasil ditambahkan")
                }
            }.onFailure {
                onResult(it.message ?: "Gagal memuat file")
            }
        }
    }

    fun removeAttachment(attachment: AttachmentUi) {
        viewModelScope.launch {
            chatRepository.deleteProjectAttachmentByUri(projectId, attachment.uri)
        }
    }
}