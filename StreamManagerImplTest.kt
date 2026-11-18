package com.sync.app.domain.chat

import com.sync.app.domain.error.AiException
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamManagerImplTest {

    @Test
    fun `hasil sukses membawa konten`() {
        val result = StreamResult.Success("halo")
        assertEquals("halo", result.content)
    }

    @Test
    fun `hasil gagal menyimpan teks parsial`() {
        val result = StreamResult.Failed("sebagian", AiException.Unknown(RuntimeException("putus")))
        assertEquals("sebagian", result.partialContent)
    }
}