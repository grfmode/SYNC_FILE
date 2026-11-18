package com.sync.app.domain.memory

/**
 * Ekstraksi memori heuristik, tanpa LLM.
 *
 * Heuristik dipilih karena ekstraksi berjalan di jalur jawaban: panggilan model
 * tambahan akan memperlambat setiap balasan dan menambah biaya.
 *
 * Aturan sengaja konservatif. Sinyal lemah masuk dengan confidence rendah
 * sehingga tetap bisa ditinjau, bukan dipakai seolah-olah fakta pasti.
 */
object MemoryExtractor {

    private const val MAX_ITEMS_PER_MESSAGE = 3
    private const val MIN_LENGTH = 12
    private const val MAX_LENGTH = 280
    private const val TENTATIVE_CONFIDENCE = 0.55f
    private const val STRONG_CONFIDENCE = 0.8f

    private val instructionPatterns = listOf(
        Regex("""(?i)\b(?:selalu|always)\s+(.{6,120})"""),
        Regex("""(?i)\b(?:jangan|never|do not|don't)\s+(?:pernah\s+)?(.{6,120})"""),
        Regex("""(?i)\b(?:gunakan|pakai|use)\s+(.{4,120}?)(?:\s+(?:untuk|for|saat|when)\b.*)?"""),
        Regex("""(?i)\b(?:harus|must|wajib)\s+(.{4,120})""")
    )

    private val constraintPatterns = listOf(
        Regex("""(?i)\b(?:maksimal|minimal|paling banyak|paling sedikit|at most|at least|max|min)\s+.{1,60}"""),
        Regex("""(?i)\b(?:batas|limit)\s+.{1,60}"""),
        Regex("""(?i)\b(?:tidak boleh|must not|not allowed)\s+.{4,120}""")
    )

    private val preferencePatterns = listOf(
        Regex("""(?i)\b(?:saya|aku)\s+(?:lebih\s+suka|suka|prefer|mau|ingin)\s+(.{4,120})"""),
        Regex("""(?i)\b(?:i|we)\s+(?:prefer|like|want)\s+(.{4,120})"""),
        Regex("""(?i)\b(?:lebih\s+baik|better to|sebaiknya)\s+(.{4,120})""")
    )

    private val decisionPatterns = listOf(
        Regex("""(?i)\b(?:kita|kami|we)\s+(?:pakai|gunakan|pilih|putuskan|decided|chose)\s+(.{4,120})"""),
        Regex("""(?i)\b(?:diputuskan|decided)\s+(?:untuk\s+)?(.{4,120})""")
    )

    private val factPatterns = listOf(
        Regex("""(?i)\b(?:versi|version|stack|framework|library|database|db)\s*(?:adalah|is|:|=)?\s*([\w.\-+/]{2,40})"""),
        Regex("""(?i)\bpath\s*(?:adalah|is|:|=)\s*([\w./\-]{4,120})""")
    )

    /** Pertanyaan biasanya bukan memori. */
    private val questionHint = Regex("""(?i)^\s*(?:apa|apakah|bagaimana|kenapa|mengapa|kapan|siapa|what|how|why|when|who)\b""")

    fun extract(
        text: String,
        scope: MemoryScope,
        projectId: Long?,
        source: MemorySource,
        provenance: String? = null
    ): List<MemoryCandidate> {
        if (text.isBlank()) return emptyList()

        val sentences = text.split(Regex("(?<=[.!?])\\s+|\\n+"))
            .map { it.trim().trim('-', '*', '•', ' ') }
            .filter { it.length in MIN_LENGTH..MAX_LENGTH }

        val results = mutableListOf<MemoryCandidate>()
        val seen = mutableSetOf<String>()

        for (sentence in sentences) {
            if (results.size >= MAX_ITEMS_PER_MESSAGE) break
            if (questionHint.containsMatchIn(sentence) && sentence.endsWith("?")) continue

            val typed = classify(sentence) ?: continue
            val key = MemoryNormalizer.dedupeKey(typed.second)
            if (!seen.add(key)) continue
            if (typed.second.length < MIN_LENGTH) continue

            results += MemoryCandidate(
                content = typed.second,
                type = typed.first,
                scope = scope,
                projectId = if (scope == MemoryScope.GLOBAL) null else projectId,
                source = source,
                confidence = if (typed.first == MemoryType.FACT) TENTATIVE_CONFIDENCE else STRONG_CONFIDENCE,
                provenance = provenance
            )
        }
        return results
    }

    private fun classify(sentence: String): Pair<MemoryType, String>? {
        if (constraintPatterns.any { it.containsMatchIn(sentence) }) return MemoryType.CONSTRAINT to sentence
        if (instructionPatterns.any { it.containsMatchIn(sentence) }) return MemoryType.INSTRUCTION to sentence
        if (preferencePatterns.any { it.containsMatchIn(sentence) }) return MemoryType.PREFERENCE to sentence
        if (decisionPatterns.any { it.containsMatchIn(sentence) }) return MemoryType.DECISION to sentence
        if (factPatterns.any { it.containsMatchIn(sentence) }) return MemoryType.FACT to sentence
        return null
    }
}