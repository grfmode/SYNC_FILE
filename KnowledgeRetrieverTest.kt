package com.sync.app.domain.knowledge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeRetrieverTest {

    private fun source(path: String, line: Int, content: String) = KnowledgeRetriever.Source(
        label = "", relPath = path, startLine = line, endLine = line + 2, content = content, chunkId = line.toLong()
    )

    private fun retriever() = KnowledgeRetriever(FakeKnowledgeRepository())

    @Test
    fun `potongan bersebelahan digabung`() {
        val merged = retriever().merge(
            listOf(source("a.kt", 1, "satu"), source("a.kt", 3, "dua"))
        )
        assertEquals(1, merged.size)
    }

    @Test
    fun `berkas berbeda tidak digabung`() {
        val merged = retriever().merge(
            listOf(source("a.kt", 1, "satu"), source("b.kt", 2, "dua"))
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun `label sumber berurutan`() {
        val merged = retriever().merge(
            listOf(source("a.kt", 1, "satu"), source("b.kt", 5, "dua"))
        )
        assertEquals(listOf("S1", "S2"), merged.map { it.label })
    }
}

private class FakeKnowledgeRepository : KnowledgeRepository {
    override fun observeFiles(projectId: Long): Flow<List<KnowledgeFile>> = emptyFlow()

    override suspend fun stats(projectId: Long): Pair<Int, Int> = 0 to 0

    override suspend fun importRepo(projectId: Long, source: RepoSource): ImportReport =
        ImportReport(0, 0, 0, 0, emptyList())

    override suspend fun indexPending(projectId: Long): IndexReport =
        IndexReport(0, 0, 0, 0, emptyList())

    override suspend fun reindexFile(fileId: Long) {}

    override suspend fun setEnabled(fileId: Long, enabled: Boolean) {}

    override suspend fun deleteFile(fileId: Long) {}

    override suspend fun deleteAll(projectId: Long) {}

    override suspend fun search(query: String, projectId: Long, limit: Int): List<KnowledgeHit> = emptyList()

    override suspend fun getChunk(id: Long): KnowledgeChunk? = null
}