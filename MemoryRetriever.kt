package com.sync.app.domain.memory

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mengambil memori yang relevan untuk satu permintaan.
 *
 * Selalu menyertakan seluruh INSTRUCTION dan CONSTRAINT aktif, karena keduanya
 * adalah aturan yang berlaku tanpa peduli topik. Sisanya dicari berdasarkan
 * kemiripan teks terhadap pesan terakhir.
 */
@Singleton
class MemoryRetriever @Inject constructor(
    private val repository: MemoryRepository
) {

    private companion object {
        const val MAX_SEARCH_HITS = 40
    }

    suspend fun retrieve(
        query: String,
        projectId: Long?,
        maxItems: Int
    ): List<MemoryItem> {
        val globalRules = repository.allActive(MemoryScope.GLOBAL, null)
            .filter { it.type == MemoryType.INSTRUCTION || it.type == MemoryType.CONSTRAINT }

        val projectRules = projectId?.let { id ->
            repository.allActive(MemoryScope.PROJECT, id)
                .filter { it.type == MemoryType.INSTRUCTION || it.type == MemoryType.CONSTRAINT }
        }.orEmpty()

        val rules = (globalRules + projectRules)
            .sortedWith(compareBy({ it.type != MemoryType.INSTRUCTION }, { !it.pinned }, { -it.confidence }))

        val remaining = (maxItems - rules.size).coerceAtLeast(0)
        if (remaining == 0 || query.isBlank()) return rules.take(maxItems)

        val globalHits = repository.search(query, MemoryScope.GLOBAL, null, MAX_SEARCH_HITS)
        val projectHits = projectId?.let { repository.search(query, MemoryScope.PROJECT, it, MAX_SEARCH_HITS) }.orEmpty()

        val ruleIds = rules.map { it.id }.toSet()
        val matched = (projectHits + globalHits)
            .filter { it.item.id !in ruleIds }
            .distinctBy { it.item.id }
            .sortedByDescending { it.score }
            .map { it.item }
            .take(remaining)

        return rules + matched
    }

    suspend fun markUsed(items: List<MemoryItem>) {
        val ids = items.filter { it.status == MemoryStatus.ACTIVE }.map { it.id }
        if (ids.isNotEmpty()) repository.touch(ids)
    }
}