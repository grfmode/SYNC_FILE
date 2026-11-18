package com.sync.app.domain.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.sync.app.data.local.DatabaseFiles
import com.sync.app.data.local.Migrations
import java.io.File

/**
 * Cadangan wajib sebelum database dinaikkan versinya.
 *
 * Dipanggil sebelum `Room.databaseBuilder`; pada titik itu database masih
 * tertutup sehingga salinan berkas konsisten.
 */
object PreMigrationBackupGuard {

    private const val TAG = "SYNC_BACKUP"

    /** Versi skema yang tersimpan; 0 bila database belum ada. */
    fun currentVersion(context: Context): Int {
        val path = DatabaseFiles.path(context, DatabaseFiles.MAIN)
        if (!File(path).exists()) return 0
        return runCatching {
            SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA user_version", null).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            }
        }.getOrElse {
            Log.w(TAG, "Tidak dapat membaca user_version: ${it.message}")
            0
        }
    }

    fun needsBackup(context: Context): Boolean {
        val version = currentVersion(context)
        return version in 1 until Migrations.CURRENT_VERSION
    }

    /**
     * Blokir, bukan fire-and-forget: migrasi tidak dijalankan sebelum salinan
     * pengaman benar-benar sah.
     */
    suspend fun runBlockingBackup(context: Context, manager: ColdBackupManager): Boolean {
        if (!needsBackup(context)) return true
        val result = manager.create("sebelum migrasi v${currentVersion(context)}→${Migrations.CURRENT_VERSION}")
        if (!result.ok) Log.e(TAG, "Cadangan pra-migrasi gagal: ${result.error}")
        return result.ok
    }
}