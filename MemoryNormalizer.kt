package com.sync.app.domain.memory

import java.security.MessageDigest
import java.util.Locale

/**
 * Normalisasi teks untuk pencarian dan deteksi duplikat.
 *
 * [dedupeKey] sengaja membuang kata fungsi supaya "pakai tab, bukan spasi" dan
 * "jangan pakai spasi" tetap terbedakan, tetapi "Pakai Tab bukan Spasi." dan
 * "pakai tab bukan spasi" dianggap sama.
 */
object MemoryNormalizer {

    private val stopwords = setOf(
        "yang", "dan", "di", "ke", "dari", "untuk", "pada", "dengan", "itu", "ini",
        "adalah", "akan", "juga", "saja", "agar", "supaya", "bisa", "dapat", "kalau",
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "is", "are",
        "be", "it", "this", "that", "with", "as", "at", "by"
    )

    private val punctuation = Regex("""[^\p{L}\p{N}\s_./:-]""")
    private val whitespace = Regex("""\s+""")

    fun normalize(text: String): String =
        text.lowercase(Locale.ROOT)
            .replace(punctuation, " ")
            .replace(whitespace, " ")
            .trim()

    fun dedupeKey(text: String): String {
        val tokens = normalize(text)
            .split(' ')
            .filter { it.isNotBlank() && it !in stopwords }
            .distinct()
        val base = if (tokens.isEmpty()) normalize(text) else tokens.joinToString(" ")
        return sha1(base).take(24)
    }

    fun uid(scope: MemoryScope, projectId: Long?, type: MemoryType, dedupeKey: String): String {
        val seed = "${scope.name}|${projectId ?: 0}|${type.name}|$dedupeKey"
        return sha1(seed).take(16)
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}