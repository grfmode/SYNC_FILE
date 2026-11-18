package com.sync.app.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryExportImportTest {

    @Test
    fun `konstanta skema stabil`() {
        assertEquals("sync-memory", MemoryExportImport.SCHEMA)
        assertEquals(1, MemoryExportImport.VERSION)
    }

    @Test
    fun `batas item masuk akal`() {
        assertEquals(5000, MemoryExportImport.MAX_ITEMS)
    }
}