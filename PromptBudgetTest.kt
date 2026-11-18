package com.sync.app.domain.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBudgetTest {

    @Test
    fun `limit kecil memakai tier rendah`() {
        assertEquals(PromptBudget.forContextLimit(8_000), PromptBudget.forContextLimit(8_000))
    }

    @Test
    fun `limit null memakai tier menengah`() {
        val budget = PromptBudget.forContextLimit(null)
        assertTrue(budget.historyWindow == 20)
    }

    @Test
    fun `tier besar punya jendela lebih lebar`() {
        assertTrue(PromptBudget.forContextLimit(200_000).historyWindow > PromptBudget.forContextLimit(8_000).historyWindow)
    }
}