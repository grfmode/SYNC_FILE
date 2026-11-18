package com.sync.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.sync.app.model.AttachmentType
import com.sync.app.model.AttachmentUi
import com.sync.app.model.ChatMessage
import com.sync.app.model.Conversation
import com.sync.app.model.LibraryItem
import com.sync.app.model.LibraryItemType
import com.sync.app.model.MessageRole
import com.sync.app.model.MessageStatus
import kotlinx.serialization.json.Json

/**
 * `memoryText` dipertahankan sebagai sumber impor legacy dan jalur rollback.
 * Sumber kebenaran memori kini ada di [MemoryItemEntity].
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val systemPrompt: String? = null,
    val memoryText: String? = null,
    val createdAt: Long
)

@Entity(
    tableName = "project_attachments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class ProjectAttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val uri: String,
    val name: String,
    val mimeType: String,
    val type: AttachmentType,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("projectId")]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val projectId: Long? = null,
    val summaryText: String? = null,
    val summaryUpToMessageId: Long? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: Long,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val attachmentsJson: String? = null,
    val createdAt: Long
)

@Entity(tableName = "library_items")
data class LibraryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val name: String,
    val mimeType: String,
    val type: LibraryItemType,
    val content: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey val name: String
)

@Entity(
    tableName = "library_item_tag_cross_ref",
    primaryKeys = ["libraryItemId", "tagName"],
    foreignKeys = [
        ForeignKey(
            entity = LibraryEntity::class,
            parentColumns = ["id"],
            childColumns = ["libraryItemId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["name"],
            childColumns = ["tagName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("libraryItemId"), Index("tagName")]
)
data class LibraryItemTagCrossRef(
    val libraryItemId: Long,
    val tagName: String
)

data class LibraryItemWithTags(
    @Embedded val item: LibraryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "name",
        associateBy = Junction(
            value = LibraryItemTagCrossRef::class,
            parentColumn = "libraryItemId",
            entityColumn = "tagName"
        )
    )
    val tags: List<TagEntity>
)

fun ConversationEntity.toDomain(): Conversation = Conversation(
    id = id,
    title = title,
    updatedAt = updatedAt,
    projectId = projectId
)

fun ProjectEntity.toDomain(): com.sync.app.model.Project = com.sync.app.model.Project(
    id = id,
    name = name,
    description = description,
    systemPrompt = systemPrompt,
    memoryText = memoryText,
    createdAt = createdAt
)

fun ProjectAttachmentEntity.toDomain(): AttachmentUi = AttachmentUi(
    uri = uri,
    name = name,
    mimeType = mimeType,
    type = type
)

fun MessageEntity.toDomain(): ChatMessage {
    val parsedAttachments = if (!attachmentsJson.isNullOrBlank()) {
        try {
            Json.decodeFromString<List<AttachmentUi>>(attachmentsJson)
        } catch (e: Exception) {
            emptyList()
        }
    } else {
        emptyList()
    }

    return ChatMessage(
        id = id,
        content = content,
        role = role,
        status = status,
        attachments = parsedAttachments
    )
}

fun LibraryItemWithTags.toDomain(): LibraryItem = LibraryItem(
    id = item.id,
    uri = item.uri,
    name = item.name,
    mimeType = item.mimeType,
    type = item.type,
    content = item.content,
    createdAt = item.createdAt,
    tags = tags.map { it.name }
)

// --- mapper memori & knowledge

fun MemoryItemEntity.toDomain(): com.sync.app.domain.memory.MemoryItem =
    com.sync.app.domain.memory.MemoryItem(
        id = id,
        uid = uid,
        scope = scope,
        projectId = projectId,
        type = type,
        status = status,
        source = source,
        content = content,
        pinned = pinned,
        confidence = confidence,
        provenance = provenance,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastUsedAt = lastUsedAt,
        useCount = useCount
    )

fun KnowledgeFileEntity.toDomain(): com.sync.app.domain.knowledge.KnowledgeFile =
    com.sync.app.domain.knowledge.KnowledgeFile(
        id = id,
        projectId = projectId,
        relPath = relPath,
        displayName = displayName,
        kind = runCatching { com.sync.app.domain.knowledge.KnowledgeKind.valueOf(kind) }
            .getOrDefault(com.sync.app.domain.knowledge.KnowledgeKind.PLAIN_TEXT),
        sizeBytes = sizeBytes,
        status = runCatching { com.sync.app.domain.knowledge.KnowledgeStatus.valueOf(status) }
            .getOrDefault(com.sync.app.domain.knowledge.KnowledgeStatus.PENDING),
        chunkCount = chunkCount,
        error = error,
        indexedAt = indexedAt
    )

fun com.sync.app.data.local.KnowledgeHitRow.toDomain(): com.sync.app.domain.knowledge.KnowledgeChunk =
    com.sync.app.domain.knowledge.KnowledgeChunk(
        id = chunkId,
        fileId = fileId,
        relPath = relPath,
        ordinal = 0,
        startLine = startLine,
        endLine = endLine,
        symbol = symbol,
        content = content
    )