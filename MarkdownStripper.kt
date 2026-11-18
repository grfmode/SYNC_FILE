package com.sync.app.domain.settings

/**
 * Pembersih penanda Markdown struktural untuk respons AI.
 *
 * Dipakai sebagai jaring pengaman ketika model AI mengabaikan instruksi
 * prompt anti-Markdown struktural (mis. mode [ChatMode.Santai]). 
 * 
 * Sejak implementasi Custom Markdown Renderer, fungsi ini TIDAK LAGI
 * menghapus Bold (**), Italic (*), atau Inline Code (`).
 *
 * Yang dibersihkan:
 * - Heading (#, ##, dst. di awal baris)
 * - Blockquote (>)
 * - List (- , * , atau 1. di awal baris)
 * - Link [text](url) → text
 * - Image ![alt](url) → alt
 *
 * Yang **tidak** dibersihkan (sengaja dibiarkan agar dirender):
 * - Fenced code block (```kotlin, ```text, dll) -> Agar tetap muncul sebagai kotak kode
 * - Bold (**...**, __...__)
 * - Italic (*...*, _..._)
 * - Inline code (`...`)
 */
object MarkdownStripper {

    fun strip(text: String): String {
        if (text.isBlank()) return text

        var result = text

        // 1. Heading di awal baris: #, ##, ###, ... → hapus marker + spasi
        result = result.replace(
            Regex("""^#{1,6}\s+""", RegexOption.MULTILINE),
            ""
        )

        // 2. Blockquote di awal baris: > text → text
        result = result.replace(
            Regex("""^>\s*""", RegexOption.MULTILINE),
            ""
        )

        // 3. List di awal baris: - , * , atau 1. → hapus marker
        result = result.replace(
            Regex("""^[-*]\s+""", RegexOption.MULTILINE),
            ""
        )
        result = result.replace(
            Regex("""^\d+\.\s+""", RegexOption.MULTILINE),
            ""
        )

        // 4. Image: ![alt](url) → alt
        result = result.replace(Regex("""!\[([^\]]*)\]\([^)]+\)"""), "$1")

        // 5. Link: [text](url) → text
        result = result.replace(Regex("""\[([^\]]+)\]\([^)]+\)"""), "$1")

        return result
    }
}