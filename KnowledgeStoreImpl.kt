package com.sync.app.domain.knowledge

import android.content.Context
import android.util.Log
import com.sync.app.data.local.KnowledgeChunkEntity
import com.sync.app.data.local.KnowledgeDao
import com.sync.app.data.local.KnowledgeFileEntity
import com.sync.app.data.local.toDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeStoreImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: KnowledgeDao,
    private val chunkers: ChunkerRegistry
) : KnowledgeRepository {

    private companion object {
        const val TAG = "SYNC_KNOWLEDGE"
        const val MAX_FILES_PER_PROJECT = 5_000
        const val MAX_REPORTED = 100
    }

    override fun observeFiles(projectId: Long): Flow<List<KnowledgeFile>> =
        dao.observeFiles(projectId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun stats(projectId: Long): Pair<Int, Int> =
        dao.countFiles(projectId) to dao.countChunks(projectId)

    private data class Staged(
        val relPath: String,
        val displayName: String,
        val kind: KnowledgeKind,
        val sizeBytes: Long,
        val sha256: String
    )

    override suspend fun importRepo(projectId: Long, source: RepoSource): ImportReport =
        withContext(Dispatchers.IO) {
            val root = projectRoot(projectId)
            val known = dao.getFiles(projectId).map { it.relPath }.toHashSet()
            val seen = HashSet<String>()
            val staged = mutableListOf<Staged>()
            val rejected = mutableListOf<String>()
            var overflow = 0
            var binary = 0
            var large = 0
            var ignored = 0
            var duplicate = 0
            var accepted = 0
            var newCount = 0

            fun note(message: String) {
                if (rejected.size < MAX_REPORTED) rejected += message else overflow++
            }

            source.stream(
                context = context,
                onEntry = { entry ->
                    ensureActive()
                    val isNew = entry.relPath !in known && seen.add(entry.relPath)
                    if (isNew && known.size + newCount >= MAX_FILES_PER_PROJECT) {
                        note("batas $MAX_FILES_PER_PROJECT berkas tercapai: ${entry.relPath}")
                    } else {
                        val file = File(KnowledgePathPolicy.toDiskPath(root, entry.relPath))
                        file.parentFile?.mkdirs()
                        file.writeBytes(entry.bytes)
                        staged += Staged(
                            relPath = entry.relPath,
                            displayName = entry.displayName,
                            kind = entry.kind,
                            sizeBytes = entry.sizeBytes,
                            sha256 = sha256(entry.bytes)
                        )
                        if (isNew) newCount++
                    }
                },
                onReject = { kind, message ->
                    when (kind) {
                        RejectKind.BINARY -> binary++
                        RejectKind.IGNORED -> ignored++
                        RejectKind.LARGE -> { large++; note(message) }
                        RejectKind.INVALID -> note(message)
                    }
                }
            )

            for (item in staged) {
                val existing = dao.getFileByPath(projectId, item.relPath)
                if (existing != null) {
                    if (existing.sha256 == item.sha256 && existing.status == KnowledgeStatus.INDEXED.name) {
                        duplicate++
                        continue
                    }
                    dao.updateContentMeta(existing.id, item.sha256, item.sizeBytes)
                    dao.updateStatus(existing.id, KnowledgeStatus.PENDING.name, null, 0, 0, null)
                    accepted++
                    continue
                }
                dao.upsertFile(
                    KnowledgeFileEntity(
                        projectId = projectId,
                        relPath = item.relPath,
                        displayName = item.displayName,
                        kind = item.kind.name,
                        sizeBytes = item.sizeBytes,
                        sha256 = item.sha256,
                        status = KnowledgeStatus.PENDING.name
                    )
                )
                accepted++
            }
            if (overflow > 0) rejected += "…dan $overflow catatan lain"
            ImportReport(accepted, binary, large, duplicate, rejected, ignored)
        }

    override suspend fun indexPending(projectId: Long): IndexReport = withContext(Dispatchers.IO) {
        var indexed = 0
        var skipped = 0
        var failed = 0
        var chunks = 0
        val errors = mutableListOf<String>()
        val pending = dao.getFiles(projectId).filter {
            it.status == KnowledgeStatus.PENDING.name || it.status == KnowledgeStatus.INDEXING.name
        }
        for (file in pending) {
            try {
                dao.updateStatus(file.id, KnowledgeStatus.INDEXING.name, null, file.cursor, file.chunkCount, null)
                val disk = File(KnowledgePathPolicy.toDiskPath(projectRoot(projectId), file.relPath))
                if (!disk.exists()) {
                    dao.updateStatus(file.id, KnowledgeStatus.FAILED.name, "berkas hilang", 0, 0, null)
                    failed++
                    errors += "${file.relPath}: berkas hilang"
                    continue
                }
                val text = disk.readText()
                if (text.isBlank()) {
                    dao.updateStatus(file.id, KnowledgeStatus.SKIPPED.name, "kosong", 0, 0, null)
                    skipped++
                    continue
                }
                val kind = runCatching { KnowledgeKind.valueOf(file.kind) }.getOrDefault(KnowledgeKind.PLAIN_TEXT)
                val raw = chunkers.forKind(kind).chunk(text).filter { it.content.isNotBlank() }
                val entities = raw.mapIndexed { index, chunk ->
                    KnowledgeChunkEntity(
                        fileId = file.id,
                        ordinal = index,
                        startLine = chunk.startLine,
                        endLine = chunk.endLine,
                        symbol = chunk.symbol,
                        content = chunk.content
                    )
                }
                dao.replaceChunks(file.id, entities, file.relPath)
                dao.updateStatus(
                    file.id,
                    KnowledgeStatus.INDEXED.name,
                    null,
                    entities.size,
                    entities.size,
                    System.currentTimeMillis()
                )
                indexed++
                chunks += entities.size
            } catch (e: Exception) {
                Log.w(TAG, "Gagal mengindeks ${file.relPath}", e)
                dao.updateStatus(file.id, KnowledgeStatus.FAILED.name, e.message, file.cursor, file.chunkCount, null)
                failed++
                errors += "${file.relPath}: ${e.message}"
            }
        }
        IndexReport(indexed, skipped, failed, chunks, errors)
    }

    override suspend fun reindexFile(fileId: Long) {
        val file = dao.getFile(fileId) ?: return
        dao.updateStatus(fileId, KnowledgeStatus.PENDING.name, null, 0, 0, null)
    }

    override suspend fun setEnabled(fileId: Long, enabled: Boolean) {
        val file = dao.getFile(fileId) ?: return
        val target = if (enabled) {
            if (file.chunkCount > 0) KnowledgeStatus.INDEXED else KnowledgeStatus.PENDING
        } else {
            KnowledgeStatus.SKIPPED
        }
        dao.updateStatus(fileId, target.name, file.error, file.cursor, file.chunkCount, file.indexedAt)
    }

    override suspend fun deleteFile(fileId: Long) {
        val file = dao.getFile(fileId) ?: return
        dao.deleteFile(fileId)
        File(KnowledgePathPolicy.toDiskPath(projectRoot(file.projectId), file.relPath)).delete()
    }

    override suspend fun deleteAll(projectId: Long) {
        dao.deleteAllForProject(projectId)
        projectRoot(projectId).deleteRecursively()
    }

    override suspend fun search(query: String, projectId: Long, limit: Int): List<KnowledgeHit> =
        withContext(Dispatchers.IO) {
            val tokens = KnowledgeQueryTerms.meaningful(query)
            if (tokens.isEmpty()) return@withContext emptyList()

            Log.d(TAG, "Search tokens: $tokens")

            val match = tokens.joinToString(" OR ") { "$it*" }
            val rows = try {
                dao.search(match, projectId, limit * 4)
            } catch (e: Exception) {
                Log.w(TAG, "FTS search gagal untuk query: $match", e)
                emptyList()
            }.ifEmpty {
                val longest = tokens.maxByOrNull { it.length } ?: tokens.first()
                try {
                    dao.searchLike("%$longest%", projectId, limit * 4)
                } catch (e: Exception) {
                    Log.w(TAG, "LIKE search gagal untuk token: $longest", e)
                    emptyList()
                }
            }

            Log.d(TAG, "Found ${rows.size} rows from database")

            rows.mapIndexed { index, row ->
                val haystack = row.content.lowercase()
                var score = tokens.count { haystack.contains(it) } * 2.0
                if (row.symbol != null && tokens.any { row.symbol!!.lowercase().contains(it) }) score += 3.0
                if (tokens.any { row.relPath.lowercase().contains(it) }) score += 1.5
                score -= index * 0.1
                score -= row.startLine / 5_000.0
                KnowledgeHit(row.toDomain(), score)
            }.sortedByDescending { it.score }.take(limit)
        }

    override suspend fun listPaths(projectId: Long, limit: Int): List<String> =
        withContext(Dispatchers.IO) { dao.listIndexedPaths(projectId, limit) }

    override suspend fun overviewChunks(projectId: Long, limit: Int): List<KnowledgeHit> =
        withContext(Dispatchers.IO) {
            dao.overviewChunks(projectId, limit).map { KnowledgeHit(it.toDomain(), 0.0) }
        }

    override suspend fun getChunk(id: Long): KnowledgeChunk? =
        dao.getChunksByIds(listOf(id)).firstOrNull()?.toDomain()

    override suspend fun findFilesByName(projectId: Long, nameQuery: String): List<KnowledgeFile> =
        withContext(Dispatchers.IO) {
            dao.getFiles(projectId).filter { file ->
                file.relPath.contains(nameQuery, ignoreCase = true) || file.displayName.contains(nameQuery, ignoreCase = true)
            }.map { it.toDomain() }
        }

    override suspend fun readWholeFile(projectId: Long, relPath: String): String? =
        withContext(Dispatchers.IO) {
            val file = File(KnowledgePathPolicy.toDiskPath(projectRoot(projectId), relPath))
            if (file.exists()) file.readText() else null
        }

    private fun projectRoot(projectId: Long): File =
        File(context.filesDir, "knowledge/p$projectId").apply { mkdirs() }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}