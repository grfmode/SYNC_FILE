package com.sync.app.domain.memory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecretFilterTest {

    @Test
    fun `token openai disamarkan`() {
        val masked = SecretFilter.mask("key sk-abcdefghijklmnopqrstuvwx")
        assertFalse(masked.contains("abcdefghijklmnopqrstuvwx"))
    }

    @Test
    fun `teks biasa tidak berubah`() {
        assertEquals2("pakai tab", SecretFilter.mask("pakai tab"))
    }

    @Test
    fun `deteksi rahasia`() {
        assertTrue(SecretFilter.containsSecret("ghp_1234567890abcdefghij"))
    }

    private fun assertEquals2(expected: String, actual: String) = org.junit.Assert.assertEquals(expected, actual)
}