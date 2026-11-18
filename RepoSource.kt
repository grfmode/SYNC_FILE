package com.sync.app.domain.knowledge

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Satu arsip logis; [uris] sudah berurutan (bagian terpecah dibaca sebagai satu aliran). */
data class ArchiveUnit(val label: String, val uris: List<Uri>)

/**
 * Sumber impor.
 *
 * Repo bisa masuk sebagai arsip zip (utuh atau terpecah) atau sebagai pohon
 * berkas hasil pilih banyak berkas. Keduanya dinormalisasi ke path relatif
 * sebelum diindeks, sehingga jalur pemrosesan sesudahnya identik.
 *
 * Entri diserahkan satu per satu lewat callback; isi arsip tidak pernah
 * dimuat seluruhnya ke memori.
 */
sealed class RepoSource {

    data class Archive(val units: List<ArchiveUnit>) : RepoSource()

    data class Tree(val uris: List<Uri>, val names: List<String>) : RepoSource()

    fun stream(
        context: Context,
        onEntry: (RepoEntry) -> Unit,
        onReject: (RejectKind, String) -> Unit
    ) {
        when (this) {
            is Archive -> units.forEach { readUnit(context, it, onEntry, onReject) }
            is Tree -> readTree(context, this, onEntry, onReject)
        }
    }

    private fun readUnit(
        context: Context,
        unit: ArchiveUnit,
        onEntry: (RepoEntry) -> Unit,
        onReject: (RejectKind, String) -> Unit
    ) {
        val resolver = context.contentResolver

        fun opener(uri: Uri): () -> InputStream = {
            resolver.openInputStream(uri) ?: throw IOException("tidak dapat dibuka")
        }

        try {
            val openers = unit.uris.map { opener(it) }
            val raw = if (openers.size == 1) openers[0]() else SplitArchiveStreams.concat(openers)
            val prepared = SplitArchiveStreams.skipSpanMarker(raw)

            ZipInputStream(prepared).use { zip ->
                SafeZipExtractor.readStreaming(
                    zip,
                    onEntry = { entry ->
                        val kind = FileTypeDetector.detect(entry.relPath, entry.bytes)
                        if (kind == KnowledgeKind.UNSUPPORTED) {
                            onReject(RejectKind.BINARY, "biner dilewati: ${entry.relPath}")
                        } else {
                            onEntry(
                                RepoEntry(
                                    relPath = entry.relPath,
                                    displayName = entry.relPath.substringAfterLast('/'),
                                    kind = kind,
                                    sizeBytes = entry.bytes.size.toLong(),
                                    bytes = entry.bytes
                                )
                            )
                        }
                    },
                    onReject = { kind, message -> onReject(kind, "${unit.label}: $message") }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onReject(RejectKind.INVALID, "${unit.label}: gagal dibaca (${e.message ?: e.javaClass.simpleName})")
        }
    }

    private fun readTree(
        context: Context,
        tree: Tree,
        onEntry: (RepoEntry) -> Unit,
        onReject: (RejectKind, String) -> Unit
    ) {
        tree.uris.forEachIndexed { index, uri ->
            val name = tree.names.getOrNull(index) ?: "file_$index"
            val safe = KnowledgePathPolicy.sanitize(name)
            if (safe == null) {
                onReject(RejectKind.INVALID, "nama ditolak: $name")
                return@forEachIndexed
            }
            val bytes = try {
                context.contentResolver.openInputStream(uri)?.use { readLimited(it, SafeZipExtractor.MAX_FILE_BYTES) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (bytes == null) {
                // null berarti tidak terbaca atau melebihi batas; bedakan lewat pembacaan ulang ringan.
                onReject(RejectKind.LARGE, "tidak dapat dibaca atau terlalu besar (maks 10 MB): $name")
                return@forEachIndexed
            }
            val kind = FileTypeDetector.detect(safe, bytes)
            if (kind == KnowledgeKind.UNSUPPORTED) {
                onReject(RejectKind.BINARY, "biner dilewati: $name")
                return@forEachIndexed
            }
            onEntry(RepoEntry(safe, safe.substringAfterLast('/'), kind, bytes.size.toLong(), bytes))
        }
    }

    /** Null bila isi melebihi [limit] byte; tidak pernah menampung lebih dari batas. */
    private fun readLimited(input: InputStream, limit: Long): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
