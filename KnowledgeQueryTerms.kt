package com.sync.app.domain.knowledge

import android.util.Log

object KnowledgeQueryTerms {
    private const val TAG = "SYNC_KNOWLEDGE_TERMS"

    private val stopWords = setOf(
        "tolong", "periksa", "cek", "coba", "lihat", "baca", "analisa", "analisis", "audit",
        "review", "check", "please", "look", "read", "examine", "inspect", "verify", "validate",
        "file", "files", "berkas", "isi", "kode", "code", "proyek", "project", "aplikasi", "app",
        "program", "sistem", "system", "fitur", "feature", "fungsi", "function", "method", "class",
        "yang", "dan", "di", "ke", "dari", "untuk", "dengan", "pada", "oleh", "tentang", "mengenai",
        "seperti", "bagaimana", "kenapa", "mengapa", "karena", "sebab", "jika", "kalau", "bila",
        "agar", "supaya", "meski", "walaupun", "tetapi", "tapi", "namun", "serta", "atau",
        "ini", "itu", "saya", "aku", "kamu", "anda", "kami", "kita", "mereka", "dia", "beliau",
        "bisa", "dapat", "boleh", "mau", "akan", "sedang", "sudah", "belum", "tidak", "bukan",
        "ada", "punya", "memiliki", "adalah", "merupakan", "yaitu", "yakni",
        "apa", "apakah", "siapa", "kapan", "dimana", "di mana", "kemana", "ke mana",
        "why", "what", "who", "when", "where", "how", "which",
        "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
        "from", "about", "as", "into", "through", "during", "before", "after", "above", "below",
        "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "do", "does", "did",
        "will", "would", "shall", "should", "may", "might", "must", "can", "could",
        "this", "that", "these", "those", "it", "its", "they", "them", "their", "we", "us", "our",
        "you", "your", "he", "him", "his", "she", "her", "i", "me", "my",
        "cara", "bagian", "tipe", "type", "nama", "name", "nilai", "value", "hasil", "result",
        "contoh", "example", "semua", "seluruh", "setiap", "each", "every", "all", "any", "some",
        "ya", "iya", "dong", "nih", "sih", "deh", "kan", "kok", "lah",
        "yes", "no", "ok", "okay", "sure", "thanks", "thank",
        "lalu", "kemudian", "then", "so", "now", "sekarang", "jawab", "sesuai"
    )

    fun meaningful(query: String): List<String> {
        val tokens = query.lowercase()
            .split(Regex("\\W+"))
            .filter { it.length > 2 && it !in stopWords }
            .distinct()
        if (tokens.isNotEmpty()) {
            Log.d(TAG, "Meaningful tokens: $tokens (from: $query)")
        } else {
            Log.d(TAG, "No meaningful tokens found in: $query")
        }
        return tokens
    }
}