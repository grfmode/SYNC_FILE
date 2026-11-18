package com.sync.app.domain.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {

    @Test
    fun `snapshot tidak ada ditolak`() {
        val result = BackupValidator.validate("/tidak/ada.db", BackupManifest("x", 0, BackupKind.COLD, 10, "f", emptyList(), null, null))
        assertFalse(result.valid)
    }

    @Test
    fun `manifest kosong tanpa berkas tetap gagal`() {
        val result = BackupValidator.validate("", BackupManifest("x", 0, BackupKind.HOT, 10, "f", emptyList(), null, null))
        assertTrue(result.reasons.isNotEmpty())
    }

    @Test
    fun `hasil gagal selalu membawa alasan`() {
        val result = ValidationResult.fail("hash beda")
        assertFalse(result.valid)
        assertTrue(result.reasons.contains("hash beda"))
    }
}