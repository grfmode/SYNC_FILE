package com.sync.app.domain.memory

import kotlinx.coroutines.flow.Flow

/**
 * Kontrak penyimpanan memori.
 *
 * Pemanggil tidak pernah menulis uid sendiri; [remember] yang menghitungnya,
 * sehingga duplikat selalu tertangkap di satu tempat.
 */
interface MemoryRepository {

    fun observe(query: MemoryQuery): Flow<List<MemoryItem>>

    suspend fun getById(id: Long): MemoryItem?

    suspend fun countByStatus(scope: MemoryScope, projectId: Long?): MemoryStats

    /**
     * Simpan satu calon memori.
     *
     * Perilaku:
     * - duplikat persis pada scope/tipe yang sama → confidence dinaikkan, tidak ada baris baru
     * - teks berbeda tetapi uid sama (tabrakan dedupe) → baris lama jadi SUPERSEDED
     * - melewati plafon ACTIVE → ACTIVE tertua yang tidak dipin diarsipkan
     */
    suspend fun remember(candidate: MemoryCandidate): MemoryWriteResult

    suspend fun rememberAll(candidates: List<MemoryCandidate>): List<MemoryWriteResult>

    suspend fun updateContent(id: Long, content: String): Boolean

    suspend fun setPinned(id: Long, pinned: Boolean)

    suspend fun setStatus(id: Long, status: MemoryStatus)

    suspend fun moveScope(id: Long, scope: MemoryScope, projectId: Long?)

    suspend fun delete(id: Long)

    suspend fun touch(ids: List<Long>)

    suspend fun search(query: String, scope: MemoryScope?, projectId: Long?, limit: Int): List<MemoryHit>

    suspend fun allActive(scope: MemoryScope?, projectId: Long?): List<MemoryItem>
}

sealed class MemoryWriteResult {
    data class Inserted(val id: Long) : MemoryWriteResult()
    data class Duplicate(val id: Long) : MemoryWriteResult()
    data class Superseded(val id: Long, val replacedId: Long) : MemoryWriteResult()
    data class Rejected(val reason: String) : MemoryWriteResult()
}