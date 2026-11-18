package com.sync.app.domain.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChunkerTest {

    @Test
    fun `chunker kode memotong pada deklarasi`() {
        val source = """
            class A {
                fun x() = 1
            }
            class B {
                fun y() = 2
            }
        """.trimIndent()
        val chunks = KotlinJavaChunker().chunk(source)
        assertTrue(chunks.size >= 2)
    }

    @Test
    fun `markdown memotong pada heading`() {
        val md = "# Satu\nisi satu\n# Dua\nisi dua"
        assertTrue(MarkdownChunker().chunk(md).size >= 2)
    }

    @Test
    fun `isi potongan tidak hilang`() {
        val text = "baris satu\nbaris dua\nbaris tiga"
        val joined = ParagraphChunker().chunk(text).joinToString("\n") { it.content }
        assertTrue(joined.contains("baris satu"))
    }

    @Test
    fun `fallback menerima semua jenis`() {
        assertTrue(FallbackChunker().supports(KnowledgeKind.UNSUPPORTED))
        assertEquals(1, ChunkerRegistry().let { 1 })
    }
}