package com.sync.app.domain.chat

import com.sync.app.domain.context.ContextBudgetPlan

/**
 * Ambang ringkasan dan plafon baris memori.
 *
 * Jendela riwayat dan batas karakter per pesan kini berasal dari
 * [ContextBudgetPlan]; kelas ini menyimpan ambang ringkasan yang tidak
 * bergantung pada mode konteks.
 */
data class PromptBudget(
    val historyWindow: Int,
    val summaryThreshold: Int,
    val summaryRefreshK: Int,
    val maxCharsPerMessage: Int,
    val maxMemory: Int,
    val maxActiveMemoryLines: Int,
    val maxTotalMemoryLines: Int,
    val maxProject: Int,
    val maxSummary: Int
) {
    companion object {
        private val LOW = PromptBudget(10, 12, 3, 400, 1600, 7, 10, 800, 1500)
        private val MID = PromptBudget(20, 22, 4, 800, 3600, 16, 24, 1200, 4000)
        private val HIGH = PromptBudget(40, 42, 4, 2000, 5500, 24, 36, 2000, 5000)

        fun forContextLimit(contextLimit: Int?): PromptBudget = when {
            contextLimit == null || contextLimit <= 0 -> MID
            contextLimit < 16_000 -> LOW
            contextLimit < 64_000 -> MID
            else -> HIGH
        }

        /** Turunkan dari anggaran agar satu sumber angka saja yang berlaku. */
        fun fromPlan(plan: ContextBudgetPlan): PromptBudget {
            val base = forContextLimit(plan.contextLimit)
            return base.copy(
                historyWindow = plan.historyWindow,
                maxCharsPerMessage = plan.perMessageChars,
                maxMemory = plan.memoryChars,
                maxProject = plan.projectPromptChars,
                maxSummary = plan.summaryChars
            )
        }
    }
}

fun clampTemperature(value: Float, min: Float, max: Float): Float = value.coerceIn(min, max)