package com.sync.app.domain.knowledge

/** Jenis berkas hasil deteksi. Menentukan pemilih chunker. */
enum class KnowledgeKind {
    KOTLIN_JAVA,
    MARKDOWN,
    JSON,
    XML,
    PLAIN_TEXT,
    UNSUPPORTED
}

enum class KnowledgeStatus {
    PENDING,
    INDEXING,
    INDEXED,
    FAILED,
    SKIPPED
}

/** Satu berkas sumber. */
data class KnowledgeFile(
    val id: Long,
    val projectId: Long,
    val relPath: String,
    val displayName: String,
    val kind: KnowledgeKind,
    val sizeBytes: Long,
    val status: KnowledgeStatus,
    val chunkCount: Int,
    val error: String?,
    val indexedAt: Long?
)

/** Potongan berkas dengan batas baris nyata. */
data class KnowledgeChunk(
    val id: Long,
    val fileId: Long,
    val relPath: String,
    val ordinal: Int,
    val startLine: Int,
    val endLine: Int,
    val symbol: String?,
    val content: String
)

/** Hasil pencarian dengan skor ranking. */
data class KnowledgeHit(
    val chunk: KnowledgeChunk,
    val score: Double
)

/** Hasil penguraian satu sumber menjadi daftar berkas relatif. */
data class RepoEntry(
    val relPath: String,
    val displayName: String,
    val kind: KnowledgeKind,
    val sizeBytes: Long,
    val bytes: ByteArray
)

data class ImportReport(
    val accepted: Int,
    val skippedBinary: Int,
    val skippedLarge: Int,
    val skippedDuplicate: Int,
    val rejected: List<String>,
    val skippedIgnored: Int = 0
)

/** Alasan sebuah entri tidak diimpor. Menentukan cara dilaporkan. */
enum class RejectKind {
    BINARY,
    LARGE,
    IGNORED,
    INVALID
}

data class IndexReport(
    val filesIndexed: Int,
    val filesSkipped: Int,
    val filesFailed: Int,
    val chunks: Int,
    val errors: List<String>
)