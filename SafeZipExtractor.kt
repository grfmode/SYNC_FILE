package com.sync.app.domain.knowledge

import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/**
 * Pembaca arsip zip dengan batas keras.
 *
 * Yang dijaga: zip-slip, jumlah entri, kedalaman path, ukuran per berkas,
 * total ukuran, dan rasio kompresi (zip bomb). Arsip yang melanggar ditolak
 * per entri, bukan menggagalkan seluruh impor.
 *
 * [readStreaming] menyerahkan satu entri pada satu waktu, sehingga arsip besar
 * tidak perlu dimuat seluruhnya ke memori.
 */
object SafeZipExtractor {

    const val MAX_ENTRIES = 20_000
    const val MAX_SKIPPED = 200_000
    const val MAX_FILE_BYTES = 10L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 400L * 1024 * 1024
    const val MAX_RATIO = 200L
    const val RATIO_CHECK_FROM = 1L * 1024 * 1024

    data class Result(
        val entries: List<RawEntry>,
        val rejected: List<String>
    )

    data class RawEntry(
        val relPath: String,
        val bytes: ByteArray,
        val compressedSize: Long
    )

    /** Versi mengumpulkan semua entri; cocok untuk arsip kecil dan uji. */
    fun read(stream: ZipInputStream): Result {
        val entries = mutableListOf<RawEntry>()
        val rejected = mutableListOf<String>()
        readStreaming(
            stream,
            onEntry = { entries += it },
            onReject = { _, message -> rejected += message }
        )
        return Result(entries, rejected)
    }

    fun readStreaming(
        stream: ZipInputStream,
        onEntry: (RawEntry) -> Unit,
        onReject: (RejectKind, String) -> Unit
    ) {
        var total = 0L
        var count = 0
        var skipped = 0
        var seen = 0
        var broken = false

        while (true) {
            val entry = try {
                stream.nextEntry ?: break
            } catch (e: Exception) {
                onReject(RejectKind.INVALID, "arsip rusak setelah $seen entri: ${e.message}")
                broken = true
                break
            }

            if (entry.isDirectory) {
                stream.closeEntry()
                continue
            }
            seen++

            val safe = KnowledgePathPolicy.sanitize(entry.name)
            if (safe == null) {
                onReject(RejectKind.INVALID, "path ditolak: ${entry.name}")
                stream.closeEntry()
                continue
            }

            if (FileTypeDetector.isIgnoredPath(safe)) {
                skipped++
                onReject(RejectKind.IGNORED, "folder diabaikan: $safe")
                stream.closeEntry()
                if (skipped > MAX_SKIPPED) {
                    onReject(RejectKind.INVALID, "terlalu banyak entri yang diabaikan; sisa arsip dilewati")
                    broken = true
                    break
                }
                continue
            }

            count++
            if (count > MAX_ENTRIES) {
                onReject(RejectKind.INVALID, "jumlah entri melebihi $MAX_ENTRIES")
                broken = true
                break
            }

            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
            var size = 0L
            var tooLarge = false

            try {
                while (true) {
                    val read = stream.read(chunk)
                    if (read < 0) break
                    size += read
                    total += read
                    if (size > MAX_FILE_BYTES || total > MAX_TOTAL_BYTES) {
                        tooLarge = true
                        break
                    }
                    buffer.write(chunk, 0, read)
                }
            } catch (e: Exception) {
                onReject(RejectKind.INVALID, "arsip terpotong pada $safe: ${e.message}")
                broken = true
                break
            }

            if (tooLarge) {
                onReject(RejectKind.LARGE, "terlalu besar: $safe")
                stream.closeEntry()
                if (total > MAX_TOTAL_BYTES) {
                    onReject(RejectKind.LARGE, "total ukuran melebihi ${MAX_TOTAL_BYTES / (1024 * 1024)} MB; sisa arsip dilewati")
                    break
                }
                continue
            }

            val compressed = entry.compressedSize
            if (compressed > 0 && size >= RATIO_CHECK_FROM && size / compressed > MAX_RATIO) {
                onReject(RejectKind.INVALID, "rasio kompresi mencurigakan: $safe")
                stream.closeEntry()
                continue
            }

            onEntry(RawEntry(safe, buffer.toByteArray(), compressed))
            stream.closeEntry()
        }

        if (seen == 0 && !broken) {
            onReject(
                RejectKind.INVALID,
                "tidak ada berkas yang terbaca (bagian zip terpecah dipilih sendiri-sendiri, atau bukan zip)"
            )
        }
    }
}
