package com.sync.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {
    const val CURRENT_VERSION = 10

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean =
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table)).use {
            it.moveToFirst()
        }

    private fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        if (!tableExists(db, table)) return false
        return db.query("PRAGMA table_info(`$table`)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            var found = false
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && cursor.getString(nameIndex) == column) {
                    found = true
                    break
                }
            }
            found
        }
    }

    private fun ensureColumn(db: SupportSQLiteDatabase, table: String, column: String, ddl: String) {
        if (!columnExists(db, table, column)) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $ddl")
        }
    }

    private fun fts4Available(db: SupportSQLiteDatabase): Boolean =
        db.query("PRAGMA compile_options").use { cursor ->
            val index = cursor.getColumnIndex("compile_options")
            var enabled = false
            while (cursor.moveToNext()) {
                if (index >= 0) {
                    val option = cursor.getString(index).uppercase()
                    if (option.contains("FTS3") || option.contains("FTS4")) {
                        enabled = true
                        break
                    }
                }
            }
            enabled
        }

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversations` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `conversationId` INTEGER NOT NULL,
                    `role` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `attachmentsJson` TEXT DEFAULT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversationId` ON `messages`(`conversationId`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `projects` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            ensureColumn(db, "conversations", "projectId", "INTEGER DEFAULT NULL")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_projectId` ON `conversations`(`projectId`)")
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureColumn(db, "conversations", "summaryText", "TEXT DEFAULT NULL")
            ensureColumn(db, "conversations", "summaryUpToMessageId", "INTEGER DEFAULT NULL")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureColumn(db, "projects", "description", "TEXT DEFAULT NULL")
            ensureColumn(db, "projects", "systemPrompt", "TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `project_attachments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `projectId` INTEGER NOT NULL,
                    `uri` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_project_attachments_projectId` ON `project_attachments`(`projectId`)")
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            ensureColumn(db, "projects", "memoryText", "TEXT DEFAULT NULL")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `library_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `uri` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `content` TEXT DEFAULT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tags` (
                    `name` TEXT NOT NULL,
                    PRIMARY KEY(`name`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `library_item_tag_cross_ref` (
                    `libraryItemId` INTEGER NOT NULL,
                    `tagName` TEXT NOT NULL,
                    PRIMARY KEY(`libraryItemId`, `tagName`),
                    FOREIGN KEY(`libraryItemId`) REFERENCES `library_items`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`tagName`) REFERENCES `tags`(`name`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_item_tag_cross_ref_libraryItemId` ON `library_item_tag_cross_ref`(`libraryItemId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_library_item_tag_cross_ref_tagName` ON `library_item_tag_cross_ref`(`tagName`)")
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `uid` TEXT NOT NULL,
                    `scope` TEXT NOT NULL,
                    `projectId` INTEGER DEFAULT NULL,
                    `type` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `source` TEXT NOT NULL,
                    `content` TEXT NOT NULL,
                    `normalized` TEXT NOT NULL,
                    `pinned` INTEGER NOT NULL DEFAULT 0,
                    `confidence` REAL NOT NULL DEFAULT 1.0,
                    `provenance` TEXT DEFAULT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `lastUsedAt` INTEGER DEFAULT NULL,
                    `useCount` INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_memory_items_uid` ON `memory_items`(`uid`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_projectId` ON `memory_items`(`projectId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_scope` ON `memory_items`(`scope`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_status` ON `memory_items`(`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_items_type` ON `memory_items`(`type`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `memory_links` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `fromId` INTEGER NOT NULL,
                    `toId` INTEGER NOT NULL,
                    `relation` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`fromId`) REFERENCES `memory_items`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`toId`) REFERENCES `memory_items`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_links_fromId` ON `memory_links`(`fromId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_memory_links_toId` ON `memory_links`(`toId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `knowledge_files` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `projectId` INTEGER NOT NULL,
                    `relPath` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `sha256` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `chunkCount` INTEGER NOT NULL DEFAULT 0,
                    `cursor` INTEGER NOT NULL DEFAULT 0,
                    `error` TEXT DEFAULT NULL,
                    `indexedAt` INTEGER DEFAULT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_knowledge_files_projectId_relPath` ON `knowledge_files`(`projectId`, `relPath`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_files_status` ON `knowledge_files`(`status`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `knowledge_chunks` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `fileId` INTEGER NOT NULL,
                    `ordinal` INTEGER NOT NULL,
                    `startLine` INTEGER NOT NULL,
                    `endLine` INTEGER NOT NULL,
                    `symbol` TEXT DEFAULT NULL,
                    `content` TEXT NOT NULL,
                    FOREIGN KEY(`fileId`) REFERENCES `knowledge_files`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_fileId` ON `knowledge_chunks`(`fileId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_knowledge_chunks_fileId_ordinal` ON `knowledge_chunks`(`fileId`, `ordinal`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `knowledge_links` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `fromChunkId` INTEGER NOT NULL,
                    `toChunkId` INTEGER NOT NULL,
                    `relation` TEXT NOT NULL,
                    FOREIGN KEY(`fromChunkId`) REFERENCES `knowledge_chunks`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`toChunkId`) REFERENCES `knowledge_chunks`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_links_fromChunkId` ON `knowledge_links`(`fromChunkId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_knowledge_links_toChunkId` ON `knowledge_links`(`toChunkId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `context_log` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `messageId` INTEGER NOT NULL,
                    `section` TEXT NOT NULL,
                    `refId` INTEGER DEFAULT NULL,
                    `refKind` TEXT NOT NULL,
                    `label` TEXT DEFAULT NULL,
                    `tokens` INTEGER NOT NULL,
                    `truncated` INTEGER NOT NULL DEFAULT 0,
                    `mode` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_context_log_messageId` ON `context_log`(`messageId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_context_log_section` ON `context_log`(`section`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `schema_meta` (
                    `key` TEXT NOT NULL,
                    `value` TEXT NOT NULL,
                    PRIMARY KEY(`key`)
                )
                """.trimIndent()
            )

            if (fts4Available(db)) {
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `memory_fts` USING FTS4(`content`, `normalized`, tokenize=unicode61)")
                db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `knowledge_fts` USING FTS4(`content`, `symbol`, `relPath`, tokenize=unicode61)")
            }

            ensureColumn(db, "conversations", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            ensureColumn(db, "conversations", "summaryText", "TEXT DEFAULT NULL")
            ensureColumn(db, "conversations", "summaryUpToMessageId", "INTEGER DEFAULT NULL")
            ensureColumn(db, "conversations", "projectId", "INTEGER DEFAULT NULL")
            ensureColumn(db, "projects", "description", "TEXT DEFAULT NULL")
            ensureColumn(db, "projects", "systemPrompt", "TEXT DEFAULT NULL")
            ensureColumn(db, "projects", "memoryText", "TEXT DEFAULT NULL")
            ensureColumn(db, "messages", "attachmentsJson", "TEXT DEFAULT NULL")
        }
    }
}