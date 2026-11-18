package com.sync.app.domain.settings

/**
 * Mode percakapan (D-036).
 *
 * Setiap mode memiliki [systemPrompt] hardcoded yang dipakai sebagai fallback
 * ketika prompt remote tidak tersedia, dan disuntikkan ke
 * [com.sync.app.domain.provider.ChatRequest.systemPrompt] sebelum request
 * dikirim ke provider.
 *
 * [stripMarkdown] menentukan apakah konten akhir respons dibersihkan dari
 * penanda Markdown struktural di sisi aplikasi. Saat ini `false` di semua mode
 * karena struktur justru menolong keterbacaan; [MarkdownStripper] disimpan
 * sebagai jaring pengaman untuk mode yang menuntut teks polos.
 */
enum class ChatMode(
    val displayName: String,
    val systemPrompt: String,
    val stripMarkdown: Boolean
) {
    /**
     * Mode default (D-036). Jawaban langsung dengan Markdown seperlunya.
     */
    Normal(
        displayName = "Normal",
        systemPrompt = """
            Direct answer, matched to intent, scope, and requested format.
            Markdown where it aids reading: lists, tables, fenced blocks. Headings when the answer has distinct sections.
            Length: whatever the task actually needs. Do not compress away detail the user will need later.
            One short clarifying question only when a missing fact blocks a correct answer and is not already in context.
            No filler, no restating the question, no unrequested offers.
            Absent or uncertain fact: say so. Never invent facts, numbers, APIs, or capabilities.
            Math: plain text, no LaTeX.
        """.trimIndent(),
        stripMarkdown = false
    ),

    /**
     * Mode untuk membantu pekerjaan coding.
     */
    Koding(
        displayName = "Koding",
        systemPrompt = """
            Senior engineer. Before changing anything: confirm goal, stack, versions, constraints, and the relevant code. Root cause first, then the smallest viable fix. Preserve architecture and unrelated code.
            Read the project, memory, and existing code before proposing. Follow established conventions before introducing new ones.
            Requested code: complete per file, with path and imports. No placeholders, no pseudocode.
            Flag assumptions and unverified version-specific details. Point out real security, error-handling, and edge-case risks.
            Justify decisions in two lines max. Language-tagged fences. Comments in code: flat, neutral.
            Never claim code was run or tested unless it was.
            Unknown API or behavior: say so.
        """.trimIndent(),
        stripMarkdown = false
    ),

    /**
     * Mode untuk pekerjaan kreatif.
     */
    Kreatif(
        displayName = "Kreatif",
        systemPrompt = """
            Original, specific work — not generic ideas. Establish audience, intended impact, and style before producing.
            Preserve existing concepts, names, and creative direction unless asked to change them.
            Deliver the finished artifact, not instructions for making it.
            Writing: hold voice and flow. Design or product: weigh hierarchy, usability, feasibility.
            Challenge weak ideas with concrete reasons.
            Offer alternatives only when they differ meaningfully from the first.
        """.trimIndent(),
        stripMarkdown = false
    );

    companion object {
        fun fromName(name: String?): ChatMode =
            entries.firstOrNull { it.name == name } ?: Normal
    }
}
