package com.sync.app.domain.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetPlanTest {

    @Test
    fun `cadangan keluaran dan keamanan mengurangi usable`() {
        val plan = ContextBudgetPlan.from(100_000, ContextMode.AUTOMATIC)
        assertEquals(100_000 - plan.outputReserve - plan.safetyReserve, plan.usable)
    }

    @Test
    fun `limit kecil tetap punya usable positif`() {
        val plan = ContextBudgetPlan.from(8_000, ContextMode.AUTOMATIC)
        assertTrue(plan.usable > 0)
    }

    @Test
    fun `pembagian tidak melebihi usable`() {
        val plan = ContextBudgetPlan.from(50_000, ContextMode.COMPLETE)
        val total = plan.projectPromptChars + plan.memoryChars + plan.knowledgeChars + plan.summaryChars
        assertTrue(total <= plan.usable * ContextBudgetPlan.CHARS_PER_TOKEN)
    }
}