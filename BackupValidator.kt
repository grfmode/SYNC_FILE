package com.sync.app.domain.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Validator snapshot.
 *
 * Snapshot dinyatakan sah hanya bila lolos semua: berkas ada, `integrity_check`
 * bersih, daftar tabel sama dengan manifest, jumlah baris sama, dan hash per
 * tabel sama. Satu kegagalan saja membatalkan seluruh snapshot.
 */
object BackupValidator {

    fun validate(snapshotPath: String, manifest: BackupManifest): ValidationResult {
        val file = File(snapshotPath)
        if (!file.exists() || file.length() == 0L) return ValidationResult.fail("snapshot kosong atau tidak ada")

        val db = runCatching {
            SQLiteDatabase.openDatabase(snapshotPath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrElse { return ValidationResult.fail("snapshot tidak dapat dibuka: ${it.message}") }

        try {
            val integrity = runCatching {
                db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else "no result"
                }
            }.getOrElse { "integrity_check gagal: ${it.message}" }

            if (integrity != "ok") return ValidationResult.fail("integrity_check: $integrity")

            val actualTables = SqliteSnapshot.tableNames(db).toSet()
            val expectedTables = manifest.tables.map { it.table }.toSet()

            val missing = expectedTables - actualTables
            if (missing.isNotEmpty()) return ValidationResult.fail("tabel hilang: ${missing.joinToString(",")}")

            val extra = actualTables - expectedTables
            if (extra.isNotEmpty()) return ValidationResult.fail("tabel tak terduga: ${extra.joinToString(",")}")

            var checkedRows = 0
            for (fingerprint in manifest.tables) {
                val (rows, hash) = SqliteSnapshot.hashTable(db, fingerprint.table)
                if (rows != fingerprint.rows) {
                    return ValidationResult.fail("jumlah baris ${fingerprint.table}: $rows != ${fingerprint.rows}")
                }
                if (hash != fingerprint.hash) {
                    return ValidationResult.fail("hash ${fingerprint.table} berbeda")
                }
                checkedRows += rows
            }

            return ValidationResult.ok(manifest.tables.size, checkedRows)
        } finally {
            db.close()
        }
    }
}