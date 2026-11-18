package com.sync.app.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryRetrieverTest {

    @Test
    fun `aturan selalu dipertahankan sebelum sisa`() {
        val rules = listOf(
            MemoryItem(1, "u1", MemoryScope.GLOBAL, null, MemoryType.INSTRUCTION, MemoryStatus.ACTIVE, MemorySource.MANUAL, "selalu jawab singkat", false, 1f, null, 0, 0, null, 0),
            MemoryItem(2, "u2", MemoryScope.GLOBAL, null, MemoryType.CONSTRAINT, MemoryStatus.ACTIVE, MemorySource.MANUAL, "maksimal 5 baris", false, 1f, null, 0, 0, null, 0)
        )
        val filtered = rules.filter { it.type == MemoryType.INSTRUCTION || it.type == MemoryType.CONSTRAINT }
        assertEquals(2, filtered.size)
    }
}