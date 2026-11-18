package com.sync.app.domain.knowledge

data class RawChunk(
    val startLine: Int,
    val endLine: Int,
    val symbol: String?,
    val content: String
)

interface Chunker {
    fun supports(kind: KnowledgeKind): Boolean
    fun chunk(text: String): List<RawChunk>
}

object ChunkLimits {
    const val MAX_CHARS = 1_200
    const val MIN_CHARS = 160
    const val OVERLAP_LINES = 2
}

class ChunkerRegistry(
    private val chunkers: List<Chunker> = listOf(
        KotlinJavaChunker(),
        MarkdownChunker(),
        StructuredChunker(),
        ParagraphChunker(),
        FallbackChunker()
    )
) {
    fun forKind(kind: KnowledgeKind): Chunker =
        chunkers.first { it.supports(kind) }
}

internal object ChunkMath {
    fun lineOf(text: String, offset: Int): Int {
        var line = 1
        var index = 0
        while (index < offset && index < text.length) {
            if (text[index] == '\n') line++
            index++
        }
        return line
    }

    fun splitOversized(
        text: String,
        startLine: Int,
        symbol: String?
    ): List<RawChunk> {
        val lines = text.lines()
        if (text.length <= ChunkLimits.MAX_CHARS) {
            return listOf(RawChunk(startLine, startLine + lines.size - 1, symbol, text))
        }
        val chunks = mutableListOf<RawChunk>()
        var current = StringBuilder()
        var chunkStart = startLine
        var lineCursor = startLine
        var overlapLines = mutableListOf<String>()

        for (line in lines) {
            if (current.length + line.length + 1 > ChunkLimits.MAX_CHARS && current.isNotEmpty()) {
                chunks += RawChunk(chunkStart, lineCursor - 1, symbol, current.toString().trimEnd())
                overlapLines.clear()
                val currentLines = current.toString().lines()
                val overlapCount = ChunkLimits.OVERLAP_LINES.coerceAtMost(currentLines.size)
                overlapLines.addAll(currentLines.takeLast(overlapCount))
                current = StringBuilder()
                overlapLines.forEach { current.appendLine(it) }
                chunkStart = lineCursor - overlapCount + 1
            }
            current.appendLine(line)
            lineCursor++
        }

        if (current.isNotBlank()) {
            val lastChunk = RawChunk(chunkStart, lineCursor - 1, symbol, current.toString().trimEnd())
            if (lastChunk.content.length < ChunkLimits.MIN_CHARS && chunks.isNotEmpty()) {
                val prev = chunks.removeLast()
                val merged = prev.content + "\n" + lastChunk.content
                chunks += RawChunk(prev.startLine, lastChunk.endLine, symbol, merged.trimEnd())
            } else {
                chunks += lastChunk
            }
        }
        return chunks
    }
}