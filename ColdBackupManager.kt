package com.sync.app.domain.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.sync.app.data.local.DatabaseFiles
import com.sync.app.data.local.Migrations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cadangan saat database tertutup.
 *
 * Hanya boleh dipanggil sebelum `Room.databaseBuilder`, karena kalau database
 * sudah terbuka salinan berkas bisa setengah jadi.
 */
@Singleton
class ColdBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directory: BackupDirectory
) {

    private companion object {
        const val TAG = "SYNC_BACKUP"
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }

    suspend fun create(note: String?): BackupResult = withContext(Dispatchers.IO) {
        val mainPath = DatabaseFiles.path(context, DatabaseFiles.MAIN)
        if (!File(mainPath).exists()) {
            return@withContext BackupResult(BackupState.FAILED, null, null, "database belum ada")
        }

        val id = directory.newId(BackupKind.COLD)
        val snapshot = directory.snapshotFile(id)

        try {
            SqliteSnapshot.copy(mainPath, snapshot.absolutePath).getOrThrow()
            val manifest = buildManifest(id, snapshot.absolutePath, note)
            directory.manifestFile(id).writeText(json.encodeToString(BackupManifest.serializer(), manifest))

            val validation = BackupValidator.validate(snapshot.absolutePath, manifest)
            if (!validation.valid) {
                directory.dirFor(id).deleteRecursively()
                return@withContext BackupResult(
                    BackupState.FAILED, null, null, validation.reasons.joinToString("; ")
                )
            }

            directory.prune()
            BackupResult(BackupState.OK, directory.dirFor(id).absolutePath, manifest, null)
        } catch (e: Exception) {
            Log.e(TAG, "Cold backup gagal", e)
            directory.dirFor(id).deleteRecursively()
            BackupResult(BackupState.FAILED, null, null, e.message ?: "kesalahan tak dikenal")
        }
    }

    fun buildManifest(id: String, snapshotPath: String, note: String?): BackupManifest {
        val db = SQLiteDatabase.openDatabase(snapshotPath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val tables = SqliteSnapshot.tableNames(db).map { table ->
                val (rows, hash) = SqliteSnapshot.hashTable(db, table)
                TableFingerprint(table, rows, hash)
            }
            return BackupManifest(
                id = id,
                createdAt = System.currentTimeMillis(),
                kind = BackupKind.COLD,
                schemaVersion = Migrations.CURRENT_VERSION,
                fingerprint = "cold:${tables.size}",
                tables = tables,
                appVersion = null,
                note = note
            )
        } finally {
            db.close()
        }
    }
}