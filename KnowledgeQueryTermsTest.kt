package com.sync.app.domain.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeQueryTermsTest {

    @Test
    fun `pesan umum tidak punya istilah cari`() {
        assertTrue(KnowledgeQueryTerms.meaningful("Tolong periksa file ini").isEmpty())
    }

    @Test
    fun `nama berkas dan simbol dipertahankan`() {
        assertEquals(
            listOf("knowledgestoreimpl", "error"),
            KnowledgeQueryTerms.meaningful("periksa KnowledgeStoreImpl error")
        )
    }
}
