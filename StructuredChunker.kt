package com.sync.app.domain.knowledge

/**
 * Chunker JSON/XML: memotong pada pemisah tingkat atas, lalu jatuh ke
 * paragraf bila strukturnya tidak terbaca.
 */
class StructuredChunker : Chunker {

    override fun supports(kind: KnowledgeKind): Boolean =
        kind == KnowledgeKind.JSON || kind == KnowledgeKind.XML

    override fun chunk(text: String): List<RawChunk> {
        val isJson = text.trimStart().startsWith("{") || text.trimStart().startsWith("[")
        val lines = text.lines()
        val result = mutableListOf<RawChunk>()
        var start = 0
        var depth = 0

        fun flush(endExclusive: Int) {
            if (endExclusive <= start) return
            val block = lines.subList(start, endExclusive).joinToString("\n")
            if (block.isNotBlank()) result += ChunkMath.splitOversized(block, start + 1, null)
            start = endExclusive
        }

        lines.forEachIndexed { index, line ->
            depth += line.count { it == '{' || it == '[' } - line.count { it == '}' || it == ']' }
            val separator = if (isJson) {
                depth == 1 && (line.trimEnd().endsWith(",") || line.trimEnd().endsWith("{"))
            } else {
                depth <= 1 && line.trimStart().startsWith("<") && index > start && line.length < 200
            }
            if (separator && index > start) flush(index)
        }
        flush(lines.size)

        return result.ifEmpty { ParagraphChunker().chunk(text) }
    }
}