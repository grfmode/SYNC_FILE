package com.sync.app.domain.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExtractorTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `path normal diterima`() {
        assertEquals("src/Main.kt", KnowledgePathPolicy.sanitize("src/Main.kt"))
    }

    @Test
    fun `zip slip ditolak`() {
        assertNull(KnowledgePathPolicy.sanitize("../luar.kt"))
    }

    @Test
    fun `entri terbaca`() {
        val bytes = zip("a.kt" to "class A")
        val result = java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { SafeZipExtractor.read(it) }
        assertEquals(1, result.entries.size)
        assertTrue(result.rejected.isEmpty())
    }
}