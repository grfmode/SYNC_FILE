package com.sync.app.domain.memory

import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyMemoryImporterTest {

    private val pattern = Regex("""^\[(\d{4}-\d{2}-\d{2})]\s+(\w+)\s+(ACTIVE|SUPERSEDED)\s*:\s*(.+)$""")

    @Test
    fun `baris sesuai format terbaca`() {
        val match = pattern.find("[2026-01-02] FACT ACTIVE: minSdk 26")
        assertTrue(match != null)
    }

    @Test
    fun `baris rusak tidak cocok`() {
        assertTrue(pattern.find("catatan bebas tanpa format") == null)
    }
}