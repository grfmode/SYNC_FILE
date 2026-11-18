package com.sync.app.domain.chat

import com.sync.app.domain.provider.ChatRequest
import com.sync.app.model.ChatMessage
import com.sync.app.model.MessageRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetGuardTest {

    private fun request(chars: Int) = ChatRequest(
        modelId = "m",
        messages = listOf(ChatMessage(1, "x".repeat(chars), MessageRole.User))
    )

    @Test
    fun `permintaan kecil muat`() {
        assertTrue(ContextBudgetGuard.estimate(request(100), 100_000).fits)
    }

    @Test
    fun `permintaan besar tidak muat`() {
        assertFalse(ContextBudgetGuard.estimate(request(2_000_000), 16_000).fits)
    }

    @Test
    fun `anggaran memakai plan yang sama`() {
        val estimate = ContextBudgetGuard.estimate(request(10), 50_000)
        assertTrue(estimate.maxInputTokens > 0)
        assertTrue(estimate.maxInputTokens < 50_000)
    }
}