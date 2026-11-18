package com.sync.app.data.repository

import com.sync.app.data.local.LibraryDao
import com.sync.app.data.local.LibraryEntity
import com.sync.app.data.local.toDomain
import com.sync.app.domain.library.LibraryRepository
import com.sync.app.model.AttachmentUi
import com.sync.app.model.AttachmentType
import com.sync.app.model.LibraryItem
import com.sync.app.model.LibraryItemType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val dao: LibraryDao
) : LibraryRepository {

    override fun observeLibraryItems(): Flow<List<LibraryItem>> =
        dao.observeLibraryItemsWithTags().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getLibraryItemById(id: Long): LibraryItem? =
        dao.getLibraryItemWithTagsById(id)?.toDomain()

    override suspend fun saveFromAttachment(attachment: AttachmentUi, tags: List<String>): Long {
        val libType = when (attachment.type) {
            AttachmentType.Image -> LibraryItemType.Image
            AttachmentType.Document -> LibraryItemType.Document
        }
        val entity = LibraryEntity(
            uri = attachment.uri,
            name = attachment.name,
            mimeType = attachment.mimeType,
            type = libType,
            content = null
        )
        return dao.insertLibraryItemWithTags(entity, tags)
    }

    override suspend fun saveLibraryItem(item: LibraryItem): Long {
        val entity = LibraryEntity(
            id = item.id,
            uri = item.uri,
            name = item.name,
            mimeType = item.mimeType,
            type = item.type,
            content = item.content,
            createdAt = item.createdAt
        )
        return dao.insertLibraryItemWithTags(entity, item.tags)
    }

    override suspend fun deleteLibraryItem(item: LibraryItem) {
        dao.deleteLibraryItemById(item.id)
    }

    override suspend fun deleteLibraryItemById(id: Long) {
        dao.deleteLibraryItemById(id)
    }
}