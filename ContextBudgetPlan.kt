package com.sync.app.domain.context

import kotlin.math.max

data class ContextBudgetPlan(
    val contextLimit: Int,
    val outputReserve: Int,
    val safetyReserve: Int,
    val usable: Int,
    val projectPromptChars: Int,
    val memoryChars: Int,
    val knowledgeChars: Int,
    val summaryChars: Int,
    val historyWindow: Int,
    val perMessageChars: Int,
    val maxMemoryItems: Int,
    val maxKnowledgeSources: Int,
    val degraded: Boolean,
    val wholeFileChars: Int,
    val maxWholeFiles: Int
) {
    fun charsToTokens(chars: Int): Int = ceilDiv(chars, CHARS_PER_TOKEN)

    companion object {
        const val CHARS_PER_TOKEN = 3
        const val MIN_OUTPUT_RESERVE = 4_096
        const val MIN_SAFETY_RESERVE = 2_048
        const val OUTPUT_RATIO = 0.08
        const val SAFETY_RATIO = 0.05
        const val ASSUMED_SMALL_LIMIT = 64_000
        const val WHOLE_FILE_FLOOR_CHARS = 16_000
        const val CHUNK_HEADROOM_CHARS = 4_000

        fun from(contextLimit: Int?, mode: ContextMode): ContextBudgetPlan {
            val degraded = contextLimit == null || contextLimit <= 0
            val limit = if (degraded) ASSUMED_SMALL_LIMIT else contextLimit!!

            val outputReserve = max(MIN_OUTPUT_RESERVE, (limit * OUTPUT_RATIO).toInt())
            val safetyReserve = max(MIN_SAFETY_RESERVE, (limit * SAFETY_RATIO).toInt())
            val usable = (limit - outputReserve - safetyReserve).coerceAtLeast(0)
            val usableChars = usable * CHARS_PER_TOKEN

            val (memoryShare, knowledgeShare, summaryShare, historyShare) = when (mode) {
                ContextMode.COMPLETE -> Shares(0.30, 0.45, 0.15, 0.10)
                ContextMode.AUTOMATIC -> Shares(0.18, 0.22, 0.10, 0.50)
                ContextMode.ECONOMICAL -> Shares(0.22, 0.10, 0.06, 0.62)
            }.asTuple()

            val wholeFileChars = maxOf(
                (usableChars * 0.25).toInt(),
                if (usableChars >= WHOLE_FILE_FLOOR_CHARS + CHUNK_HEADROOM_CHARS) WHOLE_FILE_FLOOR_CHARS else 0
            ).coerceAtMost(maxOf(usableChars - CHUNK_HEADROOM_CHARS, 0))

            val knowledgeChars = maxOf(
                (usableChars * knowledgeShare).toInt(),
                wholeFileChars + CHUNK_HEADROOM_CHARS
            )

            return ContextBudgetPlan(
                contextLimit = limit,
                outputReserve = outputReserve,
                safetyReserve = safetyReserve,
                usable = usable,
                projectPromptChars = (usableChars * 0.08).toInt().coerceAtLeast(400),
                memoryChars = (usableChars * memoryShare).toInt().coerceAtLeast(200),
                knowledgeChars = knowledgeChars,
                summaryChars = (usableChars * summaryShare).toInt(),
                historyWindow = when (mode) {
                    ContextMode.COMPLETE -> 60
                    ContextMode.AUTOMATIC -> 24
                    ContextMode.ECONOMICAL -> 12
                },
                perMessageChars = when (mode) {
                    ContextMode.COMPLETE -> 2_400
                    ContextMode.AUTOMATIC -> 900
                    ContextMode.ECONOMICAL -> 500
                },
                maxMemoryItems = when (mode) {
                    ContextMode.COMPLETE -> 40
                    ContextMode.AUTOMATIC -> 18
                    ContextMode.ECONOMICAL -> 8
                },
                maxKnowledgeSources = when (mode) {
                    ContextMode.COMPLETE -> 14
                    ContextMode.AUTOMATIC -> 6
                    ContextMode.ECONOMICAL -> 3
                },
                degraded = degraded,
                wholeFileChars = wholeFileChars,
                maxWholeFiles = 3
            )
        }

        private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor
    }

    private data class Shares(
        val memory: Double,
        val knowledge: Double,
        val summary: Double,
        val history: Double
    ) {
        fun asTuple() = listOf(memory, knowledge, summary, history)
    }
}