package com.sync.app.domain.memory

import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryExtractorTest {

    @Test
    fun `instruksi terdeteksi`() {
        val result = MemoryExtractor.extract(
            "Selalu gunakan bahasa Indonesia dalam dokumentasi proyek ini.",
            MemoryScope.GLOBAL, null, MemorySource.AUTO
        )
        assertTrue(result.any { it.type == MemoryType.INSTRUCTION })
    }

    @Test
    fun `batas maksimal tiga item per pesan`() {
        val text = (1..6).joinToString(". ") { "Selalu gunakan pola nomor $it dalam kode" }
        val result = MemoryExtractor.extract(text, MemoryScope.GLOBAL, null, MemorySource.AUTO)
        assertTrue(result.size <= 3)
    }

    @Test
    fun `pertanyaan tidak menjadi memori`() {
        val result = MemoryExtractor.extract("Apa itu coroutine?", MemoryScope.GLOBAL, null, MemorySource.AUTO)
        assertTrue(result.none { it.type == MemoryType.FACT && it.content.contains("coroutine?") })
    }
}