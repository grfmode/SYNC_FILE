package com.sync.app.domain.lab

import android.content.Context
import androidx.room.Room
import com.sync.app.data.local.DatabaseFiles
import com.sync.app.data.local.Migrations
import com.sync.app.data.local.SyncDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database uji yang terpisah dari database utama.
 *
 * Kebijakan path bersifat gagal-tertutup: bila path utama dan path uji tidak
 * dapat dibedakan, Lab tidak dibuka sama sekali, sehingga tidak ada uji yang
 * diam-diam menulis ke database asli.
 */
@Singleton
class LabDatabase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    sealed class OpenResult {
        data class Success(val database: SyncDatabase, val path: String) : OpenResult()
        data class Failure(val reason: String) : OpenResult()
    }

    private var instance: SyncDatabase? = null

    fun path(): String = DatabaseFiles.path(context, DatabaseFiles.LAB)

    fun mainPath(): String = DatabaseFiles.path(context, DatabaseFiles.MAIN)

    fun isIsolated(): Boolean {
        val lab = runCatching { File(path()).canonicalPath }.getOrNull() ?: return false
        val main = runCatching { File(mainPath()).canonicalPath }.getOrNull() ?: return false
        return lab != main
    }

    fun open(): OpenResult {
        if (!isIsolated()) {
            return OpenResult.Failure("path Lab sama dengan database utama")
        }
        instance?.let { return OpenResult.Success(it, path()) }

        return runCatching {
            val db = Room.databaseBuilder(context, SyncDatabase::class.java, DatabaseFiles.LAB)
                .addMigrations(
                    Migrations.MIGRATION_1_2,
                    Migrations.MIGRATION_2_3,
                    Migrations.MIGRATION_3_4,
                    Migrations.MIGRATION_4_5,
                    Migrations.MIGRATION_5_6,
                    Migrations.MIGRATION_6_7,
                    Migrations.MIGRATION_7_8,
                    Migrations.MIGRATION_8_9,
                    Migrations.MIGRATION_9_10
                )
                .build()
            instance = db
            OpenResult.Success(db, path())
        }.getOrElse { OpenResult.Failure(it.message ?: "gagal membuka database Lab") }
    }

    /** Isi Lab dari salinan database utama, tanpa menyentuh yang asli. */
    fun seedFromMain(): Result<Unit> = runCatching {
        // Lab boleh belum pernah dibuka; yang sudah terbuka ditutup dulu.
        instance?.close()
        instance = null

        val main = File(mainPath())
        if (!main.exists()) error("database utama belum ada")

        val lab = File(path())
        if (lab.exists()) lab.delete()
        // Sisa WAL/SHM lama tidak boleh bercampur dengan salinan baru.
        File("${path()}-wal").delete()
        File("${path()}-shm").delete()

        SqliteFileCopy.copy(main, lab)
        open().let { if (it is OpenResult.Failure) error(it.reason) }
    }

    fun close() {
        instance?.close()
        instance = null
    }

    fun reset() {
        close()
        runCatching { File(path()).delete() }
        runCatching { File("${path()}-wal").delete() }
        runCatching { File("${path()}-shm").delete() }
    }

    fun exists(): Boolean = File(path()).exists()
}

/** Salin berkas apa adanya, termasuk berkas pendamping WAL. */
internal object SqliteFileCopy {

    fun copy(source: File, target: File) {
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)

        val wal = File("${source.absolutePath}-wal")
        if (wal.exists()) wal.copyTo(File("${target.absolutePath}-wal"), overwrite = true)

        val shm = File("${source.absolutePath}-shm")
        if (shm.exists()) shm.copyTo(File("${target.absolutePath}-shm"), overwrite = true)
    }
}