package com.sync.app.domain.memory

import com.sync.app.data.local.MemoryDao
import com.sync.app.data.local.MemoryItemEntity
import com.sync.app.data.local.MemoryLinkEntity
import com.sync.app.data.local.toDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementasi penyimpanan memori.
 *
 * Batas ACTIVE dijaga di sini, bukan di UI, karena jalur tulis terbesar berasal
 * dari ekstraksi otomatis yang tidak melewati layar mana pun.
 */
@Singleton
class MemoryStoreImpl @Inject constructor(
    private val dao: MemoryDao
) : MemoryRepository {

    private companion object {
        const val MAX_ACTIVE_GLOBAL = 400
        const val MAX_ACTIVE_PROJECT = 250
        const val MIN_CONTENT_LENGTH = 3
        const val MAX_CONTENT_LENGTH = 500
        const val DUPLICATE_CONFIDENCE_STEP = 0.05f
        const val RELATION_SUPERSEDES = "SUPERSEDES"
        const val DAY_MS = 86_400_000L
    }

    override fun observe(query: MemoryQuery): Flow<List<MemoryItem>> =
        dao.observe(
            scope = query.scope?.name,
            projectId = query.projectId,
            statuses = query.statuses.map { it.name },
            types = query.types.map { it.name },
            limit = query.limit
        ).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): MemoryItem? = dao.getById(id)?.toDomain()

    override suspend fun countByStatus(scope: MemoryScope, projectId: Long?): MemoryStats {
        val rows = dao.countByStatus(scope.name, projectId)
        var active = 0
        var superseded = 0
        var archived = 0
        rows.forEach { row ->
            when (row.status) {
                MemoryStatus.ACTIVE.name -> active = row.total
                MemoryStatus.SUPERSEDED.name -> superseded = row.total
                MemoryStatus.ARCHIVED.name -> archived = row.total
            }
        }
        return MemoryStats(active, superseded, archived)
    }

    override suspend fun remember(candidate: MemoryCandidate): MemoryWriteResult {
        val cleaned = prepare(candidate.content)
            ?: return MemoryWriteResult.Rejected("isi terlalu pendek atau kosong")

        val normalized = MemoryNormalizer.normalize(cleaned)
        val key = MemoryNormalizer.dedupeKey(cleaned)
        val uid = MemoryNormalizer.uid(candidate.scope, candidate.projectId, candidate.type, key)
        val now = System.currentTimeMillis()

        val existingByUid = dao.getByUid(uid)
        if (existingByUid != null) {
            val boosted = (existingByUid.confidence + DUPLICATE_CONFIDENCE_STEP).coerceAtMost(1f)
            dao.updateConfidence(existingByUid.id, boosted, now)
            return MemoryWriteResult.Duplicate(existingByUid.id)
        }

        val twin = dao.findActiveTwin(candidate.scope.name, candidate.projectId, candidate.type.name, normalized)
        if (twin != null) {
            dao.updateConfidence(twin.id, (twin.confidence + DUPLICATE_CONFIDENCE_STEP).coerceAtMost(1f), now)
            return MemoryWriteResult.Duplicate(twin.id)
        }

        val replaced = dao.findActiveConflict(
            scope = candidate.scope.name,
            projectId = candidate.projectId,
            type = candidate.type.name,
            key = key
        )

        val id = dao.insertItem(
            MemoryItemEntity(
                uid = uid,
                scope = candidate.scope,
                projectId = candidate.projectId,
                type = candidate.type,
                status = MemoryStatus.ACTIVE,
                source = candidate.source,
                content = cleaned,
                normalized = normalized,
                pinned = false,
                confidence = candidate.confidence.coerceIn(0f, 1f),
                provenance = candidate.provenance,
                createdAt = now,
                updatedAt = now
            )
        )

        if (replaced != null) {
            dao.setStatus(replaced.id, MemoryStatus.SUPERSEDED.name, now)
            dao.insertLink(MemoryLinkEntity(fromId = id, toId = replaced.id, relation = RELATION_SUPERSEDES))
        }

        enforceCap(candidate.scope, candidate.projectId, now)
        return if (replaced != null) MemoryWriteResult.Superseded(id, replaced.id) else MemoryWriteResult.Inserted(id)
    }

    override suspend fun rememberAll(candidates: List<MemoryCandidate>): List<MemoryWriteResult> =
        candidates.map { remember(it) }

    override suspend fun updateContent(id: Long, content: String): Boolean {
        val cleaned = prepare(content) ?: return false
        val existing = dao.getById(id) ?: return false
        val normalized = MemoryNormalizer.normalize(cleaned)
        val key = MemoryNormalizer.dedupeKey(cleaned)
        dao.rewriteContent(
            id = id,
            content = cleaned,
            normalized = normalized,
            uid = MemoryNormalizer.uid(existing.scope, existing.projectId, existing.type, key),
            now = System.currentTimeMillis()
        )
        return true
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) =
        dao.setPinned(id, pinned, System.currentTimeMillis())

    override suspend fun setStatus(id: Long, status: MemoryStatus) =
        dao.setStatus(id, status.name, System.currentTimeMillis())

    override suspend fun moveScope(id: Long, scope: MemoryScope, projectId: Long?) {
        val existing = dao.getById(id) ?: return
        val targetProject = if (scope == MemoryScope.GLOBAL) null else projectId
        val uid = MemoryNormalizer.uid(scope, targetProject, existing.type, MemoryNormalizer.dedupeKey(existing.content))
        dao.moveScope(id, scope.name, targetProject, uid, System.currentTimeMillis())
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)

    override suspend fun touch(ids: List<Long>) {
        if (ids.isNotEmpty()) dao.touch(ids, System.currentTimeMillis())
    }

    override suspend fun search(
        query: String,
        scope: MemoryScope?,
        projectId: Long?,
        limit: Int
    ): List<MemoryHit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val rows = runCatching { dao.search(trimmed, scope?.name, projectId, limit * 3) }
            .getOrNull()
            .orEmpty()
            .ifEmpty { dao.searchLike("%${MemoryNormalizer.normalize(trimmed)}%", scope?.name, projectId, limit * 3) }

        val now = System.currentTimeMillis()
        return rows.map { entity ->
            val ageDays = ((now - entity.updatedAt) / DAY_MS).toDouble()
            var score = 0.0
            score += typeWeight(entity.type) * 2.0
            if (entity.pinned) score += 4.0
            score += entity.confidence * 2.0
            score -= ageDays.coerceAtMost(365.0) * 0.004
            score += entity.useCount.coerceAtMost(20) * 0.05
            MemoryHit(entity.toDomain(), score)
        }.sortedByDescending { it.score }.take(limit)
    }

    override suspend fun allActive(scope: MemoryScope?, projectId: Long?): List<MemoryItem> =
        dao.allActive(scope?.name, projectId).map { it.toDomain() }

    private fun prepare(raw: String): String? {
        val masked = SecretFilter.mask(raw.trim())
        val collapsed = masked.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length < MIN_CONTENT_LENGTH) return null
        return if (collapsed.length <= MAX_CONTENT_LENGTH) {
            collapsed
        } else {
            collapsed.take(MAX_CONTENT_LENGTH).trimEnd() + "…"
        }
    }

    private fun typeWeight(type: MemoryType): Double = when (type) {
        MemoryType.INSTRUCTION -> 3.0
        MemoryType.CONSTRAINT -> 2.6
        MemoryType.PREFERENCE -> 2.0
        MemoryType.DECISION -> 1.4
        MemoryType.FACT -> 1.0
    }

    /** ACTIVE tertua yang tidak dipin diarsipkan saat plafon terlampaui. */
    private suspend fun enforceCap(scope: MemoryScope, projectId: Long?, now: Long) {
        val cap = if (scope == MemoryScope.GLOBAL) MAX_ACTIVE_GLOBAL else MAX_ACTIVE_PROJECT
        val active = dao.countActive(scope.name, projectId)
        if (active <= cap) return
        val overflow = dao.oldestUnpinnedActive(scope.name, projectId, active - cap)
        overflow.forEach { dao.setStatus(it, MemoryStatus.ARCHIVED.name, now) }
    }
}