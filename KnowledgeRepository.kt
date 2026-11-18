package com.sync.app.domain.knowledge

import kotlinx.coroutines.flow.Flow

interface KnowledgeRepository {
    fun observeFiles(projectId: Long): Flow<List<KnowledgeFile>>
    suspend fun stats(projectId: Long): Pair<Int, Int>
    suspend fun importRepo(projectId: Long, source: RepoSource): ImportReport
    suspend fun indexPending(projectId: Long): IndexReport
    suspend fun reindexFile(fileId: Long)
    suspend fun setEnabled(fileId: Long, enabled: Boolean)
    suspend fun deleteFile(fileId: Long)
    suspend fun deleteAll(projectId: Long)
    suspend fun search(query: String, projectId: Long, limit: Int): List<KnowledgeHit>
    suspend fun getChunk(id: Long): KnowledgeChunk?
    suspend fun listPaths(projectId: Long, limit: Int): List<String> = emptyList()
    suspend fun overviewChunks(projectId: Long, limit: Int): List<KnowledgeHit> = emptyList()

    suspend fun findFilesByName(projectId: Long, nameQuery: String): List<KnowledgeFile>
    suspend fun readWholeFile(projectId: Long, relPath: String): String?
}