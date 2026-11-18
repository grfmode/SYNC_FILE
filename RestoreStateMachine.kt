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
 * Pemulihan database dengan penanda langkah.
 *
 * Setiap perpindahan langkah ditulis ke disk lebih dulu. Kalau proses mati di
 * tengah jalan, [RestoreRecovery] membaca penanda dan memilih arah: maju bila
 * salinan sudah terpasang dan lolos validasi, mundur bila belum.
 *
 * Batas dua percobaan; setelah itu database dianggap tidak aman dan aplikasi
 * masuk SAFE_MODE (baca saja) sampai pengguna memulihkan manual.
 */
@Singleton
class RestoreStateMachine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directory: BackupDirectory,
    private val cold: ColdBackupManager
) {

    private companion object {
        const val TAG = "SYNC_RESTORE"
        const val MAX_ATTEMPTS = 2
        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }

    private fun targetPath(): String = DatabaseFiles.path(context, DatabaseFiles.MAIN)

    fun readState(): RestoreState {
        val file = directory.stateFile()
        if (!file.exists()) return RestoreState()
        return runCatching {
            json.decodeFromString(RestoreState.serializer(), file.readText())
        }.getOrDefault(RestoreState())
    }

    private fun writeState(state: RestoreState) {
        runCatching {
            directory.stateFile()
                .writeText(json.encodeToString(RestoreState.serializer(), state.copy(updatedAt = System.currentTimeMillis())))
        }
    }

    /**
     * Jalankan pemulihan penuh dari snapshot yang sudah divalidasi.
     * Database utama harus tertutup.
     */
    suspend fun restore(snapshotPath: String): RestoreState = withContext(Dispatchers.IO) {
        var state = RestoreState(step = RestoreStep.SOURCE_VERIFIED, sourcePath = snapshotPath, attempts = 1)
        writeState(state)

        try {
            // 1. cadangan dingin dulu
            val safety = cold.create("sebelum restore")
            if (!safety.ok) {
                state = state.copy(step = RestoreStep.ROLLED_BACK, message = "cadangan pengaman gagal: ${safety.error}")
                writeState(state)
                return@withContext state
            }
            state = state.copy(step = RestoreStep.COLD_BACKUP_DONE)
            writeState(state)

            // 2. sisihkan database lama
            val target = File(targetPath())
            val aside = File("${target.absolutePath}.bak-${System.currentTimeMillis()}")
            if (target.exists() && !target.renameTo(aside)) {
                state = state.copy(step = RestoreStep.ROLLED_BACK, message = "gagal menyisihkan database lama")
                writeState(state)
                return@withContext state
            }
            state = state.copy(step = RestoreStep.MOVED_ASIDE, asidePath = aside.absolutePath, targetPath = target.absolutePath)
            writeState(state)

            // 3. pasang salinan
            File(snapshotPath).copyTo(target, overwrite = true)
            state = state.copy(step = RestoreStep.INSTALLED)
            writeState(state)

            // 4. validasi hasil pasang
            val manifest = readManifestFor(snapshotPath)
            val validation = if (manifest != null) {
                BackupValidator.validate(target.absolutePath, manifest)
            } else {
                ValidationResult.fail("manifest tidak ditemukan")
            }

            if (!validation.valid) {
                rollback(state)
                return@withContext readState()
            }

            state = state.copy(step = RestoreStep.VALIDATED)
            writeState(state)

            // 5. commit
            state = state.copy(step = RestoreStep.COMMITTED, message = null)
            writeState(state)
            state
        } catch (e: Exception) {
            Log.e(TAG, "Restore gagal", e)
            rollback(state)
            readState()
        }
    }

    private fun rollback(state: RestoreState) {
        val aside = state.asidePath?.let { File(it) }
        val target = File(targetPath())
        if (aside != null && aside.exists()) {
            if (target.exists()) target.delete()
            aside.renameTo(target)
        }
        writeState(state.copy(step = RestoreStep.ROLLED_BACK, message = "dibatalkan otomatis"))
    }

    /** Dipanggil saat aplikasi mulai; memilih maju atau mundur. */
    suspend fun recover(): RestoreState = withContext(Dispatchers.IO) {
        var state = readState()
        if (state.step == RestoreStep.IDLE || state.step == RestoreStep.COMMITTED ||
            state.step == RestoreStep.ROLLED_BACK || state.step == RestoreStep.SAFE_MODE
        ) {
            return@withContext state
        }

        if (state.attempts >= MAX_ATTEMPTS) {
            state = state.copy(step = RestoreStep.SAFE_MODE, message = "percobaan pemulihan habis")
            writeState(state)
            return@withContext state
        }

        state = state.copy(attempts = state.attempts + 1)
        writeState(state)

        when (state.step) {
            RestoreStep.INSTALLED, RestoreStep.VALIDATED -> {
                val manifest = state.sourcePath?.let { readManifestFor(it) }
                val validation = manifest?.let { BackupValidator.validate(targetPath(), it) }
                if (validation?.valid == true) {
                    state = state.copy(step = RestoreStep.COMMITTED, message = null)
                    writeState(state)
                    state
                } else {
                    rollback(state)
                    readState()
                }
            }
            else -> {
                rollback(state)
                readState()
            }
        }
    }

    private fun readManifestFor(snapshotPath: String): BackupManifest? {
        val dir = File(snapshotPath).parentFile ?: return null
        val manifest = File(dir, "manifest.json")
        if (!manifest.exists()) return null
        return runCatching {
            json.decodeFromString(BackupManifest.serializer(), manifest.readText())
        }.getOrNull()
    }

    fun clearState() {
        runCatching { directory.stateFile().delete() }
    }
}