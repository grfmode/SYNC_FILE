package com.sync.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class KnowledgeHitRow(
    val chunkId: Long,
    val fileId: Long,
    val relPath: String,
    val startLine: Int,
    val endLine: Int,
    val symbol: String?,
    val content: String
)

@Dao
interface KnowledgeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: KnowledgeFileEntity): Long

    @Insert
    suspend fun insertChunkRow(chunk: KnowledgeChunkEntity): Long

    @Insert
    suspend fun insertChunkFts(fts: KnowledgeFtsEntity)

    @Query("DELETE FROM knowledge_chunks WHERE fileId = :fileId")
    suspend fun deleteChunksForFile(fileId: Long)

    @Query("DELETE FROM knowledge_fts WHERE rowid IN (SELECT id FROM knowledge_chunks WHERE fileId = :fileId)")
    suspend fun deleteChunksFtsForFile(fileId: Long)

    @Transaction
    suspend fun replaceChunks(fileId: Long, chunks: List<KnowledgeChunkEntity>, relPath: String) {
        deleteChunksFtsForFile(fileId)
        deleteChunksForFile(fileId)
        chunks.forEach { chunk ->
            val id = insertChunkRow(chunk)
            insertChunkFts(
                KnowledgeFtsEntity(
                    rowId = id,
                    content = chunk.content,
                    symbol = chunk.symbol.orEmpty(),
                    relPath = relPath
                )
            )
        }
    }

    @Query("SELECT * FROM knowledge_files WHERE projectId = :projectId ORDER BY relPath ASC")
    fun observeFiles(projectId: Long): Flow<List<KnowledgeFileEntity>>

    @Query("SELECT * FROM knowledge_files WHERE projectId = :projectId ORDER BY relPath ASC")
    suspend fun getFiles(projectId: Long): List<KnowledgeFileEntity>

    @Query("SELECT * FROM knowledge_files WHERE id = :id")
    suspend fun getFile(id: Long): KnowledgeFileEntity?

    @Query("SELECT * FROM knowledge_files WHERE projectId = :projectId AND relPath = :relPath LIMIT 1")
    suspend fun getFileByPath(projectId: Long, relPath: String): KnowledgeFileEntity?

    @Query("SELECT * FROM knowledge_files WHERE status = 'INDEXING' OR status = 'PENDING'")
    suspend fun getUnfinished(): List<KnowledgeFileEntity>

    @Query("UPDATE knowledge_files SET status = :status, error = :error, cursor = :cursor, chunkCount = :chunkCount, indexedAt = :indexedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String?, cursor: Int, chunkCount: Int, indexedAt: Long?)

    @Query("UPDATE knowledge_files SET sha256 = :sha256, sizeBytes = :sizeBytes WHERE id = :id")
    suspend fun updateContentMeta(id: Long, sha256: String, sizeBytes: Long)

    @Query("DELETE FROM knowledge_files WHERE id = :id")
    suspend fun deleteFile(id: Long)

    @Query("DELETE FROM knowledge_files WHERE projectId = :projectId")
    suspend fun deleteAllForProject(projectId: Long)

    @Query(
        """
        SELECT c.id AS chunkId, c.fileId AS fileId, f.relPath AS relPath,
               c.startLine AS startLine, c.endLine AS endLine,
               c.symbol AS symbol, c.content AS content
        FROM knowledge_fts
        JOIN knowledge_chunks c ON c.id = knowledge_fts.rowid
        JOIN knowledge_files f ON f.id = c.fileId
        WHERE knowledge_fts MATCH :query
          AND f.projectId = :projectId
          AND f.status = 'INDEXED'
        LIMIT :limit
        """
    )
    suspend fun search(query: String, projectId: Long, limit: Int): List<KnowledgeHitRow>

    @Query(
        """
        SELECT c.id AS chunkId, c.fileId AS fileId, f.relPath AS relPath,
               c.startLine AS startLine, c.endLine AS endLine,
               c.symbol AS symbol, c.content AS content
        FROM knowledge_chunks c
        JOIN knowledge_files f ON f.id = c.fileId
        WHERE f.projectId = :projectId
          AND f.status = 'INDEXED'
          AND c.content LIKE :like
        LIMIT :limit
        """
    )
    suspend fun searchLike(like: String, projectId: Long, limit: Int): List<KnowledgeHitRow>

    @Query(
        """
        SELECT c.id AS chunkId, c.fileId AS fileId, f.relPath AS relPath,
               c.startLine AS startLine, c.endLine AS endLine,
               c.symbol AS symbol, c.content AS content
        FROM knowledge_chunks c
        JOIN knowledge_files f ON f.id = c.fileId
        WHERE c.id IN (:ids)
        """
    )
    suspend fun getChunksByIds(ids: List<Long>): List<KnowledgeHitRow>

    @Query("SELECT relPath FROM knowledge_files WHERE projectId = :projectId AND status = 'INDEXED' ORDER BY relPath ASC LIMIT :limit")
    suspend fun listIndexedPaths(projectId: Long, limit: Int): List<String>

    @Query(
        """
        SELECT c.id AS chunkId, c.fileId AS fileId, f.relPath AS relPath,
               c.startLine AS startLine, c.endLine AS endLine,
               c.symbol AS symbol, c.content AS content
        FROM knowledge_chunks c
        JOIN knowledge_files f ON f.id = c.fileId
        WHERE f.projectId = :projectId AND f.status = 'INDEXED' AND c.ordinal = 0
        ORDER BY CASE f.kind WHEN 'KOTLIN_JAVA' THEN 0 WHEN 'MARKDOWN' THEN 1 ELSE 2 END, f.relPath ASC
        LIMIT :limit
        """
    )
    suspend fun overviewChunks(projectId: Long, limit: Int): List<KnowledgeHitRow>

    @Query("SELECT COUNT(*) FROM knowledge_files WHERE projectId = :projectId")
    suspend fun countFiles(projectId: Long): Int

    @Query("SELECT COALESCE(SUM(chunkCount), 0) FROM knowledge_files WHERE projectId = :projectId")
    suspend fun countChunks(projectId: Long): Int
}