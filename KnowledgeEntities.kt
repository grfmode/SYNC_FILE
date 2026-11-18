package com.sync.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Satu berkas sumber yang diimpor dari repo (arsip zip atau pohon file).
 *
 * [relPath] adalah path relatif terhadap akar repo — dipakai untuk atribusi
 * `[S1] path:line` dan untuk dedup antar-impor.
 * [status] mengikuti siklus Pending → Indexing → Indexed / Failed / Skipped,
 * sehingga indeks bisa dilanjutkan setelah proses aplikasi mati.
 */
@Entity(
    tableName = "knowledge_files",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["projectId", "relPath"], unique = true),
        Index("status")
    ]
)
data class KnowledgeFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val relPath: String,
    val displayName: String,
    val kind: String,
    val sizeBytes: Long,
    val sha256: String,
    val status: String,
    val chunkCount: Int = 0,
    val cursor: Int = 0,
    val error: String? = null,
    val indexedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Potongan berkas. Batas baris disimpan agar atribusi bisa menunjuk baris nyata.
 */
@Entity(
    tableName = "knowledge_chunks",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fileId"), Index(value = ["fileId", "ordinal"], unique = true)]
)
data class KnowledgeChunkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: Long,
    val ordinal: Int,
    val startLine: Int,
    val endLine: Int,
    val symbol: String? = null,
    val content: String
)

/**
 * Relasi antar potongan (mis. simbol yang didefinisikan lalu dipakai).
 */
@Entity(
    tableName = "knowledge_links",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgeChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromChunkId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KnowledgeChunkEntity::class,
            parentColumns = ["id"],
            childColumns = ["toChunkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fromChunkId"), Index("toChunkId")]
)
data class KnowledgeLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromChunkId: Long,
    val toChunkId: Long,
    val relation: String
)

/**
 * Tabel pencarian potongan. `rowid` selalu sama dengan [KnowledgeChunkEntity.id].
 */
@Fts4(tokenizer = "unicode61")
@Entity(tableName = "knowledge_fts")
data class KnowledgeFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val content: String,
    val symbol: String,
    val relPath: String
)