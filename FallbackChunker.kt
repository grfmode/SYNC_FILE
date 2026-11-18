package com.sync.app.domain.knowledge

/**
 * Jaring terakhir. Menerima jenis apa pun dan memotong per blok karakter
 * pada batas baris.
 */
class FallbackChunker : Chunker {

    override fun supports(kind: KnowledgeKind): Boolean = true

    override fun chunk(text: String): List<RawChunk> =
        ChunkMath.splitOversized(text, 1, null)
}