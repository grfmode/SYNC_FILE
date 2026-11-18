package com.sync.app.domain.knowledge

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KnowledgeRetriever @Inject constructor(
    private val repository: KnowledgeRepository
) {
    companion object {
        const val TAG = "SYNC_KNOWLEDGE"
        const val MAX_CANDIDATES = 50
        const val MAX_SELECTED = 12
        const val MAX_MERGED_CHARS = 2_000
        const val MAX_OVERVIEW_PATHS = 400
    }

    data class Source(
        val label: String,
        val relPath: String,
        val startLine: Int,
        val endLine: Int,
        val content: String,
        val chunkId: Long
    )

    suspend fun retrieve(query: String, projectId: Long?, maxSources: Int, maxChars: Int = 8000): List<Source> {
        if (projectId == null) return emptyList()

        val wholeFiles = retrieveWholeFiles(query, projectId, 3, maxChars)
        if (wholeFiles.isNotEmpty()) {
            val hits = repository.search(query, projectId, MAX_CANDIDATES).ifEmpty { emptyList() }
            val chunkSources = hits.map { hit ->
                Source(
                    label = "",
                    relPath = hit.chunk.relPath,
                    startLine = hit.chunk.startLine,
                    endLine = hit.chunk.endLine,
                    content = hit.chunk.content,
                    chunkId = hit.chunk.id
                )
            }
            return (wholeFiles + chunkSources).take(maxSources)
        }

        val hits = repository.search(query, projectId, MAX_CANDIDATES)
            .ifEmpty { repository.overviewChunks(projectId, maxSources.coerceAtMost(MAX_SELECTED)) }
        if (hits.isEmpty()) return emptyList()

        val selected = mutableListOf<Source>()
        val usedFiles = mutableMapOf<String, MutableList<KnowledgeHit>>()
        for (hit in hits) {
            if (selected.size >= maxSources.coerceAtMost(MAX_SELECTED)) break
            val path = hit.chunk.relPath
            val siblings = usedFiles.getOrPut(path) { mutableListOf() }
            if (siblings.any { kotlin.math.abs(it.chunk.startLine - hit.chunk.startLine) <= 2 }) continue
            siblings += hit
            selected += Source(
                label = "",
                relPath = path,
                startLine = hit.chunk.startLine,
                endLine = hit.chunk.endLine,
                content = hit.chunk.content,
                chunkId = hit.chunk.id
            )
        }
        return selected
            .sortedWith(compareBy<Source> { it.relPath }.thenBy { it.startLine })
            .mapIndexed { index, source -> source.copy(label = "S${index + 1}") }
    }

    private suspend fun retrieveWholeFiles(query: String, projectId: Long, maxFiles: Int, maxChars: Int): List<Source> {
        val candidates = WholeFileMatcher.candidates(query)
        if (candidates.isEmpty()) return emptyList()

        val allFiles = repository.findFilesByName(projectId, "")
        val filesToLoad = WholeFileMatcher.match(allFiles.map { it.relPath to it.displayName }, candidates).take(maxFiles)
        Log.d(TAG, "Whole-file: kandidat=$candidates, cocok=$filesToLoad")
        if (filesToLoad.isEmpty()) return emptyList()

        val sources = mutableListOf<Source>()
        var totalChars = 0
        for (relPath in filesToLoad) {
            val content = repository.readWholeFile(projectId, relPath) ?: continue
            val lines = content.lines()
            if (totalChars + content.length > maxChars) {
                val allowed = maxChars - totalChars
                val truncated = content.substring(0, allowed.coerceAtMost(content.length)).trimEnd() +
                    "\n\n... [DIPOTONG: file terlalu besar untuk dimuat utuh, hanya $allowed karakter pertama] ..."
                sources += Source(label = "[WHOLE]", relPath = relPath, startLine = 1, endLine = lines.size, content = truncated, chunkId = -1L)
                Log.d(TAG, "Whole-file: $relPath DIPOTONG ($allowed dari ${content.length} karakter)")
                break
            }
            sources += Source(label = "[WHOLE]", relPath = relPath, startLine = 1, endLine = lines.size, content = content, chunkId = -1L)
            totalChars += content.length
            Log.d(TAG, "Whole-file: $relPath utuh (${lines.size} baris, ${content.length} karakter)")
        }
        return sources
    }

    suspend fun overview(projectId: Long, maxChars: Int): String? {
        val (files, chunks) = repository.stats(projectId)
        if (files == 0 || chunks == 0) return null
        val paths = repository.listPaths(projectId, MAX_OVERVIEW_PATHS)
        val text = StringBuilder("INDEX: $files berkas, $chunks potongan terindeks. Path:\n")
        var shown = 0
        for (path in paths) {
            if (text.length + path.length + 1 > maxChars) break
            text.append(path).append('\n')
            shown++
        }
        if (shown < files) text.append("… dan ${files - shown} berkas lain\n")
        return text.toString()
    }

    fun merge(sources: List<Source>): List<Source> {
        if (sources.size <= 1) return sources
        val merged = mutableListOf<Source>()
        var buffer: Source? = null
        for (source in sources.sortedWith(compareBy<Source> { it.relPath }.thenBy { it.startLine })) {
            val current = buffer
            if (current != null &&
                current.relPath == source.relPath &&
                source.startLine - current.endLine <= 2 &&
                current.content.length + source.content.length <= MAX_MERGED_CHARS
            ) {
                buffer = current.copy(
                    endLine = source.endLine,
                    content = current.content + "\n" + source.content
                )
            } else {
                current?.let { merged += it }
                buffer = source
            }
        }
        buffer?.let { merged += it }
        return merged.mapIndexed { index, source -> source.copy(label = "S${index + 1}") }
    }
}