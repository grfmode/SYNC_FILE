package com.sync.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Catatan apa yang benar-benar dikirim ke model pada satu jawaban.
 *
 * Satu-satunya sumber angka untuk chip konteks. Bila tabel ini kosong untuk
 * sebuah pesan, chip tidak ditampilkan — tidak ada estimasi di UI.
 *
 * [refKind] = MEMORY | KNOWLEDGE | SUMMARY | HISTORY | PROJECT | SYSTEM
 * [refId]    = id memori / id potongan / id pesan, null untuk seksi statis
 */
@Entity(
    tableName = "context_log",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("messageId"), Index("section")]
)
data class ContextLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val section: String,
    val refId: Long? = null,
    val refKind: String,
    val label: String? = null,
    val tokens: Int,
    val truncated: Boolean = false,
    val mode: String,
    val createdAt: Long = System.currentTimeMillis()
)