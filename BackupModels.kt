package com.sync.app.domain.backup

import kotlinx.serialization.Serializable

/** Jenis cadangan. COLD dibuat saat database tidak terbuka. */
enum class BackupKind { COLD, HOT }

enum class BackupState { OK, HOT_UNAVAILABLE, FAILED }

@Serializable
data class TableFingerprint(
    val table: String,
    val rows: Int,
    val hash: String
)

@Serializable
data class BackupManifest(
    val id: String,
    val createdAt: Long,
    val kind: BackupKind,
    val schemaVersion: Int,
    val fingerprint: String,
    val tables: List<TableFingerprint>,
    val appVersion: String? = null,
    val note: String? = null
) {
    val totalRows: Int get() = tables.sumOf { it.rows }
}

data class BackupResult(
    val state: BackupState,
    val directory: String?,
    val manifest: BackupManifest?,
    val error: String?
) {
    val ok: Boolean get() = state == BackupState.OK
}

data class ValidationResult(
    val valid: Boolean,
    val reasons: List<String>,
    val checkedTables: Int,
    val checkedRows: Int
) {
    companion object {
        fun ok(tables: Int, rows: Int) = ValidationResult(true, emptyList(), tables, rows)
        fun fail(vararg reasons: String) = ValidationResult(false, reasons.toList(), 0, 0)
    }
}

/** Titik henti pemulihan; disimpan di berkas penanda, bukan di database. */
enum class RestoreStep {
    IDLE,
    SOURCE_VERIFIED,
    COLD_BACKUP_DONE,
    MOVED_ASIDE,
    INSTALLED,
    VALIDATED,
    COMMITTED,
    ROLLED_BACK,
    SAFE_MODE
}

@Serializable
data class RestoreState(
    val step: RestoreStep = RestoreStep.IDLE,
    val sourcePath: String? = null,
    val asidePath: String? = null,
    val targetPath: String? = null,
    val attempts: Int = 0,
    val updatedAt: Long = 0L,
    val message: String? = null
)