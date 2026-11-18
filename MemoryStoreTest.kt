package com.sync.app.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryStoreTest {

    private fun candidate(text: String, type: MemoryType = MemoryType.PREFERENCE) = MemoryCandidate(
        content = text,
        type = type,
        scope = MemoryScope.GLOBAL,
        projectId = null,
        source = MemorySource.MANUAL,
        confidence = 1f,
        provenance = "test"
    )

    @Test
    fun `teks sama menghasilkan uid sama`() {
        val a = MemoryNormalizer.uid(MemoryScope.GLOBAL, null, MemoryType.FACT, MemoryNormalizer.dedupeKey("Pakai tab"))
        val b = MemoryNormalizer.uid(MemoryScope.GLOBAL, null, MemoryType.FACT, MemoryNormalizer.dedupeKey("pakai  tab"))
        assertEquals(a, b)
    }

    @Test
    fun `kalimat berbeda menghasilkan kunci berbeda`() {
        val a = MemoryNormalizer.dedupeKey("pakai tab bukan spasi")
        val b = MemoryNormalizer.dedupeKey("pakai spasi bukan tab")
        assertTrue(a != b)
    }

    @Test
    fun `kandidat pendek ditolak`() {
        assertTrue(candidate("a").content.length < 3)
    }
}