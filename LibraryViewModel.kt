package com.sync.app.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sync.app.domain.library.LibraryRepository
import com.sync.app.model.AttachmentUi
import com.sync.app.model.AttachmentType
import com.sync.app.model.LibraryItem
import com.sync.app.model.LibraryItemType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTag = MutableStateFlow<String?>(null)
    val selectedTag: StateFlow<String?> = _selectedTag.asStateFlow()

    // Gabungan semua item dari DB
    private val allItems = libraryRepository.observeLibraryItems()

    // Filter berdasarkan Search Query dan Tag yang dipilih
    val filteredItems: StateFlow<List<LibraryItem>> = combine(
        allItems,
        _searchQuery,
        _selectedTag
    ) { items, query, tag ->
        var result = items

        if (query.isNotBlank()) {
            result = result.filter { 
                it.name.contains(query, ignoreCase = true) || 
                (it.content?.contains(query, ignoreCase = true) == true)
            }
        }

        if (tag != null) {
            result = result.filter { it.tags.contains(tag) }
        }

        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    // Mengambil semua tag unik dari item yang ada untuk ditampilkan di Filter Chips
    val availableTags: StateFlow<List<String>> = allItems
        .combine(MutableStateFlow(Unit)) { items, _ ->
            items.flatMap { it.tags }.distinct().sorted()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onTagSelected(tag: String?) {
        _selectedTag.value = tag
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            libraryRepository.deleteLibraryItemById(id)
        }
    }

    fun saveToLibrary(item: LibraryItem) {
        viewModelScope.launch {
            libraryRepository.saveLibraryItem(item)
        }
    }

    // [UPDATE] Menerima nama file kustom
    fun saveSnippetToLibrary(name: String, content: String, tags: List<String>) {
        viewModelScope.launch {
            try {
                val finalName = if (name.isBlank()) "Snippet_${System.currentTimeMillis()}" else name
                val item = LibraryItem(
                    id = 0,
                    uri = "",
                    name = finalName,
                    mimeType = "text/plain",
                    type = LibraryItemType.Snippet,
                    content = content,
                    createdAt = System.currentTimeMillis(),
                    tags = tags
                )
                libraryRepository.saveLibraryItem(item)
            } catch (e: Exception) {
                android.util.Log.e("SyncP029", "Gagal menyimpan snippet", e)
            }
        }
    }

    // [UPDATE] Menerima nama file kustom
    fun saveAttachmentToLibrary(name: String, attachment: AttachmentUi, tags: List<String>) {
        viewModelScope.launch {
            try {
                val libType = when (attachment.type) {
                    AttachmentType.Image -> LibraryItemType.Image
                    AttachmentType.Document -> LibraryItemType.Document
                }
                val finalName = if (name.isBlank()) attachment.name else name
                val item = LibraryItem(
                    id = 0,
                    uri = attachment.uri,
                    name = finalName,
                    mimeType = attachment.mimeType,
                    type = libType,
                    content = null,
                    createdAt = System.currentTimeMillis(),
                    tags = tags
                )
                libraryRepository.saveLibraryItem(item)
            } catch (e: Exception) {
                android.util.Log.e("SyncP029", "Gagal menyimpan lampiran", e)
            }
        }
    }
}