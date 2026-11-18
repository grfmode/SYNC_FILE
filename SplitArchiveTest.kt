package com.sync.app.domain.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SplitArchiveTest {

    @Test
    fun `info-zip terpecah diurutkan dan bagian terakhir zip`() {
        val plan = SplitArchivePlanner.plan(listOf("repo.z02", "repo.zip", "repo.z01", "catatan.md"))
        assertEquals(1, plan.units.size)
        assertEquals(listOf(2, 0, 1), plan.units[0].indices)
        assertEquals(listOf(3), plan.loose)
        assertTrue(plan.problems.isEmpty())
    }

    @Test
    fun `pecahan mentah 001 002 diurutkan`() {
        val plan = SplitArchivePlanner.plan(listOf("a.zip.002", "a.zip.001"))
        assertEquals(listOf(1, 0), plan.units.single().indices)
    }

    @Test
    fun `bagian hilang dilaporkan`() {
        val plan = SplitArchivePlanner.plan(listOf("a.z01", "a.z03", "a.zip"))
        assertTrue(plan.units.isEmpty())
        assertEquals(1, plan.problems.size)
    }

    @Test
    fun `bagian terakhir zip belum dipilih dilaporkan`() {
        val plan = SplitArchivePlanner.plan(listOf("a.z01", "a.z02"))
        assertTrue(plan.units.isEmpty())
        assertEquals(1, plan.problems.size)
    }

    @Test
    fun `beberapa zip utuh masing-masing satu arsip`() {
        val plan = SplitArchivePlanner.plan(listOf("part1.zip", "part2.zip"))
        assertEquals(2, plan.units.size)
    }

    private fun sampleZip(): ByteArray {
        val content = ByteArray(30_000).also { Random(7).nextBytes(it) }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("src/a.kt"))
            zip.write(content)
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    private fun read(stream: java.io.InputStream): SafeZipExtractor.Result =
        ZipInputStream(SplitArchiveStreams.skipSpanMarker(stream)).use { SafeZipExtractor.read(it) }

    @Test
    fun `pecahan mentah disambung kembali terbaca`() {
        val bytes = sampleZip()
        val third = bytes.size / 3
        val parts = listOf(
            bytes.copyOfRange(0, third),
            bytes.copyOfRange(third, third * 2),
            bytes.copyOfRange(third * 2, bytes.size)
        )
        val joined = SplitArchiveStreams.concat(parts.map { part -> { ByteArrayInputStream(part) } })
        val result = read(joined)
        assertEquals(1, result.entries.size)
        assertEquals(30_000, result.entries[0].bytes.size)
    }

    @Test
    fun `penanda info-zip di awal dibuang`() {
        val marker = byteArrayOf(0x50, 0x4B, 0x07, 0x08)
        val result = read(ByteArrayInputStream(marker + sampleZip()))
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `bagian pertama saja tidak menghasilkan entri dan ada alasan`() {
        val bytes = sampleZip()
        val result = read(ByteArrayInputStream(bytes.copyOfRange(0, 100)))
        assertTrue(result.entries.isEmpty())
        assertTrue(result.rejected.isNotEmpty())
    }

    @Test
    fun `folder build diabaikan`() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("build/x.kt")); zip.write("a".toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("src/y.kt")); zip.write("b".toByteArray()); zip.closeEntry()
        }
        val result = ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { SafeZipExtractor.read(it) }
        assertEquals(listOf("src/y.kt"), result.entries.map { it.relPath })
    }
}
