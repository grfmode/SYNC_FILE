package com.sync.app.data.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.sync.app.domain.chat.AttachmentManager
import com.sync.app.domain.chat.PreparedAttachment
import com.sync.app.domain.provider.AttachmentPayload
import com.sync.app.domain.provider.GeneratedImage
import com.sync.app.model.AttachmentType
import com.sync.app.model.AttachmentUi
import com.sync.app.domain.knowledge.FileTypeDetector
import com.sync.app.domain.knowledge.KnowledgeKind
import com.sync.app.domain.knowledge.RejectKind
import com.sync.app.domain.knowledge.SafeZipExtractor
import com.sync.app.domain.knowledge.SplitArchivePlanner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AttachmentManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AttachmentManager {

    private companion object {
        const val MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024
        const val MAX_ZIP_BYTES = 50L * 1024 * 1024
        const val MAX_ZIP_TEXT_CHARS = 1000_000

        val ZIP_MIME_TYPES = setOf("application/zip", "application/x-zip-compressed", "application/x-zip")

        val TEXT_EXTENSIONS = setOf(
            "kt", "txt", "md", "json", "xml", "csv", "yaml", "yml", "html", "css",
            "js", "ts", "py", "java", "c", "cpp", "h", "cs", "go", "rs", "php", "rb",
            "swift", "sql", "sh", "bat", "ini", "toml", "gradle", "kts"
        )
    }

    override suspend fun loadAttachment(uri: Uri): Result<AttachmentUi> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                var fileName = "File"
                var fileSize = 0L
                resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                if (SplitArchivePlanner.isSplitPartName(fileName)) {
                    return@withContext Result.failure(
                        AttachmentException(
                            "\"$fileName\" adalah bagian zip terpecah. Impor semua bagiannya lewat Project → Knowledge → Impor."
                        )
                    )
                }

                val limit = if (isZipName(fileName)) MAX_ZIP_BYTES else MAX_FILE_SIZE_BYTES
                if (fileSize > limit) {
                    return@withContext Result.failure(
                        AttachmentException("Ukuran \"$fileName\" melebihi batas ${limit / (1024 * 1024)} MB")
                    )
                }

                val mimeType = resolver.getType(uri) ?: "application/octet-stream"
                val type = if (mimeType.startsWith("image/")) AttachmentType.Image else AttachmentType.Document

                Result.success(AttachmentUi(uri.toString(), fileName, mimeType, type))
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Result.failure(AttachmentException(t.message ?: "Gagal membaca attachment", t))
            }
        }

    /**
     * Satu lampiran gagal tidak menghentikan yang lain. Entri gagal tetap
     * dikembalikan dengan alasan agar bisa ditampilkan di UI.
     */
    override suspend fun preparePayloads(attachments: List<AttachmentUi>): List<PreparedAttachment> =
        withContext(Dispatchers.IO) {
            attachments.map { att ->
                try {
                    if (isZipName(att.name) || att.mimeType in ZIP_MIME_TYPES) {
                        return@map PreparedAttachment(
                            ui = att,
                            payload = AttachmentPayload(
                                mimeType = "text/plain",
                                base64Data = "",
                                type = att.type,
                                textContent = expandZip(att)
                            ),
                            error = null
                        )
                    }
                    val bytes = readBytes(att)
                    val ext = att.name.substringAfterLast('.', "").lowercase()
                    val isTextMime = att.mimeType.startsWith("text/") ||
                        att.mimeType in listOf("application/json", "application/xml", "application/javascript")

                    if (isTextMime || TEXT_EXTENSIONS.contains(ext)) {
                        PreparedAttachment(
                            ui = att,
                            payload = AttachmentPayload(
                                mimeType = att.mimeType,
                                base64Data = "",
                                type = att.type,
                                textContent = bytes.toString(Charsets.UTF_8)
                            ),
                            error = null
                        )
                    } else {
                        PreparedAttachment(
                            ui = att,
                            payload = AttachmentPayload(
                                mimeType = att.mimeType,
                                base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP),
                                type = att.type,
                                textContent = null
                            ),
                            error = null
                        )
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    PreparedAttachment(att, null, e.message ?: "gagal membaca berkas")
                }
            }
        }

    private fun isZipName(name: String): Boolean = name.trim().lowercase().endsWith(".zip")

    /**
     * Provider tidak menerima zip, jadi isinya dibaca di perangkat dan dikirim
     * sebagai teks. Berkas biner dan folder build/.git dilewati. Bila melewati
     * batas karakter, pemotongan dinyatakan di awal teks, tidak diam-diam.
     */
    private fun expandZip(att: AttachmentUi): String {
        val stream = context.contentResolver.openInputStream(Uri.parse(att.uri))
            ?: throw AttachmentException("Tidak dapat membaca file ${att.name}")

        val body = StringBuilder()
        var included = 0
        var omitted = 0
        var binary = 0
        var ignored = 0
        var capped = false
        val problems = mutableListOf<String>()

        stream.use { input ->
            ZipInputStream(input.buffered()).use { zip ->
                SafeZipExtractor.readStreaming(
                    zip,
                    onEntry = { entry ->
                        val kind = FileTypeDetector.detect(entry.relPath, entry.bytes)
                        if (kind == KnowledgeKind.UNSUPPORTED) {
                            binary++
                        } else {
                            val text = entry.bytes.toString(Charsets.UTF_8)
                            if (capped || body.length + text.length > MAX_ZIP_TEXT_CHARS) {
                                capped = true
                                omitted++
                            } else {
                                body.append("--- File: ").append(att.name).append('/').append(entry.relPath).append(" ---\n")
                                    .append(text).append("\n\n")
                                included++
                            }
                        }
                    },
                    onReject = { kind, message ->
                        when (kind) {
                            RejectKind.IGNORED -> ignored++
                            RejectKind.BINARY -> binary++
                            else -> {
                                if (problems.size < 5) problems += message
                            }
                        }
                    }
                )
            }
        }

        if (included == 0) {
            throw AttachmentException(
                "Zip ${att.name} tidak berisi berkas teks yang bisa dibaca" +
                    if (problems.isNotEmpty()) " (${problems.first()})" else ""
            )
        }

        val header = buildString {
            append("--- Zip: ${att.name} — $included berkas teks disertakan")
            if (binary > 0) append(", $binary biner dilewati")
            if (ignored > 0) append(", $ignored berkas di folder build/.git dst. dilewati")
            if (capped) append(", $omitted berkas lain TIDAK disertakan karena melewati batas $MAX_ZIP_TEXT_CHARS karakter")
            append(" ---")
        }
        return header + "\n\n" + body
    }

    private fun readBytes(att: AttachmentUi): ByteArray {
        val uri = Uri.parse(att.uri)
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw AttachmentException("Tidak dapat membaca file ${att.name}")

        return stream.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_FILE_SIZE_BYTES) {
                    throw AttachmentException("File ${att.name} melebihi batas 10 MB")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    override suspend fun storeGeneratedImage(image: GeneratedImage): AttachmentUi? = withContext(Dispatchers.IO) {
        try {
            val mime = image.mimeType.takeIf { it.startsWith("image/") } ?: "image/png"
            val bytes = image.base64Data?.let { Base64.decode(it.substringAfter(","), Base64.DEFAULT) }
            if (bytes != null) {
                val ext = when (mime.lowercase()) {
                    "image/jpeg" -> "jpg"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    else -> "png"
                }
                val directory = File(context.filesDir, "generated_images").apply { mkdirs() }
                val file = File(directory, "sync_${UUID.randomUUID()}.$ext")
                file.writeBytes(bytes)
                AttachmentUi(Uri.fromFile(file).toString(), file.name, mime, AttachmentType.Image)
            } else {
                image.url?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let {
                    AttachmentUi(it, "generated_image", mime, AttachmentType.Image)
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}

class AttachmentException(message: String, cause: Throwable? = null) : Exception(message, cause)