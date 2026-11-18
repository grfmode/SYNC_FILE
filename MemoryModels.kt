package com.sync.app.domain.memory

/** Jenis memori. Urutan enum = urutan prioritas saat anggaran konteks menipis. */
enum class MemoryType {
    INSTRUCTION,
    CONSTRAINT,
    PREFERENCE,
    DECISION,
    FACT
}

enum class MemoryStatus {
    ACTIVE,
    SUPERSEDED,
    ARCHIVED
}

enum class MemoryScope {
    GLOBAL,
    PROJECT
}

enum class MemorySource {
    MANUAL,
    AUTO,
    IMPORT,
    LEGACY
}

/** Satu baris memori di lapisan domain. */
data class MemoryItem(
    val id: Long,
    val uid: String,
    val scope: MemoryScope,
    val projectId: Long?,
    val type: MemoryType,
    val status: MemoryStatus,
    val source: MemorySource,
    val content: String,
    val pinned: Boolean,
    val confidence: Float,
    val provenance: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?,
    val useCount: Int
)

/** Calon memori sebelum disimpan; belum punya id. */
data class MemoryCandidate(
    val content: String,
    val type: MemoryType,
    val scope: MemoryScope,
    val projectId: Long?,
    val source: MemorySource,
    val confidence: Float,
    val provenance: String?
)

data class MemoryQuery(
    val scope: MemoryScope? = null,
    val projectId: Long? = null,
    val statuses: Set<MemoryStatus> = setOf(MemoryStatus.ACTIVE),
    val types: Set<MemoryType> = emptySet(),
    val pinnedFirst: Boolean = true,
    val limit: Int = 200
)

data class MemoryStats(
    val active: Int = 0,
    val superseded: Int = 0,
    val archived: Int = 0
)

/** Hasil pencarian dengan skor hasil ranking. */
data class MemoryHit(
    val item: MemoryItem,
    val score: Double
)