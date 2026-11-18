package com.sync.app.domain.chat

import com.sync.app.domain.context.ContextBudgetPlan
import com.sync.app.domain.context.ContextMode
import com.sync.app.domain.provider.ChatRequest
import com.sync.app.model.AttachmentType
import kotlin.math.ceil

/**
 * Pemeriksaan akhir sebelum request dikirim.
 *
 * Anggaran diambil dari [ContextBudgetPlan] sehingga angka yang dipakai sama
 * dengan angka yang dipakai assembler. Batas keras tetap ada di sini sebagai
 * jaring terakhir bila assembler melewati anggaran.
 */
object ContextBudgetGuard {

    data class Estimate(
        val contextLimit: Int,
        val estimatedInputTokens: Int,
        val outputReserveTokens: Int,
        val safetyReserveTokens: Int,
        val maxInputTokens: Int
    ) {
        val fits: Boolean get() = estimatedInputTokens <= maxInputTokens
        val usagePercent: Int get() =
            if (maxInputTokens <= 0) 100
            else ((estimatedInputTokens * 100.0) / maxInputTokens).toInt().coerceAtMost(999)
    }

    fun estimate(
        request: ChatRequest,
        contextLimit: Int,
        mode: ContextMode = ContextMode.AUTOMATIC
    ): Estimate {
        require(contextLimit > 0) { "contextLimit must be positive" }

        val textChars = request.systemPrompt.orEmpty().length +
            request.messages.sumOf { it.role.name.length + it.content.length } +
            request.attachments.sumOf { it.textContent?.length ?: 0 }

        val textTokens = ceil(textChars / CHARS_PER_TOKEN).toInt()
        val overhead = request.messages.size * TOKENS_PER_MESSAGE
        val attachmentTokens = request.attachments.sumOf { attachment ->
            when {
                attachment.textContent != null -> 0
                attachment.type == AttachmentType.Image -> IMAGE_TOKEN_ESTIMATE
                else -> DOCUMENT_TOKEN_ESTIMATE
            }
        }

        val plan = ContextBudgetPlan.from(contextLimit, mode)
        return Estimate(
            contextLimit = contextLimit,
            estimatedInputTokens = textTokens + overhead + attachmentTokens + REQUEST_OVERHEAD,
            outputReserveTokens = plan.outputReserve,
            safetyReserveTokens = plan.safetyReserve,
            maxInputTokens = plan.usable
        )
    }

    private const val CHARS_PER_TOKEN = 3.0
    private const val TOKENS_PER_MESSAGE = 8
    private const val REQUEST_OVERHEAD = 128
    private const val IMAGE_TOKEN_ESTIMATE = 8_192
    private const val DOCUMENT_TOKEN_ESTIMATE = 4_096
}