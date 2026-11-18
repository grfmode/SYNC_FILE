package com.sync.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sync.app.domain.memory.MemoryScope
import com.sync.app.domain.memory.MemorySource
import com.sync.app.domain.memory.MemoryStatus
import com.sync.app.domain.memory.MemoryType

/**
 * Memori persisten. Satu baris = satu fakta/keputusan/instruksi/preferensi.
 *
 * [uid] deterministik agar impor legacy dan impor berkas idempoten.
 * [projectId] null berarti scope GLOBAL.
 * [provenance] menyimpan asal-usul (messageId, path berkas, atau "manual").
 */
@Entity(
    tableName = "memory_items",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["uid"], unique = true),
        Index("projectId"),
        Index("scope"),
        Index("status"),
        Index("type")
    ]
)
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val scope: MemoryScope,
    val projectId: Long? = null,
    val type: MemoryType,
    val status: MemoryStatus,
    val source: MemorySource,
    val content: String,
    val normalized: String,
    val pinned: Boolean = false,
    val confidence: Float = 1f,
    val provenance: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
    val useCount: Int = 0
)

/**
 * Relasi antar memori. Dipakai untuk supersede (riwayat) dan keterkaitan.
 */
@Entity(
    tableName = "memory_links",
    foreignKeys = [
        ForeignKey(
            entity = MemoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MemoryItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["toId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("fromId"), Index("toId")]
)
data class MemoryLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromId: Long,
    val toId: Long,
    val relation: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Tabel pencarian. `rowid` selalu sama dengan [MemoryItemEntity.id].
 */
@Fts4(tokenizer = "unicode61")
@Entity(tableName = "memory_fts")
data class MemoryFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,
    val content: String,
    val normalized: String
)