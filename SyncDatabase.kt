package com.sync.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Database tunggal aplikasi.
 *
 * Versi 10 menambah memori, knowledge, context log, dan metadata skema.
 * Seluruh migrasi berada di [Migrations] agar tidak ada lompatan versi yang
 * terlewat saat urutan dibaca ulang.
 *
 * `exportSchema` tetap `false` karena folder schema tidak ikut ter-commit;
 * sidik jari skema untuk validasi backup disimpan di tabel `schema_meta`.
 */
@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ProjectEntity::class,
        ProjectAttachmentEntity::class,
        LibraryEntity::class,
        TagEntity::class,
        LibraryItemTagCrossRef::class,
        MemoryItemEntity::class,
        MemoryLinkEntity::class,
        MemoryFtsEntity::class,
        KnowledgeFileEntity::class,
        KnowledgeChunkEntity::class,
        KnowledgeLinkEntity::class,
        KnowledgeFtsEntity::class,
        ContextLogEntity::class,
        SchemaMetaEntity::class
    ],
    version = Migrations.CURRENT_VERSION,
    exportSchema = false
)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun libraryDao(): LibraryDao
    abstract fun memoryDao(): MemoryDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun contextLogDao(): ContextLogDao

    companion object {
        const val NAME = "sync.db"

        /** Sidik jari skema: jumlah tabel + nama tabel yang terdaftar di sqlite_master. */
        suspend fun fingerprint(db: SyncDatabase): String {
            val dao = db.openHelper.writableDatabase
            val tables = mutableListOf<String>()
            dao.query("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name").use { cursor ->
                val index = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) {
                    if (index >= 0) tables += cursor.getString(index)
                }
            }
            return "v${Migrations.CURRENT_VERSION}:${tables.size}:${tables.joinToString(",")}"
        }
    }
}

/** Nama berkas database utama, dipakai lapisan backup dan restore. */
object DatabaseFiles {
    const val MAIN = SyncDatabase.NAME
    const val LAB = "lab.db"

    fun path(context: Context, name: String): String =
        context.getDatabasePath(name).absolutePath
}