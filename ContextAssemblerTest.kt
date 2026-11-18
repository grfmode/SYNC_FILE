package com.sync.app.domain.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextAssemblerTest {

    @Test
    fun `seksi wajib tidak pernah dipotong`() {
        val plan = ContextBudgetPlan.from(8_000, ContextMode.ECONOMICAL)
        assertTrue(plan.usable > 0)
        assertTrue(ContextSectionId.BASE.neverTrimmed)
        assertTrue(ContextSectionId.LAYER_RULES.neverTrimmed)
        assertFalse(ContextSectionId.KNOWLEDGE.neverTrimmed)
    }

    @Test
    fun `mode hemat memberi knowledge lebih kecil dari mode lengkap`() {
        val economical = ContextBudgetPlan.from(32_000, ContextMode.ECONOMICAL)
        val complete = ContextBudgetPlan.from(32_000, ContextMode.COMPLETE)
        assertTrue(economical.knowledgeChars < complete.knowledgeChars)
    }

    @Test
    fun `limit null tidak crash dan ditandai degraded`() {
        val plan = ContextBudgetPlan.from(null, ContextMode.AUTOMATIC)
        assertTrue(plan.degraded)
        assertEquals(ContextBudgetPlan.ASSUMED_SMALL_LIMIT, plan.contextLimit)
    }
}