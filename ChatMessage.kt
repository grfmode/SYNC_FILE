package com.sync.app.model

import kotlinx.serialization.Serializable

/**
 * Model pesan percakapan.
 * Disimpan in-memory untuk T-011; akan dipindahkan ke Room pada T-012.
 * Mengikuti konsep di BLUEPRINT §7 (Message, role, status).
 * 
 * T-026: Menambahkan dukungan Attachment (Gambar & Dokumen).
 */
@Serializable
data class ChatMessage(
    val id: Long,
    val content: String,
    val role: MessageRole,
    val status: MessageStatus = MessageStatus.Completed,
    val attachments: List<AttachmentUi> = emptyList()
)

@Serializable
enum class MessageRole {
    User,
    Assistant
}

@Serializable
enum class MessageStatus {
    Sending,
    Completed,
    Failed
}

@Serializable
data class AttachmentUi(
    val uri: String,
    val name: String,
    val mimeType: String,
    val type: AttachmentType
)

@Serializable
enum class AttachmentType {
    Image,
    Document
}

// [P-029] Model untuk Universal Library
@Serializable
data class LibraryItem(
    val id: Long,
    val uri: String,
    val name: String,
    val mimeType: String,
    val type: LibraryItemType,
    val content: String? = null, // Untuk Code Snippets
    val createdAt: Long,
    val tags: List<String> = emptyList() // [Update] Tambah field Tag
)

@Serializable
enum class LibraryItemType {
    Image,
    Document,
    Snippet
}