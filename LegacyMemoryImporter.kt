package com.sync.app.domain.memory

import android.util.Log
import com.sync.app.data.local.ChatDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Impor sekali jalan dari `projects.memoryText` lama.
 *
 * Sifat:
 * - Idempoten: uid dihitung dari isi, jadi menjalankan ulang tidak menggandakan.
 * - Tidak merusak: `memoryText` tidak dikosongkan, sehingga rollback cukup
 *   dengan menghapus hasil impor.
 * - Tidak membuang: baris yang tidak sesuai format disimpan sebagai satu
 *   catatan mentah, bukan dihilangkan.
 */
@Singleton
class LegacyMemoryImporter @Inject constructor(
    private val chatDao: ChatDao,
    private val memoryRepository: MemoryRepository
) {

    private companion object {
        const val TAG = "SYNC_MEMORY"
        const val MARKER = "[legacy-import]"
        const val RAW_MARKER = "[legacy-import-raw]"

        val linePattern = Regex("""^\[(\d{4}-\d{2}-\d{2})]\s+(\w+)\s+(ACTIVE|SUPERSEDED)\s*:\s*(.+)$""")
    }

    data class Report(
        val projectsScanned: Int = 0,
        val itemsImported: Int = 0,
        val rawBlocksKept: Int = 0,
        val linesDropped: Int = 0
    )

    suspend fun importAll(): Report {
        var scanned = 0
        var imported = 0
        var rawKept = 0
        var dropped = 0

        val projects = chatDao.getProjectsWithMemory()
        for (project in projects) {
            val memoryText = project.memoryText?.takeIf { it.isNotBlank() } ?: continue
            scanned++

            val unparsed = mutableListOf<String>()
            for (line in memoryText.lines()) {
                val trimmed = line.trim()
                if (trimmed.isBlank()) continue

                val match = linePattern.find(trimmed)
                if (match == null) {
                    unparsed += trimmed
                    continue
                }

                val type = runCatching { MemoryType.valueOf(match.groupValues[2].uppercase()) }.getOrNull()
                if (type == null) {
                    unparsed += trimmed
                    continue
                }

                val content = match.groupValues[4].trim()
                if (content.length < 3) {
                    dropped++
                    continue
                }

                val candidate = MemoryCandidate(
                    content = content,
                    type = type,
                    scope = MemoryScope.PROJECT,
                    projectId = project.id,
                    source = MemorySource.LEGACY,
                    confidence = 0.9f,
                    provenance = "$MARKER project=${project.id} date=${match.groupValues[1]}"
                )

                when (memoryRepository.remember(candidate)) {
                    is MemoryWriteResult.Inserted -> imported++
                    is MemoryWriteResult.Superseded -> imported++
                    is MemoryWriteResult.Duplicate -> Unit
                    is MemoryWriteResult.Rejected -> dropped++
                }
            }

            if (unparsed.isNotEmpty()) {
                val block = unparsed.joinToString("\n").take(500)
                val candidate = MemoryCandidate(
                    content = block,
                    type = MemoryType.FACT,
                    scope = MemoryScope.PROJECT,
                    projectId = project.id,
                    source = MemorySource.LEGACY,
                    confidence = 0.4f,
                    provenance = "$RAW_MARKER project=${project.id}"
                )
                when (memoryRepository.remember(candidate)) {
                    is MemoryWriteResult.Inserted -> rawKept++
                    is MemoryWriteResult.Superseded -> rawKept++
                    else -> Unit
                }
            }
        }

        Log.i(TAG, "Impor legacy: $scanned project, $imported item, $rawKept blok mentah, $dropped baris gugur")
        return Report(scanned, imported, rawKept, dropped)
    }

    /** Hapus semua hasil impor legacy, termasuk blok mentah. */
    suspend fun rollback() {
        memoryRepository.allActive(null, null)
            .filter { it.source == MemorySource.LEGACY }
            .forEach { memoryRepository.delete(it.id) }
    }
}