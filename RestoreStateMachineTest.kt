package com.sync.app.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreStateMachineTest {

    @Test
    fun `state awal idle`() {
        assertEquals(RestoreStep.IDLE, RestoreState().step)
    }

    @Test
    fun `state menyimpan langkah dan percobaan`() {
        val state = RestoreState(step = RestoreStep.INSTALLED, attempts = 2)
        assertEquals(RestoreStep.INSTALLED, state.step)
        assertEquals(2, state.attempts)
    }

    @Test
    fun `safe mode adalah langkah akhir`() {
        assertEquals("SAFE_MODE", RestoreStep.SAFE_MODE.name)
    }
}