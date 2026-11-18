package com.sync.app.domain.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupSelfTest @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Case(val name: String, val passed: Boolean, val detail: String?, val elapsedMs: Long)
    data class Report(val passed: Boolean, val cases: List<Case>) {
        val failed: Int get() = cases.count { !it.passed }
        fun summary(): String = cases.joinToString("\n") {
            "${if (it.passed) "LULUS" else "GAGAL"} ${it.name} (${it.elapsedMs} ms)" +
                (it.detail?.let { d -> ": $d" } ?: "")
        }
    }

    suspend fun run(): Report = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "backup-selftest-${System.currentTimeMillis()}").apply { mkdirs() }
        val cases = mutableListOf<Case>()
        try {
            cases += caseWal(dir)
            cases += caseForeignKeys(dir)
            cases += caseTrigger(dir)
            cases += caseBlob(dir)
            cases += caseFts(dir)
            cases += caseIntegrity(dir)
            cases += caseTruncated(dir)
        } finally {
            dir.deleteRecursively()
        }
        val report = Report(cases.all { it.passed }, cases)
        Log.i("BackupSelfTest", "selesai: ${cases.count { !it.passed }} gagal\n${report.summary()}")
        report
    }

    private fun caseWal(dir: File) = runCase(
        dir, "wal", "mode WAL",
        preTransaction = { db ->
            val mode = db.rawQuery("PRAGMA journal_mode=WAL", null)
                .use { if (it.moveToFirst()) it.getString(0) else "?" }
            check(mode.equals("wal", ignoreCase = true)) { "gagal mengaktifkan WAL: $mode" }
        },
        setup = { db ->
            db.execSQL("CREATE TABLE t (id INTEGER PRIMARY KEY, v TEXT)")
            repeat(20) { db.execSQL("INSERT INTO t (v) VALUES (?)", arrayOf("wal-$it")) }
        },
        verify = { db ->
            val problems = mutableListOf<String>()
            val mode = db.rawQuery("PRAGMA journal_mode", null)
                .use { if (it.moveToFirst()) it.getString(0) else "?" }
            if (mode.equals("wal", ignoreCase = true)) problems += "snapshot masih bergantung pada WAL"
            problems += expectCount(db, "t", 20)
            problems
        }
    )

    private fun caseForeignKeys(dir: File) = runCase(
        dir, "fk", "foreign key cascade",
        // Pragma FK no-op di dalam transaksi, jadi wajib sebelum beginTransaction.
        preTransaction = { db -> db.execSQL("PRAGMA foreign_keys=ON") },
        setup = { db ->
            db.execSQL("CREATE TABLE parent (id INTEGER PRIMARY KEY)")
            db.execSQL("CREATE TABLE child (id INTEGER PRIMARY KEY, pid INTEGER REFERENCES parent(id) ON DELETE CASCADE)")
            db.execSQL("INSERT INTO parent (id) VALUES (1)")
            db.execSQL("INSERT INTO child (id, pid) VALUES (1, 1)")
            db.execSQL("INSERT INTO child (id, pid) VALUES (2, 1)")
            db.execSQL("DELETE FROM parent WHERE id = 1")
        },
        verify = { db ->
            val problems = expectCount(db, "parent", 0) + expectCount(db, "child", 0)
            val violations = db.rawQuery("PRAGMA foreign_key_check", null).use { it.count }
            if (violations != 0) problems + "foreign_key_check: $violations pelanggaran"
            problems
        }
    )

    private fun caseTrigger(dir: File) = runCase(
        dir, "trg", "trigger tidak fire ganda",
        setup = { db ->
            db.execSQL("CREATE TABLE a (id INTEGER PRIMARY KEY, v INTEGER)")
            db.execSQL("CREATE TABLE log (v INTEGER)")
            db.execSQL("CREATE TRIGGER trg AFTER INSERT ON a BEGIN INSERT INTO log (v) VALUES (new.v); END")
            db.execSQL("INSERT INTO a (v) VALUES (7)")
        },
        verify = { db -> expectCount(db, "a", 1) + expectCount(db, "log", 1) }
    )

    private fun caseBlob(dir: File) = runCase(
        dir, "blob", "data BLOB",
        setup = { db ->
            db.execSQL("CREATE TABLE b (id INTEGER PRIMARY KEY, data BLOB)")
            db.execSQL("INSERT INTO b (data) VALUES (?)", arrayOf(ByteArray(4096) { (it % 256).toByte() }))
        },
        verify = { db ->
            db.rawQuery("SELECT length(data), data FROM b", null).use { cursor ->
                if (!cursor.moveToFirst()) return@runCase listOf("tabel b kosong")
                val problems = mutableListOf<String>()
                if (cursor.getInt(0) != 4096) problems += "panjang BLOB ${cursor.getInt(0)}"
                if (!cursor.getBlob(1).contentEquals(ByteArray(4096) { (it % 256).toByte() })) {
                    problems += "isi BLOB berubah"
                }
                problems
            }
        }
    )

    private fun caseFts(dir: File) = runCase(
        dir, "fts", "tabel FTS4",
        setup = { db ->
            db.execSQL("CREATE VIRTUAL TABLE fts USING FTS4(content)")
            db.execSQL("INSERT INTO fts (content) VALUES ('kata penting')")
        },
        verify = { db ->
            val hits = db.rawQuery("SELECT count(*) FROM fts WHERE fts MATCH ?", arrayOf("penting"))
                .use { if (it.moveToFirst()) it.getInt(0) else -1 }
            val problems = mutableListOf<String>()
            if (hits != 1) problems += "MATCH 'penting' -> $hits baris"
            problems + expectCount(db, "fts", 1)
        }
    )

    private fun caseIntegrity(dir: File) = runCase(
        dir, "integ", "integritas setelah pulih",
        setup = { db ->
            db.execSQL("CREATE TABLE z (id INTEGER PRIMARY KEY AUTOINCREMENT, v TEXT)")
            repeat(50) { db.execSQL("INSERT INTO z (v) VALUES (?)", arrayOf("baris $it")) }
        },
        verify = { db ->
            val problems = expectCount(db, "z", 50)
            val full = db.rawQuery("PRAGMA integrity_check", null)
                .use { if (it.moveToFirst()) it.getString(0) else "kosong" }
            if (full != "ok") problems + "integrity_check: $full"
            val seq = db.rawQuery("SELECT seq FROM sqlite_sequence WHERE name='z'", null)
                .use { if (it.moveToFirst()) it.getLong(0) else -1L }
            if (seq != 50L) problems + "sqlite_sequence z = $seq, seharusnya 50"
            problems
        }
    )

    private fun caseTruncated(dir: File) = runCase(
        dir, "trunc", "deteksi file terpotong",
        setup = { db ->
            db.execSQL("CREATE TABLE p (id INTEGER PRIMARY KEY, v TEXT)")
            repeat(400) { db.execSQL("INSERT INTO p (v) VALUES (?)", arrayOf("baris $it")) }
        },
        mutate = { snapshot ->
            RandomAccessFile(snapshot, "rw").use { it.setLength(it.length() / 2) }
        },
        expectInvalid = true,
        verify = { db ->
            val check = db.rawQuery("PRAGMA quick_check", null)
                .use { if (it.moveToFirst()) it.getString(0) else "kosong" }
            if (check == "ok") listOf("quick_check tetap melaporkan ok") else emptyList()
        }
    )

    private fun runCase(
        dir: File,
        slug: String,
        name: String,
        preTransaction: (SQLiteDatabase) -> Unit = {},
        setup: (SQLiteDatabase) -> Unit,
        mutate: (File) -> Unit = {},
        expectInvalid: Boolean = false,
        verify: (SQLiteDatabase) -> List<String>
    ): Case {
        val started = System.currentTimeMillis()
        val source = File(dir, "$slug-src.db")
        val snapshot = File(dir, "$slug-snap.db")
        val restored = File(dir, "$slug-rest.db")
        fun result(passed: Boolean, detail: String?) =
            Case(name, passed, detail, System.currentTimeMillis() - started)

        return try {
            SQLiteDatabase.openOrCreateDatabase(source, null).use { db ->
                preTransaction(db)
                db.beginTransaction()
                try {
                    setup(db)
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }

            // Manifest dibangun dari SUMBER, bukan dari snapshot, agar kehilangan data terdeteksi.
            val manifest = SQLiteDatabase.openDatabase(
                source.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            ).use { srcDb ->
                BackupManifest(
                    id = "selftest",
                    createdAt = System.currentTimeMillis(),
                    kind = BackupKind.HOT,
                    schemaVersion = 10,
                    fingerprint = "selftest",
                    tables = SqliteSnapshot.tableNames(srcDb).map { table ->
                        val (rows, hash) = SqliteSnapshot.hashTable(srcDb, table)
                        TableFingerprint(table, rows, hash)
                    },
                    appVersion = null,
                    note = name
                )
            }

            SqliteSnapshot.copy(source.absolutePath, snapshot.absolutePath).getOrThrow()
            mutate(snapshot)

            val validation = BackupValidator.validate(snapshot.absolutePath, manifest)
            if (expectInvalid) {
                return result(
                    !validation.valid,
                    if (validation.valid) "file rusak tetap diloloskan validator"
                    else "ditolak validator: " + validation.reasons.joinToString("; ")
                )
            }
            if (!validation.valid) {
                return result(false, "validasi snapshot: " + validation.reasons.joinToString("; "))
            }

            snapshot.copyTo(restored, overwrite = true)
            SQLiteDatabase.openDatabase(restored.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { restoredDb ->
                val problems = mutableListOf<String>()
                val quick = restoredDb.rawQuery("PRAGMA quick_check", null)
                    .use { if (it.moveToFirst()) it.getString(0) else "kosong" }
                if (quick != "ok") problems += "quick_check hasil pulih: $quick"
                problems += verify(restoredDb)
                problems += compareFingerprints(restoredDb, manifest)
                if (problems.isEmpty()) result(true, null) else result(false, problems.joinToString("; "))
            }
        } catch (e: Exception) {
            result(false, e.message ?: e.javaClass.simpleName)
        } finally {
            source.delete()
            snapshot.delete()
            restored.delete()
        }
    }

    private fun compareFingerprints(db: SQLiteDatabase, manifest: BackupManifest): List<String> {
        val problems = mutableListOf<String>()
        val actual = SqliteSnapshot.tableNames(db).toSet()
        val expected = manifest.tables.associateBy { it.table }
        if (actual != expected.keys) {
            problems += "kumpulan tabel berbeda: ${(actual - expected.keys) + (expected.keys - actual)}"
        }
        expected.forEach { (table, fingerprint) ->
            if (table !in actual) return@forEach
            val (rows, hash) = SqliteSnapshot.hashTable(db, table)
            if (rows != fingerprint.rows) problems += "jumlah baris $table $rows != ${fingerprint.rows}"
            else if (hash != fingerprint.hash) problems += "hash $table berbeda"
        }
        return problems
    }

    private fun expectCount(db: SQLiteDatabase, table: String, expected: Int): List<String> {
        val actual = db.rawQuery("SELECT count(*) FROM `$table`", null)
            .use { if (it.moveToFirst()) it.getInt(0) else -1 }
        return if (actual == expected) emptyList() else listOf("baris $table $actual != $expected")
    }
}