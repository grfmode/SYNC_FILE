package com.sync.app.domain.library

import com.sync.app.model.AttachmentUi
import com.sync.app.model.LibraryItem
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    fun observeLibraryItems(): Flow<List<LibraryItem>>
    suspend fun getLibraryItemById(id: Long): LibraryItem?
    
    // [Update] Menambahkan parameter tags
    suspend fun saveFromAttachment(attachment: AttachmentUi, tags: List<String>): Long
    suspend fun saveLibraryItem(item: LibraryItem): Long
    
    suspend fun deleteLibraryItem(item: LibraryItem)
    suspend fun deleteLibraryItemById(id: Long)
}