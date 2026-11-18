package com.sync.app.domain.knowledge

/**
 * Chunker paragraf untuk teks bebas dan dokumen tanpa struktur.
 * Menjaga baris tumpang tindih agar kalimat di batas tidak hilang konteks.
 */
class ParagraphChunker : Chunker {

    override fun supports(kind: KnowledgeKind): Boolean = kind == KnowledgeKind.PLAIN_TEXT

    override fun chunk(text: String): List<RawChunk> {
        val lines = text.lines()
        val result = mutableListOf<RawChunk>()
        var current = StringBuilder()
        var start = 1
        var lineCursor = 1

        fun flush() {
            if (current.isNotBlank()) {
                result += ChunkMath.splitOversized(current.toString().trimEnd(), start, null)
            }
            current = StringBuilder()
        }

        for (line in lines) {
            if (line.isBlank()) {
                flush()
                start = lineCursor + 1
                lineCursor++
                continue
            }
            if (current.length + line.length + 1 > ChunkLimits.MAX_CHARS) {
                flush()
                start = (lineCursor - ChunkLimits.OVERLAP_LINES).coerceAtLeast(1)
            }
            current.appendLine(line)
            lineCursor++
        }
        flush()

        return result.ifEmpty { listOf(RawChunk(1, lines.size, null, text)) }
    }
}