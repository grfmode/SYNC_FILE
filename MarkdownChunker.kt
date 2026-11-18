package com.sync.app.domain.knowledge

/**
 * Chunker markdown: memotong pada heading dan membawa heading tersebut ke
 * dalam potongan sebagai penanda konteks.
 */
class MarkdownChunker : Chunker {

    private val heading = Regex("""^#{1,6}\s+(.*)$""")

    override fun supports(kind: KnowledgeKind): Boolean = kind == KnowledgeKind.MARKDOWN

    override fun chunk(text: String): List<RawChunk> {
        val lines = text.lines()
        val result = mutableListOf<RawChunk>()
        var start = 0
        var title: String? = null

        fun flush(endExclusive: Int) {
            if (endExclusive <= start) return
            val block = lines.subList(start, endExclusive).joinToString("\n")
            if (block.isNotBlank()) result += ChunkMath.splitOversized(block, start + 1, title)
            start = endExclusive
        }

        lines.forEachIndexed { index, line ->
            val match = heading.find(line)
            if (match != null && index > start) {
                flush(index)
                title = match.groupValues[1].trim()
            }
        }
        flush(lines.size)

        return result.ifEmpty { listOf(RawChunk(1, lines.size, null, text)) }
    }
}