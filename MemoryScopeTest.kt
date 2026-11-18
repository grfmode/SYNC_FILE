package com.sync.app.domain.memory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryScopeTest {

    @Test
    fun `scope global membuang projectId`() {
        val candidate = MemoryCandidate("aturan global", MemoryType.CONSTRAINT, MemoryScope.GLOBAL, 7L, MemorySource.MANUAL, 1f, null)
        assertNull(if (candidate.scope == MemoryScope.GLOBAL) null else candidate.projectId)
    }

    @Test
    fun `scope project menyimpan projectId`() {
        val candidate = MemoryCandidate("aturan proyek", MemoryType.CONSTRAINT, MemoryScope.PROJECT, 7L, MemorySource.MANUAL, 1f, null)
        assertEquals(7L, candidate.projectId)
    }
}