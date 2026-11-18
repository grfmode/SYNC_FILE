package com.sync.app.model

/**
 * Representasi domain sebuah percakapan.
 *
 * Sengaja dipisahkan dari [com.sync.app.data.local.ConversationEntity] agar
 * lapisan domain/UI tidak mengekspos tipe Room.
 */
data class Conversation(
    val id: Long,
    val title: String,
    val updatedAt: Long,
    val projectId: Long? = null
)