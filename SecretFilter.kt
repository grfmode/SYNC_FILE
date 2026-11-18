package com.sync.app.domain.memory

/**
 * Menyamarkan rahasia sebelum teks masuk ke memori.
 *
 * Memori ikut terkirim ke provider pada setiap request, jadi token yang
 * tertangkap di dalamnya akan bocor berulang kali. Penyamaran dilakukan di
 * jalur tulis, bukan di jalur baca, agar isi tabel tetap bersih.
 */
object SecretFilter {

    const val MASK = "••••"

    private data class Rule(val name: String, val regex: Regex, val keepPrefix: Int)

    private val rules = listOf(
        Rule("openai", Regex("""\bsk-[A-Za-z0-9_\-]{16,}"""), 3),
        Rule("anthropic", Regex("""\bsk-ant-[A-Za-z0-9_\-]{16,}"""), 7),
        Rule("github", Regex("""\bgh[pousr]_[A-Za-z0-9]{20,}"""), 4),
        Rule("aws", Regex("""\bAKIA[0-9A-Z]{16}\b"""), 4),
        Rule("google", Regex("""\bAIza[0-9A-Za-z_\-]{30,}"""), 4),
        Rule("slack", Regex("""\bxox[baprs]-[A-Za-z0-9\-]{10,}"""), 4),
        Rule("bearer", Regex("""(?i)\bbearer\s+[A-Za-z0-9._\-]{16,}"""), 7),
        Rule("assign", Regex("""(?i)\b(api[_-]?key|token|secret|password|passwd)\s*[:=]\s*["']?[A-Za-z0-9._\-]{8,}["']?"""), 0),
        Rule("pem", Regex("""-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"""), 0),
        Rule("hex", Regex("""\b[0-9a-fA-F]{40,}\b"""), 0)
    )

    fun mask(text: String): String {
        var result = text
        for (rule in rules) {
            result = rule.regex.replace(result) { match ->
                val value = match.value
                if (rule.keepPrefix in 1..value.length) {
                    value.take(rule.keepPrefix) + MASK
                } else {
                    MASK
                }
            }
        }
        return result
    }

    fun containsSecret(text: String): Boolean = rules.any { it.regex.containsMatchIn(text) }
}