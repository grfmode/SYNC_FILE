package com.sync.app.domain.backup

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Tata letak folder cadangan.
 *
 * `backups/<id>/snapshot.db` dan `manifest.json`, dengan `<id>` berurutan
 * waktu sehingga pembersihan otomatis bisa membuang yang tertua lebih dulu.
 */
class BackupDirectory(private val context: Context) {

    companion object {
        const val KEEP_COLD = 5
        const val KEEP_HOT = 3
        const val RESTORE_DIR = ".restore"
    }

    fun root(): File = File(context.filesDir, "backups").apply { mkdirs() }

    fun restoreRoot(): File = File(root(), RESTORE_DIR).apply { mkdirs() }

    fun newId(kind: BackupKind): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "${kind.name.lowercase()}-$stamp"
    }

    fun dirFor(id: String): File = File(root(), id).apply { mkdirs() }

    fun snapshotFile(id: String): File = File(dirFor(id), "snapshot.db")

    fun manifestFile(id: String): File = File(dirFor(id), "manifest.json")

    fun stateFile(): File = File(restoreRoot(), "state.json")

    /** Buang cadangan lama; hanya menyisakan [KEEP_COLD] dan [KEEP_HOT] terbaru. */
    fun prune() {
        val cold = listAll(BackupKind.COLD)
        val hot = listAll(BackupKind.HOT)
        cold.drop(KEEP_COLD).forEach { it.deleteRecursively() }
        hot.drop(KEEP_HOT).forEach { it.deleteRecursively() }
    }

    private fun listAll(kind: BackupKind): List<File> =
        root().listFiles()
            ?.filter { it.isDirectory && it.name.startsWith(kind.name.lowercase()) }
            ?.sortedByDescending { it.name }
            .orEmpty()
}