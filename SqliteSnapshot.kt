package com.sync.app.domain.backup

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

object SqliteSnapshot {
    private const val TAG = "SqliteSnapshot"

    fun copy(sourcePath: String, targetPath: String): Result<Unit> = runCatching {
        val source = File(sourcePath)
        require(source.exists()) { "database sumber tidak ada" }

        val target = File(targetPath)
        target.parentFile?.mkdirs()
        deleteSidecars(target)
        if (target.exists()) target.delete()

        val src = SQLiteDatabase.openDatabase(sourcePath, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            // Semua frame WAL harus masuk ke file utama, kalau tidak snapshot tidak mandiri.
            src.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getInt(0) != 0) {
                    Log.w(TAG, "checkpoint sibuk, sisa ${cursor.getInt(1)} frame")
                }
            }

            val virtual = virtualTableNames(src)
            val schema = readSchema(src, virtual)

            val dst = SQLiteDatabase.openOrCreateDatabase(targetPath, null)
            try {
                // journal_mode tidak bisa diubah di dalam transaksi.
                dst.execSQL("PRAGMA journal_mode=TRUNCATE")
                dst.beginTransaction()
                try {
                    // 1. Tabel dulu (termasuk virtual).
                    schema.filter { it.type == "table" }.forEach { dst.execSQL(it.sql) }

                    // 2. Data, saat trigger/index belum ada: tidak ada fire ganda, insert cepat.
                    schema.filter { it.type == "table" }.forEach { copyRows(src, dst, it.name) }

                    // 3. Index, trigger, view setelah data.
                    schema.filter { it.type != "table" }.forEach { dst.execSQL(it.sql) }

                    copySequence(src, dst)

                    dst.setTransactionSuccessful()
                } finally {
                    dst.endTransaction()   // <-- PERBAIKAN INTI
                }
            } finally {
                dst.close()
            }
            deleteSidecars(target)
        } finally {
            src.close()
        }
    }

    private data class SchemaObject(val type: String, val name: String, val sql: String)

    private fun readSchema(src: SQLiteDatabase, virtual: Set<String>): List<SchemaObject> {
        val objects = mutableListOf<SchemaObject>()
        src.rawQuery(
            "SELECT type, name, sql FROM sqlite_master WHERE sql IS NOT NULL ORDER BY name",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val type = cursor.getString(0)
                val name = cursor.getString(1)
                if (name == "android_metadata") continue
                if (isShadow(name, virtual)) continue
                if (type == "index" && name.startsWith("sqlite_autoindex_")) continue
                objects += SchemaObject(type, name, cursor.getString(2))
            }
        }
        return objects
    }

    private fun copyRows(src: SQLiteDatabase, dst: SQLiteDatabase, table: String) {
        src.rawQuery("SELECT * FROM `$table`", null).use { cursor ->
            val columns = cursor.columnCount
            if (columns == 0) return
            val placeholders = List(columns) { "?" }.joinToString(",")
            val values = arrayOfNulls<Any?>(columns)
            while (cursor.moveToNext()) {
                for (index in 0 until columns) {
                    values[index] = when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                        else -> cursor.getString(index)
                    }
                }
                dst.execSQL("INSERT INTO `$table` VALUES ($placeholders)", values)
            }
        }
    }

    private fun copySequence(src: SQLiteDatabase, dst: SQLiteDatabase) {
        val hasSequence = src.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name='sqlite_sequence'",
            null
        ).use { it.moveToFirst() }
        if (!hasSequence) return
        src.rawQuery("SELECT name, seq FROM sqlite_sequence", null).use { cursor ->
            while (cursor.moveToNext()) {
                dst.execSQL(
                    "INSERT OR REPLACE INTO sqlite_sequence (name, seq) VALUES (?, ?)",
                    arrayOf(cursor.getString(0), cursor.getLong(1))
                )
            }
        }
    }

    fun hashTable(db: SQLiteDatabase, table: String): Pair<Int, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var rows = 0
        val order = if (hasRowid(db, table)) " ORDER BY rowid" else ""
        db.rawQuery("SELECT * FROM `$table`$order", null).use { cursor ->
            while (cursor.moveToNext()) {
                rows++
                for (index in 0 until cursor.columnCount) {
                    when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> digest.update(0)
                        Cursor.FIELD_TYPE_INTEGER -> digest.update(ByteBuffer.allocate(8).putLong(cursor.getLong(index)).array())
                        Cursor.FIELD_TYPE_FLOAT -> digest.update(ByteBuffer.allocate(8).putDouble(cursor.getDouble(index)).array())
                        Cursor.FIELD_TYPE_BLOB -> digest.update(cursor.getBlob(index))
                        else -> digest.update(cursor.getString(index).toByteArray())
                    }
                    digest.update(0x1F)
                }
                digest.update(0x1E)
            }
        }
        return rows to digest.digest().joinToString(" ") { "%02x".format(it) }
    }

    fun tableNames(db: SQLiteDatabase): List<String> {
        val virtual = virtualTableNames(db)
        val names = mutableListOf<String>()
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                if (name == "android_metadata") continue
                if (isShadow(name, virtual)) continue
                names += name
            }
        }
        return names
    }

    private fun virtualTableNames(db: SQLiteDatabase): Set<String> =
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND sql LIKE '%VIRTUAL TABLE%'",
            null
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

    private fun isShadow(name: String, virtual: Set<String>) =
        virtual.any { name.startsWith("${it}_") }

    private fun hasRowid(db: SQLiteDatabase, table: String): Boolean =
        db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE name=? AND sql LIKE '%WITHOUT ROWID%'",
            arrayOf(table)
        ).use { !it.moveToFirst() }

    private fun deleteSidecars(db: File) {
        listOf("-wal", "-shm", "-journal").forEach { File(db.path + it).delete() }
    }
}