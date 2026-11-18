package com.sync.app.domain.memory

import com.sync.app.data.local.ChatDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Ekspor dan impor memori dalam format `sync-memory`.
 *
 * Impor selalu dua langkah: [preview] lalu [apply]. Pratinjau tidak menyentuh
 * database sehingga pengguna bisa melihat dampaknya lebih dulu. [apply]
 * mengembalikan token undo.
 */
@Singleton
class MemoryExportImport @Inject constructor(
    private val repository: MemoryRepository,
    private val chatDao: ChatDao
) {

    companion object {
        const val SCHEMA = "sync-memory"
        const val VERSION = 1
        const val MAX_ITEMS = 5000

        private val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    @Serializable
    data class Entry(
        @SerialName("uid") val uid: String,
        @SerialName("content") val content: String,
        @SerialName("type") val type: String,
        @SerialName("status") val status: String = "ACTIVE",
        @SerialName("scope") val scope: String = "GLOBAL",
        @SerialName("projectName") val projectName: String? = null,
        @SerialName("pinned") val pinned: Boolean = false,
        @SerialName("confidence") val confidence: Float = 1f,
        @SerialName("createdAt") val createdAt: Long = 0L
    )

    @Serializable
    data class Document(
        @SerialName("schema") val schema: String = SCHEMA,
        @SerialName("version") val version: Int = VERSION,
        @SerialName("exportedAt") val exportedAt: Long,
        @SerialName("items") val items: List<Entry>
    )

    enum class Action { NEW, DUPLICATE, UPDATE, INVALID }

    data class PreviewRow(val index: Int, val content: String, val action: Action, val note: String?)

    data class Preview(
        val rows: List<PreviewRow>,
        val document: Document,
        val newCount: Int,
        val duplicateCount: Int,
        val updateCount: Int,
        val invalidCount: Int
    ) {
        val canApply: Boolean get() = newCount + updateCount > 0 && invalidCount == 0
    }

    data class UndoToken(val insertedIds: List<Long>, val touchedIds: List<Long>)

    suspend fun export(scope: MemoryScope?, projectId: Long?): String {
        val items = repository.allActive(scope, projectId)
        val projectNames = chatDao.observeProjectsSnapshot().associate { it.id to it.name }

        val document = Document(
            exportedAt = System.currentTimeMillis(),
            items = items.map { item ->
                Entry(
                    uid = item.uid,
                    content = item.content,
                    type = item.type.name,
                    status = item.status.name,
                    scope = item.scope.name,
                    projectName = item.projectId?.let { projectNames[it] },
                    pinned = item.pinned,
                    confidence = item.confidence,
                    createdAt = item.createdAt
                )
            }
        )
        return json.encodeToString(document)
    }

    suspend fun preview(raw: String, targetProjectId: Long?): Result<Preview> = runCatching {
        val document = json.decodeFromString<Document>(raw)
        require(document.schema == SCHEMA) { "Berkas bukan ekspor memori SYNC." }
        require(document.version <= VERSION) { "Versi berkas ${document.version} lebih baru dari aplikasi." }
        require(document.items.size <= MAX_ITEMS) { "Berkas berisi ${document.items.size} item, batas $MAX_ITEMS." }

        val existingUids = repository.allActive(null, null).map { it.uid }.toSet()
        val rows = document.items.mapIndexed { index, entry ->
            val type = runCatching { MemoryType.valueOf(entry.type.uppercase()) }.getOrNull()
            val scope = runCatching { MemoryScope.valueOf(entry.scope.uppercase()) }.getOrNull()

            when {
                entry.content.isBlank() -> PreviewRow(index, entry.content, Action.INVALID, "isi kosong")
                type == null -> PreviewRow(index, entry.content, Action.INVALID, "tipe tidak dikenal: ${entry.type}")
                scope == null -> PreviewRow(index, entry.content, Action.INVALID, "scope tidak dikenal: ${entry.scope}")
                entry.uid in existingUids -> PreviewRow(index, entry.content, Action.DUPLICATE, "uid sudah ada")
                entry.status.equals("SUPERSEDED", ignoreCase = true) -> PreviewRow(index, entry.content, Action.UPDATE, "akan jadi riwayat")
                else -> PreviewRow(index, entry.content, Action.NEW, null)
            }
        }

        Preview(
            rows = rows,
            document = document,
            newCount = rows.count { it.action == Action.NEW },
            duplicateCount = rows.count { it.action == Action.DUPLICATE },
            updateCount = rows.count { it.action == Action.UPDATE },
            invalidCount = rows.count { it.action == Action.INVALID }
        )
    }

    suspend fun apply(preview: Preview, targetProjectId: Long?): UndoToken {
        val inserted = mutableListOf<Long>()
        val touched = mutableListOf<Long>()

        preview.document.items.forEachIndexed { index, entry ->
            val row = preview.rows[index]
            if (row.action == Action.DUPLICATE || row.action == Action.INVALID) return@forEachIndexed

            val type = MemoryType.valueOf(entry.type.uppercase())
            val scope = MemoryScope.valueOf(entry.scope.uppercase())
            val projectId = when (scope) {
                MemoryScope.GLOBAL -> null
                MemoryScope.PROJECT -> targetProjectId
            }

            val candidate = MemoryCandidate(
                content = entry.content,
                type = type,
                scope = scope,
                projectId = projectId,
                source = MemorySource.IMPORT,
                confidence = entry.confidence.coerceIn(0f, 1f),
                provenance = "import:${entry.uid}"
            )

            when (val result = repository.remember(candidate)) {
                is MemoryWriteResult.Inserted -> inserted += result.id
                is MemoryWriteResult.Superseded -> {
                    inserted += result.id
                    touched += result.replacedId
                }
                is MemoryWriteResult.Duplicate -> touched += result.id
                is MemoryWriteResult.Rejected -> Unit
            }
        }
        return UndoToken(inserted, touched)
    }

    /** Batalkan hasil [apply] tanpa menyentuh item lain. */
    suspend fun undo(token: UndoToken) {
        token.insertedIds.forEach { repository.delete(it) }
        token.touchedIds.forEach { repository.setStatus(it, MemoryStatus.ACTIVE) }
    }
}