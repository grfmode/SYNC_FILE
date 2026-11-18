package com.sync.app.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.domain.memory.MemoryCandidate
import com.sync.app.domain.memory.MemoryExportImport
import com.sync.app.domain.memory.MemoryItem
import com.sync.app.domain.memory.MemoryQuery
import com.sync.app.domain.memory.MemoryRepository
import com.sync.app.domain.memory.MemoryScope
import com.sync.app.domain.memory.MemorySource
import com.sync.app.domain.memory.MemoryStatus
import com.sync.app.domain.memory.MemoryType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemoryFilter(
    val scope: MemoryScope? = null,
    val projectId: Long? = null,
    val status: MemoryStatus = MemoryStatus.ACTIVE,
    val type: MemoryType? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val repository: MemoryRepository,
    private val exportImport: MemoryExportImport
) : ViewModel() {

    private val _filter = MutableStateFlow(MemoryFilter())
    val filter: StateFlow<MemoryFilter> = _filter.asStateFlow()

    val items: StateFlow<List<MemoryItem>> = _filter
        .flatMapLatest { filter ->
            repository.observe(
                MemoryQuery(
                    scope = filter.scope,
                    projectId = filter.projectId,
                    statuses = setOf(filter.status),
                    types = filter.type?.let { setOf(it) } ?: emptySet(),
                    limit = 300
                )
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _preview = MutableStateFlow<MemoryExportImport.Preview?>(null)
    val preview: StateFlow<MemoryExportImport.Preview?> = _preview.asStateFlow()

    private var undoToken: MemoryExportImport.UndoToken? = null

    fun setScope(scope: MemoryScope?, projectId: Long?) {
        _filter.value = _filter.value.copy(scope = scope, projectId = projectId)
    }

    fun setStatus(status: MemoryStatus) { _filter.value = _filter.value.copy(status = status) }
    fun setType(type: MemoryType?) { _filter.value = _filter.value.copy(type = type) }

    fun save(id: Long?, content: String, type: MemoryType, scope: MemoryScope, projectId: Long?) {
        viewModelScope.launch {
            if (id == null) {
                repository.remember(
                    MemoryCandidate(
                        content = content,
                        type = type,
                        scope = scope,
                        projectId = if (scope == MemoryScope.GLOBAL) null else projectId,
                        source = MemorySource.MANUAL,
                        confidence = 1f,
                        provenance = "manual"
                    )
                )
            } else {
                repository.updateContent(id, content)
                repository.moveScope(id, scope, projectId)
            }
            _message.value = "Tersimpan."
        }
    }

    fun togglePin(item: MemoryItem) {
        viewModelScope.launch { repository.setPinned(item.id, !item.pinned) }
    }

    fun archive(item: MemoryItem) {
        viewModelScope.launch { repository.setStatus(item.id, MemoryStatus.ARCHIVED) }
    }

    fun restore(item: MemoryItem) {
        viewModelScope.launch { repository.setStatus(item.id, MemoryStatus.ACTIVE) }
    }

    fun delete(item: MemoryItem) {
        viewModelScope.launch { repository.delete(item.id) }
    }

    fun export(scope: MemoryScope?, projectId: Long?, onReady: (String) -> Unit) {
        viewModelScope.launch { onReady(exportImport.export(scope, projectId)) }
    }

    fun previewImport(raw: String, projectId: Long?) {
        viewModelScope.launch {
            exportImport.preview(raw, projectId)
                .onSuccess { _preview.value = it }
                .onFailure { _message.value = "Berkas tidak valid: ${it.message}" }
        }
    }

    fun cancelImport() { _preview.value = null }

    fun applyImport(projectId: Long?) {
        val current = _preview.value ?: return
        viewModelScope.launch {
            undoToken = exportImport.apply(current, projectId)
            _preview.value = null
            _message.value = "Impor selesai. Bisa dibatalkan."
        }
    }

    fun undoImport() {
        val token = undoToken ?: return
        viewModelScope.launch {
            exportImport.undo(token)
            undoToken = null
            _message.value = "Impor dibatalkan."
        }
    }

    fun clearMessage() { _message.value = null }
}