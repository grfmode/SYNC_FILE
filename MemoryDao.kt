package com.sync.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class MemoryStatusCountRow(val status: String, val total: Int)

@Dao
interface MemoryDao {
    @Query(
        """
        SELECT * FROM memory_items
        WHERE (:scope IS NULL OR scope = :scope)
          AND (:projectId IS NULL OR projectId = :projectId)
          AND status IN (:statuses)
          AND (:filterTypes = 0 OR type IN (:types))
        ORDER BY pinned DESC, updatedAt DESC
        LIMIT :limit
        """
    )
    fun observe(
        scope: String?,
        projectId: Long?,
        statuses: List<String>,
        types: List<String>,
        filterTypes: Int = if (types.isEmpty()) 0 else 1,
        limit: Int
    ): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items WHERE id = :id")
    suspend fun getById(id: Long): MemoryItemEntity?

    @Query("SELECT * FROM memory_items WHERE uid = :uid LIMIT 1")
    suspend fun getByUid(uid: String): MemoryItemEntity?

    @Query(
        """
        SELECT status, COUNT(*) AS total FROM memory_items
        WHERE scope = :scope AND (:projectId IS NULL OR projectId = :projectId)
        GROUP BY status
        """
    )
    suspend fun countByStatus(scope: String, projectId: Long?): List<MemoryStatusCountRow>

    @Query(
        """
        SELECT COUNT(*) FROM memory_items
        WHERE scope = :scope AND (:projectId IS NULL OR projectId = :projectId) AND status = 'ACTIVE'
        """
    )
    suspend fun countActive(scope: String, projectId: Long?): Int

    @Query(
        """
        SELECT id FROM memory_items
        WHERE scope = :scope AND (:projectId IS NULL OR projectId = :projectId)
          AND status = 'ACTIVE' AND pinned = 0
        ORDER BY updatedAt ASC
        LIMIT :limit
        """
    )
    suspend fun oldestUnpinnedActive(scope: String, projectId: Long?, limit: Int): List<Long>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE status = 'ACTIVE'
          AND (:scope IS NULL OR scope = :scope)
          AND (:projectId IS NULL OR projectId = :projectId)
        ORDER BY pinned DESC, updatedAt DESC
        """
    )
    suspend fun allActive(scope: String?, projectId: Long?): List<MemoryItemEntity>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE status = 'ACTIVE' AND scope = :scope
          AND (:projectId IS NULL OR projectId = :projectId)
          AND type = :type AND normalized = :normalized
        LIMIT 1
        """
    )
    suspend fun findActiveTwin(scope: String, projectId: Long?, type: String, normalized: String): MemoryItemEntity?

    @Query(
        """
        SELECT * FROM memory_items
        WHERE status = 'ACTIVE' AND scope = :scope
          AND (:projectId IS NULL OR projectId = :projectId)
          AND type = :type AND normalized LIKE '%' || :key || '%'
        ORDER BY updatedAt DESC
        LIMIT 1
        """
    )
    suspend fun findActiveConflict(scope: String, projectId: Long?, type: String, key: String): MemoryItemEntity?

    @Query(
        """
        SELECT m.* FROM memory_fts
        JOIN memory_items m ON m.id = memory_fts.rowid
        WHERE memory_fts MATCH :query
          AND m.status = 'ACTIVE'
          AND (:scope IS NULL OR m.scope = :scope)
          AND (:projectId IS NULL OR m.projectId = :projectId)
        LIMIT :limit
        """
    )
    suspend fun search(query: String, scope: String?, projectId: Long?, limit: Int): List<MemoryItemEntity>

    @Query(
        """
        SELECT * FROM memory_items
        WHERE status = 'ACTIVE' AND normalized LIKE :like
          AND (:scope IS NULL OR scope = :scope)
          AND (:projectId IS NULL OR projectId = :projectId)
        ORDER BY updatedAt DESC
        LIMIT :limit
        """
    )
    suspend fun searchLike(like: String, scope: String?, projectId: Long?, limit: Int): List<MemoryItemEntity>

    @Insert
    suspend fun insertItemRow(item: MemoryItemEntity): Long

    @Insert
    suspend fun insertFts(fts: MemoryFtsEntity)

    @Insert
    suspend fun insertLink(link: MemoryLinkEntity)

    @Update
    suspend fun updateItem(item: MemoryItemEntity)

    @Query("UPDATE memory_fts SET content = :content, normalized = :normalized WHERE rowid = :id")
    suspend fun updateFts(id: Long, content: String, normalized: String)

    @Query("DELETE FROM memory_fts WHERE rowid = :id")
    suspend fun deleteFts(id: Long)

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteItemRow(id: Long)

    @Query("UPDATE memory_items SET confidence = :confidence, updatedAt = :now WHERE id = :id")
    suspend fun updateConfidence(id: Long, confidence: Float, now: Long)

    @Query("UPDATE memory_items SET content = :content, normalized = :normalized, uid = :uid, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateContent(id: Long, content: String, normalized: String, uid: String, updatedAt: Long)

    @Query("UPDATE memory_items SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query("UPDATE memory_items SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, now: Long)

    @Query("UPDATE memory_items SET scope = :scope, projectId = :projectId, uid = :uid, updatedAt = :now WHERE id = :id")
    suspend fun moveScope(id: Long, scope: String, projectId: Long?, uid: String, now: Long)

    @Query("UPDATE memory_items SET lastUsedAt = :now, useCount = useCount + 1 WHERE id IN (:ids)")
    suspend fun touch(ids: List<Long>, now: Long)

    @Transaction
    suspend fun insertItem(item: MemoryItemEntity): Long {
        val id = insertItemRow(item)
        insertFts(MemoryFtsEntity(rowId = id, content = item.content, normalized = item.normalized))
        return id
    }

    @Transaction
    suspend fun deleteById(id: Long) {
        deleteFts(id)
        deleteItemRow(id)
    }

    @Transaction
    suspend fun rewriteContent(id: Long, content: String, normalized: String, uid: String, now: Long) {
        updateContent(id, content, normalized, uid, now)
        updateFts(id, content, normalized)
    }
}