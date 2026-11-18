package com.sync.app.domain.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeIndexerTest {

    @Test
    fun `deteksi ekstensi kode`() {
        assertEquals(KnowledgeKind.KOTLIN_JAVA, FileTypeDetector.detect("A.kt", "class A".toByteArray()))
    }

    @Test
    fun `berkas biner ditandai tidak didukung`() {
        assertEquals(KnowledgeKind.UNSUPPORTED, FileTypeDetector.detect("gambar.png", byteArrayOf(0, 1, 2)))
    }

    @Test
    fun `direktori build diabaikan`() {
        assertTrue(FileTypeDetector.isIgnoredDirectory("build"))
    }
}