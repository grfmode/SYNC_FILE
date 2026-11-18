package com.sync.app.domain.backup

import android.content.Context
import android.util.Log
import com.sync.app.data.local.DatabaseFiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cadangan saat aplikasi berjalan.
 *
 * Kalau snapshot gagal divalidasi, statusnya menjadi [BackupState.HOT_UNAVAILABLE]
 * dan hasilnya tidak ditawarkan sebagai sumber pemulihan. Jalur pemulihan tetap
 * tersedia lewat cadangan dingin.
 */
@Singleton
class HotBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directory: BackupDirectory,
    private val cold: ColdBackupManager
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

        val id = directory.newId(BackupKind.HOT)
        val snapshot = directory.snapshotFile(id)

        try {
            SqliteSnapshot.copy(mainPath, snapshot.absolutePath).getOrThrow()
            val manifest = cold.buildManifest(id, snapshot.absolutePath, note)
                .copy(kind = BackupKind.HOT)
            directory.manifestFile(id).writeText(json.encodeToString(BackupManifest.serializer(), manifest))

            val validation = BackupValidator.validate(snapshot.absolutePath, manifest)
            if (!validation.valid) {
                directory.dirFor(id).deleteRecursively()
                Log.w(TAG, "Hot backup tidak valid: ${validation.reasons.joinToString("; ")}")
                return@withContext BackupResult(
                    BackupState.HOT_UNAVAILABLE, null, null, validation.reasons.joinToString("; ")
                )
            }

            directory.prune()
            BackupResult(BackupState.OK, directory.dirFor(id).absolutePath, manifest, null)
        } catch (e: Exception) {
            Log.e(TAG, "Hot backup gagal", e)
            directory.dirFor(id).deleteRecursively()
            BackupResult(BackupState.HOT_UNAVAILABLE, null, null, e.message ?: "kesalahan tak dikenal")
        }
    }
}