package com.sync.app.domain.knowledge

import java.io.File

/**
 * Aturan path relatif.
 *
 * Semua path dari sumber luar harus lewat sini sebelum menyentuh disk, karena
 * arsip zip bisa berisi `../` (zip-slip) atau nama dengan karakter aneh.
 */
object KnowledgePathPolicy {

    const val MAX_DEPTH = 32
    const val MAX_LENGTH = 400

    private val unsafeChars = Regex("""[^A-Za-z0-9._\-/]""")
    private val multiSlash = Regex("""/{2,}""")

    /** Null bila path tidak dapat dipakai. */
    fun sanitize(raw: String): String? {
        var path = raw.replace('\\', '/').trim()
        if (path.isBlank()) return null
        if (path.length > MAX_LENGTH) return null
        if (path.startsWith("/")) path = path.removePrefix("/")
        if (Regex("^[A-Za-z]:").containsMatchIn(path)) path = path.substring(2)
        if (path.split('/').any { it == ".." }) return null

        path = path.replace(unsafeChars, "_").replace(multiSlash, "/").trim('/')
        if (path.isBlank()) return null

        val segments = path.split('/')
        if (segments.size > MAX_DEPTH) return null
        if (segments.any { it.isBlank() || it == "." }) return null

        return path
    }

    /** Path disk privat untuk sebuah berkas; tetap di bawah [root]. */
    fun toDiskPath(root: File, relPath: String): String {
        val safe = sanitize(relPath) ?: relPath
        return File(root, safe).absolutePath
    }
}