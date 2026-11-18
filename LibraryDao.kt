package com.sync.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLibraryItem(item: LibraryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLibraryItemTagCrossRef(ref: LibraryItemTagCrossRef)

    @Transaction
    suspend fun insertLibraryItemWithTags(item: LibraryEntity, tags: List<String>): Long {
        val itemId = insertLibraryItem(item)
        tags.forEach { tagName ->
            val cleanTag = tagName.trim().lowercase() // Standarisasi tag
            if (cleanTag.isNotEmpty()) {
                insertTag(TagEntity(cleanTag))
                insertLibraryItemTagCrossRef(LibraryItemTagCrossRef(itemId, cleanTag))
            }
        }
        return itemId
    }

    @Query("DELETE FROM library_items WHERE id = :id")
    suspend fun deleteLibraryItemById(id: Long)

    @Transaction
    @Query("SELECT * FROM library_items ORDER BY createdAt DESC")
    fun observeLibraryItemsWithTags(): Flow<List<LibraryItemWithTags>>

    @Transaction
    @Query("SELECT * FROM library_items WHERE id = :id")
    suspend fun getLibraryItemWithTagsById(id: Long): LibraryItemWithTags?

    @Query("SELECT * FROM tags")
    fun observeAllTags(): Flow<List<TagEntity>>
}