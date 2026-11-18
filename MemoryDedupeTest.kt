package com.sync.app.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MemoryDedupeTest {

    @Test
    fun `kata fungsi diabaikan`() {
        assertEquals(
            MemoryNormalizer.dedupeKey("pakai tab untuk indentasi"),
            MemoryNormalizer.dedupeKey("pakai tab indentasi")
        )
    }

    @Test
    fun `tipe berbeda menghasilkan uid berbeda`() {
        val key = MemoryNormalizer.dedupeKey("pakai tab")
        assertNotEquals(
            MemoryNormalizer.uid(MemoryScope.GLOBAL, null, MemoryType.FACT, key),
            MemoryNormalizer.uid(MemoryScope.GLOBAL, null, MemoryType.PREFERENCE, key)
        )
    }
}